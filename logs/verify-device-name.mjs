// 验证设备名中文是否正确（走前端同一条链路：nginx -> gateway -> ingest-service）
const B = 'http://127.0.0.1'

const login = await fetch(B + '/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
})
const token = (await login.json()).data.token
const H = { Authorization: 'Bearer ' + token }

const dev = await fetch(B + '/api/v1/ingest/devices', { headers: H })
const devices = (await dev.json()).data || []
console.log('=== 设备台账（来源设备列的数据源）===')
for (const d of devices) {
  const bad = /Ã|å|æ|ç|è|é/.test(d.deviceName || '')
  console.log(`${d.deviceNo.padEnd(11)} | ${d.channel.padEnd(12)} | ${d.deviceName}${bad ? '   <== 仍疑似乱码' : ''}`)
}

const st = await fetch(B + '/api/v1/ingest/staging?pageNum=1&pageSize=5', { headers: H })
const rows = ((await st.json()).data || {}).records || []
console.log('\n=== 待确认队列「来源设备」列（页面实际显示的字段）===')
for (const r of rows) {
  console.log(`${(r.deviceNo || '').padEnd(11)} | ${r.deviceName || '(空)'} | ${r.statusLabel || ''}`)
}
