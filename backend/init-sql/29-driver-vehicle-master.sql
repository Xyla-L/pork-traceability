-- ============================================================================
-- 司机 / 车辆主数据 & 装车口「识别方式」落库（装车口自动化的第 3 层）
--
-- 背景（2026-10-09 续）：28-load-channel.sql 把装车口做成了「一个点位、一个通道」，
-- 但那时有一个隐含假设：司机一定由「人脸 / 实名终端」录入。事后复盘发现两件事：
--
--   ① 「车牌识别」和「司机实名」装在**同一台设备**上不合理。
--      相机面朝车道、司机终端要人伸手配合，安装位置、维护方、故障模式都不同；
--      更要命的是把「车」和「人」用设备绑死了（现实中交接班、临时顶班、一车两人是常态）。
--      所以设备台账改成**点位口径**：1 号装车位上的三件套。
--      上报入口仍是一个通道（一次业务事件），但报文里把「车牌怎么来的」「人怎么来的」
--      分开标清楚 —— 通道粒度 = 业务事件粒度，不必等于物理设备粒度。
--
--   ② 「人脸实名」不是唯一解，也不是最合理的解：
--      司机要在逆光 / 夜间 / 戴帽 / 口罩下停车配合，终端还最贵；冷链司机戴手套，
--      指纹更不现实。更简单、且**机器能自动录**的是 IC 刷卡（贴一下自动读卡号）——
--      这正是卡口 / 物流园区的通行做法。人脸降级为「可选强化档」。
--
-- 本脚本做四件事：
--   1) 补主数据：车辆备案 / 司机备案 / 司机-卡绑定 / 司机-车辆绑定。
--      这才是自动化的**地基** —— 没有备案表，刷卡与车牌反查都无数据可查。
--      （车辆备案顺带解决上一轮列出的「车型缺失」：车牌一认就能带出车型。）
--   2) cold_chain_transport 补 driver_source / driver_confirmed。
--      「司机是怎么来的」与「是否本人确认」不是一回事，必须落成两个字段：
--        CARD / FACE → 本人确认（司机本人在终端配合）
--        PLATE_INFERRED → 系统推定（车牌反查备案的常用司机）
--        MANUAL → 人工录入（调度员或计划里填的）
--   3) LOADER-001 改点位口径命名（原先叫「车牌识别与司机实名终端」，把两件不同的事
--      写成了一个设备名，等于在名字里就把车和人绑死了）。
--   4) 种子数据：把原先只写在设备名里的车牌（京A·12345 / 京A·99999）抽出来入库。
--
-- 司机来源的唯一口径在 com.pork.distribution.support.DriverSource，本文件的值域与它一致。
--
-- 刻意不做：
--   · 不让「车牌反查」升级成实名凭据 —— 推定只是替人工省一道查询，仍转人工，
--     项目红线「设备 ≠ 人」不变：不能拿车牌号顶替司机。
--   · 不给 driver_confirmed 做「设备自报」的布尔入参 —— 确认强度由识别方式唯一决定，
--     两个来源会打架（设备报 FACE 却说未确认）。单一事实来源。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；ALTER 前探 information_schema；种子 INSERT IGNORE；
--       设备改名可重复执行。
-- 执行：docker cp 进 mysql 容器后 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 本文件`
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 车辆备案：车牌 → 车型 / 载重 / 归属
--    原先车牌只是人工敲进设备名里的一个字符串（TEMP-001 名叫「冷藏车温度探头 京A·12345」），
--    库里没有任何车辆主数据，所以「车型」这个运单字段一直是靠人手填。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.vehicle (
  id BIGINT NOT NULL AUTO_INCREMENT,
  vehicle_no VARCHAR(16) NOT NULL COMMENT '车牌号（唯一）',
  vehicle_type VARCHAR(32) DEFAULT NULL COMMENT '车型，如 冷藏车',
  load_capacity_kg DECIMAL(10,2) DEFAULT NULL COMMENT '核定载重(kg)',
  owner_name VARCHAR(64) DEFAULT NULL COMMENT '车辆归属（自有车队 / 承运商名称）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_vehicle_no (vehicle_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车辆备案（车牌→车型/载重）';

-- ---------------------------------------------------------------------------
-- 2) 司机备案
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.driver (
  id BIGINT NOT NULL AUTO_INCREMENT,
  driver_no VARCHAR(32) NOT NULL COMMENT '司机工号（唯一）',
  driver_name VARCHAR(32) NOT NULL COMMENT '司机姓名',
  phone VARCHAR(20) DEFAULT NULL,
  id_card_no VARCHAR(32) DEFAULT NULL COMMENT '身份证号（选填；脱敏口径由业务另行约定）',
  license_no VARCHAR(32) DEFAULT NULL COMMENT '驾驶证号（选填）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1在职 0离职',
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_driver_no (driver_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='司机备案';

-- ---------------------------------------------------------------------------
-- 3) 司机-卡绑定：让「刷卡」能机器自动录
--    这是本轮替代人脸的主方案：司机贴一下卡，读卡器自动读卡号，系统换成具体的人。
--    卡在人手上 = 最低限度的责任；丢卡作废（status=0）不影响历史运单的追溯。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.driver_card (
  id BIGINT NOT NULL AUTO_INCREMENT,
  card_no VARCHAR(64) NOT NULL COMMENT 'IC 卡号（唯一）',
  driver_id BIGINT NOT NULL COMMENT '绑定的司机',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 0挂失/作废',
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_no (card_no),
  KEY idx_driver (driver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='司机-卡绑定（刷卡自动录）';

-- ---------------------------------------------------------------------------
-- 4) 司机-车辆绑定：让「车牌反查」能给出候选人
--    注意这不是一对一：一个司机可以开多台车，一台车也可以有多个司机，
--    只是标出「常用主驾」—— 反查命中它时得到的是**推定**，不是实名。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.driver_vehicle (
  id BIGINT NOT NULL AUTO_INCREMENT,
  driver_id BIGINT NOT NULL,
  vehicle_no VARCHAR(16) NOT NULL,
  is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '1=常用主驾（车牌反查时命中）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 0解除',
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_driver_vehicle (driver_id, vehicle_no),
  KEY idx_vehicle_primary (vehicle_no, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='司机-车辆绑定（车牌反查推定用）';

-- ---------------------------------------------------------------------------
-- 5) cold_chain_transport 补司机来源与确认强度
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'cold_chain_transport' AND COLUMN_NAME = 'driver_source');
SET @s1 := IF(@c1 = 0,
    'ALTER TABLE db_distribution.cold_chain_transport ADD COLUMN driver_source VARCHAR(16) DEFAULT ''MANUAL'' COMMENT ''司机是怎么来的 CARD=刷卡/FACE=人脸/PLATE_INFERRED=车牌反查推定/MANUAL=人工录入'' AFTER driver_phone',
    'SELECT ''cold_chain_transport.driver_source 已存在，跳过'' AS skip_note');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'cold_chain_transport' AND COLUMN_NAME = 'driver_confirmed');
SET @s2 := IF(@c2 = 0,
    'ALTER TABLE db_distribution.cold_chain_transport ADD COLUMN driver_confirmed TINYINT NOT NULL DEFAULT 0 COMMENT ''司机是否本人确认 1=刷卡/人脸本人确认 0=系统推定或人工录入'' AFTER driver_source',
    'SELECT ''cold_chain_transport.driver_confirmed 已存在，跳过'' AS skip_note');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

-- 存量 34 条都是人工 / 脚本建的：司机由调度填写，既非刷卡也非反查 → MANUAL / 未确认。
-- （ALTER 的 DEFAULT 已经填上了，这里再兜一次，防止有行被显式写成 NULL。）
UPDATE db_distribution.cold_chain_transport SET driver_source = 'MANUAL' WHERE driver_source IS NULL OR driver_source = '';
UPDATE db_distribution.cold_chain_transport SET driver_confirmed = 0 WHERE driver_confirmed IS NULL;

-- ---------------------------------------------------------------------------
-- 6) 装车口设备改点位口径命名
--    一个点位 = 一个 device_no（device_no 本身就是点位标识，不再另加 point_no 列）。
--    将来多开装车位就登记 LOADER-002 / LOADER-003，而不是全厂共用一个设备号。
-- ---------------------------------------------------------------------------
UPDATE db_ingest.ingest_device
SET device_name = '1号装车位（车牌识别相机 + 司机刷卡终端 + 扫码枪）',
    location = '示范定点屠宰场 冷库 1号装车位',
    remark = '装车口的点位口径：三件套共同构成一次「装车出场」事件，所以是**一个通道**，但不是**一台设备** —— 相机识车牌、读卡器识人、扫码枪识货，三者安装位置与故障模式各不相同，报文里用 vehicleSource / driverSource 分开标注来源。目的地不由设备决定，匹配不上转人工。'
WHERE device_no = 'LOADER-001';

-- ---------------------------------------------------------------------------
-- 7) 种子数据
--    车牌取自已存在的设备名（TEMP-001「冷藏车温度探头 京A·12345」），
--    司机与卡号是演示用；卡号对应关系与前端演示表单的示例值一致。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_distribution.vehicle (vehicle_no, vehicle_type, load_capacity_kg, owner_name, status, remark) VALUES
('京A·12345', '冷藏车', 5000, '自有车队', 1, '冷链在途温度探头所在车辆（车牌原先只写在设备名里，本轮抽出来备案）'),
('京A·99999', '冷藏车', 8000, '自有车队', 1, '8 吨冷藏车'),
('京B·10086', '冷藏车', 3000, '第三方承运', 1, '3 吨小型冷藏车');

INSERT IGNORE INTO db_distribution.driver (driver_no, driver_name, phone, status, remark) VALUES
('DRV-001', '张师傅', '13700000002', 1, '示范司机：京A·12345 常用主驾'),
('DRV-002', '李四', '13900000001', 1, '示范司机：京A·99999 常用主驾'),
('DRV-003', '王五', '13600000003', 1, '示范司机：京B·10086 常用主驾');

INSERT IGNORE INTO db_distribution.driver_card (card_no, driver_id, status, remark)
SELECT '8801', d.id, 1, '张师傅的 IC 卡（演示用）' FROM db_distribution.driver d WHERE d.driver_no = 'DRV-001';
INSERT IGNORE INTO db_distribution.driver_card (card_no, driver_id, status, remark)
SELECT '8802', d.id, 1, '李四的 IC 卡（演示用）' FROM db_distribution.driver d WHERE d.driver_no = 'DRV-002';
INSERT IGNORE INTO db_distribution.driver_card (card_no, driver_id, status, remark)
SELECT '8803', d.id, 1, '王五的 IC 卡（演示用）' FROM db_distribution.driver d WHERE d.driver_no = 'DRV-003';

INSERT IGNORE INTO db_distribution.driver_vehicle (driver_id, vehicle_no, is_primary, status, remark)
SELECT d.id, '京A·12345', 1, 1, '张师傅常开的车' FROM db_distribution.driver d WHERE d.driver_no = 'DRV-001';
INSERT IGNORE INTO db_distribution.driver_vehicle (driver_id, vehicle_no, is_primary, status, remark)
SELECT d.id, '京A·99999', 1, 1, '李四常开的车' FROM db_distribution.driver d WHERE d.driver_no = 'DRV-002';
INSERT IGNORE INTO db_distribution.driver_vehicle (driver_id, vehicle_no, is_primary, status, remark)
SELECT d.id, '京B·10086', 1, 1, '王五常开的车' FROM db_distribution.driver d WHERE d.driver_no = 'DRV-003';

-- ---------------------------------------------------------------------------
-- 核验
-- ---------------------------------------------------------------------------
SELECT table_name, table_comment FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME IN ('vehicle', 'driver', 'driver_card', 'driver_vehicle')
ORDER BY table_name;

SELECT table_name, column_name, column_type, column_default, column_comment
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'cold_chain_transport'
  AND COLUMN_NAME IN ('driver_source', 'driver_confirmed')
ORDER BY ordinal_position;

SELECT 'vehicle' AS t, COUNT(*) AS cnt FROM db_distribution.vehicle
UNION ALL SELECT 'driver', COUNT(*) FROM db_distribution.driver
UNION ALL SELECT 'driver_card', COUNT(*) FROM db_distribution.driver_card
UNION ALL SELECT 'driver_vehicle', COUNT(*) FROM db_distribution.driver_vehicle
UNION ALL SELECT 'transport_source_null', COUNT(*) FROM db_distribution.cold_chain_transport WHERE driver_source IS NULL;

SELECT dc.card_no, d.driver_name, d.phone
FROM db_distribution.driver_card dc JOIN db_distribution.driver d ON d.id = dc.driver_id ORDER BY dc.card_no;

SELECT dv.vehicle_no, d.driver_name, dv.is_primary
FROM db_distribution.driver_vehicle dv JOIN db_distribution.driver d ON d.id = dv.driver_id
ORDER BY dv.vehicle_no;

SELECT device_no, device_name, channel, location FROM db_ingest.ingest_device WHERE device_no = 'LOADER-001';
