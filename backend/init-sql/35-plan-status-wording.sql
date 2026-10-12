-- ============================================================================
-- 35. 计划状态用词与运单侧时间线分开：「已发运」→「已排车」
--
-- 背景（2026-10-10）：
--   delivery_plan.status = 2 在各处被叫成「已发运」，但它置上的时刻是**生成运单那一刻**
--   （generateTransportFromPlans 第 ⑥ 步），而运单此时恰恰还停在「待发车」——
--   司机上车刷卡（DEPART）才算真发车。一个说「货发走了」、一个说「车还没走」，
--   看单据的人会以为状态打架，其实只是**计划侧那个词用错了**：
--   计划这一列回答的是「货有没有排进运单」，不回答「车走到哪」。
--
-- 口径（与后端 DeliveryPlan 实体注释、前端 planStatusLabel 一致）：
--   计划侧：待发货 / 部分排车 / 已排车 / 已取消
--   运单侧：待发车 / 运输中 / 待签收 / 已签收
--   明细侧：delivery_plan_item.shipped —— 同一件事在明细分摊上的标记，同步改叫「已排车」。
--
-- 本脚本只改列注释（元数据），**不动任何数据、不改表结构**。
-- 顺带修一处历史欠账：delivery_plan.status 的注释还停在更早的
-- 「1=待发货 2=已生成运单 3=已取消」，连 4 都没有。
-- ============================================================================

ALTER TABLE db_distribution.delivery_plan
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1
        COMMENT '计划状态 1=待发货 2=已排车（货已全部排进运单）3=已取消 4=部分排车（还有货在等车）';

ALTER TABLE db_distribution.delivery_plan_item
    MODIFY COLUMN `shipped` TINYINT NOT NULL DEFAULT 0
        COMMENT '是否已排车 0=待排车 1=已排进某张运单';

-- 核对 ①：计划状态注释已是新口径
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution'
  AND ((TABLE_NAME = 'delivery_plan' AND COLUMN_NAME = 'status')
    OR (TABLE_NAME = 'delivery_plan_item' AND COLUMN_NAME = 'shipped'));

-- 核对 ②：状态值分布（纯读，确认没有 2 以外的非法值；数字含义不变，只是叫法变了）
SELECT status, COUNT(*) AS plans FROM db_distribution.delivery_plan GROUP BY status ORDER BY status;

-- 核对 ③：口径自洽性 —— 「已排车(2)」的计划不可能还有没排进运单的明细
SELECT COUNT(*) AS inconsistent_plans
FROM db_distribution.delivery_plan p
WHERE p.status = 2
  AND EXISTS (SELECT 1 FROM db_distribution.delivery_plan_item i
              WHERE i.plan_id = p.id AND i.shipped = 0);
