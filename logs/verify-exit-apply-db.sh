#!/usr/bin/env bash
# 出栏称重登记通道 + 审批实名 —— 落库断言（与 logs/verify-exit-apply-device.mjs 配套）。
#
# 为什么单独一个脚本：本环境的 node 禁止 child_process（spawnSync 一律 EBUSY），
# verify-exit-apply-device.mjs 只做 HTTP 断言并把业务记录 ID 打出来，
# 落库核验（来源三件套、审批人是否落到姓名、生猪状态、链上留痕）在这里做。
#
# 运行：bash logs/verify-exit-apply-db.sh <设备登记的申报ID> <人工申报的申报ID> <猪B的id>
# 三个 ID 用 verify-exit-apply-device.mjs 输出里的「清理用」行。
set -euo pipefail

APPLY_DEVICE="${1:?用法: $0 <设备登记的申报ID> <人工申报的申报ID> <猪B的id>}"
APPLY_MANUAL="${2:?缺参数}"
PIG_B="${3:?缺参数}"

rows=$(docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N -e "
SELECT 'dev_source', source FROM db_breeding.slaughter_apply WHERE id=${APPLY_DEVICE};
SELECT 'dev_source_ref', source_ref FROM db_breeding.slaughter_apply WHERE id=${APPLY_DEVICE};
SELECT 'dev_status', approval_status FROM db_breeding.slaughter_apply WHERE id=${APPLY_DEVICE};
SELECT 'dev_raw_payload', IF(raw_payload IS NULL,'EMPTY','PRESENT') FROM db_breeding.slaughter_apply WHERE id=${APPLY_DEVICE};
SELECT 'manual_source', source FROM db_breeding.slaughter_apply WHERE id=${APPLY_MANUAL};
SELECT 'manual_source_ref', source_ref FROM db_breeding.slaughter_apply WHERE id=${APPLY_MANUAL};
SELECT 'manual_approver', approver FROM db_breeding.slaughter_apply WHERE id=${APPLY_MANUAL};
SELECT 'manual_status', approval_status FROM db_breeding.slaughter_apply WHERE id=${APPLY_MANUAL};
SELECT 'pig_status', status FROM db_breeding.pig_individual WHERE id=${PIG_B};
SELECT 'chain_rows', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='SLAUGHTER_APPLY' AND biz_id=${APPLY_DEVICE};
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

expect "地磅登记来源标记为设备采集" "$(get dev_source)" "DEVICE"
expect "地磅登记记录设备编号（EXITSCALE-001）" "$(get dev_source_ref)" "EXITSCALE-001"
expect "地磅登记落为待审" "$(get dev_status)" "0"
expect "地磅登记保留原始报文" "$(get dev_raw_payload)" "PRESENT"
expect "人工申报来源标记为人工录入" "$(get manual_source)" "MANUAL"
expect "人工申报记录申报人实名（系统管理员）" "$(get manual_source_ref)" "系统管理员"
expect "审批人落到姓名而不是用户ID（系统管理员）" "$(get manual_approver)" "系统管理员"
expect_no "审批人没有被编成问号" "$(get manual_approver)" "?"
expect "人工申报已审批通过" "$(get manual_status)" "1"
expect "审批通过后生猪状态推进为已出栏(2)" "$(get pig_status)" "2"
expect "出栏申报有链上留痕" "$(get chain_rows)" "1"

echo ""
echo "结果：${pass} 通过，${fail} 失败"
[ "$fail" -eq 0 ]
