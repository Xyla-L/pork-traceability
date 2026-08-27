<template>
  <view class="page">
    <!-- 头部品牌区 -->
    <view class="hero">
      <view class="hero-logo">🐷</view>
      <view class="hero-title">猪肉产品质量安全溯源</view>
      <view class="hero-subtitle">扫码溯源 · 安心购 · 一键验真</view>
    </view>

    <!-- 扫码入口大按钮 -->
    <view class="scan-area">
      <view class="scan-btn" @click="handleScan">
        <text class="scan-icon">📷</text>
        <text class="scan-text">扫一扫溯源</text>
      </view>
    </view>

    <!-- 产品搜索 -->
    <view class="search-box">
      <input
        v-model="keyword"
        class="search-input"
        type="text"
        placeholder="输入批次号 / 产品名搜索"
        confirm-type="search"
        @confirm="handleSearch"
      />
      <view class="search-btn" @click="handleSearch">搜索</view>
    </view>

    <!-- 最近扫码记录 -->
    <view class="card recent">
      <view class="section-title">最近扫码</view>
      <view v-if="recentList.length === 0">
        <EmptyState icon="🗂️" text="暂无扫码记录" />
      </view>
      <view v-else>
        <view
          v-for="item in recentList"
          :key="item.qrCode"
          class="recent-item"
          @click="handleOpen(item)"
        >
          <view class="recent-left">
            <view class="recent-name">{{ item.name }}</view>
            <view class="recent-qr">{{ item.qrCode }}</view>
          </view>
          <view class="recent-right">
            <text class="recent-time">{{ item.time }}</text>
            <text class="recent-arrow">›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import EmptyState from '@/components/EmptyState.vue'

const keyword = ref('')
const recentList = ref([
  { qrCode: 'QR-PORK-2024071500101-A001', name: '猪前腿肉（真空包装）', time: '2024-07-16 10:20' },
  { qrCode: 'QR-PORK-2024071200102-B003', name: '猪里脊肉', time: '2024-07-13 18:05' },
  { qrCode: 'QR-PORK-2024070800103-C001', name: '五花肉', time: '2024-07-09 12:30' },
])

function handleScan() {
  uni.scanCode({
    success: (res) => {
      const qrCode = res.result
      if (qrCode) {
        uni.navigateTo({ url: `/pages/scan-result/scan-result?qrCode=${encodeURIComponent(qrCode)}` })
      }
    },
    fail: () => {
      // 用户取消扫码
    },
  })
}

function handleSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    uni.showToast({ title: '请输入搜索内容', icon: 'none' })
    return
  }
  // 这里以批次号/二维码作为溯源查询条件
  uni.navigateTo({ url: `/pages/scan-result/scan-result?qrCode=${encodeURIComponent(kw)}` })
}

function handleOpen(item) {
  uni.navigateTo({ url: `/pages/scan-result/scan-result?qrCode=${encodeURIComponent(item.qrCode)}` })
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 0 40rpx;

  .hero-logo {
    font-size: 96rpx;
    margin-bottom: 16rpx;
  }

  .hero-title {
    font-size: 40rpx;
    font-weight: 700;
    color: #303133;
    margin-bottom: 8rpx;
  }

  .hero-subtitle {
    font-size: 24rpx;
    color: #909399;
  }
}

.scan-area {
  display: flex;
  justify-content: center;
  padding: 8rpx 0 32rpx;

  .scan-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 320rpx;
    height: 200rpx;
    background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
    border-radius: 24rpx;
    box-shadow: 0 8rpx 24rpx rgba(64, 158, 255, 0.35);

    .scan-icon {
      font-size: 72rpx;
      margin-bottom: 12rpx;
    }

    .scan-text {
      font-size: 30rpx;
      font-weight: 600;
      color: #fff;
    }
  }
}

.search-box {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 16rpx;
  margin-bottom: 24rpx;

  .search-input {
    flex: 1;
    height: 64rpx;
    background: #f5f7fa;
    border-radius: 12rpx;
    padding: 0 24rpx;
    font-size: 28rpx;
  }

  .search-btn {
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 32rpx;
    background: #409eff;
    color: #fff;
    font-size: 28rpx;
    border-radius: 12rpx;
  }
}

.recent {
  .recent-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 24rpx 0;
    border-bottom: 1rpx solid #f0f2f5;

    &:last-child {
      border-bottom: none;
    }

    .recent-left {
      flex: 1;
      min-width: 0;

      .recent-name {
        font-size: 28rpx;
        color: #303133;
        margin-bottom: 6rpx;
      }

      .recent-qr {
        font-size: 22rpx;
        color: #c0c4cc;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .recent-right {
      display: flex;
      align-items: center;
      gap: 12rpx;

      .recent-time {
        font-size: 22rpx;
        color: #909399;
      }

      .recent-arrow {
        font-size: 32rpx;
        color: #c0c4cc;
      }
    }
  }
}
</style>
