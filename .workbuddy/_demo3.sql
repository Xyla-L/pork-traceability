SELECT '--- split_batch cols ---' AS s;
SELECT column_name FROM information_schema.columns WHERE table_schema='db_distribution' AND table_name='split_batch' ORDER BY ordinal_position;
SELECT '--- transport status dist ---' AS s;
SELECT status, COUNT(*) c FROM db_distribution.cold_chain_transport GROUP BY status;
SELECT '--- retail status dist ---' AS s;
SELECT status, is_activated, COUNT(*) c FROM db_sales.retail_sale GROUP BY status, is_activated;
SELECT '--- receipt source dist ---' AS s;
SELECT DISTINCT store_name FROM db_distribution.store_receipt ORDER BY id DESC LIMIT 5;
