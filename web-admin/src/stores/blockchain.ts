import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { BlockchainRecord, BlockchainStatus } from '@/types/common'

export const useBlockchainStore = defineStore('blockchain', () => {
  const chainRecords = ref<BlockchainRecord[]>([])
  const auditLogs = ref<any[]>([])
  const verifyStatusCache = ref<Record<string, BlockchainStatus>>({})
  const loading = ref(false)

  async function fetchChainRecords(params?: { bizType?: string; bizId?: number }) {
    loading.value = true
    try {
      const { blockchainApi } = await import('@/api/modules/blockchain')
      const res = await blockchainApi.getRecords(params)
      chainRecords.value = res.records
    } finally {
      loading.value = false
    }
  }

  async function verifyIntegrity(bizType: string, bizId: number) {
    const { blockchainApi } = await import('@/api/modules/blockchain')
    const result = await blockchainApi.verifyIntegrity(bizType, bizId)
    const key = `${bizType}_${bizId}`
    verifyStatusCache.value[key] = result.verified ? 'confirmed' : 'failed'
    return result
  }

  async function fetchAuditLogs(params?: any) {
    loading.value = true
    try {
      const { blockchainApi } = await import('@/api/modules/blockchain')
      const res = await blockchainApi.getAuditLogs(params)
      auditLogs.value = res.records
    } finally {
      loading.value = false
    }
  }

  function getCachedStatus(bizType: string, bizId: number): BlockchainStatus {
    const key = `${bizType}_${bizId}`
    return verifyStatusCache.value[key] || 'none'
  }

  return {
    chainRecords,
    auditLogs,
    verifyStatusCache,
    loading,
    fetchChainRecords,
    verifyIntegrity,
    fetchAuditLogs,
    getCachedStatus,
  }
})
