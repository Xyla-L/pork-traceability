<template>
  <view class="ai-card">
    <view class="ai-header">
      <view class="ai-title">
        <text class="ai-emoji">🍖</text>
        <text class="ai-title-text">AI 帮你看</text>
      </view>
      <text class="ai-badge" v-if="status === 'streaming'">解读中</text>
      <text class="ai-badge done" v-else-if="status === 'done'">已解读</text>
    </view>

    <!-- 未开始 -->
    <view class="ai-intro" v-if="status === 'idle'">
      <text class="ai-intro-text">
        这是哪来的、干不干净，我帮你把溯源信息翻译成大白话。
      </text>
      <view class="ai-btn primary" @click="start">
        <text>帮我解读</text>
      </view>
    </view>

    <!-- 输出区 -->
    <view class="ai-body" v-else>
      <view class="ai-content">
        <text class="ai-text">{{ content }}</text>
        <text class="ai-cursor" v-if="status === 'streaming'">▍</text>
      </view>

      <view class="ai-error" v-if="errorMsg">
        <text>{{ errorMsg }}</text>
        <text class="ai-retry" @click="start">重试</text>
      </view>

      <view class="ai-actions" v-if="status === 'done'">
        <text class="ai-hint">还有疑问可以直接问</text>
      </view>

      <view class="ai-ask" v-if="status === 'done'">
        <input
          class="ai-input"
          v-model="question"
          placeholder="比如：打过疫苗吗？保质期多久？"
          confirm-type="send"
          @confirm="ask"
        />
        <view class="ai-btn small" @click="ask">
          <text>问</text>
        </view>
      </view>

      <view class="ai-disclaimer">
        <text>AI 生成内容仅供参考，以官方检疫与检测报告为准</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { aiApi } from '@/api/modules/ai'
import { getDeviceId } from '@/utils/device'

const props = defineProps({
  qrCode: { type: String, required: true },
  // 进入页面就自动解读
  autoStart: { type: Boolean, default: false },
})

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

function start() {
  if (streaming.value) return
  content.value = ''
  errorMsg.value = ''
  finished.value = false
  streaming.value = true

  task = aiApi.consumerInterpret(props.qrCode, getDeviceId(), {
    onDelta(text) {
      content.value += text
    },
    onDone(info) {
      streaming.value = false
      finished.value = true
      conversationId.value = info?.conversationId || ''
    },
    onError(msg) {
      streaming.value = false
      errorMsg.value = msg
    },
  })
}

function ask() {
  const q = question.value.trim()
  if (!q || streaming.value) return

  question.value = ''
  content.value = ''
  errorMsg.value = ''
  streaming.value = true

  task = aiApi.consumerAsk(
    props.qrCode,
    q,
    getDeviceId(),
    // 回传上一轮的会话 ID，否则每轮追问都是新会话，上下文白丢
    conversationId.value,
    {
      onDelta(text) {
        content.value += text
      },
      onDone(info) {
        streaming.value = false
        conversationId.value = info?.conversationId || conversationId.value
      },
      onError(msg) {
        streaming.value = false
        errorMsg.value = msg
      },
    }
  )
}

onBeforeUnmount(() => {
  task?.abort()
})

if (props.autoStart) {
  start()
}
</script>

<style scoped lang="scss">
.ai-card {
  margin: 24rpx;
  padding: 28rpx;
  background: linear-gradient(135deg, #f0f9f4 0%, #ffffff 60%);
  border: 2rpx solid #d9f0e3;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(25, 190, 107, 0.06);
}

.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.ai-title {
  display: flex;
  align-items: center;
}

.ai-emoji {
  font-size: 32rpx;
  margin-right: 10rpx;
}

.ai-title-text {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2d3d;
}

.ai-badge {
  font-size: 22rpx;
  color: #19be6b;
  background: rgba(25, 190, 107, 0.1);
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
}

.ai-badge.done {
  color: #909399;
  background: #f4f4f5;
}

.ai-intro-text {
  font-size: 26rpx;
  color: #606266;
  line-height: 1.7;
}

.ai-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 20rpx;
  padding: 14rpx 40rpx;
  border-radius: 40rpx;
  background: #19be6b;
  color: #fff;
  font-size: 27rpx;
}

.ai-btn.small {
  margin-top: 0;
  margin-left: 14rpx;
  padding: 14rpx 28rpx;
  flex-shrink: 0;
}

.ai-content {
  font-size: 27rpx;
  line-height: 1.85;
  color: #303133;
}

.ai-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-cursor {
  color: #19be6b;
}

.ai-error {
  margin-top: 14rpx;
  font-size: 25rpx;
  color: #f56c6c;
}

.ai-retry {
  margin-left: 16rpx;
  color: #409eff;
  text-decoration: underline;
}

.ai-actions {
  margin-top: 20rpx;
}

.ai-hint {
  font-size: 24rpx;
  color: #a8abb2;
}

.ai-ask {
  display: flex;
  align-items: center;
  margin-top: 14rpx;
  padding: 10rpx 16rpx;
  background: #fff;
  border: 2rpx solid #ebeef5;
  border-radius: 40rpx;
}

.ai-input {
  flex: 1;
  font-size: 26rpx;
  color: #303133;
}

.ai-disclaimer {
  margin-top: 20rpx;
  font-size: 22rpx;
  color: #c0c4cc;
  text-align: center;
}
</style>
