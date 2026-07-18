import request from '@/utils/request'

export const pigApi = {
  // ========== 生猪档案 ==========
  list(params: any) {
    return request.get('/breeding/pigs', { params })
  },
  detail(id: number) {
    return request.get(`/breeding/pigs/${id}`)
  },
  create(data: any) {
    return request.post('/breeding/pigs', data)
  },
  update(id: number, data: any) {
    return request.put(`/breeding/pigs/${id}`, data)
  },

  // ========== 疫苗记录 ==========
  addVaccine(pigId: number, data: any) {
    return request.post(`/breeding/pigs/${pigId}/vaccines`, data)
  },
  getVaccines(pigId: number) {
    return request.get(`/breeding/pigs/${pigId}/vaccines`)
  },

  // ========== 出栏申报 ==========
  applySlaughter(pigId: number, data: any) {
    return request.post(`/breeding/pigs/${pigId}/apply`, data)
  },
  getApplies(params?: any) {
    return request.get('/breeding/applies', { params })
  },
  approveApply(applyId: number, data: { approved: boolean; comment?: string }) {
    return request.put(`/breeding/applies/${applyId}/approve`, data)
  },

  // ========== 产地检疫证 ==========
  uploadQuarantineCert(pigId: number, data: any) {
    return request.post(`/breeding/pigs/${pigId}/quarantine-cert`, data)
  },
  getQuarantineCert(pigId: number) {
    return request.get(`/breeding/pigs/${pigId}/quarantine-cert`)
  },

  // ========== 养殖场 ==========
  createFarm(data: any) {
    return request.post('/breeding/farms', data)
  },
  getFarms(params?: any) {
    return request.get('/breeding/farms', { params })
  },
  getFarmDetail(id: number) {
    return request.get(`/breeding/farms/${id}`)
  },
  updateFarm(id: number, data: any) {
    return request.put(`/breeding/farms/${id}`, data)
  },
}
