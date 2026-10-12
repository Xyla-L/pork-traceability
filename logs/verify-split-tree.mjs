#!/usr/bin/env node
/**
 * 分割操作页「批次树」的页面口径回归。
 *
 * 页面口径（2026-10-12 下午定稿）：
 *   胴体批次 → 每头白条一组（SPLIT_JOB，标题「白条 · 耳标号/作业号」）
 *     → 组内分「已出部位」（SPLIT）与「未产出标准部位」（MISSING_PART_GROUP / MISSING_PART）。
 *   标准部位字典固定 ⇒ 每头猪对着同一套固定清单看已出/未出。
 *   出肉率与对账结论**页面不展示**（树标签与详情抽屉都不带）；也不做「必出缺 N」「齐」小徽标。
 *
 * 这条断言的存在理由：**后端仍按作业归集、仍算对账**（业务口径，不能改），页面只是不展示。
 * 所以必须钉两侧：接口里**还在**（防后端口径被误改）、页面节点上**不在**（防「后端也不返回了」
 * 被误判成通过）。断言用页面同一份实现 `web-admin/src/utils/splitTree.ts` —— 不写第二套口径。
 *
 * 运行：node --experimental-strip-types logs/verify-split-tree.mjs
 */
import { normalizeSplitTree, isPseudoNode, splitNodeTitle, splitNodeNo, splitNodeWeight, TREE_NODE_TYPES } from '../web-admin/src/utils/splitTree.ts'

const BASE = process.env.BASE_URL || 'http://localhost:8080/api/v1'

let pass = 0
let fail = 0
function check(name, ok, detail = '') {
  if (ok) { pass++; console.log(`  [PASS] ${name}`) }
  else { fail++; console.log(`  [FAIL] ${name}${detail ? ' —— ' + detail : ''}`) }
}

async function api(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  })
  let body = null
  try { body = await res.json() } catch { /* 空响应 */ }
  return { httpStatus: res.status, ...(body || {}) }
}

const listOf = (res) => {
  const d = res?.data
  if (Array.isArray(d)) return d
  return d?.records || d?.list || []
}

/** 把原始树铺平 */
function flat(node, out = []) {
  if (!node) return out
  out.push(node)
  for (const c of node.children || []) flat(c, out)
  return out
}

const login = await api('/auth/login', {
  method: 'POST',
  body: JSON.stringify({ username: 'admin', password: '123456' }),
})
const token = login?.data?.accessToken || login?.data?.token
check('管理员登录', !!token, JSON.stringify(login).slice(0, 160))
if (!token) process.exit(1)
const auth = { Authorization: `Bearer ${token}` }

console.log('\n== 1) 找一个「真的带分割作业」的胴体批次（否则下面全是空断言）==')
const batches = await api('/distribution/batches?current=1&size=100', { headers: auth })
const withSplits = listOf(batches).filter((b) => Number(b.splitCount ?? 0) > 0)
check('存在已发生分割的胴体批次', withSplits.length > 0, `候选 ${withSplits.length} 个`)

let sample = null
let rawTree = null
for (const b of withSplits) {
  const res = await api(`/distribution/splits/tree/${encodeURIComponent(b.batchNo)}`, { headers: auth })
  const tree = res?.data || res
  if (!tree) continue
  if (flat(tree).some((n) => (n.type || null) === 'SPLIT_JOB')) { sample = b.batchNo; rawTree = tree; break }
}
check('找到一条接口里含「白条（分割作业）」节点的树', !!rawTree, '没有任何批次返回 SPLIT_JOB —— 后端归集可能被改坏了')
if (!rawTree) process.exit(1)
console.log(`  [INFO] 样本批次 = ${sample}`)

console.log('\n== 2) 后端口径没被改：白条组 / 对照组 / 出肉率都还在接口里 ==')
const rawFlat = flat(rawTree)
for (const t of TREE_NODE_TYPES) {
  check(`接口里仍有 ${t} 节点`, rawFlat.some((n) => (n.type || null) === t), '接口里已经没有这个节点了')
}
const jobRaw = rawFlat.find((n) => (n.type || null) === 'SPLIT_JOB')
const jobData = jobRaw?.data || jobRaw || {}
check('接口里作业节点仍挂着出肉率（后端照常算，页面不展示）',
  jobData.totalYieldRate != null, `totalYieldRate=${jobData.totalYieldRate}`)
const rawMissing = rawFlat.filter((n) => (n.type || null) === 'MISSING_PART')
check('接口里有未产出标准部位叶子', rawMissing.length > 0, `MISSING_PART ${rawMissing.length} 个`)

console.log('\n== 3) 页面口径：结构保留（分层），对账信息剥掉 ==')
const pageTree = normalizeSplitTree(rawTree)
check('归一化返回了树', !!pageTree)
const pageFlat = flat(pageTree)
check('页面树节点数与原树一致（只剥字段，不砍节点）',
  pageFlat.length === rawFlat.length, `页面 ${pageFlat.length} / 接口 ${rawFlat.length}`)
const pageTypes = [...new Set(pageFlat.map((n) => n.type || null))].sort()
console.log(`  [INFO] 页面树节点类型 = ${JSON.stringify(pageTypes)}`)
check('页面树保留全部五类节点（分层结构在）',
  TREE_NODE_TYPES.every((t) => pageTypes.includes(t)), JSON.stringify(pageTypes))

check('页面节点上没有 totalYieldRate', pageFlat.every((n) => n.totalYieldRate == null), '仍有节点带出肉率')
check('页面节点上没有 leanYieldRate', pageFlat.every((n) => n.leanYieldRate == null), '仍有节点带红肉出肉率')
check('页面节点上没有 checkStatus / checkNote',
  pageFlat.every((n) => n.checkStatus == null && n.checkNote == null), '仍有节点带对账结论')
check('页面节点上没有 parentWeightKg（对账基准）',
  pageFlat.every((n) => n.parentWeightKg == null), '仍有节点带白条对账基准')

console.log('\n== 4) 每头白条一组：标题「白条 · 编号」，组内已出/未出 ==')
const pageJobs = pageFlat.filter((n) => n.type === 'SPLIT_JOB')
check('页面树里有白条组节点', pageJobs.length > 0)
check('白条组标题按「白条 · 编号」渲染',
  pageJobs.every((n) => splitNodeTitle(n).startsWith('白条 · ')),
  JSON.stringify(pageJobs.map((n) => splitNodeTitle(n))))
const jobNode = pageJobs[0]
const jobChildren = jobNode?.children || []
const produced = jobChildren.filter((n) => n.type === 'SPLIT')
const missingGroup = jobChildren.find((n) => n.type === 'MISSING_PART_GROUP')
check('白条组内同时有「已出部位」与「未产出对照」', produced.length > 0 && !!missingGroup,
  `已出 ${produced.length} / 对照组 ${missingGroup ? '有' : '无'}`)
check('对照组标题为「未产出标准部位 N 项」且 N 与叶子数一致',
  !!missingGroup && splitNodeTitle(missingGroup) === `未产出标准部位 ${(missingGroup.children || []).length} 项`,
  splitNodeTitle(missingGroup || {}))
check('未产出叶子数 = 作业节点切出数与字典的差（12 项：字典 17 - 实报 5）',
  (missingGroup?.children || []).length === rawMissing.length,
  `页面 ${(missingGroup?.children || []).length} / 接口 ${rawMissing.length}`)

console.log('\n== 5) 标题 / 编号 / 重量的口径 ==')
check('根节点标题就是批次号（编号位不再重复一遍）',
  splitNodeTitle(pageTree) === pageTree.batchNo && splitNodeNo(pageTree) === '',
  `${splitNodeTitle(pageTree)} / no="${splitNodeNo(pageTree)}"`)
check('部位节点标题是部位名、编号是部位号（SP 开头）',
  produced.every((n) => splitNodeTitle(n) && splitNodeNo(n).startsWith('SP')),
  JSON.stringify(produced.map((n) => `${splitNodeTitle(n)}(${splitNodeNo(n)})`)))
check('未产出叶子标题是部位名、编号是字典部位码（P 开头）',
  (missingGroup?.children || []).every((n) => splitNodeTitle(n) && splitNodeNo(n).startsWith('P')),
  JSON.stringify((missingGroup?.children || []).map((n) => splitNodeNo(n))))
check('白条组编号位为空（编号已在标题里）', splitNodeNo(jobNode) === '')
check('对照组与叶子都是「伪节点」（不能进详情/链上）',
  isPseudoNode(missingGroup) && (missingGroup?.children || []).every((n) => isPseudoNode(n)))
check('伪节点没有重量', splitNodeWeight(missingGroup) == null
  && (missingGroup?.children || []).every((n) => splitNodeWeight(n) == null))
check('部位节点有自身重量', produced.every((n) => splitNodeWeight(n) != null),
  JSON.stringify(produced.map((n) => splitNodeWeight(n))))

console.log('\n== 6) 空树不炸 ==')
const emptyLike = normalizeSplitTree({ type: 'CARCASS', data: { batchNo: 'X', id: 1 }, children: [] })
check('没有子节点时返回根节点且 children 为空',
  emptyLike && emptyLike.type === 'CARCASS' && Array.isArray(emptyLike.children) && emptyLike.children.length === 0)
check('null 输入返回 null', normalizeSplitTree(null) === null)
check('空对照组标题走「已出齐」分支',
  splitNodeTitle({ type: 'MISSING_PART_GROUP', count: 0 }) === '未产出标准部位 · 无（该猪的标准部位已出齐）',
  splitNodeTitle({ type: 'MISSING_PART_GROUP', count: 0 }))

console.log(`\n结果：通过 ${pass} 项，失败 ${fail} 项`)
process.exit(fail === 0 ? 0 : 1)
