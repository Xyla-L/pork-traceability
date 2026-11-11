/**
 * 机构归属埋点验收（屠宰侧）：
 *   一次性主体走完 建档 → 免疫 → 出证 → 出栏称重 → 审批通过 → 闸口上报入账，
 *   入场查验记录应带上设备所属机构 org_id=3（GATE-001 已绑定示范定点屠宰场）。
 * DB 断言与清理由 bash 侧执行（本环境 node 无子进程）。
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const CERT_DEVICE = 'QSCAN-001'
const INJ_DEVICE = 'INJ-001'
const EXIT_DEVICE = 'EXITSCALE-001'
const GATE_DEVICE = 'GATE-001'

let pass = 0, fail = 0
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  [PASS] ${name}`) }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}
async function api(path, { method = 'GET', token, body, headers = {} } = {}) {
  const h = { 'Content-Type': 'application/json', ...headers }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined })
  return res.json().catch(() => null)
}
const pad = (n) => String(n).padStart(2, '0')
const nowText = (off = 0) => { const d = new Date(Date.now() + off * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}` }
const dateText = (days) => { const d = new Date(Date.now() + days * 86400000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }
async function uploadVoucher() {
  const buffer = Buffer.from(
    '89504e470d0a1a0a0000000d494844520000000100000001080200000090775'
    + '3de0000000c4944415408d763f8cfc000000301010018dd8db00000000049454e44ae426082', 'hex')
  const fd = new FormData()
  fd.append('file', new Blob([buffer], { type: 'image/png' }), `org-${stamp}.png`)
  const res = await fetch(`${BASE}/file/upload`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd })
  return (await res.json().catch(() => null))?.data?.fileId
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const report = (deviceNo, data) => api('/ingest/demo/report', { method: 'POST', token, body: { deviceNo, data } })
const stagingIds = []
const track = (r) => { const id = r?.data?.stagingId; if (id) stagingIds.push(id); return r }

const stamp = Date.now()
const earTagNo = `ET-ORG-${stamp}`
const certNo = `QC-ORG-${stamp}`
let pigId = null, applyId = null, entryId = null

console.log('\n== 1) 一次性主体：建档 + 免疫 + 出证 ==')
{
  const farms = await api('/breeding/farms?current=1&size=1', { token })
  const farm = farms?.data?.records?.[0]
  check('取到养殖场', !!farm?.id)
  const created = await api('/breeding/pigs/batches', {
    method: 'POST', token,
    body: { batchNo: `PB-ORG-${stamp}`, farmId: farm?.id, earTagStart: earTagNo, earTagEnd: earTagNo,
      breed: '三元杂', entryDate: dateText(-90), source: '自繁' },
  })
  check('批次登记成功', !!created?.data, JSON.stringify(created).slice(0, 150))
  const pigPage = await api(`/breeding/pigs?earTagNo=${encodeURIComponent(earTagNo)}&current=1&size=1`, { token })
  pigId = pigPage?.data?.records?.[0]?.id
  check('批次成员行生成', !!pigId, `pigId=${pigId}`)

  const voucherId = await uploadVoucher()
  check('凭证上传', !!voucherId)
  const vac = track(await report(INJ_DEVICE, {
    earTagNo, vaccineName: `猪瘟疫苗${stamp}`, vaccineBatchNo: `VB-ORG-${stamp}`,
    injectTime: nowText(), operator: '张兽医', fileIds: [voucherId],
  }))
  check('免疫入账', vac?.data?.accepted === true, JSON.stringify(vac?.data).slice(0, 150))
  const cert = track(await report(CERT_DEVICE, {
    earTagNo, certNo, issueOrg: '回归验证动物卫生监督所', issueTime: nowText(),
    validUntil: dateText(365), inspector: '王官方兽医',
  }))
  check('出证入账', cert?.data?.accepted === true, JSON.stringify(cert?.data).slice(0, 150))
}

console.log('\n== 2) 出栏称重 + 审批通过 ==')
{
  const r = track(await report(EXIT_DEVICE, {
    earTagNo, weightKg: 118.2, weighTime: nowText(), targetSlaughterhouse: '回归验证屠宰场',
  }))
  check('称重登记入账', r?.data?.accepted === true, JSON.stringify(r?.data).slice(0, 150))
  applyId = r?.data?.targetId
  const ap = await api(`/breeding/applies/${applyId}/approve`, {
    method: 'PUT', token, body: { approved: true, comment: '归属埋点回归' },
  })
  check('审批通过', ap?.code === 200 || ap?.success === true, JSON.stringify(ap).slice(0, 150))
}

console.log('\n== 3) 闸口上报自动入账 ==')
{
  const r = track(await report(GATE_DEVICE, {
    earTagNo, batchNo: `B-ORG-${stamp}`, quarantineCert: certNo,
    weight: 116.5, inspector: '张查验',
  }))
  check('自动入账', r?.data?.accepted === true, JSON.stringify(r?.data).slice(0, 200))
  check('写入入场查验表', r?.data?.targetTable === 'entry_inspection', `targetTable=${r?.data?.targetTable}`)
  entryId = r?.data?.targetId
}

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
console.log(`清理用 —— 入场查验 ID：${entryId}`)
console.log(`清理用 —— 猪批次 PB-ORG-${stamp} / 猪 #${pigId}（${earTagNo}）/ 申报 #${applyId} / 证 ${certNo}`)
console.log(`清理用 —— 接入流水 ID：${stagingIds.join(',') || '无'}`)
process.exit(fail === 0 ? 0 : 1)
