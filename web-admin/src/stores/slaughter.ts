import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { EntryInspection, SlaughterInspection, RactopamineTest, CarcassStamp } from '@/types/slaughter'

export const useSlaughterStore = defineStore('slaughter', () => {
  const entryList = ref<EntryInspection[]>([])
  const inspectList = ref<SlaughterInspection[]>([])
  const ractopamineList = ref<RactopamineTest[]>([])
  const stampList = ref<CarcassStamp[]>([])
  const loading = ref(false)

  async function fetchEntryList(params?: any) {
    loading.value = true
    try {
      const { slaughterApi } = await import('@/api/modules/slaughter')
      const res = await slaughterApi.getEntryInspections(params)
      entryList.value = res.records
    } finally {
      loading.value = false
    }
  }

  async function createEntry(data: Partial<EntryInspection>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.createEntryInspection(data)
  }

  async function fetchInspectList(params?: any) {
    loading.value = true
    try {
      const { slaughterApi } = await import('@/api/modules/slaughter')
      const res = await slaughterApi.getInspections(params)
      inspectList.value = res.records
    } finally {
      loading.value = false
    }
  }

  async function createInspect(data: Partial<SlaughterInspection>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.createInspection(data)
  }

  async function fetchRactopamineList(params?: any) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    const res = await slaughterApi.getRactopamineTests(params)
    ractopamineList.value = res.records
  }

  async function createRactopamine(data: Partial<RactopamineTest>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.createRactopamine(data)
  }

  async function createStamp(data: Partial<CarcassStamp>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.createStamp(data)
  }

  return {
    entryList,
    inspectList,
    ractopamineList,
    stampList,
    loading,
    fetchEntryList,
    createEntry,
    fetchInspectList,
    createInspect,
    fetchRactopamineList,
    createRactopamine,
    createStamp,
  }
})
