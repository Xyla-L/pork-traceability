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

  /* 人工新建分割 action 已删除（createSplit）：分割批次以扫码称重台自动创建为主路径。 */

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

  /* 人工温度打卡 action 已删除（addTemperature）：冷链温度由车载温控设备自动上报。 */

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
    fetchTransportList,
    createTransport,
    fetchTemperatureLog,
    createReceipt,
  }
})
