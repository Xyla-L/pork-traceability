<template>
  <el-card class="ai-report-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div class="title-wrap">
          <el-icon class="title-icon"><MagicStick /></el-icon>
          <span class="title">AI 监管简报</span>
          <el-tag v-if="status" :type="statusTagType" size="small" effect="light">{{ statusText }}</el-tag>
        </div>
        <div class="actions">
          <el-button v-if="loading" size="small" @click="stop">停止</el-button>
          <el-button
            v-else
            size="small"
            type="primary"
            :loading="false"
            @click="generate"
          >
            {{ content ? '重新生成' : '生成今日简报' }}
          </el-button>
        </div>
      </div>
    </template>

    <!-- 空状态 -->
    <el-empty
      v-if="!content && !loading"
      description="点击右上角生成，AI 会汇总当日批次与异常数据，输出结构化监管简报"
      :image-size="80"
    />

    <!-- 生成中 -->
    <div v-else class="content-wrap">
      <div v-if="loading && !content" class="thinking">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在汇总数据并生成报告，约需 10 秒…</span>
      </div>

      <div class="markdown-body ai-markdown" v-html="renderedHtml"></div>

      <div v-if="loading" class="cursor-blink">▍</div>

      <div v-if="conversationId" class="footer-meta">
        会话 {{ conversationId.slice(0, 8) }} · 共 {{ charCount }} 字
        <el-button link type="primary" size="small" @click="copy">复制全文</el-button>
      </div>
    </div>

    <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false" show-icon class="err" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Loading } from '@element-plus/icons-vue'
import { aiApi } from '@/api/modules/ai'
import { renderMarkdownLite } from '@/utils/markdownLite'

const props = withDefaults(defineProps<{ userId?: string }>(), {
  userId: 'admin',
})

const content = ref('')
const loading = ref(false)
const errorMsg = ref('')
const conversationId = ref('')
const charCount = ref(0)

let controller: AbortController | null = null

const status = computed(() => {
  if (loading.value) return 'streaming'
  if (errorMsg.value) return 'error'
  if (content.value) return 'done'
  return ''
})

const statusText = computed(() => {
  switch (status.value) {
    case 'streaming':
      return '生成中'
    case 'error':
      return '失败'
    case 'done':
      return '已完成'
    default:
      return ''
  }
})

const statusTagType = computed<'success' | 'info' | 'danger'>(() => {
  if (status.value === 'error') return 'danger'
  if (status.value === 'done') return 'success'
  return 'info'
})

/* ---------------- 轻量 Markdown 渲染 ----------------
 * 渲染逻辑与样式都收在 utils/markdownLite.ts + styles/global.scss 的 .ai-markdown 里，
 * 问答抽屉共用同一份（避免两处实现各自演化）。
 */
const renderedHtml = computed(() => renderMarkdownLite(content.value))

/* ---------------- 交互 ---------------- */

function generate() {
  if (loading.value) return

  content.value = ''
  errorMsg.value = ''
  conversationId.value = ''
  charCount.value = 0
  loading.value = true

  controller = new AbortController()

  aiApi.adminReport(props.userId, {
    signal: controller.signal,
    onDelta(text) {
      content.value += text
    },
    onDone(info) {
      // 流被掐断时 api 层会补发 conversationId='' 的收尾事件，此时保留原有会话 ID
      conversationId.value = info.conversationId || conversationId.value
      charCount.value = info.chars || content.value.length
      loading.value = false
    },
    onError(msg) {
      errorMsg.value = msg
      loading.value = false
    },
  }).catch((e) => {
    errorMsg.value = e?.message || '生成中断'
    loading.value = false
  })
}

function stop() {
  controller?.abort()
  loading.value = false
  charCount.value = content.value.length
}

async function copy() {
  try {
    await navigator.clipboard.writeText(content.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选中')
  }
}

onBeforeUnmount(() => {
  controller?.abort()
})
</script>

<style scoped>
.ai-report-panel {
  border-radius: 10px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  color: #409eff;
  font-size: 18px;
}

.title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.content-wrap {
  max-height: 560px;
  overflow-y: auto;
  padding-right: 4px;
}

.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
  padding: 12px 0;
}

/* Markdown 排版统一由 styles/global.scss 的 .ai-markdown 提供（问答抽屉共用） */

.cursor-blink {
  color: #409eff;
  animation: blink 1s steps(2, start) infinite;
}

@keyframes blink {
  to {
    visibility: hidden;
  }
}

.footer-meta {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
  font-size: 12px;
  color: #a8abb2;
  display: flex;
  align-items: center;
  gap: 8px;
}

.err {
  margin-top: 10px;
}
</style>
