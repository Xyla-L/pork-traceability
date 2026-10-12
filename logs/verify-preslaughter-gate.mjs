/**
 * 「未做宰前检查不得屠宰」+「盖章须有合格宰后检验」回归：
 *
 *   法规依据：《动物检疫管理办法》第二十条「动物屠宰前应当逐头（只）进行临床检查，健康的动物方可屠宰」；
 *            《生猪屠宰检疫规程》把宰前检查列为独立法定环节（屠宰前 2 小时内逐头检查，合格才准予屠宰）。
 *
 *   准备：四头临时猪各自走完 建档 → 产地检疫出证 → 出栏称重登记 → 审批通过 → 入场查验 → 瘦肉精阴性
 *        E = 完全没有宰前检查记录（批次 E）
 *        F = 宰前检查判「不合格」（批次 F）
 *        G = 宰前检查判「合格」（批次 G）
 *        H = 宰前检查判「合格」，但没做宰后检验（批次 H）
 *
 *   断言：
 *     1. E 报宰后检验(2) → 拒绝，原因写明「尚未做宰前检查」+「未做宰前检查不得屠宰」，不可重试
 *     2. ★ E 报宰后同步检验(3) → 同样拒绝（3 也是宰后类，同一道门槛）
 *     3. ★ F 报宰后检验 → 拒绝，原因写明「宰前检查结论为不合格」（是检了没过，不是没检）
 *     4. G 报宰后检验(2) → 通过；再报宰后同步检验(3) → 通过
 *     5. ★ 这道门槛只拦宰后，不拦宰前本身：E 补一条宰前检查合格 → 入账；之后 E 再报宰后 → 通过
 *     6. ★ H 盖章（只有宰前合格、没有宰后）→ 拒绝，原因写明「没有结论为「合格」的宰后检验记录」
 *     7. G 盖章（已有合格的宰后检验）→ 通过
 *     8. ★ 筛选口径：「宰后检验」要把 2 和 3 都带出来；「宰后检验（同步）」精确到 3；
 *        「宰前检验」只出 1 ——（原先后端对 3 直接跳过条件，选「同步检验」等于返回全部类型）
 *     9. 来源策略表里「屠宰检验」的说明已带上「未做宰前检查不得屠宰」
 *
 * 运行：node logs/verify-preslaughter-gate.mjs
 * 产生的业务记录 / 接入流水 ID 打印在末尾，由 bash 侧清理。
 * （本环境 node 禁止子进程调用，DB 核验与清理必须走 bash。）
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const TAG_DEVICE = 'TAG-W001'
const CERT_DEVICE = 'QSCAN-001'
const EXIT_DEVICE = 'EXITSCALE-001'
const GATE_DEVICE = 'GATE-001'
const READER_DEVICE = 'READER-001'
const STATION_DEVICE = 'STATION-001'
const STAMPER_DEVICE = 'STAMPER-001'

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
const batchE = `B-PSG-${stamp}-E`
const batchF = `B-PSG-${stamp}-F`
const batchG = `B-PSG-${stamp}-G`
const batchH = `B-PSG-${stamp}-H`
const cleanup = { pigs: [], applies: [], entries: [], inspections: [], stamps: [] }

const farms = await api('/breeding/farms?current=1&size=1', { token })
const farmName = farms?.data?.records?.[0]?.farmName
check('取到一个已登记养殖场', !!farmName, `farmName=${farmName}`)

/** 造一头「已入场 + 瘦肉精阴性」的猪（宰前检查由调用方按需补） */
async function setupPig(suffix, batchNo) {
  const earTagNo = `ET-PSG-${stamp}-${suffix}`
  const certNo = `QC-PSG-${stamp}-${suffix}`

  const tag = track(await report(TAG_DEVICE, {
    earTagNo, farmName, birthDate: dateText(-90), breed: '三元杂', gender: 1, penNo: '宰前门槛验证舍', origin: '自繁',
  }))
  const pigId = tag?.data?.targetId
  if (pigId) cleanup.pigs.push(pigId)

  track(await report(CERT_DEVICE, {
    earTagNo, certNo, issueOrg: '宰前门槛验证动物卫生监督所', issueTime: nowText(), validUntil: dateText(365), inspector: '王官方兽医',
  }))

  const apply = track(await report(EXIT_DEVICE, {
    earTagNo, weightKg: 119.4, weighTime: nowText(), targetSlaughterhouse: '宰前门槛验证屠宰场',
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

  // 瘦肉精阴性：先过掉「未检不得屠宰」这道门槛，这几头猪才轮得到宰前检查的判断
  const racto = track(await report(READER_DEVICE, {
    earTagNo, batchNo, sampleNo: `S-PSG-${stamp}-${suffix}`, result: 1, testTime: nowText(),
    testMethod: '胶体金快速检测卡', samplePart: '尿液', operator: '赵检测',
  }))

  return {
    earTagNo,
    certNo,
    pigId,
    applyId,
    entryId,
    entryOk: entry?.data?.accepted === true,
    ractoOk: racto?.data?.accepted === true,
  }
}

/** 宰前检查上报（工位终端） */
const preInspect = (earTagNo, batchNo, result) => report(STATION_DEVICE, {
  earTagNo, batchNo, inspectType: 1, result, temperature: result === 1 ? 38.6 : 40.2,
  veterinary: '李兽医', licenseNo: 'VET-PRE-001',
  conclusion: result === 1 ? '宰前临床检查合格，准予屠宰' : '宰前临床检查发现异常，不准予屠宰',
  issueDesc: result === 1 ? undefined : '精神沉郁、体温偏高',
  disposal: result === 1 ? undefined : '隔离观察',
})

/** 宰后检验上报（工位终端）；inspectType 2=宰后 3=宰后同步 */
const postInspect = (earTagNo, batchNo, inspectType = 2) => report(STATION_DEVICE, {
  earTagNo, batchNo, inspectType, result: 1, temperature: 38.5,
  veterinary: '李兽医', licenseNo: 'VET-001', conclusion: '宰后检验合格', organCheck: '心肺肝未见异常',
})

/** 胴体盖章上报（自动盖章机） */
const stampReport = (earTagNo, batchNo, carcassNo) => report(STAMPER_DEVICE, {
  earTagNo, batchNo, carcassNo, veterinary: '李兽医',
})

console.log('\n== 1) 准备四头临时猪（E 无宰前 / F 宰前不合格 / G 宰前合格 / H 宰前合格但无宰后）==')
const E = await setupPig('E', batchE)
const F = await setupPig('F', batchF)
const G = await setupPig('G', batchG)
const H = await setupPig('H', batchH)
check('E 入场查验入账', E.entryOk, `earTag=${E.earTagNo}`)
check('F 入场查验入账', F.entryOk, `earTag=${F.earTagNo}`)
check('G 入场查验入账', G.entryOk, `earTag=${G.earTagNo}`)
check('H 入场查验入账', H.entryOk, `earTag=${H.earTagNo}`)
check('四头猪都拿到档案编号', !!E.pigId && !!F.pigId && !!G.pigId && !!H.pigId,
  `E=${E.pigId} F=${F.pigId} G=${G.pigId} H=${H.pigId}`)
check('四头猪瘦肉精阴性都已入账（先过掉「未检不得屠宰」）',
  E.ractoOk && F.ractoOk && G.ractoOk && H.ractoOk,
  `E=${E.ractoOk} F=${F.ractoOk} G=${G.ractoOk} H=${H.ractoOk}`)

console.log('\n== 2) 宰前检查：E 不做 / F 判不合格 / G、H 判合格 ==')
const preF = track(await preInspect(F.earTagNo, batchF, 0))
check('F 宰前检查（不合格）照常入账留证',
  preF?.data?.accepted === true, JSON.stringify(preF?.data).slice(0, 220))
if (preF?.data?.targetId) cleanup.inspections.push(preF.data.targetId)

const preG = track(await preInspect(G.earTagNo, batchG, 1))
check('G 宰前检查（合格）入账', preG?.data?.accepted === true, JSON.stringify(preG?.data).slice(0, 220))
if (preG?.data?.targetId) cleanup.inspections.push(preG.data.targetId)

const preH = track(await preInspect(H.earTagNo, batchH, 1))
check('H 宰前检查（合格）入账', preH?.data?.accepted === true, JSON.stringify(preH?.data).slice(0, 220))
if (preH?.data?.targetId) cleanup.inspections.push(preH.data.targetId)

console.log('\n== 3) ★ 未做宰前检查，不得进入宰后检验 ==')
const postE = track(await postInspect(E.earTagNo, batchE, 2))
check('★ E（无宰前记录）宰后检验被拒',
  postE?.data?.accepted !== true && postE?.data?.status === 3,
  `status=${postE?.data?.status} message=${postE?.data?.message}`)
check('★ 原因写明「尚未做宰前检查」',
  String(postE?.data?.message || '').includes('尚未做宰前检查'), `message=${postE?.data?.message}`)
check('★ 原因写明「未做宰前检查不得屠宰」',
  String(postE?.data?.message || '').includes('未做宰前检查不得屠宰'), `message=${postE?.data?.message}`)
check('★ 拒绝不可重试（没检过是事实，人工确认也绕不过去）',
  postE?.data?.retryable !== true, `retryable=${postE?.data?.retryable}`)

const postSyncE = track(await postInspect(E.earTagNo, batchE, 3))
check('★ E 报「宰后同步检验(3)」同样被拒（3 也是宰后类，同一道门槛）',
  postSyncE?.data?.accepted !== true && postSyncE?.data?.status === 3,
  `status=${postSyncE?.data?.status} message=${postSyncE?.data?.message}`)

console.log('\n== 4) ★ 宰前检查判不合格，同样不得进入宰后检验 ==')
const postF = track(await postInspect(F.earTagNo, batchF, 2))
check('★ F（宰前不合格）宰后检验被拒',
  postF?.data?.accepted !== true && postF?.data?.status === 3,
  `status=${postF?.data?.status} message=${postF?.data?.message}`)
check('★ 原因写明「宰前检查结论为不合格」',
  String(postF?.data?.message || '').includes('宰前检查结论为不合格'), `message=${postF?.data?.message}`)
check('★ 口径区分正确：是「检了没过」而不是「没检」',
  !String(postF?.data?.message || '').includes('尚未做宰前检查'), `message=${postF?.data?.message}`)

console.log('\n== 5) 宰前合格的猪正常进入宰后检验 ==')
const postG = track(await postInspect(G.earTagNo, batchG, 2))
check('G（宰前合格）报宰后检验(2) 入账',
  postG?.data?.accepted === true, JSON.stringify(postG?.data).slice(0, 220))
if (postG?.data?.targetId) cleanup.inspections.push(postG.data.targetId)

const postSyncG = track(await postInspect(G.earTagNo, batchG, 3))
check('G 再报「宰后同步检验(3)」也入账（3 以同一份宰前合格为前提）',
  postSyncG?.data?.accepted === true, JSON.stringify(postSyncG?.data).slice(0, 220))
if (postSyncG?.data?.targetId) cleanup.inspections.push(postSyncG.data.targetId)

console.log('\n== 6) ★ 这道门槛只拦宰后，不拦宰前本身（否则一头猪也进不来）==')
const preE = track(await preInspect(E.earTagNo, batchE, 1))
check('★ E 补做宰前检查（判合格）→ 入账',
  preE?.data?.accepted === true, JSON.stringify(preE?.data).slice(0, 220))
if (preE?.data?.targetId) cleanup.inspections.push(preE.data.targetId)

const postE2 = track(await postInspect(E.earTagNo, batchE, 2))
check('★ 补做宰前检查后，E 的宰后检验放行（门槛是「先宰前」，不是「永久拦住」）',
  postE2?.data?.accepted === true, JSON.stringify(postE2?.data).slice(0, 220))
if (postE2?.data?.targetId) cleanup.inspections.push(postE2.data.targetId)

console.log('\n== 7) ★ 盖章要求「结论合格的宰后检验」——只有一条宰前合格不算数 ==')
const stH = track(await stampReport(H.earTagNo, batchH, `CS-PSG-${stamp}-H`))
check('★ H（只有宰前合格、没做宰后）盖章被拒',
  stH?.data?.accepted !== true && stH?.data?.status === 3,
  `status=${stH?.data?.status} message=${stH?.data?.message}`)
check('★ 原因写明「没有结论为『合格』的宰后检验记录」',
  String(stH?.data?.message || '').includes('没有结论为「合格」的宰后检验记录'), `message=${stH?.data?.message}`)

const stG = track(await stampReport(G.earTagNo, batchG, `CS-PSG-${stamp}-G`))
check('G（已有合格的宰后检验）盖章入账',
  stG?.data?.accepted === true, JSON.stringify(stG?.data).slice(0, 220))
if (stG?.data?.targetId) cleanup.stamps.push(stG.data.targetId)

console.log('\n== 8) ★ 检验类型筛选：「宰后检验」含同步，「宰后检验（同步）」精确到 3 ==')
{
  const myPigs = [E.pigId, F.pigId, G.pigId, H.pigId]
  const mine = (res) => (res?.data?.records || [])
    .filter((r) => myPigs.includes(Number(r?.pigId ?? r?.pig_id)))
    .map((r) => Number(r?.inspectType ?? r?.inspect_type))

  const byPre = await api('/slaughter/inspections?pageNum=1&pageSize=500&inspectType=' + encodeURIComponent('宰前检验'), { token })
  const preTypes = mine(byPre)
  check('筛「宰前检验」有命中', preTypes.length > 0, `records=${preTypes.length}`)
  check('★ 筛「宰前检验」返回的全是 1', preTypes.every((t) => t === 1), `types=${preTypes.join(',')}`)

  const byPost = await api('/slaughter/inspections?pageNum=1&pageSize=500&inspectType=' + encodeURIComponent('宰后检验'), { token })
  const postTypes = [...new Set(mine(byPost))]
  check('★ 筛「宰后检验」同时带出 2 和 3（两者是同一环节）',
    postTypes.includes(2) && postTypes.includes(3), `types=${postTypes.join(',')}`)

  const bySync = await api('/slaughter/inspections?pageNum=1&pageSize=500&inspectType=' + encodeURIComponent('宰后检验（同步）'), { token })
  const syncTypes = [...new Set(mine(bySync))]
  check('★ 筛「宰后检验（同步）」有命中', syncTypes.length > 0, `types=${syncTypes.join(',')}`)
  check('★ 筛「宰后检验（同步）」精确到 3，不再返回全部类型',
    syncTypes.every((t) => t === 3), `types=${syncTypes.join(',')}`)
}

console.log('\n== 9) 来源策略表里「屠宰检验」的说明已带上宰前前提 ==')
{
  const res = await api('/ingest/source-policy', { token })
  const row = (res?.data || []).find((p) => p.code === 'INSPECTION')
  check('策略表里有「屠宰检验」', !!row, JSON.stringify(res).slice(0, 200))
  check('策略仍为「设备独占」', row?.policy === 'DEVICE_ONLY', `policy=${row?.policy}`)
  check('说明提到「未做宰前检查不得屠宰」',
    String(row?.note || '').includes('未做宰前检查不得屠宰'), `note=${row?.note}`)
}

console.log(`\n===== 结果：${pass} 项通过，${fail} 项失败 =====`)
console.log('清理用 —— 接入流水 ID：' + stagingIds.join(','))
console.log('清理用 —— 临时猪 ID：' + cleanup.pigs.join(','))
console.log('清理用 —— 出栏申报 ID：' + cleanup.applies.join(','))
console.log('清理用 —— 入场查验 ID：' + cleanup.entries.join(','))
console.log('清理用 —— 屠宰检验 ID：' + cleanup.inspections.join(','))
console.log('清理用 —— 盖章 ID：' + cleanup.stamps.join(','))
console.log(`清理用 —— 耳标：${E.earTagNo},${F.earTagNo},${G.earTagNo},${H.earTagNo}`)
console.log(`清理用 —— 批次：${batchE},${batchF},${batchG},${batchH}`)
process.exit(fail === 0 ? 0 : 1)
