// 临时校验：设备台账不泄露密钥 + 演示上报能走通完整链路
const BASE = 'http://localhost:8080/api/v1'
let token = null

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { ...options, headers })
  const text = await res.text()
  let body
  try { body = JSON.parse(text) } catch { body = text }
  return { httpStatus: res.status, ...(typeof body === 'object' && body !== null ? body : { raw: body }) }
}

const out = []
const log = (s) => out.push(s)

const login = await api('/auth/login', { method: 'POST', body: JSON.stringify({ username: 'admin', password: '123456' }) })
token = login.data?.accessToken || login.data?.token || login.data?.access_token
log(`登录: ${token ? 'OK' : 'FAIL ' + JSON.stringify(login).slice(0, 200)}`)

// 1) 设备台账不能含密钥
const dev = await api('/ingest/devices')
const list = dev.data || []
log(`设备台账: ${list.length} 台`)
log(`返回字段: ${list[0] ? Object.keys(list[0]).join(', ') : '(空)'}`)
log(`含 secretKey/deviceKey 字段? ${JSON.stringify(list).match(/secret|Key/i) ? '❌ 有泄露' : '✅ 无泄露'}`)

// 2) 演示上报：温度通道（取一条运输中的运单）
const tr = await api('/distribution/transports?status=2&pageNum=1&pageSize=1')
const transport = (tr.data?.records || tr.data?.list || [])[0]
log(`\n运输中运单: ${transport ? transport.transportNo : '(无)'}`)
if (transport) {
  const r = await api('/ingest/demo/report', {
    method: 'POST',
    body: JSON.stringify({ deviceNo: 'TEMP-001', data: { transportNo: transport.transportNo, temperature: 1.8 } }),
  })
  log(`温度演示上报回执: status=${r.data?.status} ${r.data?.statusLabel} accepted=${r.data?.accepted}`)
  log(`  message: ${r.data?.message}`)
  log(`  目标: ${r.data?.targetTable} #${r.data?.targetId}`)
}

// 3) 演示上报：签收通道（待发车 → 发车 → 到达 → 签收）
const pend = await api('/distribution/transports?status=1&pageNum=1&pageSize=1')
const p = (pend.data?.records || pend.data?.list || [])[0]
log(`\n待发车运单: ${p ? p.transportNo : '(无)'}`)
if (p) {
  await api(`/distribution/transports/${p.id}/depart`, { method: 'PUT' })
  await api(`/distribution/transports/${p.id}/arrive`, { method: 'PUT' })
  const stores = await api('/distribution/stores')
  const storeName = (stores.data || [])[0]?.storeName || (stores.data || [])[0]?.store_name || '示范一店'
  const r = await api('/ingest/demo/report', {
    method: 'POST',
    body: JSON.stringify({
      deviceNo: 'PDA-001',
      data: { transportNo: p.transportNo, storeName, receiver: '张三', tempValue: -2, qtyCheck: 1, packageIntact: 1 },
    }),
  })
  log(`签收演示上报回执: status=${r.data?.status} ${r.data?.statusLabel} accepted=${r.data?.accepted}`)
  log(`  message: ${r.data?.message}`)
  log(`  目标: ${r.data?.targetTable} #${r.data?.targetId}`)
}

// 4) 演示上报：幂等（同 bizKey 再推一次）
const bizKey = `VERIFY-${Date.now()}`
const a = await api('/ingest/demo/report', { method: 'POST', body: JSON.stringify({ deviceNo: 'TEMP-001', bizKey, data: { transportNo: transport?.transportNo, temperature: 0.5 } }) })
const b = await api('/ingest/demo/report', { method: 'POST', body: JSON.stringify({ deviceNo: 'TEMP-001', bizKey, data: { transportNo: transport?.transportNo, temperature: 0.5 } }) })
log(`\n幂等: 首次 duplicate=${a.data?.duplicate} status=${a.data?.statusLabel}；重推 duplicate=${b.data?.duplicate} status=${b.data?.statusLabel}`)

console.log(out.join('\n'))
