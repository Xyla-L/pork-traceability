#!/usr/bin/env node
/**
 * 一次性验证：设备接入页「接入通道」列不再露出英文枚举名。
 *
 * 背景：IngestStagingVO / IngestDeviceVO 各自硬编码了一份通道中文名映射，只覆盖 5 个通道，
 * 其余走 `default -> channel`，于是 TAG/VACCINE/INSPECTION/STAMP/SPLIT 在页面上显示成英文。
 * 现已把中文名收敛到 IngestChannel.label()，两个 VO 共用。
 *
 * 验证三件事：
 *   1) /ingest/staging 里每条记录的 channelLabel 都与通道码对应的中文一致（不存在英文回显）
 *   2) /ingest/devices 同上
 *   3) 页面实际出现过的通道都被覆盖（不是只验首屏那几条）
 *
 * 用法：node logs/verify-channel-label.mjs
 */
const BASE = process.env.BASE || 'http://localhost:8080/api/v1'
const ADMIN = { username: 'admin', password: '123456' }

/** 期望值 = 前端 CHANNEL_LABELS（web-admin/src/api/modules/ingest.ts），两边必须一致 */
const EXPECT = {
  TAG: '养殖建档（耳标）',
  VACCINE: '免疫注射',
  QUARANTINE: '产地检疫出证',
  EXIT_APPLY: '出栏称重登记',
  ENTRY: '入场查验',
  RACTOPAMINE: '瘦肉精检测',
  INSPECTION: '屠宰检验（工位终端）',
  STAMP: '胴体自动盖章',
  BATCH: '胴体批次（分拣台）',
  SPLIT: '分割批次（扫码称重）',
  TEMPERATURE: '冷链温度采集',
  RECEIPT: '门店签收',
  SALE: '门店收银',
  LOAD: '装车发运（装车口）',
  ORDER: '订单转入（商家选购）',
  DEPART: '发车确认（车载终端）',
  ARRIVE: '到达确认（车载终端刷卡）',
  SPLIT_SNAPSHOT: '分割车间监控',
}

let token = null
let failed = 0

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

/** 只由 ASCII 字母/数字/下划线构成 → 说明是通道码原文漏到了界面上 */
function looksLikeCode(text) {
  return typeof text === 'string' && /^[A-Za-z0-9_-]+$/.test(text)
}

function audit(rows, sourceName) {
  const seen = new Map()
  const bad = []
  for (const row of rows) {
    const { channel, channelLabel } = row
    if (!channel) continue
    seen.set(channel, channelLabel)
    const want = EXPECT[channel]
    if (!want) { bad.push(`${channel}（未知通道，期望表里没有）`); continue }
    if (channelLabel !== want) bad.push(`${channel} → 期望「${want}」，实际「${channelLabel}」`)
  }
  console.log(`\n—— ${sourceName}（${rows.length} 条，涉及 ${seen.size} 种通道）`)
  for (const [channel, label] of seen) {
    console.log(`   ${channel.padEnd(12)} → ${label}${looksLikeCode(label) ? '   ← 英文！' : ''}`)
  }
  check(`${sourceName}：全部通道标签为中文`, bad.length === 0, bad.slice(0, 6).join('；'))
}

async function fetchAll(path, key) {
  const out = []
  for (let pageNum = 1; pageNum <= 20; pageNum++) {
    const res = await api(`${path}?pageNum=${pageNum}&pageSize=100`)
    const records = res.data?.records || res.data?.list || res.data
    if (!Array.isArray(records) || !records.length) break
    out.push(...records)
    if (records.length < 100) break
  }
  return out
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('① 登录成功')

  const staging = await fetchAll('/ingest/staging', 'records')
  audit(staging, '待确认队列 /ingest/staging')

  const devices = ((await api('/ingest/devices')).data) || []
  audit(devices, '设备台账 /ingest/devices')

  // 覆盖度检查：历史数据里出现过的通道，必须全部命中期望表
  const missing = [...new Set([...staging, ...devices].map((r) => r.channel))]
    .filter((c) => c && !EXPECT[c])
  check('历史数据出现过的通道都在期望表内', missing.length === 0, missing.join('、'))

  console.log(`\n${failed === 0 ? '全部通过 ✅' : `有 ${failed} 项未通过 ❌`}`)
  process.exit(failed === 0 ? 0 : 1)
}

main().catch((e) => { console.error('脚本异常：', e.message); process.exit(1) })
