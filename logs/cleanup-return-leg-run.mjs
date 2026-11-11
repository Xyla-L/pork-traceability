#!/usr/bin/env node
/** 清理 verify-return-leg 失败轮的残留（只删自己造的：按业务键定位） */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
async function api(path, { method = 'GET', token, body } = {}) {
  const h = { 'Content-Type': 'application/json' }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined })
  return res.json().catch(() => null)
}
const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const admin = login?.data?.token

// 1) 本轮回运回归建的配送单（备注带「回运回归-配送」，或车牌 T0004 且 status<4）
const rows = (await api('/distribution/transports?keyword=%E4%BA%ACA%C2%B7T0004&pageNum=1&pageSize=50', { token: admin }))?.data?.records
  || (await api(`/distribution/transports?keyword=${encodeURIComponent('京A·T0004')}&pageNum=1&pageSize=50`, { token: admin }))?.data?.records || []
console.log('T0004 运单：', rows.map(r => `${r.id}/${r.transportNo}/${r.status}`).join(','))
for (const r of rows.filter(r => r.status < 4)) {
  // 先删该单全部签收，再删运单
  const stops = (await api(`/distribution/transports/${r.id}/stops`, { token: admin }))?.data || []
  for (const s of stops.filter(s => s.receiptId)) {
    console.log('删签收', s.receiptId, (await api(`/distribution/receipts/${s.receiptId}`, { method: 'DELETE', token: admin }))?.code)
  }
  console.log('删运单', r.id, (await api(`/distribution/transports/${r.id}`, { method: 'DELETE', token: admin }))?.code)
}
// 2) 取消回归计划（备注 回运回归-）
const plans = (await api('/distribution/delivery-plans?pageNum=1&pageSize=50&keyword=' + encodeURIComponent('回运回归'), { token: admin }))?.data?.records || []
console.log('回归计划：', plans.map(p => `${p.id}/${p.status}`).join(','))
for (const p of plans.filter(p => p.status !== 3)) {
  console.log('取消计划', p.id, (await api(`/distribution/delivery-plans/${p.id}/cancel`, { method: 'POST', token: admin, body: { reason: '回运回归清理' } }))?.code)
}
const after = (await api('/distribution/splits/pending?pageNum=1&pageSize=100', { token: admin }))?.data?.records || []
console.log('待发运批次：', after.length)
