#!/usr/bin/env node
/**
 * 一次性验证：机构管理（sys_org）与其他业务页面的机构是否一致（只读）。
 *
 * 背景：机构存在两套登记——
 *   · db_common.sys_org              → 「机构管理」/ 出题审批屠宰场下拉 / 冷链门店下拉 的数据源
 *   · db_breeding.farm、db_slaughter.slaughterhouse → 业务机构档案（养殖场下拉等）
 * 本脚本核对：
 *   1) 机构树 getOrgTree 能拿到的 养殖场 / 屠宰场 数量
 *   2) 与业务接口 /breeding/farms 的养殖场数量是否一致
 *
 * 用法：node logs/verify-org-sync.mjs
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }

let token = null

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
  console.log('① 登录成功')

  // 1) 机构树
  const treeRes = await api('/system/orgs/tree')
  const tree = treeRes.data || []
  const flat = flatten(tree)
  const byType = {}
  for (const n of flat) byType[n.type] = (byType[n.type] || 0) + 1

  console.log('\n② 机构树（getOrgTree）节点统计：')
  console.log('   ' + Object.entries(byType).map(([t, c]) => `${t}=${c}`).join('  '))

  // 2) 业务侧养殖场数量
  const farmRes = await api('/breeding/farms?current=1&size=500')
  const farms = farmRes.data?.records || farmRes.data?.list || []
  console.log(`\n③ 业务侧养殖场（/breeding/farms）= ${farms.length}`)
  console.log(`   机构表养殖场（tree type=farm） = ${byType.farm || 0}`)

  const ok = (byType.farm || 0) === farms.length
  console.log(ok
    ? '\n✅ 一致：机构管理里的养殖场数量与业务档案数量相同'
    : '\n❌ 不一致：机构管理缺养殖场，请执行 backend/init-sql/20-sync-org-from-business.sql')

  // 3) 列出机构树里的养殖场/屠宰场名称，便于人工核对
  console.log('\n④ 机构树中的养殖场 / 屠宰场：')
  for (const n of flat.filter((x) => x.type === 'farm' || x.type === 'slaughter')) {
    console.log(`   [${n.type}] ${n.id} ${n.name} / ${n.manager || '-'} / ${n.phone || '-'}`)
  }

  process.exit(ok ? 0 : 1)
}

main().catch((e) => { console.error('执行失败：', e.message); process.exit(1) })
