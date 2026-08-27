<template>
  <view class="page">
    <!-- 骨架屏 -->
    <view v-if="loading" class="skeleton">
      <view class="sk-block"></view>
      <view class="sk-block tall"></view>
      <view class="sk-block tall"></view>
    </view>

    <template v-else-if="data">
      <!-- 顶部徽章 -->
      <view class="sb-top">
        <BlockchainBadge :verified="data.blockchain.verified" />
        <text class="sb-top-count">共 {{ data.blockchain.recordCount }} 条链上记录</text>
      </view>

      <!-- 检疫合格章画廊 -->
      <view class="card">
        <view class="section-title">🏅 检疫合格章</view>
        <scroll-view scroll-x class="gallery">
          <view class="gallery-row">
            <view v-for="(cert, idx) in data.certChain" :key="idx" class="cert-card">
              <view class="cert-stamp">{{ cert.photo ? '' : '✅' }}</view>
              <view class="cert-type">{{ cert.type }}</view>
              <view class="cert-no">{{ cert.certNo }}</view>
              <view class="cert-meta">{{ cert.issueOrg }}</view>
              <view class="cert-meta">{{ cert.issueTime }}</view>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 检测报告列表 -->
      <view class="card">
        <view class="section-title">📋 检测报告</view>
        <ReportSummary :reports="data.reports" />
      </view>

      <!-- 区块链存证时间轴 -->
      <view class="card">
        <view class="section-title">🔗 区块链存证时间轴</view>
        <view class="chain-timeline">
          <view v-for="(rec, idx) in data.chainRecords" :key="idx" class="ct-node">
            <view class="ct-rail">
              <view class="ct-dot"></view>
              <view v-if="idx < data.chainRecords.length - 1" class="ct-line"></view>
            </view>
            <view class="ct-content">
              <view class="ct-desc">{{ rec.desc }}</view>
              <view class="ct-time">{{ rec.time }}</view>
              <view class="ct-tx">{{ rec.txHash }} · 区块 {{ rec.blockNumber }}</view>
            </view>
          </view>
        </view>
      </view>
    </template>

    <view v-else class="card">
      <EmptyState icon="⚠️" text="暂无安心购数据" show-retry @retry="load" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useProductStore } from '@/stores/product'
import BlockchainBadge from '@/components/BlockchainBadge.vue'
import ReportSummary from '@/components/ReportSummary.vue'
import EmptyState from '@/components/EmptyState.vue'

const productStore = useProductStore()
const loading = ref(false)
const data = ref(null)
const qrCode = ref('')

onLoad(async (options) => {
  qrCode.value = decodeURIComponent(options.qrCode || '')
  await load()
})

async function load() {
  loading.value = true
  try {
    data.value = await productStore.fetchSafeBuy(qrCode.value)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

.skeleton {
  .sk-block {
    height: 120rpx;
    background: #e9edf2;
    border-radius: 16rpx;
    margin-bottom: 20rpx;

    &.tall {
      height: 360rpx;
    }
  }
}

.sb-top {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;

  .sb-top-count {
    font-size: 24rpx;
    color: #909399;
  }
}

.gallery {
  width: 100%;
  white-space: nowrap;

  .gallery-row {
    display: inline-flex;
    gap: 20rpx;
    padding-bottom: 8rpx;

    .cert-card {
      display: inline-flex;
      flex-direction: column;
      width: 280rpx;
      padding: 24rpx;
      background: #fafafa;
      border-radius: 16rpx;
      border: 1rpx solid #f0f2f5;

      .cert-stamp {
        font-size: 48rpx;
        text-align: center;
        margin-bottom: 12rpx;
      }

      .cert-type {
        font-size: 26rpx;
        font-weight: 600;
        color: #303133;
        margin-bottom: 8rpx;
      }

      .cert-no {
        font-size: 22rpx;
        color: #606266;
        margin-bottom: 8rpx;
      }

      .cert-meta {
        font-size: 20rpx;
        color: #909399;
      }
    }
  }
}

.chain-timeline {
  .ct-node {
    display: flex;
    gap: 20rpx;

    .ct-rail {
      display: flex;
      flex-direction: column;
      align-items: center;
      width: 24rpx;
      flex-shrink: 0;

      .ct-dot {
        width: 16rpx;
        height: 16rpx;
        border-radius: 50%;
        background: #409eff;
        flex-shrink: 0;
      }

      .ct-line {
        width: 4rpx;
        flex: 1;
        min-height: 40rpx;
        background: #e4e7ed;
      }
    }

    .ct-content {
      flex: 1;
      padding-bottom: 28rpx;

      .ct-desc {
        font-size: 28rpx;
        color: #303133;
        font-weight: 600;
        margin-bottom: 4rpx;
      }

      .ct-time {
        font-size: 22rpx;
        color: #909399;
      }

      .ct-tx {
        font-size: 20rpx;
        color: #c0c4cc;
      }
    }
  }
}
</style>
