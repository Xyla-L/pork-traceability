<template>
  <view class="cert-chain">
    <view v-for="(cert, idx) in certs" :key="idx" class="cert-item">
      <view class="cert-badge">✅</view>
      <view class="cert-body">
        <view class="cert-type">{{ cert.type }}</view>
        <view class="cert-no">编号：{{ cert.certNo }}</view>
        <view class="cert-org">{{ cert.issueOrg }} · {{ cert.issueTime }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  certs: {
    type: Array,
    default: () => [],
  },
})
</script>

<style lang="scss" scoped>
.cert-chain {
  .cert-item {
    display: flex;
    align-items: flex-start;
    gap: 16rpx;
    padding: 16rpx 0;

    .cert-badge {
      font-size: 32rpx;
      flex-shrink: 0;
    }

    .cert-body {
      flex: 1;

      .cert-type {
        font-size: 28rpx;
        font-weight: 600;
        color: #303133;
        margin-bottom: 4rpx;
      }

      .cert-no {
        font-size: 24rpx;
        color: #606266;
      }

      .cert-org {
        font-size: 22rpx;
        color: #909399;
      }
    }
  }
}
</style>
