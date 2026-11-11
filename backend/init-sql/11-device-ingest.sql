-- ============================================================================
-- 设备/第三方系统自动录入（B 档）接入层
-- 1) 业务表补"数据来源"三件套：source / source_ref / raw_payload
--    没有来源字段，自动化之后审计分不清哪条是人录的、哪条是机器写的
-- 2) 设备台账 ingest_device（设备号 + 密钥 + 通道 + 状态）
-- 3) 待确认队列 ingest_staging（幂等键、原始报文、校验结果、入账回执）
-- 注意：本文件须在 04/05/06 建表之后执行（文件名以 11- 开头保证顺序）
-- ============================================================================

USE db_ingest;

CREATE TABLE IF NOT EXISTS ingest_device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_no VARCHAR(64) NOT NULL COMMENT '设备编号（设备侧唯一标识）',
    device_name VARCHAR(128) NOT NULL COMMENT '设备名称',
    channel VARCHAR(32) NOT NULL COMMENT '接入通道 TEMPERATURE/ENTRY/RACTOPAMINE/SALE/RECEIPT',
    location VARCHAR(128) COMMENT '安装位置',
    secret_key VARCHAR(128) NOT NULL COMMENT '设备密钥（X-Device-Key）',
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    last_seen_time DATETIME COMMENT '最近一次上报时间',
    remark VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_no (device_no),
    INDEX idx_channel (channel),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='接入设备台账';

CREATE TABLE IF NOT EXISTS ingest_staging (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel VARCHAR(32) NOT NULL COMMENT '接入通道',
    device_no VARCHAR(64) NOT NULL COMMENT '来源设备编号',
    biz_key VARCHAR(96) NOT NULL COMMENT '设备侧业务唯一号（幂等键，与 device_no 组成唯一约束）',
    report_time DATETIME NOT NULL COMMENT '设备侧数据时间',
    receive_time DATETIME NOT NULL COMMENT '接入层接收时间',
    payload JSON NOT NULL COMMENT '原始报文（全量留档，不可变）',
    source_ref VARCHAR(128) COMMENT '来源单据号/设备流水号',
    status TINYINT DEFAULT 0 COMMENT '0待处理 1待人工处理 2已入账 3已拒绝 4已降采样丢弃',
    target_table VARCHAR(64) COMMENT '入账目标表',
    target_id BIGINT COMMENT '入账目标主键',
    error_msg VARCHAR(1024) COMMENT '校验失败原因（多条以 ; 分隔）',
    handle_time DATETIME COMMENT '人工处理时间',
    handler VARCHAR(32) COMMENT '人工处理人',
    remark VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_biz (device_no, biz_key),
    INDEX idx_status_time (status, receive_time),
    INDEX idx_channel_time (channel, receive_time),
    INDEX idx_report_time (report_time)
) ENGINE=InnoDB COMMENT='设备上报待确认队列';

-- ---------------------------------------------------------------------------
-- 业务表来源标记
-- MANUAL=人工录入 DEVICE=设备自动采集 API=第三方系统推送 IMPORT=批量导入
-- raw_payload 保留设备原始报文，便于事后逐字节核对
-- ---------------------------------------------------------------------------
USE db_distribution;
ALTER TABLE temperature_log
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER record_method,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

ALTER TABLE store_receipt
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER e_signature,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

USE db_slaughter;
ALTER TABLE entry_inspection
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER inspector,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

ALTER TABLE ractopamine_test
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER operator,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

USE db_sales;
ALTER TABLE retail_sale
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER block_hash,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

-- ---------------------------------------------------------------------------
-- 演示设备台账（密钥仅用于本地开发/答辩演示，生产须逐一更换并走密钥管理）
--
-- 2026-10-24 收敛：同一通道里报文完全相同的重复设备已删——
--   · ENTRY 只留 GATE-001（耳标识读器/检疫证核验终端与它走同一个 handler，
--     示例报文逐字段相同，多台只是名字不同，留着只会让人困惑「差别在哪」）；
--   · TEMPERATURE 只留 TEMP-001（冷库固定探头 TEMP-002、二号车探头 TEMP-003 同理；
--     模拟采集按「运输中的运单」扫描，不依赖设备表，删探头不影响温度演示）。
-- 车载终端同理只留 ARRIVE-001（见 38/39 号脚本）。
-- ---------------------------------------------------------------------------
USE db_ingest;
INSERT IGNORE INTO ingest_device (device_no, device_name, channel, location, secret_key, status, remark) VALUES
('TEMP-001', '冷藏车温度探头 京A·12345', 'TEMPERATURE', '一号冷藏车车厢', 'dev-key-temp-001', 1, '车载温控终端，300秒上报一次'),
('GATE-001', '入场门禁地磅', 'ENTRY', '屠宰场北门', 'dev-key-gate-001', 1, '车牌识别 + 地磅称重；入场事实由这一台设备采集（扫证/读耳标的差异体现在报文字段，不体现在设备台数）'),
('READER-001', '瘦肉精读数仪', 'RACTOPAMINE', '屠宰场化验室', 'dev-key-reader-001', 1, '胶体金读数仪，串口输出结果'),
('POS-001', '门店收银机 示范一店', 'SALE', '示范一店门店', 'dev-key-pos-001', 1, '扫码即激活+售出'),
('PDA-001', '门店签收PDA 示范一店', 'RECEIPT', '示范一店收货口', 'dev-key-pda-001', 1, '扫码签收 + 冷柜测温'),
('SHELF-001', '门店冷柜温度探头', 'RECEIPT', '示范一店冷柜', 'dev-key-shelf-001', 1, '签收环节温度采集');
