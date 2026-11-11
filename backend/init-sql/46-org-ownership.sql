-- 46: 屠宰/配送记录加机构归属（org_id）——只埋点，不加过滤。
--
-- 背景：机构隔离目前只覆盖零售商（store 维度）与养殖角色（FarmScope 按场）。
-- 屠宰记录（入场/检验/盖章/瘦肉精）与冷链运单上没有任何机构字段，将来出现
-- 第二家屠宰场/承运商时，既无法过滤、也无法回填历史归属。本脚本提前把
-- 归属列埋进表里，存量一次性收口到当前唯一活跃机构，新写入随路径带出：
--   · 屠宰四表：设备写入 ← ingest_device.org_id（ slaughter 侧设备 → 机构 3）
--   · 运单：装车口设备写入 ← 设备 org_id；人工生成/建单 ← 登录配送机构的 X-User-Org
-- 查询过滤、403 守卫一律不加（单活跃机构时过滤无意义），待第二家机构进场再启用。
--
-- 归属口径：org_id 指操作发生所在的机构（屠宰场 / 承运商），不是货主的机构。
-- NULL = 平台管理员操作或无登录头的内部写入，表示「归属未知」，不猜。

ALTER TABLE db_slaughter.entry_inspection
    ADD COLUMN org_id BIGINT NULL COMMENT '操作机构（sys_org.id）：屠宰场归属，NULL=平台/未知',
    ADD INDEX idx_entry_org (org_id);
ALTER TABLE db_slaughter.slaughter_inspection
    ADD COLUMN org_id BIGINT NULL COMMENT '操作机构（sys_org.id）：屠宰场归属，NULL=平台/未知',
    ADD INDEX idx_insp_org (org_id);
ALTER TABLE db_slaughter.carcass_stamp
    ADD COLUMN org_id BIGINT NULL COMMENT '操作机构（sys_org.id）：屠宰场归属，NULL=平台/未知',
    ADD INDEX idx_stamp_org (org_id);
ALTER TABLE db_slaughter.ractopamine_test
    ADD COLUMN org_id BIGINT NULL COMMENT '操作机构（sys_org.id）：屠宰场归属，NULL=平台/未知',
    ADD INDEX idx_racto_org (org_id);
ALTER TABLE db_distribution.cold_chain_transport
    ADD COLUMN org_id BIGINT NULL COMMENT '承运机构（sys_org.id）：NULL=平台/未知',
    ADD INDEX idx_transport_org (org_id);

-- 设备台账加归属：设备物理上装在哪家机构，写入的记录就归哪家。
-- 只圈本次需要的 7 台：屠宰侧 4 台（入场/检验/盖章/瘦肉精）+ 配送侧 3 台（装车/通用终端/温度）。
-- 其余设备（养殖侧、门店侧、分割）本次不改写入路径，不设归属。
ALTER TABLE db_ingest.ingest_device
    ADD COLUMN org_id BIGINT NULL COMMENT '设备所属机构（sys_org.id）：写入记录的归属来源，NULL=未绑定';

-- 存量回填：历史上所有屠宰/配送操作都由 slaughter_user(org=3)/distribution_user(org=4) 完成
UPDATE db_slaughter.entry_inspection     SET org_id = 3 WHERE org_id IS NULL;
UPDATE db_slaughter.slaughter_inspection SET org_id = 3 WHERE org_id IS NULL;
UPDATE db_slaughter.carcass_stamp        SET org_id = 3 WHERE org_id IS NULL;
UPDATE db_slaughter.ractopamine_test     SET org_id = 3 WHERE org_id IS NULL;
UPDATE db_distribution.cold_chain_transport SET org_id = 4 WHERE org_id IS NULL;

-- 设备绑定：按通道归属
UPDATE db_ingest.ingest_device SET org_id = 3
 WHERE channel IN ('ENTRY', 'INSPECTION', 'STAMP', 'RACTOPAMINE');
UPDATE db_ingest.ingest_device SET org_id = 4
 WHERE channel IN ('LOAD', 'DEPART', 'ARRIVE', 'TEMPERATURE');
