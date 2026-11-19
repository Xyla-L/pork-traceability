import { computed, onBeforeUnmount, ref } from 'vue'
import { aiApi } from '@/api/modules/ai'
import { getDeviceId } from '@/utils/device'

/**
 * 消费端 AI 问答的共享状态机。
 * 唯一来源，禁止各写一份（同侧判定器同样的纪律）：
 *   - AiInterpretCard（safe-buy 页内嵌卡片）
 *   - AiAssistantFloat（全局悬浮助手）
 * 都从这里取流式状态与发送逻辑，UI 只管渲染。
 */
export function useAiChat() {
  const content = ref('')
  const question = ref('')
  const errorMsg = ref('')
  const streaming = ref(false)
  const finished = ref(false)
  const conversationId = ref('')

  let task = null

  const status = computed(() => {
    if (streaming.value) return 'streaming'
    if (finished.value) return 'done'
    if (errorMsg.value) return 'error'
    return 'idle'
  })

  function begin(runner) {
    content.value = ''
    errorMsg.value = ''
    finished.value = false
    streaming.value = true
    task = runner({
      onDelta(text) {
        content.value += text
      },
      onDone(info) {
        streaming.value = false
        finished.value = true
        // 流被掐断时 api 层会补发 conversationId='' 的收尾事件，此时保留原有会话 ID
        conversationId.value = info?.conversationId || conversationId.value
      },
      onError(msg) {
        streaming.value = false
        errorMsg.value = msg
      },
    })
  }

  /** 首轮：按码自动解读 */
  function start(qrCode) {
    if (streaming.value || !qrCode) return
    begin((handlers) => aiApi.consumerInterpret(qrCode, getDeviceId(), handlers))
  }

  /** 追问：回传上一轮会话 ID，否则每轮都是新会话，上下文白丢 */
  function ask(qrCode, q) {
    const text = (q || '').trim()
    if (!text || streaming.value) return

    // 后端 q 硬上限 500 字符，超了会返回 error 事件；按文档在前端就拦下
    if (text.length > 500) {
      errorMsg.value = '问题太长了，精简一下再问'
      return
    }

    question.value = ''
    begin((handlers) => aiApi.consumerAsk(qrCode, text, getDeviceId(), conversationId.value, handlers))
  }

  /** 中途停止：保留已输出的部分，收尾成可追问状态 */
  function stop() {
    if (!streaming.value) return
    task?.abort()
    streaming.value = false
    finished.value = true
  }

  /** 清空会话（换码或用户主动重开） */
  function reset() {
    task?.abort()
    task = null
    content.value = ''
    question.value = ''
    errorMsg.value = ''
    streaming.value = false
    finished.value = false
    conversationId.value = ''
  }

  // 页面卸载时必须掐断流，否则后台还在收包
  onBeforeUnmount(() => {
    task?.abort()
    task = null
  })

  return { content, question, errorMsg, streaming, finished, status, start, ask, stop, reset }
}
