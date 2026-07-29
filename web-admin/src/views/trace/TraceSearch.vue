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
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { Search, Refresh, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'

// 搜索
const keyword = ref('B20240715001')
const hasSearched = ref(false)
const searching = ref(false)
const searchHints = ['B20240715001', 'ET20240601001', 'QR-PORK-20240715001', '猪前腿肉']

// 溯源数据
const traceData = ref<any>(null)
const verifyResult = ref<any>(null)
const verifying = ref(false)

// 节点详情表
const nodeDetails = ref<any[]>([])

// 图表
const graphChartRef = ref<HTMLElement>()
let graphChart: echarts.ECharts | null = null

function handleSearch() {
  if (!keyword.value.trim()) { ElMessage.warning('请输入搜索关键词'); return }
  searching.value = true
  hasSearched.value = true
  traceData.value = null
  verifyResult.value = null

  setTimeout(() => {
    // 模拟溯源数据
    traceData.value = {
      product: {
        name: '猪前腿肉 500g', batchNo: 'B20240715001', weight: '500g',
        packageDate: '2024-07-03 10:00', expireDate: '2024-07-22', qrCode: 'QR-PORK-20240715001',
      },
      traceChain: {
        farm: { name: 'XX养殖合作社', licenseNo: 'SC2023001', earTagNo: 'ET20240601001', breed: '长白猪' },
        vaccines: [
          { name: '猪瘟活疫苗', batchNo: 'CSF-20240601', time: '2024-06-05' },
          { name: '口蹄疫O型灭活疫苗', batchNo: 'FMD-20240615', time: '2024-06-20' },
        ],
        quarantineCert: { certNo: 'QC2024070100123', issueOrg: 'XX县动物卫生监督所', issueTime: '2024-07-01', inspector: '李建国', caVerified: true },
        slaughter: { slaughterhouse: 'XX市定点屠宰场', licenseNo: 'ST2023003', entryTime: '2024-07-02 06:30', inspectResult: '合格', ractopamine: '阴性', stampNo: 'ST2024070200123', veterinary: '王建国' },
        splitWorkshop: { name: '分割车间A组', splitTime: '2024-07-03 08:00', workshopTemp: '10℃', productName: '猪前腿肉', packageType: '真空包装' },
        transport: { transportNo: 'T2024070300123', vehicleNo: '京A·12345', vehicleType: '冷链车', departTime: '2024-07-03 14:00', arriveTime: '2024-07-03 18:00', temperatureLog: [], avgTemp: -13.5, abnormalCount: 0 },
        storeReceipt: { storeName: 'XX社区超市', receiptTime: '2024-07-03 18:30', receiver: '钱店长', tempAtReceipt: '-12.0℃', packageIntact: '完好' },
        blockchain: {
          verified: true, recordCount: 5,
          records: [
            { type: '产地检疫', txHash: '0x7a3b8c2d1...', blockNumber: 284710 },
            { type: '屠宰检验', txHash: '0x8c4d5e6f7a...', blockNumber: 284715 },
            { type: '分割记录', txHash: '0x9d5e6f7a8b...', blockNumber: 284720 },
            { type: '运输记录', txHash: '0xae6f7a8b9c...', blockNumber: 284725 },
            { type: '门店签收', txHash: '0xbf7a8b9c0d...', blockNumber: 284730 },
          ],
        },
      },
    }

    // 构建节点详情
    const tc = traceData.value.traceChain
    nodeDetails.value = [
      { stage: '1', title: '养殖场', time: '2024-06-05', org: tc.farm.name, keyInfo: `耳标${tc.farm.earTagNo} / ${tc.farm.breed}`, chainStatus: 'confirmed' },
      { stage: '2', title: '产地检疫', time: tc.quarantineCert.issueTime, org: tc.quarantineCert.issueOrg, keyInfo: `检疫证${tc.quarantineCert.certNo} / CA✅`, chainStatus: 'confirmed' },
      { stage: '3', title: '屠宰检验', time: tc.slaughter.entryTime, org: tc.slaughter.slaughterhouse, keyInfo: `检验${tc.slaughter.inspectResult} / 瘦肉精${tc.slaughter.ractopamine}`, chainStatus: 'confirmed' },
      { stage: '4', title: '分割加工', time: tc.splitWorkshop.splitTime, org: tc.splitWorkshop.name, keyInfo: `${tc.splitWorkshop.productName} / ${tc.splitWorkshop.packageType}`, chainStatus: 'confirmed' },
      { stage: '5', title: '冷链运输', time: tc.transport.departTime, org: `${tc.transport.vehicleNo}`, keyInfo: `平均${tc.transport.avgTemp}℃ / 异常${tc.transport.abnormalCount}次`, chainStatus: 'confirmed' },
      { stage: '6', title: '门店签收', time: tc.storeReceipt.receiptTime, org: tc.storeReceipt.storeName, keyInfo: `签收人${tc.storeReceipt.receiver} / 温度${tc.storeReceipt.tempAtReceipt}`, chainStatus: 'confirmed' },
    ]

    searching.value = false
    nextTick(() => initGraphChart())

    // 自动执行验真
    setTimeout(() => handleVerify(), 300)
  }, 800)
}

function handleVerify() {
  verifying.value = true
  setTimeout(() => {
    verifyResult.value = {
      allVerified: true,
      details: [
        { bizType: '养殖免疫', bizName: '产地检疫证明', localHash: '0x7a3b8c2d1e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b', onChainHash: '0x7a3b8c2d1e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b', matched: true },
        { bizType: '屠宰检疫', bizName: '宰前/宰后检验报告', localHash: '0x8c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d', onChainHash: '0x8c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d', matched: true },
        { bizType: '分割配送', bizName: '批次拆分记录', localHash: '0x9d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e', onChainHash: '0x9d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e', matched: true },
        { bizType: '冷链运输', bizName: '温度打卡记录', localHash: '0xae6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f', onChainHash: '0xae6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f', matched: true },
        { bizType: '市场销售', bizName: '门店签收确认', localHash: '0xbf7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a', onChainHash: '0xbf7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a', matched: true },
      ],
    }
    verifying.value = false
  }, 1200)
}

function initGraphChart() {
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

  graphChart.setOption({
    tooltip: { trigger: 'item', formatter: (params: any) => {
      if (params.dataType === 'edge') return `${params.data.source} → ${params.data.target}`
      return `<b>${params.name}</b><br/>类型: ${categories[params.data.category]?.name}<br/>${params.data.desc || ''}`
    }},
    legend: { bottom: 0, data: categories.map(c => c.name), textStyle: { fontSize: 12 } },
    series: [{
      type: 'graph', layout: 'force', roam: true, draggable: true,
      force: { repulsion: 300, edgeLength: [120, 260], gravity: 0.15 },
      categories,
      data: [
        { name: '养殖场', category: 0, symbolSize: 40, desc: 'XX养殖合作社\n耳标: ET20240601001', itemStyle: { borderColor: '#67c23a', borderWidth: 3 } },
        { name: '产地检疫', category: 0, symbolSize: 28, desc: '检疫证: QC2024070100123', itemStyle: { borderColor: '#67c23a', borderWidth: 2 } },
        { name: '屠宰场', category: 1, symbolSize: 40, desc: 'XX市定点屠宰场\n检验: 合格', itemStyle: { borderColor: '#409eff', borderWidth: 3 } },
        { name: '检疫盖章', category: 1, symbolSize: 28, desc: '印章: ST2024070200123', itemStyle: { borderColor: '#409eff', borderWidth: 2 } },
        { name: '分割车间', category: 2, symbolSize: 38, desc: '分割车间A组\n猪前腿肉 500g', itemStyle: { borderColor: '#e6a23c', borderWidth: 3 } },
        { name: '冷链运输', category: 2, symbolSize: 32, desc: '京A·12345\n平均温度 -13.5℃', itemStyle: { borderColor: '#e6a23c', borderWidth: 2 } },
        { name: '零售门店', category: 3, symbolSize: 40, desc: 'XX社区超市\n签收: 钱店长', itemStyle: { borderColor: '#9a60b4', borderWidth: 3 } },
        { name: '消费者', category: 4, symbolSize: 30, desc: '扫码溯源验证', itemStyle: { borderColor: '#909399', borderWidth: 2 } },
      ],
      links: [
        { source: '养殖场', target: '产地检疫' },
        { source: '产地检疫', target: '屠宰场' },
        { source: '屠宰场', target: '检疫盖章' },
        { source: '检疫盖章', target: '分割车间' },
        { source: '分割车间', target: '冷链运输' },
        { source: '冷链运输', target: '零售门店' },
        { source: '零售门店', target: '消费者' },
      ],
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
