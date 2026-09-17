SELECT p.ear_tag_no, p.status FROM db_breeding.pig_individual p WHERE p.ear_tag_no = 'ET-TEST-0016';
SELECT COUNT(*) AS stale FROM db_breeding.pig_individual p
JOIN db_slaughter.slaughter_inspection s ON s.pig_id = p.id AND s.inspect_type >= 2
WHERE p.status IS NULL OR p.status < 3;
