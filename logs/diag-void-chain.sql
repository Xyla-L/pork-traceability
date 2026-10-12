SET NAMES utf8mb4;
SELECT id, biz_type, biz_id, biz_key, block_number, status, chain_time
FROM db_common.blockchain_record WHERE biz_type LIKE '%VOID' ORDER BY biz_type, id;
SELECT COUNT(*) AS slaught_inspect_rows FROM db_slaughter.slaughter_inspection;
SELECT COUNT(*) AS stamp_rows FROM db_slaughter.carcass_stamp;
SELECT COUNT(*) AS racto_rows FROM db_slaughter.ractopamine_test;
SELECT COUNT(*) AS entry_rows FROM db_slaughter.entry_inspection;
