/**
 * 拒收回运链回归 —— 单段：冷链中心 → 无害化处理中心（2026-11-03 收敛）。
 *
 * 链路：门店拒收 → 货即在冷链中心 → 处理计划（跨天累积）→ 装车扫码即出单（生成/并入该车处理运单）
 *       → 司机刷卡发车 → 到达 → 无害化处理中心（DISPOSAL 角色）确认签收（只有确认没有拒收）→ 链条终结。
 *
 * 红线断言：
 *   · 取回段（门店 → 冷链中心）已取消：return-pool?stage=RETURN、return-plan/generate、return-intake 一律明确报错；
 *   · 拒收即入待回运池，不需要任何登记、也不需要入库确认；
 *   · 已排进回运段的拒收货不能再改签收；
 *   · 回运单不支持拒收（无害化中心只有确认）；
 *   · 装车扫码（D2）：扫码即出单 —— 登记成功立刻生成（或并入）该车处理运单（待发车）；
 *     车牌不符 / 缺车牌 / 带卡号一律拒绝，扫到不属于本车的货转人工；重复扫码幂等不裂单；
 *   · 权限：只有冷链中心能排车；只有无害化中心能确认签收；
 *   · 无害化中心只能看到收货方是自己的回运单（机构边界）。
 *
 * 自持：专用车牌 京A·T0004/T0005；计划自建自取消；批次回归「待发运」；运单全部删除。
 *
 * 运行：node logs/verify-return-leg.mjs
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const MYSQL = process.env.MYSQL_CONTAINER || 'pork-traceability-mysql-1'
import { execSync } from 'node:child_process'

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

/**
 * 清掉本脚本两辆专用车牌遗留的「待排车」扫码记录。
 * 扫码即出单之后，正常情况下扫完当场就被运单吃掉；只有上一轮中途失败/被中断才会留残行。
 * 不清的话下一次「没有扫码不能出单」会被这批旧枪救活，断言变成假绿。
 */
function clearStaleScans() {
  const stmt = `DELETE FROM db_distribution.return_load_scan WHERE status = 0 AND plate_no IN ('${PLATE}', '${RETURN_PLATE}')`
  const escaped = stmt.replace(/"/g, '\\"')
  const cmd = `docker exec ${MYSQL} mysql -u${process.env.DB_USER || 'pork_user'} -p${process.env.DB_PASS || '123456'}`
    + ` --default-character-set=utf8mb4 -N -e "${escaped}" 2>&1 | grep -v Warning || true`
  try { execSync(cmd, { encoding: 'utf8' }) } catch { /* 容器不可达时不影响后续断言 */ }
}

/**
 * 设备通道上报（走完整接入管道：鉴权 → 留档 → 判重 → 处理器 → 业务写入）。
 * 拒收回运装车扫码必须从这条门进，不能直写业务表 —— 要验的正是「现场这一枪」这条链路。
 */
async function deviceReport(deviceKey, payload) {
  const res = await fetch(`${BASE}/ingest/device/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Device-Key': deviceKey },
    body: JSON.stringify(payload),
  })
  const body = await res.json().catch(() => null)
  return { accepted: body?.data?.accepted, status: body?.data?.status, duplicate: body?.data?.duplicate,
    message: body?.data?.message, raw: body }
}
const pad = (n) => String(n).padStart(2, '0')
const today = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }
// 400 天窗口：避免与历史回归的报文时间戳撞秒被接入层判「重复上报」
//（12 小时窗口在连续多轮回归时撞过两次；时间闸门只卡未来，往回补传是合法能力）
const RUN_OFFSET = Math.floor(Math.random() * 400) * 86400 * 1000 + Math.floor(Math.random() * 86400) * 1000
let tickSeq = 0
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
/** 拒收回运装车扫码枪：与装车口是两台设备、两条通道（50-return-load-scan.sql） */
const RETURN_SCAN_DEVICE = 'RETURN-SCAN-001'
const RETURN_SCAN_KEY = 'dev-key-return-scan-001'
const ST_ACCEPTED = 2
const PLATE = '京A·T0004'
const RETURN_PLATE = '京A·T0005'
const CARD = '8801'

console.log('\n=== 拒收回运链回归（单段：冷链中心 → 无害化处理中心）===')

const adminLogin = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const admin = adminLogin?.data?.token
const retailLogin = await api('/auth/login', { method: 'POST', body: { username: 'retail_user', password: '123456' } })
const retail = retailLogin?.data?.token
const distLogin = await api('/auth/login', { method: 'POST', body: { username: 'distribution_user', password: '123456' } })
const dist = distLogin?.data?.token
const dispLogin = await api('/auth/login', { method: 'POST', body: { username: 'disposal_user', password: '123456' } })
const disp = dispLogin?.data?.token
const MY_STORE = Number(retailLogin?.data?.user?.orgId)
const DIST_ORG = Number(distLogin?.data?.user?.orgId)
check('四角色登录（管理员/零售商/冷链中心/无害化中心）',
  !!admin && !!retail && !!dist && !!disp && !!MY_STORE && !!DIST_ORG,
  `store=${MY_STORE} distOrg=${DIST_ORG} disposal=${dispLogin?.data?.user?.role}`)
check('★ 前置：无害化处理中心账号角色 = DISPOSAL', dispLogin?.data?.user?.role === 'DISPOSAL',
  `role=${dispLogin?.data?.user?.role}`)
if (!admin || !retail || !dist || !disp) process.exit(1)

// ── A. 准备：车牌空闲 / 批次 / 门店 / 无害化机构 ──
const unfinished = (records) => (records || []).filter((r) => r.status < 4)
// 自持清理：上一轮中断可能给两辆专用车留下未完成的运单。运单列表缺省只回 NORMAL，
// kind=DISPOSAL 的残留单在这里看不见，必须显式按 kind 查出来删掉。
for (const kind of ['NORMAL', 'DISPOSAL']) {
  const q = (p) => api(`/distribution/transports?keyword=${encodeURIComponent(p)}&kind=${kind}&pageNum=1&pageSize=50`, { token: admin })
  const stale = [...((await q(PLATE))?.data?.records || []), ...((await q(RETURN_PLATE))?.data?.records || [])]
    .filter((t) => t.status < 4)
  for (const t of stale) {
    await api(`/distribution/transports/${t.id || t.transportId}`, { method: 'DELETE', token: admin })
  }
}
const prePlate = await api(`/distribution/transports?keyword=${encodeURIComponent(PLATE)}&pageNum=1&pageSize=50`, { token: admin })
const prePlate2 = await api(`/distribution/transports?keyword=${encodeURIComponent(RETURN_PLATE)}&pageNum=1&pageSize=50`, { token: admin })
const prePlate2d = await api(`/distribution/transports?keyword=${encodeURIComponent(RETURN_PLATE)}&kind=DISPOSAL&pageNum=1&pageSize=50`, { token: admin })
check('★ 前置：两辆专用车牌名下没有未完成运单',
  unfinished(prePlate?.data?.records).length === 0
    && unfinished(prePlate2?.data?.records).length === 0
    && unfinished(prePlate2d?.data?.records).length === 0,
  unfinished(prePlate?.data?.records).concat(unfinished(prePlate2?.data?.records), unfinished(prePlate2d?.data?.records))
    .map((r) => `${r.transportNo}/${r.status}`).join(','))

const pendingRows = (await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token: admin }))?.data?.records || []
check('★ 待发运批次 ≥ 3', pendingRows.length >= 3, `条数=${pendingRows.length}`)
if (pendingRows.length < 3) process.exit(1)

const stores = []
const walk = (nodes) => {
  if (!Array.isArray(nodes)) return
  nodes.forEach((n) => { if (n.type === 'retail') stores.push({ id: n.id, name: n.name }); if (n.children?.length) walk(n.children) })
}
walk((await api('/system/orgs/tree', { token: admin }))?.data || [])
check('★ 前置：无害化处理中心机构在树上（type=disposal）',
  text(await api('/system/orgs/tree', { token: admin })).includes('"disposal"'), '')

const others = stores.filter((s) => s.id !== MY_STORE).slice(0, 2)
const S = [others[0], others[1], { id: MY_STORE, name: stores.find((s) => s.id === MY_STORE)?.name || '本店' }]

const planIds = []
const transportIds = []
const stagingIds = []

async function createThreeStopTransport(tag) {
  const [b1, b2, b3] = pendingRows.splice(0, 3)
  for (let i = 0; i < 3; i++) {
    const res = await api('/distribution/delivery-plans', {
      method: 'POST', token: admin,
      body: {
        planDate: today(), storeId: S[i].id, storeName: S[i].name, splitBatchIds: [[b1, b2, b3][i].id],
        vehicleNo: null, vehicleType: null, refrigeration: null,
        driverName: null, driverPhone: null, origin: null, plannedDepart: null, plannedArrive: null,
        remark: `回运回归-${tag}-P${i + 1}`,
      },
    })
    if (res?.data?.id) planIds.push(res.data.id)
  }
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
    t?.status === 3 && stops[2]?.storeId === MY_STORE, `status=${t?.status}`)
  return { tid, tno: t?.transportNo, stops }
}

// ── B. 拒收 → 待回运池（无需登记、无需入库确认）──
const A = await createThreeStopTransport('配送')
if (!A.tid) { console.log('配送单建不出来，退出'); process.exit(1) }
const rejectStopA = A.stops[2]
const rejected = await api(`/distribution/transports/${A.tid}/stops/reject`, {
  method: 'POST', token: retail, body: { reason: '到货温度 5℃ 断链，表面发黏' },
})
check('★ 门店拒收成功', rejected?.code === 200, brief(rejected))

const pool = (await api('/distribution/return-pool?stage=DISPOSAL', { token: admin }))?.data || []
// 处理计划跨天累积是设计行为：池里可能有以前批次遗留的拒收货（同门店），
// 断言必须按「本轮运单号 + 本店」精确定位刚拒收那一站（站点行没有 id，transportNo 是唯一业务键）
const mine = pool.find((r) => r.storeId === MY_STORE && r.transportNo === A.tno)
check('★ 拒收即入待回运池（含理由/门店/重量快照，无需任何登记与入库确认）',
  !!mine && !!mine.rejectReason && mine.transportNo != null, brief(mine))

// ── C. 取回段已取消 + 排车权限 ──
const poolReturn = await api('/distribution/return-pool?stage=RETURN', { token: admin })
check('★ 取回池已取消：stage=RETURN 明确报错（不返回空池冒充正常）',
  poolReturn?.code !== 200 && text(poolReturn).includes('取回段已取消'), brief(poolReturn))
const genReturnPlan = await api('/distribution/return-plan/generate', { method: 'POST', token: dist, body: {} })
check('★ 取回计划端点已取消：调用返回明确说明', genReturnPlan?.code !== 200 && text(genReturnPlan).includes('取回'),
  brief(genReturnPlan))
const intakeAny = await api('/distribution/transports/1/return-intake', { method: 'POST', token: dist })
check('★ 取回入库端点已取消：调用返回明确说明', intakeAny?.code !== 200 && text(intakeAny).includes('取回'), brief(intakeAny))

const genByRetail = await api('/distribution/disposal-plan/generate', { method: 'POST', token: retail, body: {} })
check('★ 零售商不能生成回运计划（只有冷链中心能排车）',
  genByRetail?.code !== 200 && text(genByRetail).includes('冷链'), brief(genByRetail))
const genByDisposal = await api('/distribution/disposal-plan/generate', { method: 'POST', token: disp, body: {} })
check('★ 无害化中心不能生成回运计划', genByDisposal?.code !== 200, brief(genByDisposal))

// 「待回运（未排车）仍可协商改签收」的例外路径在 verify-reject-flow.mjs 的 F 组覆盖 ——
// 这里不能真改：改了站点就翻成 SIGNED，后面的排车链断掉。

// ── D. 生成计划前必须先扫码（2026-11-03 口径：扫码在前、计划在后）──
// 处理运单按现场扫码结果生成：一枪都没扫就出单，「扫了才排车」这条纪律形同虚设。
// 先清掉上一轮中断留下的残枪，否则这条断言会被旧数据救活成假绿。
clearStaleScans()
const genNoScan = await api('/distribution/disposal-plan/generate', {
  method: 'POST', token: dist,
  body: { vehicles: [{ vehicleNo: RETURN_PLATE, driverName: '回归司机甲', driverPhone: '13800009901' }] },
})
check('★ 没有装车扫码时不能生成计划（明确报错，不按载重偷偷排车）',
  genNoScan?.code !== 200 && text(genNoScan).includes('扫码'), brief(genNoScan))

// ── D2. 装车扫码（专用扫码枪）：车牌 + 货码 → 扫码即出单（2026-11-04 口径）──
// 与装车口（LOAD）同一条体验：登记成功的货立刻生成（或并入）该车的处理运单（待发车），
// 司机随后即可刷卡发车；页面「生成处理运单」按钮降级为兜底（自动出单被拦/漏扫补扫后重试，不再按载重补装）。
const scanCode = mine?.batchNo || mine?.transportNo
const scanTime1 = nextTime()
const scanOk = await deviceReport(RETURN_SCAN_KEY, {
  sourceRef: RETURN_SCAN_DEVICE, bizKey: nextBizKey('RLD'), reportTime: scanTime1,
  data: { vehicleNo: RETURN_PLATE, scanCodes: [scanCode], scanTime: scanTime1 },
})
check('★ 装车扫码：车牌 + 货码 → 已入账且回执说明「已生成处理运单（待发车）」',
  scanOk?.accepted === true && String(scanOk?.message || '').includes('待发车'), brief(scanOk))

const disposalOpen = (await api('/distribution/transports?kind=DISPOSAL&status=1&pageNum=1&pageSize=20', { token: admin }))?.data?.records || []
const leg = disposalOpen.find((t) => t.vehicleNo === RETURN_PLATE)
check('★ 扫码即出单：该车名下出现「待发车」的处理运单',
  !!leg, `rows=${disposalOpen.map((t) => `${t.transportNo}/${t.vehicleNo}`).join(',')}`)
// 运单列表记录的主键字段是 id（transportId 是老字段名，别再用它取值）
const legId = leg?.id ?? leg?.transportId
if (legId) transportIds.push(legId)
const legDetail = legId ? (await api(`/distribution/transports/${legId}`, { token: admin }))?.data : null
check('★ 处理运单：kind=DISPOSAL、收货方=无害化处理中心、起点=冷链中心、终点=无害化处理中心、来源=设备',
  legDetail?.transportKind === 'DISPOSAL' && legDetail?.receiverOrgId === 2001
    && String(legDetail?.destination || '').includes('无害化')
    && String(legDetail?.origin || '').length > 0 && !String(legDetail?.origin || '').includes('无害化')
    && legDetail?.source === 'DEVICE',
  `kind=${legDetail?.transportKind} receiver=${legDetail?.receiverOrgId} origin=${legDetail?.origin} dest=${legDetail?.destination} source=${legDetail?.source}`)
check('★ 扫码出单不带司机：司机等上车那一刻由车载终端刷卡确认（与装车口同一纪律）',
  !legDetail?.driverName && !legDetail?.driverSource,
  `driver=${legDetail?.driverName} source=${legDetail?.driverSource}`)

const poolAfterScan = (await api('/distribution/return-pool?stage=DISPOSAL', { token: admin }))?.data || []
check('★ 扫码即出单后处理计划清空（这站货已排进运单）',
  !poolAfterScan.some((r) => r.transportNo === A.tno && r.storeId === MY_STORE),
  `pool=${poolAfterScan.length}（池里其余是跨天累积的历史拒收货，不算失败）`)

const legStops = (await api(`/distribution/transports/${legId}/stops`, { token: admin }))?.data || []
check('★ 处理运单站点 = 无害化处理中心一站（装了几家门店不影响站点数）',
  legStops.length === 1 && String(legStops[0]?.storeName || '').includes('无害化') && legStops[0]?.status === 0,
  brief(legStops))

const legItemRows = (await api(`/distribution/transports/${legId}/leg-items`, { token: admin }))?.data || []
check('★ 装载明细：逐条列出被拒的货（门店/原运单/批次/货重 + 拒收理由）',
  legItemRows.length >= 1
    && legItemRows.every((r) => r.storeName && r.batchNo && r.weightKg != null)
    && legItemRows.some((r) => String(r.rejectReason || '').length > 0)
    && legItemRows.some((r) => r.sourceTransportNo === mine?.transportNo),
  `items=${legItemRows.length} sample=${JSON.stringify(legItemRows[0] || {})}`)

const scannedRow = legItemRows.find((r) => (r.batchNo || r.sourceTransportNo) === scanCode)
check('★ 扫码证据带进运单：这件标「已扫码」并记下时刻，整单记下扫码设备',
  scannedRow?.scanStatus === 1 && !!scannedRow?.scanTime
    && legDetail?.loadScanDevice === RETURN_SCAN_DEVICE && !!legDetail?.loadScanTime,
  `row=${JSON.stringify(scannedRow || {})} device=${legDetail?.loadScanDevice}`)

const rescanned = await deviceReport(RETURN_SCAN_KEY, {
  sourceRef: RETURN_SCAN_DEVICE, bizKey: nextBizKey('RLD-RE'), reportTime: nextTime(),
  data: { vehicleNo: RETURN_PLATE, scanCodes: [scanCode], scanTime: nextTime() },
})
const openAgain = (await api('/distribution/transports?kind=DISPOSAL&status=1&pageNum=1&pageSize=20', { token: admin }))?.data?.records || []
check('★ 重复扫码幂等：不裂出第二张待发车处理运单',
  openAgain.filter((t) => t.vehicleNo === RETURN_PLATE).length === 1,
  `again=${openAgain.length} rescan=${JSON.stringify(rescanned?.message || '').slice(0, 120)}`)

const unknownScan = await deviceReport(RETURN_SCAN_KEY, {
  sourceRef: RETURN_SCAN_DEVICE, bizKey: nextBizKey('RLD-BAD'), reportTime: nextTime(),
  data: { vehicleNo: RETURN_PLATE, scanCodes: ['NO-SUCH-CODE-001'], scanTime: nextTime() },
})
check('★ 扫到对不上任何拒收站点的货码 → 转人工（上错车或标签不对的第一信号）',
  unknownScan?.status === 1 && String(unknownScan?.message || '').includes('对不上'), brief(unknownScan))

const noPlate = await deviceReport(RETURN_SCAN_KEY, {
  sourceRef: RETURN_SCAN_DEVICE, bizKey: nextBizKey('RLD-NOPLATE'), reportTime: nextTime(),
  data: { scanCodes: [scanCode], scanTime: nextTime() },
})
check('★ 报文缺车牌 → 已拒绝（不知道装的是哪台车，这一枪不成立）',
  noPlate?.status === 3 && String(noPlate?.message || '').includes('车牌'), brief(noPlate))

const withCard = await deviceReport(RETURN_SCAN_KEY, {
  sourceRef: RETURN_SCAN_DEVICE, bizKey: nextBizKey('RLD-CARD'), reportTime: nextTime(),
  data: { vehicleNo: RETURN_PLATE, scanCodes: [scanCode], cardNo: CARD, scanTime: nextTime() },
})
check('★ 报文带司机卡号 → 已拒绝（装车位没有读卡器，司机由车载终端确认）',
  withCard?.status === 3 && String(withCard?.message || '').includes('读卡器'), brief(withCard))

const convertAfterLeg = await api(`/distribution/transports/${A.tid}/stops/convert-receipt`, {
  method: 'POST', token: retail,
  body: { transportId: A.tid, storeId: rejectStopA.storeId, storeName: rejectStopA.storeName, receiver: '回归-改签', qtyCheck: 1, tempCheck: 1, packageIntact: 1 },
})
check('★ 已排进回运段的拒收货不能再改签收（在途货不许凭空消失）',
  convertAfterLeg?.code !== 200 && text(convertAfterLeg).includes('回运'), brief(convertAfterLeg))

const rejectOnLeg = await api(`/distribution/transports/${legId}/stops/reject`, {
  method: 'POST', token: retail, body: { reason: '回运单上再拒一次' },
})
check('★ 回运运单不支持拒收', rejectOnLeg?.code !== 200 && text(rejectOnLeg).includes('回运'), brief(rejectOnLeg))

// ── E. 回运段发车 → 到达 → 无害化中心签收 ──
const confirmEarly = await api(`/distribution/transports/${legId}/disposal-confirm`, { method: 'POST', token: disp })
check('★ 未到货不能确认签收', confirmEarly?.code !== 200, brief(confirmEarly))

// 司机刷卡发车：扫码出单的车没有预排司机，司机身份由车载终端这一刷落库（driver_source=CARD）
const departLeg = await api('/ingest/demo/report', {
  method: 'POST', token: admin,
  body: { deviceNo: ONBOARD_DEVICE, channel: 'DEPART', bizKey: nextBizKey('DEPART'), data: { departTime: nextTime(), vehicleNo: RETURN_PLATE, cardNo: CARD } },
})
stagingIds.push(departLeg?.data?.stagingId)
const legAfterDepart = (await api(`/distribution/transports/${legId}`, { token: admin }))?.data
check('★ 司机刷卡发车（扫码出单的车直接由车载终端推进，司机随卡落库）',
  departLeg?.data?.status === ST_ACCEPTED && legAfterDepart?.status === 2
    && !!legAfterDepart?.driverName && legAfterDepart?.driverConfirmed === 1,
  `report=${brief(departLeg)} driver=${legAfterDepart?.driverName}/${legAfterDepart?.driverSource}`)

// 回运也是冷链：在途期间必须能采到温度（页面「运输详情」的温度曲线靠这条数据）
await api('/distribution/internal/temperature/sample?force=true', { method: 'POST', token: admin })
const legTemps = (await api(`/distribution/transports/${legId}/temperature`, { token: admin }))?.data || []
check('★ 回运途中产生温度记录（越界/断档都有处可查）',
  legTemps.length >= 1 && legTemps[0]?.temperature != null,
  `logs=${legTemps.length}`)
const arriveLeg = await api(`/distribution/transports/${legId}/arrive`, { method: 'PUT', token: dist })
check('★ 到达无害化处理中心（单站运单一次到位 → 待签收 3）', arriveLeg?.code === 200, brief(arriveLeg))

const listForDisposal = (await api('/distribution/transports?kind=DISPOSAL&pageNum=1&pageSize=20', { token: disp }))?.data?.records || []
check('★ 无害化中心在回运单列表里看得到这单（机构边界内）',
  listForDisposal.some((t) => t.id === legId), `rows=${listForDisposal.length}`)

const confirmByDist = await api(`/distribution/transports/${legId}/disposal-confirm`, { method: 'POST', token: dist })
check('★ 冷链中心不能替无害化中心确认签收（收货方才能签）', confirmByDist?.code !== 200, brief(confirmByDist))
const rejectOnDisposal = await api(`/distribution/transports/${legId}/stops/reject`, {
  method: 'POST', token: disp, body: { reason: '回运单上想拒收' },
})
check('★ 回运段没有拒收这个动作（DISPOSAL 角色也没有）',
  rejectOnDisposal?.code !== 200 && text(rejectOnDisposal).includes('拒收'), brief(rejectOnDisposal))

const confirm = await api(`/distribution/transports/${legId}/disposal-confirm`, { method: 'POST', token: disp })
check('★ 无害化处理中心确认签收（链条终结标志 chainClosed）',
  confirm?.code === 200 && confirm?.data?.chainClosed === true, brief(confirm))
const confirmAgain = await api(`/distribution/transports/${legId}/disposal-confirm`, { method: 'POST', token: disp })
check('★ 重复确认被拦（终点动作不可重入）', confirmAgain?.code !== 200, brief(confirmAgain))

const poolEnd = (await api('/distribution/return-pool?stage=DISPOSAL', { token: admin }))?.data || []
check('★ 链终：本站货已走完全程（池里只剩跨天累积的历史拒收货）',
  !poolEnd.some((r) => r.transportNo === A.tno && r.storeId === MY_STORE), `pool=${poolEnd.length}`)

// ── F. 收尾：自持清理（删回运单 → 删配送单 → 取消计划）──
console.log('\n== F. 收尾清理 ==')
if (process.env.KEEP === '1') {
  // 留证模式：截图脚本要一张带温度/明细的回运单，这里先不删
  console.log(`    跳过清理（KEEP=1）：leg=${legId} origin=${A.tid} plans=${planIds.join(',')}`)
  console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===`)
  process.exit(fail ? 1 : 0)
}
let cleanOk = true
for (const tid of [legId, A.tid].filter(Boolean)) {
  const del = await api(`/distribution/transports/${tid}`, { method: 'DELETE', token: admin })
  if (del?.code !== 200) { cleanOk = false; console.log(`    删除运单 ${tid} 失败：${brief(del)}`) }
}
for (const pid of planIds) {
  const res = await api(`/distribution/delivery-plans/${pid}/cancel`, { method: 'POST', token: admin, body: { reason: '回运回归收尾' } })
  if (res?.code !== 200) { cleanOk = false; console.log(`    取消计划 ${pid} 失败：${brief(res)}`) }
}
check('★ 清理：运单删除 + 计划取消', cleanOk, '')
const pendingAfter = (await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token: admin }))?.data?.records || []
check('★ 批次全部回到「待发运」（回归不留痕）',
  pendingAfter.length >= 3 && pendingRows.every((b) => pendingAfter.some((p) => p.id === b.id)),
  `before=${pendingRows.length} after=${pendingAfter.length}`)
const plateAfter = (await api(`/distribution/transports?keyword=${encodeURIComponent(PLATE)}&pageNum=1&pageSize=20`, { token: admin }))?.data?.records || []
const plateAfter2 = (await api(`/distribution/transports?keyword=${encodeURIComponent(RETURN_PLATE)}&pageNum=1&pageSize=20`, { token: admin }))?.data?.records || []
// kind=DISPOSAL 的残留单在缺省列表里看不见，必须显式查一遍（上一轮吃过这个假绿的亏）
const plateAfter2d = (await api(`/distribution/transports?keyword=${encodeURIComponent(RETURN_PLATE)}&kind=DISPOSAL&pageNum=1&pageSize=20`, { token: admin }))?.data?.records || []
check('★ 两辆专用车牌名下无未完成运单（车辆放行口径；历史已完成单不算占用）',
  unfinished(plateAfter).length === 0 && unfinished(plateAfter2).length === 0 && unfinished(plateAfter2d).length === 0,
  unfinished(plateAfter).concat(unfinished(plateAfter2), unfinished(plateAfter2d))
    .map((r) => `${r.transportNo}/${r.status}`).join(','))

console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===`)
if (failures.length) console.log('失败项：\n  - ' + failures.join('\n  - '))
process.exit(fail ? 1 : 0)
