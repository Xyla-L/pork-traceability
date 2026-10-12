-- ============================================================================
-- 站点级到达：把「到达」从运单粒度下沉到「卸货门店」粒度（第 17 条通道 ARRIVE）
--
-- 背景（2026-10-10 追问）：一车装了 12 批货顺路送 3 家店，运单主表只有一个
-- arrive_time —— 它只能表达「这趟车到了第一个站」，中间两站的到没到没有落点；
-- 门店签收也拦不住「车还在第 1 站、第 3 站就签收了」。
--
-- 定下的口径（做法 A：站点级到达）：
--   一、**到达的粒度是「卸货门店」，不是「批次」**。
--       「到了没」问的是「车在不在这个卸货点」，是**位置事实**，与车厢里装了几批货无关：
--       同店多批只到一次，多站车次一站一次。判据是卸货门店数，不是批数。
--   二、站点**不是**新概念，它一直存在于 transport_item（store_id/store_name/seq）。
--       本表补的是 transport_item 装不下的东西：**这一站的行程状态**
--       （未到达 → 已到达 → 已签收）与**到达时刻**。签收记录（store_receipt）
--       是站点的**终态凭证**，只在签收那一刻才建行 —— 「到了但还在排队卸货」无处安放，
--       所以站点状态必须单独落一张表，而不是往 store_receipt 上加列。
--       （31 号文件拒绝建 transport_stop 的理由是「站点清单能从 transport_item 聚合出来」，
--        那说的是**清单**；这里是**行程状态**，是新事实，不冲突。）
--   三、station 唯一口径 = (transport_id, stop_no)，stop_no 与 transport_item.seq **完全同源**
--       （装车扫码顺序即卸货顺序），不另算一套。
--   四、运单整体状态**由站点汇总推导**（不另存布尔值，避免两处打架）：
--       全部站点到达 → 3 待签收；全部站点签收 → 4 已签收；
--       未发车（status=1）不动 —— 那时候站点本来就都是「未到达」。
--   五、**到达必须按站点序号递增**：第 2 站到了而第 1 站没到 = 漏扫或走错店，
--       转人工核对，不照单收下（与 DEPART 不敢猜「发的是哪一趟」同一条红线）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE；回填按 (transport_id, stop_no) 判重。
-- 执行：docker cp 进 mysql 容器后
--       `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 < 本文件`
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1) 站点行程台账
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.transport_stop (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  transport_id      BIGINT       NOT NULL                COMMENT '运单 id',
  stop_no           INT          NOT NULL                COMMENT '站点序号（1 起；口径 = transport_item.seq，即装车扫码顺序 = 卸货顺序）',
  store_id          BIGINT       DEFAULT NULL            COMMENT '卸货门店 id（权威来源是机构树；人工建单的老运单可能为空，此时按 store_name 定位）',
  store_name        VARCHAR(128) NOT NULL                COMMENT '卸货门店名（展示快照）',
  item_count        INT          NOT NULL DEFAULT 0      COMMENT '本站批次数',
  weight_kg         DECIMAL(12,2) DEFAULT NULL           COMMENT '本站货物总重 kg',
  status            TINYINT      NOT NULL DEFAULT 0      COMMENT '行程状态 0 未到达 / 1 已到达 / 2 已签收',
  arrive_time       DATETIME     DEFAULT NULL            COMMENT '到达时刻（未到达为空）',
  arrive_source     VARCHAR(24)  DEFAULT NULL            COMMENT '到达怎么确认的 DEVICE=门店设备自动确认 / MANUAL=人工确认',
  arrive_source_ref VARCHAR(64)  DEFAULT NULL            COMMENT '到达上报编号（设备号 / 操作留痕）',
  receipt_id        BIGINT       DEFAULT NULL            COMMENT '签收记录 id（store_receipt，仅已签收时有值）',
  raw_payload       TEXT                                 COMMENT '到达报文原文（设备通道才有，人工确认为空）',
  create_time       DATETIME     NOT NULL                COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_transport_stop (transport_id, stop_no),
  KEY idx_stop_store (transport_id, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车次站点行程台账：一站一条，记未到达/已到达/已签收与到达时刻';


-- ---------------------------------------------------------------------------
-- 2) 回填存量运单的站点行
--    分组口径：同一运单内 (transport_item.seq) 相同的货 = 同一个站点的一份货。
--    存量 36 条明细 seq 全为 1（单站），回填出 36 行；多站运单本文件写成通用形式。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_distribution.transport_stop
    (transport_id, stop_no, store_id, store_name, item_count, weight_kg, status, arrive_time, arrive_source, create_time)
SELECT g.transport_id,
       ROW_NUMBER() OVER (PARTITION BY g.transport_id ORDER BY g.min_id) AS stop_no,
       g.store_id,
       COALESCE(g.store_name, '未记录门店') AS store_name,
       g.item_count,
       g.weight_kg,
       0, NULL, NULL, NOW()
FROM (
    SELECT i.transport_id,
           MIN(i.id)                          AS min_id,
           MAX(i.store_id)                    AS store_id,
           MAX(i.store_name)                  AS store_name,
           COUNT(*)                           AS item_count,
           SUM(COALESCE(i.weight_kg, 0))      AS weight_kg
    FROM db_distribution.transport_item i
    GROUP BY i.transport_id, COALESCE(i.seq, 1)
) g;

-- 2.1) 存量运单已经在途/已到达的：站点按运单状态补「已到达」
--      （到达时刻取运单的到达时间，没有就退回发车时间 —— 那是当时唯一有的事实）
UPDATE db_distribution.transport_stop s
JOIN db_distribution.cold_chain_transport t ON t.id = s.transport_id
SET s.status        = 1,
    s.arrive_time   = COALESCE(t.arrive_time, t.depart_time, t.create_time),
    s.arrive_source = 'MANUAL'
WHERE t.status IN (3, 4) AND s.status = 0;

-- 2.2) 已有签收记录的：站点标「已签收」并挂上签收凭证
--      门店匹配以 store_id 为准，存量人工建单的老运单 store_id 为空 → 退回门店名匹配
UPDATE db_distribution.transport_stop s
JOIN db_distribution.store_receipt r
  ON r.transport_id = s.transport_id
 AND (r.store_id = s.store_id OR (s.store_id IS NULL AND r.store_name = s.store_name))
SET s.status        = 2,
    s.receipt_id    = r.id,
    s.arrive_time   = COALESCE(s.arrive_time, r.receipt_time),
    s.arrive_source = COALESCE(s.arrive_source, 'MANUAL')
WHERE s.status = 1;

-- 2.3) 兜底：**单站 + 单签收**的运单，签收记录只能属于那一站，直接配对
--      为什么需要这一档：存量 12 张种子运单的 transport_item.store_name（示范一店）
--      与 store_receipt.store_name（金牧生鲜门店）本来就对不上（种子数据两侧不一致）。
--      一车一站时签收凭证没有别的去处，按门店名匹配不上并不代表"没签收"，
--      所以用「唯一性」而非「字段相等」来兜这一档 —— 多站运单绝不走这条路。
UPDATE db_distribution.transport_stop s
JOIN db_distribution.store_receipt r ON r.transport_id = s.transport_id
JOIN (SELECT transport_id FROM (SELECT transport_id, COUNT(*) c
        FROM db_distribution.transport_stop GROUP BY transport_id) a WHERE a.c = 1) one_s
  ON one_s.transport_id = s.transport_id
JOIN (SELECT transport_id FROM (SELECT transport_id, COUNT(*) c
        FROM db_distribution.store_receipt GROUP BY transport_id) b WHERE b.c = 1) one_r
  ON one_r.transport_id = s.transport_id
SET s.status        = 2,
    s.receipt_id    = r.id,
    s.arrive_time   = COALESCE(s.arrive_time, r.receipt_time),
    s.arrive_source = COALESCE(s.arrive_source, 'MANUAL')
WHERE s.status = 1;


-- ---------------------------------------------------------------------------
-- 3) 到达确认设备台账（第 17 条通道 ARRIVE）
--    门店收货区设备：车牌识别 / 扫码枪扫运单 / 地磅 —— 只提供两个现场事实：
--    「哪台车」「到了哪个店」。站点与运单由系统**倒查**（设备不猜，也不选）。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_ingest.ingest_device (device_no, device_name, channel, location, secret_key, status, remark) VALUES
('ARRIVE-001', '门店收货区到达确认终端', 'ARRIVE', '各门店收货区（车牌识别 + 扫码枪）', 'dev-key-arrive-001', 1,
 '第 17 条通道：门店收货区确认「某车牌的车到了本店」。报的是两个物理事实（车牌 + 门店），运单与站点由配送服务倒查 —— 设备不产生任何业务判定。三条红线：该车牌名下没有运输中的运单 / 有多张运输中运单 / 报的门店不是当前第一个未到达的站，一律转人工（跳站说明漏扫或走错店）。');


-- ---------------------------------------------------------------------------
-- 4) 核验
-- ---------------------------------------------------------------------------
SELECT '站点表列' AS item, GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) AS v
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'transport_stop'
UNION ALL SELECT '站点行数', CAST(COUNT(*) AS CHAR) FROM db_distribution.transport_stop
UNION ALL SELECT '  未到达(0)', CAST(SUM(status = 0) AS CHAR) FROM db_distribution.transport_stop
UNION ALL SELECT '  已到达(1)', CAST(SUM(status = 1) AS CHAR) FROM db_distribution.transport_stop
UNION ALL SELECT '  已签收(2)', CAST(SUM(status = 2) AS CHAR) FROM db_distribution.transport_stop
UNION ALL SELECT '装载明细数（应等）', CAST(COUNT(*) AS CHAR) FROM db_distribution.transport_item;

-- 不变量：站点行数 = 各运单「站点数」之和；已签收站点数 = 已被回填的签收记录数
SELECT '按运单核对（站点数 vs 明细里的门店数）' AS check_name,
       s.transport_id, s.stops, g.items_stores, IF(s.stops = g.items_stores, 'OK', 'MISMATCH') AS result
FROM (SELECT transport_id, COUNT(*) AS stops FROM db_distribution.transport_stop GROUP BY transport_id) s
JOIN (SELECT transport_id, COUNT(DISTINCT COALESCE(seq, 1)) AS items_stores
      FROM db_distribution.transport_item GROUP BY transport_id) g ON g.transport_id = s.transport_id
WHERE s.stops <> g.items_stores;

SELECT '已签收站点数 vs 存量签收记录数' AS check_name,
       (SELECT COUNT(*) FROM db_distribution.transport_stop WHERE status = 2) AS stops_signed,
       (SELECT COUNT(*) FROM db_distribution.store_receipt r
         JOIN db_distribution.cold_chain_transport t ON t.id = r.transport_id) AS receipts_on_live_transport;
