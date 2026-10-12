#!/usr/bin/env bash
# 站点级到达（ARRIVE 通道）回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-arrive-stop-db.sh
#   1) 以 KEEP=1 跑 logs/verify-arrive-stop.mjs（HTTP 层断言，且**不自行删数据**）
#   2) 在「数据还在库里」的时候逐条核验落库事实：
#        站点行程台账（stop_no / store_id / status / arrive_time / arrive_source /
#        arrive_source_ref / **arrive_driver_name + arrive_driver_source（谁确认的到达）** /
#        receipt_id）、运单状态由站点汇总推导、
#        签收前置下沉到站点（删签收后该站退回「已到达」）、
#        站点与装载明细两处口径同源、**ARRIVE 报文里没有门店字段**（报站由服务端推）、
#        **一台车载终端承担发车 + 到站两个通道**（合并：两个通道的暂存行挂在同一个设备号上）、
#        存量数据未被波及
#   3) 清理本次产生的计划 / 明细 / 运单 / 装载明细 / 站点 / 签收 / 暂存行
#
# 断言口径：只认真实落库的事实（序号、状态、计数、来源、时刻、确认人），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 站点级到达（ARRIVE 通道）落库核验 ==="

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")
BEFORE_PITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")
BEFORE_TRANSPORTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")
BEFORE_TITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")
BEFORE_RECEIPTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt;")
BEFORE_STOPS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop;")
BEFORE_STOPS_SIGNED=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status = 2;")
# 车载终端一台设备两个通道（2026-10-11 合并），所以基线必须按「设备 + 通道」分开记，
# 否则 DEPART 与 ARRIVE 的暂存行会混在一起算。
BEFORE_STAGING_ARRIVE=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'ARRIVE';")
BEFORE_STAGING_LOAD=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001';")
BEFORE_STAGING_DEPART=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'DEPART';")
BEFORE_ITEM_NO_STORE=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item WHERE store_name IS NULL OR store_name = '';")
echo "  水位：计划 ${BEFORE_PLANS} / 计划明细 ${BEFORE_PITEMS} / 运单 ${BEFORE_TRANSPORTS} / 装载明细 ${BEFORE_TITEMS} / 签收 ${BEFORE_RECEIPTS} / 站点 ${BEFORE_STOPS}（已签收 ${BEFORE_STOPS_SIGNED}）"
echo "  存量装载明细里门店为空的条数（历史事实，应保持不变）：${BEFORE_ITEM_NO_STORE}"
echo "  暂存行：车载终端 ARRIVE-001（到达 ${BEFORE_STAGING_ARRIVE} / 发车 ${BEFORE_STAGING_DEPART}）/ LOADER-001 ${BEFORE_STAGING_LOAD}"
echo "  测试起点 ${START_TS}"

T_SCOPE="t.create_time >= '$START_TS'"
TI_SCOPE="ti.create_time >= '$START_TS'"
R_SCOPE="r.create_time >= '$START_TS'"

# ── 1) 跑 HTTP 回归（KEEP=1：不自行删数据，留给本脚本核验） ──
OUT=$(cd "$ROOT" && KEEP=1 "$NODE" logs/verify-arrive-stop.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'
PLAN_IDS=$(echo "$OUT" | grep -o '# PLAN_IDS=.*' | sed 's/# PLAN_IDS=//')
TRANSPORT_IDS=$(echo "$OUT" | grep -o '# TRANSPORT_IDS=.*' | sed 's/# TRANSPORT_IDS=//')
HTTP_FAIL=$(echo "$OUT" | grep -o '通过 [0-9]* / 失败 [0-9]*' | head -1 | grep -o '失败 [0-9]*' | grep -o '[0-9]*')
echo

# ── 2) 落库断言 ──
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"

# 2.1 本次新建的计划（三家门店各 1 张）+ 唯一一张运单
eq "本次新建计划数（三家门店各 1 张）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE p.create_time >= '$START_TS';")" "3"
eq "计划号格式合规（DP+8位日期-3位流水）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE p.create_time >= '$START_TS' AND p.plan_no NOT REGEXP '^DP[0-9]{8}-[0-9]{3}\$';")" "0"
eq "本次运单数（红线用例都没建单）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE;")" "1"
eq "★ 本次运单挂在专用车牌 京A·T0002 上（不与演示车混用）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.vehicle_no <> '京A·T0002';")" "0"
eq "★ stop_count = 3（三家门店）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.stop_count <> 3;")" "0"
eq "本次运单来源 = DEVICE / LOADER-001" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND (t.source <> 'DEVICE' OR t.source_ref <> 'LOADER-001');")" "0"
# 司机口径（2026-10-10 收敛）：装车口没有读卡器 → 建单时司机为空 → 上车刷卡才把责任落到人
eq "★ 本次运单的司机由「上车刷卡」写进运单（张师傅 / CARD / 本人确认）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND (t.driver_name <> '张师傅' OR t.driver_source <> 'CARD' OR t.driver_confirmed <> 1);")" "0"
eq "★ 本次运单有发车时刻（由车载终端那一次刷卡写下来，不是人手点的）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.depart_time IS NULL;")" "0"

# 2.2 站点行程台账 —— 本轮唯一真正的新事实
eq "★ 本次站点台账 3 行（一趟车 3 站，一站一行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE;")" "3"
eq "★ 站点序号 1/2/3 各一次（与装载明细 seq 同源，不重不漏）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT s.transport_id, s.stop_no FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE GROUP BY s.transport_id, s.stop_no HAVING COUNT(*) = 1) x;")" "3"
eq "★ 有站点行缺门店（站点归属宁缺勿猜，但不该为空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND (s.store_id IS NULL OR s.store_name IS NULL OR s.store_name = '');")" "0"
eq "★ 三站都有到达时刻（逐站到达是事实，三站都报过）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.arrive_time IS NULL;")" "0"
eq "★ 到达来源一律 DEVICE（设备确认，不是人手点出来的）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.arrive_source <> 'DEVICE';")" "0"
eq "★ 到达上报编号落到站点上（ARRIVE-001，追责能回溯是哪台设备确认的）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.arrive_source_ref <> 'ARRIVE-001';")" "0"
# 到达必须由司机本人刷卡确认（2026-10-11）：「谁把货送到这一站」要落到人，且落在**站点**上
eq "★ 三站的到达确认人都是刷卡换出的「张师傅 / 刷卡确认」（每站独立记，不挤在运单主表）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND (s.arrive_driver_name <> '张师傅' OR s.arrive_driver_source <> 'CARD');")" "0"
eq "★ 全库：设备确认的到达必须带确认人（DEVICE 到达却没有确认人 = 没人认账）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE arrive_source = 'DEVICE' AND status >= 1 AND (arrive_driver_name IS NULL OR arrive_driver_name = '');")" "0"
eq "★ 全库：有确认人却没记来源（人名非空 ≠ 本人确认过，来源必须一起记）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE (arrive_driver_name IS NULL OR arrive_driver_name = '') AND arrive_driver_source IS NOT NULL;")" "0"
eq "★ 第 1、2 站已签收（status=2），第 3 站的签收被删掉后退回「已到达」（status=1）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.status <> CASE s.stop_no WHEN 3 THEN 1 ELSE 2 END;")" "0"
eq "★ 被删签收的那一站摘掉了凭证（receipt_id 归 NULL）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.stop_no = 3 AND s.receipt_id IS NOT NULL;")" "0"
eq "★ 删签收不会把该站的「到达」一并抹掉（到达与签收是两件事）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND s.stop_no = 3 AND s.arrive_time IS NULL;")" "0"
eq "★ 站点序号与装载明细 seq 一一对应（两处站点口径必须同源）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE $T_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.transport_id = s.transport_id AND ti.seq = s.stop_no);")" "0"
eq "★ 站点行数与装载明细去重站点数一致（快照不能与权威打架）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND (SELECT COUNT(*) FROM db_distribution.transport_stop s WHERE s.transport_id = t.id) <> (SELECT COUNT(DISTINCT ti.seq) FROM db_distribution.transport_item ti WHERE ti.transport_id = t.id);")" "0"

# 2.3 运单状态由站点汇总推导（不另存布尔值）
eq "★ 本次运单状态 = 站点汇总推导（1 站未签收 → 待签收(3)）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.status <> 3;")" "0"
eq "★ 全库运单状态与站点台账汇总一致（在途/待签收/已签收都必须由站点推出来）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE t.status IN (2,3,4) AND EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.transport_id = t.id) AND t.status <> CASE WHEN (SELECT COUNT(*) FROM db_distribution.transport_stop s WHERE s.transport_id = t.id AND s.status < 2) = 0 THEN 4 WHEN (SELECT COUNT(*) FROM db_distribution.transport_stop s WHERE s.transport_id = t.id AND s.status < 1) = 0 THEN 3 ELSE 2 END;")" "0"

# 2.4 站点台账的全局不变量（对全库成立，不只本次）
eq "★ 已签收的站点必有到达时刻（没到过就签收是编的）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status = 2 AND arrive_time IS NULL;")" "0"
eq "★ 已签收的站点必有签收凭证（货权转移要留凭据）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status = 2 AND receipt_id IS NULL;")" "0"
eq "★ 未到达的站点不得有到达时刻（空白的行程不能装出「已到达」）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status = 0 AND arrive_time IS NOT NULL;")" "0"
eq "★ 站点挂的签收凭证不存在（receipt_id 悬空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s WHERE s.receipt_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM db_distribution.store_receipt r WHERE r.id = s.receipt_id);")" "0"
eq "★ 站点台账里的孤儿行（运单已不存在）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE t.id IS NULL;")" "0"
eq "★ 每张运单都有站点台账（新运单必建，存量已回填）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE NOT EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.transport_id = t.id);")" "0"
eq "★ 同一运单里同一 stop_no 只出现一次（唯一键 (transport_id, stop_no)）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT s.transport_id, s.stop_no FROM db_distribution.transport_stop s GROUP BY s.transport_id, s.stop_no HAVING COUNT(*) > 1) x;")" "0"

# 2.5 装载明细：站点归属三要素
eq "★ 本次装载明细 3 条" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE;")" "3"
eq "★ 有明细缺门店 id（站点归属是自动化的关键字段，宁缺勿猜）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE $T_SCOPE AND ti.store_id IS NULL;")" "0"
eq "★ 站点台账的门店与同名 stop_no 的装载明细门店一致（两处不能各说各话）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s JOIN db_distribution.transport_item ti ON ti.transport_id = s.transport_id AND ti.seq = s.stop_no WHERE s.transport_id IN (SELECT t.id FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE) AND (s.store_id <> ti.store_id OR s.store_name <> ti.store_name);")" "0"

# 2.6 签收记录（本次只剩 2 条：第 3 条被删了）
eq "★ 本次签收记录 2 条（第 3 站的已被删除）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r WHERE $R_SCOPE;")" "2"
eq "★ 签收记录缺签收人（货权转移必须落到具体人）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r WHERE $R_SCOPE AND (r.receiver IS NULL OR r.receiver = '');")" "0"
eq "★ 存活的签收记录都还挂在站点台账上（删签收不会留下孤凭证）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r WHERE $R_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.receipt_id = r.id);")" "0"
eq "★ (运单, 门店) 有重复签收（唯一键该拦住）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT r.transport_id, r.store_id FROM db_distribution.store_receipt r GROUP BY r.transport_id, r.store_id HAVING COUNT(*) > 1) x;")" "0"

# 2.7 暂存行：ARRIVE 通道逐次留证（按「设备 + 通道」算 —— 这台车载终端还兼报发车）
#   本次共 9 次到达上报 = 结构性拒绝 1（缺车牌 —— 现在唯一可能缺的必读项，门店已不进报文）
#                       + 已拒绝 1（刷的是别人的卡：现场刷错卡，重刷即可自愈，直接判掉不占人工队列）
#                       + 转人工 4（无在途运单、到齐后再刷，以及「没刷卡 / 卡换不出人」两条 ——
#                                  共同点是系统侧配置待补，司机重刷也过不去）
#                       + 已入账 3（三条物理到达：报文只报车牌+卡号+时刻，每站由服务端按顺序推）
eq "★ 本次 ARRIVE 通道暂存行 9 条（每次上报都留原始报文，含被拒/转人工的）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'ARRIVE' AND create_time >= '$START_TS';")" "9"
eq "★ 本次 ARRIVE 暂存行里已入账 3 条（三条物理到达，与站点台账 3 行对上）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'ARRIVE' AND create_time >= '$START_TS' AND status = 2;")" "3"
eq "★ 到达报文里带着司机卡号（刷卡是车载终端读到的物理事实，不是手工填的）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'ARRIVE' AND create_time >= '$START_TS' AND status = 2 AND payload LIKE '%cardNo%';")" "3"
eq "★ 被判「已拒绝」的 2 条：缺车牌（报文不成立）+ 刷了别人的卡（现场刷错卡，重刷即可自愈）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'ARRIVE' AND create_time >= '$START_TS' AND status = 3;")" "2"
eq "★ 「条件没到位」的 4 条一律是待人工（不判拒绝，否则外部系统会删单）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'ARRIVE' AND create_time >= '$START_TS' AND status = 1;")" "4"
eq "★ 到达报文里<b>没有</b>门店字段（车载终端读不到门店主数据，报站由服务端按顺序推）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'ARRIVE' AND create_time >= '$START_TS' AND (payload LIKE '%storeId%' OR payload LIKE '%storeName%');")" "0"
eq "★ 本次装车口暂存行 1 条" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001' AND create_time >= '$START_TS';")" "1"
eq "★ 装车口那条报文里没有司机（装车不采集司机：司机是上车刷卡那一刻才出现的）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001' AND create_time >= '$START_TS' AND payload LIKE '%driverName%';")" "0"
# 一台车载终端两个通道（2026-10-11 合并）：本脚本的发车与到达用的是**同一个设备号**
# ARRIVE-001，只有 channel 不同 —— 所以「DEPART 通道的暂存行挂在 ARRIVE-001 名下」
# 本身就是「合并」在数据上的证据。
eq "★ 本次发车暂存行 1 条（司机上车刷卡那一次），且挂在同一台车载终端 ARRIVE-001 上" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'DEPART' AND create_time >= '$START_TS';")" "1"
eq "★ 发车报文里带着卡号（刷卡是它读到的物理事实）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'DEPART' AND create_time >= '$START_TS' AND payload LIKE '%cardNo%';")" "1"
eq "★ 同一台车载终端本次覆盖了两个通道（合并生效：一台设备既报发车也报到达）" \
   "$(MYSQL "SELECT COUNT(DISTINCT channel) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND create_time >= '$START_TS';")" "2"
eq "★ 设备台账里 ARRIVE-001 已是「车载终端」（发车与到站刷卡是同一台设备）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = 'ARRIVE-001' AND device_name LIKE '%车载终端%';")" "1"
# 三台车载终端是一个系列（2026-10-11 命名统一）：命名格式一致，但绑定不同 ——
# 两台是「一车一台」（真机形态），一台是演示/回归的通用终端。
eq "★ 三台车载终端是同一系列：命名统一为「车载终端（…）」" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no IN ('DEPART-001','DEPART-003','ARRIVE-001') AND device_name LIKE '车载终端（%';")" "3"
eq "★ 系列里的绑定：两台真车各绑一台（京A·12345 / 京A·12346），通用那台有意不绑车" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE (device_no='DEPART-001' AND vehicle_no='京A·12345') OR (device_no='DEPART-003' AND vehicle_no='京A·12346') OR (device_no='ARRIVE-001' AND vehicle_no IS NULL);")" "3"

# 2.8 存量数据未被波及
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
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE device_no IN ('ARRIVE-001','LOADER-001') AND create_time >= '$START_TS';"

eq "计划数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")" "$BEFORE_PLANS"
eq "计划明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")" "$BEFORE_PITEMS"
eq "运单数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")" "$BEFORE_TRANSPORTS"
eq "装载明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")" "$BEFORE_TITEMS"
eq "签收记录数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt;")" "$BEFORE_RECEIPTS"
eq "站点台账数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop;")" "$BEFORE_STOPS"
eq "已签收站点数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status = 2;")" "$BEFORE_STOPS_SIGNED"
eq "站点台账里的孤儿行（运单已不存在）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE t.id IS NULL;")" "0"
eq "车载终端到达暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'ARRIVE';")" "$BEFORE_STAGING_ARRIVE"
eq "装车口暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001';")" "$BEFORE_STAGING_LOAD"
eq "车载终端发车暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ARRIVE-001' AND channel = 'DEPART';")" "$BEFORE_STAGING_DEPART"

echo
echo "=== 结果：通过 $PASS / 失败 $FAIL ==="
[ "$FAIL" -eq 0 ]
