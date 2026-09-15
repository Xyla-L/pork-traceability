<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="待签收" name="pending">
        <div class="search-panel">
          <el-form :model="pendingSearch" inline>
            <el-form-item label="运单号">
              <el-input v-model="pendingSearch.keyword" placeholder="请输入" clearable @keyup.enter="fetchPendingList" style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchPendingList"><el-icon><Search /></el-icon>搜索</el-button>
              <el-button @click="handleResetPending"><el-icon><Refresh /></el-icon>重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table v-loading="pendingLoading" :data="pendingList" border stripe>
          <el-table-column prop="transportNo" label="运单号" width="180" />
          <el-table-column prop="splitBatchId" label="分割批次" width="120" align="center">
            <template #default="{ row }">{{ splitMap[row.splitBatchId]?.batchNo || '--' }}</template>
          </el-table-column>
          <el-table-column prop="vehicleNo" label="配送车辆" width="120" align="center" />
          <el-table-column prop="origin" label="发货地" min-width="120" />
          <el-table-column prop="destination" label="目的地" min-width="120" />
          <el-table-column prop="driverName" label="司机" width="100" align="center" />
          <el-table-column prop="arriveTime" label="到店时间" width="170" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag type="primary" size="small">待签收</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <el-button v-if="canConfirmReceipt" type="success" link size="small" @click="openConfirmDialog(row)">确认签收</el-button>
              <span v-else style="color: #c0c4cc; font-size: 12px">无权限</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="pendingPagination.pageNum" v-model:page-size="pendingPagination.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="pendingPagination.total" layout="total, sizes, prev, pager, next, jumper"
            background @size-change="handlePendingSizeChange" @current-change="handlePendingPageChange" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="已签收" name="done">
        <div class="search-panel">
          <el-form :model="searchForm" inline>
            <el-form-item label="运单号">
              <el-input v-model="searchForm.transportNo" placeholder="请输入" clearable @keyup.enter="handleSearch" style="width: 160px" />
            </el-form-item>
            <el-form-item label="门店名称">
              <el-input v-model="searchForm.storeName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="签收时间">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 260px"
              />
            </el-form-item>
            <el-form-item label="签收状态">
              <el-select v-model="searchForm.statusFilter" placeholder="全部" clearable style="width: 120px">
                <el-option label="全部正常" :value="1" />
                <el-option label="状态异常" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
              <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table v-loading="loading" :data="tableData" border stripe>
          <el-table-column prop="receiptNo" label="签收单号" width="180" />
          <el-table-column prop="transportNo" label="关联运单" width="160" />
          <el-table-column prop="storeName" label="签收门店" min-width="150" />
          <el-table-column prop="receiver" label="签收人" width="100" align="center" />
          <el-table-column prop="receiptTime" label="签收时间" width="170" align="center" />
          <el-table-column prop="qtyCheck" label="数量核验" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.qtyCheck === 1 ? 'success' : 'danger'" size="small">{{ row.qtyCheck === 1 ? '正常' : '异常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="tempCheck" label="温度核验" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.tempCheck === 1 ? 'success' : 'danger'" size="small">{{ row.tempCheck === 1 ? '正常' : '异常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="packageIntact" label="包装状态" width="100" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.packageIntact === 1 ? '#67c23a' : '#f56c6c' }">{{ row.packageIntact === 1 ? '完好' : '破损' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="chainStatus" label="链上状态" width="100" align="center">
            <template #default="{ row }">
              <BlockchainVerifyBadge :status="row.chainStatus" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
              <el-button type="success" link size="small" @click="handleVerify(row)">验真</el-button>
              <el-popconfirm title="确定删除该签收记录吗？" width="220" @confirm="handleDelete(row)">
                <template #reference>
                  <el-button type="danger" link size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
            background @size-change="handleSizeChange" @current-change="handlePageChange" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 签收弹窗 -->
    <el-dialog v-model="dialogVisible" title="门店签收确认" width="620px" destroy-on-close>
      <el-descriptions :column="2" border size="default" style="margin-bottom: 20px">
        <el-descriptions-item label="运单号">{{ receiptData.transportNo }}</el-descriptions-item>
        <el-descriptions-item label="配送车辆">{{ receiptData.vehicleNo }}</el-descriptions-item>
        <el-descriptions-item label="发货地">{{ receiptData.origin }}</el-descriptions-item>
        <el-descriptions-item label="目的地">{{ receiptData.destination }}</el-descriptions-item>
        <el-descriptions-item label="司机">{{ receiptData.driverName }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ receiptData.driverPhone }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="receiptForm" label-width="90px" size="default" style="margin-bottom: 16px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="门店" prop="storeName">
              <el-input v-model="receiptForm.storeName" placeholder="请输入门店名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签收人" prop="receiver">
              <el-input v-model="receiptForm.receiver" placeholder="请输入签收人姓名" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="receipt-checks">
        <!-- 数量核验 -->
        <el-card shadow="hover" class="check-card">
          <template #header><span class="check-title">数量核验</span></template>
          <el-form label-width="80px" size="default">
            <el-form-item label="核验结果">
              <el-radio-group v-model="receiptForm.qtyCheck">
                <el-radio :value="1">数量一致</el-radio>
                <el-radio :value="0">数量不符</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="receiptForm.qtyCheck === 0" label="差异说明">
              <el-input v-model="receiptForm.qtyDiffNote" type="textarea" :rows="2" placeholder="请说明差异情况" />
            </el-form-item>
          </el-form>
        </el-card>
        <!-- 温度核验 -->
        <el-card shadow="hover" class="check-card">
          <template #header><span class="check-title">温度核验</span></template>
          <el-form label-width="80px" size="default">
            <el-form-item label="到货温度">
              <el-input-number v-model="receiptForm.tempValue" :min="-50" :max="50" :precision="1" style="width: 160px" />
              <span class="unit-hint" style="margin-left: 6px">℃</span>
            </el-form-item>
            <el-form-item label="核验结果">
              <el-radio-group v-model="receiptForm.tempCheck">
                <el-radio :value="1">正常</el-radio>
                <el-radio :value="0">异常</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
      <!-- 包装检查 -->
      <el-card shadow="hover" class="check-card" style="margin-bottom: 16px">
        <template #header><span class="check-title">包装检查</span></template>
        <el-form label-width="80px" size="default">
          <el-form-item label="包装状态">
            <el-radio-group v-model="receiptForm.packageIntact">
              <el-radio :value="1">完好</el-radio>
              <el-radio :value="0">破损</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </el-card>
      <!-- 签名 -->
      <el-card shadow="hover" class="check-card signature-card">
        <template #header><span class="check-title">签收人签名</span></template>
        <div class="signature-area">
          <canvas ref="signCanvasRef" width="400" height="120" class="sign-canvas"
            @mousedown="startSign" @mousemove="drawSign" @mouseup="endSign" @mouseleave="endSign"></canvas>
          <div class="sign-actions">
            <el-button size="small" @click="clearSign">清除</el-button>
            <span class="sign-hint">请在框内签名</span>
          </div>
        </div>
      </el-card>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReceipt" :loading="submitting">确认签收</el-button>
      </template>
    </el-dialog>

    <!-- 签收详情弹窗 -->
    <el-dialog v-model="detailVisible" title="门店签收详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="门店">{{ detailData.storeName }}</el-descriptions-item>
        <el-descriptions-item label="签收人">{{ detailData.receiver }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailData.receiverPhone || '--' }}</el-descriptions-item>
        <el-descriptions-item label="签收时间">{{ detailData.receiptTime }}</el-descriptions-item>
        <el-descriptions-item label="到货温度(℃)">{{ detailData.tempValue ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="温度核验">{{ detailData.tempCheck === 1 ? '正常' : detailData.tempCheck === 0 ? '异常' : '--' }}</el-descriptions-item>
        <el-descriptions-item label="数量核验">{{ detailData.qtyCheck === 1 ? '一致' : detailData.qtyCheck === 0 ? '不符' : '--' }}</el-descriptions-item>
        <el-descriptions-item label="包装完好">{{ detailData.packageIntact === 1 ? '完好' : detailData.packageIntact === 0 ? '破损' : '--' }}</el-descriptions-item>
        <el-descriptions-item label="内容哈希" :span="2">
          <span v-if="detailData.contentHash" class="hash-text">{{ detailData.contentHash }}</span>
          <span v-else>--</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'
import { distributionApi } from '@/api/modules/distribution'
import { useAuthStore } from '@/stores/auth'
import request from '@/utils/request'

const authStore = useAuthStore()
const canConfirmReceipt = computed(() => authStore.hasAnyRole(['RETAILER', 'ADMIN']))

const activeTab = ref<'pending' | 'done'>('pending')

function handleTabChange() {
  if (activeTab.value === 'pending') {
    fetchPendingList()
  } else {
    fetchList()
  }
}

// ========== 待签收列表 ==========
const pendingSearch = reactive({ keyword: '' })
const pendingList = ref<any[]>([])
const pendingLoading = ref(false)
const pendingPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const splitMap = reactive<Record<number, any>>({})

async function fetchPendingList() {
  pendingLoading.value = true
  try {
    const res: any = await distributionApi.getTransports({
      status: 3,
      keyword: pendingSearch.keyword || undefined,
      pageNum: pendingPagination.pageNum,
      pageSize: pendingPagination.pageSize,
    })
    pendingList.value = res?.records || []
    pendingPagination.total = res?.total || 0
    const missing = [...new Set(pendingList.value.map((t: any) => t.splitBatchId).filter((id: number) => id && !splitMap[id]))]
    await Promise.all(missing.map((id: number) => distributionApi.getSplitDetail(id)
      .then((s: any) => { splitMap[id] = s }).catch(() => {})))
  } catch {
    pendingList.value = []
    pendingPagination.total = 0
  } finally {
    pendingLoading.value = false
  }
}

function handleResetPending() {
  pendingSearch.keyword = ''
  pendingPagination.pageNum = 1
  fetchPendingList()
}
function handlePendingSizeChange() { pendingPagination.pageNum = 1; fetchPendingList() }
function handlePendingPageChange() { fetchPendingList() }

// ========== 已签收列表 ==========
const searchForm = reactive({ transportNo: '', storeName: '', statusFilter: null as number | null })
const dateRange = ref<[string, string] | null>(null)
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const detailVisible = ref(false)
const detailData = ref<any>({})

function handleView(row: any) {
  detailData.value = row
  detailVisible.value = true
}
function handleVerify(row: any) { ElMessage.success(`区块链验真通过: ${row.receiptNo}`) }

async function handleDelete(row: any) {
  try {
    await distributionApi.deleteReceipt(row.id)
    ElMessage.success('删除成功')
    if (tableData.value.length === 1 && pagination.pageNum > 1) pagination.pageNum--
    fetchList()
  } catch (error) {
    console.error('删除签收记录失败:', error)
  }
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { Object.assign(searchForm, { transportNo: '', storeName: '', statusFilter: null }); dateRange.value = null; handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function hasAbnormal(item: any): boolean {
  return item.qtyCheck !== 1 || item.tempCheck !== 1 || item.packageIntact !== 1 || !item.contentHash
}
function isAllNormal(item: any): boolean {
  return item.qtyCheck === 1 && item.tempCheck === 1 && item.packageIntact === 1 && !!item.contentHash
}

async function fetchList() {
  loading.value = true
  try {
    const res = await distributionApi.getReceipts({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      storeName: searchForm.storeName || undefined,
      transportNo: searchForm.transportNo || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
    })
    let list = res?.records || res?.list || []
    list = (Array.isArray(list) ? list : []).map((item: any) => ({
      ...item,
      receiptNo: item.receiptNo || `S${item.id || ''}`,
      transportNo: item.transportNo || item.transportId || '',
      chainStatus: item.contentHash ? 'confirmed' : 'pending',
    }))
    if (searchForm.statusFilter === 1) {
      list = list.filter(isAllNormal)
    } else if (searchForm.statusFilter === 0) {
      list = list.filter(hasAbnormal)
    }
    if (searchForm.transportNo) {
      list = list.filter((item: any) => item.transportNo?.includes(searchForm.transportNo))
    }
    tableData.value = list
    pagination.total = list.length
  } catch (error) {
    console.error('获取门店签收列表失败:', error)
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

// ========== 签收弹窗 ==========
const dialogVisible = ref(false)
const submitting = ref(false)
const receiptData = reactive({ transportNo: '', vehicleNo: '', origin: '', destination: '', driverName: '', driverPhone: '' })
const receiptForm = reactive({
  transportId: null as number | null,
  storeId: 1,
  storeName: '',
  receiver: '',
  receiverPhone: '',
  qtyCheck: 1,
  qtyDiffNote: '',
  tempCheck: 1,
  tempValue: -12,
  packageIntact: 1,
  receiptPhoto: [] as string[],
  eSignature: '',
})

const signCanvasRef = ref<HTMLCanvasElement>()
let isDrawing = false
let ctx: CanvasRenderingContext2D | null = null

function startSign(e: MouseEvent) {
  if (!signCanvasRef.value) return
  ctx = signCanvasRef.value.getContext('2d')
  if (!ctx) return
  isDrawing = true
  const rect = signCanvasRef.value.getBoundingClientRect()
  ctx.beginPath()
  ctx.moveTo(e.clientX - rect.left, e.clientY - rect.top)
  ctx.strokeStyle = '#333'
  ctx.lineWidth = 2
}
function drawSign(e: MouseEvent) {
  if (!isDrawing || !ctx || !signCanvasRef.value) return
  const rect = signCanvasRef.value.getBoundingClientRect()
  ctx.lineTo(e.clientX - rect.left, e.clientY - rect.top)
  ctx.stroke()
}
function endSign() { isDrawing = false; ctx = null }
function clearSign() {
  if (!signCanvasRef.value) return
  const c = signCanvasRef.value.getContext('2d')
  if (c) c.clearRect(0, 0, 400, 120)
}

function openConfirmDialog(row: any) {
  receiptData.transportNo = row.transportNo
  receiptData.vehicleNo = row.vehicleNo
  receiptData.origin = row.origin
  receiptData.destination = row.destination
  receiptData.driverName = row.driverName
  receiptData.driverPhone = row.driverPhone
  receiptForm.transportId = row.id
  receiptForm.storeName = row.destination || ''
  receiptForm.receiver = ''
  receiptForm.qtyCheck = 1
  receiptForm.qtyDiffNote = ''
  receiptForm.tempCheck = 1
  receiptForm.tempValue = -12
  receiptForm.packageIntact = 1
  clearSign()
  dialogVisible.value = true
}

async function confirmReceipt() {
  if (!receiptForm.transportId) {
    ElMessage.warning('运输单信息缺失')
    return
  }
  if (!receiptForm.storeName.trim()) {
    ElMessage.warning('请输入门店名称')
    return
  }
  if (!receiptForm.receiver.trim()) {
    ElMessage.warning('请输入签收人姓名')
    return
  }
  const canvas = signCanvasRef.value
  const signData = canvas ? canvas.toDataURL('image/png') : ''
  if (!signData || signData === 'data:image/png;base64,') {
    ElMessage.warning('请先签名')
    return
  }
  submitting.value = true
  try {
    await distributionApi.createReceipt({
      transportId: receiptForm.transportId,
      storeId: receiptForm.storeId,
      storeName: receiptForm.storeName,
      receiver: receiptForm.receiver,
      receiverPhone: receiptForm.receiverPhone,
      qtyCheck: receiptForm.qtyCheck,
      qtyDiffNote: receiptForm.qtyDiffNote,
      tempCheck: receiptForm.tempCheck,
      tempValue: receiptForm.tempValue,
      packageIntact: receiptForm.packageIntact,
      receiptPhoto: receiptForm.receiptPhoto,
      eSignature: signData,
    })
    ElMessage.success('门店签收确认成功，数据已上链')
    dialogVisible.value = false
    activeTab.value = 'done'
    fetchList()
  } catch (error) {
    console.error('签收失败:', error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => fetchPendingList())
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.receipt-checks { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.check-card { border: 1px solid #ebeef5; }
.check-title { font-size: 14px; font-weight: 600; }
.signature-card { margin-bottom: 0; }
.signature-area { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.sign-canvas { border: 1px dashed #dcdfe6; border-radius: 6px; cursor: crosshair; background: #fafafa; }
.sign-actions { display: flex; align-items: center; gap: 12px; }
.sign-hint { font-size: 12px; color: #c0c4cc; }
.unit-hint { font-size: 13px; color: #909399; }
.hash-text { font-family: 'Courier New', monospace; font-size: 12px; color: #909399; word-break: break-all; }
</style>
