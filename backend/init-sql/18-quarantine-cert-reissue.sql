-- =============================================================================
-- 18-quarantine-cert-reissue.sql
-- 产地检疫证明：让「作废后重新出证」走得通
--
-- 背景（由 void-fallback-test 用例 8 暴露）：
--   quarantine_certificate 上压着 UNIQUE KEY uk_pig (pig_id)，即「一头猪一辈子只能有一行证书」。
--   而项目对「录错了要更正」的统一口径是 **作废 + 重录**——
--   作废只是把 void_flag 翻成 1，行还在表里，于是重出证时被唯一键顶掉，抛
--   DuplicateKeyException → 前端看到「系统内部异常」（实测就是 code=5000）。
--   也就是说：产地检疫出证作为唯一保留的人工环节，其核心闭环（出证 → 作废 → 重出证）本身就是断的。
--
-- 本次改动：
--   去掉 pig_id 上的唯一约束，改为普通索引；
--   「同一头猪同时只能有一张有效证书」继续由 Service 层查询有效记录来保证
--   （QuarantineCertServiceImpl.uploadCert 里的 count(valid) 校验，@TableLogic 已自动只数有效行）。
--   cert_no 的唯一约束保留不变——证书编号不可复用，重录要换新编号。
--
-- 执行方式：
--   docker cp backend/init-sql/18-quarantine-cert-reissue.sql pork-traceability-mysql-1:/tmp/18.sql
--   docker exec pork-traceability-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 < /tmp/18.sql'
-- =============================================================================

SET NAMES utf8mb4;
USE db_breeding;

-- 幂等：只有当前确实存在 uk_pig 唯一键时才动手
SET @has_uk_pig := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = 'db_breeding' AND TABLE_NAME = 'quarantine_certificate'
                      AND INDEX_NAME = 'uk_pig');
SET @has_idx_pig := (SELECT COUNT(*) FROM information_schema.STATISTICS
                     WHERE TABLE_SCHEMA = 'db_breeding' AND TABLE_NAME = 'quarantine_certificate'
                       AND INDEX_NAME = 'idx_pig');

SET @ddl := IF(@has_uk_pig > 0 AND @has_idx_pig = 0,
               'ALTER TABLE db_breeding.quarantine_certificate DROP INDEX uk_pig, ADD INDEX idx_pig (pig_id)',
               IF(@has_uk_pig > 0,
                  'ALTER TABLE db_breeding.quarantine_certificate DROP INDEX uk_pig',
                  'DO 0'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '--- 自检：uk_pig 应为 0 行，idx_pig 应为 1 行 ---' AS check_item;
SELECT INDEX_NAME, NON_UNIQUE, COUNT(*) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'db_breeding' AND TABLE_NAME = 'quarantine_certificate'
  AND INDEX_NAME IN ('uk_pig', 'idx_pig', 'uk_cert_no')
GROUP BY INDEX_NAME, NON_UNIQUE
ORDER BY INDEX_NAME;
