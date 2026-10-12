#!/usr/bin/env bash
# 发货计划回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-delivery-plan-db.sh
#   1) 跑 logs/verify-delivery-plan.mjs（HTTP 层断言）
#   2) 按计划号格式、明细分账、运单装载明细、存量回填完整性逐条核验
#   3) 清理本次产生的计划 / 明细 / 运单 / 装载明细
#
# 断言口径：计划与运单的关联全靠落库事实判断，不去断言接口文案。
# 清理说明：delivery_plan 无逻辑删除列，回归数据直接物理删（它不是业务数据）；
#          删除后当日计划号流水会被下次创建复用 —— 这只影响回归，不影响业务。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 发货计划落库核验 ==="

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")
BEFORE_PITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")
BEFORE_TRANSPORTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")
BEFORE_TITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")
BEFORE_DUP=$(MYSQL "SELECT COUNT(*) FROM (SELECT split_batch_id FROM db_distribution.transport_item GROUP BY split_batch_id HAVING COUNT(*) > 1) t;")
echo "  水位：计划 ${BEFORE_PLANS} / 计划明细 ${BEFORE_PITEMS} / 运单 ${BEFORE_TRANSPORTS} / 装载明细 ${BEFORE_TITEMS}"
echo "  存量跨运单重复引用批次的组数（历史事实，应保持不变）：${BEFORE_DUP}"
echo "  测试起点 ${START_TS}"

# ── 1) 跑 HTTP 回归 ──
OUT=$(cd "$ROOT" && "$NODE" logs/verify-delivery-plan.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'
PLAN_IDS=$(echo "$OUT" | grep -o '# PLAN_IDS=.*' | sed 's/# PLAN_IDS=//')
TRANSPORT_IDS=$(echo "$OUT" | grep -o '# TRANSPORT_IDS=.*' | sed 's/# TRANSPORT_IDS=//')
HTTP_FAIL=$(echo "$OUT" | grep -o 'HTTP 断言：通过 [0-9]* / 失败 [0-9]*' | grep -o '失败 [0-9]*' | grep -o '[0-9]*')
echo

# ── 2) 落库断言 ──
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"

EXPECT_PLANS=$(echo "$PLAN_IDS" | tr ',' '\n' | grep -c .)
PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS';")
eq "本次新建计划数" "$PLANS" "$EXPECT_PLANS"

# 2.1 计划号与状态
eq "计划号格式合规（DP+8位日期-3位流水）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS' AND plan_no NOT REGEXP '^DP[0-9]{8}-[0-9]{3}$';")" "0"
eq "计划号无重复" \
   "$(MYSQL "SELECT COUNT(*) - COUNT(DISTINCT plan_no) FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS';")" "0"
eq "计划状态都在合法集合内(1待发货/2已生成/3已取消)" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS' AND status NOT IN (1,2,3);")" "0"
eq "计划都带货物条数（无空计划）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS' AND item_count <= 0;")" "0"
eq "计划都记录了制单人（实名留痕）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS' AND (operator IS NULL OR operator = '');")" "0"

# 2.2 不变量：item_count == 明细行数，total_weight_kg == 明细重量合计
eq "item_count 与明细行数不一致的计划" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE p.create_time >= '$START_TS' AND p.item_count <> (SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id);")" "0"
eq "计划总重与明细合计不一致的计划" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE p.create_time >= '$START_TS' AND ABS(IFNULL(p.total_weight_kg,0) - IFNULL((SELECT SUM(i.weight_kg) FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id),0)) > 0.01;")" "0"

# 2.3 明细自身完整（快照字段齐全、指向真实批次、同计划内不重复）
eq "明细缺批次号的行" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i JOIN db_distribution.delivery_plan p ON p.id = i.plan_id WHERE p.create_time >= '$START_TS' AND (i.batch_no IS NULL OR i.batch_no = '');")" "0"
eq "明细指向不存在的分割批次" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i JOIN db_distribution.delivery_plan p ON p.id = i.plan_id LEFT JOIN db_distribution.split_batch b ON b.id = i.split_batch_id WHERE p.create_time >= '$START_TS' AND b.id IS NULL;")" "0"
eq "同一计划内重复勾选同一批次（唯一键应挡住）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT i.plan_id, i.split_batch_id FROM db_distribution.delivery_plan_item i JOIN db_distribution.delivery_plan p ON p.id = i.plan_id WHERE p.create_time >= '$START_TS' GROUP BY i.plan_id, i.split_batch_id HAVING COUNT(*) > 1) t;")" "0"

# 2.4 运单装载明细：mjs 已把本次运单删掉，装载明细必须随之清除（验证级联删除）
eq "本次残留运单数（mjs 已删，应为 0）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport WHERE create_time >= '$START_TS';")" "0"
eq "本次残留装载明细（应随运单一并删除）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item WHERE create_time >= '$START_TS';")" "0"

# 2.5 本次计划的批次已释放：待发货计划占用的批次集合为空
eq "本次计划中仍处于「待发货」且占着批次的批次数（应已全部取消）" \
   "$(MYSQL "SELECT COUNT(DISTINCT i.split_batch_id) FROM db_distribution.delivery_plan_item i JOIN db_distribution.delivery_plan p ON p.id = i.plan_id WHERE p.create_time >= '$START_TS' AND p.status = 1;")" "0"

# 2.6 存量数据未被波及：34 条回填的装载明细仍在，且无孤儿
eq "运单总数回到基线" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")" "$BEFORE_TRANSPORTS"
eq "装载明细总数回到基线" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")" "$BEFORE_TITEMS"
eq "每张存量运单都有装载明细（回填完整，无遗漏）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.transport_id = t.id);")" "0"
eq "装载明细指向不存在的运单（孤儿行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti LEFT JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id WHERE t.id IS NULL;")" "0"
eq "装载明细指向不存在的分割批次（孤儿行）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item ti LEFT JOIN db_distribution.split_batch b ON b.id = ti.split_batch_id WHERE b.id IS NULL;")" "0"
eq "存量跨运单重复引用批次的组数保持不变（历史事实，不篡改）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT split_batch_id FROM db_distribution.transport_item GROUP BY split_batch_id HAVING COUNT(*) > 1) t;")" "$BEFORE_DUP"

# ── 3) 清理 ──
echo
echo "  --- 清理本次回归数据 ---"
if [ -n "$PLAN_IDS" ]; then
  MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE plan_id IN ($PLAN_IDS);"
  MYSQL "DELETE FROM db_distribution.delivery_plan WHERE id IN ($PLAN_IDS);"
fi
if [ -n "$TRANSPORT_IDS" ]; then
  MYSQL "DELETE FROM db_distribution.transport_stop WHERE transport_id IN ($TRANSPORT_IDS);"
  MYSQL "DELETE FROM db_distribution.transport_item WHERE transport_id IN ($TRANSPORT_IDS);"
  MYSQL "DELETE FROM db_distribution.cold_chain_transport WHERE id IN ($TRANSPORT_IDS);"
fi
# 按时间收尾：不依赖 mjs 回传的 id 集合
MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.delivery_plan WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.transport_stop WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.transport_item WHERE create_time >= '$START_TS';"

eq "计划数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")" "$BEFORE_PLANS"
eq "计划明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")" "$BEFORE_PITEMS"
eq "运单数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")" "$BEFORE_TRANSPORTS"
eq "装载明细数回到基线" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_item;")" "$BEFORE_TITEMS"
eq "站点台账里的孤儿行（运单已不存在）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id WHERE t.id IS NULL;")" "0"

echo
echo "=== 结果：通过 $PASS / 失败 $FAIL ==="
[ "$FAIL" -eq 0 ]
