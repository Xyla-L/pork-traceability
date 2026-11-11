/**
 * 屠宰四张列表「按批次折叠」回归：入场查验 / 屠宰检验 / 检疫盖章 / 瘦肉精检测。
 *
 * 覆盖：
 *  A 同批 3 头入场查验 → 1 组、头数 3、耳标区间正确、明细逐头在、结论「全部合格」
 *  B 同批宰前检验 3 条 + 宰后 2 条合格 + 1 条不合格 → 1 组、结论「含不合格」、计数明细正确
 *  C 同批盖章 2 枚 → 1 组、结论「全部已盖章」
 *  D 同批瘦肉精 3 阴性 + 1 阳性 → 1 组、结论「含阳性」（阳性必须写在主行上）
 *  E 未归批（batch_no 为空）的记录 → 每头独立成组，绝不互并
 *  F 分页按「组」算：size=1 翻两页拿到两组不同 groupKey，total 不变
 *
 * 数据自持：自建场 + 3 头批次，走设备通道（闸口/工位/盖章机/读数仪）真实上报，
 * 跑完按耳标前缀清业务表 + staging + 链上事件，不动演示台账。
 */
import { execSync } from 'node:child_process'

const BASE = process.env.BASE_URL || 'http://localhost/api/v1'
const MYSQL = process.env.MYSQL_CT || 'pork-traceability-mysql-1'
const DB_USER = process.env.DB_USER || 'pork_user'
const DB_PASS = process.env.DB_PASS || '123456'

const RUN = String(Date.now()).slice(-6)
const FARM_NAME = `折叠回归场${RUN}`
const BATCH_NO = `PB-FOLD${RUN}`
const TAG_PREFIX = `ET-FOLD${RUN}`
const EAR_TAGS = [1, 2, 3].map((n) => `${TAG_PREFIX}${String(n).padStart(3, '0')}`)
const FARM_BATCH = `B-FOLD-${RUN}`

let token = null
let pass = 0
let fail = 0
const failures = []
const stagingIds = []

function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`  ✅ ${name}`)
  } else {
    fail++
    failures.push(name)
    console.log(`  ❌ ${name}${detail ? ' — ' + detail : ''}`)
  }
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  // body 允许直接给对象：调用处少写一次 JSON.stringify，也不容易漏
  const body = options.body == null
    ? undefined
    : typeof options.body === 'string' ? options.body : JSON.stringify(options.body)
  const res = await fetch(`${BASE}${path}`, { ...options, headers, body })
  let json = null
  try { json = await res.json() } catch { /* 空响应体 */ }
  return { status: res.status, body: json, data: json?.data, code: json?.code, message: json?.msg || json?.message }
}

function sql(statement) {
  const escaped = statement.replace(/"/g, '\\"')
  const cmd = `docker exec ${MYSQL} mysql -u${DB_USER} -p${DB_PASS} --default-character-set=utf8mb4 -N -e "${escaped}" 2>&1 | grep -v Warning || true`
  return execSync(cmd, { encoding: 'utf8' }).trim()
}

function sqlOne(statement) {
  return sql(statement).split('\n')[0].trim()
}

const pad = (n) => String(n).padStart(2, '0')
const nowText = (offsetSec = 0) => {
  const d = new Date(Date.now() + offsetSec * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
const dateText = (days) => {
  const d = new Date(Date.now() + days * 86400000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** tick：同一次回归里多条报文必须错开时间，否则同秒全等会撞上兜底判重 */
let tick = 0
const nextTime = () => nowText(-(tick++))

/** 1x1 合法 PNG：免疫上报必须带真实存在的凭证照片（编造的 fileId 会被接入层判拒绝） */
async function uploadVoucher() {
  const png = Buffer.from(
    '89504e470d0a1a0a0000000d494844520000000100000001080200000090775'
    + '3de0000000c4944415408d763f8cfc000000301010018dd8db00000000049454e44ae426082',
    'hex',
  )
  const fd = new FormData()
  fd.append('file', new Blob([png], { type: 'image/png' }), `fold-${RUN}.png`)
  const res = await fetch(`${BASE}/file/upload`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd,
  })
  const body = await res.json().catch(() => null)
  return body?.data?.fileId
}

/** 代设备上报（走完整接入链路：鉴权 → 校验 → 判重 → 派发 → 入账） */
async function deviceReport(deviceNo, bizKey, data) {
  const r = await api('/ingest/demo/report', {
    method: 'POST',
    body: { deviceNo, bizKey: `${bizKey}-${RUN}`, reportTime: nextTime(), sourceRef: `折叠回归-${deviceNo}`, data },
  })
  const sid = r?.data?.stagingId
  if (sid) stagingIds.push(sid)
  return r
}

// ---------------------------------------------------------------- 0 登录 + 造 3 头
console.log('▶ 0 建场 + 3 头批次 + 出证 + 出栏审批 + 入场')
{
  const login = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'admin', password: '123456' }),
  })
  token = login?.data?.token || login?.data?.accessToken
  check('管理员登录', !!token)
  if (!token) process.exit(1)

  const farm = await api('/breeding/farms', {
    method: 'POST',
    body: JSON.stringify({
      farmName: FARM_NAME,
      licenseNo: `LIC-FOLD${RUN}`,
      scale: 300,
      address: '折叠回归地址',
      contactPerson: '折叠场主',
      contactPhone: '13800000000',
    }),
  })
  check('建场成功', farm?.code === 200 || farm?.code === 0, JSON.stringify(farm?.body).slice(0, 200))
  const farmId = sqlOne(`USE db_breeding; SELECT id FROM farm WHERE farm_name = '${FARM_NAME}' LIMIT 1;`)
  check('取到养殖场 id', !!farmId && farmId !== 'NULL', `farmId=${farmId}`)

  const created = await api('/breeding/pigs/batches', {
    method: 'POST',
    body: JSON.stringify({
      batchNo: BATCH_NO, farmId: Number(farmId), earTagStart: EAR_TAGS[0], earTagEnd: EAR_TAGS[2],
      breed: '三元杂', entryDate: dateText(-120), source: '自繁',
    }),
  })
  check('批次登记成功（3 头）', created?.code === 200 || created?.code === 0, JSON.stringify(created?.body).slice(0, 240))
  const pigCount = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  check('批次展开出 3 个成员行', String(pigCount) === '3', `实际 ${pigCount}`)

  // 每头：免疫 → 产地检疫出证 → 出栏称重申报 → 审批通过 → 闸口入场
  // 顺序不能反：产地检疫要查验强制免疫档案（ImmunityGate），没有免疫记录的猪现在会被
  // 当场拒绝出证。以前这一步是空的，回归照样"全绿"，等于把缺免疫的证当正常流程。
  const voucherId = await uploadVoucher()
  check('接种凭证照片上传成功（免疫必需的真实凭证）', !!voucherId, '未拿到 fileId')
  for (const tag of EAR_TAGS) {
    const vaccine = await deviceReport('INJ-001', `vac-${tag}`, {
      earTagNo: tag,
      vaccineName: '猪瘟活疫苗',
      vaccineBatchNo: `VB-FOLD${RUN}`,
      injectTime: nowText(-3600),
      dosage: '1ml',
      injectSite: '耳后皮下',
      operator: '张兽医',
      fileIds: voucherId ? [voucherId] : [],
    })
    const cert = await deviceReport('QSCAN-001', `cert-${tag}`, {
      earTagNo: tag,
      certNo: `QC-FOLD${RUN}-${tag}`,
      issueOrg: `折叠回归检疫站${RUN}`,
      issueTime: nowText(0),
      validUntil: dateText(365),
      inspector: '王官方兽医',
    })
    const apply = await deviceReport('EXITSCALE-001', `apply-${tag}`, {
      earTagNo: tag,
      weightKg: 118.5,
      weighTime: nowText(0),
      targetSlaughterhouse: `折叠回归屠宰场${RUN}`,
    })
    if (apply?.data?.targetId) {
      await api(`/breeding/applies/${apply.data.targetId}/approve`, {
        method: 'PUT',
        body: JSON.stringify({ approved: true, comment: '折叠回归：同意出栏' }),
      })
    }
    const entry = await deviceReport('GATE-001', `entry-${tag}`, {
      earTagNo: tag,
      batchNo: FARM_BATCH,
      quarantineCert: `QC-FOLD${RUN}-${tag}`,
      weight: 118.2,
      inspector: '张查验',
    })
    if (tag === EAR_TAGS[2]) {
      check('第三头入场查验入账', entry?.data?.accepted === true, JSON.stringify(entry?.data).slice(0, 240))
      check('免疫记录入账（出证的前置，缺它出证必被拒）', vaccine?.data?.accepted === true,
        JSON.stringify(vaccine?.data).slice(0, 200))
      check('出证全部入账', cert?.data?.accepted === true, JSON.stringify(cert?.data).slice(0, 200))
    }
  }
}

/** 折叠列表查询：kind = entries|inspections|stamps|ractopamine */
async function groups(kind, params = '') {
  const r = await api(`/slaughter/records/${kind}?current=1&size=100${params}`)
  return { total: r?.data?.total || 0, records: r?.data?.records || [] }
}

// ---------------------------------------------------------------- 1 入场查验
console.log('\n▶ 1 入场查验：同批 3 头 = 1 组')
{
  const g = await groups('entries', `&batchNo=${encodeURIComponent(FARM_BATCH)}`)
  const mine = g.records.filter((x) => x.batchNo === FARM_BATCH)
  check('A1 同批聚成 1 组', mine.length === 1, `实际 ${mine.length} 组`)
  const one = mine[0]
  check('A2 头数 = 3', one?.headCount === 3, `实际 ${one?.headCount}`)
  check('A3 耳标区间取到起止', one?.tagStart === EAR_TAGS[0] && one?.tagEnd === EAR_TAGS[2],
    `${one?.tagStart} ~ ${one?.tagEnd}`)
  check('A4 明细逐头保留', one?.members?.length === 3, `实际 ${one?.members?.length}`)
  check('A5 结论「全部合格」', one?.statusSummary === '全部合格', one?.statusSummary)
  check('A6 计数明细「合格 3」', one?.statusDetail === '合格 3', one?.statusDetail)
  check('A7 到场时间给了区间或单点（非空）', !!one?.firstTime, one?.firstTime)
}

// ---------------------------------------------------------------- 2 瘦肉精（先做阴性）
// 顺序有讲究：盖章要求「该批瘦肉精已出结果」，宰后检验也在瘦肉精之后再开，
// 所以这里先把 3 条阴性打进去；阳性单独留到盖章之后，免得整批受控挡住后面的环节。
console.log('\n▶ 2 瘦肉精：同批 3 条阴性 = 1 组，结论「全部阴性」')
{
  for (const tag of EAR_TAGS) {
    const r = await deviceReport('READER-001', `racto-${tag}`, {
      earTagNo: tag, batchNo: FARM_BATCH, sampleNo: `SP-FOLD${RUN}-${tag}`,
      testTime: nowText(0), testMethod: '胶体金法', testType: '克伦特罗',
      result: 1, operator: '赵检测',
    })
    if (tag === EAR_TAGS[2]) {
      check('R0 阴性结果已入账', r?.data?.accepted === true, JSON.stringify(r?.data).slice(0, 240))
    }
  }
  const g = await groups('ractopamine', `&batchNo=${encodeURIComponent(FARM_BATCH)}`)
  const mine = g.records.filter((x) => x.batchNo === FARM_BATCH)
  check('R1 同批聚成 1 组', mine.length === 1, `实际 ${mine.length} 组`)
  check('R2 头数 = 3', mine[0]?.headCount === 3, `实际 ${mine[0]?.headCount}`)
  check('R3 结论「全部阴性」', mine[0]?.statusSummary === '全部阴性', mine[0]?.statusSummary)
  check('R4 计数明细「阴性 3」', mine[0]?.statusDetail === '阴性 3', mine[0]?.statusDetail)
}

// ---------------------------------------------------------------- 3 屠宰检验
console.log('\n▶ 3 屠宰检验：同批 6 条（5 合格 1 不合格）= 1 组，结论「含不合格」')
{
  for (const tag of EAR_TAGS) {
    await deviceReport('STATION-001', `insp-pre-${tag}`, {
      earTagNo: tag, batchNo: FARM_BATCH, inspectType: 1, result: 1, temperature: 39.1,
      veterinary: '李兽医', conclusion: '宰前临床检查未见异常',
    })
  }
  for (const tag of EAR_TAGS.slice(0, 2)) {
    await deviceReport('STATION-001', `insp-post-${tag}`, {
      earTagNo: tag, batchNo: FARM_BATCH, inspectType: 2, result: 1,
      veterinary: '李兽医', organCheck: '内脏未见异常', conclusion: '宰后检验合格',
    })
  }
  const bad = await deviceReport('STATION-001', `insp-bad-${EAR_TAGS[2]}`, {
    earTagNo: EAR_TAGS[2], batchNo: FARM_BATCH, inspectType: 2, result: 0,
    veterinary: '李兽医', organCheck: '肝脏有白斑', conclusion: '宰后检验不合格，整头废弃',
    issueDesc: '肝脏白斑', disposal: '高温处理',
  })
  check('B0 不合格判定已入账', bad?.data?.accepted === true, JSON.stringify(bad?.data).slice(0, 240))

  const g = await groups('inspections', `&batchNo=${encodeURIComponent(FARM_BATCH)}`)
  const mine = g.records.filter((x) => x.batchNo === FARM_BATCH)
  check('B1 同批聚成 1 组', mine.length === 1, `实际 ${mine.length} 组`)
  const one = mine[0]
  check('B2 明细 6 条（聚合不丢单头）', one?.members?.length === 6, `实际 ${one?.members?.length}`)
  check('B3 结论「含不合格」', one?.statusSummary === '含不合格', one?.statusSummary)
  check('B4 计数明细「合格 5 / 不合格 1」', one?.statusDetail === '合格 5 / 不合格 1', one?.statusDetail)
  check('B5 明细里能定位到那头不合格',
    (one?.members || []).some((m) => m.earTagNo === EAR_TAGS[2] && m.status === 2))
  check('B6 明细里宰前/宰后两类都有（折的是批，不是把环节抹平）', (() => {
    const types = new Set((one?.members || []).map((m) => m.inspectType))
    return types.has(1) && types.has(2)
  })(), JSON.stringify([...new Set((one?.members || []).map((m) => m.inspectType))]))
}

// ---------------------------------------------------------------- 4 检疫盖章
console.log('\n▶ 4 检疫盖章：同批 2 枚 = 1 组，结论「全部已盖章」')
{
  for (const [i, tag] of EAR_TAGS.slice(0, 2).entries()) {
    const r = await deviceReport('STAMPER-001', `stamp-${tag}`, {
      earTagNo: tag, batchNo: FARM_BATCH, carcassNo: `CS-FOLD${RUN}-${i + 1}`,
      stampType: '检疫合格章', veterinary: '李兽医',
    })
    if (i === 1) {
      check('C0 盖章已入账', r?.data?.accepted === true, JSON.stringify(r?.data).slice(0, 240))
    }
  }
  const g = await groups('stamps', `&batchNo=${encodeURIComponent(FARM_BATCH)}`)
  const mine = g.records.filter((x) => x.batchNo === FARM_BATCH)
  check('C1 同批聚成 1 组', mine.length === 1, `实际 ${mine.length} 组`)
  check('C2 头数 = 2', mine[0]?.headCount === 2, `实际 ${mine[0]?.headCount}`)
  check('C3 结论「全部已盖章」', mine[0]?.statusSummary === '全部已盖章', mine[0]?.statusSummary)
  check('C4 明细 2 枚章', mine[0]?.members?.length === 2, `实际 ${mine[0]?.members?.length}`)
}

// ---------------------------------------------------------------- 5 瘦肉精追加阳性
console.log('\n▶ 5 瘦肉精追加 1 条阳性：同批 4 条 = 1 组，结论「含阳性」')
{
  const pos = await deviceReport('READER-001', `racto-pos-${EAR_TAGS[2]}`, {
    earTagNo: EAR_TAGS[2], batchNo: FARM_BATCH, sampleNo: `SP-FOLD${RUN}-POS`,
    testTime: nowText(0), testMethod: '胶体金法', testType: '克伦特罗',
    result: 0, operator: '赵检测',
  })
  check('D0 阳性结果已入账（不许当阴性吞掉）', pos?.data?.accepted === true,
    JSON.stringify(pos?.data).slice(0, 240))

  const g = await groups('ractopamine', `&batchNo=${encodeURIComponent(FARM_BATCH)}`)
  const mine = g.records.filter((x) => x.batchNo === FARM_BATCH)
  check('D1 同批仍聚成 1 组（阳性不另起一组）', mine.length === 1, `实际 ${mine.length} 组`)
  const one = mine[0]
  check('D2 头数 = 4（3 阴 + 1 阳）', one?.headCount === 4, `实际 ${one?.headCount}`)
  check('D3 结论「含阳性」', one?.statusSummary === '含阳性', one?.statusSummary)
  check('D4 计数明细「阴性 3 / 阳性 1」', one?.statusDetail === '阴性 3 / 阳性 1', one?.statusDetail)
  check('D5 阳性写在主行结论上（不需要展开才知道）', /阳性/.test(one?.statusSummary || ''))
}

// ---------------------------------------------------------------- 6 未归批
// 注意：entry_inspection.batch_no 是 NOT NULL（设备报文必带批次号），
// 所以「未归批」在库里长成空串/全空格——历史导入、老系统迁移就是这种形态。
// 折叠时必须把空串当「没有批次」，否则同一批空串的猪会被并成一行，凭空造出批次关系。
console.log('\n▶ 6 未归批：没有批次号（空串）的记录按头独立成组，绝不互并')
{
  sql(`USE db_slaughter; UPDATE entry_inspection SET batch_no = '' WHERE ear_tag_no = '${EAR_TAGS[2]}';`)
  const changed = sqlOne(`USE db_slaughter; SELECT COUNT(*) FROM entry_inspection WHERE ear_tag_no = '${EAR_TAGS[2]}' AND batch_no = '';`)
  check('E0 已把第三头改成空批次', String(changed) === '1', `实际 ${changed}`)
  const g = await groups('entries')
  const orphans = g.records.filter((x) => !x.batchNo)
  const merged = orphans.filter((x) => x.headCount > 1)
  check('E1 未归批的组一律 headCount = 1', merged.length === 0,
    merged.length ? `有 ${merged.length} 组被并成了多行：${merged.map((x) => x.headCount).join(',')}` : '')
  check('E2 未归批组带 pigId（可按头定位）', orphans.every((x) => x.pigId !== null && x.pigId !== undefined))
  const own = orphans.find((x) => (x.members || []).some((m) => m.earTagNo === EAR_TAGS[2]))
  check('E3 第三头自成一组建模成功', !!own && own.headCount === 1, `headCount=${own?.headCount}`)
  // 复原，避免影响后面的分页断言
  sql(`USE db_slaughter; UPDATE entry_inspection SET batch_no = '${FARM_BATCH}' WHERE ear_tag_no = '${EAR_TAGS[2]}';`)
}

// ---------------------------------------------------------------- 7 分页按组
console.log('\n▶ 7 分页按「组」算')
{
  const p1 = await api('/slaughter/records/entries?current=1&size=1')
  const p2 = await api('/slaughter/records/entries?current=2&size=1')
  const g1 = p1.data?.records?.[0]
  const g2 = p2.data?.records?.[0]
  check('F1 size=1 第一页只有 1 组', p1.data?.records?.length === 1)
  check('F2 两页是不同的组', !!g1 && !!g2 && g1.groupKey !== g2.groupKey, `${g1?.groupKey} vs ${g2?.groupKey}`)
  check('F3 total 是组数且两页一致', p1.data?.total === p2.data?.total, `${p1.data?.total} vs ${p2.data?.total}`)
}

// ---------------------------------------------------------------- 7 清场
console.log('\n▶ 8 清理本轮自造数据（按耳标前缀 + 批次号，不动演示台账）')
{
  const pigIds = sqlOne(`USE db_breeding; SELECT GROUP_CONCAT(id) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  const pigIdList = pigIds && pigIds !== 'NULL' ? pigIds : null
  if (pigIdList) {
    const entryIds = sqlOne(`USE db_slaughter; SELECT GROUP_CONCAT(id) FROM entry_inspection WHERE pig_id IN (${pigIdList});`)
    const inspIds = sqlOne(`USE db_slaughter; SELECT GROUP_CONCAT(id) FROM slaughter_inspection WHERE pig_id IN (${pigIdList});`)
    const stampIds = sqlOne(`USE db_slaughter; SELECT GROUP_CONCAT(id) FROM carcass_stamp WHERE pig_id IN (${pigIdList});`)
    const ractoIds = sqlOne(`USE db_slaughter; SELECT GROUP_CONCAT(id) FROM ractopamine_test WHERE pig_id IN (${pigIdList});`)
    const vacIds = sqlOne(`USE db_breeding; SELECT GROUP_CONCAT(id) FROM vaccine_record WHERE pig_id IN (${pigIdList});`)

    // 链上按 biz_id 精确清（正常入账事件也上链，留着就是幽灵凭证）
    for (const [type, ids] of [['ENTRY', entryIds], ['INSPECTION', inspIds], ['STAMP', stampIds],
      ['RACTOPAMINE', ractoIds], ['VACCINE', vacIds]]) {
      if (ids && ids !== 'NULL') sql(`USE db_common; DELETE FROM blockchain_record WHERE biz_type = '${type}' AND biz_id IN (${ids});`)
    }

    sql(`USE db_slaughter; DELETE FROM entry_inspection WHERE pig_id IN (${pigIdList});`)
    sql(`USE db_slaughter; DELETE FROM slaughter_inspection WHERE pig_id IN (${pigIdList});`)
    sql(`USE db_slaughter; DELETE FROM carcass_stamp WHERE pig_id IN (${pigIdList});`)
    sql(`USE db_slaughter; DELETE FROM ractopamine_test WHERE pig_id IN (${pigIdList});`)
    sql(`USE db_breeding; DELETE FROM quarantine_certificate WHERE pig_id IN (${pigIdList});`)
    sql(`USE db_breeding; DELETE FROM vaccine_record WHERE pig_id IN (${pigIdList});`)
    sql(`USE db_breeding; DELETE FROM slaughter_apply WHERE pig_id IN (${pigIdList});`)
    sql(`USE db_breeding; DELETE FROM pig_individual WHERE id IN (${pigIdList});`)
  }
  sql(`USE db_breeding; DELETE FROM pig_batch WHERE batch_no = '${BATCH_NO}';`)
  sql(`USE db_breeding; DELETE FROM farm WHERE farm_name = '${FARM_NAME}';`)
  if (stagingIds.length) {
    sql(`USE db_ingest; DELETE FROM ingest_staging WHERE id IN (${stagingIds.join(',')});`)
  }

  const leftPigs = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  const leftEntries = sqlOne(`USE db_slaughter; SELECT COUNT(*) FROM entry_inspection WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  const leftStaging = stagingIds.length
    ? sqlOne(`USE db_ingest; SELECT COUNT(*) FROM ingest_staging WHERE id IN (${stagingIds.join(',')});`)
    : '0'
  check('G1 成员行已清零', String(leftPigs) === '0', `剩余 ${leftPigs}`)
  check('G2 入场查验已清零', String(leftEntries) === '0', `剩余 ${leftEntries}`)
  check('G3 上报流水已清零', String(leftStaging) === '0', `剩余 ${leftStaging}`)
}

console.log(`\n── 结果：${pass} 通过 / ${fail} 失败 ──`)
if (fail > 0) {
  console.log('失败项：' + failures.join('、'))
  process.exit(1)
}
