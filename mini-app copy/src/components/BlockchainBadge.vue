<template>
  <view class="bc-badge" :class="typeClass">
    <text class="bc-icon">{{ icon }}</text>
    <text class="bc-text">{{ text }}</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 验证状态：true=已验证 false=未验证 */
  verified: {
    type: Boolean,
    default: false,
  },
  /** 自定义文案（覆盖默认文案） */
  text: {
    type: String,
    default: '',
  },
})

const icon = computed(() => (props.verified ? '✅' : '⚠️'))
const typeClass = computed(() => (props.verified ? 'is-verified' : 'is-unverified'))
const text = computed(() => props.text || (props.verified ? '区块链已验证' : '未通过区块链验证'))
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
}
</style>
