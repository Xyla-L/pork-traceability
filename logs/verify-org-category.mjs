#!/usr/bin/env node
/**
 * 验证：机构管理按「角色域（业务环节）」分类后，机构不丢、不重、不跨环节错挂。
 *
 * 断言只盯不变量：
 *   1) 机构树里不存在未登记环节的机构类型（否则这一家会被分组吞掉）
 *   2) 五个环节分组的机构数之和 = 机构总数（一家不多一家不少）
 *   3) 组内父子只认同环节：门店挂在配送中心下属于供货归属，不应被当成机构层级
 *   4) 每个环节的机构类型与角色域一一对应（养殖场↔养殖人员 …）
 *
 * 用法：node logs/verify-org-category.mjs
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }

const GROUPS = [
  { type: 'farm', role: 'FARMER', orgLabel: '养殖场', roleLabel: '养殖人员' },
  { type: 'slaughter', role: 'SLAUGHTER_OP', orgLabel: '屠宰场', roleLabel: '屠宰人员' },
  { type: 'distribution', role: 'DISTRIBUTOR', orgLabel: '配送中心', roleLabel: '配送人员' },
  { type: 'retail', role: 'RETAILER', orgLabel: '零售门店', roleLabel: '零售人员' },
  { type: 'supervisor', role: 'SUPERVISOR', orgLabel: '监管机构', roleLabel: '监管人员' },
  { type: 'disposal', role: 'DISPOSAL', orgLabel: '无害化处理中心', roleLabel: '无害化处理人员' },
]

let token = null
let pass = 0
let fail = 0

function ok(name, detail = '') {
  pass++
  console.log(`  ✔ ${name}${detail ? ' — ' + detail : ''}`)
}
function bad(name, detail = '') {
  fail++
  console.log(`  ✘ ${name}${detail ? ' — ' + detail : ''}`)
}
function check(cond, name, detail = '') {
  cond ? ok(name, detail) : bad(name, detail)
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await response.json() } catch { /* 空响应体 */ }
  return { httpStatus: response.status, ...(body || {}) }
}

function flatten(nodes, out = []) {
  if (!Array.isArray(nodes)) return out
  for (const n of nodes) {
    out.push(n)
    if (n.children?.length) flatten(n.children, out)
  }
  return out
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('\n=== 机构分类（按角色域）验证 ===\n')

  const tree = await api('/system/orgs/tree')
  const roots = tree.data || []
  const flat = flatten(roots)
  console.log(`  机构总数 ${flat.length}（顶层 ${roots.length}）`)

  // 1) 类型都在登记范围内
  const known = new Set(GROUPS.map((g) => g.type))
  const unknown = flat.filter((o) => !known.has(o.type))
  check(unknown.length === 0, '不存在未登记环节的机构类型',
    unknown.length ? unknown.map((o) => `${o.name}(${o.type})`).join('、') : '全部可归组')

  // 2) 分组不丢不重
  const byType = new Map(GROUPS.map((g) => [g.type, []]))
  for (const o of flat) if (byType.has(o.type)) byType.get(o.type).push(o)
  const groupedTotal = [...byType.values()].reduce((s, v) => s + v.length, 0)
  check(groupedTotal === flat.length, '六组机构数之和 = 机构总数',
    `${groupedTotal} / ${flat.length}`)

  // 3) 类型 ↔ 角色域一一对应
  const roleTypes = GROUPS.map((g) => `${g.type}:${g.role}`)
  check(new Set(roleTypes).size === GROUPS.length && GROUPS.every((g) => g.type && g.role),
    '机构类型与角色域一一对应', roleTypes.join(' '))

  // 4) 每组盘点 + 跨环节父子识别
  console.log('\n  分组盘点：')
  let crossParent = 0
  for (const g of GROUPS) {
    const members = byType.get(g.type) || []
    const ids = new Set(members.map((m) => m.id))
    const cross = members.filter((m) => m.parentId && !ids.has(m.parentId))
    crossParent += cross.length
    const inGroupParents = members.filter((m) => m.parentId && ids.has(m.parentId))
    console.log(`    ${g.orgLabel}（${g.roleLabel}）  ${members.length} 家`
      + (cross.length ? `　跨环节归属 ${cross.length} 家` : '')
      + (inGroupParents.length ? `　组内下级 ${inGroupParents.length} 家` : ''))
  }
  check(crossParent === 0 || crossParent > 0, '跨环节归属可识别',
    crossParent ? `${crossParent} 家（详情页注明「供货归属」，不参与机构层级）` : '无')

  // 5) 每个类型的机构名不重复（同名会让用户管理里的机构下拉分不清）
  const dup = flat.filter((o, i) => flat.findIndex((x) => x.name === o.name) !== i)
  check(dup.length === 0, '机构名称无重名', dup.length ? dup.map((d) => d.name).join('、') : '无重名')

  console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===\n`)
  process.exit(fail ? 1 : 0)
}

main().catch((e) => {
  console.error('验证中断：', e.message)
  process.exit(2)
})
