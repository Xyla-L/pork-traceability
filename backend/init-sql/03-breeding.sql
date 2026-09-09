USE db_breeding;

CREATE TABLE IF NOT EXISTS farm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, farm_name VARCHAR(128) NOT NULL, license_no VARCHAR(32) NOT NULL,
    address VARCHAR(256), contact_person VARCHAR(32), contact_phone VARCHAR(20), scale INT, status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_license_no (license_no), INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='养殖场';

CREATE TABLE IF NOT EXISTS pig_individual (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, ear_tag_no VARCHAR(32) NOT NULL, farm_id BIGINT NOT NULL,
    breed VARCHAR(32), birth_date DATE, pen_no VARCHAR(16), source VARCHAR(64), status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ear_tag (ear_tag_no), INDEX idx_farm_status (farm_id, status), INDEX idx_deleted (deleted)
) ENGINE=InnoDB COMMENT='生猪个体档案';

CREATE TABLE IF NOT EXISTS vaccine_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, pig_id BIGINT NOT NULL, vaccine_name VARCHAR(64) NOT NULL,
    batch_no VARCHAR(32) NOT NULL, manufacturer VARCHAR(128), inject_time DATETIME NOT NULL,
    dosage VARCHAR(16), inject_site VARCHAR(32), operator VARCHAR(32) NOT NULL, file_ids JSON,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, INDEX idx_pig (pig_id), INDEX idx_time (inject_time)
) ENGINE=InnoDB COMMENT='疫苗注射记录';

CREATE TABLE IF NOT EXISTS slaughter_apply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, pig_id BIGINT NOT NULL, apply_no VARCHAR(32) NOT NULL,
    apply_time DATETIME NOT NULL, weight_kg DECIMAL(6,1), target_slaughterhouse VARCHAR(128),
    approval_status TINYINT DEFAULT 0, approval_time DATETIME, approver VARCHAR(32), reject_reason VARCHAR(256),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pig (pig_id), UNIQUE KEY uk_apply_no (apply_no), INDEX idx_status (approval_status)
) ENGINE=InnoDB COMMENT='出栏申报';

CREATE TABLE IF NOT EXISTS quarantine_certificate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, pig_id BIGINT NOT NULL, cert_no VARCHAR(32) NOT NULL,
    issue_org VARCHAR(128) NOT NULL, issue_time DATETIME NOT NULL, valid_until DATE, inspector VARCHAR(32),
    cert_type VARCHAR(32) DEFAULT '产地检疫', file_id VARCHAR(64) NOT NULL, ca_signature TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cert_no (cert_no), UNIQUE KEY uk_pig (pig_id)
) ENGINE=InnoDB COMMENT='产地检疫证明';
