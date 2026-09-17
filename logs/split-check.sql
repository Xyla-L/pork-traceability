SELECT p.id, p.ear_tag_no, s.inspect_type, s.result, s.status
FROM db_slaughter.slaughter_inspection s
JOIN db_breeding.pig_individual p ON p.id = s.pig_id
WHERE s.pig_id = 20 ORDER BY s.inspect_type;
