/**
 * 存证事件类型 → 中文名（小程序侧唯一字典）
 *
 * 口径必须与 web-admin/src/utils/chainBizType.ts 保持一致：
 * 同一个事件，监管端叫「批次拆分」、消费者端叫「批次分割」，对账时就说不清了。
 * 新事件类型只改这里 + web 那份 + 后端发布点三处。
 */

const LABELS = {
  // 全链路主流程
  PIG_BATCH: '生猪建档',
  PIG_INDIVIDUAL: '生猪个体',
  VACCINE_RECORD: '免疫记录',
  QUARANTINE_CERT: '检疫证明',
  SLAUGHTER_APPLY: '屠宰申报',
  TRANSPORT_DEPART: '发车确认',
  ENTRY_INSPECTION: '入场查验',
  RACTOPAMINE_TEST: '瘦肉精检测',
  SLAUGHTER_INSPECT: '屠宰检验',
  CARCASS_STAMP: '检疫盖章',
  CARCASS_BATCH: '胴体批次',
  SPLIT_JOB: '分割作业',
  SPLIT_BATCH: '批次拆分',
  COLD_CHAIN_TRANSPORT: '冷链运输',
  TRANSPORT_ARRIVE: '到站确认',
  STORE_RECEIPT: '门店签收',
  STOP_REJECTED: '门店拒收',
  STOP_REJECT_REVERSED: '拒收改签收',
  STOP_DISPOSAL: '拒收处置',
  RETAIL_SALE: '销售激活',
  RECALL_PLANNED: '召回发起',
  RECALL_ORDER: '召回收运单',
  RECALL_COMPLETED: '召回闭环',
  DISPOSAL_PLANNED: '处置运单生成',
  DISPOSAL_CONFIRMED: '处置签收',

  // 作废与更正
  PIG_BATCH_VOID: '生猪建档作废',
  PIG_INDIVIDUAL_VOID: '生猪个体作废',
  VACCINE_RECORD_VOID: '免疫记录作废',
  QUARANTINE_CERT_VOID: '检疫证明作废',
  ENTRY_VOID: '入场查验作废',
  RACTOPAMINE_VOID: '瘦肉精检测作废',
  SLAUGHTER_INSPECT_VOID: '屠宰检验作废',
  CARCASS_STAMP_VOID: '检疫盖章作废',
  CARCASS_BATCH_VOID: '胴体批次作废',
  SPLIT_BATCH_VOID: '批次拆分作废',
  SPLIT_BATCH_MANUAL_EDIT: '批次拆分更正',
  RETAIL_SALE_VOID: '销售作废',

  // 已停发、仅历史存证里存在的类型
  VACCINE_RECORD_MANUAL_EDIT: '免疫记录更正',
  RECALL_ORDER_PROGRESS: '召回进度上报',
  RETURN_LOAD_SCANNED: '拒收装车扫码',
  RETURN_PLANNED: '处理运单生成',
  RETURN_INTAKE: '拒收入库',
}

/** 认不出的类型原样显示代码 —— 宁可露出英文，也不编一个像样的中文名 */
export function bizTypeLabel(bizType) {
  return LABELS[bizType] || bizType || '存证记录'
}

/** 交易哈希太长，列表里截首尾；完整值仍留在数据里，需要时展开 */
export function shortHash(hash, head = 10, tail = 8) {
  const text = String(hash || '')
  if (text.length <= head + tail + 1) return text
  return `${text.slice(0, head)}…${text.slice(-tail)}`
}
