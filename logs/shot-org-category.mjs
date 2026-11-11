#!/usr/bin/env node
/**
 * 机构管理「按角色域分类」的浏览器验收：分组数、角色标签、家数、截图。
 * 用法：node logs/shot-org-category.mjs
 */
import { chromium } from 'playwright-core'

const BASE = process.env.BASE || 'http://localhost:5173'
const OUT = process.env.OUT || 'logs/org-category.png'

let pass = 0
let fail = 0
const ok = (n, d = '') => { pass++; console.log(`  ✔ ${n}${d ? ' — ' + d : ''}`) }
const bad = (n, d = '') => { fail++; console.log(`  ✘ ${n}${d ? ' — ' + d : ''}`) }
const check = (c, n, d = '') => c ? ok(n, d) : bad(n, d)

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
// 验证码由前端本地生成，Math.random 固定后恒为 AAAA
await page.addInitScript(() => { Math.random = () => 0 })

try {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[type="password"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
  await page.waitForTimeout(3000)

  await page.goto(`${BASE}/admin/system/orgs`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.tree-group', { timeout: 15000 })
  await page.waitForTimeout(800)

  const groups = await page.locator('.tree-group').all()
  check(groups.length === 5, '左侧按角色域分成 5 组', `实际 ${groups.length} 组`)

  const groupTexts = []
  for (const g of groups) groupTexts.push((await g.innerText()).replace(/\s+/g, ' ').trim())
  console.log('  分组：' + groupTexts.join(' | '))

  const expected = ['养殖场', '屠宰场', '配送中心', '零售门店', '监管机构']
  check(expected.every((name) => groupTexts.some((t) => t.startsWith(name))),
    '五个环节齐全（养殖→屠宰→配送→零售→监管）')

  const roleTags = ['养殖人员', '屠宰人员', '配送人员', '零售人员', '监管人员']
  check(roleTags.every((r) => groupTexts.some((t) => t.includes(r))),
    '每组标注对应角色', roleTags.join('/'))

  const retailText = groupTexts.find((t) => t.startsWith('零售门店')) || ''
  check(/14\s*家/.test(retailText), '零售门店组家数与数据一致', retailText)

  // 组内机构数 = 机构总数（44+3+1+14+1 = 63）
  const counts = groupTexts.map((t) => Number((t.match(/(\d+)\s*家/) || [])[1] || 0))
  check(counts.reduce((a, b) => a + b, 0) === 63, '分组家数之和 = 机构总数 63', String(counts.join('+')))

  // 点一家门店看详情：所属环节 + 归属说明
  await page.click('.el-tree-node__content:has-text("零售门店")')
  await page.waitForTimeout(300)
  const firstStore = page.locator('.el-tree-node:has(.tree-group):has-text("零售门店") .el-tree-node__content').nth(1)
  if (await firstStore.count()) {
    await firstStore.click()
    await page.waitForTimeout(500)
    const detail = (await page.locator('.org-detail-panel').innerText()).replace(/\s+/g, ' ')
    check(detail.includes('所属环节') && detail.includes('零售人员'), '详情显示所属环节=零售人员')
    check(/上级 \/ 归属/.test(detail), '详情区分「上级 / 归属」', detail.slice(0, 80))
  }

  await page.screenshot({ path: OUT, fullPage: true })
  console.log(`\n  截图：${OUT}`)
} finally {
  await browser.close()
}

console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===\n`)
process.exit(fail ? 1 : 0)
