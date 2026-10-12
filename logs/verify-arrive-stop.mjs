/**
 * 站点级到达（ARRIVE 通道）回归 —— 一车多站时「到达」按站点记，不按运单记。
 *
 *   口径：
 *     · **到达的粒度是「卸货门店」，不是「批次」**：一车装 12 批货送 3 家店就是 3 次到达，
 *       同一家店的多批货只到一次。判据是卸货门店数，与车厢里装了几批货无关。
 *     · 到达是**位置事实**（车到了这个卸货点），与发车一样适合设备化。设备只报现场事实
 *       （哪台车、司机刷的是哪张卡、什么时刻），运单按车牌**倒查**、站点按顺序**推导** ——
 *       设备无权挑站，更无权决定货卸在哪。
 *     · **报文里没有「卸货门店」（2026-10-11 去掉）**：车载终端不是门店收货区的固定设备，
 *       读得到车牌/卡号/时刻/GPS，读不到门店编码（门店是 ERP/OMS 的主数据，不下发到车上）。
 *       而这件事本来也不该由设备回答：车牌把运单锁到唯一一张「未结束」的单子，
 *       站点清单由装车扫码顺序决定 ⇒「当前第一个还没到达的站」在服务端**唯一确定**，
 *       所以报文报不了站、也报不错站。「走错店」「跳站」两条拦截随之退场 ——
 *       拦截走错店的正解是定位（GPS / 电子围栏），不是让司机在驾驶室屏上敲门店编码。
 *     · **每刷一次卡推进一站**（报文不报站的直接推论）：好处是设备挑不了站；
 *       代价是误刷/重刷会把下一站标成已到达，唯一的兜底是「所有站点都已到达」之后无处可记。
 *     · **到达必须由司机本人刷卡确认**（与发车是同一台车载终端）：卡在司机手上、由机器读出，
 *       所以「谁把货送到这一站」第一次有了责任人 —— 没刷卡一律转人工，不拿车牌或人名顶替。
 *       确认人落在**站点**上（arriveDriverName / arriveDriverSource），不挤在运单主表那一个字段里。
 *     · 运单状态由站点汇总推导：全部到达 → 待签收(3)；全部签收 → 已签收(4)；
 *       只到一站时运单仍停在运输中(2)，不能提前进「待签收」。
 *     · 签收的前置下沉到站点：**该站已到达**才能签（车到了第 1 站不能给第 3 站签收）。
 *     · 司机身份的唯一来源是**上车刷卡**（装车口没有读卡器，装车时司机还没上车）：
 *       所以本脚本建单时不报司机，到发车那一步才由刷卡把司机与「本人确认」写进运单。
 *
 *   红线（本脚本逐条验）：
 *     ① 车牌名下没有在途的运单 → 转人工（还没发车就到了，或上错车）
 *     ② 没有司机卡号 / 卡换不出人（没备案、司机已停用）→ 转人工
 *        （现场刷了卡，但系统这边缺配置，得有人去补 —— 司机重刷也过不去）
 *     ③ **刷卡人 ≠ 运单安排的司机 → 直接「已拒绝」**（2026-10-11 收紧）：
 *        现场刷错卡/上错车是司机重刷就能自愈的事，不必占人工队列（人工对这条的出路也只有
 *        「拒绝」——放行等于承认刷错卡的人确认了到达）；回执 retryable=false 也正好告诉
 *        车载终端「这条别重推、请重新刷卡」
 *     ④ 站点都到齐了还刷 → 转人工（无处可记）
 *   ①②④ 一律 `manual` 而不是 `rejected`：那是「条件没到位」而非「数据不成立」，
 *   判成拒绝会让外部系统删单。（另有「站点台账为空」也转人工。）
 *
 *   断言分组：
 *     A. 准备：待发运批次 / ≥3 家门店 / 三张计划（三家门店各 1 批）
 *     B. 装车口一次扫三批 → 建单（专用车牌 京A·T0002；站点 1/2/3 = S1/S2/S3）
 *     B2. 发车：装车口不采集司机 → 人工发车被拦；**司机上车刷卡**才把责任落到人并推进为运输中。
 *         这一刷用的就是 ONBOARD_DEVICE —— 与下面逐站到达是**同一台车载终端**（一台设备两个通道）
 *     C. 结构性校验：缺车牌 → 拒绝（现在唯一可能缺的必读项）
 *     C2. 合并的边界：同一台车载终端能报发车 + 到站两个通道，但报别组的通道要被挡下
 *     D. 红线①：车牌名下没有未结束的运单 → 转人工
 *     D2. 身份三条分两档：没刷卡 / 卡换不出人 → 转人工；**刷别人的卡 → 已拒绝**
 *     E. 逐站到达：报文只报车牌+卡号+时刻，**哪一站由服务端推**；刷一次推一站、
 *        运单状态由站点汇总、到达时刻与**确认人**落库；到齐后再刷 → 红线④转人工
 *     F. 到齐后签收的前置与逐站签收、删一条签收 → 运单退回「待签收」
 *     G. 收尾：删签收 + 删运单 + 取消计划 → 三个批次回到「待发运」
 *
 *   运行：node logs/verify-arrive-stop.mjs
 *   产生的计划/运单/暂存 ID 打印在末尾，由 bash 侧做落库核验与清理。
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

let pass = 0
let fail = 0

function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`  [PASS] ${name}`)
  } else {
    fail++
    console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`)
  }
}

async function api(path, { method = 'GET', token, body, headers = {} } = {}) {
  const h = { 'Content-Type': 'application/json', ...headers }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers: h,
    body: body ? JSON.stringify(body) : undefined,
  })
  return res.json().catch(() => null)
}

const brief = (r) => JSON.stringify(r ?? {}).slice(0, 320)
const text = (r) => JSON.stringify(r ?? {})

const pad = (n) => String(n).padStart(2, '0')
const today = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }

/** 每次上报给一个不同的到达时刻：同设备 + 同通道 + 同一份报文会被判重复上报 */
let tickSeq = 0
function nextTime() {
  tickSeq += 1
  const d = new Date(Date.now() - tickSeq * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

/**
 * 通用**车载终端**：一台设备承担**两个通道** —— 发车确认（司机上车刷卡）与到站刷卡确认。
 * <p>
 * 2026-10-11 合并前它们是两台设备（到站终端原本设计装在门店收货区），现在系统侧由
 * IngestChannel.deviceGroup() 把两个通道归为「同一台终端」，接入层允许同一台设备互报。
 * 所以本脚本用**同一个设备号**走完发车与逐站到达，顺手把「合并」这件事也验了。
 * 台账里它不绑车牌（车牌以报文里的 vehicleNo 为准），因此能配合专用回归车牌 京A·T0002。
 */
const ONBOARD_DEVICE = 'ARRIVE-001'
const LOAD_DEVICE = 'LOADER-001'
const ST_MANUAL = 1
const ST_ACCEPTED = 2
const ST_REJECTED = 3
/** 运单安排的那位司机的卡（B2 里司机刷卡发车用的就是它）——到达也要用它刷，否则判「上错车」 */
const CARD = '8801'
/** 另一名司机的卡（李四）：用来验「现场刷卡的人 ≠ 运单安排的人」 */
const OTHER_CARD = '8802'
/**
 * 专用车牌：库里 京A·12345 名下有界面演示留下的待发车/运输中运单，
 * 「唯一命中」构不出来（而唯一命中正是本通道要验的前提）。用一张库里没人用的车牌。
 */
const PLATE = '京A·T0002'
/** 一张完全没有运单的车牌，用来验「没有在途的运单」这条红线 */
const EMPTY_PLATE = '京A·T9999'

const planIds = []
const transportIds = []
const stagingIds = []
const receiptIds = []

/**
 * 演示接口的默认业务键是 `"DEMO-" + 毫秒`（为了能反复触发）。脚本连着几次上报若落在**同一毫秒**，
 * 后一次会被按「同设备 + 同 bizKey」判成重复、直接回既有结论 —— 那次上报根本不会产生新暂存行，
 * 于是「本次暂存行 N 条」这类计数断言会偶发地少一条。所以这里每次都显式给一个唯一业务键，
 * 让断言确定化（这也正是外部系统的正确做法：业务键由上报方保证唯一）。
 */
let bizSeq = 0
function nextBizKey(tag) {
  bizSeq += 1
  return `${tag}-${Date.now()}-${bizSeq}`
}

async function arriveReport(over) {
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    // channel 显式传：这台车载终端同时承担两个通道，报文得说清这次是哪个动作
    body: {
      deviceNo: ONBOARD_DEVICE, channel: 'ARRIVE', bizKey: nextBizKey('ARRIVE'),
      data: { arriveTime: nextTime(), ...over },
    },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}

async function loadReport(over) {
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    body: { deviceNo: LOAD_DEVICE, bizKey: nextBizKey('LOAD'), data: { loadTime: nextTime(), ...over } },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}

/** 同一台车载终端上报发车（司机上车刷卡那一刻）—— 与到达共用一个设备号，只有 channel 不同 */
async function departReport(over) {
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    body: {
      deviceNo: ONBOARD_DEVICE, channel: 'DEPART', bizKey: nextBizKey('DEPART'),
      data: { departTime: nextTime(), ...over },
    },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}

const statusOf = (res) => res?.data?.status
const targetIdOf = (res) => res?.data?.targetId

console.log('\n=== 站点级到达（ARRIVE 通道）回归 ===')

// ── A. 准备 ──
// 防御：上一次跑崩了可能把专用车牌的车留在库里，那会让「唯一命中」不成立
const prePlate = (await api(`/distribution/transports?keyword=${encodeURIComponent(PLATE)}&pageNum=1&pageSize=20`, { token }))
const preRows = prePlate?.data?.records || []
check('★ 前置：专用车牌名下没有残留运单（上次跑崩会留车，导致「唯一命中」构不出来）',
  preRows.length === 0, `残留=${preRows.map((r) => `${r.transportNo}/${r.status}`).join(',')}`)

const pendingRes = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingRows = pendingRes?.data?.records || []
check('★ 「待发运」批次接口可读，且至少 3 个批次', pendingRows.length >= 3, `条数=${pendingRows.length}`)
if (pendingRows.length < 3) { console.log('待发运批次不足，用例无法构造，退出'); process.exit(1) }
const [b1, b2, b3] = pendingRows
console.log(`  三个待发货批次：${[b1, b2, b3].map((b) => b.batchNo).join(' / ')}`)

const treeRes = await api('/system/orgs/tree', { token })
const stores = []
const walk = (nodes) => {
  if (!Array.isArray(nodes)) return
  nodes.forEach((n) => {
    if (n.type === 'retail') stores.push({ id: n.id, name: n.name })
    if (n.children?.length) walk(n.children)
  })
}
walk(treeRes?.data || [])
check('★ 机构树里至少 3 家门店（一车三站要有三个目的地）', stores.length >= 3, `门店数=${stores.length}`)
if (stores.length < 3) { console.log('门店不足 3 家，用例无法构造，退出'); process.exit(1) }
const [s1, s2, s3] = stores
// 2026-10-11 起，ARRIVE 报文里**没有门店**了：车载终端读不到门店主数据，
// 「到的是第几站」由服务端按站点顺序推。所以这里也没有「走错店」用例 ——
// 它随门店字段一起退场，拦截走错店的正解是定位（GPS / 电子围栏）。
console.log(`  三家门店：${s1.name} / ${s2.name} / ${s3.name}（只用于建计划与站点清单，不进 ARRIVE 报文）`)

async function createPlan(splitBatchIds, store, remark) {
  const res = await api('/distribution/delivery-plans', {
    method: 'POST', token,
    body: {
      planDate: today(), storeId: store.id, storeName: store.name, splitBatchIds,
      vehicleNo: null, vehicleType: null, refrigeration: null,
      driverName: null, driverPhone: null,
      origin: null, plannedDepart: null, plannedArrive: null, remark,
    },
  })
  const plan = res?.data
  if (plan?.id) planIds.push(plan.id)
  return plan
}

const p1 = await createPlan([b1.id], s1, '到达回归-P1')
const p2 = await createPlan([b2.id], s2, '到达回归-P2')
const p3 = await createPlan([b3.id], s3, '到达回归-P3')
check('★ 准备：三张计划（三家门店各 1 批货）', p1?.status === 1 && p2?.status === 1 && p3?.status === 1,
  `p1=${p1?.planNo}/${p1?.status} p2=${p2?.planNo}/${p2?.status} p3=${p3?.planNo}/${p3?.status}`)
if (!p1?.id || !p2?.id || !p3?.id) { console.log('计划建不出来，退出'); process.exit(1) }

// ── B. 装车口建单（扫码顺序 = 站点顺序 = S1 → S2 → S3） ──
//    刻意**不报司机**：装车口没有读卡器（现场只在上车那一刻刷一次卡），装车时司机还没上车，
//    所以「司机为空」才是这里的常态 —— 建单照常，责任闸门在发车那一步（见 B2）。
const loaded = await loadReport({
  vehicleNo: PLATE,
  batchNos: [b1.batchNo, b2.batchNo, b3.batchNo],
})
check('★ 装车口扫三批 → 建出一个三站车次（已入账）', statusOf(loaded) === ST_ACCEPTED, brief(loaded))
const tid = targetIdOf(loaded)
if (tid) transportIds.push(tid)
if (!tid) { console.log('未能建出运单，后续用例无法继续，退出'); process.exit(1) }

let stops = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
check('★ 站点台账：3 站，顺序 = 扫码顺序（S1 → S2 → S3）',
  stops.length === 3 && stops[0]?.storeName === s1.name
  && stops[1]?.storeName === s2.name && stops[2]?.storeName === s3.name,
  `站点=${stops.map((s) => `${s.stopNo}.${s.storeName}`).join(' → ')}`)
check('★ 刚建单时三站都是「未到达」（车还没出发）',
  stops.every((s) => s.arrived === false && s.arriveTime == null),
  `arrived=${stops.map((s) => s.arrived).join(',')}`)

// ── B2. 发车：没有司机不许发车，上车刷卡才是司机身份的唯一来源 ──
//     装车口不采集司机，所以这张单现在没有司机 —— 空着能建单、不能发车：
//     货要走了，责任必须落到具体的人（口径见 distribution-service 的 depart 与 DEPART 通道）。
const noDriver = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 装车口不采集司机：运单司机为空、来源也留空（不假装「人工录入」）',
  !noDriver?.driverName && !noDriver?.driverSource,
  `driverName=${noDriver?.driverName} driverSource=${noDriver?.driverSource}`)

const departNoDriver = await api(`/distribution/transports/${tid}/depart`, { method: 'PUT', token })
check('★ 没有司机就发车 → 被拦（不能让货走在「没有责任人」的状态下）',
  text(departNoDriver).includes('还没有司机'), brief(departNoDriver))
check('★ 被拦后运单仍停在「待发车」，没有偷偷发出去',
  (await api(`/distribution/transports/${tid}`, { token }))?.data?.status === 1)

// 司机上车刷卡：这一刷同时写下发车时刻、司机与「本人确认」——全系统唯一能证明「谁在开」的动作
const swiped = await departReport({ vehicleNo: PLATE, cardNo: '8801' })
check('★ 司机上车刷卡 → 发车成功（卡号换成持卡人，算本人确认）',
  statusOf(swiped) === ST_ACCEPTED, brief(swiped))

const afterDepart = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 发车后运单 = 运输中(2)，司机由刷卡写进运单（张师傅 / 本人确认）',
  afterDepart?.status === 2 && afterDepart?.driverName === '张师傅'
  && afterDepart?.driverSource === 'CARD' && afterDepart?.driverConfirmed === 1,
  `status=${afterDepart?.status} driverName=${afterDepart?.driverName} `
  + `driverSource=${afterDepart?.driverSource} confirmed=${afterDepart?.driverConfirmed}`)
check('★ 发车后站点仍全部未到达（发车与到达是两件事）',
  (await api(`/distribution/transports/${tid}/stops`, { token }))?.data?.[0]?.arrived === false)

// ── C. 结构性校验（报文本身不完整 → 拒绝，不是「条件没到位」） ──
// 现在唯一可能「缺」的必读项就是车牌：门店已经不进报文（车载终端读不到），
// 卡号缺了走的是「转人工」而不是「拒绝」（那是「人没刷」不是「数据不成立」）。
const noPlate = await arriveReport({ cardNo: CARD })
check('★ 缺车牌 → 拒绝（连是哪一趟车都不知道，猜不得）',
  statusOf(noPlate) === ST_REJECTED, brief(noPlate))

// ── C2. 合并的边界（一台终端两个通道，但只放宽到「同组」为止） ──
// 车载终端把「发车确认」与「到站刷卡确认」归为同一台设备，所以上面同一个设备号既报发车
// 也报到达。但放宽只到同组：拿它去报温度就是接错点位，必须当场挡下 ——
// 不然「一台设备只报自己那条通道」这条纪律就没了。
const crossChannel = await api('/ingest/demo/report', {
  method: 'POST', token,
  body: {
    deviceNo: ONBOARD_DEVICE, channel: 'TEMPERATURE', bizKey: nextBizKey('CROSS'),
    data: { temperature: 3.3, transportNo: PLATE },
  },
})
check('★ 车载终端去报别组的通道（温度）被挡下：放宽只到「同一台终端的两个通道」',
  crossChannel?.code !== 200 && !crossChannel?.data?.stagingId, brief(crossChannel))

// ── D. 不猜的红线（认哪一趟） ──
// ① 车牌名下没有「未结束」的在途运单
const noRun = await arriveReport({ vehicleNo: EMPTY_PLATE, cardNo: CARD })
check('★ 红线①：该车牌名下没有在途的运单 → 转人工（不能替它认一趟没发的车）',
  statusOf(noRun) === ST_MANUAL && text(noRun).includes('没有在途'), brief(noRun))

// ── D2. 司机刷卡确认到达（本通道的身份口径，三条分两档判） ──
// 到达必须由司机本人刷卡确认（与发车是同一台车载终端）：卡在司机手上，这一刷才是责任人。
// 「缺卡 / 卡换不出人」是系统侧配置待补 → 转人工；「刷了别人的卡」是现场操作失误 → 直接拒绝。
const noCard = await arriveReport({ vehicleNo: PLATE })
check('★ 身份②：没有司机卡号 → 转人工（到达必须由本人刷卡确认，不拿车牌或人名顶替）',
  statusOf(noCard) === ST_MANUAL && text(noCard).includes('刷卡'), brief(noCard))

const badCard = await arriveReport({ vehicleNo: PLATE, cardNo: '0000' })
check('★ 身份②：卡号没登记在司机备案里 → 转人工（备案待补，司机重刷也过不去）',
  statusOf(badCard) === ST_MANUAL && text(badCard).includes('没有登记'), brief(badCard))

const otherCard = await arriveReport({ vehicleNo: PLATE, cardNo: OTHER_CARD })
check('★ 身份③：现场刷的是别人的卡（≠ 运单安排的司机）→ 直接拒绝（重刷即可自愈，不占人工队列）',
  statusOf(otherCard) === ST_REJECTED && text(otherCard).includes('不是运单'), brief(otherCard))

const stopsAfterNoCard = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
check('★ 没刷卡 / 刷错卡那几次都没有被偷偷记下（站点仍全部未到达）',
  stopsAfterNoCard.every((s) => s.arrived === false), `arrived=${stopsAfterNoCard.map((s) => s.arrived).join(',')}`)

// ── E. 逐站到达（报文只报车牌 + 卡号 + 时刻，哪一站由服务端推） ──
const a1 = await arriveReport({ vehicleNo: PLATE, cardNo: CARD })
check('★ 第 1 站到达（入账）', statusOf(a1) === ST_ACCEPTED, brief(a1))
// 回执**只留事实**，不带业务附言（「第 1 站」「后面还有站」这类话只落在业务表与内部接口上，
// 由前端展示）。所以这里断言的是「写对了地方」——目标表是站点、记录号指得准。
check('★ 回执写的是「站点」而不是「运单」（目标表 transport_stop + 记录号非空）',
  a1?.data?.targetTable === 'transport_stop' && a1?.data?.targetId != null, brief(a1))

let t = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 只到 1/3 站 → 运单仍停在「运输中」(2)：不能提前进「待签收」',
  t?.status === 2, `status=${t?.status}`)

stops = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
check('★ 报文里没有门店，系统照样把第 1 站记成「已到达」—— 哪一站由服务端按站点顺序推',
  stops[0]?.arrived === true && stops[1]?.arrived === false && stops[2]?.arrived === false,
  `arrived=${stops.map((s) => s.arrived).join(',')}`)
check('★ 站点台账：第 1 站记了到达时刻与来源(DEVICE)',
  !!stops[0]?.arriveTime && stops[0]?.arriveSource === 'DEVICE',
  `time=${stops[0]?.arriveTime} source=${stops[0]?.arriveSource}`)
check('★ 到达上报编号落到站点上（车载终端 ARRIVE-001，追责时能回溯是哪台设备确认的）',
  stops[0]?.arriveSourceRef === ONBOARD_DEVICE, `arriveSourceRef=${stops[0]?.arriveSourceRef}`)
// 确认人落在**站点**上：司机刷卡换出的人（本人确认）——不挤在运单主表那一个字段里，
// 一车多站时每一站都可以不是同一个人。
check('★ 到达确认人落到站点上（第 1 站 = 张师傅 / 刷卡确认）',
  stops[0]?.arriveDriverName === '张师傅' && stops[0]?.arriveDriverSource === 'CARD',
  `driver=${stops[0]?.arriveDriverName} source=${stops[0]?.arriveDriverSource}`)

// 报文里没有「第几站」，所以**每刷一次卡就往下推进一站**（这是设计的直接推论）：
// 好处是设备挑不了站、也报不错站；代价是误刷/重刷会把下一站标成已到达 ——
// 唯一的兜底是「所有站点都已到达」之后无处可记（见下面的 over 用例）。
const tap2 = await arriveReport({ vehicleNo: PLATE, cardNo: CARD })
check('★ 再刷一次卡 → 推进到第 2 站（报文不报站，刷一次推一站）',
  statusOf(tap2) === ST_ACCEPTED, brief(tap2))
stops = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
check('★ 第 2 站确实被推进了（顺序推进，不挑站）',
  stops[0]?.arrived === true && stops[1]?.arrived === true && stops[2]?.arrived === false,
  `arrived=${stops.map((s) => s.arrived).join(',')}`)
t = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 2/3 站到达 → 仍是「运输中」(2)', t?.status === 2, `status=${t?.status}`)

const tap3 = await arriveReport({ vehicleNo: PLATE, cardNo: CARD })
check('★ 第 3 站到达（入账）', statusOf(tap3) === ST_ACCEPTED, brief(tap3))
t = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 三站全部到达 → 运单转「待签收」(3)', t?.status === 3, `status=${t?.status}`)
check('★ 运单主表的 arrive_time 同步为末站到达时刻（兼容展示；真相在站点台账）',
  !!t?.arriveTime, `arriveTime=${t?.arriveTime}`)

// 站点都到齐了还刷 —— 这是「报文里没有门店」之后**唯一**能挡住多刷的地方
const over = await arriveReport({ vehicleNo: PLATE, cardNo: CARD })
check('★ 红线④：站点都到齐了还报到达 → 转人工（无处可记，不重复记也不报错拒绝）',
  statusOf(over) === ST_MANUAL && text(over).includes('都已到达'), brief(over))

// ── F. 签收（前置 = 该站已到达）与删签收回退 ──
async function sign(stop) {
  const res = await api('/distribution/receipts', {
    method: 'POST', token,
    body: {
      transportId: tid, storeId: stop.storeId, storeName: stop.storeName,
      receiver: `${stop.storeName}收货员`, qtyCheck: 1, tempCheck: 1, tempValue: -15, packageIntact: 1,
    },
  })
  const id = res?.data?.id
  if (id) receiptIds.push(id)
  return res
}

stops = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
const r1 = await sign(stops[0])
check('★ 签收第 1 站（已到达，允许）', !!r1?.data?.id, brief(r1))
t = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 只签 1/3 站 → 仍是「待签收」(3)：剩两家的货权还没转移',
  t?.status === 3, `status=${t?.status}`)

const r2 = await sign(stops[1])
const r3 = await sign(stops[2])
check('★ 三站签满', !!r2?.data?.id && !!r3?.data?.id, `${brief(r2)} / ${brief(r3)}`)
t = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 三站全部签完 → 运单转「已签收」(4)', t?.status === 4, `status=${t?.status}`)

stops = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
check('★ 站点台账三站都已签收并挂上签收凭证',
  stops.every((s) => s.received === true && s.receiptId != null && !!s.receiver),
  `received=${stops.map((s) => s.received).join(',')} receiptId=${stops.map((s) => s.receiptId).join(',')}`)

// 删一条签收 → 该站退回「已到达」、运单退回「待签收」（签收凭证是货权转移的唯一凭据）
const delRes = await api(`/distribution/receipts/${r3.data.id}`, { method: 'DELETE', token })
check('★ 删掉第 3 站的签收记录', !!delRes && delRes.code === 200, brief(delRes))
t = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 删签收后运单退回「待签收」(3)，不是停在「已签收」',
  t?.status === 3, `status=${t?.status}`)
stops = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
check('★ 被删的那一站退回「已到达」并摘掉凭证，另外两站仍是「已签收」',
  stops[2]?.arrived === true && stops[2]?.received === false && stops[2]?.receiptId == null
  && stops[0]?.received === true && stops[1]?.received === true,
  `received=${stops.map((s) => s.received).join(',')} receiptId=${stops.map((s) => s.receiptId).join(',')}`)

// ── G. 收尾 ──
if (process.env.KEEP === '1') {
  console.log('\n  （KEEP=1：跳过收尾删除，交由 db 侧核验后清理）')
} else {
  // 顺序要紧：签收未删干净时运单删不掉（已生成签收记录的运单属于溯源链下游凭证）
  for (const id of receiptIds) await api(`/distribution/receipts/${id}`, { method: 'DELETE', token })
  for (const id of transportIds) await api(`/distribution/transports/${id}`, { method: 'DELETE', token })
  for (const id of planIds) {
    await api(`/distribution/delivery-plans/${id}/cancel`, { method: 'POST', token, body: { reason: '回归收尾' } })
  }
  const pendingFinal = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
  const finalIds = new Set((pendingFinal?.data?.records || []).map((r) => r.id))
  check('★ 收尾：删签收 + 删运单 + 取消计划后，三个批次全部回到「待发运」',
    [b1, b2, b3].every((b) => finalIds.has(b.id)),
    `回到待发运的：${[b1, b2, b3].filter((b) => finalIds.has(b.id)).map((b) => b.batchNo).join(',')}`)
}

console.log(`\n=== 站点级到达回归：${pass} 通过 / ${fail} 失败 ===`)
console.log(`# PLAN_IDS=${planIds.join(',')}`)
console.log(`# TRANSPORT_IDS=${transportIds.join(',')}`)
console.log(`# STAGING_IDS=${stagingIds.join(',')}`)
console.log(`# RECEIPT_IDS=${receiptIds.join(',')}`)
process.exit(fail === 0 ? 0 : 1)
