<template>
  <div class="page-container">
    <!-- 运输基本信息条 -->
    <div class="transport-info-bar">
      <div class="info-item"><span class="info-label">🚛 运单号</span><span class="info-value">{{ transportInfo.transportNo }}</span></div>
      <div class="info-item"><span class="info-label">车牌号</span><span class="info-value">{{ transportInfo.vehicleNo }}</span></div>
      <div class="info-item"><span class="info-label">司机</span><span class="info-value">{{ transportInfo.driverName }}</span></div>
      <div class="info-item"><span class="info-label">电话</span><span class="info-value">{{ transportInfo.driverPhone }}</span></div>
      <div class="info-item"><span class="info-label">发车</span><span class="info-value">{{ transportInfo.departTime }}</span></div>
      <div class="info-item"><span class="info-label">预计到达</span><span class="info-value">{{ transportInfo.arriveTime }}</span></div>
      <div class="info-item">
        <span class="info-label">状态</span>
        <el-tag :type="statusTagType(transportInfo.status)" size="small">{{ statusLabel(transportInfo.status) }}</el-tag>
      </div>
    </div>

    <!-- 温控面板 -->
    <div class="monitor-grid">
      <!-- 温度曲线图 -->
      <el-card class="monitor-card temp-chart-card">
        <template #header>
          <div class="card-header">
            <span>🌡️ 温度变化曲线</span>
            <el-space>
              <el-tag size="small" type="danger">上限 0°C</el-tag>
              <el-tag size="small" type="info">下限 -18°C</el-tag>
            </el-space>
          </div>
        </template>
        <div ref="tempChartRef" class="chart-container"></div>
      </el-card>

      <!-- 当前温度 & 快速打卡 -->
      <div class="monitor-sidebar">
        <!-- 当前温度 -->
        <el-card class="current-temp-card" :class="{ 'temp-abnormal': currentTemp > 0 || currentTemp < -18 }">
          <div class="current-temp">
            <span class="temp-value">{{ currentTemp.toFixed(1) }}</span>
            <span class="temp-unit">°C</span>
          </div>
          <div class="temp-status">
            <el-icon v-if="isTempNormal" color="#67c23a" :size="24"><CircleCheckFilled /></el-icon>
            <el-icon v-else color="#f56c6c" :size="24"><WarningFilled /></el-icon>
            <span :style="{ color: isTempNormal ? '#67c23a' : '#f56c6c' }">{{ isTempNormal ? '温度正常' : '温度异常' }}</span>
          </div>
        </el-card>

        <!-- 温度打卡 -->
        <el-card class="temp-checkin-card">
          <template #header><span class="card-header-text">📝 温度打卡</span></template>
          <el-form :model="checkInForm" label-width="0" size="default">
            <el-form-item>
              <span class="input-label">温度值 (℃)</span>
              <el-input-number v-model="checkInForm.temperature" :min="-50" :max="50" :precision="1" :step="0.5" style="width: 100%" />
            </el-form-item>
            <el-form-item>
              <span class="input-label">记录人</span>
              <el-input v-model="checkInForm.recorder" placeholder="请输入记录人" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" style="width: 100%" @click="handleCheckIn" :loading="checkingIn">
                <el-icon><Upload /></el-icon>提交打卡
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </div>

    <!-- 温度打卡记录列表 -->
    <el-card class="checkin-list-card">
      <template #header>
        <div class="card-header">
          <span>📋 温度打卡记录</span>
          <span class="record-count">共 {{ checkInList.length }} 条</span>
        </div>
      </template>
      <el-table :data="checkInList" border stripe size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="recordTime" label="记录时间" width="170" align="center" />
        <el-table-column prop="temperature" label="温度 (℃)" width="120" align="center">
          <template #default="{ row }">
            <span :class="{ 'temp-abnormal-value': row.isAbnormal }">{{ row.temperature.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="isAbnormal" label="是否异常" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isAbnormal ? 'danger' : 'success'" size="small">{{ row.isAbnormal ? '⚠️ 异常' : '✅ 正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recorder" label="记录人" width="120" align="center" />
        <el-table-column prop="note" label="备注" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>

    <!-- 运输状态时间轴 -->
    <el-card class="transport-timeline-card">
      <template #header><span>🕐 运输状态时间轴</span></template>
      <div class="transport-timeline">
        <div v-for="(node, idx) in transportTimeline" :key="idx" class="tt-node" :class="{ 'tt-done': node.done, 'tt-active': node.active }">
          <div class="tt-line">
            <div class="tt-dot">
              <el-icon v-if="node.done" :size="16"><CircleCheckFilled /></el-icon>
              <el-icon v-else-if="node.active" :size="16" class="is-pulsing"><Clock /></el-icon>
              <div v-else class="tt-dot-empty"></div>
            </div>
            <div v-if="idx < transportTimeline.length - 1" class="tt-connector" :class="{ done: node.done }"></div>
          </div>
          <div class="tt-content">
            <div class="tt-title">{{ node.title }}</div>
            <div class="tt-time">{{ node.time }}</div>
            <div v-if="node.desc" class="tt-desc">{{ node.desc }}</div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { CircleCheckFilled, Clock, WarningFilled, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import type { EpTagType } from '@/types/common'

// 运输信息
const transportInfo = reactive({
  transportNo: 'T2024071500123', vehicleNo: '京A·12345', driverName: '赵师傅',
  driverPhone: '13800001234', departTime: '2024-07-03 08:00', arriveTime: '2024-07-03 14:00',
  origin: 'XX市定点屠宰场', destination: 'XX社区超市', status: 2,
})

const statusTagType = (s: number): EpTagType => (({ 1: 'info', 2: 'warning', 3: 'success', 4: 'danger' } as Record<number, EpTagType>)[s] || 'info')
const statusLabel = (s: number) => ({ 1: '待发', 2: '在途', 3: '已送达', 4: '异常' } as Record<number, string>)[s] || '未知'

// 温度相关
const currentTemp = ref(-12.5)
const isTempNormal = computed(() => currentTemp.value >= -18 && currentTemp.value <= 0)
const checkingIn = ref(false)
const checkInForm = reactive({ temperature: -12.5, recorder: '赵师傅' })
const checkInList = ref<any[]>([])

// 图表
const tempChartRef = ref<HTMLElement>()
let tempChart: echarts.ECharts | null = null

// 运输时间轴
const transportTimeline = ref([
  { title: '🚛 发车', time: '08:00', desc: '车牌 京A·12345 从XX屠宰场出发', done: true, active: false },
  { title: '📝 途中打卡 #1', time: '09:30', desc: '温度 -14.2℃，车辆正常运行', done: true, active: false },
  { title: '📝 途中打卡 #2', time: '11:00', desc: '温度 -12.5℃，高速行驶中', done: false, active: true },
  { title: '📝 途中打卡 #3', time: '12:30', desc: '待打卡...', done: false, active: false },
  { title: '🏁 到达签收', time: '14:00', desc: '预计到达，门店签收确认', done: false, active: false },
])

function handleCheckIn() {
  checkingIn.value = true
  setTimeout(() => {
    const isAbnormal = checkInForm.temperature > 0 || checkInForm.temperature < -18
    checkInList.value.unshift({
      id: Date.now(), recordTime: new Date().toLocaleString(),
      temperature: checkInForm.temperature, isAbnormal: isAbnormal ? 1 : 0,
      recorder: checkInForm.recorder || '系统',
      note: isAbnormal ? '温度超出正常范围 (-18℃ ~ 0℃)' : '温度正常',
    })
    currentTemp.value = checkInForm.temperature
    if (isAbnormal) {
      ElMessage.warning('⚠️ 温度异常，请立即检查冷链设备！')
    } else {
      ElMessage.success('温度打卡成功')
    }
    checkingIn.value = false
    updateChart()
  }, 500)
}

function initTempChart() {
  if (!tempChartRef.value) return
  tempChart = echarts.init(tempChartRef.value)

  const now = new Date()
  const times: string[] = []
  const temps: number[] = []
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 20 * 60000)
    times.push(`${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`)
    temps.push(-15 + Math.random() * 12 + (i === 0 ? -2 : 0))
  }

  tempChart.setOption({
    tooltip: { trigger: 'axis', formatter: (params: any) => `${params[0].axisValue}<br/>温度: <b>${params[0].value}℃</b>` },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: times, boundaryGap: false, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', name: '温度 (℃)', min: -25, max: 10,
      axisLabel: { formatter: '{value}℃' } },
    series: [{
      type: 'line', data: temps, smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { color: '#409eff', width: 2 },
      itemStyle: { color: (params: any) => params.value > 0 || params.value < -18 ? '#f56c6c' : '#409eff' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }
      ])},
      markLine: { silent: true, symbol: 'none', data: [
        { yAxis: 0, lineStyle: { color: '#f56c6c', type: 'dashed', width: 1.5 }, label: { formatter: '上限 0°C', fontSize: 10 } },
        { yAxis: -18, lineStyle: { color: '#409eff', type: 'dashed', width: 1.5 }, label: { formatter: '下限 -18°C', fontSize: 10 } },
      ]},
    }],
  })
}

function updateChart() {
  if (!tempChart) return
  const option = tempChart.getOption() as any
  const temps = option.series[0].data as number[]
  const times = option.xAxis[0].data as string[]
  const now = new Date()
  times.push(`${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`)
  temps.push(checkInForm.temperature)
  if (temps.length > 20) { temps.shift(); times.shift() }
  tempChart.setOption({ xAxis: { data: times }, series: [{ data: temps }] })
}

function handleResize() { tempChart?.resize() }

onMounted(() => {
  nextTick(() => initTempChart())
  window.addEventListener('resize', handleResize)
  // 模拟初始打卡记录
  checkInList.value = [
    { id: 1, recordTime: '2024-07-03 08:00:00', temperature: -15.2, isAbnormal: 0, recorder: '赵师傅', note: '发车时初始温度' },
    { id: 2, recordTime: '2024-07-03 09:30:00', temperature: -14.2, isAbnormal: 0, recorder: '赵师傅', note: '温度正常' },
    { id: 3, recordTime: '2024-07-03 11:00:00', temperature: -12.5, isAbnormal: 0, recorder: '赵师傅', note: '温度正常' },
  ]
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  tempChart?.dispose()
})
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }

// 运输信息条
.transport-info-bar { display: flex; flex-wrap: wrap; gap: 20px; padding: 16px 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 8px; margin-bottom: 20px; }
.info-item { display: flex; flex-direction: column; gap: 2px; }
.info-label { font-size: 11px; color: rgba(255,255,255,0.7); }
.info-value { font-size: 14px; font-weight: 600; color: #fff; }

// 监控网格
.monitor-grid { display: flex; gap: 20px; margin-bottom: 20px; }
.monitor-card { flex: 1; }
.temp-chart-card { .card-header { display: flex; justify-content: space-between; align-items: center; } }
.chart-container { height: 320px; }
.monitor-sidebar { width: 280px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; }
.current-temp-card { text-align: center; padding: 8px 0; border: 2px solid #e1f3d8;
  &.temp-abnormal { border-color: #fde2e2; }
  :deep(.el-card__body) { padding: 20px 16px; }
}
.current-temp { margin-bottom: 12px; }
.temp-value { font-size: 48px; font-weight: 700; color: #303133; line-height: 1; }
.temp-unit { font-size: 18px; color: #909399; margin-left: 4px; }
.temp-status { display: flex; align-items: center; justify-content: center; gap: 6px; font-size: 14px; font-weight: 500; }
.temp-checkin-card { flex: 1; }
.card-header-text { font-size: 14px; font-weight: 600; }
.input-label { font-size: 12px; color: #909399; display: block; margin-bottom: 4px; }

// 打卡列表
.checkin-list-card { margin-bottom: 20px; .card-header { display: flex; justify-content: space-between; align-items: center; } }
.record-count { font-size: 12px; color: #909399; }
.temp-abnormal-value { color: #f56c6c; font-weight: 700; }

// 运输时间轴
.transport-timeline-card { margin-bottom: 0; }
.transport-timeline { padding: 16px 0; }
.tt-node { display: flex; gap: 16px; min-height: 56px;
  &.tt-done { .tt-dot { color: #67c23a; } .tt-title { color: #303133; } }
  &.tt-active { .tt-title { color: #409eff; font-weight: 700; } }
  &:not(.tt-done):not(.tt-active) { opacity: 0.5; }
}
.tt-line { display: flex; flex-direction: column; align-items: center; width: 28px; flex-shrink: 0; }
.tt-dot { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; color: #c0c4cc; }
.tt-dot-empty { width: 10px; height: 10px; border-radius: 50%; background: #dcdfe6; }
.tt-connector { width: 2px; flex: 1; min-height: 20px; background: #e4e7ed; &.done { background: #67c23a; } }
.tt-content { flex: 1; padding-bottom: 20px; }
.tt-title { font-size: 14px; font-weight: 600; margin-bottom: 2px; }
.tt-time { font-size: 12px; color: #909399; }
.tt-desc { font-size: 12px; color: #606266; margin-top: 2px; }
.is-pulsing { animation: pulse 1.5s ease-in-out infinite; color: #409eff; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>
