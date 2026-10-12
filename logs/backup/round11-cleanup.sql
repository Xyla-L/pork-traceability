-- 第十一轮（分割作业 + 部位字典 + 出肉率对账）回归残留清理
-- 备份：logs/backup/round11-residue-20261009-1258.sql
-- 保留的演示真实数据：carcass_batch 53 (CB20261009-001)、carcass_batch_pig 41、
--   blockchain_record 868、ingest_staging 1068（12:29 界面演示设备归集）

-- 1) 上链留痕（按 (biz_type, biz_id) 成对删，保留 868 这条演示批次）
DELETE FROM db_common.blockchain_record
 WHERE id BETWEEN 978 AND 1012;

-- 2) 接入流水（保留 1068 演示那条）
DELETE FROM db_ingest.ingest_staging
 WHERE id BETWEEN 1183 AND 1276;

-- 3) 兜底解锁票据（今日 4 条）
DELETE FROM db_ingest.source_fallback_unlock
 WHERE id IN (28, 29, 30, 31);

-- 4) 回归创建的未实名测试账号
DELETE FROM db_common.sys_user
 WHERE id = 23 AND username = 'vn21795555';

-- 5) 胴体批次及其关联行（回归验证屠宰场 MANUAL 批次）
DELETE FROM db_distribution.carcass_batch_pig WHERE id = 49;
DELETE FROM db_distribution.carcass_batch      WHERE id = 61;

-- 6) 屠宰侧业务记录
DELETE FROM db_slaughter.carcass_stamp        WHERE id IN (72, 73);
DELETE FROM db_slaughter.slaughter_inspection WHERE id BETWEEN 177 AND 187;
DELETE FROM db_slaughter.ractopamine_test     WHERE id BETWEEN 119 AND 125;
DELETE FROM db_slaughter.entry_inspection     WHERE id BETWEEN 131 AND 139;

-- 7) 养殖侧业务记录
DELETE FROM db_breeding.vaccine_record        WHERE id IN (58, 59, 60);
DELETE FROM db_breeding.quarantine_certificate WHERE id BETWEEN 117 AND 126;
DELETE FROM db_breeding.slaughter_apply       WHERE id BETWEEN 127 AND 137;

-- 8) 临时猪
DELETE FROM db_breeding.pig_individual WHERE id BETWEEN 129 AND 137;

-- 9) 撤销回归对演示猪状态的推进（exit-apply 把猪 33 推成「已出栏(2)」）
UPDATE db_breeding.pig_individual SET status = 1 WHERE id = 33 AND status = 2;
