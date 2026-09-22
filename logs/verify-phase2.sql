SELECT 'col_pig' k, COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_breeding' AND TABLE_NAME='pig_individual' AND COLUMN_NAME='data_source';
SELECT 'col_vac' k, COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_breeding' AND TABLE_NAME='vaccine_record' AND COLUMN_NAME='source';
SELECT 'col_insp' k, COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_slaughter' AND TABLE_NAME='slaughter_inspection' AND COLUMN_NAME='source';
SELECT 'col_stamp' k, COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_slaughter' AND TABLE_NAME='carcass_stamp' AND COLUMN_NAME='source';
SELECT 'col_split' k, COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_distribution' AND TABLE_NAME='split_batch' AND COLUMN_NAME='source';
SELECT device_no, device_name, channel, status FROM db_ingest.ingest_device WHERE channel IN ('TAG','VACCINE','INSPECTION','STAMP','SPLIT');
