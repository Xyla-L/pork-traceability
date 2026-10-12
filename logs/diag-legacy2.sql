SET NAMES utf8mb4;

SELECT '=== 2. id=18 猪档案 ===' AS section;
SELECT id, ear_tag_no, breed, birth_date, status, data_source, deleted, void_flag, void_reason, void_operator, void_time
FROM db_breeding.pig_individual WHERE id IN (17,18);

SELECT '=== 2b. 引用 id=18 的胴体批次 ===' AS section;
SELECT cb.id, cb.batch_no, cb.pig_ids, cb.slaughterhouse, cb.create_time, cb.operator, cb.note
FROM db_distribution.carcass_batch cb,
     JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) jt
WHERE jt.pid = 18;

SELECT '=== 2c. 该批次的完整成员及各自作废态 ===' AS section;
SELECT cb.batch_no, cb.id AS cbatch_id, cb.pig_ids, jt.pid,
       p.ear_tag_no, p.status AS pig_status, p.deleted, p.void_flag, p.void_reason
FROM db_distribution.carcass_batch cb,
     JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) jt
LEFT JOIN db_breeding.pig_individual p ON p.id = jt.pid
WHERE cb.batch_no = 'CB20260916956996';

SELECT '=== 2d. 全部「被有效胴体批次引用、但猪档案已作废」的悬空引用 ===' AS section;
SELECT p.id AS pig_id, p.ear_tag_no, p.void_flag AS pig_void, cb.id AS cbatch_id, cb.batch_no, cb.pig_ids
FROM db_breeding.pig_individual p
JOIN db_distribution.carcass_batch cb ON JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON))
WHERE p.void_flag <> 0
ORDER BY p.id, cb.id;

SELECT '=== 2e. 该批次下游（分割批次）===' AS section;
SELECT id, batch_no, cbatch_id FROM db_distribution.split_batch WHERE cbatch_id = (
  SELECT cb.id FROM db_distribution.carcass_batch cb,
       JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) jt WHERE jt.pid = 18 LIMIT 1);
