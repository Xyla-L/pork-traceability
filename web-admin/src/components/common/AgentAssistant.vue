<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="520px"
    :with-header="false"
    class="agent-drawer"
    destroy-on-close
  >
    <div class="agent">
      <!-- 头部：把「能查什么」和「现在是谁在作答」写在最上面，避免用户对它有不实的期待 -->
      <header class="agent-head">
        <div class="agent-title">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能助手</span>
        </div>
        <el-tag v-if="meta" size="small" :type="meta.llmEnabled ? 'success' : 'info'" effect="plain">
          {{ meta.llmEnabled ? '模型整理' : '直接取数（未配置模型）' }}
        </el-tag>
        <el-icon class="agent-close" @click="visible = false"><Close /></el-icon>
      </header>

      <div ref="bodyRef" class="agent-body">
        <!-- 空态：与其放一句「有什么可以帮你」，不如直接给出能答的两类问题 -->
        <div v-if="messages.length === 0" class="agent-welcome">
          <p v-if="details.length" class="lead">我能查 {{ details.length }} 类事，都只读不改：</p>
          <p v-else class="lead">有什么可以帮你？</p>
          <p v-for="c in details" :key="c.name" class="item">· {{ c.hint }}</p>
          <p class="hint">所有算术都由系统完成，我只负责解读；合规结论由判定器给出，我不替你判断。</p>
          <div class="quick">
            <el-button
              v-for="q in QUICK_ASKS"
              :key="q"
              size="small"
              plain
              @click="ask(q)"
            >{{ q }}</el-button>
          </div>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">{{ m.content }}</div>
          <div v-if="m.role === 'assistant'" class="msg-meta">
            <span v-if="m.toolLabel">依据：{{ m.toolLabel }}</span>
            <span :class="['src', m.source]">{{ sourceLabel(m) }}</span>
            <el-button
              v-if="m.facts && m.facts.length"
              link
              type="primary"
              size="small"
              @click="m.expanded = !m.expanded"
            >{{ m.expanded ? '收起明细' : `依据明细（${m.facts.length}）` }}</el-button>
          </div>
          <!-- 明细是技术口径（原始字段路径），一律折叠，不默认摊在对话里 -->
          <div v-if="m.expanded && m.facts" class="facts">
            <div v-for="(f, fi) in m.facts" :key="fi" class="fact-line">{{ f }}</div>
          </div>
        </div>

        <div v-if="sending" class="msg assistant">
          <div class="bubble thinking">正在取数…</div>
        </div>
      </div>

      <footer class="agent-foot">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          placeholder="贴一个耳标号或批次号，或问「接入有哪些失败」"
          @keydown.enter.exact.prevent="send"
        />
        <div class="foot-actions">
          <span class="tip">Enter 发送 · Shift+Enter 换行</span>
          <el-button type="primary" :loading="sending" :disabled="!input.trim()" @click="send">
            发送
          </el-button>
        </div>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { agentApi } from '@/api/modules/agent'

interface ChatItem {
  role: 'user' | 'assistant'
  content: string
  source?: string
  toolLabel?: string
  facts?: string[]
  expanded?: boolean
}

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const QUICK_ASKS = ['今天有什么需要优先处理的', '接入有哪些失败', '温度有没有超标', '临期和召回有没有问题', '哪些批次免疫档案不全', '出栏申报驳回了多少', '运单都正常吗']

const visible = ref(props.modelValue)
const messages = ref<ChatItem[]>([])
const input = ref('')
const sending = ref(false)
const threadId = ref<number | undefined>(undefined)
const bodyRef = ref<HTMLElement>()
const meta = ref<{
  llmEnabled: boolean
  model: string
  capabilities: string[]
  capabilityDetails?: { name: string; label: string; hint: string }[]
} | null>(null)

/**
 * 空态的能力清单由后端按角色返回：养殖户不该在这里看到「举报待受理」，
 * 前端写死一份就等于把角色收窄又绕回去了。
 */
const details = computed(() => {
  const m = meta.value
  if (!m) return []
  if (m.capabilityDetails?.length) return m.capabilityDetails
  // 老接口只有「中文名（工具名）」字符串，退化成只显示名字
  return (m.capabilities || []).map((s) => ({ name: s, label: s.replace(/（.*）$/, ''), hint: s }))
})

watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
  }
)
watch(visible, (v) => {
  emit('update:modelValue', v)
  if (v) loadMeta()
})

async function loadMeta() {
  if (meta.value) return
  try {
    meta.value = await agentApi.meta()
  } catch {
    // 状态拿不到不影响提问，只是少一个「谁在作答」的标签
  }
}

/** 消息来源必须如实标注：模型写的和直接取数的可信度不一样，藏起来才是不负责 */
function sourceLabel(m: ChatItem) {
  if (m.source === 'agent') return '模型自主查询'
  if (m.source === 'llm') return '模型整理'
  if (m.source === 'knowledge') return '业务说明（无实时数据）'
  if (m.source === 'local') return meta.value?.llmEnabled ? '直接取数（模型调用失败）' : '直接取数'
  return '未取数'
}

async function ask(question: string) {
  input.value = question
  await send()
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  sending.value = true
  scrollToBottom()
  try {
    const res = await agentApi.chat({ threadId: threadId.value, message: text })
    threadId.value = res.threadId
    messages.value.push({
      role: 'assistant',
      content: res.answer,
      source: res.source,
      toolLabel: res.toolLabel,
      facts: res.facts || [],
      expanded: false,
    })
  } catch (e: any) {
    messages.value.push({
      role: 'assistant',
      content: e?.message || '这次没查成，稍后再试一次。',
      source: 'none',
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  })
}

loadMeta()
</script>

<style lang="scss" scoped>
.agent {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.agent-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-bottom: 1px solid #e4e7ed;

  .agent-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin-right: auto;
  }

  .agent-close {
    cursor: pointer;
    color: #909399;
    font-size: 16px;

    &:hover {
      color: #409eff;
    }
  }
}

.agent-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  background: #f7f8fa;
}

.agent-welcome {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;

  .lead {
    font-weight: 600;
    color: #303133;
  }

  .hint {
    margin-top: 8px;
    color: #909399;
    font-size: 12px;
  }

  .quick {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 12px;
  }
}

.msg {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;

  &.user {
    align-items: flex-end;

    .bubble {
      background: #409eff;
      color: #fff;
      border-radius: 8px 8px 2px 8px;
    }
  }

  &.assistant {
    align-items: flex-start;

    .bubble {
      background: #fff;
      color: #303133;
      border: 1px solid #ebeef5;
      border-radius: 8px 8px 8px 2px;
    }
  }

  .bubble {
    max-width: 92%;
    padding: 10px 12px;
    font-size: 13px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .thinking {
    color: #909399;
  }

  .msg-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 6px;
    font-size: 12px;
    color: #909399;

    .src {
      &.local {
        color: #e6a23c;
      }

      &.none {
        color: #f56c6c;
      }

      &.knowledge {
        color: #909399;
      }

      &.agent {
        color: #409eff;
      }
    }
  }

  .facts {
    margin-top: 8px;
    max-width: 100%;
    max-height: 260px;
    overflow-y: auto;
    background: #fff;
    border: 1px dashed #dcdfe6;
    border-radius: 6px;
    padding: 10px 12px;

    .fact-line {
      font-family: Consolas, Menlo, monospace;
      font-size: 11px;
      line-height: 1.6;
      color: #606266;
      word-break: break-all;
    }
  }
}

.agent-foot {
  border-top: 1px solid #e4e7ed;
  padding: 12px 18px 14px;

  .foot-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 8px;

    .tip {
      font-size: 11px;
      color: #c0c4cc;
    }
  }
}
</style>
