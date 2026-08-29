<template>
  <view class="page">
    <!-- 状态筛选 -->
    <view class="filter">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="filter-item"
        :class="{ active: currentStatus === tab.value }"
        @click="switchTab(tab.value)"
      >
        {{ tab.label }}
      </view>
    </view>

    <!-- 列表 -->
    <view v-if="loading" class="skeleton">
      <view class="sk-block"></view>
      <view class="sk-block"></view>
    </view>

    <view v-else-if="list.length === 0">
      <EmptyState icon="📋" text="暂无举报记录" />
    </view>

    <view v-else>
      <view v-for="item in list" :key="item.id" class="complaint-card" @click="openDetail(item)">
        <view class="cc-head">
          <text class="cc-no">{{ item.reportNo }}</text>
          <text class="cc-status" :class="statusClass(item.status)">{{ item.statusLabel }}</text>
        </view>
        <view class="cc-text">{{ item.complaintText }}</view>
        <view class="cc-meta">
          <text>举报人：{{ item.reporterName }}</text>
          <text>{{ item.createTime }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useComplaintStore } from '@/stores/complaint'
import EmptyState from '@/components/EmptyState.vue'

const complaintStore = useComplaintStore()
const loading = ref(false)
const currentStatus = ref('')

const tabs = [
  { label: '全部', value: '' },
  { label: '待处理', value: 0 },
  { label: '已受理', value: 1 },
  { label: '已办结', value: 2 },
]

const list = computed(() => complaintStore.list)

onShow(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    await complaintStore.fetchList(currentStatus.value === '' ? null : currentStatus.value)
  } finally {
    loading.value = false
  }
}

function switchTab(val) {
  currentStatus.value = val
  fetchList()
}

function statusClass(status) {
  return { 0: 'pending', 1: 'processing', 2: 'done' }[status] || ''
}

function openDetail(item) {
  uni.navigateTo({ url: `/pages/complaint-detail/complaint-detail?id=${item.id}` })
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

.filter {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;

  .filter-item {
    flex: 1;
    height: 72rpx;
    line-height: 72rpx;
    text-align: center;
    background: #fff;
    border-radius: 12rpx;
    font-size: 26rpx;
    color: #606266;

    &.active {
      background: #409eff;
      color: #fff;
      font-weight: 600;
    }
  }
}

.skeleton {
  .sk-block {
    height: 180rpx;
    background: #e9edf2;
    border-radius: 16rpx;
    margin-bottom: 20rpx;
  }
}

.complaint-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;

  .cc-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12rpx;

    .cc-no {
      font-size: 28rpx;
      font-weight: 700;
      color: #303133;
    }

    .cc-status {
      font-size: 22rpx;
      padding: 4rpx 16rpx;
      border-radius: 999rpx;

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
  }

  .cc-text {
    font-size: 26rpx;
    color: #606266;
    margin-bottom: 12rpx;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .cc-meta {
    display: flex;
    justify-content: space-between;
    font-size: 22rpx;
    color: #c0c4cc;
  }
}
</style>
