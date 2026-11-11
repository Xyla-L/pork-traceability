/**
 * 批量签收 + 零售商门店边界 —— 回归
 *
 * 验证点：
 *   1. 待签收列表带「本次签收门店 / 可签站点数」：批量签收点下去会签哪家店，列表上要先说得清
 *   2. 零售商的待签收列表只含「自己那一站还没签」的运单（本店签完就不该再出现）
 *   3. 零售商拉别家运单的站点清单拿不到别人的站点（只返回本店那一站，别人的返回空）
 *   4. 零售商批量签别家的运单 → 全部失败，且写明原因（未签收、不误报成功）
 *   5. 批量签收正例：本店那一站签成功；同一单再签一次 → 「本门店已签收」（幂等）
 *   6. 单张失败不影响其余（批量语义）、空入参被校验拦下
 *   7. 正例签完用删除签收回退（站点回「已到达」、运单回「待签收」），不给回归留残留
 *
 * 用法：node logs/verify-batch-receipt.mjs
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
const batchBody = (transportIds, extra = {}) => ({
  transportIds,
  receiver: '回归-批量签收',
  qtyCheck: 1,
  tempCheck: 1,
  tempValue: -15,
  packageIntact: 1,
  ...extra,
})

// ============================================================
console.log('\n== 1) 待签收列表的「本次签收门店」 ================')
const admin = await login('admin')
const retail = await login('retail_user')
const MY_STORE = Number(retail.user.orgId)
const MY_NAME = retail.user.orgName

const adminPending = await get(admin.token, '/distribution/transports?status=3&pageNum=1&pageSize=50')
const adminRows = rows(adminPending.data)
check('管理员待签收列表可读', adminRows.length > 0, `rows=${adminRows.length}`)
check('每行都带 signableStopCount（数字）',
  adminRows.every((r) => typeof r.signableStopCount === 'number'))
check('可签站点数=1 时给出门店名',
  adminRows.filter((r) => r.signableStopCount === 1).every((r) => !!r.signableStoreName))
check('可签站点数>1 时不给门店名（有歧义不替人挑）',
  adminRows.filter((r) => r.signableStopCount > 1).every((r) => !r.signableStoreName),
  `multi=${adminRows.filter((r) => r.signableStopCount > 1).length}`)

const retailPending = await get(retail.token, '/distribution/transports?status=3&pageNum=1&pageSize=50')
const retailRows = rows(retailPending.data)
check('零售商待签收列表可读', retailPending.code === 200)
check('零售商列表里每行的可签门店都是本店',
  retailRows.filter((r) => r.signableStoreName).every((r) => r.signableStoreId === MY_STORE),
  retailRows.filter((r) => r.signableStoreName).map((r) => `${r.transportNo}:${r.signableStoreId}`).join(','))

// 本店已签的运单不该再出现在「待签收」里
const signedAlready = []
for (const row of retailRows) {
  const stops = await get(retail.token, `/distribution/transports/${row.id}/stops`)
  const list = Array.isArray(stops.data) ? stops.data : []
  if (list.some((s) => s.storeId === MY_STORE && s.received)) signedAlready.push(row.transportNo)
}
check('零售商待签收里没有「本店已签完」的运单', signedAlready.length === 0, signedAlready.join(','))

// ============================================================
console.log('\n== 2) 站点清单的门店边界 ================')
const ownTransport = retailRows.find((r) => r.signableStopCount === 1 && r.signableStoreId === MY_STORE) || retailRows[0]
if (ownTransport) {
  const stops = await get(retail.token, `/distribution/transports/${ownTransport.id}/stops`)
  const list = Array.isArray(stops.data) ? stops.data : []
  check('本店运单的站点清单只看得到本店那一站',
    list.every((s) => s.storeId === MY_STORE),
    list.map((s) => `${s.storeId}:${s.storeName}`).join(' '))
} else {
  check('本店运单的站点清单只看得到本店那一站', true, '无待签收运单，跳过')
}

const foreign = adminRows.find((r) => r.signableStoreId && r.signableStoreId !== MY_STORE)
if (foreign) {
  const stops = await get(retail.token, `/distribution/transports/${foreign.id}/stops`)
  const list = Array.isArray(stops.data) ? stops.data : []
  check('别家运单的站点清单对零售商为空', list.length === 0, `transport=${foreign.transportNo} rows=${list.length}`)
} else {
  check('别家运单的站点清单对零售商为空', true, '无别家待签收运单，跳过')
}

// ============================================================
console.log('\n== 3) 批量签收：越权与校验 ================')
if (foreign) {
  const before = rows((await get(admin.token, '/distribution/receipts?pageNum=1&pageSize=200')).data)
    .filter((r) => r.transportId === foreign.id).length
  const denied = await send(retail.token, 'POST', '/distribution/receipts/batch', batchBody([foreign.id]))
  const item = denied.data?.items?.[0]
  check('零售商批量签别家运单：成功数为 0', denied.data?.successCount === 0, JSON.stringify(denied.data))
  check('零售商批量签别家运单：写明原因', !!item?.message && item.success === false, item?.message)
  const after = rows((await get(admin.token, '/distribution/receipts?pageNum=1&pageSize=200')).data)
    .filter((r) => r.transportId === foreign.id).length
  check('零售商批量签别家运单：不落签收单', after === before, `before=${before} after=${after}`)
}

const empty = await send(retail.token, 'POST', '/distribution/receipts/batch', batchBody([]))
// 项目的参数校验以 200 信封 + 非 200 code 返回（1001 = 参数错误），所以断言看 code 不看 HTTP status
check('空运单列表被参数校验拦下', empty.code !== 200 && /请选择/.test(empty.message || ''),
  `code=${empty.code} msg=${empty.message}`)

// ============================================================
console.log('\n== 4) 批量签收：正例 + 幂等 + 回退 ================')
const target = retailRows.find((r) => r.signableStopCount === 1 && r.signableStoreId === MY_STORE)
let createdReceiptId = null
if (!target) {
  console.log('  · 本店当前没有「已到达且未签收」的运单，正例跳过（不伪造数据）')
  check('批量签收正例（跳过：无可签运单）', true)
} else {
  const ok = await send(retail.token, 'POST', '/distribution/receipts/batch', batchBody([target.id]))
  const item = ok.data?.items?.[0]
  createdReceiptId = item?.receiptId || null
  check('批量签收 1 单成功', ok.data?.successCount === 1, JSON.stringify(ok.data))
  check('签收门店落本店', item?.storeId === MY_STORE && item?.storeName === MY_NAME,
    `${item?.storeId}:${item?.storeName}`)

  const listAfter = await get(retail.token, '/distribution/receipts?pageNum=1&pageSize=100')
  const mine = rows(listAfter.data).filter((r) => r.transportId === target.id)
  check('已签收列表出现这一单', mine.length === 1, `rows=${mine.length}`)
  check('已签收列表的签收人正确', mine[0]?.receiver === '回归-批量签收', mine[0]?.receiver)
  check('零售商已签收列表只含本店', rows(listAfter.data).every((r) => r.storeId === MY_STORE))

  const again = await send(retail.token, 'POST', '/distribution/receipts/batch', batchBody([target.id]))
  check('同一单再签一次：不再成功（幂等）', again.data?.successCount === 0, JSON.stringify(again.data))
  check('同一单再签一次：说明是「本门店已签收」',
    /已签收/.test(again.data?.items?.[0]?.message || ''), again.data?.items?.[0]?.message)

  const pendingAfter = await get(retail.token, '/distribution/transports?status=3&pageNum=1&pageSize=50')
  check('签完之后本店「待签收」里不再有这一单',
    !rows(pendingAfter.data).some((r) => r.id === target.id))

  // 回退：删掉这张签收单，站点回「已到达」、运单回「待签收」，回归不留残留
  if (createdReceiptId) {
    const removed = await send(admin.token, 'DELETE', `/distribution/receipts/${createdReceiptId}`)
    check('删除签收单成功（回退）', removed.code === 200, `code=${removed.code}`)
    const back = await get(admin.token, `/distribution/transports?status=3&pageNum=1&pageSize=50`)
    check('回退后该运单回到待签收',
      rows(back.data).some((r) => r.id === target.id), `transport=${target.transportNo}`)
    const stopsBack = await get(admin.token, `/distribution/transports/${target.id}/stops`)
    const st = (stopsBack.data || []).find((s) => s.storeId === MY_STORE)
    check('回退后本店站点回到「已到达未签收」',
      !!st && st.received === false && st.arrived === true,
      JSON.stringify(st))
  }
}

// ============================================================
console.log('\n== 5) 胴体批次：批次只由设备归集（接口仍在，页面入口已删） ================')
const batches = await get(admin.token, '/distribution/batches?pageNum=1&pageSize=5')
const batchRows = rows(batches.data)
check('胴体批次列表可读', batches.code === 200)
check('最新批次来自分拣台归集（source=DEVICE）', batchRows[0]?.source === 'DEVICE',
  `${batchRows[0]?.batchNo}:${batchRows[0]?.source}`)
check('批次都带屠宰场（人工建批不再产生「无场」的批）',
  batchRows.every((b) => !!b.slaughterhouse))

// ============================================================
console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
if (fail) {
  console.log('失败项：')
  failures.forEach((f) => console.log(`  - ${f}`))
  process.exit(1)
}
