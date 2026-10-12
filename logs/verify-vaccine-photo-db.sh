#!/usr/bin/env bash
# 免疫注射凭证照片链路 —— 落库断言（与 logs/verify-vaccine-photo.mjs 配套）。
#
# 为什么单独一个脚本：本环境的 node 禁止 child_process（spawnSync 一律 EBUSY），
# 所以 verify-vaccine-photo.mjs 只做 HTTP 断言并把 targetId/stagingId 打出来，
# 落库核验（凭证、实名、备注留痕、原始报文不可变）在这里用 docker exec mysql 完成。
#
# 运行：bash logs/verify-vaccine-photo-db.sh <已入账记录ID> <人工补齐记录ID> <stagingID> <fileId>
# ID 与 fileId 用 verify-vaccine-photo.mjs 输出里的 [INFO] 行。
set -euo pipefail

REC_AUTO="${1:?用法: $0 <自动入账记录ID> <人工补齐记录ID> <stagingID> <fileId>}"
REC_MANUAL="${2:?缺参数}"
STAGING="${3:?缺参数}"
FILE_ID="${4:?缺参数}"

rows=$(docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N -e "
SELECT 'auto_operator', operator FROM db_breeding.vaccine_record WHERE id=${REC_AUTO};
SELECT 'auto_file_ids', file_ids  FROM db_breeding.vaccine_record WHERE id=${REC_AUTO};
SELECT 'manual_operator', operator FROM db_breeding.vaccine_record WHERE id=${REC_MANUAL};
SELECT 'manual_file_ids', file_ids  FROM db_breeding.vaccine_record WHERE id=${REC_MANUAL};
SELECT 'staging_remark', remark FROM db_ingest.ingest_staging WHERE id=${STAGING};
SELECT 'staging_payload_wang', IF(payload LIKE '%王复核%','LEAKED','immutable') FROM db_ingest.ingest_staging WHERE id=${STAGING};
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

expect "自动入账记录操作人为上报的兽医（刘兽医）" "$(get auto_operator)" "刘兽医"
expect "自动入账记录已落凭证照片" "$(get auto_file_ids)" "$FILE_ID"
expect "人工补齐记录落到补录实名（王复核）" "$(get manual_operator)" "王复核"
expect "人工补齐记录带上补传的照片" "$(get manual_file_ids)" "$FILE_ID"
expect "处理备注留痕人工补齐内容" "$(get staging_remark)" "人工补齐"
expect_no "设备原始报文保持不可变（不含补录姓名）" "$(get staging_payload_wang)" "LEAKED"

echo ""
echo "结果：${pass} 通过，${fail} 失败"
[ "$fail" -eq 0 ]
