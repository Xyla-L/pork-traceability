#!/usr/bin/env node
/**
 * 一次性验证：养殖建档通道（耳标识读器）出生日期必填。
 *
 * 背景：出生日期是建档的必备身份信息（影响月龄与可出栏判断），设备侧漏传必须被挡住。
 * 不能只靠前端「没填不让点」——绕过页面直接调接口也要拦住，所以校验落在接入层。
 *
 * 验证四件事：
 *   1) 不传 birthDate → 已拒绝（status=3），原因里点名「出生日期」
 *   2) birthDate 格式不对（如 2026/03/15）→ 已拒绝，原因里点名格式
 *   3) 传合法 birthDate → 通过校验并入账（这里用已存在的耳标，业务侧返回既有档案，
 *      不会新增生猪档案，避免污染演示数据）
 *   4) 被拒的两次都没有写入任何业务数据（target 为空）
 *
 * 用法：node logs/verify-tag-birthdate.mjs
 * 说明：脚本会把产生的上报记录 id 打印出来（均为终态：已拒绝 / 已入账），需要清库时按 id 处理。
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }
const STAMP = Date.now()

let token = null
let failed = 0
const created = []

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await response.json() } catch { /* 空响应体 */ }
  return { httpStatus: response.status, ...(body || {}) }
}

function check(label, ok, extra = '') {
  console.log(`${ok ? '✅' : '❌'} ${label}${extra ? ' — ' + extra : ''}`)
  if (!ok) failed++
}

/** n 天前的日期（yyyy-MM-dd）：育肥猪的出生日期不会就是今天 */
function daysAgo(days) {
  const d = new Date()
  d.setDate(d.getDate() - days)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function report(deviceNo, data) {
  const res = await api('/ingest/demo/report', {
    method: 'POST',
    body: JSON.stringify({ deviceNo, data }),
  })
  if (!res.data) throw new Error('上报失败：' + JSON.stringify(res).slice(0, 200))
  if (res.data.stagingId) created.push(res.data.stagingId)
  return res.data
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('① 登录成功')

  const devices = ((await api('/ingest/devices?channel=TAG')).data) || []
  const device = devices.find((d) => d.status === 1)
  if (!device) throw new Error('没有启用的养殖建档设备，无法验证')
  console.log(`② 使用设备 ${device.deviceNo}（${device.channelLabel}）`)

  const farmPage = (await api('/breeding/farms?current=1&size=1')).data
  const farmName = (farmPage?.records || [])[0]?.farmName
  if (!farmName) throw new Error('没有已登记的养殖场，无法验证（应先到「养殖管理 → 养殖场」登记）')
  console.log(`③ 使用已登记养殖场「${farmName}」`)

  const pigPage = (await api('/breeding/pigs?current=1&size=1')).data
  const existing = (pigPage?.records || [])[0]
  if (!existing) throw new Error('生猪档案为空，无法复用既有耳标（脚本刻意不新建档案以免污染数据）')
  console.log(`④ 复用既有耳标 ${existing.earTagNo}（档案 id=${existing.id}）\n`)

  // ---------- 1) 漏传出生日期 ----------
  const r1 = await report(device.deviceNo, {
    earTagNo: `ET-VERIFY-NOBIRTH-${STAMP}`,
    farmName,
  })
  check('漏传出生日期：已被拒绝', r1.status === 3, `status=${r1.status}（${r1.statusLabel}）`)
  check('拒绝原因点名「出生日期」', String(r1.message || '').includes('出生日期'), r1.message)
  check('漏传出生日期：没有写入业务数据', !r1.targetId, `记录编号=${r1.targetId ?? '空'}`)

  // ---------- 2) 出生日期格式不对 ----------
  const r2 = await report(device.deviceNo, {
    earTagNo: `ET-VERIFY-BADBIRTH-${STAMP}`,
    farmName,
    birthDate: '2026/03/15',
  })
  check('出生日期格式错误：已被拒绝', r2.status === 3, `status=${r2.status}（${r2.statusLabel}）`)
  check('拒绝原因点名「格式不正确」', String(r2.message || '').includes('格式不正确'), r2.message)
  check('出生日期格式错误：没有写入业务数据', !r2.targetId, `记录编号=${r2.targetId ?? '空'}`)

  // ---------- 3) 合法的出生日期 ----------
  const birthDate = daysAgo(90)
  const r3 = await report(device.deviceNo, {
    earTagNo: existing.earTagNo,
    farmName,
    birthDate,
    breed: '三元杂',
    gender: 1,
  })
  check('合法出生日期：通过校验并入账', r3.status === 2, `status=${r3.status}（${r3.statusLabel}），${r3.message}`)
  check('返回的是既有档案，未新增生猪', r3.targetId === existing.id,
    `既有档案 id=${existing.id}，返回 targetId=${r3.targetId}`)

  console.log(`\n本次产生的上报记录 id：${[...new Set(created)].join('、')}`)
  console.log(`${failed === 0 ? '全部通过 ✅' : `有 ${failed} 项未通过 ❌`}`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => { console.error('脚本异常：', e.message); process.exit(1) })
