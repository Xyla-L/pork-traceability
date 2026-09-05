// ========== 顶部通知类型定义 ==========
export interface AppNotification {
  id: number
  title: string
  content: string
  type: 'warning' | 'info' | 'success' | 'error'
  read: boolean
  createTime: string
}
