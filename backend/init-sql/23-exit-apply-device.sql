-- ============================================================================
-- 申请出栏接入设备通道（出栏口地磅：耳标识读 + 称重）
--
-- 背景：申请出栏原先完全是人工填单（生猪档案页逐头点「申请出栏」）。
-- 但这一环里最关键的字段——出栏体重——本来就是称出来的：猪走出栏口过磅，
-- 读到耳标 + 称重同步记录，比让人凭估重抄数可靠得多。
--
-- 设备只负责一半，这一点决定了策略是「设备优先」而不是「设备独占」：
--   · 设备给得出：哪头猪、多重、什么时候称的、哪台设备
--   · 设备给不出：目标屠宰场 —— 那是「去向声明」，不是测量值，缺了就转人工补录
--
-- 本脚本做两件事：
--   1) slaughter_apply 补「数据来源」三件套（与其它设备通道业务表口径一致）
--   2) 登记一台出栏口地磅 EXITSCALE-001，通道 = EXIT_APPLY
--
-- 注意：ALTER 语句不幂等，一次性执行；须在 03（建表）与 11/14（接入层）之后执行。
-- ============================================================================

USE db_breeding;

ALTER TABLE slaughter_apply
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL=人工申报/DEVICE=出栏口地磅采集/API/IMPORT' AFTER reject_reason,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/操作人实名（设备采集记地磅编号，如 EXITSCALE-001；人工申报记登录人实名）' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文（出栏口地磅上报的完整报文，供逐字段核对）' AFTER source_ref;

-- 存量申报都是人工填单（那时还没有设备通道），来源标 MANUAL；
-- source_ref 是申报人实名，历史数据没有记录这个值，如实留空，不回填、不编造。
UPDATE slaughter_apply SET source = 'MANUAL' WHERE source IS NULL;

-- ---------------------------------------------------------------------------
-- 出栏口地磅台账（密钥仅用于本地开发/答辩演示，生产须逐一更换并走密钥管理）
-- 与 ENTRY 通道的门禁地磅区别：那台在屠宰场门口负责「入厂查验」，
-- EXITSCALE-001 在养殖场出栏口，负责「出栏称重登记」，两头猪流方向相反。
-- ---------------------------------------------------------------------------
USE db_ingest;
INSERT IGNORE INTO ingest_device (device_no, device_name, channel, location, secret_key, status, remark) VALUES
('EXITSCALE-001', '出栏口称重登记地磅', 'EXIT_APPLY', '示范养殖场出栏通道', 'dev-key-exitscale-001', 1,
 '猪只走出栏口时自动识别耳标并称重，直接生成出栏申报；目标屠宰场由人工确认后补录，缺去向则转待人工确认');

-- ---------------------------------------------------------------------------
-- 核验
-- ---------------------------------------------------------------------------
USE db_breeding;
SHOW COLUMNS FROM slaughter_apply;

USE db_ingest;
SELECT id, device_no, device_name, channel, location, status FROM ingest_device ORDER BY id;
