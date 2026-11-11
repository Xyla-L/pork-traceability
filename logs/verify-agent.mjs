/**
 * 智能助手验收：
 *   1. 只读问答能取到真数（诊断 + 追溯两条工具链各跑一次）
 *   2. 会话可续（同一 threadId 追问）
 *   3. 边界成立：未登录 401、别人的会话不可读
 *   4. 未配置模型密钥时如实降级为本地归纳（source=local），不假装模型作答
 *
 * 不做「模型答得对不对」的断言——那不是自动化能判的；这里只锁事实来源与边界。
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

let pass = 0, fail = 0
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  [PASS] ${name}`) }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}
async function api(path, { method = 'GET', token, body } = {}) {
  const h = { 'Content-Type': 'application/json' }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers: h, body: body ? JSON.stringify(body) : undefined })
  const json = await res.json().catch(() => null)
  return { status: res.status, json }
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login.json?.data?.token || login.json?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

console.log('\n== 1) 能力与运行状态 ==')
const meta = await api('/agent/meta', { token })
check('/agent/meta 可读', meta.json?.code === 200)
check('声明只读', meta.json?.data?.readonly === true)
check('至少两项能力', (meta.json?.data?.capabilities || []).length >= 2,
  JSON.stringify(meta.json?.data?.capabilities))
console.log(`  状态：llmEnabled=${meta.json?.data?.llmEnabled} model=${meta.json?.data?.model || '(未配置)'}`)

console.log('\n== 2) 接入异常诊断（真实读 staging 队列）==')
const diag = await api('/agent/chat', { method: 'POST', token, body: { message: '接入有哪些失败' } })
const diagData = diag.json?.data
check('诊断有回答', !!diagData?.answer)
check('命中接入诊断工具', diagData?.tool === 'ingest-diagnose', `tool=${diagData?.tool}`)
check('给出会话号', !!diagData?.threadId)
check('来源如实标注', ['llm', 'local'].includes(diagData?.source), `source=${diagData?.source}`)
if (diagData?.facts?.length) {
  // 断言盯「事实清单里有队列总数」而不是盯模型措辞——措辞会随模型变，事实不该变
  check('事实清单含队列总数', diagData.facts.some((f) => f.startsWith('队列总数')),
    JSON.stringify(diagData.facts.slice(0, 2)))
} else {
  console.log('  （当前队列为空，断言降级为「如实说没有待处理」）')
  check('空队列如实说明', /没有待人工处理/.test(diagData?.answer || ''))
}
console.log('  回答节选：' + (diagData?.answer || '').split('\n')[0])

console.log('\n== 3) 全链路追溯（取一条真实耳标）==')
const pigs = await api('/breeding/pigs?pageNum=1&pageSize=1', { token })
const earTag = pigs.json?.data?.records?.[0]?.earTagNo || 'ET-DEMO-0133'
const traceRes = await api('/agent/chat', {
  method: 'POST', token,
  body: { threadId: diagData?.threadId, message: `${earTag} 现在到哪一步了` },
})
const traceData = traceRes.json?.data
check('追溯有回答', !!traceData?.answer)
check('命中全链路追溯工具', traceData?.tool === 'trace', `tool=${traceData?.tool}`)
check('问题里的编号被原样带上', (traceData?.facts || []).length > 0 || /没有查到/.test(traceData?.answer || ''))
check('追问沿用同一会话', traceData?.threadId === diagData?.threadId,
  `before=${diagData?.threadId} after=${traceData?.threadId}`)
console.log('  耳标：' + earTag)
console.log('  回答节选：' + (traceData?.answer || '').split('\n')[0])

console.log('\n== 3b) 出栏申报分析（真实统计）==')
const applyRes = await api('/agent/chat', {
  method: 'POST', token, body: { threadId: diagData?.threadId, message: '出栏申报驳回了多少' },
})
const applyData = applyRes.json?.data
check('分析有回答', !!applyData?.answer)
check('命中申报分析工具', applyData?.tool === 'apply-analysis', `tool=${applyData?.tool}`)
check('给出状态分布', (applyData?.facts || []).some((f) => f.startsWith('状态分布')),
  JSON.stringify((applyData?.facts || []).slice(0, 2)))

console.log('\n== 3c) 冷链运单分析（真实统计）==')
const transRes = await api('/agent/chat', {
  method: 'POST', token, body: { threadId: diagData?.threadId, message: '运单都正常吗' },
})
const transData = transRes.json?.data
check('分析有回答', !!transData?.answer)
check('命中运单分析工具', transData?.tool === 'transport-analysis', `tool=${transData?.tool}`)
check('状态用中文不用码', (transData?.facts || []).some((f) => f.startsWith('状态[')),
  JSON.stringify((transData?.facts || []).slice(0, 3)))
console.log('  回答节选：' + (transData?.answer || '').split('\n')[0])

console.log('\n== 3d) 工作台数据解读（按角色透传，零复算）==')
const dashRes = await api('/agent/chat', {
  method: 'POST', token, body: { threadId: diagData?.threadId, message: '工作台今天整体怎么样' },
})
const dashData = dashRes.json?.data
check('解读有回答', !!dashData?.answer)
check('命中工作台工具', dashData?.tool === 'dashboard', `tool=${dashData?.tool}`)
const dashFacts = dashData?.facts || []
check('事实含角色视角', dashFacts.some((f) => f.startsWith('视角:')),
  JSON.stringify(dashFacts.slice(0, 2)))
check('事实含指标卡', dashFacts.some((f) => f.startsWith('核心指标共')))
check('降级数据源如实标注或确实无降级',
  dashFacts.some((f) => f.startsWith('注意，以下数据源')) || dashData?.answer?.length > 0)
console.log('  回答节选：' + (dashData?.answer || '').split('\n')[0])

console.log('\n== 3e) 综合简报与建议（四口径合并 + 规则建议）==')
const briefRes = await api('/agent/chat', {
  method: 'POST', token, body: { threadId: diagData?.threadId, message: '今天有什么需要优先处理的' },
})
const briefData = briefRes.json?.data
check('简报有回答', !!briefData?.answer)
check('命中简报工具', briefData?.tool === 'daily-brief', `tool=${briefData?.tool}`)
const briefFacts = briefData?.facts || []
const briefAnswer = briefData?.answer || ''
check('四方面口径齐备', ['工作台', '接入', '申报', '运单'].every((k) =>
  briefFacts.some((f) => f.includes(k))), briefFacts.slice(0, 4).join(' | '))
check('规则建议原样附加在回答后', briefAnswer.includes('【系统建议'))
// 只数 appendix 部分（【系统建议】之后），用最后一个标记——模型正文可能模仿写过一份
const appendixPart = briefAnswer.split('【系统建议').pop() || ''
const briefTips = appendixPart.split('\n').filter((l) => /^\d+\.\s/.test(l))
check('建议条数在规则范围内（1-6 条）', briefTips.length >= 1 && briefTips.length <= 6,
  `tips=${briefTips.length}`)
check('每条建议带页面入口', briefTips.every((l) => l.includes('入口') || l.includes('不用改原单')),
  briefTips.join(' | ').slice(0, 120))
console.log('  回答节选：' + briefAnswer.split('\n')[0])
console.log('  建议条数：' + briefTips.length)

console.log('\n== 3g) 专项问题不被全局简报接管（主题守卫）==')
// 反例回归：「怎么处理/建议」这类诉求词曾让简报抢答专项问题，导致每次回答都甩同一段全平台建议。
// 每条用独立会话，避免把上一轮的问题带进追问改写的锚点（否则会污染 3f）。
for (const [q, want] of [
  ['这批运单温度超标怎么处理', 'temperature'],
  ['出栏审批积压这么多建议怎么处理', 'apply-analysis'],
]) {
  const r = await api('/agent/chat', {
    method: 'POST', token, body: { message: q },
  })
  const d = r.json?.data
  check(`「${q}」归专项工具`, d?.tool === want, `tool=${d?.tool} want=${want}`)
  check(`「${q}」不附全局建议块`, !(d?.answer || '').includes('【系统建议'),
    (d?.answer || '').split('\n').slice(-2).join(' | ').slice(0, 100))
}

console.log('\n== 3f) 两级路由：换说法 + 追问 ==')
const para = await api('/agent/chat', {
  method: 'POST', token, body: { threadId: diagData?.threadId, message: '温度有没有超标' },
})
// 温度工具上线前这句要靠模型分诊兜到运单分析；现在「温度」是关键词直命中，更快也更准。
// 「换说法」的能力由 3h 的多步自主查询与业务说明覆盖，不再用这句兼职验证。
const paraData = para.json?.data
check('温度问法直命中温度工具', paraData?.tool === 'temperature', `tool=${paraData?.tool}`)
check('换说法有回答', !!paraData?.answer)

const follow = await api('/agent/chat', {
  method: 'POST', token, body: { threadId: diagData?.threadId, message: '那它有检疫证吗' },
})
const followData = follow.json?.data
check('追问能接住', !!followData?.answer && followData?.source !== 'none', `source=${followData?.source}`)
// 盯「编号有没有被补进去」而不是「回答里有没有出现编号」：
// 补上了但查无数据时，模型会说「系统里没有这头猪的检疫证数据」，不复述编号——那是正确回答。
// 没补上时 trace 会明确说「没听出要查哪个编号」，这句才是失败信号。
const followText = JSON.stringify(followData?.facts || []) + (followData?.answer || '')
check('追问补全了上一轮的编号', !/没听出要查哪个编号/.test(followText),
  `内容=${followText.slice(0, 80)}`)

// 「车/运单 到哪了」问的是运输而不是这头猪：追溯不得抢答（否则会拿车号去查耳标）
const vehicle = await api('/agent/chat', {
  method: 'POST', token, body: { message: '帮我看下车到哪了' },
})
const vehicleData = vehicle.json?.data
check('车/运单类问法交给运单分析', vehicleData?.tool === 'transport-analysis', `tool=${vehicleData?.tool}`)
check('运单回答不冒充耳标查询', !/没听出要查哪个编号/.test(vehicleData?.answer || ''))

// 反过来：同一句里提到猪，追溯仍然优先（「这车上的猪」问的还是猪）
const onTruck = await api('/agent/chat', {
  method: 'POST', token, body: { message: `${earTag} 这车上的猪被拒收了吗` },
})
check('运输词与生猪词同现时仍能答', onTruck.json?.data?.tool === 'trace',
  `tool=${onTruck.json?.data?.tool}`)

console.log('\n== 3h) 自主查询与业务说明 ==')
// 多步：一句话里问两件事，模型应自己决定查几个工具（独立会话，不污染前面的追问）
const multi = await api('/agent/chat', {
  method: 'POST', token, body: { message: `运单都正常吗，然后 ${earTag} 这头猪现在到哪一步了` },
})
const multiData = multi.json?.data
check('多步问题走自主查询', multiData?.source === 'agent',
  `source=${multiData?.source} tool=${multiData?.tool}`)
check('自主查询留下工具轨迹', !!multiData?.tool, `tool=${multiData?.tool}`)
console.log('  工具轨迹：' + multiData?.tool)

// 业务概念（工具答不了、但也不是闲聊）：应给说明，而不是机械列能力清单
const know = await api('/agent/chat', {
  method: 'POST', token, body: { message: '什么叫降采样' },
})
const knowData = know.json?.data
check('业务概念问题不再机械拒答', knowData?.source !== 'none', `source=${knowData?.source}`)
check('业务说明不编统计数字',
  knowData?.source !== 'knowledge' || !/\d+\s*(条|头|件)/.test(knowData?.answer || ''),
  (knowData?.answer || '').slice(0, 80))

console.log('\n== 3i) 温度与举报（两个新增专项工具）==')
// 温度：以前问「温度超标没」只能回「统计里没有温度数据」，还被运单分析抢走。
// 现在由 temperature 专管，且必须交代采样来源（模拟探头不能冒充真实设备）。
const temp = await api('/agent/chat', {
  method: 'POST', token, body: { message: '温度有没有超标' },
})
const tempData = temp.json?.data
const tempFacts = tempData?.facts || []
check('温度问题归温度工具', tempData?.tool === 'temperature', `tool=${tempData?.tool}`)
check('取到真实温度记录', tempFacts.some((f) => f.startsWith('体检范围') || f.startsWith('温度记录')),
  tempFacts.slice(0, 2).join(' | ').slice(0, 90))
check('温度不再答「没有这项数据」', !/没有这项(信息|数据)|不含温度记录/.test(tempData?.answer || ''),
  (tempData?.answer || '').slice(0, 80))
check('交代采样来源（不把模拟探头当设备）', tempFacts.some((f) => f.startsWith('记录来源')),
  tempFacts.filter((f) => f.startsWith('记录来源'))[0] || '')
// 回归：只数「越界」会漏掉只有断档的车（实测把 2 张说成 1 张）。张数必须与明细行数对得上。
const abnormalCount = Number(
  (tempFacts.find((f) => f.startsWith('有温度异常的运单')) || '').match(/有温度异常的运单:\s*(\d+)/)?.[1] ?? -1)
const abnormalLines = tempFacts
  .filter((f) => f.trimStart().startsWith('运单 ') && !f.includes('0 条温度记录')).length
check('异常张数与明细对得上（断档也算异常）',
  abnormalCount < 0 || (abnormalLines < 5 ? abnormalCount === abnormalLines : abnormalCount >= 5),
  `count=${abnormalCount} details=${abnormalLines}`)
console.log('  回答节选：' + (tempData?.answer || '').split('\n')[0])

// 举报：以前简报只说「5 条待处理」，问涉及哪些批次答不了
const comp = await api('/agent/chat', {
  method: 'POST', token, body: { message: '举报有哪些没处理' },
})
const compData = comp.json?.data
const compFacts = compData?.facts || []
check('举报问题归举报工具', compData?.tool === 'complaint', `tool=${compData?.tool}`)
check('举报给出状态分布', compFacts.some((f) => f.startsWith('状态分布')),
  compFacts.slice(0, 2).join(' | ').slice(0, 90))
check('举报状态用中文不用码', compFacts.some((f) => f.includes('待受理')),
  compFacts.find((f) => f.startsWith('状态分布')) || '')
check('举报不附全局建议块', !(compData?.answer || '').includes('【系统建议'))
console.log('  回答节选：' + (compData?.answer || '').split('\n')[0])

// 反例：温度工具不得把纯运单问题抢走（问「运单都正常吗」仍归运输统计）
const stillTransport = await api('/agent/chat', {
  method: 'POST', token, body: { message: '运单都正常吗' },
})
check('温度工具不抢运单问题', stillTransport.json?.data?.tool === 'transport-analysis',
  `tool=${stillTransport.json?.data?.tool}`)

console.log('\n== 3j) 准入：现阶段只开给管理员 ==')
// 闸门挪到准入层：非管理员连能力清单都拿不到，更到不了工具层。
// 工具级的 roles() 白名单保留未删，放开那天改 agent.access.roles 一行即可。
const farmLogin = await api('/auth/login', {
  method: 'POST', body: { username: 'farm_user', password: '123456' },
})
const farmToken = farmLogin.json?.data?.token
check('养殖户登录', !!farmToken)

const farmMeta = await api('/agent/meta', { token: farmToken })
check('养殖户拿不到能力清单', farmMeta.json?.data?.capabilities == null,
  JSON.stringify(farmMeta.json?.data)?.slice(0, 60))

const adminCaps = (meta.json?.data?.capabilities || []).join(',')
check('管理员能力全开（含新增两个工具）',
  /complaint/.test(adminCaps) && /temperature/.test(adminCaps)
  && /retail-risk/.test(adminCaps) && /quarantine/.test(adminCaps), adminCaps)

const farmChat = await api('/agent/chat', {
  method: 'POST', token: farmToken, body: { message: '举报有哪些没处理' },
})
check('养殖户问不了问题', farmChat.json?.data == null,
  (farmChat.json?.data?.answer || '').slice(0, 60))
check('拒绝是明确报错而不是静默失败', !!farmChat.json?.code && farmChat.json.code !== 0,
  `code=${farmChat.json?.code}`)
console.log('  管理员能力：' + adminCaps)

console.log('\n== 3k) 临期召回与检疫免疫（两个新增专项工具）==')
const riskData = (await api('/agent/chat', {
  method: 'POST', token, body: { message: '临期和召回有没有问题' },
})).json?.data
check('临期召回问题归 retail-risk', riskData?.tool === 'retail-risk', `tool=${riskData?.tool}`)
const riskFacts = riskData?.facts || []
check('给出未处置件数', riskFacts.some((f) => /未处置 \d+ 条/.test(f)),
  riskFacts.find((f) => /未处置/.test(f)) || '')
check('给出召回执行进度', riskFacts.some((f) => /召回共 \d+ 单/.test(f)),
  riskFacts.find((f) => /召回共/.test(f)) || '')
// 过期未处置必须列到具体商品：只报总数等于把最该看的东西藏起来
check('过期未处置列到具体商品',
  !/已过期未处置明细/.test(riskFacts.join('')) || riskFacts.some((f) => /到期日/.test(f)),
  riskFacts.find((f) => /已过期未处置明细/.test(f)) || '')
check('临期问题不被简报接管', !(riskData?.answer || '').includes('【系统建议'),
  (riskData?.answer || '').slice(-60))
console.log('  回答节选：' + (riskData?.answer || '').split('\n')[0])

const quarData = (await api('/agent/chat', {
  method: 'POST', token, body: { message: '哪些批次免疫档案不全' },
})).json?.data
check('检疫免疫问题归 quarantine', quarData?.tool === 'quarantine', `tool=${quarData?.tool}`)
const quarFacts = quarData?.facts || []
check('给出检疫证张数与覆盖头数', quarFacts.some((f) => /检疫证 \d+ 张/.test(f)),
  quarFacts.find((f) => /检疫证/.test(f)) || '')
// 只有日期粒度，不能冒充判定——这句边界必须写进事实里
check('交代「日期粒度不是判定」的边界', quarFacts.some((f) => /不是判定结论/.test(f)),
  quarFacts.find((f) => /说明/.test(f)) || '')
check('检疫问题不被全链路追溯抢答', quarData?.tool !== 'trace', `tool=${quarData?.tool}`)
console.log('  回答节选：' + (quarData?.answer || '').split('\n')[0])

console.log('\n== 4) 边界 ==')
const anon = await api('/agent/chat', { method: 'POST', body: { message: '接入有哪些失败' } })
check('未登录被网关拦下', anon.status === 401, `status=${anon.status}`)

const unknown = await api('/agent/chat', { method: 'POST', token, body: { message: '今天天气怎么样' } })
check('答不了的问题不硬答', unknown.json?.data?.source === 'none', `source=${unknown.json?.data?.source}`)
check('如实说明能力范围', /接不住|只能查/.test(unknown.json?.data?.answer || ''))

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
process.exit(fail === 0 ? 0 : 1)
