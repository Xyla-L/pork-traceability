import request from '@/utils/request'

export const blockchainApi = {
  /**
   * 查询区块链存证记录
   */
  getRecords(params?: { bizType?: string; bizId?: number; status?: number }) {
    return request.get('/blockchain/records', { params })
  },

  /**
   * 获取单条存证记录
   */
  getRecord(id: number) {
    return request.get(`/blockchain/records/${id}`)
  },

  /**
   * 验真：对比链上哈希
   */
  verifyIntegrity(bizType: string, bizId: number) {
    return request.post('/blockchain/verify', { bizType, bizId })
  },

  /**
   * 按批次号查询存证状态
   */
  getChainStatus(batchNo: string) {
    return request.get(`/blockchain/status/${batchNo}`)
  },

  /**
   * 获取审计日志
   */
  getAuditLogs(params?: any) {
    return request.get('/blockchain/audit-logs', { params })
  },

  /**
   * 审计统计摘要
   */
  getAuditStats() {
    return request.get('/blockchain/audit-stats')
  },
}
