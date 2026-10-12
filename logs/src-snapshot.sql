SELECT 'temperature_log' AS stage, source, COUNT(*) AS cnt FROM db_distribution.temperature_log GROUP BY source
UNION ALL SELECT 'store_receipt', source, COUNT(*) FROM db_distribution.store_receipt GROUP BY source
UNION ALL SELECT 'retail_sale', source, COUNT(*) FROM db_sales.retail_sale GROUP BY source
UNION ALL SELECT 'slaughter_inspection', source, COUNT(*) FROM db_slaughter.slaughter_inspection GROUP BY source
UNION ALL SELECT 'carcass_stamp', source, COUNT(*) FROM db_slaughter.carcass_stamp GROUP BY source
UNION ALL SELECT 'ractopamine_test', source, COUNT(*) FROM db_slaughter.ractopamine_test GROUP BY source
UNION ALL SELECT 'entry_inspection', source, COUNT(*) FROM db_slaughter.entry_inspection GROUP BY source
UNION ALL SELECT 'vaccine_record', source, COUNT(*) FROM db_breeding.vaccine_record GROUP BY source
UNION ALL SELECT 'split_batch', source, COUNT(*) FROM db_distribution.split_batch GROUP BY source
UNION ALL SELECT 'pig_individual', data_source, COUNT(*) FROM db_breeding.pig_individual GROUP BY data_source;
