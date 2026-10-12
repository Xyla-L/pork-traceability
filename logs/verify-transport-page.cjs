/**
 * 冷链运输 + 门店签收 两页的渲染核验（2026-10-12）：
 *   1. 冷链运输页：「新建计划」「新建运单」两个新增按钮已删（页面上搜不到这两个文案）；
 *   2. 门店签收页「待签收」：TR-TEST-0016 的中文列正常显示（京B-TEST16 / 示范分割车间 /
 *      绿康生鲜门店 / 吴师傅），不再是一串 '?'。
 *      该行数据曾因客户端字符集错误落成字面 '?'（HEX=3F…），已用 utf8mb4 修复。
 *
 * 登录：登录页验证码是纯前端 canvas 生成，addInitScript 把 Math.random 固定为 0
 * 即得验证码 'AAAA'，可稳定自动登录（详见 .workbuddy/memory/DETAIL.md 第十二节）。
 *
 * 运行：node logs/verify-transport-page.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const SHOT = 'E:/pork-traceability/logs/page-transport-receipt.png';
const CAPTCHA = 'AAAA';

let pass = 0;
let fail = 0;
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  [PASS] ${name}`); }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`); }
};

(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 300)));
  await page.addInitScript(() => { Math.random = () => 0; });

  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', CAPTCHA);
  await page.locator('button.login-btn').click();
  await page.waitForTimeout(6000);
  check('登录成功', !page.url().includes('/login'), `地址 ${page.url()}`);

  console.log('\n== 冷链运输页 ==');
  await page.goto(`${BASE}/admin/distribution/transport`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(6000);
  const transportText = await page.evaluate(() => document.body.innerText || '');
  check('页面加载（发货计划可见）', transportText.includes('发货计划'), `地址 ${page.url()}`);
  check('「新建计划」按钮已删', !transportText.includes('新建计划'));
  check('「新建运单」按钮已删', !transportText.includes('新建运单'));
  // 「生成运单」只挂在待发货(1)/部分排车(4)的计划行上，当前库里的计划是已排车(2)，不依赖它做断言
  check('「监控详情」入口仍在（运单行的恒定操作）', transportText.includes('监控详情'));

  console.log('\n== 门店签收页（待签收）==');
  await page.goto(`${BASE}/admin/distribution/receipt`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(6000);
  const receiptText = await page.evaluate(() => document.body.innerText || '');
  check('待签收列表加载', receiptText.includes('待签收'), `地址 ${page.url()}`);
  check('TR-TEST-0016 在待签收列表里', receiptText.includes('TR-TEST-0016'));
  check('车牌正常显示（京B-TEST16）', receiptText.includes('京B-TEST16'));
  check('发货地正常显示（示范分割车间）', receiptText.includes('示范分割车间'));
  check('目的地正常显示（绿康生鲜门店）', receiptText.includes('绿康生鲜门店'));
  check('司机正常显示（吴师傅）', receiptText.includes('吴师傅'));
  // 乱码特征：连续两个以上的 '?'（正常业务文案不会出现）
  const garbled = receiptText.match(/\?{2,}/g) || [];
  check('页面上没有成串的问号乱码', garbled.length === 0, JSON.stringify(garbled.slice(0, 5)));
  check('控制台没有 pageerror', errors.length === 0, errors.join(' | '));

  await page.screenshot({ path: SHOT, fullPage: false });
  await browser.close();
  console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`);
  console.log(`截图：${SHOT}`);
  process.exit(fail === 0 ? 0 : 1);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
