# 项目长期约定（猪肉全链路追溯）

> 只放骨架红线。「为什么」、阈值、配置键、命令原文、回归基线 → `DETAIL.md`；逐轮 → `YYYY-MM-DD.md`；断言 → `logs/verify-*`。

## 1 前端
- 不暴露表名/字段/枚举码；技术信息进 tooltip，原始报文进 collapse；「为什么」横幅全删，「下一步」保留；功能必须有恒定落点。
- 通道中文名四处同步（`IngestChannel.labelOf`/`ingest.ts`/`IngestMonitor.vue`/`verify-channel-label.mjs`），漏加必红。
- 工作台按角色分视图：只改 `DashboardServiceImpl`；block key 唯一；下游失败进 `degraded` 不冒充 0。
- **「填充示例值」不许留空表单**：缺步骤当场补（`ensureDepartableTransport` 等），补不了把可填的填出来；取数字段以接口实际返回为准（`/sales/qrcodes` 是 `qrCode`）。示例车只取「刷得出本人」的（`isDemoable`/`DEMO_DRIVER_CARDS`，前端硬编码 8801-8803）。
- 设备接入示例必须认召回链：唯一口径 `recallChainState()`（pending 池行 + kind=RECALL 运单），只认可演示收运车；RECALL 单叫「收运运单」。回归 `verify-ingest-recall-sample`。
- 前端构建走 dist 直出（stop nginx → build --outDir dist → start nginx），不加 dist-rNN。
- 通知点击按 bizType 定落点（`notificationLink.ts`）+ `?keyword=` 回填；浏览器断言别绑数据状态。

## 2 接入层
- 状态 0-5＝待处理/待人工/已入账/已拒绝/降采样/重复；报重→status=5 不新建流水；`ingest_staging` 原始报文不可变。
- 失败默认 manual，仅「数据不成立/物理读不到/外部应删单」判 rejected；来源管控 DEVICE_ONLY/DEVICE_PREFERRED，设备环节只有「作废」。
- 设备收敛：ENTRY=`GATE-001`、温度=`TEMP-001`、车载=`ARRIVE-001`（通用），别引用已删设备；通用终端上报必须显式带 channel。

## 3 设备≠人
- 人名不得用设备名兜底，无人名→转人工；TAG 通道只认 `earTagNo`+`readTime`，混入人工字段不认；同耳标再报→幂等。
- 生猪档案批次化（54 号迁移前已批次化）：`pig_batch`（`POST /pigs/batches`）唯一人工入口（耳标起止区间自动展开成员行，`POST /pigs` 已删）；区间外耳标→转人工；voidBatch 成员 status≥2 拒作废；存量回填逐头单批。

## 4 中文编码
- 中文进 HTTP 头必走 `OperatorHeader.encode/decode`，否则落库成 `?`。
- 改 common-web/common-core 必须重建全部依赖服务；中文 SQL 必须 utf8mb4。

## 5 判定器
- 分两侧禁止各写一份：屠宰 `slaughter-service/support/`，养殖 `breeding-service/support/`（`ImmunityGate`）。
- ENTRY 硬前提（审批过+状态∈{1,2}）force 绕不过；瘦肉精批批检、阳性整批受控、阻断一律 rejected；宰后口径 IN(2,3)。
- 检疫出证必须有有效免疫档案（缺→拦；全部晚于出证 5min 容差→拦）。
- 判据：下游依赖/法规前置/不可回退⇒硬拦 rejected；否则只记录标出。「拦」=挂起转人工，不是拒绝服务。
- 时间闸门只卡未来不往回卡。

## 6 批次
- SPLIT=DEVICE_ONLY 一次展开 N 条；`SplitYieldCheck` 不过只置 check_status=1。
- 胴体批次键=场+日、只动当日同场 DEVICE 未封批；`carcass_batch_pig` 权威（三字段必相等）；`carcass_stamp` 双唯一。
- 分割树根标题=`CB…（品种）`，出口 `splitNodeTitle`；品种查不到就不标，不许编兜底。

## 7 冷链运单
- 计划侧/运单侧两条时间线分开命名（「已排车」≠「已发运」）；LOAD 不判定；DEPART 唯一责任闸门；ARRIVE 粒度=门店，运单状态由站点汇总推导。
- 一车一趟：`ActiveTransportGuard` 只拦新开单（并入不算）；名下未完成(1,2,3)即拒，status=4 才算回来。
- 温度唯一口径 `TemperatureRecorder`；`TemperatureAutoSampler` 定时采集（300s、到达即停、真机让位）；真机 TEMPERATURE 通道须带 transportNo；空白≠正常靠 is_gap。
- 拒收回运「扫码即出单」：RETURN_LOAD 登记当场 `autoPlanDisposalForScan`；绝不按载重自动补装；列表缺省只回 NORMAL，DISPOSAL 必须显式查；运单主键是 id。
- 召回范围=被举报批次（只来自举报台账 target_batch，拉一页本地过筛必漏）；目标门店 `/sales/recalls/affected-stores` 不可手选。
- 召回收运链：recall_collect 池（幂等 recall_no+store+batch）→ 生成收运运单（会并全部待收行，孤儿池行=幽灵站点）→ 到店 RECALL_COLLECT 扫码（收完本站才许下一站）→ disposalConfirm 收口。处理签收页必须 DISPOSAL,RECALL 同列；stopCount 创建必写；取单 kind 常量 `ALL_TRANSPORT_KINDS`。

## 8 订单
- 绝不能 rejected（外部据此删单）→ 一律 manual；零/负重量必拦；订单号每次唯一；不建订单表、不收金额。

## 8.5 区块链存证
- BLOCKCHAIN_TYPE=evm（本地 Ganache）；合约地址固定（compose `EVM_CONTRACT_ADDRESS`），数据落 ganache-data 卷，`down -v`=链丢须重部署+迁移（配方 `docs/来源管控与上链可信方案.md`）。
- evm 适配器必须 `mirrorToLedger` 写镜像行；迁移 `POST /migrate/legacy`（默认 async+单飞锁）+ `/migrate/legacy/status`。
- 展示口径如实：按 `mode()` 区分，禁止「区块链已验证」冒充；开发链必须自报家门（developerChain/chainWarning + 页面「开发链（单节点）」tag）。
- 验真比整段历史不比最新版（bizKey 非唯一键，`versionCount`+`evidenceAt` 逐版回找）；verifyView 带链上原文比对——只证明「链上==本地快照」，业务表直改不在此能力内（改哈希口径=动 24 处，已否）。
- payload 快照（55 号迁移 `payload_json`）：store 落快照，retry/migrate 用 `payloadOf` 原样重放，**禁止 `Map.of()` 空报文上链**；缺快照如实披露（payloadMissing）。
- 上链脱敏在 store() 边界（`PhiMasker`，`BLOCKCHAIN_MASK_PHI` 默认 true）：人名打码、追溯键不动；历史明文不可撤回。
- 存证类型中文名三处同步（`chainBizType.ts` / `mini-app utils/bizType.js` / `TraceQueryServiceImpl.bizTypeLabel`）逐字一致，漏加必红（verify-blockchain ⑧）。
- 小程序消费端：verify 三分支，禁止过度承诺/绝对指控；扫到作废码显示下游业务原话；回归 `verify-blockchain`(40) + `shot-mini-chain`。
- **清残留不要删 blockchain_record**（孤儿块必红）。

## 9 纪律
- 改后端 --no-cache rebuild；build 后改源码不进镜像。**多服务并行 --no-cache 构建会踩共享 .m2 cache mount（依赖解析失败），一次只建一个**；依赖下载截断（Premature end）重试即可。
- 表名 `pig_individual`/`sys_user`，staging 在 db_ingest；MyBatis-Plus 清空列用 `lambdaUpdate().set(field,null)`。
- 回归残留必清（只删自己造的、按业务键），清完跑 `verify-reconcile`；回归要自持（独占写入自带一次性主体）；断言盯不变量不盯绝对条数。
- **删业务行前先问**（recall_collect/recall_order 不是垃圾）；误删可从 binlog ROW 镜像恢复（DETAIL §32）。
- 浏览器：Math.random=0 → 验证码恒 AAAA；playwright-core 在托管区。

## 10 机构隔离
- 边界唯一来源=JWT orgId→网关 `X-User-Org`，唯一读头 `OrgScope`；零售商缺机构 401/403，不退全平台。
- FARMER 按场圈（`FarmScope`）；屠宰/配送只埋点不圈（46 号迁移）；机构与业务表无外键只靠名称对应，改名断归属。
- 分页 inSql 下推；写操作 requireStore；批量签收逐单独立事务。

## 11 按批折叠（养殖三模块+屠宰四页）
- 主行=单据/批次、展开「关联生猪」逐头；**主行不拼耳标区间**（MIN/MAX 是假范围，tagStart/tagEnd 已删别加回来）。
- 养殖三模块单据化（54 号迁移）：免疫批次自动归批（同场+同疫苗+同生产批号+同日）；检疫一证多头（整证作废、成员只增不删）；出栏一单多头（地磅并单、单据级审批后逐头推进 status=2）；ImmunityGate 逐头校验；机构隔离单据带 farm_id 等值下推。回归 `verify-batch-group`(44)。
- 屠宰分组键只有 batchNo（跨天不加工期签名）；空串=按头独立成组绝不互并。回归 `verify-slaughter-fold`(50)+`shot-slaughter-fold`(17)。

## 12 扫码入口（2026-11-15）
- **溯源码唯一定义 `common-core TraceQrCode`**（QR-PORK- 前缀+字符集，不锁位数）；小程序 `utils/qrCode.js` 同口径——改码格式只改这两处。
- 消费者 scan/verify/safe-buy 与销售 sell 四态文案：非本系统码 / 未登记 / 已作废（带理由）/ 已售·过期·召回各有其话，**禁止把扫错码说成「产品不存在」**（会被当成假货）。
- sell 判定顺序：先商品事实（召回→已售→过期）再激活位（召回不改激活位，反序会掩盖召回）。
- 小程序扫码：mp-weixin 用 `uni.scanCode`（scanType:['qrCode']）+ extract 前置；H5 才用选图+jsQR（`#ifdef H5`）。回归 `verify-qrcode-guard`(32)+`shot-qr-guard`(7)。
