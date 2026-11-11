<template>
  <div v-loading="loading" class="dashboard">
    <!-- 角色与数据范围：同一张工作台，不同角色看到的是自己那一环 -->
    <div class="dashboard-head">
      <div class="head-main">
        <span class="head-title">{{ overview?.roleLabel || '工作台' }}</span>
        <el-tag v-if="overview?.scopeLabel" size="small" effect="plain" type="info">
          {{ overview.scopeLabel }}
        </el-tag>
      </div>
      <span v-if="degradedText" class="head-degraded">{{ degradedText }}</span>
    </div>

    <!-- ========== 指标卡片 ========== -->
    <div class="stat-cards">
      <el-card v-for="card in statCards" :key="card.key" class="stat-card" shadow="hover">
        <div class="stat-icon" :style="{ background: gradientOf(card.key) }">
          <el-icon :size="24"><component :is="iconOf(card.key)" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">
            {{ cardText(card) }}<span v-if="card.unit" class="stat-unit">{{ card.unit }}</span>
          </div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </el-card>
    </div>

    <!-- ========== 图表 / 明细区块 ========== -->
    <div class="block-grid">
      <el-card
        v-for="block in blocks"
        :key="block.key"
        class="chart-card"
        :class="{ 'span-2': block.span === 2 }"
      >
        <template #header>
          <div class="chart-header">
            <span class="chart-title">{{ block.title }}</span>
            <el-button v-if="block.link" type="primary" link size="small" @click="go(block.link.path)">
              {{ block.link.text }} →
            </el-button>
          </div>
        </template>

        <el-table
          v-if="block.type === 'table'"
          :data="block.rows || []"
          stripe
          border
          style="width: 100%"
          :empty-text="block.emptyText || '暂无记录'"
          max-height="320"
        >
          <el-table-column
            v-for="col in block.columns || []"
            :key="col.prop"
            :prop="col.prop"
            :label="col.label"
            :min-width="col.width"
            show-overflow-tooltip
          />
        </el-table>

        <div v-else-if="isEmpty(block)" class="chart-empty">暂无可展示的数据</div>

        <div
          v-else
          :ref="(el) => setChartEl(block.key, el)"
          class="chart-container"
          :class="{ sankey: block.type === 'sankey' }"
        ></div>
      </el-card>
    </div>

    <!-- ========== 待办 ========== -->
    <el-card v-if="todoList.length" class="table-card todo-card">
      <template #header>
        <div class="chart-header">
          <span class="chart-title">待办事项</span>
          <el-badge :value="todoList.length" type="danger" />
        </div>
      </template>
      <el-table :data="todoList" stripe border style="width: 100%" max-height="300">
        <el-table-column prop="title" label="事项" min-width="260" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="todoTagType(row.type)">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="time" label="时间" width="150" />
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '待处理' || row.status === '待发车' ? 'warning' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  User, KnifeFork, Box, Warning, ChatLineRound, Coin,
  Van, Search, Stamp, Money, ShoppingCart, Goods,
  AlarmClock, DocumentChecked, Select, Right, Position,
  Location, CircleCheck,
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/modules/dashboard'
import type { DashboardOverview, DashboardBlock } from '@/types/dashboard'

const router = useRouter()

const overview = ref<DashboardOverview | null>(null)
const loading = ref(true)
const statCards = computed(() => overview.value?.statCards || [])
const blocks = computed(() => overview.value?.blocks || [])
const todoList = computed(() => overview.value?.todoList || [])
const degradedText = computed(() => {
  const list = overview.value?.degraded || []
  return list.length ? `部分数据暂不可用：${list.join('、')}` : ''
})

// 后端只给 key，图标与配色由前端按 key 映射；未知 key 用兜底样式
const ICON_MAP: Record<string, any> = {
  // 管理员 / 监管
  pigCount: User, slaughterCount: KnifeFork, transportCount: Box,
  expireCount: Warning, complaintCount: ChatLineRound, chainCount: Coin,
  expiredCount: AlarmClock,
  // 养殖户
  raisingCount: User, pendingApply: DocumentChecked, approvedApply: Select, exitedCount: Right,
  // 屠宰岗
  entryToday: Van, inspectToday: Search, stampToday: Stamp, batchTotal: Box,
  // 配送
  waitingTransport: Van, movingTransport: Position, arrivingTransport: Location, receivedTransport: CircleCheck,
  // 零售
  salesAmount: Money, salesWeight: ShoppingCart, soldCount: Goods, expiringCount: Warning,
}

const GRADIENT_MAP: Record<string, string> = {
  pigCount: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  slaughterCount: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  transportCount: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  expireCount: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  complaintCount: 'linear-gradient(135deg, #f5576c 0%, #ff6b6b 100%)',
  chainCount: 'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)',
  expiredCount: 'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)',
  raisingCount: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  pendingApply: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  approvedApply: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  exitedCount: 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  entryToday: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)',
  inspectToday: 'linear-gradient(135deg, #5ee7df 0%, #b490ca 100%)',
  stampToday: 'linear-gradient(135deg, #d299c2 0%, #fef9d7 100%)',
  batchTotal: 'linear-gradient(135deg, #89f7fe 0%, #66a6ff 100%)',
  waitingTransport: 'linear-gradient(135deg, #fddb92 0%, #d1fdff 100%)',
  movingTransport: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  arrivingTransport: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  receivedTransport: 'linear-gradient(135deg, #a1c4fd 0%, #c2e9fb 100%)',
  salesAmount: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  salesWeight: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  soldCount: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  expiringCount: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
}

function iconOf(key: string) {
  return ICON_MAP[key] || Warning
}

function gradientOf(key: string) {
  return GRADIENT_MAP[key] || 'linear-gradient(135deg,#909399,#606266)'
}

function cardText(card: { value: number | string; unit?: string }) {
  const value = card.value
  if (typeof value !== 'number') return value ?? 0
  // 金额固定两位，数量按是否为整数：避免「1,432.5 元」和「27.75 kg」混着看不出量级
  if (card.unit === '元') return value.toFixed(2)
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

const todoTagType = (type: string): 'warning' | 'danger' | 'info' | 'primary' | 'success' => {
  if (type.includes('举报') || type.includes('召回')) return 'danger'
  if (type.includes('审批') || type.includes('驳回') || type.includes('临期')) return 'warning'
  if (type.includes('发车') || type.includes('签收')) return 'primary'
  return 'info'
}

// ==================== 图表实例 ====================
const chartEls = new Map<string, HTMLElement>()
const charts = new Map<string, echarts.ECharts>()

function setChartEl(key: string, el: any) {
  if (el) chartEls.set(key, el as HTMLElement)
  else chartEls.delete(key)
}

function isEmpty(block: DashboardBlock) {
  if (block.type === 'sankey') {
    const data = block.data as { nodes?: unknown[] } | undefined
    return !data?.nodes?.length
  }
  const data = block.data as any[] | undefined
  if (!data || data.length === 0) return true
  // 全是 0 与「没有数据」是一回事：画出来是个空圆环，不如直接说暂无数据
  if (block.type === 'pie' || block.type === 'bar' || block.type === 'barH') {
    return data.every((item) => !Number(item.value))
  }
  return false
}

const PALETTE = ['#5470c6', '#ee6666', '#73c0de', '#9a60b4', '#67c23a', '#e6a23c', '#909399', '#409eff']
const COLOR_BY_NAME: Record<string, string> = {
  '安全（>7天）': '#67c23a', '临期3天': '#e6a23c', '临期1天': '#f56c6c', '已过期': '#909399',
  '在养': '#67c23a', '已出栏': '#409eff', '已屠宰': '#909399', '异常': '#f56c6c',
  '合格': '#67c23a', '不合格': '#f56c6c', '未出结论': '#e6a23c',
  '待发车': '#e6a23c', '运输中': '#409eff', '待签收': '#9a60b4', '已签收': '#67c23a',
  '养殖免疫': '#5470c6', '屠宰检疫': '#ee6666', '分割配送': '#73c0de',
  '市场销售': '#9a60b4', '消费者': '#67c23a',
}

function colorOf(name: string, index: number) {
  return COLOR_BY_NAME[name] || PALETTE[index % PALETTE.length]
}

function buildOption(block: DashboardBlock) {
  if (block.type === 'sankey') {
    const data = block.data as { nodes: any[]; links: any[] } | undefined
    return {
      tooltip: { trigger: 'item', triggerOn: 'mousemove' },
      series: [{
        type: 'sankey',
        layoutIterations: 32,
        emphasis: { focus: 'adjacency' },
        nodeWidth: 24,
        nodeGap: 16,
        data: (data?.nodes || []).map((n: any, i: number) => ({
          name: n.name, value: n.value, itemStyle: { color: colorOf(n.name, i) },
        })),
        links: data?.links || [],
        lineStyle: { color: 'gradient', curveness: 0.5, opacity: 0.3 },
        label: { show: true, fontSize: 13, fontWeight: 'bold' },
      }],
    }
  }

  const rows = (block.data as any[] | undefined) || []
  const names = rows.map((item) => item.name)
  const values = rows.map((item) => item.value)

  if (block.type === 'pie') {
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['45%', '72%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 },
        label: { fontSize: 12, formatter: '{b}\n{c}' },
        data: rows.map((item, index) => ({
          name: item.name, value: item.value, itemStyle: { color: colorOf(item.name, index) },
        })),
      }],
    }
  }

  if (block.type === 'barH') {
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v: any) => `${v} ${block.unit || ''}` },
      grid: { left: '3%', right: '10%', bottom: '3%', top: '6%', containLabel: true },
      xAxis: { type: 'value', name: block.unit || '' },
      yAxis: { type: 'category', data: names, inverse: true, axisLabel: { fontSize: 12 } },
      series: [{
        type: 'bar',
        data: values,
        barMaxWidth: 22,
        itemStyle: { borderRadius: [0, 4, 4, 0], color: '#409eff' },
        label: { show: true, position: 'right', fontSize: 11 },
      }],
    }
  }

  // bar：垂直柱状
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v: any) => `${v} ${block.unit || ''}` },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '12%', containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { fontSize: 10, rotate: names.length > 12 ? 45 : 0 },
    },
    yAxis: { type: 'value', name: block.unit || '' },
    series: [{
      type: 'bar',
      data: values,
      barMaxWidth: 28,
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#79bbff' },
        ]),
      },
    }],
  }
}

function renderCharts() {
  charts.forEach((chart) => chart.dispose())
  charts.clear()
  for (const block of blocks.value) {
    if (block.type === 'table' || isEmpty(block)) continue
    const el = chartEls.get(block.key)
    if (!el) continue
    const chart = echarts.init(el)
    chart.setOption(buildOption(block))
    charts.set(block.key, chart)
  }
}

async function loadOverview() {
  loading.value = true
  try {
    const data = await dashboardApi.getOverview()
    overview.value = data
    await nextTick()
    renderCharts()
  } catch (e) {
    console.error('加载工作台数据失败:', e)
  } finally {
    loading.value = false
  }
}

function go(path: string) {
  router.push(path)
}

function handleResize() {
  charts.forEach((chart) => chart.resize())
}

onMounted(() => {
  loadOverview()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach((chart) => chart.dispose())
  charts.clear()
})
</script>

<style lang="scss" scoped>
.dashboard {
  padding: 20px;
}

// ========== 角色头 ==========
.dashboard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.head-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.head-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.head-degraded {
  font-size: 12px;
  color: #e6a23c;
}

// ========== 指标卡片 ==========
.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
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
  white-space: nowrap;
}

.stat-unit {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
  margin-left: 4px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

// ========== 区块网格 ==========
.block-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-bottom: 20px;

  @media screen and (max-width: 1200px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.span-2 {
  grid-column: span 2;

  @media screen and (max-width: 1200px) {
    grid-column: span 1;
  }
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.chart-container {
  height: 300px;

  &.sankey {
    height: 360px;
  }
}

.chart-empty {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
}

// ========== 待办 ==========
.todo-card {
  :deep(.el-table) {
    th.el-table__cell { background-color: #f5f7fa; }
  }
}
</style>
