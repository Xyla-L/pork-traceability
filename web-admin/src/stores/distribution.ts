import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { CarcassBatch, SplitBatch, SplitTreeNode, ColdChainTransport, TemperatureLog, StoreReceipt } from '@/types/distribution'

export const useDistributionStore = defineStore('distribution', () => {
  const batchList = ref<CarcassBatch[]>([])
  const splitTree = ref<SplitTreeNode | null>(null)
  const transportList = ref<ColdChainTransport[]>([])
  const temperatureData = ref<TemperatureLog[]>([])
  const receiptList = ref<StoreReceipt[]>([])
  const loading = ref(false)

  async function fetchBatchList(params?: any) {
    loading.value = true
    try {
      const { distributionApi } = await import('@/api/modules/distribution')
      const res = await distributionApi.getBatches(params)
      batchList.value = res.records
    } finally {
      loading.value = false
    }
  }

  async function createBatch(data: Partial<CarcassBatch>) {
    const { distributionApi } = await import('@/api/modules/distribution')
    await distributionApi.createBatch(data)
  }

  async function fetchSplitTree(batchNo: string) {
    loading.value = true
    try {
      const { distributionApi } = await import('@/api/modules/distribution')
      splitTree.value = await distributionApi.getSplitTree(batchNo)
    } finally {
      loading.value = false
    }
  }

  async function createSplit(data: Partial<SplitBatch>) {
    const { distributionApi } = await import('@/api/modules/distribution')
    await distributionApi.createSplit(data)
  }

  async function fetchTransportList(params?: any) {
    const { distributionApi } = await import('@/api/modules/distribution')
    const res = await distributionApi.getTransports(params)
    transportList.value = res.records
  }

  async function createTransport(data: Partial<ColdChainTransport>) {
    const { distributionApi } = await import('@/api/modules/distribution')
    await distributionApi.createTransport(data)
  }

  async function fetchTemperatureLog(transportId: number) {
    const { distributionApi } = await import('@/api/modules/distribution')
    temperatureData.value = await distributionApi.getTemperatureLog(transportId)
  }

  async function addTemperature(transportId: number, data: { temperature: number; recorder: string }) {
    const { distributionApi } = await import('@/api/modules/distribution')
    await distributionApi.addTemperature(transportId, data)
  }

  async function createReceipt(data: Partial<StoreReceipt>) {
    const { distributionApi } = await import('@/api/modules/distribution')
    await distributionApi.createReceipt(data)
  }

  return {
    batchList,
    splitTree,
    transportList,
    temperatureData,
    receiptList,
    loading,
    fetchBatchList,
    createBatch,
    fetchSplitTree,
    createSplit,
    fetchTransportList,
    createTransport,
    fetchTemperatureLog,
    addTemperature,
    createReceipt,
  }
})
