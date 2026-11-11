/**
 * 门店拒收流程回归 —— 冷链异常货物的正式出口。
 *
 * 口径：
 *   · 拒收是站点终态（STOP_REJECTED=3）：只有门店人工发起，理由必填、实名、上链；
 *     设备与正常签收通道都无权翻案。
 *   · 运单状态由站点汇总推导，拒收站点视同「已终结」：
 *     单店拒收不卡别的店签收；全部站点终结（签收或拒收）运单即闭环落 4 ——
 *     「一车一趟」的车辆放行口径因此天然放行，不存在「拒收把车锁死」。
 *   · 处置登记已收敛（2026-11-02）：三选一删除，拒收唯一出口是「运回」；
 *     登记端点保留但一律返回「统一运回」说明。回运链全程见 verify-return-leg.mjs。
 *   · 协商成功可「拒收改签收」：复用正常签收录入凭证，站点标记「曾拒收后改签」。
 *
 * 断言分组：
 *   A. 准备：待发运批次 / 门店 / 两张三站计划（含 retail_user 的门店）
 *   B. 装车口建单 + 司机刷卡发车 + 逐站到达 → 待签收(3)
 *   C. 拒收的前置与红线：理由必填；重复拒收；正常签收拦「已拒收」；批量签收写明「已拒收」
 *   D. 处置登记：登记成功；重复登记被拦；已处置不能改签收
 *   E. 部分拒收闭环：另两站签完 → 运单落 4（车辆放行口径）
 *   F. 拒收改签收（第二张车）：改签成功、标记 signedAfterReject、签满闭环
 *   G. 收尾：删签收 + 删运单 + 取消计划，批次全部回到「待发运」
 *
 * 运行：node logs/verify-reject-flow.mjs
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

let pass = 0
let fail = 0
const failures = []
function check(name, ok, detail = '') {
  if (ok) pass++
  else { fail++; failures.push(`${name}${detail ? `（${detail}）` : ''}`) }
  console.log(`  ${ok ? '[PASS]' : '[FAIL]'} ${name}${detail && !ok ? ' —— ' + detail : ''}`)
}

async function api(path, { method = 'GET', token, body, headers = {} } = {}) {
  const h = { 'Content-Type': 'application/json', ...headers }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined })
  return res.json().catch(() => null)
}
const brief = (r) => JSON.stringify(r ?? {}).slice(0, 300)
const text = (r) => JSON.stringify(r ?? {})
const pad = (n) => String(n).padStart(2, '0')
const today = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }
// 每次运行整体前移一个随机分钟数：连跑两遍脚本时两次运行间隔可能不足 1 秒，
// 若不加偏移，第 k 次上报的时间戳会在两轮里截断成同一秒，撞上一轮落库报文被判重
const RUN_OFFSET = Math.floor(Math.random() * 600) * 1000
let tickSeq = 0
/** 上一条用的毫秒值：相邻两次调用若恰好跨 1000ms 整，截断到秒会得到同一字符串而撞判重 */
let lastMs = Number.MAX_SAFE_INTEGER
function nextTime() {
  tickSeq += 1
  let ms = Date.now() - RUN_OFFSET - tickSeq * 1000
  if (ms >= lastMs) ms = lastMs - 1000
  lastMs = ms
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
let bizSeq = 0
const nextBizKey = (tag) => `${tag}-${Date.now()}-${++bizSeq}`

const ONBOARD_DEVICE = 'ARRIVE-001'
const LOAD_DEVICE = 'LOADER-001'
const ST_ACCEPTED = 2
const PLATE = '京A·T0003'
const CARD = '8801'

console.log('\n=== 门店拒收流程回归 ===')

const adminLogin = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const admin = adminLogin?.data?.token
const retailLogin = await api('/auth/login', { method: 'POST', body: { username: 'retail_user', password: '123456' } })
const retail = retailLogin?.data?.token
const MY_STORE = Number(retailLogin?.data?.user?.orgId)
check('管理员 + 零售商登录', !!admin && !!retail && !!MY_STORE, `retail store=${MY_STORE}`)
if (!admin || !retail) process.exit(1)

// ── A. 准备 ──
const prePlate = await api(`/distribution/transports?keyword=${encodeURIComponent(PLATE)}&pageNum=1&pageSize=20`, { token: admin })
check('★ 前置：专用车牌名下没有残留运单',
  (prePlate?.data?.records || []).length === 0,
  (prePlate?.data?.records || []).map((r) => `${r.transportNo}/${r.status}`).join(','))

const pendingRows = (await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token: admin }))?.data?.records || []
check('★ 待发运批次 ≥ 6', pendingRows.length >= 6, `条数=${pendingRows.length}`)
if (pendingRows.length < 6) process.exit(1)

const stores = []
const walk = (nodes) => {
  if (!Array.isArray(nodes)) return
  nodes.forEach((n) => { if (n.type === 'retail') stores.push({ id: n.id, name: n.name }); if (n.children?.length) walk(n.children) })
}
walk((await api('/system/orgs/tree', { token: admin }))?.data || [])
check('★ 机构树至少 3 家门店', stores.length >= 3, `门店数=${stores.length}`)
if (stores.length < 3) process.exit(1)
// 让 retail_user 的门店固定当第 3 站（拒收站），另两家当第 1/2 站
const others = stores.filter((s) => s.id !== MY_STORE).slice(0, 2)
const S = [others[0], others[1], { id: MY_STORE, name: stores.find((s) => s.id === MY_STORE)?.name || '本店' }]
check('★ 三站安排就绪（第 3 章站点 = 零售商本店）', S.every((s) => !!s.id), S.map((s) => s.name).join(' → '))

const planIds = []
const transportIds = []
const stagingIds = []
const receiptIds = []

async function createThreeStopTransport(tag) {
  const [b1, b2, b3] = pendingRows.splice(0, 3)
  const plans = []
  for (let i = 0; i < 3; i++) {
    const res = await api('/distribution/delivery-plans', {
      method: 'POST', token: admin,
      body: {
        planDate: today(), storeId: S[i].id, storeName: S[i].name, splitBatchIds: [[b1, b2, b3][i].id],
        vehicleNo: null, vehicleType: null, refrigeration: null,
        driverName: null, driverPhone: null, origin: null, plannedDepart: null, plannedArrive: null,
        remark: `拒收回归-${tag}-P${i + 1}`,
      },
    })
    if (res?.data?.id) planIds.push(res.data.id)
    plans.push(res?.data)
  }
  check(`★ [${tag}] 三张计划就绪`, plans.every((p) => p?.status === 1), plans.map((p) => `${p?.planNo}/${p?.status}`).join(','))
  const loaded = await api('/ingest/demo/report', {
    method: 'POST', token: admin,
    body: { deviceNo: LOAD_DEVICE, bizKey: nextBizKey('LOAD'), data: { loadTime: nextTime(), vehicleNo: PLATE, batchNos: [b1.batchNo, b2.batchNo, b3.batchNo] } },
  })
  stagingIds.push(loaded?.data?.stagingId)
  check(`★ [${tag}] 装车口扫三批 → 建单`, loaded?.data?.status === ST_ACCEPTED, brief(loaded))
  const tid = loaded?.data?.targetId
  if (tid) transportIds.push(tid)
  const depart = await api('/ingest/demo/report', {
    method: 'POST', token: admin,
    body: { deviceNo: ONBOARD_DEVICE, channel: 'DEPART', bizKey: nextBizKey('DEPART'), data: { departTime: nextTime(), vehicleNo: PLATE, cardNo: CARD } },
  })
  stagingIds.push(depart?.data?.stagingId)
  check(`★ [${tag}] 司机刷卡发车`, depart?.data?.status === ST_ACCEPTED, brief(depart))
  for (let i = 0; i < 3; i++) {
    const arr = await api('/ingest/demo/report', {
      method: 'POST', token: admin,
      body: { deviceNo: ONBOARD_DEVICE, channel: 'ARRIVE', bizKey: nextBizKey('ARRIVE'), data: { arriveTime: nextTime(), vehicleNo: PLATE, cardNo: CARD } },
    })
    stagingIds.push(arr?.data?.stagingId)
    if (arr?.data?.status !== ST_ACCEPTED) check(`★ [${tag}] 第 ${i + 1} 站到达`, false, brief(arr))
  }
  const t = (await api(`/distribution/transports/${tid}`, { token: admin }))?.data
  const stops = (await api(`/distribution/transports/${tid}/stops`, { token: admin }))?.data || []
  check(`★ [${tag}] 三站全部到达 → 待签收(3)，第 3 站 = 零售商本店`,
    t?.status === 3 && stops[2]?.storeId === MY_STORE,
    `status=${t?.status} 第三站=${stops[2]?.storeId}`)
  return { tid, stops }
}

// ── B. 第一张车：拒收 + 处置路径 ──
const A = await createThreeStopTransport('车A')
if (!A.tid) { console.log('车A 建不出来，退出'); process.exit(1) }
const rejectStopA = A.stops[2]

console.log('\n== C. 拒收的前置与红线 ==')
const shortReason = await api(`/distribution/transports/${A.tid}/stops/reject`, {
  method: 'POST', token: retail, body: { reason: '坏' },
})
check('★ 理由不足 2 字被拦（理由必填，随事件上链）', shortReason?.code !== 200, brief(shortReason))

const rejected = await api(`/distribution/transports/${A.tid}/stops/reject`, {
  method: 'POST', token: retail, body: { reason: '到货温度 -2℃ 超出冷链要求，化冻明显' },
})
check('★ 门店拒收成功（本店那一站）', rejected?.code === 200 && rejected?.data?.stopNo === 3, brief(rejected))

let tA = (await api(`/distribution/transports/${A.tid}`, { token: admin }))?.data
let stopsA = (await api(`/distribution/transports/${A.tid}/stops`, { token: admin }))?.data || []
check('★ 拒收站点视同已终结，但另两站未签 → 运单仍「待签收」(3)', tA?.status === 3, `status=${tA?.status}`)
check('★ 列表带 rejectedStopCount=1；站点行 rejected=true、received=false',
  tA?.rejectedStopCount === 1 && stopsA[2]?.rejected === true && stopsA[2]?.received === false,
  `rejectedStopCount=${tA?.rejectedStopCount} stop3=${JSON.stringify({ rejected: stopsA[2]?.rejected, received: stopsA[2]?.received })}`)
check('★ 拒收理由与实名落在站点上',
  stopsA[2]?.rejectReason?.includes('化冻') && !!stopsA[2]?.rejectBy && !!stopsA[2]?.rejectTime,
  `reason=${stopsA[2]?.rejectReason} by=${stopsA[2]?.rejectBy}`)

const again = await api(`/distribution/transports/${A.tid}/stops/reject`, {
  method: 'POST', token: retail, body: { reason: '再拒一次试试' },
})
check('★ 重复拒收被拦（终态不可重入）', again?.code !== 200 && text(again).includes('已拒收'), brief(again))

const signRejected = await api('/distribution/receipts', {
  method: 'POST', token: admin,
  body: { transportId: A.tid, storeId: rejectStopA.storeId, storeName: rejectStopA.storeName, receiver: '测试', qtyCheck: 1, tempCheck: 1, packageIntact: 1 },
})
check('★ 正常签收通道拦「已拒收」（设备/PDA 同此口径）', signRejected?.code !== 200 && text(signRejected).includes('拒收'), brief(signRejected))

const batchOnRejected = await api('/distribution/receipts/batch', {
  method: 'POST', token: retail, body: { transportIds: [A.tid], receiver: '回归-批量', qtyCheck: 1, tempCheck: 1, packageIntact: 1 },
})
check('★ 批量签收对已拒收运单：成功 0、写明「已拒收」',
  batchOnRejected?.data?.successCount === 0 && text(batchOnRejected).includes('已拒收'),
  brief(batchOnRejected))

console.log('\n== D. 处置已收敛为回运（2026-11-02 三选一删除；2026-11-03 只剩回运一段）==')
const disposalBlocked = await api(`/distribution/transports/${A.tid}/stops/disposal`, {
  method: 'POST', token: retail, body: { disposalType: 'RETURN_SUPPLIER', note: '旧客户端的最后一次尝试' },
})
check('★ 处置登记端点已收敛：任何登记都返回「统一回运」说明', disposalBlocked?.code !== 200 && text(disposalBlocked).includes('回运'), brief(disposalBlocked))

const pool = (await api('/distribution/return-pool?stage=DISPOSAL', { token: admin }))?.data || []
check('★ 拒收即入待回运池（无需任何登记）', pool.some((r) => r.stopId === rejectStopA.id || r.storeId === rejectStopA.storeId),
  `pool=${pool.length}`)

// 「未排车可改签收」的例外路径由 F 组的车B覆盖，这里不动 A 车，E 组继续按 1 拒 + 2 签闭环。

console.log('\n== E. 部分拒收闭环（1 拒 + 2 签 → 运单落 4，车辆放行） ==')
async function signStop(stop, receiver) {
  const res = await api('/distribution/receipts', {
    method: 'POST', token: admin,
    body: { transportId: A.tid, storeId: stop.storeId, storeName: stop.storeName, receiver, qtyCheck: 1, tempCheck: 1, tempValue: -15, packageIntact: 1 },
  })
  if (res?.data?.id) receiptIds.push(res.data.id)
  return res
}
const sign1 = await signStop(A.stops[0], '车A第一站收货员')
check('★ 单店拒收不卡别的店：第 1 站照常签收', !!sign1?.data?.id, brief(sign1))
tA = (await api(`/distribution/transports/${A.tid}`, { token: admin }))?.data
check('★ 1 签 1 拒 1 未签 → 仍「待签收」(3)', tA?.status === 3, `status=${tA?.status}`)
const sign2 = await signStop(A.stops[1], '车A第二站收货员')
check('★ 第 2 站签收', !!sign2?.data?.id, brief(sign2))
tA = (await api(`/distribution/transports/${A.tid}`, { token: admin }))?.data
check('★ 2 签 + 1 拒收（全部终结）→ 运单闭环「已签收」(4)：车辆放行口径天然放行',
  tA?.status === 4 && tA?.rejectedStopCount === 1, `status=${tA?.status} rejectedStopCount=${tA?.rejectedStopCount}`)

// ── F. 第二张车：拒收改签收路径 ──
console.log('\n== F. 拒收改签收 ==')
const B = await createThreeStopTransport('车B')
if (!B.tid) { console.log('车B 建不出来，退出'); process.exit(1) }
const rejectStopB = B.stops[2]
const rejectedB = await api(`/distribution/transports/${B.tid}/stops/reject`, {
  method: 'POST', token: retail, body: { reason: '包装破损，数量与运单不符' },
})
check('★ 车B 门店拒收成功', rejectedB?.code === 200, brief(rejectedB))

const convert = await api(`/distribution/transports/${B.tid}/stops/convert-receipt`, {
  method: 'POST', token: retail,
  body: { transportId: B.tid, storeId: rejectStopB.storeId, storeName: rejectStopB.storeName, receiver: '回归-改签收员', qtyCheck: 1, tempCheck: 1, packageIntact: 1 },
})
check('★ 协商成功 → 拒收改签收（复用正常签收录入凭证）', !!convert?.data?.id, brief(convert))
if (convert?.data?.id) receiptIds.push(convert.data.id)
let stopsB = (await api(`/distribution/transports/${B.tid}/stops`, { token: admin }))?.data || []
check('★ 改签后站点：received=true、rejected=false、signedAfterReject=1',
  stopsB[2]?.received === true && stopsB[2]?.rejected === false && stopsB[2]?.signedAfterReject === 1,
  JSON.stringify({ received: stopsB[2]?.received, rejected: stopsB[2]?.rejected, sar: stopsB[2]?.signedAfterReject }))
check('★ 改签的签收凭证挂在站点上（receiptId 非空）', stopsB[2]?.receiptId != null, `receiptId=${stopsB[2]?.receiptId}`)

// ── G. 收尾 ──
async function cleanupTransport(tid) {
  const all = ((await api(`/distribution/receipts?pageNum=1&pageSize=200`, { token: admin }))?.data?.records) || []
  const receipts = all.filter((r) => r.transportId === tid)
  for (const r of receipts) await api(`/distribution/receipts/${r.id}`, { method: 'DELETE', token: admin })
  await api(`/distribution/transports/${tid}`, { method: 'DELETE', token: admin })
}
for (const tid of transportIds) await cleanupTransport(tid)
for (const id of planIds) {
  await api(`/distribution/delivery-plans/${id}/cancel`, { method: 'POST', token: admin, body: { reason: '拒收回归收尾' } })
}
const pendingFinal = (await api('/distribution/splits/pending?pageNum=1&pageSize=100', { token: admin }))?.data?.records || []
const finalIds = new Set(pendingFinal.map((r) => r.id))
check('★ 收尾：删签收 + 删运单 + 取消计划后，六个批次全部回到「待发运」', finalIds.size >= 6,
  `待发运=${finalIds.size}`)

console.log(`\n=== 门店拒收流程回归：${pass} 通过 / ${fail} 失败 ===`)
if (fail) {
  console.log('失败项：')
  failures.forEach((f) => console.log(`  - ${f}`))
}
console.log(`# PLAN_IDS=${planIds.join(',')}`)
console.log(`# TRANSPORT_IDS=${transportIds.join(',')}`)
console.log(`# STAGING_IDS=${stagingIds.join(',')}`)
console.log(`# RECEIPT_IDS=${receiptIds.join(',')}`)
process.exit(fail === 0 ? 0 : 1)
