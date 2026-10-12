/**
 * 入场查验「出栏审批 + 生猪状态」前置回归：
 *   1. 该猪没有出栏申报 → 闸口上报直接拒绝（没有出栏申报记录）
 *   2. 出栏申报处于「待审」 → 拒绝（仍在等待审批）——审批没过不能进厂
 *   3. 缺查验员 → 转「待人工确认」（现场责任必须落到人，设备给不出）
 *   4. 人工确认（force 重放 + 补齐查验员）→ 仍被拒绝，理由仍是「未经出栏审批通过不得入场查验」
 *      ★ 要点：这是业务侧硬校验，人工放行也绕不过去
 *   5. 审批通过后 → 闸口上报自动入账，落到入场查验表
 *   6. 出栏申报「已驳回」的猪 → 拒绝（已被驳回）
 *   7. ★ 出栏审批已通过、但猪已是「已屠宰」终态 → 拒绝（一头猪只能进厂一次）
 *   8. 来源策略表里「入场查验」的说明已带上「出栏审批已通过为前提」与「已屠宰/异常不得再入场」
 *
 * 运行：node logs/verify-entry-gate.mjs
 * 脚本用一头「临时猪」走完 建档 → 产地检疫出证 → 出栏称重登记 → 入场查验 全链路，
 * 产生的业务记录 / 接入流水 ID 打印在末尾，由 bash 侧清理。
 * （本环境 node 禁止子进程调用，DB 核验与清理必须走 bash。）
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const TAG_DEVICE = 'TAG-W001'
const CERT_DEVICE = 'QSCAN-001'
const EXIT_DEVICE = 'EXITSCALE-001'
const GATE_DEVICE = 'GATE-001'
/** 已存在的、出栏申报被驳回的猪（只读用例，不改数据） */
const REJECTED_EAR_TAG = process.env.REJECTED_EAR_TAG || 'ET-TEST-0007'
const REJECTED_CERT = process.env.REJECTED_CERT || 'QC-TEST-0007'
/** 已存在的、出栏审批已通过但状态已是「已屠宰」的猪（只读用例，不改数据） */
const SLAUGHTERED_EAR_TAG = process.env.SLAUGHTERED_EAR_TAG || 'ET-TEST-0015'
const SLAUGHTERED_CERT = process.env.SLAUGHTERED_CERT || 'QC-TEST-0015'

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

const login = await api('/auth/login', {
  method: 'POST',
  body: { username: 'admin', password: '123456' },
})
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

/** 代设备发一次上报（走完整链路：鉴权/校验/幂等/派发） */
const report = (deviceNo, data) =>
  api('/ingest/demo/report', { method: 'POST', token, body: { deviceNo, data } })

const stagingIds = []
const track = (r) => {
  const id = r?.data?.stagingId
  if (id) stagingIds.push(id)
  return r
}

const stamp = Date.now()
const earTagNo = `ET-GATE-${stamp}`
const certNo = `QC-GATE-${stamp}`
let pigId = null
let applyId = null
let entryId = null
let manualStagingId = null

console.log('\n== 1) 造一头临时猪（建档 + 产地检疫出证）==')
{
  const farms = await api('/breeding/farms?current=1&size=1', { token })
  const farmName = farms?.data?.records?.[0]?.farmName
  check('取到一个已登记养殖场', !!farmName, `farmName=${farmName}`)

  const tag = track(await report(TAG_DEVICE, {
    earTagNo,
    farmName,
    birthDate: dateText(-90),
    breed: '三元杂',
    gender: 1,
    penNo: '回归验证舍',
    origin: '自繁',
  }))
  check('建档入账', tag?.data?.accepted === true, JSON.stringify(tag?.data).slice(0, 200))
  pigId = tag?.data?.targetId
  check('拿到生猪肉档编号', !!pigId, `pigId=${pigId}`)

  const cert = track(await report(CERT_DEVICE, {
    earTagNo,
    certNo,
    issueOrg: '回归验证动物卫生监督所',
    issueTime: nowText(),
    validUntil: dateText(365),
    inspector: '王官方兽医',
  }))
  check('产地检疫出证入账', cert?.data?.accepted === true, JSON.stringify(cert?.data).slice(0, 200))
}

console.log('\n== 2) 还没有出栏申报就拉到闸口 → 必须拒绝 ==')
{
  const r = track(await report(GATE_DEVICE, {
    earTagNo,
    batchNo: `B-GATE-${stamp}-A`,
    quarantineCert: certNo,
    weight: 116.5,
    inspector: '张查验',
  }))
  check('拒绝入厂', r?.data?.status === 3, `status=${r?.data?.status} ${r?.data?.message || ''}`)
  check('原因是「没有出栏申报记录」', String(r?.data?.message || '').includes('没有出栏申报记录'),
    `message=${r?.data?.message}`)
}

console.log('\n== 3) 出栏称重登记（产生一条「待审」申报）==')
{
  const r = track(await report(EXIT_DEVICE, {
    earTagNo,
    weightKg: 116.8,
    weighTime: nowText(),
    targetSlaughterhouse: '回归验证屠宰场',
  }))
  check('称重登记入账', r?.data?.accepted === true, JSON.stringify(r?.data).slice(0, 220))
  applyId = r?.data?.targetId
  check('拿到出栏申报编号', !!applyId, `applyId=${applyId}`)
}

console.log('\n== 4) 申报还在「待审」就拉到闸口 → 必须拒绝 ==')
{
  const r = track(await report(GATE_DEVICE, {
    earTagNo,
    batchNo: `B-GATE-${stamp}-B`,
    quarantineCert: certNo,
    weight: 116.5,
    inspector: '张查验',
  }))
  check('拒绝入厂', r?.data?.status === 3, `status=${r?.data?.status} ${r?.data?.message || ''}`)
  check('原因是「仍在等待审批」', String(r?.data?.message || '').includes('仍在等待审批'),
    `message=${r?.data?.message}`)
}

console.log('\n== 5) 缺查验员 → 转「待人工确认」（设备给不出人名）==')
{
  const r = track(await report(GATE_DEVICE, {
    earTagNo,
    batchNo: `B-GATE-${stamp}-C`,
    quarantineCert: certNo,
    weight: 116.5,
  }))
  check('转待人工确认', r?.data?.status === 1, `status=${r?.data?.status} ${r?.data?.message || ''}`)
  manualStagingId = r?.data?.stagingId
}

console.log('\n== 6) ★ 人工确认（force 重放 + 补齐查验员）→ 仍然进不去 ==')
{
  const r = await api(`/ingest/staging/${manualStagingId}/approve`, {
    method: 'POST',
    token,
    body: {
      remark: '回归验证：试试人工放行能不能绕过出栏审批',
      payload: { inspector: '张查验' },
    },
  })
  check('未入账（人工确认也绕不过出栏审批）', r?.data?.accepted !== true,
    `accepted=${r?.data?.accepted} status=${r?.data?.status}`)
  check('仍停在「待人工处理」并写明原因', r?.data?.status === 1
    && String(r?.data?.message || '').includes('未经出栏审批通过不得入场查验'),
    `status=${r?.data?.status} message=${r?.data?.message}`)
}

console.log('\n== 7) 审批通过出栏申报（实名） ==')
{
  const r = await api(`/breeding/applies/${applyId}/approve`, {
    method: 'PUT',
    token,
    body: { approved: true, comment: '回归验证：同意出栏' },
  })
  check('审批通过', r?.code === 200 || r?.success === true, JSON.stringify(r).slice(0, 200))
}

console.log('\n== 8) 审批通过后拉到闸口 → 自动入账 ==')
{
  const r = track(await report(GATE_DEVICE, {
    earTagNo,
    batchNo: `B-GATE-${stamp}-D`,
    quarantineCert: certNo,
    weight: 116.5,
    inspector: '张查验',
  }))
  check('自动入账', r?.data?.accepted === true, JSON.stringify(r?.data).slice(0, 220))
  check('写入入场查验表', r?.data?.targetTable === 'entry_inspection', `targetTable=${r?.data?.targetTable}`)
  entryId = r?.data?.targetId
  check('回执带业务记录编号', !!entryId, `targetId=${entryId}`)
}

console.log('\n== 9) 出栏申报已「驳回」的猪 → 拒绝 ==')
{
  const r = track(await report(GATE_DEVICE, {
    earTagNo: REJECTED_EAR_TAG,
    batchNo: `B-GATE-${stamp}-E`,
    quarantineCert: REJECTED_CERT,
    weight: 110,
    inspector: '张查验',
  }))
  check('拒绝入厂', r?.data?.status === 3, `status=${r?.data?.status} ${r?.data?.message || ''}`)
  check('原因是「已被驳回」', String(r?.data?.message || '').includes('已被驳回'),
    `message=${r?.data?.message}`)
}

console.log('\n== 10) ★ 出栏审批已通过、但猪已是「已屠宰」终态 → 拒绝（一头猪只能进厂一次）==')
{
  const r = track(await report(GATE_DEVICE, {
    earTagNo: SLAUGHTERED_EAR_TAG,
    batchNo: `B-GATE-${stamp}-F`,
    quarantineCert: SLAUGHTERED_CERT,
    weight: 105,
    inspector: '张查验',
  }))
  check('拒绝入厂', r?.data?.status === 3, `status=${r?.data?.status} ${r?.data?.message || ''}`)
  check('原因是「已不能再入场查验」', String(r?.data?.message || '').includes('已不能再入场查验'),
    `message=${r?.data?.message}`)
  check('回执点明了当前状态是「已屠宰」', String(r?.data?.message || '').includes('已屠宰'),
    `message=${r?.data?.message}`)
}

console.log('\n== 11) 来源策略表里「入场查验」的说明已带上前提 ==')
{
  const res = await api('/ingest/source-policy', { token })
  const row = (res?.data || []).find((p) => p.code === 'ENTRY')
  check('策略表里有「入场查验」', !!row, JSON.stringify(res).slice(0, 200))
  check('策略仍为「设备独占」', row?.policy === 'DEVICE_ONLY', `policy=${row?.policy}`)
  check('说明提到「出栏审批已通过为前提」', String(row?.note || '').includes('出栏审批已通过为前提'),
    `note=${row?.note}`)
  check('说明提到「已屠宰或异常的猪不得再入场」', String(row?.note || '').includes('已屠宰或异常的猪不得再入场'),
    `note=${row?.note}`)
}

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
console.log(`清理用 —— 临时猪：#${pigId}（耳标 ${earTagNo}）`)
console.log(`清理用 —— 出栏申报 ID：${applyId}（已审批通过，猪状态被推进为已出栏）`)
console.log(`清理用 —— 入场查验记录 ID：${entryId}`)
console.log(`清理用 —— 检疫证号：${certNo}`)
console.log(`清理用 —— 接入流水 ID：${stagingIds.join(',') || '无'}（其中 ${manualStagingId} 为待人工处理）`)
process.exit(fail === 0 ? 0 : 1)
