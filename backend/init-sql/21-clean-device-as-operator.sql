-- =============================================================================
-- 21-clean-device-as-operator.sql
--
-- 修正「设备名被写进人名/责任人字段」的存量数据。
--
-- 背景：接入层原先在各通道里做了兜底——报文没带操作人时，用设备名（形如
-- 「INJ-001 自动采集」）填进操作人字段。结果追溯链上「谁给这头猪打的疫苗」
-- 查到的是注射器本身，操作人应是现场操作设备的兽医/查验员/检测员/签收人。
--
-- 已改：接入层不再用设备名兜底，缺操作人一律转「待人工确认」由人工补录实名；
--       业务侧兜底值也由「设备自动采集」改为「未记录」。
-- 本脚本：把库里已经写错的存量值改成「未记录」（保留记录本身，不删不移）。
--
-- 例外：冷链温度的 recorder 本来就是采集设备（探头/温度记录仪），
--       没有人工参与，因此只把「TEMP-001 自动采集」规范成设备编号「TEMP-001」。
--
-- 幂等：可重复执行，无匹配行时不改动任何数据。
-- 执行：docker cp 进 mysql 容器后 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 本文件`
-- =============================================================================

-- 免疫注射：操作人 = 现场接种兽医
UPDATE db_breeding.vaccine_record
SET operator = '未记录'
WHERE operator LIKE '%自动采集%';

-- 入场查验：查验员 = 现场查验人员
UPDATE db_slaughter.entry_inspection
SET inspector = '未记录'
WHERE inspector LIKE '%自动采集%';

-- 瘦肉精检测：检测员 = 现场检测人员
UPDATE db_slaughter.ractopamine_test
SET operator = '未记录'
WHERE operator LIKE '%自动采集%';

-- 屠宰检验：检验员 = 官方兽医
UPDATE db_slaughter.slaughter_inspection
SET veterinary = '未记录'
WHERE veterinary LIKE '%自动采集%';

-- 胴体盖章：检疫员 = 授权兽医
UPDATE db_slaughter.carcass_stamp
SET veterinary = '未记录'
WHERE veterinary LIKE '%自动采集%';

-- 分割批次：操作人 = 现场分割操作人员
UPDATE db_distribution.split_batch
SET operator = '未记录'
WHERE operator LIKE '%自动采集%';

-- 门店签收：签收人 = 门店收货人
UPDATE db_distribution.store_receipt
SET receiver = '未记录'
WHERE receiver LIKE '%自动采集%';

-- 冷链温度：recorder 是采集设备本身，只规范成纯设备编号（去掉「 自动采集」后缀）
UPDATE db_distribution.temperature_log
SET recorder = TRIM(REPLACE(recorder, '自动采集', ''))
WHERE source = 'DEVICE' AND recorder LIKE '%自动采集%';

-- 清理结果自检：下面两条查询应返回 0 行
-- SELECT 'vaccine' t, operator v FROM db_breeding.vaccine_record WHERE operator LIKE '%自动采集%'
-- UNION ALL SELECT 'entry', inspector FROM db_slaughter.entry_inspection WHERE inspector LIKE '%自动采集%'
-- UNION ALL SELECT 'racto', operator FROM db_slaughter.ractopamine_test WHERE operator LIKE '%自动采集%'
-- UNION ALL SELECT 'split', operator FROM db_distribution.split_batch WHERE operator LIKE '%自动采集%'
-- UNION ALL SELECT 'receipt', receiver FROM db_distribution.store_receipt WHERE receiver LIKE '%自动采集%';
