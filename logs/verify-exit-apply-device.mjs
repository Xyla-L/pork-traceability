/**
 * 出栏称重登记通道 + 审批实名 回归：
 *   1. 令牌姓名声明 = 真实姓名（不再是登录账号）——实名断链的根因就在这一行
 *   2. 来源策略：申请出栏已挂上设备通道 EXIT_APPLY，策略为「设备优先」
 *   3. 出栏口地磅上报完整报文（耳标+体重+称重时间+目标屠宰场）→ 自动入账，落到出栏申报表
 *   4. 同一头猪再称一次（不同称重时刻）→ 拒绝（一头猪只能有一条有效申报）
 *   5. 另一头猪：缺目标屠宰场（去向）→ 转「待人工确认」（设备给不出去向，不得拿设备号顶）
 *   6. 体重越界 → 直接拒绝（称重读数异常不写进链）
 *   7. 人工申报（设备优先环节：免票据）→ 成功入账，来源标为人工
 *   8. 未登记真实姓名的账号审批 → 403（审批是实名责任行为，这条红线取代了旧的票据闸门）
 *   9. 有实名的账号审批 → 通过，审批人落到姓名（不是用户ID）
 *
 * 运行：node logs/verify-exit-apply-device.mjs
 * 产生的业务记录 / 接入流水 / 测试账号 ID 会打印在末尾，由 bash 侧清理
 * （本环境 node 禁止子进程调用，DB 核验与清理必须走 bash）。
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const DEVICE_NO = process.env.EXIT_DEVICE || 'EXITSCALE-001'

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

/** 解出 JWT 载荷，直接看令牌里到底带了什么姓名 */
function jwtPayload(token) {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(Buffer.from(part, 'base64').toString('utf8'))
  } catch {
    return null
  }
}

const pad = (n) => String(n).padStart(2, '0')
function nowText(offsetSeconds = 0) {
  const d = new Date(Date.now() + offsetSeconds * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 业务拒绝的统一判据：FORBIDDEN(2003) 且消息里带该场景的关键词 */
const isForbidden = (r, keyword) =>
  !!r && r.code === 2003 && (r.message || '').includes(keyword)

const login = await api('/auth/login', {
  method: 'POST',
  body: { username: 'admin', password: '123456' },
})
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const stagingIds = []
const track = (r) => {
  const id = r?.data?.stagingId
  if (id) stagingIds.push(id)
  return r
}

console.log('\n== 1) 令牌里的姓名声明 ==')
{
  const payload = jwtPayload(token)
  console.log(`  [INFO] 令牌声明：${JSON.stringify(payload)}`)
  check('令牌带姓名声明', !!payload?.userName, `userName=${payload?.userName}`)
  check('姓名是真实姓名（不是登录账号 admin）', payload?.userName === '系统管理员',
    `userName=${payload?.userName}`)
}

console.log('\n== 2) 来源策略：申请出栏是否已挂设备通道 ==')
{
  const res = await api('/ingest/source-policy', { token })
  const row = (res?.data || []).find((p) => p.code === 'EXIT_APPLY')
  check('策略表里有「申请出栏」', !!row, JSON.stringify(res).slice(0, 200))
  check('已挂设备通道 EXIT_APPLY', row?.channel === 'EXIT_APPLY', `channel=${row?.channel}`)
  check('策略为「设备优先」（去向仍需人工确认）', row?.policy === 'DEVICE_PREFERRED', `policy=${row?.policy}`)
}

console.log('\n== 3) 选两头「还没申报出栏」的在养猪（从末尾取，给演示用的猪留出来）==')
const candidates = await (async () => {
  const pigRes = await api('/breeding/pigs?current=1&size=30&status=1', { token })
  const pigs = pigRes?.data?.records || []
  const applyRes = await api('/breeding/applies?current=1&size=200', { token })
  const applies = applyRes?.data?.records || []
  const taken = new Set(applies.filter((a) => a.approvalStatus === 0 || a.approvalStatus === 1).map((a) => Number(a.pigId)))
  return pigs.filter((p) => p.earTagNo && !taken.has(Number(p.id))).reverse()
})()
check('取到至少 2 头可用的在养猪', candidates.length >= 2, `可用 ${candidates.length} 头`)
if (candidates.length < 2) process.exit(1)
const pigA = candidates[0]
const pigB = candidates[1]
console.log(`  [INFO] 用猪 A = #${pigA.id} ${pigA.earTagNo}；猪 B = #${pigB.id} ${pigB.earTagNo}`)

/** 代设备发一次上报（走完整链路：鉴权/校验/幂等/派发） */
const report = (data, extra = {}) =>
  api('/ingest/demo/report', { method: 'POST', token, body: { deviceNo: DEVICE_NO, data, ...extra } })

const weighA = nowText()
console.log('\n== 4) 出栏口地磅上报（耳标 + 体重 + 称重时刻 + 去向）==')
let deviceApplyId = null
{
  const r = track(await report({
    earTagNo: pigA.earTagNo,
    weightKg: 118.5,
    weighTime: weighA,
    targetSlaughterhouse: '回归验证屠宰场',
  }))
  check('自动入账', r?.data?.accepted === true, JSON.stringify(r?.data).slice(0, 220))
  check('写入出栏申报表', r?.data?.targetTable === 'slaughter_apply', `targetTable=${r?.data?.targetTable}`)
  deviceApplyId = r?.data?.targetId
  check('回执带业务记录编号', !!deviceApplyId, `targetId=${deviceApplyId}`)
}

console.log('\n== 5) 同一头猪再称一次（猪只折返 / 重复读数）==')
{
  const r = track(await report({
    earTagNo: pigA.earTagNo,
    weightKg: 119.2,
    weighTime: nowText(2),
    targetSlaughterhouse: '回归验证屠宰场',
  }))
  check('拒绝（一头猪只能有一条有效申报）', r?.data?.status === 3,
    `status=${r?.data?.status} ${r?.data?.message || ''}`)
}

console.log('\n== 6) 缺目标屠宰场（去向声明设备给不出）==')
{
  const r = track(await report({
    earTagNo: pigB.earTagNo,
    weightKg: 121,
    weighTime: nowText(3),
  }))
  check('转「待人工确认」而不是自动入账', r?.data?.status === 1,
    `status=${r?.data?.status} ${r?.data?.message || ''}`)
}

console.log('\n== 7) 体重越界（称重读数异常）==')
{
  const r = track(await report({
    earTagNo: pigB.earTagNo,
    weightKg: 5,
    weighTime: nowText(4),
    targetSlaughterhouse: '回归验证屠宰场',
  }))
  check('直接拒绝', r?.data?.status === 3, `status=${r?.data?.status} ${r?.data?.message || ''}`)
}

console.log('\n== 7) 人工申报（设备优先环节：免票据，强制实名 + 上链）==')
let manualApplyId = null
{
  const r = await api(`/breeding/pigs/${pigB.id}/apply`, {
    method: 'POST',
    token,
    body: { targetSlaughterhouse: '回归验证屠宰场', weightKg: 120.5 },
  })
  check('人工申报成功入账（2026-10-12 起不再需要兜底票据）',
    r?.code === 200 || r?.success === true, JSON.stringify(r).slice(0, 200))
  manualApplyId = r?.data
  check('回执带业务记录编号（来源应为 MANUAL，申报人应为「系统管理员」）',
    manualApplyId != null, `manualApplyId=${manualApplyId}`)
  console.log(`  [INFO] 人工申报记录编号 = ${manualApplyId}`)
}

console.log('\n== 8) 未登记真实姓名的账号尝试审批 ==')
const ghostUser = `vn${Date.now().toString().slice(-8)}`
{
  const reg = await api('/auth/register', {
    method: 'POST',
    body: { username: ghostUser, password: '123456', confirmPassword: '123456' },
  })
  check('注册一个未填真实姓名的账号', reg?.code === 200 || reg?.success === true, JSON.stringify(reg).slice(0, 200))
  const ghostLogin = await api('/auth/login', { method: 'POST', body: { username: ghostUser, password: '123456' } })
  const ghostToken = ghostLogin?.data?.token || ghostLogin?.data?.accessToken
  check('该账号可登录', !!ghostToken, JSON.stringify(ghostLogin).slice(0, 200))
  if (ghostToken) {
    const payload = jwtPayload(ghostToken)
    check('令牌里没有姓名声明（未实名）', !payload?.userName, `userName=${payload?.userName}`)
    if (manualApplyId) {
      const r = await api(`/breeding/applies/${manualApplyId}/approve`, {
        method: 'PUT',
        token: ghostToken,
        body: { approved: true, comment: '未实名账号测试' },
      })
      check('审批被拒（必须实名）', isForbidden(r, '必须实名'), `code=${r?.code} ${r?.message || ''}`)
    }
  }
}

console.log('\n== 9) 有实名的账号审批 ==')
{
  const r = await api(`/breeding/applies/${manualApplyId}/approve`, {
    method: 'PUT',
    token,
    body: { approved: true, comment: '回归验证：审批通过' },
  })
  check('审批通过', r?.code === 200 || r?.success === true, JSON.stringify(r).slice(0, 200))
  console.log('  [INFO] 审批人应为「系统管理员」（DB 侧核验）')
}

console.log('\n== 10) 审批通过后生猪状态推进 ==')
{
  const res = await api(`/breeding/pigs/${pigB.id}`, { token })
  check('猪 B 状态变为「已出栏」', Number(res?.data?.status) === 2, `status=${res?.data?.status}`)
}

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
console.log(`清理用 —— 接入流水 ID：${stagingIds.join(',') || '无'}`)
console.log(`清理用 —— 设备登记的出栏申报 ID：${deviceApplyId}`)
console.log(`清理用 —— 人工申报的出栏申报 ID：${manualApplyId}`)
console.log(`清理用 —— 测试账号：${ghostUser}`)
console.log(`清理用 —— 猪 A=#${pigA.id}（未审批，状态未变）；猪 B=#${pigB.id}（状态已变 2）`)
process.exit(fail === 0 ? 0 : 1)
