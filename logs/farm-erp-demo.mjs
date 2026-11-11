#!/usr/bin/env node
/**
 * 演示：养殖系统同步（外部 ERP）—— 用真实报文走完整接入链路。
 *
 * 这不是「往库里塞假数据」：脚本只做一件事 —— 以外部养殖 ERP 的身份，用它的设备密钥
 * 调我们的接入接口（POST /ingest/device/report）。之后判重、状态机、归一化、落库、上链、
 * 页面刷新，全部由系统自己跑。所以演示结束后，ingest_staging 里躺着原始 JSON、
 * 链上有一串事件，谁都能回头查「它当初到底推了什么」。
 *
 * 六个动作（对应四份报文模板，都在 logs/farm-erp/ 下，改模板即可改演示）：
 *   1 正常推一批 30 头       → 已入账，生猪档案页立刻出现批次与成员
 *   2 原样重推（同编号）     → 重复上报，不新建流水
 *   3 换编号重推（同报文）   → 重复上报，兜底判重也拦得住
 *   4 另一批次号、同一段耳标 → 待人工处理（一头猪只能属于一个批次）
 *   5 源系统撤销             → 批次与成员作废、上链，成员记录保留
 *   6 撤销后再推             → 待人工处理（撤销不能被重推抹掉）
 *
 * 用法：
 *   node logs/farm-erp-demo.mjs                       # 用默认场（示范生态养殖场）
 *   FARM_NAME=宏图养殖公司 node logs/farm-erp-demo.mjs
 *   node logs/farm-erp-demo.mjs --json                # 只打印回执 JSON（排查用）
 *
 * 演示后如需清掉这批演示数据：bash logs/verify-farm-erp-db.sh --clean
 */

import fs from 'node:fs'
import path from 'node:path'

const BASE = process.env.BASE_URL || 'http://localhost/api/v1'
const DEVICE_KEY = process.env.DEVICE_KEY || 'farm-erp-demo-key-2026'
const FARM = process.env.FARM_NAME || '示范生态养殖场'
const JSON_ONLY = process.argv.includes('--json')

const dir = path.join(import.meta.dirname, 'farm-erp')

function dayText(offsetDays) {
  const d = new Date(Date.now() + offsetDays * 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} `
    + `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 读模板 → 替换占位符 → 返回可发送对象。报文内容一律来自文件，不在这里拼字段。 */
function loadTemplate(name, vars) {
  const raw = fs.readFileSync(path.join(dir, name), 'utf8')
  const filled = raw.replace(/\{\{(\w+)\}\}/g, (_, key) => {
    if (vars[key] === undefined) throw new Error(`模板 ${name} 里的占位符 {{${key}}} 没有对应取值`)
    return vars[key]
  })
  const parsed = JSON.parse(filled)
  delete parsed._说明
  return parsed
}

async function push(body) {
  const res = await fetch(`${BASE}/ingest/device/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Device-Key': DEVICE_KEY },
    body: JSON.stringify(body),
  })
  const json = await res.json().catch(() => ({}))
  if (!json.data) {
    throw new Error(`上报失败 HTTP ${res.status}：${json.message || JSON.stringify(json)}`)
  }
  return json.data
}

function line(title) {
  if (JSON_ONLY) return
  console.log(`\n\x1b[1m${title}\x1b[0m`)
}

function say(text) {
  if (!JSON_ONLY) console.log('  ' + text)
}

function receipt(data) {
  if (JSON_ONLY) {
    console.log(JSON.stringify(data))
    return
  }
  const mark = data.accepted ? '✅' : data.status === 1 ? '🟡' : data.status === 5 ? '🔁' : '⛔'
  console.log(`  ${mark} ${data.statusLabel}｜${data.message}`)
  if (data.targetId) console.log(`     落库：${data.targetTable} #${data.targetId}`)
}

;(async () => {
  const RUN = Date.now().toString().slice(-8)
  const vars = { RUN, FARM, ENTRY: dayText(-20), ENTRY_FIXED: dayText(-17), BIRTH: dayText(-90), NOW: nowText() }

  if (!JSON_ONLY) {
    console.log('══ 养殖系统同步演示 ═════════════════════════════════════════')
    console.log(`  外部系统：DEMO_ERP（养殖场生产管理系统，场：${FARM}）`)
    console.log(`  接入方式：POST /ingest/device/report，通道 FARM_ERP，设备密钥 X-Device-Key`)
    console.log(`  本轮批次：ERP-${RUN}（每轮一个新号，避免与上一轮演示撞车）`)
  }

  // 1 正常推送
  line('1️⃣  ERP 推一批自繁猪（30 头，耳标连续区间）')
  const upsert = loadTemplate('01-batch-upsert.json', vars)
  const created = await push(upsert)
  receipt(created)
  say('系统动作：建批次 + 按耳标区间展开 30 头成员，来源标「养殖系统同步」，日龄按出生日期现算')
  say('页面落点：养殖管理 → 生猪档案，批次号 ERP-' + RUN + '，档案来源列显示「养殖系统同步」')

  // 2 原样重推（同编号）
  line('2️⃣  ERP 把同一条报文又推了一次（同一个上报编号）')
  const again = await push(upsert)
  receipt(again)
  say('系统动作：判重第一道命中（同设备 + 同编号），不新建流水、不重复入账')

  // 3 换编号重推（同报文）
  line('3️⃣  ERP 换个上报编号，报文内容一模一样')
  const renumber = { ...upsert, bizKey: `ERP-${RUN}-1b` }
  const dupPayload = await push(renumber)
  receipt(dupPayload)
  say('系统动作：判重第二道命中（同设备 + 同通道 + 同报文语义相等），照样拦下')

  // 4 撞号
  line('4️⃣  另一批却用了同一段耳标（ERP 侧录错）')
  const conflict = await push(loadTemplate('02-tag-conflict.json', vars))
  receipt(conflict)
  say('系统动作：不覆盖、不删单，转「待人工处理」——外部系统的数据不等于正确的数据')

  // 5 源撤销
  line('5️⃣  ERP 撤销这一批（录错了，整批撤）')
  const revoked = await push(loadTemplate('03-batch-revoke.json', vars))
  receipt(revoked)
  say('系统动作：批次与 30 头成员标记作废 + 上链（void_source=EXTERNAL），记录本身留着')

  // 6 撤销后再推
  line('6️⃣  撤销之后，ERP 把这一批改正后又推了回来（进场日期改了）')
  const reapply = await push(loadTemplate('04-reapply-after-revoke.json', vars))
  receipt(reapply)
  say('系统动作：报文有变化所以过得去判重，但已撤销的档案不自动「复活」，转人工核对——'
    + '撤销是一条留痕的结论，不能被一次重推抹掉')

  if (JSON_ONLY) return

  const ok = created.accepted && again.duplicate && dupPayload.duplicate
    && conflict.status === 1 && revoked.accepted && reapply.status === 1
  console.log('\n── 演示结果：' + (ok ? '六步全部符合预期 ✅' : '有步骤不符合预期，请看上面回执 ❌'))
  console.log('   想核对落库细节：bash logs/verify-farm-erp-db.sh')
  console.log('   想清掉这批演示数据：bash logs/verify-farm-erp-db.sh --clean')
  process.exit(ok ? 0 : 1)
})().catch((e) => {
  console.error('\n演示失败：', e.message)
  process.exit(1)
})
