import { get, post, put } from '../request'

/** 分页请求参数 */
export interface PageParams {
  page: number
  pageSize: number
}

/** 猪只信息 */
export interface PigInfo {
  id: number
  earTag: string
  breed: string
  gender: string
  birthDate: string
  weight: number
  healthStatus: string
  farmId: number
  farmName: string
  penNumber?: string
  status: string
  blockchainTxHash?: string
  blockchainBlockNumber?: number
  createdAt: string
  updatedAt: string
}

/** 猪只列表查询参数 */
export interface PigListParams extends PageParams {
  earTag?: string
  breed?: string
  healthStatus?: string
  status?: string
  farmId?: number
}

/** 创建猪只参数 */
export interface CreatePigParams {
  earTag: string
  breed: string
  gender: string
  birthDate: string
  weight: number
  healthStatus: string
  farmId: number
  penNumber?: string
}

/** 更新猪只参数 */
export interface UpdatePigParams extends Partial<CreatePigParams> {}

/** 疫苗接种记录 */
export interface VaccineRecord {
  id: number
  pigId: number
  vaccineName: string
  vaccineType: string
  dosage: string
  vaccinationDate: string
  operator: string
  remark?: string
  blockchainTxHash?: string
  createdAt: string
}

/** 添加疫苗参数 */
export interface AddVaccineParams {
  vaccineName: string
  vaccineType: string
  dosage: string
  vaccinationDate: string
  operator: string
  remark?: string
}

/** 屠宰申请信息 */
export interface SlaughterApply {
  id: number
  pigId: number
  earTag: string
  breed: string
  weight: number
  farmName: string
  applyReason: string
  status: string
  applyTime: string
  approveTime?: string
  approver?: string
  approveRemark?: string
  blockchainTxHash?: string
  createdAt: string
  updatedAt: string
}

/** 屠宰申请查询参数 */
export interface ApplyListParams extends PageParams {
  earTag?: string
  status?: string
  farmId?: number
}

/** 提交屠宰申请参数 */
export interface ApplySlaughterParams {
  pigId: number
  applyReason: string
}

/** 审批屠宰申请参数 */
export interface ApproveApplyParams {
  status: 'approved' | 'rejected'
  approveRemark?: string
}

/** 养殖场信息 */
export interface FarmInfo {
  id: number
  name: string
  address: string
  contactPerson: string
  contactPhone: string
  scale?: number
  description?: string
  status: string
  createdAt: string
  updatedAt: string
}

/** 养殖相关 API */
const pigApi = {
  // ==================== 猪只管理 ====================

  /** 获取猪只列表 */
  list(params: PigListParams) {
    return get<{ list: PigInfo[]; total: number }>('/breeding/pigs', params)
  },

  /** 获取猪只详情 */
  detail(id: number) {
    return get<PigInfo>(`/breeding/pigs/${id}`)
  },

  /** 创建猪只 */
  create(data: CreatePigParams) {
    return post<PigInfo>('/breeding/pigs', data)
  },

  /** 更新猪只信息 */
  update(id: number, data: UpdatePigParams) {
    return put<PigInfo>(`/breeding/pigs/${id}`, data)
  },

  // ==================== 疫苗管理 ====================

  /** 为猪只添加疫苗接种记录 */
  addVaccine(pigId: number, data: AddVaccineParams) {
    return post<VaccineRecord>(`/breeding/pigs/${pigId}/vaccines`, data)
  },

  /** 获取猪只的疫苗接种记录 */
  getVaccines(pigId: number) {
    return get<VaccineRecord[]>(`/breeding/pigs/${pigId}/vaccines`)
  },

  // ==================== 屠宰申请 ====================

  /** 提交屠宰申请 */
  applySlaughter(pigId: number, data: ApplySlaughterParams) {
    return post<SlaughterApply>(`/breeding/pigs/${pigId}/apply`, data)
  },

  /** 审批屠宰申请 */
  approveApply(applyId: number, data: ApproveApplyParams) {
    return put<SlaughterApply>(`/breeding/applies/${applyId}/approve`, data)
  },

  /** 获取屠宰申请列表 */
  getApplies(params: ApplyListParams) {
    return get<{ list: SlaughterApply[]; total: number }>('/breeding/applies', params)
  },

  // ==================== 养殖场 ====================

  /** 获取养殖场列表 */
  getFarms() {
    return get<FarmInfo[]>('/breeding/farms')
  },
}

export default pigApi