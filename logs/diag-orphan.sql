SET NAMES utf8mb4;

SELECT '=== I. 全库悬空引用扫描（指向已作废/不存在的上游）===' AS section;

SELECT 'split_batch.parent -> carcass_batch' AS chk, COUNT(*) AS bad
FROM db_distribution.split_batch sb
LEFT JOIN db_distribution.carcass_batch cb ON cb.id = sb.parent_batch_id
WHERE sb.split_level = 1 AND (cb.id IS NULL OR EXISTS (
   SELECT 1 FROM JSON_TABLE(cb.pig_ids,'$[*]' COLUMNS(pid INT PATH '$')) j
   LEFT JOIN db_breeding.pig_individual p ON p.id=j.pid
   WHERE p.id IS NULL OR p.void_flag <> 0))
UNION ALL
SELECT 'split_batch.parent -> split_batch(作废)', COUNT(*)
FROM db_distribution.split_batch sb JOIN db_distribution.split_batch par ON par.id = sb.parent_batch_id
WHERE sb.split_level = 2 AND par.void_flag <> 0
UNION ALL
SELECT 'transport.split_batch 已作废', COUNT(*)
FROM db_distribution.cold_chain_transport ct JOIN db_distribution.split_batch sb ON sb.id = ct.split_batch_id
WHERE sb.void_flag <> 0
UNION ALL
SELECT 'receipt.transport 已作废', COUNT(*)
FROM db_distribution.store_receipt sr JOIN db_distribution.cold_chain_transport ct ON ct.id = sr.transport_id
WHERE ct.void_flag <> 0
UNION ALL
SELECT 'temperature.transport 已作废', COUNT(*)
FROM db_distribution.temperature_log tl JOIN db_distribution.cold_chain_transport ct ON ct.id = tl.transport_id
WHERE ct.void_flag <> 0
UNION ALL
SELECT 'retail.split_batch 已作废', COUNT(*)
FROM db_sales.retail_sale rs JOIN db_distribution.split_batch sb ON sb.id = rs.split_batch_id
WHERE sb.void_flag <> 0
UNION ALL
SELECT 'retail.transport 已作废', COUNT(*)
FROM db_sales.retail_sale rs JOIN db_distribution.cold_chain_transport ct ON ct.id = rs.transport_id
WHERE ct.void_flag <> 0;

SELECT '=== J. 有效猪的屠宰链路缺口（简明）===' AS section;
SELECT p.id, p.ear_tag_no,
       (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=1) AS pre,
       (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=2) AS post,
       (SELECT COUNT(*) FROM db_slaughter.ractopamine_test rt WHERE rt.pig_id=p.id AND rt.void_flag=0) AS racto,
       (SELECT COALESCE(MAX(cs.status),-1) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id=p.id AND cs.void_flag=0) AS stamp_status
FROM db_breeding.pig_individual p
WHERE p.void_flag=0 AND (
  (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=1)=0
  OR (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=2)=0
  OR (SELECT COUNT(*) FROM db_slaughter.ractopamine_test rt WHERE rt.pig_id=p.id AND rt.void_flag=0)=0)
ORDER BY p.id;

SELECT '=== K. 级联作废闭包预估：从已作废猪出发的下游 ===' AS section;
SELECT 'carcass_batch(全成员已作废)' AS t, COUNT(*) AS cnt FROM db_distribution.carcass_batch cb
WHERE (SELECT COUNT(*) FROM JSON_TABLE(cb.pig_ids,'$[*]' COLUMNS(pid INT PATH '$')) j) > 0
  AND (SELECT COUNT(*) FROM JSON_TABLE(cb.pig_ids,'$[*]' COLUMNS(pid INT PATH '$')) j
         LEFT JOIN db_breeding.pig_individual p ON p.id=j.pid WHERE p.void_flag=0) = 0;

SELECT '=== L. 已作废猪的下游统计 ===' AS section;
SELECT p.id AS pig_id, p.ear_tag_no, cb.id AS cbatch_id, cb.batch_no,
       (SELECT COUNT(*) FROM db_distribution.split_batch sb WHERE sb.parent_batch_id = cb.id AND sb.split_level=1) AS lv1_splits
FROM db_breeding.pig_individual p
JOIN db_distribution.carcass_batch cb ON JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON))
WHERE p.void_flag <> 0;
