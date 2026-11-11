/**
 * 养殖系统同步（FARM_ERP）界面验收：推一批外部批次 → 生猪档案页看「档案来源/日龄」→ 设备接入监控看通道中文名。
 *
 * 为什么截图脚本自己推数据：档案页要证明「养殖系统同步」这个标签真的会出现，
 * 不能靠演示库里碰巧有一条。推的是真报文（走设备密钥），跑完由
 * `bash logs/verify-farm-erp-db.sh --clean` 按业务键清掉。
 *
 * 运行：node logs/shot-farm-erp.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const API = `${BASE}/api/v1`
const DEVICE_KEY = 'farm-erp-demo-key-2026'
const FARM = process.env.FARM_NAME || '示范生态养殖场'
const OUT_DIR = import.meta.dirname

const pass = []
const fail = []
const check = (name, ok, detail = '') => {
  if (ok) { pass.push(name); console.log(`  [PASS] ${name}`) }
  else { fail.push(name); console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

function dayText(offset) {
  const d = new Date(Date.now() + offset * 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} `
    + `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const RUN = Date.now().toString().slice(-8)
const batchNo = `ERP-SHOT-${RUN}`

;(async () => {
  // ---------------------------------------------------------------- 推一批外部批次
  const res = await fetch(`${API}/ingest/device/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Device-Key': DEVICE_KEY },
    body: JSON.stringify({
      channel: 'FARM_ERP',
      bizKey: `${batchNo}-1`,
      reportTime: nowText(),
      sourceRef: 'SHOT-FARM-ERP',
      data: {
        action: 'UPSERT',
        systemCode: 'DEMO_ERP',
        farmName: FARM,
        batchNo,
        breed: '三元杂',
        entryDate: dayText(-20),
        birthDate: dayText(-90),
        source: '自繁',
        earTagStart: `ET-SHW${RUN}001`,
        earTagEnd: `ET-SHW${RUN}030`,
      },
    }),
  })
  const pushed = (await res.json())?.data || {}
  check('外部批次推送入账', pushed.accepted === true, JSON.stringify(pushed).slice(0, 140))

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

  // ---------------------------------------------------------------- 生猪档案页
  await page.goto(`${BASE}/admin/farm/pigs`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(3000)
  await page.fill('input[placeholder="请输入批次号"]', batchNo)
  await page.getByRole('button', { name: /搜索/ }).first().click()
  await page.waitForTimeout(2500)

  const body = String(await page.textContent('body'))
  check('页面标题是「生猪档案」', body.includes('生猪档案'))
  check('列表有「档案来源」列', body.includes('档案来源'))
  check('列表有「日龄」列', body.includes('日龄'))
  check('该批次出现在列表', body.includes(batchNo), batchNo)
  check('来源标签显示「养殖系统同步」', body.includes('养殖系统同步'))
  check(`日龄按出生日期现算（≈90 天）`, /9[01] 天|8[89] 天/.test(body),
    (body.match(/\d+ 天/) || [''])[0])

  const pigShot = path.join(OUT_DIR, 'page-farm-erp-pig-list.png')
  await page.screenshot({ path: pigShot })

  // ---------------------------------------------------------------- 设备接入监控
  await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(2500)
  // 待确认队列首屏是按状态排的，刚入账的那条不一定在第一页；
  // 通道中文名在「设备台账」页签一定有（FARM-ERP-001 就登记在这条通道上）。
  await page.locator('.el-tabs__item', { hasText: '设备台账' }).first().click()
  await page.waitForTimeout(2000)
  const ingestBody = String(await page.textContent('body'))
  check('设备台账里这条通道显示中文名（不漏英文枚举）',
    ingestBody.includes('养殖系统同步') && ingestBody.includes('FARM-ERP-001'),
    ingestBody.includes('FARM-ERP-001') ? '有设备但标签不对' : '设备台账里没找到 FARM-ERP-001')
  const ingestShot = path.join(OUT_DIR, 'page-farm-erp-ingest.png')
  await page.screenshot({ path: ingestShot })

  check('无脚本错误', errors.length === 0, errors.join(' | '))

  await browser.close()
  console.log(`\n结果：通过 ${pass.length} / 失败 ${fail.length}`)
  console.log(`截图：${pigShot}`)
  console.log(`      ${ingestShot}`)
  console.log(`本轮批次：${batchNo}（清理：bash logs/verify-farm-erp-db.sh --clean）`)
  process.exit(fail.length ? 1 : 0)
})().catch((e) => {
  console.error('失败：', e)
  process.exit(1)
})
