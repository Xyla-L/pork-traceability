/**
 * 装车口自动建单回归（冷链运单自动化的第 2 层：设备通道 LOAD）。
 *
 *   口径：装车口（1 号装车位上的两件套：车牌识别相机 + 扫码枪）
 *        **不产生任何业务判定**。它只提供两个现场事实——哪台车、哪几批货真的上了车；
 *        系统拿这两样去**倒查**待发货计划：扫到的批次属于哪张计划，就发到那张计划的目的地去。
 *        扫码是「确认」不是「选择」——目的地是人在计划里早就定死的。
 *
 *   司机（2026-10-10 起收敛，口径见后端 support/DriverSource）：
 *     现场**只在上车那一刻刷一次卡**（车载终端 DEPART 通道），司机本人在装车时还没上车，
 *     装车口也没有读卡器。所以这里：
 *     ⓐ 司机**允许留空**（常态），留空不转人工、照常建单 —— 责任分界点挪到了发车；
 *     ⓑ 报了明文姓名 → 用报文的，来源一律「人工录入、非本人确认」（装卸工/调度代填）；
 *     ⓒ **报了卡号 → 结构性拒绝**：这条通道物理上读不到卡，报了说明设备接错点位
 *        （把驾驶室的读卡器算到装车位），早报错早修；
 *     ⓓ 「货要走了责任必须落到具体人」没有破：人工「立即发车」与 DEPART 都会拦住没有司机的运单
 *        （发车侧的红线见 verify-depart.mjs）。
 *
 *   四道责任边界（本脚本逐条验证）：
 *     ① 目的地不由设备推断：批次不属于任何计划、或分属多张计划 → 转人工，绝不猜；
 *     ② 司机由发车那端落到人（本通道不采集，留空照常建单，见上）；
 *     ③ 车牌与计划安排不一致 → 转人工（最可能的解释就是装错车）；
 *     ④ 一批货只能上一次车：已由运单发运过的批次再扫 → 拒绝。
 *
 *   「同一趟车继续装」并成一张运单（2026-10-10 起）：
 *     同一车牌 + 装车口生成 + **还没发车** + 落在装车作业窗口内（默认 120 分钟）→
 *     本批货**追加**进那张运单（站点按门店归并），而不是再开一张 —— 这正是以前
 *     「同一车牌分两次扫码裂成两张运单」的地方。已经发走的运单不在候选里，
 *     所以分车不受影响：**第一车开走了，第二车才另开一张**。
 *
 *   分车（一车装不完，剩下的等下一车）也在本脚本内验证：
 *     「待发运」的排除依据是「计划状态 ∈ {待发货, 部分排车} 且明细未排车」，
 *     不是持久化标记位，所以已取消计划、已排车明细都会自动回到可勾选状态。
 *
 *   断言分组：
 *     A. 准备：待发运批次 / 门店 / 三张计划（A 车人留空、B 车人已定、C 用于分车）
 *     B. 报文字段校验：缺车牌 → 拒绝；缺批次号 → 拒绝；**报卡号 → 拒绝（装车口没有读卡器）**
 *     C. 司机留空：**不填司机也能建单**，运单司机为空、来源留空（等发车刷卡确认）
 *     D. 转人工三例：批次不属于任何计划 / 车牌与计划不一致
 *     E. 命中建单（不报司机）：运单事实（来源=DEVICE、目的地=计划门店、司机留空）
 *     E2/E3. **同一趟车继续装**：同车牌再扫 → 并入那张待发车运单（同门店并成一站，新门店接在后面）
 *     F. 计划回填：状态转「已排车」、车次计数、明细逐条标记已排车
 *     G. 重复装车 → 拒绝
 *     H. 计划 B（现场姓名与计划安排的人一致）→ 来源记「人工录入、未确认」
 *     I. 分车：只扫一部分 → 计划转「部分排车」；剩下那批仍被计划占着（不能手工抢发）
 *     J. 手工建单的两道新拦截（已被计划占用 / 已经排进过运单的批次）
 *     K. 分车第二车：**第一车先发车**，同车牌再扫才另开一张 → 计划转「已排车」，两张运单货物不重叠
 *     L. 装车口生成的运单走同一条生命周期（**补上司机后可发车**）
 *
 *   运行：node logs/verify-load.mjs
 *   产生的计划/运单/暂存 ID 打印在末尾，由 bash 侧做落库核验与清理。
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

const brief = (r) => JSON.stringify(r ?? {}).slice(0, 260)
const text = (r) => JSON.stringify(r ?? {})

const pad = (n) => String(n).padStart(2, '0')
const today = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }

/** 每次上报给一个不同的装车时刻：同设备 + 同通道 + 同一份报文会被判重复上报而直接回既有结论 */
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

// 装车口不读卡（现场只在上车那一刻刷一次卡），所以这里不再有「姓名 → 卡号」对照；
// 刷卡那条路（含卡号换人、本人确认）在 verify-depart.mjs 里验证。
const LOAD_DEVICE = 'LOADER-001'
/** 状态码：0待处理 1待人工 2已入账 3已拒绝 4已降采样 */
const ST_MANUAL = 1
const ST_ACCEPTED = 2
const ST_REJECTED = 3

const planIds = []
const transportIds = []
const stagingIds = []

/** 代设备上报（走完整链路：鉴权/校验/防重复/写入业务），并记录暂存 id 供清理 */
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

console.log('\n=== 装车口自动建单回归 ===')

// ── A. 准备 ──
const pendingRes = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingRows = pendingRes?.data?.records || []
check('★ 「待发运」批次接口可读，且至少 10 个批次（本脚本要占用其中 9 个）',
  pendingRows.length >= 10, `条数=${pendingRows.length}`)
if (pendingRows.length < 10) {
  console.log('待发运批次不足 10 条，用例无法构造，退出')
  process.exit(1)
}
const [b1, b2, b3, b4, b5, b6, b7, b8, b9, b10] = pendingRows
console.log(`  样本批次：${[b1, b2, b3, b4, b5, b6, b7].map((b) => b.batchNo).join(' / ')}`)
console.log(`  留作物料外批次（不属于任何计划）：${b8.batchNo}`)
console.log(`  留作「同一趟车继续装」的两批：${b9.batchNo} / ${b10.batchNo}`)

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
check('机构树里能取到至少 2 家门店（计划目的地；E3 组要换一家店验证「新门店接在后面」）',
  stores.length > 1, `门店数=${stores.length}`)
if (stores.length < 2) process.exit(1)
const store = stores[0]
const storeB = stores[1]

// storeOverride：E3 组要一家**不同**的门店（验证「新门店接在最大站号之后新起一站」）
async function createPlan(splitBatchIds, { vehicleNo = null, driverName = null, remark = '装车口回归', storeOverride = null } = {}) {
  const dest = storeOverride || store
  const res = await api('/distribution/delivery-plans', {
    method: 'POST', token,
    body: {
      planDate: today(), storeId: dest.id, storeName: dest.name, splitBatchIds,
      vehicleNo, vehicleType: vehicleNo ? '冷藏车' : null, refrigeration: vehicleNo ? '机械制冷' : null,
      driverName, driverPhone: driverName ? '13900000001' : null,
      origin: null, plannedDepart: null, plannedArrive: null, remark,
    },
  })
  const plan = res?.data
  if (plan?.id) planIds.push(plan.id)
  return plan
}

// 计划 A：车与司机都留空 —— 验证「设备把车与人补齐」
const planA = await createPlan([b1.id, b2.id, b3.id], { remark: '装车口回归-A（车人留空）' })
check('★ 准备：建计划 A（车与司机留空），状态=待发货',
  planA?.status === 1 && planA?.planNo?.startsWith('DP'), `planNo=${planA?.planNo} status=${planA?.status}`)
if (!planA?.id) { console.log('计划 A 建不出来，退出'); process.exit(1) }

// 计划 B：车牌与司机已定 —— 验证「现场识别到的车牌必须与计划一致」
const planB = await createPlan([b4.id, b5.id], {
  vehicleNo: '京A·99999', driverName: '李四', remark: '装车口回归-B（车人已定）',
})
check('★ 准备：建计划 B（车牌 京A·99999 / 司机 李四）',
  planB?.status === 1 && planB?.vehicleNo === '京A·99999', `planNo=${planB?.planNo}`)
if (!planB?.id) { console.log('计划 B 建不出来，退出'); process.exit(1) }

// 计划 C：车人留空，用于分车（先扫一批，再扫剩下那批）
const planC = await createPlan([b6.id, b7.id], { remark: '装车口回归-C（分车）' })
check('★ 准备：建计划 C（两批货，留作分车用）',
  planC?.status === 1 && planC?.itemCount === 2, `planNo=${planC?.planNo} itemCount=${planC?.itemCount}`)
if (!planC?.id) { console.log('计划 C 建不出来，退出'); process.exit(1) }

// ── B. 报文字段校验（物理上必然读到的字段缺了 → 拒绝，不是转人工） ──
const noPlate = await loadReport({ batchNos: [b1.batchNo], driverName: '张师傅' })
check('★ 缺车牌号 → 拒绝', statusOf(noPlate) === ST_REJECTED, brief(noPlate))

const noBatch = await loadReport({ vehicleNo: '京A·12345', driverName: '张师傅' })
check('★ 一个批次号都没扫到 → 拒绝', statusOf(noBatch) === ST_REJECTED, brief(noBatch))

// 装车口**没有读卡器**（现场只在上车那一刻刷一次卡）：报了卡号说明设备接错点位
// （把驾驶室里的读卡器算到了装车位）。这是「报文不成立」而不是「条件没到位」，
// 所以是**拒绝**（不是转人工）：早报错早修，别让一个读不到的身份悄悄混进运单。
const cardAtLoad = await loadReport({ vehicleNo: '京A·12345', cardNo: '8801', batchNos: [b1.batchNo] })
check('★ 装车口报司机卡号 → 拒绝（这条通道没有读卡器，报了说明设备接错点位）',
  statusOf(cardAtLoad) === ST_REJECTED && text(cardAtLoad).includes('没有读卡器'), brief(cardAtLoad))

// ── C. 司机：装车口**不采集**，允许留空 ──
// 现场只在上车那一刻刷一次卡（车载终端）。装车时司机还没上车，装车口也没有读卡器，
// 所以「司机为空」是**正常中间态**：留空不转人工、照常建单；
// 「货要走了，责任必须落到具体人」那颗闸门挪到了发车（见 L 组与 verify-depart.mjs）。
// 具体断言并到 E 组一起做（那一组正是不报司机的装车）。

// ── D. 两种「说不清」一律转人工，绝不猜 ──
const orphan = await loadReport({ vehicleNo: '京A·12345', driverName: '张师傅', batchNos: [b8.batchNo] })
check('★ 批次不属于任何待发货计划 → 转人工（设备不猜目的地）',
  statusOf(orphan) === ST_MANUAL && text(orphan).includes('不属于任何待发货计划'), brief(orphan))

const wrongPlate = await loadReport({ vehicleNo: '京A·88888', driverName: '李四', batchNos: [b4.batchNo] })
check('★ 现场车牌与计划安排的（京A·99999）不一致 → 转人工核对是否装错车',
  statusOf(wrongPlate) === ST_MANUAL && text(wrongPlate).includes('不一致'), brief(wrongPlate))

const ghost = await api('/distribution/transports?keyword=京A·88888&pageNum=1&pageSize=5', { token })
check('★ 车牌不一致的那次没有偷偷建出运单',
  (ghost?.data?.records || []).length === 0, `条数=${(ghost?.data?.records || []).length}`)

// ── E. 命中：计划 A 三批一起装车（**不报司机** —— 装车口不采集司机，这才是常态） ──
const okA = await loadReport({
  vehicleNo: '京A·12345', driverPhone: '13700000002',
  batchNos: [b1.batchNo, b2.batchNo, b3.batchNo],
})
check('★ 扫到的货属于唯一一张待发货计划 → 自动建单成功（已入账）',
  statusOf(okA) === ST_ACCEPTED, brief(okA))
check('★ 司机为空不转人工：留空照常建单（责任闸门在发车那一步，见 L 组）',
  statusOf(okA) === ST_ACCEPTED && !text(okA).includes('没有司机'), brief(okA))
// 成功回执只报状态（message 硬编码「已入账」）：装车口这趟到底有没有司机，如实落在运单上，
// 不靠回执夹带业务附言 —— 回执文案不是数据源，断言只能认落库/详情里的事实（见下一条）。
check('★ 入账回执只报「已入账」，不夹带业务附言（司机口径以运单详情为准）',
  okA?.data?.message === '已入账', brief(okA))
const transportAId = targetIdOf(okA)
if (transportAId) transportIds.push(transportAId)

const tA = await api(`/distribution/transports/${transportAId}`, { token })
const transportA = tA?.data
check('★ 运单目的地 = 计划里的门店（不是设备猜的）',
  transportA?.destination === store.name, `destination=${transportA?.destination} 期望=${store.name}`)
check('★ 运单状态 = 待发车（1），可直接进入生命周期',
  transportA?.status === 1, `status=${transportA?.status}`)
check('★ 车牌取自现场；司机如实留空、来源也留空（没人是本人来证明身份，别假装「人工录入」）',
  transportA?.vehicleNo === '京A·12345' && !transportA?.driverName
  && !transportA?.driverSource && transportA?.driverConfirmed === 0,
  `vehicleNo=${transportA?.vehicleNo} driverName=${transportA?.driverName} `
  + `driverSource=${transportA?.driverSource} driverConfirmed=${transportA?.driverConfirmed}`)
check('★ 车型由「车牌 → 车辆备案」带出（计划没填车型也能补齐）',
  transportA?.vehicleType === '冷藏车', `vehicleType=${transportA?.vehicleType}`)
check('★ 运单来源标记 = DEVICE、来源设备 = LOADER-001（能分辨是谁建的）',
  transportA?.source === 'DEVICE' && transportA?.sourceRef === LOAD_DEVICE,
  `source=${transportA?.source} sourceRef=${transportA?.sourceRef}`)

// ── E2/E3. 同一趟车继续装：同车牌又扫了货上来 → 追加进那张还没发车的运单，不再开新单 ──
// 现场是人来一批、扫一批的：工人按车厢分区扫，或分属两张单据的货先后搬上车，
// 都会出现「同一车牌短时间内再次扫码」。以前这里会**裂成两张「待发车」运单**
// （同一台车、同一家店），司机发车时按车牌倒查又撞上「多张待发车」直接转人工 ——
// 一份现场作业变成两条待办。
// 判据：同车牌 + 装车口生成 + **还没发车** + 落在装车作业窗口内（默认 120 分钟）。
const planD = await createPlan([b9.id], { remark: '装车口回归-D（同门店补货）' })
check('★ 准备：建计划 D（与计划 A 同一家店，单批）',
  planD?.status === 1 && planD?.itemCount === 1, `planNo=${planD?.planNo}`)

const mergeSameStore = await loadReport({ vehicleNo: '京A·12345', batchNos: [b9.batchNo] })
// 判据只能是**落库/详情里的事实**：成功回执按老规矩只报「已入账」+ 去向（targetId），
// 不回带业务附言（附录里的那句「并入运单 X」是给日志看的，不是数据源）。
// 所以这里认两件事：回执指向的就是 A 那一张，且它随后真的多了这批货（下一条按站点核）。
check('★ ★ [并入] 同车牌再次扫码（同一家店）→ 回执指向的仍是那张还没发车的运单（没有新开一张）',
  statusOf(mergeSameStore) === ST_ACCEPTED && targetIdOf(mergeSameStore) === transportAId,
  brief(mergeSameStore))

const stopsAfterSame = await api(`/distribution/transports/${transportAId}/stops`, { token })
check('★ [并入] 同一家店的货并进同一站：站点数仍为 1、本站批次 3 → 4（没把一家店算成两站）',
  (stopsAfterSame?.data?.length ?? -1) === 1 && stopsAfterSame?.data?.[0]?.batchCount === 4,
  `站点=${JSON.stringify(stopsAfterSame?.data)}`)

const detailD = await api(`/distribution/delivery-plans/${planD.id}`, { token })
check('★ [并入] 计划 D 的明细指向那张既有运单，计划状态同步转「已排车」',
  detailD?.data?.items?.[0]?.transportId === transportAId && detailD?.data?.plan?.status === 2,
  `transportId=${detailD?.data?.items?.[0]?.transportId} status=${detailD?.data?.plan?.status}`)

// 换一家门店再补一批：并入的**仍是同一张运单**（一趟车顺路送几家店），新门店接在后面新起一站
const planE = await createPlan([b10.id], { storeOverride: storeB, remark: '装车口回归-E（另一家店）' })
check('★ 准备：建计划 E（另一家门店，单批）',
  planE?.status === 1 && planE?.storeName === storeB.name, `planNo=${planE?.planNo}`)

const mergeNewStore = await loadReport({ vehicleNo: '京A·12345', batchNos: [b10.batchNo] })
check('★ ★ [并入] 同车牌再扫另一家店的货 → 还是那张运单（不会另开一张）',
  statusOf(mergeNewStore) === ST_ACCEPTED && targetIdOf(mergeNewStore) === transportAId,
  brief(mergeNewStore))

const tAGrown = await api(`/distribution/transports/${transportAId}`, { token })
const stopsGrown = await api(`/distribution/transports/${transportAId}/stops`, { token })
check('★ [并入] 运单变成 2 站，目的地按站点台账重算为「途经 2 站」',
  tAGrown?.data?.stopCount === 2 && String(tAGrown?.data?.destination || '').startsWith('途经 2 站'),
  `stopCount=${tAGrown?.data?.stopCount} destination=${tAGrown?.data?.destination}`)
check('★ [并入] 新门店接在第 2 站（本站 1 批），第 1 站仍是 4 批原封不动',
  stopsGrown?.data?.[0]?.storeName === store.name && stopsGrown?.data?.[0]?.batchCount === 4
  && stopsGrown?.data?.[1]?.storeName === storeB.name
  && stopsGrown?.data?.[1]?.batchCount === 1,
  `站点=${JSON.stringify((stopsGrown?.data || []).map((s) => [s.stopNo, s.storeName, s.itemCount]))}`)

// ── F. 计划回填：车与人由设备补齐、明细逐条标记已排车 ──
const detailA = await api(`/distribution/delivery-plans/${planA.id}`, { token })
const itemsA = detailA?.data?.items || []
check('★ 计划 A 状态转「已排车」(2)，车次计数 = 1',
  detailA?.data?.plan?.status === 2 && detailA?.data?.plan?.transportCount === 1,
  `status=${detailA?.data?.plan?.status} transportCount=${detailA?.data?.plan?.transportCount}`)
check('★ 计划 A 三条明细全部标记已排车，且都指向本次运单',
  itemsA.length === 3 && itemsA.every((i) => i.shipped === 1 && i.transportId === transportAId),
  `明细：${itemsA.map((i) => `${i.batchNo}:${i.shipped}/${i.transportId}`).join(', ')}`)
check('★ 车牌由现场补齐；司机在计划与现场都是空的（装车口不采集），如实留空',
  detailA?.data?.plan?.vehicleNo === '京A·12345' && !detailA?.data?.plan?.driverName,
  `vehicleNo=${detailA?.data?.plan?.vehicleNo} driverName=${detailA?.data?.plan?.driverName}`)

// ── G. 重复装车 → 拒绝 ──
const again = await loadReport({ vehicleNo: '京A·12345', driverName: '张师傅', batchNos: [b1.batchNo] })
check('★ 已由运单发运过的批次再扫 → 拒绝（一批货只能上一次车）',
  statusOf(again) === ST_REJECTED && text(again).includes('已由运单发运过'), brief(again))

// ── H. 计划 B：用计划安排的车牌装车（只报姓名、不走卡） ──
const okB = await loadReport({ vehicleNo: '京A·99999', driverName: '李四', batchNos: [b4.batchNo, b5.batchNo] })
check('★ 现场车牌与计划一致 → 放行建单', statusOf(okB) === ST_ACCEPTED, brief(okB))
const transportBId = targetIdOf(okB)
if (transportBId) transportIds.push(transportBId)

const tB = await api(`/distribution/transports/${transportBId}`, { token })
check('★ 没走刷卡只报了姓名 → 来源记「人工录入」且不算本人确认（与刷卡那条路分得开）',
  tB?.data?.driverSource === 'MANUAL' && tB?.data?.driverConfirmed === 0,
  `driverSource=${tB?.data?.driverSource} driverConfirmed=${tB?.data?.driverConfirmed}`)

// ── I. 分车第一车：计划 C 只扫一批 ──
// ⚠ 车牌必须与 A 组那张**区分开**：同一车牌在窗口内再扫会被判成「同一趟车继续装」而并进去
//   （那是 E2/E3 组验证的事）。分车要的是「另开一张」，两个语义别搅在一起。
const PLATE_C = '京A·T0003'
const okC1 = await loadReport({ vehicleNo: PLATE_C, driverName: '张师傅', batchNos: [b6.batchNo] })
check('★ 分车第一车：只扫计划里的 1 批 → 建单成功', statusOf(okC1) === ST_ACCEPTED, brief(okC1))
const transportC1Id = targetIdOf(okC1)
if (transportC1Id) transportIds.push(transportC1Id)

const detailC1 = await api(`/distribution/delivery-plans/${planC.id}`, { token })
check('★ 还有货没装完 → 计划转「部分排车」(4)，车次计数 = 1',
  detailC1?.data?.plan?.status === 4 && detailC1?.data?.plan?.transportCount === 1,
  `status=${detailC1?.data?.plan?.status} transportCount=${detailC1?.data?.plan?.transportCount}`)
const shippedC1 = (detailC1?.data?.items || []).filter((i) => i.shipped === 1).map((i) => i.batchNo)
const pendingC1 = (detailC1?.data?.items || []).filter((i) => !i.shipped).map((i) => i.batchNo)
check('★ 已排车/未排车明细分得清楚（1 排 1 未排）',
  shippedC1.length === 1 && pendingC1.length === 1,
  `已排车=${shippedC1.join(',')} 未排车=${pendingC1.join(',')}`)

const pendingAfterC1 = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
const pendingIdsAfterC1 = new Set((pendingAfterC1?.data?.records || []).map((r) => r.id))
check('★ 没排上车的那批仍被计划占着，不会回到「待发运」（否则会被别的单据抢走）',
  !pendingIdsAfterC1.has(b7.id), `b7=${b7.batchNo} 是否在待发运=${pendingIdsAfterC1.has(b7.id)}`)
check('★ 已排车的那批自然不在「待发运」（被运单装载）',
  !pendingIdsAfterC1.has(b6.id), `b6=${b6.batchNo}`)

// ── J. 手工建单的两道拦截（与计划侧同口径） ──
const manualOnOccupied = await api('/distribution/transports', {
  method: 'POST', token,
  body: { splitBatchId: b7.id, vehicleNo: '京A·66666', destination: store.name },
})
check('★ 手工建单抢「被待发货计划占用」的批次 → 拦',
  text(manualOnOccupied).includes('占用'), brief(manualOnOccupied))

const manualOnShipped = await api('/distribution/transports', {
  method: 'POST', token,
  body: { splitBatchId: b1.id, vehicleNo: '京A·66666', destination: store.name },
})
check('★ 手工建单抢「已由运单发运过」的批次 → 拦',
  text(manualOnShipped).includes('已由运单发运过') || text(manualOnShipped).includes('不能重复发货'),
  brief(manualOnShipped))

// ── K. 分车第二车：**第一车先开走**，剩下的那批才另开一张运单 ──
// 这一组同时是「并入」的分界线：C1 发车之后状态不再是「待发车」，同车牌再扫就不会被并进去。
// 车没走 = 同一趟（并入）；车走了 = 新的一趟（另开一张）—— 分车正是靠这一条与并入区分开。
const departC1 = await api(`/distribution/transports/${transportC1Id}/depart`, { method: 'PUT', token })
const c1AfterDepart = await api(`/distribution/transports/${transportC1Id}`, { token })
check('★ 分车第一车先发车（车没开走就不会有第二车）',
  c1AfterDepart?.data?.status === 2, `depart=${brief(departC1)} status=${c1AfterDepart?.data?.status}`)

const okC2 = await loadReport({ vehicleNo: PLATE_C, driverName: '张师傅', batchNos: [b7.batchNo] })
check('★ 分车第二车：第一车已发走，同车牌再扫 → **另开一张**（不并进已发走的那张）',
  statusOf(okC2) === ST_ACCEPTED && targetIdOf(okC2) !== transportC1Id, brief(okC2))
const transportC2Id = targetIdOf(okC2)
if (transportC2Id) transportIds.push(transportC2Id)

const detailC2 = await api(`/distribution/delivery-plans/${planC.id}`, { token })
check('★ 全部排完 → 计划转「已排车」(2)，车次计数 = 2（一张计划两趟车）',
  detailC2?.data?.plan?.status === 2 && detailC2?.data?.plan?.transportCount === 2,
  `status=${detailC2?.data?.plan?.status} transportCount=${detailC2?.data?.plan?.transportCount}`)
check('★ 两张运单各拉一批，货物不重叠',
  transportC1Id !== transportC2Id && !!transportC1Id && !!transportC2Id,
  `第一车=${transportC1Id} 第二车=${transportC2Id}`)

// ── L. 「没有司机不许发车」：装车口生成的运单也要过这道闸门 ──
// 现场只在上车那一刻刷一次卡，所以装车建单时司机常常是空的（E 组那一张就是）。
// 空着**能建单、不能发车** —— 货要走了，责任必须落到具体的人：
//   ⓐ 人工「立即发车」被拦（本组验证）；
//   ⓑ 车载终端刷卡发车（DEPART）会把发车时刻 + 司机 + 「本人确认」一起写进运单
//      （见 verify-depart.mjs，那才是本人确认的唯一来源）。
const departNoDriver = await api(`/distribution/transports/${transportAId}/depart`, { method: 'PUT', token })
check('★ 没有司机就发车 → 被拦（不能让货走在「没有责任人」的状态下）',
  text(departNoDriver).includes('还没有司机'), brief(departNoDriver))
const stillPending = await api(`/distribution/transports/${transportAId}`, { token })
check('★ 被拦之后运单仍停在「待发车」，没有偷偷发出去',
  stillPending?.data?.status === 1, `status=${stillPending?.data?.status}`)

// 人工补录司机（编辑运单）→ 来源记「人工录入、非本人确认」：补录 ≠ 本人刷卡
const filled = await api(`/distribution/transports/${transportAId}`, {
  method: 'PUT', token,
  body: {
    splitBatchId: b1.id, vehicleNo: '京A·12345', vehicleType: '冷藏车', refrigeration: '机械制冷',
    driverName: '张师傅', driverPhone: '13700000002',
    // destination 取运单当前值：E2/E3 并入之后它已经是「途经 2 站：…」，
    // 这一组验的是司机补录，不拿目的地字段做别的文章
    origin: null, destination: stillPending?.data?.destination, plannedDepart: null, plannedArrive: null,
  },
})
const afterFill = await api(`/distribution/transports/${transportAId}`, { token })
check('★ 补录司机后来源记「人工录入、非本人确认」（补录 ≠ 本人刷卡，两者必须分得开）',
  afterFill?.data?.driverName === '张师傅' && afterFill?.data?.driverSource === 'MANUAL'
  && afterFill?.data?.driverConfirmed === 0,
  `filled=${brief(filled)} driverName=${afterFill?.data?.driverName} `
  + `driverSource=${afterFill?.data?.driverSource} confirmed=${afterFill?.data?.driverConfirmed}`)

// 补齐司机之后才放行（发车是 PUT：DistributionController @PutMapping("/transports/{id}/depart")）
const depart = await api(`/distribution/transports/${transportAId}/depart`, { method: 'PUT', token })
const afterDepart = await api(`/distribution/transports/${transportAId}`, { token })
check('★ 补齐司机后装车口生成的运单可正常发车（状态 待发车 → 运输中）',
  afterDepart?.data?.status === 2,
  `depart=${brief(depart)} status=${afterDepart?.data?.status}`)

// ── 收尾：删运单（货物退回待装车） → 取消计划（未排车批次彻底释放） ──
// KEEP=1 时跳过收尾：留给 logs/verify-load-db.sh 在「数据还在库里」的时候做落库核验，
// 由它自己收尾。默认（不带 KEEP）自清理，保证单独跑完不留痕。
if (process.env.KEEP === '1') {
  console.log('\n  （KEEP=1：跳过收尾删除，交由 db 侧核验后清理）')
} else {
  for (const id of transportIds) {
    await api(`/distribution/transports/${id}`, { method: 'DELETE', token })
  }
  for (const id of planIds) {
    await api(`/distribution/delivery-plans/${id}/cancel`, { method: 'POST', token, body: { reason: '回归收尾' } })
  }
  const pendingFinal = await api('/distribution/splits/pending?pageNum=1&pageSize=50', { token })
  const finalIds = new Set((pendingFinal?.data?.records || []).map((r) => r.id))
  const allBatches = [b1, b2, b3, b4, b5, b6, b7, b8, b9, b10]
  check('★ 收尾：删运单 + 取消计划后，10 个批次全部回到「待发运」',
    allBatches.every((b) => finalIds.has(b.id)),
    `回到待发运的：${allBatches.filter((b) => finalIds.has(b.id)).map((b) => b.batchNo).join(',')}`)
}

console.log(`\n=== 装车口自动建单回归：${pass} 通过 / ${fail} 失败 ===`)
console.log(`# PLAN_IDS=${planIds.join(',')}`)
console.log(`# TRANSPORT_IDS=${transportIds.join(',')}`)
console.log(`# STAGING_IDS=${stagingIds.join(',')}`)
process.exit(fail === 0 ? 0 : 1)
