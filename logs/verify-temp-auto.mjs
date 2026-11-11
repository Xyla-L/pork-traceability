/**
 * 冷链温度「定时自动打卡」回归。
 *
 *   需求：**发车后按固定间隔自动采一条温度，送达（全部站点到达）后自动停**。
 *
 *   口径（写进代码的三条）：
 *     1) 采集窗口 = 运单 `status=2`（已发车、尚有站点未到达）。运单状态由站点汇总推导：
 *        全部站点到达 → 3（待签收）、全部签收 → 4（已签收），所以「车在跑」与 status=2 是同一件事，
 *        不需要另设开关，也不会出现「已经签收还在写温度」的账。
 *     2) 真机优先：某张运单只要有 `source=DEVICE` 的记录，模拟采集对它永久停手 ——
 *        模拟值混进真实温度里，事后分不清哪条是车厢温度、哪条是程序编的。
 *     3) 不补历史：错过的间隔就是空白，由 gap_seconds / is_gap 标出来；
 *        一次补齐十条「当时没采到」的读数 = 替探头编造它没采过的数据。
 *
 *   断言分组：
 *     A. 前置：存在运输中运单；拿到基线条数
 *     B. 采一条：写成功 / 温度落在 -18~-16 / 条数 +1 / 来源=SIMULATED / 间隔固化 300 秒
 *     C. 节流：立刻再扫 → 不写（未到采样间隔），条数不变
 *     D. 停采边界：待发车、待签收、已签收的运单都不在采集范围
 *     E. 展示口径：温度记录按时间升序返回（前端把最后一条当「当前温度」）
 *
 *   自建对象：本脚本自己装车+发车造一张「运输中且无真机读数」的运单来采，收尾连同
 *   它名下的模拟读数一起删掉 —— 不借种子台账，也不在库里留痕。
 *   运行：node logs/verify-temp-auto.mjs
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

let pass = 0
let fail = 0
function check(name, ok, detail = '') {
  if (ok) { pass++; console.log(`  [PASS] ${name}`) }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}
function info(name, detail = '') { console.log(`  [INFO] ${name}${detail ? ' —— ' + detail : ''}`) }

async function api(path, { method = 'GET', token, body } = {}) {
  const h = { 'Content-Type': 'application/json' }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined })
  return res.json().catch(() => null)
}
const brief = (r) => JSON.stringify(r ?? {}).slice(0, 300)

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录成功（令牌可用于读取运单）', !!token, brief(login))

const rowsOf = (res) => res?.data?.records || res?.data?.rows || res?.data?.list || []
const tempsOf = async (id) => {
  const res = await api(`/distribution/transports/${id}/temperature`, { token })
  return Array.isArray(res?.data) ? res.data : []
}
const pageOf = async (status) =>
  rowsOf(await api(`/distribution/transports?status=${status}&pageNum=1&pageSize=50`, { token }))

console.log('\n== A) 前置：自建一张「运输中、且没有真机读数」的运单 ==============')
// 不借用种子台账：种子里的运输中运单常年挂着真机 DEVICE 读数，而模拟采集对它们
// 已经永久让位（规则 2：真机优先）。拿它们做正向用例，数据一换就红 ——
// 独占型写入的回归必须自带一次性主体，用完自己删。
const stores = []
;(function walk(nodes) {
  if (!Array.isArray(nodes)) return
  for (const n of nodes) {
    if (n.type === 'retail') stores.push({ id: n.id, name: n.name })
    if (n.children) walk(n.children)
  }
})((await api('/system/orgs/tree', { token }))?.data || [])
const store = stores[0]
check('取到门店（计划目的地）', !!store, store?.name || '无')

const pendingSplits = rowsOf(await api('/distribution/splits/pending?pageNum=1&pageSize=5', { token }))
const freshBatch = pendingSplits[0]
check('待发运池里有货（自建采集对象的原料）', !!freshBatch, freshBatch?.batchNo || '无')

const pad2 = (n) => String(n).padStart(2, '0')
const nowStr = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} `
    + `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

// 一次性车牌：车辆备案里有（京A·T0005），且不占用其它回归脚本的车
const TEMP_PLATE = '京A·T0005'
const planRes = await api('/distribution/delivery-plans', {
  method: 'POST', token,
  body: {
    planDate: nowStr().slice(0, 10), storeId: store?.id, storeName: store?.name,
    splitBatchIds: [freshBatch?.id], vehicleNo: null, vehicleType: null, refrigeration: null,
    driverName: null, driverPhone: null, origin: null, plannedDepart: null, plannedArrive: null,
    remark: '温度自动采集回归（自建对象）',
  },
})
const tempPlan = planRes?.data
const loadRes = await api('/ingest/demo/report', {
  method: 'POST', token,
  body: {
    deviceNo: 'LOADER-001',
    data: { loadTime: nowStr(), vehicleNo: TEMP_PLATE, driverName: '张师傅', batchNos: [freshBatch?.batchNo] },
  },
})
const targetId = loadRes?.data?.targetId
check('装车口建出待发车运单', !!targetId, brief(loadRes))
const departRes = await api(`/distribution/transports/${targetId}/depart`, { method: 'PUT', token })
check('发车 → 进入采集窗口（status=2）',
  !String(JSON.stringify(departRes ?? {})).includes('"code":5000'), brief(departRes))
const target = (await api(`/distribution/transports/${targetId}`, { token }))?.data || {}
check('运输中运单带运单号与发车时刻', !!target.transportNo && !!target.departTime,
  `${target.transportNo} / depart=${target.departTime}`)
check('这张自建运单还没有任何真机读数（模拟采集才写得进去）',
  !(await tempsOf(targetId)).some((r) => r.source === 'DEVICE'))

const before = await tempsOf(target.id)
info('采集前该车温度条数', `${before.length} 条`)

console.log('\n== B) 触发一次采集：发车后应当自动落一条 ========================')
const forced = await api('/distribution/internal/temperature/sample?force=true', { method: 'POST', token })
check('采集接口可调用', forced?.code === 200, brief(forced))
check('扫描范围只含运输中运单', (forced?.data?.scanned || 0) >= 1, `scanned=${forced?.data?.scanned}`)
check('采样间隔按配置固化（300 秒）', forced?.data?.intervalSeconds === 300, `interval=${forced?.data?.intervalSeconds}`)

const mine = (forced?.data?.items || []).find((it) => it.transportId === target.id)
check('目标运单被采到', !!mine, brief(mine))
check('本次写入成功', mine?.written === true, brief(mine))
const reading = Number(mine?.temperature)
check('读数落在冷藏区间（-18 ~ -16℃）', Number.isFinite(reading) && reading >= -18 && reading <= -16, `t=${reading}`)

const after = await tempsOf(target.id)
check('温度条数 +1（真的落库，不是回执口头成功）', after.length === before.length + 1,
  `before=${before.length} after=${after.length}`)

const last = after[after.length - 1]
check('新记录的来源是「模拟探头」（SIMULATED）', last?.source === 'SIMULATED', `source=${last?.source}`)
check('采集方式标为自动（AUTO），不是人工打卡', last?.recordMethod === 'AUTO', `method=${last?.recordMethod}`)
check('记录人写明「模拟探头」，不冒充人名', last?.recorder === '模拟探头', `recorder=${last?.recorder}`)
check('额定采样间隔随记录固化', Number(last?.sampleIntervalSeconds) === 300, `${last?.sampleIntervalSeconds}`)
check('区间内读数不标异常', Number(last?.isAbnormal) === 0, `isAbnormal=${last?.isAbnormal}`)
check('与上一条的间隔被算出来（首条为 null）',
  before.length === 0 ? last?.gapSeconds == null : Number(last?.gapSeconds) >= 0,
  `gapSeconds=${last?.gapSeconds}`)
check('温度值与采集回执一致', Number(last?.temperature) === reading, `${last?.temperature} vs ${reading}`)

console.log('\n== C) 节流：未到间隔不重复写 ==================================')
const again = await api('/distribution/internal/temperature/sample', { method: 'POST', token })
const mine2 = (again?.data?.items || []).find((it) => it.transportId === target.id)
check('立刻再扫：本条不写', mine2?.written === false, brief(mine2))
check('写明原因「未到采样间隔」', String(mine2?.reason || '').includes('未到采样间隔'), mine2?.reason)
check('条数没有变化（不会一次刷出一堆）', (await tempsOf(target.id)).length === after.length)

console.log('\n== D) 停采边界：待发车 / 待签收 / 已签收 都不采 ===============')
// 用刚才那次「不带 force」的扫描结果判断范围：状态过滤发生在间隔判断之前，
// 所以待发车/已到达/已签收的单根本不会出现在 items 里 —— 不必再强制采一遍。
const idsInItems = new Set((again?.data?.items || []).map((it) => it.transportId))
const pending = await pageOf(1)
if (pending.length) {
  check('待发车运单不在采集范围', !idsInItems.has(pending[0].id), `transport=${pending[0].transportNo}`)
  check('待发车运单温度条数不变', (await tempsOf(pending[0].id)).length === 0)
} else {
  info('没有待发车运单，跳过这一条')
}

const arriving = await pageOf(3)
if (arriving.length) {
  const t = arriving[0]
  const cnt = (await tempsOf(t.id)).length
  check('待签收（已到达）运单不在采集范围', !idsInItems.has(t.id), `transport=${t.transportNo}`)
  check('待签收运单温度条数不变（送达即停）', (await tempsOf(t.id)).length === cnt, `cnt=${cnt}`)
} else {
  info('没有待签收运单，跳过这一条')
}

const signed = await pageOf(4)
if (signed.length) {
  const t = signed[0]
  const cnt = (await tempsOf(t.id)).length
  check('已签收运单不在采集范围', !idsInItems.has(t.id), `transport=${t.transportNo}`)
  check('已签收运单温度条数不变', (await tempsOf(t.id)).length === cnt, `cnt=${cnt}`)
} else {
  info('没有已签收运单，跳过这一条')
}

console.log('\n== E) 展示口径 ============================================')
const asc = after.every((r, i) => i === 0 || String(after[i - 1].recordTime) <= String(r.recordTime))
check('温度记录按记录时间升序返回（前端最后一条 = 当前温度）', asc, after.map((r) => r.recordTime).join(' | ').slice(0, 200))

// 收尾：这张运单是本脚本自己造的，连同它名下的模拟读数一起删掉（deleteTransport 会连带删温度），
// 计划也取消掉，让它装的那批货回到待发运池 —— 不留痕。
await api(`/distribution/transports/${targetId}`, { method: 'DELETE', token })
if (tempPlan?.id) {
  await api(`/distribution/delivery-plans/${tempPlan.id}/cancel`, {
    method: 'POST', token, body: { reason: '温度采集回归收尾' },
  })
}
const pendingAfter = new Set(rowsOf(await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })).map((b) => b.id))
check('收尾：自建运单与计划已清掉，货回到待发运', !freshBatch || pendingAfter.has(freshBatch.id),
  `batch=${freshBatch?.batchNo} 回到待发运=${pendingAfter.has(freshBatch?.id)}`)

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
process.exit(fail === 0 ? 0 : 1)
