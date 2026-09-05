import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AppNotification } from '@/types/notification'

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
    const { notificationApi } = await import('@/api/modules/notification')
    const data = await notificationApi.getList()
    notifications.value = data || []
  }

  async function markNotificationRead(id: number) {
    // 本地乐观更新已读状态
    const item = notifications.value.find((n) => n.id === id)
    if (item) item.read = true
    // 同步后端（后端未就绪时静默失败，不影响本地展示）
    try {
      const { notificationApi } = await import('@/api/modules/notification')
      await notificationApi.markRead(id)
    } catch {
      // 忽略
    }
  }

  async function markAllRead() {
    notifications.value.forEach((n) => (n.read = true))
    try {
      const { notificationApi } = await import('@/api/modules/notification')
      await notificationApi.markAllRead()
    } catch {
      // 忽略
    }
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
    markAllRead,
    setBreadcrumbs,
    clearBreadcrumbs,
  }
})

// ========== 类型 ==========
export interface BreadcrumbItem {
  title: string
  path?: string
}
