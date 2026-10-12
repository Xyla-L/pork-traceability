SET NAMES utf8mb4;

SELECT '=== A. 全部猪档案（含作废）===' AS section;
SELECT id, CONCAT('[', ear_tag_no, ']') AS tag, CHAR_LENGTH(ear_tag_no) AS len, breed, birth_date, status, data_source, deleted, void_flag, void_operator, void_reason
FROM db_breeding.pig_individual ORDER BY id;

SELECT '=== B. 全部胴体批次及成员有效性 ===' AS section;
SELECT cb.id, cb.batch_no, cb.pig_ids, cb.create_time,
       (SELECT COUNT(*) FROM JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) j) AS member_cnt,
       (SELECT COUNT(*) FROM JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) j
          JOIN db_breeding.pig_individual p ON p.id = j.pid WHERE p.void_flag = 0) AS valid_cnt,
       (SELECT COUNT(*) FROM JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) j
          LEFT JOIN db_breeding.pig_individual p ON p.id = j.pid WHERE p.id IS NULL) AS missing_cnt
FROM db_distribution.carcass_batch cb ORDER BY cb.id;

SELECT '=== C. 有盖章但无检验的猪（含 void 细节）===' AS section;
SELECT p.id, p.ear_tag_no, p.status,
       (SELECT GROUP_CONCAT(CONCAT('id=', cs.id, '/status=', cs.status, '/void=', cs.void_flag, '/no=', cs.stamp_no)) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id = p.id) AS stamps
FROM db_breeding.pig_individual p
WHERE p.void_flag = 0
  AND EXISTS (SELECT 1 FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id = p.id AND cs.void_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM db_slaughter.slaughter_inspection si WHERE si.pig_id = p.id AND si.void_flag = 0)
ORDER BY p.id;

SELECT '=== D. 采样：现有检验记录格式 ===' AS section;
SELECT id, pig_id, inspect_no, batch_no, ear_tag_no, inspect_type, result, status, conclusion, veterinary, license_no, source
FROM db_slaughter.slaughter_inspection WHERE pig_id IN (5,11) ORDER BY pig_id, inspect_type;

SELECT '=== E. 采样：现有印章记录 ===' AS section;
SELECT id, pig_id, batch_no, ear_tag_no, stamp_no, carcass_no, stamp_type, status, source
FROM db_slaughter.carcass_stamp WHERE pig_id IN (4,10,12,16) ORDER BY pig_id;
