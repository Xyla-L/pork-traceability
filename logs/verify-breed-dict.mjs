const BASE = 'http://localhost:8080/api/v1'
const H = { 'Content-Type': 'application/json' }

const login = await fetch(`${BASE}/auth/login`, {
  method: 'POST', headers: H, body: JSON.stringify({ username: 'admin', password: '123456' }),
}).then((r) => r.json())
const token = login.data?.accessToken || login.data?.token
if (!token) { console.error('登录失败', login); process.exit(1) }
const auth = { ...H, Authorization: `Bearer ${token}` }

// 1) 品种字典
const breeds = await fetch(`${BASE}/breeding/pigs/breeds`, { headers: auth }).then((r) => r.json())
console.log('品种字典 code =', breeds.code)
console.log('品种列表 =', JSON.stringify(breeds.data))

// 2) 按「三元杂」搜索（字典里有，但库中该品种的有效猪已被作废）
const q = await fetch(`${BASE}/breeding/pigs?current=1&size=5&breed=${encodeURIComponent('三元杂')}`, { headers: auth }).then((r) => r.json())
console.log('按三元杂筛选 total =', q.data?.total)

// 3) 生猪总数（清理后应为 22）
const all = await fetch(`${BASE}/breeding/pigs?current=1&size=5`, { headers: auth }).then((r) => r.json())
console.log('有效生猪总数 =', all.data?.total)
const first = (all.data?.records || all.data?.list || [])[0]
console.log('首条档案字段 =', first ? Object.keys(first).join(',') : '(空)')
console.log('首条出生日期 =', first?.birthDate, '| 品种 =', first?.breed, '| 状态 =', first?.statusLabel)

// 4) 确认 ET-ACC 测试残留已不可见
const gone = await fetch(`${BASE}/breeding/pigs?current=1&size=10&earTagNo=ET-ACC`, { headers: auth }).then((r) => r.json())
console.log('ET-ACC 残留查询 total =', gone.data?.total)
