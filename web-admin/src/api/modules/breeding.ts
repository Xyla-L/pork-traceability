import request from '@/utils/request'

/**
 * breeding 模块后端分页参数使用 current/size，
 * 前端统一使用 pageNum/pageSize，此处做字段映射。
 */
function toBackendParams(params: any = {}) {
  if (!params || typeof params !== 'object') return params
  const mapped = { ...params }
  if (mapped.pageNum !== undefined) {
    mapped.current = mapped.pageNum
    delete mapped.pageNum
  }
  if (mapped.pageSize !== undefined) {
    mapped.size = mapped.pageSize
    delete mapped.pageSize
  }
  return mapped
}

export const pigApi = {
  // ========== 生猪档案 ==========
  // 人工建档接口已删除：耳标佩戴由读写器设备通道自动建档（/breeding/internal/ingest/tag）
  list(params: any) {
    return request.get('/breeding/pigs', { params: toBackendParams(params) })
  },
  detail(id: number) {
    return request.get(`/breeding/pigs/${id}`)
  },
  update(id: number, data: any) {
    return request.put(`/breeding/pigs/${id}`, data)
  },

  // ========== 疫苗记录 ==========
  // 人工录入接口已删除：免疫注射由智能连续注射器设备通道自动记录（/breeding/internal/ingest/vaccine）
  getVaccines(pigId: number) {
    return request.get(`/breeding/pigs/${pigId}/vaccines`)
  },

  // ========== 出栏申报 ==========
  applySlaughter(pigId: number, data: any) {
    return request.post(`/breeding/pigs/${pigId}/apply`, data)
  },
  getApplies(params?: any) {
    return request.get('/breeding/applies', { params: toBackendParams(params) })
  },
  approveApply(applyId: number, data: { approved: boolean; comment?: string }) {
    return request.put(`/breeding/applies/${applyId}/approve`, data)
  },

  // ========== 产地检疫证 ==========
  uploadQuarantineCert(pigId: number, data: any) {
    // 后端 QuarantineCertDTO.pigId 为 @NotNull（历史契约要求请求体带 pigId），
    // 路径里的 pigId 由控制器覆盖。这里把 pigId 一并放进 body，
    // 否则会在参数校验阶段直接报「生猪ID不能为空」，根本走不到来源管控。
    return request.post(`/breeding/pigs/${pigId}/quarantine-cert`, { ...data, pigId })
  },
  // 无检疫证属正常态（尚未出证），默认静默失败：由调用方按 null 处理，
  // 避免「列表逐头探证」或打开出证弹窗时刷出满屏「暂无检疫证」错误提示。
  getQuarantineCert(pigId: number, silent = true) {
    return request.get(`/breeding/pigs/${pigId}/quarantine-cert`, { silentError: silent })
  },

  // ========== 养殖场 ==========
  createFarm(data: any) {
    return request.post('/breeding/farms', data)
  },
  getFarms(params?: any) {
    return request.get('/breeding/farms', { params: toBackendParams(params) })
  },
  getFarmDetail(id: number) {
    return request.get(`/breeding/farms/${id}`)
  },
  updateFarm(id: number, data: any) {
    return request.put(`/breeding/farms/${id}`, data)
  },

  // ========== 作废（原「删除」入口已移除）==========
  // 生猪档案是追溯链的起点，删掉一条记录，以它为起点的事件全成悬空哈希，因此只允许作废。
  voidPig(id: number, reason: string) {
    return request.post(`/breeding/pigs/${id}/void`, { reason })
  },
  voidVaccine(pigId: number, id: number, reason: string) {
    return request.post(`/breeding/pigs/${pigId}/vaccines/${id}/void`, { reason })
  },
  voidQuarantineCert(pigId: number, id: number, reason: string) {
    return request.post(`/breeding/pigs/${pigId}/quarantine-cert/${id}/void`, { reason })
  },
}
