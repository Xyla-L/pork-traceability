import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 全局应用状态
 * - 侧边栏折叠
 * - 通知消息列表
 * - 面包屑导航缓存
 */
export const useAppStore = defineStore('app', () => {
  // ========== State ==========

  // 侧边栏是否折叠
  const sidebarCollapsed = ref(false)
  // 顶部通知消息列表
  const notifications = ref<AppNotification[]>([])
  // 当前页面面包屑导航数组
  const breadcrumbs = ref<BreadcrumbItem[]>([])

  // ========== Actions ==========

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setSidebarCollapsed(collapsed: boolean) {
    sidebarCollapsed.value = collapsed
  }

  async function fetchNotifications() {
    // TODO: 接通知接口
    // const data = await notificationApi.getList()
    // notifications.value = data
  }

  function markNotificationRead(id: number) {
    const item = notifications.value.find((n) => n.id === id)
    if (item) item.read = true
  }

  function setBreadcrumbs(items: BreadcrumbItem[]) {
    breadcrumbs.value = items
  }

  function clearBreadcrumbs() {
    breadcrumbs.value = []
  }

  return {
    sidebarCollapsed,
    notifications,
    breadcrumbs,
    toggleSidebar,
    setSidebarCollapsed,
    fetchNotifications,
    markNotificationRead,
    setBreadcrumbs,
    clearBreadcrumbs,
  }
})

// ========== 类型 ==========
export interface AppNotification {
  id: number
  title: string
  content: string
  type: 'warning' | 'info' | 'success' | 'error'
  read: boolean
  createTime: string
}

export interface BreadcrumbItem {
  title: string
  path?: string
}
