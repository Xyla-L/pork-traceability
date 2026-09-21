-- 2026-09-21 演示数据修复（增量，针对已初始化的库；新环境 init-sql 已包含等价逻辑）
-- 1) 清理"未到达却有签收单"的脏数据：TR-TEST-0008(运输中)/0009~0014(待发车) 的签收单
USE db_distribution;
DELETE sr FROM store_receipt sr
JOIN cold_chain_transport ct ON ct.id = sr.transport_id
WHERE ct.transport_no IN ('TR-TEST-0008','TR-TEST-0009','TR-TEST-0010','TR-TEST-0011','TR-TEST-0012','TR-TEST-0013','TR-TEST-0014');

-- 2) 新增"已到达且未签收"运单 TR-TEST-0016 作为签收通道演示目标
INSERT INTO cold_chain_transport (transport_no, split_batch_id, vehicle_no, vehicle_type, refrigeration, driver_name, driver_phone, origin, destination, planned_depart, planned_arrive, depart_time, arrive_time, status)
SELECT 'TR-TEST-0016', sb.id, '京B-TEST16', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '绿康生鲜门店',
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 HOUR),
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 HOUR), 3
FROM split_batch sb WHERE sb.batch_no = 'SP-TEST-0010'
ON DUPLICATE KEY UPDATE status = 3, arrive_time = VALUES(arrive_time), depart_time = VALUES(depart_time);

-- 3) 恢复 3 个在库可售商品码作为销售通道演示目标
USE db_sales;
UPDATE retail_sale
SET status = 1, sell_time = NULL, sell_price = NULL, sell_weight_kg = NULL
WHERE product_qr_code IN ('QR-PORK-TEST-0005', 'QR-PORK-TEST-0009', 'QR-PORK-TEST-0012');
