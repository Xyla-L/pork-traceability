-- ============================================================================
-- 召回收运（2026-11-04）
-- 召回与拒收的差别在「货在哪」：拒收的货已在冷链中心，回运不用排车；
-- 召回的货还在各门店货架上，冷链中心必须派车逐店上门收 —— 所以要一条真正的多站运单：
--   冷链中心 →（门店1 · 扫码收货）→（门店2 · 扫码收货）→ … → 无害化处理中心（签收终结）
--
-- 两张表分工：
--   · recall_collect      = 召回的货「一件一行」，是处理计划池的来源（status=0 即在池）；
--   · recall_collect_scan = 司机到店扫的每一枪（事实表，对不上也照留原文）。
--
-- 状态口径（recall_collect.status）：
--   0=待收运（在拒收回运的「处理计划」池里等排车）
--   1=已排车（已并入某张收运运单，司机还没到店）
--   2=已收上车（司机到店扫过码，货已交接）
--   3=已送无害化（无害化处理中心确认签收，链条终结）
-- ============================================================================

CREATE TABLE IF NOT EXISTS db_distribution.recall_collect (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  recall_id BIGINT NOT NULL COMMENT '召回单 id（db_sales.recall_order）',
  recall_no VARCHAR(32) NOT NULL COMMENT '召回编号 RC…',
  reason VARCHAR(255) DEFAULT NULL COMMENT '召回原因（快照，页面直接显示）',
  store_id BIGINT NOT NULL COMMENT '门店机构 id（货所在门店）',
  store_name VARCHAR(128) NOT NULL COMMENT '门店名（快照）',
  split_batch_id BIGINT NOT NULL COMMENT '分割批次 id',
  batch_no VARCHAR(64) DEFAULT NULL COMMENT '分割批次号（快照，司机到店扫的就是它）',
  weight_kg DECIMAL(12,2) DEFAULT NULL COMMENT '涉及货重 kg（按销售记录汇总，未知为空）',
  sale_count INT NOT NULL DEFAULT 0 COMMENT '涉及销售记录条数',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待收运 1=已排车 2=已收上车 3=已送无害化',
  transport_id BIGINT DEFAULT NULL COMMENT '并入的收运运单 id（cold_chain_transport）',
  stop_id BIGINT DEFAULT NULL COMMENT '该门店在本趟运单上的站点 id（transport_stop）',
  collect_time DATETIME DEFAULT NULL COMMENT '司机到店扫码收货时刻',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_recall_item (recall_no, store_id, split_batch_id),
  KEY idx_status (status),
  KEY idx_transport (transport_id),
  KEY idx_stop (stop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='召回待收运池（门店里的召回货，等冷链中心上门收）';

CREATE TABLE IF NOT EXISTS db_distribution.recall_collect_scan (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  scan_code VARCHAR(64) NOT NULL COMMENT '扫到的货码：批次号（或门店标签）',
  transport_id BIGINT DEFAULT NULL COMMENT '命中的收运运单',
  stop_id BIGINT DEFAULT NULL COMMENT '命中的门店站点',
  collect_id BIGINT DEFAULT NULL COMMENT '命中的召回货行（recall_collect.id）',
  plate_no VARCHAR(16) NOT NULL COMMENT '扫这一枪时识别到的车牌',
  device_no VARCHAR(64) DEFAULT NULL COMMENT '扫码设备编号',
  scan_time DATETIME NOT NULL COMMENT '设备现场扫码时刻',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=已收货 1=异常待人工',
  note VARCHAR(512) DEFAULT NULL COMMENT '异常说明：货码对不上本店召回货',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_transport (transport_id),
  KEY idx_collect (collect_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='召回到店扫码收货事实（一枪一行，对不上也留原文）';

-- 司机手持扫码枪：到店扫召回货的批次标签，登记「这车把这家店的货收走了」
INSERT IGNORE INTO db_ingest.ingest_device
  (device_no, device_name, channel, location, secret_key, status, remark, org_id, create_time)
VALUES
  ('RECALL-SCAN-001', '召回到店收货扫码枪', 'RECALL_COLLECT', '冷链配送车 随车手持终端',
   'dev-key-recall-scan-001', 1,
   '司机到门店扫召回货批次标签，登记该站已收货；不推进运单状态（发车/到达仍由车载终端刷卡）',
   4, NOW());

-- ---------------------------------------------------------------------------
-- 核验
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS recall_collect_ready FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'recall_collect';
SELECT device_no, channel FROM db_ingest.ingest_device WHERE channel = 'RECALL_COLLECT';
