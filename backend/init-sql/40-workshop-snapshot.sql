-- ---------------------------------------------------------------------------
-- 40) 分割车间监控快照（workshop_snapshot）+ 监控相机设备台账
--
-- 背景：分割操作页面要在批次树下方展示「车间现场照片 + 拍摄时间」。
--       相机按固定间隔（默认 60 秒）拍一张，走接入层新通道 SPLIT_SNAPSHOT（中文名「分割车间监控」），
--       两段式上报：图片先 POST /file/upload 换回 fileId，再把 {fileId, capturedTime, workshop} 报进来。
--
-- 为什么单独建表，而不是挂到 split_batch.file_ids：
--   split_batch 一行 = 一个部位（猪后腿、五花…），而监控照片是「车间工位在某个时刻的样子」，
--   与「哪个部位」没有对应关系 —— 相机不认识部位。硬挂上去就会出现「这张照片属于哪个部位」
--   这种没有答案的问题；而且分割批次是设备整份上报时一次性建出来的，事后塞照片还会污染已有记录。
--   split_batch.file_ids 留给「分割作业的凭证照片」，与监控快照是两回事，本迁移**不动它**。
--
-- 为什么这条通道不进 TraceStage：
--   它是「现场影像」，不是溯源链上的一环 —— 不回答任何业务问题、不参与任何前置门槛，
--   加进「来源策略」表只会多一行无意义记录。
--
-- 保留策略（滚动窗口，不是历史档案）：
--   相机是分钟级周期上报，本地盘经不起无限增长 —— 每台相机只保留最近 N 张（默认 20，见
--   DistributionDeviceIngestService 的 ingest.split-snapshot.retention），
--   新的一张进来就把最老的连同文件一起淘汰。页面展示最近 10 张。
--
-- 【2026-10-12 追加】照片与「哪一次分割」的绑定（batch_no / job_id）由迁移 41 负责：
--   本迁移在建表时已带上这两列，重复执行 41 会自动跳过。绑定只影响查询与展示，
--   滚动淘汰口径不变（仍按 device_no 保留最近 N 张）。
--
-- 设备台账：新增 1 条监控相机（设备数 23 → 24）。凡写死「设备 23」的回归断言都要同步改。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + 设备按 device_no 先删后插（uk_device_no 唯一键）。
-- ---------------------------------------------------------------------------

-- 1) 车间快照表（落 db_distribution：分割属配送域）
CREATE TABLE IF NOT EXISTS db_distribution.workshop_snapshot (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_no`     varchar(64)  NOT NULL COMMENT '拍摄设备（监控相机）编号；机器记录，用设备号是对的',
  `workshop`      varchar(64)  DEFAULT NULL COMMENT '车间（相机安装位置，如「分割一号线」）',
  -- 绑定到「哪一次分割」：由工位终端告知相机（相机自己不认识批次）。见迁移 41。
  `batch_no`      varchar(64)  DEFAULT NULL COMMENT '所属胴体批次号（空=未绑定，历史数据）',
  `job_id`        bigint       DEFAULT NULL COMMENT '所属分割作业 id（= 该批次最近一次作业）',
  `file_id`       varchar(64)  NOT NULL COMMENT '照片在文件服务里的 id（file_metadata.file_id）；只存 id 不存 URL',
  `captured_time` datetime     NOT NULL COMMENT '拍摄时刻（设备侧时间，报文级）—— 不是入库时刻',
  `source`        varchar(16)  DEFAULT NULL COMMENT '数据来源 DEVICE=设备自动上传（无业务含义型默认值）',
  `source_ref`    varchar(64)  DEFAULT NULL COMMENT '来源设备号/单据号',
  `raw_payload`   json         DEFAULT NULL COMMENT '设备原始报文留档',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '入库时刻',
  PRIMARY KEY (`id`),
  -- 两种查询：①「某台相机最近若干张」②「某次分割（批次）的现场照片」——各配一个联合索引
  KEY `idx_snapshot_device_id` (`device_no`, `id`),
  KEY `idx_snapshot_batch` (`batch_no`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='分割车间监控快照：一条记录 = 一张照片（滚动窗口，非历史档案）';

-- 2) 监控相机设备台账（channel = SPLIT_SNAPSHOT）
--    sample_interval_seconds = 60：相机自己的拍照节奏，系统不轮询（与温度探头的采样间隔同一口径）。
DELETE FROM db_ingest.ingest_device WHERE device_no = 'CAM-SPLIT-001';
INSERT INTO db_ingest.ingest_device
  (device_no, device_name, channel, location, vehicle_no, sample_interval_seconds, secret_key, status, remark)
VALUES
  ('CAM-SPLIT-001', '分割车间监控相机', 'SPLIT_SNAPSHOT', '分割一号线工位上方',
   NULL, 60, 'dev-key-cam-split-001', 1,
   '分割车间监控相机：按 60 秒间隔拍一张车间现场照片，走 SPLIT_SNAPSHOT 通道两段式上报（先传文件换 fileId，再报 fileId+capturedTime）。照片不进溯源链、不参与业务判定；每台相机只保留最近 20 张，超出即滚动淘汰。');

-- ---------------------------------------------------------------------------
-- 核验
-- ---------------------------------------------------------------------------
SELECT device_no, device_name, channel, IFNULL(vehicle_no, '(不绑车)') AS vehicle_no, sample_interval_seconds
FROM db_ingest.ingest_device WHERE device_no = 'CAM-SPLIT-001';

SELECT '设备台账总行数（应为 24）' AS item, CAST(COUNT(*) AS CHAR) AS v
FROM db_ingest.ingest_device
UNION ALL
SELECT 'workshop_snapshot 表已建', CAST(COUNT(*) AS CHAR)
FROM information_schema.tables WHERE table_schema = 'db_distribution' AND table_name = 'workshop_snapshot'
UNION ALL
SELECT 'workshop_snapshot 现有行数', CAST(COUNT(*) AS CHAR)
FROM db_distribution.workshop_snapshot;
