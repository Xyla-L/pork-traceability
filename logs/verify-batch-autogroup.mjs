/**
 * 胴体批次自动归集回归（屠宰线胴体称重分拣台通道）：
 *
 *   归集口径：设备只报「单头白条」的事实（耳标 / 胴体重 / 哪条线），
 *            批次边界与批次号由系统按「屠宰场 + 屠宰日」算出来 —— 设备报文里不带批次号。
 *
 *   写入前三道硬校验：① 瘦肉精（阳性 / 同批阳性 / 未检）② 已完成胴体盖章 ③ 未被其他批次占用。
 *
 *   准备（每头都走完整链路：建档 → 产地检疫出证 → 出栏称重 → 审批通过 → 入场查验）：
 *     P0 = 完整到盖章，先用人工建批占掉「归集验证屠宰场-甲」（用来验证设备归集不并入人工批次）
 *     P1 = 完整到盖章，「-甲」→ 应新建一个设备批次
 *     P2 = 完整到盖章，「-甲」→ 应追加到 P1 建的那个批次（同场同日归一批）
 *     P3 = 完整到盖章，「-乙」→ 应另建一个批次（不同屠宰场不混批）
 *     P4 = 只到宰后合格、未盖章        → 拒绝，原因「尚未完成胴体盖章」
 *     P5 = 只到入场查验、未做瘦肉精    → 拒绝，原因「尚无瘦肉精检测记录」
 *     P6 = 瘦肉精阳性                 → 拒绝，原因「瘦肉精检测结果阳性」
 *     P7 = 完整到盖章（仅跨日场景创建）
 *     PC = 完整到盖章，「-丙」→（需 bash 预置「已接近上限」的批次）追加进去并把该批次顶到上限、自动封批
 *     PD = 完整到盖章，「-丙」→ 上一批已封，应另开一个新批次
 *
 *   断言：
 *     1. P1 首次上报 → 入账，落库看：批次号系统生成 / 来源为设备 / 批次里只它 1 头
 *     2. ★ P2 上报（同场同日）→ 入账且 targetId == P1 的批次（是追加：批次里变 2 头、重量累加）
 *     3. ★ P3 上报（异场同日）→ 新建另一个批次（targetId != P1 的批次），不同屠场不混批
 *     4. ★ P1 重复上报 → 回既有批次、不新建也不重复入批（批次里仍是 2 头）
 *     5. ★ P4（未盖章）→ 拒绝，不可重试
 *     6. ★ P5（未检）→ 拒绝，不可重试
 *     7. ★ P6（阳性）→ 拒绝，不可重试
 *     8. 缺胴体重量 / 缺屠宰场 → 拒绝（设备没给出可归集的事实）
 *     9. ★ 设备归集不并入人工建的批次（P1 的批次 != P0 的人工批次）
 *    10. ★ 人工建批是设备优先环节：无兜底票据 → 拒绝；持票据 → 通过
 *    11. 来源策略表里「胴体批次归集」为设备优先，且说明写明自动归集口径
 *    12. ★ 封批接口：调用后批次转「已封批」，重复封批被拒
 *    13. ★（可选，需 bash 侧预置一条「昨日同场设备批次」并把 id 作为命令行参数传入）
 *        同场但不同日的设备批次不会被并入 —— 归集键含日期
 *    14. ★（可选，需 bash 侧预置一条「当日同场、已接近头数上限」的批次并把屠宰场名传入）
 *        · PC 追加后批次头数到达上限 → 自动封批（status 变 2）
 *        · PD 再上报 → 另开一个新批次（已封的批次不再并入），且新批次是「归集中」、只有 1 头
 *
 *   注：入账类回执按既定口径只报「已入账」这一个状态本身（不跟「已自动建立胴体批次」这类结论句），
 *      所以「新建还是追加」只能靠可观测事实判断 —— 批次号 / 来源 / 头数 / 重量，不去断言回执文案。
 *
 *   运行：
 *     node logs/verify-batch-autogroup.mjs [昨日同场批次id] [跨日验证屠宰场名] \
 *          [已接近上限批次所在屠宰场名] [该批次id] [头数上限]
 *   后三个参数只影响第 14 节（头数上限自动封批）；不传则跳过该节。
 *   产生的业务记录 / 接入流水 ID 打印在末尾，由 bash 侧清理。
 *   （本环境 node 禁止子进程调用，DB 核验与清理必须走 bash。）
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const TAG_DEVICE = 'TAG-W001'
const CERT_DEVICE = 'QSCAN-001'
const EXIT_DEVICE = 'EXITSCALE-001'
const GATE_DEVICE = 'GATE-001'
const READER_DEVICE = 'READER-001'
const STATION_DEVICE = 'STATION-001'
const STAMPER_DEVICE = 'STAMPER-001'
const BATCH_DEVICE = 'BATCH-001'

const YESTERDAY_BATCH_ID = process.argv[2] ? Number(process.argv[2]) : null
const CROSS_DAY_HOUSE = process.argv[3] || null
/** 已接近头数上限的预置批次所在的屠宰场（bash 侧先用 SQL 造出来，见脚本头说明） */
const FULL_HOUSE = process.argv[4] || null
/** 那条预置批次的 id 与它的头数上限（用于断言「顶到上限自动封批」） */
const FULL_BATCH_ID = process.argv[5] ? Number(process.argv[5]) : null
const FULL_LIMIT = process.argv[6] ? Number(process.argv[6]) : null

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

const pad = (n) => String(n).padStart(2, '0')
function nowText(offsetSeconds = 0) {
  const d = new Date(Date.now() + offsetSeconds * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function dateText(daysFromNow = 0) {
  const d = new Date(Date.now() + daysFromNow * 86400000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

/** 代设备发一次上报（走完整链路：鉴权/校验/防重复/写入业务） */
const report = (deviceNo, data) => api('/ingest/demo/report', { method: 'POST', token, body: { deviceNo, data } })

const stagingIds = []
const track = (r) => {
  const id = r?.data?.stagingId
  if (id) stagingIds.push(id)
  return r
}

const stamp = Date.now()
const HOUSE_A = '归集验证屠宰场-甲'
const HOUSE_B = '归集验证屠宰场-乙'
const cleanup = { pigs: [], applies: [], entries: [], inspections: [], stamps: [], carcassBatches: [] }

const farms = await api('/breeding/farms?current=1&size=1', { token })
const farmName = farms?.data?.records?.[0]?.farmName
check('取到一个已登记养殖场', !!farmName, `farmName=${farmName}`)

/**
 * 造一头猪。
 * @param stage 'stamped' 完整到盖章 / 'no-stamp' 只到宰后合格 / 'no-racto' 只到入场 / 'positive' 瘦肉精阳性
 */
async function setupPig(suffix, batchNo, slaughterhouse, stage = 'stamped') {
  const earTagNo = `ET-BAG-${stamp}-${suffix}`
  const certNo = `QC-BAG-${stamp}-${suffix}`
  const carcassNo = `CS-BAG-${stamp}-${suffix}`

  const tag = track(await report(TAG_DEVICE, {
    earTagNo, farmName, birthDate: dateText(-90), breed: '三元杂', gender: 1, penNo: '胴体批次归集验证舍', origin: '自繁',
  }))
  const pigId = tag?.data?.targetId
  if (pigId) cleanup.pigs.push(pigId)

  track(await report(CERT_DEVICE, {
    earTagNo, certNo, issueOrg: '胴体批次验证动物卫生监督所', issueTime: nowText(), validUntil: dateText(365), inspector: '王官方兽医',
  }))

  const apply = track(await report(EXIT_DEVICE, {
    earTagNo, weightKg: 119.4, weighTime: nowText(), targetSlaughterhouse: slaughterhouse,
  }))
  const applyId = apply?.data?.targetId
  if (applyId) cleanup.applies.push(applyId)
  await api(`/breeding/applies/${applyId}/approve`, {
    method: 'PUT', token, body: { approved: true, comment: '回归验证：同意出栏' },
  })

  const entry = track(await report(GATE_DEVICE, {
    earTagNo, batchNo, quarantineCert: certNo, weight: 119.0, inspector: '张查验',
  }))
  const entryId = entry?.data?.targetId
  if (entryId) cleanup.entries.push(entryId)

  if (stage === 'no-racto') {
    return { earTagNo, batchNo, pigId, entryOk: entry?.data?.accepted === true }
  }

  // 瘦肉精：stage=positive 报阳性（检测照常入账留证，只是后续被阻断），其余报阴性
  track(await report(READER_DEVICE, {
    earTagNo, batchNo, sampleNo: `S-BAG-${stamp}-${suffix}`, result: stage === 'positive' ? 0 : 1,
    testTime: nowText(), testMethod: '胶体金快速检测卡', samplePart: '尿液', operator: '赵检测',
  }))
  if (stage === 'positive') {
    return { earTagNo, batchNo, pigId, entryOk: entry?.data?.accepted === true }
  }

  // 宰前合格 → 宰后合格（盖章以合格的宰后检验为前提）
  track(await report(STATION_DEVICE, {
    earTagNo, batchNo, inspectType: 1, result: 1, temperature: 38.6,
    veterinary: '李兽医', licenseNo: 'VET-PRE-001', conclusion: '宰前临床检查合格，准予屠宰',
  }))
  const post = track(await report(STATION_DEVICE, {
    earTagNo, batchNo, inspectType: 2, result: 1, temperature: 38.5,
    veterinary: '李兽医', licenseNo: 'VET-001', conclusion: '宰后检验合格', organCheck: '心肺肝未见异常',
  }))
  if (post?.data?.targetId) cleanup.inspections.push(post.data.targetId)

  if (stage === 'no-stamp') {
    return { earTagNo, batchNo, pigId, carcassNo, entryOk: entry?.data?.accepted === true }
  }

  const st = track(await report(STAMPER_DEVICE, { earTagNo, batchNo, carcassNo, veterinary: '李兽医' }))
  if (st?.data?.targetId) cleanup.stamps.push(st.data.targetId)

  return { earTagNo, batchNo, pigId, carcassNo, stampedOk: st?.data?.accepted === true, entryOk: entry?.data?.accepted === true }
}

/** 胴体批次上报（屠宰线分拣台）：只报单头白条，不带批次号 */
const batchReport = (earTagNo, slaughterhouse, carcassWeightKg = 78.5, extra = {}) =>
  report(BATCH_DEVICE, { earTagNo, slaughterhouse, carcassWeightKg, ...extra })

/**
 * 按 id 取一行胴体批次。
 * 没有「按 id 查单条」的接口，从分页列表里捞（回归用，数据量小）。
 * 用途：入账回执不跟结论句，只能靠批次本身的事实判断「新建 / 追加 / 没重复入批」。
 */
async function batchById(id) {
  const res = await api('/distribution/batches?pageNum=1&pageSize=200', { token })
  return (res?.data?.records || []).find((b) => b.id === id) || null
}

console.log('\n== 1) 准备临时猪 ==')
const P0 = await setupPig('P0', `B-BAG-${stamp}-P0`, HOUSE_A)
const P1 = await setupPig('P1', `B-BAG-${stamp}-P1`, HOUSE_A)
const P2 = await setupPig('P2', `B-BAG-${stamp}-P2`, HOUSE_A)
const P3 = await setupPig('P3', `B-BAG-${stamp}-P3`, HOUSE_B)
const P4 = await setupPig('P4', `B-BAG-${stamp}-P4`, HOUSE_A, 'no-stamp')
const P5 = await setupPig('P5', `B-BAG-${stamp}-P5`, HOUSE_A, 'no-racto')
const P6 = await setupPig('P6', `B-BAG-${stamp}-P6`, HOUSE_A, 'positive')
// 头数上限 / 封批场景：只在 bash 预置了「已接近上限」的批次时才需要这两头
const PC = FULL_HOUSE ? await setupPig('PC', `B-BAG-${stamp}-PC`, FULL_HOUSE) : null
const PD = FULL_HOUSE ? await setupPig('PD', `B-BAG-${stamp}-PD`, FULL_HOUSE) : null
const all = { P0, P1, P2, P3, P4, P5, P6 }
check('七头猪都拿到档案编号', Object.values(all).every((p) => !!p.pigId),
  Object.entries(all).map(([k, v]) => `${k}=${v.pigId}`).join(' '))
check('P0/P1/P2/P3 四头盖章入账（归集的前提）',
  P0.stampedOk && P1.stampedOk && P2.stampedOk && P3.stampedOk,
  `P0=${P0.stampedOk} P1=${P1.stampedOk} P2=${P2.stampedOk} P3=${P3.stampedOk}`)

console.log('\n== 2) ★ 人工建批（设备优先环节：无需票据，但强制实名 + 上链留痕）==')
let manualBatchId = null
{
  const withManual = await api('/distribution/batches', {
    method: 'POST', token,
    body: { pigIds: [P0.pigId], totalWeightKg: 120, slaughterhouse: HOUSE_A, operator: '回归验证', note: '回归验证：无分拣台的场人工建批补录' },
  })
  check('人工建批直接通过（2026-10-12 起不再需要兜底票据）',
    withManual?.code === 200 || withManual?.success === true, JSON.stringify(withManual).slice(0, 240))
  manualBatchId = withManual?.data?.id ?? null
  if (manualBatchId) cleanup.carcassBatches.push(manualBatchId)
  check('人工批次带上了来源标记（人工录入 + 录入人）',
    withManual?.data?.source === 'MANUAL', `source=${withManual?.data?.source} sourceRef=${withManual?.data?.sourceRef}`)
}

console.log('\n== 3) ★ 首次上报：自动建立胴体批次（批次号由系统生成）==')
const b1 = track(await batchReport(P1.earTagNo, HOUSE_A))
check('★ P1 上报入账（已盖章 + 瘦肉精阴性 + 未占用）',
  b1?.data?.accepted === true, JSON.stringify(b1?.data).slice(0, 260))
check('★ 首报不是重复（是真落了一条新数据）',
  b1?.data?.duplicate !== true, `duplicate=${b1?.data?.duplicate}`)
const autoBatchId = b1?.data?.targetId ?? null
if (autoBatchId) cleanup.carcassBatches.push(autoBatchId)
check('★ 新建的设备批次不是人工那一批（归集不并入人工批次）',
  autoBatchId != null && autoBatchId !== manualBatchId, `auto=${autoBatchId} manual=${manualBatchId}`)
// 入账回执只报「已入账」，新建 vs 追加要靠批次本身的事实看
const autoBatch1 = autoBatchId ? await batchById(autoBatchId) : null
check('★ 新建的批次带系统生成的批次号（CB 前缀，设备报文里没有批次号）',
  String(autoBatch1?.batchNo || '').startsWith('CB'), `batchNo=${autoBatch1?.batchNo}`)
check('★ 来源标记为「设备采集」，来源设备是分拣台编号',
  autoBatch1?.source === 'DEVICE' && autoBatch1?.sourceRef === BATCH_DEVICE,
  `source=${autoBatch1?.source} sourceRef=${autoBatch1?.sourceRef}`)
check('★ 归集批次没有操作人（机器归集，不拿设备名冒充人名）',
  !autoBatch1?.operator, `operator=${autoBatch1?.operator}`)
check('★ 首头白条刚落批，批次里就它 1 头',
  (autoBatch1?.pigIds || []).length === 1, `pigIds=${JSON.stringify(autoBatch1?.pigIds)}`)

console.log('\n== 4) ★ 同场同日的第二头白条 → 追加到同一批次 ==')
const b2 = track(await batchReport(P2.earTagNo, HOUSE_A, 82.3))
check('★ P2 上报入账', b2?.data?.accepted === true, JSON.stringify(b2?.data).slice(0, 260))
check('★ 归入的正是 P1 建的那个批次（同场同日归一批，不是又新建一条）',
  b2?.data?.targetId != null && b2?.data?.targetId === autoBatchId,
  `P1批次=${autoBatchId} P2目标=${b2?.data?.targetId}`)
// 「追加」的可观测证据：同一批次里头数变 2、总重量累加
const autoBatch2 = autoBatchId ? await batchById(autoBatchId) : null
check('★ 追加后同一批次里有 2 头',
  (autoBatch2?.pigIds || []).length === 2, `pigIds=${JSON.stringify(autoBatch2?.pigIds)}`)
check('★ 总重量累加为两头胴体之和（78.5 + 82.3 = 160.8）',
  Number(autoBatch2?.totalWeightKg) === 160.8, `totalWeightKg=${autoBatch2?.totalWeightKg}`)

console.log('\n== 5) ★ 不同屠宰场 → 另建批次（不混批）==')
const b3 = track(await batchReport(P3.earTagNo, HOUSE_B, 79.1))
check('★ P3 上报入账', b3?.data?.accepted === true, JSON.stringify(b3?.data).slice(0, 260))
check('★ P3 另建了一个批次（屠宰场不同不并入）',
  b3?.data?.targetId != null && b3?.data?.targetId !== autoBatchId,
  `P1批次=${autoBatchId} P3目标=${b3?.data?.targetId}`)
if (b3?.data?.targetId) cleanup.carcassBatches.push(b3.data.targetId)

console.log('\n== 6) ★ 重复上报：回既有批次，不新建也不重复入批 ==')
const b1Again = track(await batchReport(P1.earTagNo, HOUSE_A))
check('★ P1 重复上报回既有批次（幂等，不报错）',
  b1Again?.data?.accepted === true && b1Again?.data?.targetId === autoBatchId,
  `targetId=${b1Again?.data?.targetId} 期望=${autoBatchId}`)
check('★ 回执标出这是重复上报', b1Again?.data?.duplicate === true, `duplicate=${b1Again?.data?.duplicate}`)
const autoBatch3 = autoBatchId ? await batchById(autoBatchId) : null
check('★ 重推后批次里仍是 2 头（同一头没有被重复计进去）',
  (autoBatch3?.pigIds || []).length === 2, `pigIds=${JSON.stringify(autoBatch3?.pigIds)}`)

console.log('\n== 7) ★ 未盖章的白条不得进入胴体批次 ==')
const b4 = track(await batchReport(P4.earTagNo, HOUSE_A))
check('★ P4（未盖章）被拒', b4?.data?.accepted !== true && b4?.data?.status === 3,
  `status=${b4?.data?.status} message=${b4?.data?.message}`)
check('★ 原因写明「尚未完成胴体盖章」',
  String(b4?.data?.message || '').includes('尚未完成胴体盖章'), `message=${b4?.data?.message}`)
check('★ 拒绝不可重试（没盖章是事实，人工确认也绕不过去）',
  b4?.data?.retryable !== true, `retryable=${b4?.data?.retryable}`)

console.log('\n== 8) ★ 没做过瘦肉精检测的白条不得进入胴体批次 ==')
const b5 = track(await batchReport(P5.earTagNo, HOUSE_A))
check('★ P5（未检）被拒', b5?.data?.accepted !== true && b5?.data?.status === 3,
  `status=${b5?.data?.status} message=${b5?.data?.message}`)
check('★ 原因是「尚无瘦肉精检测记录」',
  String(b5?.data?.message || '').includes('尚无瘦肉精检测记录'), `message=${b5?.data?.message}`)

console.log('\n== 9) ★ 瘦肉精阳性的白条不得进入胴体批次 ==')
const b6 = track(await batchReport(P6.earTagNo, HOUSE_A))
check('★ P6（阳性）被拒', b6?.data?.accepted !== true && b6?.data?.status === 3,
  `status=${b6?.data?.status} message=${b6?.data?.message}`)
check('★ 原因是「瘦肉精检测结果阳性」',
  String(b6?.data?.message || '').includes('瘦肉精检测结果阳性'), `message=${b6?.data?.message}`)

console.log('\n== 10) 设备没给出可归集的事实 → 拒绝 ==')
{
  const noWeight = track(await batchReport(P2.earTagNo, HOUSE_A, 0))
  check('★ 胴体重量为 0 被拒',
    noWeight?.data?.accepted !== true && noWeight?.data?.status === 3,
    `status=${noWeight?.data?.status} message=${noWeight?.data?.message}`)

  const noHouse = track(await report(BATCH_DEVICE, { earTagNo: P2.earTagNo, carcassWeightKg: 78.5 }))
  check('★ 缺屠宰场被拒（归集键缺一半，成不了批）',
    noHouse?.data?.accepted !== true, `status=${noHouse?.data?.status} message=${noHouse?.data?.message}`)
}

console.log('\n== 11) 来源策略表里「胴体批次归集」的口径 ==')
{
  const res = await api('/ingest/source-policy', { token })
  const row = (res?.data || []).find((p) => p.code === 'BATCH')
  check('策略表里有「胴体批次归集」', !!row, JSON.stringify(res).slice(0, 200))
  check('策略是「设备优先」（无分拣台的场仍可人工补录）',
    row?.policy === 'DEVICE_PREFERRED', `policy=${row?.policy}`)
  check('说明写明按「屠宰场 + 屠宰日」自动归集、批次号由系统生成',
    String(row?.note || '').includes('屠宰日') && String(row?.note || '').includes('批次号由系统生成'),
    `note=${row?.note}`)
}

console.log('\n== 12) ★ 封批：封过之后这个批次不再接受当日白条 ==')
{
  const first = await api(`/distribution/batches/${autoBatchId}/seal`, {
    method: 'POST', token,
  })
  check('★ 封批调用成功', first?.code === 200 || first?.success === true, JSON.stringify(first).slice(0, 240))
  const sealed = autoBatchId ? await batchById(autoBatchId) : null
  check('★ 批次状态转为「已封批」', sealed?.status === 2, `status=${sealed?.status}`)

  const again = await api(`/distribution/batches/${autoBatchId}/seal`, {
    method: 'POST', token,
  })
  check('★ 重复封批被拒（已封的批次不再接受封批）',
    !(again?.code === 200 || again?.success === true), JSON.stringify(again).slice(0, 200))
}

let fullSealNewBatchId = null

if (YESTERDAY_BATCH_ID && CROSS_DAY_HOUSE) {
  console.log('\n== 13) ★ 同场但不同日 → 不并入昨日批次（归集键含日期）==')
  const crossPig = await setupPig('P7', `B-BAG-${stamp}-P7`, CROSS_DAY_HOUSE)
  check('跨日验证猪已盖章', crossPig.stampedOk === true, `earTag=${crossPig.earTagNo}`)
  const b7 = track(await batchReport(crossPig.earTagNo, CROSS_DAY_HOUSE, 80.0))
  check('★ 跨日上报入账', b7?.data?.accepted === true, JSON.stringify(b7?.data).slice(0, 260))
  check('★ 另建了今日批次，没有并进昨日那条设备批次',
    b7?.data?.targetId != null && b7?.data?.targetId !== YESTERDAY_BATCH_ID,
    `今日批次=${b7?.data?.targetId} 昨日批次=${YESTERDAY_BATCH_ID}`)
  if (b7?.data?.targetId) cleanup.carcassBatches.push(b7.data.targetId)
  cleanup.pigs.push(crossPig.pigId)
} else {
  console.log('\n== 13) 跨日归集断言已跳过（未传入「昨日同场批次id」，由 bash 侧预置后驱动）==')
}

if (FULL_HOUSE && FULL_BATCH_ID && FULL_LIMIT) {
  console.log('\n== 14) ★ 达到头数上限自动封批；封批后另开新批 ==')
  check('上限场景的两头猪已盖章', PC?.stampedOk === true && PD?.stampedOk === true,
    `PC=${PC?.stampedOk} PD=${PD?.stampedOk}`)

  const bC = track(await batchReport(PC.earTagNo, FULL_HOUSE, 79.9))
  check('★ PC 上报入账', bC?.data?.accepted === true, JSON.stringify(bC?.data).slice(0, 260))
  const fullBatch = bC?.data?.targetId ? await batchById(bC.data.targetId) : null
  check('★ PC 并进了那条「已接近上限」的预置批次（不是另开新批）',
    bC?.data?.targetId != null && bC?.data?.targetId === FULL_BATCH_ID,
    `目标=${bC?.data?.targetId} 预置批次=${FULL_BATCH_ID}`)
  check('★ 顶到上限后自动封批（status 变 2）',
    fullBatch?.status === 2, `status=${fullBatch?.status}`)
  check('★ 头数已到上限值',
    Number(fullBatch?.pigCount) === FULL_LIMIT, `pigCount=${fullBatch?.pigCount} 期望=${FULL_LIMIT}`)
  if (bC?.data?.targetId) cleanup.carcassBatches.push(bC.data.targetId)

  const bD = track(await batchReport(PD.earTagNo, FULL_HOUSE, 81.3))
  check('★ PD 上报入账', bD?.data?.accepted === true, JSON.stringify(bD?.data).slice(0, 260))
  check('★ 另开了一个新批次（已封的批次不再并入）',
    bD?.data?.targetId != null && bD?.data?.targetId !== FULL_BATCH_ID,
    `目标=${bD?.data?.targetId} 已封批次=${FULL_BATCH_ID}`)
  const newBatch = bD?.data?.targetId ? await batchById(bD.data.targetId) : null
  check('★ 新批次是「归集中」且只含它自己 1 头',
    newBatch?.status === 1 && Number(newBatch?.pigCount) === 1,
    `status=${newBatch?.status} pigCount=${newBatch?.pigCount}`)
  fullSealNewBatchId = bD?.data?.targetId ?? null
  if (fullSealNewBatchId) cleanup.carcassBatches.push(fullSealNewBatchId)
} else {
  console.log('\n== 14) 头数上限 / 自动封批断言已跳过（未传入「已接近上限批次所在屠宰场名」）==')
}

console.log(`\n===== 结果：${pass} 项通过，${fail} 项失败 =====`)
console.log('清理用 —— 接入流水 ID：' + stagingIds.join(','))
console.log('清理用 —— 临时猪 ID：' + cleanup.pigs.join(','))
console.log('清理用 —— 出栏申报 ID：' + cleanup.applies.join(','))
console.log('清理用 —— 入场查验 ID：' + cleanup.entries.join(','))
console.log('清理用 —— 屠宰检验 ID：' + cleanup.inspections.join(','))
console.log('清理用 —— 盖章 ID：' + cleanup.stamps.join(','))
console.log('清理用 —— 胴体批次 ID：' + cleanup.carcassBatches.join(','))
console.log(`清理用 —— 耳标前缀：ET-BAG-${stamp}`)
console.log(`清理用 —— 批次前缀：B-BAG-${stamp}`)
console.log(`清理用 —— 屠宰场：${HOUSE_A} / ${HOUSE_B}${CROSS_DAY_HOUSE ? ' / ' + CROSS_DAY_HOUSE : ''}${FULL_HOUSE ? ' / ' + FULL_HOUSE : ''}`)

// 机器可读行（供 verify-batch-autogroup-db.sh 解析；上面的中文行是给人看的，别解析它）
console.log('#STAGING ' + stagingIds.join(','))
console.log('#CLEANUP ' + JSON.stringify(cleanup))
console.log('#PIGS ' + JSON.stringify({
  P0: P0.pigId, P1: P1.pigId, P2: P2.pigId, P3: P3.pigId,
  P4: P4.pigId, P5: P5.pigId, P6: P6.pigId,
  PC: PC?.pigId ?? null, PD: PD?.pigId ?? null,
}))
console.log('#BATCHES ' + JSON.stringify({
  manual: manualBatchId, auto: autoBatchId, houseB: b3?.data?.targetId ?? null,
  full: FULL_BATCH_ID, fullNew: fullSealNewBatchId,
}))
console.log('#HOUSES ' + JSON.stringify({ a: HOUSE_A, b: HOUSE_B, cross: CROSS_DAY_HOUSE, full: FULL_HOUSE }))
process.exit(fail === 0 ? 0 : 1)
