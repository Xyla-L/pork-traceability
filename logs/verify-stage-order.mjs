#!/usr/bin/env node
/**
 * 顺序一致性验证（只读，不写任何业务数据）
 *
 * 校验「侧边栏自上而下 = 业务链」这一口径在三个出口是否一致：
 *   1) /ingest/source-policy        —— 来源策略表格 / 兜底解锁下拉的环节顺序
 *   2) /consumer/safe-buy/{qr}      —— 消费者扫码的「证照」「报告」顺序
 *   3) 追溯页时间轴由前端渲染（见 TraceSearch.vue buildNodeDetails）
 *
 * 用法：node logs/verify-stage-order.mjs [qrCode]
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }
let token = null

// 期望顺序 = TraceStage 枚举声明顺序 = 侧边栏自下而上
const EXPECTED = [
  '养殖建档（耳标）', '免疫注射', '产地检疫出证', '申请出栏', '入场查验', '瘦肉精检测',
  '屠宰检验', '胴体盖章', '胴体批次归集', '分割批次创建', '冷链温度采集', '门店签收', '零售售出',
]

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await response.json() } catch { /* 空响应体 */ }
  return { httpStatus: response.status, ...(body || {}) }
}

const idx = (arr, kw) => arr.findIndex((x) => String(x).includes(kw))
const before = (arr, a, b) => idx(arr, a) >= 0 && idx(arr, b) >= 0 && idx(arr, a) < idx(arr, b)

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('① 登录成功')

  // ---- 1) 来源策略顺序 ----
  const pol = await api('/ingest/source-policy')
  const labels = (pol.data || []).map((p) => p.label)
  const ok1 = JSON.stringify(labels) === JSON.stringify(EXPECTED)
  console.log(`\n② 来源策略环节顺序（${labels.length} 项） ${ok1 ? '✅ 一致' : '❌ 不一致'}`)
  console.log('   实际：' + labels.join(' → '))
  if (!ok1) console.log('   期望：' + EXPECTED.join(' → '))

  // ---- 2) 消费者扫码：证照 / 报告顺序 ----
  let ok2 = true
  let ok3 = true
  const qr = process.argv[2] || process.env.QR
  if (qr) {
    const sb = await api(`/consumer/safe-buy/${qr}`)
    const certs = (sb.data?.certChain || []).map((c) => c.type)
    const reports = (sb.data?.reports || []).map((r) => r.title)
    console.log(`\n③ 消费者扫码 ${qr}`)
    console.log('   证照顺序：' + (certs.join(' → ') || '(空)'))
    console.log('   报告顺序：' + (reports.join(' → ') || '(空)'))
    ok2 = before(certs, '产地检疫', '瘦肉精')
    ok3 = before(reports, '产地检疫', '瘦肉精')
      && before(reports, '瘦肉精', '宰前')
      && before(reports, '宰前', '宰后')
    console.log(`   证照次序（产地检疫 先于 瘦肉精）：${ok2 ? '✅' : '❌'}`)
    console.log(`   报告次序（产地检疫 → 瘦肉精 → 宰前 → 宰后）：${ok3 ? '✅' : '❌'}`)
  } else {
    console.log('\n③ 未提供二维码，跳过追溯证照/报告顺序检查')
  }

  const all = ok1 && ok2 && ok3
  console.log('\n============================')
  console.log(all ? '全部通过 ✅' : '存在不一致 ❌')
  console.log('============================')
  process.exit(all ? 0 : 1)
}

main().catch((e) => {
  console.error('验证异常：', e.message)
  process.exit(1)
})
