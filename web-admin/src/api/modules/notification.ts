import request from '@/utils/request'
import type { AppNotification } from '@/types/notification'

export const notificationApi = {
  /**
   * 获取当前用户通知列表（未读优先）
   */
  getList(params?: { read?: boolean }): Promise<AppNotification[]> {
    return request.get('/notification/list', { params })
  },

  /**
   * 标记单条通知已读
   */
  markRead(id: number) {
    return request.put(`/notification/${id}/read`)
  },

  /**
   * 全部标记已读
   */
  markAllRead() {
    return request.put('/notification/read-all')
  },
}
