/**
 * 分割作业回归（分割线扫码称重台通道）：
 *
 *   作业口径：一次上报 = 一头白条的整份产出清单。设备只报「挂在哪个胴体批次下、
 *            切出了哪些部位、各多重、谁切的」——作业号由系统生成（SV + 当日流水），
 *            部位必须落在 meat_part 字典里（只报名称时按字典反查，查不到转人工）。
 *
 *   落库后立刻做出肉率对账（判定器 SplitYieldCheck，唯一口径）：
 *     ① 必出项：前腿 / 大排 / 后腿缺一项就转人工复核
 *     ② 总重上限：各部位合计 > 白条重量 × 105% → 疑似重复上报或虚报
 *     ③ 总重下限：报了骨/脂/皮/副产按 85% 判；只报红肉按 60% 判；整条出货（WHOLE）不判下限
 *   对账不通过**不影响入账**，只置 check_status=1 等人工复核 —— 分割事实已经发生，
 *   不入账等于把现场数据弄丢（与瘦肉精阳性记录照常入账留证同一口径）。
 *
 *   断言：
 *     1.  ★ 整作业上报（5 个部位）→ 入账，targetId 是新建的分割作业
 *     2.  ★ 单条兼容：不带 items 的老报文仍能入账（整作业是新增能力，不能把老设备踢出去）
 *     3.  ★ 部位只报名称 → 按字典反查（part_name 命中 / aliases 命中各一条）
 *     4.  ★ 部位不在字典内 → 转人工（宁缺勿猜）
 *     5.  ★ 缺必出部位 → 仍然入账（数据留证），check_status 由落库断言为 1
 *     6.  ★ 总重超过白条重量上限 → 仍然入账，check_status 由落库断言为 1
 *     7.  ★ 总重低于下限（只报红肉口径）→ 仍然入账，check_status 由落库断言为 1
 *     8.  ★ 同一作业内重复同一部位 → 拒绝（重号会污染部位台账）
 *     9.  ★ 部位重量为 0 → 拒绝（称重台没给出有效读数）
 *    10.  ★ 胴体批次不存在 → 转人工（不允许凭空造出溯源断链的作业）
 *    11.  ★ 缺操作员 → 转人工（设备不得代签）
 *    12.  ★ 部位缺重量 → 拒绝
 *    13.  ★（可选，需 bash 侧传耳标）带耳标上报 → 按这头白条的实际重量对账（weight_basis=PIG）
 *    14.  ★ 批次拆分树里出现「分割作业」层，作业节点下挂着它的部位
 *    15.  部位字典接口可读，且必出项标记在位
 *
 *   注：入账类回执按既定口径只报「已入账」这一个状态本身，业务明细（作业号 / 出肉率）不外露，
 *      所以作业号流水、出肉率、对账结论一律靠落库断言，不去断言回执文案。
 *
 *   运行：
 *     node logs/verify-split-job.mjs [胴体批次号] [该批次单头均重kg] [该批次内一头猪的耳标（可选）]
 *   产生的接入流水 ID 打印在末尾，由 bash 侧清理。
 *   （本环境 node 禁止子进程调用，DB 核验与清理必须走 bash。）
 */
const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'
const SPLIT_DEVICE = 'SPLIT-001'

const PARENT_BATCH_NO = process.argv[2] || null
const PARENT_WEIGHT = process.argv[3] ? Number(process.argv[3]) : null
const EAR_TAG_NO = process.argv[4] || null

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

async function api(path, { method = 'GET', token, body, headers = {} } = {}) {
  const h = { 'Content-Type': 'application/json', ...headers }
  if (token) h.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers: h,
    body: body ? JSON.stringify(body) : undefined,
  })
  return res.json().catch(() => null)
}

if (!PARENT_BATCH_NO || !PARENT_WEIGHT) {
  console.log('用法：node logs/verify-split-job.mjs <胴体批次号> <该批次单头均重kg> [耳标]')
  process.exit(1)
}

const login = await api('/auth/login', { method: 'POST', body: { username: 'admin', password: '123456' } })
const token = login?.data?.token || login?.data?.accessToken
check('管理员登录', !!token)
if (!token) process.exit(1)

const stagingIds = []
const track = (r) => {
  const id = r?.data?.stagingId
  if (id) stagingIds.push(id)
  return r
}

/** 代分割线称重台发一次作业上报 */
const split = (data) =>
  track(
    api('/ingest/demo/report', {
      method: 'POST',
      token,
      body: {
        deviceNo: SPLIT_DEVICE,
        data: { operator: '陈分割', workshop: '分割车间一号线', lineNo: '1号线', ...data },
      },
    }),
  )

/** 按白条重量取比例，保证各条用例的产出总量落在合理区间内（不依赖具体 pigs 的重量） */
const W = (ratio) => Number((PARENT_WEIGHT * ratio).toFixed(2))
const brief = (r) => JSON.stringify(r?.data).slice(0, 260)

console.log(`\n=== 分割作业回归（胴体批次 ${PARENT_BATCH_NO}，单头均重 ${PARENT_WEIGHT}kg）===`)

// ── 1. 整作业上报：一头白条的整份产出清单（合计 ≈ 91.7% 白条重量，应判「通过」）──
const FULL_RATIO = [
  { partCode: 'P02', ratio: 0.2357, packageCount: 20 },
  { partCode: 'P03', ratio: 0.1975, packageCount: 18 },
  { partCode: 'P04', ratio: 0.2420, packageCount: 22 },
  { partCode: 'P06', ratio: 0.1401, packageCount: 15 },
  { partCode: 'P07', ratio: 0.1019, packageCount: 10 },
]
const fullItems = FULL_RATIO.map((i) => ({ partCode: i.partCode, weightKg: W(i.ratio), packageCount: i.packageCount }))
const fullTotal = Number(fullItems.reduce((s, i) => s + i.weightKg, 0).toFixed(2))
console.log(`  整作业产出：${fullItems.length} 个部位，合计 ${fullTotal}kg（占白条 ${((fullTotal / PARENT_WEIGHT) * 100).toFixed(2)}%）`)

const r1 = await split({ parentBatchNo: PARENT_BATCH_NO, items: fullItems })
check('★ 整作业上报入账（一次上报 = 一整头猪的产出清单）', r1?.data?.accepted === true, brief(r1))
const jobId1 = r1?.data?.targetId ?? null

// ── 2. 单条兼容：不带 items 的旧报文（BATCH 通道的姊妹形态在这里也要活着）──
const r2 = await split({ parentBatchNo: PARENT_BATCH_NO, partCode: 'P20', weightKg: W(0.98), packageCount: 1 })
check('★ 单条兼容：不带 items 的旧报文仍能入账', r2?.data?.accepted === true, brief(r2))
check('★ 单条与整作业落在不同的作业上', r2?.data?.targetId != null && r2?.data?.targetId !== jobId1,
  `整作业=${jobId1} 单条=${r2?.data?.targetId}`)

// ── 3. 部位只报名称：按字典反查（part_name 与 aliases 各命中一次）──
const r3a = await split({ parentBatchNo: PARENT_BATCH_NO, productName: '二分体', weightKg: W(0.98) })
check('★ 只报标准部位名 → 按字典反查入账', r3a?.data?.accepted === true, brief(r3a))
const r3b = await split({ parentBatchNo: PARENT_BATCH_NO, productName: '带皮白条', weightKg: W(0.98) })
check('★ 只报兼容叫法（别名）→ 按字典反查入账', r3b?.data?.accepted === true, brief(r3b))

// ── 4. 部位不在字典内 → 转人工 ──
const r4 = await split({ parentBatchNo: PARENT_BATCH_NO, productName: '猪尾巴尖', weightKg: 1.0 })
check(
  '★ 部位不在字典内 → 转人工（宁缺勿猜）',
  r4?.data?.accepted !== true && r4?.data?.status === 1 && String(r4?.data?.message || '').includes('部位字典'),
  brief(r4),
)

// ── 5. 缺必出部位 → 仍然入账，只等人工复核 ──
const r5 = await split({
  parentBatchNo: PARENT_BATCH_NO,
  items: [
    { partCode: 'P06', weightKg: W(0.14) },
    { partCode: 'P07', weightKg: W(0.10) },
  ],
})
check('★ 缺必出部位 → 仍然入账（数据留证，结论交落库断言）', r5?.data?.accepted === true, brief(r5))

// ── 6. 总重超过白条重量上限 → 仍然入账 ──
const r6 = await split({
  parentBatchNo: PARENT_BATCH_NO,
  items: [
    { partCode: 'P02', weightKg: W(0.6) },
    { partCode: 'P03', weightKg: W(0.6) },
    { partCode: 'P04', weightKg: W(0.6) },
  ],
})
check('★ 总重超过上限（180%）→ 仍然入账', r6?.data?.accepted === true, brief(r6))

// ── 7. 总重低于下限 → 仍然入账 ──
const r7 = await split({
  parentBatchNo: PARENT_BATCH_NO,
  items: [
    { partCode: 'P02', weightKg: W(0.05) },
    { partCode: 'P03', weightKg: W(0.05) },
    { partCode: 'P04', weightKg: W(0.05) },
  ],
})
check('★ 总重低于下限（15%，只报红肉口径）→ 仍然入账', r7?.data?.accepted === true, brief(r7))

// ── 8. 同一作业内重复同一部位 → 拒绝 ──
const r8 = await split({
  parentBatchNo: PARENT_BATCH_NO,
  items: [
    { partCode: 'P02', weightKg: W(0.2) },
    { partCode: 'P02', weightKg: W(0.05) },
  ],
})
check('★ 同一作业内重复同一部位 → 拒绝', r8?.data?.accepted !== true && r8?.data?.status === 3, brief(r8))

// ── 9. 部位重量为 0 → 拒绝 ──
const r9 = await split({ parentBatchNo: PARENT_BATCH_NO, items: [{ partCode: 'P20', weightKg: 0 }] })
check('★ 部位重量为 0 → 拒绝', r9?.data?.accepted !== true && r9?.data?.status === 3, brief(r9))

// ── 10. 胴体批次不存在 → 转人工 ──
const r10 = await split({ parentBatchNo: 'CB-NOT-EXIST-999', items: [{ partCode: 'P20', weightKg: W(0.98) }] })
check('★ 胴体批次不存在 → 转人工', r10?.data?.accepted !== true && r10?.data?.status === 1, brief(r10))

// ── 11. 缺操作员 → 转人工（设备不得代签）──
const r11 = await split({ parentBatchNo: PARENT_BATCH_NO, operator: undefined, items: [{ partCode: 'P20', weightKg: W(0.98) }] })
check(
  '★ 缺操作员 → 转人工',
  r11?.data?.accepted !== true && r11?.data?.status === 1 && String(r11?.data?.message || '').includes('操作员'),
  brief(r11),
)

// ── 12. 部位缺重量 → 拒绝 ──
const r12 = await split({ parentBatchNo: PARENT_BATCH_NO, items: [{ partCode: 'P20' }] })
check('★ 部位缺重量 → 拒绝', r12?.data?.accepted !== true && r12?.data?.status === 3, brief(r12))

// ── 13. 带耳标 → 按这头白条的实际重量对账（weight_basis=PIG，落库断言）──
if (EAR_TAG_NO) {
  const r13 = await split({
    parentBatchNo: PARENT_BATCH_NO,
    earTagNo: EAR_TAG_NO,
    items: fullItems,
  })
  check('★ 带耳标上报 → 按该头白条精确对账', r13?.data?.accepted === true, brief(r13))
} else {
  console.log('  [SKIP] 未传耳标，跳过「按耳标精确对账」一项（第 13 节）')
}

// ── 14. 批次拆分树：胴体批次下应出现「分割作业」层，作业节点下挂着它的部位 ──
const tree = await api(`/distribution/splits/tree/${encodeURIComponent(PARENT_BATCH_NO)}`, { token })
const children = Array.isArray(tree?.data?.children) ? tree.data.children : []
const jobNodes = children.filter((c) => c.type === 'SPLIT_JOB')
check('★ 批次拆分树出现「分割作业」层', jobNodes.length > 0, `作业节点数=${jobNodes.length}，子节点总数=${children.length}`)
// 作业下除了实产部位，还有一条「未产出标准部位」对照分组——它是提示不是记录，故过滤后再看部位
const firstJobChildren = Array.isArray(jobNodes[0]?.children) ? jobNodes[0].children : []
const firstJobItems = firstJobChildren.filter((n) => n.type === 'SPLIT')
check('★ 作业节点下挂着它的部位明细', firstJobItems.length > 0,
  `作业 ${jobNodes[0]?.data?.jobNo} 下 ${firstJobItems.length} 个部位`)
check('★ 部位节点带部位编码', firstJobItems.every((n) => !!n?.data?.partCode),
  `partCodes=${firstJobItems.map((n) => n?.data?.partCode).join(',')}`)

// ── 14b. 「未产出标准部位」对照：字典固定，缺了哪块肉要在树上看得见 ──
const missingGroups = firstJobChildren.filter((n) => n.type === 'MISSING_PART_GROUP')
check('★ 作业下出现「未产出标准部位」对照分组', missingGroups.length === 1,
  `分组数=${missingGroups.length}`)
const group = missingGroups[0]
const missingLeaves = Array.isArray(group?.children) ? group.children : []
const reported = new Set(fullItems.map((i) => i.partCode))
check('★ 对照分组里都是「未产出」叶子且带编码与分类',
  missingLeaves.length > 0 && missingLeaves.every((n) => n.type === 'MISSING_PART' && !!n?.data?.partCode && !!n?.data?.category),
  `叶子数=${missingLeaves.length}`)
check('★ 对照里不含本次已上报的部位',
  missingLeaves.every((n) => !reported.has(n?.data?.partCode)),
  `已上报=${[...reported].join(',')}`)
check('★ 整条出货形态（二分体 P20）不进对照清单',
  missingLeaves.every((n) => n?.data?.partCode !== 'P20'))
check('★ 对照分组的项数与叶子数一致',
  Number(group?.data?.count) === missingLeaves.length,
  `count=${group?.data?.count} 叶子=${missingLeaves.length}`)
check('★ 必出项齐全的作业，对照分组「必出缺 0」',
  Number(group?.data?.requiredCount) === 0,
  `requiredCount=${group?.data?.requiredCount}`)
const someJobMissesRequired = jobNodes.some((j) =>
  (j.children || []).some((n) => n.type === 'MISSING_PART_GROUP' && Number(n?.data?.requiredCount) > 0))
check('★ 只报单部位的作业，对照分组标出「必出缺 N」', someJobMissesRequired)
const dynKey = jobNodes.filter((j) => (j.children || []).some((n) => n.type === 'MISSING_PART_GROUP'))
  .every((j) => j.children.filter((n) => n.type === 'MISSING_PART_GROUP').every((n) => !!n?.data?.key))
check('★ 对照节点带稳定标识 key（伪节点没有库表 id）', dynKey)

// ── 14c. 批次列表带「已分割条数」：页面据此把默认落点放在有数据的批次上 ──
const batchPage = await api('/distribution/batches?pageNum=1&pageSize=50', { token })
const batchRows = batchPage?.data?.records || []
const targetBatch = batchRows.find((b) => b.batchNo === PARENT_BATCH_NO)
check('★ 批次列表返回「已分割条数」且本批次 > 0',
  !!targetBatch && Number(targetBatch.splitCount) > 0,
  `${PARENT_BATCH_NO} splitCount=${targetBatch?.splitCount}`)
check('★ 未分割批次的已分割条数为 0（页面据此显示空态引导）',
  batchRows.some((b) => Number(b.splitCount) === 0),
  `样本=${batchRows.filter((b) => Number(b.splitCount) === 0).map((b) => b.batchNo).slice(0, 3).join(',') || '无'}`)

// ── 15. 部位字典接口 ──
const parts = await api('/distribution/meat-parts', { token })
const partList = Array.isArray(parts?.data) ? parts.data : []
check('部位字典可读且必出项在位',
  partList.length >= 15 && partList.some((p) => p.partCode === 'P02' && p.required === 1),
  `条数=${partList.length}`)

console.log(`\n=== 分割作业 HTTP 断言：通过 ${pass} / 失败 ${fail} ===`)
console.log(`# STAGING=${stagingIds.join(',')}`)
console.log(`# FULL_TOTAL=${fullTotal} FULL_RATE=${((fullTotal / PARENT_WEIGHT) * 100).toFixed(2)}`)
process.exit(fail > 0 ? 1 : 0)
