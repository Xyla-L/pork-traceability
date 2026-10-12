#!/usr/bin/env bash
# 入场查验「出栏审批前置」—— 落库断言（与 logs/verify-entry-gate.mjs 配套）。
#
# 为什么单独一个脚本：本环境的 node 禁止 child_process（spawnSync 一律 EBUSY），
# verify-entry-gate.mjs 只做 HTTP 断言并把业务记录 ID 打出来，
# 落库核验（未审批的上报到底有没有写进业务表、来源三件套、人工确认有没有偷偷放行）在这里做。
#
# 运行：bash logs/verify-entry-gate-db.sh <临时猪id> <出栏申报id> <入场查验id> <耳标号> <待人工处理的流水id>
# 参数取自 verify-entry-gate.mjs 输出里的「清理用」行。
set -euo pipefail

PIG_ID="${1:?用法: $0 <临时猪id> <出栏申报id> <入场查验id> <耳标号> <待人工处理的流水id>}"
APPLY_ID="${2:?缺参数}"
ENTRY_ID="${3:?缺参数}"
EAR_TAG="${4:?缺参数}"
MANUAL_STAGING="${5:?缺参数}"

rows=$(docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N -e "
SELECT 'entry_source', source FROM db_slaughter.entry_inspection WHERE id=${ENTRY_ID};
SELECT 'entry_source_ref', source_ref FROM db_slaughter.entry_inspection WHERE id=${ENTRY_ID};
SELECT 'entry_raw', IF(raw_payload IS NULL,'EMPTY','PRESENT') FROM db_slaughter.entry_inspection WHERE id=${ENTRY_ID};
SELECT 'entry_inspector', inspector FROM db_slaughter.entry_inspection WHERE id=${ENTRY_ID};
SELECT 'entry_total_pig', COUNT(*) FROM db_slaughter.entry_inspection WHERE pig_id=${PIG_ID};
SELECT 'entry_blocked_a', COUNT(*) FROM db_slaughter.entry_inspection WHERE pig_id=${PIG_ID} AND batch_no LIKE '%-A';
SELECT 'entry_blocked_b', COUNT(*) FROM db_slaughter.entry_inspection WHERE pig_id=${PIG_ID} AND batch_no LIKE '%-B';
SELECT 'entry_blocked_c', COUNT(*) FROM db_slaughter.entry_inspection WHERE pig_id=${PIG_ID} AND batch_no LIKE '%-C';
SELECT 'apply_source', source FROM db_breeding.slaughter_apply WHERE id=${APPLY_ID};
SELECT 'apply_source_ref', source_ref FROM db_breeding.slaughter_apply WHERE id=${APPLY_ID};
SELECT 'apply_status', approval_status FROM db_breeding.slaughter_apply WHERE id=${APPLY_ID};
SELECT 'apply_approver', IFNULL(approver,'NULL') FROM db_breeding.slaughter_apply WHERE id=${APPLY_ID};
SELECT 'pig_status', status FROM db_breeding.pig_individual WHERE id=${PIG_ID};
SELECT 'cert_source', IFNULL(source,'NULL') FROM db_breeding.quarantine_certificate WHERE pig_id=${PIG_ID} AND void_flag=0;
SELECT 'cert_source_ref', IFNULL(source_ref,'NULL') FROM db_breeding.quarantine_certificate WHERE pig_id=${PIG_ID} AND void_flag=0;
SELECT 'manual_status', status FROM db_ingest.ingest_staging WHERE id=${MANUAL_STAGING};
SELECT 'manual_err', IFNULL(error_msg,'') FROM db_ingest.ingest_staging WHERE id=${MANUAL_STAGING};
SELECT 'manual_target', IFNULL(target_id,'NULL') FROM db_ingest.ingest_staging WHERE id=${MANUAL_STAGING};
SELECT 'gate_rejected', COUNT(*) FROM db_ingest.ingest_staging WHERE channel='ENTRY' AND status=3 AND error_msg LIKE '%未经出栏审批通过不得入场查验%';
SELECT 'gate_rejected_terminal', COUNT(*) FROM db_ingest.ingest_staging WHERE channel='ENTRY' AND status=3 AND error_msg LIKE '%已不能再入场查验%';
" 2>/dev/null)

pass=0; fail=0
expect() { # name  haystack  needle
  if printf '%s' "$2" | grep -qF -- "$3"; then pass=$((pass+1)); echo "  [PASS] $1";
  else fail=$((fail+1)); echo "  [FAIL] $1 —— 实际值: $2"; fi
}
expect_no() {
  if printf '%s' "$2" | grep -qF -- "$3"; then fail=$((fail+1)); echo "  [FAIL] $1 —— 实际值: $2";
  else pass=$((pass+1)); echo "  [PASS] $1"; fi
}
get() { printf '%s\n' "$rows" | awk -F'\t' -v k="$1" '$1==k{print substr($0, index($0,$2))}' | sed 's/^[^\t]*\t//'; }

echo "--- 入场查验记录（只有审批通过后那一次该落库）---"
expect "入场查验来源标记为设备采集" "$(get entry_source)" "DEVICE"
expect "入场查验记录闸口设备编号（GATE-001）" "$(get entry_source_ref)" "GATE-001"
expect "入场查验保留原始报文" "$(get entry_raw)" "PRESENT"
expect "查验员落到具体人（张查验）" "$(get entry_inspector)" "张查验"
expect "★ 该猪只有 1 条入场查验记录（未审批的 3 次上报都没落库）" "$(get entry_total_pig)" "1"
expect "★ 未审批时的上报没有留下业务记录（批次 -A）" "$(get entry_blocked_a)" "0"
expect "★ 待审时的上报没有留下业务记录（批次 -B）" "$(get entry_blocked_b)" "0"
expect "★ 缺查验员的那次也没有留下业务记录（批次 -C）" "$(get entry_blocked_c)" "0"

echo "--- 出栏申报（审批通过后才有资格入场）---"
expect "出栏申报来源标记为设备采集" "$(get apply_source)" "DEVICE"
expect "出栏申报记录地磅编号（EXITSCALE-001）" "$(get apply_source_ref)" "EXITSCALE-001"
expect "出栏申报已审批通过" "$(get apply_status)" "1"
expect "审批人落到姓名（系统管理员）" "$(get apply_approver)" "系统管理员"
expect_no "审批人没有被编成问号" "$(get apply_approver)" "?"
expect "审批通过后生猪状态推进为已出栏(2)" "$(get pig_status)" "2"

echo "--- 检疫证与人工确认 ---"
expect "检疫证来源标记为设备采集" "$(get cert_source)" "DEVICE"
expect "检疫证记录扫码设备编号（QSCAN-001）" "$(get cert_source_ref)" "QSCAN-001"
expect "★ 人工确认的流水仍停在「待人工处理」" "$(get manual_status)" "1"
expect "★ 人工确认的失败原因写明「未经出栏审批通过不得入场查验」" "$(get manual_err)" "未经出栏审批通过不得入场查验"
expect "★ 人工确认没有偷偷写入业务表（target_id 为空）" "$(get manual_target)" "NULL"
rejected_n=$(get gate_rejected)
if [ "${rejected_n:-0}" -ge 3 ]; then pass=$((pass+1)); echo "  [PASS] 被出栏审批拦下的接入流水数 ≥ 3（无申报 / 待审 / 已驳回）—— 实际 ${rejected_n}";
else fail=$((fail+1)); echo "  [FAIL] 被出栏审批拦下的接入流水数 ≥ 3 —— 实际 ${rejected_n}"; fi
terminal_n=$(get gate_rejected_terminal)
if [ "${terminal_n:-0}" -ge 1 ]; then pass=$((pass+1)); echo "  [PASS] ★ 被「终态猪不得再入场」拦下的接入流水数 ≥ 1 —— 实际 ${terminal_n}";
else fail=$((fail+1)); echo "  [FAIL] ★ 被「终态猪不得再入场」拦下的接入流水数 ≥ 1 —— 实际 ${terminal_n}"; fi

echo ""
echo "结果：${pass} 通过，${fail} 失败"
[ "$fail" -eq 0 ]
