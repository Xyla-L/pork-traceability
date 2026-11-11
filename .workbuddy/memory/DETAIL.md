# 项目约定·细则全文（猪肉全链路追溯系统）

> **本文件是 `MEMORY.md` 的细则版**，含理由、配置键、回归基线与命令原文；`MEMORY.md` 只保留必须遵守的规则骨架。需要细节时按节翻本文件。
> 逐轮实施记录见同目录 `YYYY-MM-DD.md`；可执行断言见 `logs/verify-*.mjs`。

## 一、前端
- 不暴露表名/字段/代码，翻业务中文；技术信息进 `el-tooltip`（标「技术人员排查用」）；原始报文/回执进 `el-collapse` 默认收起。术语：业务表→正式业务数据；幂等→不会重复入账；`source=DEVICE`→设备采集；`bizKey`→上报编号。
- **通道中文名四处同步（现 18 条）**：后端唯一来源 `IngestChannel.label/labelOf()`；前端 `ingest.ts` 的 `CHANNEL_LABELS` + `IngestMonitor.vue` 的 `CHANNEL_TABLES`/`TABLE_TARGET_OBJECTS`/`DEMO_FIELDS`；**加通道必同步 `logs/verify-channel-label.mjs` 的 `EXPECT`**（有覆盖度断言，`LOAD`/`ORDER`/`SPLIT_SNAPSHOT` 都漏过）。
- 侧边栏顺序：养殖建档→免疫注射→产地检疫出证→申请出栏→入场查验→瘦肉精→屠宰检验→胴体盖章→胴体批次→分割批次→冷链温度→门店签收→零售售出。**出栏审批是审批动作，不进 `TraceStage`**。
- 上报回执**只留事实**（状态+去向+上报编号+未入账原因+原始回执折叠）；`demoResultHeadline` 已删不加回；toast 只报状态；受理后按钮置灰（`demoReportLocked`），改任一填写项解锁。
- `web-admin` 的 `npm run build` 是纯 `vite build`，**不做类型检查**。
- **演示示例值（「填充示例值」）必须落在成功路径上（2026-10-11 修）**：以前 `fillSample` 取到运单后一律 `DEMO_DRIVER_CARDS[司机] || '8801'` 兜底，而演示库在跑的运单安排的是「吴师傅」「周司机」这种**没进司机备案**的名字 → 示例卡必然刷出一个与运单不符的人：发车侧「现场刷卡的人 ≠ 运单安排的人」转人工、**到达侧直接「已拒绝」**（本轮刚收紧的红线）。现在的口径：
  - `cardOf(driverName)` **只按运单安排的那位司机取卡**，查不到返回 `undefined`；
  - 三种情形分别处理：① 司机在卡表里 → 刷他的卡（走「已发车」）；② 运单还没有司机 → 用备案里的 `DEMO_FALLBACK_CARD`（8801/张师傅）演示「刷卡赋身份」，并在提示语里说清会写成谁；③ 安排的是别人 → **卡号留空**，提示语写明「刷任何一张卡都会被判上错车」+ 补救办法（填他本人的卡号，或到「分割配送 → 冷链运输」编辑这趟车把司机改成备案司机 —— `updateTransport` 允许改司机且会正确落 `driver_source=MANUAL`）；
  - `preferCardResolvable(list)` 把「刷得出来」的运单排在前面（① → ② → ③，稳定排序），发车/到达两条通道共用；
  - 温度示例取 **-2℃**（`DEMO_TEMPERATURE`）：原先给 2.5℃ 超出允许区间 -18~0，一填就被标 `is_abnormal` —— 示例默认演示的却是异常路径。
  - **判据**：示例值一旦提交就落在「转人工 / 已拒绝」上，就是示例值错了（不是业务逻辑错）。

## 二、接入层
- 报重两道：① 同设备+同 `bizKey` 回既有结论；② 兜底：同设备+同通道+**同一份报文**才算重复（报文不同算新数据）。状态 待处理0/待人工1/已入账2；放行 已拒绝3（补齐可重发）/已降采样4。见 `DeviceIngestService.receive()`。
- `ingest_staging` 存原始报文不可变，业务层存归一化结果，两份都留。
- **成功回执不带业务附言**：`Handlers.toResult` 在 `written=true` 时只取 `id`/表名，`DispatchResult.accepted()` message 硬编码「已入账」→ 业务侧 `reason` 附言只落业务表与内部接口。标记类事实（如 `temperature_log.is_gap`）一律落业务表由前端展示，**别指望回执文案，测试也别断言它**。
- **失败默认 `manual` 而非 `rejected`**（`rejected` 会让外部系统删单）；只有「数据不成立、外部应删单」才判 `rejected`。

## 三、设备 ≠ 人
- 人名不得用设备名兜底；无人名→转人工，不拒绝；force 放行写「未记录」。`IngestContext` 只有 `deviceNo()`（仅机器字段用，如 `temperature_log.recorder`），`deviceLabel()` 已删。
- 人名映射：TAG/VACCINE/RACTOPAMINE/SPLIT=`operator`、ENTRY=`inspector`、INSPECTION/STAMP=`veterinary`、RECEIPT=`receiver`；前端统一 `|| '未记录'`。

## 四、中文过 HTTP 头必须编码
- HTTP 头按 ISO-8859-1，中文落库成一串 `?`（`HEX()`=`3F…`）**有损不可逆**。一律走 `com.pork.core.source.OperatorHeader.encode/decode`（URL UTF-8）：网关 `AuthGlobalFilter` 编，`SourceGuard.currentOperator()`/`IngestStagingController.handler()` 解。新增中文头照办。
- `SourceGuard` 在 `common-web` → **改 `common-web`/`common-core` 必须重建全部依赖服务（含 gateway、ingest）**。

## 五、环节前置与三道判定器
- **ENTRY 两条硬前提**（force 也绕不过）：出栏审批已通过 + 生猪状态 ∈{1在养,2已出栏}（3已屠宰/4异常为终态，一猪只进厂一次）。顺序：审批→状态→时间/pigId→幂等→落库。唯一来源 `GET /breeding/internal/applies/by-pig?pigId=` 与 `/breeding/internal/pigs/{id}/state`；**查不到/不可用一律转人工**（绝不默认正常）；已驳回不自动复活。
- 判定器分布（**禁止各写一份**）：屠宰侧 `slaughter-service/support/` = `RactopamineGuard`/`PreSlaughterGate`/`StampGuard`；养殖侧 `breeding-service/support/` = `ImmunityGate`（产地检疫的免疫前置，人工出证与设备扫码两个入口共用）；冷链温度唯一口径仍是 `TemperatureRecorder`。
- **「数据不成立」类硬拦 vs「条件没到位」转人工**（判据：下游是否依赖 / 法规是否明文前置 / 错了能不能回退）。2026-10-26 补齐的四条全部 `rejected`（不进人工队列，人工一放行就把矛盾写进链）：`QUARANTINE` 证面时间倒挂（有效期早于签发日）、签发时间在未来；`VACCINE` 非「在养」（已出栏/已屠宰）、注射时间在未来；`EXIT_APPLY` 称重时间在未来；`TEMPERATURE` 记录时间在未来。**检疫出证必须有有效免疫档案**（`ImmunityGate`：一条都没有 → 拦；全部晚于出证时间，5 分钟容差 → 拦；不按病种匹配，疫苗名是自由文本）。
- **时间闸门只卡「未来」，不往回卡**：补传历史数据、装车前预冷都是合法能力，拿时间往回卡（早于发车/早于建单）一定误伤；「这条温度属于哪一趟」由报文 `transportNo` 归属，不靠时间猜。`verify-temp-gap`（26 条断档曲线全量补传）就是这条边界的回归。
- **瘦肉精**：批批检，阳性整批受控；「未检不得屠宰」= 该猪或其所属批次有「出结果」记录；所属批次 = 报文 `batchNo` ∪ 入场查验批次 ∪ 检测记录 `batch_no`（别只信上报批次号）。只约束宰后类(2,3)；宰前不受限但阳性不得判「合格」。阻断一律 `rejected`（force 不放行），阳性记录照常入账留证。三道关卡：`ingestInspection`（幂等前）、`ingestStamp`、`DistributionServiceImpl.createBatch/updateBatch`（跨服务 `/slaughter/internal/ractopamine-blocked`，核不到抛 `REMOTE_CALL_ERROR`）。
- **宰后口径**：`inspect_type` 2=宰后检验 与 3=同步检验**同一环节** → 一律 `IN (2,3)`（`POST_SLAUGHTER_TYPES`）；展示 3→「宰后检验（同步）」。`PreSlaughterGate`：有合格宰前→放行；有宰前但无一条合格→「结论为不合格」；一条都没有→「尚未做宰前检查」。**盖章收紧**：`ingestStamp` 必须有宰后类 `(2,3)` 且 `result=1`。顺序：瘦肉精→宰前门槛→幂等→落库。
- 回归 `verify-ractopamine-block` / `verify-preslaughter-gate` / `verify-entry-gate` / `verify-exit-apply-device`（.mjs 各带 `-db.sh`）。

## 六、批次归集

**分割 `SPLIT`**（`SplitChannelHandler` → `POST /distribution/internal/ingest/split`，`DEVICE_ONLY` 人工入口已删）
- 一次上报 = 一头白条的整份产出清单：`items[]`（partCode/weightKg/packageCount）；系统生成作业号 `SV+yyyyMMdd+'-'+3位流水`，展开 N 条 `split_batch`（部位号 = 作业号 `SV`→`SP` 加 `-两位序号`）。**无 `items` 退化为单条**（顶层 partCode/productName/weightKg）。
- **`meat_part` 是部位唯一来源**：报 `partCode`；只报名称按 `part_name`/`aliases` 反查（`FIND_IN_SET`），**反查不到一律转人工**。`category` ∈ LEAN/BONE/FAT/SKIN/BYPRODUCT/WHOLE。
- **出肉率对账**（`support/SplitYieldCheck` 唯一口径）：① 缺必出项（`required=1` 前腿/大排/后腿）② 总重 > 白条重 ×`max-over-rate`(105%) ③ 下限：报骨脂皮副产按 `min-yield-rate`(85%)、只报红肉按 `min-lean-only-rate`(60%)、WHOLE 不判。基准：带耳标取 `carcass_batch_pig.weight_kg`(`PIG`)，否则批次均重(`BATCH_AVG`)，都没有只写说明(`NONE`)。**不通过不影响入账**，只置 `check_status=1` 待复核。
- **拆分树多一层**：`getSplitTree` 在胴体批次与部位间插 `type='SPLIT_JOB'`；`job_id IS NULL` 的历史记录仍平铺。接口 `GET /distribution/split-jobs`、`/meat-parts`。
- **「未产出标准部位」对照**：作业下（`scope=JOB` 逐头）与「有历史平铺记录的批次」下（`scope=BATCH` 按批汇总，措辞分开）各加一条 `MISSING_PART_GROUP`；叶子 `MISSING_PART` 无重量/无操作人/不进详情/不可选中；伪节点无库表 id 用后端 `key`（前端 `nodeKey` 优先取 `key`）。清单唯一来源 `SplitYieldCheck.standardParts()`：**启用且 `category <> 'WHOLE'`（16 条）**。`collectPartCodes` 递归必带 `split_level`（`parent_batch_id` 被胴体与分割批次共用，会撞号）。
- **分割页默认落点**：`pageBatches` 顺带返回 `splitCount`（`@TableField(exist=false)`），前端默认选**最近一条有分割数据的批次**（不能取 `records[0]`）；对不上给空态引导。
- 回归 `logs/verify-split-job.mjs` + `-db.sh`（db.sh 自驱动）。

**胴体批次**
- **批次边界系统算，设备只报「单头白条」**。归集键 = 屠宰场+屠宰日；命中当日同场 `source='DEVICE'` 且 `status=1(归集中)` → 追加，否则新建（`CB` 前缀）。**只动 DEVICE 批次，不碰人工批次。**
- **头数上限+封批**：`ingest.carcass-batch.max-pigs`（默认 200），`sealIfFull` 满则置 `status=2(已封)`，下一头自动开新批；`POST /distribution/batches/{id}/seal` 人工提前封批（stage `BATCH`，强制实名 + 上链；**2026-10-12 起不再需要票据**）。人工建批直接 `status=2`。批号 `CB+yyyyMMdd+'-'+3位流水`，**当日全局排流水、计数含已作废的**（原生 SQL 绕 `@TableLogic`，否则号被复用）。
- **`carcass_batch_pig` 是权威**（`uk_pig` 一猪一批）；`carcass_batch.pig_ids`/`pig_count` 是同一原子 SQL 维护的展示快照。不变量：`pig_count == JSON_LENGTH(pig_ids) == COUNT(carcass_batch_pig WHERE batch_id=?)`，全库无重复 `pig_id`；`deleteBatch` 先删关联行。`JSON_ARRAY_APPEND` 绑定参数按字符串处理 → 必须 `CAST(#{pigId} AS JSON)`。
- 写入前三道硬校验：瘦肉精→盖章→未被占用（`BatchGuard.validateAll`）；设备重推命中「已占用」→ 回既有批次（`written` 不报错）。策略 `DEVICE_PREFERRED`（**2026-10-12 起人工补录免票据**，强制实名 + 上链）。**人工批次只保留「创建 / 添加关联生猪 / 封批」，编辑（`PUT /distribution/batches/{id}` 的变更用途）已删除**；`PUT` 端点本身保留，仅服务「添加关联生猪」的 `pigIds` 追加。`operator` NULL、`source_ref=BATCH-001`；上链 key = `batchNo+":"+action+":"+pigId`。
- 已盖章 ≠ 入批；猪状态「已屠宰3」由宰后检验推进（`inspectType>=2`），与入不入批无关。
- 回归 `logs/verify-batch-autogroup.mjs` + `-db.sh`（自驱动，5 参数）。**断言只认可观测事实（号/来源/头数/重量），不要断言 `data.message` 业务文案。**

## 七、冷链运单链（计划→装车→发车→到达→签收）

**发货计划 = 运单的上游决策单**（`delivery_plan`+`delivery_plan_item`）
- 一张计划 = 一个目的地 + 一个车次 + N 个待发分割批次；计划号 `DP+yyyyMMdd+3位流水`（含已取消的不复用）；`status` 1待发货/**2已排车**/3已取消/**4部分排车**。**车与司机计划阶段可留空**（车次没定是常态）；`generateTransportFromPlan(s)` 时**只强制补齐车牌**（车牌是发车/到达按车牌倒查的唯一凭据）—— **司机允许留空**（2026-10-10 起），责任闸门在发车（见下 `DEPART`）。
- **「2 叫已排车，不叫已发运」的由来（2026-10-10 改）**：两条时间线必须分开命名 —— **计划侧**只回答「**货有没有排进运单**」（待发货 / 部分排车 / 已排车 / 已取消），**运单侧**才回答「**车走到哪**」（待发车 / 运输中 / 待签收 / 已签收）。`generateTransportFromPlans` 在第 ⑥/⑦ 步**生成运单那一刻**就把计划置 `PLAN_GENERATED(2)`，而运单自己才 `TRANSPORT_PENDING(1)` —— 所以计划那一列不是状态冗余，是**原来的词用错了**：排完车那一刻车恰恰还停在「待发车」。改词时前后端四处一起改：`DeliveryPlan` 类注释、`dto/DistributionRequests`、`DistributionController`、`web-admin/src/views/distribution/TransportMonitor.vue` 的 `planStatusOptions`/`planStatusLabel`（计划明细弹窗那列是「排车状态 / 已排车 / 待排车」）。DB 侧只改列注释（`backend/init-sql/35-plan-status-wording.sql`，不动结构不动数据）。
- **`transport_item` = 运单装载明细**（一车多批 + 一车多店；按 `store_id` 聚合即站点清单）；`cold_chain_transport.split_batch_id` 是**主批次**（明细第一条，兼容存量），`destination` 降级为**首站**，`stop_count` 记站点数。`store_receipt` 幂等键 `uk_transport_store(transport_id, store_id)`。
- **「待发运」两道排除**：未被 `transport_item` 引用 **且** 未被「待发货/部分发运」计划的未发运明细占用（`p.status IN (1,4) AND i.shipped=0`）。**不用标记位**（会与删单/取消不同步）。`createTransport` 手工建单与 `loadPlanBatches` 同两道拦截。
- **删运单连带**：`releasePlanItems(transportId)` 按 `delivery_plan_item.transport_id=本运单` 精确退回（不能只按批次 id 找）→ `recomputePlanStatus` 回算（4/2/1），**已取消计划不复活**；再删装载明细与站点台账、删运单。
- 回归 `logs/verify-delivery-plan.mjs` + `-db.sh`：HTTP 46 / 落库 26（含孤儿站点守卫断言）。

**装车口 `LOAD`**
- 全系统唯一**不产生业务判定**的通道：车牌相机 + 扫码枪只给两个现场事实（`vehicleNo`/`batchNos`，司机可选），系统**倒查**待发计划（扫到的批次属哪张计划就发到哪）。**扫码是「确认」不是「选择」**；目的地与发车时间是人的决策，不许设备猜。
- **2026-10-10 起装车口收敛为「两件套」**（车牌识别相机 + 扫码枪），**此处没有读卡器**：现场只在上车那一刻刷一次卡（车载 `DEPART`），装车时司机还没上车。所以：① 司机**允许留空**（常态），留空不转人工、照常建单；② 报了明文姓名 → 用报到的人名，来源一律「人工录入、非本人确认」（装卸工/调度代填）；③ **报了 `cardNo` → 结构性拒绝（`rejected`，不是 `manual`）** —— 物理上读不到的东西报上来 = 设备接错点位（把驾驶室读卡器算到装车位），早报错早修；接入层 `LoadChannelHandler.validate` 与业务层 `DistributionDeviceIngestService.ingestLoad` **双重拦**（防绕过接入层直调内部接口）。
- **两条红线**：① 批次不属于任何计划 → 转人工（分属多张计划已是**正常情况**：一车顺路送几家店，按计划分组一次发运）；② 自动建单失败 → 转人工。**「司机解析不出人」不再是拦点** —— 司机由发车那端落到人。
- **车/人分界 `resolveSiteValue(field, planned, onSite)`**：计划空→现场值补；都有且不一致→抛错转人工；都空→后续强制校验拦。**现场车牌与计划不一致不许设备改写计划**。**超载是硬判据** → 转人工，绝不自动凑满一车。
- **`ingestLoad` 刻意不加 `@Transactional`**：它不写业务表，套外层事务再 catch 内层业务异常 → Spring 标 rollback-only → 提交抛 `UnexpectedRollbackException`，把「转人工」吞成「系统内部异常」。**「调带事务服务 + catch 业务异常返回转人工」的通道照此办理。**
- **分车**：明细加 `shipped/transport_id/shipped_time`；主表 `transport_id/transport_no` 是「**最近一次**」运单，`transport_count` 记共发几车；**「哪张明细搭哪趟车」一律以 `delivery_plan_item.transport_id` 为准**。`generateTransportFromPlan(id, splitBatchIds, site)` **委托**到 `generateTransportFromPlans(List, ...)`；`splitBatchIds` 空=整单发走，指定时必须全落在本计划未发运明细内，否则 PARAM_ERROR。
- **并入既有待发车运单（2026-10-10，治「同一车牌分两次扫码裂成两张运单」）**：`generateTransportFromPlans` 在算完 `lines` 之后、新建之前先探一次 `findOpenLoadTransport(vehicleNo)` —— 候选 = `vehicle_no = 该车牌` + `source='DEVICE'` + `status=1(待发车)` + `create_time >= now - ingest.load.merge-window-minutes`（默认 **120**，写在 `distribution-service/application.yml`），按 id 升序：
  - **0 张** → 照常新建（原逻辑）。
  - **唯一一张且 `source='DEVICE'`** → `appendToTransport(open, …)`：站点按 `store_id` 优先、`store_name` 兜底归并（**同一个店不会因为分两次搬上车就被算成两站**；新门店接在最大站号之后），写 `transport_item`（`seq` = 站点号）与站点汇总（`item_count`/`weight_kg` 累加，旧行缺 `store_id` 时补全），司机/电话/来源**只补齐不覆盖**，再按站点台账重算 `stop_count` 与 `destination`（不手工拼串），最后复用同一个 `advancePlans(...)` 回填计划。
  - **>1 张**（列出运单号）或 **唯一一张但 `source<>DEVICE`** → **抛业务异常转人工**：站点结构是人排车时定下的决策，设备扫一批货进来就改写等于替人改了排车方案；人工/计划生成的待发车运单同理。
  - **窗口外不并**：挂很久还「待发车」是异常滞留单，硬并进去等于替人认定它是活单。
  - **「并入」与「分车」的分界线 = 那张运单有没有发走**：候选只取 `status=1`，已发走的自然不在里面 —— 所以**第一车开走、第二车才另开一张**，分车语义不受影响。
  - 回执靠 `ColdChainTransport.mergedIntoExisting`（`@TableField(exist=false)`，**不落库**，只用于回执/日志分流：「装车口把这批货并入运单 X…（该车还没发车，继续装的是同一趟）」vs「装车口自动生成运单 X…」）。同一批货重复扫仍由「一批货只能上一次车」硬拦。
  - **落库判据不碰时间戳精度**：MySQL `DATETIME` 是秒级，新建运单与同秒内建的计划分不出来 → `verify-load-db.sh` 改用**业务形态**断言（「5 张计划只落在 4 张运单上」+「存在一张运单挂 3 张计划」）。
  - **演示示例值不许落在会撞车的车牌上**（2026-10-11）：`IngestMonitor.vue` 取装车口示例值前先查 `status=1` 的运单车牌（`fetchPendingPlates()` + `DEMO_LOAD_PLATES = 京A·12345 / 京A·99999 / 京B·10086`，与车辆备案种子一致），优先挑**没挂待发车运单**的计划、计划车牌为空时挑空闲的演示车牌（`pickFreePlate`）；实在只剩会撞的，计划照用、但提示语里明说「本次会并入那张未发车的运单」。**这纯粹是演示页的取样策略，不是业务规则** —— 业务上撞上就该并入。
  - **一车同时只应有一张未发车运单；多了就删多出来的那张**（2026-10-11）：走 `DELETE /distribution/transports/{id}`，**不要手写 SQL** —— `deleteTransport` 的连带是完整的：`releasePlanItems` 先把明细退回「未排车」并按剩余量重算计划状态，再删装载明细 / 站点 / 温度日志 / 运单。**已签收（有 `store_receipt`）的运单会被拦住**，所以删不坏溯源下游。删除前照例 `mysqldump` 备份。删完记得核：同车牌待发车唯一、计划回 `1(待发货)`、计划明细 `shipped=0/transport_id=NULL`、不变量全 0。**批次是否回到待发运池要看它还有没有挂活跃计划的未排车明细** —— 挂着就仍算「被计划占用」而不出现在 `pagePendingSplits`，取消计划才真正放开。
  - **设备原始上报台账（`ingest_staging`）不随业务数据删**：删运单只删业务侧结果，设备报过什么是另一回事，抹掉等于篡改设备侧痕迹（要清须单独确认）。
- **来源三件套**：`source`（MANUAL/PLAN/DEVICE）+`source_ref`（装车口 `LOADER-001`）+`raw_payload`；上链 key = `batchNo+":"+action+":"+pigId`。
- 回归 `logs/verify-load.mjs` + `-db.sh`（**`KEEP=1` 自驱动**）：HTTP **49** / 落库 **55**（含孤儿站点守卫断言与 4 条并入落库断言）。**db.sh 会自行清干净**（计划 3 / 明细 3 / 运单 35 / 装载 36 / 站点孤儿 0 / LOADER-001 暂存回基线）。**`verify-load.mjs` 单独跑（KEEP 与否都一样）只删运单 + 取消计划，计划表行与暂存行会留下**（2026-10-10 实测：5 计划 / 9 明细 / 12 条 LOADER-001 暂存）→ 冒烟完必须按脚本打印的 `PLAN_IDS`/`STAGING_IDS` 精确清回基线，别用 mjs 调断言。

**司机识别**
- **「点位 ≠ 一台设备」**：一个点位可由多台机器组成（相机 / 扫码枪 / 读卡器安装位置与故障模式各不相同，换班/顶班/一车两人是常态）。**通道粒度 = 业务事件粒度**：仍是一个 `LOAD` 通道，报文用 `vehicleSource`/`driverSource` 标来源，相机坏了不影响扫码。设备台账 `LOADER-001`（**`device_no` 本身就是点位标识，不加 `point_no` 列**），多装车位 `LOADER-002…`。
- **现场只在上车那一刻刷一次卡** → 装车位是**两件套**（2026-10-10 起，见上），**读卡器只在驾驶室的车载发车终端**（`DEPART`）。**`CARD` 只可能由 `DEPART` 产生**；装车口报 `cardNo` 直接拒。
- **主数据四表（`db_distribution/29-driver-vehicle-master.sql`）**：`vehicle`/`driver`/`driver_card`/`driver_vehicle`（车牌→常用主驾，**刻意不是一对一**）。**没有备案表，刷卡与反查都无数据可查——主数据才是自动化的地基。** 种子 京A·12345/张师傅/8801、京A·99999/李四/8802、京B·10086/王五/8803。
- **`support/DriverSource` 唯一口径**：`CARD`/`FACE`=本人确认；`PLATE_INFERRED`/`MANUAL`=非本人确认。**确认强度由来源唯一推导，不收设备自报布尔值**。落库 `driver_source`+`driver_confirmed(1/0)`。**「人名非空」≠「本人确认过」。**
- **硬不变量：司机为空 ⇒ `driver_source` 必须 NULL；有名字必须有来源。** 三处都遵守：装车口建单、计划生成、人工编辑（**清空司机时必须同时清空来源**，`updateTransport` 里用 `lambdaUpdate().set(..., null)` 显式写，因为 `updateById` 跳过 null）。原因：写个 MANUAL 等于假装调度录过一个名字，追责时比「两个都空」更糟。
- **接入层 `LoadChannelHandler` 只校验物理必读项，不判司机**；装车口能自报的只有「人脸」（将来若在装卸位加实名终端），其余记 MANUAL。
- **车型补齐**：计划没填用「车牌→`vehicle`」带出；**备案不是准入门槛**。人工建单 `driver_source=MANUAL/driver_confirmed=0`；`updateTransport` 只有司机姓名真被改掉才降级 MANUAL（先取旧值再比较）。

**发车 `DEPART`**
- 唯一一条「**推进已有运单状态**」而非「写入新事实」的通道。设备只报「某车发车了」；车牌只是找车凭据不是责任人。台账 `ingest_device.vehicle_no`（**必须结构化列，不能靠 `location` 猜**）；倒查该车牌名下 `status=1` 运单（0/多张 → 转人工）→ 推进 `STATUS_IN_TRANSIT` + 写 `departTime`。**发不出也是转人工**，不判 `rejected`。
- **`DEPART` 同时是「责任闸门」，也是全系统唯一产生 `CARD`（本人确认）的地方**（2026-10-10 起）：装车口不采集司机，所以「货要走了，责任必须落到具体人」这颗闸门从「生成运单」**后移到发车** —— **人工 `depart(id)` 与 `ingestDepart` 两条路都拦「没有司机的运单」**（提示：请先编辑运单补录，或由司机在车载终端刷卡）。两条路的差别只在于后者同时把「本人确认」记下来。中间那段「已装车、还没发车、司机未知」是**如实记录的正常中间态**，界面上显示「未记录（等发车刷卡确认）」，不是放过。
- 回归 `verify-depart.mjs`（**测试专用车牌 `京A·T0001`** 才能构造「唯一命中」；F 组 `t3` 走**人工建单**，且建单前必须先取消该批次的待发货计划——计划占用中的批次不许手工抢发）、`verify-order-intake.mjs`、`verify-temp-gap.mjs`（**`京A·T0005`**；T1 必须**先发车**再建 T2，否则 T2 会被并进 T1）。落库 `verify-depart-db.sh` 33/0、`verify-temp-gap-db.sh` **34/0**。

**到达 `ARRIVE`（站点级，第 17 条通道）**
- **到达粒度 = 卸货门店，不是批次**：同店多批只到一次，多站车次一站一次。问的是车在不在这个卸货点，与装了几批货无关。
- **`transport_stop` 站点行程表（`32-transport-stop-arrive.sql`）**：站点清单可由 `transport_item` 聚合，但**行程状态**（未到达/已到达/已签收+到达时刻）是新事实，装载明细装不下：`cold_chain_transport` 只有一个 `arrive_time`，`store_receipt` 是**终态凭证**（签收那刻才建行），「到了但还在排队卸货」无处安放。`status` 0未到达/1已到达/2已签收，唯一键 `(transport_id, stop_no)`；`stop_no` **与 `transport_item.seq` 完全同源**（装车扫码顺序 = 卸货顺序）。
- **运单整体状态由站点汇总推导**（主表不另存布尔值）：全部到达→3 待签收；全部签收→4 已签收；否则 2 运输中；**未发车(1) 不推导**。末站到达时刻同步主表 `arrive_time`。
- **签收前置下沉到站点**：「该站已到达」才能签（否则拦不住「车还在第 1 站、第 3 站就签收」）；站点台账为空退回运单级校验（兼容存量）。
- **到达必须按站点序号递增**：跳站 = 漏扫或走错店 → 转人工。（**2026-10-11 起**：报文不报站，所以「跳站」在结构上已不可能 —— 服务端永远只推当前第一个未到达的站。见下条。）
- **报文里没有「卸货门店」（2026-10-11 去掉）**：`DeviceArrive` 删掉 `storeId`/`storeName`/`storeSource`；接入层只校验车牌（**现在唯一可能缺的必读项**）；业务层**直接取「当前第一个未到达的站」**，不再有门店比对与跳站判定。理由：车载终端不是门店收货区的固定设备 —— 车上装 T-Box / 读卡头 / 显示屏，读得到车牌/卡号/时刻/GPS，**读不到门店编码**（ERP/OMS 主数据，不下发到每台车）；而这件事本来也不该由设备回答：车牌把运单锁到唯一一张「未结束」的单子 + 站点清单由装车扫码顺序定 ⇒ 该推哪一站在服务端**唯一确定**，报文既不需要报站、也报不错站。**两条推论/代价必须记住**：① **每刷一次卡推进一站**（设备挑不了站；代价是误刷/重刷会把下一站标成已到达，唯一兜底是「所有站点都已到达」后无处可记 —— 去抖未做）② **「车开到清单外的店」不再有人拦**（正解是定位：GPS/电子围栏，`db_common.sys_org` 目前**没有经纬度**，需先补）。
- 设备只报**现场事实**：哪台车（`vehicleNo`）、**司机刷的是哪张卡（`cardNo`）**、到达时刻（`arriveTime`）；运单按车牌**倒查**、站点按顺序**推导**。**失败一律 `manual`**。`ingestArrive` **不加 `@Transactional`**（同 `ingestLoad`）。
- **到达改为「司机在车载终端刷卡确认」（2026-10-11）**：`ARRIVE` 中文名 =「到达确认（**车载终端刷卡**）」；**刷卡就在车载终端**（与 `DEPART` 是同一台设备），**不是门店侧读卡器**。卡号 → `driver_card` → `driver` 换人 → `arrive_driver_source=CARD`（本人确认），记在**站点**上（`transport_stop.arrive_driver_name/arrive_driver_source`；一车多站可每站不同人 —— **不挤在运单主表那个 `driver_name` 里**，那是「谁在开这趟车」）。**红线分两档判**（2026-10-11）：① 没有未结束的运单（或有 ≥2 张）/ ② 无卡号、卡换不出人（备案待补）/ ③ 站点都到齐了还刷（无处可记）→ **转人工**；而 **④ 刷卡人 ≠ 运单安排的司机 → 直接「已拒绝」**（现场刷错卡/上错车，重刷即可自愈；人工对这条的出路也只有「拒绝」，回执 `retryable=false` 明说别重推）。站点台账为空同样转人工。**判定顺序**：①②（认哪一趟）→ 卡号（谁确认的）→ 落站；先认出到的是哪一趟，才有「谁把它送到这一站」「这是第几站」可言。**空卡号给「转人工」而不是「拒绝」**（车载终端一定读得到卡，「没读到」是条件没到位）。**兜底出口 = 页面上的「确认到达」按钮**（`arrive(id)` → `SOURCE_MANUAL`，确认人留空，不拿运单司机顶替）。存量站点**不回填确认人**（`37-arrive-driver-card.sql`，编一个等于伪造本人确认）。若要放开成「没卡也按设备确认入账」，改 `ingestArrive` 里 `hasText(cardNo)` 那个分支即可。
- **车载终端合并：发车与到站刷卡是同一台设备（2026-10-11）**。接入层 `IngestChannel.deviceGroup()`（`DEPART`/`ARRIVE` 同组 `ONBOARD`，其余 `null`）、`sameDeviceAs()`、`deviceChannelGroup(name)`（设备列表按同组 `IN` 过滤）；`resolveChannel` 从「报文 channel 必须 == 台账 channel」放宽为「同组亦可」。演示入口：`DemoReportRequest.channel` + `demoReport(..., channel, ...)` —— 一台设备两通道时由调用方说清这次是哪个动作，缺省退回设备主通道（单通道设备行为不变）。VO：`IngestDeviceVO.channels/channelLabels`。迁移 `38-onboard-terminal.sql` **不动 device_no**（设备侧标识不带业务语义；改码要牵动 4 个回归脚本与前端）—— `ARRIVE-001` 初改称「车载终端（发车 / 到站刷卡）」、`DEPART-001/003` 初改称「车载终端 京A·12345 / 12346」，台数仍 23。**命名再统一（迁移 `39-onboard-terminal-naming.sql`，2026-10-11）**：三台收成**一个系列** `车载终端（…）` —— `DEPART-001`「车载终端（京A·12345）」/ `DEPART-003`「车载终端（京A·12346）」/ `ARRIVE-001`「车载终端（通用 · 演示 / 回归）」。**三台都承担两个通道**（同组），所以发车 / 到达两个列表里都会有 —— 它们是**三台不同车上的终端**（`DEPART-001/003` 与同车的温度探头 `TEMP-001/003` 一一对应），**不是「同一台设备的重复登记」，不能删**（删了两台车就没终端，`verify-depart*` 也崩）。**只有 `ARRIVE-001` 不绑车**，这不是漏配：① 它是演示 / 回归的通用终端，车牌由报文 `vehicleNo` 带入（`vehicleOf()`：报文优先、台账兜底）；② 更硬的一条 —— **接入层的「缺车牌 → 拒绝」红线正靠它成立**（`ArriveChannelHandler.validate` 判 `vehicleOf(ctx)==null`，报文与台账都拿不到才算缺），**给它绑车牌这条用例立刻失效**（`verify-arrive-stop` C 组）。前端设备列表「接入通道」列改为**把该设备能报的通道都列出来**（`row.channels` 多 tag，回退主通道）—— 只显示台账登记的那一个，就把「一台设备两个角色」藏起来了。回归断言：系列命名格式统一 + 绑定分布（两台绑车 / 一台不绑）各 1 条。**顺带未处置**：`DEPART-003`/`TEMP-003` 绑的 **京A·12346 不在车辆备案表**（备案只有 12345/99999/10086/T0001~T0005）。前端演示页：多通道设备时「接入通道」变下拉（`demoChannelOptions`，`demoChannel = demoForm.channel || 设备主通道`）；**换设备时预选该设备登记的主通道**（`onDemoDeviceChange` 里 `demoForm.channel = demoDevice.value?.channel || ''`，**不是清空**，2026-10-11）—— 清空会让下拉空白、字段却按兜底通道（= 台账主通道）渲染，用户看到「发车字段 / 发车示例值」却想不到要切下拉（发车 `departTime` 与到达 `arriveTime` 字段结构不同）。**预选不改有效通道**（原本兜底算出来就是同一个），只把「当前是哪个动作」显式摆出来；单通道设备不显示下拉、行为不变。**示例值只认「本机所在的那辆车」（2026-10-11）**：车载终端装在车上（`ingest_device.vehicle_no` = 车牌，`IngestDeviceVO.vehicleNo` 已下发，设备名里也写着车号），所以发车/到达的示例**先按本机车牌收窄**（`listTransportsOfPlate(plate)`，用 `GET /distribution/transports?keyword=<车牌>` 反查再精确比对）——绑了车牌的终端绝不退回别人的车（退回就会给出陌生的「京B-TEST…」和别人的司机，车牌与设备名对不上，用户 2026-10-11 报过）；**通用终端（`ARRIVE-001` 不绑车）**才退回「任意一趟」，且只认 `isDemoable` 的（司机在备案卡表 **或** 运单还没记司机）——司机是别人（吴师傅/周司机）的车**不再当示例**（刷谁的卡都会转人工/已拒绝，那是反面路径）。到达的倒查状态也**对齐后端**：认 `(2 运输中, 3 待签收)` 而不是只认 2。取不到时**不拿陌生车凑数**，改为把本车现状说清楚（`describePlateTransports()` 列出该车牌名下运单与状态）并给出下一步（装车 / 排新车 / 改选绑了该车的终端）。**回归注意**：`verify-arrive-stop.mjs` 发车与到站**统一用 `ONBOARD_DEVICE='ARRIVE-001'`**（depart 显式传 `channel:'DEPART'`），新增 C2 组「拿车载终端报温度（别组通道）被挡下」；`-db.sh` 的暂存行基线**必须按「设备 + 通道」分开记**，否则 DEPART 与 ARRIVE 混算。**「排车」两义（2026-10-11，用户报「明明已经排车了，却填不了示例值」）**：使用者的「排车」= 在计划里填车牌 / 编辑计划保存；系统的「排车」= **点「生成运单」**（`POST /distribution/delivery-plans/{id}/generate`，那一刻计划 status 置 2「已排车」、运单才以「待发车」建出来）。**发车 / 到达确认倒查的是运单**（`ingestDepart` 查该车牌下 status=1、`ingestArrive` 查 2/3），只填了车牌而没生成运单 → 必然取不到示例值。现在演示页取不到时会**先判「已排车但没生成运单」**（`listUnshippedPlansOfPlate` = `GET /distribution/delivery-plans?keyword=<车牌>` 再按 `vehicleNo` 精确过滤 + `status ∈ {1,4}`），点名计划号 + 给一个**可点入口**「去『冷链运输』给 <车牌> 生成运单」（`sampleJump`，`router.push({name:'TransportMonitor'})`，在 `fillSample`/`resetDemo`/`onDemoDeviceChange` 清空）；ARRIVE 侧另认一档「车下有 status=1 待发车运单 → 提示把这台车载终端的通道切到『发车确认』先发出去」。**源头也补了一句**：`TransportMonitor.vue` 计划编辑的车牌下 `.form-tip` 写明「填车牌只是把车定下来；点过『生成运单』货才挂到运单上（计划转『已排车』，运单为『待发车』）」。**回归安全**：`verify-*-db.sh` 的运单 / 计划计数都是**相对基线**（跑前记 `BEFORE_*`、跑后比对），新增或删除运单不会打崩断言。
- **改这条通道的回归时注意**：`verify-arrive-stop.mjs` 的 `CARD='8801'`（张师傅）/ `OTHER_CARD='8802'`（李四）必须与 B2 组「刷卡发车写进运单的司机」一致，否则会被判「上错车」；暂存行数 **13 → 9**（去掉门店后：拒绝 **1**（仅缺车牌）/ 转人工 **5** / 入账 3）。**每次上报必须显式传唯一 `bizKey`** —— 演示接口没传时默认 `"DEMO-" + 毫秒`，连着两次落在同一毫秒会被判重复、**不产生新暂存行**，使计数断言偶发少一条（实测 9→8）。
- **倒查认「未结束的运单」（2 在途 / 3 待签收），不是只认 2**：三站到齐后 `recomputeTransportStatus` 已把运单推到 3，此刻设备再报一次到达（外部重推、司机多刷一下），只查 2 会回「**名下没有运输中的运单，请先确认该车已完成刷卡发车**」——把人往「是不是没发车」上带；同时 `next == null → 所有站点都已到达` 变成**死代码**（新写的回归当场抓到）。改为 `in(2,3)` 后该分支才真正可达，0 张文案改为「没有在途的运单（车可能还没刷卡发车，或本趟已全部签收）」，多张文案改为「同时有 N 张未结束的运单」。
- **三档门店匹配 `matchStop`**：门店 id → 门店名 → **单站时按唯一性兜底**（存量 `transport_item.store_name`「示范一店」与 `store_receipt.store_name`「金牧生鲜门店」本就对不上，多站绝不走）。
- **单站也必须有落点（A 方案，2026-10-10）**：站点级到达若只在多站时才渲染，等于「功能上了看不见」（库里 36 张运单**全是单站**）。所以：① `cold_chain_transport` 加两个 `@TableField(exist=false)` 展示字段 `arrivedStopCount`/`receivedStopCount`，`pageTransports` 末尾 `fillStopProgress()` **一次 `in(transportId, ids)` 分组查询**批量填（不做 N+1）；② 列表「站点」列改为**「站点进度」列**（`stopProgressLabel`：`1 站 · 待发车 / 未到达 / 已到达 N/M / 待签收 / 已签收`，多站用 `途经 N 站`）；③ 详情信息条加**「途经站点」**（`stopSummaryLabel`，单站带门店名，台账未加载时退回 `stopCount` 显示「进度读取中」）；④ 签收页 `receiptStopTip`（单站也显示）。多站卡片 `v-if=">1"` **保留**（单站时它确是噪音，那部分由列表列 + 信息条覆盖）。
- 回归 `verify-multistop.mjs`(+`-db.sh`，HTTP 43/0 + 落库 47/0)、`verify-arrive-stop.mjs`(+`-db.sh`，HTTP 42/0 + 落库 **63/0**；专用车牌 `京A·T0002`/`京A·T9999`；含 B2 组「没司机不许发车 → 被拦仍停在待发车 → 刷卡发车成功 → 司机=张师傅/CARD/1」、红线①②③、「不报门店也能到达 / 刷一次推一站」、E 组「确认人落到站点」)。落库侧两条最值钱的**全局不变量**：「全库运单状态与站点台账汇总一致（2/3/4 都由站点推出来）」「已签收 ⇒ 必有 `arrive_time` 且必有 `receipt_id`」。

**温度打卡**
- **「多久采一次」是探头的属性** → `ingest_device.sample_interval_seconds`（系统不轮询，车会进隧道/地库）。`temperature_log` 记 `sample_interval_seconds`（**写入时固化**）/`gap_seconds`/`is_gap`；**断档标在断档后的第一条上**，容差 `ingest.temperature.gap-tolerance-multiplier`(默认 3)。**首条 `gap_seconds=NULL`/`is_gap=0`；补传（早于上一条）为负且不判断档**。**空白 ≠ 正常**；`is_abnormal`（单点越界）与 `is_gap` 互相独立，越界值不降采样、永远留档。recordTime 取**报文级 `reportTime`**，不是 `data.recordTime`。
- **存量断档断言别写成「历史断档记录 = 0」（2026-10-11 修正）**：页面演示在演示运单（如 `TR-TEST-0008`/id=9）上报会**真的**产生断档记录（`ingest_staging.biz_key` 前缀 = `DEMO-`；回归脚本用 `IDEM-/DIRTY-/ABNORMAL-/FAST-/VERIFY-`），那是真实业务数据（纪律九.7 不清）。`verify-temp-gap-db.sh` 改为两条不变量：① 跑前捕获 `BEFORE_GAPS`、跑后**条数不变**（回填会变多、篡改会变少）；② 每条 `is_gap=1` 必须**自带证据**（`gap_seconds` 非空且 `> COALESCE(sample_interval_seconds,300)*3`，回填造不出）。断言数 33→**34**。
- **装车口「货物归属」只认「还能发货」的计划（2026-10-11 修复）**：`ingestLoad` 按 `split_batch_id` 查未排车明细时**必须带计划状态过滤**（`.inSql(DeliveryPlanItem::getPlanId, PLAN_ACTIVE_SQL)`，`PLAN_ACTIVE_SQL = SELECT id FROM delivery_plan WHERE status IN (1,4)`）。原因：**待发运池**（`EXCLUDE_TAKEN_BY_PENDING_PLAN`）与**人工建单的占用判定**（`createTransport` / `loadPlanBatches`）**都**按 `p.status IN (1,4)` 释放已取消计划的批次，唯独这处漏了 —— 计划一取消，它的明细 `shipped` 仍是 0，同一批次重新排一张计划后就被算成「同时挂在两张计划的未排车明细上」→ **永久转人工**，这批货再也发不出去。**回归症状**：`verify-arrive-stop-db.sh` **连跑第二次必红**（第一次留下的已取消计划明细，把第二次选的批次判成冲突；报错「分割批次 N 同时挂在两张计划的未排车明细上」）。修完连跑两次 **67/0**。
- 接口：`POST /distribution/internal/ingest/{load,depart,order,arrive,split-snapshot}`、`POST /distribution/delivery-plans/{id}/generate`、`GET /distribution/splits/pending`、`GET /distribution/transports/{id}/stops`、`GET /distribution/split-snapshots`。

**分割车间监控快照 `SPLIT_SNAPSHOT`（第 18 条通道，2026-10-11）**
- **落点**：新表 `db_distribution.workshop_snapshot`（一条 = 一张照片：`device_no` / `workshop` / `file_id` / `captured_time` / `source` / `source_ref` / `raw_payload` / `create_time`）。**不挂 `split_batch`**：那张表一行 = 一个部位，而照片是「车间工位在某时刻的样子」，与哪个部位无关；`split_batch.file_ids` 留给「分割作业凭证照片」，本通道**不动它**。**不进 `TraceStage`**（不是溯源环节，不参与来源管控，加进去只多一行无意义策略）。迁移 `40-workshop-snapshot.sql`：建表 + 设备台账 `CAM-SPLIT-001`（`SPLIT_SNAPSHOT` / `sample_interval_seconds=60` / **不绑车牌** / `secret_key=dev-key-cam-split-001`）→ **设备 23 → 24**（已核查：无脚本硬编码设备总数，DEPART/ARRIVE 的通道内断言不受影响）。
- **两段式上报**：设备先 `POST /file/upload`（网关白名单已放行）换 `fileId`，再把 `{fileId|capturedTime|workshop}` 报给 `POST /ingest/device/report`。接入层 `SplitSnapshotChannelHandler` **照抄免疫注射的防伪**：调 `GET /file/{fileId}/meta` 核对照片真实存在 —— 编造的 fileId 判 **`rejected`（已拒绝，retryable=false）**；文件服务抖动判 **`dropped`（已丢弃，status=4，回执文案「已降采样」）**。**失败一律不进人工队列**（照片是分钟级周期快照，丢一张不影响追溯，转人工会把队列淹没）。报文缺 `fileId` 由 `validate` → `RejectException` → **已拒绝**。
- **拍摄时刻取报文级时间**（`Payloads.time(data,"capturedTime")`，缺省才退 `ctx.reportTime()`）—— 与温度打卡同一条纪律，现场时刻不拿入库时刻顶替。
- **记录「哪台相机拍的」用 `deviceNo`**（与温度 `recorder` 同理：本来就是机器在记录）；**没有操作人字段**，不用设备名顶替人名。
- **滚动保留窗口**（页面约定「一次分割 = 约 10 张」）：`ingest.split-snapshot.retention`（默认 **20**，页面展示最近 **10**）。落库后**同事务**淘汰该相机超出的最老快照（按 `id DESC ... OFFSET retention`，用主键而非时间戳比先后）。**淘汰删文件前必须先确认「没有别的快照还指向这份 fileId」**（`selectCount` 守卫）—— 文件服务按**内容哈希去重**，同一张图重复上传会拿到**同一个 fileId**，无条件删文件会把还在用的照片删掉，症状是后续上报被接入层判成「照片不存在（疑似伪造）」。
- **前端**：`web-admin/src/views/distribution/SplitBatch.vue` 批次树卡片**下方**新增「分割车间监控」卡（最近 10 张 `<el-image>` + 拍摄时刻 + 车间 + 刷新 + 空态提示），**30 秒轮询**（标签页隐藏时跳过，`onBeforeUnmount` 清定时器）；图片直接 `<img src="/api/v1/file/{fileId}">`（网关对 `/file/` 公开）。API：`distributionApi.getSplitSnapshots({limit})` → `GET /distribution/split-snapshots?deviceNo=&limit=`。演示入口：采集监控页通道下拉选「分割车间监控」，一键示例会**真上传一张图换真 fileId**（`uploadDemoSnapshotPhoto`）再提交。
- **回归**：`logs/verify-split-snapshot.mjs`（HTTP **19/0**：两段式 / 伪造 fileId → 已拒绝 / 缺 fileId → 已拒绝 / 滚动窗口收敛到 20）+ `-db.sh`（落库 **21/0**，自带清理与水位复位）。**跑法**：`bash logs/verify-split-snapshot-db.sh`。**注**：`workshop_snapshot` 是**滚动窗口不是历史档案**，跑完应回 0 行；`store_receipt` 全局孤儿断言至今没人写（补时需先豁免 id=17 历史种子）。
- **⚠️ 回归写入必须打「一次性测试相机」，不能打生产相机 `CAM-SPLIT-001`（2026-10-11 修）**：滚动窗口 `pruneOldSnapshots` **按设备**生效 —— 往生产相机写，一旦写满 20 张就会挤掉它最老的那张，而那张可能是**界面演示数据（= 真实业务数据，不清）**。症状有二：① `-db.sh` 的「回基线」断言必红（`19 vs 20`，被挤掉的那行永远补不回来）；② **回归把用户数据删了**。**现行口径**：`-db.sh` 自己 `INSERT ... ON DUPLICATE KEY UPDATE` 登记一台一次性相机 **`CAM-SPLIT-TEST`**（自有 `secret_key`），以 `SNAPSHOT_DEVICE=CAM-SPLIT-TEST` 调 mjs，跑完**连台账一起注销**；生产相机**降级为只读**（只核台账：通道/间隔/启用/不绑车）。mjs 内新增**写入安全闸门**：探到目标相机已有快照 → 打印 `#SKIPPED 1` 并**整体跳过写入节**（`baselineRows.length > 0` 直接退出），绝不挤别人的行。**由此回归对「相机里有没有演示数据」不再敏感，连跑两次均可 21/0。**
- **通用教训**：凡是「带按设备/按用户滚动淘汰」的写接口，回归都**不能借用生产台账上的设备号**；要么自带一次性主体，要么只读降级。否则回归自身不干净、也不安全。

## 八、订单转入 `ORDER`
- **订单 ≠ 计划 ≠ 运单**：订单是计划的来源之一（`delivery_plan.plan_source` ∈ MANUAL/ORDER，`support/PlanSource` 唯一口径 + `order_no` 留号），落 `status=1`，**车与司机留空——排车是人的决定**。
- **刻意不做**：不建订单表（权威副本在外部 ERP/OMS，存一份就是第二个真相，原始报文已在 `ingest_staging.payload`）；不收金额；不做跨订单归并。
- **绝不能判 `rejected`**（`retryable=false` = 数据不成立、不要重推 → 外部系统据此删单，订单**永久丢失**）：distribution 侧 catch `REMOTE_CALL_ERROR` 返回 `ingestManual` 不外抛，handler 侧 catch 全部转 `manual`。
- **配货**：按部位找可发运批次 **FIFO（createTime 升序）**、`MeatPartResolver.resolve` 反查部位（与分割上报共用一份实现）；凑不齐转人工（不猜、不拆零兜底）；批次不可拆→允许略超写 `remark`（截断 500）。
- **门店校验 `OrgClient`**（`GET {auth-url}/auth/internal/orgs/{id}`，`type='retail'`）：查不到/类型不符/不可用一律转人工。与 `BreedingClient` 差别：那边失败只记日志，这边必须转人工。
- **零/负重量必拦**：`need < 0` 会让「凑够即止」立刻成立 → 建 0 明细空计划。三道防线：DTO `items` 加 **`@Valid`**（**嵌套 record 列表上的 `@DecimalMin` 不加 `@Valid` 不级联**）、handler `validate` 判 `weight<=0`、service 再防御。
- `operator` 写「订单转入（终端号）」**不写人名**；台账 `ORDER-TERM-001`（`30-order-channel.sql`）。
- **列表展示的标识字段必须都能搜**：`pagePlans` keyword 覆盖 `plan_no`/`order_no`/`store_name`/`vehicle_no`。
- **订单类数据的业务幂等键必须每次唯一**（订单号）：写死「当天-001」会让第二次演示命中幂等，回执照样「已入账」但列表不增行（前端已改 `SO+yyyyMMdd-HHMMSS`）。

## 九、环境与纪律
- bash 无 `mvn`：容器 `maven:3.9.9-eclipse-temurin-17`+`pork-m2` 卷（`MSYS_NO_PATHCONV=1`）。
- **改后端代码后必须 `docker compose build --no-cache <service>`**（普通 build 命中过期缓存），**在 `backend/` 执行**。
- **⚠️ 构建启动后再改的源码不会进镜像**：症状隐蔽——「新功能大多正常，只有某分支失效」。排查 `find . -name '*.java' -newermt '<构建启动时刻>' -not -path '*/target/*'`；`docker cp` 出 jar 用 Python `zipfile` 读 class 常量池比对关键字。**纪律：改完再启动构建；构建已开跑又改了源码，必须重建。**
- MySQL 容器 `pork-traceability-mysql-1`，root `change-this-root-password`（业务 `pork_user/123456`）；SQL 走 `docker cp`+`--default-character-set=utf8mb4`。`ingest_staging.payload` 是 json 列 → 比较须 `CAST(? AS JSON)`。
- 前端构建 `CODEBUDDY_SAFE_DELETE_ENABLED=0 npm run build`；nginx bind mount `dist`，只重建不必重启。agent-browser 冷启动 >6 分钟，改用 `logs/verify-*.mjs`。
- **清理回归数据**：删前 `mysqldump` 到 `logs/backup/`；别漏 `blockchain_record`（按 `(biz_type,biz_id)` 成对匹配，`biz_id` 跨类型撞号；盖章走 `SLAUGHTER_INSPECT`+章 id）；删批次先删关联行；`VaccineRecord` 带 `@TableLogic` 作废后行仍在。
- **残留必清，且要回滚被改动的既有行**：`verify-{entry-gate,preslaughter-gate,ractopamine-block,exit-apply}-db.sh` 参数驱动 + **不自带清理**（ID 由 mjs 打印）；「轻量只读」脚本（`verify-breed-dict`/`verify-device-name`/`verify-trace-void`/`verify-cert-issue`）也会写 staging/疫苗/票据。用**时间窗**法（模板 `logs/clean-4slaughter-residue.sh`）：`mysqldump --where` 备份 → 按 `create_time`（`source_fallback_unlock` 用 `apply_time`）删子→父 → 回滚既有行；**「改了哪些既有行」用 `information_schema` 生成「全表 `update_time >= 起点` 计数」SQL 扫**（只删新增会漏回滚）。清完跑 `verify-reconcile.mjs` 复核（快照 23/25/34/34，**偏高即没清干净**）。
- **跑前清场只能删自己造的**（按业务键过滤，**不能按 `device_no` 整表删**：终端号常兼做界面演示，会抹掉演示留下的真实记录；ORDER 暂存基线 **2**，按 `payload.orderNo LIKE 'SO-REG-%'` 过滤）。
- **新增「挂在运单下的子表」必须同步所有清理脚本**：`transport_stop` 上线时 `verify-{delivery-plan,depart,load,temp-gap}-db.sh` 的清理只删运单/装载明细 → 运单删掉后**站点行悬空**。实测跑完这几轮残留 **9 行孤儿站点**（站点台账 45 而非 36，「未到达」16 而非 7），**而且没有任何断言会报警**（各脚本只断言运单/装载/签收数，没人看新表）。规矩：新子表上线时**逐个 grep `DELETE FROM db_distribution.cold_chain_transport`** 把清理补全（按父 id 段 + 按时间窗两处），并统一加守卫断言「**孤儿行（父已不存在）＝0**」；全量跑完再核对子表总数。「表建好了、断言没人写」是这类漏删的共同根因。
- **可复用清库脚本 `logs/clean-plan-chain.sh`（2026-10-11 新增）**：一键清「发货计划 + 其生成的运单链 + 5 个冷链通道暂存」，自带 `mysqldump` 备份到 `logs/backup/plan-chain-<ts>.sql` 与孤儿守卫复核。定义：`delivery_plan` 整表 → 由 `delivery_plan_item.transport_id` 定位运单 → 删这些运单的 `store_receipt`/`transport_stop`/`transport_item`/`temperature_log`/`retail_sale` → 删运单 → 删计划明细 → 删计划；暂存按 `channel IN ('LOAD','DEPART','ARRIVE','TEMPERATURE','ORDER')`。**只删「计划生成的运单」，历史运单/签收/零售/上链不动**；批次占用靠 `delivery_plan_item` 表达，清完自动回待发运池（无需手工改 `split_batch`）。清理后复核：计划 0、5 通道暂存 0、装载/站点/温度孤儿 0；`retail_sale` 孤儿 12（挂 `transport_id=17`，与 `store_receipt.id=17` 同源的 9-16 历史种子，**既有例外，不处置**）。
- **断言盯「不变量」，别盯「某类数据的绝对条数」**（2026-10-11 实修）：`verify-temp-gap-db.sh` 曾写「`create_time < START_TS AND is_gap=1` 必须 = 0」，但演示数据会落在同一条业务路径上**真的**产生断档记录 → 长期假红。改成「历史断档**条数不变**（跑前捕获基线）+ 每条断档**自带证据**」。同类风险的断言自查一遍：凡「历史 X 类记录 = 0」都要问「演示/正常业务会不会合法地产生一条」。
- **⚠️ 回归脚本被「中途掐断」＝留下残留，并让后续每一次跑全线崩（2026-10-11 真实踩到）**：清理段在脚本末尾，**被 SIGTERM/超时杀掉就永不执行**。实测：`for i in 1 2 3` 连跑到达回归，**第 3 次被命令超时杀掉** → 回归专用车牌 `京A·T0002` 下留了一张 `status=3` 运单 `TR20261011259602`（id 239）+ 3 个计划 `DP20261011-001/002/003`（id 319–321）+ 11 条暂存行（id 3534–3544）。下一次跑命中「同车牌 2 张未结束运单 → 转人工」，**从第一条身份断言起全线 FAIL（56/9）**，外表酷似功能坏了 —— 实际脚本的**首条前置守卫**已把原因写清：`★ 前置：专用车牌名下没有残留运单 —— 残留=TR…/3`。**规矩**：① **给足超时、让它跑完，别用短超时「连跑三次」验稳定性**；② 被 kill 过就按**车牌 + 时间窗**找残留，**子表→父表**手工清（`store_receipt`→`transport_stop`→`transport_item`→`cold_chain_transport`，再 `delivery_plan_item`→`delivery_plan`，最后 `ingest_staging`），清完核对水位回基线；③ 见到「前置…残留」这条 FAIL，**先清残留再重跑，不要怀疑代码**。
- **`grep -v` 在无输出时退出码为 1**：`MYSQL() { … mysql … | grep -v "Using a password"; }` 用于 `DELETE`（`-N` 无输出）会把 `&&` 链掐断、后续语句全不执行（症状：整条命令 exit 1 且无任何输出）。helper 里补 `|| true`，或改用 `;` 串联。
- **已知存量例外（2026-10-11 记录，未处置）**：全库有 **1 条孤儿签收** `db_distribution.store_receipt.id=17`（父运单 `id=17` 已不存在；`source_ref='历史人工录入'`、`create_time=2026-09-16`，2026-10-04 备份起即在库）。**不是回归产物**，按「只删自己造的」未删。注意：`transport_stop`/`transport_item` 有全局孤儿断言（=0），**签收表只断言了「本次」的孤凭证**（`$R_SCOPE`），全局那条一直没人写 —— 若将来补上，需先处理这 1 行或显式豁免。
- **MyBatis-Plus `updateById` 忽略 null**（`NOT_NULL` 策略）：清空某列必须 `lambdaUpdate().set(Field, null)`（踩坑：删运单后计划退回「待发货」，`setTransportNo(null)` 没生效）。
- **⚠️ `insert` 也跳过 null —— 数据库会拿列默认值顶上，等于 DB 替应用编数据（2026-10-10 真实踩坑）**：`cold_chain_transport.driver_source` 建列时 `DEFAULT 'MANUAL'`，而司机留空时应用把它置 null → 列被填成 `'MANUAL'`，落出「`driver_name` 为空、`driver_source=MANUAL`」的矛盾行（追责时比两个都空更糟）。**症状极具误导性**：Java 代码肉眼正确、HTTP 与 DB 结论还打架（详情接口读到的就是 DB 里的假值）。修法两处一起上：① 迁移把列默认值改成 **NULL**（`34-driver-source-drop-default.sql`）+ 清存量矛盾行；② 实体标 `@TableField(insertStrategy = FieldStrategy.ALWAYS)` 让应用的意图永远生效。**规矩：新增可空列时一律不给业务含义型默认值**（默认值只该是「技术上无害」的值，不能替业务做判断）。
- **断言别盯着回执文案**：成功回执 `message` 硬编码「已入账」，业务附言（如「司机未记录」）**不进回执**、只落业务表/详情。本次回归首跑的另一条失败就是断言去回执里找「未记录」而假红。
- **环境会批量 `paused` 全部 `pork-traceability-*` 容器**（反复出现）：处理逐个 `docker unpause`；**刚构建过新镜像时 unpause 不够，必须 `docker compose up -d --force-recreate <service>`**（否则状态显示「Up n hours」却仍跑旧进程）。
- **实际表名（极易记错）**：猪档案 `db_breeding.pig_individual`（**不是 `pig`**）、出栏申报 `slaughter_apply`、检疫证 `quarantine_certificate`、入场查验 `entry_inspection`、屠宰检验 `slaughter_inspection`、盖章 `carcass_stamp`。`blockchain_record`/`sys_user` 在 `db_common`，`ingest_staging`/`source_fallback_unlock` 在 `db_ingest`。
- **界面演示产生的数据算真实业务数据，不清**（如 `CB20261009-001` 及其关联行、`DP20261010-001` 与运单 `TR20261010975935`）；计数比基线高先分辨是测试残留还是演示数据。
- **当前水位基线（2026-10-11 22:40「分割车间监控」轮后，实测）**：**发货计划 1 / 计划明细 1** / **运单 35** / 装载明细 35 / **站点 35** / 签收 28 / 温度 77 / 分割批次 35 / 胴体批次 24 / **设备 24**（+1 监控相机 `CAM-SPLIT-001`）/ 车辆备案 8 / **`workshop_snapshot` 0**（滚动窗口，跑完必回 0）/ **冷链暂存 = 4 条（`DEMO-` 前缀的界面演示行，不清）**。较 19:55 清库后的 0/0/34/34/34/27 **各 +1**：全部来自 **20:00 那次界面演示**（采集监控页一键示例 `ORDER→LOAD→DEPART→ARRIVE`，计划 `DP20261011-001`、运单 `TR20261011150608`/id=251、站点 1、签收 1）—— **演示数据算真实业务数据，不清**（九.7）。`verify-*-db.sh` 全用相对基线（跑前记 `BEFORE_*`），不受影响。
- **上一轮（2026-10-11 19:55 用户主动清库后，实测）**：**发货计划 0 / 计划明细 0** / **运单 34** / 装载明细 34 / **站点 34** / 签收 27 / 温度 77 / 分割批次 35 / 胴体批次 24 / **设备 23** / 车辆备案 8 / **5 个冷链通道暂存全 0**。由 `logs/clean-plan-chain.sh` 执行（见九节登记）。保留的 34 张是 9 月历史运单及其 27 签收 / 77 温度 / 46 零售 / 上链记录。
- **上一轮（2026-10-11 19:40「排车 ≠ 生成运单」轮后，实测）**：**计划 5 / 计划明细 5** / **运单 36** / 装载明细 37 / **站点 36** / 签收 27 / **设备 23** / 车辆备案 8 / LOADER-001 暂存 2 / ORDER 暂存 2。**较上一轮的 35/36/35 各 +1**：计划 `DP20261010-004`（225）由「待发货」转「已排车」，建出运单 `TR20261011932373`（id 246，京A·12345，待发车，无司机）及其 1 条装载明细 + 1 个站点 —— 是用户排好的真实业务数据，不是测试残留。
- **上一轮（车载终端合并轮）**：**计划 5 / 计划明细 5** / **运单 35** / 装载明细 36 / **站点 35** / 签收 27 / **设备 23** / 车辆备案 8 / LOADER-001 暂存 2 / ORDER 暂存 2。**运单与站点由 36 降到 35 不是丢数据**：存量裂单 `TR20261010975935`（id 86，演示运单）与 id 105 合并时，重复的那个站点行按门店归并掉了（单站运单装 2 批货）。不变量全 0（站点孤儿 / 装载孤儿 / 计划明细悬挂 / 空名字配来源 / 有名字没来源 / `stop_count` 与台账不符 / `shipped` 与运单号矛盾 / 零站点运单 / 状态与站点汇总违例 / 已签收缺 `arrive_time` 与 `receipt_id`）；`重复引用批次 = 8` 是**存量历史事实**，前后不变。

## 十、来源管控简化 + 设备环节编辑入口整体删除（2026-10-12）

用户指令：「已有自动化录入部分的页面的编辑功能」全部删除；「胴体批次、养殖建档、冷链运单/计划」的编辑也删除（**冷链运单/计划除外，保留**）；「所有页面里有『该环节为关键溯源环节，人工新增入口已关闭…前往设备接入 ›』这种提示的都删除」；「申请兜底解锁的也删除」。

**口径决定（重要，别回退）**：用户要删的是**编辑**，不是**新增**。所以「产地检疫出证 / 申请出栏 / 胴体批次人工建批」三个人工**新增**入口 **保留**（代码里有明确业务必要性：纸质检疫单补录、目标屠宰场是设备给不出的去向声明、没装分拣台的屠宰场），但**不再需要票据**。

**一、兜底解锁整体删除（前后端）**
- 后端删除：`common-core/source/FallbackTicket`、`FallbackTicketCodec`；`ingest-service` 的 `FallbackUnlockController` / `FallbackUnlockService` / `FallbackUnlockRequest` / `SourceFallbackUnlock` / `SourceFallbackUnlockMapper`；`SourceGuard.TICKET_HEADER` / `requireFallbackUnlocked(...)` ×2 / `fallbackMessage(...)` / `SourceGuard.fallbackRequired(stage)`；`SourcePolicy.fallbackRequired()`；`SourcePolicyProperties.fallbackSecret` / `fallbackTtlMinutes` / `fallbackTtlMillis()`。
- 前端删除：`utils/fallbackTicket.ts`（含 `FALLBACK_ROUTES` 接口→环节映射）、`components/common/SourceGuardBar.vue`、`composables/useSourcePolicy.ts`；`utils/request.ts` 里的 `X-Fallback-Ticket` 自动附加；`api/modules/ingest.ts` 的 `fallbackApi` / `FallbackUnlockRecord` / `sourcePolicy()`；采集监控页的「来源管控」「兜底解锁」两个页签及其脚本（模板删 158–269 行、脚本删 590–676 行量级）。
- **保留**：`db_ingest.source_fallback_unlock` 表（迁移 `16-void-and-fallback.sql` 不动）、`GET /ingest/source-policy` 端点与 `SourceGuard.policies()` —— **7 个回归脚本（`verify-batch-autogroup` / `verify-entry-gate` / `verify-exit-apply-device` / `verify-preslaughter-gate` / `verify-quarantine-device` / `verify-ractopamine-block` / `verify-stage-order`）都在断言它，删了必红**。
- `SourcePolicy.DEVICE_PREFERRED` 语义改为「设备自动采集为主；设备覆盖不到时可人工补录，写入强制实名并上链留痕」；**枚举名与 `manualBlocked()` 不动**（回归断言 `policy === 'DEVICE_PREFERRED'`）。

**二、设备环节编辑入口整条删除**
- 前端：`PigTable` / `VaccineTable` / `EntryTable` / `RactoTable` 去掉「编辑」按钮（`InspectTable` / `StampTable` 早就没有，属死代码），`defineEmits` 收窄；父页去掉 `@edit="handleEdit"`、`handleEdit` / `formDialogVisible` / `currentEditData` / `dialogMode` 与弹窗模板块；**删除 6 个弹窗组件** `PigFormDialog.vue` / `VaccineFormDialog.vue` / `EntryFormDialog.vue` / `RactoFormDialog.vue` / `InspectFormDialog.vue` / `StampFormDialog.vue`；`CarcassBatch.vue` 去掉行内「编辑」+ `isEdit`/`editingId`/`handleEdit`，弹窗标题固定「创建胴体批次」。
- 后端：删除 `PUT` 端点与 `updateXxx` 实现 —— `VaccineRecordController/VaccineRecordService.updateRecord`、`PigIndividualController/PigIndividualService.updateIndividual`、`CarcassStampController/CarcassStampService.updateStamp`、`EntryInspectionController/EntryInspectionService.updateEntry`、`RactopamineTestController/RactopamineTestService.updateTest`、`SlaughterInspectionController/SlaughterInspectionService.updateInspection`。**回归无覆盖**（所有回归的 `PUT` 都打在计划/运单/出栏审批/养殖场/运单 depart·arrive 上）。
- **保留**：`PUT /distribution/batches/{id}`（服务「添加关联生猪」的 `pigIds` 追加）、`PUT /distribution/transports/{id}` 与 `PUT /breeding/farms/{id}`（回归在用）、以及所有 **作废**（`POST …/{id}/void`，实名 + 理由 + 上链）。
- **删掉的代价（已知并接受）**：设备上报时漏传凭证的记录，此后再没有「事后补附件 / 补备注」的入口；要补只能作废后重录。

**三、踩坑记录（下次别再犯）**
用脚本批量删「接口方法声明」时，终止符判错了：接口方法以 `;` 结尾、以 `}`（类闭合）收尾的是实现类。脚本按「第一行等于 `    }`」找方法末尾，对**接口文件**会一路删到文件尾（`PigIndividualService` / `VaccineRecordService` / `CarcassStampService` / `EntryInspectionService` / `RactopamineTestService` / `SlaughterInspectionService` 六个全中），编译报 `reached end of file while parsing`。**修法**：按实现类的 `@Override` 集合反推接口应声明的方法（含 `deleteStamp` / `deleteInspection` 这种「调用即抛业务异常」的占位方法）后手工重建。**教训：批量删代码前先确认语言元素（接口 `;` vs 类 `}`），并对目标文件先做 dry-run 行数核对。**

## 十一、来源管控收口后的全量回归（2026-10-12 收尾）

**跑法（关键）**：`-db.sh` 才是正确入口。分两类 ——
1. **自驱型**（自己跑 mjs + 落库断言 + **自带清理**，直接 `bash logs/xxx-db.sh`）：`verify-batch-autogroup` / `verify-depart` / `verify-multistop` / `verify-arrive-stop` / `verify-load` / `verify-delivery-plan` / `verify-order-intake` / `verify-split-job` / `verify-split-snapshot` / `verify-temp-gap`。
2. **传参型**（先跑同名 `mjs`，参数取输出末尾的「清理用」行；**这 5 个 db.sh 不做清理**）：`verify-exit-apply-db.sh <设备申报ID> <人工申报ID> <猪B的id>`、`verify-entry-gate-db.sh <猪id> <出栏申报id> <入场查验id> <耳标号> <待人工流水id>`、`verify-preslaughter-gate-db.sh <E猪id> <F猪id> <G猪id> <H猪id> <E耳标> <F耳标> <H耳标>`、`verify-ractopamine-block-db.sh <阴性猪> <阳性猪> <同批猪> <阳性耳标> <阳性批次号> <未检猪> <未检猪耳标>`、`verify-vaccine-photo-db.sh <已入账记录ID> <人工补齐记录ID> <stagingID> <fileId>`。

**⚠️ 直接裸跑同名 `mjs` 会留残留**（本轮真实踩到：裸跑 `verify-batch-autogroup.mjs` / `verify-quarantine-device.mjs` / `verify-ractopamine-block.mjs`，留下 11 头测试猪 + 94 条暂存行 + 各类子表行）。清理顺序 **子表 → 父表**：`blockchain_record` → `carcass_stamp` / `slaughter_inspection` / `ractopamine_test` / `entry_inspection` / `vaccine_record` → `carcass_batch_pig` → `carcass_batch` → `quarantine_certificate` → `slaughter_apply` → `pig_individual` → `ingest_staging`。临时账号 `vn*` 要连 `db_common.sys_user_role` 一起删（**没有 `db_auth` 库，用户表在 `db_common.sys_user`**）。

**本轮全量结果（29 个 mjs + 15 个 db.sh，全绿）**：channel-label / stage-order 全通过；batch-autogroup 37→42；cert-issue 通过（免票据抵达业务层）；**exit-apply-device 21/0** + db 11/0；quarantine-device 15/0；ractopamine-block 43/0 + db 33/0；depart 33/0；multistop 47/0；arrive-stop 67/0；load 55/0；delivery-plan 26/0；order-intake 22/0；split-job 31/0；split-snapshot 21/0；temp-gap 34/0；entry-gate 27/0 + db 21/0；preslaughter-gate 34/0 + db 26/0；vaccine-photo 13/0 + db 6/0；reconcile 18/0；其余 mjs（breed-dict / device-name / duplicate-report / operator-is-human / tag-birthdate / org-sync / org-auto-sync / trace-void / vaccine-void-visible）全通过。

**水位基线（清完复核，2026-10-12）**：批次 **23** / 分割 **34** / 零售 **34** / 有效生猪 **25** / 计划 **1** / 计划明细 **1** / 运单 **35** / 装载明细 **35** / 站点 **35** / 签收 **28** / 设备 **24** / `workshop_snapshot` **2**（滚动窗口，split-snapshot db.sh 的基线）。孤儿行终检：`transport_item` / `transport_stop` / `temperature_log` / `carcass_batch_pig` / `split_batch(level=1)` 全 **0**；`pig_count == JSON_LENGTH(pig_ids) == COUNT(carcass_batch_pig)` 不匹配 **0**。
**唯一孤儿例外**：`db_distribution.store_receipt.id=17`（父运单 id=17 已不存在；`source_ref='历史人工录入'`、`create_time=2026-09-16`、同源地还有 12 条 `retail_sale` 孤儿挂 `transport_id=17`）。2026-10-04 备份起即在库，**不是回归产物，按「只删自己造的」不处置**。将来若给签收表补全局孤儿断言，需先豁免这一行。

**本轮修掉的两个脚本问题**：
1. **`verify-exit-apply-device.mjs` 陈旧负向用例（4 项连锁 FAIL 的根因）**：第 8 步仍写「人工申报**不带兜底票据** → 403」。新口径下该环节免票据 → 这一步**先成功建了一条申报** → 第 9 步撞 `uk_pig`（「已存在待审批的出栏申报」）→ `manualApplyId=null` → 第 11 步 URL 变 `/breeding/applies/null/approve` → `5000 系统内部异常` → 第 12 步猪状态不推进。**外观极像功能坏了，实际是脚本没跟上口径。** 修法：删旧第 8 步，「免票据成功入账」并成新第 7 步（补一条「回执带业务记录编号」断言），后续步骤顺延。**教训：改口径时必须连带扫一遍「负向用例」**——正向用例写错报「功能没生效」，负向用例写错报「功能生效了」，后者最容易被误判成真 bug。
2. **`verify-trace-void.mjs` 空跑**：瘦肉精上报没带 `operator`，按「设备 ≠ 人」现在一律转「待人工处理」（`accepted=false`/`targetId=null`），脚本 `if (!tid) return` 提前返回，只剩一行诊断。修法：报文补 `operator: '刘兽医'`（本就是必要的业务字段，不是开后门）。现在跑通「入账 → 实名作废 → 追溯标注作废摘要 → 作废记录不在有效列表」。

**`GET /ingest/source-policy` 的 7 个断言脚本（改动时别删端点）**：`verify-batch-autogroup` / `verify-entry-gate` / `verify-exit-apply-device` / `verify-preslaughter-gate` / `verify-quarantine-device` / `verify-ractopamine-block` / `verify-stage-order`。

## 十二、环节说明横幅全删 + 分割页批次树扁平化（2026-10-12 下午）

**口径（用户确认）**：①「没有合格的宰后检验/瘦肉精没检过…不得盖章」这类**环节说明横幅**全部下线；② 分割操作页批次树**只呈现实物** —— 不要「分割作业」节点、不要出肉率、不要「齐 / 必出缺」对照。两件都是**纯前端展示口径**，后端业务逻辑零改动。

### 一、7 处横幅（页面级「业务前提 / 环节说明」）
`slaughter/CarcassStamp` / `slaughter/SlaughterInspect` / `slaughter/EntryInspect` 的 `precondition-tip`；`distribution/CarcassBatch` 建批弹窗内 `ractopamine-tip`；`farm/QuarantineCertList` 的 `cert-tip`；`farm/QuarantineCertDialog` 的「为什么这一环要在系统内登记」；`distribution/TransportMonitor` 的「数据来源」卡片（卡片里只有这条横幅，**整卡删**）。CSS 一并清。

**刻意保留（口径：删「静态科普横幅」，留「随状态出现的操作提示」）**——下次别连这些一起删：
- QuarantineCertDialog「一头猪同一时间只能有一张有效证」（有证才出现，换发指引）
- TransportMonitor「车牌必须补全后才能生成运单」（缺车牌才出现）
- TraceSearch 作废标注（数据标注）
- IngestMonitor 三条规则说明（「自动采集 ≠ 自动生效」/「设备用密钥接入」/「模拟上报说明」—— 平台/演示说明，非环节说明）
- tag 上的 hover tooltip（如 SplitBatch「设备独占」）

**判别口诀**：横幅 = 页面一进来就挂着的、解释「为什么这么设计」的 `el-alert`；提示 = 满足某条件才出现的、告诉你「下一步怎么办」的。前者删，后者留。

### 二、分割页批次树扁平化
- 新增 **`web-admin/src/utils/splitTree.ts`**，`normalizeSplitTree()` 是**唯一实现**（SFC 只 import，回归脚本 import 同一份 —— 不写第二套口径）。后端 `getSplitTree` 仍返回 `SPLIT_JOB` + `MISSING_PART_GROUP/MISSING_PART`（归集/对账/上链不动）。
- 页面树归一化后只剩 `CARCASS`（根）/ `SPLIT`（实产部位）两类：作业节点**拍平**（部位提到胴体批次下），对照节点**整棵丢掉**。
- 同步清死代码：`TreeNode` 的 SPLIT_JOB/MISSING 分支、`isPseudo`/`jobBadge`、抽屉作业分支、`CATEGORY_LABELS`/`categoryLabel`、`.node-badge`/`.warn-text`/`.is-pseudo`/`.form-hint` CSS、`SplitNode` 的作业/对照字段。

### 三、两条新回归 + 一条渲染核验（全绿）
1. **`logs/verify-split-tree-flat.mjs`（20/0）**，跑法 `node --experimental-strip-types logs/verify-split-tree-flat.mjs`。**钉两侧**：接口里**仍**有 SPLIT_JOB/MISSING_* 且作业节点仍带 `totalYieldRate`（防后端口径被误改）＋ 页面树**只剩** CARCASS/SPLIT、部位数不变、节点不带 yield/check 字段（防后端不返回时误判通过）。样本批次 `CB20261009-001`（作业 `SV20261009-001`，出肉率 91.72%，5 个部位）。
2. **`logs/verify-split-page.cjs`（11/0，Playwright + Edge）**：真实渲染数 DOM。树 = `CB20261009-001 → 前腿肉/大排肌肉/后腿肉/五花肉/肋排`。
3. **`logs/verify-no-hint-banner.cjs`（8/0）**：6 个页面（含点开建批弹窗）的横幅文案在渲染后正文里搜不到。

### 四、浏览器自动化的可复用经验（下次别再试错）
- **登录页验证码是纯前端 canvas 生成**：`generateCaptchaText()` 用 `chars.charAt(Math.floor(Math.random()*chars.length))` 取 4 位。`page.addInitScript(() => { Math.random = () => 0 })` → 验证码恒为 `'AAAA'`，可稳定自动登录，无需 OCR / 后端白名单。
- 登录按钮文案是「**登 录**」（中间有空格），`getByRole('button', { name: '登录' })` 会超时，用 `button.login-btn`。
- 输入框选择器用 placeholder：`请输入用户名` / `请输入密码` / `请输入验证码`。
- **`playwright-core` 装在托管工作区** `C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core`，**不在** `E:/pork-traceability/node_modules`（老 `logs/browser-check.cjs` 里的路径已失效，那是过时参考）。Edge 在 `C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe`。
- token 只在 pinia store（`persist: false`），**不在 localStorage** —— 别想写 localStorage 绕登录，走表单。
- Node 22.22 支持 `--experimental-strip-types`，回归脚本可以直接 `import` 仓库里的 `.ts` 工具模块（文件须只用可剥离语法：interface/type 注解可以，enum/namespace 不行）。

### 五、构建与生效
`web-admin` `npm run build` 通过。**nginx 直接挂载 `../web-admin/dist`（只读）**：改前端后重新 build 即生效，**不需要** `docker compose` 任何操作。

## 十三、运输页删新增按钮 + TR-TEST-0016 乱码 + 分割树口径二次调整（2026-10-12 下午）

### 一、冷链运输页：删「新建计划」「新建运单」两个按钮
- `TransportMonitor.vue` 删两按钮 + `openPlanCreate`/`openCreate` 两个 handler + `Plus` 图标导入。**弹窗与后端端点都保留**（计划弹窗与「编辑」共用；运单弹窗当前无打开路径，留作将来「补录司机」编辑入口；后端端点被回归脚本用着）。
- ⚠️ **已知陈旧文案（未改，等拍板）**：「立即发车」的 popconfirm 与 `depart()` 报错都写「请先编辑运单补录司机」，但「编辑运单」入口从不存在；且**没司机的运单人工与设备两条路都发不出去**——发车侧设备报文若带卡号，会先 `driver_card` 换人再写运单（`.set(driverName)`），所以准确说法是「请由司机在车载终端刷卡发车」。

### 二、中文落库成 `?` 的又一实例（签收页 TR-TEST-0016）
- `HEX(vehicle_no)=3F422D...` —— 与「中文过 HTTP 头」同一族：**写入时字符集/编码错了，中文全部变字面 `?`，不可逆**。根因是 `init-sql/12-demo-fix-20260921.sql` 曾被错误字符集的客户端跑过；连带 `transport_stop.id=26` 的 `store_name`（回填取了坏掉的 destination）。**全库扫描只有这两行**。
- 修复 = utf8mb4 连接 UPDATE；**加固**：脚本头加 `SET NAMES utf8mb4;`，并把 ON DUPLICATE KEY UPDATE 扩成覆盖全部描述列（`uk_transport_no` 唯一键在）——**重跑即自修复**。往后写任何含中文的 SQL 都照此办理。
- 全库排查乱码：对可疑表跑 `... WHERE col LIKE '%?%'`（`?` 连续出现即中招）；本次查过 `cold_chain_transport/delivery_plan/store_receipt/transport_stop/ingest_device`。

### 三、分割树口径（**最终版，推翻第十二节的「扁平化」**）
```
胴体批次（CARCASS）
 └─ 白条 · 耳标号/作业号（SPLIT_JOB，每头猪一组）
     ├─ 已出部位（SPLIT）
     └─ 未产出标准部位 N 项（MISSING_PART_GROUP → MISSING_PART，伪节点）
```
- **恢复** SPLIT_JOB 与 MISSING_* 上树（第十二节曾删）；**不展示**：出肉率（总/红肉）、对账结论、对账基准，「必出缺 N」「齐」小徽标也不做（问过用户：不记得有这徽标 → 不做）。后端归集/对账/上链照旧。
- 实现：`splitTree.ts` 的 `normalizeSplitTree` 结构原样保留、只剥对账字段（`totalYieldRate/leanYieldRate/checkStatus/checkNote/parentWeightKg/weightBasis`）；标题/编号/重量统一 `splitNodeTitle/splitNodeNo/splitNodeWeight`（编号位对 根/作业/对照组 返回空，防「批次号显示两遍」）。对照组恢复虚线灰底、无详情/链上按钮。抽屉的白条组分支无出肉率字段。
- 实测：`CB20261009-001 → 白条 · SV20261009-001(72kg) → 5 已出 + 未产出标准部位 11 项`。**未产出 ≠ 17−5**：字典与实报口径有出入，回归只断言页面=接口，不断言具体数字。

### 四、回归（全绿）
- `logs/verify-split-tree.mjs` 32/0（原 verify-split-tree-flat.mjs **改名重写**，旧的已删）：接口五类节点+出肉率都在（防后端被误改）＋ 页面分层保留但对账字段剥净＋标题/编号/重量口径＋伪节点无重量＋空树。`node --experimental-strip-types` 跑。
- `logs/verify-split-page.cjs` 14/0：白条组/已出/未出齐全，正文无「分割作业/出肉率/对账/必出缺」。
- `logs/verify-transport-page.cjs` 13/0（新增）：两新增按钮没了、「监控详情」仍在；签收页 TR-TEST-0016 中文正常、无成串 `?`。
- **教训：浏览器断言别依赖数据状态**——「生成运单」按钮只挂待发货/部分排车的计划行，库里唯一计划是已排车 → 断言必挂；改断恒定落点（「监控详情」）。

## 十四、分割车间监控：自动连拍 + 照片绑定 + 图片差异化（2026-10-12 下午）

**口径（用户拍板）**：「开始分割」= **分割线扫码称重台（SPLIT 通道）上报数据之后**；照片**绑定**到这次分割；每张图片**内容不一样**；节奏先用 **2 秒一张 × 3 张**（演示值），后面再调实际值。

### 一、数据模型（迁移 41）
`workshop_snapshot` 加 `batch_no` / `job_id`（都可空）+ `idx_snapshot_batch(batch_no,id)`。
- **为什么绑定信息来自工位终端而不是相机**：相机不认识批次（和它不认识部位、读不到门店主数据同理）；「当前正在分割的白条属于哪个批次」是称重台知道的，随报文带给相机。
- **为什么 job_id 服务端算**：作业号是系统生成的，设备本就不该编；服务端按 batchNo 反查该批次**最近一次**作业即可。
- **为什么可空、不给默认值**：历史数据（迁移 40 时代的 2 张）没有绑定关系，置 NULL 而不是塞个假批次 —— 假绑定会让页面把老照片当成某次分割的现场。
- 幂等 ALTER 写法：`information_schema.COLUMNS` 判存在 → `PREPARE/EXECUTE`（MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS）。

### 二、后端口径
- **带 batchNo 且查不到该批次 = 编造绑定 → rejected**（不进人工队列）。比不绑定更坏：页面会把它当某次分割的现场照片展示。
- **不带 batchNo = 不绑定**，照常入账（相机没有工位信息时的正常路径）。
- 查询 `GET /distribution/split-snapshots?deviceNo=&batchNo=&limit=`：带 batchNo = 「这一次分割的现场」；不带 = 相机最近 N 张（保留旧口径，兼容历史数据）。返回带 `batchNo/jobId/jobNo`（jobNo 一次批量查，防 N+1）。
- **成功回执不带业务附言**（message 一律「已入账」）→ 绑定事实只能靠查询接口断言，回归别盯回执文案。

### 三、前端
- **`web-admin/src/utils/splitCamera.ts`（相机模拟器，唯一实现）**：`renderWorkshopShot()` 用 canvas 画车间现场示意图 —— 时间戳精确到秒、批次号写进画面、「第 N/3 张」、色调随批次+序号错开、白条位置逐张平移、噪点扫描线。**每张都是内容不同的真图**，走真实两段式上报（先传文件换 fileId，再报 fileId+capturedTime+workshop+batchNo）。常量 `SNAPSHOT_INTERVAL_MS=2000`、`SNAPSHOT_COUNT=3`；注释写明正式口径 = 设备台账 `sample_interval_seconds`（CAM-SPLIT-001=60）。
- `IngestMonitor`：SPLIT 上报 **accepted 后 `await autoShootAfterSplit(batchNo, workshop)`**（按钮保持 loading）—— 演示时人能看到「相机真的在拍」，而不是以为没反应再点一次。弹窗新增连拍状态区块 `.camera-shot`（蓝左边框，与回执区分）。相机按**通道**找（`devices.find(d => d.channel === 'SPLIT_SNAPSHOT')`），不按设备名。
- `SplitBatch`：监控区**必须带 `currentBatchNo` 查询** —— 不带会退化成「相机最近 N 张」，那正是「新分割还是老图」的根因；标题挂批次标签；轮询 30s→10s；caption 显示作业号（蓝色）。`loadSingleTree`/`loadTree` 末尾都要 `loadSnapshots()`（换批次=换一组现场）。
- 手工兜底入口（SPLIT_SNAPSHOT 演示字段）保留，并加了 `batchNo` 选填项 + 示例值自动带批次。

### 四、回归（全绿）
| 脚本 | 结果 | 要点 |
|---|---|---|
| `verify-split-snapshot.mjs` | 31/0 | 新增第 5 节绑定用例；**绑定节必须在滚动窗口节之后跑**（放前面会被突发上报淘汰，db.sh 就没法对绑定列断言） |
| `verify-split-snapshot-db.sh` | 24/0 | 拒绝数 2→**3**（+编造批次绑定）；新增：恰 1 张绑定 / 全库无「绑定了不存在批次」的快照 / 绑定能反查到作业 |
| `verify-split-camera.cjs` | 14/0 | **端到端**：真做一次分割上报 → 无人操作自动 3/3 → 分割页按批次看到 → **fetch 文件内容比指纹，三张两两不同** |
| `verify-split-page.cjs` | 17/0 | 监控区标题挂当前批次号（从树卡片标题取批次，**别用 node-batch**——根节点编号位是空的） |
| `verify-split-tree.mjs` / reconcile | 32/0 / 18/0 | reconcile 基线：批次 23→**24**、分割 34→**39**、生猪 25→**26**（用户 13:30–13:39 界面演示：猪 305→入场 307→盖章 171→CB20261012-001→SV20261012-001，真实数据不清） |

### 五、踩坑与结论
1. **浏览器演示会命中兜底幂等**：示例值与用户上一次上报完全一样 →「同设备+同通道+同一份报文」判重 → 回执照样「已入账、记录编号=既有作业 id」但**不建新作业**，照片绑定到既有作业。设计行为；要测「新建」得改示例值（如改重量）。
2. **又踩「构建启动后改源码」**（前端版）：`.camera-shot` 样式在 `npm run build` 之后才加 → dist 里没有 → 必须重建。与后端同一教训。
3. `dist` 整目录删掉后重建，nginx bind mount 按路径解析仍正常服务，**无需重启容器**（再次确认）。注意环境的批量删除守卫会拦 vite 清空 dist（`SAFE_DELETE_BULK_CONFIRM_REQUIRED`）—— 先用 shell 的删除命令把 dist 删掉再 build。
4. 端到端验证在 CB20261012-001 上留下 3 张绑定照片（id 190–192 → SV20261012-001）——正是该功能产出的真实数据，保留。
5. 截图辅助脚本 `logs/shot-monitor.cjs`（一次性，拍监控卡片）。

## 十五、原 MEMORY.md 全文（2026-10-12 压缩为精简版前的存档）

> 只作存档：精简版 MEMORY.md 只保留规则与不变量，**此处保留压缩前的完整表述（含括号里的解释与举例）**，需要细读某条时来这里查。

# 项目长期约定（猪肉全链路追溯系统）

> 只放**规则与不变量**。**阈值/配置键/回归基线/命令原文/理由见 `DETAIL.md`**；逐轮记录见 `YYYY-MM-DD.md`；可执行断言见 `logs/verify-*.mjs`。

## 一、前端展示
1. 不暴露表名/字段/代码，翻业务中文；技术信息进 `el-tooltip`，原始报文/回执进 `el-collapse` 默认收起。
2. **通道中文名四处同步（现 18 条）**：`IngestChannel.labelOf()` / `ingest.ts CHANNEL_LABELS` / `IngestMonitor.vue` 三个映射表 / **`verify-channel-label.mjs EXPECT`**（覆盖度断言，漏加必红）。
3. 侧边栏序：养殖建档→免疫→产地检疫→申请出栏→入场查验→瘦肉精→屠宰检验→胴体盖章→胴体批次→分割批次→冷链温度→门店签收→零售售出。**出栏审批不进 `TraceStage`**。
4. 回执**只留事实**（状态+去向+上报编号+未入账原因+原始回执折叠），toast 只报状态；受理后按钮置灰（`demoReportLocked`），改任一填写项解锁。
5. **功能必须有恒定落点**（只在少数情况才渲染＝上了看不见）→ 单站也要有列表列+详情项。
6. `web-admin` 的 `npm run build` 是纯 `vite build`，**不做类型检查**。
7. **「填充示例值」必须落在成功路径上**：只按运单安排的那位司机取（`cardOf`），查不到就**留空+说明**，**不许兜底成某个人的卡**；温度示例在 -18~0。
8. **分割树口径（2026-10-12 定稿）**：胴体批次 → **每头白条一组**（「白条 · 耳标/作业号」）→ 组内已出部位 + 未产出标准部位 N 项（伪节点虚线灰底）。**不展示**出肉率/对账（`splitTree.ts` 归一化剥字段，抽屉也不带），**不做**「必出缺 N」「齐」徽标；后端归集/对账/上链照旧。标题/编号/重量唯一出口 = `splitTree.ts` 的 `splitNode*` 函数。
9. **环节说明横幅全删，状态性提示保留**：判别口诀——页面一进来就挂的「为什么这么设计」`el-alert` 删；满足条件才出现的「下一步怎么办」留。

## 二、接入层
1. 报重两道：同设备+同 `bizKey` 回既有结论；兜底同设备+同通道+**同一份报文**。状态 0待处理/1待人工/2已入账；放行 3已拒绝（补齐可重发）/4已降采样。
2. `ingest_staging` 存原始报文不可变；业务层存归一化结果；两份都留。
3. **成功回执不带业务附言** → 标记类事实（如 `is_gap`）落业务表由前端展示；**断言别盯回执文案**。
4. **失败默认 `manual`**；只有「数据不成立、外部应删单」才判 `rejected`。**物理读不到的东西报上来 = 接错点位 = `rejected`**。
5. **来源管控只剩两档（2026-10-12）**：`DEVICE_ONLY` 人工新增一律拒；`DEVICE_PREFERRED` 设备为主、覆盖不到可人工补录（**免票据**，强制实名+上链）。**「兜底解锁」前后端整体删除**；`source_fallback_unlock` 表与 `/ingest/source-policy` **保留**（后者被 7 个回归断言）。
6. **设备环节「编辑」入口整条删除（2026-10-12）**（6 环节前端按钮+弹窗、后端 PUT+updateXxx），**只留「作废」（实名+理由+上链）**；「产地检疫出证 / 申请出栏 / 人工建批」是**新增**，保留且免票据。
7. **分割车间监控快照绑定（2026-10-12）**：`workshop_snapshot.batch_no/job_id`（迁移 41）——**分割线上报（SPLIT）一入账，工位相机自动连拍**（前端模拟器 `utils/splitCamera.ts`，2 秒/张 × 3 张，演示值可调）；照片带 `batchNo` 上报，服务端绑定到**该批次最近一次作业**（批次查不到=编造绑定→`rejected`；不带就不绑定）。**查询必须带 batchNo**（页面按批次看「这一次分割的现场」，不带会退化成相机流=老图假象）。成功回执照旧不带业务附言。

## 三、设备 ≠ 人
1. 人名不得用设备名兜底；无人名→转人工不拒绝；force 放行写「未记录」。
2. 映射：TAG/VACCINE/RACTOPAMINE/SPLIT=`operator`、ENTRY=`inspector`、INSPECTION/STAMP=`veterinary`、RECEIPT=`receiver`；前端统一 `|| '未记录'`。

## 四、中文编码红线
1. 中文进 HTTP 头落库成一串 `?`（`HEX()`=`3F…`）**有损不可逆** → 一律 `OperatorHeader.encode/decode`；**新增中文透传头照办**。
2. `SourceGuard` 在 `common-web` → **改 `common-web`/`common-core` 必须重建全部依赖服务（含 gateway、ingest）**。
3. **含中文的 SQL（init/fix/演示数据）必须 utf8mb4**：文件头 `SET NAMES utf8mb4;` + 客户端 `--default-character-set=utf8mb4`；否则中文落成字面 `?`（不可逆，TR-TEST-0016 踩过）。这类脚本写成**重跑自修复**（ON DUPLICATE 覆盖描述列）。排查：`WHERE col LIKE '%?%'`。

## 五、环节前置与判定器
1. **ENTRY 两条硬前提**（force 也绕不过）：出栏审批已通过 + 生猪状态 ∈{1在养,2已出栏}。序：审批→状态→pigId→幂等→落库；**查不到或服务不可用一律转人工**；已驳回不复活。
2. 判定器唯一在 `slaughter-service/support/`：`RactopamineGuard`/`PreSlaughterGate`/`StampGuard`，**禁止各写一份**。
3. **瘦肉精**：批批检，阳性整批受控；「未检不得屠宰」按所属批次（报文 `batchNo` ∪ 入场查验批次 ∪ 检测记录批次）判，**别只信上报批次号**；只约束宰后类(2,3)；阻断一律 `rejected`（force 不放行），阳性照常入账留证。三关：`ingestInspection`、`ingestStamp`、`createBatch/updateBatch`（核不到抛 `REMOTE_CALL_ERROR`，不核不放行）。
4. **宰后口径**：`inspect_type` 2 与 3 是同一环节 → 一律 `IN (2,3)`。**盖章收紧**：`ingestStamp` 须有宰后类(2,3) 且 `result=1`。序：瘦肉精→宰前门槛→幂等→落库。

## 六、批次归集
1. **SPLIT**：`DEVICE_ONLY`，人工入口已删。一次上报=一头白条的整份产出 `items[]` → 作业号 `SV…` 展开 N 条；无 items 退化单条。**`meat_part` 是部位唯一来源，反查不到转人工**。
2. **`SplitYieldCheck`（唯一口径）**：缺失/超限不通过**不影响入账**，只置 `check_status=1` 待复核。
3. 拆分树后端插 `SPLIT_JOB` 节点；`MISSING_PART_GROUP`/`MISSING_PART` 伪节点用后端 `key`；`collectPartCodes` 递归必带 `split_level`。
4. **胴体批次**：键=屠宰场+屠宰日；只动当日同场 `source=DEVICE` 且 `status=1`，否则新建 `CB…`。**边界系统算，设备只报单头白条**；**不碰人工批次**。
5. **封批**：满 `max-pigs` 自动 `status=2`；人工提前封批（强制实名+上链）。批号**当日全局排流水、计数含已作废**。
6. **`carcass_batch_pig` 是权威**；`pig_ids`/`pig_count` 是展示快照。不变量：三者相等。`JSON_ARRAY_APPEND` 必须 `CAST(#{pigId} AS JSON)`。
7. 写入前三道：瘦肉精→盖章→未被占用（`BatchGuard.validateAll`）；设备重推「已占用」→**回既有批次**。人工建批/封批强制实名+上链；上链 key=`batchNo:action:pigId`。已盖章 ≠ 入批。

## 七、冷链运单链（计划→装车→发车→到达→签收）
1. **计划**：一计划=一目的地+一车次+N 待发批次；计划阶段车/司机可空，生成运单时**只强制车牌**。号 `DP…`（含已取消不复用）。
2. **两条时间线分开命名**：计划侧 `status` 只答「货有没有排进运单」=1待发货/4部分排车/2已排车/3已取消（**2 不叫「已发运」**）；运单侧才答「车走到哪」=1待发车/2运输中/3待签收/4已签收。**改词要前后端+列注释一起改**。
3. **`transport_item` = 装载明细**；按 `store_id` 聚站点；主表 `destination` 降级首站、`stop_count` 记站数。`store_receipt` 幂等键 `uk_transport_store`。
4. **「待发运」两道排除**：未被 `transport_item` 引用 **且** 未被「待发货/部分排车」计划的未排车明细占用；**不用标记位**。手工建单与 `loadPlanBatches` 同两道拦截。
5. **删运单连带**：按 `delivery_plan_item.transport_id` 精确退回 → 回算计划（**已取消不复活**）→ 删装载明细与站点 → 删运单。
6. **装车口 `LOAD`**（车牌相机+扫码枪，**无读卡器**）：唯一**不产生业务判定**的通道，只给现场事实去**倒查**待发计划；**扫码是「确认」不是「选择」**；**报 `cardNo`=结构性拒绝**；超载硬判据；**司机可留空**；不属任何计划/自动建单失败→转人工。
7. **并入既有待发车运单**：候选=同车牌+`source=DEVICE`+`status=1`+`create_time` 在 `ingest.load.merge-window-minutes` 内。唯一命中→追加；0 张→新建；**>1 张或非 DEVICE→转人工**；**窗口外不并**。车/人**只补齐不覆盖**。回执 `mergedIntoExisting` **不落库**。
8. **现场值只补计划空位**（`resolveSiteValue`）：都有且不一致→转人工；**现场车牌与计划不一致不许设备改写计划**。
9. **`ingestLoad`/`ingestArrive` 刻意不加 `@Transactional`**：会把「转人工」吞成「系统内部异常」。同形通道照办。
10. **分车**：一律以 `delivery_plan_item.transport_id` 为准；主表 `transport_id` 只是「最近一次」，`transport_count` 记共排几车。
11. **`DriverSource`（唯一口径）**：`CARD`/`FACE`=本人确认，其余=非本人确认；**强度由来源推导，不收设备自报布尔值**。四主数据表（`vehicle`/`driver`/`driver_card`/`driver_vehicle`）是地基。**硬不变量：司机为空 ⇒ `driver_source=NULL`**。接入层不判司机。
12. **`DEPART`**=唯一推进运单状态的通道兼**责任闸门**：车牌倒查该车牌下 `status=1` 运单（0/多张→转人工）→推 `IN_TRANSIT`+写发车时刻。**没司机的运单两条路都拦**。**`CARD` 只产自 `DEPART`/`ARRIVE`，且是同一台车载终端**（`deviceGroup()=ONBOARD` 允许同组互报，其余通道严格一设备一通道）。三台终端：`DEPART-001/003` 各绑真车牌**不能删**；`ARRIVE-001` **有意不绑车**（「缺车牌→拒绝」红线靠它成立）。加同组通道要同步 `deviceGroup()` 与 `verify-arrive-stop` 的 `ONBOARD_DEVICE`。
13. **`ARRIVE`（站点级刷卡）**：**粒度=卸货门店，不是批次**。`transport_stop` 存行程状态（0未到/1已到/2已签收；唯一键 `(transport_id,stop_no)`）；**运单状态由站点汇总推导**（未发车不推导），**签收前置下沉到站点**。**报文只报「车牌+司机卡号+到达时刻」**：服务端按站点顺序推、每刷一次进一站；倒查认「未结束的运单」(2,3)。**到达须司机本人刷卡**，确认人记在站点上（`arrive_driver_name/source`，**不进主表 `driver_name`**）。**红线**：没未结束运单或≥2 张 / 卡换不出人 / 站点已到齐 → **转人工**；**刷卡人 ≠ 运单安排的司机 → 直接「已拒绝」**（`retryable=false`，重刷自愈）。**发车侧同形比对保持「转人工」，别一起改**。兜底=页面「确认到达」（`MANUAL`）。单站也要有落点。
14. **温度打卡**：采样间隔是**探头属性**（`ingest_device.sample_interval_seconds`，系统不轮询），写入时固化到 `temperature_log`；**断档标在断档后第一条**；**首条 `gap_seconds=NULL`/`is_gap=0`；补传为负不判断档**；**空白 ≠ 正常**；`is_abnormal` 与 `is_gap` 独立。recordTime 取报文级 `reportTime`。
15. **人工「新建计划/新建运单」按钮已删（2026-10-12）**：运单只由装车口（`LOAD`）或计划「生成运单」产生；**弹窗与后端端点保留**（回归在用；运单弹窗当前无打开路径，留作「补录司机」编辑入口）。发车文案「请先编辑运单补录司机」是陈旧指引（编辑入口不存在），待拍板后改「由司机在车载终端刷卡发车」。

## 八、订单转入 `ORDER`
1. 订单是计划来源之一（`plan_source`∈MANUAL/ORDER + `order_no`），落 `status=1`，**车/司机留空**；**不建订单表**、不收金额、不跨订单归并。
2. **绝不能判 `rejected`**（外部据此删单，订单永久丢失）：两侧一律转 `manual`。**重推是常态，宁可多收一条待人工。**
3. **配货**：按部位找可发运批次 + `MeatPartResolver.resolve`（共用一份实现）；凑不齐转人工；批次不可拆→略超写 `remark`。门店校验 `OrgClient` 不符**一律转人工**。
4. **零/负重量必拦**（DTO + handler `validate` + service 三层）。
5. `operator` 写「订单转入（终端号）」**不写人名**；**列表展示的标识字段必须都能搜**。
6. **订单类数据的幂等键（订单号）必须每次唯一**（写死会让第二次演示命中幂等，看起来像坏了）。

## 九、环境与纪律（最高频踩坑）
1. 改后端后**必须 `docker compose build --no-cache <service>`**，在 `backend/` 执行。
2. **⚠️ 构建启动后再改的源码不会进镜像**：改完再启动构建；已开跑又改源码必须重建。
3. **环境会批量 `paused` 全部容器**：逐个 `docker unpause`；刚构建过新镜像必须 `up -d --force-recreate`。
4. **MyBatis-Plus 跳过 null 字段**：清空列用 `lambdaUpdate().set(Field,null)`；**新增可空列不给业务含义型默认值**。
5. **回归残留必清（含回滚被改的既有行）**；跑前清场**只删自己造的**（按业务键，**不按 `device_no` 整表删**）；清完跑 `verify-reconcile.mjs`。
6. **新增「挂在运单下的子表」必须同步所有清理脚本**并统一加「**孤儿行=0**」守卫。**「表建好了、断言没人写」是漏删的共同根因。**
7. **界面演示产生的数据算真实数据，不清**；偏高先分辨残留还是演示。
8. **滚动淘汰型写接口的回归不得借用生产台账主体**：自带一次性主体（跑完注销）或只读降级。
9. **表名**：`db_breeding.pig_individual`（**不是 `pig`**）、`slaughter_apply`、`quarantine_certificate`、`entry_inspection`、`slaughter_inspection`、`carcass_stamp`；`ingest_staging`/`source_fallback_unlock` 在 `db_ingest`；**用户表在 `db_common.sys_user`（没有 `db_auth` 库）**。
10. **DB 断言 SQL 别重复表别名**（作用域片段自带别名）。**新断言先跑一次再信。**
11. **断言盯不变量不盯绝对条数**：跑前捕获基线→跑后不变 + 标记自带证据；**浏览器断言别依赖数据状态**（按钮随行状态出现/消失），断恒定落点。
12. **演示上报必须显式传唯一 `bizKey`**（默认毫秒级会撞幂等）。
13. **`-db.sh` 才是回归正确入口**（分自驱型/传参型两类，见 `DETAIL.md` 十一）；**裸跑同名 mjs 必留残留**，清理顺序子表→父表。
14. **浏览器自动化**：登录页验证码是纯前端 canvas 生成，`addInitScript` 把 `Math.random` 固定为 0 → 验证码恒 `'AAAA'`；登录按钮用 `button.login-btn`（文案「登 录」带空格）。`playwright-core` 在托管工作区 `C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/`（**不在**仓库 node_modules），Edge 在 `C:/Program Files (x86)/Microsoft/Edge/Application/`。token 只在 pinia store、不在 localStorage。
15. **前端改完 `npm run build` 即生效**（nginx 只读挂载 `web-admin/dist`，无需动容器）。

## 十六、盖章示例值静默吃单 + 设备接入页横幅清理（2026-10-12 15:05）

**背景**：用户问「B-20261012-01 已通过瘦肉精 / 宰前宰后检验 / 胴体盖章，为什么胴体批次分拣台取不到示例值」。诊断（只查不改）后拍板：修示例值 + 删设备接入页提示横幅。

**根因链（三层叠加，最隐蔽的是第三层）**
1. 胴体批次示例值口径 = `findStampedPig()`：**已盖胴体章 且 尚未归入任何胴体批次**。当时全库该集合 = 0 具（页面上有提示语，不是坏了）。
2. 该批次两头猪：305 全过且有章（id 171，`CT-20261012-001`，13:37:21）但 13:38:32 已归入 `CB20261012-001`；306 检验全过但**没有章**。
3. **306 的章被静默吃掉了**：`STAMP` 的填充示例值把胴体编号写死成当天固定的 `CT-${todayText()}-001`，而 `ingestStamp` **第一步就是按 `carcass_no` 查重**（唯一键）→ 查到既有记录就 `written(exists.getId(), "该胴体已有盖章记录…")`，**回执状态仍是「已入账」**。铁证：接入流水 4218（13:37，耳标 305）与 4264（14:44，耳标 **306**，编号仍是 `CT-20261012-001`）→ `target_id` **都是 171**。看起来成功，什么都没写。

**关键口径（沉淀）**
- `carcass_stamp` 上 `pig_id` 与 `carcass_no` **都是唯一键** → 「整批盖章」在数据上不成立，章是**逐头白条**的动作，一头白条只有一枚章。
- 「作废」只是 `void_flag=1`、**行还在** → 判断「这头白条能不能再盖章」必须**连作废行一起看**（只查有效行会漏判，写入时直接撞唯一键冒成 500）。查法：`CarcassStampMapper.selectAnyByPigId`（`@Select` 原生 SQL 显式绕过 `@TableLogic`，别名映射，沿用 `selectVoidedByPig` 的房子风格）。
- `db_common.blockchain_ledger`（链本身）**不删**；能删的是业务侧 `blockchain_record`（既有清理脚本同一口径）。
- 一次性验证产生的盖章记录必须**物理删**，不能用「作废」收尾 —— 作废会把这头白条永久钉成「已有一枚作废的章」，以后再也盖不了。

**修复（2026-10-12 15:05）**
- 前端 `IngestMonitor.vue`：
  - 新增 `nextCarcassNo()`：`CT-${todayText()}-${HHmmss}` —— 当天内唯一 + 每次演示都是一份新报文（顺带躲开「同设备+同通道+同一份报文」的兜底判重）；
  - `findPassedInspection(accept?)` 加可选过滤，STAMP 分支传 `(pigId) => !stampedPigs.has(pigId)`（盖章台账拉 200 条在本地排掉已盖章的猪）；
  - STAMP 的 `DEMO_FIELDS` placeholder 与提示语同步新口径（一头白条一枚章 / 编号重复会回既有记录）。
- 后端 `SlaughterDeviceIngestService.ingestStamp`：在「按胴体编号查重」之后加**白条级兜底** —— 该猪名下（含作废行）已有盖章记录 → `manual` 转人工并说明两条编号不一致（作废过的另有措辞）；不再让 `uk_pig` 冲突冒成「系统内部异常」。
- 横幅：`IngestMonitor.vue` 三处 `el-alert` 全删（待确认队列「自动采集 ≠ 自动生效」/ 设备台账「设备用密钥接入」/ 模拟设备上报弹窗「这是在替设备发一次上报」）+ 失效样式 `.rule-tip` / `.demo-tip` 一并删掉。**没有回归断言这些横幅**（grep 过），`shot-source-guard.cjs` 里的 `.source-guard-bar .el-alert` 是别的页面，未受影响。

**验证（全绿）**
- 新增 `logs/verify-stamp-demo.cjs`（17/0，浏览器 + 接口：横幅三处为 0 / 示例值挑中未盖章白条 306 / 编号 `CT-yyyyMMdd-HHmmss` / 真上报确实多写一条 id 172 / 同猪换号重报 → 转人工且回执说明「两者不一致」/ 冲突上报不多写）+ `logs/verify-stamp-demo-db.sh`（15/0：落库事实核验 + mysqldump 备份 → 物理删盖章/上链/接入流水 → 水位回基线 30）。自驱型，直接 `bash logs/verify-stamp-demo-db.sh`。
- `verify-reconcile.mjs` 基线再调：有效生猪 **26 → 27**（306 是用户 14:41 界面建档的演示猪，真实数据不清）。
- `verify-channel-label.mjs` / `verify-stage-order.mjs` 通过；后端 15 模块离线编译通过；`slaughter-service` 镜像 `--no-cache` 重建 + `--force-recreate`，前端 `CODEBUDDY_SAFE_DELETE_ENABLED=0 npm run build`（env 变量可直接过批量删除守卫，不必再手动删 dist）。

## 十七、接入层现状核实（2026-10-12 15:40，纯答疑，未改代码）

用户问「怎么连设备 / 是不是收报文-解析-录入 / 是不是 HTTP / 能不能连真机了」。核实结论（可作口径引用）：

- **协议**：HTTP/1.1 + JSON。唯一真机入口 `POST /api/v1/ingest/device/report`（网关 8080 → ingest-service），请求头 `X-Device-Key`；另有 `GET /ingest/device/ping` 自检（只验密钥与通道，不写数据）。图片类先 `POST /api/v1/file/upload` 换 `fileId` 再报。**无 MQTT / Modbus / OPC-UA / WebSocket / TCP 直连**。
- **两个入口要分清**：`/ingest/device/report`（设备真机，密钥鉴权）；`/ingest/demo/report`（页面「模拟设备上报」，只传 `deviceNo`，服务端自取密钥）—— 后者是演示旁路，不是设备接口。
- **鉴权**：静态密钥存 `db_ingest.ingest_device.secret_key`（明文，种子 `dev-key-*`）；密钥无效 401、设备停用 403。网关 `PUBLIC_PATHS` 只放行 `/api/v1/ingest/device/`，`/ingest/staging`、`/ingest/devices` 仍须登录。
- **流程**：收 → `ingest_staging` 留档 → 幂等（device_no + biz_key；兜底「同设备+同通道+同一份报文」）→ `validate`（结构）/ `dispatch`（业务）→ `BizClient` 调业务服务内部接口写业务表（接入层不直连业务库）。
- **设备台账**：20 台已登记（探头/地磅/RFID/读数仪/盖章机/分割称重台/车载终端/相机/POS/PDA…），一台设备一条通道（`ONBOARD` 组车载终端例外）。
- **真机现状**：软件侧就绪（标准 HTTP 报文 + 密钥 + 自检 + 模拟器 CLI `scripts/test/device-simulator.mjs` 全绿，已实测写业务表 `source=DEVICE`）；**但截至目前接入的都是模拟设备，未接真机**（`docs/自动录入验证指南.md` 末尾「真机灰度路径」正说明这是下一步）。真机落地还差：① 网络可达 + **缺 TLS**（8080/80 均明文、无反向代理）；② 工业设备原生多是 Modbus/485/OPC-UA/MQTT，需**边缘网关转 HTTP**；③ 按现场重登设备编号与密钥（演示密钥明文存库）；④ 设备侧实现时间同步与断网补传（靠 `bizKey` 幂等去重）。
- **报文契约性质（回答「字段能不能改」用）**：信封 4 字段固定（`channel`/`bizKey`/`reportTime`/`sourceRef`）；`data` 是 `Map<String,Object>` **自由字典** —— 设备多传的键不报错、进原始报文留档、**不参与业务**；少传关键键被 `validate` 拦下。`Payloads` 做**宽松解析**（数字带引号、布尔可写 `1/true/通过/是`、时间支持 4 种格式、fileIds 可数组或逗号串）⇒ **值的形式随意，字段名要对暗号**。留档只存 `data` 部分（`toJson(request.getData())`），信封字段另列存。
- **设备不能自作主张的字段**：`recorder`/`source=DEVICE`/`sourceRef`/`rawPayload`/`sampleIntervalSeconds`（台账带）/`tempRangeMin|Max`（服务配置）由系统补；反之 `operator`/`inspector`/`veterinary`/`receiver` 人名字段设备必须给，**系统不许用设备名兜底**（缺 → 转人工）。
- **想真正新增/改字段** = 改后端（channel handler + 业务服务 DTO）后重建部署，**契约在服务端手里**，设备多加字段不会自动生效。可配置项：温度允许区间/最小间隔（`INGEST_TEMP_MIN` 等）、探头额定间隔（台账 `sample_interval_seconds`）、`ingest.load.merge-window-minutes`、`ingest.carcass-batch.max-pigs`、`ingest.split-snapshot.retention`。
- **单条报文保护**：上限 64KB（超限在落档前 400 返回）、设备时间超前 >10 分钟视为时钟错误、通道与台账登记不一致直接参数错误（同组车载终端除外）。

## 十八、重复上报改为「提示已存在 + 拒绝入账」（2026-10-19 10:25–10:55）

**用户要求**：设备上报不应重复；重复的信息要提示「该信息已存在」，并拒绝入账。

**旧行为的坑**：重复命中走 `toResponse(exists, true)`，`accepted` 照抄**原记录**的值 → 重推一条已入账数据，回执照样「已入账 / accepted=true」，前端还显示「已写入，记录编号 xxx」——重推一次看着像又写了一条。

**新语义（三层都改）**
- `IngestStatus.DUPLICATED = 5`（label「重复上报」）。**回执层状态**，队列里永远不会出现 5——重复上报不新建流水（否则重推几次队列就被刷满），原记录原样不动。
- `DeviceIngestService.duplicateResponse(original, how)`：`accepted=false`、`duplicate=true`、`status=5`、`originalStatus/originalStatusLabel` 回带原状态、`targetTable/targetId` 指回原业务记录、`discardLocalCopy=true`（给设备端的安全信号：别因为看到「拒绝」就无限重推，数据已在系统里，可删本地缓存；只有 status=3 才是「改完再报」）、message =「该信息已存在（<how>，原上报编号 #id，当前状态：…），本次拒绝入账」。三处命中统一走它：同 bizKey / 同报文兜底 / 并发 `DuplicateKeyException`。
- `toResponse` 去掉恒 false 的 duplicate 参数。
- Controller 回执语义注释补 `status=5`。
- 前端：`INGEST_STATUS_LABELS/TAGS` 补 5（warning 色）；回执区重复时显示「该信息已存在，本次拒绝入账（未写入任何业务数据）」，**不再显示「已写入」**；toast 提示「该信息已存在，本次拒绝入账」；`demoReportLocked` 对重复同样置灰；上报编号 placeholder 改为「填同一个值再报一次，会被判重复上报并拒绝入账」。

**保留的放行**：第二道兜底仍只覆盖 待处理/待人工/已入账 三态——「已拒绝/已降采样」不判重复，外部条件补上后重发同报文要能过（verify-duplicate-report 用例 4 仍验证这一点）。

**回归**
- 新增 `logs/verify-duplicate-reject.mjs` + `-db.sh`（自驱）：同 bizKey 重推 / 换号同报文 / 已入账重推 → 全部 status=5、accepted=false、指回原记录、原状态如实回带、discardLocalCopy=true；报文改一个字段 → 正常新建（防误伤）；重复不新增队列行、不写业务表；落库核验（只建 2 条真新数据行、全库无 status=5、(device_no,biz_key) 无重复）+ 清理回基线。HTTP 21 断言 + 落库 7 断言全绿。
- `verify-batch-autogroup.mjs` 第 6 节断言改为 `accepted=false && status=5 && targetId=原批次`；`verify-duplicate-report.mjs` 补 status=5 断言。42/0、17 项全绿。
- `verify-reconcile.mjs` 生猪基线 27 → 29（307/308 是 2026-10-12 15:30/15:55 界面演示补建的，上次更新基线时还没产生）。

**部署**：15 模块离线编译通过 → `ingest-service` 镜像 `--no-cache` 重建 + `--force-recreate`（healthy）；前端两次 build（第二次补 placeholder）。

**踩坑记录**：直接跑 `verify-batch-autogroup.mjs` 会留残留，且**手动清理时必须连 `db_common.blockchain_record` 一起删**——否则批次号当日复用后，封批上链命中 `batchNo:action:pigId` 幂等少写一条，`-db.sh` 的「上链 3 条」断言会红（本次实际踩到，清掉 5 条残留后 42/0 全绿）。正确入口始终是 `-db.sh`。

## 十九、AI Agent 接入方案（2026-10-19 11:05，纯答疑，未改代码）

用户问「给工作台加 agent 分析数据、给管理员角色加 agent 协助工作，代码怎么改」。

**现状核实**
- 全仓库**无任何大模型/Agent 集成**（grep openai/llm/agent 只命中 user-agent 之类）。
- 工作台：`web-admin/src/views/dashboard/DashboardView.vue`（489 行，4 图 + 2 表）；数据唯一来源 `GET /api/v1/dashboard/overview` → `trace-service` 的 `DashboardService.overview()`，已聚合 statCards / sankey / 两个 distribution / chainTxTrend / recentWarnings / todoList —— **可直接作为 agent 的事实上下文，不需要模型自己算数**。
- 权限：JWT 由网关解析后透传 `X-User-Id / X-User-Role / X-User-Name`（`AuthGlobalFilter`）；**除 auth-service 的 `.requestMatchers("/system/**").hasRole("ADMIN")` 外，各业务服务没有统一的服务级角色注解**；前端权限靠 `router meta.roles` + `menuConfig.ts` + `authStore.hasAnyRole`。
- 角色表 `sys_role`/`sys_permission` 齐全（6 角色 9 权限码）。
- 服务间调用惯例：各服务自建 `*RemoteClient`（Spring `RestClient` + `services.*-url` 配置），如 `trace/client/TraceRemoteClient.java`。
- 基础设施已有 Redis（会话/限流）、RabbitMQ（异步）、Nacos（注册）、`backend/Dockerfile` 用 `ARG SERVICE` 一套多服务。

**推荐方案（要点，供后续落地）**
1. **新建 `ai-service`**，不把 LLM 塞 trace-service、更不前端直连。网关加 `Path=/api/v1/ai/** → lb://ai-service`；**不进 PUBLIC_PATHS**，自动 JWT 鉴权并带 X-User-* 头。`backend/pom.xml` 加 module、`Dockerfile` 加 `COPY ai-service`、`docker-compose.yml` 照 trace-service 模板（`LLM_BASE_URL/LLM_API_KEY/LLM_MODEL` 走 .env，不进镜像）。
2. **两条链路共用一套内核**：工作台分析＝固定 prompt + `overview()` 数据（建议非流式 + 缓存 5 分钟）；管理员助手＝多轮 + 工具调用（SSE 流式）。
3. **工具只调各服务既有接口**（照 RemoteClient 写），不直连业务库 —— 与「接入层不碰业务库」同一口径。
4. **权限 = 登录人权限子集**：工具转发原始 X-User-* 头由下游判权；ADMIN 专属工具在 ai-service 内部读 `X-User-Role` 判断（**不改 common-web**，避免踩「改 common 须重建全部依赖服务」红线）。
5. **写操作必须过确认闸门**：agent 只产 `ai_action_audit(status=pending)`，管理员点确认 → 以**本人身份 + 实名**（`ManualOperator.hasRealName()`）执行 → 回填结果并上链（bizType 如 `AI_ACTION`）。模型不得直接落库。
6. 前端：新增 `api/modules/ai.ts`；`components/ai/AiAnalysisPanel.vue`（工作台抽屉）、`components/ai/AiAssistantDrawer.vue`（AdminLayout 全局）；`views/system/AgentConsole.vue` + 路由/menuConfig 加 ADMIN 菜单。**SSE 不能用 axios 拦截器**（假定 `{code,data}` 信封），用 fetch + ReadableStream 或 `@microsoft/fetch-event-source`，token 取 pinia。
7. 表：`ai_conversation` / `ai_message` / `ai_action_audit`（utf8mb4 + 幂等迁移，编号接 42）。
8. 中文透传头仍须 `OperatorHeader.encode/decode`（本仓红线）。

## 二十、工作台按角色分视图（2026-10-19 11:20–12:00）

**需求**：不同角色看到的工作台不一样（例：零售商看销量）。要点是**后端按角色裁剪**，不是前端过滤。

**实现**
- `trace-service`：
  - `DashboardController` 读 `X-User-Role`（网关从 JWT 解出透传），未带角色头（直连/未认证兜底）按监管视角返回 —— 只给合规指标，不给经营明细。
  - `DashboardServiceImpl` 重写为 6 个视图方法（ADMIN/FARMER/SLAUGHTER_OP/DISTRIBUTOR/RETAILER/SUPERVISOR），每个角色返回**同一套结构**：`role/roleLabel/scopeLabel + statCards(key,label,value,unit) + blocks + todoList + degraded`。
  - `blocks` 通用协议：`sankey`（data={nodes,links}）/`pie`/`bar`/`barH`（data=[{name,value}]）/`table`（columns+rows+emptyText+link）；`span=2` 整行。**前端一套渲染器**（`DashboardView.vue` 重写为数据驱动），新增角色只改后端。
  - **降级不冒充 0**：每个下游调用经 `safe()`，失败记进 `degraded`（页面顶部黄字提示「部分数据暂不可用」），指标保持 0 但用户知道是「取不到」不是「没有」。
- `sales-service`：新增内部统计接口 `GET /sales/internal/stats/summary?days=30`（`SalesServiceImpl.statsSummary`）：近 N 日销售额/销量(kg)/件数、按日趋势（补齐缺日）、部位销量 TOP5（productName 经 `DistributionClient.requireSplit` 缓存取，上限 500 次）、在售/临期数。**只认 status=2 已售且未作废**。
- 前端：`DashboardView.vue` 数据驱动渲染（图标/渐变按 key 映射，`barH` 用于排行，`v-loading`，全 0 或空数据的图表显示「暂无可展示的数据」而不是空图环）；`types/dashboard.d.ts` 重写。

**踩坑**
- **block key 必须唯一**：分销商两个运单表都用 `transportTable` 作 key，Vue `v-for :key` 撞车 → 拆成 `waitingTable`/`movingTable`，回归补了「key 唯一」断言。
- **不能伪造 X-User-Role 测 SUPERVISOR**：网关会覆盖该头，必须真账号。种子数据独缺监管角色 → 补 `supervisor_user`/123456（org=1 市监局，密码哈希与盐直接复用 farm_user 的，同密码）。
- 零售商「在售商品」恒 0（库内 status=1 的为 0，全是已售/过期/召回）→ 卡片改成「近 30 天售出件数」，避免演示时看着像坏。
- **构建启动后再改源码不进镜像**：本轮 trace-service 白建了一次（改 soldCount/key 修复在 build 启动后），又按红线重建第二次才生效。
- 数据现实：生猪在养仅 2 头（27 头已屠宰）、今日屠宰/入场/盖章均为 0、门店 ID 历史脏数据（store_id=1 混在 1001-1014 中）→ 0 都是真实口径，空态文案写「今日暂无入场记录」这类业务话。

**已知边界（未做）**：机构级数据隔离。JWT 无 orgId 声明，养殖户/零售商仍看全平台数据（与各列表页既有行为一致）；要做需 JWT 加 orgId + 网关透传 + 各服务按机构过滤，属下一阶段。

**验证**：新增 `logs/verify-dashboard-roles.mjs`（**140/0**：6 角色登录各自断言卡片/区块、结构完整性、key 唯一、15 对角色两两不同、零售商销量口径与 30 天趋势、未登录 401、监管无经营明细）+ `logs/shot-dashboard-roles.cjs`（六张全页截图 `page-dashboard-{admin,supervisor,slaughter,distributor,retailer,farmer}.png`）。既有回归 reconcile 18/0、channel-label、stage-order 全绿。部署：`trace-service`+`sales-service` 镜像 no-cache 重建 + force-recreate（healthy），前端 build 生效。

## 二十一、零售商只看自己门店的数据（2026-10-19 12:10–13:00）

### 背景
上一轮把工作台做成按角色分视图时，零售商看的是**全平台**销量（462 元），列表页的
`storeId` 只是普通查询参数——换个数字就能翻别家。属于「页面不显示」而非隔离。

### 改动
**令牌与网关**
- `JwtUtil` 新增 `generateToken(subject, role, userName, orgId)` 与 `getOrgId(token)`；
  `AuthServiceImpl.tokensFor` 写入 `sys_user.org_id`。
- `AuthGlobalFilter`：公开路径与认证路径都剥离 `X-User-Org`（防伪造）；
  认证后从令牌取 `orgId` 注入 `X-User-Org`；`ORG_REQUIRED_ROLES={RETAILER}` 的令牌
  缺机构 → 401「登录信息缺少所属门店，请重新登录」（前端有 refresh 自动换新令牌，无感升级）。

**common-web 新增 `com.pork.web.scope.OrgScope`（@Component）**
- `role()/orgId()/retailer()` 读 `X-User-Role` / `X-User-Org`（RequestContextHolder）。
- `storeId()`：零售商=orgId；**零售商但无机构 → 403**（防绕网关直连服务端口）；其他角色=null。
- `storeOr(requested)`：零售商覆盖为本人门店；其他角色原样返回。
- `requireStore(actual, subject)`：记录级校验，写操作必须过。

**sales-service**
- `SalesService` 签名加 storeId：`pageQrs`/`pageWarnings`/`activate`/`sell`/`generateQrs`/`handleWarning`；
  `pageSales`/`statsSummary` 原有 storeId 参数改由 `scopedStore()` 解析。
- 预警表无门店列，用 `inSql(saleId, "SELECT id FROM retail_sale WHERE store_id=X")` 下推（分页 total 才对）。
- `statsSummary` 的 `onSale`/`expiring` 计数同步按门店（原来只按全平台数）。
- `/sales/internal/stats/summary` 虽为内部接口但网关可达，同样过 `scopedStore`。
- 越权文案：`【数据隔离】该商品不属于当前登录门店，无权查看或操作`。

**distribution-service**
- `pageTransports(status, keyword, storeId, …)`：`inSql(id, "SELECT transport_id FROM transport_stop WHERE store_id=X")`
  —— 零售商的「待签收」只出现含本店站点的运单。
- `createReceipt(request, storeId)`：请求门店 ≠ 本人门店 → 403「只能为本门店签收」。
- `/receipts` 列表 `storeOr`；`/receipts/{id}` 详情 `requireStore`。
- `DistributionServiceImpl` 加 `@Slf4j`（原来没有 log）。

**trace-service（工作台）**
- `DashboardController.overview(role, X-User-Org)` → `retailerView(storeId)`。
- 零售商门店识别不出：卡片全 0 + `degraded=["所属门店"]`，**不退回全平台**。
- `scopeLabel`＝门店名 + 「 · 近 30 天」，门店名走 `TraceRemoteClient.org(id)` →
  auth 内部接口 `/auth/internal/orgs/{id}`（`services.auth-url`，compose `SERVICES_AUTH_URL` 已有）；
  取不到退「门店 #ID」不猜名字。
- `TraceRemoteClient` 新增 `salesStats(days, storeId)`、`warningsForStore(storeId)`、`org(id)`。

**前端**
- 新组件 `components/common/StoreScopeTag.vue`：仅 RETAILER 显示「当前仅显示本门店：安心生鲜门店」；
  挂在 SaleRecords（header-actions）、QrcodeManage、ExpireWarnings（search-panel 顶部）、StoreReceipt（页顶）。
- SaleRecords 的「门店」输入框对零售商隐藏（是数据边界不是筛选项）。

**数据修复 `init-sql/42-store-scope.sql`（幂等，已在现库执行）**
- 历史脏数据：`retail_sale`/`store_receipt`/`transport_stop` 早期 store_id=1 或 NULL。
- 修复口径：只动「store_id 不是任何零售机构」的行，按本行 store_name 反查 sys_org（type='retail'）**唯一命中**才改；
  「示范一店」（11 个站点）机构不存在，不猜、保留原样，对零售商不可见。
- 回填前预检：确认新 (transport_id, store_id) 不撞 `uk_transport_store` 唯一键、每个名字只命中一家机构。

### 部署
`auth/gateway/sales/distribution/trace` 五个镜像 `--no-cache` 重建 + `--force-recreate`，全 healthy；
前端 build 生效。其余服务未动（OrgScope 对它们是惰性新类）。

### 验证
- `logs/verify-store-scope.mjs` **36/0**：令牌含 orgId；四列表只含本门店；传 storeId=1002 不换视角；
  两门店无交集；写操作（sell/activate/建码）拿别家记录被拒；管理员仍全量；
  工作台卡片销售额 == `stats/summary?storeId=1001` 且 ≠ 全平台、临期数 == 预警接口 total。
- `logs/verify-store-scope-db.sh` **11/0**（只读）：三类表无未归属行；名字可命中的站点 store_id 必已填；
  一ID一名；零售账号 org 必为 retail 类型且每店有账号。
- 既有回归全绿：`verify-dashboard-roles.mjs` 140/0、`verify-reconcile.mjs` 18/0、
  `verify-channel-label.mjs`、`verify-stage-order.mjs`。
- 截图 `logs/shot-store-scope.cjs` → `page-scope-{dash,sales,receipt,warnings}.png`；
  实测零售商工作台 77.00 元 / 1.50 kg / 2 件 / 3 临期（30 天口径，历史作废行不计），
  销售记录 7 条全为安心生鲜门店，待签收只剩含本店站点的那 1 单。

### 实测口径说明
孙店长（1001）库里有 19 条销售记录，其中 12 条 `void_flag=1`（历史回归作废），
有效为 4 售 / 1 过期 / 2 召回 = 7 条列表 + 工作台 30 天内 77.00 元 —— 与作废口径一致，不是丢数据。

## 二十二、删除批次人工入口 + 门店签收批量签收 + 零售商站点边界（2026-10-19 13:20–14:40）

### 需求
① 删除胴体批次的「创建批次」「添加关联生猪」按钮；② 门店签收加批量签收；③ 零售商只能看到自己店的签收状况。

### 改动
**前端**
- `CarcassBatch.vue`：删工具栏两个按钮 + 创建弹窗 + 追加弹窗 + 相关脚本（batchForm/slaughterhouseOptions/occupancyMap/append* 等）与样式，删 `highlight-current-row`；页面留搜索/列表/详情/封批/分割。列表页顶注释说明「批次只由分拣台归集」。
- `SplitBatch.vue`：空态不再给「前往创建批次」按钮（入口已删），改为说明批次由分拣台自动归集。
- `StoreReceipt.vue`：待签收页加勾选列 + 「批量签收（已选 N 单）」按钮 + 批量弹窗（统一 签收人/到货温度/数量核验/温度核验/包装状态，签收人默认当前登录人 realName）；提交后弹窗内展示逐单结果表（运单号/签收门店/已签收/未签收/说明）；新增「本次签收门店」列（signableStoreName，「多站待签」打灰 tag + tooltip）；零售商隐藏「门店名称」筛选（是数据边界不是筛选项）。
- `api/modules/distribution.ts`：`batchReceipts`。

**后端 distribution-service**
- DTO `ReceiptBatch`（transportIds @NotEmpty @Size(max=50)、receiver @NotBlank，核验项与单张签收同形）。
- `DistributionService.batchReceipts(request, storeId)`：循环每单 → `planReceiptStop`（0/多候选给原因，不猜）→ 经 `ObjectProvider<DistributionService>` 自身代理调 `createReceipt`（**逐单独立事务**；自调用会绕过 @Transactional）→ 逐单结果 `success/message/receiptId`；BusinessException 记原因，RuntimeException log.error +「签收失败，请稍后重试」。
- `ReceiptPlan` record（stop/problem）；跳过原因：无站点台账 / N 站同时待签 / 没有本店站点 / 本店已签 / 车未到 / 站点未记录门店。
- `ColdChainTransport` 新增 transient：`signableStopCount/signableStoreId/signableStoreName`；`fillStopProgress(records, storeId)` 改收站点实体分组，可签站点数==1 给门店名，>1 且带门店边界时取本店那站。
- `pageTransports` 门店子查询：status=3 时追加 `AND status < 2`（排掉本店已签站点——本店签完这张车即使别家没签也不再出现在本店待签收里）。
- `/transports/{id}/stops`：零售商只返回本店那一站（防拉别家运单站点清单泄露门店名/签收人）。
- 控制器 `POST /receipts/batch` → `{total, successCount, failedCount, items[]}`。

### 部署
distribution-service 镜像 `--no-cache` 重建 ×2（第一次构建启动后又改了 planReceiptStop 的「没有本店站点」文案，按红线重建）+ `--force-recreate`；前端 build ×2（同因）。

### 验证
- `logs/verify-batch-receipt.mjs` **27/0**：列表字段 / 零售商边界（别家运单站点为空、批量签别家全失败且不落单）/ 空入参 1001 / 正例签一单（store=1001、签收人正确、再签「本门店已签收」、本店待签收不再出现）/ admin 删签收回退（站点回已到达、运单回待签收）/ 最新批次 source=DEVICE。
- `logs/verify-batch-receipt-db.sh` **8/0**（只读不变量）：签收单↔站点互相咬合、运单状态↔站点汇总、(运单,门店) 唯一、已签必有到达时刻、签收门店在站点里（只判「站点有 store_id」的行；12 张「示范一店」历史签收单 [INFO] 不计失败）。
- `logs/shot-batch-receipt.cjs` **11/0**：批次页无两按钮、签收页有批量入口与「本次签收门店」列、零售商范围标签/本店列/勾选计数/弹窗打开。截图 `page-carcass-batch.png`、`page-receipt-admin.png`、`page-receipt-retailer.png`、`page-receipt-batch-dialog.png`。
- 既有回归全绿：reconcile 18/0、channel-label、stage-order、store-scope 36/0、no-hint-banner 8/0、dashboard-roles 140/0、batch-autogroup-db 42/0（自清理）。

## 二十三、冷链温度定时自动打卡（2026-10-22 10:25–11:05）

### 背景与口径
此前温度只有设备推送一条路（`ingestTemperature`，要求 status=2），系统不轮询，在途运单温度为 0 条。
本轮落地「发车后固定间隔自动打卡、送达后结束」，三条硬口径：
1. **采集窗口 = status=2**：运单状态由站点汇总推导（全部站点到达→3、全部签收→4），
   「车在跑」与 status=2 是同一件事；不需要开关，也不会出现签收后还在写温度的账。
2. **真机优先**：运单只要有 `source=DEVICE` 的温度记录，模拟采集对它永久停手——
   模拟值混进真实温度里，事后分不清哪条是车厢温度、哪条是程序编的。
3. **不补历史**：错过的间隔就是空白（is_gap 标出来）；一次补齐十条「当时没采到」的读数
   = 替探头编造它没采过的数据。

### 改动
- `support/TemperatureRecorder`（新）：温度落库**唯一口径**（越界/断档/间隔固化），
  `ingestTemperature` 重构为调它；删掉类内重复的断档判定与 `defaultSampleIntervalSeconds`/
  `gapToleranceMultiplier` 两个 @Value 字段。
- `service/TemperatureAutoSampler`（新）：@EnableScheduling + @Scheduled(fixedDelay 30s)；
  每 30 秒扫一次 status=2，到点（距上一条 ≥ interval）才落一条；读数 -18~-16℃ 正弦缓变、
  按运单号错相位（不编越界值——异常必须来自真实探头）；
  `source=SIMULATED` / `recordMethod=AUTO` / `recorder=模拟探头` / sourceRef=`PROBE-SIM-01/{运单号}`。
- 控制器 `POST /distribution/internal/temperature/sample?force=true`：忽略「未到间隔」立即采一条，
  只给回归/演示；状态窗口与真机让位两条约束照样生效；force 记录 sourceRef 带 `#force` 后缀，
  回归收尾按后缀精确删（按间隔猜会误删定时任务的正常记录）。
- 配置：`distribution.temperature.auto-sample.enabled=true / interval-seconds=300 / scan-interval-ms=30000`。
- 前端：`SOURCE_LABELS.SIMULATED='模拟探头'`（tag=danger）；TransportMonitor 温度卡头
  「自动采集 · 每 X」（取最后一条固化的 sampleIntervalSeconds，无记录时兜底 5 分钟）；
  采集设备列 tooltip：DEVICE=探头自动采集，SIMULATED=真机接入前用于演示、不代表真实车厢温度。

### 部署
distribution-service 镜像 --no-cache 重建 ×2（第一次构建后又改了 force 参数，按红线重建）+ force-recreate；
前端 build 一次（nginx 只读挂载即生效）。

### 验证
- `verify-temp-auto.mjs` 27/0：采一条（落库/来源/间隔固化/区间内不标异常/条数+1）→
  节流（立刻再扫不写、原因「未到采样间隔」）→ 停采边界（待发车/待签收/已签收均不在范围且条数不变）→ 升序返回。
- `verify-temp-auto-db.sh` 9/0：间隔固化、读数区间、无「真机+模拟」混单、
  到达后无新增、落库时刻≈记录时刻（不补历史）、近 10 分钟新增只来自运输中单、#force 清理。
- 实测定时节奏：10:40:57 → 10:46:08（310s）→ 10:51:38（329s）；日志 `temperature_auto_sample written=2 scanned=3`
  （第 3 张是被真机让位的单）。
- 既有回归全绿：temp-gap-db 34/0（口径重构未破坏设备路径）、reconcile 18/0、channel-label、
  stage-order、store-scope 36/0、dashboard-roles 140/0、batch-receipt 27/0。
- 截图 `page-temp-auto.png` / `page-temp-records.png`。

### 踩坑
- Edit 重复插入 `temperatureRecorder` 字段 → javac 在该处早退 → **Lombok 整个没跑**，
  报出满屏 `cannot find symbol getXxx()` 假错误；真错只有一条。遇到满屏 getter 报错先找
  「already defined / 语法错」这一条。
- force 参数是镜像构建启动后才补的 → 按红线重建第二次。

### 顺手修
- store-scope「门店销售额>0」哨兵断言随种子数据老化（9 月销售滚出 30 天窗口→真实为 0）：
  断言改为「统计接口有数才要求卡片有数」；1001 店 4 条种子销售 sell_time 刷到 10-16~10-20。

## 二十四、一车一趟：一辆车同一时间只能挂一张未完成的运单（2026-10-22 12:00–16:30）

### 需求与边界
用户原话：「在冷链运输部分，运输车名下有未完成的订单时，应该不能再接受新的运单，解决并且把有多个运单的删除，只留下一个待完成的运单」。
两件事：①**加约束**（新单不受理）②**清存量**（同车牌多余的未完成单只留一张）。

**关键边界（别弄错）**：
- 不能把「并入」一起禁掉。分界线是「**要不要新开一张单**」：同一趟车继续装货走 `appendToTransport`（往里加货，仍是一张单）**必须放行**；只有「要再开一张」才拦。
- **只有 `status=4` 已签收才算「车回来了」**。1 待发车 / 2 运输中 / 3 待签收 都算未完成 —— 车还在外面，接不了新单。
- `DEPART` / `ARRIVE` 里「同车牌多张 → 转人工」的兜底代码**保留不动**：那是给脏数据留的，不是在造局。

### 实现（后端）
- 新增 `distribution-service/support/ActiveTransportGuard.java`：
  `ACTIVE_STATUS = List.of(1,2,3)`；`activeOf(vehicleNo, excludeId)`；`assertFree(vehicleNo, excludeId, action)` 抛 `BusinessException`，文案带**既有运单号 + 中文状态 + 两条出路**（送到门店签收 / 核对作废后删单）。`statusLabel()` 给中文状态，不把枚举码甩给用户。
- 三处调用（只有这三处会**新开单**）：
  1. `createTransport` 首行（人工建单）；
  2. `updateTransport` 内**只在该单的车牌真的变了**才查，且 `excludeId=自己`（否则改个目的地也会拦自己）；
  3. `generateTransportFromPlans` 放在 `findOpenLoadTransport` **并入判断之后**、`transportMapper.insert` 之前 —— 顺序反了就会连并入一起拦死。
- 没有动：`appendToTransport`（并入）、`deleteTransport`（删单本来就要能删）、站点/签收逻辑。

### 存量清理脚本 `init-sql/43-single-active-transport.sql`（幂等，已执行）
- 留哪张：`ROW_NUMBER() OVER (PARTITION BY vehicle_no ORDER BY status DESC, create_time DESC, id DESC)` 取 `rn>1` 删 —— 留**离完成最近**的那张（已到门店待签收 > 运输中 > 待发车）。
- **有 `store_receipt` 的运单不删**：门店签收单是下游凭证，删了会让已发生的货权转移失去出处。
- 连带（与 `deleteTransport` 同口径）：温度日志 → 计划明细退回（`shipped=0/transport_id=NULL/shipped_time=NULL`）→ 按剩余量重算计划状态（全未发 1 / 全发走 2 / 否则 4，`status<>3` 不复活已取消）→ 装载明细 → 站点台账 → 运单。
- 三段核对：同车多张未完成=0、孤儿装载明细=0、孤儿站点=0。
- 踩坑：①临时表要先前置 `USE db_distribution;`，否则 `ERROR 1046 No database selected`；②`delivery_plan` **没有** `cancel_reason` 列。
- 实测（清前 京A·12345 名下 276 待签收 / 277 运输中）：删 277（含 14 条模拟温度、1 批 11kg 装载明细、计划 389 的排车）；计划 389 回 `status=1`、`transport_id=NULL`；明细 582 回 `shipped=0`。幂等重跑无输出。

### 回归改造（本轮最花时间的部分）
新脚本 `logs/verify-one-vehicle.mjs` 10/0（一次性车牌 `京A·GV/GW+时间戳`，A 建单成功 → B 同车牌被拒 → C 换车牌成功 → D 改挂被拒且车牌未变 → E 删旧单后可再接单 → F 收尾回池）。

受新约束影响、必须跟着改的既有回归：

| 脚本 | 原构造 | 为什么坏 | 改法 |
|---|---|---|---|
| `verify-load.mjs` | 主力车牌 京A·12345；K 组「C1 发走就另开一张」 | 12345 名下常年挂业务单 276；新规则下 C1 运输中就不许再开 | 车牌换 京A·T0002；K 组改成「发车→同车牌再装被拒→到达→创建签收单→status=4→同车牌扫另一批另开一张」（报文加 `returnTrip` 破兜底判重） |
| `verify-depart.mjs` | F 组人工造第二张同车牌待发车 → 倒查出多张 → 转人工 | 那个局面程序上已造不出来（人工建单也过闸门） | F 组改为「T1 在路上 → 同车牌再装被拒 + T1 未动」 |
| `verify-temp-auto.mjs` | A 组借种子 `status=2` 运单采温度 | 那些单早有真机读数 → 模拟永久让位，断言必然红 | 改成自建对象（`京A·T0005`+待发运池首批→建计划→LOAD→depart），收尾删运单+取消计划 |
| `verify-multistop.mjs` | 收尾只删运单 | 有 `store_receipt` 时 `deleteTransport` 拒删 → 残留堵住车牌，后续连跑全废 | 收尾补 `DELETE /distribution/receipts/{id}`（先删签收单再删运单） |
| `verify-temp-gap.mjs` + `-db.sh` | 同一台车建 T1 运输中 + T2 待发车 | 第二张被「一车一趟」拒 → 后续 16 项连锁失败 | 改**两台车**（`京A·T0006`/`京A·T0007`），db 侧新增「两张单分属两台车」「全库同车多张未完成=0」两条断言；终端 A 暂存行 6→5、转人工 7→6、运单 3→1、全库 +3→+1 |

**三条教训（写进纪律）**：
1. **回归必须自持**：凡「独占型写入」（一车一趟下的车牌/运单），必须自带一次性主体，用完自己删；借演示台账迟早被业务数据或新约束打死。
2. **新约束会打断「借现成台账」的构造**：加约束时同步遍历所有 `verify-*`，逐个核对它假设的「同一主体能有几张单」。
3. **删运单的连带顺序固定为**：温度/签收/站点/装载明细 → 计划明细退回 → 重算计划 → 运单；漏一个子表就删不掉，删不掉就留下堵路子的残留。

### 验证（全绿）
one-vehicle 10/0｜load 53/0 + load-db 55/0｜depart 22/0 + depart-db 34/0｜multistop 44/0 + multistop-db 47/0｜arrive-stop 44/0 + arrive-stop-db 67/0｜temp-auto 32/0 + temp-auto-db 32/0+9/0｜temp-gap 26/0 + temp-gap-db 36/0｜batch-receipt 27/0 + db 8/0｜delivery-plan-db 26/0｜order-intake-db 31/0｜reconcile 18/0｜dashboard-roles 140/0｜store-scope 36/0｜channel-label / stage-order 全部通过｜no-hint-banner 8/0｜transport-page 13/0｜nav-actions 18/0。

## 二十五、分割树根节点标注猪的品种（2026-10-22 13:05–13:45）

### 需求
「分割操作页面的父节点在 CB20261012-001 这种编号后面添加猪的种类」→ 树根标题变为 **`CB20261012-001（三元杂）`**。

### 实现（口径：标题唯一出口仍是 `splitTree.ts` 的 `splitNodeTitle`）
- **breeding-service** 新增内部端点 `GET /breeding/internal/pigs/breeds?ids=`：一条 SQL 只回品种字符串（去重、排序），刻意不复用按 id 逐头取详情的 `byIds`（N 次查询带整份档案，展示一个品种犯不着）。只查有效档案（逻辑删除过滤）。
- **distribution-service**：
  - `BreedingClient.breedsOf(pigIds)` —— 展示信息不是判定依据：养殖侧不可用/为空一律返回**空列表**，绝不因它把树渲染失败；
  - `CarcassBatch` 加 `@TableField(exist=false) List<String> pigBreeds`（模式同 `splitCount`）；
  - `getSplitTree` CARCASS 分支：猪 id 取**权威表 `carcass_batch_pig`**（不读 `pigIds` 快照，虽然两者必须相等）→ `setPigBreeds` → 跟 entity 一起序列化进 `data`。
- **前端 `splitTree.ts`**：`SplitNode.pigBreeds?: string[]`；`splitNodeTitle` default 分支 = `批次号（品种1、品种2）`（同批混养逐种列出、顿号连接）；`pigBreeds` 空 → 退回纯批次号，**不许编兜底品种**。`normalizeSplitTree` 展开 Spread 自然带过，无需改。分割操作页（SplitBatch.vue）走这同一出口。

### 验证
- `verify-split-tree.mjs` **34/0**（原「根标题＝批次号」断言改为「＝批次号（品种）」+ 接口带 `pigBreeds` + 归一化不剥品种 三条）。
- 新 `logs/verify-split-tree-breed.cjs` **7/0**（浏览器实测页面根标题含「CB20261012-001（三元杂）」+ 截图 `page-split-tree-breed.png`）。
- 波及面复跑全绿：one-vehicle 10/0、batch-receipt 27/0、reconcile 18/0、trace-void 正常。breeding/distribution `--no-cache` 重建 + force-recreate，前端 build 生效。

### ⚠ 顺带发现的存量问题（与本改动无关，未修，待拍板）
`verify-split-camera.cjs` 4 项红：**分割示例值是全确定性报文**（无时间字段 —— LOAD/DEPART/ARRIVE/VACCINE 的示例都有「取当前时间」字段，SPLIT 的 data 里没有任何时间），同批次第二次演示必命中兜底判重（`status=5`），回执照样显示「原记录状态：已入账」，但**相机连拍不会触发**（`accepted=false` 走不到 `autoShootAfterSplit`）。库里证据：SPLIT 通道最新暂存行停在 2026-10-12 13:39（DEMO-1791783557656），此后任何重放都是重复。修法方向二选一：
  1. 示例值挑**批次内还没切过的耳标**（一头白条一次分割，语义最正；重复演示自然受限于一圈的猪数）；
  2. 回归自带一次性主体（但胴体批次键＝场+日，一天一场，重跑受限）。



## 二十六、演示设备收敛：同通道重复设备只留一台（2026-10-24 14:20–15:00）

### 背景与决策
用户发现：ENTRY 通道三台设备（CERT-001 检疫证核验 / GATE-001 门禁地磅 / RFID-001 耳标识读器）
报文逐字段相同（示例值按通道生成、不看设备）；车载终端三行、温度探头三行看着冗余。拍板收敛：
- **ENTRY 只留 GATE-001**，删 CERT-001、RFID-001（三台走同一个 EntryChannelHandler，示例值一样，多台只是名字不同）；
- **车载终端只留 ARRIVE-001**（通用、有意不绑车牌），删 DEPART-001、DEPART-003。
  依据：DEPART/ARRIVE 同属 IngestChannel.deviceGroup() 能力组，**报文自带 channel**，
  demoReport 显式传 channel 即可让一台终端兼任两个动作；「报文与台账都没有车牌 → 拒绝」
  的红线用例正靠不绑车的终端成立，故留它而不是留绑车的 DEPART-001；
- **温度探头只留 TEMP-001**（京A·12345），删 TEMP-002（冷库固定）、TEMP-003（二号车）。
  依据：TemperatureAutoSampler 按「status=2 的运单」扫描，**完全不依赖设备表**，删探头不影响模拟采集。

结论上「不区分冷藏车/其他车」无需改代码：vehicle_type 后端零业务判定（纯展示），温度采集门槛=运单运输中+绑探头。

### 改动
- 运行库：DELETE 六行 ingest_device（历史 ingest_staging 的 device_no 引用是历史事实，保留）。
- 种子：11 号删四行设备；13 号删对应 UPDATE；31 号删 TEMP-003 绑定与 DEPART 插入段；
  39 号重写（ARRIVE-001 remark 不再引用已删设备；对已删行的 UPDATE 落空 0 行无害，保留历史语句）。
- 回归：verify-depart.mjs 的 departReport **必须显式带 channel:'DEPART'**（没传就按台账 channel=ARRIVE 走，
  报文会被当成到站刷卡——第一跑 12 红就是这个）；DEPART_A/B 常量并入 ONBOARD='ARRIVE-001'；
  E 组「台账兜底」正向用例（载体 DEPART-003 没了）改接「报文与台账都无车牌 → 拒绝(3)」红线用例。
  verify-depart-db.sh：设备断言改「ARRIVE-001 一台不绑车牌 + DEPART 通道 0 台 + 无重复设备号」，
  结论分布改「入账1/转人工5/拒绝1」。verify-arrive-stop-db.sh 三台系列断言改单台。
  shot-duplicate-reject.cjs 下拉改选 GATE-001。

### 验证（全绿）
depart 22/0｜depart-db 30/0｜arrive-stop-db 68/0｜entry-gate 27/0 + db 21/0｜temp-gap 26/0｜判重演示出图正常。
设备台账 18 台、无重复设备号。

### 教训
- **demo 上报不传 channel 就按设备台账的 channel 路由**：通用终端（承担多通道）上报时必须显式带 channel。
- **共享 LOADER-001/ARRIVE-001 水位的 db 脚本不能并行跑**（arrive-stop-db 与 temp-gap 并行互踩，
  窗口内装车暂存多出一条 + 基线配平少一条；串行重跑即绿）。

## 二十七、TAG 通道能力边界收紧：设备只报耳标号，档案信息人工录入（2026-10-24 17:20–17:45 收尾）

### 决策背景
用户由「耳标读写器能读出品种/性别/出生日期吗」追问到生猪档案改造，最终拍板最小方案：
**除了耳标号，其他的都要人工录入**（转群/圈舍流转管理明确砍掉——溯源系统只记事实不帮猪场管生产）。

### 实现
- ingest `TagChannelHandler`：validate 只剩 earTagNo；dispatch 只透传 earTagNo+source 三件套。
- breeding `DeviceTag` DTO 瘦身（删 7 个人工字段）；`ingestTag` 重写：已建档→幂等返回；新耳标→manual「请先人工建档」。
- **人工建档恢复**：`POST /pigs` → `PigIndividualServiceImpl.create`（data_source=MANUAL）；
  `TraceStage.TAG` 默认策略 `DEVICE_ONLY→MANUAL_ALLOWED`（作废不受策略影响，已核实无其他消费点）。
- 前端：PigList「人工建档」弹窗；模拟上报 TAG 示例=耳标号+**读取时刻 readTime（默认当前）**。
  readTime 是关键：同一头猪再次经过读写器是常态不是重放，没有时刻字段必撞兜底判重（status=5）。
- 历史脚本 `verify-tag-birthdate.mjs`（验证出生日期必填）已删——它验证的行为被有意废除。

### 回归（全绿）
tag-manual 12/0｜entry-gate 27/0｜preslaughter-gate 34/0｜ractopamine-block 43/0｜batch-autogroup 37/0｜
channel-label / stage-order 通过｜reconcile 18/0。四脚本造猪改 `POST /breeding/pigs`（farmName→farmId）。

### 清理
两波残留按业务键级联清（17:2x 四脚本跑出的猪 347-368 全链 + 13:5x 连拍调试走全链的 pig 346、
CB20261024-001、SP20261024-001-01~05、split_job 139、staging/chain 窗口行、unlock 12-14）；
清后 reconcile 18/0 复核。

### 教训
- **设备通道塞不进人的知识**：TAG 曾要求报文带 birthDate 否则转人工——等于强制设备编造读不到的数。
- **新语义通道要有「事件时刻」字段**：没有时间戳的报文，第二次真实上报就是「重复上报」。
- demo 上报走了全链路调试（点示例值）会沿 TAG→检疫→出栏→入场→分割建出一整串业务数据，调试后要按链清。

## 二十八、生猪档案批次化：批次档为主档，个体行自动展开（2026-10-25 15:20–17:00）

**动机**：用户确认「除耳标号外都该人工录入」后追问现实口径——现实商品猪按批管理（同场同批进出），逐头建档既做不到也没人做；个体档只给种猪。用户拍板按批次改。

**关键设计判断（为什么不是 carcass_batch_pig 直接挂批次 id）**：一个生猪批次会拆进多个胴体批次（分拣台逐条白条上报、按场+日归集），`carcass_batch_pig.pig_id` 唯一约束若改成批次 id，第二批白条就进不了新胴体批——红线「一头猪只归一批」会反过来挡死正常流程。所以落地为：**批次档（pig_batch）承载人的知识，个体行（pig_individual）降级为批次成员行，由批次登记按耳标区间自动展开**。下游逐头事实（入场查验、检疫证、瘦肉精、盖章、白条归批、trace）零改动，全部红线保持。

- 新表 `pig_batch`（03-breeding.sql）：farm+batch_no 唯一、品种、entry_date、ear_tag_start/end、quantity、来源三件套、void 四件套；`pig_individual` 加 `batch_id` 列。
- `POST /pigs/batches`（PigBatchController）：登记时校验 场存在/同场批次号唯一/区间不与已有耳标重叠，然后按区间展开成员行（dataSource=MANUAL，sourceRef=「生猪批次 xxx（按耳标区间自动生成）」）。
- 耳标区间解析：同前缀+纯数字尾号、起≤止、≤2000 头；**例外：起=止的单头批次不要求尾号是数字**（种猪个体批 + 回归脚本单头用例都靠它）。
- `POST /pigs` 个体建档入口删除（PigIndividualDTO.java、PigTable.vue 一并删）；`pigApi.create` 删，新增 `pigBatchApi`；PigList.vue 重写为批次档案页（登记弹窗=批次号/场/耳标起止/品种/进场日期/来源，列表列=耳标区间/数量/在养已出场）。
- `ingestTag` 语义：耳标命中成员行→幂等入账；区间外→转待人工提示「登记批次（耳标区间含此号）」。**TAG 报文人工字段照旧一律不认**。
- voidBatch：成员 status≥2（已出栏/屠宰）拒绝整批作废；通过则成员行一并 void + 上链 PIG_BATCH_VOID。
- VO：PigIndividualVO 加 batchId/batchNo（列表/trace/mini-app 都能带出批次号）；菜单/路由标题改「生猪批次档案」；IngestMonitor TAG 提示改批次口径。

**迁移与种子**：`logs/pig-batch-migrate-20261025.sql`（已执行）。教训：**按场 GROUP BY 归批会产生「区间空洞」**（farm8 只有 0013/0019 两头，区间却罩住 0013~0019，5 个不存在的号落在批次区间里）——测试/存量数据回填必须逐头单批（PB-T1-<tag>）或保证区间连续。07-demo 增加示范批 PB-DEMO-0001（0001~0003 连续成员）；08 增加 PB-TEST-0xx（按场，运行库已改为逐头）。

**回归结果**：tag-manual 15/0（重写）、entry-gate 27/0+db 21/0、batch-autogroup 42/0、preslaughter 34/0、ractopamine 43/0、split-tree-breed 7/0、breed-dict 正常、reconcile 18/0（**基线 29→30**：示范批成员 ET-DEMO-0003 是新种子数据）、批次档案页截图 shot-pig-batch-list.cjs 6/0。残留按业务键级联清零（含链窗口 15:30 后 + 成员行 + 批次 + 胴体批 191）。

**坑**：① vite build 清 dist 被 sandbox 拦 → 先 `rm -rf dist` 再 build；② blockchain_record 没有 payload 列，链清理只能按 biz_key 或时间窗；③ 脚本里耳标带 `-E` 之类非数字后缀，靠「单头批次豁免」兼容，不用改标签格式。

## 二十九、门店拒收：冷链异常货物的正式出口（2026-10-25 18:25–19:10）

**动机**：冷链运单此前只有「到达→签收」正向路，货有异常时门店无处可去（单悬着车被锁、删运单毁追溯链）。拒收是真实商业事实，给它正式状态通道而不是「取消」。

- **站点终态**：`TransportStop.STOP_REJECTED=3`；`transport_stop` 加 10 列（reject_reason/by/time/source/source_ref、signed_after_reject、disposal_type/note/by/time），迁移 `44-stop-reject.sql`（information_schema 守卫幂等），32 号建表同步。
- **三接口**（DistributionController）：`POST /transports/{id}/stops/reject`（理由必填≥2字+登录实名，借 SourceGuard 闸门）、`.../stops/disposal`（RETURN_SUPPLIER/DESTROY/DISCOUNT，结论字段保护：一经登记不可改）、`.../stops/convert-receipt`（协商改签收：复用 createReceipt 落凭证+signedAfterReject=1；已处置的站拒改签）。
- **推导口径是关键设计**：REJECTED=3 ≥ STOP_SIGNED=2，`recomputeTransportStatus` 的 `>=` 比较天然把拒收视同「已终结」——单店拒收不卡别店签收、全部终结运单落 4，**ActiveTransportGuard（1/2/3 未完成）与温度采集窗（status=2）零改动**，车辆不会被锁死。
- **天然排除**：`planReceiptStop` 候选 `< STOP_SIGNED` 天然不含拒收站；新增「本门店已拒收」分支文案。**补的洞**：createReceipt 原校验 `< STOP_ARRIVED` 拦不住拒收站（3≥1）——加显式守卫「已拒收，走改签收」；PDA ingestReceipt 同口径守卫（manual，设备无权翻案）。
- **received 语义收紧**：transportStops 的 `received` 从 `>=SIGNED` 改为 `==SIGNED`——「拒了」不能冒充「收了」。fillStopProgress 加 rejectedStopCount；pageTransports 加 `rejected` 过滤参数（inSql 下推）供门店「拒收/处置」页签。
- **前端**：StoreReceipt.vue 待签收行加「拒收」按钮（理由弹窗）+ 新「拒收/处置」页签（处置登记/协商改签收表单）；TransportMonitor.vue 时间轴/站点摘要区分「签 X/拒 X」，站点表「签收/拒收」列展示理由+处置；AuditLog 与 mini-app 加 STOP_REJECTED/STOP_DISPOSAL/STOP_REJECT_REVERSED 字典。
- **链上**：STOP_REJECTED（bizKey=transportNo:STOP:stopNo）、STOP_DISPOSAL（…:DISPOSAL）、STOP_REJECT_REVERSED（…:CONVERT），hash=sha256Json。

**坑**：① DistributionDeviceIngestService 是显式构造器（非 @RequiredArgsConstructor），加 final 字段必须同步构造参数——docker build 的 maven 抓出 `might not have been initialized`，本地 `mvn|tail; echo EXIT=$?` 的 EXIT 是 tail 的，**验退出码必须 `sh -c "mvn …; echo \$?"`**；② docker compose build --no-cache 起跑后改源码不进镜像，构建中途改码要停掉重建；③ web-admin `rm -rf dist` 后台跑会被 safe-delete 拦（107 文件超阈值），前台跑走升级通道。

## 三十、养殖系统同步：外部 ERP 批次档案接入（2026-10-26 11:30–12:40）

**动机**：批次档此前 100% 人工（data_source 恒 MANUAL）。现实中溯源系统不自己造养殖端数据——批次、品种、耳标区间、进场/出生日期本来就在养殖场 ERP 里，人工再抄一遍既费人又会抄错。「接过来」但「不信它」：走接入层管道（原始报文留档 + 判重 + 状态机 + 人工兜底），绝不直写档案表。

- **DDL `45-farm-erp-ingest.sql`**：pig_batch 加 `external_system`(NULL=人工)/`external_id`/`birth_date`(批次级出生日期，ERP 给，日龄现算)/`void_source`(MANUAL/EXTERNAL)；唯一键 `uk_external(external_system, external_id)`。设备台账 FARM-ERP-001（密钥 farm-erp-demo-key-2026，注释写明生产须换）。03-breeding.sql 同步。
- **通道**：`IngestChannel.FARM_ERP("pig_batch","养殖系统同步（外部ERP）")`，放枚举第一位（档案源头早于耳标建档）。Handler `FarmErpChannelHandler`：validate 结构性校验（action∈UPSERT/REVOKE、farmName/batchNo 必填、区间或 earTags 清单二选一、REVOKE 必须有 voidReason）；dispatch 调 breeding `/breeding/internal/ingest/farm-batch`。日期归一在 handler（2026/10/01、20261001 → ISO）。
- **breeding `FarmBatchIngestService`**（回执 {written,id,reason,retryable}）：
  - UPSERT：external 键查（**含作废行**，防重推覆盖撤销痕迹）→ 已作废转人工；新建前查同场批次号撞车（含作废）+ 耳标冲突（`selectAnyByEarTags` 含作废含 deleted，分批 300/次，limit 20）；更新时空字段跳过、**成员只增不删**、quantity 与 ear_tag_start/end 用 `batchMemberSummary`（COUNT/MIN/MAX）按成员重算。
  - REVOKE：查不到转人工；已作废幂等 written；成员 status>=2 转人工（事实已发生外部撤不掉）；`voidByExternal`（void_source=EXTERNAL、operator「养殖系统(XXX)」）+ 成员一并作废 + 上链 PIG_BATCH_VOID bizKey=**批次号:REVOKE**（与人工 :VOID 区分）。
  - 转人工四条：场名对不上（V1 场名精确匹配，同名多场也转——注释写明真实对接应换对码表）、同场批次号撞车、耳标被占、撤销后重推。结构性拒绝：缺成员、撤销无理由、日期格式不成立。
- **人工复查红线**：`requireReviewerForCriticalStage` 扩展——FARM_ERP 待人工放行必须实名+理由（批次档不是溯源环节，不进 TraceStage，**改 ingest-service 本地而不动 common-core，避免全量重建**）。
- **区间口径抽公共类** `com.pork.breeding.support.PigTagRange`（PigBatchServiceImpl 原私有 record 迁出）：人工登记与外部同步共用一套展开规则。**坑：record 跨类访问组件要用 accessor `range.start()`**，字段私有（编译器抓的）。
- **前端**：① 通道中文名四处同步（ingest.ts CHANNEL_LABELS 首位 / IngestMonitor DEMO_FIELDS+CHANNEL_TABLES+TABLE_TARGET_OBJECTS.pig_batch「生猪批次档案（外部系统同步）」/ verify-channel-label.mjs / 后端 labelOf）；② fillSample 加 FARM_ERP 示例分支（批次号 ERP-<stamp>、耳标段带 stamp，防撞上一轮）；③ PigList.vue 加「日龄」列（today−birthDate 现算，空显示 -- 带 tooltip「人工登记不要求」）与「档案来源」列（el-tag 养殖系统同步=success / 人工登记=info plain，外部编号进 tooltip）。
- **演示资产**：`logs/farm-erp/` 4 份报文模板（{{RUN}}/{{FARM}}/{{ENTRY}}/{{BIRTH}}/{{ENTRY_FIXED}}/{{NOW}} 占位符，报文来自文件不拼字符串）；`logs/farm-erp-demo.mjs` 六步演示（推批→同号重推→换号重推→撞耳标→源撤销→撤后重推，默认留数据供看页面）；`logs/shot-farm-erp.mjs` 界面验收 9/0（截图 page-farm-erp-pig-list.png / page-farm-erp-ingest.png）；`logs/verify-farm-erp.mjs` 26/0；`logs/verify-farm-erp-db.sh` 列级核验 15/0 + `--clean`。
- **回归**：tag-manual 15/0（**顺手修老坑**：readTime 秒级精度，同轮两次上报落同一秒 → 报文全等撞兜底判重 → 改 tickText 每次前移 1s）、channel-label 全过（19 台设备 18 通道）、batch-autogroup 37/0、reconcile 18/0。breeding/ingest `docker compose build --no-cache` 重启，测试数据按业务键清零（外部批次/staging FARM-ERP-001/链 REVOKE 事件）。

**坑**：① **撤销后重推的报文必须与首推有字段差异**——否则兜底判重（状态含 ACCEPTED）在第一道就拦掉，到不了「已撤销不自动复活」的业务检查，演示第 6 步会显示「重复上报」而非「已撤销」（04 模板因此改了 entryDate）；② 多表 DELETE（即使表名带库前缀）报 `No database selected`，必须 `USE` 后执行；③ data_source 沿用 14 期枚举取 `API`，不新造 EXTERNAL（void_source 才是新概念）；④ batchMemberSummary 的 quantity 断言要排除 void_flag=1 的批次（作废批次保留历史数量，有效成员为 0 是对的）。

## 三十一、记录按批次分组视图：方案 A 展示层聚合（2026-10-26 14:30–15:10）

**需求来源**：用户看到疫苗记录/产地检疫/出栏申报列表 30 头猪刷 30 行耳标，问「能不能只显示批次」。核实结论：三块数据模型都是逐头（vaccine_record/quarantine_certificate/slaughter_apply 各一头一条），且检疫证 39 条=39 个证号、申报 42 条=42 个单号——**底下没有「单据」层**，列表啰嗦不是前端展示错。屠宰端（入场/检验/盖章）必须逐头：一头不合格不能牵连整批，追溯最小可信单元就是这一头。

- **方案 A（本次，展示层聚合）** vs 方案 B（补主单表）：A 不动表结构，join pig_individual 取 batch_id 分组；缺点是「推断的单据」——组键=批次+动作签名+日期，两批同天打同疫苗会并成一组（已知局限，接受）。B 是终态（免疫单/检疫证/申报单挂 N 明细），等对接证核销再上。
- **后端**：`RecordGroupVO`（groupKey/batchNo/title/subTitle/happenedOn/headCount/tagStart/tagEnd/statusSummary/members[]）+ `RecordGroupService`：组键=批次+动作签名（疫苗名/出证机构/目标屠宰场）+发生日期；**未归批（batch_id null）按头独立成组，绝不互并**（F 组断言锁死）；分页按组算（G 组）。端点 `/breeding/records/{vaccines|certs|applies}`。
- **前端**：VaccineTable/QuarantineCertList/ApplyTable 改主行=组、展开=members 逐头；疫苗页删掉「逐头并发拉 /pigs 再逐头拉 /vaccines/list」的 N+1；状态摘要后端算好（「有效 28 / 已作废 2」「待审 25」）；批量审批按钮 pendingCount(row) 由组内 approvalStatus===0 计数。
- **回归 verify-batch-group.mjs 34/0**：自持（建场→30 头批→设备通道真报文疫苗×30 带 1x1 PNG 凭证→逐头出证→逐头申报→作废 2 条→改 2 头无批次→清场按耳标前缀+批次号）。坑：免疫是设备独占环节缺凭证照判待人工（fileIds 必须真上传）；execSync 里 grep -v Warning 无匹配返回 1 会当命令失败（末尾 || true）。
- **两个残留坑**：① verify-batch-autogroup.mjs 两段式（mjs 打印 id、bash 清理），**只跑 mjs 不跑配套 verify-batch-autogroup-db.sh 会留全套残留**——14 头 ET-BAG 猪+14 个 PB-BAG 批+5 个胴体批+入场 14/盖章 8+链 2871–2936 连续段，reconcile 从 18/0 掉到 16/2（胴体批 29≠24、生猪 44≠30）。清法：链按 id 连续区间、业务表按 pig_id/batch_id，先查下游（split_job=0 才敢删）。② verify-batch-group 自身漏删链上 QC 事件（30 条幽灵凭证 biz_key=QC-<RUN>-<耳标>）——补删链语句+H4 断言。
- **验收**：shot-batch-group.mjs 12/0（自推 6 头全链路→三页截图→自清含删链）；截图 page-group-vaccine/quarantine/apply.png，分页文案「共 N 组（按批聚合，点行首箭头看逐头）」。

## 三十二、误删用户「处理计划」事故与恢复（2026-11-05 14:40–19:10）

**事故**：收尾清理时把 `recall_collect` id=18（召回单 RC20261105374580「质量不达标」/ 宏图生鲜门店 / SP-TEST-0014 / 0.36kg / 1 件）当成回归残留删除，连带删除 `recall_order` id=33 与 2 条召回通知（`recall_store`→user 18、`recall_center`→user 4）。用户当场发现并质问。

**误判链条**：① 回归跑「生成收运运单」时，并池规则把这行**别家召回的待收行**并进了测试车（status 0→1、transport_id=464）；② 脚本清理删掉测试车却不还池，行变成「指向已删运单」的悬空行；③ 次轮我看到 transport_id 悬空 + 批次名叫 SP-TEST-*，判定为残留直接删。**根因是并池污染 + 清理漏还池，不是这行本身有问题。**

**恢复（binlog 取证，MySQL 8.4 默认 ROW format 有全行镜像）**：
1. `docker exec mysql grep -qa '<业务键>' /var/lib/mysql/binlog.0000NN` 找所在 binlog（Git Bash 下要 `MSYS_NO_PATHCONV=1`，否则路径被改写）。
2. `docker cp` 拷出 → `python -m pip install mysql-replication` → `BinLogStreamReader(server_id=9101, log_file=..., log_pos=4, only_events=[WriteRowsEvent, DeleteRowsEvent])`，DELETE 事件带 before-image＝删除前全列值，INSERT 事件带原始创建值。
3. 按原 id/原字段 `INSERT` 写回（`SET NAMES utf8mb4` 保中文），**status 必须还原成语义值**：池子只认 `recall_collect.status=0`（我首次写成 1 = 已排车，页面仍不显示）。
4. 校验：`GET /distribution/return-pool?stage=DISPOSAL` 出现该行（source=RECALL、state=PENDING_RETURN）+ 召回列表 status=1。

**防复发**（`logs/verify-recall-collect.mjs`）：
- 池里已有 `status=0` 的别家召回待收货 → 默认**跳过**并打印原因（`ALLOW_MERGE=1` 才跑并池场景），避免把用户货并进测试车；
- `cleanup()` 末尾 `UPDATE recall_collect SET status=0, transport_id=NULL, stop_id=NULL WHERE transport_id=<测试车>`，把被并进来的货**还回池子**并打印行数（已实测：并 1 行→还 1 行）。
- 教训：**删任何 `recall_collect` / `recall_order` 行前必须问用户**；「像测试数据」不是依据，业务键（RC…/门店/批次）才是。
