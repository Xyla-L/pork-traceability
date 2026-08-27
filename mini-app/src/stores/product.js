import { defineStore } from 'pinia'
import { scanProduct, getSafeBuy, verifyProduct } from '@/api/modules/consumer'

/**
 * 产品溯源状态
 */
export const useProductStore = defineStore('product', {
  state: () => ({
    scanResult: null,
    safeBuy: null,
    verifyResult: null,
    loading: false,
    verifying: false,
  }),

  actions: {
    /** 扫码溯源 */
    async fetchScan(qrCode) {
      this.loading = true
      try {
        this.scanResult = await scanProduct(qrCode)
        return this.scanResult
      } finally {
        this.loading = false
      }
    },

    /** 安心购数据 */
    async fetchSafeBuy(qrCode) {
      this.safeBuy = await getSafeBuy(qrCode)
      return this.safeBuy
    },

    /** 一键验真 */
    async verify(qrCode) {
      this.verifying = true
      try {
        this.verifyResult = await verifyProduct(qrCode)
        return this.verifyResult
      } finally {
        this.verifying = false
      }
    },
  },
})
