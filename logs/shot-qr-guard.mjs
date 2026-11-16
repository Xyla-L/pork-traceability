#!/usr/bin/env node
/**
 * 小程序首页「扫码/搜索前置校验」截图回归（2026-11-15）
 * 目的：消费者扫到付款码/网址码时，在跳转之前就被拦下并说清「该扫什么」，
 *       而不是跳到结果页看到一句像「这肉是假的」的「未找到溯源信息」。
 * 只读，不改任何数据。需先起 mini-app 的 dev:h5（npm run dev:h5）。
 * 用法：node logs/shot-qr-guard.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = process.env.MINI_BASE || 'http://localhost:5174/#'  // 5173 常被管理端 dev 占用，vite 自动 +1

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const errs = []
page.on('pageerror', (e) => errs.push(String(e)))

async function open() {
  await page.goto('about:blank').catch(() => {})
  await page.goto(`${BASE}/pages/index/index`, { waitUntil: 'networkidle' }).catch(() => {})
  await page.waitForTimeout(2500)
}

let pass = 0, fail = 0
const check = (n, ok, d = '') => { ok ? (pass++, console.log(`  ✅ ${n}`)) : (fail++, console.log(`  ❌ ${n}${d ? ' —— ' + d : ''}`)) }

// ① 输入不像本系统的码
await open()
const input = page.locator('input').first()
check('首页搜索框存在', await input.count() > 0)
await input.fill('QR-PORK-ABC')
await page.getByText('搜索', { exact: true }).last().click()
await page.waitForTimeout(1200)
let body = await page.locator('body').innerText()
check('非法码 → 弹窗说明「不是有效的溯源码」', body.includes('不是有效的溯源码'))
check('非法码 → 给出该扫什么', body.includes('包装袋上的二维码'))
check('非法码 → 没有跳转到结果页', !page.url().includes('scan-result'))
await page.screenshot({ path: 'logs/qr-guard-invalid.png' })

// ② 输入合法格式但不存在的码：应跳结果页并显示后端原话
await open()
await page.locator('input').first().fill('QR-PORK-' + '0'.repeat(32))
await page.getByText('搜索', { exact: true }).last().click()
await page.waitForTimeout(3000)
body = await page.locator('body').innerText()
check('合法格式不存在的码 → 跳转到结果页', page.url().includes('scan-result'))
check('结果页说明「未查询到该溯源码对应的产品」', body.includes('未查询到该溯源码对应的产品'))
check('结果页不出现「产品不存在」这种像假货的说法', !body.includes('产品不存在'))
await page.screenshot({ path: 'logs/qr-guard-ghost.png', fullPage: true })

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
if (errs.length) console.log('页面异常：', errs.slice(0, 3))
await browser.close()
process.exit(fail ? 1 : 0)
