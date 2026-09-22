-- 修复 ingest_device 中文列的双重编码（mojibake）
-- 成因：11-device-ingest.sql 手动补执行时，PowerShell 管道按 ANSI(GBK) 读取 UTF-8 文件，
--       导致"UTF-8 字节 → Latin-1 字符 → 再按 UTF-8 存回"的双重编码。
-- 影响面：经逐表核查，仅 db_ingest.ingest_device 的 device_name/location/remark 三列受影响，
--         其余 16 张表中文列均正常（Java/JDBC 写入通道无此问题）。
-- 执行方式：必须走  docker cp + 容器内 mysql <文件  （二进制安全），不要用 PowerShell 管道。
USE db_ingest;

UPDATE ingest_device SET device_name='冷藏车温度探头 京A·12345', location='一号冷藏车车厢',   remark='车载温控终端，30秒上报一次' WHERE device_no='TEMP-001';
UPDATE ingest_device SET device_name='冷库温度探头 成品库A',    location='分割车间成品库A',   remark='冷库固定探头，5分钟上报一次' WHERE device_no='TEMP-002';
UPDATE ingest_device SET device_name='冷藏车温度探头 京A·12346', location='二号冷藏车车厢',   remark='车载温控终端' WHERE device_no='TEMP-003';
UPDATE ingest_device SET device_name='入场门禁地磅',            location='屠宰场北门',       remark='车牌识别 + 地磅称重' WHERE device_no='GATE-001';
UPDATE ingest_device SET device_name='耳标识读器',              location='屠宰场入场通道',   remark='读取电子耳标，带出来源养殖场' WHERE device_no='RFID-001';
UPDATE ingest_device SET device_name='检疫证核验终端',          location='屠宰场入场通道',   remark='扫描检疫证二维码核验' WHERE device_no='CERT-001';
UPDATE ingest_device SET device_name='瘦肉精读数仪',            location='屠宰场化验室',     remark='胶体金读数仪，串口输出结果' WHERE device_no='READER-001';
UPDATE ingest_device SET device_name='门店收银机 示范一店',      location='示范一店门店',     remark='扫码即激活+售出' WHERE device_no='POS-001';
UPDATE ingest_device SET device_name='门店签收PDA 示范一店',     location='示范一店收货口',   remark='扫码签收 + 冷柜测温' WHERE device_no='PDA-001';
UPDATE ingest_device SET device_name='门店冷柜温度探头',        location='示范一店冷柜',     remark='签收环节温度采集' WHERE device_no='SHELF-001';

-- 校验：应当 0 行匹配（无残留 mojibake 特征）
SELECT SUM(HEX(device_name) LIKE '%C3A5%' OR HEX(device_name) LIKE '%C3A8%' OR HEX(device_name) LIKE '%C3A9%') AS bad_name,
       SUM(HEX(location)    LIKE '%C3A5%' OR HEX(location)    LIKE '%C3A8%' OR HEX(location)    LIKE '%C3A9%') AS bad_loc,
       SUM(HEX(remark)      LIKE '%C3A5%' OR HEX(remark)      LIKE '%C3A8%' OR HEX(remark)      LIKE '%C3A9%') AS bad_remark
FROM ingest_device;

SELECT device_no, device_name, location, CHAR_LENGTH(device_name) AS chars, LENGTH(device_name) AS bytes
FROM ingest_device ORDER BY id;
