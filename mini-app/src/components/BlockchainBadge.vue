<template>
  <view class="bc-badge" :class="typeClass">
    <text class="bc-icon">{{ icon }}</text>
    <text class="bc-text">{{ text }}</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 验证状态：true=存证核对一致 false=未通过 */
  verified: {
    type: Boolean,
    default: false,
  },
  /** 是否真的写入了区块链；false 时只能说「平台存证已核对」 */
  onChain: {
    type: Boolean,
    default: false,
  },
  /** 存证条数；传 0 表示「没有存证」，不能等同于「核对未通过」 */
  count: {
    type: Number,
    default: null,
  },
  /** 自定义文案（覆盖默认文案） */
  text: {
    type: String,
    default: '',
  },
})

const icon = computed(() => '')
const typeClass = computed(() => {
  if (props.count === 0 && !props.verified) return 'is-local'
  return props.verified ? (props.onChain ? 'is-verified' : 'is-local') : 'is-unverified'
})
const text = computed(() => {
  if (props.text) return props.text
  // 「一条存证都没有」不是「核对未通过」：前者等待写入，后者是异常
  if (!props.verified && props.count === 0) return '暂无存证记录'
  if (!props.verified) return props.onChain ? '存证与链上不一致' : '存证核对未通过'
  return props.onChain ? '区块链已验证' : '平台存证已核对'
})
</script>

<style lang="scss" scoped>
.bc-badge {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;

  .bc-icon {
    font-size: 26rpx;
  }

  .bc-text {
    font-weight: 600;
  }

  &.is-verified {
    background: #f0f9eb;
    color: #67c23a;

    .bc-text {
      color: #67c23a;
    }
  }

  &.is-unverified {
    background: #fef0f0;
    color: #f56c6c;

    .bc-text {
      color: #f56c6c;
    }
  }

  // 本地存证：蓝灰而不是绿色，避免把「平台内记录一致」看成链上验证
  &.is-local {
    background: #f4f4f5;
    color: #606266;

    .bc-text {
      color: #606266;
    }
  }
}
</style>
