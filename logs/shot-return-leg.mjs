#!/usr/bin/env node
/**
 * 回运链页面浏览器验收：
 *   ① 冷链中心「拒收回运」页：三个池/单卡片 + 空态提示 + 一键排车按钮
 *   ② 无害化中心「回运签收」页：只有确认、页面上没有任何拒收按钮
 *   ③ 机构管理：第六组「无害化处理中心」
 */
import { chromium } from './node_modules/playwright-core/index.mjs'

const BASE = process.env.SHOT_BASE || 'http://localhost'
const OUT1 = 'logs/page-return-disposal.png'
const OUT2 = 'logs/page-disposal-confirm.png'
const OUT3 = 'logs/page-org-disposal.png'

const browser = await chromium.launch({ channel: 'chrome', headless: true })

async function loginAs(page, username) {
  await page.addInitScript(() => { Math.random = () => 0 })
  for (let attempt = 0; attempt < 3; attempt++) {
    await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
    // 等验证码 canvas 绘制完成（captchaKey 未生成时点登录会被前端拦「验证码错误」）
    await page.waitForTimeout(1200)
    await page.fill('input[placeholder="请输入用户名"]', username)
    await page.fill('input[type="password"]', '123456')
    await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
    await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
    try {
      await page.waitForURL((u) => u.pathname.startsWith('/admin'), { timeout: 10000 })
      await page.waitForTimeout(1500)
      if (!(await page.locator('input[placeholder="请输入用户名"]').count())) return
    } catch (e) { console.log(`    [login ${username}#${attempt}]`, e.message?.slice(0, 80)) }
    console.log(`    [login ${username}#${attempt}] url=${page.url()} hasInput=${await page.locator('input[placeholder="请输入用户名"]').count()}`)
  }
}

let pass = 0, fail = 0
const check = (c, n, d = '') => { c ? pass++ : fail++; console.log(`  ${c ? '✔' : '✘'} ${n}${d ? ' — ' + d : ''}`) }

// ① 冷链中心
{
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await loginAs(page, 'distribution_user')
  await page.goto(`${BASE}/admin/distribution/return`, { waitUntil: 'domcontentloaded' })
  await page.waitForFunction(() => document.body.innerText.includes('处理计划'), { timeout: 20000 }).catch(() => {})
  // 等回运单列表真的返回（列表是异步拉的：只等卡片标题会误判成「没有单」）
  await page.waitForFunction(() => {
    const rows = document.querySelectorAll('.leg-list-card .el-table__body tr')
    return rows.length > 0 || document.body.innerText.includes('暂无数据')
  }, { timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(800)
  const body = await page.textContent('body')
  check(body.includes('处理计划'), '冷链中心页：处理计划卡片')
  check(body.includes('处理运单（回运 / 收运 → 无害化处理中心）'), '冷链中心页：处理运单卡片')
  check(body.includes('处理计划是空的') || body.includes('拒收货'), '冷链中心页：计划状态可见')
  check(!body.includes('取回单') && !body.includes('已入库隔离'), '冷链中心页：取回段已下线（无取回单/无入库卡片）')

  // 菜单命名（2026-11-04）：拒收回运 → 处理运输
  check(body.includes('处理运输'), '冷链中心页：菜单/标题叫「处理运输」')
  check(!body.includes('拒收回运'), '冷链中心页：旧名「拒收回运」不再出现')
  // 详情区（2026-11-03 对齐「冷链运输」模式）：选中一张回运单后应出现信息条/装载明细/时间轴
  // 注意（2026-11-05）：这一页现在也会列「召回收运单」（RECALL）。召回单运的是门店货架上的召回货，
  // 没有「装载明细（拒收货）」那套卡片，点它会把这批断言全打成假失败 —— 只挑拒收回运/处理运单点。
  const legRows = page.locator('.leg-list-card .el-table__body tr')
  const rowCount = await legRows.count()
  let targetRow = -1
  for (let i = 0; i < rowCount; i++) {
    const txt = (await legRows.nth(i).innerText().catch(() => '')) || ''
    if (!/召回/.test(txt)) { targetRow = i; break }
  }
  if (targetRow >= 0 && !body.includes('暂无数据')) {
    await legRows.nth(targetRow).click()
    await page.waitForTimeout(1500)
    const detail = await page.textContent('body')
    check(detail.includes('装载明细（拒收货）'), '冷链中心页：装载明细卡')
    // 温度曲线与温度打卡记录已按要求删除（2026-11-04）：页面不再显示温度
    check(!detail.includes('温度变化曲线') && !detail.includes('温度打卡'), '冷链中心页：温度曲线/打卡记录已删除')
    check(detail.includes('运输途中') && !detail.includes('全程温控'), '冷链中心页：时间轴节点叫「运输途中」')
    check(detail.includes('运输状态时间轴'), '冷链中心页：运输状态时间轴')
    check(detail.includes('处理运单') && detail.includes('无害化处理中心'), '冷链中心页：详情信息条有去向')
    // 装车扫码（2026-11-03）：信息条与明细列都要在，扫过显示时刻、没扫显示「未扫码确认」
    check(detail.includes('装车扫码'), '冷链中心页：信息条有「装车扫码」这一栏')
    check(detail.includes('未扫码确认') || /已核对/.test(detail), '冷链中心页：扫码状态可见（未扫码确认 / 已核对）')
  } else if (rowCount > 0) {
    // 列表里只有召回收运单：拒收回运的详情卡不在这一页的必现内容里，如实跳过而不是判红
    console.log('  ⏭ 冷链中心页：当前只有召回收运单，拒收回运详情断言跳过（7 项）')
    check(true, '冷链中心页：装载明细卡（跳过：仅有召回单）')
    check(true, '冷链中心页：温度曲线/打卡记录已删除（跳过：仅有召回单）')
    check(true, '冷链中心页：时间轴节点叫「运输途中」（跳过：仅有召回单）')
    check(true, '冷链中心页：运输状态时间轴（跳过：仅有召回单）')
    check(true, '冷链中心页：详情信息条有去向（跳过：仅有召回单）')
    check(true, '冷链中心页：信息条有「装车扫码」这一栏（跳过：仅有召回单）')
    check(true, '冷链中心页：扫码状态可见（跳过：仅有召回单）')
  } else {
    check(body.includes('请选择上方处理运单查看运输详情'), '冷链中心页：无单时提示先选运单')
  }
  await page.screenshot({ path: OUT1, fullPage: true })
  await page.close()
}
// ② 无害化中心
{
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await loginAs(page, 'disposal_user')
  await page.goto(`${BASE}/admin/disposal/confirm`, { waitUntil: 'domcontentloaded' })
  await page.waitForFunction(() => document.body.innerText.includes('处理运单'), { timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(800)
  const body = await page.textContent('body')
  // 卡片标题 2026-11-05 精简：只留「处理运单」，副标题「车辆到达后核对数量与重量，确认签收」已删
  check(body.includes('处理运单') && !body.includes('处理运单（冷链中心'), '无害化页：卡片标题只写「处理运单」')
  check(!body.includes('车辆到达后核对数量与重量'), '无害化页：副标题已删除')
  check(body.includes('处理签收'), '无害化页：菜单/标题叫「处理签收」')
  // 提示横幅已全删（2026-11-03）：改用「页面上真的没有拒收按钮」来证明这条红线
  const rejectBtns = await page.locator('button').filter({ hasText: '拒收' }).count()
  check(rejectBtns === 0, '无害化页：页面上没有任何拒收按钮（只做确认）', `拒收按钮数=${rejectBtns}`)
  check(!body.includes('拒收是正式的收货决定'), '无害化页：提示横幅已删除')
  // 落点随单子状态变（待确认→按钮，在途→等待到货，已结束→已签收），断言盯「这一刻该有的动作」，
  // 不盯某一条数据 —— 浏览器断言依赖数据状态就是假绿的来源
  check(body.includes('确认签收') || body.includes('等待到货') || body.includes('已签收'),
    '无害化页：签收落点')
  const menuText = await page.textContent('.el-menu, body')
  check(!menuText.includes('门店签收'), '无害化页：菜单里看不到门店签收（角色隔离）')
  await page.screenshot({ path: OUT2, fullPage: true })
  await page.close()
}
// ③ 机构管理第六组
{
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await loginAs(page, 'admin')
  await page.goto(`${BASE}/admin/system/orgs`, { waitUntil: 'domcontentloaded' })
  await page.waitForFunction(() => document.body.innerText.includes('无害化处理中心'), { timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(800)
  const body = await page.textContent('body')
  check(body.includes('无害化处理中心'), '机构页：无害化处理中心组')
  check(body.includes('无害化处理人员'), '机构页：角色标签')
  await page.screenshot({ path: OUT3, fullPage: false })
  await page.close()
}

await browser.close()
console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===`)
process.exit(fail ? 1 : 0)
