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

  await page.goto('http://localhost/admin/ingest/monitor', { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(5000)

  const tables = page.locator('.el-table')
  const n = await tables.count()
  console.log('页面表格数:', n)
  for (let i = 0; i < n; i++) {
    const rows = await tables.nth(i).locator('.el-table__body-wrapper tbody tr').allTextContents()
    const headers = await tables.nth(i).locator('.el-table__header-wrapper th').allTextContents()
    console.log(`\n--- 表 ${i} 表头: ${headers.map((h) => h.trim()).filter(Boolean).join(' | ')}`)
    console.log(`    行数=${rows.length}`)
    rows.slice(0, 4).forEach((r, j) => {
      const text = r.replace(/\s+/g, ' ').trim().slice(0, 160)
      const bad = /[ÃÂåæçèé]|鍐|棌|閿|鐨/.test(text)
      console.log(`    行${j + 1}: ${text}${bad ? '   <== 乱码!' : ''}`)
    })
  }

  await page.screenshot({ path: OUT + 'page-ingest-monitor.png', fullPage: true })
  if (n > 0) {
    await tables.nth(n - 1).screenshot({ path: OUT + 'page-ingest-table.png' }).catch(() => {})
  }
  console.log('\nconsole/page errors:', errs.slice(0, 10).join(' | ') || '(无)')
  await browser.close()
})().catch((e) => { console.error('FATAL', e); process.exit(1) })
