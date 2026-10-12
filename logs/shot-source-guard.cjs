const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const OUT = 'E:/pork-traceability/logs/'

;(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })
  const errs = []
  page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)) })
  page.on('pageerror', (e) => errs.push(String(e).slice(0, 300)))

  // 登录页的验证码是 canvas 画的，hook fillText 把字符抓出来
  await page.addInitScript(() => {
    window.__cap = []
    const orig = HTMLCanvasElement.prototype.getContext
    HTMLCanvasElement.prototype.getContext = function (...args) {
      const ctx = orig.apply(this, args)
      if (ctx && !ctx.__hooked) {
        ctx.__hooked = true
        const of = ctx.fillText
        ctx.fillText = function (t, x, y) { window.__cap.push(String(t)); return of.call(this, t, x, y) }
      }
      return ctx
    }
  })

  await page.goto('http://localhost/', { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(2500)
  const cap = await page.evaluate(() => window.__cap.filter((t) => t.length === 1).slice(-4).join(''))
  const inputs = page.locator('input:visible')
  await inputs.nth(0).fill('admin')
  await page.locator('input[type=password]:visible').first().fill('123456')
  await inputs.nth(2).fill(cap)
  await page.locator('button:has-text("登 录")').click()
  await page.waitForTimeout(4000)
  console.log('登录后 URL:', page.url())

  // ---------- 1) 关键环节页面：人工新增入口已关闭 ----------
  for (const [path, name] of [
    ['/admin/slaughter/inspect', 'inspect'],
    ['/admin/slaughter/stamp', 'stamp'],
  ]) {
    await page.goto('http://localhost' + path, { waitUntil: 'load', timeout: 30000 })
    await page.waitForTimeout(3500)
    const alert = page.locator('.source-guard-bar .el-alert')
    const visible = await alert.count()
    const text = visible ? (await alert.first().innerText()).replace(/\s+/g, ' ').trim() : '(未找到管控提示条)'
    console.log(`\n[${name}] 管控提示条数量=${visible}\n  ${text}`)
    const addBtn = await page.locator('button:has-text("新增")').count()
    console.log(`  页面上「新增」按钮数量=${addBtn}（期望 0）`)
    await page.screenshot({ path: `${OUT}source-guard-${name}.png`, fullPage: false })
  }

  // ---------- 2) 设备接入 → 来源管控页签 ----------
  await page.goto('http://localhost/admin/ingest/monitor', { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(4000)
  const tab = page.locator('.el-tabs__item:has-text("来源管控")')
  if (await tab.count()) {
    await tab.first().click()
    await page.waitForTimeout(2500)
    const table = page.locator('.el-table').last()
    const headers = await table.locator('.el-table__header-wrapper th').allTextContents()
    console.log('\n[来源管控] 表头:', headers.map((h) => h.trim()).filter(Boolean).join(' | '))
    const rows = await table.locator('.el-table__body-wrapper tbody tr').allTextContents()
    console.log(`  行数=${rows.length}`)
    rows.forEach((r) => console.log('   ', r.replace(/\s+/g, ' ').trim().slice(0, 150)))
  } else {
    console.log('\n[来源管控] 未找到页签')
  }
  await page.screenshot({ path: `${OUT}source-guard-policy.png`, fullPage: true })

  if (errs.length) console.log('\n控制台错误:', errs.slice(0, 5))
  await browser.close()
})().catch((e) => { console.error('截图失败:', e.message); process.exit(1) })
