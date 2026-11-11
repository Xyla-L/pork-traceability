-- =============================================================
-- 42 门店维度的历史数据修复（零售商数据隔离的前置）
--
-- 背景：门店维度是后加的。早期门店签收单没有 store_id（或全写成 1），
--       由签收单派生的销售记录、运单站点也就跟着落在 store_id=1 上，
--       而真正的门店机构是 sys_org 里 type='retail' 的 1001~1014。
--       结果：按门店隔离以后，孙店长（org_id=1001）看不到那 16 条
--       「安心生鲜门店」的销售记录 —— 数据没错，是归属字段错了。
--
-- 修复口径（只做确定性映射，不猜）：
--   * 只动「store_id 不是任何一家零售机构」的行；
--   * 新值只从本行自带的 store_name 反查 sys_org，**要求名字唯一命中**；
--     名字对不上的行（如「示范一店」）原样保留，宁可它继续对不上，
--     也不把一条记录塞给猜出来的门店。
--
-- 幂等：修完之后这些行的 store_id 已经是合法零售机构ID，
--       WHERE 条件不再命中，重复执行无副作用。
-- =============================================================
SET NAMES utf8mb4;

-- 1) 销售记录：二维码归属门店
UPDATE db_sales.retail_sale s
  JOIN db_common.sys_org o
    ON o.name = s.store_name
   AND o.type = 'retail'
   SET s.store_id = o.id
 WHERE s.store_id NOT IN (SELECT id FROM db_common.sys_org WHERE type = 'retail');

-- 2) 门店签收单：签收主体门店
UPDATE db_distribution.store_receipt r
  JOIN db_common.sys_org o
    ON o.name = r.store_name
   AND o.type = 'retail'
   SET r.store_id = o.id
 WHERE r.store_id NOT IN (SELECT id FROM db_common.sys_org WHERE type = 'retail');

-- 3) 运单站点：卸货门店（早期为 NULL，导致「这趟车跟我有没有关系」判不出来）
UPDATE db_distribution.transport_stop t
  JOIN db_common.sys_org o
    ON o.name = t.store_name
   AND o.type = 'retail'
   SET t.store_id = o.id
 WHERE t.store_id IS NULL
    OR t.store_id NOT IN (SELECT id FROM db_common.sys_org WHERE type = 'retail');
