SELECT '--- 含 pig 29 的胴体批次 ---' AS t;
SELECT id, batch_no, pig_ids FROM db_distribution.carcass_batch
 WHERE JSON_CONTAINS(pig_ids, '29') LIMIT 10;
SELECT '--- 所有胴体批次 ---' AS t;
SELECT id, batch_no, pig_ids FROM db_distribution.carcass_batch LIMIT 10;
