-- ============================================================================
-- 胴体批次：加「批次容量上限 + 封批状态 + 生猪关联表」
--
-- 背景（2026-10-09 设计评审）：上一版把归集键定为「屠宰场 + 屠宰日」，方向对但粒度不对——
-- 真实屠宰场日屠宰量：小型定点场 100~300 头、中型 500~1000 头、大型 3000 头以上，
-- 「一天一场一个批次」会得到一个几百到几千头的批次，追溯没有区分度、召回无从下手；
-- 且 pig_ids 是 JSON 数组，追加走「读全量→加一个→写回」在并发下会丢猪。
--
-- 本脚本做三件事：
--   1) carcass_batch 加 pig_count（头数）与 status（1=归集中 / 2=已封）
--      —— 批次必须有上限，否则它不是批次；封批 = 此后不再并入当日白条。
--   2) 新建 carcass_batch_pig 关联表，用 uk_pig(pig_id) 在**数据库层**保证
--      「一头猪只能归属一个批次」（原先靠应用层遍历所有批次的 JSON 数组判断，有竞态）。
--   3) 从存量 pig_ids 回填关联表与 pig_count。
--
-- 口径：关联表是权威（唯一约束在它身上）；carcass_batch.pig_ids / pig_count 是由同一条
-- 原子 SQL 一并维护的展示快照（前端与追溯查询仍在读它，避免大面积改动）。
--
-- 幂等：本文件可重复执行——ALTER / CREATE 前先探测 information_schema，回填用 INSERT IGNORE。
-- 执行：docker cp 进 mysql 容器后 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 本文件`
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) carcass_batch 加「头数」与「归集状态」
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'carcass_batch' AND COLUMN_NAME = 'pig_count');
SET @s1 := IF(@c1 = 0,
    'ALTER TABLE db_distribution.carcass_batch ADD COLUMN pig_count INT NOT NULL DEFAULT 0 COMMENT ''批次头数（与 pig_ids 同步维护，用于归集上限判断，免去每次读 JSON 长度）'' AFTER pig_ids',
    'SELECT ''carcass_batch.pig_count 已存在，跳过'' AS skip_note');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'carcass_batch' AND COLUMN_NAME = 'status');
SET @s2 := IF(@c2 = 0,
    'ALTER TABLE db_distribution.carcass_batch ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''归集状态：1=归集中（当日同场后续白条会并入）2=已封（不再并入，达到头数上限自动封或人工封批）'' AFTER pig_count',
    'SELECT ''carcass_batch.status 已存在，跳过'' AS skip_note');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

-- 归集查询的走法：屠宰场 + 来源 + 状态 + 创建时间落在当日
SET @c3 := (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'carcass_batch' AND INDEX_NAME = 'idx_open_agg');
SET @s3 := IF(@c3 = 0,
    'ALTER TABLE db_distribution.carcass_batch ADD INDEX idx_open_agg (slaughterhouse, source, status, create_time)',
    'SELECT ''idx_open_agg 已存在，跳过'' AS skip_note');
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;

-- ---------------------------------------------------------------------------
-- 2) 胴体批次 ↔ 生猪 关联表
-- uk_pig 是「一头猪只能归属一个批次」的最终防线：应用层先查一遍只是快速路径，
-- 两条分拣线同时上报同一头白条时，靠的是这个唯一键而不是应用层的判断。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.carcass_batch_pig (
    id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
    batch_id    BIGINT      NOT NULL COMMENT '所属胴体批次 carcass_batch.id',
    pig_id      BIGINT      NOT NULL COMMENT '生猪档案 pig_individual.id',
    weight_kg   DECIMAL(6,2) COMMENT '该头胴体过磅重量(kg)，与批次总重量同源',
    create_time DATETIME    NOT NULL COMMENT '归入批次的时间',
    UNIQUE KEY uk_pig (pig_id),
    KEY idx_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '胴体批次关联生猪（一头猪只能归属一个批次，由 uk_pig 保证）';

-- ---------------------------------------------------------------------------
-- 3) 存量回填
-- ---------------------------------------------------------------------------
-- 3.1 头数 = pig_ids 长度
UPDATE db_distribution.carcass_batch
SET pig_count = IFNULL(JSON_LENGTH(pig_ids), 0)
WHERE pig_count = 0;

-- 3.2 存量批次一律置「已封」：它们都是已经成型的批次（全部为人工建批），
--     本就不该再吸收分拣台推来的白条。新批次默认 status=1（归集中）。
UPDATE db_distribution.carcass_batch SET status = 2 WHERE source = 'MANUAL';

-- 3.3 关联表回填（INSERT IGNORE 防重复执行；存量数据无同一头猪跨批次的情况）
INSERT IGNORE INTO db_distribution.carcass_batch_pig (batch_id, pig_id, create_time)
SELECT b.id, jt.pig_id, IFNULL(b.create_time, NOW())
FROM db_distribution.carcass_batch b,
     JSON_TABLE(b.pig_ids, '$[*]' COLUMNS (pig_id BIGINT PATH '$')) jt
WHERE b.pig_ids IS NOT NULL AND JSON_LENGTH(b.pig_ids) > 0;

-- ---------------------------------------------------------------------------
-- 4) 核验
-- ---------------------------------------------------------------------------
SELECT '批次总数' AS item, COUNT(*) AS v FROM db_distribution.carcass_batch
UNION ALL SELECT '关联行总数', COUNT(*) FROM db_distribution.carcass_batch_pig
UNION ALL SELECT '头数与关联行不一致的批次',
    COUNT(*) FROM db_distribution.carcass_batch b
    WHERE b.pig_count <> (SELECT COUNT(*) FROM db_distribution.carcass_batch_pig p WHERE p.batch_id = b.id)
UNION ALL SELECT '归集中(status=1)的批次', COUNT(*) FROM db_distribution.carcass_batch WHERE status = 1
UNION ALL SELECT '已封(status=2)的批次', COUNT(*) FROM db_distribution.carcass_batch WHERE status = 2;

SELECT id, batch_no, pig_count, status, source, slaughterhouse FROM db_distribution.carcass_batch ORDER BY id;
