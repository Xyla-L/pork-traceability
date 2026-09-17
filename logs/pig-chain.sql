SELECT id, ear_tag_no, status FROM db_breeding.pig_individual WHERE ear_tag_no = 'ET-TEST-0016';
SELECT id, inspect_no, inspect_type, status, inspect_time FROM db_slaughter.slaughter_inspection WHERE pig_id = (SELECT id FROM db_breeding.pig_individual WHERE ear_tag_no = 'ET-TEST-0016');
SELECT id, stamp_no, stamp_type, stamp_time FROM db_slaughter.carcass_stamp WHERE pig_id = (SELECT id FROM db_breeding.pig_individual WHERE ear_tag_no = 'ET-TEST-0016');
