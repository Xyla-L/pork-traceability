#!/usr/bin/env bash
# 重复上报「提示已存在 + 拒绝入账」的落库核验与清理（自带 mjs 驱动）。
#
# 用法：bash logs/verify-duplicate-reject-db.sh
#   1) 跑 logs/verify-duplicate-reject.mjs（HTTP 层断言：同 bizKey / 换号同报文 /
#      已入账重推 → status=5、accepted=false；报文变了要能正常新建）
#   2) 在数据还在库里时核验落库事实：
#        - 重复上报**没有**新建暂存行（队列条数只增了「真新数据」那几条）
#        - 全库不存在 status=5 的行（5 只是回执状态，不入队列）
#        - (device_no, biz_key) 没有任何重复行
#        - 脚本造的那几条仍是「待人工处理」，没被重推改坏
#   3) 物理删除本次造的行，并核对总数回到基线
#
# 断言口径：只认落库事实（条数 / 状态 / 唯一性），不去断言接口文案。
set -u

ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 重复上报拒绝入账：落库核验 ==="

BEFORE=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging;")
BEFORE_DUP=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE status = 5;")
echo "  水位：暂存行 ${BEFORE} / status=5 的行 ${BEFORE_DUP}"

# ── 1) 跑 HTTP 回归（不自行删数据，留给本脚本核验后清理）──
OUT=$(cd "$ROOT" && "$NODE" logs/verify-duplicate-reject.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'

if echo "$OUT" | grep -q "全部通过"; then
  ok "★ HTTP 层断言全部通过"
else
  no "★ HTTP 层断言未全部通过" "$(echo "$OUT" | grep -c '❌') 项失败"
fi

PREFIX=$(echo "$OUT" | grep -o '# BIZ_PREFIX=.*' | cut -d= -f2)
if [ -z "$PREFIX" ]; then
  no "★ 拿到本次造数前缀" "脚本未输出 # BIZ_PREFIX="
  echo "结果：通过 ${PASS} 项，失败 ${FAIL} 项"
  exit 1
fi
echo "  本次造数前缀：${PREFIX}"

# ── 2) 落库核验 ──
eq "★ 本次只新建了 2 条暂存行（首报 + 改字段；重复上报一条都没建）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE biz_key LIKE '${PREFIX}-%';")" "2"

eq "★ 这 2 条都停在「待人工处理」（重推没有把原记录改坏）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE biz_key LIKE '${PREFIX}-%' AND status = 1;")" "2"

eq "★ 全库没有任何 status=5 的行（5 只出现在回执里，不进队列）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE status = 5;")" "$BEFORE_DUP"

eq "★ (设备, 上报编号) 没有任何重复行（唯一键 + 判重都成立）" \
   "$(MYSQL "SELECT COUNT(*) FROM (SELECT device_no, biz_key FROM db_ingest.ingest_staging GROUP BY device_no, biz_key HAVING COUNT(*) > 1) t;")" "0"

eq "★ 重复上报没有写业务表（这批报文本身是转人工的，target_id 应为空）" \
   "$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging WHERE biz_key LIKE '${PREFIX}-%' AND target_id IS NOT NULL;")" "0"

# ── 3) 清理并复核水位 ──
MYSQL "DELETE FROM db_ingest.ingest_staging WHERE biz_key LIKE '${PREFIX}-%';" >/dev/null

AFTER=$(MYSQL "SELECT COUNT(*) FROM db_ingest.ingest_staging;")
eq "★ 清理后暂存行回到基线" "$AFTER" "$BEFORE"

echo "结果：通过 ${PASS} 项，失败 ${FAIL} 项"
[ "$FAIL" -eq 0 ] || exit 1
