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
      <ProductInfoCard :product="normalizedProduct" />

      <!-- 溯源链路 -->
      <view class="card">
        <view class="section-title">📍 溯源链路</view>
        <TraceTimeline :chain="normalizedChain" />
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
          共 {{ normalizedChain.blockchain.recordCount }} 条存证记录
        </view>
        <view v-for="(rec, idx) in normalizedChain.blockchain.records" :key="idx" class="chain-item">
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
import { addRecentScan } from '@/utils/recentScans'
import ProductInfoCard from '@/components/ProductInfoCard.vue'
import TraceTimeline from '@/components/TraceTimeline.vue'
import SafeBuyPanel from '@/components/SafeBuyPanel.vue'
import EmptyState from '@/components/EmptyState.vue'

const productStore = useProductStore()
const loading = ref(false)
const qrCode = ref('')
const keyword = ref('')
const safeBuy = ref(null)

const scanResult = computed(() => productStore.scanResult)
const blockchainVerified = computed(() => safeBuy.value?.blockchain?.verified ?? scanResult.value?.verification?.verified ?? false)
const safeBuyCerts = computed(() => safeBuy.value?.certChain || [])
const safeBuyReports = computed(() => safeBuy.value?.reports || [])

// 后端真实返回的是 upstream/downstream/breeding/slaughter/blockchain 树，
// 而 TraceTimeline 组件期望 farm/slaughter/splitWorkshop/transport/storeReceipt 扁平结构，
// 这里做一层转换。
const normalizedChain = computed(() => normalizeTraceChain(scanResult.value?.traceChain))

// 后端 product 没有 weight 字段，规格信息在 batchWeightKg / packageCount 里，补一个 weight 供卡片展示
const normalizedProduct = computed(() => {
  const p = scanResult.value?.product
  if (!p) return p
  const weight = p.batchWeightKg != null
    ? `${p.batchWeightKg}kg · ${p.packageCount}包`
    : (p.weight || '--')
  return { ...p, weight }
})

onLoad(async (options) => {
  qrCode.value = decodeURIComponent(options.qrCode || '')
  keyword.value = decodeURIComponent(options.keyword || '')
  await load()
})

async function load() {
  loading.value = true
  try {
    if (qrCode.value) {
      // 扫码 / 二维码搜索
      await productStore.fetchScan(qrCode.value)
      console.log('[scan-result] fetchScan 成功, qrCode=', qrCode.value, 'name=', productStore.scanResult?.product?.name)
      addRecentScan({ qrCode: qrCode.value, name: productStore.scanResult?.product?.name, type: 'qr' })
      try {
        safeBuy.value = await productStore.fetchSafeBuy(qrCode.value)
      } catch {
        safeBuy.value = null
      }
    } else if (keyword.value) {
      // 批次号搜索
      await productStore.fetchSearch(keyword.value)
      addRecentScan({ qrCode: keyword.value, name: productStore.scanResult?.product?.name, type: 'batch' })
      safeBuy.value = null
    }
  } finally {
    loading.value = false
  }
}

// 区块链存证类型 → 中文展示名（未收录的类型原样展示）
const BIZ_TYPE_LABEL = {
  RETAIL_SALE: '销售激活',
  SPLIT_BATCH: '批次分割',
  CARCASS_BATCH: '屠宰分割',
}

/**
 * 把后端 traceChain（upstream/downstream/breeding/slaughter/blockchain）
 * 转成 TraceTimeline 期望的扁平结构。
 */
function normalizeTraceChain(tc) {
  const empty = {
    farm: null, slaughter: null, splitWorkshop: null, transport: null, storeReceipt: null,
    blockchain: { recordCount: 0, records: [] },
  }
  if (!tc) return empty
  const upstream = Array.isArray(tc.upstream) ? tc.upstream : []
  const breedingList = Array.isArray(tc.breeding) ? tc.breeding : []
  const slaughterList = Array.isArray(tc.slaughter) ? tc.slaughter : []
  const logistics = Array.isArray(tc.downstream?.logistics) ? tc.downstream.logistics : []
  const sales = Array.isArray(tc.downstream?.sales) ? tc.downstream.sales : []

  // 养殖：breeding 第一条 + 屠宰入场的 sourceFarm 作为养殖场名
  const pig = breedingList[0] || {}
  const entry = slaughterList[0]?.entries?.[0] || {}
  const farm = {
    name: entry.sourceFarm || pig.farmName || '--',
    breed: pig.breed,
    earTagNo: pig.earTagNo,
  }

  // 屠宰：upstream 里的 CARCASS 节点有屠宰场名；inspection 的 conclusion 为结论；瘦肉精检测记录存在即视为已检
  const carcass = upstream.find((n) => n.type === 'CARCASS')
  const carcassData = carcass?.data || {}
  const inspections = slaughterList[0]?.inspections || []
  const racto = slaughterList[0]?.ractopamineTests || []
  const slaughter = {
    slaughterhouse: carcassData.slaughterhouse,
    inspectResult: inspections.find((i) => i.conclusion)?.conclusion || (inspections.length ? '合格' : '--'),
    ractopamine: racto.length ? '阴性' : '--',
  }

  // 分割：upstream 里的第一个 SPLIT 节点（即当前产品）
  const splitNode = upstream.find((n) => n.type === 'SPLIT')
  const splitData = splitNode?.data || {}
  const splitWorkshop = {
    name: splitData.workshop,
    productName: splitData.productName,
    packageType: splitData.packageType,
  }

  // 运输：logistics 第一条的 transport + temperatureLogs 算均温
  const logi = logistics[0]
  const transportRaw = logi?.transport || {}
  const temps = logi?.temperatureLogs || []
  const avgTemp = temps.length
    ? (temps.reduce((sum, t) => sum + (Number(t.temperature) || 0), 0) / temps.length).toFixed(1)
    : '--'
  const transport = {
    transportNo: transportRaw.transportNo,
    vehicleNo: transportRaw.vehicleNo,
    avgTemp,
  }

  // 销售：logistics 的 receipt 优先，否则取 sales 第一条
  const receipt = logi?.receipt
  const sale = sales[0] || {}
  const storeReceipt = {
    storeName: receipt?.storeName || sale.storeName,
    receiptTime: receipt?.receiptTime || sale.shelfTime,
  }

  // 区块链：后端返回数组，转成 { recordCount, records } 供页面展示
  const chainArr = Array.isArray(tc.blockchain) ? tc.blockchain : []
  const blockchain = {
    recordCount: chainArr.length,
    records: chainArr.map((r) => ({
      type: BIZ_TYPE_LABEL[r.bizType] || r.bizType,
      txHash: r.txHash,
      blockNumber: r.blockNumber,
    })),
  }

  return { farm, slaughter, splitWorkshop, transport, storeReceipt, blockchain }
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
