import request from '@/utils/request'

export const salesApi = {
  // ========== 二维码 ==========
  generateQrcodes(data: { splitBatchId: number; count: number }) {
    return request.post('/sales/qrcodes/batch', data)
  },
  getQrcodes(params?: any) {
    return request.get('/sales/qrcodes', { params })
  },

  // ========== 销售记录 ==========
  activateProduct(id: number) {
    return request.put(`/sales/products/${id}/activate`)
  },
  createSaleRecord(data: any) {
    return request.post('/sales/records', data)
  },
  getSaleRecords(params?: any) {
    return request.get('/sales/records', { params })
  },

  // ========== 过期预警 ==========
  getWarnings(params?: any) {
    return request.get('/sales/warnings', { params })
  },
  handleWarning(id: number, data: { handled: boolean; handler: string }) {
    return request.put(`/sales/warnings/${id}/handle`, data)
  },

  // ========== 召回管理 ==========
  createRecall(data: any) {
    return request.post('/sales/recalls', data)
  },
  getRecalls(params?: any) {
    return request.get('/sales/recalls', { params })
  },
  getRecallDetail(id: number) {
    return request.get(`/sales/recalls/${id}`)
  },
  updateRecallStatus(id: number, data: { status: number }) {
    return request.put(`/sales/recalls/${id}/status`, data)
  },
}
