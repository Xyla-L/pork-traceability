SELECT '--- db_distribution tables ---' AS s;
SHOW TABLES FROM db_distribution;
SELECT '--- split_batch ---' AS s;
SELECT id, batch_no, status FROM db_distribution.split_batch ORDER BY id DESC LIMIT 5;
SELECT '--- transport status dist ---' AS s;
SELECT status, COUNT(*) c FROM db_distribution.cold_chain_transport GROUP BY status;
SELECT '--- retail status dist ---' AS s;
SELECT status, is_activated, COUNT(*) c FROM db_sales.retail_sale GROUP BY status, is_activated;
