<template>
  <!-- 悬浮入口：扫码后带上下文问答，消费者只能问「我这块肉」，没有全平台数据入口 -->
  <view class="ai-float" @click="open">
    <text class="ai-float-label">AI 问答</text>
  </view>

  <view v-if="visible" class="ai-mask" @click="close" />
  <view v-if="visible" class="ai-sheet">
    <view class="ai-sheet-header">
      <text class="ai-sheet-title">AI 助手</text>
      <text class="ai-sheet-badge" v-if="status === 'streaming'">解答中</text>
      <text class="ai-sheet-close" @click="close">✕</text>
    </view>

    <!-- 没有码：消费者助手没有全平台数据，必须先有上下文 -->
    <view class="ai-sheet-empty" v-if="!qrCode">
      <text class="ai-empty-text">先扫一个溯源码，我才能帮你分析这块肉。</text>
      <view class="ai-sheet-btn" @click="goScan">
        <text>去扫码</text>
      </view>
    </view>

    <template v-else>
      <scroll-view class="ai-sheet-body" scroll-y>
        <!-- 未开始 -->
        <view class="ai-sheet-intro" v-if="status === 'idle'">
          <text class="ai-intro-text">
            这是哪来的、干不干净，我帮你把溯源信息翻译成大白话。
          </text>
          <view class="ai-sheet-btn" @click="start(qrCode)">
            <text>帮我解读</text>
          </view>
        </view>

        <!-- 输出区 -->
        <view v-else>
          <view class="ai-content">
            <text class="ai-text">{{ content }}</text>
            <text class="ai-cursor" v-if="status === 'streaming'">▍</text>
          </view>

          <view class="ai-error" v-if="errorMsg">
            <text>{{ errorMsg }}</text>
            <text class="ai-retry" @click="start(qrCode)">重试</text>
          </view>

          <!-- 快捷问题：点一下直接问，不用打字 -->
          <view class="ai-quick" v-if="status === 'done'">
            <view
              v-for="q in quickQuestions"
              :key="q"
              class="ai-chip"
              @click="ask(qrCode, q)"
            >
              <text>{{ q }}</text>
            </view>
          </view>
        </view>
      </scroll-view>

      <view class="ai-sheet-footer">
        <view class="ai-ask">
          <input
            class="ai-input"
            v-model="question"
            maxlength="500"
            placeholder="比如：打过疫苗吗？保质期多久？"
            confirm-type="send"
            @confirm="doAsk"
          />
          <view class="ai-sheet-btn small" v-if="status === 'streaming'" @click="stop">
            <text>停止</text>
          </view>
          <view class="ai-sheet-btn small" v-else @click="doAsk">
            <text>问</text>
          </view>
        </view>
        <view class="ai-disclaimer">
          <text>AI 生成内容仅供参考，以官方检疫与检测报告为准</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useProductStore } from '@/stores/product'
import { useAiChat } from '@/composables/useAiChat'

const props = defineProps({
  // 页面自己的码优先（深链直达也带上下文）；没传就用全局最近扫码
  qrCode: { type: String, default: '' },
})

const productStore = useProductStore()
const { content, question, errorMsg, streaming, status, start, ask, stop, reset } = useAiChat()

// 需要外部控制显隐的页面用 ref 拿 visible
const visible = defineModel('visible', { type: Boolean, default: false })

const qrCode = computed(() => props.qrCode || productStore.lastQrCode || '')

// 换了码 = 上一块肉的对话作废，不许串味
watch(qrCode, (val, old) => {
  if (val && old && val !== old) reset()
})

const quickQuestions = ['打过疫苗吗？', '什么时候屠宰的？', '冷链运输温度正常吗？', '保质期多久？']

function open() {
  visible.value = true
}

function close() {
  visible.value = false
}

function doAsk() {
  ask(qrCode.value, question.value)
}

function goScan() {
  visible.value = false
  uni.reLaunch({ url: '/pages/index/index' })
}
</script>

<style scoped lang="scss">
.ai-float {
  position: fixed;
  right: 24rpx;
  bottom: 140rpx;
  z-index: 900;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #19be6b 0%, #36cfc9 100%);
  box-shadow: 0 8rpx 24rpx rgba(25, 190, 107, 0.35);

  .ai-float-label {
    font-size: 24rpx;
    font-weight: 600;
    color: #fff;
  }
}

.ai-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 998;
  background: rgba(0, 0, 0, 0.45);
}

.ai-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  padding-bottom: env(safe-area-inset-bottom);
}

.ai-sheet-header {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx 20rpx;
  border-bottom: 2rpx solid #f0f2f5;
}

.ai-sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2d3d;
}

.ai-sheet-badge {
  margin-left: 16rpx;
  font-size: 22rpx;
  color: #19be6b;
  background: rgba(25, 190, 107, 0.1);
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
}

.ai-sheet-close {
  margin-left: auto;
  font-size: 32rpx;
  color: #909399;
  padding: 8rpx;
}

.ai-sheet-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 40rpx 80rpx;
}

.ai-empty-text {
  font-size: 27rpx;
  color: #606266;
  line-height: 1.7;
  text-align: center;
}

.ai-sheet-intro {
  padding: 40rpx 32rpx;
}

.ai-intro-text {
  font-size: 26rpx;
  color: #606266;
  line-height: 1.7;
}

.ai-sheet-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 24rpx;
  padding: 14rpx 40rpx;
  border-radius: 40rpx;
  background: #19be6b;
  color: #fff;
  font-size: 27rpx;

  &.small {
    margin-top: 0;
    margin-left: 14rpx;
    padding: 14rpx 28rpx;
    flex-shrink: 0;
  }
}

.ai-sheet-body {
  height: 44vh;
  padding: 0 32rpx;
  box-sizing: border-box;
}

.ai-content {
  padding-top: 28rpx;
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

.ai-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  padding: 24rpx 0 32rpx;
}

.ai-chip {
  padding: 12rpx 24rpx;
  border-radius: 32rpx;
  background: #f0f9f4;
  border: 2rpx solid #d9f0e3;
  font-size: 24rpx;
  color: #1f7a4d;
}

.ai-sheet-footer {
  padding: 16rpx 32rpx 20rpx;
  border-top: 2rpx solid #f0f2f5;
}

.ai-ask {
  display: flex;
  align-items: center;
  padding: 10rpx 16rpx;
  background: #f5f7fa;
  border: 2rpx solid #ebeef5;
  border-radius: 40rpx;
}

.ai-input {
  flex: 1;
  font-size: 26rpx;
  color: #303133;
}

.ai-disclaimer {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #c0c4cc;
  text-align: center;
}
</style>
