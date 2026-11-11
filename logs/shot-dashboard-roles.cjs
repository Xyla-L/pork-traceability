/**
 * 一次性截图：六个角色的工作台。
 * 跑法：node logs/shot-dashboard-roles.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const OUT = 'E:/pork-traceability/logs/';

const ACCOUNTS = [
  { user: 'admin', file: 'admin' },
  { user: 'supervisor_user', file: 'supervisor' },
  { user: 'slaughter_user', file: 'slaughter' },
  { user: 'distribution_user', file: 'distributor' },
  { user: 'retail_user', file: 'retailer' },
  { user: 'farm_user', file: 'farmer' },
];

(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });

  for (const account of ACCOUNTS) {
    const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    const page = await context.newPage();
    await page.addInitScript(() => { Math.random = () => 0; });

    await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
    await page.waitForTimeout(1200);
    await page.fill('input[placeholder="请输入用户名"]', account.user);
    await page.fill('input[placeholder="请输入密码"]', '123456');
    await page.fill('input[placeholder="请输入验证码"]', 'AAAA');
    await page.locator('button.login-btn').click();
    await page.waitForTimeout(5000);

    await page.goto(`${BASE}/admin/dashboard`, { waitUntil: 'load', timeout: 30000 });
    await page.waitForTimeout(4000);

    const title = await page.locator('.head-title').first().textContent().catch(() => '');
    const cards = await page.locator('.stat-card').count();
    const charts = await page.locator('.chart-container').count();
    console.log(`${account.user} -> ${title?.trim()} / 卡片 ${cards} / 图表 ${charts}`);

    await page.screenshot({ path: `${OUT}page-dashboard-${account.file}.png`, fullPage: true });
    await context.close();
  }

  console.log('已产出 page-dashboard-*.png');
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
