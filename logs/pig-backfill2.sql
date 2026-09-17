UPDATE db_breeding.pig_individual p
JOIN db_distribution.carcass_batch cb ON JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON))
SET p.status = 3
WHERE p.status IS NULL OR p.status < 3;
SELECT p.ear_tag_no, p.status FROM db_breeding.pig_individual p WHERE p.ear_tag_no = 'ET-TEST-0016';
SELECT COUNT(*) AS still_stale FROM db_breeding.pig_individual p
JOIN db_distribution.carcass_batch cb ON JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON))
WHERE p.status IS NULL OR p.status < 3;
