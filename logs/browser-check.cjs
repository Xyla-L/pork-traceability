const { chromium } = require('E:/pork-traceability/node_modules/playwright-core');
(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const page = await browser.newPage();
  const logs = [];
  page.on('console', (m) => logs.push(`[${m.type()}] ${m.text().slice(0, 300)}`));
  page.on('pageerror', (e) => logs.push(`[pageerror] ${String(e).slice(0, 500)}`));
  page.on('requestfailed', (r) => logs.push(`[reqfail] ${r.url().slice(0, 120)} ${r.failure()?.errorText}`));

  await page.goto('http://localhost/', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(6000);
  console.log('URL:', page.url());
  const html = await page.content();
  console.log('HTML length:', html.length);
  const rootHtml = await page.evaluate(() => {
    const app = document.querySelector('#app');
    return app ? app.innerHTML.slice(0, 400) : '(no #app)';
  });
  console.log('#app innerHTML head:', rootHtml);
  console.log('--- logs ---');
  console.log(logs.slice(0, 40).join('\n') || '(none)');
  await page.screenshot({ path: 'E:/pork-traceability/logs/page-home.png' });
  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
