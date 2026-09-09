USE db_distribution;

CREATE TABLE IF NOT EXISTS carcass_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, batch_no VARCHAR(32) NOT NULL, pig_ids JSON NOT NULL,
    total_weight_kg DECIMAL(8,1), slaughterhouse VARCHAR(128), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    operator VARCHAR(32), UNIQUE KEY uk_batch_no (batch_no)
) ENGINE=InnoDB COMMENT='胴体批次';

CREATE TABLE IF NOT EXISTS split_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, batch_no VARCHAR(32) NOT NULL, parent_batch_id BIGINT NOT NULL,
    split_level TINYINT DEFAULT 1, product_name VARCHAR(64) NOT NULL, weight_kg DECIMAL(6,1),
    package_count INT DEFAULT 1, package_type VARCHAR(32), split_time DATETIME NOT NULL,
    workshop VARCHAR(64), workshop_temp DECIMAL(4,1), operator VARCHAR(32), file_ids TEXT,
    content_hash CHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_no (batch_no), INDEX idx_parent (parent_batch_id), INDEX idx_level (split_level)
) ENGINE=InnoDB COMMENT='分割批次';

CREATE TABLE IF NOT EXISTS cold_chain_transport (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, transport_no VARCHAR(32) NOT NULL, split_batch_id BIGINT NOT NULL,
    vehicle_no VARCHAR(16) NOT NULL, vehicle_type VARCHAR(32), refrigeration VARCHAR(64),
    driver_name VARCHAR(32), driver_phone VARCHAR(20), origin VARCHAR(256), destination VARCHAR(256),
    planned_depart DATETIME, planned_arrive DATETIME, depart_time DATETIME, arrive_time DATETIME,
    status TINYINT DEFAULT 1, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_transport_no (transport_no), INDEX idx_status (status), INDEX idx_batch (split_batch_id)
) ENGINE=InnoDB COMMENT='冷链运输';

CREATE TABLE IF NOT EXISTS temperature_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, transport_id BIGINT NOT NULL, record_time DATETIME NOT NULL,
    temperature DECIMAL(4,1) NOT NULL, temp_range_min DECIMAL(4,1) DEFAULT -18.0,
    temp_range_max DECIMAL(4,1) DEFAULT 0.0, is_abnormal TINYINT DEFAULT 0,
    recorder VARCHAR(32), record_method VARCHAR(16) DEFAULT 'MANUAL', create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transport_time (transport_id, record_time), INDEX idx_abnormal (is_abnormal)
) ENGINE=InnoDB COMMENT='冷链温度记录';

CREATE TABLE IF NOT EXISTS store_receipt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, transport_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
    store_name VARCHAR(128) NOT NULL, receipt_time DATETIME NOT NULL, receiver VARCHAR(32) NOT NULL,
    receiver_phone VARCHAR(20), qty_check TINYINT DEFAULT 1, qty_diff_note VARCHAR(256),
    temp_check TINYINT DEFAULT 1, temp_value DECIMAL(4,1), package_intact TINYINT DEFAULT 1,
    receipt_photo JSON, e_signature TEXT, content_hash CHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_transport (transport_id), INDEX idx_store (store_id), INDEX idx_receipt_time (receipt_time)
) ENGINE=InnoDB COMMENT='门店签收确认';
