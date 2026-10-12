-- =============================================================================
-- 16-void-and-fallback.sql
-- 作废留痕 + 人工兜底解锁
--
-- 背景：
--   前一版做完了「来源管控」——关键环节的人工新增入口被服务端拦死。
--   但拦得住「新增」，拦不住「改动」：
--     1. 已入账的记录还能被 PUT 原地改掉，改完重算哈希重新上链，
--        链上留着两个版本，可「哪一次改是合规的」根本无从判断；
--     2. 删接口是物理删除，删完链上哈希就成了孤儿，追溯链直接断掉；
--     3. 各表表示「作废」的字段五花八门（inspection 用 status=3、stamp 用 status=2、
--        其余表干脆没有），没法用统一口径回答「这条记录现在还算不算数」。
--
-- 本次改动：
--   1. 所有溯源主表统一加 void_flag / void_reason / void_operator / void_time 四列。
--      void_flag 同时作为 MyBatis-Plus 的逻辑删除标记，查询默认只取有效记录，
--      作废记录留在库里不物理删，需要时可显式查出来标注。
--   2. 新建 db_ingest.source_fallback_unlock：关键环节想临时人工补录，
--      必须先申请一张有时限的「兜底通行证」，谁申请的、为什么、什么时候失效，全留痕。
--
-- 执行方式（PowerShell 管道会毁 UTF-8，必须走 docker cp 二进制通道）：
--   docker cp backend/init-sql/16-void-and-fallback.sql pork-traceability-mysql-1:/tmp/16.sql
--   docker exec pork-traceability-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/16.sql'
-- =============================================================================

SET NAMES utf8mb4;

-- 建存储过程需要一个缺省库（脚本里的表名都写全了库名，这里只为让 DROP/CREATE PROCEDURE 有落脚点）
USE db_ingest;

DROP PROCEDURE IF EXISTS add_void_columns;
DELIMITER $$
CREATE PROCEDURE add_void_columns(IN p_db VARCHAR(64), IN p_table VARCHAR(64))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_table AND COLUMN_NAME = 'void_flag') THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_db, '`.`', p_table, '`',
                          ' ADD COLUMN void_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''0=有效 1=已作废''',
                          ', ADD COLUMN void_reason VARCHAR(500) DEFAULT NULL COMMENT ''作废理由（必填）''',
                          ', ADD COLUMN void_operator VARCHAR(128) DEFAULT NULL COMMENT ''作废操作人（实名）''',
                          ', ADD COLUMN void_time DATETIME DEFAULT NULL COMMENT ''作废时间''',
                          ', ADD INDEX idx_void_flag (void_flag)');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 屠宰：入场查验 / 屠宰检验 / 瘦肉精 / 胴体盖章
CALL add_void_columns('db_slaughter', 'entry_inspection');
CALL add_void_columns('db_slaughter', 'slaughter_inspection');
CALL add_void_columns('db_slaughter', 'ractopamine_test');
CALL add_void_columns('db_slaughter', 'carcass_stamp');

-- 养殖：建档 / 免疫 / 检疫证
CALL add_void_columns('db_breeding', 'pig_individual');
CALL add_void_columns('db_breeding', 'vaccine_record');
CALL add_void_columns('db_breeding', 'quarantine_certificate');

-- 配送：分割批次 / 冷链温度 / 门店签收
CALL add_void_columns('db_distribution', 'split_batch');
CALL add_void_columns('db_distribution', 'temperature_log');
CALL add_void_columns('db_distribution', 'store_receipt');

-- 销售：零售售出
CALL add_void_columns('db_sales', 'retail_sale');

DROP PROCEDURE IF EXISTS add_void_columns;

-- -----------------------------------------------------------------------------
-- 历史「作废」语义迁移：把各表原来五花八门的作废标记归一到 void_flag
--   屠宰检验 status=3 即「已作废」；胴体盖章 status=2 即「已作废」；
--   养殖建档原来是 MyBatis-Plus 逻辑删除（deleted=1），语义上同样是「不再生效」。
--   迁移时必须承认这是历史标记，不能假装有人按新流程走了一遍作废。
-- -----------------------------------------------------------------------------
UPDATE db_slaughter.slaughter_inspection
SET void_flag = 1,
    void_reason = COALESCE(void_reason, '历史作废记录（状态字段迁移）'),
    void_operator = COALESCE(void_operator, 'system-migration'),
    void_time = COALESCE(void_time, NOW())
WHERE status = 3 AND void_flag = 0;

UPDATE db_slaughter.carcass_stamp
SET void_flag = 1,
    void_reason = COALESCE(void_reason, '历史作废记录（状态字段迁移）'),
    void_operator = COALESCE(void_operator, 'system-migration'),
    void_time = COALESCE(void_time, NOW())
WHERE status = 2 AND void_flag = 0;

UPDATE db_breeding.pig_individual
SET void_flag = 1,
    void_reason = COALESCE(void_reason, '历史逻辑删除记录（deleted 迁移）'),
    void_operator = COALESCE(void_operator, 'system-migration'),
    void_time = COALESCE(void_time, NOW())
WHERE deleted = 1 AND void_flag = 0;

-- -----------------------------------------------------------------------------
-- 人工兜底解锁表
--   关键环节（设备优先）默认不展示人工入口，确需人工补录时先申请一张限时通行证。
--   记录申请的环节、理由、申请人、失效时间与关闭情况，链上数据一旦被人工写入，
--   顺着这张表就能查到「当时是谁、基于什么理由放的口子」。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_ingest.source_fallback_unlock (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '解锁记录ID',
    stage_code VARCHAR(32) NOT NULL COMMENT '溯源环节代码，对应 TraceStage',
    reason VARCHAR(500) NOT NULL COMMENT '申请兜底录入的理由（必填）',
    applicant_id VARCHAR(64) DEFAULT NULL COMMENT '申请人用户ID（网关透传）',
    applicant_name VARCHAR(128) DEFAULT NULL COMMENT '申请人姓名',
    apply_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    expire_time DATETIME NOT NULL COMMENT '通行证失效时间',
    closed TINYINT NOT NULL DEFAULT 0 COMMENT '0=生效中 1=已手动关闭',
    close_operator VARCHAR(128) DEFAULT NULL COMMENT '关闭人',
    close_reason VARCHAR(500) DEFAULT NULL COMMENT '关闭理由',
    close_time DATETIME DEFAULT NULL COMMENT '关闭时间',
    PRIMARY KEY (id),
    KEY idx_stage_valid (stage_code, closed, expire_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='人工兜底录入解锁记录：关键环节默认隐藏人工入口，申请解锁后方可录入，全程留痕';

-- -----------------------------------------------------------------------------
-- 自检：应输出 11 行（每张表一行），且 all_columns_present 均为 4
-- -----------------------------------------------------------------------------
SELECT '--- 作废字段自检 ---' AS check_item;
SELECT TABLE_SCHEMA, TABLE_NAME, COUNT(*) AS all_columns_present
FROM information_schema.COLUMNS
WHERE (TABLE_SCHEMA, TABLE_NAME) IN (
    ('db_slaughter', 'entry_inspection'), ('db_slaughter', 'slaughter_inspection'),
    ('db_slaughter', 'ractopamine_test'), ('db_slaughter', 'carcass_stamp'),
    ('db_breeding', 'pig_individual'), ('db_breeding', 'vaccine_record'),
    ('db_breeding', 'quarantine_certificate'),
    ('db_distribution', 'split_batch'), ('db_distribution', 'temperature_log'),
    ('db_distribution', 'store_receipt'), ('db_sales', 'retail_sale'))
  AND COLUMN_NAME IN ('void_flag', 'void_reason', 'void_operator', 'void_time')
GROUP BY TABLE_SCHEMA, TABLE_NAME
ORDER BY TABLE_SCHEMA, TABLE_NAME;

SELECT '--- 历史作废迁移结果 ---' AS check_item;
SELECT 'slaughter_inspection' AS tbl, COUNT(*) AS void_rows FROM db_slaughter.slaughter_inspection WHERE void_flag = 1
UNION ALL SELECT 'carcass_stamp', COUNT(*) FROM db_slaughter.carcass_stamp WHERE void_flag = 1
UNION ALL SELECT 'pig_individual', COUNT(*) FROM db_breeding.pig_individual WHERE void_flag = 1;

SELECT '--- 兜底解锁表 ---' AS check_item;
SELECT COUNT(*) AS unlock_table_exists FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'db_ingest' AND TABLE_NAME = 'source_fallback_unlock';
