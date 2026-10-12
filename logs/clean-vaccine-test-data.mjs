/**
 * 清理 2026-10-08 回归脚本在演示生猪（pigId=34 / ET-DEMO-1791194024670）上留下的测试疫苗记录。
 * 口径：免疫记录已上链，不做物理删除，一律走「作废」并留理由，仍可追溯。
 * 保留：用户自己在页面上报的那一条（vaccine_record #47）。
 *
 * 运行：node logs/clean-vaccine-test-data.mjs
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

const PIG_ID = 34
/** 脚本产生的 5 条测试记录（批号带 VF- / VAC-VERIFY- 前缀） */
const TEST_RECORD_IDS = [42, 43, 44, 45, 46]
const REASON = '测试数据清理：功能回归脚本产生的演示残留'

async function api(path, { method = 'GET', token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return res.json().catch(() => ({}))
}

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

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token, JSON.stringify(login).slice(0, 120))
if (!token) process.exit(1)

console.log('\n== 作废测试疫苗记录 ==')
for (const id of TEST_RECORD_IDS) {
  const res = await api(`/breeding/pigs/${PIG_ID}/vaccines/${id}/void`, {
    method: 'POST',
    token,
    body: { reason: REASON },
  })
  const ok = res?.code === 200 || res?.code === 0 || res?.success === true
  check(`记录 #${id} 作废`, ok, JSON.stringify(res).slice(0, 160))
}

console.log('\n== 复核（void_flag 是逻辑删除字段，作废后列表应自动隐藏）==')
const list = await api(`/breeding/pigs/${PIG_ID}/vaccines`, { token })
const rows = list?.data?.records || list?.data?.list || list?.data || []
const ids = new Set((Array.isArray(rows) ? rows : []).map((r) => r.id))
for (const id of TEST_RECORD_IDS) {
  check(`#${id} 已从列表消失`, !ids.has(id), `剩余列表: ${[...ids].join(',')}`)
}
check('用户上报记录 #47 未受影响（仍在列表）', ids.has(47), `剩余列表: ${[...ids].join(',')}`)

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
