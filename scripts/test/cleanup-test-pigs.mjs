#!/usr/bin/env node
/**
 * 清理「验收测试残留」的生猪档案及其下游记录。
 *
 * 背景：`ingest-acceptance-test.mjs` 的养殖建档用例用设备通道建了 ET-ACC-* 前缀的猪，
 * 但后续用例又拿同一头猪去走屠宰检验，导致这些档案出现「已屠宰、却从未入场查验」的
 * 状态脱节——不是流程走反了，而是测试样本被跨环节复用，档案是不完整的。
 *
 * 处置原则（与项目一致）：**只作废、不删除**。作废需实名 + 理由，全程上链。
 * 覆盖范围：
 *   - 猪档案        （TAG 环节，设备优先 → 需兜底票据）
 *   - 免疫记录      （VACCINE 环节，设备优先 → 需兜底票据）
 *   - 屠宰检验记录  （INSPECTION 环节，设备独占 → 实名 + 理由即可）
 *   - 胴体盖章记录  （STAMP 环节，设备独占 → 实名 + 理由即可）
 *
 * 用法：
 *   node scripts/test/cleanup-test-pigs.mjs
 *   node scripts/test/cleanup-test-pigs.mjs --user admin --pass 123456 --base http://localhost:8080/api/v1
 */
const argv = process.argv.slice(2)
const argValue = (name, fallback) => {
  const i = argv.indexOf(name)
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback
}

const BASE = argValue('--base', 'http://localhost:8080/api/v1')
const ADMIN = { username: argValue('--user', 'admin'), password: argValue('--pass', '123456') }
const PREFIX = argValue('--prefix', 'ET-ACC-')
const REASON = '验收测试残留数据清理：该档案由 ingest-acceptance-test 脚本经设备通道建立，'
  + '状态被后续用例推进为「已屠宰」却无入场查验记录，属测试产物，作废以保持追溯数据自洽'

let token = null

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await response.json() } catch { /* 空响应体 */ }
  return { httpStatus: response.status, ...(body || {}) }
}

/** 从分页/数组响应里取出记录列表（兼容 records / list / 直接数组三种形态） */
function listOf(res) {
  const data = res?.data
  if (Array.isArray(data)) return data
  return data?.records || data?.list || []
}

async function login() {
  const res = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = res.data?.accessToken || res.data?.token || res.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(res))
  console.log(`登录成功（${ADMIN.username}）\n`)
}

async function unlockTicket(stage) {
  // 只有「设备优先」环节才需要兜底票据；设备独占环节人工入口已关闭，作废只需实名 + 理由。
  // 这里按需申请，申请不到不视为失败——策略可能随环节调整，脚本不该写死。
  const res = await api('/ingest/fallback/unlock', {
    method: 'POST',
    body: JSON.stringify({ stage, reason: REASON }),
  })
  const ticket = res.data?.ticket
  if (ticket) {
    console.log(`  ${stage} 兜底票据已申请（有效期至 ${res.data?.expireTime}）`)
  } else {
    console.log(`  ${stage} 无需兜底票据（该环节为设备独占，人工入口已关闭）`)
  }
  return ticket || null
}

/** 需要时拼上兜底票据请求头 */
function ticketHeader(ticket) {
  return ticket ? { 'X-Fallback-Ticket': ticket } : {}
}

const ok = (res) => Number(res?.code) === 200

function report(label, res) {
  console.log(`    ${ok(res) ? '✅' : '❌'} ${label}${ok(res) ? ' 已作废' : ' → ' + (res?.message || res?.code)}`)
  return ok(res)
}

async function main() {
  await login()

  const pigs = listOf(await api(`/breeding/pigs?earTagNo=${encodeURIComponent(PREFIX)}&current=1&size=200`))
    .filter((p) => String(p.earTagNo || '').startsWith(PREFIX))

  if (!pigs.length) {
    console.log(`没有找到 ${PREFIX} 前缀的测试残留猪，无需清理。`)
    return
  }

  console.log(`找到 ${pigs.length} 头测试残留猪：${pigs.map((p) => p.earTagNo).join('、')}\n`)
  console.log('检查兜底票据需求：')
  const tagTicket = await unlockTicket('TAG')
  const vaccineTicket = await unlockTicket('VACCINE')
  console.log('')

  let voidedPigs = 0
  let counts = { vaccine: 0, inspection: 0, stamp: 0 }

  for (const pig of pigs) {
    console.log(`猪档案 #${pig.id} ${pig.earTagNo}（当前状态 ${pig.statusLabel || pig.status}）`)

    // 1) 免疫记录：设备优先环节，作废需票据
    const vaccines = listOf(await api(`/breeding/pigs/${pig.id}/vaccines/list`))
    for (const v of vaccines) {
      const res = await api(`/breeding/pigs/${pig.id}/vaccines/${v.id}/void`, {
        method: 'POST',
        headers: ticketHeader(vaccineTicket),
        body: JSON.stringify({ reason: REASON }),
      })
      if (report(`免疫记录 #${v.id}（${v.vaccineName || ''}）`, res)) counts.vaccine++
    }

    // 2) 屠宰检验：设备独占环节，实名 + 理由即可
    const inspections = listOf(await api(`/slaughter/inspections?pigId=${pig.id}&pageNum=1&pageSize=100`))
    for (const s of inspections) {
      const res = await api(`/slaughter/inspections/${s.id}/void`, {
        method: 'POST',
        body: JSON.stringify({ reason: REASON }),
      })
      if (report(`屠宰检验 #${s.id}（${s.inspectNo || ''}）`, res)) counts.inspection++
    }

    // 3) 胴体盖章：设备独占环节，实名 + 理由即可
    const stamps = listOf(await api(`/slaughter/stamps?pigId=${pig.id}&pageNum=1&pageSize=100`))
    for (const s of stamps) {
      const res = await api(`/slaughter/stamps/${s.id}/void`, {
        method: 'POST',
        body: JSON.stringify({ reason: REASON }),
      })
      if (report(`胴体盖章 #${s.id}（${s.stampNo || ''}）`, res)) counts.stamp++
    }

    // 4) 猪档案：设备优先环节，作废需票据
    const res = await api(`/breeding/pigs/${pig.id}/void`, {
      method: 'POST',
      headers: ticketHeader(tagTicket),
      body: JSON.stringify({ reason: REASON }),
    })
    if (report('猪档案', res)) voidedPigs++
    console.log('')
  }

  console.log('─'.repeat(60))
  console.log(`清理完成：猪档案 ${voidedPigs}/${pigs.length}，`
    + `免疫 ${counts.vaccine} 条，检验 ${counts.inspection} 条，盖章 ${counts.stamp} 条均转为「已作废」`)
  console.log('记录仍在库中且带作废理由与实名操作人，追溯侧可核对，未做物理删除。')
}

main().catch((error) => {
  console.error('清理失败：', error?.message || error)
  process.exit(1)
})
