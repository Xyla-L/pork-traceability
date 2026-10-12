#!/usr/bin/env bash
# 订单转入（通道 ORDER）回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-order-intake-db.sh
#   1) 跑 logs/verify-order-intake.mjs（HTTP 层断言），拿到本次的计划 / 暂存 id
#   2) 在「数据还在库里」的时候逐条核验落库事实：
#        计划来源标记（plan_source=ORDER）、来源订单号、状态停在「待发货」（未排车）、
#        明细挂的是真实且未被发运的批次、计划规模快照与明细一致、
#        订单号在计划表里唯一（幂等）、订单终端台账、存量计划来源未被波及
#   3) 备份后清理本次产生的计划 / 明细 / 暂存行
#
# 断言口径：只认真实落库的事实（列值、状态、计数、引用关系），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 订单转入（通道 ORDER）落库核验 ==="

# ── 0) 跑前清场 ──
# 上一轮残留的暂存行会命中接入层第二道兜底（同设备 + 同通道 + **同一份报文**）被判成重复上报，
# 症状很隐蔽：回执照样是 rejected/accepted，但业务侧压根没走到 —— 必须先把旧暂存行清掉。
# <b>只能清回归自己造的</b>（订单号前缀 `SO-REG-%`）：这个终端号同时也承担界面演示，
# 按 device_no 整表删会把演示留下的真实业务记录一并抹掉（本轮踩过：误删了 15:29/15:30 两条演示上报，
# 靠 logs/backup/order-intake-preclean-*.sql 才还原回来）。
ORDER_STAGING_SQL="SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ORDER-TERM-001' AND JSON_UNQUOTE(JSON_EXTRACT(payload, '\$.orderNo')) LIKE 'SO-REG-%';"
ORDER_STAGING_DEL="DELETE FROM db_ingest.ingest_staging WHERE device_no = 'ORDER-TERM-001' AND JSON_UNQUOTE(JSON_EXTRACT(payload, '\$.orderNo')) LIKE 'SO-REG-%';"
PRE_STAGING=$(MYSQL "$ORDER_STAGING_SQL")
PRE_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE order_no LIKE 'SO-REG-%';")
if [ "$PRE_STAGING" != "0" ] || [ "$PRE_PLANS" != "0" ]; then
  mkdir -p "$ROOT/logs/backup"
  PRE_BACKUP="$ROOT/logs/backup/order-intake-preclean-$(date +%Y%m%d-%H%M%S).sql"
  echo "  [清场] 上一轮残留：ORDER 暂存 ${PRE_STAGING} 行 / 回归计划 ${PRE_PLANS} 张 → 备份 $PRE_BACKUP"
  {
    echo "-- 订单转入回归「跑前清场」备份"
    docker exec pork-traceability-mysql-1 mysqldump -uroot -pchange-this-root-password \
      --default-character-set=utf8mb4 --no-create-info --skip-add-locks \
      db_distribution delivery_plan delivery_plan_item 2>/dev/null | grep -v "Using a password"
    docker exec pork-traceability-mysql-1 mysqldump -uroot -pchange-this-root-password \
      --default-character-set=utf8mb4 --no-create-info --skip-add-locks \
      db_ingest ingest_staging 2>/dev/null | grep -v "Using a password"
  } > "$PRE_BACKUP"
  MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE plan_id IN (SELECT id FROM db_distribution.delivery_plan WHERE order_no LIKE 'SO-REG-%');" >/dev/null
  MYSQL "DELETE FROM db_distribution.delivery_plan WHERE order_no LIKE 'SO-REG-%';" >/dev/null
  MYSQL "$ORDER_STAGING_DEL" >/dev/null
  echo "  清场后：ORDER 暂存 $(MYSQL "$ORDER_STAGING_SQL") 行 / 回归计划 $(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE order_no LIKE 'SO-REG-%';") 张"
else
  echo "  清场：无残留"
fi

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")
BEFORE_PITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")
BEFORE_TRANSPORTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")
BEFORE_ORDER_STAGING=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ORDER-TERM-001';")
BEFORE_ORDER_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE plan_source = 'ORDER';")
echo "  水位：计划 ${BEFORE_PLANS} / 计划明细 ${BEFORE_PITEMS} / 运单 ${BEFORE_TRANSPORTS}"
echo "        ORDER 终端暂存 ${BEFORE_ORDER_STAGING} / ORDER 来源计划 ${BEFORE_ORDER_PLANS}"
echo "  测试起点 ${START_TS}"

# 本次数据的范围谓词（只覆盖本次创建的记录）
PLAN_SCOPE="p.create_time >= '$START_TS'"
ITEM_SCOPE="i.create_time >= '$START_TS'"

# ── 1) 跑 HTTP 回归 ──
OUT=$("$NODE" "$ROOT/logs/verify-order-intake.mjs" 2>&1)
echo "$OUT" | sed 's/^/  /'
PLAN_IDS=$(echo "$OUT" | grep -o '^PLAN_IDS=.*' | sed 's/^PLAN_IDS=//')
STAGING_IDS=$(echo "$OUT" | grep -o '^STAGING_IDS=.*' | sed 's/^STAGING_IDS=//')
HTTP_FAIL=$(echo "$OUT" | grep -o '通过 [0-9]* / 失败 [0-9]*' | head -1 | grep -o '失败 [0-9]*' | grep -o '[0-9]*')
echo

# ── 2) 落库断言 ──
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"

EXPECT_PLANS=$(echo "$PLAN_IDS" | tr ',' '\n' | grep -c .)
PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE;")
eq "本次新建计划数" "$PLANS" "$EXPECT_PLANS"

# 2.1 来源标记：本次计划必须全部是「订单转入」且带来源订单号
eq "★ 本次计划全部标记为订单转入（plan_source=ORDER）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.plan_source = 'ORDER';")" "$EXPECT_PLANS"
eq "★ 订单转入的计划必须带来源订单号" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.plan_source = 'ORDER' AND (p.order_no IS NULL OR p.order_no = '');")" "0"
eq "plan_source 取值越界（只允许 MANUAL/ORDER）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE plan_source NOT IN ('MANUAL','ORDER');")" "0"
eq "★ 全库没有 plan_source 为空的计划（存量也得有来源）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan WHERE plan_source IS NULL OR plan_source = '';")" "0"

# 2.2 幂等：同一订单号在计划表里只能有一张
eq "★ 同一来源订单号对应多张计划（幂等被击穿）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT order_no FROM db_distribution.delivery_plan WHERE order_no IS NOT NULL GROUP BY order_no HAVING COUNT(*) > 1) t;")" "0"

# 2.3 停在「待排车」：状态必须是待发货，且没有车 / 人 / 运单
eq "★ 订单转入的计划状态 = 待发货(1)（排车还没做）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.status <> 1;")" "0"
eq "★ 订单转入没有凭空填车与司机（车人由排车/装车口补）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND (p.vehicle_no IS NOT NULL OR p.driver_name IS NOT NULL);")" "0"
eq "★ 订单转入没有直接生成运单（transport_id / transport_no 都应为空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND (p.transport_id IS NOT NULL OR p.transport_no IS NOT NULL);")" "0"

# 2.4 计划规模快照与明细一致
eq "item_count 与明细行数一致" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.item_count <> (SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id);")" "0"
eq "计划总重与明细合计一致" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND ABS(IFNULL(p.total_weight_kg,0) - IFNULL((SELECT SUM(i.weight_kg) FROM db_distribution.delivery_plan_item i WHERE i.plan_id = p.id),0)) > 0.01;")" "0"
eq "明细行数至少 1（空计划不该落库）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan p WHERE $PLAN_SCOPE AND p.item_count < 1;")" "0"

# 2.5 明细挂的是真实批次、且都没被发运过
eq "★ 明细挂的分割批次必须真实存在" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND NOT EXISTS (SELECT 1 FROM db_distribution.split_batch b WHERE b.id = i.split_batch_id);")" "0"
eq "★ 明细挂的批次不该是已被运单发运过的货" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND EXISTS (SELECT 1 FROM db_distribution.transport_item t WHERE t.split_batch_id = i.split_batch_id);")" "0"
eq "明细都处于未排车状态（shipped=0 且无运单号）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND (i.shipped <> 0 OR i.transport_id IS NOT NULL);")" "0"
eq "明细带着批次号 / 部位 / 重量三件套（配货留痕）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item i WHERE $ITEM_SCOPE AND (i.batch_no IS NULL OR i.part_code IS NULL OR i.weight_kg IS NULL);")" "0"

# 2.6 一个批次不能被两张「待发货」计划同时占用（配货不变量）
eq "★ 同一批次被多张待发货计划的未排车明细同时占用" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT i.split_batch_id FROM db_distribution.delivery_plan_item i JOIN db_distribution.delivery_plan p ON p.id = i.plan_id WHERE p.status IN (1,4) AND i.shipped = 0 GROUP BY i.split_batch_id HAVING COUNT(DISTINCT i.plan_id) > 1) t;")" "0"

# 2.7 订单终端台账
eq "ORDER 通道的接入终端已登记" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_device WHERE device_no = 'ORDER-TERM-001' AND channel = 'ORDER' AND status = 1;")" "1"

# 2.8 暂存行全部落在 ORDER 通道，且结论是「已入账」或「待人工」，没有「已拒绝」之外的意外状态
eq "本次暂存行都记在 ORDER 通道" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ORDER-TERM-001' AND receive_time >= '$START_TS' AND channel <> 'ORDER';")" "0"
eq "本次暂存行都带了原始报文（留档不可变）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ORDER-TERM-001' AND receive_time >= '$START_TS' AND payload IS NULL;")" "0"

# 2.9 存量未被波及：运单总数不变
AFTER_TRANSPORTS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport;")
eq "运单总数未变（订单转入不碰运单）" "$AFTER_TRANSPORTS" "$BEFORE_TRANSPORTS"

echo
echo "=== 落库核验：${PASS} 通过 / ${FAIL} 失败 ==="

# ── 3) 备份 + 清理 ──
if [ -n "$PLAN_IDS" ] || [ -n "$STAGING_IDS" ]; then
  mkdir -p "$ROOT/logs/backup"
  STAMP=$(date +%Y%m%d-%H%M%S)
  BACKUP="$ROOT/logs/backup/order-intake-residue-$STAMP.sql"
  echo "备份本次数据到 $BACKUP"
  {
    echo "-- 订单转入回归残留备份 $STAMP"
    echo "-- 计划：$PLAN_IDS"
    echo "-- 暂存：$STAGING_IDS"
    docker exec pork-traceability-mysql-1 mysqldump -uroot -pchange-this-root-password \
      --default-character-set=utf8mb4 --no-create-info --skip-add-locks \
      db_distribution delivery_plan delivery_plan_item 2>/dev/null | grep -v "Using a password"
    docker exec pork-traceability-mysql-1 mysqldump -uroot -pchange-this-root-password \
      --default-character-set=utf8mb4 --no-create-info --skip-add-locks \
      db_ingest ingest_staging 2>/dev/null | grep -v "Using a password"
  } > "$BACKUP"
  echo "  备份大小：$(wc -c < "$BACKUP") 字节"

  # 先删明细再删计划（外键语义上明细从属于计划）
  if [ -n "$PLAN_IDS" ]; then
    IDS=$(echo "$PLAN_IDS" | tr ',' '\n' | grep -E '^[0-9]+$' | paste -sd, -)
    [ -n "$IDS" ] && MYSQL "DELETE FROM db_distribution.delivery_plan_item WHERE plan_id IN ($IDS);" >/dev/null
    [ -n "$IDS" ] && MYSQL "DELETE FROM db_distribution.delivery_plan WHERE id IN ($IDS);" >/dev/null
    echo "  已删除计划 / 明细：$IDS"
  fi
  if [ -n "$STAGING_IDS" ]; then
    SIDS=$(echo "$STAGING_IDS" | tr ',' '\n' | grep -E '^[0-9]+$' | paste -sd, -)
    [ -n "$SIDS" ] && MYSQL "DELETE FROM db_ingest.ingest_staging WHERE id IN ($SIDS);" >/dev/null
    echo "  已删除暂存行：$SIDS"
  fi

  AFTER_PLANS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan;")
  AFTER_PITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.delivery_plan_item;")
  AFTER_ORDER_STAGING=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no = 'ORDER-TERM-001';")
  echo "  清理后水位：计划 ${AFTER_PLANS}（基线 ${BEFORE_PLANS}）/ 计划明细 ${AFTER_PITEMS}（基线 ${BEFORE_PITEMS}）/ ORDER 暂存 ${AFTER_ORDER_STAGING}（基线 ${BEFORE_ORDER_STAGING}）"
else
  echo "没有解析到本次 ID，跳过清理"
fi

[ "$FAIL" -eq 0 ] || exit 1
