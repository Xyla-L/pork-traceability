/**
 * 分割操作页（/admin/distribution/split）批次树的**渲染**核验 —— 页面口径回归的浏览器侧。
 *
 * 为什么必须有这一层：`logs/verify-split-tree.mjs` 只证明「喂给页面的树口径正确」，
 * 证明不了「页面上真的画出来了」。这一层把页面跑起来，直接数 DOM 里的节点文字。
 *
 * 页面口径（2026-10-12 下午定稿）：
 *   胴体批次 → 每头白条一组（「白条 · 作业号/耳标号」）→ 组内「已出部位」+「未产出标准部位 N 项」。
 *   不显示：出肉率（总/红肉）、对账结论、「必出缺 N」「齐」小徽标。
 *
 * 登录：登录页的验证码是纯前端 canvas 生成的（`chars.charAt(floor(random()*len))`），
 * 所以把 Math.random 固定成 0，验证码就恒为 chars[0] = 'AAAA'，可以稳定自动登录。
 *
 * 运行：node logs/verify-split-page.cjs
 */
// playwright-core 不在本仓库 node_modules 里，用托管工作区那份（与本机 Edge 搭配，不额外下载浏览器）
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const SHOT = 'E:/pork-traceability/logs/page-split-tree.png';
const CAPTCHA = 'AAAA'; // Math.random 恒为 0 时 chars[0] 重复 4 次

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
  check('登录后离开了登录页', !page.url().includes('/login'), `地址 ${page.url()}`);

  await page.goto(`${BASE}/admin/distribution/split`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(7000);
  console.log(`  [INFO] 当前地址 = ${page.url()}`);

  const visibleText = await page.evaluate(() => document.body.innerText || '');
  const treeNodes = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.tree-node .node-name')).map((el) => (el.textContent || '').trim())
  );
  const treeNos = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.tree-node .node-batch')).map((el) => (el.textContent || '').trim())
  );
  const badges = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.tree-node .node-badge, .tree-node .node-pkg'))
      .map((el) => (el.textContent || '').trim())
  );
  console.log(`  [INFO] 树上节点（${treeNodes.length} 个）= ${JSON.stringify(treeNodes.slice(0, 12))}`);
  console.log(`  [INFO] 节点编号 = ${JSON.stringify(treeNos.slice(0, 12))}`);
  console.log(`  [INFO] 节点小标签 = ${JSON.stringify(badges.slice(0, 12))}`);

  check('已进入分割操作页（批次拆分树可见）', visibleText.includes('批次拆分树'), `地址 ${page.url()}`);

  // 分层结构：根 + 每头白条一组 + 组内已出部位 + 未产出对照
  const jobNodes = treeNodes.filter((t) => t.startsWith('白条 · '));
  const missingGroups = treeNodes.filter((t) => /^未产出标准部位 \d+ 项$/.test(t));
  const spNos = treeNos.filter((t) => t.startsWith('SP'));
  const pNos = treeNos.filter((t) => t.startsWith('P'));
  console.log(`  [INFO] 白条组 = ${JSON.stringify(jobNodes)}，对照组 = ${JSON.stringify(missingGroups)}`);
  console.log(`  [INFO] 已出部位 ${spNos.length} 个 / 未产出叶子 ${pNos.length} 个`);

  check('树上恢复了「每头白条一组」（标题「白条 · 编号」）', jobNodes.length >= 1, JSON.stringify(treeNodes));
  check('白条组下同时有「已出部位」（SP…）', spNos.length > 0, JSON.stringify(treeNos));
  check('白条组下有「未产出标准部位 N 项」对照组', missingGroups.length === 1, JSON.stringify(missingGroups));
  check('未产出叶子的编号是字典部位码（P 开头）', pNos.length > 0, JSON.stringify(treeNos));
  check('已出 + 未出 = 叶子全部挂在白条组下（部位一个不少）',
    spNos.length + pNos.length === treeNos.length, `no 合计 ${treeNos.length}`);
  check('树上没有「分割作业」这个叫法（已统一为「白条 · …」）', !treeNodes.includes('分割作业'), JSON.stringify(treeNodes));
  check('页面正文不出现「分割作业」', !visibleText.includes('分割作业'));
  check('页面正文不出现「出肉率」', !visibleText.includes('出肉率'));
  check('页面正文不出现「对账」', !visibleText.includes('对账'));
  check('页面正文不出现「必出缺」', !visibleText.includes('必出缺'));
  check('节点标签里没有单独的「齐」', !badges.some((t) => t === '齐'), JSON.stringify(badges));

  // 分割车间监控：照片按**当前批次**归属，不再是「相机最近若干张」——
  // 卡片标题必须挂着当前批次号，否则说明页面又退回成「跟批次无关的相机流」了。
  // 当前批次号从树卡片标题取（根节点的编号位是空的，取不到批次号）
  const cardTitle = await page.locator('.tree-card-header').first().innerText();
  const treeBatchNo = (cardTitle.match(/CB[\w-]+/) || [''])[0];
  const monitorText = (await page.locator('.workshop-monitor-card').innerText()).replace(/\s+/g, ' ');
  console.log(`  [INFO] 当前批次 = ${treeBatchNo}`);
  console.log(`  [INFO] 监控区 = ${monitorText.slice(0, 140)}`);
  check('分割车间监控卡片在页面上', monitorText.includes('分割车间监控'), monitorText.slice(0, 120));
  check('监控区标题挂着当前批次号（照片按批次归属）',
    !!treeBatchNo && monitorText.includes(treeBatchNo), `批次=${treeBatchNo} 区块=${monitorText.slice(0, 140)}`);
  check('监控区不再写「本页展示最近 N 张」这种与批次无关的口径',
    !monitorText.includes('本页展示最近'), monitorText.slice(0, 140));

  check('控制台没有 pageerror', errors.length === 0, errors.join(' | '));

  await page.screenshot({ path: SHOT, fullPage: false });
  await browser.close();
  console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`);
  console.log(`截图：${SHOT}`);
  process.exit(fail === 0 ? 0 : 1);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
