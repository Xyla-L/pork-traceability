USE db_slaughter;

CREATE TABLE IF NOT EXISTS entry_inspection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, pig_id BIGINT NOT NULL, batch_no VARCHAR(32) NOT NULL,
    ear_tag_no VARCHAR(32), source_farm VARCHAR(128), arrive_time DATETIME NOT NULL, weight DECIMAL(7,2),
    quarantine_cert VARCHAR(32), vehicle_no VARCHAR(16), health_check TINYINT, cert_verified TINYINT,
    abnormal_note VARCHAR(512), status TINYINT DEFAULT 0, remark VARCHAR(512), inspector VARCHAR(32) NOT NULL,
    file_ids TEXT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_pig (batch_no, pig_id), INDEX idx_pig (pig_id), INDEX idx_ear_tag (ear_tag_no),
    INDEX idx_status_time (status, arrive_time)
) ENGINE=InnoDB COMMENT='入场查验记录';

CREATE TABLE IF NOT EXISTS slaughter_inspection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, pig_id BIGINT NOT NULL, inspect_no VARCHAR(32) NOT NULL,
    batch_no VARCHAR(32) NOT NULL, ear_tag_no VARCHAR(32), inspect_type TINYINT NOT NULL,
    inspect_time DATETIME NOT NULL, temperature DECIMAL(4,1), organ_check TEXT, result TINYINT,
    status TINYINT DEFAULT 0, conclusion VARCHAR(512), issue_desc VARCHAR(512), disposal VARCHAR(128),
    veterinary VARCHAR(32) NOT NULL, license_no VARCHAR(32), e_signature TEXT, file_ids TEXT,
    content_hash CHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inspect_no (inspect_no), INDEX idx_pig_type (pig_id, inspect_type),
    INDEX idx_batch (batch_no), INDEX idx_status_time (status, inspect_time)
) ENGINE=InnoDB COMMENT='屠宰检验';

CREATE TABLE IF NOT EXISTS ractopamine_test (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, pig_id BIGINT NOT NULL, test_no VARCHAR(32) NOT NULL,
    batch_no VARCHAR(32) NOT NULL, sample_no VARCHAR(32) NOT NULL, test_type VARCHAR(32) NOT NULL,
    test_time DATETIME NOT NULL, test_method VARCHAR(32) NOT NULL,
    test_target VARCHAR(64) DEFAULT '盐酸克伦特罗+莱克多巴胺+沙丁胺醇', sample_part VARCHAR(32),
    result TINYINT, status TINYINT DEFAULT 0, detection_limit VARCHAR(16), operator VARCHAR(32) NOT NULL,
    file_ids TEXT, report_url VARCHAR(512), remark VARCHAR(512), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_test_no (test_no), UNIQUE KEY uk_sample_no (sample_no), INDEX idx_pig (pig_id),
    INDEX idx_batch (batch_no), INDEX idx_status_time (status, test_time)
) ENGINE=InnoDB COMMENT='瘦肉精专项检测';

CREATE TABLE IF NOT EXISTS carcass_stamp (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, pig_id BIGINT NOT NULL, batch_no VARCHAR(32) NOT NULL,
    carcass_no VARCHAR(32) NOT NULL, stamp_no VARCHAR(32) NOT NULL, stamp_type VARCHAR(32) DEFAULT '检疫合格章',
    stamp_time DATETIME NOT NULL, stamp_position VARCHAR(64) DEFAULT '胴体两侧臀部', veterinary VARCHAR(32) NOT NULL,
    e_signature TEXT, status TINYINT DEFAULT 0, remark VARCHAR(512), content_hash CHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pig (pig_id), UNIQUE KEY uk_stamp_no (stamp_no), UNIQUE KEY uk_carcass_no (carcass_no),
    INDEX idx_batch (batch_no), INDEX idx_status_time (status, stamp_time)
) ENGINE=InnoDB COMMENT='胴体检疫合格印章';
