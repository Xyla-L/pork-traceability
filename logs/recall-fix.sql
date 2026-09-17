UPDATE db_sales.recall_order SET affected_count = 1 WHERE id = 18 AND affected_count = 0;
UPDATE db_sales.retail_sale SET status = 4 WHERE id = 30 AND status IN (1, 2);
SELECT id, recall_no, affected_count, recalled_count FROM db_sales.recall_order WHERE id = 18;
SELECT id, split_batch_id, store_id, status FROM db_sales.retail_sale WHERE id = 30;
