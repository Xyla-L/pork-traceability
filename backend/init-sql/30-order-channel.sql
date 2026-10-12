-- ============================================================================
-- 订单接入通道：把「商家选购」当成又一个外部事实源接进来（第 16 条通道）
--
-- 背景（2026-10-09 设计评审）：
-- 现实里这些业务不是一个系统做完的 —— 订单在 ERP/OMS，本系统只管溯源。
-- 所以订单**不该在本系统产生，但可以收进来**。这不是「加一个商城模块」，
-- 而是「再加一条接入通道」：与 TEMPERATURE / ENTRY / LOAD 同一套机制
-- （ingest_device 台账 → 统一信封 → staging 幂等 → ChannelHandler → 业务表），
-- 差别只在 source 标记。OrderChannelHandler 落的是「待排车的计划」。
--
-- 三条必须守住的口径：
--   ① 订单 ≠ 计划，订单只是计划的一种**来源**。
--      链路：ERP/OMS 订单 → 订单通道 → 发货计划(plan_source=ORDER) → 装车口 → 运单。
--      运单的上游永远是计划，不是订单 —— 不能因为接了订单就退回「下单即运单」。
--   ② 不建订单表。订单的权威副本在外部系统，本系统存一份就是第二个真相；
--      原始报文已经全量留在 ingest_staging.payload（不可变），计划上记 order_no 即可反查。
--   ③ 字段层面就不收金额。不是「事后不接支付」，而是压根不接收 ——
--      溯源需要的是「要什么货、去哪、什么时候要」，金额不参与上链、不影响召回圈。
--
-- 刻意不做：
--   · 不给 delivery_plan 加新状态。「排车」这个动作现在就等于「生成运单」，
--     所以订单转入的计划停在 status=1 待发货（= 待排车），语义已经吻合。
--   · 不做订单 → 计划的 1:1 直通。delivery_plan.store_id 是单值、store_receipt 是
--     uk_transport 唯一 ⇒ 一张运单只能对一家门店；多店可拼一车、一单可分多车，
--     所以归并/排车必须有一层决定（本轮归并键 = 门店 + 计划发货日）。
--
-- 幂等：ALTER 前探 information_schema；种子 INSERT IGNORE；可重复执行。
-- 执行：docker cp 进 mysql 容器后 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 本文件`
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) delivery_plan 补「计划来源」与「来源订单号」
--    计划来源的唯一口径在 com.pork.distribution.support.PlanSource，本文件值域与它一致。
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan' AND COLUMN_NAME = 'plan_source');
SET @s1 := IF(@c1 = 0,
    'ALTER TABLE db_distribution.delivery_plan ADD COLUMN plan_source VARCHAR(16) NOT NULL DEFAULT ''MANUAL'' COMMENT ''计划来源 MANUAL=内部调度/ORDER=订单转入（口径见 PlanSource）'' AFTER status',
    'SELECT ''delivery_plan.plan_source 已存在，跳过'' AS skip_note');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan' AND COLUMN_NAME = 'order_no');
SET @s2 := IF(@c2 = 0,
    'ALTER TABLE db_distribution.delivery_plan ADD COLUMN order_no VARCHAR(64) DEFAULT NULL COMMENT ''来源订单号（订单转入时填；订单权威副本在外部系统，本系统只留号以便回溯）'' AFTER plan_source',
    'SELECT ''delivery_plan.order_no 已存在，跳过'' AS skip_note');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

-- 「这张订单发了几车」靠它查（订单权威副本不在本系统，所以只留号不留单）
SET @i1 := (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan' AND INDEX_NAME = 'idx_order_no');
SET @s3 := IF(@i1 = 0,
    'ALTER TABLE db_distribution.delivery_plan ADD KEY idx_order_no (order_no)',
    'SELECT ''delivery_plan.idx_order_no 已存在，跳过'' AS skip_note');
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;

-- 存量计划全部是内部人制的，来源记 MANUAL（ALTER 的 DEFAULT 已填，这里再兜一次防显式 NULL）
UPDATE db_distribution.delivery_plan SET plan_source = 'MANUAL' WHERE plan_source IS NULL OR plan_source = '';

-- ---------------------------------------------------------------------------
-- 2) 订单终端台账
--    这是接入的唯一一道准入：没登记就上报 = 鉴权失败。
--    将来每个商家端/每套 ERP 各登记一条，而不是全公司共用一个终端号。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_ingest.ingest_device (device_no, device_name, channel, location, secret_key, status, remark) VALUES
('ORDER-TERM-001', '商家选购终端（订单接入）', 'ORDER', '外部系统（商超/门店下单端）', 'dev-key-order-001', 1,
 '第 16 条通道：接收外部系统的选购/订单事实，转成「待排车的发货计划」。落点不是既成事实而是决策单，所以 handler 里不做业务判定，排车仍由人决定。同一 order_no 重复推送幂等；不含任何金额字段。');

-- ---------------------------------------------------------------------------
-- 3) 核验
-- ---------------------------------------------------------------------------
SELECT table_name, column_name, column_type, column_default, column_comment
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan'
  AND COLUMN_NAME IN ('plan_source', 'order_no')
ORDER BY ordinal_position;

SELECT 'plan_source=ORDER 的计划数' AS item, COUNT(*) AS v FROM db_distribution.delivery_plan WHERE plan_source = 'ORDER'
UNION ALL SELECT 'plan_source=MANUAL 的计划数', COUNT(*) FROM db_distribution.delivery_plan WHERE plan_source = 'MANUAL'
UNION ALL SELECT 'plan_source 为空的计划数', COUNT(*) FROM db_distribution.delivery_plan WHERE plan_source IS NULL;

SELECT device_no, device_name, channel, location, status FROM db_ingest.ingest_device WHERE channel = 'ORDER';
