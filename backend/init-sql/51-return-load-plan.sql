-- ============================================================================
-- 拒收回运：装车扫码前移到「处理计划」阶段（2026-11-03）
--
-- 改口径：原来是「先排车生成回运单 → 现场扫回运单号核对清单」，现场装车前手里
--   根本没有单号（回运单号是排车那一刻才生成的），于是扫码枪只能等调度排完车才有活干，
--   顺序和冷链装车口（LOAD）正好相反。现在对齐装车口：
--     装车位扫「车牌 + 货码」→ 倒查这批拒收货属于哪家门店的拒收 → 登记装车事实
--     → 调度在处理计划页按扫码结果生成处理运单 → 司机刷卡发车。
--
-- 因此扫码事实必须存在运单之前：leg_item 的 leg_transport_id 非空，装不进未生成的单，
-- 所以单独一张 return_load_scan 存「现场这一枪」。
--   · 它是**事实表**：扫到什么就记什么，货码对不上本系统任何拒收站点也照样留原文（status=2）
--   · 它不建单、不推状态：处理运单由生成计划产生，发车由车载终端刷卡推进
--   · 并入运单后 status 翻 1 并记 leg_transport_id，一眼能看出这枪用掉了
--
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================================

CREATE TABLE IF NOT EXISTS db_distribution.return_load_scan (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  scan_code VARCHAR(64) NOT NULL COMMENT '扫到的货码：拒收包装上的原运单号或分割批次号',
  stop_id BIGINT DEFAULT NULL COMMENT '反查命中的拒收站点；NULL=没对上任何拒收站点',
  transport_no VARCHAR(32) DEFAULT NULL COMMENT '命中的原运单号（快照）',
  batch_no VARCHAR(64) DEFAULT NULL COMMENT '命中的分割批次号（快照）',
  store_name VARCHAR(128) DEFAULT NULL COMMENT '拒收门店（快照）',
  weight_kg DECIMAL(12,2) DEFAULT NULL COMMENT '该站拒收货重（快照）',
  plate_no VARCHAR(16) NOT NULL COMMENT '扫这一枪时识别到的车牌：货装的是哪台车',
  device_no VARCHAR(64) DEFAULT NULL COMMENT '扫码设备编号（如 RETURN-SCAN-001）',
  scan_time DATETIME NOT NULL COMMENT '设备现场扫码时刻',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=已扫待排车 1=已并入处理运单 2=异常待人工',
  leg_transport_id BIGINT DEFAULT NULL COMMENT '并入的处理运单 id',
  note VARCHAR(512) DEFAULT NULL COMMENT '异常说明：货码对不上 / 一码多站 / 已并入别的单',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_stop (stop_id),
  KEY idx_plate (plate_no),
  KEY idx_leg (leg_transport_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拒收回运装车扫码事实（排车前登记，生成计划时并入运单）';

SELECT COUNT(*) AS table_ready FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'return_load_scan';
