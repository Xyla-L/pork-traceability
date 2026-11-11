#!/usr/bin/env node
/**
 * 提示横幅清理验收（2026-11-03）：
 *   门店签收页「拒收」弹窗里不应再出现说明横幅（拒收是正式的收货决定…）。
 *   弹窗打不开（没有可拒收的运单）时如实跳过，不伪造结论。
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = 'http://localhost:5173'
const OUT = 'logs/page-reject-dialog.png'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.addInitScript(() => { Math.random = () => 0 })
await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
await page.waitForTimeout(1200)
await page.fill('input[placeholder="请输入用户名"]', 'retail_user')
await page.fill('input[type="password"]', '123456')
await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
await page.waitForURL((u) => u.pathname.startsWith('/admin'), { timeout: 15000 })
await page.goto(`${BASE}/admin/distribution/receipt`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(2500)

let pass = 0, fail = 0
const check = (c, n, d = '') => { c ? pass++ : fail++; console.log(`  ${c ? '✔' : '✘'} ${n}${d ? ' — ' + d : ''}`) }

const rejectBtn = page.locator('button').filter({ hasText: /^拒\s*收$/ }).first()
if (await rejectBtn.count()) {
  await rejectBtn.click()
  await page.waitForTimeout(1200)
  const dialog = page.locator('.el-dialog').filter({ hasText: '门店拒收' }).first()
  const text = await dialog.textContent().catch(() => '')
  check(!!text, '门店拒收弹窗已打开')
  check(!text.includes('拒收是正式的收货决定'), '拒收弹窗：说明横幅已删除')
  check(!(await dialog.locator('.el-alert').count()), '拒收弹窗：没有任何 alert 横幅')
  check(text.includes('拒收理由'), '拒收弹窗：理由输入仍在（功能未受影响）')
  await page.screenshot({ path: OUT, fullPage: false })
  console.log(`  截图：${OUT}`)
} else {
  console.log('  跳过：当前没有可拒收的运单（弹窗无法打开，不伪造结论）')
}

await browser.close()
console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===`)
process.exit(fail ? 1 : 0)
