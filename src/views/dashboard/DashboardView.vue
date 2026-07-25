<template>
  <div class="dashboard">
    <div class="stat-cards">
      <el-card class="stat-card">
        <div class="stat-icon pig"><el-icon><User /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ statData.pigCount }}</div>
          <div class="stat-label">在养生猪数</div>
        </div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-icon slaughter"><el-icon><KnifeFork /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ statData.slaughterCount }}</div>
          <div class="stat-label">今日屠宰量</div>
        </div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-icon distribution"><el-icon><Box /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ statData.transportCount }}</div>
          <div class="stat-label">在途批次</div>
        </div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-icon warning"><el-icon><Warning /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ statData.expireCount }}</div>
          <div class="stat-label">临期产品数</div>
        </div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-icon complaint"><el-icon><ChatLineRound /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ statData.complaintCount }}</div>
          <div class="stat-label">待处理举报</div>
        </div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-icon blockchain"><el-icon><Coin /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ statData.chainCount }}</div>
          <div class="stat-label">本月上链数</div>
        </div>
      </el-card>
    </div>

    <div class="chart-row">
      <el-card class="chart-card">
        <template #header><span>产品销售趋势</span></template>
        <div ref="salesChartRef" class="chart-container"></div>
      </el-card>
      <el-card class="chart-card">
        <template #header><span>预警级别分布</span></template>
        <div ref="warningChartRef" class="chart-container"></div>
      </el-card>
    </div>

    <div class="bottom-row">
      <el-card class="table-card">
        <template #header><span>最近预警</span><el-button type="primary" link size="small" @click="goToWarnings">查看全部</el-button></template>
        <el-table :data="recentWarnings" stripe border style="width: 100%">
          <el-table-column prop="productName" label="产品名称" min-width="150" />
          <el-table-column prop="batchNo" label="批次号" min-width="120" />
          <el-table-column prop="warningLevel" label="预警级别" width="100" align="center">
            <template #default="{ row }"><el-tag :type="getWarningTagType(row.warningLevel)">{{ row.warningLevel }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="expireDate" label="过期日期" width="120" />
          <el-table-column prop="storeName" label="门店" width="100" />
        </el-table>
      </el-card>
      <el-card class="table-card">
        <template #header><span>待办事项</span></template>
        <el-table :data="todoList" stripe border style="width: 100%">
          <el-table-column prop="title" label="事项" min-width="200" />
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="time" label="时间" width="120" />
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }"><el-tag size="small">{{ row.status }}</el-tag></template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { User, KnifeFork, Box, Warning, ChatLineRound, Coin } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const statData = reactive({ pigCount: 1256, slaughterCount: 86, transportCount: 23, expireCount: 15, complaintCount: 3, chainCount: 892 })

const recentWarnings = ref([
  { productName: '猪前腿肉 500g', batchNo: 'B20240715001', warningLevel: '临期3天', expireDate: '2024-07-18', storeName: 'XX超市' },
  { productName: '猪五花肉 300g', batchNo: 'B20240714002', warningLevel: '临期1天', expireDate: '2024-07-16', storeName: 'XX便利店' },
  { productName: '猪里脊 400g', batchNo: 'B20240713003', warningLevel: '已过期', expireDate: '2024-07-15', storeName: 'XX生鲜店' },
  { productName: '猪蹄 500g', batchNo: 'B20240712004', warningLevel: '临期3天', expireDate: '2024-07-19', storeName: 'XX超市' },
  { productName: '猪肝 200g', batchNo: 'B20240711005', warningLevel: '临期1天', expireDate: '2024-07-16', storeName: 'XX市场' }
])

const todoList = ref([
  { title: '审批出栏申报单 #20240715001', type: '出栏审批', time: '2024-07-15 10:30', status: '待审批' },
  { title: '处理举报 #20240714003', type: '举报处理', time: '2024-07-14 16:20', status: '待处理' },
  { title: '审核瘦肉精检测记录', type: '检测审核', time: '2024-07-15 09:00', status: '待审核' },
  { title: '确认门店签收 #S20240715001', type: '配送签收', time: '2024-07-15 11:00', status: '待确认' },
  { title: '查看召回进度 #R20240713001', type: '产品召回', time: '2024-07-13 08:00', status: '进行中' }
])

const salesChartRef = ref()
const warningChartRef = ref()
let salesChart: echarts.ECharts | null = null
let warningChart: echarts.ECharts | null = null

const getWarningTagType = (level: string) => { if (level === '已过期') return 'danger'; if (level === '临期1天') return 'warning'; return 'info' }

const initCharts = () => {
  if (salesChartRef.value) {
    salesChart = echarts.init(salesChartRef.value)
    salesChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: ['7-10', '7-11', '7-12', '7-13', '7-14', '7-15'] },
      yAxis: { type: 'value' },
      series: [{ name: '销售量', type: 'line', smooth: true, data: [120, 132, 101, 134, 190, 230], areaStyle: {} }]
    })
  }
  if (warningChartRef.value) {
    warningChart = echarts.init(warningChartRef.value)
    warningChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{ type: 'pie', radius: ['40%', '70%'], avoidLabelOverlap: false, itemStyle: { borderRadius: 10 }, label: { show: false }, emphasis: { label: { show: true, fontSize: 18 } }, data: [{ value: 8, name: '临期3天', itemStyle: { color: '#E6A23C' } }, { value: 5, name: '临期1天', itemStyle: { color: '#F56C6C' } }, { value: 2, name: '已过期', itemStyle: { color: '#909399' } }] }]
    })
  }
}

const goToWarnings = () => {}

const handleResize = () => { salesChart?.resize(); warningChart?.resize() }

onMounted(() => { nextTick(initCharts); window.addEventListener('resize', handleResize) })
onUnmounted(() => { window.removeEventListener('resize', handleResize); salesChart?.dispose(); warningChart?.dispose() })
</script>

<style lang="scss" scoped>
.dashboard { padding: 20px; }
.stat-cards { display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { display: flex; align-items: center; gap: 16px; padding: 20px; }
.stat-icon { width: 50px; height: 50px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 24px; color: #fff; &.pig { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); } &.slaughter { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); } &.distribution { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); } &.warning { background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); } &.complaint { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); } &.blockchain { background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%); } }
.stat-info { .stat-value { font-size: 24px; font-weight: 600; color: #303133; } .stat-label { font-size: 14px; color: #909399; } }
.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
.chart-card { .chart-container { height: 300px; } }
.bottom-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.table-card { :deep(.el-table) { th.el-table__cell { background-color: #f5f7fa; } } }
</style>