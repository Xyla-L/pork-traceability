// ========== 追溯相关类型 ==========

export interface TraceResult {
  product: TraceProduct
  traceChain: TraceChain
}

export interface TraceProduct {
  name: string
  batchNo: string
  weight: string
  packageDate: string
  expireDate: string
  qrCode: string
}

export interface TraceChain {
  farm: FarmNode
  vaccines: VaccineNode[]
  quarantineCert: QuarantineCertNode
  slaughter: SlaughterNode
  splitWorkshop: SplitWorkshopNode
  transport: TransportNode
  storeReceipt: StoreReceiptNode
  blockchain: BlockchainSummary
}

export interface FarmNode {
  name: string
  licenseNo: string
  earTagNo: string
  breed: string
}

export interface VaccineNode {
  name: string
  batchNo: string
  time: string
}

export interface QuarantineCertNode {
  certNo: string
  issueOrg: string
  issueTime: string
  inspector: string
  caVerified: boolean
}

export interface SlaughterNode {
  slaughterhouse: string
  licenseNo: string
  entryTime: string
  inspectResult: string
  ractopamine: string
  stampNo: string
  veterinary: string
}

export interface SplitWorkshopNode {
  name: string
  splitTime: string
  workshopTemp: string
  productName: string
  packageType: string
}

export interface TransportNode {
  transportNo: string
  vehicleNo: string
  vehicleType: string
  departTime: string
  arriveTime: string
  temperatureLog: TempLogItem[]
  avgTemp: number
  abnormalCount: number
}

export interface TempLogItem {
  time: string
  temp: number
}

export interface StoreReceiptNode {
  storeName: string
  receiptTime: string
  receiver: string
  tempAtReceipt: string
  packageIntact: string
}

export interface BlockchainSummary {
  verified: boolean
  recordCount: number
  records: BlockchainRecordItem[]
}

export interface BlockchainRecordItem {
  type: string
  txHash: string
  blockNumber: number
}

// ========== 举报相关 ==========

export interface ComplaintReport {
  id: number
  reportNo: string
  reporterName: string
  reporterPhone: string
  targetQrCode: string
  targetBatch: string
  complaintText: string
  fileIds: string[]
  status: ComplaintStatus
  handler: string
  handleNote: string
  handleTime: string
  createTime: string
}

export type ComplaintStatus = 0 | 1 | 2 | 3
// 0=待受理, 1=处理中, 2=已办结, 3=已驳回

// ========== 验真相关 ==========

export interface VerifyResult {
  allVerified: boolean
  details: VerifyDetail[]
}

export interface VerifyDetail {
  bizType: string
  bizName: string
  localHash: string
  onChainHash: string
  matched: boolean
}
