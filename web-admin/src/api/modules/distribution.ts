import request from '@/utils/request'

export const distributionApi = {
  // ========== 胴体批次 ==========
  createBatch(data: any) {
    return request.post('/distribution/batches', data)
  },
  getBatches(params?: any) {
    return request.get('/distribution/batches', { params })
  },
  updateBatch(id: number, data: any) {
    return request.put(`/distribution/batches/${id}`, data)
  },
  deleteBatch(id: number) {
    return request.delete(`/distribution/batches/${id}`)
  },
  /** 生猪归批占用映射：[{pigId, batchId, batchNo}] */
  getPigOccupancy() {
    return request.get('/distribution/batches/pig-occupancy')
  },

  // ========== 分割操作 ==========
  createSplit(data: any) {
    return request.post('/distribution/splits', data)
  },
  getSplitDetail(id: number) {
    return request.get(`/distribution/splits/${id}`)
  },
  updateSplit(id: number, data: any) {
    return request.put(`/distribution/splits/${id}`, data)
  },
  deleteSplit(id: number) {
    return request.delete(`/distribution/splits/${id}`)
  },
  getSplitTree(batchNo: string) {
    return request.get(`/distribution/splits/tree/${batchNo}`)
  },
  getSplits(params?: any) {
    return request.get('/distribution/splits', { params })
  },

  // ========== 冷链运输 ==========
  createTransport(data: any) {
    return request.post('/distribution/transports', data)
  },
  getTransports(params?: any) {
    return request.get('/distribution/transports', { params })
  },
  getTransportDetail(id: number) {
    return request.get(`/distribution/transports/${id}`)
  },
  updateTransport(id: number, data: any) {
    return request.put(`/distribution/transports/${id}`, data)
  },
  deleteTransport(id: number) {
    return request.delete(`/distribution/transports/${id}`)
  },
  departTransport(id: number) {
    return request.put(`/distribution/transports/${id}/depart`)
  },
  addTemperature(id: number, data: { temperature: number; recorder: string }) {
    return request.post(`/distribution/transports/${id}/temperature`, data)
  },
  getTemperatureLog(transportId: number) {
    return request.get(`/distribution/transports/${transportId}/temperature`)
  },
  arriveTransport(id: number) {
    return request.put(`/distribution/transports/${id}/arrive`)
  },

  // ========== 门店签收 ==========
  createReceipt(data: any) {
    return request.post('/distribution/receipts', data)
  },
  getReceipts(params?: any) {
    return request.get('/distribution/receipts', { params })
  },
  deleteReceipt(id: number) {
    return request.delete(`/distribution/receipts/${id}`)
  },

  // ========== 门店 ==========
  getStores(params?: any) {
    return request.get('/distribution/stores', { params })
  },
}
