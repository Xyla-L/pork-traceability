<template>
  <view class="page">
    <!-- 验真中 -->
    <view v-if="verifying" class="verify-center">
      <view class="loading-spinner"></view>
      <text class="loading-text">正在核对存证…</text>
      <text class="loading-sub">逐条比对存证指纹</text>
    </view>

    <!-- 结果 -->
    <template v-else-if="result">
      <!-- 通过：标题用后端给的 chainStatus，载体变了文案自动跟着变 -->
      <view v-if="passed" class="verify-center pass">
        <view class="big-title">{{ result.chainStatus || (onChain ? '区块链已验证' : '本地存证已核对') }}</view>
        <view class="big-sub">共 {{ count }} 条存证记录全部一致</view>
        <view class="big-tip">存证内容未被改动</view>
        <view class="big-note">
          {{ onChain ? '存证已写入区块链，可凭交易哈希独立核验' : '记录存于平台本地账本，未写入区块链' }}
        </view>
      </view>

      <!-- 请求失败（作废、售出、码不存在）：把后端原话给用户 -->
      <view v-else-if="failed" class="verify-center fail">
        <view class="big-title">{{ result.chainStatus || '无法完成核验' }}</view>
        <view class="big-sub">{{ result.detail }}</view>
      </view>

      <!-- 没有任何存证：不能说成「被篡改」，那是不成立的指控 -->
      <view v-else-if="count === 0" class="verify-center pending">
        <view class="big-title">暂无存证记录</view>
        <view class="big-sub">该产品还没有可核对的存证，可能刚上架或存证仍在写入</view>
      </view>

      <!-- 有记录但不一致 -->
      <view v-else class="verify-center fail">
        <view class="big-title">{{ result.chainStatus || '存证核对未通过' }}</view>
        <view class="big-sub">{{ result.detail || `有 ${unmatched} 条记录与存证指纹不一致` }}</view>
        <view class="big-note">不一致可能是记录被改动，也可能是存证尚未写入或缺失，请与商家核实后再决定</view>
      </view>

      <!-- 逐条验真明细 -->
      <view v-if="count > 0" class="card detail-list">
        <view class="section-title">逐条验真明细</view>
        <view class="detail-head">
          <text class="col-type">业务类型</text>
          <text class="col-hash">{{ onChain ? '平台指纹' : '存证指纹' }}</text>
          <text v-if="onChain" class="col-hash">链上指纹</text>
          <text class="col-match">结果</text>
        </view>
        <view v-for="(d, idx) in result.details" :key="idx" class="detail-row">
          <text class="col-type">{{ bizTypeLabel(d.type) }}</text>
          <text class="col-hash">{{ shortHash(d.localHash, 8, 6) || '—' }}</text>
          <text v-if="onChain" class="col-hash">{{ shortHash(d.chainHash, 8, 6) || '未上链' }}</text>
          <text class="col-match" :class="d.match ? 'ok' : 'no'">{{ d.match ? '一致' : '不一致' }}</text>
        </view>
      </view>
    </template>

    <!-- 初始/空状态 -->
    <view v-else class="card">
      <EmptyState icon="" text="尚未开始验真" show-retry @retry="startVerify" />
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useProductStore } from '@/stores/product'
import EmptyState from '@/components/EmptyState.vue'
import { bizTypeLabel, shortHash } from '@/utils/bizType'

const productStore = useProductStore()
const verifying = ref(false)
const result = ref(null)
const failed = ref(false)
const qrCode = ref('')

// 载体由后端自报：本地账本模式下不能说「区块链已验证」，也不能说「链上指纹」
const onChain = computed(() => result.value?.onChain === true)
const count = computed(() => (result.value?.details || []).length)
const unmatched = computed(() => (result.value?.details || []).filter((d) => !d.match).length)
const passed = computed(() => result.value?.verified === true && count.value > 0)

onLoad((options) => {
  qrCode.value = decodeURIComponent(options.qrCode || '')
  startVerify()
})

async function startVerify() {
  verifying.value = true
  result.value = null
  failed.value = false
  try {
    result.value = await productStore.verify(qrCode.value)
  } catch (e) {
    failed.value = true
    // 用后端原话：可能是「商品已作废」这类业务说明，不该一律说成网络异常
    result.value = { verified: false, details: [], chainStatus: '无法完成核验', detail: e?.message || '网络异常，请稍后重试' }
  } finally {
    verifying.value = false
    // 页面标题随载体走：本地账本模式还叫「区块链验真」就是虚标
    uni.setNavigationBarTitle({ title: onChain.value ? '区块链验真' : '存证核对' })
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

    .pending & {
      color: #e6a23c;
    }
  }

  .big-sub {
    font-size: 28rpx;
    color: #606266;
    text-align: center;
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

  // 说明性小字：讲清这份结论的边界，不用它当卖点
  .big-note {
    margin-top: 20rpx;
    font-size: 22rpx;
    color: #909399;
    line-height: 1.6;
    text-align: center;
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
      flex: 0 0 150rpx;
    }

    .col-hash {
      flex: 1;
      color: #606266;
      text-align: center;
    }

    .col-match {
      flex: 0 0 90rpx;
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
