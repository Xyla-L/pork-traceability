/**
 * 入口可见性实测：智能助手只对管理员出现。
 *
 * 三件事必须同时成立，缺一条就是「藏了但没真藏」：
 *   ① 管理员能看到图标，点开抽屉正常
 *   ② 非管理员页面里根本没有这个图标（不是灰掉、不是点了报错）
 *   ③ 非管理员即使知道接口，也拿不到数据（这条由 verify-agent-roles.mjs 锁）
 *
 *   node logs/verify-agent-entry.mjs
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

const login = async (user) => {
  const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
  await page.addInitScript(() => { Math.random = () => 0 })
  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(1200)
  await page.fill('input[placeholder="请输入用户名"]', user)
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button.login-btn').click()
  await page.waitForTimeout(5000)
  return page
}

// ---------------------------------------------------------------- 管理员
console.log('\n== 1) 管理员：有入口、能打开 ==')
const admin = await login('admin')
const adminIcons = await admin.locator('.topbar-icon').count()
check('管理员顶栏存在助手图标', adminIcons >= 2, `topbar-icon=${adminIcons}`)

await admin.locator('.topbar-icon').first().click()
await admin.waitForTimeout(1200)
const drawerText = await admin.locator('.el-drawer').first().innerText().catch(() => '')
check('点开是智能助手抽屉', /智能助手|我能查/.test(drawerText), drawerText.slice(0, 60))
check('空态列出了能力（后端按角色过滤）', /检疫|临期|追溯|接入/.test(drawerText), drawerText.slice(0, 80))
await admin.screenshot({ path: path.join(OUT_DIR, 'agent-entry-admin.png') })

// ---------------------------------------------------------------- 非管理员
console.log('\n== 2) 非管理员：页面上没有这个入口 ==')
for (const [user, label] of [['farm_user', '养殖户'], ['retail_user', '零售商']]) {
  const page = await login(user)
  const icons = await page.locator('.topbar-icon').count()
  const hasAssistant = await page.locator('text=智能助手').count()
  check(`${label}顶栏少一个图标`, icons === adminIcons - 1, `icons=${icons} admin=${adminIcons}`)
  check(`${label}页面无「智能助手」字样`, hasAssistant === 0, `matches=${hasAssistant}`)
  // 点第一个图标不该冒出助手抽屉（那是通知铃铛的位置）
  await page.locator('.topbar-icon').first().click().catch(() => {})
  await page.waitForTimeout(900)
  const d = await page.locator('.el-drawer').count()
  check(`${label}点第一个图标不会打开助手`, d === 0, `drawer=${d}`)
  await page.screenshot({ path: path.join(OUT_DIR, `agent-entry-${user}.png`) })
  await page.close()
}

await admin.close()
await browser.close()

console.log(`\n结果：${pass.length} 通过 / ${fail.length} 失败`)
process.exit(fail.length === 0 ? 0 : 1)
