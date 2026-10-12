#!/usr/bin/env bash
# 清理 2026-10-10 15:16 之后「参数驱动回归」留下的残留。
#
# 背景：logs/verify-{entry-gate,preslaughter-gate,ractopamine-block,exit-apply}-db.sh 是
#       参数驱动 + **不自带清理**（ID 由对应 mjs 打印），跑完必须按 ID 段手工清；
#       它们还可能改动既有行（本轮把 ET-DEMO-1193435869 从「在养」推进成了「已出栏」）。
# 本脚本按时间窗（本轮唯一操作者就是回归本身）清理，并把被改动的既有行回滚。
set -u
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }
DUMP() { docker exec pork-traceability-mysql-1 mysqldump -uroot -pchange-this-root-password --default-character-set=utf8mb4 --no-create-info --skip-add-locks --skip-comments "$1" "$2" --where="$3" 2>/dev/null; }

T='2026-10-10 15:16:00'
TS=$(date +%Y%m%d-%H%M%S)
BAK="logs/backup/residue-4slaughter-${TS}.sql"

echo "=== 备份到 ${BAK} ==="
{
  echo "-- 2026-10-10 15:16 之后四个参数驱动回归的残留（清理前快照）"
  for spec in \
    "db_common blockchain_record create_time>='${T}'" \
    "db_common sys_user create_time>='${T}'" \
    "db_ingest ingest_staging create_time>='${T}'" \
    "db_ingest source_fallback_unlock apply_time>='${T}'" \
    "db_slaughter entry_inspection create_time>='${T}'" \
    "db_slaughter slaughter_inspection create_time>='${T}'" \
    "db_slaughter carcass_stamp create_time>='${T}'" \
    "db_slaughter ractopamine_test create_time>='${T}'" \
    "db_distribution carcass_batch create_time>='${T}'" \
    "db_distribution carcass_batch_pig create_time>='${T}'" \
    "db_breeding pig_individual create_time>='${T}'" \
    "db_breeding slaughter_apply create_time>='${T}'" \
    "db_breeding quarantine_certificate create_time>='${T}'" \
    "db_breeding vaccine_record create_time>='${T}'" ; do
    set -- $spec
    echo "-- ${1}.${2}"
    DUMP "$1" "$2" "$3"
  done
  echo "-- 被改动的既有行（清理前状态）"
  MYSQL "SELECT CONCAT('-- pig#33 status=', status, ' update_time=', update_time) FROM db_breeding.pig_individual WHERE id=33;"
} > "$BAK"
echo "  备份大小：$(wc -c < "$BAK") 字节"

echo
echo "=== 清理 ==="
MYSQL "DELETE FROM db_common.blockchain_record WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_sales.expire_warning WHERE pig_id IN (SELECT id FROM db_breeding.pig_individual WHERE create_time>='${T}');" >/dev/null 2>&1
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_ingest.source_fallback_unlock WHERE apply_time>='${T}';"
MYSQL "DELETE FROM db_common.sys_user_role WHERE user_id IN (SELECT id FROM (SELECT id FROM db_common.sys_user WHERE create_time>='${T}') x);"
MYSQL "DELETE FROM db_common.sys_user WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_slaughter.carcass_stamp WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_slaughter.slaughter_inspection WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_slaughter.ractopamine_test WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_slaughter.entry_inspection WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_distribution.carcass_batch_pig WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_distribution.carcass_batch WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_breeding.vaccine_record WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_breeding.quarantine_certificate WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_breeding.slaughter_apply WHERE create_time>='${T}';"
MYSQL "DELETE FROM db_breeding.pig_individual WHERE create_time>='${T}';"

echo "  --- 回滚被推进的既有行 ---"
MYSQL "UPDATE db_breeding.pig_individual SET status=1 WHERE id=33 AND status=2;"
echo "     pig#33 status → $(MYSQL "SELECT status FROM db_breeding.pig_individual WHERE id=33;")"

echo
echo "=== 清理后核对 ==="
echo "  残留 pig_individual      : $(MYSQL "SELECT COUNT(*) FROM db_breeding.pig_individual WHERE create_time>='${T}';")"
echo "  残留 slaughter_apply     : $(MYSQL "SELECT COUNT(*) FROM db_breeding.slaughter_apply WHERE create_time>='${T}';")"
echo "  残留 quarantine_cert     : $(MYSQL "SELECT COUNT(*) FROM db_breeding.quarantine_certificate WHERE create_time>='${T}';")"
echo "  残留 entry_inspection    : $(MYSQL "SELECT COUNT(*) FROM db_slaughter.entry_inspection WHERE create_time>='${T}';")"
echo "  残留 slaughter_inspection: $(MYSQL "SELECT COUNT(*) FROM db_slaughter.slaughter_inspection WHERE create_time>='${T}';")"
echo "  残留 carcass_stamp       : $(MYSQL "SELECT COUNT(*) FROM db_slaughter.carcass_stamp WHERE create_time>='${T}';")"
echo "  残留 carcass_batch       : $(MYSQL "SELECT COUNT(*) FROM db_distribution.carcass_batch WHERE create_time>='${T}';")"
echo "  残留 ingest_staging      : $(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE create_time>='${T}';")"
echo "  残留 blockchain_record   : $(MYSQL "SELECT COUNT(*) FROM db_common.blockchain_record WHERE create_time>='${T}';")"
echo "  残留 sys_user            : $(MYSQL "SELECT COUNT(*) FROM db_common.sys_user WHERE create_time>='${T}';")"
