-- 遗留问题核查：ET-TEST 缺检验 / id=18 悬空引用
SET NAMES utf8mb4;

SELECT '=== 1. ET-TEST 猪：盖章 vs 检验对照 ===' AS section;
SELECT p.id, p.ear_tag_no, p.status, p.void_flag,
       (SELECT COUNT(*) FROM db_slaughter.entry_inspection e WHERE e.pig_id = p.id AND e.void_flag = 0) AS entry_cnt,
       (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id = p.id AND si.void_flag = 0) AS inspection_cnt,
       (SELECT COUNT(*) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id = p.id AND cs.void_flag = 0) AS stamp_cnt
FROM db_breeding.pig_individual p
WHERE p.ear_tag_no LIKE 'ET-TEST%'
ORDER BY p.id;

SELECT '=== 1b. 全部「有盖章无检验」的猪（不限前缀）===' AS section;
SELECT p.id, p.ear_tag_no, p.status, p.void_flag,
       (SELECT COUNT(*) FROM db_slaughter.entry_inspection e WHERE e.pig_id = p.id AND e.void_flag = 0) AS entry_cnt,
       (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id = p.id AND si.void_flag = 0) AS inspection_cnt,
       (SELECT COUNT(*) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id = p.id AND cs.void_flag = 0) AS stamp_cnt
FROM db_breeding.pig_individual p
WHERE p.void_flag = 0
  AND EXISTS (SELECT 1 FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id = p.id AND cs.void_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM db_slaughter.slaughter_inspection si WHERE si.pig_id = p.id AND si.void_flag = 0)
ORDER BY p.id;

SELECT '=== 2. id=18 猪档案 ===' AS section;
SELECT id, ear_tag_no, status, void_flag, void_reason, void_by
FROM db_breeding.pig_individual WHERE id = 18;

SELECT '=== 2b. 引用 id=18 的胴体批次 ===' AS section;
SELECT cb.id, cb.batch_no, cb.pig_ids, cb.slaughter_date, cb.void_flag
FROM db_distribution.carcass_batch cb,
     JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) jt
WHERE jt.pid = 18;

SELECT '=== 2c. 所有「被有效胴体批次引用、但猪档案已作废」的悬空引用 ===' AS section;
SELECT p.id AS pig_id, p.ear_tag_no, p.void_flag AS pig_void,
       cb.id AS cbatch_id, cb.batch_no, cb.void_flag AS cb_void
FROM db_breeding.pig_individual p
JOIN db_distribution.carcass_batch cb
  ON JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON))
WHERE p.void_flag <> 0
ORDER BY p.id, cb.id;

SELECT '=== 2d. 该胴体批次下属分割/温度/运单 ===' AS section;
SELECT 'split_batch' AS t, id, batch_no, cbatch_id, void_flag FROM db_distribution.split_batch WHERE cbatch_id IN (
  SELECT cb.id FROM db_distribution.carcass_batch cb,
    JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) jt WHERE jt.pid = 18);
