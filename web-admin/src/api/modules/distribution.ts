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
  /** 封批：此后分拣台不再把当日白条并入本批次（换班/换圈时人工切批；达到头数上限会自动封） */
  sealBatch(id: number) {
    return request.post(`/distribution/batches/${id}/seal`)
  },
  /** 生猪归批占用映射：[{pigId, batchId, batchNo}] */
  getPigOccupancy() {
    return request.get('/distribution/batches/pig-occupancy')
  },

  // ========== 分割操作 ==========
  // 人工创建分割批次接口已删除：分割批次由分割线扫码称重台设备通道自动创建
  getSplitDetail(id: number) {
    return request.get(`/distribution/splits/${id}`)
  },
  updateSplit(id: number, data: any) {
    return request.put(`/distribution/splits/${id}`, data)
  },
  // 作废（原删除）：批次号是下游门店与追溯查询的引用键，删掉记录会让所有引用悬空
  voidSplit(id: number, reason: string) {
    return request.post(`/distribution/splits/${id}/void`, { reason })
  },
  getSplitTree(batchNo: string) {
    return request.get(`/distribution/splits/tree/${batchNo}`)
  },
  getSplits(params?: any) {
    return request.get('/distribution/splits', { params })
  },
  /** 分割作业列表：作业 = 一头白条的整份产出清单（部位是它的明细） */
  getSplitJobs(params?: any) {
    return request.get('/distribution/split-jobs', { params })
  },
  /** 部位字典：设备上报只认这里的部位编码，前端下拉也取自这里 */
  getMeatParts() {
    return request.get('/distribution/meat-parts')
  },
  /**
   * 分割车间监控快照：某台相机最近的若干张现场照片（默认最近 10 张）。
   * 只读展示，照片不参与任何业务判定。
   */
  getSplitSnapshots(params?: any) {
    return request.get('/distribution/split-snapshots', { params })
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
  /**
   * 途经站点清单（一车多店）：按卸货门店聚合装载明细而来，含每站的批次/重量与签收状态。
   * 站点顺序就是装车扫码顺序，第一站即运单的主目的地。
   */
  getTransportStops(transportId: number) {
    return request.get(`/distribution/transports/${transportId}/stops`)
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
  // 人工补录温度接口已删除：冷链温度由车载/冷库探头设备通道自动采集
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

  // ========== 发货计划（运单的上游决策单） ==========
  /** 待发运的分割批次：未被任何运单装载过，建计划时勾选它 */
  getPendingSplits(params?: any) {
    return request.get('/distribution/splits/pending', { params })
  },
  createPlan(data: any) {
    return request.post('/distribution/delivery-plans', data)
  },
  getPlans(params?: any) {
    return request.get('/distribution/delivery-plans', { params })
  },
  /** 计划详情：含明细（这一车要装的批次清单） */
  getPlanDetail(id: number) {
    return request.get(`/distribution/delivery-plans/${id}`)
  },
  updatePlan(id: number, data: any) {
    return request.put(`/distribution/delivery-plans/${id}`, data)
  },
  cancelPlan(id: number, reason?: string) {
    return request.post(`/distribution/delivery-plans/${id}/cancel`, { reason: reason || '计划取消' })
  },
  /**
   * 生成运单：目的地/计划时间/车辆司机全部从计划带出。
   * splitBatchIds 传空 = 把剩余未发运的货全部发走；传子集 = 分车，只发这几批。
   */
  generateTransportFromPlan(id: number, splitBatchIds?: number[]) {
    return request.post(
      `/distribution/delivery-plans/${id}/generate`,
      splitBatchIds && splitBatchIds.length ? { splitBatchIds } : {},
    )
  },
}
