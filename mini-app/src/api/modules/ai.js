/**
 * AI 助手接口（SSE 流式）
 *
 * 平台差异说明（这是最容易踩坑的地方）：
 *   - 微信小程序：uni.request 不支持 fetch，必须开 enableChunked 用 onChunkReceived 收分块
 *   - H5：直接走浏览器 fetch + ReadableStream
 * 所以这里按平台条件编译两套实现，对外暴露同一个函数签名。
 *
 * 后端事件协议：
 *   event: delta  data: {"text":"..."}
 *   event: done   data: {"conversationId":"..."}
 *   event: error  data: {"message":"..."}
 */

import { BASE_URL } from '../index'

/**
 * 把 SSE 原始文本切分成事件块，攒够一个事件就回调。
 * 返回一个「喂数据」的函数，调用方每收到一块原始数据就喂一次。
 */
function createSseParser(onEvent) {
  let buffer = ''

  return (chunkText) => {
    buffer += chunkText
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''

    for (const block of blocks) {
      let eventName = 'message'
      let data = ''
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        else if (line.startsWith('data:')) data += line.slice(5).trim()
      }
      if (!data) continue
      try {
        onEvent(eventName, JSON.parse(data))
      } catch (e) {
        // 忽略无法解析的片段（如心跳）
      }
    }
  }
}

/**
 * 手写 UTF-8 解码器（仅供微信小程序使用）。
 *
 * 为什么不用 TextDecoder：
 *   微信小程序运行环境没有暴露 TextDecoder（那是浏览器/Node 的 API），
 *   直接调用会抛 "TextDecoder is not defined"，流式一开就崩。
 *
 * 为什么要处理 pending：
 *   SSE 分块是按字节切的，一个中文字符（3 字节）很可能被切在两块之间。
 *   不完整的那几个字节要留到下一块一起解，否则会出现乱码。
 */
function createUtf8Decoder() {
  let pending = []

  return (arrayBuffer) => {
    const incoming = new Uint8Array(arrayBuffer)
    const bytes = pending.length ? new Uint8Array(pending.length + incoming.length) : incoming
    if (pending.length) {
      bytes.set(pending, 0)
      bytes.set(incoming, pending.length)
    }

    let out = ''
    let i = 0

    while (i < bytes.length) {
      const b = bytes[i]

      if (b < 0x80) {
        out += String.fromCharCode(b)
        i += 1
      } else if (b >= 0xc0 && b < 0xe0) {
        if (i + 1 >= bytes.length) break
        out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i + 1] & 0x3f))
        i += 2
      } else if (b >= 0xe0 && b < 0xf0) {
        if (i + 2 >= bytes.length) break
        out += String.fromCharCode(((b & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f))
        i += 3
      } else if (b >= 0xf0) {
        if (i + 3 >= bytes.length) break
        const cp =
          ((b & 0x07) << 18) |
          ((bytes[i + 1] & 0x3f) << 12) |
          ((bytes[i + 2] & 0x3f) << 6) |
          (bytes[i + 3] & 0x3f)
        out += String.fromCodePoint(cp)
        i += 4
      } else {
        // 非法起始字节，跳过
        i += 1
      }
    }

    pending = Array.prototype.slice.call(bytes, i)
    return out
  }
}

function buildUrl(path, params) {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return `${BASE_URL}${path}${qs ? '?' + qs : ''}`
}

/**
 * 发起一次 SSE 请求。
 * @returns {{ abort: () => void }} 可用于中途取消
 */
function startStream(path, params, handlers) {
  const url = buildUrl(path, params)
  const { onDelta, onDone, onError } = handlers
  let aborted = false

  const dispatch = (eventName, payload) => {
    if (aborted) return
    if (eventName === 'delta') onDelta?.(payload.text ?? '')
    else if (eventName === 'done') onDone?.(payload)
    else if (eventName === 'error') onError?.(payload.message || 'AI 生成失败')
  }

  // #ifdef MP-WEIXIN
  {
  const task = uni.request({
    url,
    method: 'GET',
    enableChunked: true,
    header: { Accept: 'text/event-stream' },
    success: () => {},
    fail: (err) => {
      if (!aborted) onError?.(err?.errMsg || 'AI 服务连接失败')
    },
  })

  const decode = createUtf8Decoder()

  const parse = createSseParser(dispatch)
  task.onChunkReceived((res) => {
    if (aborted) return
    try {
      parse(decode(res.data))
    } catch (e) {
      // 解码失败忽略，等下一块
    }
  })

  return {
    abort: () => {
      aborted = true
      try {
        task.abort()
      } catch (e) {}
    },
  }
  }
  // #endif

  // #ifndef MP-WEIXIN
  {
  const controller = new AbortController()
  const decoder = new TextDecoder('utf-8')
  const parse = createSseParser(dispatch)

  fetch(url, { headers: { Accept: 'text/event-stream' }, signal: controller.signal })
    .then(async (res) => {
      if (!res.ok || !res.body) {
        onError?.(`AI 服务返回异常（${res.status}）`)
        return
      }
      const reader = res.body.getReader()
      while (true) {
        const { done, value } = await reader.read()
        if (done || aborted) break
        parse(decoder.decode(value, { stream: true }))
      }
      reader.cancel().catch(() => {})
    })
    .catch((e) => {
      if (!aborted && e.name !== 'AbortError') onError?.(e.message || 'AI 服务连接失败')
    })

  return {
    abort: () => {
      aborted = true
      controller.abort()
    },
  }
  }
  // #endif
}

export const aiApi = {
  /** 一键解读这块肉的溯源信息 */
  consumerInterpret(qrCode, userId, handlers) {
    return startStream('/ai/consumer/interpret', { qrCode, user: userId }, handlers)
  },

  /** 追问。带上上一轮的 conversationId，Dify 侧才能延续同一会话 */
  consumerAsk(qrCode, question, userId, conversationId, handlers) {
    return startStream(
      '/ai/consumer/ask',
      { qrCode, q: question, user: userId, conversationId },
      handlers
    )
  },
}
