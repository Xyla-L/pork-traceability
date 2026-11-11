# 项目长期约定（猪肉全链路追溯）

> 只放骨架红线。「为什么」、阈值、配置键、命令原文、回归基线、举例 → `DETAIL.md`；逐轮 → `YYYY-MM-DD.md`；可跑断言 → `logs/verify-*`。**要精确数值先翻 DETAIL。**

## 1 前端
- 不暴露表名/字段/枚举码；技术信息进 tooltip，原始报文进 collapse。
- 通道中文名四处同步（现 18 条）：`IngestChannel.labelOf`/`ingest.ts`/`IngestMonitor.vue`/`verify-channel-label.mjs`，漏加必红。
- 功能必须有恒定落点（每站都有列表列+详情项）；「为什么这么设计」横幅全删，「下一步怎么办」保留。
- 工作台按角色分视图：只改 `DashboardServiceImpl`；block key 唯一；下游失败进 `degraded` 不冒充 0。
- **「填充示例值」不许留下空表单**（2026-11-05 两次报障）：字段取不到 = 功能看起来坏了。缺哪一步就当场补上（发车无车→`ensureDepartableTransport` 按处理计划排收运单再填），补不了也必须把可填的填出来（ENTRY 取在养猪填耳标、SALE 用签收单就地建码）。取数字段以接口实际返回为准（`/sales/qrcodes` 是 `qrCode`，不是 `productQrCode`）。三处入口（发车/到达/到店扫码）都要自清：跑完删测试运单 + 池行还 status=0。
- **链末端通道的上游缺口就地补齐（2026-11-05 三报）**：RECALL_COLLECT 填充按「排车→刷卡发车→补到店」自动补上游，补的动作走**设备通道**（`reportByTerminal` → ingest demoReport），不走人工接口；补了什么必须写进提示。`transportStops` 接口给的是站点视图 Map **没有 id**，跟货物行比对只能用 storeId（一车一店一站唯一）。构建改走 dist 直出（stop nginx → build --outDir dist → start nginx），不再新增 dist-rNN 目录。
- **示例车只取「刷得出本人」的**（`isDemoable`：司机空 or 在 `DEMO_DRIVER_CARDS` 备案）：车牌填了卡号空 = 演示断在刷卡，比全空更糟。库里 `TR-TEST-*` 在途单司机是「吴师傅/周司机」，都不在备案表，到达/TEMP 通道会取到它们 → 必须排除并给诊断 + 「切到发车确认」按钮，不许拿它们填半张表单。司机卡表前端硬编码 3 张（8801/8802/8803），改备案要同步。
- 示例值必须落成功路径，查不到就留空+说明，不许兜底成某人/某个号（温度 -18~0）；「申请出栏」已删；冷链「确认到达」人工入口已删（到站只由车载终端推进）。
- 通知点击按 `bizType` 定落点（`notificationLink.ts`）+ `?keyword=` 回填（`useKeywordQuery.ts`）。
- **设备接入「填充示例值」必须认处理计划**（2026-11-05）：池里有没排车的召回货时，提示要点名并给就地动作「按处理计划生成收运运单」（`sampleAction`→`generateCollectFromPlan`，排完自动重填）；成功路径也要补这句，否则用户以为示例没管计划。RECALL 单文案叫「收运运单」，不许叫「待发车运单」。回归 `logs/verify-ingest-plan-sample.mjs`。
- **示例值要认「召回那条链」，不能只查 `return-pool`**（2026-11-05 第三次报障「填充的示例值不是召回部分的」）：召回货一排进收运运单就从待收运队列移出（`recall_collect.status` 0→1），池子取回来是空的 → 所有兜底都在说「处理计划是空的」，而那张收运单正在路上。唯一口径是 `recallChainState()`（pending 池行 + `kind=RECALL` 且 status∈{1,2,3} 的运单）；取单顺序加 `preferRecallLeg`（稳排在前，卡可刷优先级仍更高）；`ensureDepartableTransport` 只认「有可演示的**收运**车」，普通配送车不算数。四通道统一指向那趟收运单并给下一步按钮（发车→切到达确认、装车口→切到店扫码、到店扫码未到达→切到达确认）。回归 `logs/verify-ingest-recall-sample.mjs`（16 项）。
- 浏览器断言别绑数据状态：`shot-return-leg` 的处理运单列表现在含 RECALL 单，只挑非召回行点详情，全是召回单时如实跳过。

## 2 接入层
- 报重两道 → `status=5` 重复上报，不新建流水。状态 0/1/2/3/4/5＝待处理/待人工/已入账/已拒绝/降采样/重复。
- `ingest_staging` 存原始报文不可变；业务层存归一化结果，两份都留。
- 失败默认 `manual`；只有「数据不成立／物理读不到（接错点位）／外部应删单」才判 `rejected`。
- 来源管控 `DEVICE_ONLY`／`DEVICE_PREFERRED`；设备环节只有「作废」，无编辑入口。
- **设备已收敛（2026-10-24，18 台无重复）**：ENTRY 留 `GATE-001`、温度留 `TEMP-001`、车载终端留 `ARRIVE-001`（通用不绑车牌）；CERT/RFID/DEPART-001/003/TEMP-002/003 已删别再引用。DEPART/ARRIVE 同能力组，**通用终端上报必须显式带 channel**，不传按台账 channel（ARRIVE）路由。

## 3 设备 ≠ 人
人名不得用设备名兜底，无人名→转人工；operator/inspector/veterinary/receiver 各归其位。
**TAG 通道**：读写器只报 `earTagNo`+`readTime`，报文混入人工字段一律不认；同耳标再报→幂等。「出生日期设备必填」已废。
**生猪档案已批次化（2026-10-25）**：批次档 `pig_batch`（`POST /pigs/batches`）是唯一人工登记入口——场+批次号+品种+进场日期+**耳标起止区间**；登记时区间自动展开成 `pig_individual` 成员行（不再逐头建档，`POST /pigs` 已删）。个体行=批次成员，下游逐头事实零改动。区间：同前缀+纯数字尾号≤2000 头；**起=止单头批豁免数字要求**。TAG：区间外耳标→转人工提示登记批次。voidBatch：成员已出栏/屠宰(status≥2)拒整批作废。存量回填**必须逐头单批**，按场 GROUP BY 会产生区间空洞。reconcile 基线=30 头。

## 4 中文编码
- 中文进 HTTP 头必走 `OperatorHeader.encode/decode`，否则落库成 `?`。
- 改 `common-web`／`common-core` 必须重建全部依赖服务；含中文 SQL 必须 utf8mb4。

## 5 判定器
- 分两侧，**禁止各写一份**：屠宰侧 `slaughter-service/support/`（`RactopamineGuard`/`PreSlaughterGate`/`StampGuard`）；养殖侧 `breeding-service/support/`（`ImmunityGate`）。
- ENTRY 两条硬前提（审批通过+状态∈{1,2}，force 也绕不过）；瘦肉精批批检、阳性整批受控、阻断一律 `rejected`；宰后口径一律 `IN (2,3)`。
- **检疫出证必须有有效免疫档案**（`ImmunityGate`，人工+设备两个入口都过）：一条没有→拦；全部晚于出证时间（5min 容差）→拦；不按病种匹配。
- 判据：下游依赖 / 法规明文前置 / 错了不能回退 ⇒ 硬拦 `rejected`；否则只记录并在页面标出。**「拦」的形态是挂起转人工，不是拒绝服务**。
- 时间闸门**只卡未来不往回卡**（补传、预冷是合法能力；归属靠报文 `transportNo`，不靠时间猜）。

## 6 批次
SPLIT＝`DEVICE_ONLY`，一次上报展开 N 条，`SplitYieldCheck` 不通过只置 `check_status=1`；胴体批次键＝场+日、只动当日同场 DEVICE 未封批；`carcass_batch_pig` 是权威（三字段必相等）；`carcass_stamp` 双唯一＝一头白条一枚章。分割树根标题＝`CB…（品种）`：从权威表取猪 id 经 breeding `/internal/pigs/breeds` 带出 `pigBreeds`，标题出口仍在 `splitNodeTitle`；品种查不到就不标，不许编兜底。

## 7 冷链运单
- 计划侧与运单侧**两条时间线分开命名**（「已排车」≠「已发运」），改词要前后端+列注释一起改。
- `LOAD` 不产生业务判定；`DEPART` 唯一推进状态的责任闸门；`ARRIVE` 粒度＝门店，运单状态由站点汇总推导。
- **一车一趟**：`ActiveTransportGuard` 只拦「新开单」（人工建单/改挂车牌/计划生成），**并入不算**；名下有未完成(1,2,3)即拒，只有 status=4 签收才算车回来；存量由 `43-single-active-transport.sql` 收口。
- 司机以 `DriverSource` 为准（CARD/FACE＝本人确认）；司机为空 ⇒ driver_source=NULL。
- 人工「新建计划/新建运单」按钮已删。
- **温度**：唯一口径 `TemperatureRecorder`（越界/断档/间隔）；定时采集 `TemperatureAutoSampler`（绕过接入层、status=2 每 300s、到达即停、真机报过即永久让位）+ 真机 `TEMPERATURE` 通道（`X-Device-Key`，报文须带 `transportNo`）；空白≠正常靠 `is_gap`。
- **拒收回运（2026-11-04 收敛为「扫码即出单」）**：门店拒收即入处理计划池（跨天累积合法，池断言按「本轮 transportNo+门店」定位，不按门店模糊匹配）；RETURN_LOAD 装车扫码（车牌+货码倒查拒收站点）登记成功当场 `autoPlanDisposalForScan` 生成/并入该车待发车处理运单（无司机，DEPART 刷卡确认）；「生成处理运单」按钮=兜底补单，**绝不按载重自动补装**。ingest written 回执可带话术（≠「已入账」落 errorMsg）。回归红线：运单列表主键是 `id`；列表缺省只回 NORMAL，DISPOSAL 必须显式查。
- **召回范围=被举报批次（2026-11-04 晚）**：弹窗选项只来自举报台账 target_batch（举报分页取满 + 逐批次号 keyword 查分割，**拉一页 splits 本地过筛必漏**）；目标门店由 `/sales/recalls/affected-stores` 自动带出不可手选（与 createRecall 同一份口径）；进度上报已删，执行状态只由无害化签收推进（disposalConfirm→sales internal complete，幂等，通知 ADMIN bizType=recall_done）。
- **召回收运（2026-11-04）**：召回发起（sales）→ 池（recall_collect，幂等 recall_no+store+batch）+ 通知（bizType=recall_store/recall_center）→ 拒收回运页处理计划自动出现（returnPool 已并入，source=RECALL，前端勿重复拼）→ 手动「生成收运运单」（RECALL，多站：门店…+末站无害化）→ DEPART/ARRIVE 照车载终端 → 到店 RECALL_COLLECT 扫码（车牌→在途单→当前站→货码；收完本站才许到下一站 recallArriveBlocker）→ disposalConfirm 签收即池收口。前置失败一律 retryable 转人工；`pageTransports` kind 支持逗号多值。**处理签收页必须 DISPOSAL,RECALL 同列**（2026-11-05 报障：只查 DISPOSAL 漏掉收运单）；召回单 stopCount 创建时必写（不写落默认 1，页面「已到 2/1 站」）。**生成收运运单会并池里全部待收行（跨召回跨门店）**——孤儿池行（召回单已删池行残留）会混进下一轮运单多出幽灵站点，清残留必须连 `recall_collect` 一起查。车载终端取单 kind 用 `ALL_TRANSPORT_KINDS='NORMAL,DISPOSAL,RECALL'` 一处常量；装车口示例排除 source=RECALL；无单时 `describePoolBlockers` 给三类卡点下一步，不许空提示。`verify-recall-collect` 已自持：跑前 `ensureBackdrop`（零售商已签收单建 3 个在售码+举报，守卫必须**排除本店再数在售门店**——建码动作本身会在本店插 status=1 行），签收后销售行 status=4 被消耗属正常。

## 8 订单
绝不能判 `rejected`（外部据此删单）→ 一律 `manual`；不建订单表、不收金额；零/负重量必拦；订单号每次唯一。

## 8.5 区块链存证（2026-11-08 已切真实链）
- **当前 `BLOCKCHAIN_TYPE=evm`（本地 Ganache，即时出块）**：合约 `PorkTraceability` 地址固定 `0xe78a0f7e598cc8b0bb87894b0f60dd2a88d6a8ab`（compose `EVM_CONTRACT_ADDRESS`），ganache 数据落 `ganache-data` 卷。**`down -v` 清卷=链丢失**，须重部署合约+重跑迁移（配方在 `docs/来源管控与上链可信方案.md`）。退回 local 只改 compose 默认即可，页面口径自动跟随。
- **每条存证带 `chain_mode`（local/evm）**：evm 适配器必须写 `blockchain_ledger` 镜像行（`mirrorToLedger`），不写则新存证全是悬空；迁移接口 `POST /blockchain/migrate/legacy?limit=&dryRun=` 幂等补写历史 local 记录并把 ledger 行改挂新交易（不改挂=孤儿块暴涨）。`/health` 暴露 `legacyLocal/migrated`；verifyView 的 `matched = stored && !legacy && v.matched`（legacy 只在 evm 模式成立）。
- **展示口径如实**：页面按 `BlockchainAdapter.mode()` 区分，禁止「区块链已验证」冒充；回归 `logs/verify-blockchain.mjs`（31 项，含交易哈希 RPC 链上核对 + ⑧字典覆盖断言：库内 DISTINCT biz_type 与代码 publish 的类型都必须进前端字典，漏了必红）。
- **存证类型中文名三处同步、一处唯一来源**：web 唯一字典 `web-admin/src/utils/chainBizType.ts`（41 类）；小程序 `mini-app/src/utils/bizType.js`；后端 `TraceQueryServiceImpl.bizTypeLabel`（小程序 safe-buy 时间轴 desc 的来源）——三处名称必须逐字一致（web 叫「批次拆分」就不许出现「批次分割」）。作废/历史类型在页面上标「· 历史」，不混进当前能力。
- **小程序消费端口径**：verify 页三分支（通过/暂无存证/不一致），禁止「可放心购买」「数据可能被篡改」这类过度承诺与绝对指控；标题随载体（local=「存证核对」）；扫到作废码要显示下游业务原话（TraceRemoteClient 透传 message 并清洗「（操作人 xxx）」），不许盖成「下游服务返回异常」。CORS 由网关统一（DedupeResponseHeader 去重），下游 common-web 的 CORS 配置不要再加。H5 dev 走 vite 代理（`#ifdef H5` 条件编译），mp-weixin 打包验证无 DEV 段残留。小程序页面截图回归 `logs/shot-mini-chain.mjs`（起 dev:h5，hash 路由截图前必须先 about:blank，否则页面实例复用残留上一次数据）。
- 网关路由 `/api/v1/blockchain/**`；接口 `/mode`、`/health`、`/records/status-map`、`/migrate/legacy`。历史孤儿块 2250 如实披露不删除。
- **哪个环节上链**：建档 PIG_BATCH、免疫 VACCINE_RECORD、检疫证 QUARANTINE_CERT、入场 ENTRY_INSPECTION、瘦肉精 RACTOPAMINE_TEST、检验 SLAUGHTER_INSPECT、盖章 CARCASS_STAMP、发车 TRANSPORT_DEPART、到站 TRANSPORT_ARRIVE、签收/拒收/召回/分割等；人工与设备同一事件类型、payload `path` 区分。新增类型必须同步三处字典（见上），否则 verify-blockchain ⑧ 必红。

- 清残留**不要删 blockchain_record**（账本块会成孤儿，`近24h无新增孤儿块` 必红）。`verify-entry-gate`/`verify-ractopamine-block` 不自清（留 ET-GATE/ET-RB 猪、推高 carcass_batch 计数），跑完必须按脚本打印的 ID 清，否则 verify-reconcile 会挂（备份后按业务键删业务行，链上记录保留）。

## 9 纪律
- 改后端必须 `--no-cache` rebuild；build 启动后再改源码不进镜像。表名：`pig_individual`（非 `pig`）、`sys_user`，staging 在 `db_ingest`。
- MyBatis-Plus 跳过 null：清空列用 `lambdaUpdate().set(field,null)`。
- 回归残留必清（只删自己造的、按业务键），清完跑 `verify-reconcile.mjs`。
- **删业务行前先问**：`recall_collect` / `recall_order` 这类「用户可见的处理计划/召回单」不许凭「批次像测试数据（SP-TEST-*）」「transport_id 悬空」判断残留——那是并池+删车留下的污染，不是垃圾。误删可从 binlog ROW 镜像原样恢复（配方见 DETAIL §32）。
- 回归要**自持**：独占型写入必须自带一次性主体，用完自己删，不借演示台账。
- 断言盯不变量不盯绝对条数；浏览器断言别依赖数据状态。
- 浏览器：`Math.random=0` → 验证码恒 `AAAA`；playwright-core 在托管区。

## 10 机构隔离与批量签收
- 边界唯一来源＝JWT `orgId` → 网关透传 `X-User-Org`（自带丢弃），唯一读头 `OrgScope`；零售商缺机构→401/403，不退回全平台。
- **FARMER 按场圈**（2026-10-29）：`FarmScope`（breeding support）orgId→sys_org 名→farm.id 换算，换算不出 403 不放全量；列表 restrict/inSql 下推 + 单条 requirePigOfFarm。机构与业务表**无外键，只靠名称对应**（20-sync 口径），改名会断归属。屠宰/配送**只埋点不圈**（2026-10-29，`46-org-ownership.sql`）：屠宰四表+`cold_chain_transport`+`ingest_device` 加 `org_id`（NULL=平台操作/设备未绑定，不猜），存量回填屠宰→3/运单→4、7 台设备按通道绑定；写入经 ingest 处理器 `ctx.device().getOrgId()` + 配送 `ownDistributionOrg()`（只认 DISTRIBUTOR）。`split_batch`/`carcass_batch` 未埋点、过滤与 403 未加，第二家屠宰/承运机构进场时再启用。工作台聚合走 internal 未圈。隔离业务码=2003。
- 分页必须 `inSql` 下推；写操作 `requireStore`。
- 批量签收 `POST /distribution/receipts/batch`：每单签自己「已到达未签收」那站，门店不由前端指定；**逐单独立事务**；站点数≠1 不猜。

## 11 屠宰按批折叠
- 屠宰四页（入场/检验/盖章/瘦肉精）主行=批次、展开逐头；接口 `/slaughter/records/{entries,inspections,stamps,ractopamine}`，取数复用各 service 原查询（pageSize 拉满内存归组），不写第二份 SQL。分组键只有 batchNo（屠宰跨天是常态，不加日期签名）；batch_no 是 NOT NULL，空串=未归批→按头独立成组，绝不互并。
- 结论挂标签（全部合格/含不合格/全部阴性/含阳性/全部已盖章/全部作废…），计数进 statusDetail 挂 tooltip；阳性/不合格必须写在主行，不许藏进展开层。逐头操作（查看/作废）只在展开明细层，逐头列表接口保留并存。
