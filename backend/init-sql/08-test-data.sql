-- 测试数据：每表15条，覆盖全链路，含正常/异常场景
-- 可重复执行（ON DUPLICATE KEY UPDATE）
SET NAMES utf8mb4;

-- ========== db_breeding ==========

USE db_breeding;

-- 养殖场：已有2条(id=1,2)，新增13条
INSERT INTO farm (farm_name, license_no, address, contact_person, contact_phone, scale, status) VALUES
('顺鑫养殖场', 'FARM-TEST-002', '北京市顺义区牛栏山镇2号', '王场长', '13800000002', 800, 1),
('绿康生态农场', 'FARM-TEST-003', '河北省三河市燕郊开发区3号', '刘经理', '13800000003', 1200, 1),
('金牧源养殖场', 'FARM-TEST-004', '天津市武清区泗村店镇4号', '陈场长', '13800000004', 600, 1),
('永发养猪合作社', 'FARM-TEST-005', '北京市密云区太师屯镇5号', '赵社长', '13800000005', 1000, 1),
('昌盛农牧公司', 'FARM-TEST-006', '河北省廊坊市香河县6号', '孙总', '13800000006', 1500, 1),
('兴农养殖基地', 'FARM-TEST-007', '北京市平谷区大华山镇7号', '周场长', '13800000007', 700, 1),
('惠农生猪养殖场', 'FARM-TEST-008', '河北省保定市涿州市8号', '吴经理', '13800000008', 450, 1),
('福田生态牧业', 'FARM-TEST-009', '天津市蓟州区下仓镇9号', '郑场长', '13800000009', 900, 1),
('大众养殖合作社', 'FARM-TEST-010', '北京市延庆区康庄镇10号', '王主任', '13800000010', 1100, 1),
('利农种猪场', 'FARM-TEST-011', '河北省唐山市丰润区11号', '冯场长', '13800000011', 550, 1),
('安康牧业', 'FARM-TEST-012', '天津市宝坻区大口屯镇12号', '蒋经理', '13800000012', 850, 1),
('丰禾农场', 'FARM-TEST-013', '北京市昌平区南口镇13号', '韩场长', '13800000013', 650, 1),
('宏图养殖公司', 'FARM-TEST-014', '河北省承德市兴隆县14号', '杨总', '13800000014', 1300, 1)
ON DUPLICATE KEY UPDATE farm_name = VALUES(farm_name);
SET @farm2 := (SELECT id FROM farm WHERE license_no='FARM-TEST-002');
SET @farm3 := (SELECT id FROM farm WHERE license_no='FARM-TEST-003');
SET @farm4 := (SELECT id FROM farm WHERE license_no='FARM-TEST-004');
SET @farm5 := (SELECT id FROM farm WHERE license_no='FARM-TEST-005');
SET @farm6 := (SELECT id FROM farm WHERE license_no='FARM-TEST-006');
SET @farm7 := (SELECT id FROM farm WHERE license_no='FARM-TEST-007');
SET @farm8 := (SELECT id FROM farm WHERE license_no='FARM-TEST-008');
SET @farm9 := (SELECT id FROM farm WHERE license_no='FARM-TEST-009');
SET @farm10 := (SELECT id FROM farm WHERE license_no='FARM-TEST-010');
SET @farm11 := (SELECT id FROM farm WHERE license_no='FARM-TEST-011');
SET @farm12 := (SELECT id FROM farm WHERE license_no='FARM-TEST-012');
SET @farm13 := (SELECT id FROM farm WHERE license_no='FARM-TEST-013');
SET @farm14 := (SELECT id FROM farm WHERE license_no='FARM-TEST-014');

-- 生猪个体：已有8条，新增15条
INSERT INTO pig_individual (ear_tag_no, farm_id, breed, birth_date, gender, pen_no, source, status) VALUES
('ET-TEST-0001', 1, '长白猪', DATE_SUB(CURDATE(), INTERVAL 200 DAY), 1, 'A-02', '自繁', 1),
('ET-TEST-0002', @farm2, '大白猪', DATE_SUB(CURDATE(), INTERVAL 180 DAY), 2, 'B-02', '自繁', 1),
('ET-TEST-0003', @farm2, '杜洛克', DATE_SUB(CURDATE(), INTERVAL 150 DAY), 1, 'B-03', '外购', 1),
('ET-TEST-0004', @farm3, '长白猪', DATE_SUB(CURDATE(), INTERVAL 160 DAY), 1, 'C-01', '自繁', 2),
('ET-TEST-0005', @farm3, '皮特兰', DATE_SUB(CURDATE(), INTERVAL 190 DAY), 2, 'C-02', '自繁', 3),
('ET-TEST-0006', @farm4, '大白猪', DATE_SUB(CURDATE(), INTERVAL 210 DAY), 1, 'D-01', '自繁', 1),
('ET-TEST-0007', @farm4, '长白猪', DATE_SUB(CURDATE(), INTERVAL 170 DAY), 2, 'D-02', '外购', 1),
('ET-TEST-0008', @farm5, '杜洛克', DATE_SUB(CURDATE(), INTERVAL 140 DAY), 1, 'E-01', '自繁', 2),
('ET-TEST-0009', @farm5, '皮特兰', DATE_SUB(CURDATE(), INTERVAL 130 DAY), 2, 'E-02', '自繁', 1),
('ET-TEST-0010', @farm6, '长白猪', DATE_SUB(CURDATE(), INTERVAL 120 DAY), 1, 'F-01', '自繁', 1),
('ET-TEST-0011', @farm6, '大白猪', DATE_SUB(CURDATE(), INTERVAL 110 DAY), 2, 'F-02', '外购', 1),
('ET-TEST-0012', @farm7, '杜洛克', DATE_SUB(CURDATE(), INTERVAL 100 DAY), 1, 'G-01', '自繁', 1),
('ET-TEST-0013', @farm8, '长白猪', DATE_SUB(CURDATE(), INTERVAL 90 DAY), 1, 'H-01', '自繁', 1),
('ET-TEST-0014', @farm9, '皮特兰', DATE_SUB(CURDATE(), INTERVAL 80 DAY), 2, 'I-01', '自繁', 1),
('ET-TEST-0015', @farm10, '大白猪', DATE_SUB(CURDATE(), INTERVAL 70 DAY), 2, 'J-01', '自繁', 1)
ON DUPLICATE KEY UPDATE farm_id=VALUES(farm_id), status=VALUES(status);
SET @pig1 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0001');
SET @pig2 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0002');
SET @pig3 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0003');
SET @pig4 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0004');
SET @pig5 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0005');
SET @pig6 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0006');
SET @pig7 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0007');
SET @pig8 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0008');
SET @pig9 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0009');
SET @pig10 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0010');
SET @pig11 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0011');
SET @pig12 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0012');
SET @pig13 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0013');
SET @pig14 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0014');
SET @pig15 := (SELECT id FROM pig_individual WHERE ear_tag_no='ET-TEST-0015');

-- 疫苗记录：已有1条，新增15条
INSERT INTO vaccine_record (pig_id, vaccine_name, batch_no, manufacturer, inject_time, dosage, inject_site, operator, file_ids) VALUES
(@pig1, '猪瘟活疫苗', 'VAC-TEST-0001', '示范动物药业', DATE_SUB(NOW(), INTERVAL 100 DAY), '2ml', '颈部肌肉', '李牧', '[]'),
(@pig2, '口蹄疫疫苗', 'VAC-TEST-0002', '华孚生物', DATE_SUB(NOW(), INTERVAL 90 DAY), '3ml', '颈部肌肉', '王场长', '[]'),
(@pig3, '猪蓝耳疫苗', 'VAC-TEST-0003', '中牧实业', DATE_SUB(NOW(), INTERVAL 85 DAY), '2ml', '臀部肌肉', '王场长', '[]'),
(@pig4, '猪瘟活疫苗', 'VAC-TEST-0004', '示范动物药业', DATE_SUB(NOW(), INTERVAL 80 DAY), '2ml', '颈部肌肉', '刘经理', '[]'),
(@pig5, '口蹄疫疫苗', 'VAC-TEST-0005', '华孚生物', DATE_SUB(NOW(), INTERVAL 75 DAY), '3ml', '颈部肌肉', '刘经理', '[]'),
(@pig6, '猪蓝耳疫苗', 'VAC-TEST-0006', '中牧实业', DATE_SUB(NOW(), INTERVAL 70 DAY), '2ml', '臀部肌肉', '陈场长', '[]'),
(@pig7, '伪狂犬疫苗', 'VAC-TEST-0007', '哈兽维科', DATE_SUB(NOW(), INTERVAL 65 DAY), '2ml', '颈部肌肉', '陈场长', '[]'),
(@pig8, '猪瘟活疫苗', 'VAC-TEST-0008', '示范动物药业', DATE_SUB(NOW(), INTERVAL 60 DAY), '2ml', '颈部肌肉', '赵社长', '[]'),
(@pig9, '口蹄疫疫苗', 'VAC-TEST-0009', '华孚生物', DATE_SUB(NOW(), INTERVAL 55 DAY), '3ml', '颈部肌肉', '赵社长', '[]'),
(@pig10, '猪蓝耳疫苗', 'VAC-TEST-0010', '中牧实业', DATE_SUB(NOW(), INTERVAL 50 DAY), '2ml', '臀部肌肉', '孙总', '[]'),
(@pig11, '伪狂犬疫苗', 'VAC-TEST-0011', '哈兽维科', DATE_SUB(NOW(), INTERVAL 45 DAY), '2ml', '颈部肌肉', '孙总', '[]'),
(@pig12, '猪瘟活疫苗', 'VAC-TEST-0012', '示范动物药业', DATE_SUB(NOW(), INTERVAL 40 DAY), '2ml', '颈部肌肉', '周场长', '[]'),
(@pig13, '口蹄疫疫苗', 'VAC-TEST-0013', '华孚生物', DATE_SUB(NOW(), INTERVAL 35 DAY), '3ml', '颈部肌肉', '吴经理', '[]'),
(@pig14, '猪蓝耳疫苗', 'VAC-TEST-0014', '中牧实业', DATE_SUB(NOW(), INTERVAL 30 DAY), '2ml', '臀部肌肉', '郑场长', '[]'),
(@pig15, '伪狂犬疫苗', 'VAC-TEST-0015', '哈兽维科', DATE_SUB(NOW(), INTERVAL 25 DAY), '2ml', '颈部肌肉', '王主任', '[]')
ON DUPLICATE KEY UPDATE inject_time=VALUES(inject_time);

-- 出栏申报：已有1条，新增15条
INSERT INTO slaughter_apply (pig_id, apply_no, apply_time, weight_kg, target_slaughterhouse, approval_status, approval_time, approver, reject_reason) VALUES
(@pig1, 'SA-TEST-0001', DATE_SUB(NOW(), INTERVAL 20 DAY), 108.5, '示范定点屠宰场', 1, DATE_SUB(NOW(), INTERVAL 19 DAY), '监管员王', NULL),
(@pig2, 'SA-TEST-0002', DATE_SUB(NOW(), INTERVAL 18 DAY), 112.0, '示范定点屠宰场', 1, DATE_SUB(NOW(), INTERVAL 17 DAY), '监管员王', NULL),
(@pig3, 'SA-TEST-0003', DATE_SUB(NOW(), INTERVAL 16 DAY), 99.5, '顺鑫屠宰场', 0, NULL, NULL, NULL),
(@pig4, 'SA-TEST-0004', DATE_SUB(NOW(), INTERVAL 15 DAY), 105.3, '示范定点屠宰场', 1, DATE_SUB(NOW(), INTERVAL 14 DAY), '监管员王', NULL),
(@pig5, 'SA-TEST-0005', DATE_SUB(NOW(), INTERVAL 14 DAY), 115.8, '顺鑫屠宰场', 1, DATE_SUB(NOW(), INTERVAL 13 DAY), '监管员王', NULL),
(@pig6, 'SA-TEST-0006', DATE_SUB(NOW(), INTERVAL 12 DAY), 98.0, '示范定点屠宰场', 0, NULL, NULL, NULL),
(@pig7, 'SA-TEST-0007', DATE_SUB(NOW(), INTERVAL 11 DAY), 103.2, '示范定点屠宰场', 2, DATE_SUB(NOW(), INTERVAL 10 DAY), '监管员王', '体重不达标'),
(@pig8, 'SA-TEST-0008', DATE_SUB(NOW(), INTERVAL 9 DAY), 119.0, '顺鑫屠宰场', 1, DATE_SUB(NOW(), INTERVAL 8 DAY), '监管员王', NULL),
(@pig9, 'SA-TEST-0009', DATE_SUB(NOW(), INTERVAL 7 DAY), 101.5, '示范定点屠宰场', 0, NULL, NULL, NULL),
(@pig10, 'SA-TEST-0010', DATE_SUB(NOW(), INTERVAL 6 DAY), 110.0, '示范定点屠宰场', 1, DATE_SUB(NOW(), INTERVAL 5 DAY), '监管员王', NULL),
(@pig11, 'SA-TEST-0011', DATE_SUB(NOW(), INTERVAL 5 DAY), 95.5, '顺鑫屠宰场', 2, DATE_SUB(NOW(), INTERVAL 4 DAY), '监管员王', '检疫不合格'),
(@pig12, 'SA-TEST-0012', DATE_SUB(NOW(), INTERVAL 4 DAY), 107.8, '示范定点屠宰场', 0, NULL, NULL, NULL),
(@pig13, 'SA-TEST-0013', DATE_SUB(NOW(), INTERVAL 3 DAY), 113.0, '顺鑫屠宰场', 1, DATE_SUB(NOW(), INTERVAL 2 DAY), '监管员王', NULL),
(@pig14, 'SA-TEST-0014', DATE_SUB(NOW(), INTERVAL 2 DAY), 100.2, '示范定点屠宰场', 0, NULL, NULL, NULL),
(@pig15, 'SA-TEST-0015', DATE_SUB(NOW(), INTERVAL 1 DAY), 109.5, '示范定点屠宰场', 1, NOW(), '监管员王', NULL)
ON DUPLICATE KEY UPDATE approval_status=VALUES(approval_status);

-- 产地检疫证明：已有1条，新增15条
INSERT INTO quarantine_certificate (pig_id, cert_no, issue_org, issue_time, valid_until, inspector, file_id, ca_signature, content_hash) VALUES
(@pig1, 'QC-TEST-0001', '顺义区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY), '王兽医', 'test-cert-001', 'TEST-SIG-001', SHA2(CONCAT('QC-TEST-0001', @pig1), 256)),
(@pig2, 'QC-TEST-0002', '顺义区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 17 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY), '王兽医', 'test-cert-002', 'TEST-SIG-002', SHA2(CONCAT('QC-TEST-0002', @pig2), 256)),
(@pig3, 'QC-TEST-0003', '三河市动物卫生监督所', DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), '赵兽医', 'test-cert-003', 'TEST-SIG-003', SHA2(CONCAT('QC-TEST-0003', @pig3), 256)),
(@pig4, 'QC-TEST-0004', '顺义区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY), '王兽医', 'test-cert-004', 'TEST-SIG-004', SHA2(CONCAT('QC-TEST-0004', @pig4), 256)),
(@pig5, 'QC-TEST-0005', '三河市动物卫生监督所', DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY), '赵兽医', 'test-cert-005', 'TEST-SIG-005', SHA2(CONCAT('QC-TEST-0005', @pig5), 256)),
(@pig6, 'QC-TEST-0006', '武清区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), '李兽医', 'test-cert-006', 'TEST-SIG-006', SHA2(CONCAT('QC-TEST-0006', @pig6), 256)),
(@pig7, 'QC-TEST-0007', '密云区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), '张兽医', 'test-cert-007', 'TEST-SIG-007', SHA2(CONCAT('QC-TEST-0007', @pig7), 256)),
(@pig8, 'QC-TEST-0008', '香河县动物卫生监督所', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), '刘兽医', 'test-cert-008', 'TEST-SIG-008', SHA2(CONCAT('QC-TEST-0008', @pig8), 256)),
(@pig9, 'QC-TEST-0009', '平谷区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), '孙兽医', 'test-cert-009', 'TEST-SIG-009', SHA2(CONCAT('QC-TEST-0009', @pig9), 256)),
(@pig10, 'QC-TEST-0010', '延庆区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), '周兽医', 'test-cert-010', 'TEST-SIG-010', SHA2(CONCAT('QC-TEST-0010', @pig10), 256)),
(@pig11, 'QC-TEST-0011', '涿州市动物卫生监督所', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), '吴兽医', 'test-cert-011', 'TEST-SIG-011', SHA2(CONCAT('QC-TEST-0011', @pig11), 256)),
(@pig12, 'QC-TEST-0012', '蓟州区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), '郑兽医', 'test-cert-012', 'TEST-SIG-012', SHA2(CONCAT('QC-TEST-0012', @pig12), 256)),
(@pig13, 'QC-TEST-0013', '丰润区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), '王兽医', 'test-cert-013', 'TEST-SIG-013', SHA2(CONCAT('QC-TEST-0013', @pig13), 256)),
(@pig14, 'QC-TEST-0014', '宝坻区动物卫生监督所', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), '韩兽医', 'test-cert-014', 'TEST-SIG-014', SHA2(CONCAT('QC-TEST-0014', @pig14), 256)),
(@pig15, 'QC-TEST-0015', '南口区动物卫生监督所', NOW(), DATE_ADD(CURDATE(), INTERVAL 3 DAY), '杨兽医', 'test-cert-015', 'TEST-SIG-015', SHA2(CONCAT('QC-TEST-0015', @pig15), 256))
ON DUPLICATE KEY UPDATE content_hash=VALUES(content_hash);

-- ========== db_slaughter ==========

USE db_slaughter;

-- 入场查验：已有2条，新增15条
INSERT INTO entry_inspection (pig_id, batch_no, ear_tag_no, source_farm, arrive_time, weight, quarantine_cert, vehicle_no, health_check, cert_verified, status, inspector, file_ids) VALUES
(@pig1, 'SB-TEST-0001', 'ET-TEST-0001', '示范生态养殖场', DATE_SUB(NOW(), INTERVAL 18 DAY), 108.0, 'QC-TEST-0001', '京A-TEST1', 1, 1, 1, '张查验', '[]'),
(@pig2, 'SB-TEST-0002', 'ET-TEST-0002', '顺鑫养殖场', DATE_SUB(NOW(), INTERVAL 17 DAY), 111.5, 'QC-TEST-0002', '京A-TEST2', 1, 1, 1, '张查验', '[]'),
(@pig3, 'SB-TEST-0003', 'ET-TEST-0003', '顺鑫养殖场', DATE_SUB(NOW(), INTERVAL 15 DAY), 99.0, 'QC-TEST-0003', '京A-TEST3', 0, 0, 0, '张查验', '[]'),
(@pig4, 'SB-TEST-0004', 'ET-TEST-0004', '绿康生态农场', DATE_SUB(NOW(), INTERVAL 14 DAY), 104.8, 'QC-TEST-0004', '冀A-TEST4', 1, 1, 1, '张查验', '[]'),
(@pig5, 'SB-TEST-0005', 'ET-TEST-0005', '绿康生态农场', DATE_SUB(NOW(), INTERVAL 13 DAY), 115.0, 'QC-TEST-0005', '冀A-TEST5', 1, 1, 1, '张查验', '[]'),
(@pig6, 'SB-TEST-0006', 'ET-TEST-0006', '金牧源养殖场', DATE_SUB(NOW(), INTERVAL 11 DAY), 97.5, 'QC-TEST-0006', '津A-TEST6', 1, 1, 1, '张查验', '[]'),
(@pig7, 'SB-TEST-0007', 'ET-TEST-0007', '金牧源养殖场', DATE_SUB(NOW(), INTERVAL 9 DAY), 102.8, 'QC-TEST-0007', '津A-TEST7', 1, 1, 1, '张查验', '[]'),
(@pig8, 'SB-TEST-0008', 'ET-TEST-0008', '永发养猪合作社', DATE_SUB(NOW(), INTERVAL 8 DAY), 118.5, 'QC-TEST-0008', '京B-TEST8', 1, 1, 1, '张查验', '[]'),
(@pig9, 'SB-TEST-0009', 'ET-TEST-0009', '永发养猪合作社', DATE_SUB(NOW(), INTERVAL 6 DAY), 101.0, 'QC-TEST-0009', '京B-TEST9', 0, 1, 0, '张查验', '[]'),
(@pig10, 'SB-TEST-0010', 'ET-TEST-0010', '昌盛农牧公司', DATE_SUB(NOW(), INTERVAL 5 DAY), 109.5, 'QC-TEST-0010', '冀B-TEST10', 1, 1, 1, '张查验', '[]'),
(@pig11, 'SB-TEST-0011', 'ET-TEST-0011', '昌盛农牧公司', DATE_SUB(NOW(), INTERVAL 4 DAY), 95.0, 'QC-TEST-0011', '冀B-TEST11', 1, 1, 1, '张查验', '[]'),
(@pig12, 'SB-TEST-0012', 'ET-TEST-0012', '兴农养殖基地', DATE_SUB(NOW(), INTERVAL 3 DAY), 107.2, 'QC-TEST-0012', '京C-TEST12', 1, 1, 1, '张查验', '[]'),
(@pig13, 'SB-TEST-0013', 'ET-TEST-0013', '惠农生猪养殖场', DATE_SUB(NOW(), INTERVAL 2 DAY), 112.5, 'QC-TEST-0013', '津B-TEST13', 1, 1, 1, '张查验', '[]'),
(@pig14, 'SB-TEST-0014', 'ET-TEST-0014', '福田生态牧业', DATE_SUB(NOW(), INTERVAL 1 DAY), 99.8, 'QC-TEST-0014', '津C-TEST14', 1, 1, 1, '张查验', '[]'),
(@pig15, 'SB-TEST-0015', 'ET-TEST-0015', '大众养殖合作社', NOW(), 109.0, 'QC-TEST-0015', '冀C-TEST15', 1, 1, 0, '张查验', '[]')
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- 屠宰检验：已有3条，新增15条（宰前+宰后）
INSERT INTO slaughter_inspection (pig_id, inspect_no, batch_no, ear_tag_no, inspect_type, inspect_time, temperature, organ_check, result, status, conclusion, veterinary, license_no, e_signature, file_ids, content_hash) VALUES
(@pig1, 'SI-TEST-PRE-001', 'SB-TEST-0001', 'ET-TEST-0001', 1, DATE_SUB(NOW(), INTERVAL 18 DAY), 38.5, '{"appearance":"normal"}', 1, 1, '宰前检验合格', '王兽医', 'VET-TEST-001', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-PRE-001', @pig1), 256)),
(@pig1, 'SI-TEST-POST-001', 'SB-TEST-0001', 'ET-TEST-0001', 2, DATE_SUB(NOW(), INTERVAL 17 DAY), NULL, '{"heart":"normal","liver":"normal","lung":"normal"}', 1, 1, '宰后检验合格', '王兽医', 'VET-TEST-001', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-POST-001', @pig1), 256)),
(@pig2, 'SI-TEST-PRE-002', 'SB-TEST-0002', 'ET-TEST-0002', 1, DATE_SUB(NOW(), INTERVAL 17 DAY), 39.0, '{"appearance":"normal"}', 1, 1, '宰前检验合格', '王兽医', 'VET-TEST-001', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-PRE-002', @pig2), 256)),
(@pig2, 'SI-TEST-POST-002', 'SB-TEST-0002', 'ET-TEST-0002', 2, DATE_SUB(NOW(), INTERVAL 16 DAY), NULL, '{"heart":"normal","liver":"normal","lung":"normal"}', 1, 1, '宰后检验合格', '王兽医', 'VET-TEST-001', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-POST-002', @pig2), 256)),
(@pig4, 'SI-TEST-PRE-004', 'SB-TEST-0004', 'ET-TEST-0004', 1, DATE_SUB(NOW(), INTERVAL 14 DAY), 38.8, '{"appearance":"normal"}', 1, 1, '宰前检验合格', '赵兽医', 'VET-TEST-002', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-PRE-004', @pig4), 256)),
(@pig4, 'SI-TEST-POST-004', 'SB-TEST-0004', 'ET-TEST-0004', 2, DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, '{"heart":"normal","liver":"normal","lung":"normal"}', 1, 1, '宰后检验合格', '赵兽医', 'VET-TEST-002', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-POST-004', @pig4), 256)),
(@pig5, 'SI-TEST-PRE-005', 'SB-TEST-0005', 'ET-TEST-0005', 1, DATE_SUB(NOW(), INTERVAL 13 DAY), 39.2, '{"appearance":"normal"}', 1, 1, '宰前检验合格', '赵兽医', 'VET-TEST-002', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-PRE-005', @pig5), 256)),
(@pig5, 'SI-TEST-POST-005', 'SB-TEST-0005', 'ET-TEST-0005', 2, DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, '{"heart":"normal","liver":"normal","lung":"normal"}', 1, 1, '宰后检验合格', '赵兽医', 'VET-TEST-002', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-POST-005', @pig5), 256)),
(@pig6, 'SI-TEST-PRE-006', 'SB-TEST-0006', 'ET-TEST-0006', 1, DATE_SUB(NOW(), INTERVAL 11 DAY), 38.6, '{"appearance":"normal"}', 1, 1, '宰前检验合格', '李兽医', 'VET-TEST-003', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-PRE-006', @pig6), 256)),
(@pig6, 'SI-TEST-POST-006', 'SB-TEST-0006', 'ET-TEST-0006', 2, DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, '{"heart":"normal","liver":"normal","lung":"normal"}', 1, 1, '宰后检验合格', '李兽医', 'VET-TEST-003', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-POST-006', @pig6), 256)),
(@pig7, 'SI-TEST-PRE-007', 'SB-TEST-0007', 'ET-TEST-0007', 1, DATE_SUB(NOW(), INTERVAL 9 DAY), 39.5, '{"appearance":"slightly_abnormal"}', 1, 1, '宰前检验合格', '李兽医', 'VET-TEST-003', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-PRE-007', @pig7), 256)),
(@pig7, 'SI-TEST-POST-007', 'SB-TEST-0007', 'ET-TEST-0007', 2, DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, '{"heart":"normal","liver":"mild_lesion","lung":"normal"}', 1, 1, '宰后检验合格，肝脏轻度病变已处理', '李兽医', 'VET-TEST-003', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-POST-007', @pig7), 256)),
(@pig8, 'SI-TEST-PRE-008', 'SB-TEST-0008', 'ET-TEST-0008', 1, DATE_SUB(NOW(), INTERVAL 8 DAY), 38.9, '{"appearance":"normal"}', 1, 1, '宰前检验合格', '刘兽医', 'VET-TEST-004', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-PRE-008', @pig8), 256)),
(@pig8, 'SI-TEST-POST-008', 'SB-TEST-0008', 'ET-TEST-0008', 2, DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, '{"heart":"normal","liver":"normal","lung":"normal"}', 1, 1, '宰后检验合格', '刘兽医', 'VET-TEST-004', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-POST-008', @pig8), 256)),
(@pig10, 'SI-TEST-PRE-010', 'SB-TEST-0010', 'ET-TEST-0010', 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 38.7, '{"appearance":"normal"}', 1, 1, '宰前检验合格', '周兽医', 'VET-TEST-005', 'TEST-VET-SIG', '[]', SHA2(CONCAT('SI-TEST-PRE-010', @pig10), 256))
ON DUPLICATE KEY UPDATE status=VALUES(status), result=VALUES(result);

-- 瘦肉精检测：已有1条，新增15条
INSERT INTO ractopamine_test (pig_id, test_no, batch_no, ear_tag_no, sample_no, test_type, test_time, test_method, sample_part, result, status, operator, file_ids, report_url) VALUES
(@pig1, 'RT-TEST-0001', 'SB-TEST-0001', 'ET-TEST-0001', 'SAMPLE-TEST-0001', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 17 DAY), '胶体金法', '尿液', 1, 2, '赵检测', '[]', '/file/test-report-0001'),
(@pig2, 'RT-TEST-0002', 'SB-TEST-0002', 'ET-TEST-0002', 'SAMPLE-TEST-0002', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 16 DAY), '胶体金法', '尿液', 1, 2, '赵检测', '[]', '/file/test-report-0002'),
(@pig3, 'RT-TEST-0003', 'SB-TEST-0003', 'ET-TEST-0003', 'SAMPLE-TEST-0003', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 15 DAY), '胶体金法', '尿液', 0, 2, '赵检测', '[]', '/file/test-report-0003'),
(@pig4, 'RT-TEST-0004', 'SB-TEST-0004', 'ET-TEST-0004', 'SAMPLE-TEST-0004', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 13 DAY), '酶联免疫法', '尿液', 1, 2, '钱检测', '[]', '/file/test-report-0004'),
(@pig5, 'RT-TEST-0005', 'SB-TEST-0005', 'ET-TEST-0005', 'SAMPLE-TEST-0005', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 12 DAY), '胶体金法', '尿液', 1, 2, '钱检测', '[]', '/file/test-report-0005'),
(@pig6, 'RT-TEST-0006', 'SB-TEST-0006', 'ET-TEST-0006', 'SAMPLE-TEST-0006', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 10 DAY), '胶体金法', '尿液', 1, 2, '赵检测', '[]', '/file/test-report-0006'),
(@pig7, 'RT-TEST-0007', 'SB-TEST-0007', 'ET-TEST-0007', 'SAMPLE-TEST-0007', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 8 DAY), '酶联免疫法', '尿液', 1, 2, '钱检测', '[]', '/file/test-report-0007'),
(@pig8, 'RT-TEST-0008', 'SB-TEST-0008', 'ET-TEST-0008', 'SAMPLE-TEST-0008', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 7 DAY), '胶体金法', '尿液', 1, 2, '赵检测', '[]', '/file/test-report-0008'),
(@pig9, 'RT-TEST-0009', 'SB-TEST-0009', 'ET-TEST-0009', 'SAMPLE-TEST-0009', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 6 DAY), '胶体金法', '尿液', 0, 1, '钱检测', '[]', '/file/test-report-0009'),
(@pig10, 'RT-TEST-0010', 'SB-TEST-0010', 'ET-TEST-0010', 'SAMPLE-TEST-0010', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 4 DAY), '胶体金法', '尿液', 1, 2, '赵检测', '[]', '/file/test-report-0010'),
(@pig11, 'RT-TEST-0011', 'SB-TEST-0011', 'ET-TEST-0011', 'SAMPLE-TEST-0011', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 3 DAY), '酶联免疫法', '尿液', 1, 2, '钱检测', '[]', '/file/test-report-0011'),
(@pig12, 'RT-TEST-0012', 'SB-TEST-0012', 'ET-TEST-0012', 'SAMPLE-TEST-0012', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 2 DAY), '胶体金法', '尿液', 1, 2, '赵检测', '[]', '/file/test-report-0012'),
(@pig13, 'RT-TEST-0013', 'SB-TEST-0013', 'ET-TEST-0013', 'SAMPLE-TEST-0013', '瘦肉精检测', DATE_SUB(NOW(), INTERVAL 1 DAY), '胶体金法', '尿液', 1, 2, '钱检测', '[]', '/file/test-report-0013'),
(@pig14, 'RT-TEST-0014', 'SB-TEST-0014', 'ET-TEST-0014', 'SAMPLE-TEST-0014', '瘦肉精检测', NOW(), '胶体金法', '尿液', 1, 1, '赵检测', '[]', '/file/test-report-0014'),
(@pig15, 'RT-TEST-0015', 'SB-TEST-0015', 'ET-TEST-0015', 'SAMPLE-TEST-0015', '瘦肉精检测', NOW(), '酶联免疫法', '尿液', 1, 0, '钱检测', '[]', '/file/test-report-0015')
ON DUPLICATE KEY UPDATE status=VALUES(status), result=VALUES(result);

-- 胴体印章：已有1条，新增15条
INSERT INTO carcass_stamp (pig_id, batch_no, ear_tag_no, carcass_no, stamp_no, stamp_type, stamp_time, veterinary, e_signature, status, content_hash) VALUES
(@pig1, 'SB-TEST-0001', 'ET-TEST-0001', 'CARCASS-TEST-0001', 'STAMP-TEST-0001', '检疫合格章', DATE_SUB(NOW(), INTERVAL 17 DAY), '王兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0001', @pig1), 256)),
(@pig2, 'SB-TEST-0002', 'ET-TEST-0002', 'CARCASS-TEST-0002', 'STAMP-TEST-0002', '检疫合格章', DATE_SUB(NOW(), INTERVAL 16 DAY), '王兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0002', @pig2), 256)),
(@pig4, 'SB-TEST-0004', 'ET-TEST-0004', 'CARCASS-TEST-0004', 'STAMP-TEST-0004', '检疫合格章', DATE_SUB(NOW(), INTERVAL 13 DAY), '赵兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0004', @pig4), 256)),
(@pig5, 'SB-TEST-0005', 'ET-TEST-0005', 'CARCASS-TEST-0005', 'STAMP-TEST-0005', '检疫合格章', DATE_SUB(NOW(), INTERVAL 12 DAY), '赵兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0005', @pig5), 256)),
(@pig6, 'SB-TEST-0006', 'ET-TEST-0006', 'CARCASS-TEST-0006', 'STAMP-TEST-0006', '检疫合格章', DATE_SUB(NOW(), INTERVAL 10 DAY), '李兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0006', @pig6), 256)),
(@pig7, 'SB-TEST-0007', 'ET-TEST-0007', 'CARCASS-TEST-0007', 'STAMP-TEST-0007', '检疫合格章', DATE_SUB(NOW(), INTERVAL 8 DAY), '李兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0007', @pig7), 256)),
(@pig8, 'SB-TEST-0008', 'ET-TEST-0008', 'CARCASS-TEST-0008', 'STAMP-TEST-0008', '检疫合格章', DATE_SUB(NOW(), INTERVAL 7 DAY), '刘兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0008', @pig8), 256)),
(@pig10, 'SB-TEST-0010', 'ET-TEST-0010', 'CARCASS-TEST-0010', 'STAMP-TEST-0010', '检疫合格章', DATE_SUB(NOW(), INTERVAL 4 DAY), '周兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0010', @pig10), 256)),
(@pig11, 'SB-TEST-0011', 'ET-TEST-0011', 'CARCASS-TEST-0011', 'STAMP-TEST-0011', '检疫合格章', DATE_SUB(NOW(), INTERVAL 3 DAY), '周兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0011', @pig11), 256)),
(@pig12, 'SB-TEST-0012', 'ET-TEST-0012', 'CARCASS-TEST-0012', 'STAMP-TEST-0012', '检疫合格章', DATE_SUB(NOW(), INTERVAL 2 DAY), '郑兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0012', @pig12), 256)),
(@pig13, 'SB-TEST-0013', 'ET-TEST-0013', 'CARCASS-TEST-0013', 'STAMP-TEST-0013', '检疫合格章', DATE_SUB(NOW(), INTERVAL 1 DAY), '韩兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0013', @pig13), 256)),
(@pig14, 'SB-TEST-0014', 'ET-TEST-0014', 'CARCASS-TEST-0014', 'STAMP-TEST-0014', '检疫合格章', NOW(), '韩兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0014', @pig14), 256)),
(@pig15, 'SB-TEST-0015', 'ET-TEST-0015', 'CARCASS-TEST-0015', 'STAMP-TEST-0015', '检疫合格章', NOW(), '杨兽医', 'TEST-VET-SIG', 1, SHA2(CONCAT('STAMP-TEST-0015', @pig15), 256)),
(@pig3, 'SB-TEST-0003', 'ET-TEST-0003', 'CARCASS-TEST-0003', 'STAMP-TEST-0003', '检疫合格章', DATE_SUB(NOW(), INTERVAL 14 DAY), '赵兽医', 'TEST-VET-SIG', 0, SHA2(CONCAT('STAMP-TEST-0003', @pig3), 256)),
(@pig9, 'SB-TEST-0009', 'ET-TEST-0009', 'CARCASS-TEST-0009', 'STAMP-TEST-0009', '检疫合格章', DATE_SUB(NOW(), INTERVAL 5 DAY), '孙兽医', 'TEST-VET-SIG', 0, SHA2(CONCAT('STAMP-TEST-0009', @pig9), 256))
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- ========== db_distribution ==========

USE db_distribution;

-- 胴体批次：已有1条，新增15条
INSERT INTO carcass_batch (batch_no, pig_ids, total_weight_kg, slaughterhouse, operator, note) VALUES
('CB-TEST-0001', JSON_ARRAY(@pig1), 82.5, '示范定点屠宰场', '陈分割', '第一批测试'),
('CB-TEST-0002', JSON_ARRAY(@pig2), 85.0, '示范定点屠宰场', '陈分割', '第二批测试'),
('CB-TEST-0003', JSON_ARRAY(@pig4), 78.5, '示范定点屠宰场', '陈分割', NULL),
('CB-TEST-0004', JSON_ARRAY(@pig5), 88.0, '顺鑫屠宰场', '陈分割', '大重量批次'),
('CB-TEST-0005', JSON_ARRAY(@pig6), 75.0, '示范定点屠宰场', '陈分割', NULL),
('CB-TEST-0006', JSON_ARRAY(@pig7), 80.5, '示范定点屠宰场', '陈分割', NULL),
('CB-TEST-0007', JSON_ARRAY(@pig8), 90.0, '示范定点屠宰场', '陈分割', NULL),
('CB-TEST-0008', JSON_ARRAY(@pig10), 83.5, '示范定点屠宰场', '陈分割', NULL),
('CB-TEST-0009', JSON_ARRAY(@pig11), 72.0, '顺鑫屠宰场', '陈分割', '较小重量'),
('CB-TEST-0010', JSON_ARRAY(@pig12), 81.0, '示范定点屠宰场', '陈分割', NULL),
('CB-TEST-0011', JSON_ARRAY(@pig13), 86.5, '顺鑫屠宰场', '陈分割', NULL),
('CB-TEST-0012', JSON_ARRAY(@pig14), 76.5, '示范定点屠宰场', '陈分割', NULL),
('CB-TEST-0013', JSON_ARRAY(@pig3), 70.0, '顺鑫屠宰场', '陈分割', '未通过检验'),
('CB-TEST-0014', JSON_ARRAY(@pig9), 74.5, '示范定点屠宰场', '陈分割', NULL),
('CB-TEST-0015', JSON_ARRAY(@pig15), 84.0, '示范定点屠宰场', '陈分割', NULL)
ON DUPLICATE KEY UPDATE pig_ids=VALUES(pig_ids);
SET @cb1 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0001');
SET @cb2 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0002');
SET @cb3 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0003');
SET @cb4 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0004');
SET @cb5 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0005');
SET @cb6 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0006');
SET @cb7 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0007');
SET @cb8 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0008');
SET @cb9 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0009');
SET @cb10 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0010');
SET @cb11 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0011');
SET @cb12 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0012');
SET @cb13 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0013');
SET @cb14 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0014');
SET @cb15 := (SELECT id FROM carcass_batch WHERE batch_no='CB-TEST-0015');

-- 分割批次：已有2条，新增15条
INSERT INTO split_batch (batch_no, parent_batch_id, split_level, product_name, weight_kg, package_count, package_type, split_time, workshop, workshop_temp, operator, file_ids, content_hash) VALUES
('SP-TEST-0001', @cb1, 1, '二分体', 41.2, 1, '白条', DATE_SUB(NOW(), INTERVAL 16 DAY), '示范分割车间', 8.0, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0001', @cb1), 256)),
('SP-TEST-0002', @cb1, 2, '猪前腿肉', 10.5, 20, '真空包装', DATE_SUB(NOW(), INTERVAL 15 DAY), '示范分割车间', 6.5, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0002', @cb1), 256)),
('SP-TEST-0003', @cb2, 1, '二分体', 42.5, 1, '白条', DATE_SUB(NOW(), INTERVAL 14 DAY), '示范分割车间', 8.0, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0003', @cb2), 256)),
('SP-TEST-0004', @cb2, 2, '猪后腿肉', 12.0, 15, '真空包装', DATE_SUB(NOW(), INTERVAL 13 DAY), '示范分割车间', 6.5, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0004', @cb2), 256)),
('SP-TEST-0005', @cb3, 1, '二分体', 39.0, 1, '白条', DATE_SUB(NOW(), INTERVAL 12 DAY), '示范分割车间', 8.0, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0005', @cb3), 256)),
('SP-TEST-0006', @cb3, 2, '猪排骨', 8.5, 10, '真空包装', DATE_SUB(NOW(), INTERVAL 11 DAY), '示范分割车间', 6.5, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0006', @cb3), 256)),
('SP-TEST-0007', @cb4, 1, '二分体', 44.0, 1, '白条', DATE_SUB(NOW(), INTERVAL 10 DAY), '示范分割车间', 8.0, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0007', @cb4), 256)),
('SP-TEST-0008', @cb4, 2, '猪五花肉', 15.0, 25, '真空包装', DATE_SUB(NOW(), INTERVAL 9 DAY), '示范分割车间', 6.5, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0008', @cb4), 256)),
('SP-TEST-0009', @cb5, 1, '二分体', 37.5, 1, '白条', DATE_SUB(NOW(), INTERVAL 8 DAY), '示范分割车间', 8.0, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0009', @cb5), 256)),
('SP-TEST-0010', @cb5, 2, '猪里脊', 5.5, 8, '真空包装', DATE_SUB(NOW(), INTERVAL 7 DAY), '示范分割车间', 6.5, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0010', @cb5), 256)),
('SP-TEST-0011', @cb6, 1, '二分体', 40.0, 1, '白条', DATE_SUB(NOW(), INTERVAL 6 DAY), '示范分割车间', 8.0, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0011', @cb6), 256)),
('SP-TEST-0012', @cb6, 2, '猪前腿肉', 11.0, 22, '真空包装', DATE_SUB(NOW(), INTERVAL 5 DAY), '示范分割车间', 6.5, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0012', @cb6), 256)),
('SP-TEST-0013', @cb7, 1, '二分体', 45.0, 1, '白条', DATE_SUB(NOW(), INTERVAL 4 DAY), '示范分割车间', 8.0, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0013', @cb7), 256)),
('SP-TEST-0014', @cb7, 2, '猪排骨', 9.0, 12, '真空包装', DATE_SUB(NOW(), INTERVAL 3 DAY), '示范分割车间', 6.5, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0014', @cb7), 256)),
('SP-TEST-0015', @cb8, 1, '二分体', 41.5, 1, '白条', DATE_SUB(NOW(), INTERVAL 2 DAY), '示范分割车间', 8.0, '陈分割', '[]', SHA2(CONCAT('SP-TEST-0015', @cb8), 256))
ON DUPLICATE KEY UPDATE content_hash=VALUES(content_hash);
SET @sp1 := (SELECT id FROM split_batch WHERE batch_no='SP-TEST-0002');
SET @sp2 := (SELECT id FROM split_batch WHERE batch_no='SP-TEST-0004');
SET @sp3 := (SELECT id FROM split_batch WHERE batch_no='SP-TEST-0006');
SET @sp4 := (SELECT id FROM split_batch WHERE batch_no='SP-TEST-0008');
SET @sp5 := (SELECT id FROM split_batch WHERE batch_no='SP-TEST-0010');
SET @sp6 := (SELECT id FROM split_batch WHERE batch_no='SP-TEST-0012');
SET @sp7 := (SELECT id FROM split_batch WHERE batch_no='SP-TEST-0014');

-- 冷链运输：已有1条，新增15条
INSERT INTO cold_chain_transport (transport_no, split_batch_id, vehicle_no, vehicle_type, refrigeration, driver_name, driver_phone, origin, destination, planned_depart, planned_arrive, depart_time, arrive_time, status) VALUES
('TR-TEST-0001', @sp1, '京B-TEST01', '冷藏车', '机械制冷', '周司机', '13800000020', '示范分割车间', '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), 3),
('TR-TEST-0002', @sp2, '京B-TEST02', '冷藏车', '机械制冷', '周司机', '13800000020', '示范分割车间', '顺鑫生鲜门店', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 3),
('TR-TEST-0003', @sp3, '京B-TEST03', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '绿康生鲜门店', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 3),
('TR-TEST-0004', @sp4, '京B-TEST04', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '金牧生鲜门店', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 3),
('TR-TEST-0005', @sp5, '京B-TEST05', '冷藏车', '机械制冷', '周司机', '13800000020', '示范分割车间', '永发生鲜门店', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 3),
('TR-TEST-0006', @sp6, '京B-TEST06', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '昌盛生鲜门店', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 3),
('TR-TEST-0007', @sp7, '京B-TEST07', '冷藏车', '机械制冷', '周司机', '13800000020', '示范分割车间', '兴农生鲜门店', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 3),
('TR-TEST-0008', @sp1, '京B-TEST08', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '惠农生鲜门店', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 2),
('TR-TEST-0009', @sp2, '京B-TEST09', '冷藏车', '机械制冷', '周司机', '13800000020', '示范分割车间', '福田生鲜门店', NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), NULL, NULL, 1),
('TR-TEST-0010', @sp3, '京B-TEST10', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '大众生鲜门店', DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), NULL, NULL, 1),
('TR-TEST-0011', @sp4, '京B-TEST11', '冷藏车', '机械制冷', '周司机', '13800000020', '示范分割车间', '利农生鲜门店', DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, NULL, 1),
('TR-TEST-0012', @sp5, '京B-TEST12', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '安康生鲜门店', DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY), NULL, NULL, 1),
('TR-TEST-0013', @sp6, '京B-TEST13', '冷藏车', '机械制冷', '周司机', '13800000020', '示范分割车间', '丰禾生鲜门店', DATE_ADD(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), NULL, NULL, 1),
('TR-TEST-0014', @sp7, '京B-TEST14', '冷藏车', '机械制冷', '吴师傅', '13800000021', '示范分割车间', '宏图生鲜门店', DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY), NULL, NULL, 1),
('TR-TEST-0015', @sp1, '京B-TEST15', '冷藏车', '液氮制冷', '周司机', '13800000020', '示范分割车间', '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), 4)
ON DUPLICATE KEY UPDATE status=VALUES(status);
SET @tr1 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0001');
SET @tr2 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0002');
SET @tr3 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0003');
SET @tr4 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0004');
SET @tr5 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0005');
SET @tr6 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0006');
SET @tr7 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0007');
SET @tr8 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0008');
SET @tr9 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0009');
SET @tr10 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0010');
SET @tr11 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0011');
SET @tr12 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0012');
SET @tr13 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0013');
SET @tr14 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0014');
SET @tr15 := (SELECT id FROM cold_chain_transport WHERE transport_no='TR-TEST-0015');

-- 温度记录：已有1条，新增15条
INSERT INTO temperature_log (transport_id, record_time, temperature, temp_range_min, temp_range_max, is_abnormal, recorder, record_method) VALUES
(@tr1, DATE_SUB(NOW(), INTERVAL 13 DAY), -3.0, -18.0, 0.0, 0, '周司机', 'DEVICE'),
(@tr1, DATE_SUB(NOW(), INTERVAL 13 DAY), -3.5, -18.0, 0.0, 0, '设备自动', 'DEVICE'),
(@tr2, DATE_SUB(NOW(), INTERVAL 11 DAY), -4.0, -18.0, 0.0, 0, '周司机', 'DEVICE'),
(@tr2, DATE_SUB(NOW(), INTERVAL 11 DAY), 2.5, -18.0, 0.0, 1, '设备自动', 'DEVICE'),
(@tr3, DATE_SUB(NOW(), INTERVAL 9 DAY), -5.0, -18.0, 0.0, 0, '吴师傅', 'DEVICE'),
(@tr3, DATE_SUB(NOW(), INTERVAL 9 DAY), -2.0, -18.0, 0.0, 0, '设备自动', 'DEVICE'),
(@tr4, DATE_SUB(NOW(), INTERVAL 7 DAY), -6.0, -18.0, 0.0, 0, '吴师傅', 'DEVICE'),
(@tr4, DATE_SUB(NOW(), INTERVAL 7 DAY), 5.0, -18.0, 0.0, 1, '设备自动', 'DEVICE'),
(@tr5, DATE_SUB(NOW(), INTERVAL 5 DAY), -4.5, -18.0, 0.0, 0, '周司机', 'DEVICE'),
(@tr6, DATE_SUB(NOW(), INTERVAL 3 DAY), -3.0, -18.0, 0.0, 0, '吴师傅', 'DEVICE'),
(@tr6, DATE_SUB(NOW(), INTERVAL 3 DAY), -2.5, -18.0, 0.0, 0, '设备自动', 'DEVICE'),
(@tr7, DATE_SUB(NOW(), INTERVAL 1 DAY), -5.5, -18.0, 0.0, 0, '周司机', 'DEVICE'),
(@tr7, DATE_SUB(NOW(), INTERVAL 1 DAY), -1.0, -18.0, 0.0, 0, '设备自动', 'DEVICE'),
(@tr15, DATE_SUB(NOW(), INTERVAL 6 DAY), -3.5, -18.0, 0.0, 0, '周司机', 'DEVICE'),
(@tr15, DATE_SUB(NOW(), INTERVAL 6 DAY), 8.0, -18.0, 0.0, 1, '设备自动', 'DEVICE')
ON DUPLICATE KEY UPDATE temperature=VALUES(temperature);

-- 门店签收：已有1条，新增15条
INSERT INTO store_receipt (transport_id, store_id, store_name, receipt_time, receiver, receiver_phone, qty_check, temp_check, temp_value, package_intact, receipt_photo, e_signature, content_hash) VALUES
(@tr1, 1001, '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 13 DAY), '孙店长', '13800000030', 1, 1, -3.0, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-001', SHA2(CONCAT('TR-TEST-0001', '1001'), 256)),
(@tr2, 1002, '顺鑫生鲜门店', DATE_SUB(NOW(), INTERVAL 11 DAY), '周店长', '13800000031', 1, 0, 2.5, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-002', SHA2(CONCAT('TR-TEST-0002', '1002'), 256)),
(@tr3, 1003, '绿康生鲜门店', DATE_SUB(NOW(), INTERVAL 9 DAY), '吴店长', '13800000032', 1, 1, -2.0, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-003', SHA2(CONCAT('TR-TEST-0003', '1003'), 256)),
(@tr4, 1004, '金牧生鲜门店', DATE_SUB(NOW(), INTERVAL 7 DAY), '陈店长', '13800000033', 0, 0, 5.0, 0, JSON_ARRAY(), 'TEST-RECEIVER-SIG-004', SHA2(CONCAT('TR-TEST-0004', '1004'), 256)),
(@tr5, 1005, '永发生鲜门店', DATE_SUB(NOW(), INTERVAL 5 DAY), '赵店长', '13800000034', 1, 1, -4.5, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-005', SHA2(CONCAT('TR-TEST-0005', '1005'), 256)),
(@tr6, 1006, '昌盛生鲜门店', DATE_SUB(NOW(), INTERVAL 3 DAY), '孙店长', '13800000035', 1, 1, -2.5, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-006', SHA2(CONCAT('TR-TEST-0006', '1006'), 256)),
(@tr7, 1007, '兴农生鲜门店', DATE_SUB(NOW(), INTERVAL 1 DAY), '周店长', '13800000036', 1, 1, -1.0, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-007', SHA2(CONCAT('TR-TEST-0007', '1007'), 256)),
(@tr15, 1001, '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 6 DAY), '孙店长', '13800000030', 0, 0, 8.0, 0, JSON_ARRAY(), 'TEST-RECEIVER-SIG-015', SHA2(CONCAT('TR-TEST-0015', '1001'), 256)),
(@tr8, 1008, '惠农生鲜门店', DATE_SUB(NOW(), INTERVAL 12 DAY), '吴店长', '13800000037', 1, 1, -3.5, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-008', SHA2(CONCAT('TR-TEST-0008', '1008'), 256)),
(@tr9, 1009, '福田生鲜门店', DATE_SUB(NOW(), INTERVAL 10 DAY), '郑店长', '13800000038', 1, 0, 1.5, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-009', SHA2(CONCAT('TR-TEST-0009', '1009'), 256)),
(@tr10, 1010, '大众生鲜门店', DATE_SUB(NOW(), INTERVAL 8 DAY), '王店长', '13800000039', 1, 1, -5.0, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-010', SHA2(CONCAT('TR-TEST-0010', '1010'), 256)),
(@tr11, 1011, '利农生鲜门店', DATE_SUB(NOW(), INTERVAL 6 DAY), '冯店长', '13800000040', 1, 1, -6.0, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-011', SHA2(CONCAT('TR-TEST-0011', '1011'), 256)),
(@tr12, 1012, '安康生鲜门店', DATE_SUB(NOW(), INTERVAL 4 DAY), '蒋店长', '13800000041', 1, 1, -4.5, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-012', SHA2(CONCAT('TR-TEST-0012', '1012'), 256)),
(@tr13, 1013, '丰禾生鲜门店', DATE_SUB(NOW(), INTERVAL 2 DAY), '韩店长', '13800000042', 1, 1, -2.5, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-013', SHA2(CONCAT('TR-TEST-0013', '1013'), 256)),
(@tr14, 1014, '宏图生鲜门店', NOW(), '杨店长', '13800000043', 1, 1, -1.0, 1, JSON_ARRAY(), 'TEST-RECEIVER-SIG-014', SHA2(CONCAT('TR-TEST-0014', '1014'), 256))
ON DUPLICATE KEY UPDATE content_hash=VALUES(content_hash);

-- ========== db_sales ==========

USE db_sales;

-- 零售终端产品：已有1条，新增15条
INSERT INTO retail_sale (split_batch_id, product_qr_code, store_id, store_name, shelf_time, sell_time, sell_price, sell_weight_kg, is_activated, activate_time, status, expire_date, block_hash) VALUES
(@sp1, 'QR-PORK-TEST-0001', 1001, '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 29.90, 0.53, 1, DATE_SUB(NOW(), INTERVAL 12 DAY), 2, DATE_ADD(CURDATE(), INTERVAL 5 DAY), NULL),
(@sp2, 'QR-PORK-TEST-0002', 1002, '顺鑫生鲜门店', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), 35.50, 0.48, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL),
(@sp3, 'QR-PORK-TEST-0003', 1003, '绿康生鲜门店', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 8 DAY), 1, DATE_ADD(CURDATE(), INTERVAL 7 DAY), NULL),
(@sp4, 'QR-PORK-TEST-0004', 1004, '金牧生鲜门店', DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL, NULL, 0, NULL, 0, DATE_ADD(CURDATE(), INTERVAL 10 DAY), NULL),
(@sp5, 'QR-PORK-TEST-0005', 1005, '永发生鲜门店', DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), 1, DATE_ADD(CURDATE(), INTERVAL 8 DAY), NULL),
(@sp6, 'QR-PORK-TEST-0006', 1006, '昌盛生鲜门店', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, DATE_ADD(CURDATE(), INTERVAL 12 DAY), NULL),
(@sp7, 'QR-PORK-TEST-0007', 1007, '兴农生鲜门店', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, DATE_ADD(CURDATE(), INTERVAL 9 DAY), NULL),
(@sp1, 'QR-PORK-TEST-0008', 1008, '惠农生鲜门店', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 28.80, 0.50, 1, DATE_SUB(NOW(), INTERVAL 11 DAY), 2, DATE_ADD(CURDATE(), INTERVAL 4 DAY), NULL),
(@sp2, 'QR-PORK-TEST-0009', 1009, '福田生鲜门店', DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 9 DAY), 1, DATE_ADD(CURDATE(), INTERVAL 6 DAY), NULL),
(@sp3, 'QR-PORK-TEST-0010', 1010, '大众生鲜门店', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 32.00, 0.45, 1, DATE_SUB(NOW(), INTERVAL 7 DAY), 2, DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL),
(@sp4, 'QR-PORK-TEST-0011', 1011, '利农生鲜门店', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, NULL, 0, NULL, 0, DATE_ADD(CURDATE(), INTERVAL 11 DAY), NULL),
(@sp5, 'QR-PORK-TEST-0012', 1012, '安康生鲜门店', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), 1, DATE_ADD(CURDATE(), INTERVAL 10 DAY), NULL),
(@sp6, 'QR-PORK-TEST-0013', 1013, '丰禾生鲜门店', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, DATE_ADD(CURDATE(), INTERVAL 8 DAY), NULL),
(@sp7, 'QR-PORK-TEST-0014', 1014, '宏图生鲜门店', NOW(), NULL, NULL, NULL, 0, NULL, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), NULL),
(@sp1, 'QR-PORK-TEST-0015', 1001, '安心生鲜门店', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 27.50, 0.55, 1, DATE_SUB(NOW(), INTERVAL 20 DAY), 3, DATE_SUB(CURDATE(), INTERVAL 2 DAY), NULL)
ON DUPLICATE KEY UPDATE status=VALUES(status);
SET @sale1 := (SELECT id FROM retail_sale WHERE product_qr_code='QR-PORK-TEST-0001');
SET @sale2 := (SELECT id FROM retail_sale WHERE product_qr_code='QR-PORK-TEST-0002');
SET @sale10 := (SELECT id FROM retail_sale WHERE product_qr_code='QR-PORK-TEST-0010');
SET @sale15 := (SELECT id FROM retail_sale WHERE product_qr_code='QR-PORK-TEST-0015');
SET @sale3 := (SELECT id FROM retail_sale WHERE product_qr_code='QR-PORK-TEST-0003');

-- 过期预警：已有0条，新增15条
INSERT INTO expire_warning (sale_id, warning_level, warning_time, notify_channel, notified, handled, handle_time, handler) VALUES
(@sale15, 3, DATE_SUB(NOW(), INTERVAL 5 DAY), 'SMS', 1, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), '孙店长'),
(@sale15, 2, DATE_SUB(NOW(), INTERVAL 7 DAY), 'SMS', 1, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), '孙店长'),
(@sale1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 'SMS', 1, 0, NULL, NULL),
(@sale2, 2, DATE_SUB(NOW(), INTERVAL 1 DAY), 'SMS', 1, 0, NULL, NULL),
(@sale10, 3, DATE_SUB(NOW(), INTERVAL 1 DAY), 'SMS', 1, 0, NULL, NULL),
(@sale3, 1, NOW(), 'SMS', 0, 0, NULL, NULL),
(@sale1, 2, NOW(), 'EMAIL', 0, 0, NULL, NULL),
(@sale2, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), 'SMS', 1, 0, NULL, NULL),
(@sale10, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), 'SMS', 1, 0, NULL, NULL),
(@sale15, 1, DATE_SUB(NOW(), INTERVAL 9 DAY), 'SMS', 1, 1, DATE_SUB(NOW(), INTERVAL 8 DAY), '孙店长'),
(@sale3, 2, DATE_ADD(NOW(), INTERVAL 1 DAY), 'SMS', 0, 0, NULL, NULL),
(@sale1, 3, DATE_ADD(NOW(), INTERVAL 2 DAY), 'SMS', 0, 0, NULL, NULL),
(@sale2, 3, DATE_ADD(NOW(), INTERVAL 3 DAY), 'EMAIL', 0, 0, NULL, NULL),
(@sale10, 2, DATE_SUB(NOW(), INTERVAL 2 DAY), 'SMS', 1, 0, NULL, NULL),
(@sale3, 3, DATE_ADD(NOW(), INTERVAL 4 DAY), 'SMS', 0, 0, NULL, NULL)
ON DUPLICATE KEY UPDATE warning_level=VALUES(warning_level);

-- 召回指令：已有0条，新增15条
INSERT INTO recall_order (recall_no, reason, risk_level, scope, initiator, initiate_time, status, completed_time, affected_count, recalled_count, block_hash) VALUES
('RC-TEST-0001', '检测发现瘦肉精残留超标', 3, '{"batchNo":"SP-TEST-0002","storeIds":[1002]}', '监管员王', DATE_SUB(NOW(), INTERVAL 10 DAY), 2, DATE_SUB(NOW(), INTERVAL 8 DAY), 15, 15, NULL),
('RC-TEST-0002', '消费者举报产品变质', 2, '{"batchNo":"SP-TEST-0001","storeIds":[1001]}', '孙店长', DATE_SUB(NOW(), INTERVAL 8 DAY), 2, DATE_SUB(NOW(), INTERVAL 6 DAY), 20, 20, NULL),
('RC-TEST-0003', '冷链温度异常导致产品受影响', 2, '{"batchNo":"SP-TEST-0004","storeIds":[1004]}', '监管员王', DATE_SUB(NOW(), INTERVAL 5 DAY), 1, NULL, 25, 18, NULL),
('RC-TEST-0004', '包装破损批量召回', 1, '{"batchNo":"SP-TEST-0006","storeIds":[1006]}', '吴店长', DATE_SUB(NOW(), INTERVAL 3 DAY), 1, NULL, 22, 10, NULL),
('RC-TEST-0005', '检验报告造假追溯', 3, '{"batchNo":"SP-TEST-0008","storeIds":[1001]}', '监管员王', DATE_SUB(NOW(), INTERVAL 2 DAY), 1, NULL, 25, 5, NULL),
('RC-TEST-0006', '过保质期仍在销售', 2, '{"batchNo":"SP-TEST-0002","storeIds":[1002]}', '消费者举报', DATE_SUB(NOW(), INTERVAL 1 DAY), 1, NULL, 15, 3, NULL),
('RC-TEST-0007', '运输环节交叉污染', 2, '{"batchNo":"SP-TEST-0010","storeIds":[1010]}', '监管员王', NOW(), 1, NULL, 12, 0, NULL),
('RC-TEST-0008', '标签信息不符', 1, '{"batchNo":"SP-TEST-0012","storeIds":[1006]}', '周店长', DATE_SUB(NOW(), INTERVAL 6 DAY), 3, DATE_SUB(NOW(), INTERVAL 4 DAY), 22, 0, NULL),
('RC-TEST-0009', '追溯码重复', 2, '{"batchNo":"SP-TEST-0014","storeIds":[1007]}', '监管员王', DATE_SUB(NOW(), INTERVAL 7 DAY), 2, DATE_SUB(NOW(), INTERVAL 5 DAY), 12, 12, NULL),
('RC-TEST-0010', '运输车辆消毒不达标', 1, '{"batchNo":"SP-TEST-0006","storeIds":[1013]}', '韩店长', DATE_SUB(NOW(), INTERVAL 4 DAY), 1, NULL, 22, 8, NULL),
('RC-TEST-0011', '批次混装违规', 2, '{"batchNo":"SP-TEST-0008","storeIds":[1004]}', '监管员王', DATE_SUB(NOW(), INTERVAL 9 DAY), 2, DATE_SUB(NOW(), INTERVAL 7 DAY), 25, 25, NULL),
('RC-TEST-0012', '温度记录缺失', 1, '{"batchNo":"SP-TEST-0010","storeIds":[1010]}', '监管员王', DATE_SUB(NOW(), INTERVAL 12 DAY), 3, DATE_SUB(NOW(), INTERVAL 10 DAY), 12, 0, NULL),
('RC-TEST-0013', '检疫证明伪造', 3, '{"batchNo":"SP-TEST-0004","storeIds":[1004]}', '监管员王', DATE_ADD(NOW(), INTERVAL 1 DAY), 1, NULL, 25, 0, NULL),
('RC-TEST-0014', '分割车间温度超标', 2, '{"batchNo":"SP-TEST-0012","storeIds":[1006]}', '孙店长', DATE_ADD(NOW(), INTERVAL 2 DAY), 1, NULL, 22, 0, NULL),
('RC-TEST-0015', '产品异物举报', 1, '{"batchNo":"SP-TEST-0014","storeIds":[1014]}', '杨店长', DATE_ADD(NOW(), INTERVAL 3 DAY), 1, NULL, 12, 0, NULL)
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- ========== db_common ==========

USE db_common;

-- 消费者举报：已有2条，新增15条
INSERT INTO complaint_report (report_no, reporter_name, reporter_phone, target_qr_code, target_batch, complaint_text, file_ids, status, handler, handle_note, handle_time, device_id) VALUES
('RP-TEST-0001', '张三', '13900000001', 'QR-PORK-TEST-0001', 'SP-TEST-0002', '买到的猪肉有异味，要求退货', JSON_ARRAY(), 0, NULL, NULL, NULL, 'dev-test-001'),
('RP-TEST-0002', '李四', '13900000002', 'QR-PORK-TEST-0002', 'SP-TEST-0004', '包装破损，产品不新鲜', JSON_ARRAY(), 1, '监管员王', '已联系门店处理', DATE_SUB(NOW(), INTERVAL 5 DAY), 'dev-test-002'),
('RP-TEST-0003', '王五', '13900000003', 'QR-PORK-TEST-0015', 'SP-TEST-0001', '产品已过期仍在销售', JSON_ARRAY(), 2, '监管员王', '已下架并罚款', DATE_SUB(NOW(), INTERVAL 3 DAY), 'dev-test-003'),
('RP-TEST-0004', '赵六', '13900000004', 'QR-PORK-TEST-0003', 'SP-TEST-0006', '二维码扫不出信息', JSON_ARRAY(), 0, NULL, NULL, NULL, 'dev-test-004'),
('RP-TEST-0005', '钱七', '13900000005', 'QR-PORK-TEST-0004', 'SP-TEST-0008', '肉质颜色异常发黑', JSON_ARRAY(), 1, '监管员王', '已送检，等待结果', DATE_SUB(NOW(), INTERVAL 2 DAY), 'dev-test-005'),
('RP-TEST-0006', '孙八', '13900000006', 'QR-PORK-TEST-0005', 'SP-TEST-0010', '标签信息与实际不符', JSON_ARRAY(), 2, '监管员王', '已核实，责令整改', DATE_SUB(NOW(), INTERVAL 1 DAY), 'dev-test-006'),
('RP-TEST-0007', '周九', '13900000007', 'QR-PORK-TEST-0006', 'SP-TEST-0012', '分量不足，少秤', JSON_ARRAY(), 0, NULL, NULL, NULL, 'dev-test-007'),
('RP-TEST-0008', '吴十', '13900000008', 'QR-PORK-TEST-0007', 'SP-TEST-0014', '发现异物，疑似毛发', JSON_ARRAY(), 1, '监管员王', '正在调查中', NOW(), 'dev-test-008'),
('RP-TEST-0009', '郑十一', '13900000009', 'QR-PORK-TEST-0008', 'SP-TEST-0002', '价格标示与结账不一致', JSON_ARRAY(), 2, '监管员王', '已退款并警告', DATE_SUB(NOW(), INTERVAL 4 DAY), 'dev-test-009'),
('RP-TEST-0010', '王十二', '13900000010', 'QR-PORK-TEST-0009', 'SP-TEST-0004', '冷链温度不达标，产品变质', JSON_ARRAY(), 0, NULL, NULL, NULL, 'dev-test-010'),
('RP-TEST-0011', '李十三', '13900000011', 'QR-PORK-TEST-0010', 'SP-TEST-0006', '分割批次信息查询不到', JSON_ARRAY(), 1, '监管员王', '系统数据同步中', NOW(), 'dev-test-011'),
('RP-TEST-0012', '张十四', '13900000012', 'QR-PORK-TEST-0011', 'SP-TEST-0008', '检疫章看不清', JSON_ARRAY(), 2, '监管员王', '已补盖清晰印章', DATE_SUB(NOW(), INTERVAL 6 DAY), 'dev-test-012'),
('RP-TEST-0013', '钱十五', '13900000013', 'QR-PORK-TEST-0012', 'SP-TEST-0010', '猪肉来源信息与溯源不一致', JSON_ARRAY(), 0, NULL, NULL, NULL, 'dev-test-013'),
('RP-TEST-0014', '孙十六', '13900000014', 'QR-PORK-TEST-0013', 'SP-TEST-0012', '门店拒绝提供检测报告', JSON_ARRAY(), 1, '监管员王', '已要求门店公示报告', NOW(), 'dev-test-014'),
('RP-TEST-0015', '周十七', '13900000015', 'QR-PORK-TEST-0014', 'SP-TEST-0014', '怀疑产品未经检验检疫', JSON_ARRAY(), 2, '监管员王', '已核实检验记录，合规', DATE_SUB(NOW(), INTERVAL 2 DAY), 'dev-test-015')
ON DUPLICATE KEY UPDATE status=VALUES(status);
SET @rp3 := (SELECT id FROM complaint_report WHERE report_no='RP-TEST-0003');
SET @rp5 := (SELECT id FROM complaint_report WHERE report_no='RP-TEST-0005');
SET @rp6 := (SELECT id FROM complaint_report WHERE report_no='RP-TEST-0006');

-- 用户通知：新增15条
INSERT INTO user_notification (user_id, title, content, type, read_status, biz_type, biz_id, read_time) VALUES
(1, '新举报待处理', '举报编号 RP-TEST-0001 待处理', 'warning', 0, 'complaint', @rp3, NULL),
(1, '举报已受理', '举报 RP-TEST-0002 已受理', 'info', 1, 'complaint', @rp5, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1, '温度异常预警', '运输 TR-TEST-0004 温度超标5.0℃', 'error', 0, 'transport', NULL, NULL),
(1, '召回指令通知', '召回 RC-TEST-0003 执行中，已完成18/25', 'warning', 0, 'recall', NULL, NULL),
(1, '产品过期预警', 'QR-PORK-TEST-0015 已过期，请尽快处理', 'error', 1, 'sale', @sale15, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, '检疫证明即将到期', 'QC-TEST-0001 有效期仅剩1天', 'warning', 0, 'quarantine', NULL, NULL),
(1, '出栏申报待审批', 'SA-TEST-0003 待审批', 'info', 0, 'slaughter_apply', NULL, NULL),
(1, '区块链上链成功', 'SP-TEST-0002 分割批次已上链', 'success', 1, 'blockchain', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, '冷链签收完成', 'TR-TEST-0007 已签收，温度正常', 'success', 1, 'receipt', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, '举报处理完成', '举报 RP-TEST-0003 已处理完毕', 'info', 1, 'complaint', @rp6, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1, '瘦肉精检测异常', 'RT-TEST-0003 检测结果阳性，请立即处理', 'error', 0, 'ractopamine', NULL, NULL),
(1, '温度打卡提醒', 'TR-TEST-0008 运输中，请按时温度打卡', 'info', 0, 'transport', NULL, NULL),
(1, '召回进度更新', 'RC-TEST-0004 已召回10/22', 'warning', 0, 'recall', NULL, NULL),
(1, '过期预警通知', 'QR-PORK-TEST-0010 即将过期', 'warning', 0, 'sale', @sale10, NULL),
(1, '系统维护通知', '系统将于今晚22:00-23:00进行维护', 'info', 1, 'system', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY))
ON DUPLICATE KEY UPDATE read_status=VALUES(read_status);
