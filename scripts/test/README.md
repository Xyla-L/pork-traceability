# B 档自动化录入 · 测试脚本

> 目的：在没有真实硬件的前提下，用「设备模拟器」把 B 档接入链路先跑通；
> 接口全通后再上单台真机灰度。**不要等真机到位才开始测。**

## 前置条件

1. 后端全套服务已启动（`cd backend && docker compose up -d`），网关可达 `http://localhost:8080`
2. 数据库已执行 `backend/init-sql/11-device-ingest.sql`（**仅全新初始化时自动执行**；
   已有库需手动补执行，见文末）
3. 演示设备台账已写入（`11-device-ingest.sql` 末尾的 `INSERT`）

## 一、冒烟：确认接入服务活着

```bash
node scripts/test/device-simulator.mjs ping TEMP-001 dev-key-temp-001
# 期望：返回 deviceNo=TEMP-001、channel=TEMPERATURE，说明密钥有效、通道登记正确
```

## 二、单通道手工验证

| 通道 | 命令 |
|---|---|
| 冷链温度 | `node scripts/test/device-simulator.mjs temp TEMP-001 dev-key-temp-001 <运单号> 22.5` |
| 连续上报 | `node scripts/test/device-simulator.mjs burst TEMP-001 dev-key-temp-001 <运单号> 10 2` |
| 入场查验 | `node scripts/test/device-simulator.mjs entry RFID-001 dev-key-rfid-001 ET-DEMO-0001 BATCH-TEST-01` |
| 瘦肉精 | `node scripts/test/device-simulator.mjs racto READER-001 dev-key-reader-001 ET-DEMO-0001 SAMPLE-001 1 BATCH-TEST-01` |
| 收银售出 | `node scripts/test/device-simulator.mjs sale POS-001 dev-key-pos-001 QR-PORK-XXXX 38.5 0.75` |
| 门店签收 | `node scripts/test/device-simulator.mjs receipt PDA-001 dev-key-pda-001 <运单号> 示范一店 张三 2.5` |

设备上报后，到「设备接入 → 待确认队列」查看处理结果（通道 / 状态 / 原因 / 原始报文）。

## 三、验收测试（一次跑完全部关键用例）

```bash
node scripts/test/ingest-acceptance-test.mjs
# 换网关地址：node scripts/test/ingest-acceptance-test.mjs --base http://localhost/api/v1
# 没有"运输中"运单时，让测试自动发车一条待发车任务（会改变演示数据状态）：
node scripts/test/ingest-acceptance-test.mjs --auto-depart
```

覆盖用例：

| # | 用例 | 判定标准 |
|---|---|---|
| 1 | 设备密钥鉴权 | 错误密钥 → `code=2001`（网关层为 401） |
| 2 | 幂等 | 同 `bizKey` 连推 3 次，业务表只新增 1 条，后两次 `duplicate=true` |
| 3 | 脏数据不入库 | `-999` / `abc` → `status=3`，业务表新增 0 条 |
| 4 | 异常温度保留 | `6.6℃`（超 -18~0）→ 入账且 `is_abnormal=1` |
| 5 | 降采样 | 非异常值间隔过密 → `status=4` 丢弃但留档 |
| 6 | 仪器未出结果 | 只推耳标不推 `result` → `status=3` 拒绝 |
| 6.1 | 瘦肉精正常入账 | 出了结果 → `status=2`，`ractopamine_test.source=DEVICE` |
| 7 | 降级可用 | 人工录入接口 `code=200`，未被接入层波及 |
| 8 | 入场查验卡关 | 检疫证核验未过 → `status=1` 转人工，业务表新增 0 条 |
| 8.1 | 人工确认放行 | `approve` 后 → `status=2`，`entry_inspection.source=DEVICE` |
| 9 | 门店收银 | POS 扫码 → `status=2`，销售记录 `source=DEVICE` 且已售出 |
| 10 | 门店签收 | PDA 签收 → 生成签收单 `source=DEVICE`，运单 1→4（已签收） |

> 用例 2 需要一条 `status=2`（运输中）的运单；没有的话测试会直接报「前置条件不满足」，
> 去「分割配送 → 冷链运输」给一条待发车任务点「发车」即可。
>
> 用例 10 会**自己新建**一条运单再发车→到达→签收，不捡现成数据——
> 演示库里存在"状态被重置但签收单还在"的历史运单，直接复用会撞「重复签收」。

**关于测试数据**：1~7 只做只读断言（依据「新增条数」与「接口回执」），不污染演示数据。
8~10 会**真实写入**业务表——这正是验收目的：证明设备端能写业务表且 `source` 标记正确。
前置数据缺失时这些用例记 `SKIP`，不计为失败。

设备侧唯一号统一带时间戳前缀，跑完可在「设备接入 → 待确认队列」按前缀筛出本轮数据。

## 四、真机灰度

模拟器全量跑通后：

1. **单台真机接入**（先一条运输线或一个门禁），跑一周
2. **双写比对**——设备自动录入与人工照旧录并行，比对差异率（这是最有说服力的验收证据）
3. 差异率达标后才切「以设备为准」，**人工入口永久保留作兜底**

## 已有库如何补执行 DDL

`init-sql/` 只在 MySQL 数据卷**首次初始化**时执行。已经有数据的库要手动补：

```bash
docker exec -i pork-traceability-mysql-1 mysql -uroot -p<root密码> < backend/init-sql/11-device-ingest.sql
```

脚本用 `CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`，重复执行安全；
但 `ALTER TABLE ... ADD COLUMN` 在同一库上执行第二次会报「列已存在」，属正常现象。

## 接口约定（给真机对接方）

**上报**：`POST /api/v1/ingest/device/report`
请求头 `X-Device-Key: <设备密钥>`，报文：

```json
{
  "channel": "TEMPERATURE",
  "bizKey": "设备侧唯一号（重发时必须保持不变，是幂等键）",
  "reportTime": "2026-09-21 10:30:00",
  "sourceRef": "单据号/流水号（可空）",
  "data": { "transportNo": "CH20260921001", "temperature": 22.5 }
}
```

**回执**：`{ stagingId, channel, bizKey, status, statusLabel, accepted, targetTable, targetId, message, duplicate }`

设备端按此决定能否删除本地缓存：

| status | 含义 | 设备端应做什么 |
|---|---|---|
| 2 | 已入账 | 删除本地缓存 |
| 1 | 待人工处理 | 删除本地缓存，**不要重推**（重推也不会入账） |
| 3 | 已拒绝（数据本身不成立） | 删除本地缓存，需人工修正后重录 |
| 4 | 已降采样丢弃 | 删除本地缓存（异常值永不丢弃，此状态只针对非异常值） |

**时间格式**：一律 `yyyy-MM-dd HH:mm:ss`，**不要用 ISO 的 `T` 分隔**（后端按该格式解析）。

**各通道 data 字段**：

| channel | 必填 | 选填 |
|---|---|---|
| `TEMPERATURE` | `transportNo`、`temperature` | `transportId`、`recordTime`、`recorder` |
| `ENTRY` | `earTagNo`、`batchNo` | `weight`、`vehicleNo`、`quarantineCert`、`healthCheck`、`abnormalNote` |
| `RACTOPAMINE` | `earTagNo`、`sampleNo`、`result`（1阴性/0阳性）、`batchNo` | `testMethod`、`testType`、`testTarget`、`samplePart`、`reportUrl` |
| `SALE` | `qrCode` | `sellPrice`、`sellWeightKg` |
| `RECEIPT` | `transportNo`、`storeName`、`receiver` | `storeId`、`receiverPhone`、`tempValue`、`qtyCheck`、`tempCheck`、`packageIntact`、`signature` |

> `RACTOPAMINE.result` 缺失会被直接拒绝（`status=3`）：机器不得替兽医签「阴性」结论。
> `RACTOPAMINE.batchNo` 也一样必填——检测记录必须能归属到某一批猪，缺批次只能挡住，
> 否则会在写库阶段炸成「系统内部异常」，设备端拿到 500 只会以为是网络问题而反复重推。
> `RECEIPT.receiver` 缺失会进「待人工处理」：签收是责任转移点，设备不能替人担责。
