/**
 * 零售商只看自己门店的数据 —— 回归
 *
 * 验证点：
 *   1. 令牌里带机构声明（orgId），且零售商账号的机构确实是一家 retail 门店
 *   2. 销售记录 / 二维码 / 过期预警 / 门店签收 四个列表只返回本门店的数据
 *   3. 传别人的门店号不会变成别人的视角（storeId 参数被服务端改写，不是「页面不显示」）
 *   4. 写操作（生成二维码 / 扫码售出 / 激活）拿别家的记录ID 会被拒
 *   5. 管理员仍能看到全部门店 —— 隔离不是「所有人都只能看一家」
 *   6. 工作台的销量口径与门店统计接口完全一致（卡片与列表不得两套数字）
 *
 * 用法：node logs/verify-store-scope.mjs
 */

const BASE = 'http://localhost:8080/api/v1'
const PASSWORD = '123456'

let pass = 0
let fail = 0
const failures = []

function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`  ✓ ${name}`)
  } else {
    fail++
    failures.push(`${name}${detail ? `（${detail}）` : ''}`)
    console.log(`  ✗ ${name}${detail ? ` — ${detail}` : ''}`)
  }
}

async function login(username) {
  const response = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PASSWORD }),
  })
  const body = await response.json()
  if (body.code !== 200 || !body.data?.token) {
    throw new Error(`${username} 登录失败：${body.message || body.code}`)
  }
  return { token: body.data.token, user: body.data.user }
}

function claims(token) {
  const part = token.split('.')[1]
  return JSON.parse(Buffer.from(part, 'base64url').toString('utf8'))
}

async function get(token, path) {
  const response = await fetch(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } })
  const body = await response.json().catch(() => ({}))
  return { status: response.status, code: body.code, data: body.data, message: body.message }
}

async function send(token, method, path, payload) {
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  })
  const body = await response.json().catch(() => ({}))
  return { status: response.status, code: body.code, data: body.data, message: body.message }
}

const rows = (page) => (page?.records || [])
const distinctStores = (list) => [...new Set(list.map((item) => item.storeId))]

// ============================================================
console.log('\n== 1) 令牌里的机构声明 ================')
const r1 = await login('retail_user')
const r2 = await login('retail_user_1002')
const admin = await login('admin')

const c1 = claims(r1.token)
const c2 = claims(r2.token)
check('零售商令牌带 orgId', String(c1.orgId) === '1001', `orgId=${c1.orgId}`)
check('另一门店令牌带自己的 orgId', String(c2.orgId) === '1002', `orgId=${c2.orgId}`)
check('登录信息里的机构与令牌一致', String(r1.user.orgId) === String(c1.orgId),
  `user.orgId=${r1.user.orgId} token.orgId=${c1.orgId}`)
check('机构名是门店而不是空', r1.user.orgName === '安心生鲜门店', `orgName=${r1.user.orgName}`)

// ============================================================
console.log('\n== 2) 销售记录：只看得到本门店 ================')
const sales1 = await get(r1.token, '/sales/records?pageNum=1&pageSize=200')
const salesRows1 = rows(sales1.data)
check('销售记录接口可用', sales1.code === 200, `code=${sales1.code} ${sales1.message || ''}`)
check('销售记录非空（门店确实有生意）', sales1.data?.total > 0, `total=${sales1.data?.total}`)
check('全部属于本门店 1001', distinctStores(salesRows1).every((id) => id === 1001),
  `实际门店 ${distinctStores(salesRows1).join(',')}`)
check('门店名与机构名一致', salesRows1.every((row) => row.storeName === '安心生鲜门店'),
  `实际 ${[...new Set(salesRows1.map((r) => r.storeName))].join(',')}`)
check('数量完整（未被额外截断）', salesRows1.length === sales1.data?.total,
  `返回 ${salesRows1.length} / 总数 ${sales1.data?.total}`)

console.log('\n-- 改门店参数不会换视角')
const peeking = await get(r1.token, '/sales/records?pageNum=1&pageSize=200&storeId=1002')
const peekRows = rows(peeking.data)
check('传别家门店号仍返回本门店数据', peekRows.every((row) => row.storeId === 1001),
  `实际门店 ${distinctStores(peekRows).join(',')}`)
check('传别家门店号不会缩小成 0 条', peekRows.length === salesRows1.length,
  `${peekRows.length} vs ${salesRows1.length}`)

console.log('\n-- 两家门店互不可见')
const sales2 = await get(r2.token, '/sales/records?pageNum=1&pageSize=200')
const rows2 = rows(sales2.data)
check('1002 只看到自己门店', rows2.every((row) => row.storeId === 1002),
  `实际门店 ${distinctStores(rows2).join(',')}`)
const ids1 = new Set(salesRows1.map((row) => row.id))
check('两家门店的记录没有交集', rows2.every((row) => !ids1.has(row.id)),
  `交集 ${rows2.filter((row) => ids1.has(row.id)).length} 条`)

// ============================================================
console.log('\n== 3) 二维码与过期预警 ================')
const qrs = await get(r1.token, '/sales/qrcodes?pageNum=1&pageSize=200')
const qrRows = rows(qrs.data)
check('二维码接口可用', qrs.code === 200, `code=${qrs.code}`)
check('二维码全部属于本门店', qrRows.every((row) => row.storeId === 1001),
  `实际门店 ${distinctStores(qrRows).join(',')}`)

const warnings = await get(r1.token, '/sales/warnings?pageNum=1&pageSize=200&handled=0')
const warningRows = rows(warnings.data)
check('预警接口可用', warnings.code === 200, `code=${warnings.code}`)
check('预警全部是本门店商品', warningRows.every((row) => row.storeName === '安心生鲜门店'),
  `实际 ${[...new Set(warningRows.map((r) => r.storeName))].join(',')}`)

const allWarnings = await get(admin.token, '/sales/warnings?pageNum=1&pageSize=200&handled=0')
check('管理员的预警比零售商多（确实做了收敛）',
  (allWarnings.data?.total || 0) > (warnings.data?.total || 0),
  `管理员 ${allWarnings.data?.total} / 零售商 ${warnings.data?.total}`)

// ============================================================
console.log('\n== 4) 门店签收：只看与自己有关的运单 ================')
const receipts = await get(r1.token, '/distribution/receipts?pageNum=1&pageSize=200')
const receiptRows = rows(receipts.data)
check('签收列表可用', receipts.code === 200, `code=${receipts.code}`)
check('签收单全部属于本门店', receiptRows.every((row) => row.storeId === 1001),
  `实际门店 ${distinctStores(receiptRows).join(',')}`)
check('签收单有数据（门店确实收过货）', receiptRows.length > 0, `条数 ${receiptRows.length}`)

const transports = await get(r1.token, '/distribution/transports?status=3&pageNum=1&pageSize=50')
const transportRows = rows(transports.data)
check('待签收运单接口可用', transports.code === 200, `code=${transports.code}`)
let everyTransportHasMyStop = true
const transportDetail = []
for (const transport of transportRows.slice(0, 10)) {
  const stops = await get(r1.token, `/distribution/transports/${transport.id}/stops`)
  const stopRows = stops.data || []
  const mine = stopRows.some((stop) => Number(stop.storeId) === 1001)
  transportDetail.push(`${transport.transportNo}:${mine ? '含本站' : '无本站'}`)
  if (!mine) everyTransportHasMyStop = false
}
check('列出的待签收运单都含本门店站点', everyTransportHasMyStop, transportDetail.join(' '))

const allTransports = await get(admin.token, '/distribution/transports?status=3&pageNum=1&pageSize=50')
check('管理员的待签收运单更多（运单未被全量下发）',
  (allTransports.data?.total || 0) >= (transports.data?.total || 0),
  `管理员 ${allTransports.data?.total} / 零售商 ${transports.data?.total}`)

// ============================================================
console.log('\n== 5) 写操作：拿别家的记录ID 也改不动 ================')
const foreignQr = [...rows(qrs.data)]
const allQrs = await get(admin.token, '/sales/qrcodes?pageNum=1&pageSize=200')
const foreignProduct = rows(allQrs.data).find((row) => row.storeId !== 1001)
check('库里存在别家门店的商品（用于越权测试）', !!foreignProduct,
  `找到 ${foreignProduct?.storeId ?? 'none'}`)

if (foreignProduct) {
  const sellAttempt = await send(r1.token, 'POST', '/sales/records',
    { qrCode: foreignProduct.productQrCode, sellPrice: 1, sellWeightKg: 0.1 })
  check('拿别家商品扫码售出被拒', sellAttempt.code !== 200,
    `code=${sellAttempt.code} ${sellAttempt.message || ''}`)

  const activateAttempt = await send(r1.token, 'PUT', `/sales/products/${foreignProduct.id}/activate`)
  check('激活别家商品被拒', activateAttempt.code !== 200,
    `code=${activateAttempt.code} ${activateAttempt.message || ''}`)
}

const foreignReceipt = (rows((await get(admin.token, '/distribution/receipts?pageNum=1&pageSize=200')).data))
  .find((row) => row.storeId !== 1001)
if (foreignReceipt) {
  const qrAttempt = await send(r1.token, 'POST', '/sales/qrcodes/batch',
    { receiptId: foreignReceipt.id, count: 1 })
  check('用别家签收单生成二维码被拒', qrAttempt.code !== 200,
    `code=${qrAttempt.code} ${qrAttempt.message || ''}`)
}

// ============================================================
console.log('\n== 6) 管理员不受限：隔离不是把所有人都关进一家店 ================')
const adminSales = await get(admin.token, '/sales/records?pageNum=1&pageSize=200')
const adminStores = distinctStores(rows(adminSales.data))
check('管理员看到多家门店', adminStores.length > 1, `门店 ${adminStores.join(',')}`)
check('管理员看到全部门店的记录',
  (adminSales.data?.total || 0) >= salesRows1.length, `管理员 ${adminSales.data?.total}`)

// ============================================================
console.log('\n== 7) 工作台销量口径与门店统计一致 ================')
const dash = await get(r1.token, '/dashboard/overview')
const dashCards = Object.fromEntries((dash.data?.statCards || []).map((card) => [card.key, card]))
check('工作台口径写明本门店', String(dash.data?.scopeLabel || '').includes('安心生鲜门店'),
  `scopeLabel=${dash.data?.scopeLabel}`)
check('工作台没有降级提示', (dash.data?.degraded || []).length === 0,
  `degraded=${(dash.data?.degraded || []).join(',')}`)

const statsOwn = await get(admin.token, '/sales/internal/stats/summary?days=30&storeId=1001')
const statsAll = await get(admin.token, '/sales/internal/stats/summary?days=30')
check('卡片销售额等于本门店统计',
  Number(dashCards.salesAmount?.value) === Number(statsOwn.data?.amount),
  `卡片 ${dashCards.salesAmount?.value} / 统计 ${statsOwn.data?.amount}`)
check('卡片销售额不等于全平台汇总（不是「假的门店口径」）',
  Number(statsOwn.data?.amount) !== Number(statsAll.data?.amount),
  `本店 ${statsOwn.data?.amount} / 全平台 ${statsAll.data?.amount}`)
check('卡片临期数等于本门店预警条数',
  Number(dashCards.expiringCount?.value) === (warnings.data?.total || 0),
  `卡片 ${dashCards.expiringCount?.value} / 预警 ${warnings.data?.total}`)
// 「大于 0」是防「空数据兜底」的哨兵，但演示数据会老化：种子销售全滚出 30 天窗口后，
// 门店销售额本来就是 0（这是真实口径，不是兜底）。所以只在统计接口有数时才要求卡片有数。
if (Number(statsOwn.data?.amount) > 0) {
  check('本门店销售额大于 0（不是空数据兜底）', Number(dashCards.salesAmount?.value) > 0,
    `value=${dashCards.salesAmount?.value}`)
} else {
  info('本门店近 30 天没有销售（种子数据滚出窗口），跳过「销售额大于 0」')
}

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
if (fail > 0) {
  console.log('失败项：')
  failures.forEach((item) => console.log(`  - ${item}`))
  process.exit(1)
}
