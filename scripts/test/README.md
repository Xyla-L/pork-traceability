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
| 7 | 采集边界 | 采集环节的人工新增端点均为 `405`（或被包装为 `code=5000`；路径只剩 GET，写方法已删除），只认设备通道 |
| 8 | 入场查验卡关 | 检疫证核验未过 → `status=1` 转人工，业务表新增 0 条 |
| 8.1 | 人工确认放行 | `approve` 后 → `status=2`，`entry_inspection.source=DEVICE` |
| 9 | 门店收银 | POS 扫码 → `status=2`，销售记录 `source=DEVICE` 且已售出 |
| 10 | 门店签收 | PDA 签收 → 生成签收单 `source=DEVICE`，运单 1→4（已签收） |
| 11 | 养殖建档（耳标） | 佩戴即建档 `data_source=DEVICE`；同耳标重推幂等返回既有档案 |
| 12 | 免疫注射 | 缺疫苗批号 → 拒绝；带批号入账且 `vaccine_record.source=DEVICE` |
| 13 | 屠宰检验 | 终端未给结论 → 拒绝（不得默认合格）；带结论+兽医入账且 `source=DEVICE` |
| 14 | 胴体盖章 | 无合格检验 → 拒绝（人工确认也绕不过）；检验合格后盖章入账 `source=DEVICE` |
| 15 | 分割批次 | 胴体批次不存在 → 转人工；扫码称重自动建批次（批次号系统生成）`source=DEVICE` |

> 用例 2 需要一条 `status=2`（运输中）的运单；没有的话测试会直接报「前置条件不满足」，
> 去「分割配送 → 冷链运输」给一条待发车任务点「发车」即可。
>
> 用例 10 会**自己新建**一条运单再发车→到达→签收，不捡现成数据——
> 演示库里存在"状态被重置但签收单还在"的历史运单，直接复用会撞「重复签收」。

**关于测试数据**：1~7 只做只读断言（依据「新增条数」与「接口回执」），不污染演示数据。
8~10 会**真实写入**业务表——这正是验收目的：证明设备端能写业务表且 `source` 标记正确。
前置数据缺失时这些用例记 `SKIP`，不计为失败。

设备侧唯一号统一带时间戳前缀，跑完可在「设备接入 → 待确认队列」按前缀筛出本轮数据。

## 四、来源管控验收（数据采集类是不是真的只认设备）

```bash
node scripts/test/source-guard-test.mjs
# 覆盖 16 个用例，全部只读断言 + 接入层留档，不写入业务数据
```

判据不是「返回一句拒绝」，而是**端点本身不存在**：人工新增的 `@PostMapping` 已整条删除，
只剩 `@GetMapping`（列表/详情），POST 会撞上 405 语义的 `HttpRequestMethodNotSupportedException`
——被全局异常处理器兜底包装成 HTTP 200 + `code=5000`。无论哪种形态，请求都在**方法匹配阶段**
就被排除，业务代码根本没执行。

| # | 用例 | 判定标准 |
|---|---|---|
| 1 | 策略接口 | `GET /ingest/source-policy` 返回 ≥11 个环节 |
| 2 | 设备独占登记 | 入场查验/瘦肉精/屠宰检验/胴体盖章/养殖建档/免疫注射/分割批次/冷链温度 共 8 个环节 `manualBlocked=true` 且都有设备通道 |
| 3 | 唯一人工口 | 产地检疫无设备通道 → 策略「设备优先」，人工入口保留但须先兜底解锁 |
| 4 | 屠宰检验端点 | `POST /slaughter/inspections` → `405`（端点已下线；可能被包装为 `code=5000`） |
| 5 | 胴体盖章端点 | `POST /slaughter/stamps` → `405` |
| 6 | 瘦肉精端点 | `POST /slaughter/ractopamine` → `405` |
| 7 | 入场查验端点 | `POST /slaughter/entries` → `405` |
| 8 | 养殖建档端点 | `POST /breeding/pigs` → `405` |
| 9 | 免疫注射端点 | `POST /breeding/pigs/{id}/vaccines` → `405` |
| 10 | 分割批次端点 | `POST /distribution/splits` → `405` |
| 11 | 冷链温度端点 | `POST /distribution/transports/{id}/temperature` → `405` |
| 12 | 没偷偷写入 | 上述调用之后检验记录总数不变（请求连方法匹配都没进） |
| 13 | 设备通道不受影响 | 关键环节设备上报仍进接入层（照收、照留档），不因策略被拒 |
| 14 | 复核必须写理由 | 关键环节待人工数据不填 `remark` → `code=1002` |
| 15 | 实名复核可走通 | 填理由后能进入复核流程（不被管控规则挡在门外） |
| 16 | 人工口受控 | 唯一保留的人工入口（产地检疫出证）无票据 → 被拒（提示含「兜底管控」） |

> 设计说明见 `docs/来源管控与上链可信方案.md`。
> 本脚本不写入业务数据；只有用例 13 会在接入层留一条「待人工处理」队列记录，属正常留档。

## 五、作废留痕 + 兜底解锁验收

```bash
node scripts/test/void-fallback-test.mjs
# 覆盖 17 个用例，验证「唯一人工口须先申请 / 结论只能公开作废 / 追溯只取有效记录」
```

| # | 用例 | 判定标准 |
|---|---|---|
| 1 | 人工口已收敛 | 全项目只剩 1 个「设备优先」环节（`QUARANTINE`） |
| 2 | 申请理由过短 | 少于 4 个字 → `code=1002` |
| 3 | 设备独占不可申请 | `TAG` 申请解锁被拒（提示含「设备独占」，该环节人工入口已删除） |
| 4 | 正常申请 | 返回 `ticket`（前缀 `v1\|`）/ `expireTime` / `applicant` |
| 5 | 解锁记录留档 | 列表可见，状态「生效中」 |
| 6 | 无票据出证 | 产地检疫出证无票据 → 被拒（提示含「兜底管控」） |
| 7 | 伪造票据 | 结构像样但签名对不上 → 被 HMAC 校验识破并拒绝 |
| 8 | 带票据出证 | 放行，可查到当前有效检疫证 |
| 9 | 作废理由过短 | 1 个字 → `code=1001/1002` |
| 10 | 无票据作废 | 设备优先环节作废同样被拒（作废与新增同级受控） |
| 11 | 带票据作废 | 放行 |
| 12 | 作废后不可见 | 不再作为当前有效检疫证返回（追溯取当前有效记录，库里仍在，未物理删） |
| 13 | 结论字段禁改 | 设备写入的疫苗记录改「疫苗批号」→ 被拒（提示含「结论保护」） |
| 14 | 说明性字段可改 | 只改附件 → 放行 |
| 15 | 设备独占可作废 | 设备写入的瘦肉精记录，实名 + 理由可作废（不需票据） |
| 16 | 追溯作废标注 | `/slaughter/internal/pigs` 返回 `voided`，含环节 / 单号 / 理由 / 操作人 / 时间 |
| 17 | 提前关闭解锁 | 解锁记录可关闭并留痕（状态「已关闭」+ 关闭人 + 关闭理由） |

> 前置：数据库需已执行 `backend/init-sql/16-void-and-fallback.sql`（作废四列 + 兜底解锁表）。
> 该脚本会写入 1 张产地检疫证并作废它、由设备通道写入 1 条疫苗记录与 1 条瘦肉精记录、
> 留下若干条解锁申请——这些都是「留痕」的一部分，不构成脏数据；
> 检疫证与瘦肉精记录都走正常作废流程，不会被物理删除。

## 六、真机灰度

模拟器全量跑通后：

1. **单台真机接入**（先一条运输线或一个门禁），跑一周
2. **双写比对**——设备自动录入与原有录入并行，比对差异率（这是最有说服力的验收证据）
3. 差异率达标后才切「以设备为准」——本项目已把 8 个采集环节的人工新增入口**整条删除**，
   只保留产地检疫出证这一处（法定由官方兽医签发、暂无设备通道，须实名 + 兜底解锁）

## 七、数据维护：清理验收测试残留

验收脚本反复运行会在库里留下**状态脱节的测试样本**——典型形态是用设备通道建了档、
又被后续用例拿去走屠宰检验推进成「已屠宰」、却从未做过入场查验的猪。
这类样本会让演示数据看起来「流程对不上」。清理脚本按项目原则**只作废、不删除**：

```bash
node scripts/test/cleanup-test-pigs.mjs
# 可选：--prefix ET-ACC-   --user admin --pass ******   --base http://localhost:8080/api/v1
```

它会按耳标前缀找出测试残留猪，连同其免疫记录（设备优先 → 需兜底票据）、
屠宰检验与胴体盖章记录（设备独占 → 实名 + 理由）一并作废，并逐条打印结果。

> 脚本对「需不需要票据」是**按当前策略动态判断**的：某个环节若从设备优先改成设备独占，
> 它会自动跳过票据申请，不会因为策略调整而失败。

## 八、追溯链一致性体检 + 修复

上面几套验收脚本都会用设备通道**真造数据**，但这些样本只走了被测的那一两个环节，
天然缺前置环节（典型：设备建档 + 屠宰检验 + 盖章，却没有入场查验），
于是库里会留下「流程对不上」的追溯链。体检 + 修复是固定动作：

```bash
# 1) 体检（只读）：八个悬空引用口径 + 有效猪链路完整性矩阵 + 作废传导 + 数据规模
#    期望「违规行 0 行、悬空引用全 0」
docker cp logs/verify-reconcile.sql pork-traceability-mysql-1:/tmp/vr.sql
docker exec pork-traceability-mysql-1 sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 --table < /tmp/vr.sql'

# 2) 修复（幂等，可反复执行）：补齐缺口的屠宰检验/瘦肉精 + 作废沿链向下传导
docker cp backend/init-sql/17-reconcile-chains.sql pork-traceability-mysql-1:/tmp/17.sql
docker exec pork-traceability-mysql-1 sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 < /tmp/17.sql'

# 3) 应用侧回归：作废是否真的从接口消失、扫码已作废是否给出理由
node logs/verify-reconcile.mjs
```

> **跑完 `scripts/test/` 下的任一验收脚本后，务必再跑上面这三步。**
> 推荐顺序：`cleanup-test-pigs.mjs`（作废测试残留猪）→ 17 号脚本（把作废传导下去）→ 体检。
>
> 17 号脚本会做四件事：① 给"有章却缺检验"的有效猪补录宰前/宰后检验与瘦肉精（按盖章状态
> 推定结论，合格才盖得出章）；② 给 `carcass_batch` 补上 16 号漏掉的作废四列；
> ③ 把"关联生猪全部已作废"的胴体批次连同其派生分割批次、零售商品逐层作废；
> ④ 以上动作全部上链，并在末尾打印自检。
>
> ⚠ 17 号脚本对 `split_batch.parent_batch_id` 是**按 `split_level` 分流**的：
> `split_level=1` 的父级是 `carcass_batch`，`>=2` 的父级是上一层 `split_batch`，
> 两表 id 空间重叠。改这脚本时别把分流条件拿掉，否则会连坐作废有效批次。

## 九、机构一致性：体检 + 补齐

「机构管理」（`sys_org`）与业务档案（`farm` / `slaughterhouse`）是两套登记。
若只往业务档案加机构、没同步 `sys_org`，就会出现**机构管理里「别的页面能选到的机构这里没有」**。

```bash
# 1) 体检（只读）：机构树的养殖场/屠宰场数量 vs 业务档案数量，应逐项相等
node logs/verify-org-sync.mjs

# 2) 补齐（幂等，可反复执行）：把业务档案里尚未登记的机构补进 sys_org
docker cp backend/init-sql/20-sync-org-from-business.sql pork-traceability-mysql-1:/tmp/20.sql
docker exec pork-traceability-mysql-1 sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 --table < /tmp/20.sql'
```

> **正常情况下不需要手动跑 20 号脚本**：养殖场档案的增 / 改 / 删已经会自动同步机构
> （`breeding-service` → `auth-service` 的 `/auth/internal/orgs`，容器内直连、不经网关）。
> 该脚本用于：① 存量库一次性补齐；② 同步链路临时不可用时兜底。
>
> 同步验证：`node logs/verify-org-auto-sync.mjs`
> （临时建一条养殖场 → 确认机构出现 → 改名 → 确认机构改名 → 删除 → 确认机构移除，
> 最后清理现场；并校验网关不转发 `/api/v1/auth/internal/**`）。

## 已有库如何补执行 DDL

`init-sql/` 只在 MySQL 数据卷**首次初始化**时执行。已经有数据的库要手动补。

> **警告：这一步是中文乱码的高发点。**
> 不要用 `Get-Content xxx.sql | docker exec -i ... mysql` 这种写法：Windows PowerShell 5.1 的
> `Get-Content` 默认按系统 ANSI(GBK) 读取文件，UTF-8 中文会被解析成乱码字节再写进库，
> 形成「UTF-8 字节 → Latin-1 字符 → 再按 UTF-8 存回」的双重编码（页面上显示为
> `å†·è—è½¦`、`鍐疯棌` 之类的乱码），且**不可逆地写进了数据**。
> 本项目 2026-09 曾因此在「设备接入 → 待确认队列 → 来源设备」列出现乱码。

**正确姿势（二进制安全）**：先把文件原样拷进容器，再在容器内用 mysql 读取。

```bash
# 1) 拷贝 SQL 到容器（docker cp 是二进制安全的，不会改编码）
docker cp backend/init-sql/11-device-ingest.sql pork-traceability-mysql-1:/tmp/patch.sql

# 2) 在容器内执行（显式指定 utf8mb4）
docker exec pork-traceability-mysql-1 sh -c \
  "mysql -uroot -p<root密码> --default-character-set=utf8mb4 < /tmp/patch.sql"
```

若在 macOS / Linux 的 bash 下用重定向，`docker exec -i ... mysql < file.sql` 是安全的
（bash 原样传字节）；只有 PowerShell 管道会坏。

**补执行后如何自检中文没坏**（返回 0 即为正常，非 0 说明该列被双重编码）：

```sql
SELECT SUM(HEX(device_name) LIKE '%C3A5%' OR HEX(device_name) LIKE '%C3A8%') AS bad FROM db_ingest.ingest_device;
```

已在乱码的列可用 `backend/init-sql/13-fix-device-encoding.sql` 重写（该脚本同时附了成因说明）。

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
| `TAG` | `earTagNo`、`farmName` | `breed`、`birthDate`、`gender`（1公/2母）、`penNo`、`origin`、`operator` |
| `VACCINE` | `earTagNo`、`vaccineName`、`vaccineBatchNo` | `manufacturer`、`injectTime`、`dosage`、`injectSite`、`operator` |
| `INSPECTION` | `earTagNo`、`batchNo`、`result`（1合格/0不合格）、`veterinary` | `inspectType`（1宰前/2宰后/3同步）、`temperature`、`organCheck`、`conclusion`、`issueDesc`、`disposal`、`licenseNo` |
| `STAMP` | `earTagNo`、`batchNo`、`carcassNo`、`veterinary` | `stampType`、`stampPosition`、`remark` |
| `SPLIT` | `parentBatchNo`、`productName`、`weightKg` | `packageCount`、`packageType`、`workshop`、`workshopTemp`、`operator` |

> `RACTOPAMINE.result` 缺失会被直接拒绝（`status=3`）：机器不得替兽医签「阴性」结论。
> `RACTOPAMINE.batchNo` 也一样必填——检测记录必须能归属到某一批猪，缺批次只能挡住，
> 否则会在写库阶段炸成「系统内部异常」，设备端拿到 500 只会以为是网络问题而反复重推。
> `RECEIPT.receiver` 缺失会进「待人工处理」：签收是责任转移点，设备不能替人担责。
