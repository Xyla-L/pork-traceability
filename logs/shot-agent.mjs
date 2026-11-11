/**
 * 智能助手抽屉实测：
 * ① TopBar 有「智能助手」入口，点开抽屉；
 * ② 空态给得出能答的两类问题 + 合规不判断的声明；
 * ③ 快捷问句「接入有哪些失败」真实发出并拿到带队列数的回答；
 * ④ 依据明细可展开，来源标注如实（未配置密钥=直接取数）；
 * ⑤ 「今天天气怎么样」不硬答。
 * 只读问答，不改任何数据。
 *
 *   node logs/shot-agent.mjs
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

// ---------------------------------------------------------------- 打开抽屉
console.log('\n== 1) 入口与空态 ==')
// ChatDotRound 图标是 topbar-right 里第一个 .topbar-icon（通知铃铛/用户头像不挂这个类）
await page.locator('.topbar-right .topbar-icon').first().click()
await page.waitForTimeout(1200)

let drawer = page.locator('.agent')
check('① 抽屉已打开', await drawer.count() > 0)
let body = String(await drawer.textContent())
check('② 空态写明五类能力', body.includes('全链路') && body.includes('待人工处理') && body.includes('申报') && body.includes('运单') && body.includes('工作台'))
check('②b 声明不替用户判断合规', body.includes('判定器'))
check('②c 来源标注如实（模型整理或直接取数）', body.includes('模型整理') || body.includes('直接取数'))

/** 等助手的下一条回答出现（模型响应要几秒，不能写死等待） */
async function waitAnswer(previousCount, timeoutMs = 45000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const n = await drawer.locator('.msg.assistant .bubble').count()
    if (n > previousCount) {
      const last = String(await drawer.locator('.msg.assistant .bubble').nth(n - 1).textContent())
      if (!last.includes('正在取数')) return n
    }
    await page.waitForTimeout(800)
  }
  return -1
}

// ---------------------------------------------------------------- 快捷问句
console.log('\n== 2) 接入诊断问答 ==')
const before = await drawer.locator('.msg.assistant .bubble').count()
await drawer.locator('.quick button', { hasText: '接入有哪些失败' }).click()
const afterDiag = await waitAnswer(before)
check('③ 模型回答已返回', afterDiag > before)
const diagText = afterDiag > 0 ? String(await drawer.locator('.msg.assistant .bubble').last().textContent()) : ''
check('③b 回答提到失败/原因', diagText.includes('失败') || diagText.includes('原因'), diagText.slice(0, 80))
check('③c 来源标注如实', String(await drawer.textContent()).includes('模型整理') ||
  String(await drawer.textContent()).includes('直接取数'))
const detailBtn = drawer.locator('.msg-meta button', { hasText: '依据明细' }).last()
if (await detailBtn.count() > 0) {
  await detailBtn.click()
  await page.waitForTimeout(500)
  body = String(await drawer.textContent())
  check('④ 依据明细展开可见取数结果', body.includes('队列总数') || body.includes('原因'))
} else {
  check('④ 依据明细按钮存在', false, '未找到「依据明细」按钮')
}

// ---------------------------------------------------------------- 分析类问题
console.log('\n== 2b) 出栏申报分析 ==')
await drawer.locator('textarea').fill('出栏申报驳回了多少')
await drawer.locator('.foot-actions button', { hasText: '发送' }).click()
const afterApply = await waitAnswer(afterDiag)
check('⑥ 分析回答已返回', afterApply > afterDiag)
// facts 默认折叠：先展开这条回答的依据明细再断言
const applyDetail = drawer.locator('.msg-meta button', { hasText: '依据明细' }).last()
if (await applyDetail.count() > 0) {
  await applyDetail.click()
  await page.waitForTimeout(500)
}
body = String(await drawer.textContent())
check('⑥b 依据里出现状态分布', body.includes('状态分布'))

// ---------------------------------------------------------------- 答不了的
console.log('\n== 3) 超出能力的问题 ==')
await drawer.locator('textarea').fill('今天天气怎么样')
await drawer.locator('.foot-actions button', { hasText: '发送' }).click()
const afterUnknown = await waitAnswer(afterApply)
body = String(await drawer.textContent())
check('⑤ 不硬答，如实说明接不住', body.includes('接不住'))

await page.screenshot({ path: path.join(OUT_DIR, 'agent-drawer.png'), fullPage: false })
console.log('  截图：logs/agent-drawer.png')

await browser.close()
console.log(`\n结果：${pass.length} 通过 / ${fail.length} 失败`)
process.exit(fail.length === 0 ? 0 : 1)
