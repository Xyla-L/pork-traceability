-- ============================================================================
-- 三件事一起落：一车多目的地（多站车次）+ 刷卡发车 + 温度定频与断档检测
--
-- 设计评审（2026-10-10）定下的三条口径：
--
-- 一、运单粒度 = 「一次送货」 → 「一个车次」
--   现实里一辆冷藏车装满后顺路送三四家门店。原模型的四层各写死一个「1」：
--     delivery_plan.store_id 单值 / cold_chain_transport.destination 单值 /
--     transport_item 只有一批货在车上（不知在哪站卸）/ store_receipt uk_transport 唯一。
--   其中**最硬的是签收**：签收是货权转移 + 责任落到具体人的节点，一车四店就是四次货权转移。
--
--   改法（刻意**不新建车次表**）：
--     · cold_chain_transport 本身就是「车次」（一台车出一趟），不套第二层；
--     · store_receipt 就是「每站签收单」—— 它的 store_id 列与 idx_store 索引本来就有，
--       报文里早就在传 storeId，唯一锁死它的只有 uk_transport；
--     · transport_item 按门店聚合就是站点清单 —— 补 store_id/seq 即可，不用建 transport_stop。
--
--   「一车多店是扫出来的，不是规划出来的」：装车口扫到的每一批货，其门店归属由它所属的
--   delivery_plan 决定（不是猜的）。扫码顺序即站点顺序（按站装车时）；混装则 seq 留空，
--   站点顺序由签收时间回填 —— 那也是事实。
--   **不做拼车算法、不做路线规划**：那是 TMS 的决策，本系统零数据源。
--
-- 二、发车设备化（第 17 条通道 DEPART）
--   装车口已能自动建单，但建完停在 status=1，发车仍要人去点按钮 —— 自动化断在最后一步。
--   发车是**物理事实**（道闸抬杆 / 地磅出场 / 点火）且**不产生业务判定**，适合设备化。
--   前提是「车载终端 ↔ 车牌」必须**结构化可查**：原来只有 ingest_device.location 里
--   一句「一号冷藏车车厢」这种自然语言，所以加 vehicle_no 列。
--
-- 三、温度定频与断档检测
--   「多久采一次」是**探头的属性** → 声明进设备台账（sample_interval_seconds）。
--   系统要做的是「发现没采到」：只判单点越界（is_abnormal）是不够的 ——
--   15:00 报一条、17:30 才报下一条，系统会显示「温度正常」，**这比温度超标更危险**
--   （超了至少看得见，空白是假装有数据）。所以记录相邻两条的间隔，超时标 is_gap=1。
--
-- 幂等：ALTER 前探 information_schema；种子 INSERT IGNORE；可重复执行。
-- 执行：见 logs/ 下的 db 脚本，或 docker cp 进 mysql 容器后
--       `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 < 本文件`
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1) transport_item 补「卸货门店」与「站点序号」
--    这是「一车多店」最核心的一处：装载明细从此能回答「这批肉在哪一站卸下」，
--    而不是只能绕道 delivery_plan_item.plan_id → delivery_plan.store_id 反查。
--    seq 为 NULL = 混装（未按站装车），站点顺序改由签收时间回填。
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='transport_item' AND COLUMN_NAME='store_id');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.transport_item ADD COLUMN store_id BIGINT DEFAULT NULL COMMENT ''卸货门店 id（多站车次：这批货在哪一站卸下）'' AFTER weight_kg',
  'SELECT ''transport_item.store_id 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='transport_item' AND COLUMN_NAME='store_name');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.transport_item ADD COLUMN store_name VARCHAR(128) DEFAULT NULL COMMENT ''卸货门店名（冗余，展示免联表）'' AFTER store_id',
  'SELECT ''transport_item.store_name 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='transport_item' AND COLUMN_NAME='seq');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.transport_item ADD COLUMN seq INT DEFAULT NULL COMMENT ''站点序号（按装车/扫码顺序，从 1 起）；NULL=混装未按站，顺序由签收时间回填'' AFTER store_name',
  'SELECT ''transport_item.seq 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='transport_item' AND INDEX_NAME='idx_item_store');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.transport_item ADD KEY idx_item_store (store_id)',
  'SELECT ''transport_item.idx_item_store 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 存量 156 条装载明细：所属运单都是单店直送，门店归属由运单目的地唯一确定，回填即可。
-- 用 (transport_id) 关联 cold_chain_transport 取 destination —— 注意 destination 是**名字**不是 id，
-- 存量运单没有 store_id，所以只回填 store_name，store_id 留 NULL（不猜一个 id 出来）。
UPDATE db_distribution.transport_item i
  JOIN db_distribution.cold_chain_transport t ON t.id = i.transport_id
   SET i.store_name = COALESCE(i.store_name, t.destination),
       i.seq = COALESCE(i.seq, 1)
 WHERE i.store_name IS NULL;


-- ---------------------------------------------------------------------------
-- 2) store_receipt 唯一键：一运单一条 → 一运单**每站**一条
--    放开的是一站一条，不是「随便签几次」：同一运单同一门店仍然只允许一条签收。
--    存量 34 条完全不受影响（原本一单只有一店，新键依旧唯一）。
-- ---------------------------------------------------------------------------
SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='store_receipt' AND INDEX_NAME='uk_transport');
SET @s := IF(@i > 0,
  'ALTER TABLE db_distribution.store_receipt DROP INDEX uk_transport',
  'SELECT ''store_receipt.uk_transport 已不存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='store_receipt' AND INDEX_NAME='uk_transport_store');
SET @s := IF(@i = 0,
  'ALTER TABLE db_distribution.store_receipt ADD UNIQUE KEY uk_transport_store (transport_id, store_id) COMMENT ''一个车次在同一门店只能有一条签收；不同门店各签各的''',
  'SELECT ''store_receipt.uk_transport_store 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;


-- ---------------------------------------------------------------------------
-- 3) cold_chain_transport 补「途经站点数」快照
--    列表展示免聚合。存量 34 条运单都是单店直送 ⇒ 默认 1。
--    destination 列**不改**、语义降级为「首站/主目的地」：多站时展示成「途经 N 站」。
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='cold_chain_transport' AND COLUMN_NAME='stop_count');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.cold_chain_transport ADD COLUMN stop_count INT NOT NULL DEFAULT 1 COMMENT ''途经站点数（1=单店直送；>1=一车多店，站点明细看 transport_item 按 store_id 聚合）'' AFTER status',
  'SELECT ''cold_chain_transport.stop_count 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 存量运单按装载明细里出现过的门店数回填（存量明细刚在上面补了 store_name，实际都是 1 家）
UPDATE db_distribution.cold_chain_transport t
   SET t.stop_count = GREATEST(1, (SELECT COUNT(DISTINCT i.store_name) FROM db_distribution.transport_item i
                                    WHERE i.transport_id = t.id AND i.store_name IS NOT NULL))
 WHERE t.id > 0;


-- ---------------------------------------------------------------------------
-- 4) ingest_device 补「结构化绑定」与「额定采样间隔」
--    vehicle_no：车载设备名下是哪台车 —— 发车设备化把「车牌」当唯一凭据，必须可查，不能靠字符串猜。
--    sample_interval_seconds：探头的额定上报间隔，断档检测的基准。
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_ingest' AND TABLE_NAME='ingest_device' AND COLUMN_NAME='vehicle_no');
SET @s := IF(@c = 0,
  'ALTER TABLE db_ingest.ingest_device ADD COLUMN vehicle_no VARCHAR(16) DEFAULT NULL COMMENT ''车载设备绑定的车牌（发车/温度类车载终端用；固定点位设备为 NULL）'' AFTER location',
  'SELECT ''ingest_device.vehicle_no 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_ingest' AND TABLE_NAME='ingest_device' AND COLUMN_NAME='sample_interval_seconds');
SET @s := IF(@c = 0,
  'ALTER TABLE db_ingest.ingest_device ADD COLUMN sample_interval_seconds INT DEFAULT NULL COMMENT ''额定上报间隔（秒），温度探头用；系统据此判断档（间隔 > 本值×容差 即标 is_gap）'' AFTER vehicle_no',
  'SELECT ''ingest_device.sample_interval_seconds 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 车载探头补上车牌绑定（原来只有 location 里一句自然语言）。
-- 注意顺序：**先绑车牌，再按车牌筛出车载探头设间隔** —— 反过来写会因为
-- 过滤条件里的 vehicle_no 还是 NULL 而漏掉全部记录（写脚本时踩过一次）。
UPDATE db_ingest.ingest_device SET vehicle_no = '京A·12345'
 WHERE device_no = 'TEMP-001' AND vehicle_no IS NULL;
UPDATE db_ingest.ingest_device SET vehicle_no = '京A·12346'
 WHERE device_no = 'TEMP-003' AND vehicle_no IS NULL;

-- 车载探头登记额定间隔（5 分钟一条）。固定点位的冷库探头不设，走服务默认值。
UPDATE db_ingest.ingest_device SET sample_interval_seconds = 300
 WHERE channel = 'TEMPERATURE' AND vehicle_no IS NOT NULL AND (sample_interval_seconds IS NULL OR sample_interval_seconds <= 0);

-- ---------------------------------------------------------------------------
-- 5) 第 17 条通道：发车确认（车载终端）
--    点位 = 车载终端本身（一台车一个终端号），刷卡即发车。
--    这是唯一一条「推进已有运单状态」而不是「写入新事实」的通道 ——
--    它不产生任何业务判定：哪台车该发、发给谁，早就在运单里定死了。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_ingest.ingest_device
  (device_no, device_name, channel, location, vehicle_no, sample_interval_seconds, secret_key, status, remark) VALUES
('DEPART-001', '车载发车终端 京A·12345', 'DEPART', '一号冷藏车驾驶室', '京A·12345', NULL, 'dev-key-depart-001', 1,
 '第 17 条通道：司机上车刷卡即确认发车，把该车牌名下「待发车」的运单推进为「运输中」。设备只报「我（某车）发车了」，运单号由系统按车牌倒查 —— 同装车口「扫码是确认不是选择」。发车是物理事实，且不产生业务判定。'),
('DEPART-003', '车载发车终端 京A·12346', 'DEPART', '二号冷藏车驾驶室', '京A·12346', NULL, 'dev-key-depart-003', 1,
 '第 17 条通道：同上。车牌与终端的绑定是结构化的（vehicle_no 列），不靠设备名里的字符串猜。');


-- ---------------------------------------------------------------------------
-- 6) temperature_log 补断档三列
--    sample_interval_seconds：写入当时的基准（设备声明的），口径固化下来，将来改设备属性不影响历史判断。
--    gap_seconds：距上一条记录的间隔（秒），NULL=首条。
--    is_gap：1 = 本条与上一条之间存在采集断档 —— 标在**断档之后的第一条**上。
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='temperature_log' AND COLUMN_NAME='sample_interval_seconds');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.temperature_log ADD COLUMN sample_interval_seconds INT DEFAULT NULL COMMENT ''写入时的额定上报间隔（秒），断档判定基准；NULL=用了服务默认值'' AFTER is_abnormal',
  'SELECT ''temperature_log.sample_interval_seconds 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='temperature_log' AND COLUMN_NAME='gap_seconds');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.temperature_log ADD COLUMN gap_seconds INT DEFAULT NULL COMMENT ''距上一条记录的间隔（秒）；NULL=本运单首条'' AFTER sample_interval_seconds',
  'SELECT ''temperature_log.gap_seconds 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='temperature_log' AND COLUMN_NAME='is_gap');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.temperature_log ADD COLUMN is_gap TINYINT NOT NULL DEFAULT 0 COMMENT ''1=本条之前存在采集断档（间隔 > 额定间隔×容差）；空白不等于正常'' AFTER gap_seconds',
  'SELECT ''temperature_log.is_gap 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='temperature_log' AND INDEX_NAME='idx_gap');
SET @s := IF(@c = 0,
  'ALTER TABLE db_distribution.temperature_log ADD KEY idx_gap (is_gap)',
  'SELECT ''temperature_log.idx_gap 已存在，跳过'' AS skip_note');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;


-- ---------------------------------------------------------------------------
-- 7) 核验
-- ---------------------------------------------------------------------------
SELECT '--- transport_item 新列 ---' AS item;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='transport_item'
  AND COLUMN_NAME IN ('store_id','store_name','seq') ORDER BY ordinal_position;

SELECT '--- store_receipt 唯一键 ---' AS item;
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='store_receipt'
  AND INDEX_NAME IN ('uk_transport','uk_transport_store')
GROUP BY INDEX_NAME, NON_UNIQUE;

SELECT '--- 载货明细门店回填情况 ---' AS item;
SELECT COUNT(*) AS 总明细, SUM(store_name IS NOT NULL) AS 已填门店, SUM(seq IS NOT NULL) AS 已填站点序号
FROM db_distribution.transport_item;

SELECT '--- temperature_log 断档列 ---' AS item;
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='temperature_log'
  AND COLUMN_NAME IN ('sample_interval_seconds','gap_seconds','is_gap');

SELECT '--- 车载/发车设备台账 ---' AS item;
SELECT device_no, device_name, channel, location, vehicle_no, sample_interval_seconds, status
FROM db_ingest.ingest_device WHERE channel IN ('DEPART') OR vehicle_no IS NOT NULL ORDER BY device_no;
