#!/usr/bin/env node
/**
 * 一次性造数：给「门店拒收弹窗」截图用 —— 造一张「已到达、未签收」的单（一站 = retail_user 本店）。
 * 不清理，由调用方截图后跑 cleanup 段（KEEP_ID 打印在末尾）。
 *
 * 运行：node logs/seed-reject-dialog.mjs
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

async function api(path, { method = 'GET', token, body } = {}) {
  const h = { 'Content-Type': 'application/json' }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined })
  return res.json().catch(() => null)
}
const brief = (r) => JSON.stringify(r ?? {}).slice(0, 200)
const pad = (n) => String(n).padStart(2, '0')
const stamp = () => {
  const d = new Date(Date.now() - Math.floor(Math.random() * 3600) * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
let seq = 0
const bizKey = (tag) => `${tag}-${Date.now()}-${++seq}`

const admin = (await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } }))?.data?.token
const retail = (await api('/auth/login', { method: 'POST', body: { username: 'retail_user', password: '123456' } }))?.data
if (!admin || !retail) { console.log('登录失败'); process.exit(1) }
const MY_STORE = Number(retail.user?.orgId)

const pending = (await api('/distribution/splits/pending?pageNum=1&pageSize=5', { token: admin }))?.data?.records || []
if (!pending.length) { console.log('没有待发运批次，先在养殖/屠宰侧造一批'); process.exit(1) }
const batch = pending[0]

// 车牌：优先 T0004，被占用就 T0005
let plate = null
for (const p of ['京A·T0004', '京A·T0005']) {
  const rs = (await api(`/distribution/transports?keyword=${encodeURIComponent(p)}&pageNum=1&pageSize=50`, { token: admin }))?.data?.records || []
  if (!rs.filter((r) => r.status < 4).length) { plate = p; break }
}
if (!plate) { console.log('两辆专用车牌都被占用，稍后再试'); process.exit(1) }

const plan = await api('/distribution/delivery-plans', {
  method: 'POST', token: admin,
  body: {
    planDate: stamp().slice(0, 10), storeId: MY_STORE, storeName: retail.user?.realName || '本店',
    splitBatchIds: [batch.id], vehicleNo: null, vehicleType: null, refrigeration: null,
    driverName: null, driverPhone: null, origin: null, plannedDepart: null, plannedArrive: null,
    remark: '拒收弹窗截图造数',
  },
})
if (!plan?.data?.id) { console.log('建计划失败：', brief(plan)); process.exit(1) }

const load = await api('/ingest/demo/report', {
  method: 'POST', token: admin,
  body: { deviceNo: 'LOADER-001', bizKey: bizKey('LOAD'), data: { loadTime: stamp(), vehicleNo: plate, batchNos: [batch.batchNo] } },
})
if (load?.data?.status !== 2) { console.log('装车建单失败：', brief(load)); process.exit(1) }
const tid = load.data.targetId

await api('/ingest/demo/report', {
  method: 'POST', token: admin,
  body: { deviceNo: 'ARRIVE-001', channel: 'DEPART', bizKey: bizKey('DEPART'), data: { departTime: stamp(), vehicleNo: plate, cardNo: '8801' } },
})
const arr = await api('/ingest/demo/report', {
  method: 'POST', token: admin,
  body: { deviceNo: 'ARRIVE-001', channel: 'ARRIVE', bizKey: bizKey('ARRIVE'), data: { arriveTime: stamp(), vehicleNo: plate, cardNo: '8801' } },
})
if (arr?.data?.status !== 2) { console.log('到达上报失败：', brief(arr)); process.exit(1) }

const t = (await api(`/distribution/transports/${tid}`, { token: admin }))?.data
console.log(`已造单：id=${tid} ${t?.transportNo} status=${t?.status} plate=${plate} plan=${plan.data.id}`)
console.log(`CLEANUP='${tid} ${plan.data.id}'`)
