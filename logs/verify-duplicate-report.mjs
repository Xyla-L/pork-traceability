#!/usr/bin/env node
/**
 * 一次性验证：同一条数据重复上报时，不会在待确认队列里堆出重复记录。
 *
 * 背景：接入层原有幂等键是 device_no + biz_key。设备若每次自造新唯一号重推，
 * 就绕过了它——数据卡在「待人工处理」时，队列里会堆出一串一模一样的记录。
 * 现新增一道兜底：同设备 + 同通道 + 同一份报文，只要已经收过就直接回既有结论、不新建，
 * 覆盖「待处理 / 待人工处理」与「已入账」三种状态；「已拒绝 / 已降采样」仍放行
 * （前者要给外部条件补齐后重发的机会，后者本来就不写业务表）。
 *
 * 验证六件事（用「耳标不存在」构造必然转人工的报文）：
 *   1) 首次上报进入「待人工处理」
 *   2) 换新上报编号、报文一模一样 → 返回同一条记录（duplicate=true），队列总数不变
 *   3) 报文改一个字段 → 正常新建（不能误伤真的新数据）
 *   4) 上一条已被拒绝后，同样的报文可以重新上报
 *   5) 队列总数与预期一致（没有偷偷多堆记录）
 *   6) 已入账的数据再报一次 → 同样被判重复、不新建、业务侧不产生新记录
 *      （复用队列里已有的已入账记录，不额外造业务数据）
 *
 * 用法：node logs/verify-duplicate-report.mjs
 * 说明：脚本会把自己造的上报记录逐条拒绝（留痕不删除），控制台最后会打印这些 id，
 *       需要清库时按打印结果自行处理。
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

/** 待确认队列条数（全量，用于确认「没有多堆记录」） */
async function queueCount() {
  const res = await api('/ingest/staging?pageNum=1&pageSize=1')
  return res.data?.total ?? -1
}

/** 带新唯一号（留空自动生成）上报，模拟「设备自造新编号重推」 */
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

  const devices = ((await api('/ingest/devices?channel=ENTRY')).data) || []
  const device = devices.find((d) => d.status === 1)
  if (!device) throw new Error('没有启用的入场查验设备，无法验证')
  console.log(`② 使用设备 ${device.deviceNo}（${device.channelLabel}）\n`)

  // 耳标不存在 → 必然转人工，正好用来构造「卡在待确认」的场景
  const data = { earTagNo: `VERIFY-NOT-EXIST-${STAMP}`, batchNo: `B-VERIFY-${STAMP}`, weight: 100 }
  const before = await queueCount()

  // ---------- 1) 首次上报 ----------
  const r1 = await report(device.deviceNo, data)
  check('首次上报进入「待人工处理」', r1.status === 1, `statusLabel=${r1.statusLabel}，原因：${r1.message}`)
  check('首次上报不是重复（duplicate=false）', r1.duplicate === false)

  // ---------- 2) 换新编号、报文相同 → 应被判为重复 ----------
  const r2 = await report(device.deviceNo, data)
  check('同报文换新编号重发：被判为重复上报', r2.duplicate === true, `duplicate=${r2.duplicate}`)
  check('重发返回的是同一条记录，没有新建', r2.stagingId === r1.stagingId,
    `首次 id=${r1.stagingId}，重发 id=${r2.stagingId}`)
  check('待确认队列条数没有增加', (await queueCount()) === before + 1,
    `上报前 ${before}，首报后+重发后 ${await queueCount()}（期望 ${before + 1}）`)

  // ---------- 3) 报文不同 → 应正常新建 ----------
  const r3 = await report(device.deviceNo, { ...data, weight: 101 })
  check('报文内容不同时正常新建', r3.duplicate === false && r3.stagingId !== r1.stagingId,
    `id=${r3.stagingId}`)
  check('新记录同样进入待人工处理', r3.status === 1)
  check('队列条数变为 +2', (await queueCount()) === before + 2)

  // ---------- 4) 已被拒绝的数据允许重报（外部条件补齐后要能补录） ----------
  for (const id of [r1.stagingId, r3.stagingId]) {
    await api(`/ingest/staging/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ remark: '验证脚本自清理：重复上报拦截验证' }),
    })
  }
  const r4 = await report(device.deviceNo, data)
  check('上一条已到终态后，同样的报文可以重新上报', r4.duplicate === false && r4.stagingId !== r1.stagingId,
    `id=${r4.stagingId}`)
  check('队列条数变为 +3', (await queueCount()) === before + 3)

  // ---------- 5) 已经入账的数据，再报一次必须被拦 ----------
  // 复用队列里已有的「已入账」记录（设备仍在启用），不额外造业务数据、不污染演示库
  const enabled = ((await api('/ingest/devices?status=1')).data) || []
  const enabledNos = new Set(enabled.map((d) => d.deviceNo))
  const acceptedPage = await api('/ingest/staging?status=2&pageNum=1&pageSize=100')
  const accepted = (acceptedPage.data?.records || []).find((r) => enabledNos.has(r.deviceNo))
  if (!accepted) {
    console.log('⚠️  跳过第 5 项：队列里没有「已入账」且设备仍在启用的记录')
  } else {
    const beforeAccepted = await queueCount()
    const res = await api('/ingest/demo/report', {
      method: 'POST',
      body: JSON.stringify({ deviceNo: accepted.deviceNo, data: JSON.parse(accepted.payload) }),
    })
    const r5 = res.data || {}
    check('已入账的数据重发：被判为重复上报', r5.duplicate === true, `duplicate=${r5.duplicate}`)
    check('已入账的数据重发：返回原记录，没有新建', r5.stagingId === accepted.id,
      `原 id=${accepted.id}，重发 id=${r5.stagingId}`)
    check('已入账的数据重发：业务侧未产生新记录', r5.targetId === accepted.targetId,
      `原记录编号 ${accepted.targetId}，重发后 ${r5.targetId}`)
    const afterAccepted = await queueCount()
    check('已入账的数据重发：队列条数没有增加', afterAccepted === beforeAccepted,
      `${beforeAccepted} → ${afterAccepted}`)
  }

  // ---------- 收尾：把自己造的数据拒绝掉 ----------
  if (r4.status === 1) {
    await api(`/ingest/staging/${r4.stagingId}/reject`, {
      method: 'POST',
      body: JSON.stringify({ remark: '验证脚本自清理：重复上报拦截验证' }),
    })
  }

  console.log(`\n本次产生的上报记录 id：${[...new Set(created)].join('、')}`)
  console.log(`${failed === 0 ? '全部通过 ✅' : `有 ${failed} 项未通过 ❌`}`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => { console.error('脚本异常：', e.message); process.exit(1) })
