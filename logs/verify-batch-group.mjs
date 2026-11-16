/**
 * 单据视图回归：免疫 / 产地检疫 / 出栏申报「一张单据一行 + 关联生猪」的口径。
 * （2026-11-15 单据化改造后重写：三个模块各有一张真单据表，
 *   批次号是存下来的单据号——免疫批次号 / 检疫证号 / 申报单号，
 *   不再是查询时按「批次+签名+日期」临时算出来的。）
 *
 * 覆盖：
 *  A 30 头同疫苗同日逐头上报 → 自动归成 1 张免疫批次，头数/关联生猪名单正确
 *  B 换一种疫苗 → 是另外 1 张批次单（不同疫苗不并）
 *  C 作废 2 头 → voidCount 与状态摘要同步，名单仍然逐头在
 *  D 一证多头：一张检疫证覆盖 29 头 → 1 张证单据，证号即批次号
 *  E 一单多头：一次申报 30 头 → 1 张申报单；单据级审批通过后整单已出栏
 *  F 免疫档案硬前置：免疫被作废的猪出证必须被拒；补免后放行
 *  G 免疫批次名单不依赖养殖批次：把猪的 batch_id 置空，批次单名单不变
 *  H 分页按「单据」算：size=1 翻两页拿到不同单据，total 一致
 *
 * 数据自持：自己建场 + 建 30 头批次，跑完按耳标前缀精确清理（不碰演示台账）。
 * 链上记录保留不删（清 blockchain_record 会让账本块成孤儿——项目红线）。
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
const CERT_NO = `QC-${RUN}-BATCH`
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

/** paged 全量拉回（单据视图，size 给大） */
async function fetchGroups(kind) {
  const res = await api(`/breeding/records/${kind}?current=1&size=500`)
  return { total: res.data?.total || 0, records: res.data?.records || [] }
}

/** 本轮自造的单据：名单里含本轮耳标前缀的组（免疫批次按场归批，本场只有本轮的猪） */
const mineGroups = (records) => records.filter((g) =>
  (g.members || []).some((m) => (m.earTagNo || '').startsWith(TAG_PREFIX)))

;(async () => {
  console.log(`\n── 单据视图回归（RUN=${RUN}）──\n`)

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
  console.log('\n▶ 2 疫苗：30 头同疫苗同日逐头上报 = 自动归成 1 张免疫批次')
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
  // 先确认上报真的入了账：单据断言失败时，要能立刻分清是「归批写错了」还是「数据没进来」
  const badVac = vacReceipts.filter((r) => !r.data || Number(r.data.status) !== 2)
  check('A1 30 条免疫上报全部入账', badVac.length === 0,
    badVac.length ? `${badVac.length} 条未入账，首条：${JSON.stringify(badVac[0]).slice(0, 240)}` : '')

  let vac = await fetchGroups('vaccines')
  let mine = mineGroups(vac.records).filter((g) => g.title === VACCINE_A)
  check('A2 逐头上报自动归成 1 张免疫批次', mine.length === 1, `实际 ${mine.length} 张`)
  const groupA = mine[0]
  check('A3 批次号是免疫批次号（YH 开头，不是养殖批次号）',
    typeof groupA?.docNo === 'string' && groupA.docNo.startsWith('YH'), `实际 ${groupA?.docNo}`)
  check('A4 关联生猪 30 头', groupA?.headCount === 30, `实际 ${groupA?.headCount}`)
  check('A5 名单逐头保留且耳标正确',
    groupA?.members?.length === 30
    && groupA.members.every((m) => m.earTagNo && m.earTagNo.startsWith(TAG_PREFIX)),
    `实际 ${groupA?.members?.length}`)
  check('A6 状态摘要是「全部有效」', groupA?.statusSummary === '全部有效', groupA?.statusSummary)

  // ---------------------------------------------------------------- 3 换疫苗不并单
  console.log('\n▶ 3 同批换一种疫苗 = 另一张免疫批次')
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
  mine = mineGroups(vac.records)
  check('B1 本场出现 2 张免疫批次（不同疫苗不并）', mine.length === 2, `实际 ${mine.length} 张`)

  // ---------------------------------------------------------------- 4 作废两条
  console.log('\n▶ 4 名单内作废 2 头 → 计数与摘要同步')
  const target = groupA.members.slice(0, 2)
  for (const m of target) {
    await api(`/breeding/pigs/${m.pigId}/vaccines/${m.id}/void`, {
      method: 'POST',
      body: JSON.stringify({ reason: '分组回归：作废计数验证' }),
    })
  }
  vac = await fetchGroups('vaccines')
  const groupA2 = mineGroups(vac.records).find((g) => g.title === VACCINE_A)
  check('C1 voidCount = 2', groupA2?.voidCount === 2, `实际 ${groupA2?.voidCount}`)
  check('C2 状态摘要「部分作废」', groupA2?.statusSummary === '部分作废', groupA2?.statusSummary)
  check('C2b 计数进明细「有效 28 / 已作废 2」',
    groupA2?.statusDetail === '有效 28 / 已作废 2', groupA2?.statusDetail)
  check('C3 作废后名单仍是 30 头（成员不消失）', groupA2?.members?.length === 30, `实际 ${groupA2?.members?.length}`)
  const voidedInDetail = (groupA2?.members || []).filter((m) => m.voidFlag === 1).length
  check('C4 名单里 2 头标记为已作废', voidedInDetail === 2, `实际 ${voidedInDetail}`)

  // 单头批次整单作废：必须是「全部作废」，绝不能落到「部分作废」——
  // 那一次免疫动作整体失效了，「有效 0」这种计数读起来像还剩点什么。
  const groupB = mineGroups(vac.records).find((g) => g.title === VACCINE_B)
  const soleMember = groupB?.members?.[0]
  check('C5 换疫苗那张只有 1 头', !!soleMember, `members=${groupB?.members?.length}`)
  if (soleMember) {
    await api(`/breeding/pigs/${soleMember.pigId}/vaccines/${soleMember.id}/void`, {
      method: 'POST',
      body: JSON.stringify({ reason: '分组回归：整单作废文案验证' }),
    })
  }
  const vac3 = await fetchGroups('vaccines')
  const groupB2 = mineGroups(vac3.records).find((g) => g.title === VACCINE_B)
  check('C6 单头批次作废后摘要是「全部作废」',
    groupB2?.statusSummary === '全部作废', groupB2?.statusSummary)
  check('C7 全作废时明细「已作废 1 头」', groupB2?.statusDetail === '已作废 1 头', groupB2?.statusDetail)
  check('C8 全作废不显示「有效 0」',
    !String(groupB2?.statusSummary || '').includes('有效 0')
    && !String(groupB2?.statusDetail || '').includes('有效 0'),
    `${groupB2?.statusSummary} / ${groupB2?.statusDetail}`)

  // ---------------------------------------------------------------- 5 检疫：一证多头
  console.log('\n▶ 5 检疫：一张证覆盖一车猪（一证多头）')
  // 先说清这一段的现实顺序：产地检疫要查验**有效**免疫记录。
  // 上面把 A 批次前两头的免疫作废了，这两头现在没有免疫档案，出证必须被当场拒绝。
  // 注意别拿 soleMember 当样本：它只是 VACCINE_B 那条被作废，身上还留着有效的 VACCINE_A。
  const blockedPig = target[0]
  const solePigId = blockedPig?.pigId
  let blockedCert = null
  if (solePigId) {
    blockedCert = await api('/breeding/quarantine-certs', {
      method: 'POST',
      body: JSON.stringify({
        pigIds: members.map((p) => p.id),
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
  // 上一步作废的是 A 批次前两头，两头都要补 —— 只补一头，另一头出证时还会被拒。
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
  }

  // 一张证覆盖其余 29 头（blocked 那头已单独持证，不能再进第二张证）
  const batchCertPigs = members.filter((p) => p.id !== solePigId).map((p) => p.id)
  const batchCert = await api('/breeding/quarantine-certs', {
    method: 'POST',
    body: JSON.stringify({
      pigIds: batchCertPigs,
      certNo: CERT_NO,
      issueOrg: CERT_ORG,
      issueTime: `${DAY} 08:30:00`,
      validUntil: DAY,
      inspector: '李检疫',
      fileId: voucherId,
      caSignature: `CA-GRP-${RUN}-${CERT_NO}`,
    }),
  })
  check('D0 一证多头出证成功（29 头）', batchCert?.code === 200 || batchCert?.code === 0,
    JSON.stringify(batchCert?.message).slice(0, 240))
  const certs = await fetchGroups('certs')
  const certGroups = mineGroups(certs.records).filter((g) => g.docNo === CERT_NO)
  check('D1 证号即批次号，恰好 1 张', certGroups.length === 1, `实际 ${certGroups.length} 张`)
  check('D2 关联生猪 29 头', certGroups[0]?.headCount === 29, `实际 ${certGroups[0]?.headCount}`)
  check('D3 名单耳标全部属于本轮', certGroups[0]?.members?.every((m) => m.earTagNo.startsWith(TAG_PREFIX)))
  check('D4 状态摘要「有效」', certGroups[0]?.statusSummary === '有效', certGroups[0]?.statusSummary)

  // ---------------------------------------------------------------- 6 申报：一单多头
  console.log('\n▶ 6 申报：一次申报 30 头 = 一张申报单，审批是单据级')
  const applyRes = await api('/breeding/applies', {
    method: 'POST',
    body: JSON.stringify({
      pigIds: members.map((p) => p.id),
      weightKg: 3050,
      targetSlaughterhouse: `分组回归屠宰场${RUN}`,
    }),
  })
  const applyDocId = applyRes.data
  check('E0 一单多头申报成功', !!applyDocId, JSON.stringify(applyRes.message).slice(0, 200))
  let applies = await fetchGroups('applies')
  let applyGroups = mineGroups(applies.records)
  check('E1 30 头在 1 张申报单上', applyGroups.length === 1 && applyGroups[0]?.headCount === 30,
    `实际 ${applyGroups.length} 张 / head=${applyGroups[0]?.headCount}`)
  check('E2 单号是申报号（SA 开头）',
    typeof applyGroups[0]?.docNo === 'string' && applyGroups[0].docNo.startsWith('SA'),
    `实际 ${applyGroups[0]?.docNo}`)
  check('E3 状态摘要「待审批」', applyGroups[0]?.statusSummary === '待审批', applyGroups[0]?.statusSummary)

  const approveRes = await api(`/breeding/applies/${applyDocId}/approve`, {
    method: 'PUT',
    body: JSON.stringify({ approved: true, comment: '分组回归：单据级审批验证' }),
  })
  check('E4 单据级审批通过', approveRes?.code === 200 || approveRes?.code === 0,
    JSON.stringify(approveRes?.message).slice(0, 160))
  applies = await fetchGroups('applies')
  applyGroups = mineGroups(applies.records)
  check('E5 审批后摘要「已通过」', applyGroups[0]?.statusSummary === '已通过', applyGroups[0]?.statusSummary)
  const outPigs = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%' AND status = 2;`)
  check('E6 单内每一头都推进为「已出栏」', String(outPigs) === '30', `实际 ${outPigs}`)

  // ---------------------------------------------------------------- 7 免疫批次不依赖养殖批次
  console.log('\n▶ 7 免疫批次名单不依赖养殖批次归属')
  const orphanIds = members.slice(28, 30).map((p) => p.id)
  sql(`USE db_breeding; UPDATE pig_individual SET batch_id = NULL WHERE id IN (${orphanIds.join(',')});`)
  const stillNull = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE id IN (${orphanIds.join(',')}) AND batch_id IS NULL;`)
  check('G1 已把 2 头改成无养殖批次', String(stillNull) === '2', `实际 ${stillNull}`)
  const vacAfter = await fetchGroups('vaccines')
  const groupA3 = mineGroups(vacAfter.records).find((g) => g.title === VACCINE_A)
  // 成员在上报那一刻就挂进了免疫批次；养殖批次变动不改写历史名单——
  // 「它当时确实打了这一针」是事实，不因后来转场/并批而消失。
  check('G2 免疫批次名单仍是 30 头（历史事实不被改写）',
    groupA3?.headCount === 30, `实际 ${groupA3?.headCount}`)

  // ---------------------------------------------------------------- 8 分页按单据
  console.log('\n▶ 8 分页按「单据」算')
  const p1 = await api('/breeding/records/vaccines?current=1&size=1')
  const p2 = await api('/breeding/records/vaccines?current=2&size=1')
  const g1 = p1.data?.records?.[0]
  const g2 = p2.data?.records?.[0]
  check('H1 size=1 第一页只有 1 张', p1.data?.records?.length === 1)
  check('H2 两页是不同的单据', !!g1 && !!g2 && g1.groupKey !== g2.groupKey, `${g1?.groupKey} vs ${g2?.groupKey}`)
  check('H3 total 是单据数且两页一致', p1.data?.total === p2.data?.total, `${p1.data?.total} vs ${p2.data?.total}`)

  // ---------------------------------------------------------------- 9 清场
  console.log('\n▶ 9 清理本轮自造数据（按耳标前缀 + 批次号，不动演示台账）')
  const pigIds = sqlOne(`USE db_breeding; SELECT GROUP_CONCAT(id) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  if (pigIds && pigIds !== 'NULL') {
    // 单据化后多四张表要清：成员表先于主表，免疫批次按「名单全是本轮猪」的单据删
    const vacBatchIds = sqlOne(`USE db_breeding; SELECT IFNULL(GROUP_CONCAT(DISTINCT vbatch_id),'NULL') FROM vaccine_record WHERE pig_id IN (${pigIds});`)
    const certIds = sqlOne(`USE db_breeding; SELECT IFNULL(GROUP_CONCAT(DISTINCT cert_id),'NULL') FROM quarantine_cert_pig WHERE pig_id IN (${pigIds});`)
    const applyIds = sqlOne(`USE db_breeding; SELECT IFNULL(GROUP_CONCAT(DISTINCT apply_id),'NULL') FROM slaughter_apply_pig WHERE pig_id IN (${pigIds});`)
    sql(`USE db_breeding; DELETE FROM vaccine_batch_pig WHERE pig_id IN (${pigIds});`)
    if (vacBatchIds && vacBatchIds !== 'NULL') sql(`USE db_breeding; DELETE FROM vaccine_batch WHERE id IN (${vacBatchIds});`)
    sql(`USE db_breeding; DELETE FROM vaccine_record WHERE pig_id IN (${pigIds});`)
    sql(`USE db_breeding; DELETE FROM quarantine_cert_pig WHERE pig_id IN (${pigIds});`)
    if (certIds && certIds !== 'NULL') sql(`USE db_breeding; DELETE FROM quarantine_certificate WHERE id IN (${certIds});`)
    sql(`USE db_breeding; DELETE FROM slaughter_apply_pig WHERE pig_id IN (${pigIds});`)
    if (applyIds && applyIds !== 'NULL') sql(`USE db_breeding; DELETE FROM slaughter_apply WHERE id IN (${applyIds});`)
    sql(`USE db_breeding; DELETE FROM pig_individual WHERE id IN (${pigIds});`)
  }
  sql(`USE db_breeding; DELETE FROM pig_batch WHERE batch_no = '${BATCH_NO}';`)
  sql(`USE db_breeding; DELETE FROM farm WHERE farm_name = '${FARM_NAME}';`)
  sql(`USE db_ingest; DELETE FROM ingest_staging WHERE biz_key LIKE 'GRP${RUN}-%';`)
  // 链上记录一律保留：删 blockchain_record 会让账本块成孤儿（项目红线），
  // 本轮链上事件都以 biz_key 带 RUN 后缀，可在链上追溯「这轮回归发生过」

  const leftPigs = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_individual WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  const leftBatch = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM pig_batch WHERE batch_no = '${BATCH_NO}';`)
  const leftStage = sqlOne(`USE db_ingest; SELECT COUNT(*) FROM ingest_staging WHERE biz_key LIKE 'GRP${RUN}-%';`)
  const leftVacBatch = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM vaccine_batch b WHERE EXISTS (SELECT 1 FROM vaccine_batch_pig m WHERE m.batch_id = b.id AND m.ear_tag_no LIKE '${TAG_PREFIX}%');`)
  const leftCertMember = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM quarantine_cert_pig WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  const leftApplyMember = sqlOne(`USE db_breeding; SELECT COUNT(*) FROM slaughter_apply_pig WHERE ear_tag_no LIKE '${TAG_PREFIX}%';`)
  check('I1 成员行已清零', String(leftPigs) === '0', `剩余 ${leftPigs}`)
  check('I2 批次已清零', String(leftBatch) === '0', `剩余 ${leftBatch}`)
  check('I3 上报流水已清零', String(leftStage) === '0', `剩余 ${leftStage}`)
  check('I4 免疫批次及名单已清零', String(leftVacBatch) === '0' && String(leftCertMember) === '0' && String(leftApplyMember) === '0',
    `免疫批次 ${leftVacBatch} / 证成员 ${leftCertMember} / 申报成员 ${leftApplyMember}`)

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
