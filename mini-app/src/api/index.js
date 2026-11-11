/**
 * uni.request 统一封装
 * 约定：后端统一响应体 { code, message, data }
 *   code === 200      成功，resolve(data)
 *   code === 10001    上链中（不是错误），resolve(data)
 *   其他              失败，toast 提示并 reject
 */

// 使用 mock 数据（后端 consumer API 未就绪前）
import { USE_MOCK } from './mock'
import { getDeviceId } from '@/utils/device'

let BASE_URL = import.meta.env.VITE_API_BASE || '/api/v1'

// #ifdef H5
// H5 开发态走 vite 代理（vite.config.js 里 /api → 网关）：直连 8080 会撞跨域，
// 而小程序端不支持相对路径，所以这段只在 H5 生效。
if (import.meta.env.DEV) BASE_URL = '/api/v1'
// #endif

const request = (url, options = {}) => {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        'X-Device-Id': getDeviceId(),
        ...options.header,
      },
      success: (res) => {
        const { code, message, data } = res.data || {}
        if (code === 200) {
          resolve(data)
        } else if (code === 10001) {
          // 上链中：不视为错误
          uni.showToast({ title: message || '数据上链中，请稍后查看', icon: 'none' })
          resolve(data)
        } else {
          uni.showToast({ title: message || '请求失败', icon: 'none' })
          reject(new Error(message || '请求失败'))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
        reject(err)
      },
    })
  })
}

export { BASE_URL }
export default request
export { USE_MOCK }
