/**
 * AI 助手接口（SSE 流式）
 *
 * 为什么不用项目里的 axios 封装：
 *   浏览器的 axios 基于 XHR，拿不到「边生成边返回」的增量数据，
 *   只能等整个响应结束。SSE 必须用 fetch + ReadableStream 逐块读取。
 *
 * 后端事件协议：
 *   event: delta  data: {"text":"..."}          —— 追加到界面
 *   event: done   data: {"conversationId":...}  —— 结束
 *   event: error  data: {"message":"..."}       —— 出错
 */

import { getToken } from '@/utils/auth'

const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

export interface AiStreamHandlers {
  onDelta: (text: string) => void
  onDone?: (info: { conversationId: string; chars: number }) => void
  onError?: (message: string) => void
}

export interface AiStreamOptions extends AiStreamHandlers {
  signal?: AbortSignal
}

function buildQuery(params: Record<string, string | undefined>): string {
  const qs = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.append(k, v)
  })
  return qs.toString()
}

/**
 * 通用 SSE 读取器。返回一个 Promise，流结束时 resolve，出错时 reject。
 */
async function streamSse(path: string, params: Record<string, string | undefined>, options: AiStreamOptions) {
  const url = `${API_BASE}${path}?${buildQuery(params)}`

  // fetch 绕过了 axios 封装，所以 token 得手动带上。
  // 监管简报走网关时要求 JWT，缺了这个头会被 AuthGlobalFilter 直接 401。
  const headers: Record<string, string> = { Accept: 'text/event-stream' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(url, {
      method: 'GET',
      headers,
      signal: options.signal,
    })
  } catch (e: any) {
    if (e.name === 'AbortError') return
    options.onError?.('AI 服务连接失败，请确认 ai-service 已启动')
    return
  }

  if (!response.ok || !response.body) {
    // 把常见的鉴权失败翻译成人话，比"返回异常（403）"有用得多
    const hint =
      response.status === 403
        ? '没有权限查看监管数据，请联系管理员开通监管角色'
        : response.status === 401
          ? '登录状态已失效，请重新登录'
          : `AI 服务返回异常（${response.status}）`
    options.onError?.(hint)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // SSE 以空行分隔事件；最后一段可能不完整，留在 buffer 里等下一块
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

        let payload: any
        try {
          payload = JSON.parse(data)
        } catch {
          continue
        }

        if (eventName === 'delta') {
          options.onDelta(payload.text ?? '')
        } else if (eventName === 'done') {
          options.onDone?.({
            conversationId: payload.conversationId ?? '',
            chars: payload.chars ?? 0,
          })
        } else if (eventName === 'error') {
          options.onError?.(payload.message || 'AI 生成失败')
        }
      }
    }
  } catch (e: any) {
    if (e.name !== 'AbortError') {
      options.onError?.(e.message || '流式读取中断')
    }
  } finally {
    reader.cancel().catch(() => {})
  }
}

export const aiApi = {
  /** 监管端：生成当日监管简报 */
  adminReport(user: string, options: AiStreamOptions) {
    return streamSse('/ai/admin/report', { user }, options)
  },

  /** 消费者端：一键解读溯源信息 */
  consumerInterpret(qrCode: string, user: string, options: AiStreamOptions) {
    return streamSse('/ai/consumer/interpret', { qrCode, user }, options)
  },

  /** 消费者端：基于溯源数据追问。conversationId 传上一轮拿到的值，才能延续上下文 */
  consumerAsk(qrCode: string, question: string, user: string, conversationId: string, options: AiStreamOptions) {
    return streamSse('/ai/consumer/ask', { qrCode, q: question, user, conversationId }, options)
  },

  /** 探活：后端会真实调一次 Dify */
  health() {
    return fetch(`${API_BASE}/ai/health`).then((r) => r.json())
  },
}
