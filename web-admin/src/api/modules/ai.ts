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
  // 文档要求：只收到 delta 没有 done（连接被中间层掐断）时，
  // reader.read() 返回 done=true 视为正常结束，用已收到的内容收尾，
  // 否则组件的 loading 会永远转圈。
  let doneFired = false

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
          doneFired = true
          options.onDone?.({
            conversationId: payload.conversationId ?? '',
            chars: payload.chars ?? 0,
          })
        } else if (eventName === 'error') {
          options.onError?.(payload.message || 'AI 生成失败')
        }
      }
    }

    if (!doneFired) options.onDone?.({ conversationId: '', chars: 0 })
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

  /** 监管端：自由问答。conversationId 传上一轮的值才能延续上下文 */
  adminAsk(question: string, user: string, conversationId: string, options: AiStreamOptions) {
    return streamSse('/ai/admin/ask', { q: question, user, conversationId }, options)
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

  // ==================== 自然语言操作 ====================
  // 和上面的问答不同：这几个返回的是结构化 JSON，不是流。
  // 要的是「执行了哪个接口、返回了什么」，逐字吐出来反而不好用。

  /** 发一句话，让它翻译成接口调用 */
  actionChat(text: string, conversationId: string) {
    return postJson<ActionChatResult>('/ai/action/chat', { text, conversationId })
  },

  /** 写操作二次确认（参数留在服务端，这里只传会话 id） */
  actionConfirm(conversationId: string) {
    return postJson<ActionChatResult>('/ai/action/confirm', { conversationId })
  },

  /** 检索调试：看看一句话会被理解成哪几个接口 */
  graphSearch(q: string, topK = 8) {
    return getJson<{ query: string; count: number; items: GraphSearchItem[] }>(
      `/ai/graph/search?q=${encodeURIComponent(q)}&topK=${topK}`
    )
  },

  /** 图谱规模统计 */
  graphStats() {
    return getJson<Record<string, any>>('/ai/graph/stats')
  },
}

// ==================== 自然语言操作的类型 ====================

export type ActionPhase = 'UNSUPPORTED' | 'NEED_SLOT' | 'READY' | 'EXECUTED' | 'FAILED'

export interface ActionOperation {
  id: string
  summary: string
  method: string
  path: string
  gatewayPath: string
  module: string
  service: string
}

export interface ActionMissingSlot {
  name: string
  type: string
  hint: string
}

export interface ActionExecution {
  success: boolean
  status: number
  elapsedMs: number
  /** 下游返回的原始数据（JSON 字符串），前端按需解析 */
  data?: string | null
  error?: string | null
}

export interface ActionCandidate {
  id: string
  summary: string
  method: string
  score: number
}

export interface ActionChatResult {
  phase: ActionPhase
  conversationId: string
  operation?: ActionOperation | null
  slots: Record<string, any>
  missing: ActionMissingSlot[]
  question: string
  answer: string
  execution?: ActionExecution | null
  candidates: ActionCandidate[]
}

export interface GraphSearchItem {
  id: string
  method: string
  path: string
  gatewayPath: string
  summary: string
  score: number
  hits: string[]
  executable: boolean
  /** 不能执行时后端给的原因：internal / 黑名单 / 写操作未点名 */
  denyReason?: string
}

/** 普通 JSON 请求。不走 streamSse，但同样要手动带 token（绕过了 axios 拦截器） */
async function postJson<T>(path: string, body: unknown): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })
  return unwrap<T>(response)
}

async function getJson<T>(path: string): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${API_BASE}${path}`, { method: 'GET', headers })
  return unwrap<T>(response)
}

/**
 * 后端统一返回 {code, message, data}。
 * 业务失败（如 401/503）的 HTTP 状态码可能仍是 200，所以不能只看 response.ok。
 */
async function unwrap<T>(response: Response): Promise<T> {
  const payload = await response.json().catch(() => null)
  if (!response.ok || !payload) {
    const msg =
      response.status === 401
        ? '登录状态已失效，请重新登录'
        : payload?.message || `服务返回异常（${response.status}）`
    throw new Error(msg)
  }
  if (payload.code !== 200) {
    throw new Error(payload.message || '操作失败')
  }
  return payload.data as T
}
