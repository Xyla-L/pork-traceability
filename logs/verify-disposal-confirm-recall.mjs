#!/usr/bin/env node
/**
 * 「处理签收」页验收（2026-11-05 报障）：司机把召回货送到无害化处理中心后，
 * 处理签收页必须能看见那张召回收运单，并能对它做确认签收。
 *
 * 只为断言、不写业务数据：不点「确认签收」（签收是不可回退的现场事实，留给使用者点）。
 * 用法：node logs/verify-disposal-confirm-recall.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = process.env.SHOT_BASE || 'http://localhost'
const API = process.env.API_URL || 'http://localhost:8080/api/v1'

async function api(path, opt = {}, token) {
  const headers = { ...(opt.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }
  const res = await fetch(API + path, { method: opt.method || 'GET', headers, body: opt.body ? JSON.stringify(opt.body) : undefined })
  return res.json().catch(() => null)
}

let pass = 0, fail = 0
const check = (c, n, d = '') => { c ? pass++ : fail++; console.log(`  ${c ? '✔' : '✘'} ${n}${d ? ' — ' + d : ''}`) }

// ── ① 接口口径：处置单要同时包含 回运(DISPOSAL) 与 收运(RECALL) ──
const token = (await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } }))?.data?.token
const disposeOnly = (await api('/distribution/transports?kind=DISPOSAL&pageNum=1&pageSize=50', {}, token))?.data?.records || []
const both = (await api('/distribution/transports?kind=DISPOSAL,RECALL&pageNum=1&pageSize=50', {}, token))?.data?.records || []
const recalls = both.filter((r) => r.transportKind === 'RECALL')
check(both.length >= disposeOnly.length, '① 含召回的多值查询不少于只查回运',
  `DISPOSAL=${disposeOnly.length} DISPOSAL,RECALL=${both.length}`)
check(recalls.every((r) => r.transportKind === 'RECALL'), '① 返回的召回单 transportKind 正确')
const arrivedRecall = recalls.find((r) => Number(r.status) === 3)
check(recalls.length > 0, '① 召回收运单已进入处置单口径', recalls.map((r) => r.transportNo).join('、') || '(无召回单)')
if (recalls.length) {
  const bad = recalls.filter((r) => Number(r.arrivedStopCount || 0) > Number(r.stopCount || 0))
  check(bad.length === 0, '① 到货进度不出现「已到 > 总站数」',
    recalls.map((r) => `${r.transportNo} ${r.arrivedStopCount}/${r.stopCount}`).join(' '))
}

// ── ② 页面：处理签收列表要列出这些单 ──
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
async function loginAs(username) {
  await page.addInitScript(() => { Math.random = () => 0 })
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
  await page.waitForTimeout(1200)
  await page.fill('input[placeholder="请输入用户名"]', username)
  await page.fill('input[type="password"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
  await page.waitForURL((u) => u.pathname.startsWith('/admin'), { timeout: 20000 })
  await page.waitForTimeout(1500)
}
await loginAs('admin')
await page.goto(`${BASE}/admin/disposal/confirm`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(2500)

const body = await page.locator('body').innerText()
check(/处理运单/.test(body), '② 处理签收页已打开')
for (const r of recalls) {
  check(body.includes(r.transportNo), `② 列表里出现召回单 ${r.transportNo}`)
}
if (recalls.length) {
  check(/召回收运/.test(body), '② 召回单有类型标签（召回收运）')
  check(/拒收回运/.test(body) || !disposeOnly.length, '② 拒收回运单没有被召回单挤掉')
}
if (arrivedRecall) {
  const row = page.locator('tr', { hasText: arrivedRecall.transportNo }).first()
  const rowText = await row.innerText()
  check(/确认签收/.test(rowText), `② 已到站的召回单 ${arrivedRecall.transportNo} 可点「确认签收」`, rowText.replace(/\n/g, ' | '))
  check(/待确认/.test(rowText), '② 该单状态显示「待确认」')
}

// 空表兜底文案：一条处置单都没有时不许显示一张空白表
if (!both.length) check(/没有待签收的处置单/.test(body), '② 无数据时给出空状态说明')

await page.screenshot({ path: 'logs/page-disposal-confirm-recall.png', fullPage: true })
await browser.close()

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
process.exit(fail ? 1 : 0)
