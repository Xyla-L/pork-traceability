/**
 * 车载终端（ARRIVE-001）能不能演示「拒收回运的处理运单」发车？
 * 只做三件事：选设备 → 把接入通道切到「发车确认」→ 点填充示例值，读车牌。
 * 不提交上报（不改数据）。
 *   node logs/verify-depart-sample.mjs
 */
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const TARGET_CHANNEL = process.env.CH || '发车确认'
const pass = []
const fail = []
const check = (name, ok, detail = '') => {
  if (ok) { pass.push(name); console.log(`  [PASS] ${name}`) }
  else { fail.push(name); console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

// ── 自持准备：示例值要有「可对牌刷卡」的运单才填得出来。环境里没有就自己造一张（用完自删）──
const API = process.env.API_URL || 'http://localhost:8080/api/v1'
const SAMPLE_PLATE = process.env.SAMPLE_PLATE || '京A·T0004'
const pad = (n) => String(n).padStart(2, '0')
const nowText = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}` }
async function jfetch(path, opt = {}) {
  try {
    const headers = { ...(opt.body ? { 'Content-Type': 'application/json' } : {}), ...opt.headers }
    const res = await fetch(API + path, { method: opt.method || 'GET', headers, body: opt.body ? JSON.stringify(opt.body) : undefined })
    return await res.json()
  } catch { return null }
}
const cleanupFns = []
async function setupSampleTransport() {
  const token = (await jfetch('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } }))?.data?.token
  if (!token) return
  const H = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
  const wantStatus = TARGET_CHANNEL.includes('到达') ? 2 : 1
  // 环境里已有可借的单（无司机的待发车/在途单）就不造新的：示例值对它填即可
  for (const kind of ['NORMAL', 'DISPOSAL']) {
    const rows = (await jfetch(`/distribution/transports?kind=${kind}&status=${wantStatus}&pageNum=1&pageSize=50`, { headers: H }))?.data?.records || []
    if (rows.some((t) => t.vehicleNo && !t.driverName)) return
  }
  const batch = (await jfetch('/distribution/splits/pending?pageNum=1&pageSize=5', { headers: H }))?.data?.records?.[0]
  if (!batch) return
  let store = null
  const walk = (nodes) => { for (const n of nodes || []) { if (!store && n.type === 'retail') store = n; walk(n.children) } }
  walk((await jfetch('/system/orgs/tree', { headers: H }))?.data || [])
  if (!store) return
  const plan = await jfetch('/distribution/delivery-plans', { method: 'POST', headers: H, body: {
    planDate: nowText().slice(0, 10), storeId: store.id, storeName: store.name, splitBatchIds: [batch.id],
    vehicleNo: null, vehicleType: null, refrigeration: null, driverName: null, driverPhone: null,
    origin: null, plannedDepart: null, plannedArrive: null, remark: '发车示例值回归自持单',
  } })
  const planId = plan?.data?.id
  const load = await jfetch('/ingest/demo/report', { method: 'POST', headers: H, body: {
    deviceNo: 'LOADER-001', bizKey: 'DEPART-SAMPLE-' + Date.now(),
    data: { loadTime: nowText(), vehicleNo: SAMPLE_PLATE, batchNos: [batch.batchNo] },
  } })
  const tid = load?.data?.targetId
  if (load?.data?.status !== 2 || !tid) {
    if (planId) await jfetch(`/distribution/delivery-plans/${planId}/cancel`, { method: 'POST', headers: H, body: { reason: '示例值回归自持清理' } })
    return
  }
  if (TARGET_CHANNEL.includes('到达')) {
    await jfetch('/ingest/demo/report', { method: 'POST', headers: H, body: {
      deviceNo: 'ARRIVE-001', channel: 'DEPART', bizKey: 'DEPART-SAMPLE-D-' + Date.now(),
      data: { departTime: nowText(), vehicleNo: SAMPLE_PLATE, cardNo: '8801' },
    } })
  }
  cleanupFns.push(async () => {
    await jfetch(`/distribution/transports/${tid}`, { method: 'DELETE', headers: H })
    await jfetch(`/distribution/delivery-plans/${planId}/cancel`, { method: 'POST', headers: H, body: { reason: '示例值回归自持清理' } })
  })
}
await setupSampleTransport()

const browser = await chromium.launch({
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  headless: true,
})
try {
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

await page.locator('button:has-text("模拟设备上报")').first().click()
await page.waitForTimeout(1500)

const dlg = page.locator('.el-dialog:has-text("模拟设备上报")').first()
// 设备下拉
await dlg.locator('.el-select').first().click()
await page.waitForTimeout(800)
const dev = page.locator('.el-select-dropdown__item:has-text("ARRIVE-001")').first()
check('① 设备下拉里有车载终端 ARRIVE-001', (await dev.count()) > 0)
await dev.click()
await page.waitForTimeout(1200)

// 通道下拉：一台终端两个动作，默认是到站刷卡
const channelSelect = dlg.locator('.el-form-item:has-text("接入通道") .el-select').first()
const hasChannelSelect = (await channelSelect.count()) > 0
check('② 接入通道可切换（一台终端两个动作）', hasChannelSelect)
if (hasChannelSelect) {
  await channelSelect.click()
  await page.waitForTimeout(600)
  const departOpt = page.locator(`.el-select-dropdown__item:visible:has-text("${TARGET_CHANNEL}")`).first()
  check(`③ 通道选项里有「${TARGET_CHANNEL}」`, (await departOpt.count()) > 0)
  await departOpt.click()
  await page.waitForTimeout(1000)
}
const dlgText0 = String(await dlg.textContent())
// 通道切换以「表单字段跟着换」为准：el-select 的选中值在 input.value 里，不在 textContent 里
const expectField = TARGET_CHANNEL.includes('到达') ? '到达时间' : '发车时间'
check(`④ 通道已切为${TARGET_CHANNEL}`, dlgText0.includes(expectField), dlgText0.slice(0, 160))

// 填充示例值
const fillBtn = dlg.locator('button:has-text("填充示例值")').first()
await fillBtn.click()
await page.waitForTimeout(8000)

const plateInput = dlg.locator('input[placeholder*="车牌"]').first()
const plate = String(await plateInput.inputValue()).trim()
console.log(`  → 示例车牌：${plate}`)
check('⑤ 车牌自动填充（可对处理运单刷卡）', plate.length > 0, `实际="${plate}"`)

const cardInput = dlg.locator('input[placeholder*="卡"]').first()
const card = String(await cardInput.inputValue()).trim()
console.log(`  → 示例卡号：${card}`)
check('⑥ 卡号也已填（司机刷卡）', card.length > 0, `实际="${card}"`)

const text = String(await dlg.textContent())
console.log('  → 提示：' + (text.match(/取到[^。]*。/)?.[0] || text.slice(-160)))
check('⑦ 提示未说找不到运单', !text.includes('没有「待发车」') && !text.includes('名下没有在途'), '')

if (process.env.SHOT) {
  await dlg.screenshot({ path: import.meta.dirname + '/page-depart-sample.png' })
  console.log('  → 截图：logs/page-depart-sample.png')
}

await browser.close()
} finally {
  for (const fn of cleanupFns) { try { await fn() } catch { /* 清理失败不掩盖断言结果 */ } }
}
console.log(`\n结果：${pass.length}/${pass.length + fail.length} 通过，${fail.length} 失败`)
if (fail.length) { fail.forEach((f) => console.log('  FAIL: ' + f)); process.exit(1) }
