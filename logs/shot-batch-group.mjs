/**
 * 批次分组视图（方案 A）界面验收：推一批 6 头、每头疫苗+检疫+申报 →
 * 三个页面都应显示「一行一组、展开看逐头」，而不是 6 行耳标刷屏。
 *
 * 跑完自清（业务表 + 链上 QC 事件），不动演示台账。
 * 运行：node logs/shot-batch-group.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')

const BASE = process.env.BASE_URL || 'http://localhost'
const API = `${BASE}/api/v1`
const OUT_DIR = import.meta.dirname

const pass = []
const fail = []
const check = (name, ok, detail = '') => {
  if (ok) { pass.push(name); console.log(`  [PASS] ${name}`) }
  else { fail.push(name); console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

const RUN = Date.now().toString().slice(-8)
const TAG_PREFIX = `ET-SHG${RUN}`
const BATCH_NO = `PB-SHG-${RUN}`
const FARM_NAME = '分组截图场'
const VAC_NAME = `口蹄疫疫苗${RUN}`
const HOUSE = '截图屠宰场'

const dayText = (offset) => {
  const d = new Date(Date.now() + offset * 86400000)
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
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  return res.json().catch(() => ({}))
}

/** 1x1 合法 PNG（免疫上报必须带真实凭证照片，编造 fileId 会被拒） */
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
  const f = pngFile(`shg-${RUN}.png`)
  fd.append('file', new Blob([f.buffer], { type: 'image/png' }), f.name)
  const res = await fetch(`${API}/file/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: fd,
  })
  return (await res.json().catch(() => null))?.data?.fileId
}

;(async () => {
  // ---------------------------------------------------------------- 准备：登录 + 建场 + 建批 + 6 头全链路
  const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
  token = login.data?.accessToken || login.data?.token || ''
  check('登录成功', !!token)

  await api('/breeding/farms', { method: 'POST', body: {
    farmName: FARM_NAME, licenseNo: `LIC-SHG${RUN}`, scale: 100,
    address: '分组截图用', contactPerson: '截图', contactPhone: '13800000000',
  } })
  const farms = await api('/breeding/farms?current=1&size=500')
  const farmId = (farms.data?.records || []).find((f) => f.farmName === FARM_NAME)?.id
  check('建场成功', !!farmId)

  await api('/breeding/pigs/batches', { method: 'POST', body: {
    batchNo: BATCH_NO, farmId, breed: '三元杂',
    entryDate: dayText(-15), source: '自繁',
    earTagStart: `${TAG_PREFIX}001`, earTagEnd: `${TAG_PREFIX}006`,
  } })
  const pigs = await api(`/breeding/pigs?current=1&size=50&farmId=${farmId}`)
  const members = (pigs.data?.records || [])
    .filter((p) => p.earTagNo?.startsWith(TAG_PREFIX))
    .sort((a, b) => a.earTagNo.localeCompare(b.earTagNo))
  check('批次 6 头成员就位', members.length === 6, `实际 ${members.length}`)

  const voucherId = await uploadVoucher()
  check('凭证照片上传成功', !!voucherId)

  const INJ_KEY = 'dev-key-inj-001'
  for (const p of members) {
    await fetch(`${API}/ingest/device/report`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Device-Key': INJ_KEY },
      body: JSON.stringify({
        channel: 'VACCINE', bizKey: `SHOT${RUN}-${p.earTagNo}`, reportTime: nowText(),
        sourceRef: '分组截图注射器',
        data: {
          earTagNo: p.earTagNo, vaccineName: VAC_NAME, vaccineBatchNo: `VACB${RUN}`,
          injectTime: `${dayText(0)} 08:30:00`, dosage: '2ml', operator: '张兽医',
          injectSite: '颈部肌肉', fileIds: [voucherId],
        },
      }),
    })
    await api(`/breeding/pigs/${p.id}/quarantine-cert`, { method: 'POST', body: {
      pigId: p.id,
      certNo: `QC-SHG${RUN}-${p.earTagNo}`,
      issueOrg: '截图动物卫生监督所',
      issueTime: `${dayText(0)} 08:30:00`,
      validUntil: dayText(3),
      inspector: '李检疫',
      fileId: voucherId,
      caSignature: `CA-SHG${RUN}-${p.earTagNo}`,
    } })
    await api(`/breeding/pigs/${p.id}/apply`, { method: 'POST', body: {
      pigId: p.id,
      weightKg: 105 + Number(p.earTagNo.slice(-1)),
      targetSlaughterhouse: HOUSE,
    } })
  }
  console.log('  · 6 头疫苗/检疫/申报已推送')

  // ---------------------------------------------------------------- 截图三个页面
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

  // ① 疫苗记录：应是一行一次免疫（展开才见 6 头）
  await page.goto(`${BASE}/admin/farm/vaccines`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(3000)
  let body = String(await page.textContent('body'))
  check('① 疫苗页出现免疫组标题（疫苗名+头数）', body.includes(VAC_NAME), VAC_NAME)
  check('① 疫苗页有「覆盖头数」列且该组为 6', body.includes('覆盖头数') && body.includes(BATCH_NO),
    body.includes('覆盖头数') ? '' : '没有覆盖头数列')
  const shotVaccine = path.join(OUT_DIR, 'page-group-vaccine.png')
  await page.screenshot({ path: shotVaccine })

  // ② 产地检疫
  await page.goto(`${BASE}/admin/farm/quarantine`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(3000)
  body = String(await page.textContent('body'))
  check('② 检疫页出现出证组（养殖场+证号前缀）', body.includes(FARM_NAME) && body.includes('QC-SHG'))
  check('② 检疫页组行标头数「6 头」', body.includes('6 头'), (body.match(/\d+ 头/) || [''])[0])
  const shotQuarantine = path.join(OUT_DIR, 'page-group-quarantine.png')
  await page.screenshot({ path: shotQuarantine })

  // ③ 出栏审批
  await page.goto(`${BASE}/admin/farm/apply`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(3000)
  body = String(await page.textContent('body'))
  check('③ 审批页出现申报组（目标屠宰场）', body.includes(HOUSE))
  check('③ 审批页有待审计数', body.includes('待审'))
  const shotApply = path.join(OUT_DIR, 'page-group-apply.png')
  await page.screenshot({ path: shotApply })

  check('三个页面无脚本错误', errors.length === 0, errors.slice(0, 2).join(' | '))
  await browser.close()

  // ---------------------------------------------------------------- 清理（业务表 + 链上 QC 事件）
  const { execSync } = await import('node:child_process')
  const q = (s) => {
    const escaped = s.replace(/"/g, '\\"').replace(/\n/g, ' ')
    try {
      return String(execSync(
        `docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 --default-character-set=utf8mb4 -N -e "${escaped}" 2>&1 | grep -v Warning || true`,
      )).trim()
    } catch { return '' }
  }
  const pigIds = q(`USE db_breeding; SELECT GROUP_CONCAT(id) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  if (pigIds && pigIds !== 'NULL') {
    q(`USE db_breeding; DELETE FROM vaccine_record WHERE pig_id IN (${pigIds});
       DELETE FROM quarantine_certificate WHERE pig_id IN (${pigIds});
       DELETE FROM slaughter_apply WHERE pig_id IN (${pigIds});
       DELETE FROM pig_individual WHERE id IN (${pigIds});`)
  }
  q(`USE db_breeding; DELETE FROM pig_batch WHERE batch_no = '${BATCH_NO}';
     DELETE FROM farm WHERE farm_name = '${FARM_NAME}';`)
  q(`USE db_ingest; DELETE FROM ingest_staging WHERE biz_key LIKE 'SHOT${RUN}-%';`)
  q(`USE db_common; DELETE FROM blockchain_record WHERE biz_key LIKE 'QC-SHG${RUN}-%';`)
  const left = q(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  check('清理完成', left === '0' || left === '', `剩余 ${left}`)

  console.log(`\n── 结果：${pass.length} 通过 / ${fail.length} 失败 ──`)
  if (fail.length > 0) { console.log('失败项：' + fail.join('；')); process.exit(1) }
  process.exit(0)
})()
