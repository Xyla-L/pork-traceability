#!/usr/bin/env bash
# 「未做宰前检查不得屠宰」+「盖章须有合格宰后检验」—— 落库断言（与 logs/verify-preslaughter-gate.mjs 配套）。
#
# 为什么单独一个脚本：本环境的 node 禁止 child_process，mjs 只做 HTTP 断言；
# 「被拦下的上报到底有没有偷偷写进业务表」「宰前不合格的记录有没有照常留证」「猪状态有没有被误推进」
# 这些必须落到库上看。
#
# 运行：bash logs/verify-preslaughter-gate-db.sh <E猪id> <F猪id> <G猪id> <H猪id> <E耳标> <F耳标> <H耳标>
# 参数取自 verify-preslaughter-gate.mjs 输出里的「清理用」行。
set -euo pipefail

PIG_E="${1:?用法: $0 <E猪id> <F猪id> <G猪id> <H猪id> <E耳标> <F耳标> <H耳标>}"
PIG_F="${2:?缺参数}"
PIG_G="${3:?缺参数}"
PIG_H="${4:?缺参数}"
EAR_E="${5:?缺参数}"
EAR_F="${6:?缺参数}"
EAR_H="${7:?缺参数}"

rows=$(docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N -e "
SELECT 'e_total', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_E} AND void_flag=0;
SELECT 'e_pre_passed', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_E} AND inspect_type=1 AND result=1 AND void_flag=0;
SELECT 'e_post', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_E} AND inspect_type=2 AND void_flag=0;
SELECT 'e_sync_rejected_nowrite', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_E} AND inspect_type=3 AND void_flag=0;
SELECT 'f_total', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_F} AND void_flag=0;
SELECT 'f_pre_result', IFNULL(MAX(result),-1) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_F} AND inspect_type=1 AND void_flag=0;
SELECT 'f_pre_conclusion', IFNULL(MAX(conclusion),'NULL') FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_F} AND inspect_type=1 AND void_flag=0;
SELECT 'f_post', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_F} AND inspect_type=2 AND void_flag=0;
SELECT 'g_pre', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_G} AND inspect_type=1 AND result=1 AND void_flag=0;
SELECT 'g_post', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_G} AND inspect_type=2 AND result=1 AND void_flag=0;
SELECT 'g_sync', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_G} AND inspect_type=3 AND result=1 AND void_flag=0;
SELECT 'h_pre', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_H} AND inspect_type=1 AND result=1 AND void_flag=0;
SELECT 'h_post', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_H} AND inspect_type IN (2,3) AND void_flag=0;
SELECT 'stamp_g', COUNT(*) FROM db_slaughter.carcass_stamp WHERE pig_id=${PIG_G} AND void_flag=0;
SELECT 'stamp_h', COUNT(*) FROM db_slaughter.carcass_stamp WHERE pig_id=${PIG_H} AND void_flag=0;
SELECT 'pig_e_status', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${PIG_E}), 'MISSING');
SELECT 'pig_f_status', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${PIG_F}), 'MISSING');
SELECT 'pig_h_status', IFNULL((SELECT IF(status=3,'SLAUGHTERED','OTHER') FROM db_breeding.pig_individual WHERE id=${PIG_H}), 'MISSING');
SELECT 'rej_e_nopre', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_E}','%') AND error_msg LIKE '%尚未做宰前检查%';
SELECT 'rej_e_nopre_target', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_E}','%') AND error_msg LIKE '%尚未做宰前检查%' AND target_id IS NOT NULL;
SELECT 'rej_e_nopre_blocked_msg', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_E}','%') AND error_msg LIKE '%未做宰前检查不得屠宰%';
SELECT 'rej_f_failedpre', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_F}','%') AND error_msg LIKE '%宰前检查结论为不合格%';
SELECT 'rej_f_failedpre_target', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_F}','%') AND error_msg LIKE '%宰前检查结论为不合格%' AND target_id IS NOT NULL;
SELECT 'rej_h_stamp', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_H}','%') AND error_msg LIKE '%宰后检验记录%';
SELECT 'rej_h_stamp_target', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_H}','%') AND error_msg LIKE '%宰后检验记录%' AND target_id IS NOT NULL;
SELECT 'chain_e', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='SLAUGHTER_INSPECT' AND biz_id IN (SELECT id FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_E});
SELECT 'chain_g', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='SLAUGHTER_INSPECT' AND biz_id IN (SELECT id FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_G});
" 2>/dev/null)

pass=0; fail=0
expect() { # name  haystack  needle
  if printf '%s' "$2" | grep -qF -- "$3"; then pass=$((pass+1)); echo "  [PASS] $1";
  else fail=$((fail+1)); echo "  [FAIL] $1 —— 实际值: $2"; fi
}
get() { printf '%s\n' "$rows" | awk -F'\t' -v k="$1" '$1==k{print $2}'; }
atleast() { # name actual min
  if [ "${2:-0}" -ge "${3}" ] 2>/dev/null; then pass=$((pass+1)); echo "  [PASS] $1 —— 实际 ${2}";
  else fail=$((fail+1)); echo "  [FAIL] $1 —— 实际 ${2}（期望 ≥ ${3}）"; fi
}

echo "--- E（没做宰前检查）：被拦的两次一条都没写进业务表，补做宰前后才落库 ---"
expect "★ E 报宰后(2) 与宰后同步(3) 都被拦，一次都没写进检验表" "$(get e_sync_rejected_nowrite)" "0"
expect "E 补做宰前检查（合格）后落了 1 条" "$(get e_pre_passed)" "1"
expect "E 补做宰前后，宰后检验才落库 1 条" "$(get e_post)" "1"
expect "E 最终只有 2 条检验记录（1 宰前 + 1 宰后）" "$(get e_total)" "2"

echo "--- F（宰前检查判不合格）：不合格结论照常留证，但不得进入宰后 ---"
expect "F 的宰前检查记录落了库" "$(get f_total)" "1"
expect "F 的宰前检查结论是不合格(0)" "$(get f_pre_result)" "0"
expect "★ F 一条宰后检验都没有" "$(get f_post)" "0"

echo "--- G（宰前合格）：宰后检验与宰后同步检验各自落库 ---"
expect "G 有 1 条合格的宰前检查" "$(get g_pre)" "1"
expect "G 有 1 条合格的宰后检验(2)" "$(get g_post)" "1"
expect "G 有 1 条合格的宰后同步检验(3)" "$(get g_sync)" "1"

echo "--- ★ 盖章：只有宰前合格不算数，必须有合格的宰后检验 ---"
expect "H 有 1 条合格的宰前检查" "$(get h_pre)" "1"
expect "★ H 一条宰后检验都没有" "$(get h_post)" "0"
expect "★ H 一条盖章记录都没有（只有宰前合格不够）" "$(get stamp_h)" "0"
expect "G（已有合格宰后检验）正常盖章 1 条" "$(get stamp_g)" "1"

echo "--- 猪状态：只有真的过了宰后的才推进为已屠宰 ---"
expect "E 状态已推进为已屠宰" "$(get pig_e_status)" "SLAUGHTERED"
expect "★ F 没被推进为已屠宰（阻断发生在写入前）" "$(get pig_f_status)" "OTHER"
expect "★ H 没被推进为已屠宰（只有宰前、没有宰后）" "$(get pig_h_status)" "OTHER"

echo "--- 被拦下的上报：原因留痕，且没偷偷写入业务表 ---"
atleast "★ E 因「尚未做宰前检查」被拒的流水 ≥ 2（宰后 + 宰后同步）" "$(get rej_e_nopre)" 2
expect "★ E 被拒的流水一律没有业务记录编号" "$(get rej_e_nopre_target)" "0"
atleast "其中写明「未做宰前检查不得屠宰」的 ≥ 2" "$(get rej_e_nopre_blocked_msg)" 2
atleast "★ F 因「宰前检查结论为不合格」被拒的流水 ≥ 1" "$(get rej_f_failedpre)" 1
expect "★ F 被拒的流水同样没有业务记录编号" "$(get rej_f_failedpre_target)" "0"
atleast "★ H 盖章被拒的流水 ≥ 1（原因写明没有合格的宰后检验）" "$(get rej_h_stamp)" 1
expect "★ H 被拒的盖章流水没有业务记录编号" "$(get rej_h_stamp_target)" "0"

echo "--- 上链留痕 ---"
atleast "E 的检验记录已上链 ≥ 2（宰前 + 宰后）" "$(get chain_e)" 2
atleast "G 的检验记录已上链 ≥ 3（宰前 + 宰后 + 同步）" "$(get chain_g)" 3

echo
echo "===== 落库核验：${pass} 项通过，${fail} 项失败 ====="
[ "$fail" -eq 0 ]
