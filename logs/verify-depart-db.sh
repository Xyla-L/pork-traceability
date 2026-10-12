#!/usr/bin/env bash
# 刷卡发车（DEPART 通道）回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-depart-db.sh
#   1) 以 KEEP=1 跑 logs/verify-depart.mjs（HTTP 层断言，且**不自行删数据**）
#   2) 在「数据还在库里」的时候逐条核验落库事实：
#        发车推进（status 1→2 + depart_time）、司机来源提升为 CARD/本人确认、
#        说不清的那几次一张运单都没发走、设备台账的车牌是结构化绑定、
#        暂存回执的结论分布（入账 1 / 转人工 7，**一条拒绝都没有**）、
#        **演示数据（京A·12345 上的界面演示运单）未被本次触碰**
#   3) 清理本次产生的计划 / 明细 / 运单 / 装载明细 / 暂存行
#
# 断言口径：只认真实落库的事实（状态、时间戳、来源标记、计数），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 刷卡发车（DEPART）落库核验 ==="

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")
BEFORE_PITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")
BEFORE_TRANSPORTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")
BEFORE_TITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")
BEFORE_LOAD=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001';")
BEFORE_DEPART_A=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'DEPART-001';")
BEFORE_DEPART_B=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'DEPART-003';")
# 演示数据水位：京A·12345 上的运单是界面演示建出来的真实业务数据，本回归不得改动它
BEFORE_DEMO=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE vehicle_no = '京A·12345';")
BEFORE_DEMO_DEPARTED=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE vehicle_no = '京A·12345' AND depart_time IS NOT NULL;")
echo "  水位：计划 ${BEFORE_PLANS} / 计划明细 ${BEFORE_PITEMS} / 运单 ${BEFORE_TRANSPORTS} / 装载明细 ${BEFORE_TITEMS}"
echo "  暂存：装车口 ${BEFORE_LOAD} / 发车终端A ${BEFORE_DEPART_A} / 发车终端B ${BEFORE_DEPART_B}"
echo "  演示数据：京A·12345 上的运单 ${BEFORE_DEMO} 张（其中已发车 ${BEFORE_DEMO_DEPARTED} 张）"
echo "  测试起点 ${START_TS}"

T_SCOPE="t.create_time >= '$START_TS'"
STG_SCOPE="s.create_time >= '$START_TS'"

# ── 1) 跑 HTTP 回归（KEEP=1：不自行删数据，留给本脚本核验） ──
OUT=$(cd "$ROOT" && KEEP=1 "$NODE" logs/verify-depart.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'
PLAN_IDS=$(echo "$OUT" | grep -o '# PLAN_IDS=.*' | sed 's/# PLAN_IDS=//')
TRANSPORT_IDS=$(echo "$OUT" | grep -o '# TRANSPORT_IDS=.*' | sed 's/# TRANSPORT_IDS=//')
HTTP_FAIL=$(echo "$OUT" | grep -o '通过 [0-9]* / 失败 [0-9]*' | head -1 | grep -o '失败 [0-9]*' | grep -o '[0-9]*')
echo

# ── 2) 落库断言 ──
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"

# 2.1 设备台账：车牌是结构化绑定（不靠设备名里的字符串猜）
eq "★ 设备台账 DEPART 通道 2 台" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE channel = 'DEPART';")" "2"
eq "★ DEPART 设备缺 vehicle_no（车牌必须可查，否则发车设备化就失去了唯一凭据）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE channel = 'DEPART' AND (vehicle_no IS NULL OR vehicle_no = '');")" "0"
eq "★ DEPART 设备的车牌有重复（一台车只该有一个发车终端）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT vehicle_no FROM db_ingest.ingest_device WHERE channel = 'DEPART' GROUP BY vehicle_no HAVING COUNT(*) > 1) x;")" "0"
eq "★ DEPART-001 / DEPART-003 的车牌正是 京A·12345 / 京A·12346" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = 'DEPART-001' AND vehicle_no = '京A·12345';")" "1"
eq "★ DEPART-003 绑定京A·12346" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = 'DEPART-003' AND vehicle_no = '京A·12346';")" "1"

# 2.2 暂存回执的结论分布：**一条拒绝都不该有**
eq "本次终端A（京A·12345）暂存行 6 条" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no = 'DEPART-001';")" "6"
eq "本次终端B（京A·12346）暂存行 2 条" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no = 'DEPART-003';")" "2"
eq "★ 已入账(2) 只有 1 条（唯一那张待发车运单被发走）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no IN ('DEPART-001','DEPART-003') AND s.status = 2;")" "1"
eq "★ 转人工(1) 7 条（没待发车 ×2 / 卡未备案 / 推定 / 上错车 / 重复 / 说不清）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no IN ('DEPART-001','DEPART-003') AND s.status = 1;")" "7"
eq "★ 被判定为「拒绝」(3) 的条数必须为 0 —— 本通道的失败是「条件没到位」而不是「数据不成立」，判成拒绝会让外部系统把这条发车记录删掉" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no IN ('DEPART-001','DEPART-003') AND s.status = 3;")" "0"
eq "本次暂存行都挂在 DEPART 通道上（通道归属不能串）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no IN ('DEPART-001','DEPART-003') AND s.channel <> 'DEPART';")" "0"

# 2.3 运单：3 张，其中 1 张被发走、2 张仍停在待发车
eq "本次新建运单数（t1/t2 由装车口建、t3 人工建 —— 装车口已不会为同一车牌裂出第二张）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE;")" "3"
eq "★ 被发走的 1 张（status=2）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.status = 2;")" "1"
eq "★ 说不清的那次没有误发（仍有 2 张停在待发车 status=1）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.status = 1;")" "2"
eq "★ 发走的运单写了现场发车时刻" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.status = 2 AND t.depart_time IS NULL;")" "0"
eq "★ 发走的运单司机来源提升为 CARD 且本人确认（「本人刷了卡」这件事只有刷卡能证明）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.status = 2 AND (t.driver_source <> 'CARD' OR t.driver_confirmed <> 1);")" "0"
eq "★ 没发走的运单没有 depart_time（状态与时间戳必须同进同退）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.status = 1 AND t.depart_time IS NOT NULL;")" "0"
eq "★ 装车口没刷卡时记的是「人工录入、非本人确认」（confirmed 必须为 0）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.status = 1 AND (t.driver_source <> 'MANUAL' OR t.driver_confirmed <> 0);")" "0"
eq "本次运单都仍有装载明细（发车不改变装载事实）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.transport_id = t.id);")" "0"

# 2.4 演示数据未被触碰（京A·12345 上的运单是界面演示建出的真实业务数据）
eq "★ 本次没有新建任何 京A·12345 的运单（测试用的是专用车牌）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE create_time >= '$START_TS' AND vehicle_no = '京A·12345';")" "0"
eq "★ 本次没有把演示运单发走（京A·12345 上标了 depart_time 的运单数保持不变）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE vehicle_no = '京A·12345' AND depart_time >= '$START_TS';")" "0"
eq "★ 演示运单状态数保持不变" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE vehicle_no = '京A·12345';")" "$BEFORE_DEMO"

# 2.5 存量未被波及
eq "全库运单数 = 基线 + 本次 3 张" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")" "$((BEFORE_TRANSPORTS + 3))"
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
# 按时间收尾：不依赖 mjs 回传的 id 集合（车牌过滤保证不会误删演示运单）
MYSQL "DELETE FROM db_distribution.store_receipt WHERE transport_id IN (SELECT id FROM db_distribution.cold_chain_transport WHERE create_time >= '$START_TS' AND vehicle_no = '京A·T0001');"
MYSQL "DELETE FROM db_distribution.transport_stop WHERE transport_id IN (SELECT id FROM db_distribution.cold_chain_transport WHERE create_time >= '$START_TS' AND vehicle_no = '京A·T0001');"
MYSQL "DELETE FROM db_distribution.transport_item WHERE transport_id IN (SELECT id FROM db_distribution.cold_chain_transport WHERE create_time >= '$START_TS' AND vehicle_no = '京A·T0001');"
MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE plan_id IN (SELECT id FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS');"
MYSQL "DELETE FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.cold_chain_transport WHERE create_time >= '$START_TS' AND vehicle_no = '京A·T0001';"
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE device_no IN ('LOADER-001','DEPART-001','DEPART-003') AND create_time >= '$START_TS';"

eq "计划数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")" "$BEFORE_PLANS"
eq "计划明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")" "$BEFORE_PITEMS"
eq "运单数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")" "$BEFORE_TRANSPORTS"
eq "装载明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")" "$BEFORE_TITEMS"
eq "站点台账里的孤儿行（运单已不存在）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE t.id IS NULL;")" "0"
eq "装车口暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001';")" "$BEFORE_LOAD"
eq "发车终端A暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'DEPART-001';")" "$BEFORE_DEPART_A"
eq "发车终端B暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'DEPART-003';")" "$BEFORE_DEPART_B"

echo
echo "=== 结果：通过 $PASS / 失败 $FAIL ==="
[ "$FAIL" -eq 0 ]
