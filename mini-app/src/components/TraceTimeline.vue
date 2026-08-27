<template>
  <view class="tt">
    <view v-for="(node, idx) in nodes" :key="idx" class="tt-node">
      <view class="tt-rail">
        <view class="tt-dot" :class="{ done: node.done, active: node.active }"></view>
        <view v-if="idx < nodes.length - 1" class="tt-line" :class="{ done: node.done }"></view>
      </view>
      <view class="tt-content">
        <view class="tt-title" :class="{ active: node.active }">
          <text class="tt-icon">{{ node.icon }}</text>
          <text>{{ node.title }}</text>
        </view>
        <view class="tt-time">{{ node.time }}</view>
        <view v-if="node.desc" class="tt-desc">{{ node.desc }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 溯源链路数据 */
  chain: {
    type: Object,
    required: true,
  },
})

const nodes = computed(() => {
  const c = props.chain
  const list = [
    {
      key: 'farm',
      title: '养殖',
      icon: '🐷',
      time: c.farm?.name || '--',
      desc: c.farm ? `${c.farm.breed} · 耳标 ${c.farm.earTagNo}` : '',
      done: true,
      active: false,
    },
    {
      key: 'slaughter',
      title: '屠宰',
      icon: '🔪',
      time: c.slaughter?.slaughterhouse || '--',
      desc: c.slaughter ? `${c.slaughter.inspectResult} · 瘦肉精${c.slaughter.ractopamine}` : '',
      done: true,
      active: false,
    },
    {
      key: 'split',
      title: '分割',
      icon: '🍖',
      time: c.splitWorkshop?.name || '--',
      desc: c.splitWorkshop ? `${c.splitWorkshop.productName} · ${c.splitWorkshop.packageType}` : '',
      done: true,
      active: false,
    },
    {
      key: 'transport',
      title: '运输',
      icon: '🚚',
      time: c.transport?.transportNo || '--',
      desc: c.transport ? `${c.transport.vehicleNo} · 均温 ${c.transport.avgTemp}℃` : '',
      done: true,
      active: false,
    },
    {
      key: 'store',
      title: '销售',
      icon: '🏪',
      time: c.storeReceipt?.storeName || '--',
      desc: c.storeReceipt ? `签收于 ${c.storeReceipt.receiptTime}` : '',
      done: true,
      active: true,
    },
  ]
  return list
})
</script>

<style lang="scss" scoped>
.tt {
  .tt-node {
    display: flex;
    gap: 20rpx;

    .tt-rail {
      display: flex;
      flex-direction: column;
      align-items: center;
      width: 24rpx;
      flex-shrink: 0;

      .tt-dot {
        width: 20rpx;
        height: 20rpx;
        border-radius: 50%;
        background: #c0c4cc;
        border: 4rpx solid #fff;
        box-shadow: 0 0 0 2rpx #c0c4cc;
        flex-shrink: 0;

        &.done {
          background: #67c23a;
          box-shadow: 0 0 0 2rpx #67c23a;
        }

        &.active {
          background: #409eff;
          box-shadow: 0 0 0 2rpx #409eff;
        }
      }

      .tt-line {
        width: 4rpx;
        flex: 1;
        min-height: 40rpx;
        background: #e4e7ed;

        &.done {
          background: #67c23a;
        }
      }
    }

    .tt-content {
      flex: 1;
      padding-bottom: 32rpx;

      .tt-title {
        display: flex;
        align-items: center;
        gap: 8rpx;
        font-size: 30rpx;
        font-weight: 600;
        color: #303133;
        margin-bottom: 4rpx;

        &.active {
          color: #409eff;
        }

        .tt-icon {
          font-size: 28rpx;
        }
      }

      .tt-time {
        font-size: 24rpx;
        color: #909399;
      }

      .tt-desc {
        font-size: 24rpx;
        color: #606266;
        margin-top: 4rpx;
      }
    }
  }
}
</style>
