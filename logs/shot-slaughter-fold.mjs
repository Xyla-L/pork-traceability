/**
 * 屠宰四张列表「按批次折叠」界面验收：
 *   主行是一个批次（批次号 + 头数 + 耳标区间 + 结论），展开才是逐头明细。
 *
 * 不造数据：直接看演示台账里的记录是否已按批折叠。
 * 运行：node logs/shot-slaughter-fold.mjs
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

const pages = [
  ['entry', '入场查验', 'page-fold-entry.png'],
  ['inspect', '屠宰检验', 'page-fold-inspect.png'],
  ['stamp', '检疫盖章', 'page-fold-stamp.png'],
  ['ractopamine', '瘦肉精检测', 'page-fold-racto.png'],
]

for (const [route, label, file] of pages) {
  await page.goto(`${BASE}/admin/slaughter/${route}`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(3000)
  const body = String(await page.textContent('body'))

  check(`${label}：分页文案改成「共 N 批」`, /共\s*\d+\s*批/.test(body), body.slice(0, 60))
  check(`${label}：说明「按批折叠，点行首箭头看逐头」`, body.includes('按批折叠'))
  check(`${label}：主行有耳标区间列（不再逐头列耳标）`, body.includes('耳标区间'))

  // 展开第一行，应当出现逐头明细表
  const expander = page.locator('.el-table__expand-icon').first()
  const hasRow = await expander.count()
  if (hasRow) {
    await expander.click()
    await page.waitForTimeout(800)
    const after = String(await page.textContent('body'))
    check(`${label}：展开后出现逐头明细`, after.includes('明细'), '')
  }

  const shot = path.join(OUT_DIR, file)
  await page.screenshot({ path: shot })
  console.log(`  [SHOT] ${shot}`)
}

check('界面无脚本运行时错误', errors.length === 0, errors.slice(0, 3).join(' | '))

await browser.close()
console.log(`\n── 结果：${pass.length} 通过 / ${fail.length} 失败 ──`)
if (fail.length) {
  console.log('失败项：' + fail.join('、'))
  process.exit(1)
}
