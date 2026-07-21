import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { QrcodeItem, RetailSale, ExpireWarning, RecallOrder } from '@/types/sales'

export const useSalesStore = defineStore('sales', () => {
  const qrcodeList = ref<QrcodeItem[]>([])
  const saleRecords = ref<RetailSale[]>([])
  const warnings = ref<ExpireWarning[]>([])
  const recallOrders = ref<RecallOrder[]>([])
  const loading = ref(false)

  async function fetchQrcodeList(params?: any) {
    loading.value = true
    try {
      const { salesApi } = await import('@/api/modules/sales')
      const res = await salesApi.getQrcodes(params)
      qrcodeList.value = res.records
    } finally {
      loading.value = false
    }
  }

  async function generateQrcodes(data: { splitBatchId: number; count: number }) {
    const { salesApi } = await import('@/api/modules/sales')
    return await salesApi.generateQrcodes(data)
  }

  async function activateProduct(id: number) {
    const { salesApi } = await import('@/api/modules/sales')
    await salesApi.activateProduct(id)
  }

  async function fetchSaleRecords(params?: any) {
    const { salesApi } = await import('@/api/modules/sales')
    const res = await salesApi.getSaleRecords(params)
    saleRecords.value = res.records
  }

  async function fetchWarnings(params?: any) {
    const { salesApi } = await import('@/api/modules/sales')
    const res = await salesApi.getWarnings(params)
    warnings.value = res.records
  }

  async function handleWarning(id: number, data: { handled: boolean; handler: string }) {
    const { salesApi } = await import('@/api/modules/sales')
    await salesApi.handleWarning(id, data)
  }

  async function createRecall(data: Partial<RecallOrder>) {
    const { salesApi } = await import('@/api/modules/sales')
    await salesApi.createRecall(data)
  }

  async function fetchRecallList(params?: any) {
    const { salesApi } = await import('@/api/modules/sales')
    const res = await salesApi.getRecalls(params)
    recallOrders.value = res.records
  }

  return {
    qrcodeList,
    saleRecords,
    warnings,
    recallOrders,
    loading,
    fetchQrcodeList,
    generateQrcodes,
    activateProduct,
    fetchSaleRecords,
    fetchWarnings,
    handleWarning,
    createRecall,
    fetchRecallList,
  }
})
