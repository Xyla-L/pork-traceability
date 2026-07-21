// ========== 屠宰相关类型 ==========

export interface EntryInspection {
  id: number
  pigId: number
  arriveTime: string
  vehicleNo: string
  healthCheck: number // 1=通过, 0=异常
  certVerified: number // 1=通过, 0=异常
  abnormalNote: string
  inspector: string
  createTime: string
}

export interface SlaughterInspection {
  id: number
  pigId: number
  inspectType: 1 | 2 // 1=宰前, 2=宰后
  inspectTime: string
  organCheck: OrganCheckResult
  result: number // 1=合格, 0=不合格
  issueDesc: string
  veterinary: string
  eSignature: string
  fileIds: string[]
  contentHash: string
  createTime: string
}

export interface OrganCheckResult {
  heart: string
  liver: string
  lung: string
  kidney: string
  lymphNode: string
  [key: string]: string
}

export interface RactopamineTest {
  id: number
  pigId: number
  testTime: string
  testMethod: string
  samplePart: string
  result: number // 1=阴性, 0=阳性
  operator: string
  fileIds: string[]
  createTime: string
}

export interface CarcassStamp {
  id: number
  pigId: number
  stampNo: string
  stampTime: string
  stampPosition: string
  veterinary: string
  eSignature: string
  createTime: string
}
