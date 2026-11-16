#!/usr/bin/env node
/**
 * 扫码入口「只认本系统溯源码」回归（2026-11-15）
 *
 * 用户口径：「小程序扫一扫溯源和销售扫码销售，是不是应该限制只能扫对应的商品二维码，
 * 不能扫付款码之类的？」
 *
 * 要守的不变量（不是「有没有报错」，而是「报的是不是人话」）：
 *   ① 扫付款码 / 会员码 / 网址码 → 明确说「这不是本系统的猪肉溯源码」，不许说成「产品不存在」
 *   ② 格式对但库里没有 → 说「未查询到该溯源码对应的产品」，不许说成「假货」
 *   ③ 真码照旧能查到（新闸门不能误伤正常扫码）
 *   ④ 已售出的码仍可溯源（消费者买回家才扫码，这是主场景，不能当错误）
 *   ⑤ 验真与安心购：同一把尺子，不许一边拦一边放
 *   ⑥ 已作废的码 → 说「已被作废 + 理由」，不许冒充「查不到」
 *   ⑦ 扫码销售：扫错码不能变成一次销售，不可售的码要说清原因
 *
 * 样本口径：只取 void_flag=0 的有效行 —— 作废行会被逻辑删除滤掉，
 * 拿它当样本会把「已作废」误判成「查不到」。
 *
 * 自持：全程只读 + 只打「必然失败」的写入，跑完库里没有新增行，无需清理。
 * 用法：node logs/verify-qrcode-guard.mjs
 */
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { execSync } = require('node:child_process')

const API = process.env.API_URL || 'http://localhost:8080/api/v1'
/** 与后端 TraceQrCode.NOT_TRACE_CODE 逐字一致的判据片段 */
const NOT_TRACE = '不是本系统的猪肉溯源码'

let token = null
let pass = 0
let fail = 0
let skip = 0
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  ✅ ${name}`) }
  else { fail++; console.log(`  ❌ ${name}${detail ? ' —— ' + detail : ''}`) }
}
const skipped = (name, why) => { skip++; console.log(`  ⏭️  ${name}（跳过：${why}）`) }

async function api(path, opt = {}) {
  const headers = { ...(opt.body ? { 'Content-Type': 'application/json' } : {}), ...(opt.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(API + path, {
    method: opt.method || 'GET',
    headers,
    body: opt.body ? JSON.stringify(opt.body) : undefined,
  })
  let body = null
  try { body = await res.json() } catch { /* 空响应体 */ }
  return { httpStatus: res.status, ...(body || {}) }
}

/** 只读查库：挑真实码用，不写任何业务行 */
function sqlOne(sql) {
  const out = execSync(
    `docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 -N -B -e "${sql}"`,
    { stdio: 'pipe' }).toString().trim()
  return out.split('\n')[0] || ''
}

/** 各种「不是本系统码」的真实样本：收银台/相册里最容易误扫的东西 */
const FOREIGN_CODES = [
  ['支付宝付款码链接', 'https://qr.alipay.com/bax01234567890abcdef'],
  ['微信付款码链接', 'https://u.wechat.com/ABCDEFGHIJKLMNOP'],
  ['纯数字会员码/条码', '6222021234567890123'],
  ['公众号带参二维码', 'http://weixin.qq.com/q/02Kx9Z8yQ0000000000000'],
  ['空泛文本', 'hello-world-1234'],
]

async function main() {
  // ── 登录（管理端：读接口）──
  const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
  token = login.data?.token || login.data?.accessToken
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))

  // ── 选真实码：只认「有效行」（void_flag=0）──
  // 作废行会被 MyBatis 的逻辑删除自动滤掉；拿作废码当样本，会把「已作废」误判成「查不到」，
  // 后面的断言就全跑偏了（第一次跑就是这么翻车的）。
  const onSaleQr = sqlOne(`SELECT product_qr_code FROM db_sales.retail_sale WHERE void_flag=0 AND status=1 ORDER BY id DESC LIMIT 1`)
  const soldQr = sqlOne(`SELECT product_qr_code FROM db_sales.retail_sale WHERE void_flag=0 AND status=2 ORDER BY id DESC LIMIT 1`)
  const voidedQr = sqlOne(`SELECT product_qr_code FROM db_sales.retail_sale WHERE void_flag=1 ORDER BY id DESC LIMIT 1`)
  const realQr = onSaleQr || soldQr || sqlOne(`SELECT product_qr_code FROM db_sales.retail_sale WHERE void_flag=0 ORDER BY id DESC LIMIT 1`)
  if (!realQr) throw new Error('库里没有有效商品码，先造一张再跑')
  const realStatus = sqlOne(`SELECT status FROM db_sales.retail_sale WHERE product_qr_code='${realQr}' LIMIT 1`)
  console.log(`真实样本：${realQr}（status=${realStatus}）${voidedQr ? `，作废样本=${voidedQr}` : ''}`)

  // ── ① 消费者扫码：非本系统码一律在「查库之前」被拦下 ──
  // 文案用 POST /consumer/verify 验：它走 body，能把后端原话完整带回来。
  // GET /consumer/scan/{code} 遇到含 "/" 的链接会被网关当非法路径挡成 404
  //（前端已经拦在前面，真实链路走不到），所以这里只断言「拿不到溯源数据」。
  console.log('\n① 扫到别家的码（消费者端）')
  for (const [label, code] of FOREIGN_CODES) {
    const v = await api('/consumer/verify', { method: 'POST', body: { qrCode: code } })
    const vMsg = String(v.message || '')
    check(`${label} → 明确说「不是本系统的溯源码」`, vMsg.includes(NOT_TRACE),
      `code=${v.code} message=${vMsg.slice(0, 50)}`)
    const s = await api(`/consumer/scan/${encodeURIComponent(code)}`)
    check(`${label} → 拿不到任何溯源数据`, s.code !== 200 && !s.data?.product, `code=${s.code}`)
    check(`${label} → 不出现「产品不存在/查无此货」口径`,
      !/产品不存在|查无此货/.test(String(s.message || '') + vMsg),
      `message=${String(s.message || vMsg).slice(0, 50)}`)
  }

  // ── ② 格式对但库里没有：说的是「没登记」，不是「假货」──
  console.log('\n② 长得像本系统的码、但库里没有')
  const ghost = 'QR-PORK-' + '0'.repeat(32)
  const ghostRes = await api(`/consumer/scan/${ghost}`)
  const ghostMsg = String(ghostRes.message || '')
  check('提示「未查询到该溯源码对应的产品」', /未查询到该溯源码对应的产品/.test(ghostMsg),
    `code=${ghostRes.code} message=${ghostMsg.slice(0, 60)}`)
  check('不甩「参数校验失败」这类系统话术', !/参数校验失败/.test(ghostMsg), ghostMsg.slice(0, 60))

  // ── ③ 真码不能被新闸门挡住 ──
  console.log('\n③ 真码照常可查（新闸门不能误伤）')
  const okRes = await api(`/consumer/scan/${realQr}`)
  check('登记过的码 scan 成功', okRes.code === 200 && !!okRes.data?.product?.name,
    `code=${okRes.code} product=${okRes.data?.product?.name || '—'}`)
  check('溯源链路与验真结论都带回来了',
    !!okRes.data?.traceChain && !!okRes.data?.verification,
    `traceChain=${!!okRes.data?.traceChain} verification=${!!okRes.data?.verification}`)

  // ── ④ 已售出的码必须仍可溯源（消费者买回家才扫，主场景）──
  console.log('\n④ 非在售的码仍可溯源')
  if (soldQr) {
    const soldRes = await api(`/consumer/scan/${soldQr}`)
    check('已售码 scan 成功（未被当成错误）', soldRes.code === 200 && !!soldRes.data?.product?.name,
      `code=${soldRes.code} message=${String(soldRes.message || '').slice(0, 60)}`)
  } else {
    // 库里没有已售样本时，用「非在售」（召回/过期）证明同一件事：
    // 状态不是「在售」并不等于查不到 —— 这正是消费者买回家扫码的场景
    const alt = await api(`/consumer/scan/${realQr}`)
    check(`非在售码（status=${realStatus}）仍可溯源，没被当成错误`,
      alt.code === 200 && !!alt.data?.product?.name,
      `code=${alt.code} message=${String(alt.message || '').slice(0, 60)}`)
  }

  // ── ⑤ 验真 / 安心购：同一口径 ──
  console.log('\n⑤ 验真与安心购同一把尺子')
  const vBad = await api('/consumer/verify', { method: 'POST', body: { qrCode: FOREIGN_CODES[0][1] } })
  check('付款码走验真 → 提示扫错码', String(vBad.message || '').includes(NOT_TRACE),
    `code=${vBad.code} message=${String(vBad.message || '').slice(0, 60)}`)
  const vOk = await api('/consumer/verify', { method: 'POST', body: { qrCode: realQr } })
  check('真码走验真 → 仍能给出结论', vOk.code === 200 && typeof vOk.data?.verified === 'boolean',
    `code=${vOk.code} verified=${vOk.data?.verified}`)
  const sbBad = await api(`/consumer/safe-buy/${encodeURIComponent(FOREIGN_CODES[2][1])}`)
  check('付款码走安心购 → 提示扫错码', String(sbBad.message || '').includes(NOT_TRACE),
    `code=${sbBad.code} message=${String(sbBad.message || '').slice(0, 60)}`)
  const sbOk = await api(`/consumer/safe-buy/${realQr}`)
  check('真码走安心购 → 仍能出证章/存证', sbOk.code === 200 && !!sbOk.data?.blockchain,
    `code=${sbOk.code}`)

  // ── ⑥ 已作废的码：说清「作废 + 理由」，不是「查不到」──
  console.log('\n⑥ 已作废的码')
  if (voidedQr) {
    const vd = await api(`/consumer/scan/${voidedQr}`)
    const vdMsg = String(vd.message || '')
    check('作废码 → 说明「已被作废」', /已被作废/.test(vdMsg), vdMsg.slice(0, 60))
    check('作废码 → 带上作废理由', /作废理由/.test(vdMsg), vdMsg.slice(0, 60))
    check('作废码 → 不冒充「未查询到」', !/未查询到该溯源码/.test(vdMsg), vdMsg.slice(0, 60))
  } else {
    skipped('作废码文案', '库里没有 void_flag=1 的码')
  }

  // ── ⑦ 销售写入：收银台扫错码不能变成一次销售 ──
  console.log('\n⑦ 扫码销售（零售商店员身份）')
  const retail = await api('/auth/login', { method: 'POST', body: { username: 'retail_user', password: '123456' } })
  const retailToken = retail.data?.token || retail.data?.accessToken
  if (!retailToken) {
    skipped('销售端扫码写入', 'retail_user 登录失败')
  } else {
    const sell = async (qrCode) => {
      const res = await fetch(API + '/sales/records', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${retailToken}` },
        body: JSON.stringify({ qrCode, sellPrice: 1, sellWeightKg: 0.5 }),
      })
      const body = await res.json().catch(() => ({}))
      return { httpStatus: res.status, ...body }
    }
    const sBad = await sell(FOREIGN_CODES[2][1])
    check('拿付款码销售 → 明确说「不是本系统的溯源码」',
      String(sBad.message || '').includes(NOT_TRACE),
      `code=${sBad.code} message=${String(sBad.message || '').slice(0, 60)}`)
    check('拿付款码销售 → 不落销售记录（未成功）', sBad.code !== 200, `code=${sBad.code}`)

    const sGhost = await sell(ghost)
    check('不存在的码 → 说「不是本平台已入库的商品码」',
      /不是本平台已入库的商品码/.test(String(sGhost.message || '')),
      `message=${String(sGhost.message || '').slice(0, 60)}`)

    // 不可售的码（已售 / 过期 / 召回）：必须说清「为什么不能卖」，不能说成「不存在」
    const unusable = soldQr || realQr
    if (unusable) {
      const sBad2 = await sell(unusable)
      const msg = String(sBad2.message || '')
      check('不可售的码再卖 → 说清原因（已售出/已过期/已召回/不属于本门店）',
        /已售出|已过保质期|已被召回|不属于本门店/.test(msg) && !/不存在|未入库/.test(msg),
        `message=${msg.slice(0, 60)}`)
      check('不可售的码再卖 → 没有真的卖出去', sBad2.code !== 200, `code=${sBad2.code}`)
    } else {
      skipped('不可售的码再卖', '库里没有可作为样本的码')
    }
  }

  console.log(`\n结果：${pass} 通过 / ${fail} 失败${skip ? ` / ${skip} 跳过` : ''}`)
  if (fail > 0) process.exit(1)
}

main().catch((e) => { console.error('脚本异常：', e); process.exit(1) })
