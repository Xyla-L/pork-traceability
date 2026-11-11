#!/usr/bin/env node
/**
 * 存证链路验收（2026-11-06）：
 *   ① 存证模式如实：local 必须自报「未写入区块链」，不能冒充链上
 *   ② 存证记录无长期积压、eventId 唯一、已存证记录哈希与块号齐全
 *   ③ 账本与存证记录不悬空；近 24 小时不新增孤儿块
 *   ④ /health 与库内数字一致；/records/status-map 返回真实状态
 *   ⑤ 覆盖缺口补齐：生猪建档端到端上链（自持：一次性批次，验完作废）
 *   ⑥ 审计页展示「存证体检」，且本地模式下不出现「区块链已验证」字样
 *
 * 写入仅限 ⑤ 的一次性测试批次（唯一前缀 CHNTEST*，验完作废，不碰演示台账）。
 * 用法：node logs/verify-blockchain.mjs
 */
import { chromium } from './node_modules/playwright-core/index.mjs'
import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

const BASE = process.env.SHOT_BASE || 'http://localhost'
const API = process.env.API_URL || 'http://localhost:8080/api/v1'

async function api(path, opt = {}, token) {
  const headers = { ...(opt.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }
  const res = await fetch(API + path, { method: opt.method || 'GET', headers, body: opt.body ? JSON.stringify(opt.body) : undefined })
  return res.json().catch(() => null)
}

function sqlOne(sql) {
  try {
    return execSync(
      `docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 -N -B --default-character-set=utf8mb4 -e "${sql}"`,
      { stdio: 'pipe', shell: 'bash' }
    ).toString().trim()
  } catch {
    return ''
  }
}

let pass = 0, fail = 0
const check = (c, n, d = '') => { c ? pass++ : fail++; console.log(`  ${c ? '✔' : '✘'} ${n}${d ? ' — ' + d : ''}`) }

const token = (await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } }))?.data?.token
check(!!token, '前置：管理员登录成功')
if (!token) process.exit(1)

// ── ① 存证模式如实 ──
const modeRes = (await api('/blockchain/mode', {}, token))?.data || {}
const local = modeRes.mode === 'local'
check(['local', 'evm'].includes(modeRes.mode), '① 存证模式取值合法（local / evm）', String(modeRes.mode))
check(modeRes.onChain === (modeRes.mode === 'evm'), '① onChain 与 mode 一致',
  `mode=${modeRes.mode} onChain=${modeRes.onChain}`)
if (local) {
  check(/未写入区块链/.test(modeRes.summary || ''), '① 本地模式明确说明「未写入区块链」', (modeRes.summary || '').slice(0, 60))
  check(!modeRes.explorerUrl, '① 本地模式不提供区块浏览器地址', String(modeRes.explorerUrl))
} else {
  check(!!modeRes.explorerUrl || modeRes.contractAddress !== null, '① 链上模式给出核验入口（浏览器或合约地址）')
}

// ── ② 存证记录本身的一致性 ──
const total = Number(sqlOne('SELECT COUNT(*) FROM db_common.blockchain_record') || 0)
const success = Number(sqlOne('SELECT COUNT(*) FROM db_common.blockchain_record WHERE status=1') || 0)
const stale = Number(sqlOne("SELECT COUNT(*) FROM db_common.blockchain_record WHERE status IN (0,2) AND update_time < NOW() - INTERVAL 24 HOUR") || 0)
const dupEvent = Number(sqlOne('SELECT COUNT(*) - COUNT(DISTINCT event_id) FROM db_common.blockchain_record') || 0)
const noHash = Number(sqlOne("SELECT COUNT(*) FROM db_common.blockchain_record WHERE status=1 AND (tx_hash IS NULL OR tx_hash='' OR block_number IS NULL OR block_number<=0)") || 0)

check(stale === 0, '② 没有超过 24 小时仍未完成的存证', `积压 ${stale} 条`)
check(dupEvent === 0, '② eventId 唯一（重复消费不会写出两条记录）', `重复 ${dupEvent} 条`)
check(noHash === 0, '② 已存证记录都有哈希与块号', `缺失 ${noHash} 条`)

// ── ③ 账本 ↔ 存证配对 ──
const dangling = Number(sqlOne(
  "SELECT COUNT(*) FROM db_common.blockchain_record r WHERE r.status=1 AND NOT EXISTS (SELECT 1 FROM db_common.blockchain_ledger l WHERE l.tx_hash=r.tx_hash)") || 0)
const orphanRecent = Number(sqlOne(
  "SELECT COUNT(*) FROM db_common.blockchain_ledger l WHERE l.chain_time >= NOW() - INTERVAL 24 HOUR AND NOT EXISTS (SELECT 1 FROM db_common.blockchain_record r WHERE r.tx_hash=l.tx_hash)") || 0)
const orphanAll = Number(sqlOne(
  'SELECT COUNT(*) FROM db_common.blockchain_ledger l WHERE NOT EXISTS (SELECT 1 FROM db_common.blockchain_record r WHERE r.tx_hash=l.tx_hash)') || 0)
check(dangling === 0, '③ 没有「显示已存证但账本查不到块」的悬空记录', `悬空 ${dangling} 条`)
check(orphanRecent === 0, '③ 近 24 小时没有新增孤儿块', `新增 ${orphanRecent} 个`)
console.log(`  ℹ 历史孤儿块 ${orphanAll} 个（早期 record 重建遗留，无法验真、不删除，页面如实披露）`)

// ── ④ health 与库内数字一致 ──
const health = (await api('/blockchain/health', {}, token))?.data || {}
check(Number(health.total) === total, '④ health.total 与库内记录数一致', `health=${health.total} db=${total}`)
check(Number(health.success) === success, '④ health.success 与库内已存证数一致', `health=${health.success} db=${success}`)
check(Number(health.danglingRecord) === dangling, '④ health.danglingRecord 与库内一致', `health=${health.danglingRecord} db=${dangling}`)
check(Number(health.orphanLedger) === orphanAll, '④ health.orphanLedger 与库内一致', `health=${health.orphanLedger} db=${orphanAll}`)
check(health.ok === (Array.isArray(health.issues) && health.issues.length === 0), '④ ok 与 issues 自洽',
  `ok=${health.ok} issues=${JSON.stringify(health.issues || [])}`)

// ── ⑤ status-map 要回真实状态，而不是「有哈希就算成功」 ──
const sample = (await api('/blockchain/records?bizType=STORE_RECEIPT&pageNum=1&pageSize=5', {}, token))?.data?.records?.[0]
if (sample) {
  const map = (await api(`/blockchain/records/status-map?bizType=STORE_RECEIPT&bizIds=${sample.bizId}`, {}, token))?.data || {}
  check(Number(map[String(sample.bizId)]) === Number(sample.status), '⑤ status-map 返回该签收单的真实存证状态',
    `接口=${map[String(sample.bizId)]} 记录=${sample.status}`)
} else {
  console.log('  ℹ 暂无 STORE_RECEIPT 存证记录，跳过 status-map 断言')
}

// ── ⑤ 覆盖缺口补齐：生猪建档端到端上链（自持一次性批次） ──
{
  const stamp = new Date().toISOString().replace(/\D/g, '').slice(2, 14) + String(Math.floor(Math.random() * 90) + 10)
  const batchNo = `CB-CHAINTEST-${stamp}`
  const tagStart = `CHNTEST${stamp.slice(-6)}01`
  const tagEnd = `CHNTEST${stamp.slice(-6)}02`
  const createRes = await api('/breeding/pigs/batches', { method: 'POST', body: {
    batchNo, farmId: 1, breed: '存证回归', entryDate: '2026-11-06',
    earTagStart: tagStart, earTagEnd: tagEnd,
  } }, token)
  const batchId = createRes?.data
  check(!!batchId, '⑤ 一次性测试批次登记成功', createRes?.message || `batchId=${batchId}`)
  if (batchId) {
    let record = null
    for (let i = 0; i < 15 && !record; i++) {
      await new Promise((r) => setTimeout(r, 1000))
      const page = (await api(`/blockchain/records?bizType=PIG_BATCH&bizId=${batchId}&pageNum=1&pageSize=5`, {}, token))?.data
      record = page?.records?.[0] || null
    }
    check(!!record, '⑤ 建档事件已进存证记录（PIG_BATCH）', record ? `status=${record.status}` : '15s 内未出现')
    if (record) {
      check(Number(record.status) === 1 && !!record.txHash && !!record.blockNumber,
        '⑤ 建档存证已入账且哈希块号齐全', `status=${record.status} tx=${(record.txHash || '').slice(0, 10)}`)
    }
    const voidRes = await api(`/breeding/pigs/batches/${batchId}/void`, { method: 'POST', body: { reason: '存证回归一次性批次，验完作废' } }, token)
    check(Number(voidRes?.code) === 200, '⑤ 测试批次已作废（自持清理）', voidRes?.message || '')
    const voidPage = await (async () => {
      for (let i = 0; i < 15; i++) {
        const page = (await api(`/blockchain/records?bizType=PIG_BATCH_VOID&bizId=${batchId}&pageNum=1&pageSize=5`, {}, token))?.data
        if (page?.records?.[0]) return page
        await new Promise((r) => setTimeout(r, 1000))
      }
      return null
    })()
    check(!!voidPage?.records?.[0], '⑤ 作废事件同样上链（PIG_BATCH_VOID）')
  }
}

// ── ⑥ 页面口径：审计页要有体检卡片，本地模式不许出现「区块链已验证」 ──
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1500, height: 1000 } })
try {
  await page.addInitScript(() => { Math.random = () => 0 })
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 20000 })
  await page.waitForTimeout(1000)
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[type="password"]', '123456')
  await page.fill('input[placeholder="请输入验证码"]', 'AAAA')
  await page.locator('button').filter({ hasText: /登\s*录/ }).first().click()
  await page.waitForURL((u) => u.pathname.startsWith('/admin'), { timeout: 20000 })
  await page.goto(`${BASE}/admin/system/audit`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2500)
  const text = (await page.locator('.page-container').innerText()).replace(/\s+/g, ' ')
  check(/存证体检/.test(text), '⑥ 审计页显示「存证体检」卡片')
  if (local) {
    check(/存证载体：平台本地账本/.test(text), '⑥ 本地模式如实标注载体', text.slice(0, 60))
    check(!/区块链已验证/.test(text), '⑥ 本地模式页面不出现「区块链已验证」')
    check(/未写入区块链/.test(text), '⑥ 页面说明本地模式未写入区块链')
  } else {
    check(/存证载体：区块链/.test(text), '⑦ 链上模式标注区块链载体')
  }
  await page.screenshot({ path: 'logs/page-blockchain-audit.png', fullPage: true })
  // 业务类型下拉必须能筛到新补齐的事件类型（否则链上有了、页面查不到）
  await page.locator('.el-form .el-select').first().click()
  await page.waitForTimeout(600)
  const options = (await page.locator('.el-select-dropdown__item').allInnerTexts()).map((s) => s.trim())
  for (const label of ['生猪建档', '发车确认', '到站确认']) {
    check(options.includes(label), `⑥ 业务类型下拉包含「${label}」`, options.length ? `共 ${options.length} 项` : '下拉未展开')
  }
  await page.keyboard.press('Escape')
} finally {
  await browser.close()
}

// ── ⑦ 真上链模式：交易必须在链上查得到、历史本地存证必须迁完 ──
if (!local) {
  check(!!modeRes.contractAddress, '⑦ 链上模式给出合约地址', String(modeRes.contractAddress))
  const legacy = Number((await api('/blockchain/health', {}, token))?.data?.legacyLocal ?? -1)
  check(legacy === 0, '⑦ 没有「写于本地账本时期」的未迁移记录', `legacyLocal=${legacy}`)
  const evmSample = (await api('/blockchain/records?bizType=STORE_RECEIPT&pageNum=1&pageSize=1', {}, token))?.data?.records?.[0]
  if (evmSample) {
    const rpc = process.env.EVM_RPC || 'http://localhost:8545'
    const tx = await fetch(rpc, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'eth_getTransactionByHash', params: [evmSample.txHash] }),
    }).then((r) => r.json()).catch(() => ({}))
    check(!!tx?.result, '⑦ 存证记录的交易哈希在链上真实存在', (evmSample.txHash || '').slice(0, 12))
    check(Number(tx?.result?.blockNumber) === Number(evmSample.blockNumber),
      '⑦ 记录里的区块号与链上一致', `链上=${tx?.result?.blockNumber} 记录=${evmSample.blockNumber}`)
  } else {
    console.log('  ℹ 暂无 STORE_RECEIPT 记录，跳过链上交易断言')
  }
}

// ── ⑧ 字典覆盖率：链上有的类型，页面必须叫得出名字 ──
// 口径两向收口：库里已存在的类型 + 代码里会发布的类型，都必须进 @/utils/chainBizType。
// 否则链上有存证、页面显示一串英文代码，等于查不到。
{
  const dictSrc = readFileSync('web-admin/src/utils/chainBizType.ts', 'utf8')
  const dictKeys = [...dictSrc.matchAll(/^\s{2}([A-Z][A-Z0-9_]*): \{/gm)].map((m) => m[1])
  const dbTypes = sqlOne('SELECT DISTINCT biz_type FROM db_common.blockchain_record')
    .split('\n').map((s) => s.trim()).filter(Boolean)
  let codeTypes = []
  try {
    const raw = execSync(
      `grep -rhoE '(publish|ChainTxMessage\\.create)\\("[A-Z][A-Z0-9_]*"' backend --include=*.java || true`,
      { shell: 'bash', encoding: 'utf8' }
    )
    codeTypes = [...new Set((raw.match(/"[A-Z][A-Z0-9_]*"/g) || []).map((s) => s.slice(1, -1)))]
  } catch { /* grep 不可用时只校验库内类型 */ }
  const missDb = dbTypes.filter((t) => !dictKeys.includes(t))
  const missCode = codeTypes.filter((t) => !dictKeys.includes(t))
  check(missDb.length === 0, '⑧ 库里每种存证类型都有中文名', missDb.length ? '缺：' + missDb.join(',') : `${dbTypes.length} 类全覆盖`)
  if (codeTypes.length) {
    check(missCode.length === 0, '⑧ 代码会发布的类型都已进字典', missCode.length ? '缺：' + missCode.join(',') : `${codeTypes.length} 类全覆盖`)
  }
}

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
process.exit(fail ? 1 : 0)
