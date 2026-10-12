-- ============================================================================
-- 装车口自动化通道（LOAD）：让运单从「人点按钮生成」变成「装车即生成」
--
-- 背景（2026-10-09 设计评审续）：上一轮补了发货计划，把「发哪批货、去哪家店」这个决策
-- 第一次写进了系统。但运单本身仍然是人点按钮生成的。查设备台账会发现一个很干净的缺口：
-- 18 台设备覆盖了养殖→屠宰→分割→在途→门店的每一个环节，唯独「装车出场」这一环是 0 台。
--
-- 为什么现在建不出「全自动」：运单 9 个字段里，目的地门店与计划发车时间是经营/排班决策，
-- 机器永远拿不到（sales 库只有零售售出与召回单，没有要货单可继承）；而车牌（车牌识别相机）、
-- 司机（实名终端）、装了哪几批货（扫码枪）是现场客观事实，机器拿得到。
--
-- 所以装车口的职责边界是：**拿现场事实去倒查计划，而不是自己决定发去哪**。
--   车停到装车位 → 相机识车牌 + 司机实名 → 扫码枪扫这批部位标签
--     → 匹配一张待发货计划的未发运明细 → 命中就自动生成运单；匹配不上转人工，绝不猜。
-- 扫码是「确认」不是「选择」：目的地是人在计划里早就定死的，设备只是把「哪些货真的上了这辆车」
-- 记下来。猜错目的地就是货发错店，那不是效率问题，是事故。
--
-- 本脚本做四件事：
--   1) delivery_plan_item 补「已发运」标记 —— 支持分车：一车装不完，剩下的等下一车。
--      原先一张计划只能整单生成一次运单，装不下就得取消重开，现场不这么干活。
--   2) delivery_plan 补 transport_count，状态机加 4=部分发运。
--      单值 transport_id/transport_no 的语义收敛为「最近一次生成的运单」，全貌一律以明细为准。
--   3) cold_chain_transport 补来源三件套（与 carcass_batch / split_batch 口径一致）：
--      运单从此能分辨「人工建的」还是「装车口生成的」，这是追责的起点。
--   4) 登记一台装车口识别终端 LOADER-001，通道 = LOAD。
--
-- 刻意不做：不给装车口开 force 旁路（车与人对不上就转人工，不硬塞）；目的地绝不由设备推断。
-- 幂等：ALTER 前探测 information_schema，设备 INSERT IGNORE，回填可重复执行。
-- 执行：docker cp 进 mysql 容器后 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 本文件`
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) delivery_plan_item 补发运标记（分车的基础）
--    已发运的明细留在计划里不删：它是「这张计划实际发出去什么」的台账。
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan_item' AND COLUMN_NAME = 'shipped');
SET @s1 := IF(@c1 = 0,
    'ALTER TABLE db_distribution.delivery_plan_item ADD COLUMN shipped TINYINT NOT NULL DEFAULT 0 COMMENT ''是否已发运 0=待装车 1=已随某张运单发运'' AFTER weight_kg',
    'SELECT ''delivery_plan_item.shipped 已存在，跳过'' AS skip_note');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan_item' AND COLUMN_NAME = 'transport_id');
SET @s2 := IF(@c2 = 0,
    'ALTER TABLE db_distribution.delivery_plan_item ADD COLUMN transport_id BIGINT NULL COMMENT ''实际发运这张明细的运单 id（分车时同一计划会指向不同运单）'' AFTER shipped',
    'SELECT ''delivery_plan_item.transport_id 已存在，跳过'' AS skip_note');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan_item' AND COLUMN_NAME = 'shipped_time');
SET @s3 := IF(@c3 = 0,
    'ALTER TABLE db_distribution.delivery_plan_item ADD COLUMN shipped_time DATETIME NULL COMMENT ''该明细实际发运时刻'' AFTER transport_id',
    'SELECT ''delivery_plan_item.shipped_time 已存在，跳过'' AS skip_note');
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;

-- 未发运明细是「待发运批次」查询的关联条件，单独建索引
SET @i1 := (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan_item' AND INDEX_NAME = 'idx_shipped');
SET @s4 := IF(@i1 = 0,
    'ALTER TABLE db_distribution.delivery_plan_item ADD INDEX idx_shipped (shipped, plan_id)',
    'SELECT ''delivery_plan_item.idx_shipped 已存在，跳过'' AS skip_note');
PREPARE st4 FROM @s4; EXECUTE st4; DEALLOCATE PREPARE st4;

-- ---------------------------------------------------------------------------
-- 2) delivery_plan 补 transport_count
--    分车后一张计划可能对应多张运单，单值 transport_id 表达不了全貌，
--    于是它降级为「最近一次生成的运单」，另用 transport_count 记一共发了几车。
--    状态机：1=待发货 2=已发运（明细全部发完）3=已取消 4=部分发运（还有货在等车）
-- ---------------------------------------------------------------------------
SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'delivery_plan' AND COLUMN_NAME = 'transport_count');
SET @s5 := IF(@c4 = 0,
    'ALTER TABLE db_distribution.delivery_plan ADD COLUMN transport_count INT NOT NULL DEFAULT 0 COMMENT ''已生成的运单张数（分车后 >1）'' AFTER transport_no',
    'SELECT ''delivery_plan.transport_count 已存在，跳过'' AS skip_note');
PREPARE st5 FROM @s5; EXECUTE st5; DEALLOCATE PREPARE st5;

-- ---------------------------------------------------------------------------
-- 3) cold_chain_transport 补来源三件套
--    运单此前没有任何来源标记：人工建的、装车口生成的，在库里长得一模一样，
--    出了发错店的事故根本追不到是哪条路径产生的。
-- ---------------------------------------------------------------------------
SET @c5 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'cold_chain_transport' AND COLUMN_NAME = 'source');
SET @s6 := IF(@c5 = 0,
    'ALTER TABLE db_distribution.cold_chain_transport ADD COLUMN source VARCHAR(16) DEFAULT ''MANUAL'' COMMENT ''数据来源 MANUAL=人工建单/PLAN=计划生成/DEVICE=装车口自动生成'' AFTER status',
    'SELECT ''cold_chain_transport.source 已存在，跳过'' AS skip_note');
PREPARE st6 FROM @s6; EXECUTE st6; DEALLOCATE PREPARE st6;

SET @c6 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'cold_chain_transport' AND COLUMN_NAME = 'source_ref');
SET @s7 := IF(@c6 = 0,
    'ALTER TABLE db_distribution.cold_chain_transport ADD COLUMN source_ref VARCHAR(64) COMMENT ''来源设备号（装车口自动生成记设备编号，如 LOADER-001）'' AFTER source',
    'SELECT ''cold_chain_transport.source_ref 已存在，跳过'' AS skip_note');
PREPARE st7 FROM @s7; EXECUTE st7; DEALLOCATE PREPARE st7;

SET @c7 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'cold_chain_transport' AND COLUMN_NAME = 'raw_payload');
SET @s8 := IF(@c7 = 0,
    'ALTER TABLE db_distribution.cold_chain_transport ADD COLUMN raw_payload JSON COMMENT ''设备原始报文（装车口上报的完整报文，供逐字段核对）'' AFTER source_ref',
    'SELECT ''cold_chain_transport.raw_payload 已存在，跳过'' AS skip_note');
PREPARE st8 FROM @s8; EXECUTE st8; DEALLOCATE PREPARE st8;

-- 存量 34 条运单都是人工/脚本建的，来源标 MANUAL；不回填、不编造
UPDATE db_distribution.cold_chain_transport SET source = 'MANUAL' WHERE source IS NULL;

-- 存量已被计划生成的运单：其明细即「已发运」，一并回填，否则这些货会重新出现在「待发运」里
UPDATE db_distribution.delivery_plan SET transport_count = 1
WHERE status = 2 AND transport_id IS NOT NULL AND transport_count = 0;

UPDATE db_distribution.delivery_plan_item i
JOIN db_distribution.delivery_plan p ON p.id = i.plan_id
SET i.shipped = 1,
    i.transport_id = p.transport_id,
    i.shipped_time = p.generated_time
WHERE p.status = 2 AND p.transport_id IS NOT NULL AND i.shipped = 0;

-- ---------------------------------------------------------------------------
-- 4) 装车口识别终端台账
--    与其它通道设备的区别：这台设备不产生任何业务判定，它只提供三个现场事实
--    （车牌、司机、扫到了哪几批货），由系统拿去倒查计划。
--    密钥仅用于本地开发/答辩演示，生产须逐一更换并走密钥管理。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_ingest.ingest_device (device_no, device_name, channel, location, secret_key, status, remark) VALUES
('LOADER-001', '装车口车牌识别与司机实名终端', 'LOAD', '示范定点屠宰场 冷库装车位', 'dev-key-loader-001', 1,
 '装车口三件套（车牌识别相机 + 司机实名终端 + 扫码枪）的接入点：识读车牌与司机、扫描本车装载的分割部位标签；系统据此倒查待发货计划并自动生成运单，目的地不由设备决定，匹配不上转人工');

-- ---------------------------------------------------------------------------
-- 核验
-- ---------------------------------------------------------------------------
SELECT table_name, column_name, column_type, column_default, column_comment
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution'
  AND ((TABLE_NAME = 'delivery_plan_item' AND COLUMN_NAME IN ('shipped', 'transport_id', 'shipped_time'))
    OR (TABLE_NAME = 'delivery_plan' AND COLUMN_NAME = 'transport_count')
    OR (TABLE_NAME = 'cold_chain_transport' AND COLUMN_NAME IN ('source', 'source_ref', 'raw_payload')))
ORDER BY table_name, ordinal_position;

SELECT source, COUNT(*) AS cnt FROM db_distribution.cold_chain_transport GROUP BY source;

SELECT id, device_no, device_name, channel, location, status FROM db_ingest.ingest_device ORDER BY id;
