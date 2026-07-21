import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { TraceResult, VerifyResult, ComplaintReport } from '@/types/trace'

export const useTraceStore = defineStore('trace', () => {
  const traceResult = ref<TraceResult | null>(null)
  const verifyResult = ref<VerifyResult | null>(null)
  const complaintList = ref<ComplaintReport[]>([])
  const loading = ref(false)
  const verifying = ref(false)

  async function search(keyword: string) {
    loading.value = true
    try {
      const { traceApi } = await import('@/api/modules/trace')
      traceResult.value = await traceApi.search(keyword)
    } finally {
      loading.value = false
    }
  }

  async function fetchUpstream(batchNo: string) {
    const { traceApi } = await import('@/api/modules/trace')
    return await traceApi.getUpstream(batchNo)
  }

  async function fetchDownstream(batchNo: string) {
    const { traceApi } = await import('@/api/modules/trace')
    return await traceApi.getDownstream(batchNo)
  }

  async function verify(qrCode: string) {
    verifying.value = true
    try {
      const { traceApi } = await import('@/api/modules/trace')
      verifyResult.value = await traceApi.verify(qrCode)
      return verifyResult.value
    } finally {
      verifying.value = false
    }
  }

  async function fetchComplaints(params?: any) {
    loading.value = true
    try {
      const { traceApi } = await import('@/api/modules/trace')
      const res = await traceApi.getComplaints(params)
      complaintList.value = res.records
    } finally {
      loading.value = false
    }
  }

  async function submitComplaint(data: any) {
    const { traceApi } = await import('@/api/modules/trace')
    await traceApi.submitComplaint(data)
  }

  async function handleComplaint(id: number, data: any) {
    const { traceApi } = await import('@/api/modules/trace')
    await traceApi.handleComplaint(id, data)
  }

  function resetTraceResult() {
    traceResult.value = null
    verifyResult.value = null
  }

  return {
    traceResult,
    verifyResult,
    complaintList,
    loading,
    verifying,
    search,
    fetchUpstream,
    fetchDownstream,
    verify,
    fetchComplaints,
    submitComplaint,
    handleComplaint,
    resetTraceResult,
  }
})
