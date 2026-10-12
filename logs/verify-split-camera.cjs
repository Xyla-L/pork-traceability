/**
 * 分割车间监控「自动连拍」端到端核验（浏览器侧 / Playwright + Edge）。
 *
 * 验的是一条串联口径：**分割线上报 → 工位相机自动连拍 → 照片绑定这次分割 → 分割页按批次看得到**。
 *   1. 在「设备接入 → 模拟设备上报」选「分割线扫码称重台」，填示例值并上报；
 *   2. 上报入账后**不用任何人再点上传**，页面自己冒出「分割车间监控 · 已完成 3/3 张」；
 *   3. 切到「分割操作」选中该批次，监控区里能看到这 3 张照片；
 *   4. 3 张照片的**文件内容两两不同**（不是同一张图重复报三遍）。
 *
 * 为什么必须放到浏览器里验：第 2、3、4 步都是**页面行为**——
 * 后端接口被测过了，但「有没有真的自动拍、页面上有没有真的画出来」只有把页面跑起来才知道。
 *
 * ⚠️ 这个脚本会**真的产生一次分割**（一条作业 + N 条部位 + 3 张快照 + 链上记录），
 *    跑完必须清理：见脚本末尾打印的 #BATCHNO / #SINCE，按它们删（只删自己造的）。
 *
 * 登录：登录页验证码是纯前端 canvas 生成的，把 Math.random 固定成 0 → 恒为 'AAAA'。
 * 运行：node logs/verify-split-camera.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const SHOT = 'E:/pork-traceability/logs/page-split-camera.png';
const CAPTCHA = 'AAAA';
/** 相机连拍节奏（与 web-admin/src/utils/splitCamera.ts 的常量一致）：2 秒一张、共 3 张 */
const EXPECT_SHOTS = 3;

let pass = 0;
let fail = 0;
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  [PASS] ${name}`); }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`); }
};

const pad = (n) => String(n).padStart(2, '0');
const nowText = () => {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

(async () => {
  const since = nowText();
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 300)));

  await page.addInitScript(() => { Math.random = () => 0; });

  // ---------- 登录 ----------
  await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', CAPTCHA);
  await page.locator('button.login-btn').click();
  await page.waitForTimeout(6000);
  check('登录成功', !page.url().includes('/login'), `地址 ${page.url()}`);

  // ---------- 设备接入：选分割称重台、填示例值、上报 ----------
  await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(4000);
  await page.locator('button:has-text("模拟设备上报")').first().click();
  await page.waitForTimeout(1200);
  check('模拟上报弹窗已打开', await page.locator('.el-dialog:visible').count() > 0);

  // 接入设备选「分割线扫码称重台」（注意别选到 CAM-SPLIT-001，它名字里也含 SPLIT-001）
  await page.locator('.el-dialog:visible .el-select').first().click();
  await page.waitForTimeout(600);
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: /^SPLIT-001 ·/ }).first().click();
  await page.waitForTimeout(1200);
  const dialogText = await page.locator('.el-dialog:visible').innerText();
  console.log(`  [INFO] 弹窗通道 = ${(dialogText.match(/分割批次[^\n]*/) || ['(未识别)'])[0]}`);
  check('通道是「分割批次（扫码称重）」', dialogText.includes('分割批次'), dialogText.slice(0, 200));

  // 填充示例值（会给一个真实胴体批次 + 一整份产出清单）
  await page.locator('.el-dialog:visible button:has-text("填充示例值")').click();
  await page.waitForTimeout(3000);
  const batchNo = (await page.inputValue('input[placeholder^="需是已存在的白条批次"]')).trim();
  console.log(`  [INFO] 示例批次号 = ${batchNo}`);
  check('示例值给出了真实胴体批次号', /^CB/.test(batchNo), `batchNo=${batchNo}`);

  // 上报
  const t0 = Date.now();
  await page.locator('.el-dialog:visible .el-dialog__footer button:has-text("上报")').first().click();

  // ---------- 等自动连拍跑完 ----------
  let shotText = '';
  for (let i = 0; i < 60; i++) {
    await page.waitForTimeout(1000);
    const block = page.locator('.el-dialog:visible .camera-shot');
    if (await block.count()) {
      shotText = (await block.innerText()).replace(/\s+/g, ' ').trim();
      if (shotText.includes(`已完成 ${EXPECT_SHOTS}/${EXPECT_SHOTS}`)) break;
    }
  }
  const cost = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`  [INFO] 连拍耗时 ≈ ${cost}s；相机区块 = ${shotText || '(没出现)'}`);
  check('上报后界面自动出现「分割车间监控」连拍区块', !!shotText);
  check(`无人操作的情况下自动拍完 ${EXPECT_SHOTS} 张（相机自己在拍）`,
    shotText.includes(`已完成 ${EXPECT_SHOTS}/${EXPECT_SHOTS}`), shotText);
  check('连拍区块点名了绑定批次（照片归属这次分割）', shotText.includes(batchNo), shotText);
  check('连拍区块点名了相机设备号', shotText.includes('CAM-SPLIT-001'), shotText);

  const receipt = await page.locator('.el-dialog:visible .demo-result').first().innerText();
  check('分割上报已入账（回执在）', receipt.includes('已入账'), receipt.replace(/\s+/g, ' ').slice(0, 160));

  await page.screenshot({ path: SHOT, fullPage: false });

  // ---------- 分割操作页：按批次能看到这组照片，且三张内容不同 ----------
  await page.goto(`${BASE}/admin/distribution/split?batchNo=${encodeURIComponent(batchNo)}`,
    { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(7000);

  const monitorText = await page.locator('.workshop-monitor-card').innerText();
  const photoSrcs = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.workshop-monitor-card .monitor-photo img'))
      .map((el) => el.getAttribute('src') || ''));
  console.log(`  [INFO] 监控区照片（${photoSrcs.length} 张）= ${JSON.stringify(photoSrcs.map((s) => s.slice(-12)))}`);
  check('分割操作页的监控区显示了照片', photoSrcs.length >= EXPECT_SHOTS, `张数=${photoSrcs.length}`);
  check('监控区标题挂着当前批次号（照片按批次归属，不是「相机最近几张」）',
    monitorText.includes(batchNo), monitorText.replace(/\s+/g, ' ').slice(0, 160));
  check('每张照片都是独立文件（fileId 不重复）',
    new Set(photoSrcs).size === photoSrcs.length, JSON.stringify(photoSrcs));

  // 直接取文件内容比一比：三张照片必须「图不一样」，不能是同一张图重复上报
  const newest = photoSrcs.slice(0, EXPECT_SHOTS).map((s) => s.split('/file/')[1]);
  const digests = await page.evaluate(async (ids) => {
    const out = [];
    for (const id of ids) {
      const buf = await (await fetch(`/api/v1/file/${id}`)).arrayBuffer();
      const bytes = new Uint8Array(buf);
      let h = 2166136261;
      for (let i = 0; i < bytes.length; i++) { h ^= bytes[i]; h = Math.imul(h, 16777619); }
      out.push({ size: bytes.length, hash: (h >>> 0).toString(16) });
    }
    return out;
  }, newest);
  console.log(`  [INFO] 照片指纹 = ${JSON.stringify(digests)}`);
  check(`${EXPECT_SHOTS} 张照片的文件内容两两不同（不是同一张图重复报）`,
    digests.length === EXPECT_SHOTS && new Set(digests.map((d) => d.hash)).size === EXPECT_SHOTS,
    JSON.stringify(digests));

  check('控制台没有 pageerror', errors.length === 0, errors.join(' | '));

  await browser.close();
  console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`);
  console.log(`截图：${SHOT}`);
  console.log('#BATCHNO ' + batchNo);
  console.log('#SINCE ' + since);
  process.exit(fail === 0 ? 0 : 1);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
