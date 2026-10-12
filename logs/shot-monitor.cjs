// 一次性截图：分割操作页滚动到「分割车间监控」卡片（给人看的，不做断言）
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');
const BATCH = process.argv[2] || 'CB20261012-001';
(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true,
  });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } });
  await page.addInitScript(() => { Math.random = () => 0; });
  await page.goto('http://localhost/login', { waitUntil: 'load' });
  await page.waitForTimeout(1500);
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA');
  await page.locator('button.login-btn').click();
  await page.waitForTimeout(6000);
  await page.goto(`http://localhost/admin/distribution/split?batchNo=${encodeURIComponent(BATCH)}`, { waitUntil: 'load' });
  await page.waitForTimeout(7000);
  await page.locator('.workshop-monitor-card').scrollIntoViewIfNeeded();
  await page.waitForTimeout(1500);
  await page.locator('.workshop-monitor-card').screenshot({ path: 'E:/pork-traceability/logs/page-split-monitor.png' });
  await browser.close();
  console.log('截图完成：logs/page-split-monitor.png');
})().catch((e) => { console.error(e); process.exit(1); });
