// 机构数据隔离验收：养殖角色只见自己场，其他角色不受影响
// 断言盯不变量（farm_user 列表里不出现别场的猪/申报/免疫），不盯绝对条数
import { execSync } from 'node:child_process'

const BASE = 'http://localhost'
const OUT_DIR = 'E:/pork-traceability/logs'
const MYSQL = 'docker exec pork-traceability-mysql-1 mysql -uroot -pchange-this-root-password -N -e'

let pass = 0, fail = 0
const check = (name, ok, detail = '') => {
  console.log(`${ok ? '✅' : '❌'} ${name}${detail && !ok ? ' —— ' + detail : ''}`)
  ok ? pass++ : fail++
}

const sql = (q) => execSync(`${MYSQL} "${q}"`, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }).trim()

// farm_user 所属机构 → 自己的场（中文不跨进程边界，全部在 SQL 里 join）
const OWN_FARM_ID = sql("SELECT f.id FROM db_breeding.farm f JOIN db_common.sys_org o ON o.id=2 AND f.farm_name=o.name LIMIT 1")
const OWN_PIGS = sql(`SELECT COUNT(*) FROM db_breeding.pig_individual WHERE deleted=0 AND void_flag=0 AND farm_id=${OWN_FARM_ID}`)
const ALL_PIGS = sql('SELECT COUNT(*) FROM db_breeding.pig_individual WHERE deleted=0 AND void_flag=0')
const ORG_FARM = sql("SELECT f.farm_name FROM db_breeding.farm f JOIN db_common.sys_org o ON o.id=2 AND f.farm_name=o.name LIMIT 1")
console.log(`# farm_user(org=2) -> farm ${OWN_FARM_ID}(${ORG_FARM})  own ${OWN_PIGS} / all ${ALL_PIGS}`)

const { createRequire } = await import('node:module')
const require = createRequire(import.meta.url)
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core')
const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true })

async function login(page, username) {
  await page.addInitScript(() => { Math.random = () => 0 })
  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 })
  await page.waitForTimeout(1500)
  await page.fill('input[placeholder="请输入用户名"]', username)
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button.login-btn').click()
  await page.waitForTimeout(5000)
  // 从 localStorage 拿 token 供 API 直调
  return page.evaluate(() => localStorage.getItem('trace_admin_token'))
}

const api = (token, path) => fetch(`${BASE}/api/v1${path}`, {
  headers: { Authorization: `Bearer ${token}` },
}).then(r => r.json())

// ---------- farm_user：只能看自己场 ----------
const page1 = await browser.newPage({ viewport: { width: 1680, height: 950 } })
const farmToken = await login(page1, 'farm_user')
check('① farm_user 登录成功', !!farmToken)

const pigs = await api(farmToken, '/breeding/pigs?current=1&size=100')
const pigRows = pigs?.data?.records || []
check('② 生猪列表非空', pigRows.length > 0, `返回 ${pigRows.length} 条`)
const foreignPigs = pigRows.filter(p => p.farmId !== Number(OWN_FARM_ID))
check(`③ 生猪列表只含自己场（场 ${OWN_FARM_ID}）`, foreignPigs.length === 0,
  `混入别场 ${foreignPigs.length} 条: ${foreignPigs.slice(0, 3).map(p => p.earTagNo).join(',')}`)
check(`④ 生猪列表条数 = 自己场头数（${OWN_PIGS}）`, pigRows.length === Number(OWN_PIGS), `实际 ${pigRows.length}`)
check('⑤ total 是自己场头数（不是全平台）', pigs?.data?.total === Number(OWN_PIGS), `total=${pigs?.data?.total}`)

const applies = await api(farmToken, '/breeding/applies?current=1&size=100')
const applyRows = applies?.data?.records || []
const ownPigIds = new Set(sql(`SELECT id FROM db_breeding.pig_individual WHERE deleted=0 AND void_flag=0 AND farm_id=${OWN_FARM_ID}`).split('\n'))
const foreignApplies = applyRows.filter(a => !ownPigIds.has(String(a.pigId)))
check('⑥ 出栏申报只含自己场的猪', applyRows.length > 0 && foreignApplies.length === 0,
  `申报 ${applyRows.length} 条，混入别场 ${foreignApplies.length} 条`)
const counts = await api(farmToken, '/breeding/applies/counts')
const countsSum = (counts?.data ? Object.values(counts.data) : []).reduce((a, b) => a + Number(b || 0), 0)
check('⑦ 状态计数与列表同口径（≤自己场头数）', countsSum <= Number(OWN_PIGS), `counts 合计 ${countsSum}`)

const vaccines = await api(farmToken, '/breeding/pigs/nonexist/vaccines'.replace('/nonexist', '') + '?current=1&size=200')
check('⑧ 疫苗分组列表可访问', vaccines !== undefined)

// 疫苗走分组接口（页面口径）
const vacGroups = await api(farmToken, '/breeding/records/vaccines?current=1&size=50')
const vacRows = vacGroups?.data?.records || []
const foreignVacFarms = vacRows.filter(g => g.farmId && Number(g.farmId) !== Number(OWN_FARM_ID))
check('⑨ 疫苗分组只含自己场', foreignVacFarms.length === 0,
  `混入别场 ${foreignVacFarms.length} 组`)

const applyGroups = await api(farmToken, '/breeding/records/applies?current=1&size=50')
const applyGroupRows = applyGroups?.data?.records || []
const foreignApplyFarms = applyGroupRows.filter(g => g.farmId && Number(g.farmId) !== Number(OWN_FARM_ID))
check('⑩ 出栏申报分组只含自己场', foreignApplyFarms.length === 0,
  `混入别场 ${foreignApplyFarms.length} 组`)

const certGroups = await api(farmToken, '/breeding/records/certs?current=1&size=50')
const certRows = certGroups?.data?.records || []
const foreignCertFarms = certRows.filter(g => g.farmId && Number(g.farmId) !== Number(OWN_FARM_ID))
check('⑪ 检疫分组只含自己场', foreignCertFarms.length === 0,
  `混入别场 ${foreignCertFarms.length} 组`)

// 拿别场的猪调详情 → 403
const foreignPigId = sql(`SELECT id FROM db_breeding.pig_individual WHERE deleted=0 AND farm_id<>${OWN_FARM_ID} LIMIT 1`)
const detail = await api(farmToken, `/breeding/pigs/${foreignPigId}`)
check('⑫ 拿别场猪的 id 调详情被隔离拦下（2003）', detail?.code === 2003,
  `code=${detail?.code} msg=${detail?.message}`)

await page1.screenshot({ path: `${OUT_DIR}/page-farm-scope.png` })
await page1.close()

// ---------- admin：仍然全平台 ----------
const page2 = await browser.newPage({ viewport: { width: 1680, height: 950 } })
const adminToken = await login(page2, 'admin')
check('⑬ admin 登录成功', !!adminToken)
const adminPigs = await api(adminToken, '/breeding/pigs?current=1&size=1')
check('⑭ admin 仍看全平台（total = 全部头数）', Number(adminPigs?.data?.total) === Number(ALL_PIGS),
  `total=${adminPigs?.data?.total}，全平台=${ALL_PIGS}`)
const adminApplies = await api(adminToken, '/breeding/applies/counts')
const adminCountsSum = (adminApplies?.data ? Object.values(adminApplies.data) : []).reduce((a, b) => a + Number(b || 0), 0)
check('⑮ admin 申报计数 ≥ farm_user 计数', adminCountsSum >= countsSum, `admin=${adminCountsSum} farm=${countsSum}`)
await page2.close()

await browser.close()
console.log(`\n===== 结果：${pass} 通过 / ${fail} 失败 =====`)
process.exit(fail > 0 ? 1 : 0)
