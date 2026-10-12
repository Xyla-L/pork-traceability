#!/usr/bin/env bash
# 清空「发货计划链」+ 冷链相关设备上报台账（供重新上报测试）。
#
# 清理范围（只清「计划 + 计划生成的运单」这一条链，不动历史运单 / 签收凭证 / 零售演示数据）：
#   1) 全部发货计划 delivery_plan + 明细 delivery_plan_item
#   2) 这些计划生成的运单 cold_chain_transport（按 delivery_plan_item.transport_id 定位）
#      及其下游：store_receipt / transport_stop / transport_item / temperature_log / retail_sale
#   3) LOAD / DEPART / ARRIVE / TEMPERATURE / ORDER 五个通道的设备上报暂存行
#
# 不动：9 月历史运单及其签收/零售/上链记录、分割批次与胴体批次（清计划后自动回到待发运池）、
#       其他通道的设备上报台账、上链记录（区块链记录只增不删）。
#
# 前置：自动 mysqldump 备份到 logs/backup/。用法：bash logs/clean-plan-chain.sh
set -u

ROOT=/e/pork-traceability
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password" || true; }
MYSQLX() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -e "$1" 2>&1 | grep -v "Using a password" || true; }

TS=$(date +%Y%m%d-%H%M%S)
BK="$ROOT/logs/backup/plan-chain-$TS.sql"
mkdir -p "$ROOT/logs/backup"

echo "=== 清空发货计划链（$TS） ==="

PLAN_IDS=$(MYSQL "SELECT COALESCE(GROUP_CONCAT(id),'') FROM db_distribution.delivery_plan;")
TRANSPORT_IDS=$(MYSQL "SELECT COALESCE(GROUP_CONCAT(DISTINCT transport_id),'') FROM db_distribution.delivery_plan_item WHERE transport_id IS NOT NULL;")
echo "计划 ids : ${PLAN_IDS:-（无）}"
echo "运单 ids : ${TRANSPORT_IDS:-（无）}"

echo "--- 备份到 $BK"
docker exec pork-traceability-mysql-1 mysqldump -uroot -pchange-this-root-password \
  --databases db_distribution db_sales db_ingest --single-transaction --no-tablespaces \
  > "$BK" 2>/dev/null
echo "备份大小：$(wc -c < "$BK") 字节"

echo "--- 删除（子表 → 父表）"
if [ -n "$TRANSPORT_IDS" ]; then
  for pair in \
    "db_distribution.store_receipt|transport_id" \
    "db_distribution.transport_stop|transport_id" \
    "db_distribution.transport_item|transport_id" \
    "db_distribution.temperature_log|transport_id" \
    "db_sales.retail_sale|transport_id" \
    "db_distribution.cold_chain_transport|id"; do
    tbl=${pair%%|*}; col=${pair##*|}
    MYSQLX "DELETE FROM $tbl WHERE $col IN ($TRANSPORT_IDS);"
    echo "  $tbl <- $col IN ($TRANSPORT_IDS)"
  done
fi
if [ -n "$PLAN_IDS" ]; then
  MYSQLX "DELETE FROM db_distribution.delivery_plan_item WHERE plan_id IN ($PLAN_IDS);"
  echo "  delivery_plan_item <- plan_id IN ($PLAN_IDS)"
  MYSQLX "DELETE FROM db_distribution.delivery_plan WHERE id IN ($PLAN_IDS);"
  echo "  delivery_plan <- id IN ($PLAN_IDS)"
fi
echo "  ingest_staging <- channel IN (LOAD,DEPART,ARRIVE,TEMPERATURE,ORDER)"
MYSQLX "DELETE FROM db_ingest.ingest_staging WHERE channel IN ('LOAD','DEPART','ARRIVE','TEMPERATURE','ORDER');"

echo "--- 复核（全部应为 0）"
MYSQL "SELECT 'delivery_plan', COUNT(*) FROM db_distribution.delivery_plan
UNION ALL SELECT 'delivery_plan_item', COUNT(*) FROM db_distribution.delivery_plan_item
UNION ALL SELECT '冷链暂存行', COUNT(*) FROM db_ingest.ingest_staging WHERE channel IN ('LOAD','DEPART','ARRIVE','TEMPERATURE','ORDER')
UNION ALL SELECT '孤儿装载明细', COUNT(*) FROM db_distribution.transport_item i LEFT JOIN db_distribution.cold_chain_transport t ON t.id=i.transport_id WHERE t.id IS NULL
UNION ALL SELECT '孤儿站点', COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.cold_chain_transport t ON t.id=s.transport_id WHERE t.id IS NULL
UNION ALL SELECT '孤儿温度', COUNT(*) FROM db_distribution.temperature_log l LEFT JOIN db_distribution.cold_chain_transport t ON t.id=l.transport_id WHERE t.id IS NULL
UNION ALL SELECT '孤儿零售', COUNT(*) FROM db_sales.retail_sale r LEFT JOIN db_distribution.cold_chain_transport t ON t.id=r.transport_id WHERE r.transport_id IS NOT NULL AND t.id IS NULL;"

echo "--- 保留量（历史运单 / 签收 / 温度 / 批次）"
MYSQL "SELECT '运单', COUNT(*) FROM db_distribution.cold_chain_transport
UNION ALL SELECT '签收凭证', COUNT(*) FROM db_distribution.store_receipt
UNION ALL SELECT '温度日志', COUNT(*) FROM db_distribution.temperature_log
UNION ALL SELECT '分割批次', COUNT(*) FROM db_distribution.split_batch
UNION ALL SELECT '胴体批次', COUNT(*) FROM db_distribution.carcass_batch;"

echo "=== 完成 ==="
