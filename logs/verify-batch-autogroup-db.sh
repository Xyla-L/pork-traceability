#!/usr/bin/env bash
# 胴体批次自动归集 —— 落库断言（与 logs/verify-batch-autogroup.mjs 配套）。
#
# 为什么不像别的回归那样「mjs 先跑、把 id 当参数传进来」，而是本脚本自己驱动 mjs：
# 有两类数据只能走 SQL 造，而且都必须在 mjs 跑之前就位：
#   1)「同场但不同日的批次不会并批」需要一条**昨天**的设备批次；
#   2)「达到头数上限自动封批」需要一条**今天、同场、已接近上限**的归集中批次——
#      200 头不可能靠真上报攒出来，只能把 pig_count 先填到 上限-1。
# 所以顺序是：先 SQL 预置 → 再跑 mjs（把这三样传给它）→ 再 SQL 断言 → 清理。
#
# 需要落库核验的事：
#   · 被拦下的上报到底有没有偷偷写进业务表
#   · 「同场同日归一批」在库里是不是真的只有一条批次
#   · 设备归集的批次有没有把操作人写成设备名（应该是空 —— 归集是机器行为、没有操作人）
#   · 人工兜底的批次有没有如实标成 MANUAL 并落录入人
#   · 头数上限有没有真的触发自动封批、封批后是否真的另开新批
#   · 头数 / pig_ids 长度 / 关联行数三者是否一致，一头猪有没有落在两个批次
#   · 猪状态有没有被误推进为已屠宰
#
# 运行：bash logs/verify-batch-autogroup-db.sh
set -euo pipefail

MYSQL_BIN="docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4"
NODE_BIN="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

STAMP=$(date +%s)
HOUSE_CROSS="归集验证屠宰场-跨日-${STAMP}"
HOUSE_FULL="归集验证屠宰场-上限-${STAMP}"
# 必须与 distribution-service 的 ingest.carcass-batch.max-pigs 默认值一致（application 里默认 200）。
# 改了那个上限就要同步改这里，否则下面「顶到上限自动封批」的断言会失败——这是有意的：
# 宁可让测试红着提醒你，也不要它悄悄不测了。
BATCH_LIMIT=200

q() { $MYSQL_BIN -N -e "$1" 2>/dev/null; }

echo "=== 0) 记录水位（清理用）与预置两条批次 ==="
CHAIN_BEFORE=$(q "SELECT IFNULL(MAX(id),0) FROM db_common.blockchain_record;")
YESTERDAY_ID=$(q "
INSERT INTO db_distribution.carcass_batch
  (batch_no, pig_ids, pig_count, total_weight_kg, slaughterhouse, create_time, source, source_ref, status, note)
VALUES
  ('CB-YD-${STAMP}', JSON_ARRAY(), 0, 0, '${HOUSE_CROSS}', DATE_SUB(NOW(), INTERVAL 1 DAY), 'DEVICE', 'BATCH-001', 2,
   '回归验证：昨日设备批次，今日白条不应并入');
SELECT LAST_INSERT_ID();")
# 「已接近上限」的批次：头数刻意先填成 上限-1（关联行只有 1 条，是这一条合成数据的固有偏差，
# 只用来把自动封批那条路径跑到，不作为「头数=关联行数」不变量的断言对象）。
FULL_ID=$(q "
INSERT INTO db_distribution.carcass_batch
  (batch_no, pig_ids, pig_count, total_weight_kg, slaughterhouse, create_time, source, source_ref, status, note)
VALUES
  ('CB-FULL-${STAMP}', JSON_ARRAY(), $((BATCH_LIMIT-1)), 0, '${HOUSE_FULL}', NOW(), 'DEVICE', 'BATCH-001', 1,
   '回归验证：模拟已接近头数上限的归集中批次');
SELECT LAST_INSERT_ID();")
echo "  昨日批次 id=${YESTERDAY_ID}  屠宰场=${HOUSE_CROSS}"
echo "  接近上限批次 id=${FULL_ID}  屠宰场=${HOUSE_FULL}  预置头数=$((BATCH_LIMIT-1))  上链水位=${CHAIN_BEFORE}"

echo
echo "=== 1) 跑 HTTP 断言（把预置数据传给 mjs）==="
OUT=$("$NODE_BIN" "$ROOT_DIR/logs/verify-batch-autogroup.mjs" \
  "$YESTERDAY_ID" "$HOUSE_CROSS" "$HOUSE_FULL" "$FULL_ID" "$BATCH_LIMIT" 2>&1) || true
printf '%s\n' "$OUT"

pick() { printf '%s\n' "$OUT" | grep -F "$1 " | tail -1 | sed "s/^$1 //"; }
STAGING=$(pick '#STAGING' || true)
CLEANUP=$(pick '#CLEANUP' || true)
PIGS_JSON=$(pick '#PIGS' || true)
BATCHES_JSON=$(pick '#BATCHES' || true)
HOUSES_JSON=$(pick '#HOUSES' || true)

jget() { printf '%s' "$1" | sed -n "s/.*\"$2\":\([^,}]*\).*/\1/p"; }
PIGS=$(printf '%s' "$CLEANUP" | sed -n 's/.*"pigs":\[\([^]]*\)\].*/\1/p' | tr -d ' ')
BATCH_LIST=$(printf '%s' "$CLEANUP" | sed -n 's/.*"carcassBatches":\[\([^]]*\)\].*/\1/p' | tr -d ' ')

P0=$(jget "$PIGS_JSON" P0); P1=$(jget "$PIGS_JSON" P1); P2=$(jget "$PIGS_JSON" P2); P3=$(jget "$PIGS_JSON" P3)
P4=$(jget "$PIGS_JSON" P4); P5=$(jget "$PIGS_JSON" P5); P6=$(jget "$PIGS_JSON" P6)
PC=$(jget "$PIGS_JSON" PC); PD=$(jget "$PIGS_JSON" PD)
MANUAL_BATCH=$(jget "$BATCHES_JSON" manual); AUTO_BATCH=$(jget "$BATCHES_JSON" auto); HOUSE_B_BATCH=$(jget "$BATCHES_JSON" houseB)
FULL_NEW=$(jget "$BATCHES_JSON" fullNew)
# 屠宰场名是 JSON 字符串，取值时要剥掉两侧引号，否则拼进 SQL 会多一对引号
HOUSE_A=$(jget "$HOUSES_JSON" a | tr -d '"')

if [ -z "${AUTO_BATCH:-}" ] || [ "$AUTO_BATCH" = "null" ]; then
  echo "!! 没有拿到自动归集的批次 id，后续断言无法进行（HTTP 断言大概率已失败，先看上面的输出）"
  exit 1
fi

echo
echo "=== 2) 落库断言 ==="
rows=$(q "
SELECT 'auto_exists', COUNT(*) FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH} AND void_flag=0;
SELECT 'auto_pig_count', JSON_LENGTH(pig_ids) FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'auto_has_p1', JSON_CONTAINS(pig_ids, '${P1}') FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'auto_has_p2', JSON_CONTAINS(pig_ids, '${P2}') FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'auto_source', IFNULL(source,'NULL') FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'auto_source_ref', IFNULL(source_ref,'NULL') FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'auto_operator_empty', IF(operator IS NULL,'NULL',operator) FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'auto_batch_no_prefix', LEFT(batch_no,2) FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'auto_weight', total_weight_kg FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'manual_source', IFNULL(source,'NULL') FROM db_distribution.carcass_batch WHERE id=${MANUAL_BATCH};
SELECT 'manual_source_ref', IFNULL(source_ref,'NULL') FROM db_distribution.carcass_batch WHERE id=${MANUAL_BATCH};
SELECT 'houseB_pig_count', JSON_LENGTH(pig_ids) FROM db_distribution.carcass_batch WHERE id=${HOUSE_B_BATCH};
SELECT 'houseB_has_p3', JSON_CONTAINS(pig_ids, '${P3}') FROM db_distribution.carcass_batch WHERE id=${HOUSE_B_BATCH};
SELECT 'yesterday_pig_count', JSON_LENGTH(pig_ids) FROM db_distribution.carcass_batch WHERE id=${YESTERDAY_ID};
SELECT 'dev_batches_today_houseA', COUNT(*) FROM db_distribution.carcass_batch
  WHERE slaughterhouse='$HOUSE_A' AND source='DEVICE' AND void_flag=0 AND DATE(create_time)=CURDATE();
SELECT 'p4_in_any_batch', COUNT(*) FROM db_distribution.carcass_batch WHERE JSON_CONTAINS(pig_ids, '${P4}');
SELECT 'p5_in_any_batch', COUNT(*) FROM db_distribution.carcass_batch WHERE JSON_CONTAINS(pig_ids, '${P5}');
SELECT 'p6_in_any_batch', COUNT(*) FROM db_distribution.carcass_batch WHERE JSON_CONTAINS(pig_ids, '${P6}');
SELECT 'p1_status', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${P1}),'MISSING');
SELECT 'p2_status', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${P2}),'MISSING');
SELECT 'p3_status', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${P3}),'MISSING');
-- P4 走完了宰后合格检验，所以状态确实是「已屠宰」——这个推进来自宰后检验（SlaughterDeviceIngestService
-- 里 inspectType>=2 就推进），跟入不入批无关。它只是没盖章，所以进不了批次（见上面 p4_in_any_batch=0）。
SELECT 'p4_status_from_inspection', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${P4}),'MISSING');
-- P5 只到入场查验、没做瘦肉精，也就没走到宰后检验，状态不应被推进为「已屠宰」。
SELECT 'p5_status_not3', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${P5}),'MISSING');
SELECT 'p6_status_not3', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${P6}),'MISSING');
SELECT 'chain_auto', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='CARCASS_BATCH' AND biz_id=${AUTO_BATCH};
SELECT 'chain_auto_new', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='CARCASS_BATCH' AND biz_id=${AUTO_BATCH} AND biz_key LIKE '%AUTO_GROUP_NEW%';
SELECT 'chain_auto_append', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='CARCASS_BATCH' AND biz_id=${AUTO_BATCH} AND biz_key LIKE '%AUTO_GROUP_APPEND%';
SELECT 'chain_manual', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='CARCASS_BATCH' AND biz_id=${MANUAL_BATCH} AND biz_key LIKE '%MANUAL_CREATE%';
-- 封批与封批后的分叉
SELECT 'auto_status_after_seal', status FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'chain_auto_seal', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='CARCASS_BATCH' AND biz_id=${AUTO_BATCH} AND biz_key LIKE '%MANUAL_SEAL%';
SELECT 'full_pig_count', pig_count FROM db_distribution.carcass_batch WHERE id=${FULL_ID};
SELECT 'full_status', status FROM db_distribution.carcass_batch WHERE id=${FULL_ID};
SELECT 'full_has_pc', JSON_CONTAINS(pig_ids, '${PC}') FROM db_distribution.carcass_batch WHERE id=${FULL_ID};
SELECT 'full_links', COUNT(*) FROM db_distribution.carcass_batch_pig WHERE batch_id=${FULL_ID};
SELECT 'fullnew_exists', COUNT(*) FROM db_distribution.carcass_batch WHERE id=${FULL_NEW} AND void_flag=0;
SELECT 'fullnew_status', status FROM db_distribution.carcass_batch WHERE id=${FULL_NEW};
SELECT 'fullnew_pig_count', pig_count FROM db_distribution.carcass_batch WHERE id=${FULL_NEW};
SELECT 'fullnew_has_pd', JSON_CONTAINS(pig_ids, '${PD}') FROM db_distribution.carcass_batch WHERE id=${FULL_NEW};
SELECT 'fullnew_count_vs_links', (SELECT pig_count FROM db_distribution.carcass_batch WHERE id=${FULL_NEW}) - (SELECT COUNT(*) FROM db_distribution.carcass_batch_pig WHERE batch_id=${FULL_NEW});
SELECT 'open_batches_full_today', COUNT(*) FROM db_distribution.carcass_batch WHERE slaughterhouse='${HOUSE_FULL}' AND source='DEVICE' AND status=1 AND void_flag=0 AND DATE(create_time)=CURDATE();
-- 不变量的落库核验：头数 / JSON 长度 / 关联行数三者一致，且一头猪不会落在两个批次
SELECT 'auto_count_vs_links', (SELECT pig_count FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH}) - (SELECT COUNT(*) FROM db_distribution.carcass_batch_pig WHERE batch_id=${AUTO_BATCH});
SELECT 'auto_json_vs_count', JSON_LENGTH(pig_ids) - pig_count FROM db_distribution.carcass_batch WHERE id=${AUTO_BATCH};
SELECT 'uk_pig_dupes', COUNT(*) FROM (SELECT pig_id FROM db_distribution.carcass_batch_pig GROUP BY pig_id HAVING COUNT(*) > 1) t;
")

pass=0; fail=0
expect() { if printf '%s' "$2" | grep -qF -- "$3"; then pass=$((pass+1)); echo "  [PASS] $1";
  else fail=$((fail+1)); echo "  [FAIL] $1 —— 实际值: $2"; fi; }
get() { printf '%s\n' "$rows" | awk -F'\t' -v k="$1" '$1==k{print $2}'; }

echo "--- 自动归集出来的批次 ---"
expect "设备批次已落库" "$(get auto_exists)" "1"
expect "★ 同场同日的两头白条归在同一批次里" "$(get auto_pig_count)" "2"
expect "★ 批次里含第一头（P1）" "$(get auto_has_p1)" "1"
expect "★ 批次里含第二头（P2，追加进来的）" "$(get auto_has_p2)" "1"
expect "★ 来源标成 DEVICE" "$(get auto_source)" "DEVICE"
expect "★ 来源设备记的是分拣台编号" "$(get auto_source_ref)" "BATCH-001"
expect "★ 操作人为空（归集是机器行为，没拿设备名冒充人名）" "$(get auto_operator_empty)" "NULL"
expect "批次号由系统生成（CB 前缀）" "$(get auto_batch_no_prefix)" "CB"
expect "★ 总重量为两头胴体过磅之和（78.5 + 82.3 = 160.8）" "$(get auto_weight)" "160.8"
expect "★ 同场同日只此一条设备批次" "$(get dev_batches_today_houseA)" "1"

echo "--- 人工兜底建的那一批 ---"
expect "人工批次已落库" "$(get manual_source)" "MANUAL"
# 来源列（source_ref）取的是**登录态实名**，不是请求体里那个 operator 字段 —— 请求体可伪造，
# 来源列是审计口径。脚本用 admin/123456 登录，所以这里就是「系统管理员」。
expect "★ 人工批次的来源记的是登录态实名（不是请求体里自报的操作人）" "$(get manual_source_ref)" "系统管理员"

echo "--- 不同屠宰场不混批 ---"
expect "★ 乙场另建了批次，且只含它自己那 1 头" "$(get houseB_pig_count)" "1"
expect "★ 乙场批次里含 P3" "$(get houseB_has_p3)" "1"

echo "--- 归集键含日期：昨日的同场批次不被并入 ---"
expect "★ 昨日设备批次仍为空（今日白条没有并进去）" "$(get yesterday_pig_count)" "0"

echo "--- 头数上限与封批 ---"
expect "★ 封批后批次状态落成「已封批」" "$(get auto_status_after_seal)" "2"
expect "★ 封批动作已上链" "$(get chain_auto_seal)" "1"
expect "★ 接近上限的批次被顶到上限（199 + 1 = 200）" "$(get full_pig_count)" "$BATCH_LIMIT"
expect "★ 顶到上限自动封批（status=2）" "$(get full_status)" "2"
expect "★ 预置批次里含 PC" "$(get full_has_pc)" "1"
expect "★ PC 落进了预置批次（追加而不是另开）" "$(get full_links)" "1"
expect "★ 封批后 PD 另开了一个新批次" "$(get fullnew_exists)" "1"
expect "★ 新批次是「归集中」（status=1）" "$(get fullnew_status)" "1"
expect "★ 新批次只含 PD 这一头" "$(get fullnew_pig_count)" "1"
expect "★ 新批次里含 PD" "$(get fullnew_has_pd)" "1"
expect "★ 同场当日只剩一个归集中的批次（已封的那个不再被并入）" "$(get open_batches_full_today)" "1"

echo "--- 不变量的落库核验 ---"
expect "★ 头数与关联行数一致（两者由同一条原子 SQL 维护，不会各写各的）" "$(get auto_count_vs_links)" "0"
expect "★ 头数与 pig_ids 长度一致" "$(get auto_json_vs_count)" "0"
expect "★ 一头猪不会落在两个批次（uk_pig 生效）" "$(get uk_pig_dupes)" "0"

echo "--- 被拦下的猪：一条批次都进不去 ---"
expect "★ 未盖章的 P4 不属于任何批次" "$(get p4_in_any_batch)" "0"
expect "★ 未检的 P5 不属于任何批次" "$(get p5_in_any_batch)" "0"
expect "★ 阳性的 P6 不属于任何批次" "$(get p6_in_any_batch)" "0"

echo "--- 猪状态：屠宰状态由「宰后检验」推进，与入不入批无关 ---"
expect "P1 状态已推进为已屠宰" "$(get p1_status)" "SLAUGHTERED"
expect "P2 状态已推进为已屠宰" "$(get p2_status)" "SLAUGHTERED"
expect "P3 状态已推进为已屠宰" "$(get p3_status)" "SLAUGHTERED"
expect "P4 走完宰后合格检验，状态亦为已屠宰（与是否入批无关）" "$(get p4_status_from_inspection)" "SLAUGHTERED"
expect "★ P5（未检，没走到宰后检验）状态未被推进为已屠宰" "$(get p5_status_not3)" "OTHER"
expect "★ P6（阳性）没被推进为已屠宰" "$(get p6_status_not3)" "OTHER"

echo "--- 上链留痕 ---"
expect "★ 自动批次有上链记录（新建 + 追加 + 人工封批共 3 条）" "$(get chain_auto)" "3"
expect "★ 其中一条是「新建批次」动作" "$(get chain_auto_new)" "1"
expect "★ 一条是「追加到既有批次」动作" "$(get chain_auto_append)" "1"
expect "人工建批同样上链 1 条" "$(get chain_manual)" "1"

echo
echo "===== 落库核验：${pass} 项通过，${fail} 项失败 ====="

echo
echo "=== 3) 清理本轮临时数据 ==="
q "
DELETE FROM db_ingest.ingest_staging WHERE id IN (${STAGING:-0});
DELETE FROM db_common.blockchain_record WHERE id > ${CHAIN_BEFORE};
-- 关联行要先删：批次删了而关联行还在，会变成一条「鬼占用」，以后 uk_pig 会拦着这头猪进任何新批次
DELETE FROM db_distribution.carcass_batch_pig WHERE batch_id IN (${BATCH_LIST:-0},${YESTERDAY_ID},${FULL_ID});
DELETE FROM db_distribution.carcass_batch WHERE id IN (${BATCH_LIST:-0},${YESTERDAY_ID},${FULL_ID});
DELETE FROM db_slaughter.carcass_stamp WHERE pig_id IN (${PIGS:-0});
DELETE FROM db_slaughter.slaughter_inspection WHERE pig_id IN (${PIGS:-0});
DELETE FROM db_slaughter.ractopamine_test WHERE pig_id IN (${PIGS:-0});
DELETE FROM db_slaughter.entry_inspection WHERE pig_id IN (${PIGS:-0});
DELETE FROM db_breeding.slaughter_apply WHERE pig_id IN (${PIGS:-0});
DELETE FROM db_breeding.quarantine_certificate WHERE pig_id IN (${PIGS:-0});
DELETE FROM db_breeding.pig_individual WHERE id IN (${PIGS:-0});
DELETE FROM db_ingest.source_fallback_unlock WHERE stage_code='BATCH' AND reason LIKE '%回归验证%';
" >/dev/null
echo "  已清理（临时猪 ${PIGS}，批次 ${BATCH_LIST}，昨日批次 ${YESTERDAY_ID}，接近上限批次 ${FULL_ID}）"

[ "$fail" -eq 0 ]
