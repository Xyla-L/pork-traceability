-- ============================================================================
-- 设备接入层二期：养殖建档 / 免疫注射 / 屠宰检验 / 胴体盖章 / 分割批次 五条新通道
-- 1) 业务表补"数据来源"三件套（pig_individual 用 data_source——它的 source 列
--    是业务上的"自繁/外购"，早已被占用）
-- 2) 新设备台账：TAG-W001 耳标读写器、INJ-001 智能注射器、STATION-001 检验工位终端、
--    STAMPER-001 自动盖章机、SPLIT-001 分割线扫码称重台
-- 注意：须在 03/04/05 建表与 11 期接入层之后执行
-- ============================================================================

USE db_breeding;
ALTER TABLE pig_individual
    ADD COLUMN data_source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT（区别于业务字段 source=自繁/外购）' AFTER status,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER data_source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

ALTER TABLE vaccine_record
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER operator,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

USE db_slaughter;
ALTER TABLE slaughter_inspection
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER content_hash,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

ALTER TABLE carcass_stamp
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER content_hash,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

USE db_distribution;
ALTER TABLE split_batch
    ADD COLUMN source VARCHAR(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT' AFTER content_hash,
    ADD COLUMN source_ref VARCHAR(64) COMMENT '来源设备号/单据号' AFTER source,
    ADD COLUMN raw_payload JSON COMMENT '设备原始报文' AFTER source_ref;

-- ---------------------------------------------------------------------------
-- 二期设备台账（密钥仅用于本地开发/答辩演示，生产须逐一更换并走密钥管理）
-- ---------------------------------------------------------------------------
USE db_ingest;
INSERT IGNORE INTO ingest_device (device_no, device_name, channel, location, secret_key, status, remark) VALUES
('TAG-W001', '耳标读写器 示范一号舍', 'TAG', '示范养殖场一号舍入口', 'dev-key-tag-001', 1, '佩戴耳标即建档；耳标必须人工佩戴，设备只记录身份与归属'),
('INJ-001', '智能免疫注射器', 'VACCINE', '示范养殖场兽医室', 'dev-key-inj-001', 1, '自动记录剂量/时间；疫苗批号靠扫码，缺批号拒绝入库'),
('STATION-001', '屠宰检验工位终端', 'INSPECTION', '屠宰场检验工位', 'dev-key-station-001', 1, '兽医判定+终端录入；判定权在人，终端只当"笔"'),
('STAMPER-001', '胴体自动盖章机', 'STAMP', '屠宰场盖章工位', 'dev-key-stamper-001', 1, '检验合格才准打章，授权兽医必填'),
('SPLIT-001', '分割线扫码称重台', 'SPLIT', '分割车间一号线', 'dev-key-split-001', 1, '扫白条钩标签自动建分割批次，批次号系统生成');
