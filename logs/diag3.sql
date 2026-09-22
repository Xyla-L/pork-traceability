SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA LIKE 'db\_%'
  AND DATA_TYPE IN ('varchar','text','longtext','char')
ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION;

SELECT 'staging.error_msg' AS col, HEX(error_msg) AS hex, CHAR_LENGTH(error_msg) AS chars
FROM db_ingest.ingest_staging WHERE error_msg IS NOT NULL AND error_msg <> '' LIMIT 3;
