-- ============================================================================
-- 产地检疫出证接入设备通道（扫码登记终端）
--
-- 背景：产地检疫原先没有设备通道，是全项目唯一保留的人工采集入口。
-- 现实中检疫合格证明上带二维码，检疫申报点完全可以用扫码枪/手持机把
-- 证号、耳标、签发机构、有效期、官方兽医读进系统，替代手抄。
-- 本脚本让这一环具备「设备优先」的前提条件：
--   1) quarantine_certificate 补「数据来源」三件套（与其它设备通道业务表口径一致）
--   2) file_id / ca_signature 放宽为可空：扫码终端拿不到扫描件与 CA 签名时如实留空，
--      不再需要写占位字符串冒充（人工出证路径仍然必填，由 DTO 校验保证）
--   3) 登记一台扫码终端 QSCAN-001，通道 = QUARANTINE
--
-- 注意：ALTER 语句不幂等，一次性执行；须在 03（建表）与 11/14（接入层）之后执行。
-- ============================================================================

USE db_breeding;

ALTER TABLE quarantine_certificate
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL=人工出证/DEVICE=扫码登记终端采集/API/IMPORT' AFTER ca_signature,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号（扫码登记记终端编号，如 QSCAN-001）' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文（扫码终端上报的完整报文，供逐字段核对）' AFTER source_ref;

-- 扫码终端只有扫码枪的形态拿不到扫描件，也读不出 CA 签名：
-- 与其写 "CERT-SCAN-xxx" 这种占位串冒充（假凭证），不如让列允许 NULL，如实留空。
ALTER TABLE quarantine_certificate
    MODIFY COLUMN file_id VARCHAR(64) NULL COMMENT '检疫证原件扫描件文件ID（扫码登记终端可能没有）',
    MODIFY COLUMN ca_signature TEXT NULL COMMENT '签发机构CA数字签名（扫码登记终端可能读不出）';

-- ---------------------------------------------------------------------------
-- 扫码登记终端台账（密钥仅用于本地开发/答辩演示，生产须逐一更换并走密钥管理）
-- 与 CERT-001 的区别：CERT-001 挂在「入场查验」通道，只负责核验；
-- QSCAN-001 挂在「产地检疫出证」通道，负责把新证登记进系统。
-- ---------------------------------------------------------------------------
USE db_ingest;
INSERT IGNORE INTO ingest_device (device_no, device_name, channel, location, secret_key, status, remark) VALUES
('QSCAN-001', '产地检疫证扫码登记终端', 'QUARANTINE', '示范养殖场检疫申报点', 'dev-key-qscan-001', 1,
 '扫检疫单二维码带出证号/耳标/签发机构/有效期/官方兽医，人工核对后登记；只搬运证面信息，不产生检疫结论');

-- ---------------------------------------------------------------------------
-- 核验
-- ---------------------------------------------------------------------------
USE db_breeding;
SHOW COLUMNS FROM quarantine_certificate;

USE db_ingest;
SELECT id, device_no, device_name, channel, location, status FROM ingest_device ORDER BY id;
