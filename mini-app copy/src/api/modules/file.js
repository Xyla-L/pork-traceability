import { BASE_URL } from '../index'
import { USE_MOCK } from '../mock'

/**
 * 文件上传模块
 * 小程序端使用 uni.uploadFile 上传图片，返回 fileId。
 */

export const uploadFile = (filePath) => {
  if (USE_MOCK) {
    // Mock：直接返回一个假的 fileId
    return new Promise((resolve) => {
      setTimeout(() => resolve({ fileId: 'mock_' + Date.now() }), 300)
    })
  }
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/file/upload',
      filePath,
      name: 'file',
      success: (res) => {
        const body = JSON.parse(res.data || '{}')
        if (body.code === 200) {
          resolve(body.data)
        } else {
          uni.showToast({ title: body.message || '上传失败', icon: 'none' })
          reject(new Error(body.message))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '上传失败，请重试', icon: 'none' })
        reject(err)
      },
    })
  })
}
