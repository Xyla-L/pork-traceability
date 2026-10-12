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

  /* 人工新增 action 已删除（createEntry / createInspect / createRactopamine / createStamp）：
     屠宰四环节全部为设备独占，写入只走设备通道。 */

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

  async function updateInspect(id: number, data: Partial<SlaughterInspection>) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    await slaughterApi.updateInspection(id, data)
  }

  async function fetchRactopamineList(params?: any) {
    const { slaughterApi } = await import('@/api/modules/slaughter')
    const res = await slaughterApi.getRactopamine(params)
    ractopamineList.value = res.records || res.list || []
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
    updateEntry,
    fetchInspectList,
    updateInspect,
    fetchRactopamineList,
    updateRactopamine,
    fetchStampList,
    updateStamp,
  }
})
