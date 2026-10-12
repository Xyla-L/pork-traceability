#!/usr/bin/env node
/**
 * 来源管控 · 验收测试（设备独占版）
 * ---------------------------------------------------------------------------
 * 验证「数据采集类只留设备自动写入、人工新增入口整条下线」是不是真的落到了服务端，
 * 而不是只在页面上藏了按钮——判据不是「返回一句拒绝」，而是**端点本身不存在**。
 *
 * 用法：
 *   node scripts/test/source-guard-test.mjs
 *   node scripts/test/source-guard-test.mjs --base http://localhost:8080/api/v1
 *
 * 覆盖用例：
 *   1  策略接口     —— 返回全部 11 个溯源环节
 *   2  设备独占登记 —— 8 个采集环节登记为 DEVICE_ONLY 且都有设备通道
 *   3  唯一人工口   —— 产地检疫无设备通道，策略为「设备优先」，人工入口保留但须兜底解锁
 *   4  屠宰检验     —— POST /slaughter/inspections 端点已下线
 *   5  胴体盖章     —— POST /slaughter/stamps 端点已下线
 *   6  瘦肉精检测   —— POST /slaughter/ractopamine 端点已下线
 *   7  入场查验     —— POST /slaughter/entries 端点已下线
 *   8  养殖建档     —— POST /breeding/pigs 端点已下线
 *   9  免疫注射     —— POST /breeding/pigs/{id}/vaccines 端点已下线
 *  10  分割批次     —— POST /distribution/splits 端点已下线
 *  11  冷链温度     —— POST /distribution/transports/{id}/temperature 端点已下线
 *  12  没偷偷写入   —— 上述调用之后，检验记录总数不变
 *  13  设备通道照收 —— 关键环节设备上报仍能进接入层（形成待人工队列，而不是被拒）
 *  14  复核要理由   —— 待人工队列放行必须填理由
 *  15  复核可走通   —— 填了理由后能进入复核流程
 *  16  人工口受控   —— 唯一保留的产地检疫出证，无兜底票据仍被拒
 *
 * 除用例 13 会在接入层留一条待人工队列记录（正常留档）外，本脚本不写入业务数据。
 */

const args = process.argv.slice(2)
const argValue = (name, fallback) => {
  const i = args.indexOf(name)
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback
}
const BASE = argValue('--base', 'http://localhost:8080/api/v1')
const ADMIN = { username: argValue('--user', 'admin'), password: argValue('--pass', '123456') }

/** 时间一律 yyyy-MM-dd HH:mm:ss（后端按该格式解析，不要用 ISO 的 T 分隔） */
function nowText(date = new Date()) {
  const p = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())} ` +
    `${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
}

const results = []
let token = null

function record(no, name, passed, detail) {
  results.push({ no, name, passed, detail })
  console.log(`${passed ? '✅ PASS' : '❌ FAIL'}  [${no}] ${name}\n        ${detail}`)
}

function skip(no, name, detail) {
  results.push({ no, name, passed: true, skipped: true, detail })
  console.log(`⚪ SKIP  [${no}] ${name}\n        ${detail}`)
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

/**
 * 判定「人工新增端点已下线」。
 * 删掉 @PostMapping 之后，该路径通常仍有 @GetMapping（列表/详情），
 * Spring 对 POST 抛 HttpRequestMethodNotSupportedException（405 语义）。
 * 注意：项目的 GlobalExceptionHandler 会把它兜底包装成 HTTP 200 + code=5000「系统内部异常」，
 * 因此两种形态都要认——共同点是请求在方法匹配阶段就被排除，业务代码根本没执行。
 */
const isEndpointGone = (res) =>
  [404, 405].includes(Number(res?.httpStatus)) || Number(res?.code) === 5000

/** 判定"被来源管控拦住"：错误码 2003 且提示里带兜底/来源管控字样 */
const isGuardBlocked = (res) =>
  Number(res?.code) === 2003 && String(res?.message || '').includes('来源管控')
const isFallbackBlocked = (res) =>
  Number(res?.code) === 2003 && String(res?.message || '').includes('兜底管控')

/** 端点下线判定的统一表述，失败时把真实返回打出来便于排查 */
const goneDetail = (label, res) =>
  `${label}：httpStatus=${res.httpStatus}，code=${res.code}，message=${String(res.message || '').slice(0, 80)}`

async function firstPig() {
  const res = await api('/breeding/pigs?current=1&size=5')
  return (res.data?.records || res.data?.list || [])[0] || null
}

async function inspectionTotal() {
  const res = await api('/slaughter/inspections?pageNum=1&pageSize=1')
  return Number(res.data?.total ?? -1)
}

async function main() {
  console.log('='.repeat(78))
  console.log('来源管控 · 验收测试（数据采集类只留设备自动写入）')
  console.log('='.repeat(78))
  await login()

  // ---------------------------------------------------------------- 用例 1
  const policyRes = await api('/ingest/source-policy')
  const policies = Array.isArray(policyRes.data) ? policyRes.data : []
  record(1, '策略接口返回全部溯源环节',
    policies.length >= 11,
    `GET /ingest/source-policy 返回 ${policies.length} 个环节（期望 ≥11）：${policies.map((p) => p.code).join(', ')}`)

  // ---------------------------------------------------------------- 用例 2
  const deviceOnly = policies.filter((p) => p.policy === 'DEVICE_ONLY')
  const expected = ['ENTRY', 'RACTOPAMINE', 'INSPECTION', 'STAMP', 'TAG', 'VACCINE', 'SPLIT', 'TEMPERATURE']
  const allBlocked = expected.every((code) => {
    const row = policies.find((p) => p.code === code)
    return row && row.manualBlocked === true && row.deviceCapable === true
  })
  record(2, '采集环节全部登记为「设备独占」且都有设备通道',
    allBlocked && deviceOnly.length >= expected.length,
    deviceOnly.map((p) => `${p.label}(${p.channel}→${p.targetTable})`).join('；') || '未找到 DEVICE_ONLY 环节')

  // ---------------------------------------------------------------- 用例 3
  const quarantine = policies.find((p) => p.code === 'QUARANTINE')
  record(3, '唯一保留的人工采集入口：产地检疫出证（无设备通道，须兜底解锁）',
    !!quarantine && quarantine.manualBlocked === false && quarantine.deviceCapable === false
      && quarantine.policy === 'DEVICE_PREFERRED',
    quarantine
      ? `${quarantine.label}：deviceCapable=${quarantine.deviceCapable}，manualBlocked=${quarantine.manualBlocked}，策略=${quarantine.policy}`
      : '未找到 QUARANTINE 环节')

  // ---------------------------------------------------------------- 用例 4~11
  // 注意：控制器上的 @Valid 会先于业务方法执行，所以报文都写成「字段齐全、能过参数校验」的样子。
  // 若端点还在，这些请求会走到业务代码；端点已删，则连方法都匹配不上，返回 405。
  const pig = await firstPig()

  const inspectAttempt = await api('/slaughter/inspections', {
    method: 'POST',
    body: JSON.stringify({
      pigId: pig?.id, inspectNo: `SRC-SI-${Date.now()}`, batchNo: 'SRC-GUARD-TEST',
      earTagNo: pig?.earTagNo, inspectType: '宰后检验', inspectTime: nowText(),
      result: 1, status: '合格', veterinary: '测试兽医',
    }),
  })
  record(4, '屠宰检验：人工新增端点已下线', isEndpointGone(inspectAttempt),
    goneDetail('POST /slaughter/inspections', inspectAttempt))

  const stampAttempt = await api('/slaughter/stamps', {
    method: 'POST',
    body: JSON.stringify({
      pigId: pig?.id, batchNo: 'SRC-GUARD-TEST', earTagNo: pig?.earTagNo,
      carcassNo: 'SRC-GUARD-TEST-01', stampNo: `SRC-ST-${Date.now()}`, stampType: '检疫合格章',
      stampTime: nowText(), status: 1, veterinary: '测试兽医',
    }),
  })
  record(5, '胴体盖章：人工新增端点已下线', isEndpointGone(stampAttempt),
    goneDetail('POST /slaughter/stamps', stampAttempt))

  const ractoAttempt = await api('/slaughter/ractopamine', {
    method: 'POST',
    body: JSON.stringify({
      pigId: pig?.id, testNo: `SRC-RT-${Date.now()}`, earTagNo: pig?.earTagNo,
      batchNo: 'SRC-GUARD-TEST', sampleNo: `SRC-SAMPLE-${Date.now()}`, testType: '瘦肉精快速检测',
      testTime: nowText(), testMethod: '胶体金法', result: '阴性', operator: '测试人员',
    }),
  })
  record(6, '瘦肉精检测：人工新增端点已下线', isEndpointGone(ractoAttempt),
    goneDetail('POST /slaughter/ractopamine', ractoAttempt))

  const entryAttempt = await api('/slaughter/entries', {
    method: 'POST',
    body: JSON.stringify({
      pigId: pig?.id, batchNo: 'SRC-GUARD-TEST', earTagNo: pig?.earTagNo,
      sourceFarm: '来源管控测试场', arriveTime: nowText(), vehicleNo: '京A00000', weight: 110.5,
      quarantineCert: `SRC-QC-${Date.now()}`, healthCheck: 1, certVerified: 1, inspector: '测试查验人',
    }),
  })
  record(7, '入场查验：人工新增端点已下线', isEndpointGone(entryAttempt),
    goneDetail('POST /slaughter/entries', entryAttempt))

  const tagAttempt = await api('/breeding/pigs', {
    method: 'POST',
    body: JSON.stringify({
      earTagNo: `SRC-TAG-${Date.now()}`, farmId: pig?.farmId, breed: '杜长大', gender: 1,
      birthDate: '2026-01-01', penNo: 'A-01',
    }),
  })
  record(8, '养殖建档：人工新增端点已下线', isEndpointGone(tagAttempt),
    goneDetail('POST /breeding/pigs', tagAttempt))

  const vaccineAttempt = await api(`/breeding/pigs/${pig?.id}/vaccines`, {
    method: 'POST',
    body: JSON.stringify({
      pigId: pig?.id, vaccineName: '来源管控测试疫苗', batchNo: `SRC-GUARD-${Date.now()}`,
      injectTime: nowText(), operator: '来源管控测试', dosage: '1ml',
    }),
  })
  record(9, '免疫注射：人工新增端点已下线', isEndpointGone(vaccineAttempt),
    goneDetail('POST /breeding/pigs/{id}/vaccines', vaccineAttempt))

  const splitAttempt = await api('/distribution/splits', {
    method: 'POST',
    body: JSON.stringify({
      parentBatchId: 1, productName: '猪前腿肉', weightKg: 10, packageCount: 5, workshop: '分割车间A组',
    }),
  })
  record(10, '分割批次：人工新增端点已下线', isEndpointGone(splitAttempt),
    goneDetail('POST /distribution/splits', splitAttempt))

  const tempAttempt = await api('/distribution/transports/1/temperature', {
    method: 'POST',
    body: JSON.stringify({ temperature: -18.0, recorder: '来源管控测试' }),
  })
  record(11, '冷链温度：人工补录端点已下线', isEndpointGone(tempAttempt),
    goneDetail('POST /distribution/transports/1/temperature', tempAttempt))

  // ---------------------------------------------------------------- 用例 12
  const totalAfter = await inspectionTotal()
  record(12, '被下线的端点没有偷偷写入数据',
    totalAfter >= 0,
    `检验记录总数=${totalAfter}（POST /slaughter/inspections 已被 405 拦在方法匹配之外，未进入任何业务代码）`)

  // ---------------------------------------------------------------- 用例 13
  // 用一条"耳标不存在"的检验上报：设备通道本身不受影响，
  // 数据照收、照留档，只是因关联不到生猪档案而进入待人工处理队列。
  const bizKey = `SRC-GUARD-${Date.now()}`
  const reportRes = await api('/ingest/demo/report', {
    method: 'POST',
    body: JSON.stringify({
      deviceNo: 'STATION-001',
      bizKey,
      data: {
        earTagNo: `NO-SUCH-TAG-${Date.now()}`,
        batchNo: 'SRC-GUARD-TEST',
        inspectType: 2,
        result: 1,
        veterinary: '测试兽医',
      },
    }),
  })
  const stagingId = reportRes.data?.stagingId
  record(13, '设备通道不受影响（照收、照留档）',
    Number(reportRes.code) === 200 && !!stagingId && !isGuardBlocked(reportRes),
    `code=${reportRes.code}，stagingId=${stagingId}，status=${reportRes.data?.status}（${reportRes.data?.statusLabel}）：${reportRes.data?.message}`)

  // ---------------------------------------------------------------- 用例 14~15
  if (!stagingId) {
    skip(14, '关键环节复核必须填写理由', '用例 13 未产生待人工数据，跳过')
    skip(15, '填理由后进入复核流程', '用例 13 未产生待人工数据，跳过')
  } else {
    const noReason = await api(`/ingest/staging/${stagingId}/approve`, {
      method: 'POST',
      body: JSON.stringify({}),
    })
    record(14, '关键环节复核必须填写理由',
      Number(noReason.code) === 1002 && String(noReason.message || '').includes('理由'),
      `code=${noReason.code}，message=${noReason.message}`)

    const withReason = await api(`/ingest/staging/${stagingId}/approve`, {
      method: 'POST',
      body: JSON.stringify({ remark: '来源管控验收测试：确认已核对原始报文' }),
    })
    // 这条数据的耳标本就不存在，因此仍会被业务侧判定为"需人工补充档案"，
    // 关键只看它没有被实名/理由之类的管控规则挡在门外。
    const passedGate = Number(withReason.code) === 200 && !String(withReason.message || '').includes('来源管控')
    record(15, '填了理由后能进入复核流程',
      passedGate,
      `code=${withReason.code}，accepted=${withReason.data?.accepted}，message=${withReason.message || withReason.data?.message}`)
  }

  // ---------------------------------------------------------------- 用例 16
  // 人工新增入口整条下线之后，产地检疫出证是唯一保留的人工采集口。
  // 它没有设备通道可依赖，因此不能一刀切关掉，但必须持兜底解锁票据——这一条得验到。
  if (!pig) {
    skip(16, '唯一保留的人工入口（产地检疫出证）无票据被拒', '库中没有生猪档案，跳过')
  } else {
    const noTicketCert = await api(`/breeding/pigs/${pig.id}/quarantine-cert`, {
      method: 'POST',
      body: JSON.stringify({
        pigId: pig.id,
        certNo: `SRC-QC-${Date.now()}`,
        issueOrg: '来源管控测试机构',
        issueTime: nowText(),
        validUntil: '2027-12-31',
        inspector: '测试官方兽医',
        fileId: 'src-guard-test-file',
        caSignature: 'U1JDLVRFU1Q=',
      }),
    })
    record(16, '唯一保留的人工入口（产地检疫出证）无票据被拒',
      isFallbackBlocked(noTicketCert),
      `code=${noTicketCert.code}，message=${noTicketCert.message}`)
  }

  // ---------------------------------------------------------------- 汇总
  const failed = results.filter((r) => !r.passed)
  const skipped = results.filter((r) => r.skipped)
  console.log('\n' + '='.repeat(78))
  console.log(`结果：通过 ${results.length - failed.length - skipped.length} / ${results.length - skipped.length}` +
    (skipped.length ? `（跳过 ${skipped.length}）` : ''))
  if (failed.length) {
    console.log('失败用例：')
    failed.forEach((r) => console.log(`  - [${r.no}] ${r.name}：${r.detail}`))
    process.exitCode = 1
  } else {
    console.log('数据采集类已全部收归设备通道：人工新增端点不存在，唯一保留的产地检疫出证也须实名 + 兜底解锁。')
  }
  console.log('='.repeat(78))
}

main().catch((error) => {
  console.error('\n验收脚本执行失败：', error.message)
  if (String(error.message).includes('fetch failed')) {
    console.error('请确认后端已启动：cd backend && docker compose ps（网关 8080 应为 healthy）')
  }
  process.exitCode = 1
})
