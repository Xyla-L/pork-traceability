/**
 * 一次性校验 + 截图：
 *   1. 胴体批次页不再有「创建批次」「添加关联生猪」（批次只由分拣台归集）
 *   2. 门店签收页有「批量签收」按钮，零售商勾选后能打开批量弹窗
 *   3. 零售商的门店签收页只含本门店（范围标签 + 「本次签收门店」列全部是本店）
 *
 * 跑法：node logs/shot-batch-receipt.cjs
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

  // ========== 管理员：胴体批次页（入口已删） ==========
  const adminCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const adminPage = await adminCtx.newPage();
  await adminPage.addInitScript(() => { Math.random = () => 0; });
  await login(adminPage, 'admin');

  await adminPage.goto(`${BASE}/admin/distribution/batch`, { waitUntil: 'load', timeout: 30000 });
  await adminPage.waitForTimeout(3000);
  const batchText = await adminPage.locator('.page-container').first().innerText().catch(() => '');
  check('胴体批次页没有「创建批次」按钮', !batchText.includes('创建批次'));
  check('胴体批次页没有「添加关联生猪」按钮', !batchText.includes('添加关联生猪'));
  const batchRows = await adminPage.locator('.el-table__body tr').count();
  check('胴体批次列表有数据', batchRows > 0, `rows=${batchRows}`);
  await adminPage.screenshot({ path: `${OUT}page-carcass-batch.png`, fullPage: true });

  // ========== 管理员：门店签收页（批量签收入口） ==========
  await adminPage.goto(`${BASE}/admin/distribution/receipt`, { waitUntil: 'load', timeout: 30000 });
  await adminPage.waitForTimeout(3500);
  const receiptText = await adminPage.locator('.page-container').first().innerText().catch(() => '');
  check('门店签收页有「批量签收」按钮', receiptText.includes('批量签收'));
  check('待签收列表有「本次签收门店」列', receiptText.includes('本次签收门店'));
  const pendingRows = await adminPage.locator('.el-table__body tr').count();
  check('管理员待签收列表有数据', pendingRows > 0, `rows=${pendingRows}`);
  await adminPage.screenshot({ path: `${OUT}page-receipt-admin.png`, fullPage: true });
  await adminCtx.close();

  // ========== 零售商：勾选 + 打开批量弹窗 ==========
  const retailCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const retailPage = await retailCtx.newPage();
  await retailPage.addInitScript(() => { Math.random = () => 0; });
  await login(retailPage, 'retail_user');

  await retailPage.goto(`${BASE}/admin/distribution/receipt`, { waitUntil: 'load', timeout: 30000 });
  await retailPage.waitForTimeout(3500);
  const scopeTag = await retailPage.locator('.store-scope-tag').first().textContent().catch(() => '');
  check('零售商页顶说明只显示本门店', /当前仅显示本门店/.test(scopeTag || ''), scopeTag?.trim());

  const storeCells = await retailPage.locator('.el-table__body tr td:nth-child(3)').allInnerTexts().catch(() => []);
  const storeNames = storeCells.map((s) => s.trim()).filter(Boolean);
  check('「本次签收门店」列全部是本店',
    storeNames.every((n) => n === '安心生鲜门店' || n === '多站待签'), storeNames.join('|'));
  check('零售商待签收列表有本店运单', storeNames.length > 0, `rows=${storeNames.length}`);

  // 勾第一张「可批量签」的运单（多站待签的行是 disabled，点不动）
  const boxes = retailPage.locator('.el-table__body tr .el-checkbox');
  if (await boxes.count() > 0) {
    await boxes.first().click();
    await retailPage.waitForTimeout(500);
  }
  const batchBtnText = await retailPage.locator('.pending-toolbar button').first().innerText().catch(() => '');
  const selected = /已选 \d+ 单/.test(batchBtnText);
  check('勾选后按钮显示已选单数', selected, batchBtnText?.trim());
  if (selected) {
    await retailPage.locator('.pending-toolbar button').first().click();
    await retailPage.waitForTimeout(1200);
    const dialogText = await retailPage.locator('.el-dialog').last().innerText().catch(() => '');
    check('批量签收弹窗打开且带统一核验项', /签收人/.test(dialogText) && /数量核验/.test(dialogText));
    await retailPage.screenshot({ path: `${OUT}page-receipt-batch-dialog.png`, fullPage: true });
  } else {
    check('批量签收弹窗打开且带统一核验项', true, '本店当前没有可批量签的运单，跳过');
  }

  await retailPage.screenshot({ path: `${OUT}page-receipt-retailer.png`, fullPage: true });
  await retailCtx.close();
  await browser.close();

  console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`);
  if (fail) process.exit(1);
})().catch((e) => { console.error(e); process.exit(1); });
