/**
 * 日期格式化
 * @param date - 日期对象、时间戳或日期字符串
 * @param fmt - 格式化模板，默认 'YYYY-MM-DD HH:mm:ss'
 *   占位符: YYYY(年) MM(月) DD(日) HH(时) mm(分) ss(秒)
 * @returns 格式化后的日期字符串
 */
export function formatDate(date: Date | string | number | null | undefined, fmt: string = 'YYYY-MM-DD HH:mm:ss'): string {
  if (date === null || date === undefined || date === '') {
    return ''
  }

  const d = new Date(date)

  if (isNaN(d.getTime())) {
    return ''
  }

  const tokens: Record<string, string> = {
    YYYY: String(d.getFullYear()),
    MM: String(d.getMonth() + 1).padStart(2, '0'),
    DD: String(d.getDate()).padStart(2, '0'),
    HH: String(d.getHours()).padStart(2, '0'),
    mm: String(d.getMinutes()).padStart(2, '0'),
    ss: String(d.getSeconds()).padStart(2, '0'),
  }

  let result = fmt
  for (const [token, value] of Object.entries(tokens)) {
    result = result.replace(token, value)
  }

  return result
}

/**
 * 格式化重量显示
 * @param kg - 重量（千克）
 * @param precision - 保留小数位数，默认 1
 * @returns 格式化后的重量字符串，如 "125.5 kg"
 */
export function formatWeight(kg: number | null | undefined, precision: number = 1): string {
  if (kg === null || kg === undefined) {
    return '-'
  }

  return `${Number(kg).toFixed(precision)} kg`
}

/**
 * 格式化价格
 * @param price - 价格数值（单位：元）
 * @param precision - 保留小数位数，默认 2
 * @returns 格式化后的价格字符串，如 "¥128.50"
 */
export function formatPrice(price: number | null | undefined, precision: number = 2): string {
  if (price === null || price === undefined) {
    return '-'
  }

  return `¥${Number(price).toFixed(precision)}`
}

/**
 * 格式化温度
 * @param temp - 温度数值（摄氏度）
 * @param precision - 保留小数位数，默认 1
 * @returns 格式化后的温度字符串，如 "36.5°C"
 */
export function formatTemp(temp: number | null | undefined, precision: number = 1): string {
  if (temp === null || temp === undefined) {
    return '-'
  }

  return `${Number(temp).toFixed(precision)}°C`
}