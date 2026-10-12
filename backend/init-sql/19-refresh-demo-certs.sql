-- =============================================================================
-- 19-refresh-demo-certs.sql —— 顺延演示检疫证有效期（幂等，可反复执行）
-- =============================================================================
-- 目的：
--   让「入场查验」能演示出「自动入账」——设备上报耳标 + 检疫证号后，
--   核验通过（证存在 / 未过期 / 证上耳标与扫描耳标一致）才会自动写业务表。
--
-- 背景（这是一个真实踩过的坑）：
--   08-test-data.sql 早期把产地检疫证的 valid_until 写成「初始化当天 +1~3 天」，
--   即 DATE_ADD(CURDATE(), INTERVAL n DAY)。种子数据一旦在库里放上一段时间，
--   这些证就全部过期，入场查验会因「检疫证已过有效期」永远转「待人工处理」，
--   模拟上报怎么点都入不了账，看起来像功能坏了，其实是数据腐化。
--   08 号种子已改为 +180 天；已建库用本脚本补救，隔一段时间也可再跑一次。
--
-- 安全性：
--   只动 cert_no 以 'QC-' 开头的演示证、且仅限「未作废且已过期」的行；
--   真实上传的证（编号规则不同）不会被误伤。
-- =============================================================================

UPDATE db_breeding.quarantine_certificate
SET valid_until = DATE_ADD(CURDATE(), INTERVAL 180 DAY)
WHERE void_flag = 0
  AND cert_no LIKE 'QC-%'
  AND (valid_until IS NULL OR valid_until < CURDATE());

-- 自检：过期演示证应剩 0 张
SELECT COUNT(*) AS still_expired_demo_certs
FROM db_breeding.quarantine_certificate
WHERE void_flag = 0 AND cert_no LIKE 'QC-%' AND valid_until < CURDATE();
