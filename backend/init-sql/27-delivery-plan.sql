-- ============================================================================
-- 冷链运单自动化（第 1 层）：补上「发货计划」这个缺失的上游对象
--
-- 背景（2026-10-09 设计评审）：全系统每个环节都有一台现场设备作为事实来源
-- （养殖建档靠耳标读写器、出栏称重靠地磅、分割批次靠扫码称重台、温度靠车载探头…），
-- 唯独冷链运单是凭空手填的。运单 9 个字段里，只有「分割批次」和「起运地」有来源
-- （起运地已能从批次带出），其余 7 个字段系统里查无出处。
--
-- 但运单与分割的自动化性质不同：分割是「事实已经发生，只是没记下来」，让设备记即可；
-- 运单是「决策还没做」—— 这车拉哪批货、发去哪家店、谁开，信息根本不存在。
-- 所以第一步不是装设备，而是把决策结构化，补一张上游单据。
--
-- 本脚本做三件事：
--   1) 新建 delivery_plan（发货计划）：一张计划 = 一个目的地 + 一个车次 + N 个待发分割批次。
--      明细存 delivery_plan_item。运单可从计划单一键生成，目的地/时间/车辆信息自动带出。
--   2) 新建 transport_item（运单装载明细）：解决「一张运单只能挂一个分割批次」的限制。
--      现实里一车必然装多批（同一屠宰场同日期的多个分割产出），
--      原表只有单值 split_batch_id 表达不了，于是「哪些产出还没发运」也查不出来。
--      保留 cold_chain_transport.split_batch_id 作为「主批次」字段，向后兼容存量数据与现有查询。
--   3) 存量 34 条运单按 split_batch_id 回填 transport_item，历史一对一的运单也能进入新查询口径。
--
-- 不做的事（刻意）：
--   · 不给运单加全局唯一约束「一个分割批次只能发运一次」——存量数据里 split_batch_id=24
--     被 4 张运单引用（「一条上报=一条部位记录」时代的产物），是历史事实，不篡改、不硬塞约束。
--     「待发运」用查询口径表达（未被任何 transport_item 引用）。
--   · 计划单不物理删除，只置 status=3 取消，保留「发过什么计划」的轨迹。
--
-- 幂等：本文件可重复执行 —— CREATE 用 IF NOT EXISTS，回填用 INSERT IGNORE（靠 uk_transport_split 去重）。
-- 执行：docker cp 进 mysql 容器后 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 本文件`
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 发货计划（决策单：发哪批货、去哪家店、什么时候走）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.delivery_plan (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    plan_no          VARCHAR(32)   NOT NULL COMMENT '计划号 DP+yyyyMMdd+3位当日流水；当日全局排流水，含已取消的不复用',
    plan_date        DATE          NOT NULL COMMENT '计划发货日',
    store_id         BIGINT        NOT NULL COMMENT '目的地门店 id（取自机构树 type=retail）',
    store_name       VARCHAR(128)  NOT NULL COMMENT '目的地门店名（冗余，展示免联表）',
    vehicle_no       VARCHAR(16)   NULL COMMENT '车牌号；计划阶段可空（车还没定），生成运单时必填',
    vehicle_type     VARCHAR(32)   NULL COMMENT '车辆类型：冷藏车/冷冻车/保温车',
    refrigeration    VARCHAR(64)   NULL COMMENT '制冷方式：机械制冷/蓄冷板/液氮',
    driver_name      VARCHAR(32)   NULL COMMENT '司机姓名（实名）；计划阶段可空',
    driver_phone     VARCHAR(20)   NULL COMMENT '司机电话',
    origin           VARCHAR(256)  NULL COMMENT '起运地（由货物批次带出，可改）',
    planned_depart   DATETIME      NULL COMMENT '计划发车',
    planned_arrive   DATETIME      NULL COMMENT '计划到达',
    status           TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1=待发货 2=已生成运单 3=已取消',
    item_count       INT           NOT NULL DEFAULT 0 COMMENT '明细条数快照（= 要发运的分割批次数）',
    total_weight_kg  DECIMAL(10,2) NULL COMMENT '计划总重(kg)快照 = 各明细分割批次重量合计',
    transport_id     BIGINT        NULL COMMENT '生成运单后回填 cold_chain_transport.id',
    transport_no     VARCHAR(32)   NULL COMMENT '生成运单后回填运单号（冗余，展示免联表）',
    generated_time   DATETIME      NULL COMMENT '运单生成时刻',
    remark           VARCHAR(500)  NULL COMMENT '备注',
    operator         VARCHAR(32)   NULL COMMENT '制单人（实名）',
    create_time      DATETIME      NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_no (plan_no),
    KEY idx_status (status),
    KEY idx_plan_date (plan_date),
    KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '发货计划：一个目的地+一个车次+N个待发分割批次，运单由它一键生成';

-- ---------------------------------------------------------------------------
-- 2) 计划明细（这一车要装的货）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.delivery_plan_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    plan_id         BIGINT        NOT NULL COMMENT '所属计划 delivery_plan.id',
    split_batch_id  BIGINT        NOT NULL COMMENT '要发运的分割批次 split_batch.id',
    batch_no        VARCHAR(32)   NOT NULL COMMENT '分割批次号（冗余，展示免联表）',
    part_code       VARCHAR(16)   NULL COMMENT '部位编码（冗余）',
    product_name    VARCHAR(64)   NULL COMMENT '部位名（冗余）',
    weight_kg       DECIMAL(8,2)  NULL COMMENT '该批次重量(kg)，制单时快照',
    create_time     DATETIME      NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_split (plan_id, split_batch_id) COMMENT '同一计划里同一分割批次只能出现一次',
    KEY idx_plan (plan_id),
    KEY idx_split (split_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '发货计划明细：本次要装车发运的分割批次清单';

-- ---------------------------------------------------------------------------
-- 3) 运单装载明细（解决「一张运单只能挂一个批次」）
--    cold_chain_transport.split_batch_id 保留为「主批次」（第一条明细），向后兼容。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.transport_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    transport_id    BIGINT        NOT NULL COMMENT '所属运单 cold_chain_transport.id',
    split_batch_id  BIGINT        NOT NULL COMMENT '装载的分割批次 split_batch.id',
    batch_no        VARCHAR(32)   NOT NULL COMMENT '分割批次号（冗余）',
    weight_kg       DECIMAL(8,2)  NULL COMMENT '该批次重量(kg)，装车时快照',
    create_time     DATETIME      NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transport_split (transport_id, split_batch_id) COMMENT '同一运单内同一批次只记一次',
    KEY idx_transport (transport_id),
    KEY idx_split (split_batch_id) COMMENT '「这个批次发运了没有」的判定走它'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '运单装载明细：一张运单实际装运的分割批次（一车多批）';

-- ---------------------------------------------------------------------------
-- 4) 存量回填：历史运单是一对一的，按 split_batch_id 补出装载明细
--    INSERT IGNORE 靠 uk_transport_split 去重，脚本可重放。
--    跨运单重复引用同一批次的历史数据照实保留（不篡改既成事实）。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO db_distribution.transport_item
    (transport_id, split_batch_id, batch_no, weight_kg, create_time)
SELECT t.id, t.split_batch_id, b.batch_no, b.weight_kg, t.create_time
FROM db_distribution.cold_chain_transport t
JOIN db_distribution.split_batch b ON b.id = t.split_batch_id
WHERE t.split_batch_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5) 核验
-- ---------------------------------------------------------------------------
SELECT '发货计划数' AS item, COUNT(*) AS v FROM db_distribution.delivery_plan
UNION ALL SELECT '计划明细数', COUNT(*) FROM db_distribution.delivery_plan_item
UNION ALL SELECT '运单装载明细数', COUNT(*) FROM db_distribution.transport_item
UNION ALL SELECT '其中有装载明细的运单数', COUNT(DISTINCT transport_id) FROM db_distribution.transport_item
UNION ALL SELECT '运单总数', COUNT(*) FROM db_distribution.cold_chain_transport
UNION ALL SELECT '尚未发放运单的分割批次数',
    (SELECT COUNT(*) FROM db_distribution.split_batch b
      WHERE b.void_flag = 0
        AND NOT EXISTS (SELECT 1 FROM db_distribution.transport_item ti WHERE ti.split_batch_id = b.id));
