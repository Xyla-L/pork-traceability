import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const notifications = ref<any[]>([])
  const breadcrumbs = ref<any[]>([])

  const toggleSidebar = () => { sidebarCollapsed.value = !sidebarCollapsed.value }

  const fetchNotifications = async () => { notifications.value = [] }

  const markRead = (id: string) => { const idx = notifications.value.findIndex(n => n.id === id); if (idx > -1) notifications.value[idx].read = true }

  return { sidebarCollapsed, notifications, breadcrumbs, toggleSidebar, fetchNotifications, markRead }
})