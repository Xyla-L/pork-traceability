/**
 * 商品溯源码的判定 —— 与后端 common-core 的 TraceQrCode 同一口径。
 *
 * 码由 sales-service 生成：QR-PORK- + UUID 去横线大写（32 位十六进制）。
 * 判据只用前缀 + 字符集，不锁死位数：老演示数据里有 QR-PORK-DEMO-0001 这种手工码。
 *
 * 为什么要在这里拦：扫到付款码、公众号码、网址码时，如果直接跳溯源页再去查库，
 * 用户看到的是「未找到溯源信息」——在食品安全场景里会被理解成「这肉是假的」。
 * 扫错码是操作问题，不是商品问题，必须在跳转之前就把话说清楚。
 */

const PATTERN = /QR-PORK-[A-Z0-9][A-Z0-9-]{5,63}/

export const NOT_TRACE_CODE = '这不是本系统的猪肉溯源码：请对准包装袋上的二维码重新扫描'

/** 从扫码原文里取出溯源码（容忍被包在链接/参数里），取不到返回空串 */
export function extractTraceCode(raw) {
  if (!raw) return ''
  const matched = String(raw).trim().toUpperCase().match(PATTERN)
  return matched ? matched[0] : ''
}

export function isTraceCode(raw) {
  return !!extractTraceCode(raw)
}
