import { defineStore } from 'pinia'
import { scanProduct, getSafeBuy, verifyProduct, searchProduct } from '@/api/modules/consumer'

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
    // 最近一次拿到的溯源码：全局 AI 助手的上下文来源（「我这块肉」的边界）
    lastQrCode: '',
  }),

  actions: {
    /** 扫码溯源 */
    async fetchScan(qrCode) {
      this.loading = true
      if (qrCode) this.lastQrCode = qrCode
      try {
        this.scanResult = await scanProduct(qrCode)
        return this.scanResult
      } finally {
        this.loading = false
      }
    },

    /** 产品搜索（二维码 / 批次号） */
    async fetchSearch(keyword) {
      this.loading = true
      try {
        const res = await searchProduct(keyword)
        // 后端 search：二维码返回 scan 结构（含 product/traceChain），
        // 批次号返回 full 结构（batchNo/upstream/.../blockchain，无 product）。
        // 无 product 时包装成结果页可用的 scan 结构。
        this.scanResult = res && !res.product && res.batchNo
          ? { product: { name: `批次 ${res.batchNo}`, batchNo: res.batchNo }, traceChain: res }
          : res
        return this.scanResult
      } finally {
        this.loading = false
      }
    },

    /** 安心购数据 */
    async fetchSafeBuy(qrCode) {
      if (qrCode) this.lastQrCode = qrCode
      this.safeBuy = await getSafeBuy(qrCode)
      return this.safeBuy
    },

    /** 一键验真 */
    async verify(qrCode) {
      this.verifying = true
      if (qrCode) this.lastQrCode = qrCode
      try {
        this.verifyResult = await verifyProduct(qrCode)
        return this.verifyResult
      } finally {
        this.verifying = false
      }
    },
  },
})
