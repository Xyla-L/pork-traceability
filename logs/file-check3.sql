SHOW TABLES FROM db_common;
SELECT file_id, original_name, biz_ref, create_time FROM db_common.file_metadata ORDER BY create_time DESC LIMIT 6;
