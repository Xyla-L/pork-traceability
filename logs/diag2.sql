SELECT device_no, CHAR_LENGTH(remark) AS chars, HEX(remark) AS hex FROM db_ingest.ingest_device ORDER BY id;

SHOW COLUMNS FROM db_ingest.ingest_staging;

SELECT COUNT(*) AS staging_rows, MAX(CHAR_LENGTH(CAST(data_json AS CHAR))) AS max_json_len FROM db_ingest.ingest_staging;

SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='db_ingest' AND TABLE_NAME='ingest_staging'
  AND DATA_TYPE IN ('varchar','text','longtext','json');
