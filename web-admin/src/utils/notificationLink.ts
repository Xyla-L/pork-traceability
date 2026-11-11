import type { AppNotification } from '@/types/notification'

export interface NotificationTarget {
  path: string
  query?: Record<string, string>
}

/**
 * 通知点开去哪一页：按 biz_type 定，不按标题里的中文猜 ——
 * 标题会改，业务归属不会。
 * 没登记的 biz_type（如「系统维护通知」）本来就没有对应业务页面，点开只标已读。
 */
const BIZ_ROUTE: Record<string, string> = {
  complaint: '/admin/trace/complaints',
  transport: '/admin/distribution/transport',
  receipt: '/admin/distribution/receipt',
  recall: '/admin/sales/recall',
  sale: '/admin/sales/warnings',
  quarantine: '/admin/farm/quarantine',
  slaughter_apply: '/admin/farm/apply',
  ractopamine: '/admin/slaughter/ractopamine',
  blockchain: '/admin/distribution/split',
  // 存证对账异常：回存证审计页看体检明细
  chain_alert: '/admin/system/audit',
  // 召回：门店备货看召回单，冷链中心排车看拒收回运（处理计划里就是这批货）
  recall_store: '/admin/sales/recall',
  recall_center: '/admin/distribution/return',
  // 收运签收完成、召回收口：管理员回召回单看最终状态与已收回数量
  recall_done: '/admin/sales/recall',
}

/**
 * 这些页面的搜索框能按业务编号直接定位 → 跳转时把编号放进 ?keyword=。
 * 其余页面没有按编号搜的入口，塞一个搜不到的词过去，跳完还是空列表，不如不带。
 */
const KEYWORD_BIZ = new Set(['complaint', 'transport', 'receipt', 'ractopamine'])

/** 分割批次页本来就能按批次号打开（?batchNo=），上链通知直接定位到那张作业 */
const BATCH_NO_BIZ = new Set(['blockchain'])

/** 从正文里取业务编号：RP-TEST-0001 / TR-TEST-0004 / QR-PORK-TEST-0015 */
const CODE_IN_TEXT = /[A-Z]{2,6}(?:-[A-Z0-9]+){1,3}/

export function resolveNotificationTarget(item: AppNotification): NotificationTarget | null {
  const bizType = item.bizType || ''
  const path = BIZ_ROUTE[bizType]
  if (!path) return null

  const code = (item.content || '').match(CODE_IN_TEXT)?.[0] || ''
  if (!code) return { path }
  if (KEYWORD_BIZ.has(bizType)) return { path, query: { keyword: code } }
  if (BATCH_NO_BIZ.has(bizType)) return { path, query: { batchNo: code } }
  return { path }
}
