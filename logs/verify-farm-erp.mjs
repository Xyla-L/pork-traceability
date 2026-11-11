#!/usr/bin/env node
/**
 * 养殖系统同步（FARM_ERP）通道回归 —— 18 项断言，覆盖「接过来」与「不信它」两侧。
 *
 * 覆盖面：
 *   A 正常同步：建档 + 成员展开 + 字段落对（批次号/品种/进场/出生/来源/外部编号）
 *   B 判重两道：同编号重推、换编号同报文重推
 *   C 增量更新：扩区间 → 成员补齐、数量与耳标范围按成员重算
 *   D 四条转人工红线：耳标被占、场名对不上、批次号撞车（由 D1 的耳标占用代表）、无成员
 *   E 结构性拒绝：缺成员、撤销无理由
 *   F 源撤销：作废 + 成员一并作废 + 上链 + 列表消失 + 不自动复活 + 重复撤销幂等
 *   G 人工复查红线：待人工放行必须写理由（实名由网关注入，本地无法伪造，故只验理由）
 *
 * 说明：本环境 node 禁止 child_process，列级事实（void_source 等）由
 * logs/verify-farm-erp-db.sh 用 docker exec mysql 核验；本脚本只走 API。
 *
 * 运行：node logs/verify-farm-erp.mjs
 * 清理：bash logs/verify-farm-erp-db.sh --clean
 */

const BASE = process.env.BASE_URL || 'http://localhost/api/v1'
const ADMIN = { username: 'admin', password: '123456' }
const DEVICE_KEY = process.env.DEVICE_KEY || 'farm-erp-demo-key-2026'
/** 回归专用场（宏图养殖公司）：演示场留给演示台账，回归不借演示数据 */
const FARM = process.env.FARM_NAME || '宏图养殖公司'

let token = null
let pass = 0
let fail = 0

function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`  [PASS] ${name}`)
  } else {
    fail++
    console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`)
  }
}

async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return res.json().catch(() => ({}))
}

/** 设备通道上报：这是外部系统唯一该走的路（不带 JWT，只有设备密钥） */
async function push(data, bizKey) {
  const res = await fetch(`${BASE}/ingest/device/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Device-Key': DEVICE_KEY },
    body: JSON.stringify({
      channel: 'FARM_ERP',
      bizKey,
      reportTime: nowText(),
      sourceRef: 'VERIFY-FARM-ERP',
      data,
    }),
  })
  const json = await res.json().catch(() => ({}))
  return json.data || { error: json.message || 'HTTP ' + res.status }
}

function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} `
    + `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function dayText(offset) {
  const d = new Date(Date.now() + offset * 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 按批次号查档案（列表接口；已作废批次会被逻辑删除过滤掉，正好用来验「撤销后消失」） */
async function findBatch(batchNo) {
  const res = await api(`/breeding/pigs/batches?batchNo=${encodeURIComponent(batchNo)}&current=1&size=10`)
  const records = res.data?.records || []
  return records.find((r) => r.batchNo === batchNo) || null
}

async function findPig(earTagNo) {
  const res = await api(`/breeding/pigs?earTagNo=${encodeURIComponent(earTagNo)}&current=1&size=10`)
  const records = res.data?.records || []
  return records.find((r) => r.earTagNo === earTagNo) || null
}

;(async () => {
  const RUN = Date.now().toString().slice(-8)
  const batchNo = `ERP-VFY-${RUN}`
  const tagStart = `ET-VFY${RUN}001`
  const tagEnd = `ET-VFY${RUN}030`
  const base = {
    action: 'UPSERT',
    systemCode: 'DEMO_ERP',
    farmName: FARM,
    batchNo,
    breed: '三元杂',
    entryDate: dayText(-20),
    birthDate: dayText(-90),
    source: '自繁',
    earTagStart: tagStart,
    earTagEnd: tagEnd,
  }

  const login = await api('/auth/login', { method: 'POST', body: ADMIN })
  token = login.data?.accessToken || login.data?.token || login.data?.access_token
  if (!token) throw new Error('登录失败：' + JSON.stringify(login).slice(0, 200))
  console.log(`\n══ 养殖系统同步回归（本轮批次 ${batchNo}）══════════════════`)

  // ---------------------------------------------------------------- A 正常同步
  console.log('\nA 正常同步')
  const created = await push(base, `${batchNo}-1`)
  check('A1 推送入账，写入目标是批次表', created.accepted === true && created.targetTable === 'pig_batch',
    JSON.stringify(created).slice(0, 160))
  const batchId = created.targetId

  const batch = await findBatch(batchNo)
  check('A2 批次出现在生猪档案列表', !!batch)
  check('A3 品种/进场/出生日期落对',
    !!batch && batch.breed === '三元杂' && batch.entryDate === base.entryDate && batch.birthDate === base.birthDate,
    JSON.stringify(batch && { breed: batch.breed, entryDate: batch.entryDate, birthDate: batch.birthDate }))
  check('A4 数据来源标「外部同步」（API），外部编号留档',
    !!batch && batch.dataSource === 'API' && batch.externalId === batchNo && batch.externalSystem === 'DEMO_ERP',
    JSON.stringify(batch && { ds: batch.dataSource, ext: batch.externalId, sys: batch.externalSystem }))
  check('A5 成员按区间展开 30 头', !!batch && batch.quantity === 30, String(batch && batch.quantity))
  check('A6 耳标起止与推送一致',
    !!batch && batch.earTagStart === tagStart && batch.earTagEnd === tagEnd)
  const member = await findPig(tagStart)
  check('A7 成员行继承批次出生日期', !!member && member.birthDate === base.birthDate,
    JSON.stringify(member && { birth: member.birthDate }))

  // ---------------------------------------------------------------- B 判重两道
  console.log('\nB 判重两道')
  const dup1 = await push(base, `${batchNo}-1`)
  check('B1 同编号重推 → 重复上报，不新建流水',
    dup1.duplicate === true && dup1.status === 5 && dup1.accepted === false,
    JSON.stringify(dup1).slice(0, 140))
  const dup2 = await push(base, `${batchNo}-1b`)
  check('B2 换编号、同报文重推 → 兜底判重拦下',
    dup2.duplicate === true && dup2.status === 5, JSON.stringify(dup2).slice(0, 140))

  // ---------------------------------------------------------------- C 增量更新
  console.log('\nC 增量更新（扩区间）')
  const grown = await push({ ...base, earTagEnd: `ET-VFY${RUN}032` }, `${batchNo}-2`)
  check('C1 同外部批次再推 → 更新而非新建', grown.accepted === true && grown.targetId === batchId,
    JSON.stringify(grown).slice(0, 140))
  const batch2 = await findBatch(batchNo)
  check('C2 成员补到 32 头', !!batch2 && batch2.quantity === 32, String(batch2 && batch2.quantity))
  check('C3 耳标止号按成员重算', !!batch2 && batch2.earTagEnd === `ET-VFY${RUN}032`,
    batch2 && batch2.earTagEnd)

  // ---------------------------------------------------------------- D 转人工红线
  console.log('\nD 转人工红线（绝不覆盖）')
  const conflict = await push({
    ...base,
    batchNo: `${batchNo}-B`,
    earTagStart: `ET-VFY${RUN}010`,
    earTagEnd: `ET-VFY${RUN}015`,
  }, `${batchNo}-3`)
  check('D1 耳标已被别的批次占用 → 待人工处理',
    conflict.status === 1 && /已被其他批次/.test(conflict.message || ''), conflict.message)

  const noFarm = await push({ ...base, farmName: '回归-不存在的养殖场', batchNo: `${batchNo}-C` }, `${batchNo}-4`)
  check('D2 场名对不上 → 待人工处理（不猜场）',
    noFarm.status === 1 && /没有登记/.test(noFarm.message || ''), noFarm.message)

  // 撞号要用「本回归场自己的人工批次」：别处的批次撞不到同一个 uk_batch(farm_id,batch_no)
  const farms = await api('/breeding/farms?current=1&size=500')
  const farmRow = (farms.data?.records || farms.data?.list || []).find((f) => (f.farmName || f.name) === FARM)
  const manualList = farmRow
    ? await api(`/breeding/pigs/batches?farmId=${farmRow.id}&current=1&size=100`)
    : { data: { records: [] } }
  const someManual = (manualList.data?.records || []).find((r) => r.dataSource === 'MANUAL')
  if (someManual) {
    const hit = await push({
      ...base,
      batchNo: someManual.batchNo,
      earTagStart: `ET-VFY${RUN}901`,
      earTagEnd: `ET-VFY${RUN}902`,
    }, `${batchNo}-5`)
    check('D3 同场批次号与人工登记撞车 → 待人工处理',
      hit.status === 1 && /同名批次号/.test(hit.message || ''), hit.message)
  } else {
    check('D3 同场批次号与人工登记撞车 → 待人工处理', false,
      `回归场「${FARM}」没有人工登记的批次可撞（需要至少一条人工批次才能覆盖此红线）`)
  }

  // ---------------------------------------------------------------- E 结构性拒绝
  console.log('\nE 结构性拒绝（数据本身不成立）')
  const noMember = await push({
    action: 'UPSERT', systemCode: 'DEMO_ERP', farmName: FARM, batchNo: `${batchNo}-D`,
  }, `${batchNo}-6`)
  check('E1 既无区间也无清单 → 已拒绝',
    noMember.status === 3 && /耳标/.test(noMember.message || ''), noMember.message)

  const revokeNoReason = await push({
    action: 'REVOKE', systemCode: 'DEMO_ERP', farmName: FARM, batchNo,
  }, `${batchNo}-7`)
  check('E2 撤销不写理由 → 已拒绝',
    revokeNoReason.status === 3 && /理由/.test(revokeNoReason.message || ''), revokeNoReason.message)

  // ---------------------------------------------------------------- G 人工复查红线
  console.log('\nG 人工复查红线')
  const stagingManual = await api('/ingest/staging?channel=FARM_ERP&status=1&pageNum=1&pageSize=5')
  const manualRow = (stagingManual.data?.records || [])[0]
  if (manualRow) {
    const approveNoRemark = await api(`/ingest/staging/${manualRow.id}/approve`, {
      method: 'POST', body: {},
    })
    check('G1 待人工放行不写理由 → 被拦',
      String(approveNoRemark.code) !== '200' && /放行理由/.test(approveNoRemark.message || ''),
      JSON.stringify(approveNoRemark).slice(0, 160))
  } else {
    check('G1 待人工放行不写理由 → 被拦', false, '队列里没有待人工的 FARM_ERP 记录')
  }

  // ---------------------------------------------------------------- F 源撤销
  console.log('\nF 源撤销')
  const revoked = await push({
    action: 'REVOKE', systemCode: 'DEMO_ERP', farmName: FARM, batchNo,
    voidReason: '回归：ERP 侧录错，整批撤销',
  }, `${batchNo}-8`)
  check('F1 撤销入账', revoked.accepted === true, JSON.stringify(revoked).slice(0, 140))
  check('F2 撤销后档案列表查不到该批次', (await findBatch(batchNo)) === null)
  check('F3 撤销后成员一并作废（耳标查不到）', (await findPig(tagStart)) === null)

  const chain = await api(`/blockchain/records?bizType=PIG_BATCH_VOID&bizId=${batchId}&pageNum=1&pageSize=5`)
  const chainRows = chain.data?.records || []
  check('F4 撤销已上链且事件标记为源撤销（:REVOKE）',
    chainRows.some((r) => String(r.bizKey || '').endsWith(':REVOKE')),
    JSON.stringify(chainRows.map((r) => r.bizKey)))

  const reapply = await push({ ...base, entryDate: dayText(-17) }, `${batchNo}-9`)
  check('F5 撤销后再推（内容已改）→ 待人工处理，不自动复活',
    reapply.status === 1 && /已被撤销|撤销/.test(reapply.message || ''), reapply.message)

  const revokeAgain = await push({
    action: 'REVOKE', systemCode: 'DEMO_ERP', farmName: FARM, batchNo,
    voidReason: '回归：重复撤销',
  }, `${batchNo}-10`)
  // 重复撤销按幂等成功处理：回执口径里 accepted=true 就是「这条已经在系统里，别再推了」，
  // 具体说明（幂等返回）走服务端日志，不占回执文案 —— 回执文案由接入层统一成「已入账」
  check('F6 重复撤销 → 幂等成功（同一批次 id，未新建）',
    revokeAgain.accepted === true && revokeAgain.targetId === batchId,
    JSON.stringify(revokeAgain).slice(0, 140))

  // ---------------------------------------------------------------- 通道展示
  console.log('\nH 通道展示')
  const staging = await api('/ingest/staging?channel=FARM_ERP&pageNum=1&pageSize=5')
  const row = (staging.data?.records || [])[0]
  check('H1 通道中文名不漏英文枚举',
    !!row && row.channelLabel === '养殖系统同步（外部ERP）', row && row.channelLabel)
  check('H2 上报流水留存原始报文',
    !!row && row.payload && String(row.payload).includes('batchNo'), row && String(row.payload).slice(0, 60))

  console.log('\n──────────────────────────────────────────────────────────')
  console.log(`结果：通过 ${pass} / 失败 ${fail}`)
  console.log(`本轮批次：${batchNo}（已在 F 段撤销）`)
  console.log('列级核验 + 清理：bash logs/verify-farm-erp-db.sh --clean')
  process.exit(fail ? 1 : 0)
})().catch((e) => {
  console.error('\n回归异常：', e)
  process.exit(1)
})
