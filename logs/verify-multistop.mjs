/**
 * 多站车次（一车多目的地）回归 —— 装车口 LOAD 通道的「一车多店」形态。
 *
 *   口径：一辆冷藏车装满后顺路送三四家门店，所以**运单粒度是「一个车次」而不是「一次送货」**。
 *        但目的地不是规划出来的，是**扫出来的**：
 *          每一批货的门店归属由它所属的 `delivery_plan` 定死（人在计划里早就定了），
 *          扫码顺序就是站点顺序（按站装车时，先装的先卸）。
 *        装车口把「属于不同计划、但上了同一台车」的货并成一个车次 —— 一次都没猜目的地。
 *
 *   站点的存储口径（本脚本逐条核验）：
 *     · 清单不另建表：装载明细 `transport_item` 带 `store_id / store_name / seq`，
 *       按门店聚合就是站点清单；但**行程状态**（未到达 / 已到达 / 已签收 + 到达时刻）装在
 *       `transport_stop` 里 —— 装载明细回答的是「装了哪些货」，装不下「这一站到了没」；
 *     · 运单 `destination` 单站时是门店名（兼容存量），多站时是「途经 N 站：A、B」；
 *     · `stop_count` 是途经站点数快照，真正的权威是站点台账与装载明细；
 *     · 到达**按站**推进（一趟车途经 3 站 = 三次确认），运单状态由站点汇总推导：
 *       全部到达 → 待签收；全部签收 → 已签收；只到一站时运单仍停在「运输中」；
 *     · 签收的前置是**该站已到达**（车到了第 1 站，不能给第 3 站签收），
 *       签收按 **(运单, 门店)** 记账，同一站只能签一次，**所有站签完运单才算「已签收」**。
 *
 *   断言分组：
 *     A. 准备：待发运批次 / ≥3 家门店 / 三张不同门店各 1 批的计划
 *     B. 一次装车扫三批（**扫码顺序 ≠ 建计划顺序**）→ 建单成功
 *     C. 运单事实：destination「途经 3 站：…」、stop_count=3、顺序 = 扫码顺序
 *     D. 站点清单接口：3 站、seq 1/2/3 与批次归属一一对应、primary 落在第一站
 *     E. 三张计划都被推进为「已排车」，明细都指向本运单
 *     F. 一车多店仍守住的两条红线：车牌不一致的两张计划拼一车 → 转人工；
 *        混进不属于任何计划的批次 → 转人工
 *     G. 站点级到达：发车 → 运输中；「确认到达」一次只推一站；只到 1/3 站仍是「运输中」；
 *        未到达的站不能签（跨站抢签被拦）；三站到齐才转「待签收」
 *     H. 逐站签收：签 1 站仍是「待签收」；签满 3 站才转「已签收」；同一站重复签 → 报错
 *     I. 收尾：删运单（货物退回）+ 取消计划
 *
 *   运行：node logs/verify-multistop.mjs
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

/** 每次上报给一个不同的装车时刻：同设备 + 同通道 + 同一份报文会被判重复上报 */
let tickSeq = 0
function nextLoadTime() {
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
// ⚠ 用**测试专用车牌**，不用演示车牌 京A·12345：
//   2026-10-10 起装车口会把同车牌的货**并进那张还没发车的运单**（同一趟车继续装），
//   而 京A·12345 名下常年挂着界面演示建出的待发车运单 —— 用它做正向用例会被并进去。
const PLATE = '京A·T0004'
const ST_MANUAL = 1
const ST_ACCEPTED = 2

const planIds = []
const transportIds = []
const stagingIds = []
const receiptIds = []

async function loadReport(over) {
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    body: { deviceNo: LOAD_DEVICE, data: { loadTime: nextLoadTime(), ...over } },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}
const statusOf = (res) => res?.data?.status
const targetIdOf = (res) => res?.data?.targetId

console.log('\n=== 多站车次（一车多店）回归 ===')

// ── A. 准备 ──
const pendingRes = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingRows = pendingRes?.data?.records || []
check('★ 「待发运」批次接口可读，且至少 5 个批次（本脚本要占用 3 个）',
  pendingRows.length >= 5, `条数=${pendingRows.length}`)
if (pendingRows.length < 5) { console.log('待发运批次不足，用例无法构造，退出'); process.exit(1) }
const [b1, b2, b3, b4, b5] = pendingRows
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
check('★ 机构树里至少 3 家门店（一车三店要有三个目的地）', stores.length >= 3, `门店数=${stores.length}`)
if (stores.length < 3) { console.log('门店不足 3 家，用例无法构造，退出'); process.exit(1) }
const [s1, s2, s3] = stores
console.log(`  三家门店：${s1.name} / ${s2.name} / ${s3.name}`)

async function createPlan(splitBatchIds, store, { vehicleNo = null, driverName = null, remark = '多站车次回归' } = {}) {
  const res = await api('/distribution/delivery-plans', {
    method: 'POST', token,
    body: {
      planDate: today(), storeId: store.id, storeName: store.name, splitBatchIds,
      vehicleNo, vehicleType: vehicleNo ? '冷藏车' : null, refrigeration: vehicleNo ? '机械制冷' : null,
      driverName, driverPhone: driverName ? '13900000001' : null,
      origin: null, plannedDepart: null, plannedArrive: null, remark,
    },
  })
  const plan = res?.data
  if (plan?.id) planIds.push(plan.id)
  return plan
}

// 三张计划：三家不同门店，各 1 批货；车与司机都留空（等现场读）
const p1 = await createPlan([b1.id], s1, { remark: '多站回归-P1' })
const p2 = await createPlan([b2.id], s2, { remark: '多站回归-P2' })
const p3 = await createPlan([b3.id], s3, { remark: '多站回归-P3' })
check('★ 准备：三张计划（三家门店各 1 批货，车与司机留空）',
  p1?.status === 1 && p2?.status === 1 && p3?.status === 1,
  `p1=${p1?.planNo}/${p1?.status} p2=${p2?.planNo}/${p2?.status} p3=${p3?.planNo}/${p3?.status}`)
if (!p1?.id || !p2?.id || !p3?.id) { console.log('计划建不出来，退出'); process.exit(1) }

// ── B. 一次装车扫三批（故意用「扫码顺序 ≠ 建计划顺序」） ──
//    先扫第 3 张计划（S3）的货，再扫第 1 张（S1）、最后第 2 张（S2）。
//    站点顺序必须跟着扫码走，不能跟着数据库 id 走 —— 按站装车时扫码顺序就是卸货顺序。
const ok = await loadReport({
  vehicleNo: PLATE, driverName: '张师傅',
  batchNos: [b3.batchNo, b1.batchNo, b2.batchNo],
})
check('★ 一车装了分属三张计划的货 → 并成一个车次自动建单（已入账）',
  statusOf(ok) === ST_ACCEPTED, brief(ok))
const tid = targetIdOf(ok)
if (tid) transportIds.push(tid)
if (!tid) { console.log('未能建出运单，后续用例无法继续，退出'); process.exit(1) }

// ── C. 运单事实 ──
const tRes = await api(`/distribution/transports/${tid}`, { token })
const t = tRes?.data
check('★ 途经站点数 = 3（三家门店）', t?.stopCount === 3, `stopCount=${t?.stopCount}`)
check('★ 多站运单的目的地写成「途经 3 站：S3、S1、S2」（顺序 = 扫码顺序）',
  typeof t?.destination === 'string' && t.destination.startsWith('途经 3 站：')
  && t.destination.includes(s3.name) && t.destination.indexOf(s3.name) < t.destination.indexOf(s1.name)
  && t.destination.indexOf(s1.name) < t.destination.indexOf(s2.name),
  `destination=${t?.destination}`)
check('★ 车牌取自现场（计划里是空的，由设备补齐）', t?.vehicleNo === PLATE, `vehicleNo=${t?.vehicleNo}`)
check('★ 司机只报了姓名 → 记「人工录入、非本人确认」（刷卡确认只发生在发车那一刻）',
  t?.driverName === '张师傅' && t?.driverSource === 'MANUAL' && t?.driverConfirmed === 0,
  `driverName=${t?.driverName} driverSource=${t?.driverSource} driverConfirmed=${t?.driverConfirmed}`)
check('★ 运单来源 = DEVICE / LOADER-001（能分辨是装车口自动建的）',
  t?.source === 'DEVICE' && t?.sourceRef === LOAD_DEVICE,
  `source=${t?.source} sourceRef=${t?.sourceRef}`)

// ── D. 站点清单接口 ──
const stopsRes = await api(`/distribution/transports/${tid}/stops`, { token })
const stops = stopsRes?.data || []
check('★ 站点清单返回 3 站', stops.length === 3, `条数=${stops.length}`)
const seqOk = stops.length === 3 && stops[0].seq === 1 && stops[1].seq === 2 && stops[2].seq === 3
check('★ 站点序号 1/2/3 连续（按扫码顺序）', seqOk, `seq=${stops.map((s) => s.seq).join(',')}`)
check('★ 站点顺序 = 扫码顺序（S3 → S1 → S2），不是建计划顺序',
  stops[0]?.storeName === s3.name && stops[1]?.storeName === s1.name && stops[2]?.storeName === s2.name,
  `顺序=${stops.map((s) => s.storeName).join(' → ')}`)
check('★ 每站各 1 批货，且批次归属正确',
  stops.every((s) => s.batchCount === 1)
  && stops[0]?.batchNos?.[0] === b3.batchNo
  && stops[1]?.batchNos?.[0] === b1.batchNo
  && stops[2]?.batchNos?.[0] === b2.batchNo,
  `站点批次=${stops.map((s) => (s.batchNos || []).join('+')).join(' | ')}`)
check('★ 第一站标记为主站（运单的「主目的地」），其余不标',
  stops[0]?.primary === true && stops[1]?.primary === false && stops[2]?.primary === false,
  `primary=${stops.map((s) => s.primary).join(',')}`)
check('★ 门店 id 与名字都落到了站点上（id 是权威、名字是展示快照）',
  stops.every((s) => s.storeId != null && !!s.storeName),
  `storeId=${stops.map((s) => s.storeId).join(',')}`)

// ── E. 三张计划都被推进 ──
for (const [label, plan, store] of [['P3', p3, s3], ['P1', p1, s1], ['P2', p2, s2]]) {
  const d = await api(`/distribution/delivery-plans/${plan.id}`, { token })
  const items = d?.data?.items || []
  check(`★ 计划 ${label}（${store.name}）转「已排车」(2)，1 条明细指向本运单`,
    d?.data?.plan?.status === 2 && d?.data?.plan?.transportCount === 1
    && items.length === 1 && items[0].shipped === 1 && items[0].transportId === tid,
    `status=${d?.data?.plan?.status} transportCount=${d?.data?.plan?.transportCount} `
    + `items=${items.map((i) => `${i.batchNo}:${i.shipped}/${i.transportId}`).join(',')}`)
}

// ── F. 一车多店仍守住的两条红线 ──
// F1. 两张计划安排了不同的车 → 一趟车挂两台车，拒绝并转人工
const pA = await createPlan([b4.id], s1, { vehicleNo: '京A·99999', driverName: '李四', remark: '多站回归-车冲突A' })
const pB = await createPlan([b5.id], s2, { vehicleNo: PLATE, driverName: '张师傅', remark: '多站回归-车冲突B' })
check('★ 准备：两张计划（批次 4/5）各安排了一台不同的车', !!pA?.id && !!pB?.id, `${pA?.planNo} / ${pB?.planNo}`)
if (pA?.id && pB?.id) {
  const beforeTotal = (await api('/distribution/transports?pageNum=1&pageSize=1', { token }))?.data?.total
  const conflict = await loadReport({
    vehicleNo: PLATE, batchNos: [b4.batchNo, b5.batchNo],
  })
  check('★ 计划安排的车不一致 → 转人工（一趟车只能有一个车牌，绝不擅自挑一个）',
    statusOf(conflict) === ST_MANUAL && text(conflict).includes('不一致'), brief(conflict))
  const afterTotal = (await api('/distribution/transports?pageNum=1&pageSize=1', { token }))?.data?.total
  check('★ 冲突那次没有偷偷建出运单（运单总数没变）',
    afterTotal === beforeTotal, `前=${beforeTotal} 后=${afterTotal}`)
}

// F2. 混进「不属于任何计划」的批次 → 转人工（不猜它去哪家店）
const pendingNow = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const free = (pendingNow?.data?.records || [])[0]
check('★ 准备：库里还有未被任何计划占用的批次（用于构造「孤立批次」）',
  !!free, `剩余待发运条数=${(pendingNow?.data?.records || []).length}`)
if (free) {
  const orphan = await loadReport({ vehicleNo: PLATE, batchNos: [free.batchNo] })
  check('★ 批次不属于任何待发货计划 → 转人工（设备不猜目的地）',
    statusOf(orphan) === ST_MANUAL && text(orphan).includes('不属于任何待发货计划'), brief(orphan))
}

// ── G. 逐站确认到达（站点级到达：一趟车途经 3 站，一次推一站） ──
await api(`/distribution/transports/${tid}/depart`, { method: 'PUT', token })
const afterDepart = await api(`/distribution/transports/${tid}`, { token })
check('★ 发车：待发车(1) → 运输中(2)', afterDepart?.data?.status === 2, `status=${afterDepart?.data?.status}`)

// 跨站抢签：车还在第 1 站，先试着给第 3 站签 —— 签收的前置是「该站已到达」
const steal = await sign(stops[2])
check('★ 车还没到第 3 站就想签第 3 站 → 拦（签收前置下沉到站点，不看运单整体状态）',
  !steal?.data?.id && text(steal).includes('还没到'), brief(steal))

const a1 = await api(`/distribution/transports/${tid}/arrive`, { method: 'PUT', token })
check('★ 确认到达：只推进第 1 站（不是整张运单），并回报是哪一站',
  a1?.data?.stopNo === 1 && a1?.data?.storeName === s3.name, brief(a1))
const afterA1 = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 只到 1/3 站 → 运单仍停在「运输中」(2)：还有两站没到，不能提前进「待签收」',
  afterA1?.status === 2, `status=${afterA1?.status}`)

const stopsA1 = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
check('★ 站点台账：第 1 站已到达（带到达时刻），第 2/3 站未到达',
  stopsA1[0]?.arrived === true && !!stopsA1[0]?.arriveTime
  && stopsA1[1]?.arrived === false && stopsA1[2]?.arrived === false,
  `arrived=${stopsA1.map((s) => s.arrived).join(',')} arriveTime=${stopsA1.map((s) => s.arriveTime || '-').join(',')}`)

// 第 2 站还没到 → 签第 2 站同样要拦（跨站抢签与跳站是同一条口径）
const steal2 = await sign(stops[1])
check('★ 第 2 站还没到就签第 2 站 → 拦（跨站抢签）',
  !steal2?.data?.id && text(steal2).includes('还没到'), brief(steal2))

await api(`/distribution/transports/${tid}/arrive`, { method: 'PUT', token })
const afterA2 = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 2/3 站到达 → 仍是「运输中」(2)', afterA2?.status === 2, `status=${afterA2?.status}`)

const a3 = await api(`/distribution/transports/${tid}/arrive`, { method: 'PUT', token })
check('★ 第 3 站到达（三站到齐）', a3?.data?.stopNo === 3 && a3?.data?.storeName === s2.name, brief(a3))
const afterArrive = (await api(`/distribution/transports/${tid}`, { token }))?.data
check('★ 三站全部到达 → 运单转「待签收」(3)',
  afterArrive?.status === 3, `status=${afterArrive?.status}`)
check('★ 运单主表的 arrive_time 同步为末站到达时刻（兼容展示；真相在站点台账）',
  !!afterArrive?.arriveTime, `arriveTime=${afterArrive?.arriveTime}`)

const overArrive = await api(`/distribution/transports/${tid}/arrive`, { method: 'PUT', token })
check('★ 站点都到齐后再点「确认到达」→ 拦（没有未到达的站可推进）',
  !overArrive?.data && text(overArrive).includes('都已到达'), brief(overArrive))

// ── H. 逐站签收 ──
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

const r1 = await sign(stops[0])
const s1Res = await api(`/distribution/transports/${tid}`, { token })
check('★ 签收第 1 站成功', !!r1?.data?.id, brief(r1))
check('★ 只签 1/3 站 → 运单仍是「待签收」(3)：剩下两家的货权还没转移',
  s1Res?.data?.status === 3, `status=${s1Res?.data?.status}`)

const r2 = await sign(stops[1])
const s2Res = await api(`/distribution/transports/${tid}`, { token })
check('★ 签收第 2 站成功', !!r2?.data?.id, brief(r2))
check('★ 签 2/3 站 → 仍是「待签收」(3)', s2Res?.data?.status === 3, `status=${s2Res?.data?.status}`)

// 同一站重复签 → 报「已签收」（幂等键是「运单 + 门店」：放开的是站点数，不是签收次数）
const dup = await sign(stops[0])
check('★ 同一站重复签收 → 拦（放开的是站点数，不是签收次数）',
  !dup?.data?.id && (text(dup).includes('已签收') || text(dup).includes('已存在')), brief(dup))

const r3 = await sign(stops[2])
const s3Res = await api(`/distribution/transports/${tid}`, { token })
check('★ 签收第 3 站成功', !!r3?.data?.id, brief(r3))
check('★ 三站全部签完 → 运单转「已签收」(4)',
  s3Res?.data?.status === 4, `status=${s3Res?.data?.status}`)

const stopsAfter = (await api(`/distribution/transports/${tid}/stops`, { token }))?.data || []
check('★ 站点清单反映三站都已签收，且带签收人与时间',
  stopsAfter.length === 3 && stopsAfter.every((s) => s.received === true && !!s.receiver && !!s.receiptTime),
  `received=${stopsAfter.map((s) => s.received).join(',')}`)

// ── I. 收尾 ──
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

console.log(`\n=== 多站车次回归：${pass} 通过 / ${fail} 失败 ===`)
console.log(`# PLAN_IDS=${planIds.join(',')}`)
console.log(`# TRANSPORT_IDS=${transportIds.join(',')}`)
console.log(`# STAGING_IDS=${stagingIds.join(',')}`)
console.log(`# RECEIPT_IDS=${receiptIds.join(',')}`)
process.exit(fail === 0 ? 0 : 1)
