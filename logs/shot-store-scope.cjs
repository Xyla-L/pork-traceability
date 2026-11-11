/**
 * 一次性截图：零售商视角（工作台 / 销售记录 / 门店签收）。
 * 跑法：node logs/shot-store-scope.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const OUT = 'E:/pork-traceability/logs/';

const PAGES = [
  { path: '/admin/dashboard', file: 'dash' },
  { path: '/admin/sales/records', file: 'sales' },
  { path: '/admin/distribution/receipt', file: 'receipt' },
  { path: '/admin/sales/warnings', file: 'warnings' },
];

(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await context.newPage();
  await page.addInitScript(() => { Math.random = () => 0; });

  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(1200);
  await page.fill('input[placeholder="请输入用户名"]', 'retail_user');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA');
  await page.locator('button.login-btn').click();
  await page.waitForTimeout(5000);

  for (const item of PAGES) {
    await page.goto(`${BASE}${item.path}`, { waitUntil: 'load', timeout: 30000 });
    await page.waitForTimeout(3500);
    const rows = await page.locator('.el-table__body tr').count();
    const tag = await page.locator('.store-scope-tag').first().textContent().catch(() => '');
    console.log(`${item.path} -> 表格行 ${rows} / 范围标签 ${tag?.trim() || '无'}`);
    await page.screenshot({ path: `${OUT}page-scope-${item.file}.png`, fullPage: true });
  }

  console.log('已产出 page-scope-*.png');
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
