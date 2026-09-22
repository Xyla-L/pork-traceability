SELECT device_no, channel, target_table, status, CHAR_LENGTH(device_name) AS name_ok
FROM db_ingest.ingest_device ORDER BY channel, device_no;

SELECT channel, COUNT(*) AS devices FROM db_ingest.ingest_device GROUP BY channel ORDER BY channel;
