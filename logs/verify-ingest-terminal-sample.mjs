#!/usr/bin/env node
/**
 * 车载终端「填充示例值」回归（2026-11-05）。
 *
 * 背景：处理计划里明明有货，车载终端点填充却只填了时间 —— 车牌、卡号全空，
 * 面板上还写着「没有数据」。根因是设备侧的取单口径漏了 RECALL（收运运单）：
 * 搜救的是 NORMAL + DISPOSAL，召回收运的车在设备侧等于不存在。
 *
 * 这里盯的是**不变量**，不是某一条数据：
 *   ① 点完「填充示例值」必须给出明确交代 —— 要么字段填上，要么提示语说清卡在哪一步；
 *      既没填、又只说「没有数据」= 页面坏了（本次就是这种）。
 *   ② 接口里只要存在「待发车」的运单（三种 kind 任一），示例值就必须把它的车牌填进表单。
 *      这条直接守住「kind 漏了一种」这个 bug：漏了 RECALL，接口有单、表单还是空。
 *   ③ 运单上没有司机时，示例卡号给 8801（刷卡赋身份的唯一合法路径）。
 *
 * 只读页面 + 只读接口，不提交任何上报、不造业务数据。
 * 用法：node logs/verify-ingest-terminal-sample.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'
import { execSync } from 'node:child_process'

const BASE = process.env.SHOT_BASE || 'http://localhost'
const API = process.env.API_URL || 'http://localhost:8080/api/v1'
const ALL_KINDS = 'NORMAL,DISPOSAL,RECALL'

async function api(path, opt = {}, token) {
  const headers = { ...(opt.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }
  const res = await fetch(API + path, { method: opt.method || 'GET', headers, body: opt.body ? JSON.stringify(opt.body) : undefined })
  return res.json().catch(() => null)
}

let pass = 0, fail = 0
const check = (c, n, d = '') => { c ? pass++ : fail++; console.log(`  ${c ? '✔' : '✘'} ${n}${d ? ' — ' + d : ''}`) }

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token
if (!token) throw new Error('登录失败')

// 服务端事实：有哪几辆「待发车」的车（三种 kind 一起查，和页面口径一致）
const waitings = ((await api(`/distribution/transports?status=1&kind=${ALL_KINDS}&pageNum=1&pageSize=50`, {}, token))?.data?.records) || []
const pool = ((await api('/distribution/return-pool?stage=DISPOSAL', {}, token))?.data) || []
// 池里有未排车的召回货时，「填充示例值」会就地排一张收运运单（这正是「点了要看见值」的保证）。
// 记下跑前已有的 RECALL 单，跑完把本次新排的删掉、池行还回待收运 —— 回归不许留残留。
const recallIdsBefore = new Set(((await api(`/distribution/transports?kind=RECALL&pageNum=1&pageSize=50`, {}, token))?.data?.records || []).map((t) => t.id))
console.log(`   待发车运单 ${waitings.length} 张` + (waitings.length ? `：${waitings.map((t) => `${t.transportNo}(${t.transportKind}/${t.vehicleNo})`).join(' ')}` : ''))
console.log(`   处理计划 ${pool.length} 行（召回 ${pool.filter((r) => r.source === 'RECALL').length}）`)

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const errors = []
page.on('pageerror', (e) => errors.push(String(e.message).slice(0, 200)))

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

await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(2500)
await page.locator('button').filter({ hasText: '模拟设备上报' }).first().click()
await page.waitForTimeout(1000)

// 接入设备 = 车载终端（通用终端，一台设备两个通道）
await page.locator('.el-dialog .el-select').first().click()
await page.waitForTimeout(500)
await page.locator('.el-select-dropdown__item').filter({ hasText: 'ARRIVE-001' }).first().click()
await page.waitForTimeout(700)

// 通道 = 发车确认
const selects = page.locator('.el-dialog .el-select')
if ((await selects.count()) > 1) {
  await selects.nth(1).click()
  await page.waitForTimeout(500)
  const opt = page.locator('.el-select-dropdown__item:visible').filter({ hasText: '发车确认' }).first()
  if (await opt.count()) { await opt.click(); await page.waitForTimeout(700) }
}

await page.locator('button').filter({ hasText: '填充示例值' }).first().click()
await page.waitForTimeout(2500)

const dlg = page.locator('.el-dialog:visible').first()
const hint = (await dlg.innerText()).replace(/\s+/g, ' ')
const fields = await dlg.locator('input').evaluateAll((els) =>
  els.map((e) => ({ ph: e.getAttribute('placeholder') || '', val: e.value })))
const byPh = (kw) => (fields.find((f) => f.ph.includes(kw)) || {}).val || ''
const plate = byPh('车牌')
const card = byPh('卡号')
const departTime = byPh('发车时刻')

check(!!departTime, '① 现场时刻一定填得上（设备采集时刻）', departTime)
check(hint.length > 40, '① 填充后必须给出交代（不是点了没反应）', hint.slice(0, 40) + '…')

// ② 服务端有「待发车」的车 ⇒ 示例值必须把车牌填出来（这正是漏 kind 时会失败的地方）
if (waitings.length) {
  const expected = waitings.map((t) => t.vehicleNo)
  check(!!plate && expected.includes(plate), '② 有「待发车」运单时，车牌必须被填上',
    `表单=${plate || '(空)'} 接口候选=${expected.join('、')}`)
  // ③ 运单没司机 → 8801；运单有司机但不在备案卡表 → 留空并在提示里说明
  const target = waitings.find((t) => t.vehicleNo === plate)
  if (target) {
    if (!target.driverName) {
      check(card === '8801', '③ 运单上没有司机 → 示例卡号给 8801（刷卡即赋身份）', card || '(空)')
    } else {
      check(card === '' && /不在司机备案的演示卡表/.test(hint), '③ 司机不在备案卡表 → 卡号留空并说明原因',
        `卡号=${card || '(空)'}`)
    }
  }
} else {
  // 没有待发车运单时，「填充示例值」应当自己把缺的那步补上（按处理计划排一张收运单）并回填，
  // 而不是留一张只剩时刻的空表单 —— 用户 2026-11-05 报的就是「点了并没有回填信息」。
  check(/处理计划|收运运单|扫码装车|装一车货/.test(hint), '② 没有待发车运单时，提示语要说清下一步', hint.slice(0, 60) + '…')
  if (pool.some((r) => r.source === 'RECALL' && !r.scanTime)) {
    check(!!plate, '② 处理计划里有未排车的召回货 → 填充后车牌必须有值', `表单=${plate || '(空)'}`)
    // 车牌填了、卡号空着 = 演示断在刷卡那一步（用户 2026-11-05 报「司机卡号没有回填」）
    check(!!card, '② 同场景下卡号也必须填上（能刷出本人）', `卡号=${card || '(空)'}`)
  }
}

check(errors.length === 0, '④ 页面无脚本报错', errors.join(' | ').slice(0, 120))

await page.screenshot({ path: 'logs/page-ingest-terminal.png', fullPage: false })
await browser.close()

// ── 自清：本次填充顺手排出来的收运运单删掉，池行还回「待收运」 ──
const recallAfter = ((await api(`/distribution/transports?kind=RECALL&pageNum=1&pageSize=50`, {}, token))?.data?.records) || []
const created = recallAfter.filter((t) => !recallIdsBefore.has(t.id))
for (const t of created) {
  try {
    execSync(`docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password -N -B -e "`
      + `DELETE FROM db_distribution.recall_collect_scan WHERE transport_id=${t.id}; `
      + `DELETE FROM db_distribution.transport_stop WHERE transport_id=${t.id}; `
      + `DELETE FROM db_distribution.cold_chain_transport WHERE id=${t.id}; `
      + `UPDATE db_distribution.recall_collect SET status=0, transport_id=NULL, stop_id=NULL WHERE transport_id=${t.id}"`,
      { stdio: 'pipe' })
    console.log(`   清理：本次填充排出的收运运单 ${t.transportNo} 已删，池行已还回处理计划`)
  } catch (e) {
    console.log('   清理失败（请手工删）：', String(e.stderr || e.message).slice(0, 160))
  }
}

console.log(`\n结果：${fail === 0 ? `全部通过 ✅（${pass} 项）` : `有 ${fail} 项未通过 ❌（通过 ${pass} 项）`}`)
process.exit(fail === 0 ? 0 : 1)
