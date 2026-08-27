<template>
  <view class="empty-state">
    <view class="empty-icon">{{ icon }}</view>
    <view class="empty-text">{{ text }}</view>
    <button v-if="showRetry" class="empty-btn" size="mini" @click="emit('retry')">重试</button>
  </view>
</template>

<script setup>
defineProps({
  icon: {
    type: String,
    default: '📭',
  },
  text: {
    type: String,
    default: '暂无数据',
  },
  showRetry: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['retry'])
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 40rpx;

  .empty-icon {
    font-size: 96rpx;
    margin-bottom: 24rpx;
  }

  .empty-text {
    font-size: 28rpx;
    color: #909399;
    margin-bottom: 24rpx;
  }

  .empty-btn {
    font-size: 26rpx;
    color: #409eff;
    background: #ecf5ff;
    border-radius: 999rpx;
    padding: 0 32rpx;
  }
}
</style>
