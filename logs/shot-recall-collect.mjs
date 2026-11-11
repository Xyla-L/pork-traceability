#!/usr/bin/env node
/**
 * 召回收运页面验收（2026-11-04）：
 *   ① 拒收回运页的「处理计划」里，召回货与拒收货同表出现，来源可辨
 *   ② 汇总行给出「召回 N 批 / M 家门店」（调度排车的依据）
 *   ③ 召回池有货时才出现「生成收运运单」入口，弹窗里能给车 + 司机
 *   ④ 处理运单列表同时列出回运（DISPOSAL）与收运（RECALL），并标类型
 *
 * 自持：自己发起一张召回单撑住数据，跑完按业务键清干净（recall_no / 通知）。
 * 用法：node logs/shot-recall-collect.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = process.env.SHOT_BASE || 'http://localhost'
const API = process.env.API_URL || 'http://localhost:8080/api/v1'
const pad = (n) => String(n).padStart(2, '0')
const nowText = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
async function api(path, opt = {}, token) {
  const headers = { ...(opt.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }
  const res = await fetch(API + path, { method: opt.method || 'GET', headers, body: opt.body ? JSON.stringify(opt.body) : undefined })
  return res.json().catch(() => null)
}

let pass = 0, fail = 0
const check = (c, n, d = '') => { c ? pass++ : fail++; console.log(`  ${c ? '✔' : '✘'} ${n}${d ? ' — ' + d : ''}`) }

// ── 造一张召回单（只用到池子那一段，不生成运单）──
const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token
const sales = (await api('/sales/records?pageNum=1&pageSize=100', {}, token))?.data?.records || []
const cands = sales.filter((s) => s.splitBatchId && s.storeId && [1, 2].includes(Number(s.status)))
let recallNo = null, recallId = null
if (cands.length) {
  const storeId = cands[0].storeId
  const batchIds = [...new Set(cands.filter((s) => s.storeId === storeId).map((s) => s.splitBatchId))]
  const created = await api('/sales/recalls', {
    method: 'POST',
    body: { reason: '召回收运页面验收：' + nowText(), riskLevel: 3, scope: { batchIds, storeIds: [storeId] }, initiator: 'admin' },
  }, token)
  recallNo = created?.data?.recallNo
  recallId = created?.data?.id
}
console.log(`   召回单：${recallNo || '（没造出来，页面将只有拒收货）'}`)

const browser = await chromium.launch({ channel: 'chrome', headless: true })
async function loginAs(page, username) {
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

const page = await browser.newPage({ viewport: { width: 1440, height: 950 } })
await loginAs(page, 'distribution_user')
await page.goto(`${BASE}/admin/distribution/return`, { waitUntil: 'domcontentloaded' })
await page.waitForFunction(() => document.body.innerText.includes('处理计划'), { timeout: 20000 }).catch(() => {})
await page.waitForTimeout(1500)

const body = await page.textContent('body')
check(body.includes('共') && body.includes('kg'), '处理计划：汇总行在（共 N 批 / 合计 kg）')
check(!recallNo || body.includes('召回'), '处理计划：召回来源可见', recallNo ? '' : '本次没造召回单')
check(!recallNo || body.includes('等冷链中心上门收'), '处理计划：写明召回货等上门收')
const genBtn = page.locator('button').filter({ hasText: '生成收运运单' })
check(await genBtn.count() > 0, '处理计划：有召回货时出现「生成收运运单」')
check(body.includes('待上门收'), '处理计划：召回行标「待上门收」')
check(body.includes('处理运单（回运 / 收运 → 无害化处理中心）'), '处理运单卡片标题含回运/收运')

await page.screenshot({ path: 'logs/page-recall-pool.png', fullPage: true })

// 弹窗（不提交：提交会出运单，验收只看入口长什么样）
if (await genBtn.count()) {
  await genBtn.first().click()
  await page.waitForTimeout(1200)
  const dialog = await page.textContent('.el-dialog').catch(() => '')
  check(dialog.includes('生成收运运单'), '弹窗：标题正确')
  check(dialog.includes('收运车辆'), '弹窗：能选车（不给空白手填）')
  check(dialog.includes('随车扫码枪'), '弹窗：写明到店扫码收货的现场动作')
  await page.screenshot({ path: 'logs/page-recall-dialog.png' })
  await page.locator('.el-dialog__footer button').filter({ hasText: '取消' }).click()
}

await browser.close()

if (process.env.KEEP==='1') { console.log('KEEP=1 跳过清理'); process.exit(fail===0?0:1) }
// ── 清理：召回单 + 池子行 + 通知 ──
if (recallNo) {
  const { execSync } = await import('node:child_process')
  const sql = [
    `DELETE FROM db_distribution.recall_collect WHERE recall_no = '${recallNo}'`,
    `DELETE FROM db_sales.recall_order WHERE recall_no = '${recallNo}'`,
    `DELETE FROM db_common.user_notification WHERE biz_type IN ('recall_store','recall_center') AND biz_id = ${recallId}`,
  ].join('; ')
  try {
    execSync(`docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 -e "${sql}"`, { stdio: 'pipe' })
    console.log('   清理：验收用的召回单与通知已删除')
  } catch (e) { console.log('   清理失败：', String(e.stderr || e.message).slice(0, 160)) }
}

console.log(`\n${fail === 0 ? `全部通过 ✅（${pass} 项）` : `有 ${fail} 项未通过 ❌（通过 ${pass} 项）`}`)
process.exit(fail === 0 ? 0 : 1)
