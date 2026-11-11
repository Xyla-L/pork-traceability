-- ============================================================================
-- 养殖系统接入：外部批次同步（2026-10-26）
--
-- 背景：批次档此前只有「人工登记」一条路径（data_source 恒为 MANUAL）。现实中
-- 溯源系统不自己造养殖端数据 —— 批次、品种、耳标区间、出生日期本来就躺在养殖场的
-- ERP / 生产管理系统里。这一版把那条路开出来，但仍然只走接入层（原始报文留档 +
-- 判重 + 状态机），绝不直写档案表。
--
-- 三个新增事实：
--   一、外部身份：external_system + external_id 是「这条批次从哪个系统的哪条记录来」。
--       唯一键 (external_system, external_id) 才是外部系统的幂等键 —— 两家 ERP 都叫什么
--       PB-001 是常态，(farm_id, batch_no) 挡不住，只能靠这一对。
--   二、出生日期：养殖 ERP 给的是「这一批猪大概什么时候生的」，日龄由它现算。
--       现实中没人逐头填生日（同窝同批共用一个日期），所以落在批次级，成员行继承。
--   三、撤销来源：void_source 区分「人作废（MANUAL）」与「源系统撤销（EXTERNAL）」。
--       两者都上链、都保留成员行，但责任方不同，事后追责不能混成一条。
--
-- 幂等：ADD COLUMN 用 information_schema 守卫；唯一键用 SHOW INDEX 守卫；
--       设备台账用 INSERT ... ON DUPLICATE KEY UPDATE。
-- 执行：mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 < 本文件
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1) pig_batch 新增外部标识列（幂等，一次 ALTER 全加）
-- ---------------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'db_breeding'
                   AND TABLE_NAME = 'pig_batch'
                   AND COLUMN_NAME = 'external_id');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE db_breeding.pig_batch
     ADD COLUMN external_system VARCHAR(32) DEFAULT NULL
       COMMENT ''外部系统标识（如 FARM_ERP / 具体的养殖 SaaS 代号）；NULL=人工登记'' AFTER source,
     ADD COLUMN external_id VARCHAR(64) DEFAULT NULL
       COMMENT ''外部系统内的批次主键；与 external_system 组成幂等键'' AFTER external_system,
     ADD COLUMN birth_date DATE DEFAULT NULL
       COMMENT ''批次出生日期（养殖 ERP 提供，同批共用；日龄由它现算，不存日龄）'' AFTER entry_date,
     ADD COLUMN void_source VARCHAR(16) DEFAULT NULL
       COMMENT ''作废来源 MANUAL=人工作废 / EXTERNAL=源系统撤销（两者都上链，责任方不同）'' AFTER void_operator',
  'SELECT ''external_id 已存在，跳过 ADD COLUMN'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = 'db_breeding'
                   AND TABLE_NAME = 'pig_batch'
                   AND INDEX_NAME = 'uk_external');
SET @ddl2 := IF(@has_idx = 0,
  'ALTER TABLE db_breeding.pig_batch
     ADD UNIQUE KEY uk_external (external_system, external_id)',
  'SELECT ''uk_external 已存在，跳过 ADD INDEX'' AS note');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;


-- ---------------------------------------------------------------------------
-- 2) 设备台账：养殖系统接入（一条通道一行，密钥仅本地开发/答辩演示使用）
--    这不是「设备」，是外部系统的一个接入凭据 —— 复用设备台账是因为鉴权、
--    停用、最后活跃时间、密钥轮换这些能力本来就是同一套，没必要再造一张表。
-- ---------------------------------------------------------------------------
INSERT INTO db_ingest.ingest_device
  (device_no, device_name, channel, location, secret_key, status, remark)
VALUES
  ('FARM-ERP-001', '养殖系统接入（外部ERP · 演示/联调）', 'FARM_ERP', '模拟养殖场 ERP',
   'farm-erp-demo-key-2026', 1,
   '外部养殖管理系统推送批次档案；报文见 logs/farm-erp/。生产接入前须更换密钥并登记真实系统代号')
ON DUPLICATE KEY UPDATE
  device_name = VALUES(device_name),
  channel     = VALUES(channel),
  location    = VALUES(location),
  status      = 1,
  remark      = VALUES(remark);


-- ---------------------------------------------------------------------------
-- 3) 核验
-- ---------------------------------------------------------------------------
SELECT 'pig_batch 列' AS item,
       GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) AS v
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_breeding' AND TABLE_NAME = 'pig_batch'
UNION ALL
SELECT 'uk_external 索引', GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'db_breeding' AND TABLE_NAME = 'pig_batch' AND INDEX_NAME = 'uk_external'
UNION ALL
SELECT '外部来源批次', CONCAT('合计=', COUNT(*), ' 其中外部=', SUM(external_id IS NOT NULL),
                            ' 人工=', SUM(external_id IS NULL))
FROM db_breeding.pig_batch
UNION ALL
SELECT '养殖系统接入设备', CONCAT(device_no, ' / ', channel, ' / status=', status)
FROM db_ingest.ingest_device WHERE device_no = 'FARM-ERP-001';
