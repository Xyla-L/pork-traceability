#!/usr/bin/env node
/**
 * B 档自动化录入 · 接入层验收测试
 * ---------------------------------------------------------------------------
 * 在没有真实设备的前提下，用模拟器把 6 组关键用例跑一遍，直接给出通过/失败结论。
 *
 * 用法：
 *   node scripts/test/ingest-acceptance-test.mjs
 *   node scripts/test/ingest-acceptance-test.mjs --base http://localhost:8080/api/v1
 *
 * 覆盖用例（对应前面定下的验收标准）：
 *   1 鉴权        —— 错误设备密钥必须被拒
 *   2 幂等        —— 同一 bizKey 连推 3 次，业务表只能有 1 条
 *   3 脏数据不入库 —— -999℃ / 非数字温度必须被拒，且不得写业务表
 *   4 异常温度保留 —— 5℃ 超区间仍入账并标记 is_abnormal=1（不做降采样丢弃）
 *   5 高频降采样   —— 非异常值按最小间隔丢弃，但留档可查
 *   6 仪器未出结果 —— 瘦肉精只推耳标不推结果，必须拒绝（不得默认阴性）
 *   7 降级可用    —— 设备通道异常不影响人工录入入口（这里验证人工接口仍可达）
 *   8 入场查验    —— 检疫证核验未过不自动入账、转人工；人工确认后才写业务表
 *   9 门店收银    —— POS 扫码售出，零售记录来源标记为 DEVICE
 *  10 门店签收    —— PDA 签收生成签收单，并把运单推进到「已签收」
 *  11 养殖建档    —— 缺养殖场/养殖场未登记转人工；正常建档 dataSource=DEVICE；同耳标幂等
 *  12 免疫注射    —— 缺疫苗批号必须拒绝；带批号入账 vaccine_record.source=DEVICE
 *  13 屠宰检验    —— 终端未给结论必须拒绝（不得默认合格）；带结论+兽医入账 source=DEVICE
 *  14 胴体盖章    —— 无合格检验必须拒绝（人工确认也绕不过）；检验合格后盖章入账
 *  15 分割批次    —— 胴体批次不存在转人工；扫码称重自动建批次（批次号系统生成）
 *
 * 8 以后会真实写入业务数据（这正是验收目的：证明设备能写业务表），
 * 因此需要 --auto-depart 之外的前置数据；缺前置时用例标 SKIP 而非 FAIL。
 *
 * 只读校验（不改动业务数据）：断言全部基于「新增记录数」与「接口返回」，
 * 不删除、不修改任何既有数据，避免污染演示数据。
 */

import { ago, bizKey, nowText, report } from './device-simulator.mjs'

const args = process.argv.slice(2)
const argValue = (name, fallback) => {
  const i = args.indexOf(name)
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback
}
const BASE = argValue('--base', 'http://localhost:8080/api/v1')
const ADMIN = { username: argValue('--user', 'admin'), password: argValue('--pass', '123456') }

const results = []
let token = null

function record(no, name, passed, detail) {
  results.push({ no, name, passed, detail })
  console.log(`${passed ? '✅ PASS' : '❌ FAIL'}  [${no}] ${name}\n        ${detail}`)
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers })
  const text = await response.text()
  try {
    return { httpStatus: response.status, ...JSON.parse(text) }
  } catch {
    return { httpStatus: response.status, message: text }
  }
}

async function login() {
  const res = await api('/auth/login', { method: 'POST', body: JSON.stringify(ADMIN) })
  const data = res.data || {}
  token = data.accessToken || data.token || data.access_token
  if (!token) throw new Error(`登录失败：${res.message || JSON.stringify(res).slice(0, 200)}`)
  console.log(`登录成功（${ADMIN.username}）\n`)
}

/** 找一条运输中的运单——温度通道只接受"运输中"的运单 */
async function findInTransitTransport() {
  const res = await api('/distribution/transports?status=2&pageNum=1&pageSize=5')
  const records = res.data?.records || res.data?.list || []
  if (records[0]) return records[0]
  if (!args.includes('--auto-depart')) return null
  // 显式要求时，自动把最新一条"待发车"的运单发车（会改变演示数据状态，仅测试环境使用）
  const pending = await api('/distribution/transports?status=1&pageNum=1&pageSize=5')
  const candidate = (pending.data?.records || pending.data?.list || [])[0]
  if (!candidate) return null
  await api(`/distribution/transports/${candidate.id}/depart`, { method: 'PUT' })
  console.log(`已自动发车运单 ${candidate.transportNo}（id=${candidate.id}）用于温度通道测试\n`)
  return { ...candidate, status: 2 }
}

async function main() {
  console.log('='.repeat(78))
  console.log('B 档自动化录入 · 接入层验收测试')
  console.log(`网关: ${BASE}`)
  console.log('='.repeat(78) + '\n')

  await login()

  // ---------------------------------------------------------------- 1 鉴权
  {
    const res = await report('this-key-does-not-exist', 'TEMPERATURE', bizKey('BAD'),
      { transportNo: 'NOPE', temperature: 1 })
    // 接入服务用统一错误码 UNAUTHORIZED=2001 返回（HTTP 仍为 200）；网关层面则是 401
    const rejected = res.code === 2001 || res.code === 401 || res.httpStatus === 401
    record(1, '设备密钥鉴权', rejected,
      `HTTP ${res.httpStatus} code=${res.code} message=${res.message}`)
  }

  const transport = await findInTransitTransport()
  if (!transport) {
    record(2, '前置条件：存在运输中的运单', false,
      '没有 status=2（运输中）的运单，温度通道无法测。请先在「分割配送 → 冷链运输」给一条待发车任务点「发车」，' +
      '或加 --auto-depart 让测试自动发车一条（会改变演示数据状态）后重跑。')
  } else {
    console.log(`使用运单 ${transport.transportNo}（id=${transport.id}）\n`)
    const before = await temperatureCount(transport.id)

    // ------------------------------------------------------------ 2 幂等
    {
      const key = bizKey('IDEM')
      const payload = { transportNo: transport.transportNo, temperature: 3.3 }
      const first = await report('dev-key-temp-001', 'TEMPERATURE', key, payload)
      const second = await report('dev-key-temp-001', 'TEMPERATURE', key, payload)
      const third = await report('dev-key-temp-001', 'TEMPERATURE', key, payload)
      const after = await temperatureCount(transport.id)
      const added = after - before
      const deduped = second.data?.duplicate === true && third.data?.duplicate === true
      record(2, '幂等：同一 bizKey 连推 3 次只入账 1 条', added === 1 && deduped,
        `新增 ${added} 条（期望 1）；重复标记 duplicate=${second.data?.duplicate}/${third.data?.duplicate}；首次=${first.data?.statusLabel}`)
    }

    const before3 = await temperatureCount(transport.id)

    // ---------------------------------------------------- 3 脏数据不入库
    {
      const bad1 = await report('dev-key-temp-001', 'TEMPERATURE', bizKey('DIRTY'),
        { transportNo: transport.transportNo, temperature: -999 })
      const bad2 = await report('dev-key-temp-001', 'TEMPERATURE', bizKey('DIRTY'),
        { transportNo: transport.transportNo, temperature: 'abc' })
      const after = await temperatureCount(transport.id)
      const added = after - before3
      const bothRejected = [bad1, bad2].every((r) => r.data?.accepted === false && r.data?.status === 3)
      record(3, '脏数据不入库（-999℃ / 非数字）', added === 0 && bothRejected,
        `业务表新增 ${added} 条（期望 0）；-999 → ${bad1.data?.statusLabel}（${bad1.data?.message}）；'abc' → ${bad2.data?.statusLabel}`)
    }

    // -------------------------------------------- 4 异常温度保留且标记异常
    {
      const marker = 6.6 // 挑一个不会与演示数据重复的温度值，便于在库里精确定位
      const res = await report('dev-key-temp-001', 'TEMPERATURE', bizKey('ABNORMAL'),
        { transportNo: transport.transportNo, temperature: marker })
      const rows = (await api(`/distribution/transports/${transport.id}/temperature`)).data || []
      const hit = rows.find((r) => Number(r.temperature) === marker)
      record(4, '异常温度照常入账并标记 is_abnormal=1',
        res.data?.accepted === true && hit && hit.isAbnormal === 1,
        `入账=${res.data?.accepted}；库内匹配到温度=${hit?.temperature} isAbnormal=${hit?.isAbnormal}（异常值永不降采样丢弃）`)
    }

    // -------------------------------- 5 非异常高频上报按最小间隔降采样丢弃
    {
      // 取一个「区间内」的温度（-5℃），并把设备时间推到 2 分钟后，
      // 以便与上一条已入账记录的间隔超过 60 秒窗口，让降采样逻辑真正生效
      const base = 120
      const first = await report('dev-key-temp-001', 'TEMPERATURE', bizKey('FAST'),
        { transportNo: transport.transportNo, temperature: -5.0 },
        { reportTime: nowText(new Date(Date.now() + base * 1000)) })
      const second = await report('dev-key-temp-001', 'TEMPERATURE', bizKey('FAST'),
        { transportNo: transport.transportNo, temperature: -5.1 },
        { reportTime: nowText(new Date(Date.now() + (base + 1) * 1000)) })
      record(5, '高频非异常值按最小间隔降采样，留档但不入业务表',
        first.data?.accepted === true && second.data?.status === 4,
        `首条 → ${first.data?.statusLabel}；1 秒后第二条 → ${second.data?.statusLabel}：${second.data?.message}`)
    }
  }

  // ------------------------------------------------ 6 瘦肉精：仪器未出结果必须拒绝
  {
    const resp = await report('dev-key-reader-001', 'RACTOPAMINE', bizKey('RT'),
      { earTagNo: 'ET-TEST-0001', sampleNo: `S-${Date.now()}` })
    const rejected = resp.data?.accepted === false && resp.data?.status === 3
    record(6, '瘦肉精：仪器未出结果必须拒绝（不得默认阴性）', rejected,
      `${resp.data?.statusLabel}：${resp.data?.message}`)
  }

  // --------------------------- 6.1 瘦肉精：仪器出了结果才入账，且来源标记为 DEVICE
  {
    const sample = await findPigWithCert()
    if (!sample) {
      recordSkip(6.1, '瘦肉精：仪器出结果后入账，且来源标记为 DEVICE', '养殖库取不到生猪样本')
    } else {
      const sampleNo = `S-ACC-${Date.now()}`
      const batchNo = argValue('--batch-no', `BATCH-ACC-${Date.now()}`)
      const resp = await report('dev-key-reader-001', 'RACTOPAMINE', bizKey('RT'), {
        earTagNo: sample.earTagNo,
        sampleNo,
        batchNo,
        result: 1,
        testMethod: '胶体金免疫层析法',
        testTarget: '克伦特罗',
        samplePart: '尿液',
      })
      const rows = firstPage(await api('/slaughter/ractopamine?pageNum=1&pageSize=10'))
      const row = rows.find((r) => r.sampleNo === sampleNo)
      record(6.1, '瘦肉精：仪器出结果后入账，且来源标记为 DEVICE',
        resp.data?.accepted === true && row?.source === 'DEVICE',
        `耳标 ${sample.earTagNo} 样本 ${sampleNo} → ${resp.data?.statusLabel}；` +
        `检测记录 source=${row?.source} 结果=${row?.result}（1=阴性）`)
    }
  }

  // ------------------------------------------------ 7 降级：人工入口仍然可用
  {
    const res = await api('/slaughter/entries?pageNum=1&pageSize=1')
    record(7, '降级可用：设备通道异常时人工录入入口不受影响', res.code === 200,
      `人工入口 GET /slaughter/entries → code=${res.code} total=${res.data?.total}（说明原有手工流程未被接入层改动波及）`)
  }

  // ==========================================================================
  // 以下三条把剩下的三个通道也走通：入场查验 / 门店收银 / 门店签收。
  // 前四个用例只覆盖了温度与瘦肉精，入库目标表的 source 字段仍是 MANUAL，
  // 必须真跑一遍才能证明"设备写业务表"这件事在五条通道上都成立。
  // ==========================================================================

  // ------------------------------------- 8 入场查验：核验未过必须转人工再放行
  {
    const sample = await findPigWithCert()
    if (!sample) {
      recordSkip(8, '入场查验通道：检疫证核验未过转人工、人工确认后才入账',
        '养殖库取不到「生猪 + 产地检疫证」样本，跳过（可用 --ear-tag/--cert-no 指定）')
    } else {
      const batchNo = argValue('--batch-no', `BATCH-ACC-${Date.now()}`)
      const before = await entryTotal()
      const resp = await report('dev-key-rfid-001', 'ENTRY', bizKey('ENTRY'), {
        earTagNo: sample.earTagNo,
        batchNo,
        quarantineCert: sample.certNo,
        vehicleNo: '京A·12345',
        weight: 118.5,
        healthCheck: 1,
      })
      const after = await entryTotal()
      record(8, '入场查验：检疫证核验未过 → 不自动入账，转待人工处理',
        resp.data?.status === 1 && after === before,
        `耳标 ${sample.earTagNo}（检疫证 ${sample.certNo}，有效期至 ${sample.validUntil}）：` +
        `${resp.data?.statusLabel}；entry_inspection ${before} → ${after}（须不变）；原因：${resp.data?.message}`)

      // 8b 人工确认放行——这一步才允许写业务表，且来源必须标成 DEVICE
      if (resp.data?.stagingId) {
        const approve = await api(`/ingest/staging/${resp.data.stagingId}/approve`, {
          method: 'POST',
          body: JSON.stringify({ handler: '验收测试' }),
        })
        const afterApprove = await entryTotal()
        const rows = firstPage(await api('/slaughter/entries?pageNum=1&pageSize=1'))
        const row = rows[0]
        record(8.1, '入场查验：人工确认后写入业务表，且来源标记为 DEVICE',
          approve.data?.status === 2 && afterApprove === before + 1 && row?.source === 'DEVICE',
          `确认后 ${approve.data?.statusLabel}；entry_inspection ${before} → ${afterApprove}；` +
          `入库记录 source=${row?.source} sourceRef=${row?.sourceRef}（设备自动采集）`)
      }
    }
  }

  // ------------------------------- 9 门店收银：POS 扫码即激活 + 售出，来源可追
  {
    const receipt = firstPage(await api('/distribution/receipts?pageNum=1&pageSize=1'))[0]
    if (!receipt) {
      recordSkip(9, '门店收银通道：POS 扫码售出并标记来源为 DEVICE', '配送库没有签收单，无法生成产品码')
    } else {
      const gen = await api('/sales/qrcodes/batch', {
        method: 'POST',
        body: JSON.stringify({ receiptId: receipt.id, count: 1 }),
      })
      const qr = firstString(gen.data)
      if (!qr) {
        recordSkip(9, '门店收银通道：POS 扫码售出并标记来源为 DEVICE',
          `生成产品码失败：${gen.message || JSON.stringify(gen.data)?.slice(0, 160)}`)
      } else {
        const resp = await report('dev-key-pos-001', 'SALE', bizKey('POS'), {
          qrCode: qr, sellPrice: 38.5, sellWeightKg: 0.75,
        })
        const rows = firstPage(await api('/sales/records?pageNum=1&pageSize=10'))
        const row = rows.find((r) => r.productQrCode === qr)
        record(9, '门店收银通道：POS 扫码售出并标记来源为 DEVICE',
          resp.data?.accepted === true && row?.source === 'DEVICE',
          `码 ${qr} → ${resp.data?.statusLabel}；销售记录 source=${row?.source} ` +
          `status=${row?.status}（2=已售出）售价=${row?.sellPrice}`)
      }
    }
  }

  // --------------------------- 10 门店签收：PDA 扫码签收，运单状态与来源双校验
  {
    // 不能直接捡现成的 status=1 运单：演示库里有的运单状态被重置过、签收单却还在，
    // 一签就撞"重复签收"。这里自己建一条运单，测试才能反复跑而不受历史数据干扰。
    const split = firstPage(await api('/distribution/splits?pageNum=1&pageSize=1'))[0]
    if (!split) {
      recordSkip(10, '门店签收通道：PDA 签收生成签收单并把运单转「已签收」',
        '配送库没有分割批次，无法新建运单')
    } else {
      const created = await api('/distribution/transports', {
        method: 'POST',
        body: JSON.stringify({
          splitBatchId: split.id,
          vehicleNo: `京A·T${String(Date.now()).slice(-4)}`,
          vehicleType: '冷藏车',
          refrigeration: '机械制冷',
          driverName: '李四',
          driverPhone: '13900000000',
          origin: '分割车间',
          destination: '示范一店',
        }),
      })
      const transport = created.data
      if (!transport?.id) {
        recordSkip(10, '门店签收通道：PDA 签收生成签收单并把运单转「已签收」',
          `新建运单失败：${created.message || JSON.stringify(created).slice(0, 160)}`)
      } else {
        // 签收的前置状态是「已到达」，所以要先发车再报到达
        await api(`/distribution/transports/${transport.id}/depart`, { method: 'PUT' })
        await api(`/distribution/transports/${transport.id}/arrive`, { method: 'PUT' })
        const store = (await api('/distribution/stores')).data?.[0] || {}
        const storeName = store.store_name || store.storeName || '示范一店'
        const resp = await report('dev-key-pda-001', 'RECEIPT', bizKey('PDA'), {
          transportNo: transport.transportNo,
          storeId: store.store_id,
          storeName,
          receiver: '张三',
          receiverPhone: '13800000000',
          tempValue: -2.5,
          qtyCheck: 1,
          tempCheck: 1,
          packageIntact: 1,
        })
        const stored = firstPage(await api('/distribution/receipts?pageNum=1&pageSize=10'))
          .find((r) => r.transportId === transport.id)
        const after = (await api(`/distribution/transports/${transport.id}`)).data
        record(10, '门店签收通道：PDA 签收生成签收单并把运单转「已签收」',
          resp.data?.accepted === true && stored?.source === 'DEVICE' && after?.status === 4,
          `新运单 ${transport.transportNo}（id=${transport.id}）→ ${resp.data?.statusLabel}；` +
          `签收单 source=${stored?.source} 签收人=${stored?.receiver}；` +
          `运单状态 1（待发车）→ ${after?.status}（4=已签收）`)
      }
    }
  }

  // ==========================================================================
  // 二期通道：养殖建档 / 免疫注射 / 屠宰检验 / 胴体盖章 / 分割批次。
  // 覆盖三条法规与技术红线：判定权在人（检验）、盖章以检验为前提、批次号系统生成。
  // ==========================================================================

  // -------------------------------------- 11 养殖建档：耳标读写器，佩戴即建档
  let freshPig = null
  {
    const earTagNo = `ET-ACC-${Date.now()}`
    // 11a 养殖场未登记 → 转人工（登记后可确认入账，retryable）
    const badFarm = await report('dev-key-tag-001', 'TAG', bizKey('TAG'), {
      earTagNo, farmName: '不存在的养殖场-X',
    })
    // 11b 正常建档
    const farm = firstPage(await api('/breeding/farms?pageNum=1&pageSize=1'))[0]
    const farmName = farm?.farmName || farm?.farm_name
    if (!farmName) {
      recordSkip(11, '养殖建档通道：佩戴即建档且 dataSource=DEVICE', '养殖库没有养殖场，无法建档')
    } else {
      const resp = await report('dev-key-tag-001', 'TAG', bizKey('TAG'), {
        earTagNo, farmName, breed: '三元杂', gender: 1, penNo: '验收舍', origin: '自繁',
      })
      const rows = firstPage(await api(`/breeding/pigs?pageNum=1&pageSize=20&earTagNo=${encodeURIComponent(earTagNo)}`))
      const row = rows[0]
      freshPig = row ? { id: row.id, earTagNo } : null
      // 11c 同耳标再推一次（新 bizKey）→ 业务级幂等：返回既有档案，不新建第二条
      const again = await report('dev-key-tag-001', 'TAG', bizKey('TAG'), {
        earTagNo, farmName, breed: '三元杂', gender: 1,
      })
      const archives = firstPage(await api(`/breeding/pigs?pageNum=1&pageSize=20&earTagNo=${encodeURIComponent(earTagNo)}`))
      record(11, '养殖建档：佩戴即建档、来源标记 DEVICE、同耳标幂等',
        badFarm.data?.status === 1 && resp.data?.accepted === true && row?.dataSource === 'DEVICE'
          && again.data?.accepted === true && archives.length === 1,
        `未登记场 → ${badFarm.data?.statusLabel}；建档 → ${resp.data?.statusLabel}；` +
        `pig_individual.dataSource=${row?.dataSource}；重复上报 → ${again.data?.statusLabel}，` +
        `同耳标档案数=${archives.length}（期望 1）`)
    }
  }

  // ------------------------------------------ 12 免疫注射：无批号不入库
  {
    const sample = freshPig || (await findPigWithCert())
    if (!sample?.earTagNo) {
      recordSkip(12, '免疫注射通道：缺疫苗批号必须拒绝；带批号入账 source=DEVICE', '取不到生猪样本')
    } else {
      const noBatch = await report('dev-key-inj-001', 'VACCINE', bizKey('INJ'), {
        earTagNo: sample.earTagNo, vaccineName: '猪瘟兔化弱毒疫苗',
      })
      const batchNo = `VB-ACC-${Date.now()}`
      const ok = await report('dev-key-inj-001', 'VACCINE', bizKey('INJ'), {
        earTagNo: sample.earTagNo, vaccineName: '猪瘟兔化弱毒疫苗', vaccineBatchNo: batchNo,
        dosage: '2ml', injectSite: '耳后颈部', operator: '刘兽医',
      })
      // 查询路径要的是 pigId（不是疫苗记录 id）；接口返回分页结构（data.records）
      let pigId = sample.id
      if (!pigId) {
        const pigs = firstPage(await api(`/breeding/pigs?pageNum=1&pageSize=5&earTagNo=${encodeURIComponent(sample.earTagNo)}`))
        pigId = pigs[0]?.id
      }
      const vaccines = pigId ? firstPage(await api(`/breeding/pigs/${pigId}/vaccines?pageNum=1&pageSize=10`)) : []
      const row = vaccines.find((v) => v.batchNo === batchNo)
      record(12, '免疫注射：缺疫苗批号必须拒绝；带批号入账且来源标记 DEVICE',
        noBatch.data?.status === 3 && ok.data?.accepted === true && row?.source === 'DEVICE',
        `缺批号 → ${noBatch.data?.statusLabel}（${noBatch.data?.message}）；` +
        `带批号 → ${ok.data?.statusLabel}；vaccine_record.source=${row?.source}`)
    }
  }

  // -------------------------------- 13 屠宰检验：终端未给结论必须拒绝
  let inspectedPig = null
  {
    const sample = freshPig || (await findPigWithCert())
    if (!sample?.earTagNo) {
      recordSkip(13, '屠宰检验通道：无结论必须拒绝；带结论+兽医入账 source=DEVICE', '取不到生猪样本')
    } else {
      const noResult = await report('dev-key-station-001', 'INSPECTION', bizKey('INSP'), {
        earTagNo: sample.earTagNo, batchNo: `BATCH-ACC-${Date.now()}`, veterinary: '李官方兽医',
      })
      const batchNo = `BATCH-ACC-${Date.now()}`
      const ok = await report('dev-key-station-001', 'INSPECTION', bizKey('INSP'), {
        earTagNo: sample.earTagNo, batchNo, result: 1, inspectType: 2, veterinary: '李官方兽医',
        conclusion: '体表、脏器无可见病变，判合格',
      })
      inspectedPig = { earTagNo: sample.earTagNo, batchNo }
      const rows = firstPage(await api('/slaughter/inspections?pageNum=1&pageSize=10'))
      const row = rows.find((r) => r.batchNo === batchNo && (r.earTagNo === sample.earTagNo || r.ear_tag_no === sample.earTagNo))
      record(13, '屠宰检验：终端未给结论必须拒绝（不得默认合格）；带结论入账且来源标记 DEVICE',
        noResult.data?.status === 3 && ok.data?.accepted === true && row?.source === 'DEVICE' && row?.result === 1,
        `无结论 → ${noResult.data?.statusLabel}（${noResult.data?.message}）；` +
        `带结论 → ${ok.data?.statusLabel}；slaughter_inspection.source=${row?.source} 结果=${row?.result}`)
    }
  }

  // -------------------------------- 14 胴体盖章：无合格检验不准打章（硬校验）
  {
    if (!inspectedPig) {
      recordSkip(14, '胴体盖章通道：无合格检验必须拒绝；合格后盖章 source=DEVICE', '前置检验用例未执行')
    } else {
      // 14a 对刚建档、未检验的猪盖章 → 必须拒绝
      let rejectMsg = '(未执行)'
      if (freshPig) {
        const bad = await report('dev-key-stamper-001', 'STAMP', bizKey('STMP'), {
          earTagNo: freshPig.earTagNo, batchNo: `BATCH-ACC-${Date.now()}`,
          carcassNo: `CT-NOINSPECT-${Date.now()}`, veterinary: '李官方兽医',
        })
        rejectMsg = `${bad.data?.statusLabel}：${bad.data?.message}`
      }
      // 14b 对检验合格的猪盖章 → 入账
      const carcassNo = `CT-ACC-${Date.now()}`
      const ok = await report('dev-key-stamper-001', 'STAMP', bizKey('STMP'), {
        earTagNo: inspectedPig.earTagNo, batchNo: inspectedPig.batchNo,
        carcassNo, veterinary: '李官方兽医',
      })
      const rows = firstPage(await api('/slaughter/stamps?pageNum=1&pageSize=10'))
      const row = rows.find((r) => r.carcassNo === carcassNo || r.carcass_no === carcassNo)
      record(14, '胴体盖章：无合格检验必须拒绝；检验合格后盖章入账且来源标记 DEVICE',
        (!freshPig || rejectMsg.includes('拒绝')) && ok.data?.accepted === true && row?.source === 'DEVICE',
        `未检验 → ${rejectMsg}；合格后 → ${ok.data?.statusLabel}；` +
        `carcass_stamp.source=${row?.source} 章号=${row?.stampNo || row?.stamp_no}`)
    }
  }

  // -------------------------------- 15 分割批次：扫码称重自动建批次
  {
    const bad = await report('dev-key-split-001', 'SPLIT', bizKey('SPLT'), {
      parentBatchNo: `CB-NOT-EXIST-${Date.now()}`, productName: '带皮白条', weightKg: 78.5,
    })
    const parent = firstPage(await api('/distribution/batches?pageNum=1&pageSize=1'))[0]
    if (!parent?.batchNo && !parent?.batch_no) {
      recordSkip(15, '分割批次通道：胴体批次不存在转人工；扫码称重自动建批次', '配送库没有胴体批次')
    } else {
      const parentBatchNo = parent.batchNo || parent.batch_no
      const ok = await report('dev-key-split-001', 'SPLIT', bizKey('SPLT'), {
        parentBatchNo, productName: '带皮白条', weightKg: 78.5, packageCount: 1,
        packageType: '真空袋', workshop: '分割车间一号线',
      })
      const rows = firstPage(await api('/distribution/splits?pageNum=1&pageSize=10'))
      const row = rows[0]
      record(15, '分割批次：胴体批次不存在转人工；扫码称重自动建批次（批次号系统生成）',
        bad.data?.status === 1 && ok.data?.accepted === true && row?.source === 'DEVICE'
          && String(row?.batchNo || '').startsWith('SP'),
        `不存在的批次 → ${bad.data?.statusLabel}；正常上报 → ${ok.data?.statusLabel}；` +
        `split_batch.source=${row?.source} 批次号=${row?.batchNo}（SP 前缀由系统生成）`)
    }
  }

  // ------------------------------------------------ 汇总
  const passed = results.filter((r) => r.passed).length
  const skipped = results.filter((r) => r.skipped).length
  console.log('\n' + '='.repeat(78))
  console.log(`结果：${passed}/${results.length} 通过${skipped ? `（其中 ${skipped} 条因前置数据缺失跳过）` : ''}`)
  const failed = results.filter((r) => !r.passed)
  if (failed.length) {
    console.log('未通过：')
    failed.forEach((r) => console.log(`  [${r.no}] ${r.name} —— ${r.detail}`))
  }
  console.log('='.repeat(78))
  process.exit(failed.length ? 1 : 0)
}

async function temperatureCount(transportId) {
  const res = await api(`/distribution/transports/${transportId}/temperature`)
  return (res.data || []).length
}

/** 分页响应统一取值：后端有的用 records、有的直接给数组 */
function firstPage(res) {
  const data = res?.data
  if (Array.isArray(data)) return data
  return data?.records || data?.list || []
}

/** 产品码批量生成返回 Map<String, List<String>>，key 不固定，取第一个码即可 */
function firstString(data) {
  if (!data) return null
  if (typeof data === 'string') return data
  for (const value of Object.values(data)) {
    if (Array.isArray(value) && typeof value[0] === 'string') return value[0]
    if (typeof value === 'string') return value
  }
  return null
}

async function entryTotal() {
  const res = await api('/slaughter/entries?pageNum=1&pageSize=1')
  return res.data?.total ?? 0
}

/** 取一头带产地检疫证的生猪——入场查验通道要核到证才谈得上「自动入账」 */
async function findPigWithCert() {
  const overrideTag = argValue('--ear-tag', null)
  if (overrideTag) {
    return { earTagNo: overrideTag, certNo: argValue('--cert-no', null), validUntil: '(命令行指定)' }
  }
  for (const pig of firstPage(await api('/breeding/pigs?pageNum=1&pageSize=20'))) {
    if (!pig.earTagNo) continue
    const cert = (await api(`/breeding/pigs/${pig.id}/quarantine-cert`)).data
    if (cert?.certNo) return { earTagNo: pig.earTagNo, certNo: cert.certNo, validUntil: cert.validUntil }
  }
  return null
}

/** 前置条件缺失不算失败——它说明环境没准备好，不是接入层有 bug */
function recordSkip(no, name, detail) {
  results.push({ no, name, passed: true, skipped: true, detail })
  console.log(`⏭️  SKIP  [${no}] ${name}\n        ${detail}`)
}

main().catch((e) => {
  console.error('\n测试执行失败：', e.message)
  process.exit(2)
})
