-- =============================================================================
-- 15-source-governance.sql
-- 来源管控：历史数据来源标记与操作人回填
--
-- 背景：
--   source 列在 11 号和 14 号脚本里加进来时就带了 DEFAULT 'MANUAL'，
--   所以老数据大体能标成「人工」；但 source_ref（操作人/设备号）当年没有值，
--   结果就是——知道是人工录的，却不知道是谁录的。
--   既然对外口径是「来源可标识、责任可追溯」，这半天必须补上。
--
-- 处理原则：
--   1. 只补 source / source_ref，不改任何业务字段，不动已上链记录的 content_hash；
--   2. 已经是 DEVICE / API / IMPORT 的记录一律不碰；
--   3. 来源确实无从考证的，统一写「历史人工录入」，不假装成设备采集——
--      宁可承认是人工，也不能让审计口径失真。
--
-- 执行方式（PowerShell 管道会毁 UTF-8，必须走 docker cp 二进制通道）：
--   docker cp backend/init-sql/15-source-governance.sql pork-traceability-mysql-1:/tmp/15.sql
--   docker exec pork-traceability-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/15.sql'
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. db_slaughter：入场查验 / 屠宰检验 / 瘦肉精 / 胴体盖章
-- -----------------------------------------------------------------------------
USE db_slaughter;

UPDATE entry_inspection
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''),
                          CONCAT('历史人工录入-', COALESCE(NULLIF(inspector, ''), '未记录')))
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

UPDATE slaughter_inspection
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''),
                          CONCAT('历史人工录入-', COALESCE(NULLIF(veterinary, ''), '未记录')))
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

UPDATE ractopamine_test
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''),
                          CONCAT('历史人工录入-', COALESCE(NULLIF(operator, ''), '未记录')))
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

UPDATE carcass_stamp
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''),
                          CONCAT('历史人工录入-', COALESCE(NULLIF(veterinary, ''), '未记录')))
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

-- -----------------------------------------------------------------------------
-- 2. db_breeding：养殖建档（用 data_source 列）/ 免疫记录
-- -----------------------------------------------------------------------------
USE db_breeding;

UPDATE pig_individual
SET data_source = COALESCE(NULLIF(data_source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''), '历史人工录入')
WHERE data_source IS NULL OR data_source = '' OR source_ref IS NULL OR source_ref = '';

UPDATE vaccine_record
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''),
                          CONCAT('历史人工录入-', COALESCE(NULLIF(operator, ''), '未记录')))
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

-- -----------------------------------------------------------------------------
-- 3. db_distribution：分割批次 / 冷链温度 / 门店签收
-- -----------------------------------------------------------------------------
USE db_distribution;

UPDATE split_batch
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''),
                          CONCAT('历史人工录入-', COALESCE(NULLIF(operator, ''), '未记录')))
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

UPDATE temperature_log
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''), '历史人工录入')
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

UPDATE store_receipt
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''), '历史人工录入')
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

-- -----------------------------------------------------------------------------
-- 4. db_sales：零售售出
-- -----------------------------------------------------------------------------
USE db_sales;

UPDATE retail_sale
SET source = COALESCE(NULLIF(source, ''), 'MANUAL'),
    source_ref = COALESCE(NULLIF(source_ref, ''), '历史人工录入')
WHERE source IS NULL OR source = '' OR source_ref IS NULL OR source_ref = '';

-- -----------------------------------------------------------------------------
-- 5. 自检：任何一行来源为空都说明还有漏网之鱼（期望结果全部为 0）
-- -----------------------------------------------------------------------------
SELECT 'entry_inspection' AS tbl, COUNT(*) AS unresolved FROM db_slaughter.entry_inspection
WHERE source IS NULL OR source = ''
UNION ALL SELECT 'slaughter_inspection', COUNT(*) FROM db_slaughter.slaughter_inspection
WHERE source IS NULL OR source = ''
UNION ALL SELECT 'ractopamine_test', COUNT(*) FROM db_slaughter.ractopamine_test
WHERE source IS NULL OR source = ''
UNION ALL SELECT 'carcass_stamp', COUNT(*) FROM db_slaughter.carcass_stamp
WHERE source IS NULL OR source = ''
UNION ALL SELECT 'pig_individual', COUNT(*) FROM db_breeding.pig_individual
WHERE data_source IS NULL OR data_source = ''
UNION ALL SELECT 'vaccine_record', COUNT(*) FROM db_breeding.vaccine_record
WHERE source IS NULL OR source = ''
UNION ALL SELECT 'split_batch', COUNT(*) FROM db_distribution.split_batch
WHERE source IS NULL OR source = ''
UNION ALL SELECT 'store_receipt', COUNT(*) FROM db_distribution.store_receipt
WHERE source IS NULL OR source = ''
UNION ALL SELECT 'temperature_log', COUNT(*) FROM db_distribution.temperature_log
WHERE source IS NULL OR source = ''
UNION ALL SELECT 'retail_sale', COUNT(*) FROM db_sales.retail_sale
WHERE source IS NULL OR source = '';

-- -----------------------------------------------------------------------------
-- 6. 来源分布快照：关键环节里有多少条来自设备、多少条来自人工
--    这张结果就是「来源可标识」的直观证据，评审/答辩现场可以直接跑一次。
-- -----------------------------------------------------------------------------
SELECT '屠宰检验' AS stage, source, COUNT(*) AS cnt FROM db_slaughter.slaughter_inspection GROUP BY source
UNION ALL SELECT '胴体盖章', source, COUNT(*) FROM db_slaughter.carcass_stamp GROUP BY source
UNION ALL SELECT '瘦肉精检测', source, COUNT(*) FROM db_slaughter.ractopamine_test GROUP BY source
UNION ALL SELECT '入场查验', source, COUNT(*) FROM db_slaughter.entry_inspection GROUP BY source
UNION ALL SELECT '养殖建档', data_source, COUNT(*) FROM db_breeding.pig_individual GROUP BY data_source
UNION ALL SELECT '免疫注射', source, COUNT(*) FROM db_breeding.vaccine_record GROUP BY source
UNION ALL SELECT '分割批次', source, COUNT(*) FROM db_distribution.split_batch GROUP BY source
UNION ALL SELECT '门店签收', source, COUNT(*) FROM db_distribution.store_receipt GROUP BY source
UNION ALL SELECT '零售售出', source, COUNT(*) FROM db_sales.retail_sale GROUP BY source;
