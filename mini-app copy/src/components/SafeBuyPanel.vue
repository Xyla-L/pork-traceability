<template>
  <view class="safe-buy-panel">
    <view class="sbp-header">
      <text class="sbp-title">🛡️ 安心购</text>
      <BlockchainBadge :verified="verified" />
    </view>

    <view class="sbp-section">
      <view class="sbp-section-title">全链条检疫章</view>
      <CertChain :certs="certs" />
    </view>

    <view class="sbp-section">
      <view class="sbp-section-title">检测报告</view>
      <ReportSummary :reports="reports" />
    </view>
  </view>
</template>

<script setup>
import BlockchainBadge from './BlockchainBadge.vue'
import CertChain from './CertChain.vue'
import ReportSummary from './ReportSummary.vue'

defineProps({
  verified: {
    type: Boolean,
    default: false,
  },
  certs: {
    type: Array,
    default: () => [],
  },
  reports: {
    type: Array,
    default: () => [],
  },
})
</script>

<style lang="scss" scoped>
.safe-buy-panel {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;

  .sbp-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 20rpx;

    .sbp-title {
      font-size: 30rpx;
      font-weight: 700;
      color: #303133;
    }
  }

  .sbp-section {
    margin-bottom: 24rpx;

    &:last-child {
      margin-bottom: 0;
    }

    .sbp-section-title {
      font-size: 26rpx;
      font-weight: 600;
      color: #606266;
      margin-bottom: 8rpx;
      padding-bottom: 8rpx;
      border-bottom: 1rpx solid #f0f2f5;
    }
  }
}
</style>
