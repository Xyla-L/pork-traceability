/**
 * 工作台按角色分视图 —— 回归
 *
 * 验证点：
 *   1. 六个账号登录后，接口按 X-User-Role 返回不同视图（不是前端过滤，是后端裁剪）
 *   2. 每个角色都必须有：role / roleLabel / scopeLabel / statCards / blocks
 *   3. 零售商的卡片是销量口径（销售额、销量、在售、临期），并带 30 天趋势
 *   4. 角色之间的卡片集合互不相同（防止「所有角色拿到同一套数据、只是页面不显示」）
 *   5. 结构完整性：每个 block 有 key/type/title/span，表格块必须有 columns
 *   6. 下游全部健康时 degraded 为空（有值说明某服务取数失败，页面会提示）
 *
 * 说明：SUPERVISOR 没有种子账号，用「不带角色头」的直连调用验证兜底为监管视角。
 *
 * 用法：node logs/verify-dashboard-roles.mjs
 */

const BASE = 'http://localhost:8080/api/v1'
const PASSWORD = '123456'

let pass = 0
let fail = 0
const failures = []

function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`  ✓ ${name}`)
  } else {
    fail++
    failures.push(`${name}${detail ? `（${detail}）` : ''}`)
    console.log(`  ✗ ${name}${detail ? ` — ${detail}` : ''}`)
  }
}

async function login(username) {
  const response = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PASSWORD }),
  })
  const body = await response.json()
  if (body.code !== 200 || !body.data?.token) {
    throw new Error(`${username} 登录失败：${body.message || body.code}`)
  }
  return body.data.token
}

async function overview(token) {
  const response = await fetch(`${BASE}/dashboard/overview`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (response.status !== 200) throw new Error(`工作台接口返回 ${response.status}`)
  const body = await response.json()
  if (body.code !== 200) throw new Error(`工作台接口业务失败：${body.message}`)
  return body.data
}

const CASES = [
  {
    user: 'admin',
    role: 'ADMIN',
    roleLabel: '系统管理员',
    mustCards: ['pigCount', 'slaughterCount', 'transportCount', 'expireCount', 'complaintCount', 'chainCount'],
    mustBlocks: ['flowSankey'],
  },
  {
    user: 'farm_user',
    role: 'FARMER',
    roleLabel: '养殖户',
    mustCards: ['raisingCount', 'pendingApply', 'approvedApply', 'exitedCount'],
    mustBlocks: ['pigStatusPie', 'rejectBar', 'applyTable', 'pigTable'],
  },
  {
    user: 'slaughter_user',
    role: 'SLAUGHTER_OP',
    roleLabel: '屠宰场操作员',
    mustCards: ['entryToday', 'inspectToday', 'stampToday', 'batchTotal'],
    mustBlocks: ['inspectResultPie', 'entryTable', 'stampTable'],
  },
  {
    user: 'distribution_user',
    role: 'DISTRIBUTOR',
    roleLabel: '分销商',
    mustCards: ['waitingTransport', 'movingTransport', 'arrivingTransport', 'receivedTransport'],
    mustBlocks: ['transportPie', 'transportMixBar', 'waitingTable', 'movingTable'],
  },
  {
    user: 'retail_user',
    role: 'RETAILER',
    roleLabel: '零售商',
    mustCards: ['salesAmount', 'salesWeight', 'soldCount', 'expiringCount'],
    mustBlocks: ['salesTrendBar', 'partRankBar', 'expirePie', 'warningTable'],
  },
  {
    user: 'supervisor_user',
    role: 'SUPERVISOR',
    roleLabel: '监管人员',
    mustCards: ['complaintCount', 'expireCount', 'expiredCount', 'transportCount', 'slaughterCount', 'chainCount'],
    mustBlocks: ['flowSankey', 'warningPie', 'chainBar', 'warningTable'],
  },
]

const views = []

console.log('\n== 1) 各角色视图 ================')
for (const item of CASES) {
  console.log(`\n-- ${item.user} -> ${item.role}`)
  const token = await login(item.user)
  const data = await overview(token)
  views.push({ role: item.role, data })

  check(`${item.role} 角色码正确`, data.role === item.role, `实际 ${data.role}`)
  check(`${item.role} 角色名正确`, data.roleLabel === item.roleLabel, `实际 ${data.roleLabel}`)
  check(`${item.role} 带数据范围说明`, typeof data.scopeLabel === 'string' && data.scopeLabel.length > 0)

  const cardKeys = (data.statCards || []).map((card) => card.key)
  check(`${item.role} 卡片数 ≥ 4`, cardKeys.length >= 4, `实际 ${cardKeys.length}`)
  for (const key of item.mustCards) {
    check(`${item.role} 含卡片 ${key}`, cardKeys.includes(key), `实际 ${cardKeys.join(',')}`)
  }

  const blockKeys = (data.blocks || []).map((block) => block.key)
  for (const key of item.mustBlocks) {
    check(`${item.role} 含区块 ${key}`, blockKeys.includes(key), `实际 ${blockKeys.join(',')}`)
  }

  check(`${item.role} 无降级数据源`, (data.degraded || []).length === 0, `degraded=${JSON.stringify(data.degraded)}`)
}

console.log('\n== 2) 结构完整性 ================')
for (const view of views) {
  const blocks = view.data.blocks || []
  const badType = blocks.filter((block) => !['sankey', 'pie', 'bar', 'barH', 'table'].includes(block.type))
  check(`${view.role} block type 合法`, badType.length === 0, badType.map((b) => b.key).join(','))

  const badSpan = blocks.filter((block) => block.span !== 1 && block.span !== 2)
  check(`${view.role} block span 合法`, badSpan.length === 0, badSpan.map((b) => b.key).join(','))

  const badTitle = blocks.filter((block) => !block.title)
  check(`${view.role} block 都有标题`, badTitle.length === 0, badTitle.map((b) => b.key).join(','))

  // key 重复会让前端 v-for 的 :key 撞车，表现为某个卡片渲染错位或消失
  const keys = blocks.map((block) => block.key)
  check(`${view.role} block key 唯一`, new Set(keys).size === keys.length, keys.join(','))

  const tables = blocks.filter((block) => block.type === 'table')
  check(`${view.role} 表格块都有列定义`, tables.every((block) => (block.columns || []).length > 0))
  check(`${view.role} 表格块 rows 是数组`, tables.every((block) => Array.isArray(block.rows)))
}

console.log('\n== 3) 角色之间确有差异 ================')
const signature = views.map((view) => ({ role: view.role, key: view.data.statCards.map((c) => c.key).join('|') }))
for (let i = 0; i < signature.length; i++) {
  for (let j = i + 1; j < signature.length; j++) {
    check(`${signature[i].role} 与 ${signature[j].role} 卡片不同`, signature[i].key !== signature[j].key)
  }
}

console.log('\n== 4) 零售商销量口径 ================')
const retailer = views.find((view) => view.role === 'RETAILER').data
const retailerCards = Object.fromEntries(retailer.statCards.map((card) => [card.key, card]))
check('零售商有销售额卡片', retailerCards.salesAmount?.unit === '元', `unit=${retailerCards.salesAmount?.unit}`)
check('零售商有销量卡片', retailerCards.salesWeight?.unit === 'kg', `unit=${retailerCards.salesWeight?.unit}`)
check('销售额是数字', typeof retailerCards.salesAmount?.value === 'number', typeof retailerCards.salesAmount?.value)

const trend = (retailer.blocks || []).find((block) => block.key === 'salesTrendBar')
check('销量趋势是整行（span=2）', trend?.span === 2, `span=${trend?.span}`)
check('销量趋势补齐 30 天', Array.isArray(trend?.data) && trend.data.length === 30, `实际 ${trend?.data?.length}`)
check('销量趋势每个点都有名称与值',
  (trend?.data || []).every((point) => typeof point.name === 'string' && typeof point.value === 'number'))

const rank = (retailer.blocks || []).find((block) => block.key === 'partRankBar')
check('部位排行最多 5 项', Array.isArray(rank?.data) && rank.data.length <= 5, `实际 ${rank?.data?.length}`)

console.log('\n== 5) 未认证与监管专属口径 ================')
const anonymous = await fetch(`${BASE}/dashboard/overview`)
check('未登录访问工作台被拒', anonymous.status === 401 || anonymous.status === 403, `status=${anonymous.status}`)

const supervisor = views.find((view) => view.role === 'SUPERVISOR').data
check('监管视图不含经营明细卡片',
  !(supervisor.statCards || []).some((card) => card.key === 'salesAmount'))
check('监管视图含已过期未处置',
  (supervisor.statCards || []).some((card) => card.key === 'expiredCount'))
check('监管视图待办为举报', (supervisor.todoList || []).every((item) => item.type === '举报处理'))

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
if (fail > 0) {
  console.log('失败项：')
  failures.forEach((item) => console.log(`  - ${item}`))
  process.exit(1)
}
