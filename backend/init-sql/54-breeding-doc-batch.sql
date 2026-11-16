-- ============================================================================
-- 54 养殖三模块单据化：免疫 / 产地检疫 / 出栏申报各有一张「作业单据」
--
-- 背景（2026-11-15）：现实里这三个动作都是「一次作业覆盖多头」——
--   一批猪同一天打同一种疫苗、一张产地检疫证覆盖一车猪、一份出栏申报含一车猪。
--   原来三张表都是「一条记录挂一头猪」，页面上看到的「批次」是查询时按
--   「养殖批次 + 动作签名 + 日期」临时算出来的（RecordGroupServiceImpl），
--   它的上限写在代码注释里：数据一多就得换成真单据表。
--
-- 这里落成真单据：
--   疫苗 → vaccine_batch（免疫批次号 YH...）+ vaccine_batch_pig
--   检疫 → quarantine_certificate 升级为单据（证号即批次号）+ quarantine_cert_pig
--   申报 → slaughter_apply 升级为单据（申报号即批次号）+ slaughter_apply_pig
--
-- 逐头事实表一律保留（免疫档案是出证硬前提、出栏状态推进、追溯按头查），
-- 变的只是「批次号由单据持有」，不再由查询临时拼。
-- ============================================================================
USE db_breeding;

-- ============ 1. 免疫批次（一次免疫作业） ============
CREATE TABLE IF NOT EXISTS vaccine_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    farm_id BIGINT NOT NULL,
    batch_no VARCHAR(32) NOT NULL COMMENT '免疫批次号（这一批猪自己的批次号，与养殖批次无关）',
    vaccine_name VARCHAR(64) NOT NULL,
    prod_batch_no VARCHAR(32) COMMENT '疫苗生产批号（厂家瓶批号，与免疫批次号是两件事）',
    manufacturer VARCHAR(128),
    inject_time DATETIME NOT NULL,
    operator VARCHAR(32),
    pig_count INT NOT NULL DEFAULT 0 COMMENT '覆盖头数（成员行数，作废也计入）',
    data_source VARCHAR(16) NOT NULL DEFAULT 'DEVICE' COMMENT 'DEVICE 设备采集 / MANUAL 人工',
    source_ref VARCHAR(128),
    void_flag TINYINT NOT NULL DEFAULT 0,
    void_reason VARCHAR(256), void_operator VARCHAR(32), void_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vaccine_batch (farm_id, batch_no),
    INDEX idx_vbatch_farm_time (farm_id, inject_time),
    INDEX idx_vbatch_void (void_flag)
) ENGINE=InnoDB COMMENT='免疫批次：一次免疫作业（同场+同疫苗+同生产批号+同一天自动归为一批）';

CREATE TABLE IF NOT EXISTS vaccine_batch_pig (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    pig_id BIGINT NOT NULL,
    ear_tag_no VARCHAR(32) COMMENT '冗余耳标号：成员列表不必再 join 猪档案即可显示',
    void_flag TINYINT NOT NULL DEFAULT 0 COMMENT '该头在此批次中的记录是否作废（与 vaccine_record 同步）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vbp (batch_id, pig_id),
    INDEX idx_vbp_pig (pig_id)
) ENGINE=InnoDB COMMENT='免疫批次关联生猪';

ALTER TABLE vaccine_record
    ADD COLUMN vbatch_id BIGINT NULL COMMENT '所属免疫批次 vaccine_batch.id' AFTER pig_id,
    ADD INDEX idx_vaccine_vbatch (vbatch_id);

-- 存量回填：按「同场 + 同疫苗 + 同生产批号 + 同一天」把历史逐头记录归批，
-- 与改造前页面上算出来的分组完全一致（口径不变，只是从「算」变成「存」）。
-- 批次号里的序号取 MIN(id)：聚合查询里不能选非聚合列（only_full_group_by），
-- 而且用组内最小 id 生成的批次号是稳定可复现的，重跑结果一致。
INSERT INTO vaccine_batch (farm_id, batch_no, vaccine_name, prod_batch_no, manufacturer,
                           inject_time, operator, pig_count, data_source, source_ref, create_time)
SELECT i.farm_id,
       CONCAT('YH', DATE_FORMAT(MIN(v.inject_time), '%Y%m%d'), LPAD(MIN(v.id), 6, '0')),
       v.vaccine_name, v.batch_no, MAX(v.manufacturer),
       MIN(v.inject_time), MAX(v.operator), COUNT(*), 'DEVICE', NULL, NOW()
FROM vaccine_record v
JOIN pig_individual i ON i.id = v.pig_id
WHERE v.vbatch_id IS NULL
GROUP BY i.farm_id, v.vaccine_name, v.batch_no, DATE(v.inject_time);

-- 回填成员与关联（用同一分组键对上刚建的单据）
UPDATE vaccine_record v
JOIN pig_individual i ON i.id = v.pig_id
JOIN vaccine_batch b ON b.farm_id = i.farm_id
     AND b.vaccine_name = v.vaccine_name
     AND (b.prod_batch_no = v.batch_no OR (b.prod_batch_no IS NULL AND v.batch_no IS NULL))
     AND DATE(b.inject_time) = DATE(v.inject_time)
SET v.vbatch_id = b.id
WHERE v.vbatch_id IS NULL;

-- 成员去重：同一头猪同一天可能有多条免疫记录（不同时间补打），成员只有一条，
-- 作废标记取组内最大值（只要有一条作废就算该头在本次作业里作废）。
-- INSERT IGNORE 让这段回填可重复执行（唯一键 uk_vbp 兜底）。
INSERT IGNORE INTO vaccine_batch_pig (batch_id, pig_id, ear_tag_no, void_flag)
SELECT v.vbatch_id, v.pig_id, MAX(i.ear_tag_no), MAX(v.void_flag)
FROM vaccine_record v JOIN pig_individual i ON i.id = v.pig_id
WHERE v.vbatch_id IS NOT NULL
GROUP BY v.vbatch_id, v.pig_id;

-- ============ 2. 检疫证升级为单据 ============
ALTER TABLE quarantine_certificate
    MODIFY COLUMN pig_id BIGINT NULL COMMENT '兼容列：单据化后一张证覆盖多头，成员见 quarantine_cert_pig',
    ADD COLUMN farm_id BIGINT NULL COMMENT '养殖场（机构隔离与列表过滤用）' AFTER pig_id,
    ADD COLUMN pig_count INT NOT NULL DEFAULT 0 COMMENT '证面覆盖头数' AFTER farm_id;

CREATE TABLE IF NOT EXISTS quarantine_cert_pig (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cert_id BIGINT NOT NULL,
    pig_id BIGINT NOT NULL,
    ear_tag_no VARCHAR(32),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qcp (cert_id, pig_id),
    INDEX idx_qcp_pig (pig_id)
) ENGINE=InnoDB COMMENT='检疫证关联生猪（一证多头）';

INSERT INTO quarantine_cert_pig (cert_id, pig_id, ear_tag_no, create_time)
SELECT c.id, c.pig_id, i.ear_tag_no, c.create_time
FROM quarantine_certificate c JOIN pig_individual i ON i.id = c.pig_id
WHERE c.pig_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM quarantine_cert_pig m WHERE m.cert_id = c.id AND m.pig_id = c.pig_id);

UPDATE quarantine_certificate c
SET c.farm_id = (SELECT i.farm_id FROM pig_individual i WHERE i.id = c.pig_id)
WHERE c.farm_id IS NULL AND c.pig_id IS NOT NULL;

UPDATE quarantine_certificate c
SET c.pig_count = (SELECT COUNT(*) FROM quarantine_cert_pig m WHERE m.cert_id = c.id);

-- ============ 3. 出栏申报升级为单据 ============
ALTER TABLE slaughter_apply
    MODIFY COLUMN pig_id BIGINT NULL COMMENT '兼容列：单据化后一张申报单覆盖多头，成员见 slaughter_apply_pig',
    ADD COLUMN farm_id BIGINT NULL COMMENT '养殖场（机构隔离与列表过滤用）' AFTER pig_id,
    ADD COLUMN pig_count INT NOT NULL DEFAULT 1 COMMENT '申报头数' AFTER farm_id,
    ADD COLUMN total_weight_kg DECIMAL(10,1) NULL COMMENT '申报总重（逐头体重之和）' AFTER weight_kg,
    DROP INDEX uk_pig;

CREATE TABLE IF NOT EXISTS slaughter_apply_pig (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    apply_id BIGINT NOT NULL,
    pig_id BIGINT NOT NULL,
    ear_tag_no VARCHAR(32),
    weight_kg DECIMAL(6,1) COMMENT '该头体重（设备称重来自地磅，人工申报可空）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sap (apply_id, pig_id),
    INDEX idx_sap_pig (pig_id)
) ENGINE=InnoDB COMMENT='出栏申报关联生猪（一单多头）';

INSERT INTO slaughter_apply_pig (apply_id, pig_id, ear_tag_no, weight_kg, create_time)
SELECT a.id, a.pig_id, i.ear_tag_no, a.weight_kg, a.create_time
FROM slaughter_apply a JOIN pig_individual i ON i.id = a.pig_id
WHERE a.pig_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM slaughter_apply_pig m WHERE m.apply_id = a.id AND m.pig_id = a.pig_id);

UPDATE slaughter_apply a
SET a.farm_id = (SELECT i.farm_id FROM pig_individual i WHERE i.id = a.pig_id)
WHERE a.farm_id IS NULL AND a.pig_id IS NOT NULL;

UPDATE slaughter_apply a
SET a.pig_count = CASE WHEN a.pig_count IS NULL OR a.pig_count < 1 THEN 1 ELSE a.pig_count END,
    a.total_weight_kg = COALESCE(a.total_weight_kg, a.weight_kg)
WHERE a.total_weight_kg IS NULL;
