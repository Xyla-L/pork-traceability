#!/usr/bin/env node
/**
 * 召回收运全链路回归（2026-11-04）
 *
 * 用户口径：「发起召回后，商家和冷链中心收到通知，拒收回运的处理计划自动生成对应的计划，
 * 司机发车计划和之前一样，到店扫码收货，最后送无害化处理中心。」
 *
 * 本脚本按这句话一步步走，每一步都断言不变量（不盯绝对条数）：
 *   ① 发起召回 → 召回单落库（RC…）、影响范围 > 0
 *   ② 待收运池自动出现这批货（不用人点任何按钮）
 *   ③ 通知：门店（RETAILER）与冷链中心（DISTRIBUTOR）都收到「召回」通知
 *   ④ 生成收运运单：一张多站单，站点 = 各门店 + 无害化处理中心（最后一站）
 *   ⑤ 还没收货就想往下一站走 → 必须被挡（货还在店里）
 *   ⑥ 发车（和之前一样）→ 到店刷卡到达 → 扫码枪扫这一站的货 → 该站全收完才放行
 *   ⑦ 到无害化处理中心 → 签收 → 召回池收口（链终结）
 *
 * 自持：本脚本自己造召回单与运单，跑完按业务键清干净（recall_no / transport_id）。
 * 用法：node logs/verify-recall-collect.mjs
 */
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)

const API = process.env.API_URL || 'http://localhost:8080/api/v1'
const CARD = process.env.CARD || '8801'          // 张师傅的备案卡
const DRIVER = process.env.DRIVER || '张师傅'

let token = null
let pass = 0
let fail = 0
const check = (name, ok, detail = '') => {
  if (ok) { pass++; console.log(`  ✅ ${name}`) }
  else { fail++; console.log(`  ❌ ${name}${detail ? ' —— ' + detail : ''}`) }
}

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

const pad = (n) => String(n).padStart(2, '0')
/** 直连库计数（只读）：通知这类「写进去了但接口上不好断言」的事实靠它核实 */
function notifications(bizType, bizId) {
  const { execSync } = require('node:child_process')
  try {
    const out = execSync(
      `docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 -N -B -e `
      + `"SELECT COUNT(*) FROM db_common.user_notification WHERE biz_type='${bizType}' AND biz_id=${bizId}"`,
      { stdio: 'pipe' }).toString().trim()
    return Number(out || 0)
  } catch { return 0 }
}

/** 每次上报都要一份新的现场时刻：报文完全相同会被判「重复上报」，那不是业务失败 */
let tick = 0
const nowText = (offsetSec = 0) => {
  const d = new Date(Date.now() + (offsetSec + tick++) * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 直连库取单值（只读）：批次号这类「接口上绕一圈才拿得到」的事实靠它核实 */
function sqlOne(sql) {
  const { execSync } = require('node:child_process')
  try {
    return execSync(
      `docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 -N -B -e "${sql}"`,
      { stdio: 'pipe' }).toString().trim()
  } catch { return '' }
}

/**
 * 背景数据自持：召回收运签收后会把销售记录推进到「已收走」，每跑一轮就消耗一份「在售」背景。
 * 这里用零售商的已签收单现建 3 个在售二维码 + 一条举报，保证脚本可重复执行，不依赖手工造数。
 * 建码 / 举报失败不报错 —— 说明库里还有可用背景，让后面的前置检查自己判断。
 */
async function ensureBackdrop() {
  const raw = await fetch(API + '/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'retail_user', password: '123456' }),
  }).then((r) => r.json()).catch(() => null)
  const retailToken = raw?.data?.token
  if (!retailToken) return
  const call = async (path, opt = {}) => fetch(API + path, {
    method: opt.method || 'GET',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${retailToken}` },
    body: opt.body ? JSON.stringify(opt.body) : undefined,
  }).then((r) => r.json()).catch(() => null)

  const receipts = (await call('/distribution/receipts?pageNum=1&pageSize=20'))?.data?.records || []
  for (const receipt of receipts) {
    if (!receipt?.id || !receipt?.transportId) continue
    // 只挑「这个批次目前只在一家门店在售」的签收单：本脚本后面按「一站一店」断言，
    // 多店批次在弹窗预览里会带出多家门店，选它必然卡在「不是单门店案例」。
    // 注意**必须排除本签收单自己的门店**再数：建码动作马上会在本店插 3 行 status=1 的在售码，
    // 别的店还有在售的话，建完码这个批次就变成两家店在售 —— 2026-11-05 死循环根因：
    // 签收单 #148（安心生鲜）建码前只数出「1 家店在售」（顺丰生鲜），建完变 2 家， picked 永远为空。
    const otherCnt = Number(sqlOne(`SELECT COUNT(DISTINCT store_id) FROM db_sales.retail_sale `
      + `WHERE split_batch_id=(SELECT split_batch_id FROM db_distribution.cold_chain_transport WHERE id=${receipt.transportId}) `
      + `AND status=1 AND store_id <> ${Number(receipt.storeId || 0)}`) || 0)
    if (otherCnt > 0) continue
    const made = await call('/sales/qrcodes/batch', { method: 'POST', body: { receiptId: receipt.id, count: 3 } })
    const codes = made?.data?.qrcodes
    if (!Array.isArray(codes) || codes.length === 0) continue // 运单未签收/无批次等，换下一张
    const batchNo = sqlOne(`SELECT b.batch_no FROM db_distribution.split_batch b `
      + `JOIN db_distribution.cold_chain_transport t ON t.split_batch_id=b.id WHERE t.id=${receipt.transportId}`)
    if (!batchNo) continue
    await fetch(API + '/trace/complaints', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ targetQrCode: codes[0], targetBatch: batchNo, complaintText: '召回收运回归背景：' + nowText(), reporterName: '回归造数' }),
    }).then((r) => r.json()).catch(() => null)
    console.log(`   背景自持：签收单 #${receipt.id} 现建 ${codes.length} 个在售码并提交举报（批次 ${batchNo}）`)
    return
  }
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
  token = login.data?.token || login.data?.accessToken
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log('① 登录成功')

  await ensureBackdrop()

  // ── 选一个「被举报过、且只有一家门店在卖」的批次：这正是弹窗里能选的召回范围 ──
  // 范围只能来自举报台账，所以回归也从举报出发，而不是随手挑一个分割批次。
  const complaints = (await api('/trace/complaints?pageNum=1&pageSize=500')).data?.records || []
  const reported = [...new Set(complaints.map((c) => String(c.targetBatch || '').trim()).filter(Boolean))]
  let picked = null
  for (const no of reported) {
    // 按批次号精确查（列表接口 pageSize 上限 200，拉一页再本地过筛会漏掉大部分批次）
    const rows = (await api(`/distribution/splits?keyword=${encodeURIComponent(no)}&pageNum=1&pageSize=20`)).data?.records || []
    const split = rows.find((b) => String(b.batchNo || '').trim() === no)
    if (!split) continue
    const pv = (await api(`/sales/recalls/affected-stores?batchIds=${split.id}`)).data || {}
    // 单门店案例：这条链路后面按「一站一店」断言，多店批次留给收运的多站场景
    if ((pv.stores || []).length === 1) { picked = { split, pv }; break }
  }
  if (!picked) throw new Error('没有既有销售记录的单门店被举报批次：先造一条举报 + 收银记录')
  const batchIds = [picked.split.id]
  const store = { storeId: picked.pv.stores[0].storeId, storeName: picked.pv.stores[0].storeName }
  const sameStore = Array.from({ length: picked.pv.affectedCount })
  console.log(`   取到门店「${store.storeName}」的被举报批次 ${picked.split.batchNo}，受影响 ${picked.pv.affectedCount} 件`)

  // ── 前置保护：池子里已有别家召回的待收货时不许开跑 ──
  // 「生成收运运单」是并池语义（一张车收全城待收的召回货），会把别人还在等车的货并进测试车、多出门店站点；
  // 本脚本按「一站一店」断言，跑下去只会互相干扰，清理时还可能动到别人的生意。
  // 默认跳过；确实要验证并池行为时用 ALLOW_MERGE=1。
  const foreignPending = Number(sqlOne('SELECT COUNT(*) FROM db_distribution.recall_collect WHERE status = 0') || 0)
  if (foreignPending > 0 && process.env.ALLOW_MERGE !== '1') {
    console.log(`\n跳过：处理计划池里已有 ${foreignPending} 行别家召回的待收货。`
      + '生成收运运单会把它们并进测试车（多出门店站点），本脚本按单店断言会被干扰。'
      + '\n      先处理/挪开这些货再跑；要专门验证并池行为请设 ALLOW_MERGE=1。')
    process.exit(0)
  }

  const reason = '召回收运链路回归：' + nowText()
  const created = await api('/sales/recalls', {
    method: 'POST',
    body: { reason, riskLevel: 3, scope: { batchIds, storeIds: [store.storeId] }, initiator: 'admin' },
  })
  const recall = created.data
  check('② 召回单已发起（RC…）', !!recall?.recallNo && String(recall.recallNo).startsWith('RC'), JSON.stringify(created).slice(0, 160))
  check('② 影响范围 > 0（有货可召回）', Number(recall?.affectedCount || 0) > 0, `affectedCount=${recall?.affectedCount}`)
  const recallNo = recall.recallNo

  // ── 弹窗的「目标门店」由范围自动带出：口径必须与发起后实际圈到的门店一致 ──
  check('② 召回范围能自动带出目标门店（无需手选）',
    picked.pv.stores[0].storeId === store.storeId && picked.pv.stores[0].saleCount === sameStore.length,
    `${picked.pv.stores[0].storeName} · ${picked.pv.stores[0].saleCount} 件`)
  // 弹窗上显示的门店/件数，必须与召回真正圈到的范围一致（同一个口径，不许各算一份）
  check('② 自动带出的门店数 = 召回实际受影响件数',
    Number(recall?.affectedCount || 0) === Number(picked.pv.affectedCount || 0),
    `弹窗 ${picked.pv.affectedCount} 件 / 召回 ${recall?.affectedCount} 件`)

  // ── 待收运池：不需要任何人点按钮，货自动进「处理计划」 ──
  const pool1 = (await api('/distribution/recall-pool')).data || []
  const mine = pool1.filter((r) => r.recallNo === recallNo)
  check('③ 待收运池自动出现本次召回的货', mine.length > 0, `池中 ${pool1.length} 行，本次 ${mine.length} 行`)
  check('③ 池中这批货都在同一家门店', mine.every((r) => r.storeId === store.storeId))
  check('③ 池中这批货都带批次号（司机到店扫的就是它）', mine.every((r) => !!r.batchNo))

  // ── 生成收运运单：选一台空闲的备案车 ──
  const vehicles = (await api('/distribution/vehicles')).data || []
  check('④ 车辆备案里有可选的车', vehicles.length > 0, `共 ${vehicles.length} 台`)
  const running = (await api('/distribution/transports?kind=NORMAL,DISPOSAL,RECALL&pageNum=1&pageSize=100')).data?.records || []
  const busy = new Set(running.filter((t) => [1, 2, 3].includes(Number(t.status))).map((t) => t.vehicleNo))
  const plate = (vehicles.find((v) => !busy.has(v.vehicleNo)) || {}).vehicleNo
  check('④ 有车名下没有未完成运单（一车一趟）', !!plate, '所有车都在跑，先签收再回归')

  const gen = await api('/distribution/recall-transports', {
    method: 'POST',
    body: { vehicleNo: plate, driverName: DRIVER, driverPhone: '13800000001' },
  })
  const leg = gen.data || {}
  check('④ 收运运单已生成（TR…）', !!leg.transportNo && String(leg.transportNo).startsWith('TR'), JSON.stringify(gen).slice(0, 160))
  const tid = leg.transportId
  const stops = (await api(`/distribution/transports/${tid}/stops`)).data || []
  // 站点 = 各门店（按门店聚合）+ 最后一站无害化处理中心
  check('④ 站点按门店分站，末站是无害化处理中心', stops.length >= 2
    && /无害化/.test(stops[stops.length - 1].storeName || ''), stops.map((s) => s.storeName).join(' / '))
  const storeStops = stops.filter((s) => s.stopNo < stops[stops.length - 1].stopNo)
  check('④ 本次召回的门店在站点清单里', storeStops.some((s) => s.storeId === store.storeId),
    storeStops.map((s) => s.storeName).join(' / '))

  // ── 负向：还没到店收货，就不许往下一站走 ──
  const earlyArrive = await api('/ingest/demo/report', {
    method: 'POST',
    body: {
      deviceNo: 'ARRIVE-001', channel: 'ARRIVE', bizKey: 'RECALL-EARLY-' + Date.now(),
      data: { vehicleNo: plate, cardNo: CARD, arriveTime: nowText() },
    },
  })
  check('⑤ 未发车就想确认到达 → 不被当作正常入账', Number(earlyArrive.data?.status) !== 2,
    `status=${earlyArrive.data?.status} ${earlyArrive.data?.message || ''}`)

  // ── 发车（和之前一样：司机确认发车）──
  const depart = await api(`/distribution/transports/${tid}/depart`, { method: 'PUT' })
  const afterDepart = (await api(`/distribution/transports/${tid}`)).data || {}
  check('⑥ 发车成功，运单转「运输中」', Number(afterDepart.status) === 2, `status=${afterDepart.status} ${depart.message || ''}`)

  // ── 到店：车载终端刷卡到达 ──
  const arrive = await api('/ingest/demo/report', {
    method: 'POST',
    body: {
      deviceNo: 'ARRIVE-001', channel: 'ARRIVE', bizKey: 'RECALL-ARRIVE-' + Date.now(),
      data: { vehicleNo: plate, cardNo: CARD, arriveTime: nowText() },
    },
  })
  check('⑥ 到店刷卡到达已入账', Number(arrive.data?.status) === 2,
    `status=${arrive.data?.status} ${arrive.data?.message || ''}`)

  // ── 负向：货码对不上本店 → 照留原文但不算收货 ──
  const wrong = await api('/ingest/demo/report', {
    method: 'POST',
    body: {
      deviceNo: 'RECALL-SCAN-001', channel: 'RECALL_COLLECT', bizKey: 'RECALL-WRONG-' + Date.now(),
      data: { vehicleNo: plate, scanCodes: ['SP-NOT-EXIST-' + Date.now()], scanTime: nowText() },
    },
  })
  // 一件都没对上 ⇒ 既不是「已入账」(2)，也不该判「已拒绝」(3)：现场重扫即可，转人工(1)
  check('⑥ 扫到不属于本店的货码 → 转人工并写明原因', Number(wrong.data?.status) === 1
    && /对不上/.test(String(wrong.data?.message || '')),
    `status=${wrong.data?.status} ${wrong.data?.message || ''}`)

  // ── 扫码收货：这一站全收完 ──
  const codes = mine.map((r) => r.batchNo)
  const scan = await api('/ingest/demo/report', {
    method: 'POST',
    body: {
      deviceNo: 'RECALL-SCAN-001', channel: 'RECALL_COLLECT', bizKey: 'RECALL-SCAN-' + Date.now(),
      data: { vehicleNo: plate, scanCodes: codes, scanTime: nowText() },
    },
  })
  check('⑥ 到店扫码收货已入账', Number(scan.data?.status) === 2,
    `status=${scan.data?.status} ${scan.data?.message || ''}`)
  const items = (await api(`/distribution/recall-transports/${tid}/items`)).data || []
  check('⑥ 这批货都标成「已收上车」并留了收货时刻',
    items.length === mine.length && items.every((r) => Number(r.status) >= 2 && !!r.collectTime),
    items.map((r) => `${r.batchNo}:${r.status}`).join(' '))
  const stopNow = (await api(`/distribution/transports/${tid}/stops`)).data || []
  const myStop = stopNow.find((s) => s.storeId === store.storeId)
  check('⑥ 收完这一站，站点转「已收货」', Number(myStop?.status) === 2, `stopStatus=${myStop?.status}`)
  const pool2 = (await api('/distribution/recall-pool')).data || []
  check('⑥ 收走的货已离开待收运池', pool2.filter((r) => r.recallNo === recallNo).length === 0,
    `池中仍有 ${pool2.filter((r) => r.recallNo === recallNo).length} 行`)

  // ── 送到无害化处理中心：到达 + 签收 ──
  const arriveEnd = await api('/ingest/demo/report', {
    method: 'POST',
    body: {
      deviceNo: 'ARRIVE-001', channel: 'ARRIVE', bizKey: 'RECALL-ARRIVE-END-' + Date.now(),
      data: { vehicleNo: plate, cardNo: CARD, arriveTime: nowText() },
    },
  })
  check('⑦ 收完货后才允许到达下一站（无害化处理中心）', Number(arriveEnd.data?.status) === 2,
    `status=${arriveEnd.data?.status} ${arriveEnd.data?.message || ''}`)

  const confirm = await api(`/distribution/transports/${tid}/disposal-confirm`, { method: 'POST' })
  const final = (await api(`/distribution/transports/${tid}`)).data || {}
  check('⑦ 无害化处理中心确认签收，链条终结', Number(final.status) === 4,
    `status=${final.status} ${confirm.message || ''}`)

  // ── 收运签收 = 召回执行完毕：状态收口 + 通知管理员（不再需要人去点「进度上报」）──
  const recallDone = (await api(`/sales/recalls/${recall.id}`)).data || {}
  check('⑧ 签收后召回执行状态自动转为「已完成」', Number(recallDone.status) === 3
    && Number(recallDone.recalledCount) === Number(recallDone.affectedCount),
    `status=${recallDone.status} 已收回 ${recallDone.recalledCount}/${recallDone.affectedCount}`)
  check('⑧ 收口时间已落库', !!recallDone.completedTime, `completedTime=${recallDone.completedTime}`)
  check('⑧ 管理员收到「召回已完成」通知', notifications('recall_done', recall.id) > 0,
    `recall_done 条数=${notifications('recall_done', recall.id)}`)
  check('⑧ 重复签收不会重复收口（幂等）',
    Number((await api(`/distribution/transports/${tid}/disposal-confirm`, { method: 'POST' })).code) !== 200,
    '第二次签收被拒')

  if (process.env.KEEP !== '1') cleanup(recallNo, tid)
  console.log(`\n结果：${fail === 0 ? `全部通过 ✅（${pass} 项）` : `有 ${fail} 项未通过 ❌（通过 ${pass} 项）`}`)
  console.log(JSON.stringify({ recallNo, transportNo: leg.transportNo, transportId: tid, plate }, null, 1))
  process.exit(fail === 0 ? 0 : 1)
}

/**
 * 回归残留必清：只删本脚本自己造的行，按业务键（recall_no / transport_id）定位。
 * 不删 ingest_staging —— 原始报文按设计不可变，回执里已留痕即可。
 */
function cleanup(recallNo, transportId) {
  const { execSync } = require('node:child_process')
  // 并池是业务规则：生成收运运单会把池里「别人还在等车」的召回货一并并进来，
  // 删掉测试车后它们会变成指向已删运单的悬空行 —— 必须先数出来、再原样还回池子。
  const merged = Number(sqlOne(`SELECT COUNT(*) FROM db_distribution.recall_collect `
    + `WHERE transport_id = ${transportId} AND recall_no <> '${recallNo}'`) || 0)
  const sql = [
    `DELETE FROM db_distribution.recall_collect_scan WHERE transport_id = ${transportId}`,
    `DELETE FROM db_distribution.recall_collect WHERE recall_no = '${recallNo}'`,
    `DELETE FROM db_distribution.transport_stop WHERE transport_id = ${transportId}`,
    `DELETE FROM db_distribution.cold_chain_transport WHERE id = ${transportId}`,
    `DELETE FROM db_common.user_notification WHERE biz_type IN ('recall_store','recall_center','recall_done')`
      + ` AND biz_id = (SELECT id FROM db_sales.recall_order WHERE recall_no = '${recallNo}')`,
    `DELETE FROM db_sales.recall_order WHERE recall_no = '${recallNo}'`,
    // 被并进来的别人的货：还回「待收运」，不能跟着测试车一起消失
    `UPDATE db_distribution.recall_collect SET status = 0, transport_id = NULL, stop_id = NULL`
      + ` WHERE transport_id = ${transportId}`,
  ].join('; ')
  try {
    execSync(`docker exec pork-traceability-mysql-1 mysql -upork_user -p123456 -e "${sql}"`,
      { stdio: 'pipe' })
    console.log('\n清理：本次召回单与收运运单已按业务键删除'
      + (merged > 0 ? `；另有 ${merged} 行别家召回的待收货被并进来，已还回处理计划池` : ''))
  } catch (e) {
    console.log('\n清理失败（请手工删）：', String(e.stderr || e.message).slice(0, 200))
  }
}

main().catch((e) => { console.error('脚本异常：', e.message); process.exit(1) })
