/**
 * 设备标识工具
 * 匿名模式下用于区分「本设备」提交的举报记录。
 * deviceId 首次生成后持久化到本地存储，同一设备、卸载前保持不变。
 */

const STORAGE_KEY = 'deviceId'

/** 生成一个 UUID（优先使用 crypto.randomUUID，降级为随机串） */
function genUuid() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/** 获取（不存在则生成并持久化）当前设备标识 */
export function getDeviceId() {
  let id = uni.getStorageSync(STORAGE_KEY)
  if (!id) {
    id = genUuid()
    uni.setStorageSync(STORAGE_KEY, id)
  }
  return id
}
