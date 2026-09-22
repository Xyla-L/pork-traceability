SELECT id, pig_id, vaccine_name, batch_no, source, source_ref, operator FROM db_breeding.vaccine_record ORDER BY id DESC LIMIT 5;
SELECT id, ear_tag_no, data_source, source_ref FROM db_breeding.pig_individual ORDER BY id DESC LIMIT 3;
