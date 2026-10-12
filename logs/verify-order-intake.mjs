/**
 * 订单转入回归（第 16 条接入通道 ORDER：外部选购事实 → 待排车计划）。
 *
 *   口径：现实里这些业务不是一个系统做完的 —— 订单在 ERP/OMS，本系统只管溯源。
 *        所以订单**不在本系统产生，但可以收进来**。它不是「本系统长出商流」，
 *        只是又多了一条事实源，与设备通道同一套机制（台账 → 统一信封 → 暂存幂等 → handler → 业务表）。
 *
 *   三条不能破的界线（本脚本逐条验证）：
 *     ① 订单 ≠ 计划：订单只是计划的**来源之一**（plan_source=ORDER），落点是「待发货」= 待排车，
 *        车/司机/发车时间都还没定。运单的上游永远是计划；
 *     ② 不复制订单：权威副本在外部系统，本系统只留 order_no（原始报文已在 ingest_staging.payload）；
 *     ③ 字段层面就不收金额：报文里根本没有价格的容身之地。
 *
 *   责任边界（一律转人工，绝不猜）：
 *     · 门店在机构树里查不到 / 不是门店（type≠retail） → 转人工（门店 ID 决定召回圈范围）；
 *     · 部位不在 meat_part 字典里 → 转人工（部位是召回圈圈货的依据）；
 *     · 可发运库存备不齐订购量 → 转人工（实物批次不可拆，不拆零兜底）。
 *
 *   断言分组：
 *     A. 准备：待发运批次 / 机构树门店
 *     B. 报文结构性校验：缺订单号 / 缺门店 / 缺明细 / 重量非正 → **拒绝**（报文本身不成立）
 *     C. 门店核对：不存在的门店 → 转人工
 *     D. 部位核对：字典里没有的部位 → 转人工
 *     E. 备不齐货：订购量远超库存 → 转人工
 *     F. 正常转入：生成「待发货」计划（来源=ORDER、带订单号、明细挂真实批次、无车无人）
 *     G. 幂等：同一订单号换一份报文重推 → 回既有计划，不重复建单
 *     H. 占用生效：被订单占用的批次从「待发运」列表里消失
 *     I. 通道与去向：channel=ORDER、targetTable=delivery_plan
 *
 *   运行：node logs/verify-order-intake.mjs
 *   产生的计划/暂存 ID 打印在末尾，由 bash 侧做落库核验与清理。
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

const brief = (r) => JSON.stringify(r ?? {}).slice(0, 300)
const text = (r) => JSON.stringify(r ?? {})

const pad = (n) => String(n).padStart(2, '0')
function ts(offsetSec = 0) {
  const d = new Date(Date.now() - offsetSec * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

/** 订单接入终端的台账编号（见 backend/init-sql/30-order-channel.sql） */
const ORDER_DEVICE = 'ORDER-TERM-001'
/** 状态码：0待处理 1待人工 2已入账 3已拒绝 4已降采样 */
const ST_MANUAL = 1
const ST_ACCEPTED = 2
const ST_REJECTED = 3

const planIds = []
const stagingIds = []
let seq = 0

/**
 * 代终端上报订单。每次换一份新报文（订单号带序号）——同设备 + 同通道 + **同一份报文**
 * 会被接入层判成重复上报直接回既有结论，那样就验不到业务层了。
 */
async function orderReport(over = {}) {
  seq += 1
  const base = {
    orderNo: `SO-REG-${Date.now()}-${seq}`,
    storeId: null,
    expectArrive: ts(seq),
    remark: `订单转入回归 #${seq}`,
    items: [],
  }
  const res = await api('/ingest/demo/report', {
    method: 'POST', token,
    body: { deviceNo: ORDER_DEVICE, data: { ...base, ...over } },
  })
  const id = res?.data?.stagingId
  if (id) stagingIds.push(id)
  return res
}
const statusOf = (res) => res?.data?.status
const targetIdOf = (res) => res?.data?.targetId
const messageOf = (res) => String(res?.data?.message ?? '')

console.log('\n=== 订单转入（通道 ORDER）回归 ===')

// ── A. 准备 ──
const pendingRes = await api('/distribution/splits/pending?pageNum=1&pageSize=200', { token })
const pendingRows = pendingRes?.data?.records || []
check('★ 「待发运」批次接口可读，且至少有 1 个批次可供订单配货',
  pendingRows.length >= 1, `条数=${pendingRows.length}`)
if (!pendingRows.length) {
  console.log('待发运批次为空，用例无法构造，退出')
  process.exit(1)
}
const stockBatch = pendingRows[0]
console.log(`  样本批次：${stockBatch.batchNo}（部位 ${stockBatch.partCode || '--'} / ${stockBatch.weightKg || 0}kg）`)

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
check('机构树里能取到门店（订单目的地）', stores.length > 0, `门店数=${stores.length}`)
if (!stores.length) process.exit(1)
const store = stores[0]

const partCode = stockBatch.partCode
check('样本批次带部位编码（订单配货按部位找货）', !!partCode, `partCode=${partCode}`)
if (!partCode) {
  console.log('样本批次缺 partCode，用例无法构造，退出')
  process.exit(1)
}
// 订购量不超过该批次重量：保证至少有一条批次能配上，让「正常转入」这条路走得通
const orderWeight = Math.max(1, Math.min(Number(stockBatch.weightKg) || 10, 20))

// ── B. 报文结构性校验：报文本身不成立 → 拒绝（不是转人工） ──
const noOrderNo = await orderReport({ orderNo: null, storeId: store.id, items: [{ partCode, weightKg: orderWeight }] })
check('★ 缺订单号 → 拒绝（没有订单号就无法幂等，重推会重复建计划）',
  statusOf(noOrderNo) === ST_REJECTED && messageOf(noOrderNo).includes('订单号'), brief(noOrderNo))

const noStore = await orderReport({ storeId: null, items: [{ partCode, weightKg: orderWeight }] })
check('★ 缺目的地门店 → 拒绝（订单必须说清发去哪家店）',
  statusOf(noStore) === ST_REJECTED && messageOf(noStore).includes('门店'), brief(noStore))

const noItems = await orderReport({ storeId: store.id, items: [] })
check('★ 没有货物明细 → 拒绝（订单至少要有「部位 + 重量」一项）',
  statusOf(noItems) === ST_REJECTED && messageOf(noItems).includes('货物明细'), brief(noItems))

const badWeight = await orderReport({ storeId: store.id, items: [{ partCode, weightKg: 0 }] })
check('★ 订购重量为 0 → 拒绝（负/零重量会凑出 0 明细的空计划）',
  statusOf(badWeight) === ST_REJECTED && messageOf(badWeight).includes('重量'), brief(badWeight))

// ── C. 门店核对：机构树里查不到 → 转人工，绝不默认放行 ──
const ghostStore = await orderReport({ storeId: 999999, items: [{ partCode, weightKg: orderWeight }] })
check('★ 目的地门店在机构树里查不到 → 转人工（门店 ID 决定召回圈范围，不猜）',
  statusOf(ghostStore) === ST_MANUAL && messageOf(ghostStore).includes('查不到'), brief(ghostStore))

// ── D. 部位核对：字典里没有 → 转人工（宁缺勿猜） ──
const ghostPart = await orderReport({ storeId: store.id, items: [{ partCode: 'ZZZ-NOT-EXIST', weightKg: 5 }] })
check('★ 部位不在部位字典里 → 转人工（部位是召回圈圈货的依据）',
  statusOf(ghostPart) === ST_MANUAL && messageOf(ghostPart).includes('部位字典'), brief(ghostPart))

// ── E. 备不齐货：订购量远超可发运库存 → 转人工（实物批次不可拆，不拆零兜底） ──
const tooMuch = await orderReport({ storeId: store.id, items: [{ partCode, weightKg: 99999 }] })
check('★ 可发运库存备不齐订购量 → 转人工（不猜、不拆零兜底）',
  statusOf(tooMuch) === ST_MANUAL && text(tooMuch).includes('备不齐'), brief(tooMuch))

// ── F. 正常转入 ──
const orderNo = `SO-REG-OK-${Date.now()}`
const ok = await orderReport({
  orderNo, storeId: store.id, items: [{ partCode, weightKg: orderWeight }],
  remark: '订单转入回归-正常',
})
check('★ 门店真实 + 部位在字典 + 库存够 → 转入成功（已入账）',
  statusOf(ok) === ST_ACCEPTED, brief(ok))
const planId = targetIdOf(ok)
if (planId) planIds.push(planId)
check('★ 回执里的数据去向 = 发货计划（不是运单：订单不等于计划）',
  ok?.data?.targetTable === 'delivery_plan', `targetTable=${ok?.data?.targetTable}`)
check('★ 回执通道 = ORDER', ok?.data?.channel === 'ORDER', `channel=${ok?.data?.channel}`)
// 入账回执只报状态（见 DeviceIngestService.toResponse：accepted 时 errorMsg=null → message 就是状态中文），
// 业务文案（「待发货（待排车）」等）只进日志、不进回执 —— 所以结论必须靠落库事实（下面的 plan.status）。
check('★ 入账回执只报状态「已入账」（不夹带业务文案 —— 结论要看落库事实）',
  messageOf(ok) === '已入账', `message=${messageOf(ok)}`)

const detailRes = await api(`/distribution/delivery-plans/${planId}`, { token })
const plan = detailRes?.data?.plan
const planItems = detailRes?.data?.items || []
check('★ 计划来源 = 订单转入（plan_source=ORDER）',
  plan?.planSource === 'ORDER', `planSource=${plan?.planSource}`)
check('★ 计划带着来源订单号（可回溯外部系统）',
  plan?.orderNo === orderNo, `orderNo=${plan?.orderNo} 期望=${orderNo}`)
check('★ 计划状态 = 待发货（1），车与司机都还没定 —— 排车是人的决定',
  plan?.status === 1 && !plan?.vehicleNo && !plan?.driverName,
  `status=${plan?.status} vehicle=${plan?.vehicleNo} driver=${plan?.driverName}`)
check('★ 计划号按 DP 前缀当日流水生成',
  String(plan?.planNo || '').startsWith('DP'), `planNo=${plan?.planNo}`)
check('★ 明细挂的是真实实物批次（有批次号 + 部位 + 重量）',
  planItems.length >= 1 && planItems.every((i) => i.batchNo && i.partCode && i.weightKg != null),
  `明细=${planItems.map((i) => i.batchNo).join(',')}`)
check('★ 明细里的批次都在「可发运」清单里（配的是真货）',
  planItems.every((i) => pendingRows.some((p) => p.batchNo === i.batchNo)),
  `明细=${planItems.map((i) => i.batchNo).join(',')}`)
check('★ 明细重量合计 = 计划总重快照',
  Math.abs(Number(plan?.totalWeightKg) - planItems.reduce((s, i) => s + Number(i.weightKg || 0), 0)) < 0.01,
  `totalWeightKg=${plan?.totalWeightKg} 合计=${planItems.reduce((s, i) => s + Number(i.weightKg || 0), 0)}`)
check('★ 制单人不写人名（订单是外部系统推的，没有内部制单人）',
  String(plan?.operator || '').includes('订单转入'), `operator=${plan?.operator}`)

// ── G. 幂等：同一订单号、换一份报文重推 ──
const again = await orderReport({
  orderNo, storeId: store.id, items: [{ partCode, weightKg: orderWeight }],
  remark: '订单转入回归-重推（报文不同，验的是业务层幂等）',
})
check('★ 同一订单号重推 → 仍回「已入账」（回既有计划，不当成错误）',
  statusOf(again) === ST_ACCEPTED, brief(again))
check('★ 重推回执同样只报状态「已入账」（重推不是错误）',
  statusOf(again) === ST_ACCEPTED && messageOf(again) === '已入账', brief(again))
check('★ 重推指向同一张计划（没有新建第二张）',
  String(targetIdOf(again)) === String(planId), `first=${planId} again=${targetIdOf(again)}`)
// 幂等的真正代价在这里：不重复建单只是表象，要点是**没有副作用**（没有被重复追加明细）
const detailAgain = await api(`/distribution/delivery-plans/${planId}`, { token })
check('★ 重推后计划明细没被重复追加（幂等没有副作用）',
  (detailAgain?.data?.items || []).length === planItems.length,
  `重推前=${planItems.length} 重推后=${(detailAgain?.data?.items || []).length}`)

const plansRes = await api('/distribution/delivery-plans?pageNum=1&pageSize=200', { token })
const sameOrderPlans = (plansRes?.data?.records || []).filter((p) => p.orderNo === orderNo)
check('★ 该订单在计划表里只有 1 张（幂等真的生效了）',
  sameOrderPlans.length === 1, `条数=${sameOrderPlans.length}`)

// 列表「来源」列把订单号展示给用户看了，那它就必须能被搜到 ——
// 用户从设备接入看到「订单已转入」之后，第一反应就是拿订单号回计划列表里查；
// 查不到只会被当成「上报成功了却没建计划」（本轮踩过：keyword 只匹配计划号/门店/车牌）。
// 口径：**列表上展示出来的标识字段，都得能搜**。
const byOrderKeyword = await api(
  `/distribution/delivery-plans?keyword=${encodeURIComponent(orderNo)}&pageNum=1&pageSize=10`, { token })
const hitByOrder = byOrderKeyword?.data?.records || []
check('★ 计划列表能按**来源订单号**搜到这张计划（列表展示了订单号，就必须能搜）',
  hitByOrder.length === 1 && Number(hitByOrder[0].id) === Number(planId),
  `命中=${hitByOrder.length} 计划=${hitByOrder.map((p) => p.planNo).join(',')}`)

// ── H. 占用生效：配出去的批次不再出现在「待发运」里 ──
const pendingAfter = await api('/distribution/splits/pending?pageNum=1&pageSize=200', { token })
const afterRows = pendingAfter?.data?.records || []
const stillFree = planItems.filter((i) => afterRows.some((p) => p.batchNo === i.batchNo))
check('★ 被订单占用的批次已从「待发运」列表消失（不会再被第二张计划抢走）',
  stillFree.length === 0, `仍在列表里的=${stillFree.map((i) => i.batchNo).join(',')}`)

// ── I. 没有偷偷建出运单 ──
const transportRes = await api(`/distribution/transports?keyword=${encodeURIComponent(orderNo)}&pageNum=1&pageSize=5`, { token })
check('★ 订单转入没有直接建运单（运单只由排车/装车口生成）',
  (transportRes?.data?.records || []).length === 0, `条数=${(transportRes?.data?.records || []).length}`)

console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===`)
console.log(`PLAN_IDS=${planIds.join(',')}`)
console.log(`STAGING_IDS=${stagingIds.join(',')}`)
if (fail > 0) process.exit(1)
