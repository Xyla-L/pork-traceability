#!/usr/bin/env bash
# 分割作业回归的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-split-job-db.sh
#   1) 从库里挑一条「有猪、有重量」的胴体批次当被测对象（不新建数据，测完不改它）
#   2) 跑 logs/verify-split-job.mjs（HTTP 层断言）
#   3) 按作业号/部位号格式、出肉率对账结论、不变量逐条核验
#   4) 清理本次产生的分割作业 / 部位记录 / 上链记录 / 接入流水
#
# 断言口径：作业与部位全部靠落库事实判断（入账回执按既定口径只报「已入账」，业务明细不外露）。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 分割作业落库核验 ==="

# ── 0) 挑一条有猪、有重量的胴体批次 ──
ROW=$(MYSQL "SELECT b.batch_no, ROUND(b.total_weight_kg / b.pig_count, 2),
                    (SELECT p.pig_id FROM db_distribution.carcass_batch_pig p WHERE p.batch_id = b.id ORDER BY p.pig_id LIMIT 1)
             FROM db_distribution.carcass_batch b
             WHERE b.pig_count > 0 AND b.total_weight_kg > 0 AND IFNULL(b.void_flag, 0) = 0
             ORDER BY b.id DESC LIMIT 1;" | tr '\t' ' ')
BATCH_NO=$(echo "$ROW" | awk '{print $1}')
AVG_W=$(echo "$ROW" | awk '{print $2}')
PIG_ID=$(echo "$ROW" | awk '{print $3}')

if [ -z "$BATCH_NO" ] || [ -z "$AVG_W" ]; then
  echo "  没找到「有猪且有重量」的胴体批次，无法测出肉率对账；请先归集或人工建一条批次。"
  exit 1
fi
EAR_TAG=$(MYSQL "SELECT ear_tag_no FROM db_breeding.pig_individual WHERE id = ${PIG_ID:-0};" | head -1)
echo "  被测胴体批次：${BATCH_NO}（单头均重 ${AVG_W}kg，样本猪 id=${PIG_ID}，耳标 ${EAR_TAG:-无}）"

START_TS=$(MYSQL "SELECT NOW();" | head -1)
BEFORE_JOBS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job;")
BEFORE_ITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch;")
BEFORE_JOB_LINKED=$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch WHERE create_time < '$START_TS' AND job_id IS NOT NULL;")
echo "  水位：分割作业 ${BEFORE_JOBS} / 分割记录 ${BEFORE_ITEMS} / 历史已挂作业记录 ${BEFORE_JOB_LINKED}；测试起点 ${START_TS}"

# ── 0.5) 跑前清场 ──
# 接入层有两道重复上报拦截（同设备 + 同 bizKey；同设备 + 同通道 + 同一份报文）。
# 上一次回归留下的同一条报文会让本次被判重 —— 回执照样返回 accepted，业务侧却完全不落库，
# 症状是「HTTP 断言全绿、落库断言全空」。回归要能反复跑，就必须先清掉自己上次的流水。
PRE=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE channel = 'SPLIT' AND create_time >= CURDATE();")
if [ "$PRE" != "0" ]; then
  MYSQL "DELETE FROM db_ingest.ingest_staging WHERE channel = 'SPLIT' AND create_time >= CURDATE();"
  echo "  跑前清掉 SPLIT 通道今日残留流水 ${PRE} 条（否则会被判重复上报、不落库）"
fi

# ── 1) 跑 HTTP 回归 ──
OUT=$(cd "$ROOT" && "$NODE" logs/verify-split-job.mjs "$BATCH_NO" "$AVG_W" "$EAR_TAG" 2>&1)
echo "$OUT" | sed 's/^/  /'
STAGING=$(echo "$OUT" | grep -o '# STAGING=.*' | sed 's/# STAGING=//')
HTTP_FAIL=$(echo "$OUT" | grep -o 'HTTP 断言：通过 [0-9]* / 失败 [0-9]*' | grep -o '失败 [0-9]*' | grep -o '[0-9]*')
echo

# ── 2) 落库断言 ──
# 期望条数：r1(5部位) r2/r3a/r3b(各1部位) r5(2) r6(3) r7(3) = 7 份作业 / 16 个部位；
# 带耳标时 r13 再加 1 份作业 / 5 个部位。转人工与拒绝的用例不入账，不计。
EXTRA=0
[ -n "${EAR_TAG:-}" ] && EXTRA=1
EXPECT_JOBS=$((7 + EXTRA))
EXPECT_ITEMS=$((16 + EXTRA * 5))
W="create_time >= '$START_TS'"

JOBS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W;")
ITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch WHERE $W;")
eq "本次新建分割作业数" "$JOBS" "$EXPECT_JOBS"
eq "本次新建部位记录数" "$ITEMS" "$EXPECT_ITEMS"
eq "HTTP 层断言失败数" "${HTTP_FAIL:-0}" "0"

# 2.1 作业号 / 部位号格式（SV+8位日期-3位流水；部位号挂在作业号下 -2位序号）
eq "作业号格式合规" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND job_no NOT REGEXP '^SV[0-9]{8}-[0-9]{3}$';")" "0"
eq "作业号无重复" "$((JOBS - $(MYSQL "SELECT COUNT(DISTINCT job_no) FROM db_distribution.split_job WHERE $W;")))" "0"
eq "作业号当日流水号无重复" \
   "$((JOBS - $(MYSQL "SELECT COUNT(DISTINCT SUBSTRING_INDEX(job_no, '-', -1)) FROM db_distribution.split_job WHERE $W;")))" "0"
eq "部位号格式合规" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch WHERE $W AND batch_no NOT REGEXP '^SP[0-9]{8}-[0-9]{3}-[0-9]{2}$';")" "0"

# 2.2 不变量：每份作业的 item_count == 部位行数，total_weight_kg == 部位重量合计
eq "item_count 与部位行数不一致的作业" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job j WHERE j.$W AND j.item_count <> (SELECT COUNT(*) FROM db_distribution.split_batch b WHERE b.job_id = j.id);")" "0"
eq "total_weight_kg 与部位合计不一致的作业" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job j WHERE j.$W AND ABS(IFNULL(j.total_weight_kg, 0) - IFNULL((SELECT SUM(b.weight_kg) FROM db_distribution.split_batch b WHERE b.job_id = j.id), 0)) > 0.01;")" "0"
eq "出肉率与「合计/基准」不一致的作业" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job j WHERE j.$W AND j.parent_weight_kg > 0 AND ABS(j.total_yield_rate - ROUND(j.total_weight_kg / j.parent_weight_kg * 100, 2)) > 0.05;")" "0"
eq "挂作业但缺部位编码的记录" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch WHERE $W AND (job_id IS NULL OR part_code IS NULL);")" "0"
eq "部位编码不在字典内的记录" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch b WHERE b.$W AND b.part_code NOT IN (SELECT part_code FROM db_distribution.meat_part);")" "0"
eq "来源与来源设备不符的部位记录" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch WHERE $W AND NOT (source = 'DEVICE' AND source_ref = 'SPLIT-001');")" "0"

# 2.3 出肉率对账结论
eq "整作业（5 部位 ≈91.7%）判「通过」" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND item_count = 5 AND check_status = 0;")" "$((1 + EXTRA))"
eq "缺必出部位 → 待人工复核" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND item_count = 2 AND check_status = 1 AND check_note LIKE '%缺少必出部位%';")" "1"
eq "总重超上限 → 待人工复核" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND item_count = 3 AND check_status = 1 AND check_note LIKE '%超过上限%';")" "1"
eq "总重低于下限 → 待人工复核" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND item_count = 3 AND check_status = 1 AND check_note LIKE '%低于下限%';")" "1"
# 单条兼容模式一次只报一个部位，注定缺必出项 —— 「入账 + 转人工复核」才是正确语义
# （分割事实已发生必须留证，同时提示这台设备的行为可疑）
eq "单条兼容三种形态均入账且因缺必出部位转人工" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND item_count = 1 AND check_status = 1 AND check_note LIKE '%缺少必出部位%';")" "3"
eq "单条兼容下「P20 / 二分体 / 带皮白条」都反查命中 P20" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch b JOIN db_distribution.split_job j ON b.job_id = j.id WHERE j.$W AND j.item_count = 1 AND b.part_code = 'P20';")" "3"
eq "对账通过时 check_note 也是通过口径" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND check_status = 0 AND (check_note IS NULL OR check_note NOT LIKE '%出肉率核对通过%');")" "0"

# 2.4 对账基准来源
if [ -n "${EAR_TAG:-}" ]; then
  eq "带耳标 → 基准来源为 PIG（按这头白条精确对账）" \
     "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND weight_basis = 'PIG';")" "1"
  eq "其余作业按批次单头均重对账" \
     "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND weight_basis = 'BATCH_AVG' AND parent_weight_kg = $AVG_W;")" "$((EXPECT_JOBS - 1))"
else
  eq "全部作业按批次单头均重对账" \
     "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job WHERE $W AND weight_basis = 'BATCH_AVG' AND parent_weight_kg = $AVG_W;")" "$EXPECT_JOBS"
fi

# 2.5 历史记录未被本次回归改动。
#     旧口径是「存量历史记录一律不带 job_id」，但作业层上线后界面演示也会产生带 job_id 的记录
#     （如 2026-10-09 14:44 的 SV20261009-001），所以改成「本次之前已挂作业的条数保持不变」。
eq "历史已挂作业的分割记录未被本次回归改动" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch WHERE create_time < '$START_TS' AND job_id IS NOT NULL;")" "$BEFORE_JOB_LINKED"

# 2.6 上链：本次每份作业 + 每个部位各一条
eq "作业上链条数" \
   "$(MYSQL "SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type = 'SPLIT_JOB' AND create_time >= '$START_TS';")" "$EXPECT_JOBS"
eq "部位上链条数" \
   "$(MYSQL "SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type = 'SPLIT_BATCH' AND create_time >= '$START_TS';")" "$EXPECT_ITEMS"

# 2.7 部位字典口径：拆分树「未产出标准部位」对照的分母来自字典，且必须排除整条出货形态(WHOLE)
#     ——二分体是「要不要再分割」的问题，不是「切出了什么」，算进对照会把整条出货误报成缺十几个部位
DICT_ALL=$(MYSQL "SELECT COUNT(*) FROM db_distribution.meat_part WHERE enabled = 1;")
DICT_WHOLE=$(MYSQL "SELECT COUNT(*) FROM db_distribution.meat_part WHERE enabled = 1 AND category = 'WHOLE';")
eq "字典启用项 17 条" "$DICT_ALL" "17"
eq "整条出货形态(WHOLE) 1 条且不进对照清单" "$DICT_WHOLE" "1"
eq "对照口径分母 16 条" "$((DICT_ALL - DICT_WHOLE))" "16"
eq "必出项(required=1) 3 条" \
   "$(MYSQL "SELECT COUNT(*) FROM db_distribution.meat_part WHERE enabled = 1 AND required = 1;")" "3"

# ── 3) 清理 ──
echo
echo "  --- 清理本次回归数据 ---"
MYSQL "DELETE FROM db_common.blockchain_record WHERE biz_type IN ('SPLIT_JOB', 'SPLIT_BATCH') AND create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.split_batch WHERE create_time >= '$START_TS';"
MYSQL "DELETE FROM db_distribution.split_job WHERE create_time >= '$START_TS';"
if [ -n "$STAGING" ]; then
  MYSQL "DELETE FROM db_ingest.ingest_staging WHERE id IN ($STAGING);"
fi
# 按设备 + 时间收尾：不依赖 mjs 回传的 stagingId（入账类回执未必带它）
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE channel = 'SPLIT' AND create_time >= '$START_TS';"

AFTER_JOBS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_job;")
AFTER_ITEMS=$(MYSQL "SELECT COUNT(*) FROM db_distribution.split_batch;")
eq "作业数回到基线" "$AFTER_JOBS" "$BEFORE_JOBS"
eq "分割记录数回到基线" "$AFTER_ITEMS" "$BEFORE_ITEMS"

echo
echo "=== 结果：通过 $PASS / 失败 $FAIL ==="
[ "$FAIL" -eq 0 ]
