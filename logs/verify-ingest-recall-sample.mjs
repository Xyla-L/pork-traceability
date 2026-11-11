/**
 * 「模拟设备上报 → 填充示例值」必须认处理计划里那批召回货（2026-11-05 用户报障）。
 *
 * 报障原话：设备模拟部分填充的示例值不是召回部分的。
 * 根因：召回货一排进收运运单就从「待收运」队列移出（recall_collect.status 0→1），
 *       前端拿 return-pool 取到的池子是空的 → 所有兜底文案都说「处理计划是空的」，
 *       而那张收运运单正在路上跑（本次实测 TR20261105263043 / SP-TEST-0014 / 宏图生鲜门店）。
 *
 * 断言（只读，不改任何业务数据）：
 *   ① 到达确认：车牌 / 司机 / 卡号取自那趟收运运单，提示点名召回批次
 *   ② 发车确认：不再说「处理计划也是空的」，点名那趟收运运单并给出下一步按钮
 *   ③ 到店扫码：未到达时不留半张表单（车牌空），给「切到到达确认」按钮，且点名召回批次
 *   ④ 装车扫码：不说「处理计划是空的」，说明召回货不走装车口并指向到店扫码
 *   ⑤ 全流程无脚本报错
 *
 * 前置：库里存在「未完成（1/2/3）的召回收运运单」。没有就跳过（退出码 0），不造数据 ——
 * 造数据要发起召回，会动业务台账，不属于示例值回归的职责。
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = 'http://localhost'
const API = 'http://localhost:8080/api/v1'
let pass = 0
let fail = 0
const check = (ok, name, detail = '') => {
  if (ok) { pass++; console.log(`✅ ${name}`) } else { fail++; console.log(`❌ ${name}${detail ? ' — ' + detail : ''}`) }
}
const api = async (path, { method = 'GET', token, body } = {}) => {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return res.json().catch(() => null)
}

async function main() {
  // ── 前置：拿 admin token 与「在途召回收运单」 ──
  const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
  const token = login?.data?.token || login?.data?.accessToken
  if (!token) throw new Error('admin 登录失败')

  const legs = (await api('/distribution/transports?kind=RECALL&pageNum=1&pageSize=20', { token }))?.data?.records || []
  const alive = legs.filter((t) => [1, 2, 3].includes(Number(t.status)))
  // 「还能演示到达」的收运单：运输中(2)，或已部分到站(3 且有未到站)。
  // 全站到齐的单不再接受到达上报，页面会跳过它去挑别的车 —— 断言必须跟随这个口径，
  // 否则单子每往前走一步（这正是链条在正常推进），脚本就报一次假败。
  const unarrived = alive.filter((t) =>
    Number(t.status) === 2 ||
    (Number(t.status) === 3 && Number(t.arrivedStopCount || 0) < Number(t.stopCount || 1)))
  const pendingLeg = alive.find((t) => Number(t.status) === 1)
  const leg = unarrived[0] || alive[0]
  const canDemoArrive = unarrived.length > 0
  const pool = (await api('/distribution/return-pool?stage=DISPOSAL', { token }))?.data || []
  const recallPending = pool.filter((r) => r.source === 'RECALL' && !r.scanTime)

  if (recallPending.length) {
    console.log(`⚠️ 处理计划里有 ${recallPending.length} 批召回货还没排车：本脚本只读，跳过（发车确认会真的排车）`)
    console.log('   先到「处理运输 → 处理计划」生成收运运单，或跑 verify-recall-collect 走完整链路。')
    return
  }
  if (!alive.length) {
    console.log('⚠️ 当前没有未完成的召回收运运单（终态或已收口）：跳过')
    return
  }
  console.log(`前置：未完成的收运运单 ${leg.transportNo}（车牌 ${leg.vehicleNo}，状态 ${leg.status}）`
    + (canDemoArrive ? '' : '（已全站到齐，到达/扫码走弱断言）'))

  // ── 打开设备模拟 ──
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e.message).slice(0, 160)))
  await page.addInitScript(() => { Math.random = () => 0 })
  await page.goto(BASE + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
  await page.waitForTimeout(1200)
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[type="password"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
  await page.waitForURL((u) => u.pathname.startsWith('/admin'), { timeout: 20000 })
  await page.waitForTimeout(1200)
  await page.goto(BASE + '/admin/ingest/monitor', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2500)
  await page.locator('button').filter({ hasText: '模拟设备上报' }).first().click()
  await page.waitForTimeout(1000)
  const dlg = page.locator('.el-dialog:visible').first()

  const pickDevice = async (dev) => {
    await dlg.locator('.el-select').first().click()
    await page.waitForTimeout(450)
    await page.locator('.el-select-dropdown__item:visible').filter({ hasText: dev }).first().click()
    await page.waitForTimeout(900)
  }
  const pickChannel = async (name) => {
    const sels = dlg.locator('.el-select')
    if ((await sels.count()) < 2) return false
    await sels.nth(1).click()
    await page.waitForTimeout(450)
    const opt = page.locator('.el-select-dropdown__item:visible').filter({ hasText: name }).first()
    if (await opt.count()) { await opt.click(); await page.waitForTimeout(800); return true }
    await page.keyboard.press('Escape')
    return false
  }
  const fill = async () => {
    await dlg.locator('button').filter({ hasText: '填充示例值' }).first().click()
    await page.waitForTimeout(4500)
  }
  const state = async () => {
    const fields = await dlg.locator('input, textarea').evaluateAll((els) =>
      els.map((e) => ({ ph: e.getAttribute('placeholder') || '', val: (e.value || '').trim() })))
    const g = (kw) => (fields.find((f) => f.ph.includes(kw)) || {}).val || ''
    const hint = (await dlg.locator('.demo-actions').innerText().catch(() => '')).replace(/\s+/g, ' ')
    const buttons = await dlg.locator('.demo-actions button').allInnerTexts()
    return { plate: g('车牌'), card: g('卡号'), driver: g('没刷卡时填'), codes: g('召回货批次'), hint, buttons }
  }

  // ── ① 到达确认：示例值必须来自那趟收运运单 ──
  await pickDevice('ARRIVE-001')
  const hasArrive = await pickChannel('到达确认')
  check(hasArrive, '① 车载终端有「到达确认」通道')
  await fill()
  const arrive = await state()
  if (canDemoArrive) {
    check(arrive.plate === leg.vehicleNo, '① 到达确认填的车牌 = 那趟收运运单的车牌',
      `表单=${arrive.plate || '(空)'} 期望=${leg.vehicleNo}`)
    check(/收运运单|召回货/.test(arrive.hint), '① 提示指向召回链', arrive.hint.slice(0, 60))
  } else {
    // 收运单都已全站到齐（等着无害化签收）：到达通道没有可刷的站，不许乱填别家的车
    check(arrive.plate === '' || arrive.plate === leg.vehicleNo,
      '① 收运单都已到站：到达通道不得填出别家的车', `表单=${arrive.plate || '(空)'}`)
    check(/收运运单|到达|签收|在途/.test(arrive.hint), '① 提示说明当前卡点', arrive.hint.slice(0, 80))
  }
  check(!/处理计划也是空的|处理计划是空的/.test(arrive.hint), '① 到达确认不得声称处理计划为空', arrive.hint.slice(0, 80))

  // ── ② 发车确认：不得再谎称处理计划为空，要指向那趟车 ──
  await pickChannel('发车确认')
  await fill()
  const depart = await state()
  if (pendingLeg) {
    const departPointsToLeg = depart.hint.includes(pendingLeg.transportNo) || depart.plate === pendingLeg.vehicleNo
    check(departPointsToLeg, '② 发车确认指向待发车的收运运单（或直接取到它）', `提示=${depart.hint.slice(0, 90)}`)
  } else {
    check(!/处理计划也是空的/.test(depart.hint), '② 无待发车单时不得谎称处理计划为空', depart.hint.slice(0, 80))
  }
  check(!/处理计划也是空的|处理计划是空的/.test(depart.hint), '② 发车确认不得声称处理计划为空', depart.hint.slice(0, 80))
  if (depart.hint.includes('没有「待发车」的车可填')) {
    check(depart.buttons.includes('切到「到达确认」'), '② 缺待发车时给出「切到到达确认」按钮', JSON.stringify(depart.buttons))
  }

  // ── ③ 到店扫码：必须填出这趟车的车牌 + 这一站待收的召回货批次 ──
  // 2026-11-05 二次报障：这一步原先在「车在途、门店还没刷到店」时只留空表单 + 一个按钮，
  // 点「填充示例值」什么都看不到。现在缺的上游动作（发车 / 到店刷卡）就地补齐后再填 ——
  // 补的是设备本来就会做的那次上报，所以这里同时断言「补齐」被写在提示里（写操作要可见）。
  await pickDevice('RECALL-SCAN-001')
  await fill()
  const collect = await state()
  check(!/处理计划是空的/.test(collect.hint), '③ 到店扫码不得声称处理计划为空', collect.hint.slice(0, 80))
  if (canDemoArrive) {
    check(collect.plate === leg.vehicleNo, '③ 到店扫码填的车牌 = 那趟收运运单的车牌',
      `表单=${collect.plate || '(空)'} 期望=${leg.vehicleNo}`)
    const collected = /都收完了/.test(collect.hint)
    check(collected || collect.codes.includes('SP-'), '③ 到店扫码填出这一站待收的召回货批次',
      `批次=${collect.codes || '(空)'} 提示=${collect.hint.slice(0, 60)}`)
    check(collected || /待收的 \d+ 批召回货/.test(collect.hint), '③ 提示点名这一站有几批待收', collect.hint.slice(0, 80))
  } else {
    // 所有收运单的货都收完了（链条走到最后一步）：不得留半张表单，要指向「处理签收」
    check(collect.plate === '' && collect.codes === '',
      '③ 货已收完时不留半张表单', `车牌=${collect.plate || '(空)'} 批次=${collect.codes || '(空)'}`)
    check(/收运运单/.test(collect.hint), '③ 提示点名收运运单', collect.hint.slice(0, 90))
    check(/签收|收完/.test(collect.hint), '③ 提示指向下一步（无害化处理中心签收）', collect.hint.slice(0, 90))
  }
  if (/就地补齐/.test(collect.hint)) {
    check(/到店刷卡|刷卡发车/.test(collect.hint), '③ 就地补齐时说明补的是哪次设备动作', collect.hint.slice(-90))
  }

  // ── ④ 装车扫码：说明召回货不走装车口，并指向到店扫码 ──
  await pickDevice('RETURN-SCAN-001')
  await fill()
  const load = await state()
  check(!/处理计划是空的/.test(load.hint), '④ 装车扫码不得声称处理计划为空', load.hint.slice(0, 80))
  check(load.hint.includes(leg.transportNo) || /召回/.test(load.hint), '④ 装车扫码说明召回货的去向',
    load.hint.slice(0, 90))
  if (/收运运单/.test(load.hint)) {
    check(load.buttons.includes('切到「到店扫码收货」'), '④ 装车扫码给出「切到到店扫码收货」按钮', JSON.stringify(load.buttons))
  }

  check(errors.length === 0, '⑤ 页面无脚本报错', errors.join(' | ').slice(0, 140))
  await page.screenshot({ path: 'logs/page-ingest-recall-sample.png', fullPage: false })
  await browser.close()
}

try {
  await main()
} catch (e) {
  console.log('❌ 执行异常：' + String(e.message || e).slice(0, 200))
  fail++
}
console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
process.exit(fail ? 1 : 0)
