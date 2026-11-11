#!/usr/bin/env node
/**
 * 探针：车载终端「填充示例值」到底填了什么 / 提示什么（2026-11-05 用户报「无法填充示例值」）
 * 只读页面，不提交任何上报。
 * 用法：node logs/probe-ingest-sample.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = process.env.SHOT_BASE || 'http://localhost'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const errors = []
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 300)) })
page.on('pageerror', (e) => errors.push('pageerror: ' + String(e.message).slice(0, 300)))

await page.addInitScript(() => { Math.random = () => 0 })
await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
await page.waitForTimeout(1200)
await page.fill('input[placeholder="请输入用户名"]', 'admin')
await page.fill('input[type="password"]', '123456')
await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
await page.waitForURL((u) => u.pathname.startsWith('/admin'), { timeout: 20000 })
await page.waitForTimeout(1500)

await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(3000)
await page.locator('button').filter({ hasText: '模拟设备上报' }).first().click()
await page.waitForTimeout(1200)

// 选设备：车载终端
const deviceInput = page.locator('.el-dialog input').first()
await deviceInput.click()
await page.waitForTimeout(600)
const opt = page.locator('.el-select-dropdown__item').filter({ hasText: 'ARRIVE-001' }).first()
const hasOpt = await opt.count()
console.log('设备下拉里有 ARRIVE-001：', hasOpt > 0)
if (hasOpt) { await opt.click(); await page.waitForTimeout(800) }

// 通道下拉（车载终端有两个通道）
const selects = page.locator('.el-dialog .el-select')
const n = await selects.count()
console.log('弹窗内下拉数量：', n)
for (let i = 0; i < n; i++) {
  const text = (await selects.nth(i).innerText().catch(() => '')).replace(/\s+/g, ' ').trim()
  console.log(`  下拉#${i}: ${text}`)
}
if (n > 1) {
  await selects.nth(1).click(); await page.waitForTimeout(600)
  const depart = page.locator('.el-select-dropdown__item:visible').filter({ hasText: '发车确认' }).first()
  if (await depart.count()) { await depart.click(); await page.waitForTimeout(800) }
}

// 点「填充示例值」
await page.locator('button').filter({ hasText: '填充示例值' }).first().click()
await page.waitForTimeout(2500)

const dlg = page.locator('.el-dialog:visible').first()
const dlgText = (await dlg.innerText()).replace(/\n{2,}/g, '\n')
console.log('\n=== 弹窗文本 ===\n' + dlgText)

const inputs = await dlg.locator('input').evaluateAll((els) =>
  els.map((e) => ({ ph: e.getAttribute('placeholder'), val: e.value })).filter((r) => r.val))
console.log('\n=== 已填字段 ===\n' + JSON.stringify(inputs, null, 1))

await page.screenshot({ path: 'logs/page-ingest-depart.png', fullPage: false })
console.log('\n=== 控制台错误 ===\n' + (errors.length ? errors.join('\n') : '（无）'))
await browser.close()
