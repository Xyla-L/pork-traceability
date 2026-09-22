SELECT device_no,
       CHAR_LENGTH(device_name) AS name_chars,
       LENGTH(device_name)      AS name_bytes,
       HEX(device_name)         AS name_hex
FROM db_ingest.ingest_device
ORDER BY id;

SELECT device_no,
       CHAR_LENGTH(location) AS loc_chars,
       HEX(location)         AS loc_hex
FROM db_ingest.ingest_device
ORDER BY id;
