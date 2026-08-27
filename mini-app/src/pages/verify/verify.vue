<template>
  <view class="page">
    <!-- 验真中 -->
    <view v-if="verifying" class="verify-center">
      <view class="loading-spinner"></view>
      <text class="loading-text">正在验证...</text>
      <text class="loading-sub">对比 5 条链上记录...</text>
    </view>

    <!-- 结果 -->
    <template v-else-if="result">
      <!-- 通过 -->
      <view v-if="result.verified" class="verify-center pass">
        <view class="big-icon">✅</view>
        <view class="big-title">区块链已验证</view>
        <view class="big-sub">全部 {{ result.details.length }} 条记录匹配</view>
        <view class="big-tip">可放心购买</view>
      </view>

      <!-- 失败 -->
      <view v-else class="verify-center fail">
        <view class="big-icon">⚠️</view>
        <view class="big-title">数据可能被篡改</view>
        <view class="big-sub">请谨慎购买</view>
      </view>

      <!-- 逐条验真明细 -->
      <view class="card detail-list">
        <view class="section-title">逐条验真明细</view>
        <view class="detail-head">
          <text class="col-type">业务类型</text>
          <text class="col-hash">本地哈希</text>
          <text class="col-hash">链上哈希</text>
          <text class="col-match">结果</text>
        </view>
        <view v-for="(d, idx) in result.details" :key="idx" class="detail-row">
          <text class="col-type">{{ d.type }}</text>
          <text class="col-hash">{{ d.localHash }}</text>
          <text class="col-hash">{{ d.chainHash }}</text>
          <text class="col-match" :class="d.match ? 'ok' : 'no'">{{ d.match ? '✅ 匹配' : '❌ 不一致' }}</text>
        </view>
      </view>
    </template>

    <!-- 初始/空状态 -->
    <view v-else class="card">
      <EmptyState icon="🔍" text="尚未开始验真" show-retry @retry="startVerify" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useProductStore } from '@/stores/product'
import EmptyState from '@/components/EmptyState.vue'

const productStore = useProductStore()
const verifying = ref(false)
const result = ref(null)
const qrCode = ref('')

onLoad((options) => {
  qrCode.value = decodeURIComponent(options.qrCode || '')
  startVerify()
})

async function startVerify() {
  verifying.value = true
  result.value = null
  try {
    result.value = await productStore.verify(qrCode.value)
  } finally {
    verifying.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

.verify-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 40rpx;
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 20rpx;

  .loading-spinner {
    width: 64rpx;
    height: 64rpx;
    border: 6rpx solid #e4e7ed;
    border-top-color: #409eff;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    margin-bottom: 24rpx;
  }

  .loading-text {
    font-size: 30rpx;
    color: #303133;
    font-weight: 600;
  }

  .loading-sub {
    font-size: 24rpx;
    color: #909399;
    margin-top: 8rpx;
  }

  .big-icon {
    font-size: 120rpx;
    margin-bottom: 24rpx;
  }

  .big-title {
    font-size: 40rpx;
    font-weight: 700;
    margin-bottom: 12rpx;

    .pass & {
      color: #67c23a;
    }

    .fail & {
      color: #f56c6c;
    }
  }

  .big-sub {
    font-size: 28rpx;
    color: #606266;
  }

  .big-tip {
    margin-top: 16rpx;
    padding: 12rpx 32rpx;
    border-radius: 999rpx;
    background: #f0f9eb;
    color: #67c23a;
    font-size: 28rpx;
    font-weight: 600;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.detail-list {
  .detail-head,
  .detail-row {
    display: flex;
    align-items: center;
    gap: 8rpx;
    font-size: 22rpx;

    .col-type {
      flex: 0 0 140rpx;
    }

    .col-hash {
      flex: 1;
      color: #606266;
      text-align: center;
    }

    .col-match {
      flex: 0 0 140rpx;
      text-align: right;
    }
  }

  .detail-head {
    color: #909399;
    padding-bottom: 12rpx;
    border-bottom: 1rpx solid #f0f2f5;
    margin-bottom: 12rpx;
  }

  .detail-row {
    padding: 16rpx 0;
    border-bottom: 1rpx solid #f0f2f5;

    &:last-child {
      border-bottom: none;
    }

    .col-type {
      color: #303133;
      font-weight: 600;
    }

    .col-match {
      &.ok {
        color: #67c23a;
      }

      &.no {
        color: #f56c6c;
      }
    }
  }
}
</style>
