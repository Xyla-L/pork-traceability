#!/usr/bin/env bash
# 瘦肉精阻断（阳性整批停 + 未检不得屠宰，只约束宰后类）—— 落库断言（与 logs/verify-ractopamine-block.mjs 配套）。
#
# 为什么单独一个脚本：本环境的 node 禁止 child_process，mjs 只做 HTTP 断言；
# 「被拦下的上报到底有没有偷偷写进业务表」「阳性有没有照常留证」「上链有没有留痕」
# 这些必须落到库上看。
#
# 运行：bash logs/verify-ractopamine-block-db.sh <阴性猪id> <阳性猪id> <同批猪id> <阳性耳标> <阳性批次号> <未检猪id> <未检猪耳标>
# 参数取自 verify-ractopamine-block.mjs 输出里的「清理用」行。
set -euo pipefail

PIG_A="${1:?用法: $0 <阴性猪id> <阳性猪id> <同批猪id> <阳性耳标> <阳性批次号> <未检猪id> <未检猪耳标>}"
PIG_B="${2:?缺参数}"
PIG_C="${3:?缺参数}"
EAR_TAG_B="${4:?缺参数}"
BATCH_Y="${5:?缺参数}"
PIG_D="${6:?缺参数}"
EAR_TAG_D="${7:?缺参数}"

rows=$(docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N -e "
SELECT 'racto_positive', COUNT(*) FROM db_slaughter.ractopamine_test WHERE pig_id=${PIG_B} AND result=0 AND void_flag=0;
SELECT 'racto_source', IFNULL(source,'NULL') FROM db_slaughter.ractopamine_test WHERE pig_id=${PIG_B} AND result=0 AND void_flag=0 LIMIT 1;
SELECT 'racto_source_ref', IFNULL(source_ref,'NULL') FROM db_slaughter.ractopamine_test WHERE pig_id=${PIG_B} AND result=0 AND void_flag=0 LIMIT 1;
SELECT 'racto_raw', IF(raw_payload IS NULL,'EMPTY','PRESENT') FROM db_slaughter.ractopamine_test WHERE pig_id=${PIG_B} AND result=0 AND void_flag=0 LIMIT 1;
SELECT 'racto_test_no', IFNULL(test_no,'NULL') FROM db_slaughter.ractopamine_test WHERE pig_id=${PIG_B} AND result=0 AND void_flag=0 LIMIT 1;
SELECT 'racto_negative_c', COUNT(*) FROM db_slaughter.ractopamine_test WHERE pig_id=${PIG_C} AND result=1 AND void_flag=0;
SELECT 'racto_untested', COUNT(*) FROM db_slaughter.ractopamine_test WHERE pig_id=${PIG_D} AND void_flag=0;
SELECT 'insp_negative', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_A} AND void_flag=0;
SELECT 'insp_pre_negative', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_A} AND inspect_type=1 AND result=1 AND void_flag=0;
SELECT 'insp_post_negative', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_A} AND inspect_type=2 AND result=1 AND void_flag=0;
SELECT 'insp_negative_result', IFNULL(MIN(result),-1) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_A} AND void_flag=0;
SELECT 'insp_positive_pre_fail', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_B} AND inspect_type=1 AND result=0 AND void_flag=0;
SELECT 'insp_positive_post', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_B} AND inspect_type IN (2,3) AND void_flag=0;
SELECT 'insp_same_batch', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_C} AND void_flag=0;
SELECT 'insp_untested_pre_pass', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_D} AND inspect_type=1 AND result=1 AND void_flag=0;
SELECT 'insp_untested_post', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_D} AND inspect_type IN (2,3) AND void_flag=0;
SELECT 'stamp_negative', COUNT(*) FROM db_slaughter.carcass_stamp WHERE pig_id=${PIG_A} AND void_flag=0;
SELECT 'stamp_positive', COUNT(*) FROM db_slaughter.carcass_stamp WHERE pig_id=${PIG_B} AND void_flag=0;
SELECT 'stamp_untested', COUNT(*) FROM db_slaughter.carcass_stamp WHERE pig_id=${PIG_D} AND void_flag=0;
SELECT 'carcass_batch_positive', COUNT(*) FROM db_distribution.carcass_batch WHERE JSON_CONTAINS(pig_ids, CAST(${PIG_B} AS JSON)) AND void_flag=0;
SELECT 'carcass_batch_untested', COUNT(*) FROM db_distribution.carcass_batch WHERE JSON_CONTAINS(pig_ids, CAST(${PIG_D} AS JSON)) AND void_flag=0;
SELECT 'carcass_batch_negative', COUNT(*) FROM db_distribution.carcass_batch WHERE JSON_CONTAINS(pig_ids, CAST(${PIG_A} AS JSON)) AND void_flag=0;
SELECT 'pig_status_negative', IFNULL(MAX(status),-1) FROM db_breeding.pig_individual WHERE id=${PIG_A};
SELECT 'pig_positive_slaughtered', IFNULL((SELECT IF(status=3,'YES','NO') FROM db_breeding.pig_individual WHERE id=${PIG_B}), 'MISSING');
SELECT 'pig_untested_slaughtered', IFNULL((SELECT IF(status=3,'YES','NO') FROM db_breeding.pig_individual WHERE id=${PIG_D}), 'MISSING');
SELECT 'rejected_total', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND (error_msg LIKE CONCAT('%','${EAR_TAG_B}','%') OR error_msg LIKE CONCAT('%','${BATCH_Y}','%'));
SELECT 'rejected_positive_msg', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_TAG_B}','%') AND error_msg LIKE '%瘦肉精检测结果阳性%';
SELECT 'rejected_batch_msg', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${BATCH_Y}','%') AND error_msg LIKE '%批次%';
SELECT 'rejected_no_target', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND (error_msg LIKE CONCAT('%','${EAR_TAG_B}','%') OR error_msg LIKE CONCAT('%','${BATCH_Y}','%')) AND target_id IS NULL;
SELECT 'rejected_untested_msg', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_TAG_D}','%') AND error_msg LIKE '%尚无瘦肉精检测记录%';
SELECT 'rejected_untested_no_target', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_TAG_D}','%') AND target_id IS NOT NULL;
SELECT 'rejected_pre_pass_msg', COUNT(*) FROM db_ingest.ingest_staging WHERE status=3 AND error_msg LIKE CONCAT('%','${EAR_TAG_B}','%') AND error_msg LIKE '%宰前检查不得判定为%';
SELECT 'chain_inspection', COUNT(*) FROM db_common.blockchain_record WHERE biz_type='SLAUGHTER_INSPECT' AND biz_id IN (SELECT id FROM db_slaughter.slaughter_inspection WHERE pig_id=${PIG_A});
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

echo "--- 阳性结果：照常落库留证（不是「不入库」，而是「入库并阻断」）---"
expect "阳性检测记录已落库 1 条" "$(get racto_positive)" "1"
expect "来源标记为设备采集" "$(get racto_source)" "DEVICE"
expect "读数仪设备编号（READER-001）" "$(get racto_source_ref)" "READER-001"
expect "保留原始报文" "$(get racto_raw)" "PRESENT"
expect "检测编号是系统生成的 RT 前缀" "$(get racto_test_no | cut -c1-2)" "RT"
expect "同批次的 C 猪阴性记录也落了库" "$(get racto_negative_c)" "1"
expect "★ 未检组的 D 猪一条检测记录都没有（它被拒的原因正是「没检过」）" "$(get racto_untested)" "0"

echo "--- 屠宰检验：阴性组放行，阳性组与同批次一律没写进业务表 ---"
echo "    （A 会落两条：1 条宰前合格 + 1 条宰后合格——宰后检验另有「未做宰前检查不得屠宰」这道门槛）"
expect "阴性猪有 1 条合格的宰前检查" "$(get insp_pre_negative)" "1"
expect "阴性猪有 1 条合格的宰后检验" "$(get insp_post_negative)" "1"
expect "阴性猪共 2 条检验记录（宰前 + 宰后）" "$(get insp_negative)" "2"
expect "阴性猪检验结论全为合格(1)" "$(get insp_negative_result)" "1"
expect "★ 阳性猪：宰前检查判「不合格」照常入账留证（瘦肉精门槛已不拦宰前）" "$(get insp_positive_pre_fail)" "1"
expect "★ 阳性猪：一条宰后检验都没有（瘦肉精闸门在宰后入口仍然生效）" "$(get insp_positive_post)" "0"
expect "★ 同批次的 C 猪一条屠宰检验都没有（判宰前合格被拒，故无任何记录）" "$(get insp_same_batch)" "0"
expect "★ 未检的 D 猪：宰前检查能入账（未检不拦宰前）" "$(get insp_untested_pre_pass)" "1"
expect "★ 未检的 D 猪：一条宰后检验都没有（未检闸门在宰后入口仍然生效）" "$(get insp_untested_post)" "0"

echo "--- 胴体盖章与胴体批次 ---"
expect "阴性猪有 1 条盖章记录" "$(get stamp_negative)" "1"
expect "★ 阳性猪一条盖章记录都没有" "$(get stamp_positive)" "0"
expect "★ 未检的 D 猪一条盖章记录都没有" "$(get stamp_untested)" "0"
expect "★ 阳性猪没进任何胴体批次" "$(get carcass_batch_positive)" "0"
expect "★ 未检的 D 猪没进任何胴体批次" "$(get carcass_batch_untested)" "0"
expect "阴性猪正常建成 1 个胴体批次" "$(get carcass_batch_negative)" "1"
expect "阴性猪状态已推进为已屠宰(3)" "$(get pig_status_negative)" "3"
expect "★ 阳性猪没有被推进为已屠宰（阻断发生在写入前，猪状态没动）" "$(get pig_positive_slaughtered)" "NO"
expect "★ 未检的 D 猪也没有被推进为已屠宰" "$(get pig_untested_slaughtered)" "NO"

echo "--- 被拦下的上报：状态与原因都留痕，且没偷偷写入业务表 ---"
atleast "被拒的屠宰/盖章流水数 ≥ 4（B 宰后 / C 宰后 / B 盖章 / B 判宰前合格；C 判宰前合格那条文案不含批次号，不计入本统计）" "$(get rejected_total)" 4
atleast "其中写明「瘦肉精检测结果阳性」的 ≥ 1" "$(get rejected_positive_msg)" 1
atleast "★ 其中因「宰前检查不得判定为合格」被拒的 ≥ 1（阳性猪不得判宰前合格）" "$(get rejected_pre_pass_msg)" 1
atleast "其中写明「批次」受控的 ≥ 1" "$(get rejected_batch_msg)" 1
expect "被拒流水一律没有业务记录编号（没偷偷写库）" "$(get rejected_no_target)" "$(get rejected_total)"
atleast "★ 未检猪被拒的流水 ≥ 2（屠宰检验 + 盖章）" "$(get rejected_untested_msg)" 2
expect "★ 未检猪被拒的流水同样没有业务记录编号" "$(get rejected_untested_no_target)" "0"
atleast "阴性猪的屠宰检验已上链留痕 ≥ 1" "$(get chain_inspection)" 1

echo
echo "===== 落库核验：${pass} 项通过，${fail} 项失败 ====="
[ "$fail" -eq 0 ]
