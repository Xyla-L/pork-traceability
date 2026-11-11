-- ============================================================================
-- 拒收回运链：门店拒收 → 冷链中心回运 → 无害化处理中心（2026-11-02）
--
-- 背景：处置三选一（退回供应商 / 就地销毁 / 折价接收）收敛为单一路径 ——
--   拒收即入待回运池，由冷链中心排车运回（回运段），确认入库后再生成的处置段
--   计划把货送往无害化处理中心，处置段终点只有「确认签收」，没有拒收。
--
-- 两段都复用 cold_chain_transport / transport_stop / 温度采样 / 车载终端通道：
--   · transport_kind 区分三段：NORMAL 正常配送 / RETURN 回运段 / DISPOSAL 处置段
--   · 回运/处置段没有单一分割批次 → split_batch_id 放开为 NULL
--   · receiver_org_id 记收货方机构（回运段=冷链中心，处置段=无害化处理中心）
--   · intake_by / intake_time：冷链中心「确认入库」（进入隔离，可被处置段挑走）
--   · leg_item：回运/处置运单 ↔ 原拒收站点 的货物清单
--     （一张拒收站点全程搭两段车，按 (source_stop_id, leg_kind) 唯一防重复排车）
--
-- 载重约束不新增字段：车辆备案（29-driver-vehicle-master.sql）已有 load_capacity_kg。
--
-- 幂等：ADD COLUMN 用 information_schema 守卫；CREATE TABLE IF NOT EXISTS；
--       种子 INSERT IGNORE。执行：容器内 mysql < 本文件
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1) cold_chain_transport 新增段落/收货方/入库列
-- ---------------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'db_distribution'
                   AND TABLE_NAME = 'cold_chain_transport'
                   AND COLUMN_NAME = 'transport_kind');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE db_distribution.cold_chain_transport
     ADD COLUMN transport_kind VARCHAR(16) NOT NULL DEFAULT ''NORMAL''
       COMMENT ''运单段落 NORMAL=正常配送 / RETURN=回运段(门店→冷链中心) / DISPOSAL=处置段(冷链中心→无害化处理中心)'' AFTER source_ref,
     ADD COLUMN receiver_org_id BIGINT DEFAULT NULL
       COMMENT ''收货方机构 id：回运段=冷链中心(distribution) / 处置段=无害化处理中心(disposal)；正常配送为空'' AFTER transport_kind,
     ADD COLUMN intake_by VARCHAR(64) DEFAULT NULL
       COMMENT ''回运入库确认人（冷链中心登录用户实名；确认后货物进入隔离、可被处置段挑走）'' AFTER receiver_org_id,
     ADD COLUMN intake_time DATETIME DEFAULT NULL
       COMMENT ''回运入库确认时刻'' AFTER intake_by',
  'SELECT ''transport_kind 已存在，跳过 ADD COLUMN'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- 2) split_batch_id 放开为 NULL（回运/处置段装的是「多家门店的拒收货」，没有单一分割批次）
-- ---------------------------------------------------------------------------
ALTER TABLE db_distribution.cold_chain_transport
  MODIFY COLUMN split_batch_id BIGINT DEFAULT NULL
    COMMENT '分割批次 id（正常配送必填；回运/处置段装多家门店的拒收货，为空，货物清单见 leg_item）';


-- ---------------------------------------------------------------------------
-- 3) 货物清单 leg_item：回运/处置运单 ↔ 原拒收站点
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.leg_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  leg_transport_id BIGINT NOT NULL COMMENT '回运/处置运单 id（cold_chain_transport）',
  leg_kind VARCHAR(16) NOT NULL COMMENT '段落 RETURN=回运段 / DISPOSAL=处置段',
  source_stop_id BIGINT NOT NULL COMMENT '原拒收站点 id（transport_stop.status=3 的那行）',
  transport_no VARCHAR(32) NOT NULL COMMENT '原运单号（快照，追溯用）',
  store_id BIGINT DEFAULT NULL COMMENT '拒收门店 id（快照）',
  store_name VARCHAR(128) NOT NULL COMMENT '拒收门店名（快照）',
  batch_no VARCHAR(64) DEFAULT NULL COMMENT '分割批次号（快照，可能为空）',
  weight_kg DECIMAL(12,2) DEFAULT NULL COMMENT '拒收站点货重 kg（快照；未知按 0 计入载重并提示）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_kind (source_stop_id, leg_kind),
  KEY idx_leg (leg_transport_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回运/处置段货物清单（原拒收站点快照）';


-- ---------------------------------------------------------------------------
-- 4) 种子数据：无害化处理中心机构（id=2001，与既有种子不冲突）
--    账号 disposal_user 由 AdminBootstrap 幂等创建，不在这里写密码哈希。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_common.sys_org
  (id, parent_id, type, name, manager, phone, address, remark, create_time, update_time)
VALUES
  (2001, 0, 'disposal', '无害化处理中心（示范）', '钱主任', '010-99887766',
   '北京市大兴区无害化处理园区1号', '病害动物产品集中无害化处理', NOW(), NOW());


-- ---------------------------------------------------------------------------
-- 5) 种子车辆载重复核（29 号脚本已建 vehicle + load_capacity_kg；仅核验）
-- ---------------------------------------------------------------------------
SELECT 'vehicle 载重覆盖' AS item,
       CONCAT(COUNT(*), ' 辆备案车 / ', SUM(load_capacity_kg IS NOT NULL), ' 辆有核定载重') AS v
FROM db_distribution.vehicle WHERE status = 1;
