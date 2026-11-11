/**
 * 一次性校验 + 截图（2026-10-22）：
 *   1. 生猪档案页不再有「申请出栏」（档案只写作废）
 *   2. 冷链运输页不再有「确认到达」（到站只由车载终端刷卡推进）
 *   3. 顶部通知点开能跳到对应业务页，并带业务编号落地定位
 *
 * 跑法：node logs/verify-nav-actions.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const OUT = 'E:/pork-traceability/logs/';

let pass = 0
let fail = 0
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  ✓ ${name}`) }
  else { fail++; console.log(`  ✗ ${name}${detail ? ` — ${detail}` : ''}`) }
}

async function login(page, username) {
  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(1200);
  await page.fill('input[placeholder="请输入用户名"]', username);
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA');
  await page.locator('button.login-btn').click();
  await page.waitForTimeout(5000);
}

async function openBell(page) {
  await page.locator('.topbar-right .topbar-icon').first().click();
  await page.waitForTimeout(900);
}

async function clickNotify(page, text) {
  const item = page.locator('.notify-item', { hasText: text }).first();
  await item.click();
  await page.waitForTimeout(1800);
}

(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await ctx.newPage();
  await page.addInitScript(() => { Math.random = () => 0; });
  await login(page, 'admin');

  // ========== 1. 生猪档案：申请出栏入口已删 ==========
  await page.goto(`${BASE}/admin/farm/pigs`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(3000);
  const pigText = await page.locator('body').innerText().catch(() => '');
  const pigRows = await page.locator('.el-table__body tr').count();
  check('生猪档案列表有数据', pigRows > 0, `rows=${pigRows}`);
  check('生猪档案页没有「申请出栏」', !pigText.includes('申请出栏'));
  check('生猪档案页保留「作废」', pigText.includes('作废'));
  await page.screenshot({ path: `${OUT}page-nav-pigs.png`, fullPage: true });

  // ========== 2. 冷链运输：确认到达入口已删 ==========
  await page.goto(`${BASE}/admin/distribution/transport`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(4000);
  const transportText = await page.locator('.page-container').first().innerText().catch(() => '');
  check('冷链运输页没有「确认到达」', !transportText.includes('确认到达'));
  check('冷链运输页仍是运单监控（有温度曲线/发车入口）',
    transportText.includes('冷链运单') || transportText.includes('立即发车') || transportText.includes('待发车'));
  await page.screenshot({ path: `${OUT}page-nav-transport.png`, fullPage: true });

  // ========== 3. 通知：举报 → 举报管理 ==========
  await openBell(page);
  const notifyCount = await page.locator('.notify-item').count();
  check('通知弹窗有通知', notifyCount > 0, `count=${notifyCount}`);

  await clickNotify(page, 'RP-TEST-0001');
  let url = page.url();
  check('举报通知跳到举报管理页', url.includes('/admin/trace/complaints'), url);
  check('举报通知带上举报编号', url.includes('keyword=RP-TEST-0001'), url);
  const reportNoValue = await page.locator('.search-panel input').first().inputValue().catch(() => '');
  check('举报管理的搜索框已填入该编号', reportNoValue === 'RP-TEST-0001', `value=${reportNoValue}`);
  check('通知弹窗点击后关闭', !(await page.locator('.notify-item').first().isVisible().catch(() => false)));
  await page.screenshot({ path: `${OUT}page-nav-complaint.png`, fullPage: true });

  // ========== 4. 通知：运输 → 冷链运输 ==========
  await openBell(page);
  await clickNotify(page, 'TR-TEST-0004');
  url = page.url();
  check('运输通知跳到冷链运输页', url.includes('/admin/distribution/transport'), url);
  check('运输通知带上运单号', url.includes('keyword=TR-TEST-0004'), url);
  const transportKeyword = await page.locator('input[placeholder="运单号/车牌/司机"]').first().inputValue().catch(() => '');
  check('运单搜索框已填入该运单号', transportKeyword === 'TR-TEST-0004', `value=${transportKeyword}`);

  // ========== 5. 通知：签收 → 门店签收「已签收」页签 ==========
  await openBell(page);
  await clickNotify(page, 'TR-TEST-0007');
  url = page.url();
  check('签收通知跳到门店签收页', url.includes('/admin/distribution/receipt'), url);
  check('签收通知带上运单号', url.includes('keyword=TR-TEST-0007'), url);
  const activeTab = await page.locator('.el-tabs__item.is-active').first().innerText().catch(() => '');
  check('签收通知落在「已签收」页签', activeTab.includes('已签收'), `tab=${activeTab}`);
  await page.screenshot({ path: `${OUT}page-nav-receipt.png`, fullPage: true });

  // ========== 6. 通知：分割批次上链 → 分割操作带批次号 ==========
  await openBell(page);
  await clickNotify(page, 'SP-TEST-0002');
  url = page.url();
  check('上链通知跳到分割操作页', url.includes('/admin/distribution/split'), url);
  check('上链通知带上批次号', url.includes('batchNo=SP-TEST-0002'), url);

  await browser.close();
  console.log(`\n通过 ${pass} / 失败 ${fail}`);
  process.exit(fail === 0 ? 0 : 1);
})();
