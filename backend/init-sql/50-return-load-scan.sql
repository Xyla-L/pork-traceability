-- ============================================================================
-- 拒收回运装车扫码（2026-11-03）
--
-- 背景：正常配送有装车口扫码（LOAD）确认「哪些货真的上了这台车」，而回运单是
--   调度在页面上按载重一键生成的 —— 清单在生成那一刻就定死了，现场<b>没有任何证据</b>
--   证明这些拒收货真的装上了车。回运恰恰是多门店拼车、混装，装错车/漏装一站的后果是
--   拒收货被错送或不知去向，比正常单更需要现场核对。
--
-- 为什么不复用装车口那把扫码枪（LOADER-001）：
--   1) LOAD 通道的两条判定与回运天生冲突 —— 「批次已由运单发运过的禁止重复装车」
--      （拒收货一定发运过）、「扫批次倒查待发货计划」（回运不走计划，池子在 leg_item）；
--   2) 靠「扫到的字符串长什么样」去猜是正常货还是拒收货，格式一变就分错流，
--      而分错的代价是废品进正常运单 —— 这条红线不该靠格式推断守；
--   3) 两者语义完全不同：LOAD 产生运单，回运扫码只是核对已有单据（不建单）。
--   所以按「一台设备一条通道」的老纪律，新增一台专用扫码枪 + 一条通道 RETURN_LOAD。
--
-- 本通道不新增业务表：扫描结论落两处快照
--   · leg_item.scan_*：逐件证据（这一行货被扫到了、什么时候、哪台设备）
--   · cold_chain_transport.load_scan_*：整单摘要（本次扫码时刻/设备/未匹配说明）
--   原始报文仍在 db_ingest.ingest_staging（接入层留档，不可变），不重复存。
--
-- 幂等：ADD COLUMN 用 information_schema 守卫；种子 INSERT IGNORE。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1) leg_item 逐件扫码证据
-- ---------------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'db_distribution'
                   AND TABLE_NAME = 'leg_item'
                   AND COLUMN_NAME = 'scan_status');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE db_distribution.leg_item
     ADD COLUMN scan_status TINYINT NOT NULL DEFAULT 0
       COMMENT ''装车扫码状态 0=未扫码确认 1=已扫码核对（现场扫到了这件货）'' AFTER weight_kg,
     ADD COLUMN scan_time DATETIME DEFAULT NULL
       COMMENT ''装车扫码时刻（设备现场时刻）'' AFTER scan_status,
     ADD COLUMN scan_device_no VARCHAR(64) DEFAULT NULL
       COMMENT ''扫码设备编号（回运装车扫码枪）'' AFTER scan_time',
  'SELECT ''leg_item.scan_* 已存在，跳过 ADD COLUMN'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- 2) cold_chain_transport 整单扫码摘要
-- ---------------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'db_distribution'
                   AND TABLE_NAME = 'cold_chain_transport'
                   AND COLUMN_NAME = 'load_scan_time');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE db_distribution.cold_chain_transport
     ADD COLUMN load_scan_time DATETIME DEFAULT NULL
       COMMENT ''回运装车扫码时刻（专用扫码枪上报；为空=未扫码确认）'' AFTER intake_time,
     ADD COLUMN load_scan_device VARCHAR(64) DEFAULT NULL
       COMMENT ''回运装车扫码设备编号'' AFTER load_scan_time,
     ADD COLUMN load_scan_note VARCHAR(512) DEFAULT NULL
       COMMENT ''扫码核对摘要：命中几件、扫到但不属于本车的货（转人工用）'' AFTER load_scan_device',
  'SELECT ''cold_chain_transport.load_scan_* 已存在，跳过 ADD COLUMN'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- 3) 设备台账：拒收回运装车扫码枪
--    点位与装车口同一个装车位（冷库装车位），但物理上是另一把枪、另一把密钥：
--    它只扫拒收货，扫出来的东西永远进「回运核对」，不可能混成正常运单。
--    org_id=4（配送机构/冷链中心）：这台设备装在哪家机构，记录就归哪家。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_ingest.ingest_device
  (device_no, device_name, channel, location, secret_key, status, remark, org_id, create_time)
VALUES
  ('RETURN-SCAN-001', '拒收回运装车扫码枪', 'RETURN_LOAD', '示范定点屠宰场 冷库装车位（回运专用）',
   'dev-key-return-scan-001', 1,
   '只扫拒收货：扫回运单号 + 车上货物的原运单号/批次号，系统核对清单并留痕。不建单、不推进状态（发车仍由车载终端刷卡）',
   4, NOW());


-- ---------------------------------------------------------------------------
-- 4) 核验
-- ---------------------------------------------------------------------------
SELECT device_no, channel, location, org_id FROM db_ingest.ingest_device WHERE channel = 'RETURN_LOAD';
SELECT COUNT(*) AS leg_item_scan_cols FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'leg_item' AND COLUMN_NAME LIKE 'scan%';
