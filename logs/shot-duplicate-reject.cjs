/**
 * 一次性截图：模拟设备上报「重复上报 → 该信息已存在、拒绝入账」。
 * 跑法：node logs/shot-duplicate-reject.cjs
 *
 * 做法：同一台设备、同一份报文、同一个上报编号连报两次，截第二张回执。
 * 耳标刻意用不存在的值 —— 首报必然「待人工处理」，不写业务表，不会污染演示数据。
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const OUT = 'E:/pork-traceability/logs/';
const STAMP = Date.now();
const BIZ_KEY = `SHOT-DUP-${STAMP}`;
const EAR_TAG = `SHOT-NOT-EXIST-${STAMP}`;

(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const page = await browser.newPage({ viewport: { width: 1500, height: 950 } });
  await page.addInitScript(() => { Math.random = () => 0; });

  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA');
  await page.locator('button.login-btn').click();
  await page.waitForTimeout(6000);

  await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(3500);

  /** 按 form-item 的 label 填值 */
  const fillByLabel = async (label, value) => {
    const item = page.locator('.el-dialog:visible .el-form-item')
      .filter({ has: page.locator(`label:text-is("${label}")`) }).first();
    await item.locator('input, textarea').first().fill(value);
  };

  /** 打开弹窗、选设备、把表单填成固定值（两次调用完全一致 → 报文相同） */
  const prepare = async () => {
    await page.locator('button:has-text("模拟设备上报")').first().click();
    await page.waitForTimeout(1200);
    await page.locator('.el-dialog:visible .el-select').first().click();
    await page.waitForTimeout(600);
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
      .filter({ hasText: /^GATE-001 ·/ }).first().click();
    await page.waitForTimeout(1200);

    await fillByLabel('耳标号', EAR_TAG);
    await fillByLabel('入场批次号', `SHOT-B-${STAMP}`);
    await fillByLabel('查验员', '张三');
    await fillByLabel('上报编号', BIZ_KEY);
    await page.waitForTimeout(400);
  };

  // ── 第一次：正常受理（转人工，不写业务表）──
  await prepare();
  await page.locator('.el-dialog:visible button:has-text("上报")').click();
  await page.waitForTimeout(3000);
  await page.screenshot({ path: `${OUT}page-ingest-duplicate-first.png` });

  // ── 第二次：关掉重开，填一模一样的内容，再报一次 ──
  await page.locator('.el-dialog:visible button:has-text("取消"), .el-dialog:visible .el-dialog__headerbtn')
    .first().click();
  await page.waitForTimeout(800);
  await prepare();
  await page.locator('.el-dialog:visible button:has-text("上报")').click();
  await page.waitForTimeout(3000);

  // 回执区滚进视野再截
  await page.locator('.demo-result').scrollIntoViewIfNeeded();
  await page.waitForTimeout(600);
  await page.screenshot({ path: `${OUT}page-ingest-duplicate-reject.png` });

  const tag = (await page.locator('.demo-result .el-tag').first().textContent())?.trim();
  const dest = (await page.locator('.demo-result-list li').first().locator('.v').textContent())?.trim();
  const reason = (await page.locator('.demo-result-reason').textContent())?.trim();
  console.log(`回执标签：${tag}`);
  console.log(`数据去向：${dest}`);
  console.log(`未入账原因：${reason}`);
  console.log(`bizKey=${BIZ_KEY}`);

  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
