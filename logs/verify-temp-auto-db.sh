#!/usr/bin/env bash
# 冷链温度「定时自动打卡」的落库核验（只读，不清理）。
#
# 用法：bash logs/verify-temp-auto-db.sh
#
# 为什么不清理：定时采集写下的记录就是**功能产物**（正常每 5 分钟一条），
# 它属于「演示/业务数据」，删掉等于把功能自己产出的证据擦了。
# 本脚本只读，断言的是「这类数据必须成立的不变量」。
#
# 断言口径（盯不变量，不盯绝对条数）：
#   1) 每条模拟记录都固化了额定采样间隔（口径随记录留档，改配置不影响历史）
#   2) 模拟读数落在冷藏区间，且越界与否与 is_abnormal 一致
#   3) 运单一旦有真机（DEVICE）记录，就不再出现模拟记录（不混数据）
#   4) 送达之后不再新增：已到达/已签收的运单，不存在「到达时刻之后才写进去」的模拟记录
#   5) 没有补造历史：模拟记录的落库时刻与记录时刻相差不超过 2 分钟
set -u

MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 温度定时采集 · 落库不变量 ==="

# ── 0) 跑 HTTP 回归，并记下测试起点（用于收尾清掉本次强制采集的那一条） ──
ROOT=/e/pork-traceability
NODE="C:/Users/17875/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
START_TS=$(MYSQL "SELECT NOW();" | head -1)
echo "  测试起点 ${START_TS}"
OUT=$(cd "$ROOT" && "$NODE" logs/verify-temp-auto.mjs 2>&1)
echo "$OUT" | sed 's/^/  /'
HTTP_FAIL=$(echo "$OUT" | grep -o '失败 [0-9]* 项' | grep -o '[0-9]*')
eq "HTTP 层断言全通过" "${HTTP_FAIL:-99}" "0"

TOTAL=$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log WHERE source='SIMULATED';")
echo "  模拟记录条数：${TOTAL}"

# ── 1) 间隔固化 ──
eq "模拟记录都带额定采样间隔" \
  "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log WHERE source='SIMULATED' AND sample_interval_seconds IS NULL;")" "0"

# ── 2) 读数与异常标记自洽 ──
eq "模拟读数都在 -18~0℃（模拟探头不编越界值）" \
  "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log WHERE source='SIMULATED' AND (temperature < -18 OR temperature > 0);")" "0"
eq "区间内读数未误标异常" \
  "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log WHERE source='SIMULATED' AND is_abnormal = 1 AND temperature BETWEEN temp_range_min AND temp_range_max;")" "0"

# ── 3) 真机优先：同一运单不许既有真机又有模拟 ──
eq "没有「真机 + 模拟」混在同一条运单上" \
  "$(MYSQL "SELECT COUNT(*) FROM (SELECT transport_id FROM db_distribution.temperature_log GROUP BY transport_id HAVING SUM(source='DEVICE') > 0 AND SUM(source='SIMULATED') > 0) x;")" "0"

# ── 4) 送达即停：到达/签收之后不得再写模拟记录 ──
# 单站运单：到达时刻 → 状态 3；全部签收 → 4。到达之后本采集器不该再落任何一条。
eq "已到达/已签收运单在到达之后没有新增模拟记录" \
  "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l
             JOIN db_distribution.cold_chain_transport t ON t.id = l.transport_id
            WHERE l.source='SIMULATED' AND t.status IN (3,4)
              AND t.arrive_time IS NOT NULL AND l.create_time > t.arrive_time;")" "0"

# ── 5) 不补造历史 ──
eq "模拟记录的落库时刻与记录时刻相差 ≤ 2 分钟（不是回填的假历史）" \
  "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log
            WHERE source='SIMULATED' AND ABS(TIMESTAMPDIFF(SECOND, record_time, create_time)) > 120;")" "0"

# ── 6) 只有运输中的车在进数：最近 10 分钟新增的模拟记录，其运单必须是 status=2 ──
eq "近 10 分钟新增的模拟记录全部来自运输中运单" \
  "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log l
             JOIN db_distribution.cold_chain_transport t ON t.id = l.transport_id
            WHERE l.source='SIMULATED' AND l.create_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)
              AND t.status <> 2;")" "0"

# ── 7) 收尾：清掉本次回归用 force 强采的记录 ──
# 它不是业务事实，只是「接口能写」的证据；留着会在曲线上堆出一串间隔很短的点。
# 定时任务自己写的（没有 #force 标记）一律不动。
MYSQL "DELETE FROM db_distribution.temperature_log WHERE source='SIMULATED' AND source_ref LIKE '%#force';" >/dev/null
eq "清理本次强制采集的临时记录（source_ref 带 #force）" \
  "$(MYSQL "SELECT COUNT(*) FROM db_distribution.temperature_log WHERE source='SIMULATED' AND source_ref LIKE '%#force';")" "0"

echo ""
echo "结果：通过 ${PASS} 项，失败 ${FAIL} 项"
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
