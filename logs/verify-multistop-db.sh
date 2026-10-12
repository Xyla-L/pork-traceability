#!/usr/bin/env bash
# 多站车次（一车多店）回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-multistop-db.sh
#   1) 以 KEEP=1 跑 logs/verify-multistop.mjs（HTTP 层断言，且**不自行删数据**）
#   2) 在「数据还在库里」的时候逐条核验落库事实：
#        站点三要素（store_id / store_name / seq）、按门店聚合的站点数、
#        运单 stop_count 与 destination、逐站签收（uk_transport_store）、
#        计划明细与装载明细一一对应、存量数据未被波及
#   3) 清理本次产生的计划 / 明细 / 运单 / 装载明细 / 签收 / 暂存行
#
# 断言口径：只认真实落库的事实（门店 id、序号、计数、状态），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 多站车次（一车多店）落库核验 ==="

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")
BEFORE_PITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")
BEFORE_TRANSPORTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")
BEFORE_TITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")
BEFORE_RECEIPTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt;")
BEFORE_LOAD_STAGING=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001';")
# 站点行程台账（32-transport-stop-arrive.sql）：本次只应新增 3 行（一趟车 3 站）
BEFORE_STOPS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop;")
BEFORE_STOPS_SIGNED=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status = 2;")
# 存量：装载明细里门店信息已回填（31-multi-stop-depart-temp.sql），这里记个水位
BEFORE_ITEM_NO_STORE=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item WHERE store_name IS NULL OR store_name = '';")
echo "  水位：计划 ${BEFORE_PLANS} / 计划明细 ${BEFORE_PITEMS} / 运单 ${BEFORE_TRANSPORTS} / 装载明细 ${BEFORE_TITEMS} / 签收 ${BEFORE_RECEIPTS}"
echo "  存量装载明细里门店为空的条数（历史事实，应保持不变）：${BEFORE_ITEM_NO_STORE}"
echo "  测试起点 ${START_TS}"

PLAN_SCOPE="p.create_time >= '$START_TS'"
ITEM_SCOPE="i.create_time >= '$START_TS'"
T_SCOPE="t.create_time >= '$START_TS'"
TI_SCOPE="ti.create_time >= '$START_TS'"
R_SCOPE="r.create_time >= '$START_TS'"

# ── 1) 跑 HTTP 回归（KEEP=1：不自行删数据，留给本脚本核验） ──
OUT=$(cd "$ROOT" && KEEP=1 "$NODE" logs/verify-multistop.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'
PLAN_IDS=$(echo "$OUT" | grep -o '# PLAN_IDS=.*' | sed 's/# PLAN_IDS=//')
TRANSPORT_IDS=$(echo "$OUT" | grep -o '# TRANSPORT_IDS=.*' | sed 's/# TRANSPORT_IDS=//')
HTTP_FAIL=$(echo "$OUT" | grep -o '通过 [0-9]* / 失败 [0-9]*' | head -1 | grep -o '失败 [0-9]*' | grep -o '[0-9]*')
echo

# ── 2) 落库断言 ──
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"

PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE;")
eq "本次新建计划数（主流程 3 张 + 车冲突 2 张）" "$PLANS" "5"
eq "计划号格式合规（DP+8位日期-3位流水）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.plan_no NOT REGEXP '^DP[0-9]{8}-[0-9]{3}\$';")" "0"
eq "计划号无重复" \
   "$(MYSQL "SELECT COUNT(*) - COUNT(DISTINCT p.plan_no) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE;")" "0"

# 2.1 本车主流程运单：途经 3 站
eq "本次运单数（只有主流程那一张；两次转人工都没建单）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE;")" "1"
eq "★ stop_count = 3（三家门店）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.stop_count <> 3;")" "0"
eq "★ destination 写成「途经 3 站：…」（多站不用第 1 站的门店名占位）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.destination NOT LIKE '途经 3 站：%';")" "0"
eq "本次运单来源 = DEVICE / LOADER-001" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND (t.source <> 'DEVICE' OR t.source_ref <> 'LOADER-001');")" "0"
eq "★ 司机来源 = 人工录入、非本人确认（装车口不采集司机：刷卡只发生在发车那一刻，见 verify-depart）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND (t.driver_source <> 'MANUAL' OR t.driver_confirmed <> 0);")" "0"
eq "★ 签满三站后运单转「已签收」(4)" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.status <> 4;")" "0"

# 2.1b 站点行程台账（站点级到达的落点）
eq "★ 本次站点台账 3 行（一趟车 3 站，一站一行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE;")" "3"
eq "★ 站点序号 1/2/3 各一次（与装载明细 seq 同源，不重不漏）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT s.transport_id, s.stop_no FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE GROUP BY s.transport_id, s.stop_no HAVING COUNT(*) = 1) x;")" "3"
eq "★ 有站点行缺门店（站点归属宁缺勿猜，但不该为空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND (s.store_id IS NULL OR s.store_name IS NULL OR s.store_name = '');")" "0"
eq "★ 三站全部已签收（status=2）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.status <> 2;")" "0"
eq "★ 有站点「已签收却没有到达时刻」（已签收 ⇒ 必有到达时间这个不变量）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.status = 2 AND s.arrive_time IS NULL;")" "0"
eq "★ 有站点挂的签收凭证不存在（receipt_id 悬空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.receipt_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM db_distribution.store_receipt r WHERE r.id = s.receipt_id);")" "0"
eq "★ 站点序号与装载明细 seq 一一对应（两处站点口径必须同源）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.transport_id = s.transport_id AND ti.seq = s.stop_no);")" "0"
eq "★ 站点行数与装载明细去重站点数一致（快照不能与权威打架）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND (SELECT COUNT(*) FROM db_distribution.transport_stop s WHERE s.transport_id = t.id) <> (SELECT COUNT(DISTINCT ti.seq) FROM db_distribution.transport_item ti WHERE ti.transport_id = t.id);")" "0"
eq "★ 每张运单都有站点台账（新运单必建，存量已回填）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE NOT EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.transport_id = t.id);")" "0"

# 2.2 装载明细的站点三要素
eq "★ 本次装载明细 3 条" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE;")" "3"
eq "★ 有明细缺门店 id（站点归属是自动化的关键字段，宁缺勿猜）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE AND ti.store_id IS NULL;")" "0"
eq "★ 有明细缺门店名（展示快照不能为空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE AND (ti.store_name IS NULL OR ti.store_name = '');")" "0"
eq "★ 有明细缺站点序号 seq" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE AND ti.seq IS NULL;")" "0"
eq "★ 站点序号 1/2/3 各一次（按扫码顺序分配，不重不漏）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE AND ti.seq IN (1,2,3);")" "3"
eq "★ 每个 seq 只对应一家门店（一个站点不含两家店）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT ti.transport_id, ti.seq FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE GROUP BY ti.transport_id, ti.seq HAVING COUNT(DISTINCT ti.store_id) > 1) x;")" "0"
eq "★ 同一运单里同一家门店出现在多个 seq（站点重复）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT ti.transport_id, ti.store_id FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE GROUP BY ti.transport_id, ti.store_id HAVING COUNT(DISTINCT ti.seq) > 1) x;")" "0"
eq "★ stop_count 与装载明细去重门店数一致（快照不能与权威打架）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.stop_count <> (SELECT COUNT(DISTINCT ti.store_id) FROM db_distribution.transport_item ti WHERE ti.transport_id = t.id AND ti.store_id IS NOT NULL);")" "0"

# 2.3 装载明细与计划明细一一对应
eq "★ 装载明细的批次与计划明细指向的批次不是同一集合" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.delivery_plan_item i WHERE i.split_batch_id = ti.split_batch_id AND i.transport_id = ti.transport_id);")" "0"
eq "★ 计划明细指向的批次没有对应的装载明细" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND i.shipped = 1 AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.split_batch_id = i.split_batch_id AND ti.transport_id = i.transport_id);")" "0"
eq "★ 装载明细里的门店与计划明细所属计划的门店不一致（发货去向被串了）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.delivery_plan_item i ON i.split_batch_id = ti.split_batch_id AND i.transport_id = ti.transport_id JOIN db_distribution.delivery_plan p ON p.id = i.plan_id WHERE ti.create_time >= '$START_TS' AND (ti.store_id <> p.store_id OR ti.store_name <> p.store_name);")" "0"

# 2.4 逐站签收：uk_transport_store（放开的是站点数，不是签收次数）
eq "★ 本次签收记录 3 条（三家门店各一条）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r WHERE $R_SCOPE;")" "3"
eq "★ (运单, 门店) 有重复签收（唯一键该拦住）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT r.transport_id, r.store_id FROM db_distribution.store_receipt r GROUP BY r.transport_id, r.store_id HAVING COUNT(*) > 1) x;")" "0"
eq "★ 签收的门店不是本运单装载明细上的门店（签错站）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r WHERE $R_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.transport_id = r.transport_id AND ti.store_id = r.store_id);")" "0"
eq "★ 签收记录缺签收人（货权转移必须落到具体人）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r WHERE $R_SCOPE AND (r.receiver IS NULL OR r.receiver = '');")" "0"
eq "全库签收记录条数（历史事实，只允许增加本次的 3 条）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt;")" "$((BEFORE_RECEIPTS + 3))"

# 2.5 存量数据未被波及
eq "存量装载明细里门店为空的条数保持不变（历史事实，不篡改）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item WHERE store_name IS NULL OR store_name = '';")" "$BEFORE_ITEM_NO_STORE"
eq "装载明细指向不存在的运单（孤儿行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti LEFT JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE t.id IS NULL;")" "0"
eq "每张运单都有装载明细（含存量回填）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.transport_id = t.id);")" "0"

# ── 3) 清理 ──
echo
echo "  --- 清理本次回归数据 ---"
if [ -n "$TRANSPORT_IDS" ]; then
  MYSQL "DELETE FROM db_distribution.store_receipt WHERE transport_id IN ($TRANSPORT_IDS);"
  MYSQL "DELETE FROM db_distribution.transport_stop WHERE transport_id IN ($TRANSPORT_IDS);"
  MYSQL "DELETE FROM db_distribution.transport_item WHERE transport_id IN ($TRANSPORT_IDS);"
  MYSQL "DELETE FROM db_distribution.cold_chain_transport WHERE id IN ($TRANSPORT_IDS);"
fi
if [ -n "$PLAN_IDS" ]; then
  MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE plan_id IN ($PLAN_IDS);"
  MYSQL "DELETE FROM db_distribution.delivery_plan WHERE id IN ($PLAN_IDS);"
fi
# 按时间收尾：不依赖 mjs 回传的 id 集合
MYSQL "DELETE FROM db_distribution.store_receipt WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.transport_stop WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.transport_item WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.cold_chain_transport WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001' AND create_time >= '$START_TS';"

eq "计划数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")" "$BEFORE_PLANS"
eq "计划明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")" "$BEFORE_PITEMS"
eq "运单数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")" "$BEFORE_TRANSPORTS"
eq "装载明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")" "$BEFORE_TITEMS"
eq "签收记录数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt;")" "$BEFORE_RECEIPTS"
eq "站点台账数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop;")" "$BEFORE_STOPS"
eq "已签收站点数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status = 2;")" "$BEFORE_STOPS_SIGNED"
eq "站点台账里的孤儿行（运单已不存在）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE t.id IS NULL;")" "0"
eq "装车口暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001';")" "$BEFORE_LOAD_STAGING"

echo
echo "=== 结果：通过 $PASS / 失败 $FAIL ==="
[ "$FAIL" -eq 0 ]
