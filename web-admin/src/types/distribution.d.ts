// ========== 分割配送相关类型 ==========

export interface CarcassBatch {
  id: number
  batchNo: string
  pigIds: number[]
  totalWeightKg: number
  createTime: string
  operator: string
}

export interface SplitBatch {
  id: number
  batchNo: string
  parentBatchId: number
  splitLevel: number
  productName: string
  weightKg: number
  packageCount: number
  splitTime: string
  workshop: string
  operator: string
  fileIds: string[]
  contentHash: string
  createTime: string
  children?: SplitBatch[]
}

export interface SplitTreeNode extends SplitBatch {
  children: SplitTreeNode[]
  hasBlockchain: boolean
  txHash?: string
}

export interface ColdChainTransport {
  id: number
  transportNo: string
  splitBatchId: number
  vehicleNo: string
  vehicleType: string
  driverName: string
  driverPhone: string
  origin: string
  destination: string
  departTime: string
  arriveTime: string
  status: TransportStatus
  createTime: string
}

export type TransportStatus = 1 | 2 | 3 | 4
// 1=待发, 2=在途, 3=已送达, 4=异常

export interface TemperatureLog {
  id: number
  transportId: number
  recordTime: string
  temperature: number
  isAbnormal: number
  recorder: string
  createTime: string
}

export interface StoreReceipt {
  id: number
  transportId: number
  storeId: number
  storeName: string
  receiptTime: string
  receiver: string
  qtyCheck: number
  tempCheck: number
  receiptPhoto: string[]
  eSignature: string
  contentHash: string
  createTime: string
}
