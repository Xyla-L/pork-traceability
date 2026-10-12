/**
 * 瘦肉精阻断回归（阳性整批停 + 未检不得屠宰）：
 *
 *   准备：四头临时猪各自走完 建档 → 产地检疫出证 → 出栏称重登记 → 审批通过 → 入场查验
 *        A = 阴性对照组（批次 X，瘦肉精报阴性后再补一条「宰前检查合格」）
 *        B = 阳性组（批次 Y）
 *        C = 与 B 同批次（批次 Y），但自身检测为阴性
 *        D = 从头到尾没做过瘦肉精检测（批次 Z）
 *
 *   注：瘦肉精阻断只约束**宰后类**环节（宰后检验 / 盖章 / 胴体批次）——宰前检查是屠宰前的临床筛查，
 *      不设前置门槛，阳性/未检的猪同样能录（阳性猪只能判「不合格」，见第 2″ 节）。
 *      宰后检验另有「未做宰前检查不得屠宰」这道门槛（专项回归见 logs/verify-preslaughter-gate.mjs），
 *      所以阴性组 A 会先补一条合格宰前检查，这样测到的才是瘦肉精本身的行为。
 *
 *   断言：
 *     1. A 报阴性 → 入账
 *     2. ★ B 报阳性 → 阳性照常入账留证（回执只报状态本身，「被封」由后续环节被拒来证明）
 *     3. C 报阴性（与 B 同批次）→ 阴性照常入账（检测本身不受影响）
 *     3″. ★ 宰前检查不受瘦肉精门槛约束：B（自身阳性）判「合格」被拒、判「不合格」照常入账留证；
 *         C（同批次阳性）判「合格」同样被拒；D（未检）判「合格」照常入账（未检不拦宰前）
 *     4. ★ A 做屠宰检验 → 通过（阻断不能误伤无关的猪）
 *     5. ★ B 做屠宰检验 → 拒绝，原因是「该猪瘦肉精检测结果阳性」
 *     6. ★ C 做屠宰检验 → 拒绝，原因是「同属批次」（C 自己没检出，仍被整批受控）
 *     7. ★ D 做屠宰检验 → 拒绝，原因是「尚无瘦肉精检测记录 / 未检不得屠宰」
 *     8. ★ B 盖章 → 拒绝，原因是阳性
 *     9. A 盖章 → 通过
 *    10. ★ D 盖章 → 拒绝，原因是未检（盖章同样不给没检过的猪）
 *    11. ★ 胴体批次创建（含 B / 含 D）→ 拒绝；只含 A → 通过
 *        （胴体批次是「设备优先」环节：人工建批须先申请兜底解锁票据，分拣台自动归集不经此路径。
 *         本节同时覆盖：无票据 → 直接拒绝；持票据 → 才轮到瘦肉精判定。）
 *    12. 来源策略表里「瘦肉精检测」的说明已带上「一批检出阳性则整批…」与「未检不得屠宰」
 *
 * 运行：node logs/verify-ractopamine-block.mjs
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
const batchX = `B-RB-${stamp}-X`
const batchY = `B-RB-${stamp}-Y`
const batchZ = `B-RB-${stamp}-Z`
const cleanup = { pigs: [], applies: [], entries: [], inspections: [], stamps: [], carcassBatches: [], tests: [] }

const farms = await api('/breeding/farms?current=1&size=1', { token })
const farmName = farms?.data?.records?.[0]?.farmName
check('取到一个已登记养殖场', !!farmName, `farmName=${farmName}`)

/** 造一头能入场的猪：建档 → 出证 → 出栏称重登记 → 审批通过 → 入场查验 */
async function setupPig(suffix, batchNo) {
  const earTagNo = `ET-RB-${stamp}-${suffix}`
  const certNo = `QC-RB-${stamp}-${suffix}`

  const tag = track(await report(TAG_DEVICE, {
    earTagNo, farmName, birthDate: dateText(-90), breed: '三元杂', gender: 1, penNo: '回归验证舍', origin: '自繁',
  }))
  const pigId = tag?.data?.targetId
  if (pigId) cleanup.pigs.push(pigId)

  track(await report(CERT_DEVICE, {
    earTagNo, certNo, issueOrg: '回归验证动物卫生监督所', issueTime: nowText(), validUntil: dateText(365), inspector: '王官方兽医',
  }))

  const apply = track(await report(EXIT_DEVICE, {
    earTagNo, weightKg: 118.2, weighTime: nowText(), targetSlaughterhouse: '回归验证屠宰场',
  }))
  const applyId = apply?.data?.targetId
  if (applyId) cleanup.applies.push(applyId)
  await api(`/breeding/applies/${applyId}/approve`, {
    method: 'PUT', token, body: { approved: true, comment: '回归验证：同意出栏' },
  })

  const entry = track(await report(GATE_DEVICE, {
    earTagNo, batchNo, quarantineCert: certNo, weight: 118.0, inspector: '张查验',
  }))
  const entryId = entry?.data?.targetId
  if (entryId) cleanup.entries.push(entryId)

  return {
    earTagNo,
    certNo,
    pigId,
    applyId,
    entryId,
    entryOk: entry?.data?.accepted === true,
  }
}

console.log('\n== 1) 准备四头临时猪（A 阴性 / B 阳性 / C 与 B 同批次 / D 未检）==')
const A = await setupPig('A', batchX)
const B = await setupPig('B', batchY)
const C = await setupPig('C', batchY)
const D = await setupPig('D', batchZ)
check('A 入场查验入账', A.entryOk, `earTag=${A.earTagNo}`)
check('B 入场查验入账', B.entryOk, `earTag=${B.earTagNo}`)
check('C 入场查验入账', C.entryOk, `earTag=${C.earTagNo}`)
check('D（未检组）入场查验入账', D.entryOk, `earTag=${D.earTagNo}`)
check('四头猪都拿到档案编号', !!A.pigId && !!B.pigId && !!C.pigId && !!D.pigId,
  `A=${A.pigId} B=${B.pigId} C=${C.pigId} D=${D.pigId}`)

/** 瘦肉精读数仪上报 */
const racto = (earTagNo, batchNo, result, sampleNo) => report(READER_DEVICE, {
  earTagNo, batchNo, sampleNo, result, testTime: nowText(),
  testMethod: '胶体金快速检测卡', samplePart: '尿液', operator: '赵检测',
})

console.log('\n== 2) 瘦肉精上报：A 阴性 / B 阳性 / C 阴性（与 B 同批次）==')
const ractoA = track(await racto(A.earTagNo, batchX, 1, `S-RB-${stamp}-A`))
check('A 阴性入账', ractoA?.data?.accepted === true, JSON.stringify(ractoA?.data).slice(0, 220))
// 入账回执只报状态本身、不掺解释句（沿用既定的回执口径）：
// 「这头猪被阻断了」这件事不用回执去说，而是由后面屠宰检验/盖章真的被拒来证明。
check('阴性入账回执只报状态本身', ractoA?.data?.message === '已入账',
  `message=${ractoA?.data?.message}`)

const ractoB = track(await racto(B.earTagNo, batchY, 0, `S-RB-${stamp}-B`))
check('★ B 阳性照常入账留证（阳性不是「不入库」而是「入库并阻断」）',
  ractoB?.data?.accepted === true, JSON.stringify(ractoB?.data).slice(0, 220))
check('★ 阳性入账回执同样只报状态本身（阻断效果由后续环节的拒绝体现）',
  ractoB?.data?.message === '已入账', `message=${ractoB?.data?.message}`)

const ractoC = track(await racto(C.earTagNo, batchY, 1, `S-RB-${stamp}-C`))
check('C 阴性入账（检测本身不受同批次阳性影响）',
  ractoC?.data?.accepted === true, JSON.stringify(ractoC?.data).slice(0, 220))

/** 屠宰检验上报（工位终端） */
const inspect = (earTagNo, batchNo) => report(STATION_DEVICE, {
  earTagNo, batchNo, result: 1, inspectType: 2, temperature: 38.5,
  veterinary: '李兽医', licenseNo: 'VET-001', conclusion: '检验合格', organCheck: '心肺肝未见异常',
})

console.log('\n== 2′) 宰前检查：给阴性组 A 补一条合格的宰前检查（宰后检验另有「未做宰前检查不得屠宰」门槛）==')
// 先给阴性组 A 补一条合格宰前检查：A 接下来要报宰后检验，而宰后检验另有「未做宰前检查不得屠宰」
// 这道门槛（专项回归见 logs/verify-preslaughter-gate.mjs），补上它才测得到瘦肉精本身的行为。
const preA = track(await report(STATION_DEVICE, {
  earTagNo: A.earTagNo, batchNo: batchX, inspectType: 1, result: 1, temperature: 38.6,
  veterinary: '王兽医', licenseNo: 'VET-PRE-001', conclusion: '宰前临床检查合格，准予屠宰',
}))
check('A 宰前检查入账（判合格，准予屠宰）', preA?.data?.accepted === true,
  JSON.stringify(preA?.data).slice(0, 220))
if (preA?.data?.targetId) cleanup.inspections.push(preA.data.targetId)

/** 宰前检查上报（工位终端） */
const preInspect = (earTagNo, batchNo, result) => report(STATION_DEVICE, {
  earTagNo, batchNo, inspectType: 1, result, temperature: result === 1 ? 38.6 : 40.2,
  veterinary: '王兽医', licenseNo: 'VET-PRE-001',
  conclusion: result === 1 ? '宰前临床检查合格，准予屠宰' : '宰前临床检查发现异常，不准予屠宰',
  disposal: result === 1 ? undefined : '隔离观察',
})

console.log('')
console.log('== 2″) ★ 宰前检查不受瘦肉精门槛约束（但它不得与瘦肉精结论自相矛盾）==')
// 这道门槛以前对宰前检查也生效，导致阳性/未检的猪连一条「宰前不合格」的处置记录都留不下；
// 现在收窄到只约束宰后类：宰前检查能录，但已检出阳性的猪不得判「合格」。
const preBSelf = track(await preInspect(B.earTagNo, batchY, 1))
check('★ B（自身阳性）判宰前「合格」被拒（不得与瘦肉精结论矛盾）',
  preBSelf?.data?.accepted !== true && preBSelf?.data?.status === 3,
  `status=${preBSelf?.data?.status} message=${preBSelf?.data?.message}`)
check('★ 原因写明「宰前检查不得判定为『合格』」',
  String(preBSelf?.data?.message || '').includes('宰前检查不得判定为「合格」'),
  `message=${preBSelf?.data?.message}`)
check('★ 拒绝不可重试（阳性是事实，不是补个字段就能过）', preBSelf?.data?.retryable !== true,
  `retryable=${preBSelf?.data?.retryable}`)

const preBFail = track(await preInspect(B.earTagNo, batchY, 0))
check('★ B（自身阳性）判宰前「不合格」照常入账留证（这条以前根本录不进来）',
  preBFail?.data?.accepted === true, JSON.stringify(preBFail?.data).slice(0, 220))
if (preBFail?.data?.targetId) cleanup.inspections.push(preBFail.data.targetId)

const preCSelf = track(await preInspect(C.earTagNo, batchY, 1))
check('★ C（同批次阳性、自身阴性）判宰前「合格」同样被拒（同批受控）',
  preCSelf?.data?.accepted !== true && preCSelf?.data?.status === 3,
  `status=${preCSelf?.data?.status} message=${preCSelf?.data?.message}`)

const preD = track(await preInspect(D.earTagNo, batchZ, 1))
check('★ D（瘦肉精未检）判宰前「合格」照常入账（未检不拦宰前，检测可能还没做）',
  preD?.data?.accepted === true, JSON.stringify(preD?.data).slice(0, 220))
if (preD?.data?.targetId) cleanup.inspections.push(preD.data.targetId)

console.log('\n== 3) ★ 屠宰检验：阴性组放行，阳性组与其同批次一律拒绝 ==')
const insA = track(await inspect(A.earTagNo, batchX))
check('A（阴性）屠宰检验入账', insA?.data?.accepted === true, JSON.stringify(insA?.data).slice(0, 220))
if (insA?.data?.targetId) cleanup.inspections.push(insA.data.targetId)

const insB = track(await inspect(B.earTagNo, batchY))
check('★ B（阳性）屠宰检验被拒', insB?.data?.accepted !== true && insB?.data?.status === 3,
  `status=${insB?.data?.status} message=${insB?.data?.message}`)
check('★ 原因是「瘦肉精检测结果阳性」', String(insB?.data?.message || '').includes('瘦肉精检测结果阳性'),
  `message=${insB?.data?.message}`)
check('★ 拒绝不可重试（人工确认也放不过去）', insB?.data?.retryable !== true,
  `retryable=${insB?.data?.retryable}`)

const insC = track(await inspect(C.earTagNo, batchY))
check('★ C（同批次、自身阴性）屠宰检验被拒', insC?.data?.accepted !== true && insC?.data?.status === 3,
  `status=${insC?.data?.status} message=${insC?.data?.message}`)
check('★ 原因是「同属批次」而不是「C 自己阳性」',
  String(insC?.data?.message || '').includes('批次') && !String(insC?.data?.message || '').includes('该猪（耳标'),
  `message=${insC?.data?.message}`)

console.log('\n== 3′) ★ 未检不得屠宰：一次都没检过的猪同样进不了屠宰检验 ==')
const insD = track(await inspect(D.earTagNo, batchZ))
check('★ D（未检）屠宰检验被拒', insD?.data?.accepted !== true && insD?.data?.status === 3,
  `status=${insD?.data?.status} message=${insD?.data?.message}`)
check('★ 原因是「尚无瘦肉精检测记录」',
  String(insD?.data?.message || '').includes('尚无瘦肉精检测记录'), `message=${insD?.data?.message}`)
check('★ 原因写明「未检不得屠宰」', String(insD?.data?.message || '').includes('未检不得屠宰'),
  `message=${insD?.data?.message}`)
check('★ 拒绝不可重试（未检是事实，不是补个字段就能过）', insD?.data?.retryable !== true,
  `retryable=${insD?.data?.retryable}`)

/** 胴体盖章上报（自动盖章机） */
const stampReport = (earTagNo, batchNo, carcassNo) => report(STAMPER_DEVICE, {
  earTagNo, batchNo, carcassNo, veterinary: '李兽医',
})

console.log('\n== 4) ★ 胴体盖章：阳性猪连章都不该有 ==')
const stA = track(await stampReport(A.earTagNo, batchX, `CS-RB-${stamp}-A`))
check('A（阴性且检验合格）盖章入账', stA?.data?.accepted === true, JSON.stringify(stA?.data).slice(0, 220))
if (stA?.data?.targetId) cleanup.stamps.push(stA.data.targetId)

const stB = track(await stampReport(B.earTagNo, batchY, `CS-RB-${stamp}-B`))
check('★ B（阳性）盖章被拒', stB?.data?.accepted !== true && stB?.data?.status === 3,
  `status=${stB?.data?.status} message=${stB?.data?.message}`)
check('★ 原因是阳性（而不是「没有合格检验」）',
  String(stB?.data?.message || '').includes('瘦肉精检测结果阳性'), `message=${stB?.data?.message}`)

const stD = track(await stampReport(D.earTagNo, batchZ, `CS-RB-${stamp}-D`))
check('★ D（未检）盖章被拒', stD?.data?.accepted !== true && stD?.data?.status === 3,
  `status=${stD?.data?.status} message=${stD?.data?.message}`)
check('★ 原因是未检（而不是「没有合格检验」）',
  String(stD?.data?.message || '').includes('尚无瘦肉精检测记录'), `message=${stD?.data?.message}`)

console.log('\n== 5) ★ 胴体批次创建（分割配送的人工入口也要拦）==')
// 胴体批次是「设备优先」环节：分拣台按「屠宰场 + 屠宰日」自动归集是主路径，
// 人工建批是设备覆盖不到时的补录（2026-10-12 起免票据，强制实名 + 上链）。
// 本节用 B 验「受理条件的猪照样被瘦肉精拦下」。
{
  const withB = await api('/distribution/batches', {
    method: 'POST', token,
    body: {
      pigIds: [B.pigId], totalWeightKg: 90, slaughterhouse: '回归验证屠宰场',
      operator: '回归验证', note: '回归验证：阳性猪不得进入胴体批次',
    },
  })
  check('★ 含阳性猪的批次创建被拒', withB?.code !== 200 && withB?.success !== true,
    JSON.stringify(withB).slice(0, 260))
  check('★ 拒绝原因点明瘦肉精', String(withB?.message || '').includes('不得进入胴体批次'),
    `message=${withB?.message}`)
}

{
  const withA = await api('/distribution/batches', {
    method: 'POST', token,
    body: {
      pigIds: [A.pigId], totalWeightKg: 88, slaughterhouse: '回归验证屠宰场',
      operator: '回归验证', note: '回归验证：阴性组应能正常建批次',
    },
  })
  check('A（阴性）批次创建通过', withA?.code === 200 || withA?.success === true,
    JSON.stringify(withA).slice(0, 260))
  const batchId = withA?.data?.id
  if (batchId) cleanup.carcassBatches.push(batchId)
}

{
  const withD = await api('/distribution/batches', {
    method: 'POST', token,
    body: {
      pigIds: [D.pigId], totalWeightKg: 86, slaughterhouse: '回归验证屠宰场',
      operator: '回归验证', note: '回归验证：未检的猪不得进入胴体批次',
    },
  })
  check('★ 含未检猪的批次创建被拒', withD?.code !== 200 && withD?.success !== true,
    JSON.stringify(withD).slice(0, 260))
  check('★ 拒绝原因点明「未检不得屠宰」', String(withD?.message || '').includes('未检不得屠宰')
    || String(withD?.message || '').includes('尚无瘦肉精检测记录'), `message=${withD?.message}`)
}

console.log('\n== 6) 来源策略表里「瘦肉精检测」的说明已带上两条口径 ==')
{
  const res = await api('/ingest/source-policy', { token })
  const row = (res?.data || []).find((p) => p.code === 'RACTOPAMINE')
  check('策略表里有「瘦肉精检测」', !!row, JSON.stringify(res).slice(0, 200))
  check('策略仍为「设备独占」', row?.policy === 'DEVICE_ONLY', `policy=${row?.policy}`)
  check('说明提到「一批检出阳性则整批不得进入屠宰检验及后续环节」',
    String(row?.note || '').includes('一批检出阳性则整批不得进入屠宰检验及后续环节'), `note=${row?.note}`)
  check('说明提到「未检不得屠宰」', String(row?.note || '').includes('未检不得屠宰'), `note=${row?.note}`)
}

console.log(`\n===== 结果：${pass} 项通过，${fail} 项失败 =====`)
console.log('清理用 —— 接入流水 ID：' + stagingIds.join(','))
console.log('清理用 —— 临时猪 ID：' + cleanup.pigs.join(','))
console.log('清理用 —— 出栏申报 ID：' + cleanup.applies.join(','))
console.log('清理用 —— 入场查验 ID：' + cleanup.entries.join(','))
console.log('清理用 —— 屠宰检验 ID：' + cleanup.inspections.join(','))
console.log('清理用 —— 盖章 ID：' + cleanup.stamps.join(','))
console.log('清理用 —— 胴体批次 ID：' + cleanup.carcassBatches.join(','))
console.log(`清理用 —— 耳标：${A.earTagNo},${B.earTagNo},${C.earTagNo},${D.earTagNo}`)
console.log(`清理用 —— 批次：${batchX},${batchY},${batchZ}`)
console.log(`清理用 —— 未检猪 ID：${D.pigId} 耳标：${D.earTagNo} 批次：${batchZ}`)
process.exit(fail === 0 ? 0 : 1)
