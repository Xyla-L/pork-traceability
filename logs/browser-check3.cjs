const { chromium } = require('E:/pork-traceability/node_modules/playwright-core');
(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const page = await browser.newPage();
  const logs = [];
  page.on('console', (m) => { if (m.type() === 'error') logs.push(`[console.error] ${m.text().slice(0, 200)}`); });
  page.on('pageerror', (e) => logs.push(`[pageerror] ${String(e).slice(0, 300)}`));
  await page.addInitScript(() => {
    window.__cap = [];
    const orig = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (...args) {
      const ctx = orig.apply(this, args);
      if (!ctx.__hooked) {
        ctx.__hooked = true;
        const of = ctx.fillText;
        ctx.fillText = function (t, x, y) { window.__cap.push(String(t)); return of.call(this, t, x, y); };
      }
      return ctx;
    };
  });

  await page.goto('http://localhost/', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(2500);
  const cap = await page.evaluate(() => window.__cap.filter((t) => t.length === 1).slice(-4).join(''));
  console.log('验证码:', cap);
  const inputs = page.locator('input:visible');
  await inputs.nth(0).fill('admin');
  await page.locator('input[type=password]:visible').first().fill('123456');
  await inputs.nth(2).fill(cap);
  await page.locator('button:has-text("登 录")').click();
  await page.waitForTimeout(4000);
  console.log('URL after login:', page.url());

  const routes = ['/admin/slaughter/entry', '/admin/slaughter/inspect', '/admin/slaughter/ractopamine', '/admin/slaughter/stamp'];
  for (const r of routes) {
    await page.goto('http://localhost' + r, { waitUntil: 'load', timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(3000);
    const rows = await page.locator('.el-table__body-wrapper tbody tr').count();
    const empty = await page.locator('.el-table__empty-text').count();
    const emptyText = empty ? (await page.locator('.el-table__empty-text').first().textContent() || '').trim() : '';
    const firstCell = rows ? (await page.locator('.el-table__body-wrapper tbody tr').first().textContent() || '').replace(/\s+/g, ' ').slice(0, 130) : '';
    console.log(`${r}\n    行数=${rows}${empty ? ` 空提示="${emptyText}"` : ''} 首行: ${firstCell}`);
  }
  await page.screenshot({ path: 'E:/pork-traceability/logs/page-slaughter.png' });
  console.log('--- console/page errors ---');
  console.log(logs.slice(0, 20).join('\n') || '(无)');
  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
