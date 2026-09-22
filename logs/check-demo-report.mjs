// 验证管理端「模拟设备上报」入口（等同于前端 IngestMonitor 页面的按钮行为）
const B = 'http://127.0.0.1'

const login = await fetch(B + '/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
})
const token = (await login.json()).data.token
const H = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }

// 温度通道推一个异常温度：异常值永不降采样，便于观察入账效果
const payload = {
  deviceNo: 'TEMP-001',
  bizKey: 'DEMO-' + Date.now(),
  sourceRef: 'UI-DEMO',
  data: { transportNo: 'TR-TEST-0008', temperature: 6.6, recorder: 'demo-probe' },
}
const res = await fetch(B + '/api/v1/ingest/demo/report', {
  method: 'POST',
  headers: H,
  body: JSON.stringify(payload),
})
const out = await res.json()
console.log('demo/report -> HTTP', res.status, 'code=' + out.code)
console.log(JSON.stringify(out.data, null, 2))

const q = await fetch(B + '/api/v1/ingest/staging?pageNum=1&pageSize=1', { headers: H })
const qj = await q.json()
const first = (qj.data && qj.data.records && qj.data.records[0]) || {}
console.log('queue total=' + (qj.data && qj.data.total), '| latest:', first.channel, first.statusLabel || '')
