/**
 * 产地检疫「扫码登记」通道回归（设备通道第一批）：
 *   1. 来源策略：产地检疫已挂上设备通道（channel=QUARANTINE）
 *   2. 扫码登记 → 自动入账，落到产地检疫证表，来源标记为设备采集
 *   3. 同一张证重发 → 幂等返回既有记录，不新建
 *   4. 同一头猪再登记一张新证 → 转「待人工确认」（换发须先作废原证）
 *   5. 耳标不在生猪档案 → 转「待人工确认」（不丢数据、不误入账）
 *   6. 缺官方兽医 → 转「待人工确认」（扫码终端是工具，不得代签签发人）
 *   7. 编造扫描件 fileId → 直接拒绝（假凭证不得入库）
 *
 * 运行：node logs/verify-quarantine-device.mjs
 * 脚本自身可重复执行：登记成功的证在末尾会被作废，那头猪回到「未出证」。
 * 产生的接入流水 ID 会打印出来，由 bash 侧清理（本环境 node 不能调子进程）。
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const DEVICE_NO = process.env.QUARANTINE_DEVICE || 'QSCAN-001'

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
  const json = await res.json().catch(() => null)
  return json
}

/** 代设备发一次上报（走完整链路：鉴权/校验/幂等/派发，不绕过任何一步） */
function report(data, extra = {}) {
  return api('/ingest/demo/report', {
    method: 'POST',
    token,
    body: { deviceNo: DEVICE_NO, data, ...extra },
  })
}

const pad = (n) => String(n).padStart(2, '0')
function nowText() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function daysLater(days) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const login = await api('/auth/login', {
  method: 'POST',
  body: { username: 'admin', password: '123456' },
})
let token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const stagingIds = []
const track = (r) => {
  const id = r?.data?.stagingId
  if (id) stagingIds.push(id)
  return r
}

console.log('\n== 1) 来源策略：产地检疫是否已挂设备通道 ==')
{
  const res = await api('/ingest/source-policy', { token })
  const row = (res?.data || []).find((p) => p.code === 'QUARANTINE')
  check('策略表里有「产地检疫出证」', !!row, JSON.stringify(res).slice(0, 160))
  check('已挂设备通道 QUARANTINE', row?.channel === 'QUARANTINE', `channel=${row?.channel}`)
  check('策略仍是「设备优先」（人工出证保留但须兜底解锁）', row?.policy === 'DEVICE_PREFERRED', `policy=${row?.policy}`)
}

console.log('\n== 2) 找一头尚未出证的在养猪 ==')
const pig = await (async () => {
  const res = await api('/breeding/pigs?current=1&size=10&status=1', { token })
  const pigs = res?.data?.records || []
  for (const p of pigs) {
    const cert = await api(`/breeding/pigs/${p.id}/quarantine-cert`, { token })
    if (!cert?.data) return p
  }
  return null
})()
check('取到「尚未出证」的在养猪', !!pig?.id, pig ? `耳标 ${pig.earTagNo}` : '没有可用的猪')
if (!pig?.id) process.exit(1)

const certNo = `QC-VERIFY-${Date.now()}`
const goodCert = {
  earTagNo: pig.earTagNo,
  certNo,
  issueOrg: '回归验证签发机构',
  issueTime: nowText(),
  validUntil: daysLater(180),
  inspector: '王官方兽医',
}

console.log('\n== 3) 扫码登记（带证面信息，无扫描件）==')
{
  const r = track(await report(goodCert))
  check('自动入账', r?.data?.accepted === true, JSON.stringify(r?.data).slice(0, 200))
  check('写入产地检疫证表', r?.data?.targetTable === 'quarantine_certificate', `targetTable=${r?.data?.targetTable}`)
  check('回执带业务记录编号', !!r?.data?.targetId, `targetId=${r?.data?.targetId}`)
  console.log(`  [INFO] 登记记录编号 = ${r?.data?.targetId}，证号 ${certNo}，耳标 ${pig.earTagNo}`)
}

console.log('\n== 4) 同一张证重发（幂等）==')
{
  const r = track(await report(goodCert))
  check('判为已登记、返回既有记录', r?.data?.duplicate === true || r?.data?.accepted === true,
    JSON.stringify(r?.data).slice(0, 200))
}

console.log('\n== 5) 同一头猪再登记一张新证（换发场景）==')
{
  const r = track(await report({ ...goodCert, certNo: `${certNo}-NEW` }))
  check('转「待人工确认」而非自动入账', r?.data?.status === 1, `status=${r?.data?.status} ${r?.data?.message || ''}`)
}

console.log('\n== 6) 耳标不在生猪档案 ==')
{
  const r = track(await report({ ...goodCert, earTagNo: 'NO-SUCH-TAG-VERIFY', certNo: `${certNo}-T2` }))
  check('转「待人工确认」', r?.data?.status === 1, `status=${r?.data?.status} ${r?.data?.message || ''}`)
}

console.log('\n== 7) 缺官方兽医 ==')
{
  const { inspector, ...noVet } = goodCert
  const r = track(await report({ ...noVet, certNo: `${certNo}-T3` }))
  check('转「待人工确认」（扫码终端不得代签签发人）', r?.data?.status === 1,
    `status=${r?.data?.status} ${r?.data?.message || ''}`)
}

console.log('\n== 8) 编造扫描件 fileId ==')
{
  const r = track(await report({ ...goodCert, certNo: `${certNo}-T4`, fileIds: ['fake-scan-file-id-xxxx'] }))
  check('直接拒绝（假凭证不得入库）', r?.data?.status === 3, `status=${r?.data?.status} ${r?.data?.message || ''}`)
}

console.log('\n== 9) 清理：作废本次登记的测试检疫证 ==')
{
  const cert = await api(`/breeding/pigs/${pig.id}/quarantine-cert`, { token })
  const certId = cert?.data?.id
  check('取回刚登记的证（用于作废）', !!certId, JSON.stringify(cert).slice(0, 160))
  if (certId) {
    const voided = await api(`/breeding/pigs/${pig.id}/quarantine-cert/${certId}/void`, {
      method: 'POST',
      token,
      body: { reason: '测试数据清理：扫码登记通道回归脚本产生' },
    })
    check('测试检疫证已作废（该猪回到「未出证」）', voided?.code === 200 || voided?.success === true,
      JSON.stringify(voided).slice(0, 200))
  }
}

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
console.log(`本次产生的接入流水 ID：${stagingIds.join(',') || '无'}`)
process.exit(fail === 0 ? 0 : 1)
