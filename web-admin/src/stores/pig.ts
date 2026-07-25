import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { PigIndividual, VaccineRecord, SlaughterApply, QuarantineCert } from '@/types/pig'
import type { PageQuery, PageResult } from '@/types/common'
import { pigApi } from '@/api/modules/breeding'

export const usePigStore = defineStore('pig', () => {
  // ========== State ==========
  
  // 生猪分页列表
  const pigList = ref<PigIndividual[]>([])
  // 分页总条数
  const total = ref(0)
  // 当前选中/查看的单头生猪详情
  const currentPig = ref<PigIndividual | null>(null)
  // 当前生猪对应的疫苗记录数组
  const vaccineList = ref<VaccineRecord[]>([])
  // 屠宰申请列表
  const applyList = ref<SlaughterApply[]>([])
  // 当前生猪检疫证明
  const quarantineCert = ref<QuarantineCert | null>(null)
  // 全局加载状态，控制表格loading动画
  const loading = ref(false)

  // ========== Actions ==========

  async function fetchPigList(query: PigListQuery & PageQuery) {
    loading.value = true
    try {
      const res: PageResult<PigIndividual> = await pigApi.list(query)
      pigList.value = res.records
      total.value = res.total
    } finally {
      loading.value = false
    }
  }

  async function fetchPigDetail(id: number) {
    loading.value = true
    try {
      currentPig.value = await pigApi.detail(id)
    } finally {
      loading.value = false
    }
  }

  async function createPig(data: Partial<PigIndividual>) {
    await pigApi.create(data)
  }

  async function updatePig(id: number, data: Partial<PigIndividual>) {
    await pigApi.update(id, data)
  }

  async function fetchVaccines(pigId: number) {
    vaccineList.value = await pigApi.getVaccines(pigId)
  }

  async function addVaccine(pigId: number, data: Partial<VaccineRecord>) {
    await pigApi.addVaccine(pigId, data)
  }

  async function fetchApplies(params?: any) {
    const res: PageResult<SlaughterApply> = await pigApi.getApplies(params)
    applyList.value = res.records
  }

  async function approveApply(applyId: number, approved: boolean, comment?: string) {
    await pigApi.approveApply(applyId, { approved, comment })
  }

  async function fetchQuarantineCert(pigId: number) {
    quarantineCert.value = await pigApi.getQuarantineCert(pigId)
  }

  async function uploadQuarantineCert(pigId: number, data: any) {
    await pigApi.uploadQuarantineCert(pigId, data)
  }

  function resetCurrentPig() {
    currentPig.value = null
    vaccineList.value = []
    quarantineCert.value = null
  }

  return {
    pigList,
    total,
    currentPig,
    vaccineList,
    applyList,
    quarantineCert,
    loading,
    fetchPigList,
    fetchPigDetail,
    createPig,
    updatePig,
    fetchVaccines,
    addVaccine,
    fetchApplies,
    approveApply,
    fetchQuarantineCert,
    uploadQuarantineCert,
    resetCurrentPig,
  }
})

export interface PigListQuery extends Record<string, unknown> {
  farmId?: number
  earTagNo?: string
  breed?: string
  status?: number
  keyword?: string
}
