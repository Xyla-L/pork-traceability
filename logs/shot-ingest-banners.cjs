/**
 * 一次性截图：设备接入监控页（横幅清理后）+ 胴体盖章填充示例值（新口径）。
 * 跑法：node logs/shot-ingest-banners.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const OUT = 'E:/pork-traceability/logs/';

(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const page = await browser.newPage({ viewport: { width: 1500, height: 900 } });
  await page.addInitScript(() => { Math.random = () => 0; });

  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA');
  await page.locator('button.login-btn').click();
  await page.waitForTimeout(6000);

  await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(4000);
  await page.screenshot({ path: `${OUT}page-ingest-staging.png` });

  await page.locator('.el-tabs__item:has-text("设备台账")').click();
  await page.waitForTimeout(1800);
  await page.screenshot({ path: `${OUT}page-ingest-devices.png` });

  await page.locator('button:has-text("模拟设备上报")').first().click();
  await page.waitForTimeout(1200);
  await page.locator('.el-dialog:visible .el-select').first().click();
  await page.waitForTimeout(600);
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: /^STAMPER-001 ·/ }).first().click();
  await page.waitForTimeout(1500);
  await page.locator('.el-dialog:visible button:has-text("填充示例值")').click();
  await page.waitForTimeout(3000);
  await page.screenshot({ path: `${OUT}page-ingest-stamp-demo.png` });

  console.log('已产出：page-ingest-staging.png / page-ingest-devices.png / page-ingest-stamp-demo.png');
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
