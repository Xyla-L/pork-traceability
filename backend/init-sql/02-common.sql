SET NAMES utf8mb4;

USE db_common;

CREATE TABLE IF NOT EXISTS sys_org (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    manager VARCHAR(32), phone VARCHAR(20), address VARCHAR(256), remark VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id), INDEX idx_type (type)
) ENGINE=InnoDB COMMENT='组织机构';

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash CHAR(64) NOT NULL,
    password_salt VARCHAR(64),
    nickname VARCHAR(64), real_name VARCHAR(32), phone VARCHAR(20), email VARCHAR(128),
    org_id BIGINT, role VARCHAR(32) NOT NULL, status TINYINT DEFAULT 1,
    last_login_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username), INDEX idx_role (role), INDEX idx_org (org_id)
) ENGINE=InnoDB COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(16) DEFAULT 'API',
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_permission_code (permission_code)
) ENGINE=InnoDB COMMENT='系统权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    INDEX idx_role_user (role_id, user_id)
) ENGINE=InnoDB COMMENT='用户角色关联';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    INDEX idx_permission_role (permission_id, role_id)
) ENGINE=InnoDB COMMENT='角色权限关联';

INSERT INTO sys_role (role_code, role_name) VALUES
    ('FARMER', '养殖人员'), ('SLAUGHTER_OP', '屠宰人员'),
    ('DISTRIBUTOR', '配送人员'), ('RETAILER', '零售人员'),
    ('SUPERVISOR', '监管人员'), ('ADMIN', '系统管理员')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO sys_permission (permission_code, permission_name) VALUES
    ('breeding:manage', '养殖管理'), ('slaughter:manage', '屠宰管理'),
    ('distribution:manage', '配送管理'), ('sales:manage', '销售管理'),
    ('trace:read', '追溯查询'), ('complaint:handle', '举报处理'),
    ('recall:manage', '召回管理'), ('blockchain:audit', '存证审计'),
    ('system:manage', '系统管理')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name);

CREATE TABLE IF NOT EXISTS complaint_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_no VARCHAR(32) NOT NULL, reporter_name VARCHAR(32), reporter_phone VARCHAR(20),
    target_qr_code VARCHAR(64), target_batch VARCHAR(64), complaint_text VARCHAR(1024) NOT NULL,
    file_ids JSON, status TINYINT DEFAULT 0, handler VARCHAR(32), handle_note VARCHAR(512),
    handle_time DATETIME, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    device_id VARCHAR(64),
    UNIQUE KEY uk_report_no (report_no), INDEX idx_status (status), INDEX idx_batch (target_batch),
    INDEX idx_device (device_id)
) ENGINE=InnoDB COMMENT='消费者举报信息';

ALTER TABLE complaint_report ADD COLUMN IF NOT EXISTS device_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL, title VARCHAR(128) NOT NULL, content VARCHAR(512) NOT NULL,
    type VARCHAR(16) DEFAULT 'info', read_status TINYINT DEFAULT 0,
    biz_type VARCHAR(32), biz_id BIGINT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP, read_time DATETIME,
    INDEX idx_user_read (user_id, read_status, create_time),
    UNIQUE KEY uk_user_biz (user_id, biz_type, biz_id)
) ENGINE=InnoDB COMMENT='用户通知';

CREATE TABLE IF NOT EXISTS blockchain_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL, biz_type VARCHAR(32) NOT NULL, biz_id BIGINT NOT NULL,
    biz_key VARCHAR(64) NOT NULL, content_hash CHAR(64) NOT NULL, tx_hash VARCHAR(128),
    block_number BIGINT, chain_time DATETIME, status TINYINT DEFAULT 0, retry_count INT DEFAULT 0,
    error_msg VARCHAR(512), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_event_id (event_id), UNIQUE KEY uk_tx_hash (tx_hash),
    INDEX idx_biz (biz_type, biz_id), INDEX idx_biz_key (biz_key), INDEX idx_status_time (status, update_time)
) ENGINE=InnoDB COMMENT='区块链存证记录';

CREATE TABLE IF NOT EXISTS blockchain_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    biz_key VARCHAR(64) NOT NULL, content_hash CHAR(64) NOT NULL, tx_hash VARCHAR(128) NOT NULL,
    block_number BIGINT NOT NULL, chain_time DATETIME NOT NULL,
    UNIQUE KEY uk_tx_hash (tx_hash), INDEX idx_biz_key (biz_key), INDEX idx_block_number (block_number)
) ENGINE=InnoDB COMMENT='开发环境独立区块链账本';

CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_id VARCHAR(64) NOT NULL, original_name VARCHAR(256) NOT NULL, stored_path VARCHAR(512) NOT NULL,
    file_size BIGINT NOT NULL, mime_type VARCHAR(64) NOT NULL, sha256 CHAR(64) NOT NULL,
    uploader VARCHAR(32), upload_time DATETIME DEFAULT CURRENT_TIMESTAMP, biz_ref VARCHAR(128),
    UNIQUE KEY uk_file_id (file_id), INDEX idx_sha256 (sha256), INDEX idx_upload_time (upload_time)
) ENGINE=InnoDB COMMENT='文件元数据';
