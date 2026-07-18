/**
 * 前端 SHA-256 计算工具
 * 用于验真时对比本地计算值与链上存储值
 *
 * 注意：前端 SHA-256 仅用于用户本地对比参考，
 * 正式上链哈希以后端 file-service 计算的值为准
 */

/**
 * 计算字符串的 SHA-256 哈希值
 */
export async function sha256(input: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(input)
  const hashBuffer = await crypto.subtle.digest('SHA-256', data)
  return bufferToHex(hashBuffer)
}

/**
 * 计算文件的 SHA-256 哈希值
 */
export async function fileSha256(file: File): Promise<string> {
  const arrayBuffer = await file.arrayBuffer()
  const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer)
  return bufferToHex(hashBuffer)
}

function bufferToHex(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * 截取哈希前8位，用于前端快速对比展示
 */
export function shortHash(hash: string): string {
  return hash.substring(0, 8)
}
