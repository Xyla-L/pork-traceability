-- 2026-10-25 生猪档案批次化：运行库迁移（幂等）
-- 1) 新建批次档表；2) 个体表补 batch_id 列；3) 为存量演示/测试猪补批次档。

USE db_breeding;

CREATE TABLE IF NOT EXISTS pig_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, batch_no VARCHAR(32) NOT NULL, farm_id BIGINT NOT NULL,
    breed VARCHAR(32), entry_date DATE, ear_tag_start VARCHAR(32) NOT NULL, ear_tag_end VARCHAR(32) NOT NULL,
    quantity INT NOT NULL, source VARCHAR(64),
    data_source VARCHAR(16) NOT NULL DEFAULT 'MANUAL', source_ref VARCHAR(128), raw_payload TEXT,
    void_flag TINYINT DEFAULT 0, void_reason VARCHAR(256), void_operator VARCHAR(32), void_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch (farm_id, batch_no), INDEX idx_farm (farm_id), INDEX idx_void (void_flag)
) ENGINE=InnoDB COMMENT='生猪批次档案';

SET @has_batch_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'db_breeding' AND TABLE_NAME = 'pig_individual' AND COLUMN_NAME = 'batch_id');
SET @ddl := IF(@has_batch_col = 0,
    'ALTER TABLE pig_individual ADD COLUMN batch_id BIGINT COMMENT ''所属生猪批次 pig_batch.id'', ADD INDEX idx_batch (batch_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 演示批：PB-DEMO-0001（示范生态养殖场，区间 ET-DEMO-0001 ~ ET-DEMO-0003）
INSERT INTO pig_batch (batch_no, farm_id, breed, entry_date, ear_tag_start, ear_tag_end, quantity, source, data_source, source_ref)
SELECT 'PB-DEMO-0001', id, '长白猪', DATE_SUB(CURDATE(), INTERVAL 180 DAY), 'ET-DEMO-0001', 'ET-DEMO-0003', 3, '自繁', 'MANUAL', '人工登记批次'
FROM farm WHERE license_no = 'FARM-DEMO-001'
ON DUPLICATE KEY UPDATE breed = VALUES(breed);
SET @demo_farm := (SELECT id FROM farm WHERE license_no = 'FARM-DEMO-001');
SET @demo_batch := (SELECT id FROM pig_batch WHERE batch_no = 'PB-DEMO-0001');
INSERT INTO pig_individual (ear_tag_no, farm_id, batch_id, breed, pen_no, source, status, data_source, source_ref)
VALUES ('ET-DEMO-0002', @demo_farm, @demo_batch, '长白猪', 'A-01', '自繁', 1, 'MANUAL', '生猪批次 PB-DEMO-0001（按耳标区间自动生成）'),
       ('ET-DEMO-0003', @demo_farm, @demo_batch, '长白猪', 'A-01', '自繁', 1, 'MANUAL', '生猪批次 PB-DEMO-0001（按耳标区间自动生成）')
ON DUPLICATE KEY UPDATE batch_id = VALUES(batch_id);
UPDATE pig_individual SET batch_id = @demo_batch
WHERE ear_tag_no BETWEEN 'ET-DEMO-0001' AND 'ET-DEMO-0003' AND batch_id IS NULL;

-- 测试批：PB-TEST-0xx（按场归批），存量测试猪回填 batch_id
INSERT INTO pig_batch (batch_no, farm_id, breed, ear_tag_start, ear_tag_end, quantity, source, data_source, source_ref)
SELECT CONCAT('PB-TEST-', LPAD(p.farm_id, 3, '0')), p.farm_id, MIN(p.breed), MIN(p.ear_tag_no), MAX(p.ear_tag_no), COUNT(*),
       '自繁', 'MANUAL', '人工登记批次'
FROM pig_individual p WHERE p.ear_tag_no LIKE 'ET-TEST-0%' GROUP BY p.farm_id
ON DUPLICATE KEY UPDATE breed = VALUES(breed);
UPDATE pig_individual p JOIN pig_batch b
  ON p.ear_tag_no BETWEEN b.ear_tag_start AND b.ear_tag_end
SET p.batch_id = b.id
WHERE b.batch_no LIKE 'PB-TEST-%' AND p.batch_id IS NULL;

SELECT (SELECT COUNT(*) FROM pig_batch) AS batch_rows,
       (SELECT COUNT(*) FROM pig_individual WHERE batch_id IS NOT NULL) AS pigs_with_batch;
