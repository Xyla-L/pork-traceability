-- ===========================================================================
-- 33 号迁移：装车口去掉「司机刷卡终端」（现场只在上车那一刻刷一次卡）
--
-- 背景：前几轮把「刷卡」这件事放了两处 —— 装车位一次（LOAD 报文里 driverSource=CARD）、
-- 驾驶室一次（DEPART 车载终端）。现场确认实际**只有上车那一次**刷卡，所以本轮收敛：
--
--   1) 设备台账：LOADER-001 从「三件套」退回「两件套」——只留车牌识别相机 + 扫码枪。
--      device_no 仍是**点位标识**（1 号装车位），不新增/不删除设备号。
--   2) 装车口不再有读卡器：LOAD 报文带 cardNo 一律按**结构性拒绝**挡下
--      （不是静默忽略：报了卡号说明设备接错点位，把驾驶室的读卡器算到装车位了，早报错早修）。
--      见 ingest-service 的 LoadChannelHandler.validate 与 distribution-service 的 ingestLoad。
--   3) 司机在装车口**允许留空**（装车时司机还没上车，也没人能替他证明身份）：
--      建单照常，不再因此转人工；「货要走了，责任必须落到具体人」那颗闸门**后移到发车** ——
--      人工「立即发车」(depart) 与车载终端刷卡 (DEPART) 都会拦住没有司机的运单。
--
-- 影响面：仅台账文案 + 校验口径，**无表结构变化、无数据回填**。
-- 幂等：UPDATE 本身幂等，重复执行结果一致。
-- ===========================================================================

UPDATE db_ingest.ingest_device
SET device_name = '1号装车位（车牌识别相机 + 扫码枪）',
    location = '示范定点屠宰场 冷库 1号装车位',
    remark = '装车口的点位口径：相机与扫码枪共同构成一次「装车出场」事件，所以是**一个通道**（LOAD），但不是**一台设备** —— 相机面朝车道、扫码枪在装卸位，安装位置与故障模式各不相同，报文里用 vehicleSource 标注车牌来源。'
             '此处**没有读卡器**：现场只在上车那一刻刷一次卡，司机身份由车载终端（DEPART 通道）确认，所以装车报文的司机可留空、且不应携带卡号（带了按结构性拒绝挡下）。'
             '目的地不由设备决定，匹配不上转人工。'
WHERE device_no = 'LOADER-001';

-- 核对：装车口应只剩相机 + 扫码枪，且全库「司机刷卡终端」只剩车载发车终端
SELECT device_no, device_name, channel, location FROM db_ingest.ingest_device WHERE device_no = 'LOADER-001';
SELECT device_no, device_name, channel FROM db_ingest.ingest_device WHERE channel IN ('LOAD', 'DEPART') ORDER BY channel, device_no;
SELECT COUNT(*) AS 装车口还写着读卡器的设备数 FROM db_ingest.ingest_device WHERE channel = 'LOAD' AND device_name LIKE '%读卡%';
