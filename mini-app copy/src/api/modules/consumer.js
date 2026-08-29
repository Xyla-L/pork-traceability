import request from '../index'
import { USE_MOCK, delay, mockScanResult, mockSafeBuy, mockVerifyResult } from '../mock'

/**
 * 消费者接口模块
 * 后端就绪后：USE_MOCK 置为 false，自动切换为真实请求。
 */

// 扫码溯源：获取完整溯源信息
export const scanProduct = async (qrCode) => {
  if (USE_MOCK) {
    await delay()
    return mockScanResult
  }
  return request(`/consumer/scan/${qrCode}`)
}

// 安心购：获取全链条展示数据
export const getSafeBuy = async (qrCode) => {
  if (USE_MOCK) {
    await delay()
    return mockSafeBuy
  }
  return request(`/consumer/safe-buy/${qrCode}`)
}

// 一键验真
export const verifyProduct = async (qrCode) => {
  if (USE_MOCK) {
    await delay(1200)
    return mockVerifyResult
  }
  return request('/consumer/verify', {
    method: 'POST',
    data: { qrCode },
  })
}
