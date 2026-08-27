<template>
  <view class="page">
    <!-- 骨架屏 -->
    <view v-if="loading" class="skeleton">
      <view class="sk-block"></view>
      <view class="sk-block"></view>
      <view class="sk-block tall"></view>
    </view>

    <!-- 内容 -->
    <view v-else-if="scanResult">
      <!-- 产品信息 -->
      <ProductInfoCard :product="scanResult.product" />

      <!-- 溯源链路 -->
      <view class="card">
        <view class="section-title">📍 溯源链路</view>
        <TraceTimeline :chain="scanResult.traceChain" />
      </view>

      <!-- 安心购面板 -->
      <SafeBuyPanel
        :verified="blockchainVerified"
        :certs="safeBuyCerts"
        :reports="safeBuyReports"
      />

      <!-- 安心购详情入口 -->
      <view class="safe-buy-entry" @click="handleSafeBuy">
        <text class="entry-text">查看完整安心购详情（证章画廊 / 检测报告 / 链上时间轴）</text>
        <text class="entry-arrow">›</text>
      </view>

      <!-- 区块链记录 -->
      <view class="card">
        <view class="section-title">🔗 区块链存证</view>
        <view class="chain-count">
          共 {{ scanResult.traceChain.blockchain.recordCount }} 条存证记录
        </view>
        <view v-for="(rec, idx) in scanResult.traceChain.blockchain.records" :key="idx" class="chain-item">
          <text class="chain-type">{{ rec.type }}</text>
          <text class="chain-tx">{{ rec.txHash }}</text>
          <text class="chain-block">区块 {{ rec.blockNumber }}</text>
        </view>
      </view>

      <!-- 底部占位（避免被悬浮按钮遮挡） -->
      <view class="bottom-space"></view>
    </view>

    <!-- 空/错误状态 -->
    <view v-else class="card">
      <EmptyState icon="⚠️" text="未找到该产品的溯源信息" show-retry @retry="load" />
    </view>

    <!-- 底部悬浮操作栏 -->
    <view v-if="scanResult" class="bottom-bar">
      <view class="bar-report" @click="handleComplaint">
        <text>⚠️ 举报</text>
      </view>
      <view class="bar-verify" @click="handleVerify">
        <text>🔍 一键区块链验真</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useProductStore } from '@/stores/product'
import ProductInfoCard from '@/components/ProductInfoCard.vue'
import TraceTimeline from '@/components/TraceTimeline.vue'
import SafeBuyPanel from '@/components/SafeBuyPanel.vue'
import EmptyState from '@/components/EmptyState.vue'

const productStore = useProductStore()
const loading = ref(false)
const qrCode = ref('')
const safeBuy = ref(null)

const scanResult = computed(() => productStore.scanResult)
const blockchainVerified = computed(() => safeBuy.value?.blockchain?.verified ?? scanResult.value?.traceChain?.blockchain?.verified ?? false)
const safeBuyCerts = computed(() => safeBuy.value?.certChain || [])
const safeBuyReports = computed(() => safeBuy.value?.reports || [])

onLoad(async (options) => {
  qrCode.value = decodeURIComponent(options.qrCode || '')
  await load()
})

async function load() {
  loading.value = true
  try {
    await productStore.fetchScan(qrCode.value)
    try {
      safeBuy.value = await productStore.fetchSafeBuy(qrCode.value)
    } catch {
      // 安心购数据失败不阻断主流程
      safeBuy.value = null
    }
  } finally {
    loading.value = false
  }
}

function handleVerify() {
  uni.navigateTo({ url: `/pages/verify/verify?qrCode=${encodeURIComponent(qrCode.value)}` })
}

function handleComplaint() {
  const p = scanResult.value?.product
  uni.navigateTo({
    url: `/pages/complaint/complaint?qrCode=${encodeURIComponent(p?.qrCode || qrCode.value)}&batchNo=${encodeURIComponent(p?.batchNo || '')}`,
  })
}

function handleSafeBuy() {
  uni.navigateTo({ url: `/pages/safe-buy/safe-buy?qrCode=${encodeURIComponent(qrCode.value)}` })
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

.skeleton {
  .sk-block {
    height: 180rpx;
    background: #e9edf2;
    border-radius: 16rpx;
    margin-bottom: 20rpx;

    &.tall {
      height: 400rpx;
    }
  }
}

.safe-buy-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ecf5ff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;

  .entry-text {
    flex: 1;
    font-size: 26rpx;
    color: #409eff;
  }

  .entry-arrow {
    font-size: 36rpx;
    color: #409eff;
    margin-left: 12rpx;
  }
}

.chain-count {
  font-size: 24rpx;
  color: #606266;
  margin-bottom: 16rpx;
}

.chain-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f2f5;

  &:last-child {
    border-bottom: none;
  }

  .chain-type {
    flex-shrink: 0;
    font-size: 26rpx;
    color: #303133;
    font-weight: 600;
  }

  .chain-tx {
    flex: 1;
    font-size: 24rpx;
    color: #909399;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .chain-block {
    flex-shrink: 0;
    font-size: 22rpx;
    color: #c0c4cc;
  }
}

.bottom-space {
  height: 120rpx;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx 24rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.06);

  .bar-report {
    width: 160rpx;
    height: 80rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12rpx;
    border: 2rpx solid #f56c6c;
    color: #f56c6c;
    font-size: 26rpx;
  }

  .bar-verify {
    flex: 1;
    height: 80rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12rpx;
    background: #409eff;
    color: #fff;
    font-size: 28rpx;
    font-weight: 600;
  }
}
</style>
