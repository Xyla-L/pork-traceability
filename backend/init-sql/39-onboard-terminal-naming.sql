-- ---------------------------------------------------------------------------
-- 39) 车载终端系列命名统一
--
-- 背景：车载终端是**一个系列**（同一台设备承担「发车确认 + 到站刷卡确认」两个通道，
--       见 ingest-service 的 IngestChannel.deviceGroup() / sameDeviceAs()），
--       历史上按车登记了 DEPART-001 / DEPART-003 两行绑车终端，外加一台通用终端，
--       展示名写法不统一，在设备列表里看不出它们是同一种设备。
--
-- 2026-10-24 设备收敛：DEPART-001 / DEPART-003 已删（报文与 GATE/TEMP 收敛同理——
--       通道由报文自带，能力校验按 deviceGroup()，绑车台账只是 vehicleOf() 的兜底，
--       演示场景一台通用终端就能覆盖全部用例）。本脚本保留的只剩 ARRIVE-001 一行。
--       对已删设备的 UPDATE 落空 0 行，无害，故不删除历史语句。
--
-- 只改 device_name / location / remark，**不动 device_no**：
--   回归脚本（verify-arrive-stop*）、前端演示默认设备、
--   历史暂存行（ingest_staging.device_no）都指向这些号码；改号换不来业务收益，
--   反而让「同一台车的同一个终端」看起来像换了设备（迁移 38 已论证过一次）。
--
-- ARRIVE-001 → 演示 / 回归用的**通用终端**，有意不绑车
--   「不绑车」不是漏配：车牌由报文里的 vehicleNo 带入（接入层 vehicleOf() 报文优先、
--   台账兜底），而且「报文与台账都没有车牌 → 拒绝」这条红线用例正靠它成立。
--
-- 幂等：按 device_no 定位的赋值式 UPDATE，重复执行结果一致。
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
    remark      = '车载终端：唯一一台，同时承担发车确认与到站刷卡确认两个通道（IngestChannel.deviceGroup() 同组，报文自带 channel 说明本次是哪个动作）。本行**有意不绑车牌** —— 车牌由报文里的 vehicleNo 带入（接入层 vehicleOf()：报文优先、台账兜底）；「报文与台账都没有车牌 → 拒绝」这条红线用例也正靠它成立。真机部署时按车登记、在 vehicle_no 里结构化绑定车牌即可，不必新增通道。'
WHERE device_no = 'ARRIVE-001';

-- 核验：车载终端只剩一台
SELECT device_no, device_name, IFNULL(vehicle_no, '(不绑车)') AS vehicle_no, channel, location
FROM db_ingest.ingest_device
WHERE device_no IN ('DEPART-001', 'DEPART-003', 'ARRIVE-001')
ORDER BY device_no;

SELECT '车载终端行数（2026-10-24 收敛后应为 1）' AS item, CAST(COUNT(*) AS CHAR) AS v
FROM db_ingest.ingest_device WHERE device_name LIKE '车载终端%'
UNION ALL
SELECT '设备台账总行数（收敛后应为 18）', CAST(COUNT(*) AS CHAR) FROM db_ingest.ingest_device;
