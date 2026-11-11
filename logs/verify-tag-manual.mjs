/**
 * TAG 通道 × 批次档案 回归（2026-10-25）：
 * 电子耳标芯片里只有一个编号，读写器物理上只能提供「耳标号」。
 * 批次信息（养殖场、批次号、品种、进场日期、来源）是人的知识 —— 由人工按批次登记；
 * 耳标区间内的每头猪登记时自动展开成成员行，设备读到的耳标直接命中，不再逐头建档。
 *
 * 断言分组：
 *   A. 设备只报耳标号（区间外新号）→ 转待人工，提示先登记批次
 *   B. 报文混入人工字段（farmName/breed/gender…）→ 后端不认，仍转待人工
 *   C. 人工登记批次（POST /pigs/batches，区间覆盖该耳标）→ 成员行自动生成
 *   D. 同耳标再设备上报 → 幂等入账，返回既有成员档案
 *   E. 重复批次号再登记 → 被拒（同场批次号唯一）
 *
 * 收尾：打印「清理用」行（staging ids / batchId / pigId），由外层（本会话）负责物理清理——
 * 本脚本自身不改库，重复运行安全（每次用新时间戳耳标号/批次号）。
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const TAG_DEVICE = 'TAG-W001'

let pass = 0
let fail = 0
function check(name, ok, detail = '') {
  if (ok) { pass++; console.log(`  [PASS] ${name}`) }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

async function api(path, { method = 'GET', token, body } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  let json = null
  try { json = await res.json() } catch { /* 204 等 */ }
  return json
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const report = (data) =>
  api('/ingest/demo/report', { method: 'POST', token, body: { deviceNo: TAG_DEVICE, data } })

const stamp = Date.now()
// 耳标尾号必须是数字才能按区间圈定成员：ET-TM-<stamp> ~ ET-TM-<stamp+2> 共 3 头
const earTagNo = `ET-TM-${stamp}`
const tagEnd = `ET-TM-${stamp + 2}`
const batchNo = `PB-TM-${stamp}`
const stagingIds = []

const dateText = (offset) => {
  const d = new Date(Date.now() + offset * 86400000)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const nowText = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/**
 * 读取时刻按「秒」精度不重复：同一头猪再次经过读写器是常态（不是重放），
 * 但兜底判重比较的是「同设备 + 同通道 + 报文 JSON 语义相等」——
 * 如果两次上报落在同一秒，报文就完全一样，会被判成重复而上不了账（本脚本 D 段就踩过）。
 * 所以每次取时间时向前挪 1 秒，保证同一轮里的每份报文都是新的。
 */
let tickSeq = 0
const tickText = () => {
  const d = new Date(Date.now() - (tickSeq++) * 1000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

console.log('\n== A) 设备只报耳标号（区间外新号）→ 转待人工 ==')
{
  const r = await report({ earTagNo, readTime: tickText() })
  stagingIds.push(r?.data?.stagingId)
  check('上报被受理为流水', !!r?.data?.stagingId, JSON.stringify(r?.data).slice(0, 160))
  check('结论 = 待人工(1)', r?.data?.status === 1, `status=${r?.data?.status}`)
  check('提示登记批次', String(r?.data?.message || '').includes('批次'), r?.data?.message)
}

console.log('\n== B) 报文混入人工字段 → 后端不认，仍转待人工 ==')
{
  const r = await report({ earTagNo: `ET-TM-${stamp}-b`, readTime: tickText(), farmName: '不存在也不认的场', batchNo: 'PB-假批次', breed: '三元杂', gender: 1, birthDate: dateText(-90), penNo: 'X', origin: '自繁' })
  stagingIds.push(r?.data?.stagingId)
  check('结论 = 待人工(1)，没有按报文字段建档', r?.data?.status === 1, `status=${r?.data?.status} ${r?.data?.message || ''}`)
  const dup = await api(`/breeding/pigs?earTagNo=${encodeURIComponent(`ET-TM-${stamp}-b`)}`, { token })
  check('库里没有按报文建出档案', (dup?.data?.records?.length || dup?.data?.total || 0) === 0,
    JSON.stringify(dup?.data).slice(0, 120))
}

console.log('\n== C) 人工登记批次 → 区间成员行自动生成 ==')
let batchId = null
let pigId = null
{
  const farms = await api('/breeding/farms?current=1&size=1', { token })
  const farmId = farms?.data?.records?.[0]?.id
  check('取到已登记养殖场', !!farmId)
  const created = await api('/breeding/pigs/batches', {
    method: 'POST', token,
    body: { batchNo, farmId, earTagStart: earTagNo, earTagEnd: tagEnd, breed: '杜洛克', entryDate: dateText(-30), source: '外购-边界验证供应商' },
  })
  check('批次登记成功', !!created?.data, JSON.stringify(created).slice(0, 160))
  batchId = created?.data
  const detail = await api(`/breeding/pigs/batches/${batchId}`, { token })
  check('批次信息按人工录入落库',
    detail?.data?.batchNo === batchNo && detail?.data?.breed === '杜洛克'
      && detail?.data?.quantity === 3 && detail?.data?.source === '外购-边界验证供应商',
    JSON.stringify(detail?.data).slice(0, 240))
  check('在养成员 3 头', detail?.data?.onFarmCount === 3, JSON.stringify(detail?.data).slice(0, 240))
  const page = await api(`/breeding/pigs?earTagNo=${encodeURIComponent(earTagNo)}&current=1&size=1`, { token })
  pigId = page?.data?.records?.[0]?.id
  check('耳标成员行已生成', !!pigId, `pigId=${pigId}`)
  check('成员档案带批次号与批次品种', page?.data?.records?.[0]?.batchNo === batchNo
    && page?.data?.records?.[0]?.breed === '杜洛克', JSON.stringify(page?.data?.records?.[0]).slice(0, 200))
}

console.log('\n== D) 同耳标再设备上报 → 幂等入账 ==')
{
  // readTime 每次都取当前：同一头猪再次经过读写器是常态（不是重放），
  // 报文里带读取时刻，兜底判重就不会把两次真实读取误判成重复上报
  const r = await report({ earTagNo, readTime: tickText() })
  stagingIds.push(r?.data?.stagingId)
  check('结论 = 已入账(2)（幂等返回既有档案）', r?.data?.status === 2, `status=${r?.data?.status} ${r?.data?.message || ''}`)
  check('targetId 就是批次成员行', String(r?.data?.targetId) === String(pigId), `targetId=${r?.data?.targetId} pigId=${pigId}`)
}

console.log('\n== E) 重复批次号再登记 → 被拒 ==')
{
  const farms = await api('/breeding/farms?current=1&size=1', { token })
  const farmId = farms?.data?.records?.[0]?.id
  const again = await api('/breeding/pigs/batches', {
    method: 'POST', token,
    body: { batchNo, farmId, earTagStart: `ET-TM-${stamp + 10}`, earTagEnd: `ET-TM-${stamp + 10}` },
  })
  check('登记被拒（同场批次号已存在）', !again?.data && String(again?.message || '').includes('批次号已存在'),
    JSON.stringify(again).slice(0, 160))
}

console.log(`\n=== 结果：通过 ${pass} / 失败 ${fail} ===`)
console.log(`清理用 stagingIds=${stagingIds.filter(Boolean).join(',')} batchId=${batchId || ''} pigId=${pigId || ''}`)
process.exit(fail ? 1 : 0)
