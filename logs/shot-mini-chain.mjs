#!/usr/bin/env node
/**
 * 小程序 H5 消费端存证口径截图（2026-11-08）
 * 目的：确认消费者看到的是人话、且载体口径随后端自报变化
 *   ① 正常码的扫码结果页：存证区块标题、类型中文、哈希截断
 *   ② 正常码的验真页：标题用后端 chainStatus、明细类型中文
 *   ③ 已作废码的验真页：显示「该商品已被作废」而不是系统错误
 * 只读，不改任何数据。
 * 用法：node logs/shot-mini-chain.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = process.env.MINI_BASE || 'http://localhost:5173/#'
const OK_QR = process.env.OK_QR || 'QR-PORK-2DAB84DA3F6942F1AB361A3D604B5863'
const VOID_QR = process.env.VOID_QR || 'QR-PORK-2B85965DB25544BBBA70F8C29258758A'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const errors = []
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
page.on('pageerror', (e) => errors.push(String(e)))

async function shot(path, file, wait = 3500) {
  // 纯 hash 变化不会重新加载文档，uni 的页面实例会沿用上一页状态：
  // 先离开再进，保证每次都是干净的 onLoad
  await page.goto('about:blank').catch(() => {})
  await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' }).catch(() => {})
  await page.waitForTimeout(wait)
  await page.screenshot({ path: `logs/${file}`, fullPage: true })
  const text = await page.locator('body').innerText().catch(() => '')
  console.log(`\n=== ${file} ===`)
  console.log(text.split('\n').filter((s) => s.trim()).slice(0, 24).join('\n'))
  return text
}

const scanText = await shot(`/pages/scan-result/scan-result?qrCode=${OK_QR}`, 'mini-scan-result.png')
const verifyText = await shot(`/pages/verify/verify?qrCode=${OK_QR}`, 'mini-verify-ok.png')
const voidText = await shot(`/pages/verify/verify?qrCode=${VOID_QR}`, 'mini-verify-void.png')

const bad = []
if (/[A-Z]{3,}_[A-Z_]+/.test(scanText)) bad.push('扫码页出现英文事件代码')
if (/区块链已验证/.test(verifyText) === false) bad.push('验真页未显示区块链已验证（evm 模式预期）')
if (!/已被作废/.test(voidText)) bad.push('作废码未显示后端业务说明')
if (/下游服务返回异常/.test(voidText)) bad.push('作废码仍显示系统错误文案')

console.log('\n控制台错误:', errors.length ? errors.slice(0, 3) : '无')
console.log(bad.length ? `✘ ${bad.join('; ')}` : '✔ 三项口径检查通过')
await browser.close()
process.exit(bad.length ? 1 : 0)
