// ========== 销售相关类型 ==========

export interface RetailSale {
  id: number
  splitBatchId: number
  productQrCode: string
  storeId: number
  storeName: string
  sellTime: string
  sellPrice: number
  sellWeightKg: number
  isActivated: number
  activateTime: string
  status: SaleStatus
  expireDate: string
  blockHash: string
  createTime: string
}

export type SaleStatus = 1 | 2 | 3 | 4
// 1=在售, 2=已售, 3=已过期, 4=已召回

export interface QrcodeItem {
  id: number
  qrCode: string
  splitBatchId: number
  batchNo: string
  status: QrcodeStatus
  expireDate: string
  createTime: string
}

export type QrcodeStatus = 0 | 1 | 2 | 3
// 0=未激活, 1=在售, 2=已售, 3=已过期

export interface ExpireWarning {
  id: number
  saleId: number
  warningLevel: 1 | 2 | 3
  // 1=临期3天, 2=临期1天, 3=已过期
  warningTime: string
  notified: number
  handled: number
  handleTime: string
  handler: string
  createTime: string
  // 关联字段
  productQrCode?: string
  productName?: string
  storeName?: string
}

export interface RecallOrder {
  id: number
  recallNo: string
  reason: string
  riskLevel: 1 | 2 | 3 // 1=一般, 2=严重, 3=紧急
  scope: RecallScope
  initiator: string
  initiateTime: string
  status: RecallStatus
  completedTime: string
  affectedCount: number
  recalledCount: number
  blockHash: string
  createTime: string
}

export interface RecallScope {
  batchIds: number[]
  storeIds: number[]
}

export type RecallStatus = 1 | 2 | 3 | 4
// 1=已发布, 2=执行中, 3=已完成, 4=已撤销
