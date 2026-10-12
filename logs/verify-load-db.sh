#!/usr/bin/env bash
# 装车口自动建单回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-load-db.sh
#   1) 以 KEEP=1 跑 logs/verify-load.mjs（HTTP 层断言，且**不自行删数据**）
#   2) 在「数据还在库里」的时候逐条核验落库事实：
#        明细发运标记、计划与运单的对应、分车不变量、运单来源标记、
#        装载明细与计划明细一一对应、「待发运」口径复算、存量数据未被波及、
#        司机口径（装车口不采集司机：留空可建单、报了卡号被拒、来源留空不假装有人录过；
#                 刷卡=本人确认只发生在发车那端，见 verify-depart-db.sh）
#   3) 清理本次产生的计划 / 明细 / 运单 / 装载明细 / 暂存行
#
# 为什么用 KEEP=1：默认运行会自己删掉运单，删完再查就只能断言「没有残留」，
# 运单来源标记、目的地、分车对应关系这些最该核的东西全查空了。
# 断言口径：只认真实落库的事实（批次号、状态、标记、计数），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 装车口自动建单落库核验 ==="

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")
BEFORE_PITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")
BEFORE_TRANSPORTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")
BEFORE_TITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")
BEFORE_STAGING=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001';")
BEFORE_DUP=$(MYSQL "SELECT COUNT(*) FROM (SELECT split_batch_id FROM db_distribution.transport_item GROUP BY split_batch_id HAVING COUNT(*) > 1) t;")
echo "  水位：计划 ${BEFORE_PLANS} / 计划明细 ${BEFORE_PITEMS} / 运单 ${BEFORE_TRANSPORTS} / 装载明细 ${BEFORE_TITEMS} / 装车口暂存 ${BEFORE_STAGING}"
echo "  存量跨运单重复引用批次的组数（历史事实，应保持不变）：${BEFORE_DUP}"
echo "  测试起点 ${START_TS}"

# 本次数据的范围谓词（只覆盖本次创建的记录）
PLAN_SCOPE="p.create_time >= '$START_TS'"
ITEM_SCOPE="i.create_time >= '$START_TS'"
T_SCOPE="create_time >= '$START_TS'"

# ── 1) 跑 HTTP 回归（KEEP=1：不自行删数据，留给本脚本核验） ──
OUT=$(cd "$ROOT" && KEEP=1 "$NODE" logs/verify-load.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'
PLAN_IDS=$(echo "$OUT" | grep -o '# PLAN_IDS=.*' | sed 's/# PLAN_IDS=//')
TRANSPORT_IDS=$(echo "$OUT" | grep -o '# TRANSPORT_IDS=.*' | sed 's/# TRANSPORT_IDS=//')
HTTP_FAIL=$(echo "$OUT" | grep -o '通过 [0-9]* / 失败 [0-9]*' | head -1 | grep -o '失败 [0-9]*' | grep -o '[0-9]*')
echo

# ── 2) 落库断言 ──
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"

EXPECT_PLANS=$(echo "$PLAN_IDS" | tr ',' '\n' | grep -c .)
PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE;")
eq "本次新建计划数" "$PLANS" "$EXPECT_PLANS"

# 2.1 计划主表
eq "计划号格式合规（DP+8位日期-3位流水）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.plan_no NOT REGEXP '^DP[0-9]{8}-[0-9]{3}\$';")" "0"
eq "计划号无重复" \
   "$(MYSQL "SELECT COUNT(*) - COUNT(DISTINCT p.plan_no) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE;")" "0"
eq "计划状态都在合法集合内(1待发货/2已排车/3已取消/4部分排车)" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.status NOT IN (1,2,3,4);")" "0"
eq "计划都记录了制单人（实名留痕）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND (p.operator IS NULL OR p.operator = '');")" "0"
eq "item_count 与明细行数一致" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.item_count <> (SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id);")" "0"
eq "计划总重与明细合计一致" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND ABS(IFNULL(p.total_weight_kg,0) - IFNULL((SELECT SUM(i.weight_kg) FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id),0)) > 0.01;")" "0"

# 2.2 明细排车标记：shipped 与 transport_id 必须同进同退
eq "★ shipped=1 却没有运单号的明细" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND i.shipped = 1 AND i.transport_id IS NULL;")" "0"
eq "★ shipped=0 却挂着运单号的明细" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND i.shipped = 0 AND i.transport_id IS NOT NULL;")" "0"
eq "shipped 取值越界（只允许 0/1）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND i.shipped NOT IN (0,1);")" "0"
eq "本次 9 条明细全部标记为已排车（五张计划都已排完）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND i.shipped = 1;")" "9"

# 2.3 计划与运单的对应关系（分车不变量）
eq "★ 计划主表的 transport_id 悬挂（指向已不存在的运单）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p LEFT JOIN db_distribution.cold_chain_transport t ON t.id = p.transport_id WHERE $PLAN_SCOPE AND p.transport_id IS NOT NULL AND t.id IS NULL;")" "0"
eq "★ 计划主表的 transport_no 与实际运单号对不上" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p JOIN db_distribution.cold_chain_transport t ON t.id = p.transport_id WHERE $PLAN_SCOPE AND p.transport_no <> t.transport_no;")" "0"
eq "★ transport_count 与明细指向的不同运单数不等" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.transport_count <> (SELECT COUNT(DISTINCT i.transport_id) FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id AND i.transport_id IS NOT NULL);")" "0"
eq "★ 分车：存在一张计划其明细分属 2 张不同运单" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT p.id FROM db_distribution.delivery_plan p JOIN db_distribution.delivery_plan_item i ON i.plan_id = p.id WHERE $PLAN_SCOPE AND i.transport_id IS NOT NULL GROUP BY p.id HAVING COUNT(DISTINCT i.transport_id) = 2) t;")" "1"
eq "★ 状态=2（已排车）却仍有未排车明细的计划" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.status = 2 AND EXISTS (SELECT 1 FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id AND i.shipped = 0);")" "0"
eq "★ 状态=1（待发货）却已有已排车明细的计划" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.status = 1 AND EXISTS (SELECT 1 FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id AND i.shipped = 1);")" "0"

# 2.4 本次由计划生成的运单（设备通道）
EXPECT_TRANSPORTS=$(echo "$TRANSPORT_IDS" | tr ',' '\n' | grep -c .)
eq "本次新建运单数" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE;")" "$EXPECT_TRANSPORTS"
eq "★ 本次运单来源标记非 DEVICE 的" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND source <> 'DEVICE';")" "0"
eq "★ 本次运单来源设备非 LOADER-001 的" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND source_ref <> 'LOADER-001';")" "0"
eq "★ 本次运单缺目的地（目的地必须来自计划，不能为空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND (destination IS NULL OR destination = '');")" "0"
eq "★ 本次运单缺车牌（车牌是发车与到达按车牌倒查的唯一凭据，不能为空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND (vehicle_no IS NULL OR vehicle_no = '');")" "0"
eq "本次运单状态都在合法集合内(1待发车/2运输中/3待签收/4已签收)" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND status NOT IN (1,2,3,4);")" "0"
eq "★ 有运单被发车过（补录司机后的 A 单 + 分车第一车，共 2 张）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND depart_time IS NOT NULL;")" "2"

# 2.4.1 「同一趟车继续装」并入的落库痕迹（2026-10-10 新增）
#   并入的判据只认**业务形态**，不碰时间戳精度（DATETIME 是秒级，同秒事件分不出来）：
#     E2 / E3 是**分两次单独扫码**的，各带一张新计划；如果没有并入，它们会各自另开一张运单。
#   所以两条合起来就够：① 5 张计划只落在 4 张运单上（有两张被并走了）；
#                      ② 存在一张运单同时挂着 3 张计划（A 那张装了 planA + planD + planE）。
eq "★ 存在一张运单挂着 3 张计划（A 那张：原计划 + 同店补货 + 换店补货）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT i.transport_id FROM db_distribution.delivery_plan_item i WHERE i.transport_id IS NOT NULL AND $ITEM_SCOPE GROUP BY i.transport_id HAVING COUNT(DISTINCT i.plan_id) = 3) x;")" "1"
eq "★ 本次 5 张计划落在 4 张运单上（两张是被并进去的，没有各开一张）" \
   "$(MYSQL "SELECT COUNT(DISTINCT i.transport_id) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND i.transport_id IS NOT NULL;")" "4"
eq "★ 站点批次数合计 = 装载明细数（并入时按门店归并，一站一行的汇总不能少算）" \
   "$(MYSQL "SELECT IFNULL(SUM(s.item_count),0) FROM db_distribution.transport_stop s JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE t.$T_SCOPE;")" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE t.$T_SCOPE;")"
eq "★ 运单的 stop_count 与站点台账行数一致（并入后按台账重算，不手工拼）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE $T_SCOPE AND t.stop_count <> (SELECT COUNT(*) FROM db_distribution.transport_stop s WHERE s.transport_id = t.id);")" "0"

# 2.5 装载明细与计划明细必须一一对应（同一批货不能既算在这车又算在那车）
eq "★ 本次运单的装载明细数 = 本次计划中已排车明细数" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE t.$T_SCOPE;")" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND i.shipped = 1;")"
eq "★ 装载明细里的批次与计划明细指向的批次不是同一集合（差集非空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE t.$T_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.delivery_plan_item i WHERE i.split_batch_id = ti.split_batch_id AND i.transport_id = ti.transport_id);")" "0"
eq "★ 计划明细指向的批次没有对应的装载明细" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND i.shipped = 1 AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.split_batch_id = i.split_batch_id AND ti.transport_id = i.transport_id);")" "0"

# 2.6 「待发运」口径复算：本次计划的批次一个都不该出现在待发运里
eq "★ 本次计划的批次仍有落在「待发运」集合里的（说明占用口径算漏了）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch b WHERE NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.split_batch_id = b.id) AND NOT EXISTS (SELECT 1 FROM db_distribution.delivery_plan_item i JOIN db_distribution.delivery_plan p ON p.id = i.plan_id WHERE i.split_batch_id = b.id AND p.status IN (1,4) AND i.shipped = 0) AND b.id IN (SELECT i.split_batch_id FROM db_distribution.delivery_plan_item i JOIN db_distribution.delivery_plan p ON p.id = i.plan_id WHERE $PLAN_SCOPE);")" "0"

# 2.7 存量数据未被波及
eq "每张存量运单都有装载明细（回填完整，无遗漏）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.transport_id = t.id);")" "0"
eq "装载明细指向不存在的运单（孤儿行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti LEFT JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE t.id IS NULL;")" "0"
eq "装载明细指向不存在的分割批次（孤儿行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti LEFT JOIN db_distribution.split_batch b ON b.id = ti.split_batch_id WHERE b.id IS NULL;")" "0"
eq "存量跨运单重复引用批次的组数保持不变（历史事实，不篡改）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT split_batch_id FROM db_distribution.transport_item GROUP BY split_batch_id HAVING COUNT(*) > 1) t;")" "$BEFORE_DUP"
eq "存量运单来源标记都已回填（不留 NULL）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE source IS NULL OR source = '';")" "0"

# 2.8 司机来源与确认强度（本轮新增：「人名非空」≠「本人确认过」）
eq "司机/车辆主数据表已就位（备案是自动化的地基：3 司机 + 3 卡 + 3 绑定 + 8 台车，含 5 张回归专用车牌）" \
   "$(MYSQL "SELECT (SELECT COUNT(*) FROM db_distribution.driver) + (SELECT COUNT(*) FROM db_distribution.driver_card) + (SELECT COUNT(*) FROM db_distribution.driver_vehicle) + (SELECT COUNT(*) FROM db_distribution.vehicle);")" "17"
eq "★ 本次运单 driver_source 越界（只允许 CARD/FACE/PLATE_INFERRED/MANUAL；司机为空时可为 NULL）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND driver_source IS NOT NULL AND driver_source NOT IN ('CARD','FACE','PLATE_INFERRED','MANUAL');")" "0"
eq "★ 「本人确认」与来源自相矛盾（confirmed=1 却不是刷卡/人脸）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND driver_confirmed = 1 AND driver_source NOT IN ('CARD','FACE');")" "0"
eq "★ 记了「刷卡」却没标本人确认（confirmed=0 却是 CARD）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND driver_source = 'CARD' AND driver_confirmed <> 1;")" "0"
eq "★ 本次没有任何运单记成「刷卡确认」（装车口没有读卡器，刷卡只发生在发车那一刻）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND driver_source = 'CARD';")" "0"
eq "★ 报了姓名的几次都记成「人工录入、非本人确认」（计划 B + 分车两车 + 补录司机的那一张）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND driver_source = 'MANUAL' AND driver_confirmed = 0;")" "4"
eq "★ 没有「车牌推定」直接建出运单（装车口不推定责任人：推定只能进回执，不得放行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND driver_source = 'PLATE_INFERRED';")" "0"
eq "★ 司机为空却写着来源（空名字配一个来源 = 假装有人录过）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND (driver_name IS NULL OR driver_name = '') AND driver_source IS NOT NULL;")" "0"
eq "★ 有司机名字却没有来源（追责时说不清这人是本人刷卡还是别人代填）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE driver_name IS NOT NULL AND driver_name <> '' AND (driver_source IS NULL OR driver_source = '');")" "0"
eq "★ 本次运单车型都齐全（计划没填的由「车牌 → 车辆备案」带出）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE $T_SCOPE AND (vehicle_type IS NULL OR vehicle_type = '');")" "0"
eq "存量运单司机来源都已回填（本次新建的除外：装车口不采集司机，留空是常态）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE create_time < '$START_TS' AND (driver_source IS NULL OR driver_source = '' OR driver_confirmed IS NULL);")" "0"

# ── 3) 清理 ──
echo
echo "  --- 清理本次回归数据 ---"
if [ -n "$TRANSPORT_IDS" ]; then
  MYSQL "DELETE FROM db_distribution.transport_stop WHERE transport_id IN ($TRANSPORT_IDS);"
  MYSQL "DELETE FROM db_distribution.transport_item WHERE transport_id IN ($TRANSPORT_IDS);"
  MYSQL "DELETE FROM db_distribution.cold_chain_transport WHERE id IN ($TRANSPORT_IDS);"
fi
if [ -n "$PLAN_IDS" ]; then
  MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE plan_id IN ($PLAN_IDS);"
  MYSQL "DELETE FROM db_distribution.delivery_plan WHERE id IN ($PLAN_IDS);"
fi
# 按时间收尾：不依赖 mjs 回传的 id 集合
MYSQL "DELETE FROM db_distribution.transport_stop WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.transport_item WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001' AND create_time >= '$START_TS';"

eq "计划数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")" "$BEFORE_PLANS"
eq "计划明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")" "$BEFORE_PITEMS"
eq "运单数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")" "$BEFORE_TRANSPORTS"
eq "装载明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")" "$BEFORE_TITEMS"
eq "站点台账里的孤儿行（运单已不存在）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE t.id IS NULL;")" "0"
eq "装车口暂存行数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'LOADER-001';")" "$BEFORE_STAGING"

echo
echo "=== 结果：通过 $PASS / 失败 $FAIL ==="
[ "$FAIL" -eq 0 ]
