import request from '@/utils/request'

// ========== 屠宰管理接口 ==========
// 路径以 Web 管理端页面实际调用为准（entries / inspections / ractopamine / stamps）

export const slaughterApi = {
  // ========== 入场查验 ==========
  getEntries(params?: any) {
    return request.get('/slaughter/entries', { params })
  },
  createEntry(data: any) {
    return request.post('/slaughter/entries', data)
  },
  updateEntry(id: number, data: any) {
    return request.put(`/slaughter/entries/${id}`, data)
  },

  // ========== 屠宰检验 ==========
  getInspections(params?: any) {
    return request.get('/slaughter/inspections', { params })
  },
  createInspection(data: any) {
    return request.post('/slaughter/inspections', data)
  },
  updateInspection(id: number, data: any) {
    return request.put(`/slaughter/inspections/${id}`, data)
  },

  // ========== 瘦肉精检测 ==========
  getRactopamine(params?: any) {
    return request.get('/slaughter/ractopamine', { params })
  },
  createRactopamine(data: any) {
    return request.post('/slaughter/ractopamine', data)
  },
  updateRactopamine(id: number, data: any) {
    return request.put(`/slaughter/ractopamine/${id}`, data)
  },

  // ========== 检疫盖章 ==========
  getStamps(params?: any) {
    return request.get('/slaughter/stamps', { params })
  },
  createStamp(data: any) {
    return request.post('/slaughter/stamps', data)
  },
  updateStamp(id: number, data: any) {
    return request.put(`/slaughter/stamps/${id}`, data)
  },

  // ========== 屠宰场 ==========
  getSlaughterhouses(params?: any) {
    return request.get('/slaughter/slaughterhouses', { params })
  },
}
