/**
 * AI 监管简报页面验收：登录 → 工作台 → 点「生成今日简报」→ 抓流式中与完成两张图。
 * 运行：node logs/shot-ai-report.mjs
 */
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const API = `${BASE}/api/v1`
const OUT = import.meta.dirname

const login = await fetch(`${API}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
}).then((r) => r.json())
if (login.code !== 200) throw new Error('登录失败：' + JSON.stringify(login))

const browser = await chromium.launch({ channel: 'msedge', headless: true }).catch(() => chromium.launch({ headless: true }))
const page = await browser.newPage({ viewport: { width: 1500, height: 1000 } })
page.on('console', (m) => { if (m.type() === 'error') console.log('  [console]', m.text().slice(0, 160)) })

await page.goto(`${BASE}/admin/`, { waitUntil: 'domcontentloaded' })
await page.evaluate(([t, u]) => {
  localStorage.setItem('trace_admin_token', t)
  localStorage.setItem('trace_admin_user', JSON.stringify(u))
}, [login.data.token, login.data.user || { nickname: '系统管理员', role: 'ADMIN' }])

await page.goto(`${BASE}/admin/`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2500)

const hasPanel = await page.locator('text=AI 监管简报').first().isVisible().catch(() => false)
console.log('面板可见:', hasPanel)

if (hasPanel) {
  await page.locator('button:has-text("生成今日简报")').first().click()
  await page.waitForTimeout(6000)
  await page.screenshot({ path: `${OUT}/shot-ai-streaming.png`, fullPage: false })
  console.log('已截流式中')

  // 等生成完成（最多 180s），以「已完成」tag 或内容长度稳定为准
  const t0 = Date.now()
  let done = false
  while (Date.now() - t0 < 180000) {
    const tag = await page.locator('.el-tag:has-text("已完成")').first().isVisible().catch(() => false)
    if (tag) { done = true; break }
    await page.waitForTimeout(3000)
  }
  await page.waitForTimeout(1200)
  await page.screenshot({ path: `${OUT}/shot-ai-done.png`, fullPage: false })
  console.log('生成完成:', done)

  const body = await page.locator('.markdown-body').first().innerText().catch(() => '')
  console.log('报告字数:', body.length)
  console.log('含四段标题:', ['总体情况', '异常研判', '处置建议', '需要持续关注'].every((h) => body.includes(h)))
  console.log('表格渲染:', (await page.locator('.md-table').count()) > 0)
}

await browser.close()
