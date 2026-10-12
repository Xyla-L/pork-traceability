/**
 * 刷卡发车回归（第 16 条通道 DEPART：车载发车终端）。
 *
 *   口径：装车口已经能自动建单，但建完停在「待发车(1)」，发车还要人去点按钮 —— 自动化断在最后一步。
 *        DEPART 补的就是这一步：司机上车刷卡 → 系统按**车牌**倒查该车名下「待发车」的运单
 *        → 推进为「运输中(2)」并写现场发车时刻。
 *
 *   与 LOAD 同源的三条口径（本脚本逐条验证）：
 *     ① **设备只报「我（某车）发车了」**，运单号由系统倒查 —— 刷卡是「确认」不是「选择」；
 *        （车牌可以不报：设备台账的 vehicle_no 把终端与车绑死，结构化绑定，不靠设备名猜）
 *     ② **发车不产生业务判定**：哪台车发、发给谁，早就在运单里定死了；
 *     ③ **车牌只是找车的凭据、不是责任人**：司机必须由卡号换出具体的人，换不出人一律转人工。
 *
 *   本通道是**唯一一条「推进已有运单状态」而非「写入新事实」的通道**，
 *   所以失败语义与其它通道不同：**没有待发车运单 → 转人工，不是「拒绝」**。
 *   拒绝（retryable=false）的语义是「数据本身不成立、别重推」，外部系统据此删单 ——
 *   而「还没有待发车的运单」只是条件没到位，外部重推是合理的。
 *
 *   ⚠ 为什么用**测试专用车牌**（京A·T0001）而不是种子里的 京A·12345：
 *     「唯一命中一张待发车运单」这个前置条件依赖**该车牌名下到底有几张待发车运单**，
 *     而 京A·12345 是演示数据的常用车牌（库里就有一张界面演示建出的待发车运单）。
 *     用它做正向用例会既受演示数据干扰、又可能把演示运单发走。所以：
 *       · 正向/歧义用例走测试车牌，完全自持；
 *       · 需要「车牌反查常用司机」的用例才用 京A·12345（它在车辆备案里有常用主驾张师傅），
 *         且该用例在「司机落人」这步就转人工，**根本走不到倒查运单**，不碰演示数据；
 *       · 「车牌取自台账绑定」用 DEPART-003（绑定京A·12346，名下无运单），同样不碰。
 *
 *   用例顺序有讲究（后端判定顺序：司机落人 → 倒查待发车运单 → 与运单的人比对）：
 *     「司机不一致」与「正常发车」都要求该车牌名下**恰好只有一张**待发车运单，
 *     所以单张场景一路跑完再补第二第三张，最后才构造「多张待发车」。
 *
 *   运行：node logs/verify-depart.mjs
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
  const res = await fetch(BASE + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined })
  return res.json().catch(() => null)
}

const brief = (r) => JSON.stringify(r ?? {}).slice(0, 320)
const text = (r) => JSON.stringify(r ?? {})

const pad = (n) => String(n).padStart(2, '0')
const today = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }
/** 每次上报给一个递增的现场时刻：同设备 + 同通道 + 同一份报文会被判重复上报 */
let tickSeq = 0
function tick() {
  tickSeq += 1
  const d = new Date(Date.now() - tickSeq * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const LOAD_DEVICE = 'LOADER-001'
/** 车载终端 A：台账绑定 京A·12345（演示车牌，只用于「车牌反查常用司机」这一例） */
const DEPART_A = 'DEPART-001'
/** 车载终端 B：台账绑定 京A·12346（专用于验证「车牌取自台账绑定」，名下没有任何运单） */
const DEPART_B = 'DEPART-003'
/** 测试专用车牌：库里的运单与计划都不用它，保证「唯一命中」可构造 */
const TEST_PLATE = '京A·T0001'
/** 种子里的演示车牌：在车辆备案里有常用主驾「张师傅」，用于「车牌反查推定」用例 */
const SEED_PLATE = '京A·12345'
/** DEPART_B 台账绑定的车牌 */
const BOUND_PLATE = '京A·12346'
const ST_MANUAL = 1
const ST_ACCEPTED = 2

const planIds = []
const transportIds = []
const stagingIds = []

/** 装车口上报：先把「待发车」的运单造出来（**不带卡号** —— 装车口刷的是扫码枪，司机实名在发车时刷） */
async function loadReport(batchNo) {
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    body: {
      deviceNo: LOAD_DEVICE,
      data: { loadTime: tick(), vehicleNo: TEST_PLATE, driverName: '张师傅', batchNos: [batchNo] },
    },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}
/** 车载终端上报发车 */
async function departReport(over, deviceNo = DEPART_A) {
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    body: { deviceNo, data: { departTime: tick(), ...over } },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}
const statusOf = (res) => res?.data?.status
const targetIdOf = (res) => res?.data?.targetId

console.log('\n=== 刷卡发车回归（DEPART） ===')

// ── A. 准备 ──
const pendingRes = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingRows = pendingRes?.data?.records || []
check('★ 「待发运」批次接口可读，且至少 3 个批次（本脚本要占用 3 个）',
  pendingRows.length >= 3, `条数=${pendingRows.length}`)
if (pendingRows.length < 3) { console.log('待发运批次不足 3 条，用例无法构造，退出'); process.exit(1) }
const [b1, b2, b3] = pendingRows
console.log(`  三个批次：${[b1, b2, b3].map((b) => b.batchNo).join(' / ')}`)

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
check('机构树里能取到门店', stores.length > 0, `门店数=${stores.length}`)
if (!stores.length) process.exit(1)
const store = stores[0]

async function createPlan(batch, idx) {
  const res = await api('/distribution/delivery-plans', {
    method: 'POST', token,
    body: {
      planDate: today(), storeId: store.id, storeName: store.name, splitBatchIds: [batch.id],
      vehicleNo: TEST_PLATE, vehicleType: '冷藏车', refrigeration: '机械制冷',
      driverName: '张师傅', driverPhone: '13700000002',
      origin: null, plannedDepart: null, plannedArrive: null, remark: `发车回归-P${idx}`,
    },
  })
  const plan = res?.data
  if (plan?.id) planIds.push(plan.id)
  return plan
}

const plans = []
for (const [idx, b] of [b1, b2, b3].entries()) plans.push(await createPlan(b, idx + 1))
check('★ 准备：三张计划（测试车牌 京A·T0001 / 司机 张师傅 / 各 1 批货）',
  plans.every((p) => p?.id && p?.vehicleNo === TEST_PLATE), plans.map((p) => p?.planNo).join(','))

/**
 * 人工建一张同车牌的「待发车」运单。
 * <p>
 * 为什么需要它：装车口那条路 2026-10-10 起会把**同车牌**的货并进那张还没发车的运单
 * （同一趟车继续装），所以系统自己已经不再产出「同车牌多张待发车」这种局面；
 * 但**人工排车**、或窗口外滞留的老单照样会形成它 —— 发车端必须识别得出并交人工。
 */
async function buildPendingByHand(batch) {
  const res = await api('/distribution/transports', {
    method: 'POST', token,
    body: {
      splitBatchId: batch.id, vehicleNo: TEST_PLATE, vehicleType: '冷藏车', refrigeration: '机械制冷',
      driverName: '张师傅', driverPhone: '13700000002',
      origin: null, destination: store.name, plannedDepart: null, plannedArrive: null,
    },
  })
  const id = res?.data?.id
  if (id) transportIds.push(id)
  return id
}

/** 由装车口建出「待发车」运单 */
async function buildPendingTransport(batch) {
  const res = await loadReport(batch.batchNo)
  if (statusOf(res) !== ST_ACCEPTED) {
    check(`★ 准备：装车口为批次 ${batch.batchNo} 建出待发车运单`, false, brief(res))
    return null
  }
  const id = targetIdOf(res)
  if (id) transportIds.push(id)
  return id
}

const t1 = await buildPendingTransport(b1)
check('★ 准备：装车口建出待发车运单 T1', !!t1, `tid=${t1}`)
if (!t1) { console.log('待发车运单建不出来，退出'); process.exit(1) }
const t1Res = await api(`/distribution/transports/${t1}`, { token })
check('★ T1 初始状态 = 待发车(1)（这正是自动化断掉的那一步）',
  t1Res?.data?.status === 1, `status=${t1Res?.data?.status}`)
check('★ 装车口只报了司机姓名、没刷卡 → 记「人工录入、非本人确认」（发车刷卡才会升到本人确认）',
  t1Res?.data?.driverName === '张师傅' && t1Res?.data?.driverSource === 'MANUAL'
  && t1Res?.data?.driverConfirmed === 0,
  `driverSource=${t1Res?.data?.driverSource} driverConfirmed=${t1Res?.data?.driverConfirmed}`)

// ── B. 转人工四例（注意：这些**不是**「拒绝」——条件没到位可以重推） ──
// B1. 台账绑定京A·12346 的终端报「这台车发车」，该车牌名下没有任何运单
const noPending = await departReport({ vehicleNo: BOUND_PLATE, cardNo: '8801' }, DEPART_B)
check('★ 该车牌名下没有待发车运单 → 转人工（不是拒绝：条件没到位，外部重推是合理的）',
  statusOf(noPending) === ST_MANUAL && text(noPending).includes('没有待发车'), brief(noPending))

const badCard = await departReport({ vehicleNo: TEST_PLATE, cardNo: '9999' })
check('★ 卡号未登记在司机备案里 → 转人工（认不出持卡人就不放行）',
  statusOf(badCard) === ST_MANUAL && text(badCard).includes('没有登记'), brief(badCard))

// 用演示车牌（车辆备案里常用主驾是张师傅）验证「车牌反查只是推定」；
// 该用例在「司机落人」这步就转人工，走不到倒查运单，因此不会碰演示数据
const noDriver = await departReport({ vehicleNo: SEED_PLATE })
check('★ 没刷卡也没姓名 → 车牌反查出常用司机（推定）后仍转人工（车牌号不顶替责任人）',
  statusOf(noDriver) === ST_MANUAL && text(noDriver).includes('推定') && text(noDriver).includes('张师傅'),
  brief(noDriver))

// 现场刷的是李四的卡（8802），但 T1 运单安排的司机是张师傅 → 最可能是上错车
const wrongDriver = await departReport({ vehicleNo: TEST_PLATE, cardNo: '8802' })
check('★ 现场刷卡的人与运单安排的司机不一致 → 转人工核对是否上错车',
  statusOf(wrongDriver) === ST_MANUAL && text(wrongDriver).includes('不一致'), brief(wrongDriver))

const notDeparted = await api(`/distribution/transports/${t1}`, { token })
check('★ 上面四次都转人工，T1 仍停在「待发车」没被误发',
  notDeparted?.data?.status === 1, `status=${notDeparted?.data?.status}`)

// ── C. 正常发车 ──
const departAt = tick()
const okRes = await departReport({ vehicleNo: TEST_PLATE, cardNo: '8801', departTime: departAt })
check('★ 车牌唯一命中一张待发车运单 → 自动发车（已入账）', statusOf(okRes) === ST_ACCEPTED, brief(okRes))
check('★ 回执指向的就是 T1（运单号由系统倒查，设备从没报过运单号）',
  targetIdOf(okRes) === t1, `targetId=${targetIdOf(okRes)} 期望=${t1}`)

const after = await api(`/distribution/transports/${t1}`, { token })
check('★ 运单状态推进为「运输中」(2)', after?.data?.status === 2, `status=${after?.data?.status}`)
check('★ 写了现场发车时刻', !!after?.data?.departTime, `departTime=${after?.data?.departTime}`)
check('★ 司机来源提升为「刷卡 CARD」且「本人确认」——这件事只有刷卡能证明，追责看的就是它',
  after?.data?.driverSource === 'CARD' && after?.data?.driverConfirmed === 1,
  `driverSource=${after?.data?.driverSource} driverConfirmed=${after?.data?.driverConfirmed}`)

// ── D. 重复发车 → 转人工 ──
const again = await departReport({ vehicleNo: TEST_PLATE, cardNo: '8801' })
check('★ 该车已无待发车运单，再报一次 → 转人工（外系统重推不会造成二次发车）',
  statusOf(again) === ST_MANUAL && text(again).includes('没有待发车'), brief(again))

// ── E. 车牌可来自设备台账绑定（报文不带车牌也能定位到车） ──
//    用 DEPART-003（台账绑定京A·12346，名下无运单）：回执里出现的车牌必须是
//    台账绑定的那个 —— 这就证明了「车牌是结构化读出来的」，不是从设备名里猜的。
const boundPlate = await departReport({ vehicleNo: null, cardNo: '8801' }, DEPART_B)
check('★ 报文不带车牌 → 取设备台账绑定的车牌（回执里出现 京A·12346，不是设备名里的字符串）',
  statusOf(boundPlate) === ST_MANUAL && text(boundPlate).includes(BOUND_PLATE), brief(boundPlate))

// ── F. 同一车牌名下多张待发车运单 → 转人工 ──
const t2 = await buildPendingTransport(b2)
// 第二张走**人工建单**：装车口同车牌的货会被并进 t2（同一趟车继续装），
// 只有人工排车才造得出「同车牌两张待发车」—— 而那正是这一组要验的局面。
// 手工建单前必须先把 b3 的待发货计划取消掉：**计划占用中的批次不许手工抢发**
// （`createTransport` 的两道拦截之一），不释放就建不出 t3。
if (plans[2]?.id) {
  await api(`/distribution/delivery-plans/${plans[2].id}/cancel`, {
    method: 'POST', token, body: { reason: 'F 组：释放 b3 用于人工建单' },
  })
}
const t3 = await buildPendingByHand(b3)
check('★ 准备：再建两张同车牌的待发车运单（第二张人工建，装车口已经不会这么裂单了）',
  !!t2 && !!t3 && t2 !== t3, `t2=${t2} t3=${t3}`)
if (t2 && t3) {
  const ambiguous = await departReport({ vehicleNo: TEST_PLATE, cardNo: '8801' })
  check('★ 同一车牌名下有两张待发车运单 → 转人工（不敢猜发的是哪一趟）',
    statusOf(ambiguous) === ST_MANUAL && text(ambiguous).includes('同时有'), brief(ambiguous))
  const stillPending = await api(`/distribution/transports/${t2}`, { token })
  check('★ 说不清的那次没有擅自把运单发走（仍停在待发车）',
    stillPending?.data?.status === 1, `status=${stillPending?.data?.status}`)
}

// ── G. 收尾 ──
if (process.env.KEEP === '1') {
  console.log('\n  （KEEP=1：跳过收尾删除，交由 db 侧核验后清理）')
} else {
  for (const id of transportIds) await api(`/distribution/transports/${id}`, { method: 'DELETE', token })
  for (const id of planIds) {
    await api(`/distribution/delivery-plans/${id}/cancel`, { method: 'POST', token, body: { reason: '回归收尾' } })
  }
  const pendingFinal = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
  const finalIds = new Set((pendingFinal?.data?.records || []).map((r) => r.id))
  check('★ 收尾：删运单 + 取消计划后，三个批次全部回到「待发运」',
    [b1, b2, b3].every((b) => finalIds.has(b.id)),
    `回到待发运的：${[b1, b2, b3].filter((b) => finalIds.has(b.id)).map((b) => b.batchNo).join(',')}`)
}

console.log(`\n=== 刷卡发车回归：${pass} 通过 / ${fail} 失败 ===`)
console.log(`# PLAN_IDS=${planIds.join(',')}`)
console.log(`# TRANSPORT_IDS=${transportIds.join(',')}`)
console.log(`# STAGING_IDS=${stagingIds.join(',')}`)
process.exit(fail === 0 ? 0 : 1)
