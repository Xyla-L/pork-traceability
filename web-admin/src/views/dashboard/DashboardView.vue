<template>
  <div class="dashboard">
    <!-- ========== 统计卡片 ========== -->
    <div class="stat-cards">
      <el-card v-for="card in statCards" :key="card.key" class="stat-card" shadow="hover">
        <div class="stat-icon" :style="{ background: card.gradient }">
          <el-icon :size="24"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
        <div class="stat-trend" v-if="card.trend">
          <el-icon :color="card.trend > 0 ? '#67c23a' : '#f56c6c'">
            <component :is="card.trend > 0 ? 'CaretTop' : 'CaretBottom'" />
          </el-icon>
          <span :style="{ color: card.trend > 0 ? '#67c23a' : '#f56c6c' }">{{ Math.abs(card.trend) }}%</span>
          <span class="trend-label">较昨日</span>
        </div>
      </el-card>
    </div>

    <!-- ========== 图表行：桑基图 + 临期分布饼图 ========== -->
    <div class="chart-row">
      <el-card class="chart-card">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">全链路数据流通统计</span>
            <el-tooltip content="桑基图：养殖→屠宰→分割→配送→销售各环节数据流转量" placement="top">
              <el-icon class="chart-hint"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
        </template>
        <div ref="sankeyChartRef" class="chart-container sankey-chart"></div>
      </el-card>

      <el-card class="chart-card">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">临期/过期产品分布</span>
            <el-tag size="small" type="warning">实时</el-tag>
          </div>
        </template>
        <div ref="expirePieRef" class="chart-container"></div>
      </el-card>
    </div>

    <!-- ========== 图表行：每日上链量柱状图 + 预警级别分布 ========== -->
    <div class="chart-row">
      <el-card class="chart-card">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">区块链每日上链量</span>
            <el-tag size="small" type="success">近30天</el-tag>
          </div>
        </template>
        <div ref="chainTxBarRef" class="chart-container"></div>
      </el-card>

      <el-card class="chart-card">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">预警级别分布</span>
          </div>
        </template>
        <div ref="warningChartRef" class="chart-container"></div>
      </el-card>
    </div>

    <!-- ========== 底部：最近预警 + 待办事项 ========== -->
    <div class="bottom-row">
      <el-card class="table-card">
        <template #header>
          <div class="table-header">
            <span>最近预警</span>
            <el-button type="primary" link @click="goToWarnings">查看全部 →</el-button>
          </div>
        </template>
        <el-table :data="recentWarnings" stripe border style="width: 100%">
          <el-table-column prop="productName" label="产品名称" min-width="150" />
          <el-table-column prop="batchNo" label="批次号" min-width="120" />
          <el-table-column prop="warningLevel" label="预警级别" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="warningTagType(row.warningLevel)" size="small">{{ row.warningLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expireDate" label="过期日期" width="120" />
          <el-table-column prop="storeName" label="门店" width="120" />
          <el-table-column label="操作" width="80" align="center">
            <template #default>
              <el-button type="primary" link size="small">处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="table-card">
        <template #header>
          <div class="table-header">
            <span>待办事项</span>
            <el-badge :value="todoList.length" type="danger" />
          </div>
        </template>
        <el-table :data="todoList" stripe border style="width: 100%">
          <el-table-column prop="title" label="事项" min-width="200" />
          <el-table-column prop="type" label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="todoTagType(row.type)">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="time" label="时间" width="120" />
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === '待审批' ? 'warning' : row.status === '进行中' ? 'primary' : 'info'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  User, KnifeFork, Box, Warning, ChatLineRound, Coin,
  CaretTop, CaretBottom, QuestionFilled,
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/modules/dashboard'

const router = useRouter()

// ==================== 统计卡片数据 ====================
interface StatCard {
  key: string
  icon: any
  label: string
  value: number | string
  gradient: string
  trend?: number
}

// 后端只返回 key/value/trend，图标与渐变色由前端按 key 映射
const ICON_MAP: Record<string, any> = {
  pigCount: User, slaughterCount: KnifeFork, transportCount: Box,
  expireCount: Warning, complaintCount: ChatLineRound, chainCount: Coin,
}
const GRADIENT_MAP: Record<string, string> = {
  pigCount: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  slaughterCount: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  transportCount: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  expireCount: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  complaintCount: 'linear-gradient(135deg, #f5576c 0%, #ff6b6b 100%)',
  chainCount: 'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)',
}

const statCards = reactive<StatCard[]>([])

// ==================== 最近预警数据 ====================
const recentWarnings = ref<any[]>([])

const warningTagType = (level: string) => {
  if (level === '已过期') return 'danger'
  if (level === '临期1天') return 'warning'
  return 'info'
}

// ==================== 待办事项 ====================
const todoList = ref<any[]>([])

const todoTagType = (type: string): 'warning' | 'danger' | 'info' | 'primary' | 'success' => {
  const map: Record<string, 'warning' | 'danger' | 'info' | 'primary' | 'success'> = { '出栏审批': 'warning', '举报处理': 'danger', '检测审核': 'info', '配送签收': 'primary', '产品召回': 'danger' }
  return map[type] || 'info'
}

// ==================== ECharts 实例 ====================
const sankeyChartRef = ref<HTMLElement>()
const expirePieRef = ref<HTMLElement>()
const chainTxBarRef = ref<HTMLElement>()
const warningChartRef = ref<HTMLElement>()

let sankeyChart: echarts.ECharts | null = null
let expirePieChart: echarts.ECharts | null = null
let chainTxBarChart: echarts.ECharts | null = null
let warningChart: echarts.ECharts | null = null

// ==================== 初始化桑基图 ====================
function initSankeyChart(data: any) {
  if (!sankeyChartRef.value) return
  sankeyChart = echarts.init(sankeyChartRef.value)

  const nodes = (data?.nodes || []).map((n: any) => ({ name: n.name, value: n.value }))
  const links = (data?.links || []).map((l: any) => ({ source: l.source, target: l.target, value: l.value }))
  const colorMap: Record<string, string> = {
    '养殖免疫': '#5470c6', '屠宰检疫': '#ee6666', '分割配送': '#73c0de',
    '市场销售': '#9a60b4', '消费者': '#67c23a',
  }

  sankeyChart.setOption({
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          return `<b>${params.name}</b><br/>节点总量: ${params.value} 批次`
        }
        return `${params.data.source} → ${params.data.target}<br/>流转量: <b>${params.data.value}</b> 批次`
      },
    },
    series: [{
      type: 'sankey',
      layoutIterations: 32,
      emphasis: { focus: 'adjacency' },
      nodeWidth: 24,
      nodeGap: 16,
      data: nodes.map((n: any) => ({ ...n, itemStyle: { color: colorMap[n.name] || '#5470c6' } })),
      links,
      lineStyle: { color: 'gradient', curveness: 0.5, opacity: 0.3 },
      label: { show: true, fontSize: 13, fontWeight: 'bold' },
    }],
  })
}

// ==================== 初始化临期分布饼图 ====================
function initExpirePieChart(data: any[]) {
  if (!expirePieRef.value) return
  expirePieChart = echarts.init(expirePieRef.value)
  const colorMap: Record<string, string> = {
    '安全（>7天）': '#67c23a', '临期3天': '#e6a23c', '临期1天': '#f56c6c', '已过期': '#909399',
  }
  expirePieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 件 ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['45%', '75%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      data: (data || []).map((d: any) => ({ value: d.value, name: d.name, itemStyle: { color: colorMap[d.name] || '#909399' } })),
    }],
  })
}

// ==================== 初始化每日上链量柱状图 ====================
function initChainTxBarChart(data: any[]) {
  if (!chainTxBarRef.value) return
  chainTxBarChart = echarts.init(chainTxBarRef.value)

  const dates = (data || []).map((d: any) => d.date)
  const txData = (data || []).map((d: any) => d.count)

  chainTxBarChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: dates, axisLabel: { rotate: 45, fontSize: 10 } },
    yAxis: { type: 'value', name: '上链笔数' },
    series: [{
      type: 'bar',
      data: txData,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#79bbff' },
        ]),
        borderRadius: [4, 4, 0, 0],
      },
      emphasis: {
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#337ecc' },
          { offset: 1, color: '#409eff' },
        ])},
      },
      markLine: {
        silent: true,
        data: [{ type: 'average', name: '平均值', label: { fontSize: 10 } }],
        lineStyle: { color: '#e6a23c', type: 'dashed' },
      },
    }],
  })
}

// ==================== 初始化预警级别饼图 ====================
function initWarningChart(data: any[]) {
  if (!warningChartRef.value) return
  warningChart = echarts.init(warningChartRef.value)
  const colorMap: Record<string, string> = {
    '临期3天': '#e6a23c', '临期1天': '#f56c6c', '已过期': '#909399', '已召回': '#409eff',
  }
  warningChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['50%', '80%'],
      center: ['50%', '45%'],
      roseType: 'area',
      itemStyle: { borderRadius: 6 },
      data: (data || []).map((d: any) => ({ value: d.value, name: d.name, itemStyle: { color: colorMap[d.name] || '#909399' } })),
    }],
  })
}

// ==================== 数据加载 ====================
async function loadOverview() {
  try {
    const data = await dashboardApi.getOverview()
    statCards.length = 0
    const cards = (data.statCards || []).map((c: any) => ({
      key: c.key, label: c.label, value: c.value, trend: c.trend,
      icon: ICON_MAP[c.key] || Warning, gradient: GRADIENT_MAP[c.key] || 'linear-gradient(135deg,#909399,#606266)',
    }))
    statCards.push(...cards)
    recentWarnings.value = data.recentWarnings || []
    todoList.value = data.todoList || []

    await nextTick()
    initSankeyChart(data.sankey)
    initExpirePieChart(data.expireDistribution)
    initChainTxBarChart(data.chainTxTrend)
    initWarningChart(data.warningDistribution)
  } catch (e) {
    console.error('加载工作台数据失败:', e)
  }
}

// ==================== 生命周期 ====================
function goToWarnings() {
  router.push('/admin/sales/warnings')
}

function handleResize() {
  sankeyChart?.resize()
  expirePieChart?.resize()
  chainTxBarChart?.resize()
  warningChart?.resize()
}

onMounted(() => {
  loadOverview()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  sankeyChart?.dispose()
  expirePieChart?.dispose()
  chainTxBarChart?.dispose()
  warningChart?.dispose()
})
</script>

<style lang="scss" scoped>
.dashboard {
  padding: 20px;
}

// ========== 统计卡片 ==========
.stat-cards {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 20px;

  @media screen and (max-width: 1400px) {
    grid-template-columns: repeat(3, 1fr);
  }
}

.stat-card {
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 20px 18px;
  }
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  flex-shrink: 0;

  .trend-label {
    color: #909399;
    margin-left: 2px;
  }
}

// ========== 图表行 ==========
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;

  @media screen and (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.chart-hint {
  color: #909399;
  cursor: help;
  font-size: 14px;
}

.chart-container {
  height: 320px;

  &.sankey-chart {
    height: 360px;
  }
}

// ========== 底部表格 ==========
.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;

  @media screen and (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.table-card {
  :deep(.el-table) {
    th.el-table__cell { background-color: #f5f7fa; }
  }
}
</style>
