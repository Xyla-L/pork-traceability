<template>
  <view class="report-summary">
    <view v-for="(report, idx) in reports" :key="idx" class="report-item">
      <view class="report-head" @click="toggle(idx)">
        <text class="report-status" :class="report.pass ? 'pass' : 'fail'">
          {{ report.pass ? '✅' : '⚠️' }}
        </text>
        <text class="report-title">{{ report.title }}</text>
        <text class="report-arrow">{{ expanded[idx] ? '▾' : '▸' }}</text>
      </view>
      <view class="report-summary">{{ report.summary }}</view>
      <view v-if="expanded[idx]" class="report-detail">{{ report.detail }}</view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from 'vue'

defineProps({
  reports: {
    type: Array,
    default: () => [],
  },
})

const expanded = reactive({})

function toggle(idx) {
  expanded[idx] = !expanded[idx]
}
</script>

<style lang="scss" scoped>
.report-summary {
  .report-item {
    padding: 20rpx 0;
    border-bottom: 1rpx solid #f0f2f5;

    &:last-child {
      border-bottom: none;
    }

    .report-head {
      display: flex;
      align-items: center;
      gap: 12rpx;

      .report-status {
        font-size: 28rpx;
      }

      .report-title {
        flex: 1;
        font-size: 28rpx;
        font-weight: 600;
        color: #303133;
      }

      .report-arrow {
        font-size: 28rpx;
        color: #c0c4cc;
      }
    }

    .report-summary {
      font-size: 24rpx;
      color: #606266;
      margin-top: 8rpx;
      padding-left: 40rpx;
    }

    .report-detail {
      font-size: 24rpx;
      color: #909399;
      margin-top: 8rpx;
      padding: 16rpx;
      background: #f5f7fa;
      border-radius: 12rpx;
    }
  }
}
</style>
