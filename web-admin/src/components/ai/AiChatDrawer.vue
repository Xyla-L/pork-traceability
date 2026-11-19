<template>
  <div class="ai-chat-root">
    <!-- 悬浮入口：任何后台页面都能拉起来，不打断当前工作 -->
    <button class="ai-fab" type="button" title="AI 助手（基于平台数据问答）" @click="visible = true">
      <el-icon><MagicStick /></el-icon>
      <span>AI 助手</span>
    </button>

    <el-drawer
      v-model="visible"
      :size="drawerSize"
      :with-header="false"
      append-to-body
      class="ai-chat-drawer"
    >
      <div class="chat-shell">
        <!-- 头部 -->
        <div class="chat-header">
          <div class="head-main">
            <el-icon class="head-icon"><MagicStick /></el-icon>
            <div class="head-text">
              <div class="head-title">AI 助手</div>
              <div class="head-sub">
                {{
                  mode === 'ask'
                    ? '基于平台真实数据回答；查不到会直说，不猜'
                    : '一句话完成查询与操作；改数据的动作会先问你'
                }}
              </div>
            </div>
          </div>
          <div class="head-actions">
            <el-button v-if="messages.length" link size="small" @click="clear">清空</el-button>
            <el-button link size="small" @click="visible = false">收起</el-button>
          </div>
        </div>

        <!--
          模式切换：
            问答 = 只读，调用 /ai/admin/ask（SSE 流式）
            操作 = 会真的调接口，调用 /ai/action/chat（结构化 JSON）
          两者会话互不影响，切回来时各自的上下文还在。
        -->
        <div class="mode-switch">
          <button
            v-for="m in modes"
            :key="m.value"
            type="button"
            :class="['mode-btn', { active: mode === m.value }]"
            @click="switchMode(m.value)"
          >
            {{ m.label }}
          </button>
        </div>

        <!-- 消息区 -->
        <div ref="bodyRef" class="chat-body">
          <!-- ==================== 问答模式 ==================== -->
          <template v-if="mode === 'ask'">
            <!-- 首屏引导 + 快捷问题 -->
            <div v-if="!messages.length" class="intro">
              <p class="intro-title">可以问我这些：</p>
              <div class="chips">
                <span v-for="q in quickQuestions" :key="q" class="chip" @click="send(q)">{{ q }}</span>
              </div>
              <p class="intro-note">
                想查具体对象时，把<strong>批次号 / 溯源码</strong>带上，回答会更准（例如「PB20261025001 这批到哪了」）。
              </p>
            </div>

            <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
              <div v-if="m.role === 'ai'" class="avatar"><el-icon><MagicStick /></el-icon></div>
              <div class="bubble">
                <template v-if="m.role === 'ai'">
                  <div v-if="m.text" class="ai-markdown" v-html="renderMarkdownLite(m.text)"></div>
                  <div v-else-if="m.loading" class="thinking">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>正在取数并生成回答…</span>
                  </div>
                  <div v-if="m.error" class="err-text">{{ m.error }}</div>
                </template>
                <template v-else>{{ m.text }}</template>
              </div>
            </div>
          </template>

          <!-- ==================== 操作模式 ==================== -->
          <template v-else>
            <div v-if="!actionMessages.length" class="intro">
              <p class="intro-title">可以直接让它动手：</p>
              <div class="chips">
                <span v-for="q in actionQuickQuestions" :key="q" class="chip" @click="sendAction(q)">
                  {{ q }}
                </span>
              </div>
              <p class="intro-note">
                它会先告诉你<strong>理解成了哪个接口</strong>，参数不够会追问；
                <strong>查询类直接执行，写操作要你点确认</strong>。
              </p>
            </div>

            <div v-for="(m, i) in actionMessages" :key="i" :class="['msg', m.role]">
              <div v-if="m.role === 'ai'" class="avatar"><el-icon><Operation /></el-icon></div>
              <div :class="['bubble', m.role === 'ai' ? 'bubble-card' : '']">
                <template v-if="m.role === 'ai'">
                  <div v-if="m.loading" class="thinking">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>正在理解并路由到接口…</span>
                  </div>
                  <ActionBubble
                    v-else-if="m.action"
                    :result="m.action"
                    :confirming="!!m.confirming"
                    @confirm="confirmAction(i)"
                  />
                  <div v-if="m.error" class="err-text">{{ m.error }}</div>
                </template>
                <template v-else>{{ m.text }}</template>
              </div>
            </div>
          </template>
        </div>

        <!-- 输入区 -->
        <div class="chat-input">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="500"
            show-word-limit
            :placeholder="
              mode === 'ask'
                ? '问点什么，Enter 发送 / Shift+Enter 换行'
                : '说你要做什么，比如「查一下所有养殖场」'
            "
            @keydown.enter.exact.prevent="send()"
          />
          <div class="input-actions">
            <span class="hint">
              {{ mode === 'ask' ? '只读问答，不会改动任何业务数据' : '查询直接执行；改数据的动作会先请你确认' }}
            </span>
            <div class="btns">
              <el-button v-if="sending && mode === 'ask'" size="small" @click="stop">停止</el-button>
              <el-button
                v-else
                size="small"
                type="primary"
                :disabled="!draft.trim()"
                @click="send()"
              >
                发送
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { MagicStick, Loading, Operation } from '@element-plus/icons-vue'
import { aiApi, type ActionChatResult } from '@/api/modules/ai'
import { renderMarkdownLite } from '@/utils/markdownLite'
import { useAuthStore } from '@/stores/auth'
import ActionBubble from './ActionBubble.vue'

interface ChatMessage {
  role: 'user' | 'ai'
  text: string
  loading?: boolean
  error?: string
}

/**
 * 操作模式的消息。
 *
 * 和问答模式分开存，是因为两者语义不同：
 * 问答是一条条文本，操作用是「一次路由结果」——里面带着接口、缺失参数、
 * 执行状态和确认按钮，硬塞进 ChatMessage 会让问答侧的渲染逻辑到处判空。
 */
interface ActionMessage {
  role: 'user' | 'ai'
  text?: string
  loading?: boolean
  error?: string
  action?: ActionChatResult
  confirming?: boolean
}

type Mode = 'ask' | 'action'

const MAX_QUESTION_LEN = 500

const quickQuestions = [
  '今天有哪些异常需要处理？',
  '现在有哪些车还在途？',
  '最近的瘦肉精检测有没有阳性？',
  '哪些批次被召回了、处理到哪一步了？',
]

const actionQuickQuestions = [
  '查一下所有养殖场',
  '今天卖了多少猪肉',
  '看看有哪些过期的产品',
  '查一下各屠宰场今天的检验记录',
]

const modes: { label: string; value: Mode }[] = [
  { label: '问答', value: 'ask' },
  { label: '操作', value: 'action' },
]

const authStore = useAuthStore()

const visible = ref(false)
const draft = ref('')
const messages = ref<ChatMessage[]>([])
const sending = ref(false)
const conversationId = ref('')
const bodyRef = ref<HTMLElement | null>(null)

// 操作模式独立的一套状态：会话 id 不能和问答共用，
// 否则两边会把各自的上下文串成一条线（问「它怎么样了」会指向完全不同的东西）
const mode = ref<Mode>('ask')
const actionMessages = ref<ActionMessage[]>([])
const actionConversationId = ref('')

let controller: AbortController | null = null

/** 窄屏收缩，避免抽屉盖满整个页面 */
const drawerSize = computed(() => (window.innerWidth < 768 ? '92%' : '440px'))

/** Dify 侧按 user 隔离会话；用登录名，缺省时退回 admin */
const userId = computed(() => authStore.user?.username || 'admin')

function scrollToBottom() {
  nextTick(() => {
    const el = bodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// 流式追加不会触发数组替换，靠 watch 兜住滚动
watch(
  () => messages.value.map((m) => m.text.length + (m.error ? 1 : 0)).join(','),
  () => scrollToBottom()
)

function send(preset?: string) {
  // 一个入口两种语义，模板里就不用给每个按钮写两遍绑定
  if (mode.value === 'action') {
    void sendAction(preset)
    return
  }
  if (sending.value) return

  const question = (preset ?? draft.value).trim()
  if (!question) return
  // 输入框已限长，这里兜住快捷问题以外的异常来源
  if (question.length > MAX_QUESTION_LEN) {
    messages.value.push({ role: 'user', text: question.slice(0, 60) + '…' })
    messages.value.push({ role: 'ai', text: '', error: '问题太长了，精简到 500 字以内再问' })
    return
  }

  draft.value = ''
  messages.value.push({ role: 'user', text: question })
  messages.value.push({ role: 'ai', text: '', loading: true })
  sending.value = true
  scrollToBottom()

  const aiIndex = messages.value.length - 1
  controller = new AbortController()

  aiApi
    .adminAsk(question, userId.value, conversationId.value, {
      signal: controller.signal,
      onDelta(text) {
        const msg = messages.value[aiIndex]
        if (!msg) return
        msg.loading = false
        msg.text += text
      },
      onDone(info) {
        // 流被中间层掐断时 api 层会补发 conversationId='' 的收尾，此时保留原会话
        conversationId.value = info.conversationId || conversationId.value
        const msg = messages.value[aiIndex]
        if (msg) {
          msg.loading = false
          if (!msg.text && !msg.error) msg.error = '这次没有拿到回答，换个问法再试试'
        }
        sending.value = false
        scrollToBottom()
      },
      onError(message) {
        const msg = messages.value[aiIndex]
        if (msg) {
          msg.loading = false
          msg.error = message
        }
        sending.value = false
        scrollToBottom()
      },
    })
    .catch((e: any) => {
      const msg = messages.value[aiIndex]
      if (msg) {
        msg.loading = false
        msg.error = e?.message || '问答中断，请重试'
      }
      sending.value = false
    })
}

function stop() {
  controller?.abort()
  sending.value = false
  const msg = messages.value[messages.value.length - 1]
  if (msg && msg.role === 'ai') {
    msg.loading = false
    if (!msg.text && !msg.error) msg.error = '已停止'
  }
}

function clear() {
  controller?.abort()
  if (mode.value === 'action') {
    actionMessages.value = []
    actionConversationId.value = ''
  } else {
    messages.value = []
    conversationId.value = ''
  }
  sending.value = false
  draft.value = ''
}

// ==================== 操作模式 ====================

function switchMode(next: Mode) {
  if (mode.value === next) return
  mode.value = next
  nextTick(scrollToBottom)
}

/**
 * 发一句话让它执行。
 *
 * 注意这里没有「停止」：和问答的 SSE 不同，这是一次普通请求，
 * 服务端可能已经在执行了，前端中断没有意义 —— 假装能停下来反而更危险。
 */
async function sendAction(preset?: string) {
  if (sending.value) return

  const text = (preset ?? draft.value).trim()
  if (!text) return
  if (text.length > MAX_QUESTION_LEN) {
    actionMessages.value.push({ role: 'ai', error: '描述太长了，精简到 500 字以内' })
    return
  }

  draft.value = ''
  actionMessages.value.push({ role: 'user', text })
  actionMessages.value.push({ role: 'ai', loading: true })
  const index = actionMessages.value.length - 1
  sending.value = true
  scrollToBottom()

  try {
    const result = await aiApi.actionChat(text, actionConversationId.value)
    // 后端可能新建会话，也可能续用旧的；以返回值为准
    actionConversationId.value = result.conversationId || actionConversationId.value
    const msg = actionMessages.value[index]
    if (msg) {
      msg.loading = false
      msg.action = result
    }
  } catch (e: any) {
    const msg = actionMessages.value[index]
    if (msg) {
      msg.loading = false
      msg.error = e?.message || '操作失败，请稍后重试'
    }
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

/** 用户点了卡片上的「确认执行」：写操作才会走到这里 */
async function confirmAction(index: number) {
  const msg = actionMessages.value[index]
  if (!msg?.action || msg.confirming) return

  msg.confirming = true
  try {
    const result = await aiApi.actionConfirm(msg.action.conversationId)
    actionMessages.value.push({ role: 'ai', action: result })
  } catch (e: any) {
    actionMessages.value.push({ role: 'ai', error: e?.message || '确认失败' })
  } finally {
    msg.confirming = false
    scrollToBottom()
  }
}

onBeforeUnmount(() => {
  controller?.abort()
})
</script>

<style scoped>
.ai-chat-root {
  /* 只是一个挂载点，不参与布局 */
  position: static;
}

.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 28px;
  z-index: 2000;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 18px;
  border: none;
  border-radius: 22px;
  background: linear-gradient(135deg, #409eff, #2f7fe0);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(64, 158, 255, 0.35);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.ai-fab:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgba(64, 158, 255, 0.45);
}

.chat-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid #ebeef5;
}

.head-main {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.head-icon {
  color: #409eff;
  font-size: 20px;
}

.head-text {
  min-width: 0;
}

.head-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.head-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  line-height: 1.4;
}

.head-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.mode-switch {
  display: flex;
  gap: 6px;
  padding: 10px 16px 0;
  background: #fff;
}

.mode-btn {
  flex: 1;
  height: 30px;
  border: 1px solid #e4e7ed;
  background: #fff;
  border-radius: 8px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  transition: all 0.15s ease;
}

.mode-btn:hover {
  border-color: #b9d8ff;
  color: #409eff;
}

.mode-btn.active {
  background: #ecf5ff;
  border-color: #409eff;
  color: #409eff;
  font-weight: 600;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafbfc;
}

.intro {
  color: #606266;
}

.intro-title {
  font-size: 13px;
  color: #909399;
  margin: 0 0 8px;
}

.chips {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chip {
  display: block;
  padding: 9px 12px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.chip:hover {
  border-color: #409eff;
  color: #409eff;
}

.intro-note {
  margin: 14px 0 0;
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
}

.msg {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  align-items: flex-start;
}

.msg.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #ecf5ff;
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 14px;
}

.bubble {
  max-width: 88%;
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
  background: #fff;
  color: #303133;
  border: 1px solid #ebeef5;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg.user .bubble {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
  white-space: pre-wrap;
}

.msg.ai .bubble {
  /* Markdown 自己控制空白，别让 pre-wrap 把空行放大 */
  white-space: normal;
}

/* 操作卡片自带边框和内边距，气泡只当容器，不要再套一层白底 */
.bubble-card {
  flex: 1;
  max-width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  white-space: normal;
}

.thinking {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #909399;
  font-size: 12px;
}

.err-text {
  margin-top: 6px;
  color: #f56c6c;
  font-size: 12px;
}

.chat-input {
  border-top: 1px solid #ebeef5;
  padding: 12px 16px 14px;
  background: #fff;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  gap: 8px;
}

.hint {
  font-size: 12px;
  color: #a8abb2;
}

.btns {
  flex-shrink: 0;
}
</style>
