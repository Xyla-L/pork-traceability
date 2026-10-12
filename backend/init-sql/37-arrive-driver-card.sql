-- ============================================================================
-- 到达改为「司机在车载终端刷卡确认」：站点台账记下「谁确认的到达」
--
-- 背景（2026-10-11）：原来「到达」由门店收货区的车牌识别设备自动确认，只记了
-- 「哪台设备确认的」（arrive_source_ref），**答不出是谁把货送到这一站的**。
-- 而发车早已是司机上车刷卡（DEPART 通道）—— 到达这一端却没有人。
--
-- 定下的口径：
--   一、**到达由司机本人刷卡确认**：卡在司机手上、由**车载终端**读出（与发车同一台设备），
--       所以「谁把货送到了这一站」第一次有了责任人 —— 这是「设备 ≠ 人」的落点。
--   二、**没刷卡一律转人工**：车载终端一定读得到卡，「没读到」就是司机没刷，
--       不拿车牌或人名顶替；门店在页面上的「确认到达」是兜底出口（记 MANUAL、无确认人）。
--   三、**确认人落在站点上，不落在运单上**：一车多站时每一站都可以由不同的人确认，
--       运单主表那个 driver_name 是「谁在开这趟车」（发车那一下定的），两件事不能混。
--   四、**存量不回填**：已有的到达记录是设备自动确认的，当时并没有确认人 ——
--       编一个出来等于伪造本人确认，所以两列一律留空。
--
-- 幂等：ADD COLUMN 用 information_schema 守卫；设备台账用 UPDATE（重复执行结果一致）。
-- 执行：docker cp 进 mysql 容器后
--       `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 < 本文件`
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1) transport_stop 新增「谁确认到达」两列（幂等）
-- ---------------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'db_distribution'
                   AND TABLE_NAME = 'transport_stop'
                   AND COLUMN_NAME = 'arrive_driver_name');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE db_distribution.transport_stop
     ADD COLUMN arrive_driver_name VARCHAR(64) DEFAULT NULL
       COMMENT ''到达确认人姓名（司机刷卡换出的人，本人确认）；人工确认到达为空''
       AFTER arrive_source_ref,
     ADD COLUMN arrive_driver_source VARCHAR(24) DEFAULT NULL
       COMMENT ''到达确认来源 CARD=刷卡确认（本人）/ MANUAL=人工录入；人工确认到达为空''
       AFTER arrive_driver_name',
  'SELECT ''arrive_driver_name 已存在，跳过 ADD COLUMN'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- 2) arrive_source 列注释跟着改口径（原来写的是「门店设备自动确认」）
-- ---------------------------------------------------------------------------
ALTER TABLE db_distribution.transport_stop
  MODIFY COLUMN arrive_source VARCHAR(24) DEFAULT NULL
    COMMENT '到达怎么确认的 DEVICE=车载终端刷卡设备确认 / MANUAL=人工确认（页面补确认）';


-- ---------------------------------------------------------------------------
-- 3) 到达确认设备台账改为「车载终端」（司机到站刷卡的那一台，与发车同一设备）
--    设备码不变（ARRIVE-001），改的是它是什么、装在哪、怎么确认。
-- ---------------------------------------------------------------------------
UPDATE db_ingest.ingest_device
SET device_name = '车载终端（到站刷卡确认）',
    location    = '驾驶室（司机到站刷卡）',
    remark      = '第 17 条通道：司机到站后在车载终端刷卡确认「某车牌的车到了本店」。报三个现场事实（车牌 + 门店 + 司机卡号），运单与站点由配送服务倒查 —— 设备不产生任何业务判定。五条红线：该车牌名下没有未结束的运单 / 有多张未结束的运单 / 报的门店不是当前第一个未到达的站（跳站 = 漏扫或走错店）/ 没有司机卡号 / 卡换不出人或刷卡人与运单安排的司机不一致，一律转人工。'
WHERE device_no = 'ARRIVE-001';


-- ---------------------------------------------------------------------------
-- 4) 核验
-- ---------------------------------------------------------------------------
SELECT 'transport_stop 列' AS item,
       GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) AS v
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'transport_stop'
UNION ALL
SELECT '列注释 arrive_source',
       COLUMN_COMMENT FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'transport_stop' AND COLUMN_NAME = 'arrive_source'
UNION ALL
SELECT '列注释 arrive_driver_name',
       COLUMN_COMMENT FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'transport_stop' AND COLUMN_NAME = 'arrive_driver_name'
UNION ALL
SELECT '到达设备台账',
       CONCAT(device_name, ' @ ', location) FROM db_ingest.ingest_device WHERE device_no = 'ARRIVE-001'
UNION ALL
SELECT '站点行数', CAST(COUNT(*) AS CHAR) FROM db_distribution.transport_stop
UNION ALL
SELECT '  未到达(0)', CAST(SUM(status = 0) AS CHAR) FROM db_distribution.transport_stop
UNION ALL
SELECT '  已到达(1)', CAST(SUM(status = 1) AS CHAR) FROM db_distribution.transport_stop
UNION ALL
SELECT '  已签收(2)', CAST(SUM(status = 2) AS CHAR) FROM db_distribution.transport_stop
UNION ALL
SELECT '存量已到达但带了确认人的行（应为 0：存量不回填，不伪造本人确认）',
       CAST(COUNT(*) AS CHAR) FROM db_distribution.transport_stop WHERE status >= 1 AND arrive_driver_name IS NOT NULL;
