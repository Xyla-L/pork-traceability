/**
 * 发货计划回归（冷链运单自动化的第 1 层）：
 *
 *   口径：计划是运单的**上游决策单** —— 先定「发哪批货、去哪家店、什么时候走」，
 *        运单从计划一键生成，目的地/计划时间/车辆司机信息全部带出，人不再重复手填。
 *
 *   与分割自动化的区别：分割是「事实已经发生，只是没记下来」（让设备记就行）；
 *        运单是「决策还没做」——这车拉哪批货、发去哪家店，信息根本不存在。
 *        所以先补单据，再谈装设备。
 *
 *   两条边界（本脚本逐条验证）：
 *     ① 车与司机在**计划阶段允许留空**（车次没定是常态）；生成运单时**只强制补齐车牌** ——
 *        车牌是后面发车（DEPART）与到达（ARRIVE）按车牌倒查的唯一凭据，少了它整条车的行程就断了。
 *        **司机可以继续留空**（2026-10-10 起）：现场只在上车那一刻刷一次卡，生成运单时司机还没上车；
 *        「责任必须落到具体人」那颗闸门挪到了**发车**（见 verify-depart.mjs）。
 *     ② 一趟只能拉没发过的货：已经排进过运单的批次、被别的待发货计划占用的批次，制单阶段就拦下，
 *        不等装车口才发现两张单据抢同一批货。
 *
 *   断言：
 *     1.  ★ 建计划：计划号 DP+8位日期+3位流水，状态=待发货，货物条数与总重与勾选一致
 *     2.  ★ 起运地没传时按货物批次自动带出（少填一个字段）
 *     3.  ★ 计划详情返回全部明细，批次号与勾选完全一致
 *     4.  ★ 已进计划的批次从「待发运」列表消失（否则会被重复勾选）
 *     5.  ★ 同一批次被别的待发货计划占用 → 拦
 *     6.  ★ 已由运单发运过的批次 → 拦（重复发货是实质业务事故）
 *     7.  ★ 不存在的批次 → 拦
 *     8.  ★ 一个批次都不勾 → 校验拦
 *     9.  ★ 生成运单缺车牌 → 拦；补车牌后 → 成功（司机可留空，闸门在发车）
 *    10.  ★ 生成后：计划状态转「已排车」，运单号回填，主批次取明细第一条
 *    11.  ★ 重复生成运单 → 拦
 *    12.  ★ 已排车的计划不能改货物明细 / 不能取消 → 拦
 *    13.  ★ 生成运单后，其货物批次仍不在「待发运」列表（货物已被运单占住）
 *    14.  ★ 删除运单 → 计划退回「待发货」（单据与实际状态重新对齐）
 *    15.  ★ 取消计划 → 状态转已取消，其占用的批次释放回「待发运」
 *    16.  ★ 已取消的计划不能生成运单 / 不能重复取消 → 拦
 *
 *   注：运单号是「TR+日期+6位随机」的历史口径（与批次号/作业号的有序流水不同），
 *      本脚本只断言格式，不断言具体值。
 *
 *   运行：node logs/verify-delivery-plan.mjs
 *   产生的计划/运单 ID 打印在末尾，由 bash 侧做落库核验与清理。
 *   （本环境 node 禁止子进程调用，DB 核验与清理必须走 bash。）
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

const brief = (r) => JSON.stringify(r ?? {}).slice(0, 240)
const text = (r) => JSON.stringify(r ?? {})

const pad = (n) => String(n).padStart(2, '0')
const today = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }
const fmt = (d) => `${today()} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const planIds = []
const transportIds = []

console.log('\n=== 发货计划回归 ===')

// ── 准备 1：待发运的分割批次 ──
const pendingRes = await api('/distribution/splits/pending?pageNum=1&pageSize=20', { token })
const pendingRows = pendingRes?.data?.records || []
check('★ 待发运分割批次接口可读，且至少有 2 个批次可勾选',
  pendingRows.length >= 2, `条数=${pendingRows.length}`)
if (pendingRows.length < 2) {
  console.log('待发运批次不足 2 条，后续用例无法构造，退出')
  process.exit(1)
}
const [b1, b2] = pendingRows
const pendingIdsBefore = new Set(pendingRows.map((r) => r.id))
console.log(`  样本批次：${b1.batchNo}(${b1.weightKg}kg) / ${b2.batchNo}(${b2.weightKg}kg)`)

// ── 准备 2：一条已经发运过的批次（用于反向断言） ──
const tRes = await api('/distribution/transports?pageNum=1&pageSize=5', { token })
const shippedTransport = (tRes?.data?.records || [])[0]
const shippedBatchId = shippedTransport?.splitBatchId
check('取到一条已排车的运单（供「不能重复发货」用例使用）', !!shippedBatchId,
  `transportNo=${shippedTransport?.transportNo} splitBatchId=${shippedBatchId}`)

// ── 准备 3：门店（机构树里 type=retail） ──
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
check('机构树里能取到门店（建计划的目的地）', stores.length > 0, `门店数=${stores.length}`)
if (!stores.length) process.exit(1)
const store = stores[0]

// ── 1. 建计划：车与司机先留空（计划阶段允许） ──
const basePlan = {
  planDate: today(),
  storeId: store.id,
  storeName: store.name,
  splitBatchIds: [b1.id, b2.id],
  plannedDepart: fmt(new Date()),
  plannedArrive: fmt(new Date(Date.now() + 2 * 3600 * 1000)),
  remark: '回归测试计划（自动生成）',
}
const created = await api('/distribution/delivery-plans', { method: 'POST', token, body: basePlan })
const planA = created?.data
check('★ 建计划成功', !!planA?.id, brief(created))
if (!planA?.id) { console.log('计划创建失败，退出'); process.exit(1) }
planIds.push(planA.id)

check('★ 计划号格式为 DP+8位日期+3位流水', /^DP\d{8}-\d{3}$/.test(planA.planNo || ''), `planNo=${planA.planNo}`)
check('★ 新建计划状态为「待发货」', planA.status === 1, `status=${planA.status}`)
check('★ 货物条数等于勾选的批次数', planA.itemCount === 2, `itemCount=${planA.itemCount}`)
const expectedWeight = Number(((b1.weightKg || 0) + (b2.weightKg || 0)).toFixed(2))
check('★ 计划总重等于各批次重量合计',
  Math.abs(Number(planA.totalWeightKg || 0) - expectedWeight) < 0.01,
  `计划=${planA.totalWeightKg} 期望=${expectedWeight}`)
check('★ 起运地未传时按货物批次自动带出', !!planA.origin, `origin=${planA.origin}`)

// ── 2. 计划详情 ──
const detail = await api(`/distribution/delivery-plans/${planA.id}`, { token })
const detailItems = detail?.data?.items || []
check('★ 计划详情返回全部明细', detailItems.length === 2, `条数=${detailItems.length}`)
check('★ 明细里的批次与勾选完全一致',
  detailItems.map((i) => i.splitBatchId).sort().join(',') === [b1.id, b2.id].sort().join(','),
  `明细=${detailItems.map((i) => i.batchNo).join(',')}`)

// ── 3. 已进计划的批次应从「待发运」消失 ──
const pendingAfter = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingIdsAfter = new Set((pendingAfter?.data?.records || []).map((r) => r.id))
check('★ 已进计划的批次从「待发运」列表消失（不会被重复勾选）',
  !pendingIdsAfter.has(b1.id) && !pendingIdsAfter.has(b2.id),
  `${b1.batchNo}=${pendingIdsAfter.has(b1.id)} ${b2.batchNo}=${pendingIdsAfter.has(b2.id)}`)
check('未进计划的批次仍在「待发运」列表',
  pendingIdsAfter.size === pendingIdsBefore.size - 2,
  `前后=${pendingIdsBefore.size}→${pendingIdsAfter.size}（应为 -2）`)

// ── 4. 生成运单：缺车牌 → 拦；补车牌后 → 成功（司机可留空） ──
const noPlate = await api(`/distribution/delivery-plans/${planA.id}/generate`, { method: 'POST', token })
check('★ 生成运单时缺车牌 → 拦下（车牌是发车/到达按车牌倒查的唯一凭据）',
  text(noPlate).includes('车牌'), brief(noPlate))

const withPlate = await api(`/distribution/delivery-plans/${planA.id}`, {
  method: 'PUT', token, body: { ...basePlan, vehicleNo: '京A·PLAN01', vehicleType: '冷藏车' },
})
check('补车牌成功', withPlate?.code === 200 || withPlate?.code === 0, brief(withPlate))

// 司机**允许留空**（2026-10-10 起）：现场只在上车那一刻刷一次卡，生成运单时司机还没上车，
// 所以这里不再拦 —— 只补车牌也能建单。「货要走了责任必须落到具体人」那颗闸门挪到了**发车**
// （人工「立即发车」与车载终端刷卡两条路，口径见 verify-depart.mjs）。
// 「计划里早就填了司机 → 运单带出该司机并记人工录入」这条路由 verify-load.mjs（计划 B）覆盖。
const genRes = await api(`/distribution/delivery-plans/${planA.id}/generate`, { method: 'POST', token })
const transport = genRes?.data
check('★ 只补齐车牌就能生成运单（司机可留空，责任闸门在发车）', !!transport?.id, brief(genRes))
if (!transport?.id) { console.log('生成运单失败，退出'); process.exit(1) }
transportIds.push(transport.id)
check('★ 司机留空时来源也留空（写 MANUAL 等于假装有人录过 —— 空名字不配有来源）',
  !transport.driverName && !transport.driverSource && transport.driverConfirmed === 0,
  `driverName=${transport.driverName} driverSource=${transport.driverSource} driverConfirmed=${transport.driverConfirmed}`)

check('运单号格式为 TR+8位日期+6位随机', /^TR\d{14}$/.test(transport.transportNo || ''), `transportNo=${transport.transportNo}`)
check('★ 新运单状态为「待发车」', transport.status === 1, `status=${transport.status}`)
check('★ 目的地从计划带出（未再手填）', transport.destination === store.name, `destination=${transport.destination}`)
check('★ 主批次取计划明细第一条',
  transport.splitBatchId === detailItems[0].splitBatchId,
  `主批次=${transport.splitBatchId} 明细首条=${detailItems[0].splitBatchId}`)

// ── 5. 计划状态已回填 ──
const planAfterGen = await api(`/distribution/delivery-plans/${planA.id}`, { token })
const planA2 = planAfterGen?.data?.plan
check('★ 计划状态转为「已排车」', planA2?.status === 2, `status=${planA2?.status}`)
check('★ 计划回填运单号与运单 id',
  planA2?.transportNo === transport.transportNo && planA2?.transportId === transport.id,
  `transportNo=${planA2?.transportNo}`)

// ── 6. 生成后不可再动 ──
const regen = await api(`/distribution/delivery-plans/${planA.id}/generate`, { method: 'POST', token })
check('★ 货物已全部排车 → 再生成运单被拦下', text(regen).includes('已全部排车'), brief(regen))
const editAfterGen = await api(`/distribution/delivery-plans/${planA.id}`, { method: 'PUT', token, body: basePlan })
check('★ 已排车的计划不能改货物明细 → 拦下', text(editAfterGen).includes('不能再改'), brief(editAfterGen))
const cancelAfterGen = await api(`/distribution/delivery-plans/${planA.id}/cancel`, { method: 'POST', token, body: { reason: '试图直接取消' } })
check('★ 已排车的计划不能直接取消 → 拦下（须先删运单）',
  text(cancelAfterGen).includes('没有可取消的货物'), brief(cancelAfterGen))

// ── 7. 运单占住货物：那两批仍不在待发运 ──
const pendingAfterGen = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingIdsGen = new Set((pendingAfterGen?.data?.records || []).map((r) => r.id))
check('★ 生成运单后，其货物批次仍不在「待发运」（货物已被运单占住）',
  !pendingIdsGen.has(b1.id) && !pendingIdsGen.has(b2.id),
  `${b1.batchNo}=${pendingIdsGen.has(b1.id)} ${b2.batchNo}=${pendingIdsGen.has(b2.id)}`)

// ── 8. 占用冲突：另建一个计划勾同一批 → 拦 ──
const planB = await api('/distribution/delivery-plans', {
  method: 'POST', token,
  body: { ...basePlan, splitBatchIds: [b1.id], remark: '占位计划' },
})
// 此时 b1 已被运单占住，所以这条本身就该被拦；换一个当前空闲的批次来验「计划占用」冲突
const planBRes = planB
check('★ 已被运单发运过的批次不能进新计划 → 拦下',
  text(planBRes).includes('已由运单发运过') || text(planBRes).includes('不能重复发货'), brief(planBRes))

// 取一个空闲批次，建计划 C 占住它，再用计划 D 抢同一批 → 应被「计划占用」拦下
const freeRows = (pendingAfterGen?.data?.records || []).filter((r) => r.id !== b1.id && r.id !== b2.id)
if (freeRows.length >= 1) {
  const free = freeRows[0]
  const planC = await api('/distribution/delivery-plans', {
    method: 'POST', token,
    body: { ...basePlan, splitBatchIds: [free.id], remark: '占用计划' },
  })
  check('建第二个计划占住一个空闲批次', !!planC?.data?.id, brief(planC))
  if (planC?.data?.id) planIds.push(planC.data.id)
  const planD = await api('/distribution/delivery-plans', {
    method: 'POST', token,
    body: { ...basePlan, splitBatchIds: [free.id], remark: '抢货计划' },
  })
  check('★ 同一批次被待发货计划占用 → 拦下（不让两张单据抢同一批货）',
    text(planD).includes('占用'), brief(planD))
  // 取消 C，其占用的批次应释放
  if (planC?.data?.id) {
    const cancelled = await api(`/distribution/delivery-plans/${planC.data.id}/cancel`, {
      method: 'POST', token, body: { reason: '回归用例：释放占用' },
    })
    check('取消占位计划成功', cancelled?.code === 200 || cancelled?.code === 0, brief(cancelled))
    const afterRelease = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
    const released = new Set((afterRelease?.data?.records || []).map((r) => r.id))
    check('★ 取消计划后其占用的批次释放回「待发运」', released.has(free.id), `批次 ${free.batchNo}`)
    // 释放后可再次被勾选
    const planE = await api('/distribution/delivery-plans', {
      method: 'POST', token,
      body: { ...basePlan, splitBatchIds: [free.id], remark: '释放后重占' },
    })
    check('★ 释放后该批次可被新计划重新占用', !!planE?.data?.id, brief(planE))
    if (planE?.data?.id) {
      planIds.push(planE.data.id)
      // 收尾释放：不取消它就会一直占着这个批次，影响后续断言与下次回归
      await api(`/distribution/delivery-plans/${planE.data.id}/cancel`, {
        method: 'POST', token, body: { reason: '回归用例：收尾释放占用' },
      })
    }
  }
} else {
  check('有空闲批次用于「计划占用」冲突用例', false, '待发运列表里没有额外空闲批次')
}

// ── 9. 参数与前置校验 ──
const notExist = await api('/distribution/delivery-plans', {
  method: 'POST', token, body: { ...basePlan, splitBatchIds: [99999999] },
})
check('★ 不存在的分割批次 → 拦下', text(notExist).includes('不存在'), brief(notExist))

const emptyItems = await api('/distribution/delivery-plans', {
  method: 'POST', token, body: { ...basePlan, splitBatchIds: [] },
})
// 校验失败不会返回一个可用的计划对象：不断言具体错误码，只断言「没建出来」
check('★ 一个批次都不勾 → 校验拦下（没有返回可用的计划对象）',
  emptyItems?.data?.id == null, brief(emptyItems))

// ── 10. 删除运单 → 计划退回待发货 ──
const delRes = await api(`/distribution/transports/${transport.id}`, { method: 'DELETE', token })
check('删除运单成功', delRes?.code === 200 || delRes?.code === 0, brief(delRes))
const planAfterDel = await api(`/distribution/delivery-plans/${planA.id}`, { token })
const planA3 = planAfterDel?.data?.plan
check('★ 删除运单后计划退回「待发货」并清空运单回填（单据与实际重新对齐）',
  planA3?.status === 1 && !planA3?.transportNo && !planA3?.transportId,
  `status=${planA3?.status} transportNo=${planA3?.transportNo} transportId=${planA3?.transportId}`)

// 运单删了、计划还开着（已退回待发货），货物仍然被计划占着 —— 这是对的。
// 若此时货物就回到待发运列表，等于一次「删运单」操作把货放了两次（计划里还留着一条明细）。
const pendingAfterDel = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingIdsDel = new Set((pendingAfterDel?.data?.records || []).map((r) => r.id))
check('★ 运单删除后货物仍被计划占着（不会失控回到可勾选列表）',
  !pendingIdsDel.has(b1.id) && !pendingIdsDel.has(b2.id),
  `${b1.batchNo}=${pendingIdsDel.has(b1.id)} ${b2.batchNo}=${pendingIdsDel.has(b2.id)}`)

// ── 11. 取消计划 ──
const cancelA = await api(`/distribution/delivery-plans/${planA.id}/cancel`, {
  method: 'POST', token, body: { reason: '回归用例：取消计划' },
})
check('★ 取消计划成功', cancelA?.code === 200 || cancelA?.code === 0, brief(cancelA))
const planCancelled = await api(`/distribution/delivery-plans/${planA.id}`, { token })
check('★ 取消后状态为「已取消」', planCancelled?.data?.plan?.status === 3,
  `status=${planCancelled?.data?.plan?.status}`)
const cancelAgain = await api(`/distribution/delivery-plans/${planA.id}/cancel`, {
  method: 'POST', token, body: { reason: '重复取消' },
})
check('★ 重复取消 → 拦下', text(cancelAgain).includes('已取消'), brief(cancelAgain))
const genCancelled = await api(`/distribution/delivery-plans/${planA.id}/generate`, { method: 'POST', token })
check('★ 已取消的计划不能生成运单 → 拦下', text(genCancelled).includes('已取消'), brief(genCancelled))

// 「释放」的完整含义：计划取消后，货才真正回到可勾选状态
const pendingAfterCancel = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingIdsCancel = new Set((pendingAfterCancel?.data?.records || []).map((r) => r.id))
check('★ 取消计划后货物才真正回到「待发运」可勾选',
  pendingIdsCancel.has(b1.id) && pendingIdsCancel.has(b2.id),
  `${b1.batchNo}=${pendingIdsCancel.has(b1.id)} ${b2.batchNo}=${pendingIdsCancel.has(b2.id)}`)

// ── 12. 计划列表与筛选 ──
const planPage = await api('/distribution/delivery-plans?pageNum=1&pageSize=20', { token })
const planRows = planPage?.data?.records || []
check('计划列表可读且包含本次创建的计划',
  planRows.some((p) => p.id === planA.id), `条数=${planRows.length}`)
const cancelledPage = await api('/distribution/delivery-plans?status=3&pageNum=1&pageSize=20', { token })
check('按状态筛选命中已取消计划',
  (cancelledPage?.data?.records || []).every((p) => p.status === 3),
  `已取消条数=${(cancelledPage?.data?.records || []).length}`)

console.log(`\n=== 发货计划 HTTP 断言：通过 ${pass} / 失败 ${fail} ===`)
console.log(`# PLAN_IDS=${planIds.join(',')}`)
console.log(`# TRANSPORT_IDS=${transportIds.join(',')}`)
console.log(`# BATCH_IDS=${[b1.id, b2.id].join(',')}`)
process.exit(fail > 0 ? 1 : 0)
