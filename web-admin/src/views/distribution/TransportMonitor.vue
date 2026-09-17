<template>
  <div class="page-container">
    <!-- ========== 运单列表 ========== -->
    <el-card class="transport-list-card">
      <div class="list-header">
        <span class="card-title">冷链运单</span>
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="运单号/车牌/司机" clearable style="width: 200px"
            :prefix-icon="Search" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="handleSearch">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新建运单</el-button>
        </div>
      </div>

      <el-table :data="transportList" v-loading="listLoading" border stripe size="small"
        highlight-current-row @row-click="selectTransport"
        :row-class-name="rowClassName" style="margin-top: 12px">
        <el-table-column prop="transportNo" label="运单号" width="170" />
        <el-table-column label="分割批次" min-width="180">
          <template #default="{ row }">
            <template v-if="splitMap[row.splitBatchId]">
              <div>{{ splitMap[row.splitBatchId].productName || '--' }}</div>
              <div class="sub-text">{{ splitMap[row.splitBatchId].batchNo }}</div>
            </template>
            <span v-else class="sub-text">#{{ row.splitBatchId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="vehicleNo" label="车牌" width="100" />
        <el-table-column label="司机" width="120">
          <template #default="{ row }">
            <div>{{ row.driverName || '--' }}</div>
            <div v-if="row.driverPhone" class="sub-text">{{ row.driverPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="运输路线" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.origin || '--' }}</span>
            <el-icon class="route-arrow"><Right /></el-icon>
            <span>{{ row.destination || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="plannedDepart" label="计划发车" width="160" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="selectTransport(row)">监控详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next, jumper"
          :total="total" :current-page="query.pageNum" :page-size="query.pageSize"
          @current-change="onPageChange" />
      </div>
    </el-card>

    <!-- ========== 运输监控 ========== -->
    <template v-if="currentTransport">
      <div ref="monitorRef" class="monitor-anchor"></div>
      <!-- 运输基本信息条 -->
      <div class="transport-info-bar">
        <div class="info-item"><span class="info-label">运单号</span><span class="info-value">{{ currentTransport.transportNo }}</span></div>
        <div class="info-item"><span class="info-label">车牌号</span><span class="info-value">{{ currentTransport.vehicleNo || '--' }}</span></div>
        <div class="info-item"><span class="info-label">司机</span><span class="info-value">{{ currentTransport.driverName || '--' }}</span></div>
        <div class="info-item"><span class="info-label">电话</span><span class="info-value">{{ currentTransport.driverPhone || '--' }}</span></div>
        <div class="info-item">
          <span class="info-label">{{ currentTransport.departTime ? '实际发车' : '计划发车' }}</span>
          <span class="info-value">{{ currentTransport.departTime || currentTransport.plannedDepart || '--' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">{{ currentTransport.arriveTime ? '实际到达' : '预计到达' }}</span>
          <span class="info-value">{{ currentTransport.arriveTime || currentTransport.plannedArrive || '--' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">状态</span>
          <el-tag :type="statusTagType(currentTransport.status)" size="small">{{ statusLabel(currentTransport.status) }}</el-tag>
        </div>
        <div class="info-actions">
          <el-popconfirm v-if="currentTransport.status === 1" title="确认该运单立即发车？" @confirm="handleDepart(currentTransport)">
            <template #reference>
              <el-button type="warning" size="small" plain>立即发车</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-else-if="currentTransport.status === 2" title="确认该运单已送达？" @confirm="handleArrive(currentTransport)">
            <template #reference>
              <el-button type="success" size="small" plain>确认到达</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>

      <!-- 温控面板 -->
      <div class="monitor-grid">
        <!-- 温度曲线图 -->
        <el-card class="monitor-card temp-chart-card">
          <template #header>
            <div class="card-header">
              <span>温度变化曲线</span>
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
          <el-card class="current-temp-card" :class="{ 'temp-abnormal': currentTemp !== null && (currentTemp > 0 || currentTemp < -18) }">
            <div class="current-temp">
              <template v-if="currentTemp !== null">
                <span class="temp-value">{{ currentTemp.toFixed(1) }}</span>
                <span class="temp-unit">°C</span>
              </template>
              <span v-else class="temp-value temp-empty">--</span>
            </div>
            <div class="temp-status">
              <template v-if="currentTemp === null">
                <el-icon color="#909399" :size="24"><InfoFilled /></el-icon>
                <span style="color:#909399">暂无打卡</span>
              </template>
              <template v-else>
                <el-icon v-if="isTempNormal" color="#67c23a" :size="24"><CircleCheckFilled /></el-icon>
                <el-icon v-else color="#f56c6c" :size="24"><WarningFilled /></el-icon>
                <span :style="{ color: isTempNormal ? '#67c23a' : '#f56c6c' }">{{ isTempNormal ? '温度正常' : '温度异常' }}</span>
              </template>
            </div>
          </el-card>

          <!-- 温度打卡 -->
          <el-card class="temp-checkin-card">
            <template #header><span class="card-header-text">温度打卡</span></template>
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
                <el-button type="primary" style="width: 100%" @click="handleCheckIn" :loading="checkingIn"
                  :disabled="currentTransport.status !== 2">
                  <el-icon><Upload /></el-icon>提交打卡
                </el-button>
                <div v-if="currentTransport.status !== 2" class="checkin-tip">仅「在途」运单可以记录温度</div>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
      </div>

      <!-- 温度打卡记录列表 -->
      <el-card class="checkin-list-card">
        <template #header>
          <div class="card-header">
            <span>温度打卡记录</span>
            <span class="record-count">共 {{ checkInList.length }} 条</span>
          </div>
        </template>
        <el-table :data="checkInList" border stripe size="small">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="recordTime" label="记录时间" width="170" align="center" />
          <el-table-column prop="temperature" label="温度 (℃)" width="120" align="center">
            <template #default="{ row }">
              <span :class="{ 'temp-abnormal-value': row.isAbnormal }">{{ Number(row.temperature).toFixed(1) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="isAbnormal" label="是否异常" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isAbnormal ? 'danger' : 'success'" size="small">{{ row.isAbnormal ? '异常' : '正常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="recorder" label="记录人" min-width="160" align="center" />
        </el-table>
      </el-card>

      <!-- 运输状态时间轴 -->
      <el-card class="transport-timeline-card">
        <template #header><span>运输状态时间轴</span></template>
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
    </template>

    <el-empty v-else description="请选择上方运单查看冷链监控详情" />

    <!-- ========== 新建/编辑运单弹窗 ========== -->
    <el-dialog v-model="createVisible" :title="editingId ? '编辑冷链运单' : '新建冷链运单'" width="680px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="分割批次" prop="splitBatchId">
          <el-select v-model="createForm.splitBatchId" filterable remote placeholder="输入产品名/批次号搜索"
            :remote-method="searchSplits" :loading="splitLoading" style="width: 100%"
            :disabled="editingId !== null && editingStatus !== 1" @change="handleSplitChange">
            <el-option v-for="s in splitOptions" :key="s.id" :label="`${s.productName || '--'}（${s.batchNo}）`" :value="s.id" />
          </el-select>
          <div v-if="editingId !== null && editingStatus !== 1" class="form-tip">运单已发车，关联分割批次不可变更</div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="车牌号" prop="vehicleNo">
              <el-input v-model="createForm.vehicleNo" placeholder="如 京A·12345" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="车辆类型">
              <el-select v-model="createForm.vehicleType" placeholder="请选择" clearable style="width:100%">
                <el-option label="冷藏车" value="冷藏车" />
                <el-option label="冷冻车" value="冷冻车" />
                <el-option label="保温车" value="保温车" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="司机姓名" prop="driverName">
              <el-input v-model="createForm.driverName" placeholder="请输入司机姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="司机电话" prop="driverPhone">
              <el-input v-model="createForm.driverPhone" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="起运地" prop="origin">
              <el-input v-model="createForm.origin" placeholder="如 顺鑫屠宰场冷库" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目的地" prop="destination">
              <el-select
                v-model="createForm.destination"
                placeholder="请选择或输入门店"
                filterable
                allow-create
                default-first-option
                style="width: 100%"
              >
                <el-option
                  v-for="item in storeOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.name"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划发车">
              <el-date-picker v-model="createForm.plannedDepart" type="datetime" placeholder="选择日期时间"
                value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划到达">
              <el-date-picker v-model="createForm.plannedArrive" type="datetime" placeholder="选择日期时间"
                value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-popconfirm v-if="editingId && editingStatus === 1" title="确认该运单立即发车？" @confirm="handleDepartFromEdit">
          <template #reference>
            <el-button type="warning">发车</el-button>
          </template>
        </el-popconfirm>
        <el-popconfirm v-else-if="editingId && editingStatus === 2" title="确认该运单已送达？" @confirm="handleArriveFromEdit">
          <template #reference>
            <el-button type="success">确认到达</el-button>
          </template>
        </el-popconfirm>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">{{ editingId ? '保存修改' : '确认创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { CircleCheckFilled, Clock, WarningFilled, Upload, Search, RefreshLeft, Plus, Right, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import type { FormInstance } from 'element-plus'
import type { EpTagType } from '@/types/common'
import { distributionApi } from '@/api/modules/distribution'
import request from '@/utils/request'

// ==================== 运单列表 ====================
const query = reactive({ keyword: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })
const transportList = ref<any[]>([])
const total = ref(0)
const listLoading = ref(false)
const currentTransport = ref<any>(null)
// 分割批次 id -> {productName, batchNo} 映射，用于列表展示
const splitMap = reactive<Record<number, any>>({})

const statusOptions = [
  { value: 1, label: '待发车' },
  { value: 2, label: '运输中' },
  { value: 3, label: '待签收' },
  { value: 4, label: '已签收' },
]
const statusTagType = (s: number): EpTagType => (({ 1: 'info', 2: 'warning', 3: 'primary', 4: 'success' } as Record<number, EpTagType>)[s] || 'info')
const statusLabel = (s: number) => ({ 1: '待发车', 2: '运输中', 3: '待签收', 4: '已签收' } as Record<number, string>)[s] || '未知'

function rowClassName({ row }: { row: any }) {
  return currentTransport.value?.id === row.id ? 'current-row' : ''
}

async function fetchTransports() {
  listLoading.value = true
  try {
    const res: any = await distributionApi.getTransports({
      keyword: query.keyword || undefined,
      status: query.status,
      pageNum: query.pageNum, pageSize: query.pageSize,
    })
    transportList.value = res?.records || []
    total.value = res?.total || 0
    // 补齐列表引用到的分割批次名称
    const missing = [...new Set(transportList.value.map((t: any) => t.splitBatchId).filter((id: number) => id && !splitMap[id]))]
    await Promise.all(missing.map((id: number) => distributionApi.getSplitDetail(id)
      .then((s: any) => { splitMap[id] = s }).catch(() => {})))
  } catch { /* 拦截器已提示 */ } finally { listLoading.value = false }
}

function handleSearch() { query.pageNum = 1; fetchTransports() }
function handleReset() {
  query.keyword = ''; query.status = undefined; query.pageNum = 1
  fetchTransports()
}
function onPageChange(p: number) { query.pageNum = p; fetchTransports() }

// ==================== 运输监控 ====================
const currentTemp = ref<number | null>(null)
const isTempNormal = computed(() => currentTemp.value === null || (currentTemp.value >= -18 && currentTemp.value <= 0))
const checkingIn = ref(false)
const checkInForm = reactive({ temperature: 0, recorder: '' })
const checkInList = ref<any[]>([])
const transportTimeline = ref<any[]>([])
const monitorRef = ref<HTMLElement>()

// 图表
const tempChartRef = ref<HTMLElement>()
let tempChart: echarts.ECharts | null = null

async function selectTransport(row: any) {
  if (!row || currentTransport.value?.id === row.id) return
  currentTransport.value = row
  checkInForm.recorder = row.driverName || ''
  currentTemp.value = null
  checkInList.value = []
  buildTimeline(row)
  await loadTemperatureLogs()
  nextTick(() => monitorRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

async function handleDepart(row: any) {
  try {
    await distributionApi.departTransport(row.id)
    ElMessage.success(`运单 ${row.transportNo} 已发车`)
    await afterStatusChange(row.id)
  } catch { /* 拦截器已提示 */ }
}

async function handleArrive(row: any) {
  try {
    await distributionApi.arriveTransport(row.id)
    ElMessage.success(`运单 ${row.transportNo} 已确认送达`)
    await afterStatusChange(row.id)
  } catch { /* 拦截器已提示 */ }
}

async function handleDepartFromEdit() {
  if (!editingId.value) return
  try {
    await distributionApi.departTransport(editingId.value)
    ElMessage.success('运单已发车')
    createVisible.value = false
    await afterStatusChange(editingId.value)
  } catch { /* 拦截器已提示 */ }
}

async function handleArriveFromEdit() {
  if (!editingId.value) return
  try {
    await distributionApi.arriveTransport(editingId.value)
    ElMessage.success('运单已确认送达')
    createVisible.value = false
    await afterStatusChange(editingId.value)
  } catch { /* 拦截器已提示 */ }
}

async function afterStatusChange(id: number) {
  await fetchTransports()
  const latest = transportList.value.find((t: any) => t.id === id)
  if (latest) {
    currentTransport.value = latest
    buildTimeline(latest)
  }
}

async function handleCheckIn() {
  if (!currentTransport.value) { ElMessage.warning('未选择运输任务，无法打卡'); return }
  checkingIn.value = true
  try {
    await distributionApi.addTemperature(currentTransport.value.id, {
      temperature: checkInForm.temperature, recorder: checkInForm.recorder,
    })
    const isAbnormal = checkInForm.temperature > 0 || checkInForm.temperature < -18
    currentTemp.value = checkInForm.temperature
    if (isAbnormal) ElMessage.warning('温度异常，请立即检查冷链设备！')
    else ElMessage.success('温度打卡成功')
    await loadTemperatureLogs()
  } catch { /* 错误已由拦截器提示 */ } finally { checkingIn.value = false }
}

async function loadTemperatureLogs() {
  if (!currentTransport.value) return
  try {
    const logs: any = await distributionApi.getTemperatureLog(currentTransport.value.id)
    checkInList.value = logs || []
    currentTemp.value = checkInList.value.length ? Number(checkInList.value[0].temperature) : null
    nextTick(() => renderChart())
  } catch { checkInList.value = [] }
}

function renderChart() {
  if (!tempChartRef.value) return
  if (!tempChart) tempChart = echarts.init(tempChartRef.value)

  const logs = checkInList.value.slice().reverse()
  const times = logs.map((l: any) => (l.recordTime || '').slice(11, 16) || '--')
  const temps = logs.map((l: any) => Number(l.temperature))

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

function handleResize() { tempChart?.resize() }

// 根据运输状态与时间生成时间轴
function buildTimeline(t: any) {
  const status = t.status
  // 实际时间不存在时展示计划时间并加"预计"前缀，避免在途运单看起来已到达
  const departTime = t.departTime || (t.plannedDepart ? `预计 ${t.plannedDepart}` : '')
  const arriveTime = t.arriveTime || (t.plannedArrive ? `预计 ${t.plannedArrive}` : '')
  transportTimeline.value = [
    {
      title: '发车', time: departTime || '--', desc: `车牌 ${t.vehicleNo || '--'} 从 ${t.origin || '--'} 出发`,
      done: status >= 2, active: status === 1,
    },
    {
      title: '运输途中', time: t.departTime || '--', desc: '冷链运输中，全程温控记录',
      done: status >= 3, active: status === 2,
    },
    {
      title: '到达门店', time: arriveTime || '--', desc: `到达 ${t.destination || '--'}，等待门店签收`,
      done: status >= 3, active: status === 3,
    },
    {
      title: '门店签收', time: status >= 4 ? arriveTime : '--', desc: '门店已确认签收',
      done: status >= 4, active: false,
    },
  ]
}

// ==================== 新建/编辑运单 ====================
const createVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const editingStatus = ref(1)
const createFormRef = ref<FormInstance>()
const splitOptions = ref<any[]>([])
const splitLoading = ref(false)
const createForm = reactive<any>({
  splitBatchId: null, vehicleNo: '', vehicleType: '', refrigeration: '',
  driverName: '', driverPhone: '', origin: '', destination: '',
  plannedDepart: '', plannedArrive: '',
})
const createRules = {
  splitBatchId: [{ required: true, message: '请选择分割批次', trigger: 'change' }],
  vehicleNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  driverName: [{ required: true, message: '请输入司机姓名', trigger: 'blur' }],
  origin: [{ required: true, message: '请输入起运地', trigger: 'blur' }],
  destination: [{ required: true, message: '请输入目的地', trigger: 'blur' }],
  driverPhone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
}

async function searchSplits(kw: string) {
  splitLoading.value = true
  try {
    const res: any = await distributionApi.getSplits({ keyword: kw || undefined, pageNum: 1, pageSize: 20 })
    splitOptions.value = res?.records || []
    splitOptions.value.forEach((s: any) => { splitMap[s.id] = s })
  } catch { splitOptions.value = [] } finally { splitLoading.value = false }
}

// 门店下拉：从机构树提取 type=retail 的节点
const storeOptions = ref<Array<{ id: number; name: string }>>([])
const fetchStoreList = async () => {
  try {
    const tree = await request.get('/system/orgs/tree')
    const list: Array<{ id: number; name: string }> = []
    const flatten = (nodes: any[]) => {
      if (!Array.isArray(nodes)) return
      nodes.forEach((n) => {
        if (n.type === 'retail') list.push({ id: n.id, name: n.name })
        if (n.children?.length) flatten(n.children)
      })
    }
    flatten(tree || [])
    storeOptions.value = list
  } catch (error) {
    console.error('获取门店列表失败:', error)
    storeOptions.value = []
  }
}

// 选好分割批次后自动带入起运地为该屠宰场
async function handleSplitChange(splitId: number) {
  if (!splitId) return
  const record = splitOptions.value.find((s: any) => s.id === splitId)
  if (!record) return
  // 优先用 record 自带的 slaughterhouse
  if (record.slaughterhouse) {
    createForm.origin = record.slaughterhouse
    return
  }
  // 通过胴体批次查屠宰场：getBatches 返回的胴体批次含 slaughterhouse
  try {
    const res: any = await distributionApi.getBatches({ current: 1, size: 500 })
    const batches = res?.records || []
    const batch = batches.find((b: any) =>
      b.batchNo === record.batchNo ||
      (record.parentBatchId && b.id === record.parentBatchId)
    )
    if (batch?.slaughterhouse) {
      createForm.origin = batch.slaughterhouse
    }
  } catch { /* 忽略，用户手动输入 */ }
}

function resetForm() {
  Object.assign(createForm, {
    splitBatchId: null, vehicleNo: '', vehicleType: '', refrigeration: '',
    driverName: '', driverPhone: '', origin: '', destination: '',
    plannedDepart: '', plannedArrive: '',
  })
}

async function openCreate() {
  editingId.value = null
  editingStatus.value = 1
  resetForm()
  createVisible.value = true
  await searchSplits('')
  nextTick(() => createFormRef.value?.clearValidate())
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (editingId.value) {
        await distributionApi.updateTransport(editingId.value, { ...createForm })
        ElMessage.success('运单信息已更新')
        createVisible.value = false
        await fetchTransports()
        const updatedId = editingId.value
        const latest = transportList.value.find((t: any) => t.id === updatedId)
        if (latest && currentTransport.value?.id === updatedId) {
          currentTransport.value = latest
          checkInForm.recorder = latest.driverName || ''
          buildTimeline(latest)
        }
      } else {
        const res: any = await distributionApi.createTransport({ ...createForm })
        ElMessage.success(`运单 ${res.transportNo} 创建成功，状态：待发`)
        createVisible.value = false
        query.pageNum = 1
        await fetchTransports()
        const created = transportList.value.find((t: any) => t.id === res.id)
        if (created) await selectTransport(created)
      }
    } catch { /* 拦截器已提示 */ } finally { submitting.value = false }
  })
}

// ==================== 生命周期 ====================
onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await fetchTransports()
  fetchStoreList()
  // 默认加载第一条运单的监控
  if (transportList.value.length) await selectTransport(transportList.value[0])
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  tempChart?.dispose()
})
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #f5f7fa; border-radius: 4px; }
.card-title { font-size: 16px; font-weight: 700; color: #303133; }
.monitor-anchor { scroll-margin-top: 12px; }

// 运单列表
.transport-list-card { margin-bottom: 20px;
  .list-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
  .toolbar { display: flex; gap: 8px; flex-wrap: wrap; }
  .sub-text { font-size: 11px; color: #909399; }
  .route-arrow { display: inline-block; vertical-align: middle; margin: 0 4px; color: #c0c4cc; }
}
:deep(.el-table .current-row) { background-color: #ecf5ff !important; }
.pager { display: flex; justify-content: center; margin-top: 12px; }

// 运输信息条
.transport-info-bar { display: flex; flex-wrap: wrap; gap: 20px; align-items: center; padding: 16px 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 8px; margin-bottom: 20px; }
.info-item { display: flex; flex-direction: column; gap: 2px; }
.info-label { font-size: 11px; color: rgba(255,255,255,0.7); }
.info-value { font-size: 14px; font-weight: 600; color: #fff; }
.info-actions { margin-left: auto; }

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
.temp-empty { color: #c0c4cc; }
.temp-unit { font-size: 18px; color: #909399; margin-left: 4px; }
.temp-status { display: flex; align-items: center; justify-content: center; gap: 6px; font-size: 14px; font-weight: 500; }
.temp-checkin-card { flex: 1; }
.card-header-text { font-size: 14px; font-weight: 600; }
.input-label { font-size: 12px; color: #909399; display: block; margin-bottom: 4px; }
.checkin-tip { font-size: 12px; color: #e6a23c; text-align: center; margin-top: 6px; }
.form-tip { font-size: 12px; color: #909399; line-height: 1.4; margin-top: 2px; }

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
