SET NAMES utf8mb4;

SELECT '========== 1. 有效猪的屠宰链路完整性（每头都应有 查验/宰前/宰后/瘦肉精/盖章）==========' AS section;
SELECT p.id, p.ear_tag_no, p.status,
       (SELECT COUNT(*) FROM db_slaughter.entry_inspection e WHERE e.pig_id=p.id AND e.void_flag=0) AS entry,
       (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=1) AS pre,
       (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=2) AS post,
       (SELECT COUNT(*) FROM db_slaughter.ractopamine_test rt WHERE rt.pig_id=p.id AND rt.void_flag=0) AS racto,
       (SELECT COUNT(*) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id=p.id AND cs.void_flag=0) AS stamp,
       (SELECT COUNT(*) FROM db_breeding.vaccine_record v WHERE v.pig_id=p.id AND v.void_flag=0) AS vaccine
FROM db_breeding.pig_individual p
WHERE p.void_flag=0
ORDER BY p.id;

SELECT '---------- 违规行（应为 0 行）：有效猪缺任一环节 ----------' AS section;
SELECT p.id, p.ear_tag_no FROM db_breeding.pig_individual p
WHERE p.void_flag=0 AND (
  (SELECT COUNT(*) FROM db_slaughter.entry_inspection e WHERE e.pig_id=p.id AND e.void_flag=0)=0
  OR (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=1)=0
  OR (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=2)=0
  OR (SELECT COUNT(*) FROM db_slaughter.ractopamine_test rt WHERE rt.pig_id=p.id AND rt.void_flag=0)=0
  OR (SELECT COUNT(*) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id=p.id AND cs.void_flag=0)=0);

SELECT '========== 2. 悬空引用扫描（应全为 0）==========' AS section;
SELECT '批次引用不存在的猪' AS chk, COUNT(*) AS bad FROM db_distribution.carcass_batch cb,
       JSON_TABLE(cb.pig_ids,'$[*]' COLUMNS(pid INT PATH '$')) j
       LEFT JOIN db_breeding.pig_individual p ON p.id=j.pid WHERE p.id IS NULL AND cb.void_flag=0
UNION ALL SELECT '有效批次引用已作废的猪', COUNT(*) FROM db_distribution.carcass_batch cb,
       JSON_TABLE(cb.pig_ids,'$[*]' COLUMNS(pid INT PATH '$')) j
       JOIN db_breeding.pig_individual p ON p.id=j.pid WHERE p.void_flag<>0 AND cb.void_flag=0
UNION ALL SELECT '有效分割的父批次无效/不存在', COUNT(*) FROM db_distribution.split_batch sb
       LEFT JOIN db_distribution.carcass_batch cb ON cb.id=sb.parent_batch_id AND cb.void_flag=0
       WHERE sb.void_flag=0 AND sb.split_level=1 AND cb.id IS NULL
UNION ALL SELECT '有效分割的父分割已作废', COUNT(*) FROM db_distribution.split_batch sb
       JOIN db_distribution.split_batch par ON par.id=sb.parent_batch_id
       WHERE sb.void_flag=0 AND sb.split_level>=2 AND par.void_flag<>0
UNION ALL SELECT '有效运单指向已作废分割', COUNT(*) FROM db_distribution.cold_chain_transport ct
       JOIN db_distribution.split_batch sb ON sb.id=ct.split_batch_id WHERE sb.void_flag<>0
UNION ALL SELECT '有效签收指向已作废运单', 0
UNION ALL SELECT '有效零售指向已作废分割', COUNT(*) FROM db_sales.retail_sale rs
       JOIN db_distribution.split_batch sb ON sb.id=rs.split_batch_id WHERE sb.void_flag<>0 AND rs.void_flag=0
UNION ALL SELECT '有效零售指向不存在分割', COUNT(*) FROM db_sales.retail_sale rs
       LEFT JOIN db_distribution.split_batch sb ON sb.id=rs.split_batch_id
       WHERE sb.id IS NULL AND rs.void_flag=0;

SELECT '========== 3. 作废传导：已作废猪 → 下游是否全部作废 ==========' AS section;
SELECT p.id AS pig_id, p.ear_tag_no, cb.id AS cbatch_id, cb.batch_no, cb.void_flag AS cbatch_void,
       (SELECT COUNT(*) FROM db_distribution.split_batch sb WHERE sb.parent_batch_id=cb.id AND sb.void_flag=0) AS live_child_splits,
       (SELECT COUNT(*) FROM db_sales.retail_sale rs JOIN db_distribution.split_batch sb ON sb.id=rs.split_batch_id
          WHERE sb.parent_batch_id=cb.id AND rs.void_flag=0) AS live_retail
FROM db_breeding.pig_individual p
JOIN db_distribution.carcass_batch cb ON JSON_CONTAINS(cb.pig_ids, CAST(p.id AS JSON))
WHERE p.void_flag<>0;

SELECT '========== 4. 数据规模汇总 ==========' AS section;
SELECT '有效生猪档案' AS item, COUNT(*) AS cnt FROM db_breeding.pig_individual WHERE void_flag=0
UNION ALL SELECT '已作废生猪档案', COUNT(*) FROM db_breeding.pig_individual WHERE void_flag<>0
UNION ALL SELECT '有效胴体批次', COUNT(*) FROM db_distribution.carcass_batch WHERE void_flag=0
UNION ALL SELECT '已作废胴体批次', COUNT(*) FROM db_distribution.carcass_batch WHERE void_flag<>0
UNION ALL SELECT '有效分割批次', COUNT(*) FROM db_distribution.split_batch WHERE void_flag=0
UNION ALL SELECT '已作废分割批次', COUNT(*) FROM db_distribution.split_batch WHERE void_flag<>0
UNION ALL SELECT '有效零售商品', COUNT(*) FROM db_sales.retail_sale WHERE void_flag=0
UNION ALL SELECT '已作废零售商品', COUNT(*) FROM db_sales.retail_sale WHERE void_flag<>0
UNION ALL SELECT '有效屠宰检验', COUNT(*) FROM db_slaughter.slaughter_inspection WHERE void_flag=0
UNION ALL SELECT '有效瘦肉精检测', COUNT(*) FROM db_slaughter.ractopamine_test WHERE void_flag=0
UNION ALL SELECT '有效盖章', COUNT(*) FROM db_slaughter.carcass_stamp WHERE void_flag=0;

SELECT '========== 5. 上链存证一致性 ==========' AS section;
SELECT (SELECT COUNT(*) FROM db_common.blockchain_record) AS chain_records,
       (SELECT COUNT(*) FROM db_common.blockchain_ledger) AS ledger_rows,
       (SELECT COUNT(*) FROM db_common.blockchain_record br
          LEFT JOIN db_common.blockchain_ledger bl ON bl.biz_key=br.biz_key AND bl.content_hash=br.content_hash
        WHERE bl.id IS NULL) AS record_without_ledger,
       (SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type IN
          ('CARCASS_BATCH_VOID','SPLIT_BATCH_VOID','RETAIL_SALE_VOID')) AS reconcile_void_events;
