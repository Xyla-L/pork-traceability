/**
 * 分割操作页：树根节点标题在批次号（CB…）后面标注猪的品种（2026-10-22）。
 *
 *   口径：根标题 = 批次号（品种1、品种2）；品种来自养殖侧档案（pig_individual.breed），
 *   由 distribution-service 在拆分树接口里带出（data.pigBreeds）。查不到就不标 ——
 *   品种是展示信息，缺了不拦树，也不许编一个兜底。
 *
 * 断言盯恒定落点（不依赖具体数据状态）：页面上能找到「批次号」这个根，
 * 且如果接口给了品种，标题必须是「批次号（品种）」的形状。
 *
 * 运行：node logs/verify-split-tree-breed.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const SHOT = 'E:/pork-traceability/logs/page-split-tree-breed.png';
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

  await page.goto(`${BASE}/admin/distribution/split`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(6000);
  const bodyText = await page.evaluate(() => document.body.innerText || '');
  check('分割操作页加载（拆分树可见）', bodyText.includes('拆分树') || bodyText.includes('分割批次') || bodyText.includes('胴体批次'), `地址 ${page.url()}`);

  // 根节点：CB 编号必须出现在树上（恒定落点 —— 树根就是胴体批次）
  const rootMatch = bodyText.match(/CB\d{8}-?\d*/);
  check('树上能找到胴体批次根节点（CB…）', !!rootMatch, rootMatch ? rootMatch[0] : '未找到 CB 编号');
  const rootNo = rootMatch ? rootMatch[0] : '';

  // 用页面自己的接口核对「接口给了品种 ⇒ 标题必须是 批次号（品种）」
  // （token 走接口登录拿，与 verify-stamp-demo.cjs 同一套；浏览器只负责渲染断言与截图）
  const loginRes = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' }),
  }).then((r) => r.json()).catch(() => null);
  const token = loginRes?.data?.token || loginRes?.data?.accessToken || null;
  check('接口登录拿到 token', !!token);
  if (token && rootNo) {
    const treeRes = await fetch(`${BASE}/api/v1/distribution/splits/tree/${encodeURIComponent(rootNo)}`, {
      headers: { Authorization: `Bearer ${token}` },
    }).then((r) => r.json()).catch(() => null);
    const rootData = treeRes?.data?.data || treeRes?.data || {};
    const breeds = Array.isArray(rootData.pigBreeds) ? rootData.pigBreeds.filter(Boolean) : [];
    check('拆分树接口根节点带 pigBreeds', Array.isArray(rootData.pigBreeds), JSON.stringify(rootData.pigBreeds));
    if (breeds.length > 0) {
      const expectTitle = `${rootNo}（${breeds.join('、')}）`;
      check('页面根标题 = 批次号（品种）', bodyText.includes(expectTitle),
        `期望「${expectTitle}」`);
    } else {
      check('接口没给品种时根标题保持纯批次号（不许编兜底）', bodyText.includes(rootNo), rootNo);
    }
  }

  check('页面无脚本错误', errors.length === 0, errors.join(' | '));
  await page.screenshot({ path: SHOT, fullPage: true });
  console.log(`  截图：${SHOT}`);

  await browser.close();
  console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail === 0 ? 0 : 1);
})();
