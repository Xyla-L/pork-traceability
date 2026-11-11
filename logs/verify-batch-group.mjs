/**
 * 分组视图回归：免疫 / 产地检疫 / 出栏申报「按批次一行」的口径。
 *
 * 覆盖：
 *  A 同一批同一天同一种疫苗 → 聚成 1 组，头数/耳标区间正确
 *  B 同一批换一种疫苗 → 是另外 1 组，两次免疫不能被并成一次
 *  C 组内作废 2 条 → voidCount 与状态摘要同步，明细仍然逐头在
 *  D 同一批同一天同机构出证 30 张 → 1 组，标题标「等 N 张」
 *  E 同一批同一天申报 30 头 → 1 组；审掉 5 条 → 状态摘要是 5/25
 *  F 未归批的猪不能被凭空并成一批：两头无批次猪同疫苗同日 → 必须是 2 组
 *  G 分页按「组」算：size=1 翻两页拿到两组不同 groupKey，total 不变
 *
 * 数据自持：自己建场 + 建 30 头批次，跑完按耳标前缀精确清理（不碰演示台账）。
 * 需要直接改库的两处（造未归批、清场）走 docker exec，其余全部 HTTP。
 */
import { execSync } from 'node:child_process'

const BASE = process.env.BASE_URL || 'http://localhost/api/v1'
const MYSQL = process.env.MYSQL_CT || 'pork-traceability-mysql-1'
const DB_USER = process.env.DB_USER || 'pork_user'
const DB_PASS = process.env.DB_PASS || '123456'
const ADMIN = { username: 'admin', password: '123456' }
const INJ_KEY = 'dev-key-inj-001'

const RUN = String(Date.now()).slice(-6)
const FARM_NAME = `分组回归场${RUN}`
const BATCH_NO = `PB-GRP${RUN}`
const TAG_PREFIX = `ET-GRP${RUN}`
const VACCINE_A = `口蹄疫疫苗${RUN}`
const VACCINE_B = `猪瘟疫苗${RUN}`
const VAC_BATCH = `VACB${RUN}`
const CERT_ORG = `分组回归检疫站${RUN}`
const DAY = new Date().toISOString().slice(0, 10)

let token = null
let pass = 0
let fail = 0
const failures = []

function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`  ✅ ${name}`)
  } else {
    fail++
    failures.push(name)
    console.log(`  ❌ ${name}${detail ? ' — ' + detail : ''}`)
  }
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${BASE}${path}`, { ...options, headers })
  let body = null
  try { body = await res.json() } catch { /* 空响应体 */ }
  return { status: res.status, body, data: body?.data, code: body?.code, message: body?.msg || body?.message }
}

/** 设备通道上报（走完整接入管道，不是直写业务表） */
async function deviceReport(deviceKey, payload) {
  const res = await fetch(`${BASE}/ingest/device/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Device-Key': deviceKey },
    body: JSON.stringify(payload),
  })
  let body = null
  try { body = await res.json() } catch { /* ignore */ }
  return { data: body?.data, message: body?.msg || body?.message || body?.data?.message }
}

function sql(statement) {
  const escaped = statement.replace(/"/g, '\\"')
  // 末尾的 || true 不能省：grep -v Warning 在没有匹配行时返回 1，execSync 会把它当命令失败
  const cmd = `docker exec ${MYSQL} mysql -u${DB_USER} -p${DB_PASS} --default-character-set=utf8mb4 -N -e "${escaped}" 2>&1 | grep -v Warning || true`
  return execSync(cmd, { encoding: 'utf8' }).trim()
}

function sqlOne(statement) {
  const out = sql(statement)
  return out.split('\n')[0].trim()
}

const tagOf = (n) => `${TAG_PREFIX}${String(n).padStart(3, '0')}`

/** 1x1 合法 PNG：免疫上报必须带真实存在的凭证照片，编造的 fileId 会被接入层判拒绝 */
function pngFile() {
  return {
    name: `grp-${RUN}.png`,
    buffer: Buffer.from(
      '89504e470d0a1a0a0000000d494844520000000100000001080200000090775'
      + '3de0000000c4944415408d763f8cfc000000301010018dd8db00000000049454e44ae426082',
      'hex',
    ),
  }
}

async function uploadVoucher() {
  const fd = new FormData()
  const f = pngFile()
  fd.append('file', new Blob([f.buffer], { type: 'image/png' }), f.name)
  const res = await fetch(`${BASE}/file/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: fd,
  })
  const body = await res.json().catch(() => null)
  return body?.data?.fileId
}

function nowTime(offsetSec = 0) {
  const d = new Date(Date.now() + offsetSec * 1000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** paged 全量拉回（分组视图，size 给大） */
async function fetchGroups(kind) {
  const res = await api(`/breeding/records/${kind}?current=1&size=500`)
  return { total: res.data?.total || 0, records: res.data?.records || [] }
}

;(async () => {
  console.log(`\n── 分组视图回归（RUN=${RUN}）──\n`)

  // ---------------------------------------------------------------- 0 登录
  const login = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  token = login.data?.accessToken || login.data?.token
  if (!token) {
    console.error('登录失败：' + JSON.stringify(login.body).slice(0, 200))
    process.exit(1)
  }
  console.log('▶ 登录成功\n')

  // ---------------------------------------------------------------- 1 造场与批次
  console.log('▶ 1 建场 + 30 头批次')
  const farm = await api('/breeding/farms', {
    method: 'POST',
    body: JSON.stringify({
      farmName: FARM_NAME,
      licenseNo: `LIC-GRP${RUN}`,
      scale: 200,
      address: '分组回归用',
      contactPerson: '回归',
      contactPhone: '13000000000',
    }),
  })
  // 建场接口只回执成功、不回 id，按场名回查一次（不猜、不复用别人的场）
  let farmId = farm.data?.id || farm.data
  if (!farmId) {
    const farms = await api('/breeding/farms?current=1&size=500')
    farmId = (farms.data?.records || []).find((f) => f.farmName === FARM_NAME)?.id
  }
  check('V1 建场成功', !!farmId, JSON.stringify(farm.body).slice(0, 160))

  const batch = await api('/breeding/pigs/batches', {
    method: 'POST',
    body: JSON.stringify({
      batchNo: BATCH_NO,
      farmId: Number(farmId),
      breed: '三元杂',
      entryDate: DAY,
      earTagStart: tagOf(1),
      earTagEnd: tagOf(30),
      source: '自繁',
    }),
  })
  const batchId = batch.data?.id || batch.data?.batchId || (typeof batch.data === 'number' ? batch.data : null)
  check('V2 批次登记成功（30 头）', !!batchId, JSON.stringify(batch.body).slice(0, 200))

  const pigs = await api(`/breeding/pigs?current=1&size=50&farmId=${farmId}`)
  const members = (pigs.data?.records || []).filter((p) => (p.earTagNo || '').startsWith(TAG_PREFIX))
  check('V3 耳标区间展开出 30 个成员', members.length === 30, `实际 ${members.length}`)
  if (members.length === 0) {
    console.log('\n没有成员行，后续断言无意义，终止。')
    process.exit(1)
  }

  // ---------------------------------------------------------------- 2 疫苗：整批一次免疫
  console.log('\n▶ 2 疫苗：30 头同疫苗同日 = 一次免疫')
  const voucherId = await uploadVoucher()
  check('A0 接种凭证照片上传成功', !!voucherId, '未拿到 fileId')
  const vacReceipts = []
  for (const p of members) {
    vacReceipts.push(await deviceReport(INJ_KEY, {
      channel: 'VACCINE',
      bizKey: `GRP${RUN}-VAC-A-${p.earTagNo}`,
      reportTime: nowTime(0),
      sourceRef: '分组回归注射器',
      data: {
        earTagNo: p.earTagNo,
        vaccineName: VACCINE_A,
        vaccineBatchNo: VAC_BATCH,
        manufacturer: '回归生物',
        injectTime: `${DAY} 08:30:00`,
        dosage: '2ml',
        injectSite: '颈部肌肉',
        operator: '张兽医',
        // 免疫是设备独占环节，缺接种凭证照片会被判「待人工处理」而不是入账——
        // 这是业务红线（免疫记录必须有佐证材料），回归也要按真设备的报文带齐
        fileIds: [voucherId],
      },
    }))
  }
  // 先确认上报真的入了账：分组断言失败时，要能立刻分清是「聚合写错了」还是「数据没进来」
  const badVac = vacReceipts.filter((r) => !r.data || Number(r.data.status) !== 2)
  check('A1 30 条免疫上报全部入账', badVac.length === 0,
    badVac.length ? `${badVac.length} 条未入账，首条：${JSON.stringify(badVac[0]).slice(0, 240)}` : '')

  let vac = await fetchGroups('vaccines')
  let mine = vac.records.filter((g) => g.batchNo === BATCH_NO && g.title === VACCINE_A)
  check('A1 同批同日同疫苗聚成 1 组', mine.length === 1, `实际 ${mine.length} 组`)
  const groupA = mine[0]
  check('A2 组内头数 = 30', groupA?.headCount === 30, `实际 ${groupA?.headCount}`)
  check('A3 耳标区间取到起止',
    groupA?.tagStart === tagOf(1) && groupA?.tagEnd === tagOf(30),
    `${groupA?.tagStart} ~ ${groupA?.tagEnd}`)
  check('A4 明细逐头保留（聚合不丢单头）', groupA?.members?.length === 30, `实际 ${groupA?.members?.length}`)
  check('A5 明细带耳标号', groupA?.members?.every((m) => m.earTagNo && m.earTagNo.startsWith(TAG_PREFIX)))
  check('A6 状态摘要是「全部有效」', groupA?.statusSummary === '全部有效', groupA?.statusSummary)

  // ---------------------------------------------------------------- 3 换疫苗不并组
  console.log('\n▶ 3 同批换一种疫苗 = 另一次免疫')
  await deviceReport(INJ_KEY, {
    channel: 'VACCINE',
    bizKey: `GRP${RUN}-VAC-B`,
    reportTime: nowTime(1),
    sourceRef: '分组回归注射器',
    data: {
      earTagNo: members[0].earTagNo,
      vaccineName: VACCINE_B,
      vaccineBatchNo: VAC_BATCH,
      injectTime: `${DAY} 09:00:00`,
      dosage: '1ml',
      injectSite: '颈部肌肉',
      operator: '张兽医',
      fileIds: [voucherId],
    },
  })
  vac = await fetchGroups('vaccines')
  mine = vac.records.filter((g) => g.batchNo === BATCH_NO)
  check('B1 同批出现 2 个免疫组（不同疫苗不并）', mine.length === 2, `实际 ${mine.length} 组`)

  // ---------------------------------------------------------------- 4 作废两条
  console.log('\n▶ 4 组内作废 2 条 → 计数与摘要同步')
  const target = groupA.members.slice(0, 2)
  const voidedIds = target.map((m) => m.id)
  for (const m of target) {
    await api(`/breeding/pigs/${m.pigId}/vaccines/${m.id}/void`, {
      method: 'POST',
      body: JSON.stringify({ reason: '分组回归：作废计数验证' }),
    })
  }
  vac = await fetchGroups('vaccines')
  const groupA2 = vac.records.find((g) => g.batchNo === BATCH_NO && g.title === VACCINE_A)
  check('C1 voidCount = 2', groupA2?.voidCount === 2, `实际 ${groupA2?.voidCount}`)
  check('C2 状态摘要是结论「部分有效」',
    groupA2?.statusSummary === '部分有效', groupA2?.statusSummary)
  check('C2b 计数进明细「有效 28 / 已作废 2」',
    groupA2?.statusDetail === '有效 28 / 已作废 2', groupA2?.statusDetail)
  check('C3 作废后明细仍是 30 条（记录不消失）', groupA2?.members?.length === 30, `实际 ${groupA2?.members?.length}`)
  const voidedInDetail = (groupA2?.members || []).filter((m) => m.voidFlag === 1).length
  check('C4 明细里 2 条标记为已作废', voidedInDetail === 2, `实际 ${voidedInDetail}`)

  // 单头组整组作废：这里原来是「有效 0 / 已作废 1」，读起来像「这批还剩点东西」，
  // 实际是这一次免疫动作整体失效 —— 必须是「全部作废」，绝不能落到「部分有效」。
  const groupB = vac.records.find((g) => g.batchNo === BATCH_NO && g.title === VACCINE_B)
  const soleMember = groupB?.members?.[0]
  check('C5 换疫苗那组只有 1 头', !!soleMember, `members=${groupB?.members?.length}`)
  if (soleMember) {
    await api(`/breeding/pigs/${soleMember.pigId}/vaccines/${soleMember.id}/void`, {
      method: 'POST',
      body: JSON.stringify({ reason: '分组回归：整组作废文案验证' }),
    })
    voidedIds.push(soleMember.id)
  }
  const vac3 = await fetchGroups('vaccines')
  const groupB2 = vac3.records.find((g) => g.batchNo === BATCH_NO && g.title === VACCINE_B)
  check('C6 单头组作废后摘要是「全部作废」',
    groupB2?.statusSummary === '全部作废', groupB2?.statusSummary)
  check('C7 全作废时明细「已作废 1」', groupB2?.statusDetail === '已作废 1', groupB2?.statusDetail)
  check('C8 全作废不显示「有效 0」',
    !String(groupB2?.statusSummary || '').includes('有效 0')
    && !String(groupB2?.statusDetail || '').includes('有效 0'),
    `${groupB2?.statusSummary} / ${groupB2?.statusDetail}`)

  // ---------------------------------------------------------------- 5 检疫
  console.log('\n▶ 5 检疫：同批同日同机构出证 30 张 = 一次出证')
  const certResults = []

  // 先说清这一段的现实顺序：产地检疫要查验**有效**免疫记录。
  // 上面把 groupA 前两头的免疫作废了（作废=这次免疫不算了），这两头现在没有免疫档案，
  // 出证必须被当场拒绝 —— 这是本轮补上的硬前置，回归要盯住它，不能默默放行。
  // 注意别拿 soleMember 当样本：它只是 VACCINE_B 那条被作废，身上还留着有效的 VACCINE_A。
  const blockedPig = target[0]
  const solePigId = blockedPig?.pigId
  let blockedCert = null
  if (solePigId) {
    blockedCert = await api(`/breeding/pigs/${solePigId}/quarantine-cert`, {
      method: 'POST',
      body: JSON.stringify({
        pigId: solePigId,
        certNo: `QC-${RUN}-BLOCKED`,
        issueOrg: CERT_ORG,
        issueTime: `${DAY} 08:30:00`,
        validUntil: DAY,
        inspector: '李检疫',
        fileId: voucherId,
        caSignature: `CA-GRP-${RUN}-BLOCKED`,
      }),
    })
    check('F1 免疫记录已作废的猪，出证被拒绝',
      blockedCert?.code !== 200 && blockedCert?.code !== 0,
      `居然出证成功：${JSON.stringify(blockedCert?.data).slice(0, 160)}`)
    check('F2 拒绝原因写明「没有有效免疫记录」',
      /没有任何有效免疫记录/.test(blockedCert?.message || ''), blockedCert?.message)
  }

  // 现场做法是「作废录错了 → 重新接种并上报」，补上免疫再出证就该成功。
  // 时间必须早于出证时刻（08:30），否则会撞上「先出证后免疫」的时序判定。
  // 上一步作废的是 groupA 前两头，两头都要补 —— 只补一头，另一头出证时还会被拒。
  for (const m of target) {
    await deviceReport(INJ_KEY, {
      channel: 'VACCINE',
      bizKey: `GRP${RUN}-REVAC-${m.pigId}`,
      reportTime: nowTime(0),
      sourceRef: '分组回归注射器',
      data: {
        earTagNo: m.earTagNo,
        vaccineName: `猪瘟疫苗${RUN}`,
        vaccineBatchNo: `REVAC-${RUN}`,
        manufacturer: '回归生物',
        injectTime: `${DAY} 07:00:00`,
        dosage: '1ml',
        injectSite: '耳后皮下',
        operator: '张兽医',
        fileIds: [voucherId],
      },
    })
  }
  if (solePigId) {
    const retry = await api(`/breeding/pigs/${solePigId}/quarantine-cert`, {
      method: 'POST',
      body: JSON.stringify({
        pigId: solePigId,
        certNo: `QC-${RUN}-RETRY`,
        issueOrg: CERT_ORG,
        issueTime: `${DAY} 08:30:00`,
        validUntil: DAY,
        inspector: '李检疫',
        fileId: voucherId,
        caSignature: `CA-GRP-${RUN}-RETRY`,
      }),
    })
    check('F3 补上有效免疫后，出证放行', retry?.code === 200 || retry?.code === 0,
      JSON.stringify(retry?.message).slice(0, 200))
    certResults.push(retry)
  }

  for (const p of members) {
    if (p.id === solePigId) continue
    certResults.push(await api(`/breeding/pigs/${p.id}/quarantine-cert`, {
      method: 'POST',
      body: JSON.stringify({
        pigId: p.id,
        certNo: `QC-${RUN}-${p.earTagNo}`,
        issueOrg: CERT_ORG,
        issueTime: `${DAY} 08:30:00`,
        validUntil: DAY,
        inspector: '李检疫',
        fileId: voucherId,
        caSignature: `CA-GRP-${RUN}-${p.earTagNo}`,
      }),
    }))
  }
  const badCert = certResults.filter((r) => r.code !== 200 && r.code !== 0)
  check('D0 30 张检疫证出证成功', badCert.length === 0,
    badCert.length ? JSON.stringify(badCert[0]).slice(0, 240) : '')
  const certs = await fetchGroups('certs')
  const certGroups = certs.records.filter((g) => g.batchNo === BATCH_NO)
  check('D1 同批同日同机构聚成 1 组', certGroups.length === 1, `实际 ${certGroups.length} 组`)
  check('D2 组内头数 = 30', certGroups[0]?.headCount === 30, `实际 ${certGroups[0]?.headCount}`)
  check('D3 标题标出证张数',
    /等 30 张/.test(certGroups[0]?.title || ''), certGroups[0]?.title)
  check('D4 明细有 30 张证', certGroups[0]?.members?.length === 30, `实际 ${certGroups[0]?.members?.length}`)

  // ---------------------------------------------------------------- 6 申报 + 审批
  console.log('\n▶ 6 申报：同批同日申报 30 头 = 一次出栏申报')
  let weight = 100
  for (const p of members) {
    weight += 0.7
    await api(`/breeding/pigs/${p.id}/apply`, {
      method: 'POST',
      body: JSON.stringify({
        pigId: p.id,
        weightKg: Number(weight.toFixed(1)),
        targetSlaughterhouse: `分组回归屠宰场${RUN}`,
      }),
    })
  }
  let applies = await fetchGroups('applies')
  let applyGroups = applies.records.filter((g) => g.batchNo === BATCH_NO)
  check('E1 同批同日申报聚成 1 组', applyGroups.length === 1, `实际 ${applyGroups.length} 组`)
  check('E2 组内头数 = 30', applyGroups[0]?.headCount === 30, `实际 ${applyGroups[0]?.headCount}`)
  check('E3 状态摘要「全部待审」', applyGroups[0]?.statusSummary === '全部待审', applyGroups[0]?.statusSummary)
  check('E3b 计数在 statusDetail（待审 30）', (applyGroups[0]?.statusDetail || '').includes('30'), applyGroups[0]?.statusDetail)

  const toApprove = (applyGroups[0]?.members || []).slice(0, 5)
  for (const m of toApprove) {
    await api(`/breeding/applies/${m.id}/approve`, {
      method: 'PUT',
      body: JSON.stringify({ approved: true, comment: '分组回归：状态摘要验证' }),
    })
  }
  applies = await fetchGroups('applies')
  applyGroups = applies.records.filter((g) => g.batchNo === BATCH_NO)
  check('E4 审掉 5 条后摘要「待审中」（结论标签，计数进 tooltip）',
    applyGroups[0]?.statusSummary === '待审中'
      && (applyGroups[0]?.statusDetail || '').includes('已通过 5'),
    `${applyGroups[0]?.statusSummary} / ${applyGroups[0]?.statusDetail}`)

  // ---------------------------------------------------------------- 7 未归批不能被并
  console.log('\n▶ 7 未归批：两头无关的猪不能被并成一批')
  const orphans = members.slice(28, 30)
  const orphanIds = orphans.map((p) => p.id).join(',')
  sql(`USE db_breeding; UPDATE pig_individual SET batch_id = NULL WHERE id IN (${orphanIds});`)
  const stillNull = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE id IN (${orphanIds}) AND batch_id IS NULL;`)
  check('F1 已把 2 头改成无批次', String(stillNull) === '2', `实际 ${stillNull}`)

  const vacAfter = await fetchGroups('vaccines')
  const orphanGroups = vacAfter.records.filter((g) => g.batchId === null
    && (g.members || []).some((m) => orphans.some((o) => o.earTagNo === m.earTagNo)))
  check('F2 未归批的猪各自成组（不被并成 1 组）',
    orphanGroups.length >= orphans.length && orphanGroups.every((g) => g.headCount === 1),
    `实际 ${orphanGroups.length} 组，headCount=${orphanGroups.map((g) => g.headCount).join(',')}`)
  check('F3 未归批组的 batchNo 为空', orphanGroups.every((g) => !g.batchNo))

  // ---------------------------------------------------------------- 8 分页按组
  console.log('\n▶ 8 分页按「组」算')
  const p1 = await api('/breeding/records/vaccines?current=1&size=1')
  const p2 = await api('/breeding/records/vaccines?current=2&size=1')
  const g1 = p1.data?.records?.[0]
  const g2 = p2.data?.records?.[0]
  check('G1 size=1 第一页只有 1 组', p1.data?.records?.length === 1)
  check('G2 两页是不同的组', !!g1 && !!g2 && g1.groupKey !== g2.groupKey, `${g1?.groupKey} vs ${g2?.groupKey}`)
  check('G3 total 是组数且两页一致', p1.data?.total === p2.data?.total, `${p1.data?.total} vs ${p2.data?.total}`)

  // ---------------------------------------------------------------- 9 清场
  console.log('\n▶ 9 清理本轮自造数据（按耳标前缀 + 批次号，不动演示台账）')
  const pigIds = sqlOne(`USE db_breeding; SELECT GROUP_CONCAT(id) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  if (pigIds && pigIds !== 'NULL') {
    sql(`USE db_breeding; DELETE FROM vaccine_record WHERE pig_id IN (${pigIds});`)
    sql(`USE db_breeding; DELETE FROM quarantine_certificate WHERE pig_id IN (${pigIds});`)
    sql(`USE db_breeding; DELETE FROM slaughter_apply WHERE pig_id IN (${pigIds});`)
    sql(`USE db_breeding; DELETE FROM pig_individual WHERE id IN (${pigIds});`)
  }
  sql(`USE db_breeding; DELETE FROM pig_batch WHERE batch_no = '${BATCH_NO}';`)
  sql(`USE db_breeding; DELETE FROM farm WHERE farm_name = '${FARM_NAME}';`)
  sql(`USE db_ingest; DELETE FROM ingest_staging WHERE biz_key LIKE 'GRP${RUN}-%';`)
  if (voidedIds.length > 0) {
    const keys = voidedIds.map((id) => `'VACCINE:${id}:VOID'`).join(',')
    sql(`USE db_common; DELETE FROM blockchain_record WHERE biz_key IN (${keys});`)
  }
  // 出证会留链上事件（QC-<RUN>-<耳标>）——业务表删了链不删，下次回归台账就多出 30 条幽灵凭证
  sql(`USE db_common; DELETE FROM blockchain_record WHERE biz_key LIKE 'QC-${RUN}-%';`)

  const leftPigs = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  const leftBatch = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_batch WHERE batch_no = '${BATCH_NO}';`)
  const leftStage = sqlOne(`USE db_ingest; SELECT COUNT(*) FROM ingest_staging WHERE biz_key LIKE 'GRP${RUN}-%';`)
  check('H1 成员行已清零', String(leftPigs) === '0', `剩余 ${leftPigs}`)
  check('H2 批次已清零', String(leftBatch) === '0', `剩余 ${leftBatch}`)
  check('H3 上报流水已清零', String(leftStage) === '0', `剩余 ${leftStage}`)
  const leftChain = sqlOne(`USE db_common; SELECT COUNT(*) FROM blockchain_record WHERE biz_key LIKE 'QC-${RUN}-%';`)
  check('H4 出证链上事件已清零', String(leftChain) === '0', `剩余 ${leftChain}`)

  // ---------------------------------------------------------------- 汇总
  console.log(`\n── 结果：${pass} 通过 / ${fail} 失败 ──`)
  if (fail > 0) {
    console.log('失败项：' + failures.join('、'))
    process.exit(1)
  }
})().catch((e) => {
  console.error('脚本异常：', e)
  process.exit(1)
})
