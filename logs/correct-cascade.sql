-- 一次性纠正：撤销上一轮「把 split_level=1 的父级误当成 split_batch」造成的连坐作废。
-- 保留：carcass_batch 17、split_batch 18、以及引用 split_batch 18 的零售商品。
SET NAMES utf8mb4;

SELECT '--- 撤销前 ---' AS stage;
SELECT (SELECT COUNT(*) FROM db_distribution.split_batch WHERE void_flag=1) AS voided_splits,
       (SELECT COUNT(*) FROM db_sales.retail_sale WHERE void_flag=1) AS voided_retail,
       (SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type IN ('SPLIT_BATCH_VOID','RETAIL_SALE_VOID')) AS void_chain_records;

UPDATE db_distribution.split_batch
SET void_flag = 0, void_reason = NULL, void_operator = NULL, void_time = NULL
WHERE void_operator = 'system-reconcile' AND id <> 18;

UPDATE db_sales.retail_sale
SET void_flag = 0, void_reason = NULL, void_operator = NULL, void_time = NULL
WHERE void_operator = 'system-reconcile' AND split_batch_id <> 18;

DELETE FROM db_common.blockchain_record WHERE biz_type = 'SPLIT_BATCH_VOID' AND biz_id <> 18;
DELETE FROM db_common.blockchain_record
WHERE biz_type = 'RETAIL_SALE_VOID'
  AND biz_id NOT IN (SELECT id FROM db_sales.retail_sale WHERE split_batch_id = 18);

DELETE FROM db_common.blockchain_ledger
WHERE biz_key LIKE '%:VOID'
  AND (biz_key, content_hash) NOT IN (SELECT biz_key, content_hash FROM db_common.blockchain_record);

SELECT '--- 撤销后 ---' AS stage;
SELECT (SELECT COUNT(*) FROM db_distribution.split_batch WHERE void_flag=1) AS voided_splits,
       (SELECT COUNT(*) FROM db_sales.retail_sale WHERE void_flag=1) AS voided_retail,
       (SELECT COUNT(*) FROM db_common.blockchain_record WHERE biz_type IN ('SPLIT_BATCH_VOID','RETAIL_SALE_VOID')) AS void_chain_records;
