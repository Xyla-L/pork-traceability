import { defineStore } from 'pinia'
import { submitComplaint, getComplaintList, getComplaintDetail } from '@/api/modules/complaint'

/**
 * 举报状态
 */
export const useComplaintStore = defineStore('complaint', {
  state: () => ({
    list: [],
    detail: null,
    loading: false,
  }),

  actions: {
    async fetchList(status) {
      this.loading = true
      try {
        // 全部时不传 status，避免把 null 序列化进查询串
        const params = status === null || status === undefined ? {} : { status }
        const res = await getComplaintList(params)
        // 后端返回 MyBatis-Plus 分页对象 { records, total, ... }
        this.list = res.records || []
        return this.list
      } finally {
        this.loading = false
      }
    },

    async fetchDetail(id) {
      this.detail = await getComplaintDetail(id)
      return this.detail
    },

    async submit(data) {
      return submitComplaint(data)
    },
  },
})
