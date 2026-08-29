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
        const res = await getComplaintList({ status })
        this.list = res.list || []
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
