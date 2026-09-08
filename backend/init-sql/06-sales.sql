USE db_sales;

CREATE TABLE IF NOT EXISTS retail_sale (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, split_batch_id BIGINT NOT NULL, product_qr_code VARCHAR(64) NOT NULL,
    store_id BIGINT NOT NULL, store_name VARCHAR(128), shelf_time DATETIME, sell_time DATETIME,
    sell_price DECIMAL(8,2), sell_weight_kg DECIMAL(6,2), is_activated TINYINT DEFAULT 0,
    activate_time DATETIME, status TINYINT DEFAULT 1, expire_date DATE NOT NULL, block_hash VARCHAR(128),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_qr (product_qr_code),
    INDEX idx_status_expire (status, expire_date), INDEX idx_store (store_id), INDEX idx_batch (split_batch_id)
) ENGINE=InnoDB COMMENT='零售终端产品';

CREATE TABLE IF NOT EXISTS expire_warning (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, sale_id BIGINT NOT NULL, warning_level TINYINT NOT NULL,
    warning_time DATETIME NOT NULL, notify_channel VARCHAR(32), notified TINYINT DEFAULT 0,
    handled TINYINT DEFAULT 0, handle_time DATETIME, handler VARCHAR(32), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sale_level (sale_id, warning_level), INDEX idx_warning (warning_level, handled)
) ENGINE=InnoDB COMMENT='过期预警';

CREATE TABLE IF NOT EXISTS recall_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, recall_no VARCHAR(32) NOT NULL, reason VARCHAR(512) NOT NULL,
    risk_level TINYINT DEFAULT 1, scope JSON NOT NULL, initiator VARCHAR(32) NOT NULL,
    initiate_time DATETIME NOT NULL, status TINYINT DEFAULT 1, completed_time DATETIME,
    affected_count INT DEFAULT 0, recalled_count INT DEFAULT 0, block_hash VARCHAR(128),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recall_no (recall_no), INDEX idx_status (status), INDEX idx_time (initiate_time)
) ENGINE=InnoDB COMMENT='召回指令';
