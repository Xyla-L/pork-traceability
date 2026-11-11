#!/usr/bin/env node
/**
 * 回归：车载终端「填充示例值」必须真的填出东西 —— 不许点完只剩一个时刻。
 *
 * 用户两次报障（2026-11-05）：
 *   ① 「填充示例值时没有填充处理计划的」—— 示例只去挑库里别的待发车运单，处理计划里那批召回货没人管；
 *   ② 「点击填充示例值并没有回填信息」—— 处理计划里有召回货但还没排车时，页面没有任何「待发车运单」，
 *      车牌 / 司机 / 卡号全都无处可取，表单只剩一个时刻，看起来就是功能坏了。
 *
 * 现在的口径：**填充示例值自己把缺的那步补上** —— 处理计划里有未排车的召回货，就先按计划排成
 * 收运运单（业务上就是计划的正向推进，不是凭空造单），再照常从这趟车上取车牌 / 司机 / 卡号。
 *
 * 盯的不变量（不是某条数据）：
 *   ① 点完「填充示例值」，车牌必须有值 —— 这是「回填」的最低线；
 *   ② 处理计划里原本有未排车的召回货 ⇒ 必须正好新出一张 RECALL 运单，且表单车牌 = 该运单车牌；
 *   ③ 提示要说清「这是收运运单」以及「这张单是刚按处理计划排出来的」（不能让人以为凭空多了一趟车）；
 *   ④ 再点一次不会重复出单（幂等）；
 *   ⑤ 全程无脚本报错。
 *
 * 自持：跑完删掉本次生成的收运运单，池行还回「待收运」，不留残留。
 * 用法：node logs/verify-ingest-plan-sample.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'
import { execSync } from 'node:child_process'

const BASE = process.env.SHOT_BASE || 'http://localhost'
const API = process.env.API_URL || 'http://localhost:8080/api/v1'

const MYSQL = (sql) => execSync(
  `docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password -N -B -e "${sql}"`,
  { stdio: 'pipe' }).toString().trim()

let pass = 0
const fail = []
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  ✓ ${name}${detail ? ' — ' + detail : ''}`) }
  else { fail.push(name); console.log(`  ✗ ${name}${detail ? ' — ' + detail : ''}`) }
}

const adminToken = await fetch(`${API}/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
}).then((r) => r.json()).then((b) => b?.data?.token)
const call = (path) => fetch(API + path, { headers: { Authorization: `Bearer ${adminToken}` } }).then((r) => r.json())

const poolBefore = (await call('/distribution/return-pool?stage=DISPOSAL'))?.data || []
const recallPending = poolBefore.filter((r) => r.source === 'RECALL' && !r.scanTime)
const waitingBefore = (await call('/distribution/transports?status=1&kind=NORMAL,DISPOSAL,RECALL&pageNum=1&pageSize=50'))?.data?.records || []
if (!recallPending.length) {
  console.log('跳过：处理计划里没有「没排车」的召回货，先造一条召回（或跑 verify-recall-collect 的背景自持）再验')
  process.exit(0)
}
console.log(`   处理计划待排车召回货 ${recallPending.length} 批；当前待发车运单 ${waitingBefore.length} 张`)
const recallIdsBefore = new Set(((await call('/distribution/transports?kind=RECALL&pageNum=1&pageSize=50'))?.data?.records || []).map((t) => t.id))

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const errors = []
page.on('pageerror', (e) => errors.push(String(e.message).slice(0, 200)))
await page.addInitScript(() => { Math.random = () => 0 })
await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
await page.waitForTimeout(1000)
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
await page.locator('.el-dialog input').first().click()
await page.waitForTimeout(500)
await page.locator('.el-select-dropdown__item').filter({ hasText: 'ARRIVE-001' }).first().click()
await page.waitForTimeout(700)
const selects = page.locator('.el-dialog .el-select')
if ((await selects.count()) > 1) {
  await selects.nth(1).click(); await page.waitForTimeout(500)
  await page.locator('.el-select-dropdown__item:visible').filter({ hasText: '发车确认' }).first().click()
  await page.waitForTimeout(700)
}

const dlg = page.locator('.el-dialog:visible').first()
const readForm = async () => {
  const fields = await dlg.locator('input, textarea').evaluateAll((els) => els.map((e) => ({
    ph: e.getAttribute('placeholder') || '', val: e.value,
  })).filter((x) => x.ph))
  const byPh = (kw) => (fields.find((f) => f.ph.includes(kw)) || {}).val || ''
  return {
    plate: byPh('车牌'),
    card: byPh('卡号'),
    // 姓名输入框的 placeholder 里没有「司机」两个字（「没刷卡时填；刷卡后由卡号自动换成持卡人…」），
    // 按「没刷卡时填」取，别让「卡号」框的说明把自己带偏（那里也写着「司机上车刷的卡号」）
    driver: byPh('没刷卡时填'),
    departTime: byPh('发车时刻'),
    fields,
  }
}

// ── 第一次点：应当自动排车 + 回填 ──
await dlg.locator('button').filter({ hasText: '填充示例值' }).first().click()
await page.waitForTimeout(5000)
const hint = (await dlg.locator('.demo-actions').innerText().catch(() => '')).replace(/\s+/g, ' ')
const form = await readForm()

check('① 点完「填充示例值」车牌被填上（不许只剩一个时刻）', !!form.plate, `车牌=${form.plate || '(空)'} 时刻=${form.departTime || '(空)'}`)
check('① 司机/卡号也跟着有值', !!form.card && !!form.driver, `司机=${form.driver || '(空)'} 卡号=${form.card || '(空)'}`)
check('② 提示说得清这趟车是什么单', /收运运单|待发车运单|处理运单/.test(hint), hint.slice(0, 90) + '…')

const recallAfter = (await call('/distribution/transports?kind=RECALL&pageNum=1&pageSize=50'))?.data?.records || []
const created = recallAfter.filter((t) => !recallIdsBefore.has(t.id))
const leg = created[0]
check('③ 处理计划里未排车的召回货 → 自动排出一张收运运单', created.length === 1,
  created.map((t) => `${t.transportNo}/${t.vehicleNo}`).join(', ') || '(没出单)')
if (leg) {
  check('③ 表单车牌 = 这张收运运单的车牌', form.plate === leg.vehicleNo, `表单=${form.plate} 运单=${leg.vehicleNo}`)
  check('④ 提示交代「这张单是刚按处理计划排出来的」', /按处理计划/.test(hint), hint.slice(-90) + '…')
} else {
  // 库里已经有别的待发车运单时，不该再排新车 —— 也算通过，但要把复用关系说清楚
  check('③ 已有可发的车时不重复排车', waitingBefore.length > 0, `待发车 ${waitingBefore.length} 张`)
}

// ── 第二次点：幂等 ──
await dlg.locator('button').filter({ hasText: '填充示例值' }).first().click()
await page.waitForTimeout(3500)
const recallAgain = (await call('/distribution/transports?kind=RECALL&pageNum=1&pageSize=50'))?.data?.records || []
check('⑤ 再点一次不会重复出单', recallAgain.filter((t) => !recallIdsBefore.has(t.id)).length === created.length,
  `第一次 ${created.length} 张，第二次后 ${recallAgain.filter((t) => !recallIdsBefore.has(t.id)).length} 张`)
check('⑥ 页面无脚本报错', errors.length === 0, errors.join(' | ').slice(0, 120))

// ── 自清：删掉本次测试用的收运运单，池行还回「待收运」 ──
for (const t of created) {
  try {
    MYSQL(`DELETE FROM db_distribution.recall_collect_scan WHERE transport_id=${t.id}; `
      + `DELETE FROM db_distribution.transport_stop WHERE transport_id=${t.id}; `
      + `DELETE FROM db_distribution.cold_chain_transport WHERE id=${t.id}; `
      + `UPDATE db_distribution.recall_collect SET status=0, transport_id=NULL, stop_id=NULL WHERE transport_id=${t.id}`)
    console.log(`  清理：测试收运运单 ${t.transportNo} 已删，池行已还回处理计划`)
  } catch (e) {
    console.log('  清理失败（请手工删）：', String(e.stderr || e.message).slice(0, 160))
  }
}
await browser.close()
console.log(`\n结果：${fail.length === 0 ? `全部通过 ✅（${pass} 项）` : `有 ${fail.length} 项未通过 ❌`}`)
process.exit(fail.length === 0 ? 0 : 1)
