#!/usr/bin/env node
/**
 * 一次性验证：养殖场档案变更 → 机构表（机构管理）自动同步（会临时建一条养殖场，用完即删，自清理）。
 *
 * 验证四件事：
 *   1) 新建养殖场后，机构管理（/system/orgs/tree）里立刻多出同名「养殖场」节点
 *   2) 养殖场改名后，机构节点跟着改名（不是新增一个）
 *   3) 删除养殖场后，机构节点一并移除
 *   4) 网关不转发 /api/v1/auth/internal/**（内部接口对外不可达）
 *
 * 用法：node logs/verify-org-auto-sync.mjs
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }
const STAMP = Date.now()
const FARM_NAME = `同步验证养殖场-${STAMP}`
const FARM_NAME_2 = `同步验证养殖场-改名-${STAMP}`

let token = null
let failed = 0

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
  for (const n of nodes) { out.push(n); if (n.children?.length) flatten(n.children, out) }
  return out
}

async function orgOf(name) {
  const tree = (await api('/system/orgs/tree')).data || []
  return flatten(tree).find((n) => n.type === 'farm' && n.name === name) || null
}

function check(label, ok, extra = '') {
  console.log(`${ok ? '✅' : '❌'} ${label}${extra ? ' — ' + extra : ''}`)
  if (!ok) failed++
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('① 登录成功\n')

  let farmId = null
  try {
    // ---------- 1) 新建养殖场 ----------
    const create = await api('/breeding/farms', {
      method: 'POST',
      body: JSON.stringify({
        farmName: FARM_NAME, licenseNo: `FARM-SYNC-TEST-${STAMP}`,
        address: '同步验证专用地址', contactPerson: '验证员', contactPhone: '13800009999', scale: 123,
      }),
    })
    check('新建养殖场接口返回成功', create.code === 200, `code=${create.code} ${create.message || ''}`)

    // 找到新建的养殖场 id（用于后续改名/删除）
    const page = await api('/breeding/farms?current=1&size=500')
    const records = page.data?.records || page.data?.list || []
    const created = records.find((r) => r.farmName === FARM_NAME
      || r.farm_name === FARM_NAME)
    farmId = created?.id ?? null

    const orgAfterCreate = await orgOf(FARM_NAME)
    check('新建后机构管理里出现该养殖场', !!orgAfterCreate,
      orgAfterCreate ? `orgId=${orgAfterCreate.id} parentId=${orgAfterCreate.parentId}` : '未找到节点')

    // ---------- 2) 改名 ----------
    if (farmId) {
      const upd = await api(`/breeding/farms/${farmId}`, {
        method: 'PUT',
        body: JSON.stringify({
          farmName: FARM_NAME_2, licenseNo: `FARM-SYNC-TEST-${STAMP}`,
          address: '同步验证专用地址', contactPerson: '验证员', contactPhone: '13800009999', scale: 123,
        }),
      })
      check('养殖场改名接口返回成功', upd.code === 200, `code=${upd.code} ${upd.message || ''}`)

      const renamed = await orgOf(FARM_NAME_2)
      const oldStill = await orgOf(FARM_NAME)
      check('改名后机构节点同步改名（旧名不再存在）', !!renamed && !oldStill,
        renamed ? `orgId=${renamed.id}` : '新名节点未找到')
    } else {
      check('取到新建养殖场 id', false, '列表里没找到刚建的养殖场，跳过改名与删除验证')
    }
  } finally {
    // ---------- 3) 删除（自清理） ----------
    if (farmId) {
      const del = await api(`/breeding/farms/${farmId}`, { method: 'DELETE' })
      check('删除养殖场接口返回成功', del.code === 200, `code=${del.code} ${del.message || ''}`)
      const orgAfterDelete = await orgOf(FARM_NAME_2)
      check('删除后机构节点一并移除', !orgAfterDelete,
        orgAfterDelete ? `仍存在 orgId=${orgAfterDelete.id}` : '')
    }
  }

  // ---------- 4) 内部接口对外不可达 ----------
  const blocked = await api('/auth/internal/orgs/sync', {
    method: 'POST',
    body: JSON.stringify({ type: 'farm', name: '不该被外部创建的机构' }),
  })
  check('网关不转发 /auth/internal/**（外部不可越权登记机构）',
    blocked.httpStatus === 404 || blocked.code === 404,
    `httpStatus=${blocked.httpStatus} code=${blocked.code}`)

  console.log(failed === 0 ? '\n🎉 全部通过' : `\n⚠️ 有 ${failed} 项未通过`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => { console.error('执行失败：', e.message); process.exit(1) })
