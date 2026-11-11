/**
 * 状态摘要三态界面验收：
 *  原来「有效 0 / 已作废 1」是在描述一次性动作整组作废，读起来像「还剩 0 条有效」，
 *  现在结论须是「全部作废 / 部分有效 / 全部有效」，计数只在 tooltip 里。
 *
 * 不造数据：直接用演示猪 ET-DEMO-1791194024670（它身上恰好 1 组部分有效 + 若干组全作废）。
 * 运行：node logs/shot-status-tristate.mjs
 */
import path from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const OUT_DIR = import.meta.dirname
const TAG = process.env.DEMO_TAG || 'ET-DEMO-1791194024670'

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

// ---------------------------------------------------------------- 疫苗页
await page.goto(`${BASE}/admin/farm/vaccines`, { waitUntil: 'load', timeout: 30000 })
await page.waitForTimeout(2500)
await page.fill('input[placeholder="请输入生猪耳标号"]', TAG)
await page.locator('button:has-text("搜索")').first().click()
await page.waitForTimeout(2500)

let body = String(await page.textContent('body'))
check('① 出现「全部作废」结论', body.includes('全部作废'))
check('② 出现「部分有效」结论', body.includes('部分有效'))
check('③ 不再出现「有效 0」这种计数当结论的写法', !body.includes('有效 0'), '页面仍有「有效 0」')

// tooltip：hover「部分有效」标签，应看到计数明细
const partial = page.locator('.el-tag:has-text("部分有效")').first()
if (await partial.count()) {
  await partial.hover()
  await page.waitForTimeout(900)
  const tip = String(await page.textContent('body'))
  check('④ hover「部分有效」显示计数明细', /有效 \d+ \/ 已作废 \d+/.test(tip),
    (tip.match(/有效 \d+ \/ 已作废 \d+/) || ['无'])[0])
}
await page.mouse.move(10, 10)
await page.waitForTimeout(400)
const shotVaccine = path.join(OUT_DIR, 'page-status-tristate-vaccine.png')
await page.screenshot({ path: shotVaccine })
console.log(`  [SHOT] ${shotVaccine}`)

// ---------------------------------------------------------------- 检疫页
await page.goto(`${BASE}/admin/farm/quarantine`, { waitUntil: 'load', timeout: 30000 })
await page.waitForTimeout(2500)
body = String(await page.textContent('body'))
check('⑤ 检疫页证状态列存在', body.includes('证状态'))
check('⑥ 检疫页不再出现「有效 0」', !body.includes('有效 0'))
const shotCert = path.join(OUT_DIR, 'page-status-tristate-cert.png')
await page.screenshot({ path: shotCert })
console.log(`  [SHOT] ${shotCert}`)

check('⑦ 无脚本运行时错误', errors.length === 0, errors.slice(0, 3).join(' | '))

await browser.close()
console.log(`\n── 结果：${pass.length} 通过 / ${fail.length} 失败 ──`)
if (fail.length) {
  console.log('失败项：' + fail.join('、'))
  process.exit(1)
}
