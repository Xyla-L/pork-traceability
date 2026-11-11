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
   * 验真：对比本地哈希与存证载体上的哈希
   * 返回 matched / localHash / onChainHash / mode / onChain —— 页面必须看 onChain，
   * 本地账本模式下的「一致」不能对外说成「区块链已验证」
   */
  verifyIntegrity(bizType: string, bizId: number) {
    return request.post('/blockchain/verify', { bizType, bizId })
  },

  /**
   * 当前存证模式：local（平台库内账本，未上链）/ evm（真实上链）
   */
  getMode() {
    return request.get('/blockchain/mode')
  },

  /**
   * 存证对账体检：失败积压、悬空存证、孤儿块
   */
  getHealth() {
    return request.get('/blockchain/health')
  },

  /**
   * 批量取存证状态（列表页一次取一页，不要逐行查）
   */
  getStatusMap(bizType: string, bizIds: number[]) {
    if (!bizIds?.length) return Promise.resolve({})
    return request.get('/blockchain/records/status-map', {
      params: { bizType, bizIds: bizIds.join(',') },
    })
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
