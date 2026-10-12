#!/usr/bin/env node
/**
 * 应用侧验证：一致性修复（17 号脚本）之后，作废是否真的「从应用里消失」。
 * 数据侧不变量由 logs/verify-reconcile.sql 负责，本脚本只看接口返回。
 *
 *   node logs/verify-reconcile.mjs
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }

let token = null
let pass = 0
let fail = 0

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await res.json() } catch { /* 空响应 */ }
  return { httpStatus: res.status, ...(body || {}) }
}

const listOf = (res) => {
  const d = res?.data
  if (Array.isArray(d)) return d
  return d?.records || d?.list || []
}
const totalOf = (res) => Number(res?.data?.total ?? listOf(res).length)

function check(label, ok, detail = '') {
  if (ok) { pass++; console.log(`  ✅ ${label}${detail ? ' — ' + detail : ''}`) }
  else { fail++; console.log(`  ❌ ${label}${detail ? ' — ' + detail : ''}`) }
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login))
  console.log('登录成功\n')

  console.log('【1】胴体批次：已作废批次不应出现')
  const batches = await api('/distribution/batches?current=1&size=200')
  const batchNos = listOf(batches).map((b) => b.batchNo)
  // 基线随环境演进：24 = 22 条种子/历史批次 + CB20261009-001（2026-10-09 界面演示设备归集产生）
  // + CB20261012-001（2026-10-12 界面演示：用户做了一次分割，验证车间监控照片归属）。
  // 两条都是真实业务数据（DEVICE 来源、1 头白条、归集中），不是测试残留，所以调基线而不是删数据。
  check('批次总数已收敛', totalOf(batches) === 24, `total=${totalOf(batches)}（预期 24）`)
  check('已作废批次 CB20260916956996 不在列表中', !batchNos.includes('CB20260916956996'))
  check('有效批次 CB-DEMO-0001 仍在', batchNos.includes('CB-DEMO-0001'))
  check('批次列表未回传作废标记为 1 的行', listOf(batches).every((b) => Number(b.voidFlag ?? 0) === 0))

  console.log('\n【2】分割批次：只有 1 个该被作废')
  const splits = await api('/distribution/splits?pageNum=1&pageSize=200')
  const splitNos = listOf(splits).map((s) => s.batchNo)
  // 基线随环境演进：39 = 库里 40 条分割记录 - 1 条已作废。其中两组来自界面演示（真实业务数据，不是残留）：
  //   · 5 条 SP20261009-001-01..05（2026-10-09 14:44，作业 SV20261009-001，出肉率 91.72%，对账通过）
  //   · 5 条 SP20261012-001-01..05（2026-10-12 13:39，作业 SV20261012-001，用户做的一次分割）
  // 所以调基线，不删数据。
  check('分割批次总数 = 39（40 - 1 已作废）', totalOf(splits) === 39, `total=${totalOf(splits)}`)
  check('已作废 SP20260916859403 不在列表', !splitNos.includes('SP20260916859403'))
  check('属于有效猪的 SP20260917959868 仍在（未被连坐）', splitNos.includes('SP20260917959868'))
  check('种子分割 SP-TEST-0002 仍在', splitNos.includes('SP-TEST-0002'))

  console.log('\n【3】零售：作废商品不可见，且扫码给出「已作废」而非「不存在」')
  const records = await api('/sales/records?pageNum=1&pageSize=200')
  const qrs = (listOf(records).map((r) => r.productQrCode || r.qrCode || r.productCode)).filter(Boolean)
  check('零售记录 = 34（46 - 12 已作废）', totalOf(records) === 34, `total=${totalOf(records)}`)
  check('已作废商品码不在列表', !qrs.some((q) => String(q).startsWith('QR-PORK-D2F05C2E')), `样本=${qrs.slice(0, 3).join(',')}`)
  check('有效商品 QR-PORK-TEST-0001 仍在', qrs.includes('QR-PORK-TEST-0001'))
  check('回归：属于有效猪的商品 QR-PORK-221C43C6… 仍在', qrs.some((q) => String(q).startsWith('QR-PORK-221C43C6')))

  const voidedQr = 'QR-PORK-D2F05C2E0EB741A3BA23C9CE8EFF676E'
  const detail = await api(`/sales/internal/products/qr/${voidedQr}`)
  const msg = String(detail?.message || '')
  check('扫码已作废商品 → 明确提示「已作废」', msg.includes('已作废') && msg.includes('作废理由'), msg.slice(0, 60))

  console.log('\n【4】生猪档案：有效 27 头，补录后链路字段齐全')
  const pigs = await api('/breeding/pigs?current=1&size=300')
  // 基线 = 22 头种子/测试猪 + 5 头用户演示猪（ET-DEMO-1791185982174 / 1791193435869 / 1791194024670
  // / 1791783002362 / 1791787264150）。后两头是 2026-10-12 用户在界面上做的完整演示：
  //   · 305（13:30 建档 → 入场 13:34 → 盖章 13:37 → 归批 CB20261012-001 → 分割 SV20261012-001）
  //   · 306（14:41 建档 → 入场 14:43 → 宰前/宰后检验 14:43）
  // 都是真实业务数据，不清；跑完回归若高于 27，说明有测试残留没清干净。
  check('有效生猪 27 头', totalOf(pigs) === 27, `total=${totalOf(pigs)}（预期 27）`)
  check('已作废猪 ET-DEMO-0002 不在列表', !listOf(pigs).some((p) => p.earTagNo === 'ET-DEMO-0002'))
  const p11 = listOf(pigs).find((p) => p.earTagNo === 'ET-TEST-0011')
  check('ET-TEST-0011 仍在且出生日期有值', !!p11 && !!p11.birthDate, p11 ? `birthDate=${p11.birthDate}` : '未找到')

  console.log('\n【5】屠宰检验：补录记录可查（ET-TEST-0011 应有 2 条检验）')
  const insp = await api(`/slaughter/inspections?pigId=${p11?.id}&pageNum=1&pageSize=50`)
  check('ET-TEST-0011 屠宰检验 = 2 条', totalOf(insp) === 2, `total=${totalOf(insp)}`)
  check('包含宰前与宰后', listOf(insp).map((i) => Number(i.inspectType)).sort().join(',') === '1,2')

  console.log('\n' + '─'.repeat(56))
  console.log(`结果：${pass} 通过 / ${fail} 失败`)
  if (fail > 0) process.exit(1)
}

main().catch((e) => { console.error('验证失败：', e?.message || e); process.exit(1) })
