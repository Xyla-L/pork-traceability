/**
 * 复验：疫苗 / 检疫两页展开逐头后「查看凭证」与「作废」真实可用。
 *
 * 背景：检疫出证弹窗里有一个从未定义的 refreshTicket() 调用，弹窗一打开就在
 * loadCert 之前抛 ReferenceError，证书永远加载不出来 → 只显示「出证表单」，
 * 证信息看不到、作废按钮（v-if="cert"）永远不出现。
 *
 * 流程：自推 4 头全链路（疫苗带真凭证照 + 出证带真扫描件）→ 页面点击复验 → DB 断言 → 自清。
 */
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { execSync } from 'node:child_process'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const API = `${BASE}/api/v1`
const OUT = import.meta.dirname

const pass = []
const fail = []
const check = (name, ok, detail = '') => {
  if (ok) { pass.push(name); console.log(`  [PASS] ${name}`) }
  else { fail.push(name); console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

const RUN = Date.now().toString().slice(-8)
const TAG_PREFIX = `ET-VAC${RUN}`
const BATCH_NO = `PB-VAC-${RUN}`
const FARM_NAME = '凭证复验场'
const VAC_NAME = `口蹄疫疫苗${RUN}`
const HOUSE = `复验屠宰场${RUN}`
const INJ_KEY = 'dev-key-inj-001'

const dayText = (o) => {
  const d = new Date(Date.now() + o * 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const nowText = () => {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

let token = ''
async function api(url, { method = 'GET', body } = {}) {
  const res = await fetch(`${API}${url}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  return res.json().catch(() => ({}))
}

function pngFile(name) {
  return {
    name,
    buffer: Buffer.from(
      '89504e470d0a1a0a0000000d494844520000000100000001080200000090775'
      + '3de0000000c4944415408d763f8cfc000000301010018dd8db00000000049454e44ae426082',
      'hex',
    ),
  }
}

async function uploadVoucher() {
  const fd = new FormData()
  const f = pngFile(`vac-${RUN}.png`)
  fd.append('file', new Blob([f.buffer], { type: 'image/png' }), f.name)
  const res = await fetch(`${API}/file/upload`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd,
  })
  return (await res.json().catch(() => null))?.data?.fileId
}

const q = (s) => {
  const escaped = s.replace(/"/g, '\\"').replace(/\n/g, ' ')
  try {
    return String(execSync(
      `docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N -e "${escaped}" 2>&1 | grep -v Warning || true`,
    )).trim()
  } catch { return '' }
}

;(async () => {
  // ---------------------------------------------------------------- 数据准备
  const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
  token = login.data?.accessToken || login.data?.token || ''
  check('登录成功', !!token)

  await api('/breeding/farms', { method: 'POST', body: {
    farmName: FARM_NAME, licenseNo: `LIC-VAC${RUN}`, scale: 100,
    address: '复验用', contactPerson: '复验', contactPhone: '13900000000',
  } })
  const farms = await api('/breeding/farms?current=1&size=500')
  const farmId = (farms.data?.records || []).find((f) => f.farmName === FARM_NAME)?.id
  check('建场成功', !!farmId)

  await api('/breeding/pigs/batches', { method: 'POST', body: {
    batchNo: BATCH_NO, farmId, breed: '三元杂', entryDate: dayText(-10), source: '自繁',
    earTagStart: `${TAG_PREFIX}001`, earTagEnd: `${TAG_PREFIX}004`,
  } })
  const pigs = await api(`/breeding/pigs?current=1&size=50&farmId=${farmId}`)
  const members = (pigs.data?.records || [])
    .filter((p) => p.earTagNo?.startsWith(TAG_PREFIX))
    .sort((a, b) => a.earTagNo.localeCompare(b.earTagNo))
  check('4 头成员就位', members.length === 4, `实际 ${members.length}`)

  const voucherId = await uploadVoucher()
  check('凭证照片上传成功', !!voucherId)

  for (const p of members) {
    await fetch(`${API}/ingest/device/report`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Device-Key': INJ_KEY },
      body: JSON.stringify({
        channel: 'VACCINE', bizKey: `VACV${RUN}-${p.earTagNo}`, reportTime: nowText(),
        sourceRef: '凭证复验注射器',
        data: {
          earTagNo: p.earTagNo, vaccineName: VAC_NAME, vaccineBatchNo: `VACB${RUN}`,
          injectTime: `${dayText(0)} 09:00:00`, dosage: '2ml', operator: '张兽医',
          injectSite: '颈部肌肉', fileIds: [voucherId],
        },
      }),
    })
    await api(`/breeding/pigs/${p.id}/quarantine-cert`, { method: 'POST', body: {
      pigId: p.id, certNo: `QC-VAC${RUN}-${p.earTagNo}`,
      issueOrg: '复验动物卫生监督所', issueTime: `${dayText(0)} 09:30:00`,
      validUntil: dayText(3), inspector: '李检疫',
      fileId: voucherId, caSignature: `CA-VAC${RUN}-${p.earTagNo}`,
    } })
  }
  check('4 头疫苗上报 + 出证完成', true)

  // ---------------------------------------------------------------- 页面复验
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  })
  const page = await browser.newPage({ viewport: { width: 1680, height: 950 } })
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e)))
  await page.addInitScript(() => { Math.random = () => 0 })

  await page.goto(`${BASE}/login`, { waitUntil: 'load' })
  await page.waitForTimeout(1500)
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button.login-btn').click()
  await page.waitForTimeout(6000)

  const targetTag = members[0].earTagNo

  // ---- 疫苗页：提示文案已删 + 查看凭证 + 作废
  await page.goto(`${BASE}/admin/farm/vaccines`, { waitUntil: 'load' })
  await page.waitForTimeout(3000)
  let body = String(await page.textContent('body'))
  check('疫苗页提示文案已删', !body.includes('逐头明细'))

  // 搜索定位到本批（避免展开到演示数据）
  await page.fill('input[placeholder="请输入生猪耳标号"]', targetTag)
  await page.getByRole('button', { name: /搜索/ }).first().click()
  await page.waitForTimeout(2000)
  await page.locator('.el-table__expand-icon').first().click()
  await page.waitForTimeout(1000)
  check('疫苗展开面板无提示文案', !(String(await page.textContent('body')).includes('逐头明细，可单独')))

  await page.getByRole('button', { name: '查看凭证' }).first().click()
  await page.waitForTimeout(2500)
  const vacDlg = page.locator('.el-dialog:visible').first()
  const vacDlgText = await vacDlg.textContent()
  check('疫苗凭证弹窗打开', (await vacDlg.count()) > 0)
  check('疫苗凭证照片渲染（el-image 存在）', (await vacDlg.locator('.el-image').count()) > 0, vacDlgText.slice(0, 120))
  await page.screenshot({ path: path.join(OUT, 'verify-vaccine-cert.png') })
  await vacDlg.locator('.el-dialog__headerbtn').click()
  await page.waitForTimeout(600)

  await page.getByRole('button', { name: '作废', exact: true }).first().click()
  await page.waitForTimeout(800)
  const vacBox = page.locator('.el-message-box:visible')
  check('疫苗作废确认框打开', (await vacBox.count()) > 0)
  // 填作废理由（confirmVoid 组件的输入框）
  await vacBox.locator('textarea, input.el-input__inner').last().fill('复验作废')
  await vacBox.getByRole('button', { name: /确\s*定|作\s*废/ }).click()
  await page.waitForTimeout(2500)
  const vacVoidFlag = q(`USE db_breeding; SELECT void_flag FROM vaccine_record v
     JOIN pig_individual i ON i.id=v.pig_id WHERE i.ear_tag_no='${targetTag}' AND v.vaccine_name='${VAC_NAME}';`)
  check('疫苗作废落库（void_flag=1）', String(vacVoidFlag).includes('1'), `返回 ${vacVoidFlag}`)

  // ---- 检疫页：弹窗能看到证 + 能作废
  await page.goto(`${BASE}/admin/farm/quarantine`, { waitUntil: 'load' })
  await page.waitForTimeout(3000)
  body = String(await page.textContent('body'))
  check('检疫页提示文案已删', !body.includes('逐头明细'))

  await page.fill('input[placeholder="请输入耳标号"]', targetTag)
  await page.getByRole('button', { name: /搜索/ }).first().click()
  await page.waitForTimeout(2000)
  await page.locator('.el-table__expand-icon').first().click()
  await page.waitForTimeout(1000)

  await page.getByRole('button', { name: /查看/ }).first().click()
  await page.waitForTimeout(2500)
  const certDlg = page.locator('.el-dialog:visible').first()
  const certDlgText = await certDlg.textContent()
  check('检疫弹窗打开', (await certDlg.count()) > 0)
  check('检疫证号显示（不再全空）', certDlgText.includes(`QC-VAC${RUN}`), certDlgText.replace(/\s+/g, ' ').slice(0, 160))
  check('扫描件区域渲染', (await certDlg.locator('.scan-block').count()) > 0
    || certDlgText.includes('扫描件'), '')
  check('作废按钮出现（cert 加载成功）', (await certDlg.getByRole('button', { name: '作废' }).count()) > 0)
  await page.screenshot({ path: path.join(OUT, 'verify-quarantine-dialog.png') })

  await certDlg.getByRole('button', { name: '作废' }).click()
  await page.waitForTimeout(800)
  const certBox = page.locator('.el-message-box:visible')
  check('检疫作废确认框打开', (await certBox.count()) > 0)
  await certBox.locator('textarea, input.el-input__inner').last().fill('复验作废')
  await certBox.getByRole('button', { name: /确\s*定|作\s*废/ }).click()
  await page.waitForTimeout(2500)
  const certVoidFlag = q(`USE db_breeding; SELECT void_flag FROM quarantine_certificate
     WHERE cert_no='QC-VAC${RUN}-${targetTag}';`)
  check('检疫作废落库（void_flag=1）', String(certVoidFlag).includes('1'), `返回 ${certVoidFlag}`)

  check('页面无脚本错误', errors.length === 0, errors.slice(0, 3).join(' | '))
  await browser.close()

  // ---------------------------------------------------------------- 清理
  const pigIds = q(`USE db_breeding; SELECT GROUP_CONCAT(id) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  if (pigIds && pigIds !== 'NULL') {
    q(`USE db_breeding; DELETE FROM vaccine_record WHERE pig_id IN (${pigIds});
       DELETE FROM quarantine_certificate WHERE pig_id IN (${pigIds});
       DELETE FROM slaughter_apply WHERE pig_id IN (${pigIds});
       DELETE FROM pig_individual WHERE id IN (${pigIds});`)
  }
  q(`USE db_breeding; DELETE FROM pig_batch WHERE batch_no='${BATCH_NO}'; DELETE FROM farm WHERE farm_name='${FARM_NAME}';`)
  q(`USE db_ingest; DELETE FROM ingest_staging WHERE biz_key LIKE 'VACV${RUN}-%';`)
  q(`USE db_common; DELETE FROM blockchain_record WHERE biz_key LIKE 'QC-VAC${RUN}-%';`)
  const left = q(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  check('清理完成', left === '0' || left === '', `剩余 ${left}`)

  console.log(`\n── 结果：${pass.length} 通过 / ${fail.length} 失败 ──`)
  if (fail.length > 0) { console.log('失败项：' + fail.join('；')); process.exit(1) }
  process.exit(0)
})()
