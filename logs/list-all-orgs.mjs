#!/usr/bin/env node
/** 临时：打印全部机构名（按环节分组），用于核对回归残留的准确名称。 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }

let token = null
async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  return response.json()
}
function flatten(nodes, out = []) {
  if (!Array.isArray(nodes)) return out
  for (const n of nodes) {
    out.push(n)
    if (n.children?.length) flatten(n.children, out)
  }
  return out
}

const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
token = login.data?.accessToken || login.data?.token || login.data?.access_token
const tree = await api('/system/orgs/tree')
const flat = flatten(tree.data || [])
for (const o of flat) {
  console.log(`[${o.type}] id=${o.id} parent=${o.parentId} name=[${o.name}] len=${(o.name || '').length}`)
}
