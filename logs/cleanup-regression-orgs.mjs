#!/usr/bin/env node
/**
 * 清理机构管理里的回归测试残留机构（折溢回归场XXXXXX）。
 *
 * 默认 dry-run：只列出匹配机构与删除障碍（下级机构 / 关联用户），不动数据。
 * 加 --apply 才真正删除：逐家走 DELETE /system/orgs/{id}，失败逐条报告，不中断。
 *
 * 用法：node logs/cleanup-regression-orgs.mjs [--apply]
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }
/** 本次要删的：养殖场环节的「折叠回归场XXXXXX」与「逻辑闸门场XXXXXXXXXXXX」（回归测试自建的一次性主体） */
const PATTERN = /^(折叠回归场|逻辑闸门场)\d+$/
/** 同类的其他测试残留，只报告不删，等用户逐类确认 */
const SIBLINGS = [/^分组回归场\d+$/, /^分组截图场$/, /^凭证复验场$/]
const APPLY = process.argv.includes('--apply')

let token = null
let pass = 0
let fail = 0
const ok = (n, d = '') => { pass++; console.log(`  ✔ ${n}${d ? ' — ' + d : ''}`) }
const bad = (n, d = '') => { fail++; console.log(`  ✘ ${n}${d ? ' — ' + d : ''}`) }
const check = (c, n, d = '') => c ? ok(n, d) : bad(n, d)

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await response.json() } catch { /* 空响应体 */ }
  return { httpStatus: response.status, ...(body || {}) }
}

function flatten(nodes, out = [], parentId = null) {
  if (!Array.isArray(nodes)) return out
  for (const n of nodes) {
    out.push({ ...n, _parentName: parentId?.name || null })
    if (n.children?.length) flatten(n.children, out, n)
  }
  return out
}

const browser = null // 仅供阅读：本脚本纯 API，不开浏览器

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败')

  console.log(`\n=== 回归残留机构清理（${APPLY ? 'APPLY 实删' : 'DRY-RUN 只看'}）===\n`)

  const tree = await api('/system/orgs/tree')
  const flat = flatten(tree.data || [])
  const targets = flat.filter((o) => PATTERN.test(o.name || ''))

  if (!targets.length) {
    console.log('  未发现匹配「' + PATTERN.source + '」的机构，无需清理。')
    return
  }

  // 同类测试残留（折叠回归场/逻辑闸门场/分组截图场/凭证复验场）只报告，不在本次范围
  const otherGroup = flat.filter((o) => SIBLINGS.some((re) => re.test(o.name || '')))

  console.log(`  匹配到 ${targets.length} 家：`)
  for (const t of targets) {
    const children = (t.children || []).length
    const users = await api(`/system/users?pageNum=1&pageSize=1&orgId=${t.id}`)
    const userTotal = users.data?.total ?? '?'
    console.log(`    [${t.type}] id=${t.id} ${t.name}　下级 ${children}　关联用户 ${userTotal}`)
  }
  if (otherGroup.length) {
    console.log(`  ⚠ 养殖场之外还有 ${otherGroup.length} 家同模式残留：`
      + otherGroup.map((o) => `${o.name}(${o.type})`).join('、'))
  }

  if (!APPLY) {
    console.log('\n  dry-run 结束，未删除任何数据。确认清单后加 --apply 执行。')
    return
  }

  console.log('\n  开始删除：')
  for (const t of targets) {
    const children = (t.children || []).length
    if (children > 0) {
      bad(`删除 ${t.name}`, `仍有 ${children} 家下级机构，跳过（先清下级）`)
      continue
    }
    const result = await api(`/system/orgs/${t.id}`, { method: 'DELETE' })
    if (result.httpStatus === 200 && (result.code === undefined || result.code === 0 || result.code === 200)) {
      ok(`删除 ${t.name}`, `id=${t.id}`)
    } else {
      bad(`删除 ${t.name}`, `id=${t.id} ${JSON.stringify(result).slice(0, 160)}`)
    }
  }

  // 复核：树上不再有匹配项
  const after = await api('/system/orgs/tree')
  const afterFlat = flatten(after.data || [])
  const left = afterFlat.filter((o) => PATTERN.test(o.name || ''))
  check(left.length === 0, '复核：机构树中已无折溢回归场',
    left.length ? left.map((o) => o.name).join('、') : `剩余机构 ${afterFlat.length} 家`)

  console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===\n`)
  process.exit(fail ? 1 : 0)
}

main().catch((e) => {
  console.error('清理中断：', e.message)
  process.exit(2)
})
