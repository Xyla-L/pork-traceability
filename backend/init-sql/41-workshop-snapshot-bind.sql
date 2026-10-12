-- ---------------------------------------------------------------------------
-- 41) 分割车间监控快照：与「这次分割」建立绑定（batch_no / job_id）
--
-- 背景：分割操作页的监控区原本只按相机取「最近 N 张」，与「哪一次分割」无关 ——
--       于是新做一次分割，页面看到的还是上一次的老照片。现在要求：
--       分割线扫码称重台上报数据之后，相机才开始拍，且拍到的照片要**归属于这次分割**。
--
-- 绑定口径：
--   · batch_no —— 胴体批次号。工位终端会把「当前正在分割的白条所属批次」一并告知相机
--     （相机自己不认识批次，这是工位终端给的信息，不是相机推断的）。
--   · job_id   —— 该批次**最近一次**分割作业（一次上报 = 一头白条 = 一个作业）。
--     服务端按 batch_no 反查作业，所以相机侧只需要报批次号，不必知道作业号。
--
-- 为什么两列都要：
--   页面是按**批次**选的（顶部批次下拉），查询/展示用 batch_no 最直接；
--   job_id 是精确到「哪一头白条」的权威外键，将来要在树上按白条组分照片时不必再猜。
--
-- 为什么允许为空：
--   历史数据（本次之前写进去的照片）没有绑定关系，置 NULL 而不是给个假批次 ——
--   插一条不存在的绑定比不绑定更坏：页面会把它当成某次分割的现场照片展示出来。
--   口径：新增可空列**一律不给业务含义型默认值**。
--
-- 幂等：information_schema 判存在后再 PREPARE/EXECUTE（MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS）。
-- ---------------------------------------------------------------------------

SET NAMES utf8mb4;

-- 1) batch_no
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'workshop_snapshot' AND COLUMN_NAME = 'batch_no');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.workshop_snapshot ADD COLUMN batch_no VARCHAR(64) DEFAULT NULL COMMENT ''所属胴体批次号（工位终端告知相机；空=未绑定，历史数据）'' AFTER workshop',
  'SELECT ''workshop_snapshot.batch_no 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) job_id
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'workshop_snapshot' AND COLUMN_NAME = 'job_id');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.workshop_snapshot ADD COLUMN job_id BIGINT DEFAULT NULL COMMENT ''所属分割作业 id（= 该批次最近一次作业；一次作业 = 一头白条的整份产出）'' AFTER batch_no',
  'SELECT ''workshop_snapshot.job_id 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) 页面查询永远是「某批次的照片，按时间/id 倒序」——一个联合索引就够
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'workshop_snapshot' AND INDEX_NAME = 'idx_snapshot_batch');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.workshop_snapshot ADD KEY idx_snapshot_batch (batch_no, id)',
  'SELECT ''workshop_snapshot.idx_snapshot_batch 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------------------------------------------------------------------------
-- 核验
-- ---------------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'workshop_snapshot'
  AND COLUMN_NAME IN ('batch_no', 'job_id');

SELECT '未绑定的历史快照（batch_no IS NULL）' AS item, CAST(COUNT(*) AS CHAR) AS v
FROM db_distribution.workshop_snapshot WHERE batch_no IS NULL
UNION ALL
SELECT '已绑定的快照', CAST(COUNT(*) AS CHAR)
FROM db_distribution.workshop_snapshot WHERE batch_no IS NOT NULL;
