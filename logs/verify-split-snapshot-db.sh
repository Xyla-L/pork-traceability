#!/usr/bin/env bash
# 分割车间监控快照回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-split-snapshot-db.sh
#   1) 登记一台**一次性测试相机**（CAM-SPLIT-TEST），再跑 logs/verify-split-snapshot.mjs
#      （HTTP 层断言：两段式上报 / 凭证防伪 / 结构性拒绝 / 滚动窗口）
#   2) 在「数据还在库里」的时候逐条核验落库事实：
#        生产相机台账（通道 / 采样间隔 / 启用）、测试相机的快照来源标记、拍摄时刻、
#        暂存行的状态分布（几条入账 / 几条拒绝）、滚动窗口是否收敛到上限、
#        以及「每张快照都指向一个真实存在的文件」（无孤儿引用）
#   3) 清理本次产生的快照行、暂存行、测试相机台账与文件，并核对数目回到基线
#
# ⚠️ 为什么写入必须打在一台一次性相机上：
#   滚动窗口 pruneOldSnapshots 是**按设备**生效的。往生产相机 CAM-SPLIT-001 写，
#   一旦写满上限就会挤掉它最老的那张 —— 而那张很可能来自界面演示（= 真实业务数据，
#   按项目纪律不能删）。回归脚本没有删别人数据的权力，所以这里把生产相机降级为
#   「只读台账检查」，所有写入都在用完即删的 CAM-SPLIT-TEST 上完成。
#   这样本套回归对「相机里有没有演示数据」不再敏感，可无脑重复跑。
#
# 断言口径：只认真实落库的事实（条数、标记、外键存在性），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }
DEV=CAM-SPLIT-001          # 生产/演示相机：只读检查台账，不写
TDEV=CAM-SPLIT-TEST        # 一次性测试相机：所有写入都打在这里，跑完连台账一起删
TKEY=TEST-SPLIT-SNAPSHOT-KEY
RETENTION=20

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 分割车间监控快照落库核验 ==="

START_TS=$(MYSQL "SELECT NOW();" | head -1)

# ── 0) 预检与测试相机登记（幂等：上次异常退出留下的残留会被复用）──
BEFORE_SNAP=$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot;")
BEFORE_STG_DEV=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = '$DEV';")
BEFORE_DEV=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device;")
echo "  水位：快照 ${BEFORE_SNAP} / 生产相机暂存行 ${BEFORE_STG_DEV} / 设备台账 ${BEFORE_DEV}"
echo "  测试起点 ${START_TS}"

eq "★ 相机 $DEV 已登记（监控相机的台账是永久配置，不随回归增删）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = '$DEV';")" "1"
eq "★ 相机 $DEV 的相机名承接了「分割车间监控」这条专用通道" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = '$DEV' AND channel = 'SPLIT_SNAPSHOT';")" "1"
eq "★ 相机 $DEV 声明了 60 秒的拍摄间隔（「多久拍一张」是相机的属性）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = '$DEV' AND sample_interval_seconds = 60;")" "1"
eq "★ 相机 $DEV 是启用状态" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = '$DEV' AND status = 1;")" "1"
eq "★ 相机 $DEV 不绑车牌（固定点位设备，vehicle_no 应为空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = '$DEV' AND (vehicle_no IS NULL OR vehicle_no = '');")" "1"

MYSQL "INSERT INTO db_ingest.ingest_device (device_no, device_name, channel, location, sample_interval_seconds, secret_key, status, remark)
       VALUES ('$TDEV', '回归用一次性相机（跑完即删）', 'SPLIT_SNAPSHOT', '分割车间（测试）', $RETENTION, '$TKEY', 1, 'verify-split-snapshot-db.sh 自动登记')
       ON DUPLICATE KEY UPDATE channel='SPLIT_SNAPSHOT', sample_interval_seconds=$RETENTION, secret_key='$TKEY', status=1;" >/dev/null
eq "★ 一次性测试相机 $TDEV 已登记（写入不再碰生产相机）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = '$TDEV' AND channel = 'SPLIT_SNAPSHOT' AND status = 1;")" "1"
# 测试相机上是上一轮异常退出留下的行，本脚本有权清掉（它本来就是一次性的）
MYSQL "DELETE FROM db_distribution.workshop_snapshot WHERE device_no = '$TDEV';"
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE device_no = '$TDEV';"
eq "★ 测试相机起跑前是空的（滚动窗口节才能执行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot WHERE device_no = '$TDEV';")" "0"

# ── 1) 跑 HTTP 回归（不自行删数据，留给本脚本核验与清理）──
OUT=$(cd "$ROOT" && SNAPSHOT_DEVICE="$TDEV" SNAPSHOT_RETENTION="$RETENTION" "$NODE" logs/verify-split-snapshot.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'
HTTP_FAIL=$(echo "$OUT" | grep -o '失败 [0-9]*' | head -1 | grep -o '[0-9]*')
WROTE_SKIPPED=$(echo "$OUT" | grep -c '#SKIPPED 1')
echo

# ── 2) 落库断言 ──
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"
eq "HTTP 层没有跳过写入节（测试相机是空的，应完整执行）" "$WROTE_SKIPPED" "0"

# 2.1 快照行本身（认设备号，测试相机的行全由本次产生）
eq "★ 本次入账的快照来源标记是「设备采集」，来源设备是相机编号" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot s WHERE s.device_no = '$TDEV' AND (s.source <> 'DEVICE' OR s.source_ref <> '$TDEV');")" "0"
eq "★ 每条快照都有拍摄时刻（设备侧时间，非空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot s WHERE s.device_no = '$TDEV' AND s.captured_time IS NULL;")" "0"
eq "★ 每条快照都有 file_id（照片凭证）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot s WHERE s.device_no = '$TDEV' AND (s.file_id IS NULL OR s.file_id = '');")" "0"
eq "★ 每条快照的 file_id 都指向一个真实存在的文件（没有编造凭证的孤儿引用）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot s WHERE s.device_no = '$TDEV' AND NOT EXISTS (SELECT 1 FROM db_common.file_metadata m WHERE m.file_id = s.file_id);")" "0"

# 2.2 暂存行状态分布：3 条拒绝（伪造 fileId / 缺照片 / 编造批次绑定），其余入账
eq "★ 测试相机暂存行里恰有 3 条被判「已拒绝」（伪造 fileId + 缺照片 + 编造批次绑定）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging t WHERE t.device_no = '$TDEV' AND t.status = 3;")" "3"
eq "★ 没有被误判成「待人工处理」的行（照片空缺/批次报错都不需要人插手）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging t WHERE t.device_no = '$TDEV' AND t.status = 1;")" "0"
eq "★ 暂存行的目标表是 workshop_snapshot" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging t WHERE t.device_no = '$TDEV' AND t.target_table <> 'workshop_snapshot';")" "0"

# 2.2b 绑定列（2026-10-12）：照片要归属到「这一次分割」，且归属不能是编的
eq "★ 只有带 batchNo 的那 1 张绑定了批次（不带的不硬塞归属）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot WHERE device_no = '$TDEV' AND batch_no IS NOT NULL;")" "1"
eq "★ 全库没有「绑定了系统中不存在的批次」的快照（编造绑定进不了库）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot s WHERE s.batch_no IS NOT NULL AND NOT EXISTS (SELECT 1 FROM db_distribution.carcass_batch b WHERE b.batch_no = s.batch_no);")" "0"
eq "★ 绑定了批次的那张能反查到分割作业（作业号由服务端认，不靠设备报）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot s JOIN db_distribution.split_job j ON j.id = s.job_id WHERE s.device_no = '$TDEV' AND s.batch_no IS NOT NULL;")" "1"

# 2.3 滚动窗口：总数收敛到上限，且「最老的已被淘汰」
eq "★ 突发上报后快照总数收敛到上限 $RETENTION（滚动窗口生效，不是无限增长）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot WHERE device_no = '$TDEV';")" "$RETENTION"

# ── 3) 清理 ──
echo
echo "  --- 清理本次回归数据 ---"
FILE_IDS=$(MYSQL "SELECT GROUP_CONCAT(DISTINCT s.file_id) FROM db_distribution.workshop_snapshot s WHERE s.device_no = '$TDEV';")
MYSQL "DELETE FROM db_distribution.workshop_snapshot WHERE device_no = '$TDEV';"
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE device_no = '$TDEV';"
MYSQL "DELETE FROM db_ingest.ingest_device WHERE device_no = '$TDEV';"

# 删文件（照片是本次测试上传的合成图）：文件可能已被滚动淘汰删掉，404 忽略
if [ -n "${FILE_IDS:-}" ] && [ "$FILE_IDS" != "NULL" ]; then
  TOKEN=$(curl -s -X POST http://localhost/api/v1/auth/login -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"123456"}' \
    | "$NODE" -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{try{const j=JSON.parse(s);console.log(j?.data?.token||j?.data?.accessToken||'')}catch{console.log('')}})" 2>/dev/null)
  IFS=',' read -ra IDS <<< "$FILE_IDS"
  for fid in "${IDS[@]}"; do
    [ -n "$fid" ] && curl -s -o /dev/null -X DELETE "http://localhost/api/v1/file/$fid" -H "Authorization: Bearer $TOKEN" || true
  done
fi

eq "快照表行数回到基线（写入只落在测试相机上，生产相机纹丝不动）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.workshop_snapshot;")" "$BEFORE_SNAP"
eq "生产相机暂存行数回到基线（本次一行都没往它头上写）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = '$DEV';")" "$BEFORE_STG_DEV"
eq "设备台账行数回到基线（测试相机已注销）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device;")" "$BEFORE_DEV"
eq "测试相机的快照 / 暂存 / 台账均已注销" \
   "$(MYSQL "SELECT (SELECT COUNT(*) FROM db_distribution.workshop_snapshot WHERE device_no='$TDEV') + (SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no='$TDEV') + (SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no='$TDEV');")" "0"

echo
echo "=== 结果：通过 $PASS / 失败 $FAIL ==="
[ "$FAIL" -eq 0 ]
