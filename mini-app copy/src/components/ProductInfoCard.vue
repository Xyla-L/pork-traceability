<template>
  <view class="pic-card">
    <view class="pic-name">{{ product.name }}</view>
    <view class="pic-grid">
      <view class="pic-item">
        <text class="pic-label">批次号</text>
        <text class="pic-value">{{ product.batchNo }}</text>
      </view>
      <view class="pic-item">
        <text class="pic-label">规格</text>
        <text class="pic-value">{{ product.weight }}</text>
      </view>
      <view class="pic-item">
        <text class="pic-label">包装日期</text>
        <text class="pic-value">{{ product.packageDate }}</text>
      </view>
      <view class="pic-item">
        <text class="pic-label">保质期至</text>
        <text class="pic-value">{{ product.expireDate }}</text>
      </view>
    </view>

    <view class="pic-countdown" :class="countdownClass">
      <text v-if="daysLeft > 0">⏳ 剩余 {{ daysLeft }} 天</text>
      <text v-else-if="daysLeft === 0">⚠️ 今天到期</text>
      <text v-else>❌ 已过期 {{ -daysLeft }} 天</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { daysUntil } from '@/utils/format'

const props = defineProps({
  product: {
    type: Object,
    required: true,
  },
})

const daysLeft = computed(() => daysUntil(props.product.expireDate))

const countdownClass = computed(() => {
  if (daysLeft.value > 3) return 'is-safe'
  if (daysLeft.value > 0) return 'is-warning'
  return 'is-danger'
})
</script>

<style lang="scss" scoped>
.pic-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;

  .pic-name {
    font-size: 34rpx;
    font-weight: 700;
    color: #303133;
    margin-bottom: 24rpx;
  }

  .pic-grid {
    display: flex;
    flex-wrap: wrap;

    .pic-item {
      width: 50%;
      display: flex;
      flex-direction: column;
      margin-bottom: 20rpx;

      .pic-label {
        font-size: 22rpx;
        color: #909399;
        margin-bottom: 4rpx;
      }

      .pic-value {
        font-size: 28rpx;
        color: #303133;
      }
    }
  }

  .pic-countdown {
    padding: 12rpx 20rpx;
    border-radius: 12rpx;
    font-size: 24rpx;
    font-weight: 600;

    &.is-safe {
      background: #f0f9eb;
      color: #67c23a;
    }

    &.is-warning {
      background: #fdf6ec;
      color: #e6a23c;
    }

    &.is-danger {
      background: #fef0f0;
      color: #f56c6c;
    }
  }
}
</style>
