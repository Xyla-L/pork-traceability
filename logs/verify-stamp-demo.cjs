/**
 * 胴体盖章「填充示例值」口径 + 设备接入页横幅清理 端到端核验（浏览器 + 接口）。
 *
 * 背景（2026-10-12 实修）：`carcass_stamp` 上 `pig_id` 与 `carcass_no` **都是唯一键**，
 * 而盖章通道第一步就是按**胴体编号**查重 —— 撞上既有记录会直接返回那一条、回执照样是
 * 「已入账」。原先示例值把胴体编号写死成 `CT-当天-001`：第一头白条用完这个号之后，
 * 同一天给第二头白条盖章的上报被**静默吃掉**（接入流水里两条上报的 target_id 指向同一条），
 * 看着成功、库里什么都没写。本脚本验的就是这条口径：
 *
 *   1. 设备接入页已无提示横幅（弹窗内 + 两个页签），`.el-alert` 恒为 0；
 *   2. 盖章通道「填充示例值」给出的是**尚未盖章**的白条，且胴体编号是**当天新的**（CT-yyyyMMdd-HHmmss）；
 *   3. 点上报 → 回执「已入账」→ 库里真的**多了一条**盖章记录（不是回既有记录）；
 *   4. 同一头白条换个编号再报 → **转人工**并说明两条编号不一致（不再撞唯一键冒成系统异常）。
 *
 * ⚠️ 这个脚本会**真的产生一条盖章记录**（一条 carcass_stamp + 上链 + 2 条接入流水）。
 *    跑完必须清理：`logs/verify-stamp-demo-db.sh` 按打印出来的 #STAMPID / #BIZKEY_* 删（只删自己造的）。
 *
 * 登录：登录页验证码是纯前端 canvas 生成的，把 Math.random 固定成 0 → 恒为 'AAAA'。
 * 运行：node logs/verify-stamp-demo.cjs
 */
const { chromium } = require('C:/Users/17875/.workbuddy/binaries/node/workspace/node_modules/playwright-core');

const BASE = 'http://localhost';
const CAPTCHA = 'AAAA';
/** 盖章机终端（台账里的设备号，与图库演示数据一致） */
const STAMP_DEVICE = 'STAMPER-001';

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
const todayText = () => {
  const d = new Date();
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`;
};

let token = null;
async function api(path, { method = 'GET', body } = {}) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = `Bearer ${token}`;
  const res = await fetch(`${BASE}/api/v1${path}`, {
    method, headers: h, body: body ? JSON.stringify(body) : undefined,
  });
  return res.json().catch(() => null);
}

(async () => {
  const since = nowText();
  const bizKeyConflict = `STAMP-CONFLICT-${Date.now()}`;
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 300)));
  await page.addInitScript(() => { Math.random = () => 0; });

  try {
    // ---------- 登录 ----------
    await page.goto(`${BASE}/login`, { waitUntil: 'load', timeout: 30000 });
    await page.waitForTimeout(1500);
    await page.fill('input[placeholder="请输入用户名"]', 'admin');
    await page.fill('input[placeholder="请输入密码"]', '123456');
    await page.fill('input[placeholder="请输入验证码"]', CAPTCHA);
    await page.locator('button.login-btn').click();
    await page.waitForTimeout(6000);
    check('登录成功', !page.url().includes('/login'), `地址 ${page.url()}`);

    const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } });
    token = login?.data?.token || login?.data?.accessToken;
    check('接口侧也拿到登录态（后续按业务键核对用）', !!token);

    // ---------- 1) 提示横幅已清空 ----------
    await page.goto(`${BASE}/admin/ingest/monitor`, { waitUntil: 'load', timeout: 30000 });
    await page.waitForTimeout(4000);
    const stagingAlerts = await page.locator('.el-tab-pane:visible .el-alert').count();
    check('「待确认队列」页签已无提示横幅', stagingAlerts === 0, `横幅数=${stagingAlerts}`);

    await page.locator('.el-tabs__item:has-text("设备台账")').click();
    await page.waitForTimeout(1500);
    const deviceAlerts = await page.locator('.el-tab-pane:visible .el-alert').count();
    check('「设备台账」页签已无提示横幅', deviceAlerts === 0, `横幅数=${deviceAlerts}`);

    // ---------- 2) 盖章通道填示例值 ----------
    await page.locator('button:has-text("模拟设备上报")').first().click();
    await page.waitForTimeout(1200);
    await page.locator('.el-dialog:visible .el-select').first().click();
    await page.waitForTimeout(600);
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
      .filter({ hasText: new RegExp(`^${STAMP_DEVICE} ·`) }).first().click();
    await page.waitForTimeout(1500);

    const dialogAlerts = await page.locator('.el-dialog:visible .el-alert').count();
    check('「模拟设备上报」弹窗里已无提示横幅', dialogAlerts === 0, `横幅数=${dialogAlerts}`);
    const dialogText = await page.locator('.el-dialog:visible').innerText();
    check('通道是「胴体自动盖章」', dialogText.includes('胴体盖章'), dialogText.slice(0, 120));

    // 填之前的盖章台账（用来断言「挑的是没盖过章的白条」）
    const stampsBefore = (await api('/slaughter/stamps?pageNum=1&pageSize=200'))?.data?.records || [];
    const stampedPigsBefore = new Set(stampsBefore.map((s) => Number(s.pigId)));

    await page.locator('.el-dialog:visible button:has-text("填充示例值")').click();
    await page.waitForTimeout(3000);

    const fieldInput = (label) =>
      page.locator(`.el-dialog:visible .el-form-item:has(label:text-is("${label}")) input`).first();
    const earTag = (await fieldInput('耳标号').inputValue()).trim();
    const carcassNo = (await fieldInput('胴体编号').inputValue()).trim();
    const batchNo = (await fieldInput('批次号').inputValue()).trim();
    console.log(`  [INFO] 示例值：耳标=${earTag} 批次=${batchNo} 胴体编号=${carcassNo}`);
    check('示例值给出了耳标与胴体编号', !!earTag && !!carcassNo, `耳标=${earTag} 胴体=${carcassNo}`);
    check('胴体编号不再写死当天 -001，而是带时刻的新号（CT-yyyyMMdd-HHmmss）',
      new RegExp(`^CT-${todayText()}-\\d{6}$`).test(carcassNo), `carcassNo=${carcassNo}`);

    const hit = stampsBefore.find((s) => (s.earTagNo || '').trim() === earTag);
    check('挑中的白条是「还没盖过章」的那一头（一头白条只有一枚章）', !hit,
      hit ? `耳标 ${earTag} 已有盖章记录 id=${hit.id}` : '');
    const sampleHint = await page.locator('.el-dialog:visible .demo-actions .sub-cell').innerText().catch(() => '');
    console.log(`  [INFO] 提示语 = ${sampleHint.replace(/\s+/g, ' ').slice(0, 140)}`);

    // ---------- 3) 真上报一次：必须多出一条记录 ----------
    const stampsBeforeCount = stampsBefore.length;
    await page.locator('.el-dialog:visible .el-dialog__footer button:has-text("上报")').first().click();
    await page.waitForTimeout(5000);
    const receiptText = (await page.locator('.el-dialog:visible .demo-result').first().innerText()).replace(/\s+/g, ' ');
    console.log(`  [INFO] 回执 = ${receiptText.slice(0, 180)}`);
    check('上报回执是「已入账」', receiptText.includes('已入账'), receiptText.slice(0, 180));
    const bizKeySuccess = (receiptText.match(/(ST|DEMO|STAMP)[\w-]*\d{6,}/) || [''])[0];

    const stampsAfter = (await api('/slaughter/stamps?pageNum=1&pageSize=200'))?.data?.records || [];
    const fresh = stampsAfter.find((s) => (s.earTagNo || '').trim() === earTag && (s.carcassNo || '').trim() === carcassNo);
    console.log(`  [INFO] 盖章台账：${stampsBeforeCount} → ${stampsAfter.length}`);
    check('库里真的多了一条盖章记录（不是回既有记录）', stampsAfter.length === stampsBeforeCount + 1,
      `${stampsBeforeCount} → ${stampsAfter.length}`);
    check('新记录就是这次上报的耳标 + 胴体编号', !!fresh, JSON.stringify(stampsAfter.slice(0, 2)));

    // ---------- 4) 同一头白条换个编号再报：转人工，不再冒数据库异常 ----------
    const conflict = await api('/ingest/demo/report', {
      method: 'POST',
      body: {
        deviceNo: STAMP_DEVICE,
        bizKey: bizKeyConflict,
        data: {
          earTagNo: earTag,
          batchNo,
          carcassNo: `CT-${todayText()}-999999`,
          veterinary: '李官方兽医',
        },
      },
    });
    console.log(`  [INFO] 冲突上报回执 = ${(conflict?.message || JSON.stringify(conflict)).slice(0, 200)}`);
    check('同一头白条换编号重报 → 不再抛系统异常', !!conflict && conflict.code === 200,
      JSON.stringify(conflict)?.slice(0, 200));
    check('同一头白条换编号重报 → 转人工（待人工确认）', conflict?.data?.status === 1 || conflict?.data?.accepted === false,
      JSON.stringify(conflict?.data)?.slice(0, 200));
    check('回执说明了「两条编号不一致」（而不是含糊的失败）',
      String(conflict?.data?.message || '').includes('不一致'), String(conflict?.data?.message || ''));

    const stampsFinal = (await api('/slaughter/stamps?pageNum=1&pageSize=200'))?.data?.records || [];
    check('冲突上报没有多写任何记录', stampsFinal.length === stampsBeforeCount + 1,
      `${stampsBeforeCount + 1} → ${stampsFinal.length}`);

    check('控制台没有 pageerror', errors.length === 0, errors.join(' | '));

    // ---------- 清理用的 ID ----------
    console.log('\n----- 清理用（交给 logs/verify-stamp-demo-db.sh）-----');
    console.log(`#STAMPID=${fresh?.id ?? ''}`);
    console.log(`#PIGID=${fresh?.pigId ?? ''}`);
    console.log(`#CARCASSNO=${carcassNo}`);
    console.log(`#SAFENO=CT-${todayText()}-999999`);
    console.log(`#BIZKEY_CONFLICT=${bizKeyConflict}`);
    console.log(`#BIZKEY_SUCCESS=${bizKeySuccess}`);
    console.log(`#SINCE=${since}`);
  } finally {
    await browser.close();
  }

  console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail === 0 ? 0 : 1);
})().catch((e) => {
  console.error('脚本异常：', e);
  process.exit(1);
});
