/**
 * 人工体验脚本（不进回归）：连问几个不同形态的问题，把完整回答打出来。
 * 用途是看「像不像对话」，不是判断对错——事实仍以 verify-agent 的断言为准。
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

const login = await fetch(BASE + '/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
}).then((r) => r.json())
const token = login?.data?.token || login?.data?.accessToken
if (!token) { console.error('登录失败'); process.exit(1) }

const ASKS = process.argv.slice(2).length ? process.argv.slice(2) : [
  '运单都正常吗，然后 ET-TM-1793245612058 这头猪现在到哪一步了',
  '什么叫降采样',
  '为什么会有这么多待人工处理的记录',
  '这批运单温度超标怎么处理',
  '今天有什么需要优先处理的',
]

let threadId
for (const message of ASKS) {
  const t0 = Date.now()
  const res = await fetch(BASE + '/agent/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ threadId, message }),
  }).then((r) => r.json())
  const d = res?.data
  threadId = d?.threadId
  const secs = ((Date.now() - t0) / 1000).toFixed(1)
  console.log('\n' + '='.repeat(70))
  console.log(`问：${message}`)
  console.log(`来源：${d?.source}  工具：${d?.tool}  耗时：${secs}s  依据 ${d?.facts?.length || 0} 条`)
  console.log('-'.repeat(70))
  console.log(d?.answer || '(空)')
}
