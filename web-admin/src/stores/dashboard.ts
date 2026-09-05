import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DashboardOverview } from '@/types/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  // 工作台聚合数据
  const overview = ref<DashboardOverview | null>(null)
  const loading = ref(false)

  async function fetchOverview() {
    loading.value = true
    try {
      const { dashboardApi } = await import('@/api/modules/dashboard')
      overview.value = await dashboardApi.getOverview()
      return overview.value
    } finally {
      loading.value = false
    }
  }

  return {
    overview,
    loading,
    fetchOverview,
  }
})
