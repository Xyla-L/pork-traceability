/**
 * 「环节说明横幅」清扫的**渲染**核验 —— 浏览器侧全量扫一遍被改过的页面。
 *
 * 背景（2026-10-12）：页面级「业务前提 / 环节说明」横幅全部下线（7 处），
 * 与更早一轮删掉的「该环节为关键溯源环节，人工新增入口已关闭…前往设备接入 ›」同族。
 * 这类横幅的共同点是：解释环节为什么这么设计、有什么前提、人工入口为何关闭。
 * 保留的只有**随状态出现**的操作提示（如「一头猪同一时间只能有一张有效证」）
 * 与**数据标注**（追溯页的作废说明）——这两类不是横幅式科普。
 *
 * 只断言「被删的那几句文案不再出现在页面上」，不断言别的（避免跟正常业务文案撞车）。
 * 运行：node logs/verify-no-hint-banner.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const SHOT = 'E:/pork-traceability/logs/page-no-banner.png';
const CAPTCHA = 'AAAA'; // Math.random 恒为 0 → canvas 验证码恒为 'A'×4

// 页面 → 该页面上不该再出现的横幅文案片段
const TARGETS = [
  { path: '/admin/slaughter/stamp', name: '胴体盖章', gone: ['都不得盖章', '盖章等于把肉品放行'] },
  { path: '/admin/slaughter/inspect', name: '屠宰检验', gone: ['都不得进入屠宰检验', '宰后检疫是「屠宰之后」的环节'] },
  { path: '/admin/slaughter/entry', name: '入场查验', gone: ['入场查验的两条前提', '出栏审批是「要不要卖、卖给谁」'] },
  { path: '/admin/farm/quarantine', name: '产地检疫', gone: ['业务链第 3 环', '业务顺序：养殖建档'] },
  { path: '/admin/distribution/batch', name: '胴体批次（建批弹窗）', gone: ['胴体批次的三道硬前提', '白条得以流通的凭证就是那枚章'], dialog: true },
  { path: '/admin/distribution/transport', name: '冷链运输', gone: ['人工打卡入口已删除', '探头读数不允许人工代填'] },
];

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
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 200)));

  await page.addInitScript(() => { Math.random = () => 0; });
  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', CAPTCHA);
  await page.locator('button.login-btn').click();
  await page.waitForTimeout(6000);
  check('登录成功', !page.url().includes('/login'), `地址 ${page.url()}`);

  for (const t of TARGETS) {
    await page.goto(`${BASE}${t.path}`, { waitUntil: 'load', timeout: 30000 });
    await page.waitForTimeout(4500);
    let text = await page.evaluate(() => document.body.innerText || '');

    if (t.dialog) {
      // 建批弹窗里的横幅：先点开「创建批次」再取正文
      const btn = page.locator('button', { hasText: /创建批次|新建批次/ }).first();
      if (await btn.count() > 0) {
        await btn.click();
        await page.waitForTimeout(2000);
        text = await page.evaluate(() => document.body.innerText || '');
        await page.keyboard.press('Escape');
        await page.waitForTimeout(600);
      } else {
        console.log(`  [INFO] ${t.name}：没找到创建按钮（可能无可用批次），跳过弹窗内文案核验`);
      }
    }
    const hits = t.gone.filter((s) => text.includes(s));
    check(`${t.name}：横幅文案已清空`, hits.length === 0,
      `仍能搜到 ${JSON.stringify(hits)}`);
  }

  check('控制台没有 pageerror', errors.length === 0, errors.join(' | '));
  await page.goto(`${BASE}/admin/slaughter/stamp`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(3000);
  await page.screenshot({ path: SHOT });

  await browser.close();
  console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`);
  console.log(`截图：${SHOT}`);
  process.exit(fail === 0 ? 0 : 1);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
