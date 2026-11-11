/**
 * 一次性校验 + 截图：冷链温度「定时自动打卡」在页面上的落点。
 *
 *  1. 运输监控页选一张运输中的运单 → 温度打卡记录里出现自动采集的读数
 *  2. 标题带「自动采集 · 每 5 分钟」（节奏可预期，不用猜为什么没有新记录）
 *  3. 采集设备列写「模拟探头」——真机接入前不冒充真实车厢温度
 *  4. 温度曲线画得出来（有数据点）
 *
 * 跑法：node logs/shot-temp-auto.cjs
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

(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 } });
  const page = await ctx.newPage();
  await page.addInitScript(() => { Math.random = () => 0; });
  await login(page, 'admin');

  await page.goto(`${BASE}/admin/distribution/transport`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(3500);

  // 运单表里找一张「运输中」的单（计划表的清一色待发货/已排车，不会误命中）
  const movingRow = page.locator('.el-table__body tr', { hasText: '运输中' }).first();
  const rowCount = await movingRow.count();
  check('运输监控页存在运输中运单', rowCount > 0, `count=${rowCount}`);
  if (!rowCount) {
    await page.screenshot({ path: `${OUT}page-temp-auto.png`, fullPage: true });
    await browser.close();
    console.log(`\n通过 ${pass} 项，失败 ${fail} 项`);
    process.exit(1);
  }
  await movingRow.click();
  await page.waitForTimeout(3000);

  const bodyText = await page.locator('.page-container').first().innerText().catch(() => '');
  check('温度打卡记录标题带「自动采集」节奏说明', bodyText.includes('自动采集'), '');
  check('节奏写明「每 5 分钟」', /每\s*5\s*分钟/.test(bodyText), '');
  check('采集设备列出现「模拟探头」（不冒充真实读数）', bodyText.includes('模拟探头'), '');
  check('有温度读数（不是"-- 暂无打卡"）', !bodyText.includes('暂无打卡'), '');

  const recordRows = await page.locator('.checkin-list-card .el-table__body tr').count();
  check('温度记录列表有条目', recordRows > 0, `rows=${recordRows}`);

  // 曲线容器里应当有 canvas（echarts 画出来了，不是空白面板）
  const canvas = await page.locator('.temp-chart-card canvas').count();
  check('温度曲线已渲染', canvas > 0, `canvas=${canvas}`);

  await page.locator('.checkin-list-card').scrollIntoViewIfNeeded().catch(() => {});
  await page.waitForTimeout(600);
  await page.screenshot({ path: `${OUT}page-temp-auto.png`, fullPage: true });
  await page.locator('.checkin-list-card').screenshot({ path: `${OUT}page-temp-records.png` }).catch(() => {});

  await browser.close();
  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail === 0 ? 0 : 1);
})();
