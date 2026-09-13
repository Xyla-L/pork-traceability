<template>
  <view class="page">
    <!-- 隐藏 canvas：用于从相册图片解析二维码像素 -->
    <canvas id="scan-canvas" type="2d" class="scan-canvas" />
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
        placeholder="输入批次号 / 二维码搜索"
        confirm-type="search"
        @confirm="handleSearch"
      />
      <view class="search-btn" @click="handleSearch">搜索</view>
    </view>

    <!-- 我的举报入口 -->
    <view class="card complaint-entry" @click="goComplaint">
      <view class="complaint-entry-left">
        <text class="complaint-entry-icon">📋</text>
        <text class="complaint-entry-text">我的举报</text>
      </view>
      <text class="recent-arrow">›</text>
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
import { onShow } from '@dcloudio/uni-app'
import jsQR from 'jsqr'
import EmptyState from '@/components/EmptyState.vue'
import { getRecentScans } from '@/utils/recentScans'

const keyword = ref('')
const recentList = ref(getRecentScans())

// 每次回到首页刷新缓存（扫完返回时能看到新记录）
onShow(() => {
  recentList.value = getRecentScans()
})

async function handleScan() {
  let path = ''
  try {
    path = await chooseImage()
    const qrCode = await decodeQr(path)
    if (qrCode) {
      uni.navigateTo({ url: `/pages/scan-result/scan-result?qrCode=${encodeURIComponent(qrCode)}` })
    } else {
      uni.showToast({ title: '未识别到二维码', icon: 'none' })
    }
  } catch (e) {
    // 用户取消选图不提示
    if (e?.message !== 'cancel') {
      uni.showToast({ title: e?.message || '解析失败，请重试', icon: 'none' })
    }
  }
}

/** 从相册选择一张图片，返回临时路径；取消时 reject('cancel') */
function chooseImage() {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      success: (res) => {
        const p = res.tempFilePaths?.[0]
        p ? resolve(p) : reject(new Error('未选择图片'))
      },
      fail: () => reject(new Error('cancel')),
    })
  })
}

/** 读取图片尺寸，画到 canvas 上取像素，用 jsQR 解析二维码内容 */
function decodeQr(path) {
  return new Promise((resolve, reject) => {
    uni.getImageInfo({
      src: path,
      success: (info) => {
        const width = info.width
        const height = info.height
        uni.createSelectorQuery()
          .select('#scan-canvas')
          .fields({ node: true, size: true })
          .exec((res) => {
            const canvas = res?.[0]?.node
            if (!canvas) return reject(new Error('画布初始化失败'))
            canvas.width = width
            canvas.height = height
            const ctx = canvas.getContext('2d')
            const img = canvas.createImage()
            img.onload = () => {
              ctx.drawImage(img, 0, 0, width, height)
              const imageData = ctx.getImageData(0, 0, width, height)
              const code = jsQR(imageData.data, width, height)
              resolve(code?.data || '')
            }
            img.onerror = () => reject(new Error('图片加载失败'))
            img.src = path
          })
      },
      fail: () => reject(new Error('读取图片失败')),
    })
  })
}

function handleSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    uni.showToast({ title: '请输入搜索内容', icon: 'none' })
    return
  }
  // 区分二维码号 / 批次号：以 QR- 开头按二维码扫，否则按批次号搜索
  const isQr = /^QR-/i.test(kw)
  const query = isQr ? `qrCode=${encodeURIComponent(kw)}` : `keyword=${encodeURIComponent(kw)}`
  uni.navigateTo({ url: `/pages/scan-result/scan-result?${query}` })
}

function handleOpen(item) {
  uni.navigateTo({ url: `/pages/scan-result/scan-result?qrCode=${encodeURIComponent(item.qrCode)}` })
}

function goComplaint() {
  uni.navigateTo({ url: '/pages/complaint-list/complaint-list' })
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

/* 隐藏 canvas：仅用于解析二维码像素，不占布局、不可见 */
.scan-canvas {
  position: fixed;
  left: -9999rpx;
  top: 0;
  width: 300px;
  height: 300px;
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

.complaint-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  margin-bottom: 20rpx;

  .complaint-entry-left {
    display: flex;
    align-items: center;
    gap: 16rpx;

    .complaint-entry-icon {
      font-size: 40rpx;
    }

    .complaint-entry-text {
      font-size: 30rpx;
      font-weight: 600;
      color: #303133;
    }
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
