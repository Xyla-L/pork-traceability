#!/usr/bin/env node
/**
 * 发起产品召回弹窗验收（2026-11-04 改版）：
 *   ① 列表里「进度上报」按钮已删除（执行状态只由收运签收推进）
 *   ② 召回范围只能选被举报的批次（选项来自举报台账，不是全部分割批次）
 *   ③ 选了范围就自动带出目标门店（只读标签，不给手选）
 *   ④ 弹窗排版正常（截图人眼复核）
 *
 * 自持：不提交、不落库（预览接口是只读的），跑完不留任何数据。
 * 用法：node logs/shot-recall-create.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = process.env.SHOT_BASE || 'http://localhost'
const API = process.env.API_URL || 'http://localhost:8080/api/v1'

let pass = 0, fail = 0
const check = (c, n, d = '') => { c ? pass++ : fail++; console.log(`  ${c ? '✔' : '✘'} ${n}${d ? ' — ' + d : ''}`) }

// 先问后端：哪个被举报批次真的有销售记录（否则自动带出门店会是空的，验收就没意义）
const login = await (await fetch(API + '/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
})).json()
const token = login?.data?.token
const complaints = (await (await fetch(API + '/trace/complaints?pageNum=1&pageSize=500', {
  headers: { Authorization: `Bearer ${token}` } })).json())?.data?.records || []
const reported = [...new Set(complaints.map((c) => String(c.targetBatch || '').trim()).filter(Boolean))]
let target = null
for (const no of reported) {
  const splits = (await (await fetch(`${API}/distribution/splits?keyword=${encodeURIComponent(no)}&pageNum=1&pageSize=20`, {
    headers: { Authorization: `Bearer ${token}` } })).json())?.data
  const rows = splits?.records || []
  const hit = rows.find((r) => String(r.batchNo).trim() === no)
  if (!hit) continue
  const pv = (await (await fetch(`${API}/sales/recalls/affected-stores?batchIds=${hit.id}`, {
    headers: { Authorization: `Bearer ${token}` } })).json())?.data
  if ((pv?.stores || []).length) { target = { batchNo: no, splitId: hit.id, stores: pv.stores }; break }
}
console.log(`   验收批次：${target ? target.batchNo + '（' + target.stores.length + ' 家门店）' : '（没有既有销售记录的被举报批次）'}`)

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 950 } })
await page.addInitScript(() => { Math.random = () => 0 })
await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
await page.waitForTimeout(1200)
await page.fill('input[placeholder="请输入用户名"]', 'admin')
await page.fill('input[type="password"]', '123456')
await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
await page.waitForURL((u) => u.pathname.startsWith('/admin'), { timeout: 20000 })
await page.waitForTimeout(1200)

await page.goto(`${BASE}/admin/sales/recall`, { waitUntil: 'domcontentloaded' })
await page.waitForFunction(() => document.body.innerText.includes('召回'), { timeout: 20000 }).catch(() => {})
await page.waitForTimeout(1500)

const body = await page.textContent('body')
check(!body.includes('进度上报'), '召回列表：不再有「进度上报」按钮')

await page.locator('button').filter({ hasText: /发起召回/ }).first().click()
await page.waitForTimeout(1200)
const dlg = page.locator('.el-dialog').filter({ hasText: '发起产品召回' })
check(await dlg.count() > 0, '弹窗已打开')
const dlgText = await dlg.textContent()
check(dlgText.includes('只列被举报过的批次'), '召回范围：写明只列被举报批次', '')
check(dlgText.includes('选择召回范围后自动带出门店'), '目标门店：未选范围时提示「自动带出」')

// 选批次：el-select 多选，点开后面板在 body 上
if (target) {
  await dlg.locator('.el-select').first().click()
  await page.waitForTimeout(600)
  const opt = page.locator('.el-select-dropdown__item').filter({ hasText: target.batchNo }).first()
  check(await opt.count() > 0, `召回范围：被举报批次 ${target.batchNo} 在选项里`)
  await opt.click()
  await page.waitForTimeout(1200)
  const after = await dlg.textContent()
  const storeName = target.stores[0].storeName
  check(after.includes(storeName), '目标门店：选完范围自动带出门店', `${target.stores.length} 家 · 首个 ${storeName}`)
  check(after.includes('自动带出，不需要手选'), '目标门店：说明这是自动带出的，不能手选')
  check(after.includes(`${target.stores.length} 家门店`), '目标门店：给出门店数合计')
  // 关掉下拉，截图更干净
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
} else {
  console.log('  （跳过选批次：没有可用的被举报批次）')
}

await page.screenshot({ path: 'logs/page-recall-create.png' })
// 详情弹窗也看一眼（范围/门店标签是否延续）
await dlg.locator('button').filter({ hasText: '取消' }).first().click()
await page.waitForTimeout(500)

await browser.close()
console.log(`\n结果：${fail === 0 ? `全部通过 ✅（${pass} 项）` : `有 ${fail} 项未通过 ❌（通过 ${pass} 项）`}`)
process.exit(fail === 0 ? 0 : 1)
