/**
 * 回归：已作废的免疫记录在列表接口里的可见性
 *   1. includeVoid=true  → 返回全量，已作废记录带 voidFlag=1 + 作废理由/操作人/时间
 *   2. includeVoid=false（默认）→ 只返回有效记录（与历史行为一致，供屠宰检验等只读消费方使用）
 *
 * 运行：node logs/verify-vaccine-void-visible.mjs
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const PIG_ID = Number(process.env.PIG_ID || 34)

async function api(path, { method = 'GET', token } = {}) {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers })
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

const loginRes = await fetch(BASE + '/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
})
const login = await loginRes.json().catch(() => ({}))
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token, JSON.stringify(login).slice(0, 120))
if (!token) process.exit(1)

console.log('\n== 含已作废（includeVoid=true）==')
const withVoid = await api(`/breeding/pigs/${PIG_ID}/vaccines/list?includeVoid=true`, { token })
const all = Array.isArray(withVoid?.data) ? withVoid.data : []
check('接口返回数组', Array.isArray(withVoid?.data), JSON.stringify(withVoid).slice(0, 160))

const voided = all.filter((r) => Number(r.voidFlag) === 1)
const alive = all.filter((r) => Number(r.voidFlag) !== 1)
check('已作废记录被返回（不再凭空消失）', voided.length > 0, `voided=${voided.length}`)
check(
  '已作废记录带作废痕迹（理由/操作人/时间）',
  voided.every((r) => r.voidReason && r.voidOperator && r.voidTime),
  JSON.stringify(voided[0] || {}),
)
check(
  '作废字段已脱出内部命名（前端可直接用）',
  voided.every((r) => 'voidFlag' in r && 'voidReason' in r && 'voidOperator' in r && 'voidTime' in r),
)
console.log(`  [INFO] 猪 #${PIG_ID}：有效 ${alive.length} 条，已作废 ${voided.length} 条`)
voided.forEach((r) => {
  console.log(`  [INFO]   #${r.id} ${r.vaccineName} 批号 ${r.batchNo} → 作废理由「${r.voidReason}」（${r.voidOperator} / ${r.voidTime}）`)
})

console.log('\n== 不含已作废（includeVoid=false，默认）==')
const noVoid = await api(`/breeding/pigs/${PIG_ID}/vaccines/list?includeVoid=false`, { token })
const onlyAlive = Array.isArray(noVoid?.data) ? noVoid.data : []
check('默认接口不返回已作废记录', onlyAlive.every((r) => Number(r.voidFlag) !== 1), JSON.stringify(onlyAlive.map((r) => r.id)))
check('默认接口条数与有效条数一致', onlyAlive.length === alive.length, `${onlyAlive.length} vs ${alive.length}`)

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
