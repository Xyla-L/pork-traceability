<template>
  <div class="action-card">
    <!-- 第一行：理解成了哪个接口。用户最需要先确认这一点 -->
    <div class="ac-head">
      <el-tag :type="phaseType" size="small" effect="light">{{ phaseText }}</el-tag>
      <div v-if="result.operation" class="ac-op">
        <span class="ac-summary">{{ result.operation.summary }}</span>
        <span :class="['ac-method', result.operation.method.toLowerCase()]">
          {{ result.operation.method }}
        </span>
        <code class="ac-path">{{ result.operation.path }}</code>
      </div>
    </div>

    <p v-if="result.question" class="ac-text">{{ result.question }}</p>

    <!-- 缺什么参数：直接列出来，用户照着补就行 -->
    <div v-if="result.missing.length" class="ac-missing">
      <span class="ac-label">还需要</span>
      <el-tag
        v-for="m in result.missing"
        :key="m.name"
        size="small"
        type="warning"
        effect="plain"
      >
        {{ m.name }}（{{ m.hint }}）
      </el-tag>
    </div>

    <!-- 已收集到的参数：让用户看见系统记住了什么，不然补参数像在猜 -->
    <div v-if="slotEntries.length" class="ac-slots">
      <span class="ac-label">已填</span>
      <span v-for="[k, v] in slotEntries" :key="k" class="ac-slot">
        <em>{{ k }}</em>=<b>{{ v }}</b>
      </span>
    </div>

    <div v-if="result.execution" class="ac-result">
      <div class="ac-result-head">
        <el-icon v-if="result.execution.success" class="ok"><CircleCheck /></el-icon>
        <el-icon v-else class="bad"><CircleClose /></el-icon>
        <span>{{ result.execution.success ? '执行成功' : '执行失败' }}</span>
        <span class="ac-meta">
          HTTP {{ result.execution.status }} · {{ result.execution.elapsedMs }} ms
        </span>
      </div>
      <div v-if="result.execution.error" class="ac-error">{{ result.execution.error }}</div>
      <pre v-if="preview" class="ac-data">{{ preview }}</pre>
      <el-button
        v-if="result.execution.data && result.execution.data.length > 600"
        link
        size="small"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : '展开全部' }}
      </el-button>
    </div>

    <div v-if="result.phase === 'READY'" class="ac-actions">
      <el-button size="small" type="primary" :loading="confirming" @click="$emit('confirm')">
        确认执行
      </el-button>
      <span class="ac-tip">涉及写操作，需要你点一下才会真正提交</span>
    </div>

    <!-- 候选接口：排查「为什么选了这个」时唯一的入口 -->
    <el-collapse v-if="result.candidates.length > 1" class="ac-more">
      <el-collapse-item :title="`参考：另外 ${result.candidates.length - 1} 个近似接口`" name="1">
        <div v-for="c in result.candidates" :key="c.id" class="ac-candidate">
          <span :class="['ac-method', c.method.toLowerCase()]">{{ c.method }}</span>
          <code>{{ c.id.split(':').slice(3).join(':') }}</code>
          <span class="ac-score">{{ (c.score * 100).toFixed(0) }}%</span>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import type { ActionChatResult } from '@/api/modules/ai'

const props = defineProps<{ result: ActionChatResult; confirming?: boolean }>()
defineEmits<{ (e: 'confirm'): void }>()

const expanded = ref(false)

const phaseText = computed(() => {
  switch (props.result.phase) {
    case 'UNSUPPORTED':
      return '没找到对应功能'
    case 'NEED_SLOT':
      return '需要补充信息'
    case 'READY':
      return '等待确认'
    case 'EXECUTED':
      return '已执行'
    default:
      return '执行失败'
  }
})

const phaseType = computed(() => {
  switch (props.result.phase) {
    case 'EXECUTED':
      return 'success'
    case 'NEED_SLOT':
    case 'READY':
      return 'warning'
    case 'FAILED':
      return 'danger'
    default:
      return 'info'
  }
})

const slotEntries = computed(() => {
  const slots = props.result.slots || {}
  return Object.entries(slots)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => {
      // body 是对象，展开成一行 key=value 更好读
      if (typeof v === 'object') {
        return [k, JSON.stringify(v)]
      }
      return [k, String(v)]
    }) as [string, string][]
})

/** 结果预览：JSON 就格式化，非 JSON 直接截断显示 */
const preview = computed(() => {
  const raw = props.result.execution?.data
  if (!raw) return ''
  let text = raw
  try {
    text = JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    // 不是 JSON（比如导出文件流说明），原样展示
  }
  return expanded.value || text.length <= 600 ? text : text.slice(0, 600) + '\n…'
})
</script>

<style scoped>
.action-card {
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  background: #fff;
  padding: 10px 12px;
  box-shadow: 0 1px 3px rgba(31, 45, 61, 0.04);
}

.ac-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ac-op {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}

.ac-summary {
  font-size: 13px;
  font-weight: 600;
  color: #1f2d3d;
}

.ac-method {
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  background: #eef2f7;
  color: #5a6b7f;
}

.ac-method.get {
  background: #e8f3ff;
  color: #2f7fe0;
}

.ac-method.post {
  background: #e9f7ef;
  color: #27a35a;
}

.ac-method.put {
  background: #fff5e6;
  color: #d98a1a;
}

.ac-method.delete {
  background: #fdecec;
  color: #d64545;
}

.ac-path {
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
  color: #7a869a;
  word-break: break-all;
}

.ac-text {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
}

.ac-label {
  font-size: 12px;
  color: #909399;
  margin-right: 6px;
}

.ac-missing,
.ac-slots {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.ac-slot {
  font-size: 12px;
  background: #f4f7fb;
  border-radius: 4px;
  padding: 2px 6px;
  color: #5a6b7f;
}

.ac-slot em {
  font-style: normal;
  color: #909399;
  margin-right: 3px;
}

.ac-result {
  margin-top: 10px;
  border-top: 1px dashed #ebeef5;
  padding-top: 8px;
}

.ac-result-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #5a6b7f;
}

.ac-result-head .ok {
  color: #27a35a;
}

.ac-result-head .bad {
  color: #d64545;
}

.ac-meta {
  margin-left: auto;
  color: #b0b8c4;
  font-size: 11px;
}

.ac-error {
  margin-top: 6px;
  font-size: 12px;
  color: #d64545;
  line-height: 1.5;
}

.ac-data {
  margin: 6px 0 0;
  padding: 8px;
  background: #f7f9fc;
  border-radius: 6px;
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
  line-height: 1.5;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  color: #33475b;
}

.ac-actions {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.ac-tip {
  font-size: 11px;
  color: #b0b8c4;
}

.ac-more {
  margin-top: 8px;
  border-top: none;
}

.ac-candidate {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  padding: 2px 0;
  color: #7a869a;
}

.ac-candidate code {
  font-family: Consolas, Monaco, monospace;
  word-break: break-all;
}

.ac-score {
  margin-left: auto;
  color: #b0b8c4;
}
</style>
