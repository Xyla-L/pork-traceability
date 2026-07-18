import request from '@/utils/request'

export const traceApi = {
  // ========== 追溯查询 ==========
  search(keyword: string) {
    return request.get('/trace/search', { params: { keyword } })
  },
  getUpstream(batchNo: string) {
    return request.get(`/trace/upstream/${batchNo}`)
  },
  getDownstream(batchNo: string) {
    return request.get(`/trace/downstream/${batchNo}`)
  },
  getFullTrace(batchNo: string) {
    return request.get(`/trace/full/${batchNo}`)
  },

  // ========== 一键验真 ==========
  verify(qrCode: string) {
    return request.get(`/trace/verify/${qrCode}`)
  },

  // ========== 举报管理 ==========
  submitComplaint(data: any) {
    return request.post('/trace/complaints', data)
  },
  getComplaints(params?: any) {
    return request.get('/trace/complaints', { params })
  },
  handleComplaint(id: number, data: { status: number; handleNote?: string }) {
    return request.put(`/trace/complaints/${id}/handle`, data)
  },
}

// ========== 消费者接口 ==========
export const consumerApi = {
  scanProduct(qrCode: string) {
    return request.get(`/consumer/scan/${qrCode}`)
  },
  getSafeBuy(qrCode: string) {
    return request.get(`/consumer/safe-buy/${qrCode}`)
  },
  verifyProduct(qrCode: string) {
    return request.post('/consumer/verify', { qrCode })
  },
}
