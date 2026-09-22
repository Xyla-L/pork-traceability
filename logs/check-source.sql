SELECT 'temperature_log' AS biz_table, source, COUNT(*) AS cnt FROM db_distribution.temperature_log GROUP BY source
UNION ALL SELECT 'store_receipt', source, COUNT(*) FROM db_distribution.store_receipt GROUP BY source
UNION ALL SELECT 'entry_inspection', source, COUNT(*) FROM db_slaughter.entry_inspection GROUP BY source
UNION ALL SELECT 'ractopamine_test', source, COUNT(*) FROM db_slaughter.ractopamine_test GROUP BY source
UNION ALL SELECT 'retail_sale', source, COUNT(*) FROM db_sales.retail_sale GROUP BY source;

SELECT status, COUNT(*) AS cnt FROM db_ingest.ingest_staging GROUP BY status ORDER BY status;
