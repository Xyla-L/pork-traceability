/**
 * AI 助手（问答抽屉）页面验收。
 *
 * 断言的是「功能形态」而不是「回答内容」：
 *   入口按钮在 → 抽屉能拉开 → 快捷问题能点 → 流式有字 → 结束能收尾。
 * 不校验模型输出文字，避免模型换个措辞就红。
 *
 * 用法：node logs/shot-ai-chat.mjs
 */
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('playwright-core')

const BASE = process.env.ADMIN_BASE || 'http://localhost/admin'
const API = process.env.API_BASE || 'http://localhost/api/v1'
const OUT = import.meta.dirname

const result = { pass: 0, fail: 0 }
function check(name, ok, extra = '') {
  if (ok) {
    result.pass++
    console.log(`  ✓ ${name}${extra ? ' — ' + extra : ''}`)
  } else {
    result.fail++
    console.log(`  ✗ ${name}${extra ? ' — ' + extra : ''}`)
  }
}

// 1) 拿 token（走网关，和前端同一条链路）
const loginRes = await fetch(`${API}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
})
const login = await loginRes.json()
if (!login?.data?.token) {
  console.error('登录失败，无法继续：', login?.message)
  process.exit(1)
}

const browser = await chromium.launch({ channel: 'msedge', headless: true }).catch(() =>
  chromium.launch({ headless: true })
)
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
await page.evaluate(
  ([t, u]) => {
    localStorage.setItem('trace_admin_token', t)
    localStorage.setItem('trace_admin_user', JSON.stringify(u))
  },
  [login.data.token, login.data.user || { username: 'admin', realName: '系统管理员', role: 'ADMIN' }]
)

await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })

// 2) 悬浮入口
const fab = page.locator('.ai-fab')
const fabVisible = await fab.isVisible().catch(() => false)
check('右下角有 AI 助手入口', fabVisible)

await fab.click()
await page.waitForSelector('.chat-shell', { timeout: 15000 })
check('抽屉能拉开', await page.locator('.chat-shell').isVisible())

const chipCount = await page.locator('.chip').count()
check('首屏有快捷问题', chipCount >= 3, `${chipCount} 个`)
await page.screenshot({ path: `${OUT}/shot-ai-chat-intro.png` })

// 3) 点一个快捷问题，观察流式。
//    注意挑生成快的那条：「今天有哪些异常」会带出举报+临期两张大表，
//    模型要输出两千字以上，几分钟才完，不适合做验收等待基准。
await page.locator('.chip').nth(1).click()
await page.waitForSelector('.msg.user', { timeout: 10000 })
check('用户消息已上屏', true)

const streamingOk = await page
  .waitForFunction(
    () => {
      const el = document.querySelector('.msg.ai .ai-markdown')
      return !!el && el.innerText.trim().length > 20
    },
    { timeout: 180000 }
  )
  .then(() => true)
  .catch(() => false)
check('回答开始流式输出', streamingOk)
await page.screenshot({ path: `${OUT}/shot-ai-chat-streaming.png` })

// 4) 等结束：sending 时按钮是「停止」，结束后变回「发送」——用这个状态判定收尾
const doneOk = await page
  .waitForFunction(
    () => {
      const stopBtn = Array.from(document.querySelectorAll('.chat-input button')).find((b) =>
        (b.textContent || '').includes('停止')
      )
      return !stopBtn
    },
    { timeout: 300000, polling: 1000 }
  )
  .then(() => true)
  .catch(() => false)
check('回答正常收尾', doneOk)

const answerLen = await page
  .locator('.msg.ai .ai-markdown')
  .last()
  .evaluate((el) => el.innerText.trim().length)
  .catch(() => 0)
check('回答有内容', answerLen > 50, `${answerLen} 字`)

const errCount = await page.locator('.msg.ai .err-text').count()
check('没有报错气泡', errCount === 0)

await page.screenshot({ path: `${OUT}/shot-ai-chat-done.png` })

await browser.close()

console.log(`\n结果：${result.pass} 通过 / ${result.fail} 失败`)
process.exit(result.fail === 0 ? 0 : 1)
