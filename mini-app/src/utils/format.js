/**
 * 格式化工具函数
 */

/** 计算保质期剩余天数 */
export function daysUntil(dateStr) {
  if (!dateStr) return 0
  const target = new Date(dateStr.replace(/-/g, '/')).getTime()
  const now = new Date().getTime()
  return Math.ceil((target - now) / (1000 * 60 * 60 * 24))
}

/** 哈希前 8 位（本地哈希/链上哈希展示用） */
export function shortHash(hash, len = 8) {
  if (!hash) return '--'
  return hash.length > len ? hash.slice(0, len) : hash
}

/** 手机号脱敏 */
export function maskPhone(phone) {
  if (!phone) return ''
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}
