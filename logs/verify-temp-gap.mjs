/**
 * 温度断档检测回归（温度探头定频打卡）。
 *
 *   口径：**「多久采一次」是探头的属性**，系统不轮询、也不假设（车会进隧道/地库停采）。
 *        所以额定间隔由探头声明、登记在设备台账 `ingest_device.sample_interval_seconds`，
 *        随每条报文带上，写进 `temperature_log.sample_interval_seconds` 留档
 *        （将来改设备属性不影响历史判断 —— 口径固化在写的那一刻）。
 *
 *   断档判定（业务侧 ingestTemperature）：
 *     · `gap_seconds` = 本条与**上一条**（按 recordTime 取最近的一条）的间隔；
 *     · 间隔 > 额定间隔 × 容差倍数（默认 3）⇒ `is_gap = 1`，标在**断档之后的第一条**上；
 *     · **补传的历史数据（时间戳早于上一条）不判断档**，否则整段补传会被标成一片红；
 *     · 首条没有上一条 ⇒ `gap_seconds = NULL`、`is_gap = 0`。
 *
 *   为什么非做不可：只判单点越界（is_abnormal）是不够的 —— 15:00 报一条、17:30 才报下一条，
 *   中间两个半小时是空白，而界面会显示「温度正常」。**空白比超标更危险**：
 *   超了至少看得见，空白是假装有数据。
 *
 *   断言分组：
 *     A. 准备：同一台车的两张运单（T1 发车后用于测温度、T2 留在待发车用于测「未发车不得采温度」）
 *     B. 硬前置：未发车运单报温度 → 转人工；温度值缺失 → 拒绝
 *     C. 断档检测：首条 / 正常间隔 / 超长间隔(断档) / 补传历史(不判)
 *     D. 落库事实：sample_interval_seconds 固化、gap_seconds 与 is_gap 与预期一一对应
 *     E. 越界温度仍照常入账并标异常（异常值永远保留，不做降采样）
 *     F. 收尾：删运单 + 取消计划
 *
 *   注意：本通道的 recordTime 取自**报文级 reportTime**（设备采集时刻），不是业务字段，
 *   所以调 /ingest/demo/report 时要设顶层的 reportTime，而不是 data 里的字段。
 *   运行：node logs/verify-temp-gap.mjs
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

const n2 = (n) => String(n).padStart(2, '0')
const today = () => { const d = new Date(); return `${d.getFullYear()}-${n2(d.getMonth() + 1)}-${n2(d.getDate())}` }
const fmt = (d) => `${d.getFullYear()}-${n2(d.getMonth() + 1)}-${n2(d.getDate())} `
  + `${n2(d.getHours())}:${n2(d.getMinutes())}:${n2(d.getSeconds())}`

/** 温度记录时刻的基准：往前留一分钟，避免与"现在"撞上 */
const BASE_AT = new Date(Date.now() - 60_000)
const at = (offsetSec) => fmt(new Date(BASE_AT.getTime() + offsetSec * 1000))

/** 每次上报一个递增的装车时刻：同设备 + 同通道 + 同一份报文会被判重复上报 */
let tickSeq = 0
const tick = () => fmt(new Date(Date.now() - (++tickSeq) * 1000))

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const LOAD_DEVICE = 'LOADER-001'
const TEMP_DEVICE = 'TEMP-001'
// ⚠ 用**测试专用车牌**：2026-10-10 起装车口会把同车牌的货并进那张还没发车的运单
//   （同一趟车继续装），而演示车牌 京A·12345 名下常年挂着待发车运单，用它会被并进去
const VEHICLE = '京A·T0005'
const ST_MANUAL = 1
const ST_ACCEPTED = 2
const ST_REJECTED = 3

const planIds = []
const transportIds = []
const stagingIds = []

/** 装车口上报：先把「待发车」的运单造出来 */
async function loadReport(batchNo) {
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    // 装车口不读卡（现场只在上车那一刻刷一次卡），所以这里不带卡号：
    // 司机由计划那侧带上（见上面的建计划）
    body: { deviceNo: LOAD_DEVICE, data: { loadTime: tick(), vehicleNo: VEHICLE, batchNos: [batchNo] } },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}
/**
 * 探头上报：转速与时刻分开传 —— 时刻走报文级 reportTime（设备采集时刻），
 * 温度走业务字段。
 */
async function tempReport(transportNo, { reportTime, temperature, tempRangeMin, tempRangeMax } = {}) {
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    body: {
      deviceNo: TEMP_DEVICE,
      reportTime,
      data: { transportNo, temperature, tempRangeMin, tempRangeMax },
    },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}
const statusOf = (res) => res?.data?.status
const targetIdOf = (res) => res?.data?.targetId

console.log('\n=== 温度断档检测回归 ===')

// ── A. 准备 ──
const pendingRes = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingRows = pendingRes?.data?.records || []
check('★ 「待发运」批次接口可读，且至少 2 个批次', pendingRows.length >= 2, `条数=${pendingRows.length}`)
if (pendingRows.length < 2) { console.log('待发运批次不足 2 条，用例无法构造，退出'); process.exit(1) }
const [b1, b2] = pendingRows

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
      vehicleNo: VEHICLE, vehicleType: '冷藏车', refrigeration: '机械制冷',
      driverName: '张师傅', driverPhone: '13700000002',
      origin: null, plannedDepart: null, plannedArrive: null, remark: `断档回归-P${idx}`,
    },
  })
  const plan = res?.data
  if (plan?.id) planIds.push(plan.id)
  return plan
}

const pA = await createPlan(b1, 1)
const pB = await createPlan(b2, 2)
check('★ 准备：两张计划（各 1 批货、同一台车）', !!pA?.id && !!pB?.id, `${pA?.planNo} / ${pB?.planNo}`)
if (!pA?.id || !pB?.id) { console.log('计划建不出来，退出'); process.exit(1) }

/** 建出「待发车」运单并返回 { id, transportNo } */
async function buildPending(batch) {
  const res = await loadReport(batch.batchNo)
  if (statusOf(res) !== ST_ACCEPTED) {
    check(`★ 准备：装车口为批次 ${batch.batchNo} 建出待发车运单`, false, brief(res))
    return null
  }
  const id = targetIdOf(res)
  if (id) transportIds.push(id)
  const d = await api(`/distribution/transports/${id}`, { token })
  return { id, transportNo: d?.data?.transportNo }
}

const t1 = await buildPending(b1)
if (!t1?.id) { console.log('T1 建不出来，退出'); process.exit(1) }

// T1 先发车 → 运输中：这是温度采集的前置状态。
// ⚠ 顺序在本轮（2026-10-10）被刻意调换过：装车口现在会把**同车牌**的货并进那张
//   「还没发车」的运单（同一趟车继续装），若先建 T2 再发车，T2 会被并进 T1 ——
//   脚本要的「一辆待发车 + 一辆运输中」就只剩一辆了（而且两者的 transportNo 相同，
//   还会让下面 notIntransit 与 r1 的报文变成同一份、被判重）。所以先让 T1 离开「待发车」。
await api(`/distribution/transports/${t1.id}/depart`, { method: 'PUT', token })
const t1After = await api(`/distribution/transports/${t1.id}`, { token })
check('★ T1 发车 → 运输中(2)（温度采集的前置状态）', t1After?.data?.status === 2, `status=${t1After?.data?.status}`)

// 同一台车的第二张单：T1 已经发走，所以这次不会被并进 T1
const t2 = await buildPending(b2)
check('★ 准备：同车两张运单（T1 运输中用于测温度、T2 仍待发车用于测「未发车不得采温度」）',
  !!t2?.id && t2.id !== t1.id, `T1=${t1?.transportNo} T2=${t2?.transportNo}`)
if (!t2?.id || t2.id === t1.id) { console.log('T2 建不出来（或被并进了 T1），退出'); process.exit(1) }

// ── B. 硬前置 ──
const notIntransit = await tempReport(t2.transportNo, { reportTime: at(-3900), temperature: -18 })
check('★ 未发车的运单报温度 → 转人工（温度只属于「运输中」的在途监控）',
  statusOf(notIntransit) === ST_MANUAL && text(notIntransit).includes('运输中'), brief(notIntransit))

const noValue = await tempReport(t1.transportNo, { reportTime: at(-3900) })
check('★ 温度值缺失 → 拒绝（脏数据不进冷链台账）',
  statusOf(noValue) === ST_REJECTED, brief(noValue))

// ── C. 断档检测（时间刻意排成：正常 → 正常 → 断档 → 补传） ──
//   设备台账里 TEMP-001 的额定间隔是 300 秒，容差倍数默认 3 ⇒ 超过 900 秒判断档。
const r1 = await tempReport(t1.transportNo, { reportTime: at(-3600), temperature: -18 })
check('★ 首条：无上一条可比 → 正常入账（不判断档）', statusOf(r1) === ST_ACCEPTED, brief(r1))

// 允许区间是 [-18, 0]：除刻意越界的那条（5℃）外，其余温度都必须落在区间内，
// 否则它们会被如实标成异常，把「越界恰好 1 条」的断言搅浑。
const r2 = await tempReport(t1.transportNo, { reportTime: at(-3300), temperature: -17.6 })
check('★ 距上一条 300 秒（= 额定间隔）→ 正常入账，不断档', statusOf(r2) === ST_ACCEPTED, brief(r2))

const r3 = await tempReport(t1.transportNo, { reportTime: at(-2100), temperature: -17.8 })
check('★ 距上一条 1200 秒（> 300×3）→ 入账并**标记断档**', statusOf(r3) === ST_ACCEPTED, brief(r3))
// 回执按「只留事实」口径：成功只报 状态 + 去向（表名 + 记录号），不带业务附言。
// 断档这个事实落在 temperature_log 台账上（D 组逐条核），现场在「运输监控」页看得到
// （断档次数 tag + 「距上一条」列），**不靠回执文案传达** —— 回执文案一改，测试和现场就一起瞎。
check('★ 入账回执给出去向（表名 + 记录号），便于回查台账；断档不在回执文案里',
  statusOf(r3) === ST_ACCEPTED && targetIdOf(r3) != null
    && String(text(r3)).includes('temperature_log'),
  brief(r3))

// 补传一条历史数据（时刻早于上一条）：**不判断档**，否则整段补传会被标成一片红
const r4 = await tempReport(t1.transportNo, { reportTime: at(-3000), temperature: -17.9 })
check('★ 补传的历史数据（时刻早于上一条）→ 入账但不判断档', statusOf(r4) === ST_ACCEPTED, brief(r4))

// ── D. 落库事实 ──
const logsRes = await api(`/distribution/transports/${t1.id}/temperature`, { token })
const logs = logsRes?.data || []
check('★ 温度记录 4 条（前两条失败的上报没留下脏数据）', logs.length === 4, `条数=${logs.length}`)
check('★ 每条都固化了写入当时的额定间隔 300 秒（改设备属性不影响历史判断）',
  logs.every((l) => Number(l.sampleIntervalSeconds) === 300),
  `intervals=${logs.map((l) => l.sampleIntervalSeconds).join(',')}`)

// 接口按 recordTime 升序返回 → 顺序是 r1, r2, r4(补传), r3(断档)
const byOrder = (i) => logs[i] || {}
check('★ 序号 0（首条）：gap_seconds 为空、is_gap=0',
  byOrder(0).gapSeconds == null && Number(byOrder(0).isGap) === 0,
  `gap=${byOrder(0).gapSeconds} isGap=${byOrder(0).isGap}`)
check('★ 序号 1：gap_seconds=300、is_gap=0（正好一个额定间隔，不算断档）',
  Number(byOrder(1).gapSeconds) === 300 && Number(byOrder(1).isGap) === 0,
  `gap=${byOrder(1).gapSeconds} isGap=${byOrder(1).isGap}`)
check('★ 序号 2（补传的历史数据）：gap_seconds 为负、is_gap=0（补传不判断档）',
  Number(byOrder(2).gapSeconds) < 0 && Number(byOrder(2).isGap) === 0,
  `gap=${byOrder(2).gapSeconds} isGap=${byOrder(2).isGap}`)
check('★ 序号 3（断档那条）：gap_seconds=1200、is_gap=1（标在断档之后的**第一条**上）',
  Number(byOrder(3).gapSeconds) === 1200 && Number(byOrder(3).isGap) === 1,
  `gap=${byOrder(3).gapSeconds} isGap=${byOrder(3).isGap}`)
check('★ 全串只有 1 条被判断档（没有把补传段误标成一片红）',
  logs.filter((l) => Number(l.isGap) === 1).length === 1,
  `断档条数=${logs.filter((l) => Number(l.isGap) === 1).length}`)
check('★ 采集设备如实记为探头编号（温度无人工参与，不写人名）',
  logs.every((l) => l.recorder === TEMP_DEVICE),
  `recorder=${logs.map((l) => l.recorder).join(',')}`)

// ── E. 越界温度照常入账并标异常 ──
const r5 = await tempReport(t1.transportNo, { reportTime: at(0), temperature: 5, tempRangeMin: -18, tempRangeMax: 0 })
check('★ 越界温度（5℃，超出 -18~0）→ 照常入账，异常值不做降采样',
  statusOf(r5) === ST_ACCEPTED, brief(r5))
const logs2 = (await api(`/distribution/transports/${t1.id}/temperature`, { token }))?.data || []
const abnormal = logs2.filter((l) => Number(l.isAbnormal) === 1)
// 用温度值定位那条记录，而不是靠 abnormal[0] —— 数组顺序一旦变，断言就指错人
const outlier = logs2.find((l) => Number(l.temperature) === 5)
check('★ 越界那条被标为异常（温度超标看得见）',
  abnormal.length === 1 && Number(outlier?.isAbnormal) === 1,
  `异常条数=${abnormal.length} 值=${abnormal.map((l) => l.temperature).join(',')}`)
check('★ 越界那条同时也是一次断档（距上一条 2100 秒 > 900）',
  Number(outlier?.isGap) === 1 && Number(outlier?.gapSeconds) === 2100,
  `isGap=${outlier?.isGap} gap=${outlier?.gapSeconds}`)
check('★ 越界值不做降采样，照常留档（异常记录一条都不能少）',
  logs2.length === 5, `条数=${logs2.length}`)

// ── F. 收尾 ──
if (process.env.KEEP === '1') {
  console.log('\n  （KEEP=1：跳过收尾删除，交由 db 侧核验后清理）')
} else {
  for (const id of transportIds) await api(`/distribution/transports/${id}`, { method: 'DELETE', token })
  for (const id of planIds) {
    await api(`/distribution/delivery-plans/${id}/cancel`, { method: 'POST', token, body: { reason: '回归收尾' } })
  }
  const pendingFinal = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
  const finalIds = new Set((pendingFinal?.data?.records || []).map((r) => r.id))
  check('★ 收尾：删运单 + 取消计划后，两个批次全部回到「待发运」',
    [b1, b2].every((b) => finalIds.has(b.id)),
    `回到待发运的：${[b1, b2].filter((b) => finalIds.has(b.id)).map((b) => b.batchNo).join(',')}`)
}

console.log(`\n=== 温度断档检测回归：${pass} 通过 / ${fail} 失败 ===`)
console.log(`# PLAN_IDS=${planIds.join(',')}`)
console.log(`# TRANSPORT_IDS=${transportIds.join(',')}`)
console.log(`# STAGING_IDS=${stagingIds.join(',')}`)
process.exit(fail === 0 ? 0 : 1)
