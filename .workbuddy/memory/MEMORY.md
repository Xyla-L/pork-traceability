# 项目长期约定（猪肉全链路追溯系统）

> 骨架规则。**理由/阈值/配置键/回归基线/命令原文/举例 → `DETAIL.md`**（压缩前全文存档见其「## 十五」）；逐轮记录 → `YYYY-MM-DD.md`；可跑断言 → `logs/verify-*`。

## 一、前端展示
1. 不暴露表名/字段/枚举码，翻业务中文；技术信息进 `el-tooltip`，原始报文进 `el-collapse` 默认收起。
2. 通道中文名四处同步（现 18 条）：`IngestChannel.labelOf()`｜`ingest.ts` 的 `CHANNEL_LABELS`｜`IngestMonitor.vue` 三映射表｜`verify-channel-label.mjs` 的 `EXPECT`。漏加必红。
3. 侧边栏序：养殖建档→免疫→产地检疫→申请出栏→入场查验→瘦肉精→屠宰检验→胴体盖章→胴体批次→分割批次→冷链温度→门店签收→零售售出；**出栏审批不进 `TraceStage`**。
4. 回执只留事实（状态/去向/上报编号/未入账原因/原始回执折叠）；toast 只报状态；受理后按钮置灰（`demoReportLocked`），改填写项解锁。
5. **功能必须有恒定落点**（只在少数情况才渲染＝上了看不见）；单站也要有列表列+详情项。
6. `web-admin` 的 `npm run build`＝纯 `vite build`，**不做类型检查**。
7. **示例值必须落在成功路径上**：只按运单安排的那位司机取卡（`cardOf`）；查不到就留空+说明，**不许兜底成某个人的卡**；温度示例 -18~0。
8. **分割树**：胴体批次 → 每头白条一组 → 组内已出部位 + 未产出标准部位（伪节点虚线灰底）；**不展示**出肉率/对账、无徽标；标题/编号/重量唯一出口＝`splitTree.ts` 的 `splitNode*`。
9. **说明横幅全删（「为什么这么设计」），状态性提示保留（「下一步怎么办」）**；2026-10-12 起设备接入监控页（含「模拟设备上报」弹窗）的三处横幅也已删。

## 二、接入层
1. 报重两道：同设备+同 `bizKey` 回既有结论；兜底同设备+同通道+同一份报文。状态 0待处理/1待人工/2已入账；放行 3已拒绝/4已降采样。
2. `ingest_staging` 存原始报文不可变；业务层存归一化结果；两份都留。
3. **成功回执不带业务附言**（恒「已入账」）→ 标记类事实落业务表由前端展示；**别断言回执文案**。
4. **失败默认 `manual`**；仅「数据不成立/外部应删单」判 `rejected`；**物理读不到的报上来＝接错点位＝`rejected`**。
5. **来源管控两档**：`DEVICE_ONLY` 人工新增一律拒；`DEVICE_PREFERRED` 可人工补录（免票据+实名+上链）。**「兜底解锁」已整体删除**；表与 `/ingest/source-policy` 保留（被断言引用）。
6. **设备环节「编辑」入口已整条删除**，只留「作废」（实名+理由+上链）；出证/申请出栏/人工建批是新增，保留且免票据。
7. **分割快照绑定**：`workshop_snapshot.batch_no/job_id`（迁移 41）；SPLIT 一入账自动连拍（`utils/splitCamera.ts`，2 秒×3 张）；照片带 `batchNo` → 绑该批次最近一次作业（批次查不到＝编造绑定→`rejected`）；**查询必须带 `batchNo`**。
8. **盖章通道两把唯一锁**：`carcass_stamp` 的 `pig_id` 与 `carcass_no` 都唯一 → **一头白条只有一枚章**，示例值必须挑未盖章的白条、胴体编号必须每次新给（`CT-当天-HHmmss`；写死 `CT-当天-001` 会被按编号查重**静默回既有记录**，回执照样「已入账」）。同猪换号重报 → 转人工（查重要**含作废行**，作废只是 `void_flag=1` 行还在，漏判会撞唯一键冒成 500）。

## 三、设备 ≠ 人
1. 人名不得用设备名兜底；无人名→转人工（不拒绝）；force 放行写「未记录」。
2. 人名映射：TAG/VACCINE/RACTOPAMINE/SPLIT=`operator`；ENTRY=`inspector`；INSPECTION/STAMP=`veterinary`；RECEIPT=`receiver`；前端统一 `|| '未记录'`。

## 四、中文编码红线
1. 中文进 HTTP 头落库成 `?`（有损不可逆）→ 一律 `OperatorHeader.encode/decode`；新增中文透传头照办。
2. `SourceGuard` 在 `common-web` → **改 `common-web`/`common-core` 必须重建全部依赖服务（含 gateway、ingest）**。
3. **含中文的 SQL 必须 utf8mb4**（`SET NAMES utf8mb4;` + 客户端 `--default-character-set=utf8mb4`），脚本写成重跑自修复。

## 五、环节前置与判定器
1. **ENTRY 两条硬前提**（force 也绕不过）：出栏审批已通过 + 生猪状态 ∈{1在养,2已出栏}。序：审批→状态→pigId→幂等→落库；查不到/服务不可用→转人工；已驳回不复活。
2. 判定器唯一在 `slaughter-service/support/`（`RactopamineGuard`/`PreSlaughterGate`/`StampGuard`），禁止各写一份。
3. **瘦肉精**：批批检，阳性整批受控；「未检不得屠宰」按所属批次（报文 `batchNo` ∪ 入场查验批次 ∪ 检测记录 `batch_no`）判；只约束宰后类(2,3)；阻断一律 `rejected`；阳性照常入账留证。三关：`ingestInspection`/`ingestStamp`/`createBatch|updateBatch`。
4. **宰后口径**：`inspect_type` 2 与 3 同环节 → 一律 `IN (2,3)`；**盖章收紧**须有宰后类(2,3) 且 `result=1`。序：瘦肉精→宰前门槛→幂等→落库。

## 六、批次归集
1. **SPLIT**：`DEVICE_ONLY`，人工入口已删；一次上报＝一头白条整份产出 `items[]` → 作业号 `SV…` 展开 N 条（无 items 退化单条）；`meat_part` 是部位唯一来源，反查不到转人工。
2. **`SplitYieldCheck`（唯一口径）**：缺必出项/超限不通过**不影响入账**，只置 `check_status=1` 待复核。
3. 拆分树插 `SPLIT_JOB` 节点；`MISSING_PART_GROUP`/`MISSING_PART` 伪节点用后端 `key`；`collectPartCodes` 递归必带 `split_level`。
4. **胴体批次**：键＝屠宰场+屠宰日；只动当日同场 `source=DEVICE` 且 `status=1`，否则新建 `CB…`；边界系统算、设备只报单头白条；**不碰人工批次**。
5. **封批**：满 `max-pigs` 自动 `status=2`；人工提前封批（实名+上链）；批号当日全局排流水、**计数含已作废**。
6. **`carcass_batch_pig` 是权威**；`pig_ids`/`pig_count` 是展示快照；三者必相等；`JSON_ARRAY_APPEND` 必须 `CAST(#{pigId} AS JSON)`。
7. 写入前三道：瘦肉精→盖章→未被占用（`BatchGuard.validateAll`）；设备重推「已占用」→回既有批次；人工建批/封批实名+上链（key=`batchNo:action:pigId`）；已盖章 ≠ 入批。

## 七、冷链运单链
1. **计划**＝一目的地+一车次+N 待发批次；计划阶段车/司机可空，生成运单**只强制车牌**；号 `DP…`（含已取消不复用）。
2. **两条时间线分开命名**：计划侧只答「货有没有排进运单」＝1待发货/4部分排车/**2已排车**/3已取消（**不叫「已发运」**）；运单侧答「车走到哪」＝1待发车/2运输中/3待签收/4已签收。**改词要前后端+列注释一起改**。
3. **`transport_item`＝装载明细**；按 `store_id` 聚站点；主表 `destination` 降级首站、`stop_count` 记站数；`store_receipt` 幂等键 `uk_transport_store`。
4. **「待发运」两道排除**：未被 `transport_item` 引用 且 未被「待发货/部分排车」计划的未排车明细占用（`p.status IN (1,4) AND i.shipped=0`）；**不用标记位**；手工建单与 `loadPlanBatches` 同两道拦截。
5. **删运单连带**：按 `delivery_plan_item.transport_id` 精确退回 → 回算计划（**已取消不复活**）→ 删装载明细与站点台账 → 删运单。
6. **`LOAD`**（车牌相机+扫码枪，**无读卡器**）：唯一**不产生业务判定**的通道（现场事实倒查待发计划）；**扫码是确认不是选择**；**报 `cardNo`＝结构性拒绝**；超载硬判据、不自动凑满；**司机可留空**；不属任何计划/建单失败→转人工。
7. **并入既有待发车运单**：候选＝同车牌+`source=DEVICE`+`status=1`+`create_time` 在 `ingest.load.merge-window-minutes` 内；唯一命中→追加，0 张→新建，**>1 张或非 DEVICE→转人工**，窗口外不并；车/人**只补齐不覆盖**；回执 `mergedIntoExisting` 不落库；**分界＝那张运单有没有发走**。
8. **现场值只补计划空位**（`resolveSiteValue`）：都有且不一致→转人工；**现场车牌与计划不一致不许设备改写计划**。
9. **`ingestLoad`/`ingestArrive` 刻意不加 `@Transactional`**（会把「转人工」吞成「系统内部异常」）；同形通道照办。
10. **分车**以 `delivery_plan_item.transport_id` 为准；主表 `transport_id` 只是「最近一次」，`transport_count` 记共排几车。
11. **`DriverSource`（唯一口径）**：`CARD`/`FACE`＝本人确认，其余＝非本人确认；**强度由来源推导，不收设备自报布尔值**；四主数据表（`vehicle`/`driver`/`driver_card`/`driver_vehicle`）是地基；**硬不变量：司机为空 ⇒ `driver_source=NULL`**；接入层不判司机。
12. **`DEPART`**＝唯一推进运单状态的通道兼**责任闸门**（车牌倒查 `status=1` 运单，0/多张→转人工；推 `IN_TRANSIT`+写发车时刻）；**没司机的运单两条路都拦**；**`CARD` 只产自 `DEPART`/`ARRIVE`，是同一台车载终端**（`deviceGroup()=ONBOARD` 允许同组互报）；`DEPART-001/003` 各绑真车牌**不能删**，`ARRIVE-001` **有意不绑车**（「缺车牌→拒绝」红线靠它成立）；加同组通道同步 `deviceGroup()` 与 `verify-arrive-stop`。
13. **`ARRIVE`**：**粒度＝卸货门店**；`transport_stop` 存行程状态（0未到/1已到/2已签收；唯一键 `(transport_id,stop_no)`）；**运单状态由站点汇总推导**（未发车不推导），签收前置下沉到站点；**报文只报车牌+卡号+到达时刻**，服务端按序推、每刷一次进一站，倒查认(2,3)；**须司机本人刷卡**，确认人记在站点（`arrive_driver_name/source`）；**红线**：无未结束运单/≥2 张、卡换不出人、站点已到齐→`manual`；**刷卡人 ≠ 运单安排的司机→「已拒绝」**（`retryable=false`，重刷自愈）；**发车侧同形比对保持转人工**；兜底＝页面「确认到达」（`MANUAL`）；单站也要有落点。
14. **温度打卡**：采样间隔是**探头属性**（`sample_interval_seconds`，系统不轮询），写入时固化到 `temperature_log`；**断档标在断档后第一条**；首条 `gap_seconds=NULL`/`is_gap=0`；补传为负不判断档；**空白 ≠ 正常**；`is_abnormal` 与 `is_gap` 独立；recordTime 取报文级 `reportTime`。
15. **人工「新建计划/新建运单」按钮已删**（运单只由 `LOAD` 或计划「生成运单」产生；弹窗与端点保留供回归）；发车文案「请先编辑运单补录司机」是**陈旧指引**，待拍板改「由司机在车载终端刷卡发车」。

## 八、订单转入 `ORDER`
1. 订单是计划来源之一（`plan_source`∈MANUAL/ORDER + `order_no`），落 `status=1`，**车/司机留空**；**不建订单表**、不收金额、不跨订单归并。
2. **绝不能判 `rejected`**（外部据此删单）→ 两侧一律 `manual`；**重推是常态，宁可多收一条待人工**。
3. **配货**：按部位找可发运批次 + `MeatPartResolver.resolve`（共用一份）；凑不齐转人工；批次不可拆→略超写 `remark`；门店校验 `OrgClient`（`type='retail'`）不符一律转人工。
4. **零/负重量必拦**（DTO + handler `validate` + service；嵌套列表要 `@Valid`）。
5. `operator` 写「订单转入（终端号）」**不写人名**；**列表展示的标识字段必须都能搜**。
6. **订单号每次唯一**（写死会让第二次演示命中幂等，看着像坏了）。

## 九、环境与纪律
1. 改后端后**必须 `docker compose build --no-cache <service>`**，在 `backend/` 执行。
2. **⚠️ 构建启动后再改的源码不会进镜像**：改完再启动构建；已开跑又改源码必须重建。
3. **环境会批量 `paused` 全部容器**：逐个 `docker unpause`；刚构建过新镜像必须 `up -d --force-recreate`。
4. **MyBatis-Plus 跳过 null 字段**：清空列用 `lambdaUpdate().set(Field,null)`；**新增可空列不给业务含义型默认值**。
5. **回归残留必清**（含回滚被改的既有行）；清场**只删自己造的**（按业务键，**不按 `device_no` 整表删**）；清完跑 `verify-reconcile.mjs`。
6. **新增「挂在运单下的子表」必须同步所有清理脚本**并加「**孤儿行＝0**」守卫。
7. **界面演示产生的数据算真实数据，不清**；基线偏高先分辨残留还是演示。
8. **滚动淘汰型写接口的回归不得借用生产台账主体**：自带一次性主体或只读降级。
9. **表名**：`db_breeding.pig_individual`（**不是 `pig`**）、`slaughter_apply`、`quarantine_certificate`、`entry_inspection`、`slaughter_inspection`、`carcass_stamp`；`ingest_staging`/`source_fallback_unlock` 在 `db_ingest`；用户表在 `db_common.sys_user`（无 `db_auth` 库）。
10. **DB 断言别重复表别名**；**新断言先跑一次再信**；**断言盯不变量不盯绝对条数**；**浏览器断言别依赖数据状态**，断恒定落点。
11. **演示上报必须显式传唯一 `bizKey`**；**`-db.sh` 才是回归正确入口**，**裸跑同名 mjs 必留残留**（清理顺序子表→父表）。
12. **浏览器自动化**：验证码是纯前端 canvas，`addInitScript` 固定 `Math.random=0` → 恒 `'AAAA'`；登录按钮 `button.login-btn`；`playwright-core` 在托管工作区 `C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/`（**不在**仓库 node_modules）；Edge 在 `C:/Program Files (x86)/Microsoft/Edge/Application/`；token 只在 pinia。
13. **前端改完 `npm run build` 即生效**（nginx 只读挂载 `web-admin/dist`）。
