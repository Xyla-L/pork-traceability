#!/usr/bin/env node
/**
 * 重复上报必须「提示该信息已存在 + 拒绝入账」。
 *
 * 口径（2026-10-19 起）：
 *   ① 同设备 + 同 bizKey                    → status=5「重复上报」，accepted=false
 *   ② 同设备 + 同通道 + 同一份报文（换号重推）→ 同上
 *   ③ 已入账的数据再报一次                  → 同上，业务表不新增、原记录不改
 *   ④ 报文内容不同                          → 正常新建（不能误伤真的新数据）
 *   ⑤ 回执 discardLocalCopy=true            → 设备端据此删缓存，不会无限重推
 *
 * 为什么**不新建流水**：重复上报若也落一行，设备重推几次队列就被刷满，
 * 人工还得逐条辨别哪条有效 —— 这恰恰是当初加兜底判重要解决的问题。
 *
 * 造数策略：用「耳标不存在」的入场查验报文构造必然转人工的数据（不写业务表）；
 * 「已入账重推」复用队列里现成的已入账记录，不额外制造业务数据。
 *
 * 用法：node logs/verify-duplicate-reject.mjs
 * 输出：# STAGING_IDS=... 供 verify-duplicate-reject-db.sh 清理
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: process.env.ADMIN_PASS || '123456' }
const STAMP = Date.now()
const PREFIX = `DUPCHK-${STAMP}`

let token = null
let failed = 0
const createdIds = []

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

/** 走演示上报入口（与设备上报共用同一条 receive 链路，只是不需要密钥头） */
async function report(deviceNo, data, bizKey) {
  const res = await api('/ingest/demo/report', {
    method: 'POST',
    body: JSON.stringify({ deviceNo, bizKey, data }),
  })
  if (!res.data) throw new Error('上报失败：' + JSON.stringify(res).slice(0, 300))
  if (res.data.stagingId) createdIds.push(res.data.stagingId)
  return res.data
}

async function queueCount() {
  const res = await api('/ingest/staging?pageNum=1&pageSize=1')
  return res.data?.total ?? -1
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('① 登录成功')

  const devices = (await api('/ingest/devices?channel=ENTRY')).data || []
  const device = devices.find((d) => d.status === 1)
  if (!device) throw new Error('没有启用的入场查验设备')
  console.log(`② 使用设备 ${device.deviceNo}（${device.channelLabel || device.channel}）\n`)

  // 耳标不存在 → 必然转人工，不写业务表，适合反复造数
  const data = { earTagNo: `${PREFIX}-NOT-EXIST`, batchNo: `${PREFIX}-B1`, weight: 100 }
  const before = await queueCount()

  console.log('== 1) 首次上报：正常受理（待人工） ==')
  const first = await report(device.deviceNo, data, `${PREFIX}-K1`)
  check('首报进入「待人工处理」', first.status === 1, `status=${first.status} ${first.statusLabel}`)
  check('首报不是重复（duplicate=false）', first.duplicate === false, `duplicate=${first.duplicate}`)

  console.log('\n== 2) 同设备同 bizKey 再报一次 → 提示已存在、拒绝入账 ==')
  const sameKey = await report(device.deviceNo, data, `${PREFIX}-K1`)
  check('★ 回执状态是「重复上报」', sameKey.status === 5, `status=${sameKey.status} ${sameKey.statusLabel}`)
  check('★ 明确拒绝入账（accepted=false）', sameKey.accepted === false, `accepted=${sameKey.accepted}`)
  check('★ 标出 duplicate=true', sameKey.duplicate === true, `duplicate=${sameKey.duplicate}`)
  check('★ 文案点明「该信息已存在」', String(sameKey.message || '').includes('该信息已存在'),
    `message=${sameKey.message}`)
  check('★ 指向原记录，没有新建', sameKey.stagingId === first.stagingId,
    `首报 id=${first.stagingId}，重报 id=${sameKey.stagingId}`)
  check('★ 回带原记录状态（知道原数据现在是什么状态）', sameKey.originalStatus === 1,
    `originalStatus=${sameKey.originalStatus} ${sameKey.originalStatusLabel}`)
  check('★ 告诉设备可以删本地缓存（不会无限重推）', sameKey.discardLocalCopy === true,
    `discardLocalCopy=${sameKey.discardLocalCopy}`)

  console.log('\n== 3) 换一个 bizKey、报文一模一样（设备自造新号重推）→ 同样拒绝 ==')
  const samePayload = await report(device.deviceNo, data, `${PREFIX}-K2`)
  check('★ 兜底判重生效（同设备同通道同报文）', samePayload.status === 5,
    `status=${samePayload.status} ${samePayload.statusLabel}`)
  check('★ 同样拒绝入账', samePayload.accepted === false, `accepted=${samePayload.accepted}`)
  check('★ 指回同一条原记录', samePayload.stagingId === first.stagingId,
    `id=${samePayload.stagingId} 期望=${first.stagingId}`)
  check('★ 文案说明是「相同报文」', String(samePayload.message || '').includes('相同报文'),
    `message=${samePayload.message}`)
  check('待确认队列没有多出记录', (await queueCount()) === before + 1,
    `上报前 ${before}，现在 ${await queueCount()}（期望 ${before + 1}）`)

  console.log('\n== 4) 报文改了一个字段 → 必须正常新建（不能误伤新数据） ==')
  const changed = await report(device.deviceNo, { ...data, weight: 101 }, `${PREFIX}-K3`)
  check('★ 内容不同就正常受理', changed.duplicate === false && changed.status === 1,
    `duplicate=${changed.duplicate} status=${changed.status}`)
  check('★ 是一条新的暂存记录', changed.stagingId !== first.stagingId,
    `id=${changed.stagingId} 首报 id=${first.stagingId}`)
  check('队列 +2（首报与改字段各一条）', (await queueCount()) === before + 2,
    `现在 ${await queueCount()}（期望 ${before + 2}）`)

  console.log('\n== 5) 重复上报不改动原记录 ==')
  const original = await api(`/ingest/staging/${first.stagingId}`)
  check('★ 原记录仍是「待人工处理」', original.data?.status === 1,
    `status=${original.data?.status} ${original.data?.statusLabel}`)

  console.log('\n== 6) 已入账的数据再报一次 → 拒绝，业务表不新增 ==')
  const enabled = (await api('/ingest/devices?status=1')).data || []
  const enabledNos = new Set(enabled.map((d) => d.deviceNo))
  const acceptedPage = await api('/ingest/staging?status=2&pageNum=1&pageSize=100')
  const accepted = (acceptedPage.data?.records || []).find((r) => enabledNos.has(r.deviceNo))
  if (!accepted) {
    console.log('⚠️  跳过：队列里没有「已入账」且设备仍在启用的记录')
  } else {
    const beforeAccepted = await queueCount()
    const res = await api('/ingest/demo/report', {
      method: 'POST',
      body: JSON.stringify({ deviceNo: accepted.deviceNo, data: JSON.parse(accepted.payload) }),
    })
    const again = res.data || {}
    check('★ 已入账数据重推：回「重复上报」', again.status === 5,
      `status=${again.status} ${again.statusLabel}`)
    check('★ 已入账数据重推：拒绝入账', again.accepted === false, `accepted=${again.accepted}`)
    check('★ 已入账数据重推：指回原记录', again.stagingId === accepted.id,
      `原 id=${accepted.id}，回 ${again.stagingId}`)
    check('★ 已入账数据重推：业务记录编号未变（没有写第二条）', again.targetId === accepted.targetId,
      `原 ${accepted.targetId}，回 ${again.targetId}`)
    check('★ 已入账数据重推：原状态如实回带', again.originalStatus === 2,
      `originalStatus=${again.originalStatus} ${again.originalStatusLabel}`)
    check('已入账数据重推：队列条数不变', (await queueCount()) === beforeAccepted,
      `${beforeAccepted} → ${await queueCount()}`)
    const after = await api(`/ingest/staging/${accepted.id}`)
    check('★ 已入账数据重推：原记录仍是「已入账」', after.data?.status === 2,
      `status=${after.data?.status}`)
  }

  const ids = [...new Set(createdIds)]
  console.log(`\n# STAGING_IDS=${ids.join(',')}`)
  console.log(`# BIZ_PREFIX=${PREFIX}`)
  console.log(`${failed === 0 ? '全部通过 ✅' : `有 ${failed} 项未通过 ❌`}`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => { console.error('脚本异常：', e.message); process.exit(1) })
