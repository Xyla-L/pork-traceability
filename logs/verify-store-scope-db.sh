#!/usr/bin/env bash
# 零售商数据隔离的落库核验（只读，不写库、不造数据，所以无需清理）
#
# 断言的是**不变量**，不是绝对条数（数据会随演示增长）：
#   1. 销售/签收/站点三类表里，不存在「store_id 不是任何一家零售机构」的行
#      —— 这类行对任何零售商都不可见，等于被隔离机制吞掉
#   2. 名字能在机构表命中的站点，store_id 必须已经填上（回填完整）
#   3. 同一个 store_id 只能对应一个 store_name（防止一处写错ID、另一处写错名字，
#      两条路各看各的）
#   4. 零售商账号的 org_id 必须指向 type='retail' 的机构
#      —— 令牌里的 orgId 就是门店ID，指错了就是把人关进别人的店
#   5. 每家零售机构都得有账号（否则那家店的隔离没有落点，永远看不到自己的数据）
#
# 用法：bash logs/verify-store-scope-db.sh
set -u

MYSQL() { docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password --default-character-set=utf8mb4 -N -e "$1" 2>&1 | grep -v "Using a password"; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
no() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1 —— $2"; }
eq() { if [ "$2" = "$3" ]; then ok "$1（$2）"; else no "$1" "期望 $3，实际 $2"; fi }

echo "=== 零售商数据隔离：落库核验 ==="

echo "-- 1) 三类表的门店归属完整性"
eq "销售记录未归属行" "$(MYSQL "SELECT COUNT(*) FROM db_sales.retail_sale WHERE store_id NOT IN (SELECT id FROM db_common.sys_org WHERE type='retail');")" "0"
eq "门店签收单未归属行" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.store_receipt WHERE store_id NOT IN (SELECT id FROM db_common.sys_org WHERE type='retail');")" "0"

echo "-- 2) 站点回填完整：名字能命中机构的不许还空着"
eq "未归属站点（名字可命中却仍空/非法）" "$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop t JOIN db_common.sys_org o ON o.name=t.store_name AND o.type='retail' WHERE t.store_id IS NULL OR t.store_id NOT IN (SELECT id FROM db_common.sys_org WHERE type='retail');")" "0"
ORPHAN_STOP=$(MYSQL "SELECT COUNT(*) FROM db_distribution.transport_stop WHERE store_id IS NULL OR store_id NOT IN (SELECT id FROM db_common.sys_org WHERE type='retail');")
echo "  [INFO] 仍有 ${ORPHAN_STOP} 个站点未归属 —— 应为机构表里根本不存在的门店（如历史演示的「示范一店」），对任何零售商都不可见"

echo "-- 3) 同一门店ID 只能有一个门店名（销售表 / 签收表各查一次）"
eq "销售表 一ID多名" "$(MYSQL "SELECT COUNT(*) FROM (SELECT store_id FROM db_sales.retail_sale GROUP BY store_id HAVING COUNT(DISTINCT store_name)>1) x;")" "0"
eq "签收表 一ID多名" "$(MYSQL "SELECT COUNT(*) FROM (SELECT store_id FROM db_distribution.store_receipt GROUP BY store_id HAVING COUNT(DISTINCT store_name)>1) x;")" "0"

echo "-- 4) 零售商账号的机构必须是一家门店"
eq "角色为零售但机构不是零售类型" "$(MYSQL "SELECT COUNT(*) FROM db_common.sys_user u LEFT JOIN db_common.sys_org o ON o.id=u.org_id WHERE u.role='RETAILER' AND (o.id IS NULL OR o.type<>'retail');")" "0"
eq "零售商账号缺机构（令牌将不带 orgId）" "$(MYSQL "SELECT COUNT(*) FROM db_common.sys_user WHERE role='RETAILER' AND org_id IS NULL;")" "0"

echo "-- 5) 每家零售机构都有账号"
eq "无账号的门店机构数" "$(MYSQL "SELECT COUNT(*) FROM db_common.sys_org o WHERE o.type='retail' AND NOT EXISTS (SELECT 1 FROM db_common.sys_user u WHERE u.org_id=o.id);")" "0"

echo "-- 6) 抽样门店（1001 安心生鲜门店）自洽"
eq "1001 存在已售记录" "$([ "$(MYSQL "SELECT COUNT(*) FROM db_sales.retail_sale WHERE store_id=1001 AND status=2;")" -gt 0 ] && echo yes || echo no)" "yes"
eq "1001 的记录门店名全部正确" "$(MYSQL "SELECT COUNT(*) FROM db_sales.retail_sale WHERE store_id=1001 AND store_name<>'安心生鲜门店';")" "0"
echo "  [INFO] 1001 已售 $(MYSQL "SELECT COUNT(*) FROM db_sales.retail_sale WHERE store_id=1001 AND status=2;") 条 / 金额 $(MYSQL "SELECT IFNULL(SUM(sell_price),0) FROM db_sales.retail_sale WHERE store_id=1001 AND status=2;") 元"

echo "-- 7) 令牌机构 = 门店ID：门店ID 必须唯一对应机构ID"
eq "有销售记录却找不到机构" "$(MYSQL "SELECT COUNT(DISTINCT s.store_id) FROM db_sales.retail_sale s LEFT JOIN db_common.sys_org o ON o.id=s.store_id AND o.type='retail' WHERE o.id IS NULL;")" "0"

echo
echo "结果：通过 ${PASS} 项，失败 ${FAIL} 项"
[ "${FAIL}" -eq 0 ] || exit 1
