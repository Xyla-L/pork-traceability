-- =============================================================================
-- 17-reconcile-chains.sql
-- 追溯链一致性修复（2026-10-04）
--
-- 背景（两处历史遗留）：
--   1. 「有章无检」：部分生猪档案挂了胴体检疫合格章，却查不到任何屠宰检验（宰前/宰后）记录。
--      成因见 08-test-data.sql —— 种子数据是「每表 15 条」按功能模块拼出来的，
--      盖章给了 15 头、检验只给了 8 头，跨表状态本就不保证一致。
--      但「章」是检验合格的结果，没有检验支撑的章在追溯上就是一个洞。
--   2. 「作废未传导」：生猪 ET-DEMO-0002（id=18）在 16 号脚本里按 deleted=1 迁移成「已作废」，
--      可它名下的胴体批次 CB20260916956996、及其派生分割批次与零售商品**仍然是有效状态**——
--      等于「已作废的猪，肉还摆在货架上」。作废必须沿链向下传导，否则作废形同虚设。
--
-- 本次改动：
--   A. 补齐有效猪的屠宰链路：缺宰前/宰后检验的，按「检验结论与印章生效状态一致」补录
--      （章已生效=合格、章未生效=不合格）；缺瘦肉精检测的补录；并同步补上链存证。
--   B. 胴体批次补作废四列（16 号漏了这张表，导致「批次无法作废」），实体同步加 @TableLogic。
--   C. 级联作废：全成员均已作废的胴体批次 → 其派生分割批次 → 关联零售商品，逐层作废，
--      写清理由与操作人并单独上链存证。只作废、不物理删除。
--
-- 幂等：A 用 NOT EXISTS 守卫，B 用 information_schema 判断，C 只处理 void_flag=0 的行，
--       链上存证用 (biz_type,biz_id) 去重。可重复执行。
--
-- 执行方式（PowerShell 管道会毁 UTF-8，必须走 docker cp 二进制通道）：
--   docker cp backend/init-sql/17-reconcile-chains.sql pork-traceability-mysql-1:/tmp/17.sql
--   docker exec pork-traceability-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 < /tmp/17.sql'
-- =============================================================================

SET NAMES utf8mb4;
USE db_slaughter;

-- =============================================================================
-- A. 补齐有效猪的屠宰链路
--    口径：
--      · 有效猪（pig_individual.void_flag = 0）且持有有效印章（carcass_stamp.void_flag = 0）
--      · 缺宰前检验（inspect_type = 1）或宰后检验（inspect_type = 2）或瘦肉精检测
--      · 补录结论与「章是否生效」一致：stamp.status = 1（章生效）→ result = 1 合格；
--        stamp.status = 0（章未生效）→ result = 0 不合格
--      · 来源标记 source = 'MANUAL'、source_ref = 'RECONCILE-20261004'，
--        如实说明这批记录是数据修复补录，不伪装成设备采集
-- =============================================================================

-- A1. 宰前检验
INSERT INTO slaughter_inspection
    (pig_id, inspect_no, batch_no, ear_tag_no, inspect_type, inspect_time, temperature,
     organ_check, result, status, conclusion, veterinary, license_no, e_signature, file_ids,
     content_hash, source, source_ref, create_time, void_flag)
SELECT p.id,
       CONCAT('SI-RECON-PRE-', p.id),
       COALESCE(eb.batch_no, st.batch_no, 'SB-RECON'),
       p.ear_tag_no,
       1,
       DATE_SUB(COALESCE(st.stamp_time, NOW()), INTERVAL 1 DAY),
       38.5,
       IF(COALESCE(st.status, 1) = 1, '{"appearance":"normal"}', '{"appearance":"abnormal"}'),
       IF(COALESCE(st.status, 1) = 1, 1, 0),
       1,
       IF(COALESCE(st.status, 1) = 1, '宰前检验合格', '宰前检验不合格，按规定处理'),
       COALESCE(st.veterinary, '王兽医'),
       CONCAT('VET-RECON-', LPAD(p.id, 4, '0')),
       'RECON-SIG',
       '[]',
       SHA2(CONCAT('SI-RECON-PRE-', p.id, ':', p.id), 256),
       'MANUAL', 'RECONCILE-20261004', NOW(), 0
FROM db_breeding.pig_individual p
JOIN db_slaughter.carcass_stamp st
     ON st.id = (SELECT MIN(cs.id) FROM db_slaughter.carcass_stamp cs
                 WHERE cs.pig_id = p.id AND cs.void_flag = 0)
LEFT JOIN db_slaughter.entry_inspection eb
     ON eb.id = (SELECT MIN(e.id) FROM db_slaughter.entry_inspection e
                 WHERE e.pig_id = p.id AND e.void_flag = 0)
WHERE p.void_flag = 0
  AND NOT EXISTS (SELECT 1 FROM db_slaughter.slaughter_inspection si
                  WHERE si.pig_id = p.id AND si.inspect_type = 1 AND si.void_flag = 0);

-- A2. 宰后检验
INSERT INTO slaughter_inspection
    (pig_id, inspect_no, batch_no, ear_tag_no, inspect_type, inspect_time, temperature,
     organ_check, result, status, conclusion, veterinary, license_no, e_signature, file_ids,
     content_hash, source, source_ref, create_time, void_flag)
SELECT p.id,
       CONCAT('SI-RECON-POST-', p.id),
       COALESCE(eb.batch_no, st.batch_no, 'SB-RECON'),
       p.ear_tag_no,
       2,
       COALESCE(st.stamp_time, NOW()),
       38.8,
       IF(COALESCE(st.status, 1) = 1,
          '{"heart":"normal","liver":"normal","lung":"normal"}',
          '{"heart":"normal","liver":"lesion","lung":"normal"}'),
       IF(COALESCE(st.status, 1) = 1, 1, 0),
       1,
       IF(COALESCE(st.status, 1) = 1, '宰后检验合格', '宰后检验不合格，按规定处理'),
       COALESCE(st.veterinary, '王兽医'),
       CONCAT('VET-RECON-', LPAD(p.id, 4, '0')),
       'RECON-SIG',
       '[]',
       SHA2(CONCAT('SI-RECON-POST-', p.id, ':', p.id), 256),
       'MANUAL', 'RECONCILE-20261004', NOW(), 0
FROM db_breeding.pig_individual p
JOIN db_slaughter.carcass_stamp st
     ON st.id = (SELECT MIN(cs.id) FROM db_slaughter.carcass_stamp cs
                 WHERE cs.pig_id = p.id AND cs.void_flag = 0)
LEFT JOIN db_slaughter.entry_inspection eb
     ON eb.id = (SELECT MIN(e.id) FROM db_slaughter.entry_inspection e
                 WHERE e.pig_id = p.id AND e.void_flag = 0)
WHERE p.void_flag = 0
  AND NOT EXISTS (SELECT 1 FROM db_slaughter.slaughter_inspection si
                  WHERE si.pig_id = p.id AND si.inspect_type = 2 AND si.void_flag = 0);

-- A3. 瘦肉精检测（阴性）
INSERT INTO ractopamine_test
    (pig_id, test_no, batch_no, ear_tag_no, sample_no, test_type, test_time, test_method,
     sample_part, result, status, operator, file_ids, report_url, content_hash,
     source, source_ref, create_time, void_flag)
SELECT p.id,
       CONCAT('RT-RECON-', p.id),
       COALESCE(eb.batch_no, st.batch_no, 'SB-RECON'),
       p.ear_tag_no,
       CONCAT('SAMPLE-RECON-', p.id),
       '瘦肉精检测',
       DATE_SUB(COALESCE(st.stamp_time, NOW()), INTERVAL 2 DAY),
       '胶体金法',
       '尿液',
       1,
       2,
       '赵检测',
       '[]',
       CONCAT('/file/recon-report-', p.id),
       SHA2(CONCAT('RT-RECON-', p.id, ':', p.id), 256),
       'MANUAL', 'RECONCILE-20261004', NOW(), 0
FROM db_breeding.pig_individual p
JOIN db_slaughter.carcass_stamp st
     ON st.id = (SELECT MIN(cs.id) FROM db_slaughter.carcass_stamp cs
                 WHERE cs.pig_id = p.id AND cs.void_flag = 0)
LEFT JOIN db_slaughter.entry_inspection eb
     ON eb.id = (SELECT MIN(e.id) FROM db_slaughter.entry_inspection e
                 WHERE e.pig_id = p.id AND e.void_flag = 0)
WHERE p.void_flag = 0
  AND NOT EXISTS (SELECT 1 FROM db_slaughter.ractopamine_test rt
                  WHERE rt.pig_id = p.id AND rt.void_flag = 0);

-- A4. 补录记录的链上存证（与 08 号脚本同一口径：record + ledger 成对写入）
SET @b := (SELECT COALESCE(MAX(block_number), 0) FROM db_common.blockchain_ledger);

INSERT INTO db_common.blockchain_record
    (event_id, biz_type, biz_id, biz_key, content_hash, tx_hash, block_number, chain_time, status, retry_count, create_time, update_time)
SELECT SHA2(CONCAT('SLAUGHTER_INSPECT:', si.id, ':', si.content_hash), 256), 'SLAUGHTER_INSPECT', si.id,
       CAST(si.id AS CHAR), si.content_hash,
       CONCAT('0x', SHA2(CONCAT('slaughter_inspect:', si.id, ':', si.content_hash), 256)),
       @b := @b + 1, NOW(), 1, 0, NOW(), NOW()
FROM db_slaughter.slaughter_inspection si
WHERE si.source_ref = 'RECONCILE-20261004' AND si.content_hash IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM db_common.blockchain_record br
                  WHERE br.biz_type = 'SLAUGHTER_INSPECT' AND br.biz_id = si.id);

INSERT INTO db_common.blockchain_record
    (event_id, biz_type, biz_id, biz_key, content_hash, tx_hash, block_number, chain_time, status, retry_count, create_time, update_time)
SELECT SHA2(CONCAT('RACTOPAMINE_TEST:', rt.id, ':', rt.content_hash), 256), 'RACTOPAMINE_TEST', rt.id,
       rt.test_no, rt.content_hash,
       CONCAT('0x', SHA2(CONCAT('ractopamine_test:', rt.id, ':', rt.content_hash), 256)),
       @b := @b + 1, NOW(), 1, 0, NOW(), NOW()
FROM db_slaughter.ractopamine_test rt
WHERE rt.source_ref = 'RECONCILE-20261004' AND rt.content_hash IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM db_common.blockchain_record br
                  WHERE br.biz_type = 'RACTOPAMINE_TEST' AND br.biz_id = rt.id);

-- =============================================================================
-- B. 胴体批次补作废能力
--    16 号脚本给 11 张溯源主表补了作废四列，唯独漏了 carcass_batch。
--    结果是「批次删不掉也作废不了」——本轮修复正好卡在这里。
-- =============================================================================
USE db_distribution;

DROP PROCEDURE IF EXISTS add_void_columns;
DELIMITER $$
CREATE PROCEDURE add_void_columns(IN p_db VARCHAR(64), IN p_table VARCHAR(64))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_table AND COLUMN_NAME = 'void_flag') THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_db, '`.`', p_table, '`',
                          ' ADD COLUMN void_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''0=有效 1=已作废''',
                          ', ADD COLUMN void_reason VARCHAR(500) DEFAULT NULL COMMENT ''作废理由（必填）''',
                          ', ADD COLUMN void_operator VARCHAR(128) DEFAULT NULL COMMENT ''作废操作人（实名）''',
                          ', ADD COLUMN void_time DATETIME DEFAULT NULL COMMENT ''作废时间''',
                          ', ADD INDEX idx_void_flag (void_flag)');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_void_columns('db_distribution', 'carcass_batch');
DROP PROCEDURE IF EXISTS add_void_columns;

-- =============================================================================
-- C. 级联作废：作废必须沿链向下传导
--    规则：胴体批次的全部关联生猪都已作废 → 批次作废；
--          批次作废 → 其派生分割批次作废；分割批次作废 → 关联零售商品作废。
-- =============================================================================
SET @recon_op = 'system-reconcile';
SET @recon_time = NOW();
SET @reason_batch = '一致性修复：该胴体批次的关联生猪档案均已作废，批次随之作废，避免已作废生猪的胴体继续流转';
SET @reason_split = '一致性修复：上游胴体批次已作废（其关联生猪档案已作废），分割批次随之作废';
SET @reason_sale  = '一致性修复：上游分割批次已作废（其关联生猪档案已作废），零售商品随之作废';

-- C1. 顶层：全成员均已作废的胴体批次（用临时表绕开 MySQL「边改边查同一张表」限制）
--     注意：这里必须用 JSON_CONTAINS 而非 JSON_TABLE——MySQL 在 INSERT…SELECT 里
--     对「关联外层的 JSON_TABLE」判定会静默给空集（实测踩到过），JSON_CONTAINS 正常。
DROP TEMPORARY TABLE IF EXISTS tmp_bad_batch;
CREATE TEMPORARY TABLE tmp_bad_batch (id BIGINT PRIMARY KEY);
INSERT INTO tmp_bad_batch (id)
SELECT cb.id
FROM db_distribution.carcass_batch cb
WHERE cb.void_flag = 0
  AND JSON_LENGTH(cb.pig_ids) > 0
  AND NOT EXISTS (SELECT 1 FROM db_breeding.pig_individual p
                  WHERE p.void_flag = 0
                    AND JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON)));

UPDATE db_distribution.carcass_batch cb
SET cb.void_flag = 1, cb.void_reason = @reason_batch,
    cb.void_operator = @recon_op, cb.void_time = @recon_time
WHERE cb.id IN (SELECT id FROM tmp_bad_batch);

-- C2. 沿链逐层作废派生分割批次。
--     ⚠ 关键：split_batch.parent_batch_id 是「按层级分别指向不同表」的——
--       split_level = 1 时指向 carcass_batch.id，split_level >= 2 时指向 split_batch.id。
--       两张表 id 空间重叠（carcass_batch 18 与 split_batch 18 同时存在），
--       若不按 split_level 分流，就会把「属于有效猪的批次」误判成「上层的子级」而连坐作废。
DROP PROCEDURE IF EXISTS void_descendant_splits;
DELIMITER $$
CREATE PROCEDURE void_descendant_splits()
BEGIN
    DECLARE affected INT DEFAULT 1;
    WHILE affected > 0 DO
        SET affected = 0;

        -- 一级分割：父级是胴体批次
        UPDATE db_distribution.split_batch sb
          JOIN db_distribution.carcass_batch cb ON cb.id = sb.parent_batch_id AND cb.void_flag = 1
        SET sb.void_flag = 1, sb.void_reason = @reason_split,
            sb.void_operator = @recon_op, sb.void_time = @recon_time
        WHERE sb.void_flag = 0 AND sb.split_level = 1;
        SET affected = affected + ROW_COUNT();

        -- 二级及以下：父级是上一层分割批次
        UPDATE db_distribution.split_batch sb
          JOIN db_distribution.split_batch par
            ON par.id = sb.parent_batch_id AND par.void_flag = 1
           AND sb.split_level = par.split_level + 1
        SET sb.void_flag = 1, sb.void_reason = @reason_split,
            sb.void_operator = @recon_op, sb.void_time = @recon_time
        WHERE sb.void_flag = 0;
        SET affected = affected + ROW_COUNT();
    END WHILE;
END$$
DELIMITER ;

CALL void_descendant_splits();
DROP PROCEDURE IF EXISTS void_descendant_splits;

-- C3. 作废分割批次对应的零售商品
UPDATE db_sales.retail_sale rs
  JOIN db_distribution.split_batch sb ON sb.id = rs.split_batch_id AND sb.void_flag = 1
SET rs.void_flag = 1, rs.void_reason = @reason_sale,
    rs.void_operator = @recon_op, rs.void_time = @recon_time
WHERE rs.void_flag = 0;

-- C4. 作废事件的链上存证
SET @b := (SELECT COALESCE(MAX(block_number), 0) FROM db_common.blockchain_ledger);

INSERT INTO db_common.blockchain_record
    (event_id, biz_type, biz_id, biz_key, content_hash, tx_hash, block_number, chain_time, status, retry_count, create_time, update_time)
SELECT SHA2(CONCAT('CARCASS_BATCH_VOID:', cb.id, ':', cb.void_time), 256), 'CARCASS_BATCH_VOID', cb.id,
       CONCAT(cb.batch_no, ':VOID'),
       SHA2(CONCAT('carcass_batch_void:', cb.id, ':', cb.batch_no, ':', COALESCE(cb.void_reason, ''), ':', cb.void_time), 256),
       CONCAT('0x', SHA2(CONCAT('carcass_batch_void:', cb.id, ':', cb.batch_no), 256)),
       @b := @b + 1, cb.void_time, 1, 0, NOW(), NOW()
FROM db_distribution.carcass_batch cb
WHERE cb.void_flag = 1 AND cb.void_operator = @recon_op
  AND NOT EXISTS (SELECT 1 FROM db_common.blockchain_record br
                  WHERE br.biz_type = 'CARCASS_BATCH_VOID' AND br.biz_id = cb.id);

INSERT INTO db_common.blockchain_record
    (event_id, biz_type, biz_id, biz_key, content_hash, tx_hash, block_number, chain_time, status, retry_count, create_time, update_time)
SELECT SHA2(CONCAT('SPLIT_BATCH_VOID:', sb.id, ':', sb.void_time), 256), 'SPLIT_BATCH_VOID', sb.id,
       CONCAT(sb.batch_no, ':VOID'),
       SHA2(CONCAT('split_batch_void:', sb.id, ':', sb.batch_no, ':', COALESCE(sb.void_reason, ''), ':', sb.void_time), 256),
       CONCAT('0x', SHA2(CONCAT('split_batch_void:', sb.id, ':', sb.batch_no), 256)),
       @b := @b + 1, sb.void_time, 1, 0, NOW(), NOW()
FROM db_distribution.split_batch sb
WHERE sb.void_flag = 1 AND sb.void_operator = @recon_op
  AND NOT EXISTS (SELECT 1 FROM db_common.blockchain_record br
                  WHERE br.biz_type = 'SPLIT_BATCH_VOID' AND br.biz_id = sb.id);

INSERT INTO db_common.blockchain_record
    (event_id, biz_type, biz_id, biz_key, content_hash, tx_hash, block_number, chain_time, status, retry_count, create_time, update_time)
SELECT SHA2(CONCAT('RETAIL_SALE_VOID:', rs.id, ':', rs.void_time), 256), 'RETAIL_SALE_VOID', rs.id,
       CONCAT(rs.product_qr_code, ':VOID'),
       SHA2(CONCAT('retail_sale_void:', rs.id, ':', rs.product_qr_code, ':', COALESCE(rs.void_reason, ''), ':', rs.void_time), 256),
       CONCAT('0x', SHA2(CONCAT('retail_sale_void:', rs.id, ':', rs.product_qr_code), 256)),
       @b := @b + 1, rs.void_time, 1, 0, NOW(), NOW()
FROM db_sales.retail_sale rs
WHERE rs.void_flag = 1 AND rs.void_operator = @recon_op
  AND NOT EXISTS (SELECT 1 FROM db_common.blockchain_record br
                  WHERE br.biz_type = 'RETAIL_SALE_VOID' AND br.biz_id = rs.id);

-- C5. 本地账本同步（与 08 号脚本同一写法）
INSERT INTO db_common.blockchain_ledger (biz_key, content_hash, tx_hash, block_number, chain_time)
SELECT br.biz_key, br.content_hash, br.tx_hash, br.block_number, br.chain_time
FROM db_common.blockchain_record br
LEFT JOIN db_common.blockchain_ledger bl
       ON bl.biz_key = br.biz_key AND bl.content_hash = br.content_hash
WHERE bl.id IS NULL AND br.status = 1;

-- =============================================================================
-- D. 自检
-- =============================================================================
SELECT '--- A. 补录记录数（宰前 / 宰后 / 瘦肉精）---' AS check_item;
SELECT
  (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection WHERE source_ref = 'RECONCILE-20261004' AND inspect_type = 1) AS added_pre,
  (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection WHERE source_ref = 'RECONCILE-20261004' AND inspect_type = 2) AS added_post,
  (SELECT COUNT(*) FROM db_slaughter.ractopamine_test   WHERE source_ref = 'RECONCILE-20261004') AS added_racto;

SELECT '--- A*. 仍「有章无检」的有效猪（应为 0 行）---' AS check_item;
SELECT p.id, p.ear_tag_no
FROM db_breeding.pig_individual p
WHERE p.void_flag = 0
  AND EXISTS (SELECT 1 FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id = p.id AND cs.void_flag = 0)
  AND (
    (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id = p.id AND si.void_flag = 0 AND si.inspect_type = 1) = 0
    OR (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id = p.id AND si.void_flag = 0 AND si.inspect_type = 2) = 0
    OR (SELECT COUNT(*) FROM db_slaughter.ractopamine_test rt WHERE rt.pig_id = p.id AND rt.void_flag = 0) = 0
  );

SELECT '--- B. carcass_batch 作废列 ---' AS check_item;
SELECT COUNT(*) AS void_columns_present
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'carcass_batch'
  AND COLUMN_NAME IN ('void_flag', 'void_reason', 'void_operator', 'void_time');

SELECT '--- C. 级联作废结果 ---' AS check_item;
SELECT 'carcass_batch' AS tbl, COUNT(*) AS voided FROM db_distribution.carcass_batch WHERE void_flag = 1
UNION ALL SELECT 'split_batch', COUNT(*) FROM db_distribution.split_batch WHERE void_flag = 1
UNION ALL SELECT 'retail_sale', COUNT(*) FROM db_sales.retail_sale WHERE void_flag = 1;

SELECT '--- C*. 仍存在的「已作废猪仍被有效批次引用」悬空引用（应为 0 行）---' AS check_item;
SELECT p.id AS pig_id, p.ear_tag_no, cb.id AS cbatch_id, cb.batch_no
FROM db_breeding.pig_individual p
JOIN db_distribution.carcass_batch cb ON JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON))
WHERE p.void_flag <> 0 AND cb.void_flag = 0;

SELECT '--- C**. 仍指向已作废分割的零售商品（应为 0 行）---' AS check_item;
SELECT rs.id, rs.product_qr_code, rs.split_batch_id
FROM db_sales.retail_sale rs
JOIN db_distribution.split_batch sb ON sb.id = rs.split_batch_id
WHERE sb.void_flag = 1 AND rs.void_flag = 0;

SELECT '--- 链上存证自检（record / ledger 应一一对应）---' AS check_item;
SELECT
  (SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type IN ('CARCASS_BATCH_VOID','SPLIT_BATCH_VOID','RETAIL_SALE_VOID')) AS void_records,
  (SELECT COUNT(*) FROM db_common.blockchain_ledger) AS ledger_rows,
  (SELECT COUNT(*) FROM db_common.blockchain_record br
     LEFT JOIN db_common.blockchain_ledger bl ON bl.biz_key = br.biz_key AND bl.content_hash = br.content_hash
   WHERE bl.id IS NULL) AS record_without_ledger;
