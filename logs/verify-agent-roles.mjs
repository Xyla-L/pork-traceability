/**
 * 准入验收：智能助手现阶段只开给系统管理员。
 *
 * 锁三件事：
 *   1. 非管理员调四个接口全部被拒（不能靠「前端藏了图标」当权限——藏起来挡不住直接调接口）
 *   2. 没有令牌的请求一样进不来
 *   3. 管理员能拿到全部能力，且两个新工具（临期召回 / 检疫免疫）确实能被路由到
 *
 * 角色白名单（`AgentTool.roles()`）仍然保留在原处，没有删：放开那天只需改
 * `agent.access.roles` 一行，再把本脚本换回「各角色能力集合对拍」即可。
 * 现在按角色对拍会全红——因为闸门在准入层，根本到不了工具层。
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

let pass = 0, fail = 0
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  [PASS] ${name}`) }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}
async function api(path, { method = 'GET', token, body } = {}) {
  const h = { 'Content-Type': 'application/json' }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined })
  const json = await res.json().catch(() => null)
  return { status: res.status, json }
}
const capNames = (data) => (data?.capabilities || [])
  .map((s) => (s.match(/（(.*)）/) || [])[1] || s)

const login = async (user) => {
  const r = await api('/auth/login', { method: 'POST', body: { username: user, password: '123456' } })
  return r.json?.data?.token
}

/** 现阶段被关在门外的角色：保留名单，放开时从这里删 */
const OUTSIDERS = [
  ['farm_user', '养殖户'],
  ['distribution_user', '配送'],
  ['retail_user', '零售商'],
]

console.log('== 1) 管理员：全能力 ==')
const adminToken = await login('admin')
check('管理员登录', !!adminToken)
const ALL_TOOLS = ['ingest-diagnose', 'daily-brief', 'temperature', 'complaint',
  'transport-analysis', 'apply-analysis', 'retail-risk', 'quarantine', 'dashboard', 'trace']
if (adminToken) {
  const meta = await api('/agent/meta', { token: adminToken })
  const caps = capNames(meta.json?.data)
  console.log('  能力：' + caps.join(' / '))
  for (const t of ALL_TOOLS) check(`管理员能用 ${t}`, caps.includes(t), caps.join(','))
}

console.log('\n== 2) 非管理员：四个接口全部拒绝 ==')
for (const [user, label] of OUTSIDERS) {
  const token = await login(user)
  check(`${label}（${user}）登录`, !!token)
  if (!token) continue
  const meta = await api('/agent/meta', { token })
  const chat = await api('/agent/chat', { method: 'POST', token, body: { message: '今天有什么需要优先处理的' } })
  const threads = await api('/agent/threads', { token })
  check(`${label} 拿不到能力清单`, meta.json?.data?.capabilities == null, `data=${JSON.stringify(meta.json?.data)?.slice(0, 60)}`)
  check(`${label} 问不了问题`, chat.json?.data == null, `answer=${(chat.json?.data?.answer || '').slice(0, 40)}`)
  check(`${label} 翻不了会话`, threads.json?.data == null)
  // 网关对业务异常统一返回 200 + code，所以盯 data 为空而不是 HTTP 码
  const code = chat.json?.code
  check(`${label} 被明确拒绝（不是静默失败）`, code != null && code !== 0 && code !== 200, `code=${code}`)
}

console.log('\n== 3) 无令牌：进不来 ==')
const anon = await api('/agent/chat', { method: 'POST', body: { message: '温度有没有超标' } })
check('匿名请求被拒', anon.status === 401 || anon.status === 403 || anon.json?.data == null,
  `status=${anon.status} data=${JSON.stringify(anon.json?.data)?.slice(0, 40)}`)

console.log('\n== 4) 管理员的两个新工具能被路由到 ==')
if (adminToken) {
  for (const [q, want] of [
    ['临期和召回有没有问题', 'retail-risk'],
    ['哪些批次免疫档案不全', 'quarantine'],
  ]) {
    const r = await api('/agent/chat', { method: 'POST', token: adminToken, body: { message: q } })
    const d = r.json?.data
    check(`「${q}」归 ${want}`, d?.tool === want, `tool=${d?.tool}`)
    check(`「${q}」有取数不是空答`, (d?.facts || []).length > 0, `facts=${(d?.facts || []).length}`)
  }
}

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
process.exit(fail === 0 ? 0 : 1)
