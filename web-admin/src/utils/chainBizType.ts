/**
 * 存证事件类型字典（唯一来源）
 *
 * 为什么集中到一处：链上有的类型，页面必须叫得出名字。
 * 之前 TraceSearch 与 AuditLog 各存一份，补了新事件只改一处，
 * 结果审计页筛得到、追溯页显示原始代码，反之亦然。
 *
 * 新增事件类型时只改这个文件 + 后端发布点，别在页面里再写一份。
 */
import type { EpTagType } from '@/types/common'

export interface ChainBizTypeMeta {
  label: string
  color: EpTagType
  /** 已停止发布、仅历史存证里存在的类型：列表上要标出来，避免被当成当前能力 */
  legacy?: boolean
}

/** 事件类型 → 中文名 + 标签色。key 与后端 ChainTxMessage.create(bizType) 完全一致。 */
export const CHAIN_BIZ_TYPE: Record<string, ChainBizTypeMeta> = {
  // ── 全链路主流程（按业务时序） ──
  PIG_BATCH: { label: '生猪建档', color: 'success' },
  PIG_INDIVIDUAL: { label: '生猪个体', color: 'success' },
  VACCINE_RECORD: { label: '免疫记录', color: 'success' },
  QUARANTINE_CERT: { label: '检疫证明', color: 'primary' },
  SLAUGHTER_APPLY: { label: '屠宰申报', color: 'primary' },
  TRANSPORT_DEPART: { label: '发车确认', color: 'warning' },
  ENTRY_INSPECTION: { label: '入场查验', color: 'info' },
  RACTOPAMINE_TEST: { label: '瘦肉精检测', color: 'warning' },
  SLAUGHTER_INSPECT: { label: '屠宰检验', color: 'info' },
  CARCASS_STAMP: { label: '检疫盖章', color: 'primary' },
  CARCASS_BATCH: { label: '胴体批次', color: 'info' },
  SPLIT_JOB: { label: '分割作业', color: 'info' },
  SPLIT_BATCH: { label: '批次拆分', color: 'info' },
  COLD_CHAIN_TRANSPORT: { label: '冷链运输', color: 'warning' },
  TRANSPORT_ARRIVE: { label: '到站确认', color: 'warning' },
  STORE_RECEIPT: { label: '门店签收', color: 'warning' },
  STOP_REJECTED: { label: '门店拒收', color: 'danger' },
  STOP_REJECT_REVERSED: { label: '拒收改签收', color: 'warning' },
  STOP_DISPOSAL: { label: '拒收处置', color: 'info' },
  RETAIL_SALE: { label: '销售激活', color: 'success' },
  RECALL_PLANNED: { label: '召回发起', color: 'danger' },
  RECALL_ORDER: { label: '召回收运单', color: 'danger' },
  RECALL_COMPLETED: { label: '召回闭环', color: 'success' },
  DISPOSAL_PLANNED: { label: '处置运单生成', color: 'info' },
  DISPOSAL_CONFIRMED: { label: '处置签收', color: 'success' },

  // ── 作废与更正 ──
  PIG_BATCH_VOID: { label: '生猪建档作废', color: 'warning' },
  PIG_INDIVIDUAL_VOID: { label: '生猪个体作废', color: 'warning' },
  VACCINE_RECORD_VOID: { label: '免疫记录作废', color: 'warning' },
  QUARANTINE_CERT_VOID: { label: '检疫证明作废', color: 'warning' },
  ENTRY_VOID: { label: '入场查验作废', color: 'warning' },
  RACTOPAMINE_VOID: { label: '瘦肉精检测作废', color: 'warning' },
  SLAUGHTER_INSPECT_VOID: { label: '屠宰检验作废', color: 'warning' },
  CARCASS_STAMP_VOID: { label: '检疫盖章作废', color: 'warning' },
  CARCASS_BATCH_VOID: { label: '胴体批次作废', color: 'warning' },
  SPLIT_BATCH_VOID: { label: '批次拆分作废', color: 'warning' },
  SPLIT_BATCH_MANUAL_EDIT: { label: '批次拆分更正', color: 'info' },
  RETAIL_SALE_VOID: { label: '销售作废', color: 'warning' },

  // ── 历史类型：代码已不再发布，仅旧存证里存在 ──
  VACCINE_RECORD_MANUAL_EDIT: { label: '免疫记录更正', color: 'info', legacy: true },
  RECALL_ORDER_PROGRESS: { label: '召回进度上报', color: 'info', legacy: true },
  RETURN_LOAD_SCANNED: { label: '拒收装车扫码', color: 'info', legacy: true },
  RETURN_PLANNED: { label: '处理运单生成', color: 'info', legacy: true },
  RETURN_INTAKE: { label: '拒收入库', color: 'info', legacy: true },
}

/** 筛选项分组：正常类型与历史类型分开，别让历史类型混进「当前能产生的存证」 */
export const CHAIN_BIZ_TYPE_GROUPS: { name: string; types: string[] }[] = [
  {
    name: '全链路事件',
    types: [
      'PIG_BATCH', 'PIG_INDIVIDUAL', 'VACCINE_RECORD', 'QUARANTINE_CERT', 'SLAUGHTER_APPLY',
      'TRANSPORT_DEPART', 'ENTRY_INSPECTION', 'RACTOPAMINE_TEST', 'SLAUGHTER_INSPECT', 'CARCASS_STAMP',
      'CARCASS_BATCH', 'SPLIT_JOB', 'SPLIT_BATCH', 'COLD_CHAIN_TRANSPORT', 'TRANSPORT_ARRIVE',
      'STORE_RECEIPT', 'STOP_REJECTED', 'STOP_REJECT_REVERSED', 'STOP_DISPOSAL', 'RETAIL_SALE',
      'RECALL_PLANNED', 'RECALL_ORDER', 'RECALL_COMPLETED', 'DISPOSAL_PLANNED', 'DISPOSAL_CONFIRMED',
    ],
  },
  {
    name: '作废与更正',
    types: [
      'PIG_BATCH_VOID', 'PIG_INDIVIDUAL_VOID', 'VACCINE_RECORD_VOID', 'QUARANTINE_CERT_VOID', 'ENTRY_VOID',
      'RACTOPAMINE_VOID', 'SLAUGHTER_INSPECT_VOID', 'CARCASS_STAMP_VOID', 'CARCASS_BATCH_VOID',
      'SPLIT_BATCH_VOID', 'SPLIT_BATCH_MANUAL_EDIT', 'RETAIL_SALE_VOID',
    ],
  },
  {
    name: '历史遗留（已停发）',
    types: [
      'VACCINE_RECORD_MANUAL_EDIT', 'RECALL_ORDER_PROGRESS',
      'RETURN_LOAD_SCANNED', 'RETURN_PLANNED', 'RETURN_INTAKE',
    ],
  },
]

/** 认不出的类型原样显示 —— 宁可露出代码，也不许编一个像样的中文名 */
export function chainBizTypeLabel(type: string): string {
  return CHAIN_BIZ_TYPE[type]?.label || type
}

export function chainBizTypeColor(type: string): EpTagType {
  return CHAIN_BIZ_TYPE[type]?.color || 'info'
}

export function isLegacyBizType(type: string): boolean {
  return !!CHAIN_BIZ_TYPE[type]?.legacy
}
