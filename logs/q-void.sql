USE db_slaughter;
SELECT '--- 已作废的瘦肉精记录 ---' AS t;
SELECT id, pig_id, test_no, void_reason, void_operator FROM ractopamine_test WHERE void_flag = 1 ORDER BY id DESC LIMIT 5;
SELECT '--- 该猪所属胴体批次 ---' AS t;
SELECT r.pig_id, c.id AS carcass_id, c.batch_no FROM ractopamine_test r
  LEFT JOIN db_distribution.carcass_batch c ON c.id = r.pig_id
 WHERE r.void_flag = 1 ORDER BY r.id DESC LIMIT 5;
SELECT '--- 胴体批次表结构 ---' AS t;
SHOW COLUMNS FROM db_distribution.carcass_batch;
