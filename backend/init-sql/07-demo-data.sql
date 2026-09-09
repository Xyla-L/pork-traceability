-- Idempotent end-to-end demo data. Loaded after all service schemas.

USE db_breeding;

INSERT INTO farm (farm_name, license_no, address, contact_person, contact_phone, scale, status)
VALUES ('示范生态养殖场', 'FARM-DEMO-001', '北京市顺义区示范路1号', '李牧', '13800000001', 500, 1)
ON DUPLICATE KEY UPDATE farm_name = VALUES(farm_name);
SET @farm_id := (SELECT id FROM farm WHERE license_no = 'FARM-DEMO-001');

INSERT INTO pig_individual (ear_tag_no, farm_id, breed, birth_date, pen_no, source, status)
VALUES ('ET-DEMO-0001', @farm_id, '长白猪', DATE_SUB(CURDATE(), INTERVAL 180 DAY), 'A-01', '自繁', 3)
ON DUPLICATE KEY UPDATE farm_id = VALUES(farm_id), status = VALUES(status);
SET @pig_id := (SELECT id FROM pig_individual WHERE ear_tag_no = 'ET-DEMO-0001');

INSERT INTO vaccine_record (pig_id, vaccine_name, batch_no, manufacturer, inject_time, dosage, inject_site, operator, file_ids)
SELECT @pig_id, '猪瘟活疫苗', 'VAC-DEMO-001', '示范动物药业', DATE_SUB(NOW(), INTERVAL 120 DAY),
       '2ml/头', '颈部肌肉', '李牧', JSON_ARRAY()
WHERE NOT EXISTS (SELECT 1 FROM vaccine_record WHERE pig_id = @pig_id AND batch_no = 'VAC-DEMO-001');

INSERT INTO slaughter_apply (pig_id, apply_no, apply_time, weight_kg, target_slaughterhouse,
                             approval_status, approval_time, approver)
VALUES (@pig_id, 'SA-DEMO-0001', DATE_SUB(NOW(), INTERVAL 10 DAY), 118.5, '示范定点屠宰场',
        1, DATE_SUB(NOW(), INTERVAL 9 DAY), '监管员王')
ON DUPLICATE KEY UPDATE approval_status = VALUES(approval_status);

SET @cert_hash := SHA2(CONCAT('QC-DEMO-0001', ':', @pig_id, ':', 'DEMO-SM2-SIGNATURE'), 256);
INSERT INTO quarantine_certificate (pig_id, cert_no, issue_org, issue_time, valid_until, inspector,
                                    file_id, ca_signature, content_hash)
VALUES (@pig_id, 'QC-DEMO-0001', '顺义区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 8 DAY),
        DATE_ADD(CURDATE(), INTERVAL 1 DAY), '王兽医', 'demo-cert-file', 'DEMO-SM2-SIGNATURE', @cert_hash)
ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash);

USE db_slaughter;

INSERT INTO entry_inspection (pig_id, batch_no, ear_tag_no, source_farm, arrive_time, weight,
                              quarantine_cert, vehicle_no, health_check, cert_verified, status, inspector, file_ids)
VALUES (@pig_id, 'SB-DEMO-0001', 'ET-DEMO-0001', '示范生态养殖场', DATE_SUB(NOW(), INTERVAL 7 DAY),
        117.8, 'QC-DEMO-0001', '京A-DEMO1', 1, 1, 1, '张查验', '[]')
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO slaughter_inspection (pig_id, inspect_no, batch_no, ear_tag_no, inspect_type, inspect_time,
                                  temperature, organ_check, result, status, conclusion, veterinary,
                                  license_no, e_signature, file_ids, content_hash)
VALUES
(@pig_id, 'SI-DEMO-PRE-001', 'SB-DEMO-0001', 'ET-DEMO-0001', 1, DATE_SUB(NOW(), INTERVAL 7 DAY),
 38.5, '{"appearance":"normal"}', 1, 1, '宰前检验合格', '王兽医', 'VET-DEMO-001', 'DEMO-VET-SIGN', '[]',
 SHA2(CONCAT('SI-DEMO-PRE-001', ':', @pig_id), 256)),
(@pig_id, 'SI-DEMO-POST-001', 'SB-DEMO-0001', 'ET-DEMO-0001', 2, DATE_SUB(NOW(), INTERVAL 6 DAY),
 NULL, '{"heart":"normal","liver":"normal","lung":"normal"}', 1, 1, '宰后检验合格',
 '王兽医', 'VET-DEMO-001', 'DEMO-VET-SIGN', '[]', SHA2(CONCAT('SI-DEMO-POST-001', ':', @pig_id), 256))
ON DUPLICATE KEY UPDATE status = VALUES(status), result = VALUES(result);

INSERT INTO ractopamine_test (pig_id, test_no, batch_no, sample_no, test_type, test_time, test_method,
                              sample_part, result, status, operator, file_ids, report_url)
VALUES (@pig_id, 'RT-DEMO-0001', 'SB-DEMO-0001', 'SAMPLE-DEMO-001', '瘦肉精检测',
        DATE_SUB(NOW(), INTERVAL 6 DAY), '胶体金法', '尿液', 1, 2, '赵检测', '[]', '/file/demo-test-report')
ON DUPLICATE KEY UPDATE status = VALUES(status), result = VALUES(result);

SET @stamp_hash := SHA2(CONCAT('STAMP-DEMO-001', ':', @pig_id, ':', 'SB-DEMO-0001'), 256);
INSERT INTO carcass_stamp (pig_id, batch_no, carcass_no, stamp_no, stamp_type, stamp_time,
                           veterinary, e_signature, status, content_hash)
VALUES (@pig_id, 'SB-DEMO-0001', 'CARCASS-DEMO-001', 'STAMP-DEMO-001', '检疫合格章',
        DATE_SUB(NOW(), INTERVAL 6 DAY), '王兽医', 'DEMO-VET-SIGN', 1, @stamp_hash)
ON DUPLICATE KEY UPDATE status = VALUES(status), content_hash = VALUES(content_hash);

USE db_distribution;

INSERT INTO carcass_batch (batch_no, pig_ids, total_weight_kg, slaughterhouse, operator)
VALUES ('CB-DEMO-0001', JSON_ARRAY(@pig_id), 82.5, '示范定点屠宰场', '陈分割')
ON DUPLICATE KEY UPDATE pig_ids = VALUES(pig_ids);
SET @carcass_batch_id := (SELECT id FROM carcass_batch WHERE batch_no = 'CB-DEMO-0001');

SET @split1_hash := SHA2(CONCAT('SP-DEMO-0001', ':', 'CB-DEMO-0001', ':', '二分体'), 256);
INSERT INTO split_batch (batch_no, parent_batch_id, split_level, product_name, weight_kg, package_count,
                         package_type, split_time, workshop, workshop_temp, operator, file_ids, content_hash)
VALUES ('SP-DEMO-0001', @carcass_batch_id, 1, '二分体', 41.2, 1, '白条',
        DATE_SUB(NOW(), INTERVAL 5 DAY), '示范分割车间', 8.0, '陈分割', '[]', @split1_hash)
ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash);
SET @split1_id := (SELECT id FROM split_batch WHERE batch_no = 'SP-DEMO-0001');

SET @split2_hash := SHA2(CONCAT('SP-DEMO-0002', ':', 'SP-DEMO-0001', ':', '猪前腿肉'), 256);
INSERT INTO split_batch (batch_no, parent_batch_id, split_level, product_name, weight_kg, package_count,
                         package_type, split_time, workshop, workshop_temp, operator, file_ids, content_hash)
VALUES ('SP-DEMO-0002', @split1_id, 2, '猪前腿肉', 10.5, 20, '真空包装',
        DATE_SUB(NOW(), INTERVAL 4 DAY), '示范分割车间', 6.5, '陈分割', '[]', @split2_hash)
ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash);
SET @split2_id := (SELECT id FROM split_batch WHERE batch_no = 'SP-DEMO-0002');

INSERT INTO cold_chain_transport (transport_no, split_batch_id, vehicle_no, vehicle_type, refrigeration,
                                  driver_name, driver_phone, origin, destination, planned_depart,
                                  planned_arrive, depart_time, arrive_time, status)
VALUES ('TR-DEMO-0001', @split2_id, '京B-DEMO2', '冷藏车', '机械制冷', '周司机', '13800000002',
        '示范分割车间', '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
        DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 3)
ON DUPLICATE KEY UPDATE status = VALUES(status);
SET @transport_id := (SELECT id FROM cold_chain_transport WHERE transport_no = 'TR-DEMO-0001');

INSERT INTO temperature_log (transport_id, record_time, temperature, temp_range_min, temp_range_max,
                             is_abnormal, recorder, record_method)
SELECT @transport_id, DATE_SUB(NOW(), INTERVAL 60 HOUR), -2.5, -18.0, 0.0, 0, '周司机', 'DEVICE'
WHERE NOT EXISTS (SELECT 1 FROM temperature_log WHERE transport_id = @transport_id);

SET @receipt_hash := SHA2(CONCAT('TR-DEMO-0001', ':', '1001', ':', 'DEMO-RECEIVER-SIGN'), 256);
INSERT INTO store_receipt (transport_id, store_id, store_name, receipt_time, receiver, receiver_phone,
                           qty_check, temp_check, temp_value, package_intact, receipt_photo, e_signature, content_hash)
VALUES (@transport_id, 1001, '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 2 DAY), '孙店长', '13800000003',
        1, 1, -2.1, 1, JSON_ARRAY(), 'DEMO-RECEIVER-SIGN', @receipt_hash)
ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash);

USE db_sales;

INSERT INTO retail_sale (split_batch_id, product_qr_code, store_id, store_name, shelf_time,
                         is_activated, activate_time, status, expire_date)
VALUES (@split2_id, 'QR-PORK-DEMO-0001', 1001, '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 1 DAY),
        1, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, DATE_ADD(CURDATE(), INTERVAL 5 DAY))
ON DUPLICATE KEY UPDATE status = VALUES(status), expire_date = VALUES(expire_date);
SET @sale_id := (SELECT id FROM retail_sale WHERE product_qr_code = 'QR-PORK-DEMO-0001');
SET @sale_hash := SHA2(CONCAT('QR-PORK-DEMO-0001', ':', @split2_id), 256);

USE db_common;

INSERT INTO blockchain_ledger (biz_key, content_hash, tx_hash, block_number, chain_time)
SELECT 'SP-DEMO-0002', @split2_hash, CONCAT('0x', SHA2(CONCAT('SP-DEMO-0002', @split2_hash), 256)), 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM blockchain_ledger WHERE biz_key = 'SP-DEMO-0002' AND BINARY content_hash = BINARY @split2_hash);
INSERT INTO blockchain_ledger (biz_key, content_hash, tx_hash, block_number, chain_time)
SELECT 'QR-PORK-DEMO-0001', @sale_hash, CONCAT('0x', SHA2(CONCAT('QR-PORK-DEMO-0001', @sale_hash), 256)), 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM blockchain_ledger WHERE biz_key = 'QR-PORK-DEMO-0001' AND BINARY content_hash = BINARY @sale_hash);

INSERT INTO blockchain_record (event_id, biz_type, biz_id, biz_key, content_hash, tx_hash,
                               block_number, chain_time, status, retry_count)
VALUES
('demo-split-0002', 'SPLIT_BATCH', @split2_id, 'SP-DEMO-0002', @split2_hash,
 CONCAT('0x', SHA2(CONCAT('SP-DEMO-0002', @split2_hash), 256)), 1, NOW(), 1, 0),
('demo-sale-0001', 'RETAIL_SALE', @sale_id, 'QR-PORK-DEMO-0001', @sale_hash,
 CONCAT('0x', SHA2(CONCAT('QR-PORK-DEMO-0001', @sale_hash), 256)), 2, NOW(), 1, 0)
ON DUPLICATE KEY UPDATE status = VALUES(status), content_hash = VALUES(content_hash), tx_hash = VALUES(tx_hash);
