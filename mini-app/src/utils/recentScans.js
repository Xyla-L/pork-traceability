/**
 * 最近扫码记录（本地缓存）
 * 后端暂无「扫码历史」接口，扫码/搜索成功后把记录存到本地 storage，
 * 首页「最近扫码」从缓存读取，去重 + 保留最近若干条。
 */

const KEY = 'recentScans'
const MAX = 10

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

/** 格式化当前时间为 YYYY-MM-DD HH:mm */
function formatNow() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 读取最近扫码记录（不存在则空数组） */
export function getRecentScans() {
  const raw = uni.getStorageSync(KEY)
  return Array.isArray(raw) ? raw : []
}

/**
 * 新增一条扫码记录（按 qrCode 去重，最新的排最前）
 * @param {{ qrCode: string, name?: string, type?: 'qr'|'batch' }} item
 */
export function addRecentScan({ qrCode, name, type = 'qr' }) {
  if (!qrCode) return
  const record = { qrCode, name: name || qrCode, type, time: formatNow() }
  const list = getRecentScans().filter((r) => r.qrCode !== qrCode)
  uni.setStorageSync(KEY, [record, ...list].slice(0, MAX))
  console.log('[recentScans] 写入后 storage =', uni.getStorageSync(KEY))
}
