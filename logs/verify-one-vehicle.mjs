/**
 * 一辆车同一时间只跑一趟（2026-10-22）：
 *   1. 该车牌已有未完成运单（待发车/运输中/待签收）→ 人工建第二张被拒
 *   2. 用「编辑运单改挂车牌」也绕不过去
 *   3. 把那张未完成的运单删掉之后，同一辆车才能接下一单
 *
 * 自持：车牌用一次性编号、批次用当前「待发运」池里的，收尾全部删干净。
 * 跑法：node logs/verify-one-vehicle.mjs
 * 依赖：distribution-service（ActiveTransportGuard）
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

let pass = 0
let fail = 0
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  ✓ ${name}`) }
  else { fail++; console.log(`  ✗ ${name}${detail ? ` — ${detail}` : ''}`) }
}
const brief = (r) => JSON.stringify(r ?? {}).slice(0, 300)
const text = (r) => JSON.stringify(r ?? {})

async function api(path, { method = 'GET', token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  return res.json().catch(() => null)
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

/** 一次性车牌：库里的计划与运单都不会用到它，保证「这张车名下没有历史单」 */
const PLATE = '京A·GV' + String(Date.now()).slice(-4)
const PLATE2 = '京A·GW' + String(Date.now()).slice(-4)
const created = []

function createBody(batchId, plate) {
  return {
    splitBatchId: batchId, vehicleNo: plate, vehicleType: '冷藏车', refrigeration: '机械制冷',
    driverName: '张师傅', driverPhone: '13700000002',
    origin: null, destination: '回归门店', plannedDepart: null, plannedArrive: null,
  }
}

console.log('\n=== 一车一趟回归（ActiveTransportGuard） ===')

const pending = await api('/distribution/splits/pending?pageNum=1&pageSize=20', { token })
const batches = (pending?.data?.records || []).filter((b) => b.id)
if (batches.length < 2) {
  console.log(`  待发运批次不足 2 个（当前 ${batches.length} 个），无法构造用例，退出`)
  process.exit(1)
}
const [b1, b2] = batches

// ── A. 第一张：这辆车名下干净 → 建得出来 ──
const first = await api('/distribution/transports', { method: 'POST', token, body: createBody(b1.id, PLATE) })
const firstId = first?.data?.id
check('★ 该车牌名下没有未完成运单 → 人工建单成功', !!firstId, brief(first))
if (firstId) created.push(firstId)

// ── B. 第二张：同车牌还有待发车运单 → 拒 ──
const second = await api('/distribution/transports', { method: 'POST', token, body: createBody(b2.id, PLATE) })
check('★ 该车牌已有未完成运单（待发车）→ 第二张被拒',
  !second?.data?.id && text(second).includes('未完成的运单'), brief(second))
check('★ 拒绝理由说清了是哪张单、以及怎么办（签收或删单）',
  text(second).includes('签收') && text(second).includes('一辆车同一时间只能跑一趟'), brief(second))

// ── C. 换个车牌：另一辆车能正常建单 ──
const other = await api('/distribution/transports', { method: 'POST', token, body: createBody(b2.id, PLATE2) })
const otherId = other?.data?.id
check('★ 换一台车（车牌不同）→ 正常建单（约束是按车牌，不是按批次）', !!otherId, brief(other))
if (otherId) created.push(otherId)

// ── D. 编辑运单把车牌改挂到「忙」的那台车上 → 同样被拒 ──
if (otherId) {
  const moveBody = { ...createBody(b2.id, PLATE), vehicleType: '冷藏车' }
  const moved = await api(`/distribution/transports/${otherId}`, { method: 'PUT', token, body: moveBody })
  check('★ 编辑运单改挂到忙碌车牌 → 被拒（换个入口也绕不过一车一趟）',
    text(moved).includes('未完成的运单'), brief(moved))
  const stillOther = await api(`/distribution/transports/${otherId}`, { token })
  check('★ 被拒之后那张运单的车牌没有被改动',
    stillOther?.data?.vehicleNo === PLATE2, `vehicleNo=${stillOther?.data?.vehicleNo}`)
}

// ── E. 把第一张删掉 → 同一辆车才能接下一单 ──
const del = await api(`/distribution/transports/${firstId}`, { method: 'DELETE', token })
check('★ 删除那张未完成运单', !del?.code || del?.code === 200 || del?.success !== false, brief(del))
created.shift()

const again = await api('/distribution/transports', { method: 'POST', token, body: createBody(b1.id, PLATE) })
const againId = again?.data?.id
check('★ 旧单清掉之后，同一辆车可以接新的一单（约束不是永久封车）', !!againId, brief(again))
if (againId) created.push(againId)

// ── F. 收尾：只删本次造的运单，批次回到待发运 ──
for (const id of created) {
  await api(`/distribution/transports/${id}`, { method: 'DELETE', token })
}
const pendingAfter = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const ids = new Set((pendingAfter?.data?.records || []).map((r) => r.id))
check('★ 收尾：本次用过的批次都回到「待发运」', ids.has(b1.id) && ids.has(b2.id),
  `b1=${ids.has(b1.id)} b2=${ids.has(b2.id)}`)

console.log(`\n=== 一车一趟回归：${pass} 通过 / ${fail} 失败 ===`)
process.exit(fail === 0 ? 0 : 1)
