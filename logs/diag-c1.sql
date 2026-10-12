SET NAMES utf8mb4;
USE db_distribution;

SELECT '--- 直接跑判定子查询 ---' AS s;
SELECT cb.id, cb.batch_no, cb.pig_ids, JSON_LENGTH(cb.pig_ids) AS jlen,
       (SELECT COUNT(*) FROM JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) j
          JOIN db_breeding.pig_individual p ON p.id = j.pid WHERE p.void_flag = 0) AS still_valid
FROM carcass_batch cb WHERE cb.id = 17;

SELECT '--- 不带 JSON_TABLE 的等价判定 ---' AS s;
SELECT cb.id, cb.batch_no
FROM carcass_batch cb
WHERE cb.void_flag = 0 AND JSON_LENGTH(cb.pig_ids) > 0
  AND NOT EXISTS (
      SELECT 1 FROM db_breeding.pig_individual p
      WHERE p.void_flag = 0 AND JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON)));

DROP TEMPORARY TABLE IF EXISTS tmp_probe;
CREATE TEMPORARY TABLE tmp_probe (id BIGINT PRIMARY KEY);
INSERT INTO tmp_probe (id)
SELECT cb.id FROM carcass_batch cb
WHERE cb.void_flag = 0 AND JSON_LENGTH(cb.pig_ids) > 0
  AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(cb.pig_ids, '$[*]' COLUMNS(pid INT PATH '$')) j
                  JOIN db_breeding.pig_individual p ON p.id = j.pid WHERE p.void_flag = 0);
SELECT COUNT(*) AS tmp_after_json_table FROM tmp_probe;
