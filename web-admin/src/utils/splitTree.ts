/**
 * 分割批次树的「页面口径」（2026-10-12 下午二次调整）：
 *
 *   胴体批次（CARCASS）
 *     └─ 每头白条一组（SPLIT_JOB，标题「白条 · 耳标号/作业号」）
 *          ├─ 已出部位（SPLIT，这头猪实报的那几块）
 *          └─ 未产出标准部位（MISSING_PART_GROUP → MISSING_PART，字典对照）
 *
 * 标准部位字典是固定的，「一头猪该切出哪些部位」因此也是固定的 —— 树上按这套固定清单
 * 对照着看：实报的进「已出」，字典里有、这头猪没报上来的进「未出」组。
 *
 * 出肉率（总/红肉）与对账结论后端仍照常计算并挂在作业上，但**页面不展示**：
 * normalizeSplitTree 会把这些字段从节点对象上剥掉（详情抽屉同样拿不到），
 * 标题/编号/重量由本模块统一给出（splitNodeTitle / splitNodeNo / splitNodeWeight），
 * 它们不会输出任何对账信息。也不做「必出缺 N」「齐」这类小徽标 —— 缺项靠组标题表达。
 * 单独抽成模块，是为了让回归脚本能用同一份实现断言页面口径（不重复写第二套）。
 */

export interface SplitNode {
  id?: number; type?: string; batchNo?: string; parentBatchId: number | null; splitLevel?: number
  productName?: string; partCode?: string; partName?: string
  jobId?: number; jobNo?: string; earTagNo?: string; parentBatchNo?: string; itemCount?: number
  weightKg?: number; packageCount?: number
  splitTime: string; workshop: string; operator: string; fileIds: string[]
  contentHash: string; txHash?: string; createTime: string; children?: SplitNode[]
  totalWeightKg?: number // 胴体批次与「白条」组是合计重量，部位是自身重量
  note?: string
  key?: string     // 对照节点没有库表 id，用后端给的 key 做稳定标识
  count?: number   // 对照分组：未产出的部位数
}

/** 树上允许出现的节点类型（对照节点是「字典提示」，不是业务记录） */
export const TREE_NODE_TYPES = ['CARCASS', 'SPLIT_JOB', 'MISSING_PART_GROUP', 'MISSING_PART', 'SPLIT']

/** 对照节点不是发生过的业务事实：不能进详情、不能看链上 */
export function isPseudoNode(node: SplitNode): boolean {
  return node.type === 'MISSING_PART_GROUP' || node.type === 'MISSING_PART'
}

/**
 * 把后端的原始树归一化成页面要渲染的形状（结构原样保留，只做两件事）：
 *   ① 统一 `data` 包装与空 children；
 *   ② 剥掉出肉率与对账字段 —— 那套信息只在后端算，页面（含详情抽屉）一律不带。
 */
export function normalizeSplitTree(node: any): SplitNode | null {
  if (!node) return null
  const data = node.data || node
  const type = node.type || data.type
  const out: any = {
    ...data,
    type,
    batchNo: data.batchNo || node.batchNo,
    id: data.id ?? node.id,
    parentBatchId: data.parentBatchId ?? null,
    splitLevel: data.splitLevel ?? 0,
    totalWeightKg: data.totalWeightKg,
    children: (Array.isArray(node.children) ? node.children : [])
      .map(normalizeSplitTree)
      .filter(Boolean),
  }
  delete out.totalYieldRate
  delete out.leanYieldRate
  delete out.checkStatus
  delete out.checkNote
  delete out.parentWeightKg
  delete out.weightBasis
  return out
}

/** 节点主标题：作业节点说「白条 · 编号」，对照分组说缺了几项，其余照实 */
export function splitNodeTitle(node: SplitNode): string {
  switch (node.type) {
    case 'SPLIT_JOB':
      return `白条 · ${node.earTagNo || node.jobNo || '未记录'}`
    case 'MISSING_PART_GROUP':
      return (node.count ?? 0) > 0
        ? `未产出标准部位 ${node.count} 项`
        : '未产出标准部位 · 无（该猪的标准部位已出齐）'
    case 'MISSING_PART':
      return node.partName || '未知部位'
    case 'SPLIT':
      return node.productName || node.batchNo || '未知部位'
    default:
      return node.batchNo || '未知批次' // CARCASS 根
  }
}

/**
 * 节点编号：部位号 / 批次号。标题里已经带了编号的节点（作业、对照分组、根）不再重复。
 */
export function splitNodeNo(node: SplitNode): string {
  if (node.type === 'SPLIT_JOB' || node.type === 'MISSING_PART_GROUP' || node.type === 'CARCASS') return ''
  if (node.type === 'MISSING_PART') return node.partCode || ''
  return node.batchNo || ''
}

/** 重量：部位是自身重量，胴体批次与「白条」组是合计；对照节点没有重量（它们没发生） */
export function splitNodeWeight(node: SplitNode): number | null {
  if (isPseudoNode(node)) return null
  const w = node.type === 'SPLIT' ? node.weightKg : (node.totalWeightKg ?? node.weightKg)
  return w == null ? null : w
}
