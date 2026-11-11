/**
 * 诊断：疫苗记录 / 产地检疫 两页展开逐头后，「查看凭证」「作废」能不能用。
 * 只观察、不改数据（作废点开弹窗后取消）。
 */
import { createRequire } from 'node:module'
import path from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const OUT = import.meta.dirname

;(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  })
  const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
  const errors = []
  const apiFails = []
  page.on('pageerror', (e) => errors.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errors.push('[console] ' + m.text()) })
  page.on('response', (r) => {
    if (r.status() >= 400) apiFails.push(`${r.status()} ${r.request().method()} ${r.url()}`)
  })
  await page.addInitScript(() => { Math.random = () => 0 })

  await page.goto(`${BASE}/login`, { waitUntil: 'load' })
  await page.waitForTimeout(1500)
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button.login-btn').click()
  await page.waitForTimeout(6000)

  // ---------------------------------------------------------------- 疫苗页
  await page.goto(`${BASE}/admin/farm/vaccines`, { waitUntil: 'load' })
  await page.waitForTimeout(3500)
  const rows = page.locator('.el-table__body-wrapper tbody tr.el-table__row')
  console.log('疫苗页主行数:', await rows.count())
  // 点第一行行首的展开箭头
  await page.locator('.el-table__expand-icon').first().click()
  await page.waitForTimeout(1200)
  const panelText = String(await page.textContent('body'))
  console.log('展开面板出现:', panelText.includes('本次免疫覆盖'))
  const viewBtn = page.getByRole('button', { name: '查看凭证' }).first()
  console.log('「查看凭证」按钮数量:', await page.getByRole('button', { name: '查看凭证' }).count())
  if (await viewBtn.count() > 0) {
    await viewBtn.click()
    await page.waitForTimeout(2500)
    const dlg = await page.locator('.el-dialog:visible').count()
    const dlgText = dlg ? String(await page.locator('.el-dialog:visible').first().textContent()) : ''
    console.log('凭证弹窗打开:', dlg > 0, '| 内容片段:', dlgText.replace(/\s+/g, ' ').slice(0, 160))
    await page.screenshot({ path: path.join(OUT, 'diag-vaccine-cert.png') })
    // 关闭
    await page.locator('.el-dialog:visible .el-dialog__headerbtn').click().catch(() => {})
    await page.waitForTimeout(600)
  }
  const voidBtn = page.getByRole('button', { name: '作废', exact: true }).first()
  console.log('「作废」按钮数量:', await page.getByRole('button', { name: '作废', exact: true }).count())
  if (await voidBtn.count() > 0) {
    await voidBtn.click()
    await page.waitForTimeout(1200)
    const mb = await page.locator('.el-message-box:visible').count()
    console.log('作废确认框打开:', mb > 0)
    await page.screenshot({ path: path.join(OUT, 'diag-vaccine-void.png') })
    if (mb) await page.locator('.el-message-box:visible .el-button').first().click().catch(() => {})
    await page.waitForTimeout(500)
  }

  // ---------------------------------------------------------------- 检疫页
  apiFails.length = 0
  await page.goto(`${BASE}/admin/farm/quarantine`, { waitUntil: 'load' })
  await page.waitForTimeout(3500)
  console.log('\n检疫页主行数:', await page.locator('.el-table__body-wrapper tbody tr.el-table__row').count())
  await page.locator('.el-table__expand-icon').first().click()
  await page.waitForTimeout(1200)
  const qBtn = page.getByRole('button', { name: /查看/ }).first()
  console.log('检疫展开后「查看/作废」按钮数量:', await page.getByRole('button', { name: /查看/ }).count())
  if (await qBtn.count() > 0) {
    await qBtn.click()
    await page.waitForTimeout(2500)
    const dlg = await page.locator('.el-dialog:visible').count()
    const dlgText = dlg ? String(await page.locator('.el-dialog:visible').first().textContent()) : ''
    console.log('检疫弹窗打开:', dlg > 0, '| 内容片段:', dlgText.replace(/\s+/g, ' ').slice(0, 200))
    await page.screenshot({ path: path.join(OUT, 'diag-quarantine-dialog.png') })
    if (dlg) await page.locator('.el-dialog:visible .el-dialog__headerbtn').click().catch(() => {})
  }
  console.log('\n失败接口:', apiFails.length ? apiFails.join('\n  ') : '无')
  console.log('页面错误:', errors.length ? errors.slice(0, 6).join('\n  ') : '无')
  await browser.close()
})()
