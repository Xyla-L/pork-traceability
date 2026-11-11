#!/usr/bin/env bash
# ============================================================================
# 养殖系统同步（FARM_ERP）通道：落库列级核验 + 可选清理
#
# 为什么单独一个 sh：本环境的 node 禁止 child_process，.mjs 只能走 API；
# 而「void_source 到底是 MANUAL 还是 EXTERNAL」「data_source 是不是 API」
# 这类列级事实 API 看不全（已作废的批次在列表里会被逻辑删除过滤掉）。
#
# 用法：
#   bash logs/verify-farm-erp-db.sh            # 只核验
#   bash logs/verify-farm-erp-db.sh --clean    # 核验后清掉本通道产生的全部演示/回归数据
#
# 清理口径：只删 external_system 非空（即本通道同步进来的）批次及其成员、
# 以及 FARM-ERP-001 这个接入凭据的上报流水与对应链上事件。人工登记的批次一行不动。
# ============================================================================
set -u

MYSQL="docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N"

q() { $MYSQL -e "$1" 2>/dev/null; }

pass=0
fail=0
check() {
  if [ "$2" = "$3" ]; then
    echo "  [PASS] $1（$2）"
    pass=$((pass + 1))
  else
    echo "  [FAIL] $1（实际 $2，期望 $3）"
    fail=$((fail + 1))
  fi
}
nonzero() {
  local label="$1" actual="$2"
  if [ -n "$actual" ] && [ "$actual" != "0" ] && [ "$actual" != "NULL" ]; then
    echo "  [PASS] $label（$actual）"
    pass=$((pass + 1))
  else
    echo "  [FAIL] $label（实际 $actual，期望非空/非0）"
    fail=$((fail + 1))
  fi
}

echo "══ 养殖系统同步：落库列级核验 ══════════════════════════════"

# ---------------------------------------------------------------- 1 外部批次身份
echo "· 外部批次身份（external_system / external_id / data_source）"
check "外部批次总数 > 0 与否" "$(q "SELECT IF(COUNT(*)>0,'yes','no') FROM db_breeding.pig_batch WHERE external_system IN ('FARM_ERP','DEMO_ERP')")" "yes"
check "外部批次都写了 external_id" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch WHERE external_system IN ('FARM_ERP','DEMO_ERP') AND (external_id IS NULL OR external_id='')")" "0"
check "外部批次 data_source 一律 API" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch WHERE external_system IN ('FARM_ERP','DEMO_ERP') AND data_source<>'API'")" "0"
check "人工批次 data_source 一律 MANUAL" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch WHERE external_system IS NULL AND data_source<>'MANUAL'")" "0"

# ---------------------------------------------------------------- 2 出生日期继承
echo "· 出生日期（批次 → 成员继承，日龄由它现算）"
nonzero "有出生日期的外部批次数" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch WHERE external_system IN ('FARM_ERP','DEMO_ERP') AND birth_date IS NOT NULL")"
check "外部批次成员与批次出生日期一致" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_individual i JOIN db_breeding.pig_batch b ON i.batch_id=b.id
        WHERE b.external_system IN ('FARM_ERP','DEMO_ERP') AND b.birth_date IS NOT NULL
          AND (i.birth_date IS NULL OR i.birth_date<>b.birth_date)")" "0"

# ---------------------------------------------------------------- 3 成员与数量自洽
echo "· 成员数/耳标范围与批次行一致（重算口径）"
check "quantity = 有效成员数（仅未作废批次）" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch b
        WHERE b.external_system IN ('FARM_ERP','DEMO_ERP') AND b.void_flag=0
          AND b.quantity <> (SELECT COUNT(*) FROM db_breeding.pig_individual i
                             WHERE i.batch_id=b.id AND i.void_flag=0 AND i.deleted=0)")" "0"
check "耳标起止 = 成员最小/最大号" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch b
        JOIN (SELECT batch_id, MIN(ear_tag_no) mn, MAX(ear_tag_no) mx FROM db_breeding.pig_individual
              WHERE void_flag=0 AND deleted=0 GROUP BY batch_id) m ON m.batch_id=b.id
        WHERE b.external_system IN ('FARM_ERP','DEMO_ERP') AND (b.ear_tag_start<>m.mn OR b.ear_tag_end<>m.mx)")" "0"

# ---------------------------------------------------------------- 4 撤销来源
echo "· 撤销来源（人工作废 MANUAL / 源撤销 EXTERNAL）"
check "源撤销批次 void_source=EXTERNAL" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch WHERE external_system IN ('FARM_ERP','DEMO_ERP') AND void_flag=1 AND (void_source IS NULL OR void_source<>'EXTERNAL')")" "0"
check "源撤销批次 操作人写着养殖系统" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch WHERE external_system IN ('FARM_ERP','DEMO_ERP') AND void_flag=1 AND void_operator NOT LIKE '养殖系统%'")" "0"
check "人工作废批次 void_source=MANUAL" \
  "$(q "SELECT COUNT(*) FROM db_breeding.pig_batch WHERE external_system IS NULL AND void_flag=1 AND void_source IS NOT NULL AND void_source<>'MANUAL'")" "0"

# ---------------------------------------------------------------- 5 原始报文留档
echo "· 原始报文留档（判重与追责都靠它）"
nonzero "FARM_ERP 通道上报流水条数" \
  "$(q "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no='FARM-ERP-001'")"
check "上报流水都存了原始报文" \
  "$(q "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no='FARM-ERP-001' AND (payload IS NULL OR JSON_LENGTH(payload)=0)")" "0"
check "流水状态只出现 1/2/3（待人工/已入账/已拒绝）" \
  "$(q "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no='FARM-ERP-001' AND status NOT IN (1,2,3)")" "0"

# ---------------------------------------------------------------- 6 链上事件
echo "· 链上事件（源撤销必须留痕）"
check "源撤销事件 biz_key 以 :REVOKE 结尾" \
  "$(q "SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type='PIG_BATCH_VOID' AND biz_key LIKE '%:REVOKE' AND biz_key NOT LIKE '%:REVOKE%REVOKE%'")" \
  "$(q "SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type='PIG_BATCH_VOID' AND biz_key LIKE '%:REVOKE'")"

echo "──────────────────────────────────────────────────────────"
echo "结果：通过 $pass / 失败 $fail"

if [ "${1:-}" = "--clean" ]; then
  # 多表 DELETE 必须在「已选库」的连接里执行（跨库前缀在多表 DELETE 上会报
  # No database selected），所以拆成三条各自 USE 的调用 —— 这是本项目的既有坑。
  echo "· 清理本通道产生的数据（按业务键，不动人工登记批次）"
  $MYSQL -e "USE db_breeding;
    DELETE i FROM pig_individual i JOIN pig_batch b ON i.batch_id = b.id
     WHERE b.external_system IN ('FARM_ERP','DEMO_ERP');
    DELETE FROM pig_batch WHERE external_system IN ('FARM_ERP','DEMO_ERP');" 2>&1 | grep -v Warning
  $MYSQL -e "USE db_ingest; DELETE FROM ingest_staging WHERE device_no='FARM-ERP-001';" 2>&1 | grep -v Warning
  $MYSQL -e "USE db_common; DELETE FROM blockchain_record
              WHERE biz_type='PIG_BATCH_VOID' AND biz_key LIKE 'ERP-%:REVOKE';" 2>&1 | grep -v Warning
  echo "  剩余外部批次：$(q "SELECT COUNT(*) FROM db_breeding.pig_batch WHERE external_system IS NOT NULL")"
  echo "  剩余上报流水：$(q "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE device_no='FARM-ERP-001'")"
  echo "  剩余撤销链上事件：$(q "SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type='PIG_BATCH_VOID' AND biz_key LIKE 'ERP-%:REVOKE'")"
fi

exit $([ "$fail" -eq 0 ] && echo 0 || echo 1)
