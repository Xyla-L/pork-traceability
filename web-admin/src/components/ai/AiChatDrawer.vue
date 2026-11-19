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
              <div class="head-sub">基于平台真实数据回答；查不到会直说，不猜</div>
            </div>
          </div>
          <div class="head-actions">
            <el-button v-if="messages.length" link size="small" @click="clear">清空</el-button>
            <el-button link size="small" @click="visible = false">收起</el-button>
          </div>
        </div>

        <!-- 消息区 -->
        <div ref="bodyRef" class="chat-body">
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
            placeholder="问点什么，Enter 发送 / Shift+Enter 换行"
            @keydown.enter.exact.prevent="send()"
          />
          <div class="input-actions">
            <span class="hint">只读问答，不会改动任何业务数据</span>
            <div class="btns">
              <el-button v-if="sending" size="small" @click="stop">停止</el-button>
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
import { MagicStick, Loading } from '@element-plus/icons-vue'
import { aiApi } from '@/api/modules/ai'
import { renderMarkdownLite } from '@/utils/markdownLite'
import { useAuthStore } from '@/stores/auth'

interface ChatMessage {
  role: 'user' | 'ai'
  text: string
  loading?: boolean
  error?: string
}

const MAX_QUESTION_LEN = 500

const quickQuestions = [
  '今天有哪些异常需要处理？',
  '现在有哪些车还在途？',
  '最近的瘦肉精检测有没有阳性？',
  '哪些批次被召回了、处理到哪一步了？',
]

const authStore = useAuthStore()

const visible = ref(false)
const draft = ref('')
const messages = ref<ChatMessage[]>([])
const sending = ref(false)
const conversationId = ref('')
const bodyRef = ref<HTMLElement | null>(null)

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
  messages.value = []
  conversationId.value = ''
  sending.value = false
  draft.value = ''
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
