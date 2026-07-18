import dayjs from 'dayjs'

/**
 * 日期时间格式化
 */
export function formatDateTime(date: string | Date, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return '-'
  return dayjs(date).format(format)
}

/**
 * 日期格式化
 */
export function formatDate(date: string | Date, format = 'YYYY-MM-DD'): string {
  if (!date) return '-'
  return dayjs(date).format(format)
}

/**
 * 重量格式化 (kg)
 */
export function formatWeight(kg: number, decimals = 1): string {
  if (kg == null) return '-'
  return `${kg.toFixed(decimals)} kg`
}

/**
 * 金额格式化
 */
export function formatPrice(yuan: number, decimals = 2): string {
  if (yuan == null) return '-'
  return `¥${yuan.toFixed(decimals)}`
}

/**
 * 温度格式化
 */
export function formatTemp(celsius: number): string {
  if (celsius == null) return '-'
  return `${celsius >= 0 ? '+' : ''}${celsius.toFixed(1)}℃`
}

/**
 * 文件大小格式化
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

/**
 * 相对时间（距现在多久）
 */
export function formatRelativeTime(date: string | Date): string {
  if (!date) return '-'
  const now = dayjs()
  const target = dayjs(date)
  const diffMinutes = now.diff(target, 'minute')

  if (diffMinutes < 1) return '刚刚'
  if (diffMinutes < 60) return `${diffMinutes} 分钟前`
  const diffHours = now.diff(target, 'hour')
  if (diffHours < 24) return `${diffHours} 小时前`
  const diffDays = now.diff(target, 'day')
  if (diffDays < 30) return `${diffDays} 天前`
  return formatDate(date)
}
