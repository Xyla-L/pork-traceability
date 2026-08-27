<template>
  <view class="page">
    <view v-if="loading" class="skeleton">
      <view class="sk-block tall"></view>
    </view>

    <template v-else-if="detail">
      <!-- 状态卡片 -->
      <view class="card status-card">
        <view class="status-badge" :class="statusClass(detail.status)">
          {{ detail.statusLabel }}
        </view>
        <view class="status-no">{{ detail.reportNo }}</view>
        <view class="status-time">提交时间：{{ detail.createTime }}</view>
      </view>

      <!-- 举报信息 -->
      <view class="card">
        <view class="section-title">举报信息</view>
        <view class="info-row">
          <text class="info-label">举报人</text>
          <text class="info-value">{{ detail.reporterName }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">联系电话</text>
          <text class="info-value">{{ detail.reporterPhone || '匿名' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">二维码</text>
          <text class="info-value">{{ detail.targetQrCode }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">批次号</text>
          <text class="info-value">{{ detail.targetBatch }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">问题描述</text>
          <text class="info-value">{{ detail.complaintText }}</text>
        </view>
      </view>

      <!-- 监管回复 -->
      <view class="card">
        <view class="section-title">处理回复</view>
        <view v-if="detail.reply" class="reply">
          <view class="reply-content">{{ detail.reply }}</view>
          <view class="reply-time">{{ detail.replyTime }}</view>
        </view>
        <view v-else class="reply-empty">暂未处理回复</view>
      </view>
    </template>

    <view v-else class="card">
      <EmptyState icon="⚠️" text="举报信息不存在" show-retry @retry="load" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useComplaintStore } from '@/stores/complaint'
import EmptyState from '@/components/EmptyState.vue'

const complaintStore = useComplaintStore()
const loading = ref(false)
const detail = ref(null)
const id = ref('')

onLoad((options) => {
  id.value = options.id || ''
  load()
})

async function load() {
  loading.value = true
  try {
    detail.value = await complaintStore.fetchDetail(id.value)
  } finally {
    loading.value = false
  }
}

function statusClass(status) {
  return { 0: 'pending', 1: 'processing', 2: 'done' }[status] || ''
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

.skeleton {
  .sk-block {
    background: #e9edf2;
    border-radius: 16rpx;

    &.tall {
      height: 400rpx;
    }
  }
}

.status-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx;

  .status-badge {
    font-size: 28rpx;
    font-weight: 700;
    padding: 8rpx 32rpx;
    border-radius: 999rpx;
    margin-bottom: 16rpx;

    &.pending {
      background: #fdf6ec;
      color: #e6a23c;
    }

    &.processing {
      background: #ecf5ff;
      color: #409eff;
    }

    &.done {
      background: #f0f9eb;
      color: #67c23a;
    }
  }

  .status-no {
    font-size: 30rpx;
    color: #303133;
    font-weight: 600;
  }

  .status-time {
    font-size: 22rpx;
    color: #909399;
    margin-top: 8rpx;
  }
}

.info-row {
  display: flex;
  gap: 20rpx;
  padding: 12rpx 0;

  .info-label {
    flex: 0 0 140rpx;
    color: #909399;
    font-size: 26rpx;
  }

  .info-value {
    flex: 1;
    color: #303133;
    font-size: 26rpx;
    word-break: break-all;
  }
}

.reply {
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 20rpx;

  .reply-content {
    font-size: 26rpx;
    color: #303133;
    margin-bottom: 12rpx;
  }

  .reply-time {
    font-size: 22rpx;
    color: #c0c4cc;
    text-align: right;
  }
}

.reply-empty {
  font-size: 26rpx;
  color: #c0c4cc;
  padding: 20rpx 0;
}
</style>
