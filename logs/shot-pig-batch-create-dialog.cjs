/**
 * 登记生猪批次弹窗截图（2026-10-26 界面微调验收）。
 * 只读：打开弹窗断言「顶部提示横幅已删」「品种是文本输入而非下拉」，不点提交。
 */
const path = require('path')
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const OUT = process.env.OUT || path.join(__dirname, 'page-pig-batch-create-dialog.png')

;(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  })
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } })
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e)))
  await page.addInitScript(() => { Math.random = () => 0 })

  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(1500)
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button.login-btn').click()
  await page.waitForTimeout(6000)

  await page.goto(`${BASE}/admin/farm/pigs`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(4000)

  const pass = []
  const fail = []
  const check = (name, ok) => (ok ? pass.push(name) : fail.push(name))

  // 打开「登记批次」弹窗
  await page.getByRole('button', { name: /登记批次/ }).first().click()
  await page.waitForTimeout(1200)
  const dialog = page.locator('.el-dialog', { hasText: '登记生猪批次' }).first()
  await dialog.waitFor({ state: 'visible', timeout: 10000 })
  const dlgText = String(await dialog.textContent())

  check('弹窗已打开', await dialog.isVisible())
  check('顶部提示横幅已删除（无 el-alert）', (await dialog.locator('.el-alert').count()) === 0)
  check('横幅文案已不存在', !dlgText.includes('按批次登记，不逐头录入'))

  // 品种表单项：定位 label 为「品种」的 form-item
  const breedItem = dialog.locator('.el-form-item', { hasText: '品种' }).first()
  const breedInput = breedItem.locator('input')
  check('品种是文本输入框', (await breedInput.count()) > 0)
  check('品种无下拉选择器', (await breedItem.locator('.el-select').count()) === 0)
  const ph = await breedInput.first().getAttribute('placeholder')
  check('品种占位提示为输入型（如 大白…）', String(ph || '').includes('大白'))

  // 其他字段仍在（回归：删横幅别把表单带掉）
  check('批次号字段仍在', dlgText.includes('批次号'))
  check('耳标起止字段仍在', dlgText.includes('耳标起号') && dlgText.includes('耳标止号'))
  check('养殖场字段仍在', dlgText.includes('养殖场'))
  check('无脚本错误', errors.length === 0)

  await page.screenshot({ path: OUT, fullPage: false })
  console.log(`结果：通过 ${pass.length} / 失败 ${fail.length}`)
  fail.forEach((f) => console.log('  [FAIL] ' + f))
  console.log('截图：' + OUT)
  await browser.close()
  process.exit(fail.length ? 1 : 0)
})().catch((e) => { console.error('失败：', e); process.exit(1) })
