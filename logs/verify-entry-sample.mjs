/**
 * 入场查验「填充示例值」验收：
 * 修复前只扫生猪档案前 30 条（按创建时间倒序），ET-DEMO-0133 排第 44 位 →
 * 误报「没有同时满足…的猪」。修复后按状态取全量，应能正经取到它。
 *
 * 只点「填充示例值」，不提交上报（不改数据）。
 *   node logs/verify-entry-sample.mjs
 */
import path from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const OUT_DIR = import.meta.dirname
const EXPECT_TAG = process.env.EXPECT_TAG || 'ET-DEMO-0133'

const pass = []
const fail = []
const check = (name, ok, detail = '') => {
  if (ok) { pass.push(name); console.log(`  [PASS] ${name}`) }
  else { fail.push(name); console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

const browser = await chromium.launch({
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  headless: true,
})
const page = await browser.newPage({ viewport: { width: 1680, height: 1000 } })
await page.addInitScript(() => { Math.random = () => 0 })

await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 })
await page.waitForTimeout(1500)
await page.fill('input[placeholder="请输入用户名"]', 'admin')
await page.fill('input[placeholder="请输入密码"]', '123456')
await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
await page.locator('button.login-btn').click()
await page.waitForTimeout(6000)

await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'load', timeout: 30000 })
await page.waitForTimeout(3000)

// 打开「模拟设备上报」弹窗
const openDemo = page.locator('button:has-text("模拟设备上报")').first()
check('① 「模拟设备上报」入口存在', (await openDemo.count()) > 0)
await openDemo.click()
await page.waitForTimeout(1500)

// 选入场门禁地磅设备（GATE-001），通道随设备自动定为「入场门禁地磅」
const deviceSelect = page.locator('.el-dialog input[placeholder=""]').first()
const dlg = page.locator('.el-dialog:has-text("模拟设备上报")').first()
await dlg.locator('.el-select').first().click()
await page.waitForTimeout(800)
const gateOption = page.locator('.el-select-dropdown__item:has-text("GATE-001")').first()
check('② 设备下拉里有门禁地磅 GATE-001', (await gateOption.count()) > 0)
await gateOption.click()
await page.waitForTimeout(1500)
const dlgText = String(await dlg.textContent())
check('③ 通道识别为入场门禁地磅', dlgText.includes('入场门禁') || dlgText.includes('入场查验'), dlgText.slice(0, 60))

// 点「填充示例值」
const fillBtn = dlg.locator('button:has-text("填充示例值")').first()
check('④ 填充示例值按钮存在', (await fillBtn.count()) > 0)
await fillBtn.click()
await page.waitForTimeout(8000)

const body = String(await page.textContent('body'))
check('⑤ 不再提示「没有同时满足…的猪」', !body.includes('没有同时满足'))
check(`⑥ 提示里取到了 ${EXPECT_TAG}`, body.includes(EXPECT_TAG), body.match(/取到耳标[^（]*/)?.[0] || '未匹配到提示')

// 表单里的耳标号输入框应被填上
const values = await dlg.locator('input').evaluateAll((els) => els.map((e) => e.value))
check('⑦ 表单耳标号已填', values.includes(EXPECT_TAG), JSON.stringify(values.slice(0, 12)))

await page.screenshot({ path: path.join(OUT_DIR, 'page-entry-sample.png') })
console.log(`\n结果: ${pass.length} 通过 / ${fail.length} 失败`)
await browser.close()
if (fail.length > 0) process.exit(1)
