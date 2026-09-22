SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_NAME IN ('temperature_log','entry_inspection','ractopamine_test','store_receipt','retail_sale','ingest_staging','ingest_device','retail_product')
ORDER BY TABLE_NAME, TABLE_SCHEMA;
