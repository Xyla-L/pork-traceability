// 逐通道实测「模拟设备上报」，确认开箱可用性
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
const paged = (res) => res.data?.records || res.data?.list || []

const login = await api('/auth/login', { method: 'POST', body: JSON.stringify({ username: 'admin', password: '123456' }) })
token = login.data?.accessToken || login.data?.token || login.data?.access_token

async function demo(deviceNo, data) {
  const r = await api('/ingest/demo/report', { method: 'POST', body: JSON.stringify({ deviceNo, data }) })
  log(`  → status=${r.data?.status} ${r.data?.statusLabel} accepted=${r.data?.accepted}`)
  log(`    ${r.data?.message}${r.data?.targetId ? `（${r.data?.targetTable} #${r.data?.targetId}）` : ''}`)
  return r
}

// 1) TEMPERATURE
log('[1] TEMPERATURE / TEMP-001')
const tr = paged(await api('/distribution/transports?status=2&pageNum=1&pageSize=1'))[0]
log(`  运单: ${tr?.transportNo || '(无运输中运单)'}`)
if (tr) await demo('TEMP-001', { transportNo: tr.transportNo, temperature: 3.2 })

// 2) ENTRY
log('\n[2] ENTRY / RFID-001')
const pig = paged(await api('/breeding/pigs?current=1&size=1'))[0]
log(`  耳标: ${pig?.earTagNo || '(无生猪)'}`)
if (pig) await demo('RFID-001', { earTagNo: pig.earTagNo, batchNo: `B-VERIFY-${Date.now()}`, weight: 110.5, healthCheck: 1 })

// 3) RACTOPAMINE
log('\n[3] RACTOPAMINE / READER-001')
if (pig) await demo('READER-001', { earTagNo: pig.earTagNo, batchNo: `B-VERIFY-${Date.now()}`, sampleNo: `S-V-${Date.now()}`, result: 1 })

// 4) SALE
log('\n[4] SALE / POS-001')
const qr = paged(await api('/sales/qrcodes?pageNum=1&pageSize=1'))[0]
log(`  商品码: ${qr?.productQrCode || '(无)'}`)
if (qr?.productQrCode) await demo('POS-001', { qrCode: qr.productQrCode, sellPrice: 38.5, sellWeightKg: 0.75 })

// 5) RECEIPT
log('\n[5] RECEIPT / PDA-001')
const pend = paged(await api('/distribution/transports?status=1&pageNum=1&pageSize=10'))
const receipts = paged(await api('/distribution/receipts?pageNum=1&pageSize=200'))
const signed = new Set(receipts.map((r) => r.transportId))
const usable = pend.find((p) => !signed.has(p.id))
log(`  待发车 ${pend.length} 条，其中未签收 ${pend.filter((p) => !signed.has(p.id)).length} 条`)
if (usable) {
  await api(`/distribution/transports/${usable.id}/depart`, { method: 'PUT' })
  await api(`/distribution/transports/${usable.id}/arrive`, { method: 'PUT' })
  const storeName = paged(await api('/distribution/stores'))[0]?.storeName || '示范一店'
  await demo('PDA-001', { transportNo: usable.transportNo, storeName, receiver: '张三', tempValue: -2, qtyCheck: 1, packageIntact: 1 })
} else {
  log('  ⚠ 没有「待发车且未签收」的运单，签收通道需要先新建一条运单')
}

console.log(out.join('\n'))
