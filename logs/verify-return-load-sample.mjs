/**
 * 拒收回运装车（RETURN_LOAD）「填充示例值」验收：车牌必须自动填上，且填的是一辆**名下没有
 * 未完成运单**的备案车。
 *
 * 修复前：车牌取「已有回运单上的车牌」，没有回运单就永远是空的；有回运单也是拿一张未完成
 * 运单占着的车去扫，生成处理运单时必被 ActiveTransportGuard 拦。
 *
 * 只点「填充示例值」，不提交上报（不改数据）。
 *   node logs/verify-return-load-sample.mjs
 */
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const DEMO_PLATES = ['京A·12345', '京A·99999', '京B·10086']

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
const page = await browser.newPage({ viewport: { width: 1680, height: 1000 } })
await page.addInitScript(() => { Math.random = () => 0 })

await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 })
await page.waitForTimeout(1500)
await page.fill('input[placeholder="请输入用户名"]', 'admin')
await page.fill('input[placeholder="请输入密码"]', '123456')
await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
await page.locator('button.login-btn').click()
await page.waitForTimeout(6000)

await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'load', timeout: 30000 })
await page.waitForTimeout(3000)

const openDemo = page.locator('button:has-text("模拟设备上报")').first()
check('① 「模拟设备上报」入口存在', (await openDemo.count()) > 0)
await openDemo.click()
await page.waitForTimeout(1500)

const dlg = page.locator('.el-dialog:has-text("模拟设备上报")').first()
await dlg.locator('.el-select').first().click()
await page.waitForTimeout(800)
const deviceOption = page.locator('.el-select-dropdown__item:has-text("RETURN-SCAN-001")').first()
check('② 设备下拉里有回运扫码枪 RETURN-SCAN-001', (await deviceOption.count()) > 0)
await deviceOption.click()
await page.waitForTimeout(1500)

const dlgText = String(await dlg.textContent())
check('③ 通道识别为拒收回运装车', dlgText.includes('拒收回运装车'), dlgText.slice(0, 80))

const fillBtn = dlg.locator('button:has-text("填充示例值")').first()
check('④ 填充示例值按钮存在', (await fillBtn.count()) > 0)
await fillBtn.click()
await page.waitForTimeout(8000)

// 车牌输入框：配置里 placeholder 带「相机识别到的车牌」
const plateInput = dlg.locator('input[placeholder*="相机识别到的车牌"]').first()
const plate = String(await plateInput.inputValue()).trim()
console.log(`  → 示例车牌：${plate}`)
check('⑤ 车牌号自动填充非空', plate.length > 0, `实际="${plate}"`)
check('⑥ 填的是备案演示车（载重/车型查得到）', DEMO_PLATES.includes(plate), `实际="${plate}"`)

const scanInput = dlg.locator('input[placeholder*="扫到的拒收货标签"]').first()
const codes = String(await scanInput.inputValue()).trim()
check('⑦ 货物码也填了（处理计划里的货）', codes.length > 0, `实际="${codes}"`)

const text = String(await dlg.textContent())
check('⑧ 提示语说明这辆车可直接排车', text.includes('名下没有未完成的运单'), text.match(/车牌取[^。]*/)?.[0] || '')

if (process.env.SHOT) {
  await dlg.screenshot({ path: import.meta.dirname + '/page-return-load-sample.png' })
  console.log('  → 截图：logs/page-return-load-sample.png')
}

await browser.close()

console.log(`\n结果：${pass.length}/${pass.length + fail.length} 通过，${fail.length} 失败`)
if (fail.length) { fail.forEach((f) => console.log('  FAIL: ' + f)); process.exit(1) }
