#!/usr/bin/env bash
# 批量签收的落库核验（只读，不写库、不造数据，所以无需清理）
#
# 断言的是**不变量**，不是绝对条数：
#   1. 站点已签收 ⇒ 必须挂着签收单（receipt_id 非空）；签收单存在 ⇒ 对应站点必须已回填 receipt_id
#      —— 批量签收是一张一张独立提交的，最容易出的错就是「签收单插进去了、站点还停在已到达」
#   2. 运单已签收 ⇒ 所有站点都已签；运单待签收 ⇒ 至少还有一站没签
#      —— 运单状态由站点汇总推导，两边不许打架
#   3. 同一 (运单, 门店) 只能有一张签收单（幂等键是这一对，不是运单）
#   4. 站点已签收 ⇒ 必须有到达时刻（先签后到也要补一个，否则「到了没」无从回答）
#   5. 批量签收写的是本店：签收单的 store_id 必须能在该运单的站点里找到
#
# 用法：bash logs/verify-batch-receipt-db.sh
set -u

MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 批量签收：落库核验 ==="

echo "-- 1) 签收单与站点台账互相咬合"
eq "站点已签收却没挂签收单" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status=2 AND receipt_id IS NULL;")" "0"
eq "签收单对应的站点未回填 receipt_id" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r JOIN db_distribution.transport_stop s ON s.transport_id=r.transport_id AND s.store_id=r.store_id WHERE s.receipt_id IS NULL;")" "0"
eq "站点挂的 receipt_id 指向不存在的签收单" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop s LEFT JOIN db_distribution.store_receipt r ON r.id=s.receipt_id WHERE s.receipt_id IS NOT NULL AND r.id IS NULL;")" "0"

echo "-- 2) 运单状态由站点汇总推导，两边不许打架"
eq "运单已签收但仍有未签站点" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE t.status=4 AND EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.transport_id=t.id AND s.status<2);")" "0"
eq "运单待签收但站点已全部签完" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.cold_chain_transport t WHERE t.status=3 AND EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.transport_id=t.id) AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.transport_id=t.id AND s.status<2);")" "0"

echo "-- 3) 幂等键：(运单, 门店) 只能有一张签收单"
eq "同一运单同一门店重复签收" "$(MYSQL "SELECT COUNT(*) FROM (SELECT transport_id, store_id FROM db_distribution.store_receipt GROUP BY transport_id, store_id HAVING COUNT(*)>1) x;")" "0"

echo "-- 4) 签收是站点终态，必须能回答「什么时候到的」"
eq "站点已签收却无到达时刻" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status=2 AND arrive_time IS NULL;")" "0"

echo "-- 5) 签收单的门店必须在该运单的站点里"
# 只判「站点台账里有门店ID」的行：历史演示数据的站点 store_name 是「示范一店」且 store_id 为空，
# 那一批签收单（9 月）本来就对不上任何机构，属于旧账，不当成本次改动的问题。
eq "签收单门店不在运单站点中（签错了车）" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r WHERE EXISTS (SELECT 1 FROM db_distribution.transport_stop s2 WHERE s2.transport_id=r.transport_id AND s2.store_id IS NOT NULL) AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.transport_id=r.transport_id AND s.store_id=r.store_id);")" "0"
LEGACY_RECEIPT=$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt r WHERE NOT EXISTS (SELECT 1 FROM db_distribution.transport_stop s WHERE s.transport_id=r.transport_id AND s.store_id IS NOT NULL);")
echo "  [INFO] ${LEGACY_RECEIPT} 张签收单所属运单的站点没有门店ID（历史演示「示范一店」），无法判定，不计入失败"

echo
echo "结果：通过 ${PASS} 项，失败 ${FAIL} 项"
[ "$FAIL" -eq 0 ] || exit 1
