USE db_breeding;

CREATE TABLE IF NOT EXISTS farm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, farm_name VARCHAR(128) NOT NULL, license_no VARCHAR(32) NOT NULL,
    address VARCHAR(256), contact_person VARCHAR(32), contact_phone VARCHAR(20), scale INT, status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_license_no (license_no), INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='养殖场';

-- 生猪批次档案：养殖端档案的登记单位（现实商品猪按批次管理，逐头建档既做不到也没人做）。
-- 人工登记一张批次表单，耳标起止号圈定成员区间；登记时区间内的耳标自动展开成 pig_individual 成员行。
-- 2026-10-26 起还可由养殖 ERP 经接入层同步进来：external_system + external_id 是外部系统的幂等键
--（两家 ERP 都叫 PB-001 是常态，(farm_id,batch_no) 挡不住），birth_date 供日龄现算，void_source 区分人工作废/源撤销。
CREATE TABLE IF NOT EXISTS pig_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, batch_no VARCHAR(32) NOT NULL, farm_id BIGINT NOT NULL,
    breed VARCHAR(32), entry_date DATE, birth_date DATE,
    ear_tag_start VARCHAR(32) NOT NULL, ear_tag_end VARCHAR(32) NOT NULL,
    quantity INT NOT NULL, source VARCHAR(64),
    external_system VARCHAR(32), external_id VARCHAR(64),
    data_source VARCHAR(16) NOT NULL DEFAULT 'MANUAL', source_ref VARCHAR(128), raw_payload TEXT,
    void_flag TINYINT DEFAULT 0, void_reason VARCHAR(256), void_operator VARCHAR(32), void_source VARCHAR(16), void_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch (farm_id, batch_no), UNIQUE KEY uk_external (external_system, external_id),
    INDEX idx_farm (farm_id), INDEX idx_void (void_flag)
) ENGINE=InnoDB COMMENT='生猪批次档案';

CREATE TABLE IF NOT EXISTS pig_individual (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, ear_tag_no VARCHAR(32) NOT NULL, farm_id BIGINT NOT NULL,
    batch_id BIGINT COMMENT '所属生猪批次 pig_batch.id；成员行由批次按耳标区间自动展开，无人工逐头建档入口',
    breed VARCHAR(32), birth_date DATE, gender TINYINT DEFAULT 1, pen_no VARCHAR(16), source VARCHAR(64), status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0, content_hash CHAR(64) DEFAULT NULL, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ear_tag (ear_tag_no), INDEX idx_farm_status (farm_id, status), INDEX idx_batch (batch_id), INDEX idx_deleted (deleted)
) ENGINE=InnoDB COMMENT='生猪个体档案（批次成员行）';

CREATE TABLE IF NOT EXISTS vaccine_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, pig_id BIGINT NOT NULL, vaccine_name VARCHAR(64) NOT NULL,
    batch_no VARCHAR(32) NOT NULL, manufacturer VARCHAR(128), inject_time DATETIME NOT NULL,
    dosage VARCHAR(16), inject_site VARCHAR(32), operator VARCHAR(32) NOT NULL, file_ids JSON,
    content_hash CHAR(64) DEFAULT NULL, create_time DATETIME DEFAULT CURRENT_TIMESTAMP, INDEX idx_pig (pig_id), INDEX idx_time (inject_time)
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
    -- pig_id 建普通索引而不是唯一键：证书作废后必须能重新出证（「作废 + 重录」是本项目的统一更正口径），
    -- 唯一键会把作废行一起算进去，导致「作废了却再也出不了证」。
    -- 「一头猪同时只能有一张有效证书」由 Service 层查有效记录来保证（见 QuarantineCertServiceImpl）。
    UNIQUE KEY uk_cert_no (cert_no), INDEX idx_pig (pig_id)
) ENGINE=InnoDB COMMENT='产地检疫证明';
