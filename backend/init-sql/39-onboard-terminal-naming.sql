-- ---------------------------------------------------------------------------
-- 39) 车载终端系列命名统一
--
-- 背景：车载终端是**一个系列**（同一台设备承担「发车确认 + 到站刷卡确认」两个通道，
--       见 ingest-service 的 IngestChannel.deviceGroup() / sameDeviceAs()），
--       但三行的展示名写法不统一，在设备列表里看不出它们是同一种设备：
--         · DEPART-001  「车载终端 京A·12345」（空格分隔）
--         · DEPART-003  「车载终端 京A·12346」（空格分隔）
--         · ARRIVE-001  「车载终端（发车 / 到站刷卡）」← 没有车号，像个「主终端」
--       统一成「车载终端（…）」，系列一眼可见。
--
-- 只改 device_name / location / remark，**不动 device_no**：
--   回归脚本（verify-depart*、verify-arrive-stop*）、前端演示默认设备、
--   历史暂存行（ingest_staging.device_no）都指向这些号码；改号换不来业务收益，
--   反而让「同一台车的同一个终端」看起来像换了设备（迁移 38 已论证过一次）。
--
-- 三行的角色：
--   · DEPART-001 → 京A·12345 这一台上的终端（vehicle_no 是结构化的车辆绑定）
--   · DEPART-003 → 京A·12346 这一台上的终端（同上）
--   · ARRIVE-001 → 演示 / 回归用的**通用终端**，有意不绑车
--      「不绑车」不是漏配：车牌由报文里的 vehicleNo 带入（接入层 vehicleOf() 报文优先、
--      台账兜底），而且「报文与台账都没有车牌 → 拒绝」这条红线用例正靠它成立。
--
-- 幂等：三条都是按 device_no 定位的赋值式 UPDATE，重复执行结果一致。
-- ---------------------------------------------------------------------------

UPDATE db_ingest.ingest_device
SET device_name = '车载终端（京A·12345）',
    location    = '一号冷藏车驾驶室（司机上车刷卡 / 到站刷卡）',
    remark      = '车载终端：同一台设备承担两个通道 —— 发车确认（司机上车刷卡，把责任落到具体人）与到站刷卡确认（每刷一次推进一站）。两通道同组，见 IngestChannel.deviceGroup()。装在本行 vehicle_no 记的那台车上（结构化绑定，不靠设备名里的字符串猜）。'
WHERE device_no = 'DEPART-001';

UPDATE db_ingest.ingest_device
SET device_name = '车载终端（京A·12346）',
    location    = '二号冷藏车驾驶室（司机上车刷卡 / 到站刷卡）',
    remark      = '车载终端：同上，装在本行 vehicle_no 记的那台车上。'
WHERE device_no = 'DEPART-003';

UPDATE db_ingest.ingest_device
SET device_name = '车载终端（通用 · 演示 / 回归）',
    location    = '演示 / 回归用（不绑车，车牌以报文为准）',
    remark      = '车载终端：同上，一台设备承担两个通道。本行**有意不绑车牌** —— 它是演示 / 回归用的通用终端，车牌由报文里的 vehicleNo 带入（接入层 vehicleOf()：报文优先、台账兜底）；「报文与台账都没有车牌 → 拒绝」这条红线用例也正靠它成立。真机上的车载终端都绑车，见 DEPART-001 / DEPART-003。'
WHERE device_no = 'ARRIVE-001';

-- 核验：三台车载终端的名称、绑定车牌与所在位置
SELECT device_no, device_name, IFNULL(vehicle_no, '(不绑车)') AS vehicle_no, channel, location
FROM db_ingest.ingest_device
WHERE device_no IN ('DEPART-001', 'DEPART-003', 'ARRIVE-001')
ORDER BY device_no;

SELECT '车载终端行数（合并不增删行，应为 3）' AS item, CAST(COUNT(*) AS CHAR) AS v
FROM db_ingest.ingest_device WHERE device_name LIKE '车载终端%'
UNION ALL
SELECT '设备台账总行数（应为 23）', CAST(COUNT(*) AS CHAR) FROM db_ingest.ingest_device;
