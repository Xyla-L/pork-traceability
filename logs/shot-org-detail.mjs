#!/usr/bin/env node
/** 截取机构详情面板，确认右侧详情正常渲染。 */
import { chromium } from 'playwright-core'

const BASE = process.env.BASE || 'http://localhost:5173'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.addInitScript(() => { Math.random = () => 0 })

try {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[type="password"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
  // 等登录真正完成（进入工作台），不靠固定时长
  await page.waitForURL(/admin|dashboard|workbench/i, { timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(1500)

  await page.goto(`${BASE}/admin/system/orgs`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.tree-group', { timeout: 15000 })
  await page.waitForTimeout(800)

  const node = page.locator('.el-tree-node__content').filter({ hasText: '安康生鲜门店' }).first()
  await node.click()
  await page.waitForTimeout(600)

  const panel = page.locator('.org-detail-panel')
  console.log('panel count:', await panel.count())
  console.log('panel box:', JSON.stringify(await panel.boundingBox()))
  await panel.screenshot({ path: 'logs/org-detail.png' }).catch((e) => console.log('shot err:', e.message))
} finally {
  await browser.close()
}
console.log('done')
