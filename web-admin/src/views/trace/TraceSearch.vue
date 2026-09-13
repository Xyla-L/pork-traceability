<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <div class="search-section">
      <div class="search-hero">
        <h2 class="search-title">🔍 应急追溯查询</h2>
        <p class="search-subtitle">输入批次号 / 耳标号 / 二维码 / 产品名，秒级反查上下游关联节点</p>
        <div class="search-input-row">
          <el-input v-model="keyword" placeholder="请输入批次号、耳标号、二维码或产品名称..." size="large"
            clearable @keyup.enter="handleSearch" class="search-input">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" size="large" @click="handleSearch" :loading="searching">
            <el-icon><Search /></el-icon>追溯
          </el-button>
        </div>
        <div class="search-hints">
          <span>快捷搜索：</span>
          <el-tag v-for="hint in searchHints" :key="hint" size="small" class="search-hint-tag" @click="keyword = hint; handleSearch()">
            {{ hint }}
          </el-tag>
        </div>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="searching" class="loading-section">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 溯源结果 -->
    <template v-if="!searching && traceData">
      <!-- 产品信息 -->
      <el-card class="result-card">
        <template #header><span class="card-title">📦 产品信息</span></template>
        <el-descriptions :column="3" border size="default">
          <el-descriptions-item label="产品名称">{{ traceData.product.name }}</el-descriptions-item>
          <el-descriptions-item label="批次号"><el-tag size="small">{{ traceData.product.batchNo }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="重量">{{ traceData.product.weight }}</el-descriptions-item>
          <el-descriptions-item label="包装日期">{{ traceData.product.packageDate }}</el-descriptions-item>
          <el-descriptions-item label="保质期至">{{ traceData.product.expireDate }}</el-descriptions-item>
          <el-descriptions-item label="二维码"><code>{{ traceData.product.qrCode }}</code></el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 溯源关系图 + 验证面板 -->
      <div class="trace-grid">
        <!-- ECharts 力导向图 -->
        <el-card class="trace-graph-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">🕸️ 溯源关系图</span>
              <el-space>
                <el-tag size="small" type="success" v-if="verifyResult?.allVerified">✅ 全链路已验证</el-tag>
                <el-tag size="small" type="danger" v-else>⚠️ 存在验证失败</el-tag>
              </el-space>
            </div>
          </template>
          <div ref="graphChartRef" class="graph-container"></div>
        </el-card>

        <!-- 验真面板 -->
        <el-card class="verify-panel">
          <template #header>
            <div class="card-header">
              <span class="card-title">🔐 区块链验真</span>
              <el-button size="small" type="primary" @click="handleVerify" :loading="verifying">
                <el-icon><Refresh /></el-icon>重新验真
              </el-button>
            </div>
          </template>

          <!-- 验真总览 -->
          <div class="verify-overview" v-if="verifyResult">
            <div class="verify-summary" :class="verifyResult.allVerified ? 'verify-pass' : 'verify-fail'">
              <el-icon :size="36"><component :is="verifyResult.allVerified ? 'CircleCheckFilled' : 'WarningFilled'" /></el-icon>
              <div>
                <div class="verify-summary-title">{{ verifyResult.allVerified ? '全链路验证通过' : '部分验证失败' }}</div>
                <div class="verify-summary-desc">
                  {{ verifyResult.allVerified ? '该产品溯源数据与区块链存证完全一致，可放心使用' : '部分环节数据与链上记录不匹配，请谨慎处理' }}
                </div>
              </div>
            </div>

            <!-- 逐条明细 -->
            <div class="verify-details">
              <div v-for="item in verifyResult.details" :key="item.bizName" class="verify-detail-item">
                <div class="vd-header">
                  <span class="vd-biz">{{ item.bizType }} - {{ item.bizName }}</span>
                  <el-tag :type="item.matched ? 'success' : 'danger'" size="small">
                    {{ item.matched ? '✅ 匹配' : '❌ 不匹配' }}
                  </el-tag>
                </div>
                <div class="vd-hashes">
                  <div class="vd-hash-row">
                    <span class="vd-label">本地哈希</span>
                    <code class="vd-code">{{ item.localHash?.substring(0, 20) }}...</code>
                  </div>
                  <div class="vd-hash-row">
                    <span class="vd-label">链上哈希</span>
                    <code class="vd-code" :class="{ 'hash-mismatch': !item.matched }">{{ item.onChainHash?.substring(0, 20) }}...</code>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <el-empty v-else description='点击"重新验真"开始区块链验证' :image-size="60" />

          <!-- 链上存证记录 -->
          <div class="chain-records" v-if="traceData?.traceChain?.blockchain?.records?.length">
            <div class="vd-biz" style="margin-bottom: 8px;">📋 链上存证记录</div>
            <div v-for="rec in traceData.traceChain.blockchain.records" :key="rec.txHash" class="chain-record-item">
              <span class="cr-type">{{ rec.type }}</span>
              <code class="cr-hash" :title="rec.txHash">{{ rec.txHash?.substring(0, 16) }}...</code>
              <el-tag size="small" type="info">区块 #{{ rec.blockNumber }}</el-tag>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 上下游节点详情表 -->
      <el-card class="detail-table-card">
        <template #header><span class="card-title">📊 全链路节点详情</span></template>
        <el-table :data="nodeDetails" border stripe size="small">
          <el-table-column prop="stage" label="环节" width="80" align="center" />
          <el-table-column prop="title" label="节点名称" min-width="150" />
          <el-table-column prop="time" label="时间" width="170" align="center" />
          <el-table-column prop="org" label="机构" min-width="160" />
          <el-table-column prop="keyInfo" label="关键信息" min-width="200" show-overflow-tooltip />
          <el-table-column prop="chainStatus" label="链上状态" width="100" align="center">
            <template #default="{ row }">
              <BlockchainVerifyBadge :status="row.chainStatus" />
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 空状态 -->
    <el-empty v-if="!searching && !traceData && hasSearched" description="未找到相关溯源数据，请检查输入后重试" :image-size="100">
      <el-button type="primary" @click="keyword = ''; hasSearched = false">重新搜索</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Search, Refresh, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'
import { traceApi } from '@/api/modules/trace'

// 搜索
const keyword = ref('QR-PORK-DEMO-0001')
const hasSearched = ref(false)
const searching = ref(false)
const searchHints = ['SP-DEMO-0002', 'CB-DEMO-0001', 'QR-PORK-DEMO-0001']

// 溯源数据
const traceData = ref<any>(null)
const verifyResult = ref<any>(null)
const verifying = ref(false)

// 节点详情表
const nodeDetails = ref<any[]>([])

// 图表
const graphChartRef = ref<HTMLElement>()
let graphChart: echarts.ECharts | null = null

const pick = (v: any, fallback: any = '-') =>
  v === null || v === undefined || v === '' ? fallback : v

function mapChainRecords(chain: any) {
  const list = Array.isArray(chain) ? chain : []
  return list.map((r: any) => ({
    type: pick(r?.bizType),
    txHash: r?.txHash ?? '',
    blockNumber: r?.blockNumber ?? '-',
  }))
}

function chainStatusOf(full: any): 'confirmed' | 'pending' | 'none' {
  const records = Array.isArray(full?.blockchain) ? full.blockchain : []
  if (!records.length) return 'none'
  return records.every((r: any) => r?.status === 1) ? 'confirmed' : 'pending'
}

// 后端 search 返回两种结构：QR 扫码 {product, traceChain, verification}；批次号 full 结构
// {batchNo, upstream, downstream, breeding, slaughter, blockchain}，统一映射为页面视图模型
function buildTraceData(res: any, full: any, kw: string) {
  const upstream: any[] = Array.isArray(full?.upstream) ? full.upstream : []
  const splitNodes = upstream.filter(n => n?.type === 'SPLIT')
  const carcass = upstream.find(n => n?.type === 'CARCASS')?.data
  const currentSplit = splitNodes[0]?.data
  const sales: any[] = Array.isArray(full?.downstream?.sales) ? full.downstream.sales : []
  const sale = sales[0]
  const product = res?.product && typeof res.product === 'object' ? res.product : {}
  const weight = product.sellWeightKg ?? product.batchWeightKg ?? currentSplit?.weightKg ?? carcass?.totalWeightKg
  return {
    product: {
      name: pick(product.name ?? currentSplit?.productName),
      batchNo: pick(product.batchNo ?? full?.batchNo ?? kw),
      weight: weight !== null && weight !== undefined ? `${weight} kg` : '-',
      packageDate: pick(product.packageDate ?? currentSplit?.splitTime),
      expireDate: pick(product.expireDate ?? sale?.expireDate),
      qrCode: pick(product.productQrCode ?? sale?.productQrCode ?? (kw.toUpperCase().startsWith('QR-') ? kw : '')),
    },
    traceChain: {
      blockchain: { records: mapChainRecords(full?.blockchain) },
    },
  }
}

function buildNodeDetails(full: any) {
  const rows: any[] = []
  const chainStatus = chainStatusOf(full)
  const push = (title: string, time: any, org: any, keyInfo: string) => {
    rows.push({ stage: String(rows.length + 1), title, time: pick(time), org: pick(org), keyInfo, chainStatus })
  }

  const breeding: any[] = Array.isArray(full?.breeding) ? full.breeding : []
  breeding.forEach(pig => {
    push('养殖场', pig?.birthDate ?? pig?.createTime, pig?.farmName,
      `耳标${pick(pig?.earTagNo)} / ${pick(pig?.breed)}`)
  })

  const slaughter: any[] = Array.isArray(full?.slaughter) ? full.slaughter : []
  slaughter.forEach(row => {
    const entry = Array.isArray(row?.entries) ? row.entries[0] : null
    if (entry) {
      push('入场查验', entry.arriveTime, entry.sourceFarm,
        `检疫证${pick(entry.quarantineCert)} / 健康检查${entry.healthCheck === 1 ? '通过' : '异常'}`)
    }
    const inspections = Array.isArray(row?.inspections) ? row.inspections : []
    inspections.forEach((ins: any) => {
      push('屠宰检验', ins.inspectTime, ins.veterinary ?? entry?.sourceFarm,
        `${ins.inspectType === 1 ? '宰前检验' : ins.inspectType === 2 ? '宰后检验' : '检验'} / ${pick(ins.conclusion)}`)
    })
    const stamp = Array.isArray(row?.stamps) ? row.stamps[0] : null
    if (stamp) {
      push('检疫盖章', stamp.stampTime, stamp.veterinary, `印章${pick(stamp.stampNo)} / ${pick(stamp.stampType)}`)
    }
  })

  // upstream 链路从当前分割批次回溯到胴体，倒序后即时间正序
  const upstream: any[] = Array.isArray(full?.upstream) ? [...full.upstream].reverse() : []
  upstream.forEach(node => {
    const d = node?.data
    if (!d) return
    if (node.type === 'CARCASS') {
      push('胴体批次', d.createTime, d.slaughterhouse, `批次${pick(d.batchNo)} / ${pick(d.totalWeightKg)}kg`)
    } else {
      push('分割加工', d.splitTime, d.workshop,
        `${pick(d.productName)} / ${pick(d.packageType)} / ${pick(d.weightKg)}kg`)
    }
  })

  const logistics: any[] = Array.isArray(full?.downstream?.logistics) ? full.downstream.logistics : []
  logistics.forEach(item => {
    const t = item?.transport
    if (t) {
      push('冷链运输', t.departTime ?? t.plannedDepart ?? t.createTime, t.vehicleNo,
        `单号${pick(t.transportNo)} / ${pick(t.origin)}→${pick(t.destination)}`)
    }
    const r = item?.receipt
    if (r) {
      push('门店签收', r.receiptTime, r.storeName,
        `签收人${pick(r.receiver)} / 温度${r.tempValue ?? '-'}℃`)
    }
  })

  const sales: any[] = Array.isArray(full?.downstream?.sales) ? full.downstream.sales : []
  sales.forEach(s => {
    push('零售上架', s.shelfTime, s.storeName, `二维码${pick(s.productQrCode)}`)
  })

  if (!rows.length && full?.batchNo) {
    push('批次信息', null, null, `批次${full.batchNo}`)
  }
  return rows
}

async function handleSearch() {
  const kw = keyword.value.trim()
  if (!kw) { ElMessage.warning('请输入搜索关键词'); return }
  searching.value = true
  hasSearched.value = true
  traceData.value = null
  verifyResult.value = null
  nodeDetails.value = []

  try {
    const res: any = await traceApi.search(kw)
    const full = res?.product ? res.traceChain : res
    if (!full || typeof full !== 'object') {
      traceData.value = null
      return
    }
    traceData.value = buildTraceData(res, full, kw)
    nodeDetails.value = buildNodeDetails(full)
    searching.value = false
    await nextTick()
    initGraphChart(full)
    // 自动执行验真（无可验真二维码时静默跳过）
    handleVerify(true)
  } catch {
    // 后端业务错误 / 网络错误已由请求拦截器统一提示
    traceData.value = null
    nodeDetails.value = []
  } finally {
    searching.value = false
  }
}

async function handleVerify(silent: unknown = false) {
  const productQr = traceData.value?.product?.qrCode
  const kw = keyword.value.trim()
  const qr = typeof productQr === 'string' && productQr.toUpperCase().startsWith('QR-')
    ? productQr
    : kw.toUpperCase().startsWith('QR-') ? kw : ''
  if (!qr) {
    if (silent !== true) ElMessage.warning('当前溯源结果中没有可验真的产品二维码')
    return
  }
  verifying.value = true
  try {
    const res: any = await traceApi.verify(qr)
    if (!res || typeof res !== 'object') {
      verifyResult.value = null
      return
    }
    verifyResult.value = {
      allVerified: !!res.allVerified,
      details: (Array.isArray(res.details) ? res.details : []).map((d: any) => ({
        bizType: pick(d?.type),
        bizName: `存证记录 #${pick(d?.bizId)}`,
        localHash: d?.localHash ?? '',
        onChainHash: d?.chainHash ?? '',
        matched: d?.match === true,
      })),
    }
  } catch {
    verifyResult.value = null
  } finally {
    verifying.value = false
  }
}

function initGraphChart(full: any) {
  if (!graphChartRef.value) return
  graphChart?.dispose()
  graphChart = echarts.init(graphChartRef.value)

  const categories = [
    { name: '养殖端', itemStyle: { color: '#67c23a' } },
    { name: '屠宰端', itemStyle: { color: '#409eff' } },
    { name: '分割配送', itemStyle: { color: '#e6a23c' } },
    { name: '销售端', itemStyle: { color: '#9a60b4' } },
    { name: '消费者', itemStyle: { color: '#909399' } },
  ]

  const data: any[] = []
  const links: any[] = []
  const seq: string[] = []
  const used = new Set<string>()
  const chain = (name: string, category: number, symbolSize: number, desc: string) => {
    if (!name) return
    if (!used.has(name)) {
      used.add(name)
      data.push({
        name, category, symbolSize, desc,
        itemStyle: { borderColor: categories[category].itemStyle.color, borderWidth: 2 },
      })
    }
    seq.push(name)
  }

  const breeding: any[] = Array.isArray(full?.breeding) ? full.breeding : []
  const pig = breeding[0]
  if (pig) {
    chain(pick(pig.farmName, '养殖场'), 0, 40, `耳标: ${pick(pig.earTagNo)}\n品种: ${pick(pig.breed)}`)
  }

  const upstream: any[] = Array.isArray(full?.upstream) ? [...full.upstream].reverse() : []
  const carcass = upstream.find(n => n?.type === 'CARCASS')?.data
  const slaughterRows: any[] = Array.isArray(full?.slaughter) ? full.slaughter : []
  const entry = slaughterRows[0]?.entries?.[0]
  const slaughterhouse = carcass?.slaughterhouse ?? entry?.sourceFarm
  if (slaughterhouse || carcass) {
    chain(pick(slaughterhouse, '屠宰场'), 1, 40,
      carcass ? `胴体批次: ${pick(carcass.batchNo)}\n总重: ${pick(carcass.totalWeightKg)}kg` : '')
  }
  upstream.filter(n => n?.type === 'SPLIT').forEach(n => {
    const d = n.data
    chain(`${pick(d?.productName, '分割批次')} ${pick(d?.batchNo, '')}`.trim(), 2, 36,
      `批次: ${pick(d?.batchNo)}\n${pick(d?.weightKg)}kg / ${pick(d?.packageType)}`)
  })

  const logistics: any[] = Array.isArray(full?.downstream?.logistics) ? full.downstream.logistics : []
  logistics.forEach(item => {
    const t = item?.transport
    if (t) chain(`冷链 ${pick(t.vehicleNo, '')}`.trim(), 2, 30, `单号: ${pick(t.transportNo)}`)
  })
  const sales: any[] = Array.isArray(full?.downstream?.sales) ? full.downstream.sales : []
  const receipt = logistics.map(i => i?.receipt).find(Boolean)
  const storeName = sales[0]?.storeName ?? receipt?.storeName
  if (storeName) {
    chain(storeName, 3, 38, receipt ? `签收: ${pick(receipt.receiver)}` : '零售门店')
  }
  if (sales[0]?.productQrCode) {
    chain('消费者', 4, 28, `扫码: ${sales[0].productQrCode}`)
  }

  // 数据不足时至少画出批次节点
  if (!data.length) {
    chain(`批次 ${pick(full?.batchNo, '')}`.trim(), 2, 40, '暂无上下游关联数据')
  }
  for (let i = 1; i < seq.length; i++) {
    if (seq[i] !== seq[i - 1]) links.push({ source: seq[i - 1], target: seq[i] })
  }

  graphChart.setOption({
    tooltip: { trigger: 'item', formatter: (params: any) => {
      if (params.dataType === 'edge') return `${params.data.source} → ${params.data.target}`
      return `<b>${params.name}</b><br/>类型: ${categories[params.data.category]?.name}<br/>${(params.data.desc || '').replace(/\n/g, '<br/>')}`
    }},
    legend: { bottom: 0, data: categories.map(c => c.name), textStyle: { fontSize: 12 } },
    series: [{
      type: 'graph', layout: 'force', roam: true, draggable: true,
      force: { repulsion: 300, edgeLength: [120, 260], gravity: 0.15 },
      categories,
      data,
      links,
      lineStyle: { color: '#c0c4cc', curveness: 0.2, width: 2, opacity: 0.8 },
      label: { show: true, fontSize: 12, position: 'right', formatter: '{b}' },
      emphasis: { focus: 'adjacency', lineStyle: { width: 4 } },
    }],
  })
}

function handleResize() { graphChart?.resize() }

onMounted(() => {
  window.addEventListener('resize', handleResize)
  // 自动搜索默认关键词
  handleSearch()
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  graphChart?.dispose()
})
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }

// 搜索区
.search-section { margin-bottom: 24px; }
.search-hero { text-align: center; padding: 32px 24px; background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%); border-radius: 12px; border: 1px solid #ebeef5; }
.search-title { font-size: 24px; font-weight: 700; color: #303133; margin: 0 0 8px; }
.search-subtitle { font-size: 13px; color: #909399; margin: 0 0 20px; }
.search-input-row { display: flex; gap: 12px; max-width: 700px; margin: 0 auto; }
.search-input { flex: 1; }
.search-hints { margin-top: 14px; display: flex; align-items: center; justify-content: center; gap: 6px; flex-wrap: wrap; font-size: 12px; color: #909399; }
.search-hint-tag { cursor: pointer; }

// 加载
.loading-section { padding: 40px 0; }

// 结果
.result-card { margin-bottom: 20px; }
.card-title { font-size: 15px; font-weight: 600; color: #303133; }
.card-header { display: flex; justify-content: space-between; align-items: center; }

// 图表 + 验证面板
.trace-grid { display: flex; gap: 20px; margin-bottom: 20px; }
.trace-graph-card { flex: 1; .card-header { display: flex; justify-content: space-between; align-items: center; } }
.graph-container { height: 450px; }
.verify-panel { width: 420px; flex-shrink: 0; }

// 验真面板
.verify-overview { display: flex; flex-direction: column; gap: 16px; }
.verify-summary { display: flex; align-items: center; gap: 12px; padding: 16px; border-radius: 8px;
  &.verify-pass { background: #f0f9eb; border: 1px solid #e1f3d8; }
  &.verify-fail { background: #fef0f0; border: 1px solid #fde2e2; }
}
.verify-summary-title { font-size: 15px; font-weight: 700; color: #303133; }
.verify-summary-desc { font-size: 12px; color: #909399; margin-top: 2px; }
.verify-details { display: flex; flex-direction: column; gap: 8px; max-height: 320px; overflow-y: auto; }
.verify-detail-item { padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 6px; }
.vd-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.vd-biz { font-size: 13px; font-weight: 600; color: #303133; }
.vd-hashes { display: flex; flex-direction: column; gap: 2px; }
.vd-hash-row { display: flex; align-items: center; gap: 8px; }
.vd-label { font-size: 11px; color: #909399; white-space: nowrap; width: 56px; }
.vd-code { font-family: 'Courier New', monospace; font-size: 11px; color: #909399; background: #f5f7fa; padding: 1px 6px; border-radius: 3px;
  &.hash-mismatch { color: #f56c6c; background: #fef0f0; }
}

// 链上存证
.chain-records { margin-top: 16px; padding-top: 16px; border-top: 1px solid #ebeef5; }
.chain-record-item { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 12px; }
.cr-type { color: #606266; font-weight: 500; }
.cr-hash { font-family: 'Courier New', monospace; font-size: 11px; color: #909399; background: #f5f7fa; padding: 1px 4px; border-radius: 2px; cursor: pointer; }

// 节点详情表
.detail-table-card { margin-bottom: 0; }
</style>
