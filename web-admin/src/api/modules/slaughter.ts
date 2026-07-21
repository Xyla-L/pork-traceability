import request from '@/utils/request'

export const slaughterApi = {
  // ========== 入场查验 ==========
  createEntryInspection(data: any) {
    return request.post('/slaughter/entry-inspect', data)
  },
  getEntryInspection(id: number) {
    return request.get(`/slaughter/entry-inspect/${id}`)
  },
  getEntryInspections(params?: any) {
    return request.get('/slaughter/entry-inspect', { params })
  },

  // ========== 屠宰检验 ==========
  createInspection(data: any) {
    return request.post('/slaughter/inspect', data)
  },
  getInspections(params?: any) {
    return request.get('/slaughter/inspects', { params })
  },

  // ========== 瘦肉精检测 ==========
  createRactopamine(data: any) {
    return request.post('/slaughter/ractopamine', data)
  },
  getRactopamineTests(params?: any) {
    return request.get('/slaughter/ractopamine', { params })
  },

  // ========== 检疫盖章 ==========
  createStamp(data: any) {
    return request.post('/slaughter/stamp', data)
  },
  getStamps(params?: any) {
    return request.get('/slaughter/stamps', { params })
  },

  // ========== 屠宰场 ==========
  getSlaughterhouses(params?: any) {
    return request.get('/slaughter/slaughterhouses', { params })
  },
}
