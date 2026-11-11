// ========== 生猪相关类型 ==========

export interface PigIndividual {
  id: number
  earTagNo: string
  farmId: number
  farmName: string
  /** 所属生猪批次（个体行由批次按耳标区间自动展开，无人工逐头建档入口） */
  batchId?: number
  batchNo?: string
  breed: string
  birthDate: string
  penNo: string
  source: string
  status: PigStatus
  createTime: string
  updateTime: string
}

/** 生猪批次档案：养殖端档案的登记单位（同场、同批进、同批出） */
export interface PigBatch {
  id: number
  batchNo: string
  farmId: number
  farmName: string
  breed: string
  entryDate: string
  earTagStart: string
  earTagEnd: string
  /** 批次数量（= 耳标区间大小） */
  quantity: number
  source: string
  /** 成员状态汇总 */
  onFarmCount?: number
  shippedCount?: number
  slaughteredCount?: number
  abnormalCount?: number
  createTime: string
  updateTime: string
  voidReason?: string
  voidOperator?: string
  voidTime?: string
}

export type PigStatus = 1 | 2 | 3 | 4
// 1=在养, 2=已出栏, 3=已屠宰, 4=异常

export interface VaccineRecord {
  id: number
  pigId: number
  vaccineName: string
  batchNo: string
  injectTime: string
  dosage: string
  operator: string
  fileIds: string[]
  createTime: string
}

export interface SlaughterApply {
  id: number
  pigId: number
  applyNo: string
  applyTime: string
  weightKg: number
  targetSlaughterhouse: string
  approvalStatus: ApprovalStatus
  approvalTime: string
  approver: string
  createTime: string
}

export type ApprovalStatus = 0 | 1 | 2
// 0=待审, 1=通过, 2=驳回

export interface QuarantineCert {
  id: number
  pigId: number
  certNo: string
  issueOrg: string
  issueTime: string
  validUntil: string
  inspector: string
  caSignature: string
  fileId: string
  contentHash: string
  createTime: string
}
