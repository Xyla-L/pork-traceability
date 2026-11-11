/**
 * 生猪档案页截图（2026-10-25 批次化改造后的界面验收）。
 * 纯只读：登录 → 打开 /admin/farm/pigs → 断言批次列表可见 → 截图。
 */
const path = require('path')
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const OUT = process.env.OUT || path.join(__dirname, 'page-pig-batch-list.png')

;(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  })
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 } })
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e)))
  // 验证码恒为 AAAA 的既有约定（Math.random=0）
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

  const body = await page.textContent('body')
  const pass = []
  const fail = []
  const check = (name, ok) => (ok ? pass.push(name) : fail.push(name))
  check('登录成功进入管理端', page.url().includes('/admin'), `地址 ${page.url()}`)
  check('页面标题是「生猪档案」', String(body).includes('生猪档案'))
  check('批次列表出现批次号（PB-…）', /PB-/.test(String(body)))
  check('列表带耳标区间列', String(body).includes('耳标区间'))
  check('登记批次按钮存在', String(body).includes('登记批次'))
  check('无脚本错误', errors.length === 0)

  await page.screenshot({ path: OUT, fullPage: false })
  console.log(`结果：通过 ${pass.length} / 失败 ${fail.length}`)
  fail.forEach((f) => console.log('  [FAIL] ' + f))
  console.log('截图：' + OUT)
  await browser.close()
  process.exit(fail.length ? 1 : 0)
})().catch((e) => { console.error('失败：', e); process.exit(1) })
