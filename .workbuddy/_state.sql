SELECT '--- transport status dist ---' AS s;
SELECT status, COUNT(*) c FROM db_distribution.cold_chain_transport GROUP BY status;
SELECT '--- 待发车但已有签收单（状态不一致） ---' AS s;
SELECT t.id, t.transport_no, t.status, r.id AS receipt_id
FROM db_distribution.cold_chain_transport t
JOIN db_distribution.store_receipt r ON r.transport_id = t.id
ORDER BY t.id;
SELECT '--- 待发车且未签收（可用于签收演示） ---' AS s;
SELECT t.id, t.transport_no, t.status FROM db_distribution.cold_chain_transport t
LEFT JOIN db_distribution.store_receipt r ON r.transport_id = t.id
WHERE t.status = 1 AND r.id IS NULL ORDER BY t.id;
SELECT '--- source 分布 ---' AS s;
SELECT 'temperature_log' t, source, COUNT(*) c FROM db_distribution.temperature_log GROUP BY source
UNION ALL SELECT 'store_receipt', source, COUNT(*) FROM db_distribution.store_receipt GROUP BY source
UNION ALL SELECT 'entry_inspection', source, COUNT(*) FROM db_slaughter.entry_inspection GROUP BY source
UNION ALL SELECT 'ractopamine_test', source, COUNT(*) FROM db_slaughter.ractopamine_test GROUP BY source
UNION ALL SELECT 'retail_sale', source, COUNT(*) FROM db_sales.retail_sale GROUP BY source;
