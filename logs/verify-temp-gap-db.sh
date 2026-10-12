#!/usr/bin/env bash
# 温度断档检测回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-temp-gap-db.sh
#   1) 以 KEEP=1 跑 logs/verify-temp-gap.mjs（HTTP 层断言，且**不自行删数据**）
#   2) 在「数据还在库里」的时候逐条核验落库事实：
#        额定间隔固化（sample_interval_seconds）、间隔与断档标记（gap_seconds / is_gap）、
#        「断档标在断档之后的第一条上」、补传不判断档、越界值标异常且不被丢弃、
#        存量断档记录未被回填改动（条数不变 + 每条都自带 gap_seconds 证据）
#   3) 清理本次产生的计划 / 明细 / 运单 / 装载明细 / 温度记录 / 暂存行
#
# 断言口径：只认真实落库的事实（数值、标记、条数），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 温度断档检测落库核验 ==="

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")
BEFORE_PITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")
BEFORE_TRANSPORTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")
BEFORE_TITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")
BEFORE_TEMP=$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log;")
BEFORE_TEMP_DEV=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'TEMP-001';")
# 存量断档记录条数：演示/历史遗留的断档是真实业务数据（不清），这里只作为「有没有被改动」的标尺
BEFORE_GAPS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log WHERE is_gap = 1;")
echo "  水位：计划 ${BEFORE_PLANS} / 计划明细 ${BEFORE_PITEMS} / 运单 ${BEFORE_TRANSPORTS} / 装载明细 ${BEFORE_TITEMS} / 温度记录 ${BEFORE_TEMP}"
echo "  测试起点 ${START_TS}"

T_SCOPE="t.create_time >= '$START_TS'"
TL_SCOPE="l.create_time >= '$START_TS'"
STG_SCOPE="s.create_time >= '$START_TS'"

# ── 1) 跑 HTTP 回归（KEEP=1：不自行删数据，留给本脚本核验） ──
OUT=$(cd "$ROOT" && KEEP=1 "$NODE" logs/verify-temp-gap.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'
PLAN_IDS=$(echo "$OUT" | grep -o '# PLAN_IDS=.*' | sed 's/# PLAN_IDS=//')
TRANSPORT_IDS=$(echo "$OUT" | grep -o '# TRANSPORT_IDS=.*' | sed 's/# TRANSPORT_IDS=//')
HTTP_FAIL=$(echo "$OUT" | grep -o '通过 [0-9]* / 失败 [0-9]*' | head -1 | grep -o '失败 [0-9]*' | grep -o '[0-9]*')
echo

# ── 2) 落库断言 ──
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"

# 2.1 台账：探头的额定间隔
eq "★ 台账里 TEMP-001 声明了额定间隔 300 秒（「多久采一次」是探头的属性）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = 'TEMP-001' AND sample_interval_seconds = 300;")" "1"

# 2.2 温度记录本身
eq "★ 本次温度记录 5 条（4 条正常区间 + 1 条越界）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE;")" "5"
eq "★ 每条都固化了写入当时的额定间隔 300 秒（口径固化，将来改设备属性不影响历史判断）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.sample_interval_seconds <> 300;")" "0"
eq "★ 采集设备如实记为探头编号（温度无人工参与，不写人名）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.recorder <> 'TEMP-001';")" "0"
eq "本次温度记录都挂在本次新建的运单上（没有跑到存量运单上）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.cold_chain_transport t WHERE t.id = l.transport_id AND t.create_time >= '$START_TS');")" "0"

# 2.3 断档标记
eq "★ 恰好 1 条 gap_seconds 为空（首条，没有上一条可比）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.gap_seconds IS NULL;")" "1"
eq "★ 首条的 is_gap = 0（首条不该被标断档）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.gap_seconds IS NULL AND l.is_gap <> 0;")" "0"
eq "★ 有 1 条 gap_seconds = 300（正好一个额定间隔，不算断档）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.gap_seconds = 300 AND l.is_gap = 0;")" "1"
eq "★ 有 1 条 gap_seconds 为负（补传的历史数据）且 is_gap = 0 —— 补传不判断档，否则整段补传会被标成一片红" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.gap_seconds < 0 AND l.is_gap = 0;")" "1"
eq "★ 有 1 条 gap_seconds = 1200 且 is_gap = 1（1200 > 300×3，标在断档之后的**第一条**上）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.gap_seconds = 1200 AND l.is_gap = 1;")" "1"
eq "★ 有 1 条 gap_seconds = 2100 且 is_gap = 1（越界那条同时也在一次断档之后）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.gap_seconds = 2100 AND l.is_gap = 1;")" "1"
eq "★ 本次共 2 条被判断档" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.is_gap = 1;")" "2"

# 2.4 不变量：断档判定与额定间隔必须自洽（is_gap=1 ⇔ gap > interval×3）
eq "★ 标了断档但间隔其实没超（假阳）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.is_gap = 1 AND l.gap_seconds <= l.sample_interval_seconds * 3;")" "0"
eq "★ 间隔明显超了（> interval×3）却没标断档（漏报）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.gap_seconds > l.sample_interval_seconds * 3 AND l.is_gap <> 1;")" "0"

# 2.5 越界值：标异常且永远保留
eq "★ 越界温度恰好 1 条" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.is_abnormal = 1;")" "1"
eq "★ 越界那条记的是 5℃（超出 -18~0 的允许区间）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.is_abnormal = 1 AND l.temperature = 5;")" "1"
eq "★ 允许区间被如实落到记录上（min=-18 / max=0）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.temp_range_min = -18 AND l.temp_range_max = 0;")" "5"
eq "★ 有未标异常却超出允许区间的记录（漏判越界）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l WHERE $TL_SCOPE AND l.is_abnormal = 0 AND (l.temperature < l.temp_range_min OR l.temperature > l.temp_range_max);")" "0"

# 2.6 暂存行：缺失温度被拒绝、未发车运单被转人工
eq "本次探头暂存行 7 条（1 条拒绝 + 1 条转人工 + 5 条入账）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no = 'TEMP-001';")" "7"
eq "★ 温度值缺失那条判「拒绝」(3)：脏数据不得进冷链台账" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no = 'TEMP-001' AND s.status = 3;")" "1"
eq "★ 未发车的运单报温度那条转人工(1)：温度只属于在途监控" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no = 'TEMP-001' AND s.status = 1;")" "1"
eq "★ 其余 5 条已入账(2)" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging s WHERE $STG_SCOPE AND s.device_no = 'TEMP-001' AND s.status = 2;")" "5"

# 2.7 存量未被波及
#
# 这里不写「历史断档记录 = 0」：断档是真实发生过的事实，页面演示在演示运单上也会真的产生断档记录
# （那是业务数据，不清）。要防的是「存量被回填改动」——所以用两个更硬的口径：
#   ① 条数不变：回填会把老的正常记录标成断档（变多），篡改会把断档抹平（变少），两者都能抓住
#   ② 断档必须自带证据：is_gap=1 只能来自写入时算出的 gap_seconds，回填不会凭空造出这个证据
eq "本次新建运单数（T1 采集温度 / T2 用于未发车用例）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE;")" "2"
eq "全库温度记录数 = 基线 + 本次 5 条" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log;")" "$((BEFORE_TEMP + 5))"
eq "存量温度记录没有被回填改动（历史断档记录的条数不变：既没被新标，也没被抹掉）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log WHERE create_time < '$START_TS' AND is_gap = 1;")" "$BEFORE_GAPS"
eq "★ 每条断档记录都自带证据（gap_seconds 非空且确实超过额定间隔×3 —— 回填造不出这个证据）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log WHERE create_time < '$START_TS' AND is_gap = 1 AND (gap_seconds IS NULL OR gap_seconds <= COALESCE(sample_interval_seconds, 300) * 3);")" "0"

# ── 3) 清理 ──
echo
echo "  --- 清理本次回归数据 ---"
if [ -n "$TRANSPORT_IDS" ]; then
  MYSQL "DELETE FROM db_distribution.temperature_log WHERE transport_id IN ($TRANSPORT_IDS);"
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
MYSQL "DELETE FROM db_distribution.temperature_log WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.store_receipt WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.transport_stop WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.transport_item WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.cold_chain_transport WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE device_no IN ('LOADER-001','TEMP-001') AND create_time >= '$START_TS';"

eq "计划数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")" "$BEFORE_PLANS"
eq "计划明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")" "$BEFORE_PITEMS"
eq "运单数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")" "$BEFORE_TRANSPORTS"
eq "装载明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")" "$BEFORE_TITEMS"
eq "站点台账里的孤儿行（运单已不存在）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE t.id IS NULL;")" "0"
eq "温度记录数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log;")" "$BEFORE_TEMP"
eq "探头暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'TEMP-001';")" "$BEFORE_TEMP_DEV"

echo
echo "=== 结果：通过 $PASS / 失败 $FAIL ==="
[ "$FAIL" -eq 0 ]
