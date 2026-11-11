/**
 * 业务逻辑闸门回归：设备/人工写入前，那些「不成立就不能入账」的判定。
 *
 * 本轮补的闸门（此前都能静默写进链）：
 *   1 产地检疫出证：该猪没有任何有效免疫记录        → 拒绝（法定前置：查验强制免疫档案）
 *   2 产地检疫出证：免疫记录全部晚于出证时间        → 拒绝（先出证后免疫，时间顺序不成立）
 *   3 产地检疫出证：有效期早于签发时间              → 拒绝（证面数据自相矛盾）
 *   4 产地检疫出证：签发时间跑到未来                → 拒绝（终端时钟错误/报文串号）
 *   5 产地检疫出证：该猪已屠宰                      → 拒绝（产地检疫是调运前置，猪已不在场）
 *   6 产地检疫出证：该猪已出栏                      → 转人工（已不在场，须核对纸质证与调运记录）
 *   7 免疫注射：该猪已屠宰/已出栏                   → 拒绝（不在养的猪不能接种）
 *   8 免疫注射：注射时间跑到未来                    → 拒绝（会把整条免疫档案的时间轴带歪）
 *
 * 同时验证「正路仍然通」：正常免疫 → 正常出证 → 入账，避免闸门写成堵死。
 *
 * 自持：自己建场、自己登记单头批次、自己上传凭证、自己清理；不借演示台账。
 * 运行：node logs/verify-logic-gates.mjs
 */
import { execSync } from 'node:child_process'

const BASE = process.env.BASE_URL || 'http://localhost/api/v1'
const ADMIN = { username: 'admin', password: '123456' }
const RUN = process.env.RUN || String(Date.now())
const FARM_NAME = `逻辑闸门场${RUN}`
const BATCH_NO = `PB-LG${RUN}`
const EAR_TAG = `ET-LG${RUN}`          // 单头批：起=止，豁免「纯数字尾号」要求
const INJ_KEY = 'dev-key-inj-001'
const QSCAN_KEY = 'dev-key-qscan-001'
const MYSQL = 'pork-traceability-mysql-1'

let token = null
let pass = 0
let fail = 0

function check(name, ok, detail = '') {
  if (ok) { pass++; console.log(`  [PASS] ${name}`) }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await res.json() } catch { /* 空响应体 */ }
  return { status: res.status, body, data: body?.data, code: body?.code, message: body?.msg || body?.message }
}

async function deviceReport(deviceKey, payload) {
  const res = await fetch(`${BASE}/ingest/device/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Device-Key': deviceKey },
    body: JSON.stringify(payload),
  })
  let body = null
  try { body = await res.json() } catch { /* ignore */ }
  return { data: body?.data, message: body?.msg || body?.message || body?.data?.message }
}

function sql(statement) {
  const escaped = statement.replace(/"/g, '\\"')
  const cmd = `docker exec ${MYSQL} mysql -u${process.env.DB_USER || 'pork_user'} -p${process.env.DB_PASS || '123456'} --default-character-set=utf8mb4 -N -e "${escaped}" 2>&1 | grep -v Warning || true`
  return execSync(cmd, { encoding: 'utf8' }).trim()
}

const pad = (n) => String(n).padStart(2, '0')
function dayText(offsetDays = 0) {
  const d = new Date(Date.now() + offsetDays * 86400000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
const DAY = dayText(0)
const YESTERDAY = dayText(-1)

function pngFile() {
  return {
    name: `lg-${RUN}.png`,
    buffer: Buffer.from(
      '89504e470d0a1a0a0000000d494844520000000100000001080200000090775'
      + '3de0000000c4944415408d763f8cfc000000301010018dd8db00000000049454e44ae426082',
      'hex',
    ),
  }
}

async function uploadVoucher() {
  const fd = new FormData()
  const f = pngFile()
  fd.append('file', new Blob([f.buffer], { type: 'image/png' }), f.name)
  const res = await fetch(`${BASE}/file/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: fd,
  })
  const body = await res.json().catch(() => null)
  return body?.data?.fileId
}

/** 人工出证：返回 {code, message} */
function issueCert(pigId, certNo, issueTime, validUntil) {
  return api(`/breeding/pigs/${pigId}/quarantine-cert`, {
    method: 'POST',
    body: JSON.stringify({
      pigId, certNo, issueTime, validUntil,
      issueOrg: '逻辑闸门回归机构',
      inspector: '王官方兽医',
      fileId: voucherId,
      caSignature: `CA-LG-${RUN}`,
    }),
  })
}

/** 设备出证（走完整接入管道：留档 → 判重 → 判定 → 落库） */
function deviceCert(bizKey, issueTime, validUntil, certNo) {
  return deviceReport(QSCAN_KEY, {
    channel: 'QUARANTINE',
    bizKey: `LG${RUN}-${bizKey}`,
    reportTime: `${DAY} 09:00:00`,
    sourceRef: 'QSCAN-001',
    data: {
      earTagNo: EAR_TAG,
      certNo: certNo || `QC-LG${RUN}-${bizKey}`,
      issueOrg: '逻辑闸门回归机构',
      issueTime,
      validUntil,
      inspector: '王官方兽医',
    },
  })
}

function deviceVaccine(bizKey, injectTime, extra = {}) {
  return deviceReport(INJ_KEY, {
    channel: 'VACCINE',
    bizKey: `LG${RUN}-${bizKey}`,
    reportTime: `${DAY} 09:00:00`,
    sourceRef: 'INJ-001',
    data: {
      earTagNo: EAR_TAG,
      vaccineName: `口蹄疫疫苗${RUN}`,
      vaccineBatchNo: `VB-LG${RUN}`,
      manufacturer: '回归生物',
      injectTime,
      dosage: '2ml',
      injectSite: '颈部肌肉',
      operator: '张兽医',
      fileIds: [voucherId],
      ...extra,
    },
  })
}

let voucherId = null
let farmId = null
let batchId = null
let pigId = null

;(async () => {
  console.log(`\n── 业务逻辑闸门回归（RUN=${RUN}）──\n`)

  // ---------------------------------------------------------------- 0 登录
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token
  if (!token) {
    console.error('登录失败：' + JSON.stringify(login.body).slice(0, 200))
    process.exit(1)
  }

  voucherId = await uploadVoucher()
  check('A0 凭证照片上传成功', !!voucherId, '未拿到 fileId')

  // ---------------------------------------------------------------- 1 造场与单头批次
  console.log('\n▶ 1 建场 + 单头批次（起=止）')
  await api('/breeding/farms', {
    method: 'POST',
    body: JSON.stringify({
      farmName: FARM_NAME,
      licenseNo: `LIC-LG${RUN}`,
      scale: 50,
      address: '逻辑闸门回归用',
      contactPerson: '回归',
      contactPhone: '13100000000',
    }),
  })
  const farms = await api('/breeding/farms?current=1&size=500')
  farmId = (farms.data?.records || []).find((f) => f.farmName === FARM_NAME)?.id
  check('A1 建场成功', !!farmId, '按场名回查不到')

  const batch = await api('/breeding/pigs/batches', {
    method: 'POST',
    body: JSON.stringify({
      batchNo: BATCH_NO,
      farmId: Number(farmId),
      breed: '长白',
      entryDate: DAY,
      earTagStart: EAR_TAG,
      earTagEnd: EAR_TAG,
      source: '自繁',
    }),
  })
  check('A2 单头批次登记成功',
    batch.code === 200 || batch.code === 0, JSON.stringify(batch.message).slice(0, 200))

  const pigs = await api(`/breeding/pigs?current=1&size=50&farmId=${farmId}`)
  const pig = (pigs.data?.records || []).find((p) => p.earTagNo === EAR_TAG)
  pigId = pig?.id
  check('A3 批次展开出这一头', !!pigId, `耳标 ${EAR_TAG}`)
  if (!pigId) { cleanup(); process.exit(1) }

  // ---------------------------------------------------------------- 2 无免疫就出证 → 拒
  console.log('\n▶ 2 没有任何免疫记录就出证')
  {
    const r = await issueCert(pigId, `QC-LG${RUN}-NOVAC`, `${DAY} 08:00:00`, dayText(180))
    check('B1 出证被拒绝', r.code !== 200 && r.code !== 0, JSON.stringify(r.data).slice(0, 160))
    check('B2 原因写明「没有任何有效免疫记录」',
      /没有任何有效免疫记录/.test(r.message || ''), r.message)
    const rows = sql(`USE db_breeding; SELECT COUNT(*) FROM quarantine_certificate WHERE pig_id=${pigId} AND void_flag=0;`)
    check('B3 库里没有留下这张证', String(rows) === '0', `实际 ${rows}`)
  }

  // ---------------------------------------------------------------- 3 免疫时间在未来 → 拒
  console.log('\n▶ 3 注射时间跑到未来')
  {
    const r = await deviceVaccine('VAC-FUTURE', `${dayText(365)} 10:00:00`)
    check('C1 判为已拒绝', r.data?.status === 3, `status=${r.data?.status} ${r.data?.message || ''}`)
    check('C2 原因写明「晚于当前时间」', /晚于当前时间/.test(r.data?.message || ''), r.data?.message)
  }

  // ---------------------------------------------------------------- 4 正常免疫 → 入账
  console.log('\n▶ 4 正常免疫（时间早于出证）')
  {
    const r = await deviceVaccine('VAC-OK', `${DAY} 07:00:00`)
    check('D1 免疫入账', r.data?.accepted === true, JSON.stringify(r.data).slice(0, 200))
    const n = sql(`USE db_breeding; SELECT COUNT(*) FROM vaccine_record WHERE pig_id=${pigId} AND void_flag=0;`)
    check('D2 库里有一条有效免疫', String(n) === '1', `实际 ${n}`)
  }

  // ---------------------------------------------------------------- 5 正常出证 → 成功
  console.log('\n▶ 5 有免疫、时间顺序正确 → 出证放行')
  let certId = null
  {
    const r = await issueCert(pigId, `QC-LG${RUN}-OK`, `${DAY} 08:00:00`, dayText(180))
    check('E1 出证成功', r.code === 200 || r.code === 0, JSON.stringify(r.message).slice(0, 200))
    const rows = sql(`USE db_breeding; SELECT id FROM quarantine_certificate WHERE pig_id=${pigId} AND void_flag=0 LIMIT 1;`)
    certId = rows.split('\n')[0].trim()
    check('E2 证已落库', !!certId, `certId=${certId}`)
  }

  // ---------------------------------------------------------------- 6 免疫晚于出证 → 拒
  console.log('\n▶ 6 免疫记录全部晚于出证时间（先出证后免疫）')
  {
    const r = await deviceCert('OUT-OF-ORDER', `${YESTERDAY} 08:00:00`, dayText(180))
    check('F1 判为已拒绝', r.data?.status === 3, `status=${r.data?.status} ${r.data?.message || ''}`)
    check('F2 原因写明「时间顺序不成立」', /时间顺序不成立/.test(r.data?.message || ''), r.data?.message)
  }

  // ---------------------------------------------------------------- 7 有效期早于签发 → 拒
  console.log('\n▶ 7 有效期早于签发时间')
  {
    const r = await deviceCert('BAD-VALID', `${DAY} 08:00:00`, '2020-01-01')
    check('G1 判为已拒绝', r.data?.status === 3, `status=${r.data?.status} ${r.data?.message || ''}`)
    check('G2 原因写明「早于签发时间」', /早于签发时间/.test(r.data?.message || ''), r.data?.message)
  }

  // ---------------------------------------------------------------- 8 签发时间在未来 → 拒
  console.log('\n▶ 8 签发时间跑到未来')
  {
    const r = await deviceCert('FUTURE-ISSUE', `${dayText(30)} 10:00:00`, dayText(400))
    check('H1 判为已拒绝', r.data?.status === 3, `status=${r.data?.status} ${r.data?.message || ''}`)
    check('H2 原因写明「晚于当前时间」', /晚于当前时间/.test(r.data?.message || ''), r.data?.message)
  }

  // ---------------------------------------------------------------- 9 已出栏 → 出证转人工
  console.log('\n▶ 9 猪已出栏（不在场）再出证')
  {
    sql(`USE db_breeding; UPDATE pig_individual SET status=2 WHERE id=${pigId};`)
    const r = await deviceCert('AFTER-EXIT', `${DAY} 08:00:00`, dayText(180))
    check('I1 转「待人工确认」而非直接入账', r.data?.status === 1,
      `status=${r.data?.status} ${r.data?.message || ''}`)
    check('I2 原因写明「已不在养殖场」', /已不在养殖场/.test(r.data?.message || ''), r.data?.message)
  }

  // ---------------------------------------------------------------- 10 已屠宰 → 出证/免疫都拒
  console.log('\n▶ 10 猪已屠宰')
  {
    sql(`USE db_breeding; UPDATE pig_individual SET status=3 WHERE id=${pigId};`)
    const c = await deviceCert('AFTER-SLAUGHTER', `${DAY} 08:00:00`, dayText(180))
    check('J1 出证判为已拒绝', c.data?.status === 3, `status=${c.data?.status} ${c.data?.message || ''}`)
    check('J2 原因写明「已屠宰」', /已屠宰/.test(c.data?.message || ''), c.data?.message)
    const v = await deviceVaccine('VAC-AFTER-SLAUGHTER', `${DAY} 07:30:00`)
    check('J3 免疫判为已拒绝', v.data?.status === 3, `status=${v.data?.status} ${v.data?.message || ''}`)
    check('J4 免疫拒绝原因带状态中文名「已屠宰」', /「已屠宰」/.test(v.data?.message || ''), v.data?.message)
  }

  // ---------------------------------------------------------------- 11 清理
  console.log('\n▶ 11 清理本轮自造数据')
  cleanup()
  const left = sql(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE ear_tag_no='${EAR_TAG}';`)
  check('K1 成员行已清空', String(left) === '0', `实际 ${left}`)
  certId = null

  console.log(`\n── 结果：${pass} 通过 / ${fail} 失败 ──\n`)
  process.exit(fail === 0 ? 0 : 1)
})()

function cleanup() {
  if (!EAR_TAG) return
  const key = `%LG${RUN}%`
  sql(`USE db_breeding;
    DELETE v FROM vaccine_record v JOIN pig_individual i ON i.id=v.pig_id WHERE i.ear_tag_no='${EAR_TAG}';
    DELETE c FROM quarantine_certificate c JOIN pig_individual i ON i.id=c.pig_id WHERE i.ear_tag_no='${EAR_TAG}';
    DELETE a FROM slaughter_apply a JOIN pig_individual i ON i.id=a.pig_id WHERE i.ear_tag_no='${EAR_TAG}';`)
  sql(`USE db_breeding; DELETE FROM pig_individual WHERE ear_tag_no='${EAR_TAG}';`)
  sql(`USE db_breeding; DELETE FROM pig_batch WHERE batch_no='${BATCH_NO}';`)
  sql(`USE db_breeding; DELETE FROM farm WHERE farm_name='${FARM_NAME}';`)
  sql(`USE db_ingest; DELETE FROM ingest_staging WHERE biz_key LIKE '${key}';`)
  sql(`USE db_common; DELETE FROM blockchain_record WHERE biz_key LIKE '${key}';`)
  console.log('  已按耳标/批次号/运单键清干净（只删本轮自造的）')
}
