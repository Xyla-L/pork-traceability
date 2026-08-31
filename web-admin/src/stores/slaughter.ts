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
      const res = await slaughterApi.getEntries(params)
      entryList.value = res.records || res.list || []
    } finally {
      loading.value = false
    }
  }

  async function createEntry(data: Partial<EntryInspection>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.createEntry(data)
  }

  async function updateEntry(id: number, data: Partial<EntryInspection>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.updateEntry(id, data)
  }

  async function fetchInspectList(params?: any) {
    loading.value = true
    try {
      const { slaughterApi } = await import('@/api/modules/slaughter')
      const res = await slaughterApi.getInspections(params)
      inspectList.value = res.records || res.list || []
    } finally {
      loading.value = false
    }
  }

  async function createInspect(data: Partial<SlaughterInspection>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.createInspection(data)
  }

  async function updateInspect(id: number, data: Partial<SlaughterInspection>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.updateInspection(id, data)
  }

  async function fetchRactopamineList(params?: any) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    const res = await slaughterApi.getRactopamine(params)
    ractopamineList.value = res.records || res.list || []
  }

  async function createRactopamine(data: Partial<RactopamineTest>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.createRactopamine(data)
  }

  async function updateRactopamine(id: number, data: Partial<RactopamineTest>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.updateRactopamine(id, data)
  }

  async function fetchStampList(params?: any) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    const res = await slaughterApi.getStamps(params)
    stampList.value = res.records || res.list || []
  }

  async function createStamp(data: Partial<CarcassStamp>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.createStamp(data)
  }

  async function updateStamp(id: number, data: Partial<CarcassStamp>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.updateStamp(id, data)
  }

  return {
    entryList,
    inspectList,
    ractopamineList,
    stampList,
    loading,
    fetchEntryList,
    createEntry,
    updateEntry,
    fetchInspectList,
    createInspect,
    updateInspect,
    fetchRactopamineList,
    createRactopamine,
    updateRactopamine,
    fetchStampList,
    createStamp,
    updateStamp,
  }
})
