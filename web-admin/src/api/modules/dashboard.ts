import request from '@/utils/request'
import type { DashboardOverview } from '@/types/dashboard'

export const dashboardApi = {
  /**
   * 获取工作台聚合数据（统计卡片 + 图表 + 最近预警/待办）
   */
  getOverview(): Promise<DashboardOverview> {
    return request.get('/dashboard/overview')
  },
}
