// ========== 生猪相关类型 ==========

export interface PigIndividual {
  id: number
  earTagNo: string
  farmId: number
  farmName: string
  breed: string
  birthDate: string
  penNo: string
  source: string
  status: PigStatus
  createTime: string
  updateTime: string
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
