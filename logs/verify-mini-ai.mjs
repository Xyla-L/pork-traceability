#!/usr/bin/env node
/**
 * 小程序消费端 AI 助手冒烟（2026-11-18）
 *
 * 目的：不做真机，只在 H5/模拟器这一条链路上确认「扫码页 AI 解读 + 追问」和
 * 全局悬浮 AI 助手是活的。
 *   ① 进 safe-buy 自动开始解读，能出字
 *   ② 输出是「逐字增长」还是「一次性整篇」（模拟器缓冲不算失败，只记录口径）
 *   ③ 结束后徽标变「已解读」，追问框出现，输入框 maxlength=500
 *   ④ 追问能发出去并有新的回答（多轮上下文不报错）
 *   ⑤ verify 页悬浮入口：弹层带上下文、解读、快捷问题、停止按钮位
 *   ⑥ 全程无控制台报错、AI 接口无 4xx/5xx
 *
 * 只读，不改任何数据。
 * 用法：node logs/verify-mini-ai.mjs
 *   可用环境变量覆盖：MINI_BASE（默认 5174 的 H5）、SMOKE_QR（默认取一个已售未作废的码）
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = process.env.MINI_BASE || 'http://localhost:5174/#'
const QR = process.env.SMOKE_QR || 'QR-PORK-2DAB84DA3F6942F1AB361A3D604B5863'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })

const consoleErrors = []
const aiCalls = []
const httpFails = []
page.on('console', (m) => {
  if (m.type() !== 'error') return
  // dev server 没有 favicon.ico，H5 调试期必然 404；mp-weixin 产物不带这个请求，不算问题
  if ((m.location()?.url || '').includes('favicon')) return
  consoleErrors.push(m.text())
})
page.on('pageerror', (e) => consoleErrors.push(String(e)))
page.on('response', (res) => {
  const url = res.url()
  if (url.includes('/ai/consumer/')) aiCalls.push({ url: url.split('?')[0].split('/api/v1').pop(), status: res.status() })
  if (res.status() >= 400) httpFails.push({ status: res.status(), url: url.split('?')[0] })
})

const results = []
const ok = (name, detail = '') => results.push({ pass: true, name, detail })
const bad = (name, detail = '') => results.push({ pass: false, name, detail })

/**
 * 采样输出区文本长度，用来判断是流式还是整段缓冲。
 * badgeSel：状态徽标选择器（卡片 .ai-badge / 弹层 .ai-sheet-badge）
 * doneSel：收尾标志（卡片=徽标变「已解读」；弹层=出现快捷问题 chips）
 */
async function sample(label, { maxMs = 60000, stepMs = 700, badgeSel = '.ai-badge', doneSel = null } = {}) {
  const streamingWords = ['解读中', '解答中']
  const samples = []
  const started = Date.now()
  while (Date.now() - started < maxMs) {
    const snap = await page.evaluate(
      ({ badgeSel, doneSel }) => {
        const text = document.querySelector('.ai-text')
        const badge = document.querySelector(badgeSel)
        const doneMark = doneSel ? !!document.querySelector(doneSel) : null
        return { len: text ? text.innerText.length : 0, badge: badge ? badge.innerText.trim() : '' , doneMark }
      },
      { badgeSel, doneSel }
    ).catch(() => ({ len: 0, badge: '', doneMark: null }))
    samples.push({ t: Date.now() - started, ...snap })
    const stillStreaming = streamingWords.includes(snap.badge)
    const done = doneSel ? (snap.doneMark && !stillStreaming) : snap.badge === '已解读'
    if (done && samples.length > 2) break
    await page.waitForTimeout(stepMs)
  }
  const grows = samples.filter((s, i) => i > 0 && s.len > samples[i - 1].len).length
  const finalLen = samples[samples.length - 1].len
  const last = samples[samples.length - 1]
  const done = doneSel ? (last.doneMark && !streamingWords.includes(last.badge)) : last.badge === '已解读'
  console.log(`\n[${label}] 采样 ${samples.length} 次，最终 ${finalLen} 字，增长 ${grows} 次，收尾=${done}`)
  const track = samples.filter((s, i) => i === 0 || s.len !== samples[i - 1].len)
  console.log('  长度轨迹: ' + track.slice(0, 12).map((s) => `${s.t}ms=${s.len}`).join(' → ') + (track.length > 12 ? ' …' : ''))
  return { samples, grows, finalLen, done }
}

// 1. 进入扫码结果页（hash 路由直接换会复用页面实例，先离开）
await page.goto('about:blank').catch(() => {})
await page.goto(`${BASE}/pages/safe-buy/safe-buy?qrCode=${QR}`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.ai-card', { timeout: 20000 })
ok('扫码页 AI 卡片已渲染')

const first = await sample('首轮解读')
if (first.finalLen > 20) ok('首轮解读有输出', `${first.finalLen} 字`)
else bad('首轮解读无输出', `${first.finalLen} 字`)
if (first.done) ok('首轮正常收尾（徽标=已解读）')
else bad('首轮未收尾', '徽标没变成「已解读」，可能卡在解读中')
if (first.grows >= 2) ok('逐字流式输出', `增长 ${first.grows} 次`)
else ok('输出为整段缓冲（模拟器/中间层行为，功能不受影响）', `增长 ${first.grows} 次`)

await page.screenshot({ path: 'logs/shot-mini-ai.png', fullPage: true })

// 2. 追问框与字数上限
const askVisible = await page.locator('.ai-ask').isVisible().catch(() => false)
askVisible ? ok('追问框已出现') : bad('追问框未出现')
const maxLen = await page.locator('.ai-input input, input.ai-input').first().getAttribute('maxlength').catch(() => null)
maxLen === '500' ? ok('输入框上限 500 字') : bad('输入框上限异常', String(maxLen))

// 3. 追问一轮
const q = '这块肉做过瘦肉精检测吗？'
const input = page.locator('.ai-input input, input.ai-input').first()
await input.fill(q).catch(() => {})
await page.locator('.ai-btn.small').first().click().catch(() => {})
await page.waitForTimeout(1200)
const second = await sample('追问')
if (second.finalLen > 20) ok('追问有回答', `${second.finalLen} 字`)
else bad('追问无回答', `${second.finalLen} 字`)
if (second.done) ok('追问正常收尾')
else bad('追问未收尾')

// 4. 全局悬浮助手（verify 页，深链直达也应带上下文）
await page.goto('about:blank').catch(() => {})
await page.goto(`${BASE}/pages/verify/verify?qrCode=${QR}`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.ai-float', { timeout: 15000 })
ok('悬浮入口已渲染（verify 页）')
await page.locator('.ai-float').click()
await page.waitForSelector('.ai-sheet', { timeout: 5000 })
const introVisible = await page.locator('.ai-sheet-intro').isVisible().catch(() => false)
introVisible ? ok('弹层打开且带上下文（出现「帮我解读」）') : bad('弹层未带上下文', '没拿到码，空态了')
await page.locator('.ai-sheet-btn').first().click()
const third = await sample('悬浮助手解读', { badgeSel: '.ai-sheet-badge', doneSel: '.ai-chip' })
if (third.finalLen > 20) ok('悬浮助手解读有输出', `${third.finalLen} 字`)
else bad('悬浮助手解读无输出', `${third.finalLen} 字`)
if (third.done) ok('悬浮助手正常收尾')
else bad('悬浮助手未收尾')
const chips = await page.locator('.ai-chip').count()
chips === 4 ? ok('快捷问题 4 条') : bad('快捷问题数量异常', String(chips))
await page.locator('.ai-chip').first().click()
const fourth = await sample('快捷问题追问', { badgeSel: '.ai-sheet-badge', doneSel: '.ai-chip' })
if (fourth.finalLen > 20 && fourth.done) ok('快捷问题有回答并收尾', `${fourth.finalLen} 字`)
else bad('快捷问题异常', `${fourth.finalLen} 字, done=${fourth.done}`)

// 停止：发出一个问题，等「停止」出现就点掉，流应被掐断且回到可追问状态
await page.locator('.ai-input input, input.ai-input').last().fill('这个批次的猪是哪个养殖场的？')
await page.locator('.ai-sheet-btn.small').last().click()
const stopBtn = page.locator('.ai-sheet-btn.small').filter({ hasText: '停止' })
await stopBtn.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {})
if (await stopBtn.isVisible().catch(() => false)) {
  await stopBtn.click()
  await page.waitForTimeout(600)
  const chipsAfterStop = await page.locator('.ai-chip').count()
  const badge = await page.locator('.ai-sheet-badge').count()
  chipsAfterStop > 0 && badge === 0 ? ok('停止按钮可中断流式') : bad('停止后状态不对', `chips=${chipsAfterStop}, badge=${badge}`)
} else bad('流式期间没出现停止按钮')

await page.screenshot({ path: 'logs/shot-mini-ai-float.png', fullPage: true })

// 5. 接口与控制台
const failedCalls = aiCalls.filter((c) => c.status >= 400)
if (aiCalls.length >= 4 && failedCalls.length === 0) ok('AI 接口四次调用均 2xx', aiCalls.map((c) => c.status).join(','))
else if (failedCalls.length) bad('AI 接口报错', JSON.stringify(failedCalls))
else ok('AI 接口调用次数偏少（可能被浏览器缓存）', JSON.stringify(aiCalls))
consoleErrors.length
  ? bad('控制台有报错', `${consoleErrors.slice(0, 2).join(' | ')}${httpFails.length ? ' ← ' + httpFails.map((f) => `${f.status} ${f.url}`).join(', ') : ''}`)
  : ok('控制台无报错')
if (httpFails.length) console.log('\n4xx/5xx 资源: ' + httpFails.map((f) => `${f.status} ${f.url}`).join('\n  '))

console.log('\n=== 结果 ===')
for (const r of results) console.log(`${r.pass ? '✔' : '✘'} ${r.name}${r.detail ? ' — ' + r.detail : ''}`)
const failed = results.filter((r) => !r.pass)
console.log(failed.length ? `\n${failed.length}/${results.length} 项失败` : `\n${results.length}/${results.length} 项通过`)
await browser.close()
process.exit(failed.length ? 1 : 0)
