-- ============================================================================
-- 胴体批次接入设备通道（屠宰线胴体称重分拣台）
--
-- 背景：胴体批次原先只有管理端一条人工入口——操作员手工挑猪、填屠宰场，才凑出一个批次。
-- 但「哪几头白条算一批」在现实中根本不是人挑出来的：白条从分拣线下来，扫一下挂钩标签、
-- 过一下磅，同一屠宰场同一天的白条自然就是一批。人挑的批次边界，既对不上现场，也审不出来。
--
-- 分工口径（与分割批次一致）：设备只报它测得到的单头事实——耳标、胴体重、什么时候、哪条线；
-- 批次边界由系统按「屠宰场 + 屠宰日」自动归集。批次号依旧由系统生成，设备报文里不带批次号：
-- 批次号是人手编不出也编不对的东西，让它进设备报文只会制造跨场混批或一猪两批。
--
-- 本脚本做两件事：
--   1) carcass_batch 补「数据来源」三件套（与 split_batch 等设备通道业务表口径一致）
--   2) 登记一台屠宰线胴体分拣台 BATCH-001，通道 = BATCH
--
-- 幂等：本文件可重复执行——ALTER 前先探测 information_schema，设备用 INSERT IGNORE。
-- 执行：docker cp 进 mysql 容器后 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 本文件`
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) carcass_batch 补来源三件套
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'carcass_batch' AND COLUMN_NAME = 'source');
SET @s1 := IF(@c1 = 0,
    'ALTER TABLE db_distribution.carcass_batch ADD COLUMN source VARCHAR(16) DEFAULT ''MANUAL'' COMMENT ''数据来源 MANUAL=人工建批/DEVICE=分拣台自动归集'' AFTER operator',
    'SELECT ''carcass_batch.source 已存在，跳过'' AS skip_note');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'carcass_batch' AND COLUMN_NAME = 'source_ref');
SET @s2 := IF(@c2 = 0,
    'ALTER TABLE db_distribution.carcass_batch ADD COLUMN source_ref VARCHAR(64) COMMENT ''来源设备号（分拣台自动归集记设备编号，如 BATCH-001）'' AFTER source',
    'SELECT ''carcass_batch.source_ref 已存在，跳过'' AS skip_note');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'carcass_batch' AND COLUMN_NAME = 'raw_payload');
SET @s3 := IF(@c3 = 0,
    'ALTER TABLE db_distribution.carcass_batch ADD COLUMN raw_payload JSON COMMENT ''设备原始报文（分拣台上报的完整报文，供逐字段核对）'' AFTER source_ref',
    'SELECT ''carcass_batch.raw_payload 已存在，跳过'' AS skip_note');
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;

-- 存量批次都是人工建的（那时还没有设备通道），来源标 MANUAL；
-- source_ref 是设备号，人工批次没有这个值，如实留空，不回填、不编造。
UPDATE db_distribution.carcass_batch SET source = 'MANUAL' WHERE source IS NULL;

-- ---------------------------------------------------------------------------
-- 2) 屠宰线胴体分拣台台账
-- 与 STAMPER-001（盖章机）区别：盖章机在同步检疫之后执行盖章动作，只有一头白条一件事；
-- BATCH-001 在白条下线分拣处，负责「识读挂钩标签 + 过磅」，把同一屠宰场同一天的白条归成一批。
-- 密钥仅用于本地开发/答辩演示，生产须逐一更换并走密钥管理。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_ingest.ingest_device (device_no, device_name, channel, location, secret_key, status, remark) VALUES
('BATCH-001', '屠宰线胴体称重分拣台', 'BATCH', '示范定点屠宰场 白条分拣线', 'dev-key-batch-001', 1,
 '白条下线时识读挂钩标签并过磅，系统按「屠宰场 + 屠宰日」自动归集成胴体批次；批次号由系统生成，设备不上报批次号');

-- ---------------------------------------------------------------------------
-- 核验
-- ---------------------------------------------------------------------------
SELECT table_schema, table_name, column_name, column_type, column_default, column_comment
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'carcass_batch'
ORDER BY ordinal_position;

SELECT id, device_no, device_name, channel, location, status FROM db_ingest.ingest_device ORDER BY id;
