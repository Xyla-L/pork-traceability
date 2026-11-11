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
  // ========== 生猪档案（批次成员行，只读） ==========
  // 个体行由批次按耳标区间自动展开（/pigs/batches），不存在人工逐头建档入口
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

/**
 * 记录的分组视图：把逐头存储的免疫 / 检疫 / 申报按批次收成「一次操作一行」。
 *
 * 只是换一种读法——写操作（作废、出证、审批）仍走上面的逐头接口，
 * 分组视图本身不带任何写能力，免得「聚合展示」变成「聚合越权」。
 */
export const recordGroupApi = {
  vaccines(params: any) {
    return request.get('/breeding/records/vaccines', { params: toBackendParams(params) })
  },
  certs(params: any) {
    return request.get('/breeding/records/certs', { params: toBackendParams(params) })
  },
  applies(params: any) {
    return request.get('/breeding/records/applies', { params: toBackendParams(params) })
  },
}

/**
 * 生猪批次档案：养殖端档案的登记单位。
 * 现实中商品猪按批次管理（同场、同批进、同批出），人工登记一张批次表单，
 * 耳标起止号之间的每头猪自动成为批次成员——不存在逐头录入个体信息的入口。
 */
export const pigBatchApi = {
  list(params: any) {
    return request.get('/breeding/pigs/batches', { params: toBackendParams(params) })
  },
  create(data: any) {
    return request.post('/breeding/pigs/batches', data)
  },
  detail(id: number) {
    return request.get(`/breeding/pigs/batches/${id}`)
  },
  voidBatch(id: number, reason: string) {
    return request.post(`/breeding/pigs/batches/${id}/void`, { reason })
  },
}
