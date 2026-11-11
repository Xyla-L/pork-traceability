/**
 * 出栏审批：批量审批（通过/驳回二选一）+ 状态列结论标签 实测。
 * ① 主行按钮是「批量审批（N）」，点开是带通过/驳回双选的弹窗；
 * ② 选驳回时提示整批驳回且必须写理由，不写理由提交被拦下；
 * ③ 审批状态列只给结论（全部待审/全部通过/全部驳回），不再把「待审 1」这种计数贴出来；
 *    计数在 tooltip 里（hover 才出现，不在正文）。
 * 不提交、不改数据。
 *
 *   node logs/shot-apply-batch.mjs
 */
import path from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const OUT_DIR = import.meta.dirname

const pass = []
const fail = []
const check = (name, ok, detail = '') => {
  if (ok) { pass.push(name); console.log(`  [PASS] ${name}`) }
  else { fail.push(name); console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

const browser = await chromium.launch({
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  headless: true,
})
const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
await page.addInitScript(() => { Math.random = () => 0 })

await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 })
await page.waitForTimeout(1500)
await page.fill('input[placeholder="请输入用户名"]', 'admin')
await page.fill('input[placeholder="请输入密码"]', '123456')
await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
await page.locator('button.login-btn').click()
await page.waitForTimeout(6000)

// ---------------------------------------------------------------- 出栏审批页
await page.goto(`${BASE}/admin/farm/apply`, { waitUntil: 'load', timeout: 30000 })
await page.waitForTimeout(2500)

let body = String(await page.textContent('body'))
check('① 主行按钮为「批量审批」', body.includes('批量审批'))
check('①b 主行按钮不再叫「批量通过」', !body.includes('批量通过'))

// 审批状态列（第 10 列，索引 9）：只给结论，不带计数
const statusCell = page.locator('.el-table__row').first().locator('td').nth(9)
const statusText = String(await statusCell.textContent()).trim()
console.log(`  [INFO] 审批状态列文本: "${statusText}"`)
check('② 状态列不再出现「待审 1」这类计数', !/待审\s*\d/.test(statusText), statusText)
check('②b 状态列给的是结论标签', /全部待审|待审中|全部通过|全部驳回|含驳回/.test(statusText), statusText)

// hover 标签 → tooltip 里应有计数明细
const statusTag = statusCell.locator('.el-tag').first()
if (await statusTag.count()) {
  await statusTag.hover()
  await page.waitForTimeout(900)
  const tipBody = String(await page.textContent('body'))
  check('③ hover 状态标签可见计数明细', /待审 \d+|已通过 \d+|已驳回 \d+/.test(tipBody))
  await page.mouse.move(10, 10)
  await page.waitForTimeout(300)
}

// ---------------------------------------------------------------- 批量审批弹窗
const batchBtn = page.locator('button:has-text("批量审批")').first()
check('④ 逐头批量审批入口存在', (await batchBtn.count()) > 0)
await batchBtn.click()
await page.waitForTimeout(1200)

const batchDlg = page.locator('.el-dialog:has-text("批量审批")').first()
check('⑤ 批量审批弹窗打开', (await batchDlg.count()) > 0)
let dlgText = String(await batchDlg.textContent())
check('⑥ 弹窗显示本批待审批头数', /待审批\s*\d+\s*头/.test(dlgText.replace(/\s+/g, ' ')), dlgText.slice(0, 80))
check('⑦ 弹窗含「通过/驳回」双选', dlgText.includes('通过') && dlgText.includes('驳回'))

// 选驳回 → 整批驳回警示 + 理由必填
await batchDlg.locator('.el-radio').nth(1).click()
await page.waitForTimeout(500)
dlgText = String(await batchDlg.textContent())
check('⑧ 选驳回出现「全部驳回」警示', dlgText.includes('全部驳回'))
await batchDlg.locator('button:has-text("驳回")').last().click()
await page.waitForTimeout(600)
const pageText = String(await page.textContent('body'))
check('⑨ 不写理由提交驳回被校验拦下', (await batchDlg.count()) > 0 && pageText.includes('驳回时必须填写理由'))
await page.screenshot({ path: path.join(OUT_DIR, 'page-apply-batch-reject.png') })

// 关闭弹窗，不提交
await batchDlg.locator('button:has-text("取 消")').first().click()
await page.waitForTimeout(600)

// ---------------------------------------------------------------- 逐头审批弹窗仍可用
await page.locator('.el-table__expand-icon').first().click()
await page.waitForTimeout(1200)
body = String(await page.textContent('body'))
check('⑩ 展开层提示可单独驳回', body.includes('单独驳回'))

const memberApprove = page.locator('.member-panel button:has-text("审批")').first()
if (await memberApprove.count()) {
  await memberApprove.click()
  await page.waitForTimeout(1200)
  const dlg = page.locator('.el-dialog:has-text("出栏申报审批")').first()
  check('⑪ 逐头审批弹窗含通过/驳回', (await dlg.count()) > 0)
  await dlg.locator('button:has-text("取 消")').first().click()
  await page.waitForTimeout(500)
}

// ---------------------------------------------------------------- 已驳回详情
const rejectedTab = page.locator('.tab-item:has-text("已驳回")').first()
if (await rejectedTab.count()) {
  await rejectedTab.click()
  await page.waitForTimeout(2000)
  const expand2 = page.locator('.el-table__expand-icon').first()
  if (await expand2.count()) { await expand2.click(); await page.waitForTimeout(1000) }
  const viewBtn = page.locator('button:has-text("查看")').first()
  if (await viewBtn.count()) {
    await viewBtn.click()
    await page.waitForTimeout(1200)
    const vBody = String(await page.textContent('body'))
    check('⑫ 已驳回详情含驳回原因', vBody.includes('驳回原因'))
    check('⑬ 已驳回详情含「下一步」整改引导', vBody.includes('整改完成后生猪重新出栏过磅'))
    await page.screenshot({ path: path.join(OUT_DIR, 'page-apply-rejected-detail.png') })
  }
}

await page.screenshot({ path: path.join(OUT_DIR, 'page-apply-list.png') })
console.log(`\n结果: ${pass.length} 通过 / ${fail.length} 失败`)
await browser.close()
if (fail.length > 0) process.exit(1)
