-- 2026-09-21 演示数据修复（增量，针对已初始化的库；新环境 init-sql 已包含等价逻辑）
--
-- ⚠️ 执行本文件必须带 utf8mb4：`mysql --default-character-set=utf8mb4 ... < 本文件`。
--    2026-09-21 曾用错误字符集的客户端跑过一次，第 2) 步插入的 TR-TEST-0016 中文列
--    全部落成字面 '?'（HEX=3F…，有损不可逆），签收页整行乱码。`SET NAMES` 写进文件
--    就是为了让任何客户端连上来都先声明字符集，不再依赖调用方记得加参数。
SET NAMES utf8mb4;

-- 1) 清理"未到达却有签收单"的脏数据：TR-TEST-0008(运输中)/0009~0014(待发车) 的签收单
USE db_distribution;
DELETE sr FROM store_receipt sr
JOIN cold_chain_transport ct ON ct.id = sr.transport_id
WHERE ct.transport_no IN ('TR-TEST-0008','TR-TEST-0009','TR-TEST-0010','TR-TEST-0011','TR-TEST-0012','TR-TEST-0013','TR-TEST-0014');

-- 2) 新增"已到达且未签收"运单 TR-TEST-0016 作为签收通道演示目标
--    ON DUPLICATE 把描述列也一并覆盖：这条记录曾经乱码，重跑本文件即可自修复
--    （时间列用 VALUES() 回填当日演示时刻，描述列回填正确中文）。
INSERT INTO cold_chain_transport (transport_no, split_batch_id, vehicle_no, vehicle_type, refrigeration, driver_name, driver_phone, origin, destination, planned_depart, planned_arrive, depart_time, arrive_time, status)
SELECT 'TR-TEST-0016', sb.id, '京B-TEST16', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '绿康生鲜门店',
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 HOUR),
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 HOUR), 3
FROM split_batch sb WHERE sb.batch_no = 'SP-TEST-0010'
ON DUPLICATE KEY UPDATE
  vehicle_no = VALUES(vehicle_no), vehicle_type = VALUES(vehicle_type), refrigeration = VALUES(refrigeration),
  driver_name = VALUES(driver_name), driver_phone = VALUES(driver_phone),
  origin = VALUES(origin), destination = VALUES(destination),
  status = 3, arrive_time = VALUES(arrive_time), depart_time = VALUES(depart_time);

-- 3) 恢复 3 个在库可售商品码作为销售通道演示目标
USE db_sales;
UPDATE retail_sale
SET status = 1, sell_time = NULL, sell_price = NULL, sell_weight_kg = NULL
WHERE product_qr_code IN ('QR-PORK-TEST-0005', 'QR-PORK-TEST-0009', 'QR-PORK-TEST-0012');
