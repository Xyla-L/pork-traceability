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
        <span>正在汇总数据并生成报告…</span>
      </div>

      <div class="markdown-body" v-html="renderedHtml"></div>

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
 * 只处理报告里真正用到的语法：标题、表格、列表、粗体。
 * 内容全部先做 HTML 转义，避免 AI 输出里夹带标签。
 */
function escapeHtml(s: string) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function inline(s: string) {
  return escapeHtml(s).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
}

const renderedHtml = computed(() => {
  const lines = content.value.split('\n')
  const out: string[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    // 表格：连续以 | 开头的行
    if (line.trim().startsWith('|')) {
      const rows: string[] = []
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        rows.push(lines[i].trim())
        i++
      }
      const cells = rows.map((r) =>
        r.replace(/^\||\|$/g, '').split('|').map((c) => c.trim())
      )
      const body = cells.filter((row) => !row.every((c) => /^:?-{2,}:?$/.test(c)))
      if (body.length) {
        out.push('<table class="md-table"><thead><tr>')
        body[0].forEach((c) => out.push(`<th>${inline(c)}</th>`))
        out.push('</tr></thead><tbody>')
        body.slice(1).forEach((row) => {
          out.push('<tr>')
          row.forEach((c) => out.push(`<td>${inline(c)}</td>`))
          out.push('</tr>')
        })
        out.push('</tbody></table>')
      }
      continue
    }

    if (/^####\s+/.test(line)) out.push(`<h4>${inline(line.replace(/^####\s+/, ''))}</h4>`)
    else if (/^###\s+/.test(line)) out.push(`<h3>${inline(line.replace(/^###\s+/, ''))}</h3>`)
    else if (/^##\s+/.test(line)) out.push(`<h2>${inline(line.replace(/^##\s+/, ''))}</h2>`)
    else if (/^#\s+/.test(line)) out.push(`<h1>${inline(line.replace(/^#\s+/, ''))}</h1>`)
    else if (/^\s*[-*]\s+/.test(line)) out.push(`<li>${inline(line.replace(/^\s*[-*]\s+/, ''))}</li>`)
    else if (/^\s*\d+\.\s+/.test(line)) out.push(`<li>${inline(line.replace(/^\s*\d+\.\s+/, ''))}</li>`)
    else if (line.trim() === '') out.push('<div class="gap"></div>')
    else out.push(`<p>${inline(line)}</p>`)

    i++
  }

  // 把连续 li 包进 ul
  return out
    .join('')
    .replace(/(<li>[\s\S]*?<\/li>)(?!\s*<li>)/g, '<ul>$1</ul>')
})

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
      conversationId.value = info.conversationId
      charCount.value = content.value.length
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

.markdown-body {
  font-size: 14px;
  line-height: 1.75;
  color: #303133;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2) {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 16px 0 8px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}

.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  font-size: 14px;
  font-weight: 600;
  margin: 12px 0 6px;
}

.markdown-body :deep(p) {
  margin: 4px 0;
}

.markdown-body :deep(.gap) {
  height: 6px;
}

.markdown-body :deep(ul) {
  margin: 4px 0;
  padding-left: 20px;
}

.markdown-body :deep(li) {
  margin: 2px 0;
}

.markdown-body :deep(.md-table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}

.markdown-body :deep(.md-table th),
.markdown-body :deep(.md-table td) {
  border: 1px solid #ebeef5;
  padding: 6px 10px;
  text-align: left;
}

.markdown-body :deep(.md-table th) {
  background: #f5f7fa;
  font-weight: 600;
  color: #606266;
}

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
