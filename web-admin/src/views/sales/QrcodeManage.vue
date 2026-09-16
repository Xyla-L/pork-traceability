<template>
  <div class="page-container">
    <!-- 搜索 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="批次号">
          <el-input v-model="searchForm.batchNo" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="二维码">
          <el-input v-model="searchForm.qrCode" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="未激活" :value="0" />
            <el-option label="在售" :value="1" />
            <el-option label="已售" :value="2" />
            <el-option label="已过期" :value="3" />
            <el-option label="已召回" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="handleGenerate"><el-icon><PictureFilled /></el-icon>生成二维码</el-button>
      <el-button @click="handleBatchDownload" :disabled="selectedIds.length === 0">
        <el-icon><Download /></el-icon>批量下载 ({{ selectedIds.length }})
      </el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="qrCode" label="二维码编号" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.qrCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="batchNo" label="关联批次" width="160">
        <template #default="{ row }">{{ row.batchNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="transportNo" label="运输单号" width="160">
        <template #default="{ row }">{{ row.transportNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="storeName" label="所属门店" width="140">
        <template #default="{ row }">{{ row.storeName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="expireDate" label="过期日期" width="120" align="center">
        <template #default="{ row }">{{ row.expireDate || '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="qrStatusType(row.status)" size="small">{{ qrStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="生成时间" width="160" align="center">
        <template #default="{ row }">{{ row.createTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="二维码预览" width="100" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handlePreview(row)">预览</el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" type="success" link size="small" @click="handleActivate(row)">激活</el-button>
          <el-button type="primary" link size="small" @click="handleDownload(row)">下载</el-button>
          <el-button v-if="row.status === 3" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <!-- 生成二维码弹窗 -->
    <el-dialog v-model="genVisible" title="生成二维码" width="560px" destroy-on-close>
      <el-form ref="genFormRef" :model="genForm" :rules="genRules" label-width="120px">
        <el-form-item label="选择门店签收单" prop="receiptId">
          <el-select v-model="genForm.receiptId" placeholder="请选择已运抵门店的签收单" filterable style="width: 100%"
            @visible-change="handleReceiptDropdownVisible">
            <el-option v-for="r in receiptOptions" :key="r.id" :label="receiptOptionLabel(r)" :value="r.id" />
          </el-select>
          <div v-if="selectedReceipt" class="receipt-detail-hint">
            <span>门店：{{ selectedReceipt.storeName }}</span>
            <span>签收时间：{{ selectedReceipt.receiptTime }}</span>
          </div>
        </el-form-item>
        <el-form-item label="生成数量" prop="count">
          <el-input-number v-model="genForm.count" :min="1" :max="1000" :step="1" style="width: 200px" />
        </el-form-item>
        <el-form-item label="过期日期" prop="expireDate">
          <el-date-picker v-model="genForm.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="默认7天后"
            style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" @click="handleGenSubmit" :loading="genSubmitting">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 二维码预览弹窗 -->
    <el-dialog v-model="previewVisible" title="二维码预览" width="420px" center>
      <div class="qr-preview-wrap">
        <div class="qr-preview-img">
          <canvas ref="qrCanvasRef" width="220" height="220"></canvas>
        </div>
        <p class="qr-preview-code">{{ previewQrCode?.qrCode }}</p>
        <div class="qr-preview-actions">
          <el-button type="primary" size="small" @click="handleDownload(previewQrCode)">下载</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 批量下载预览 -->
    <el-dialog v-model="batchPreviewVisible" title="批量下载预览" width="800px">
      <div class="batch-grid">
        <div v-for="code in selectedCodes.slice(0, 20)" :key="code" class="batch-qr-item">
          <canvas :ref="el => drawQrOnCanvas(el as HTMLCanvasElement, code)" width="120" height="120"></canvas>
          <span class="batch-qr-label">{{ code.substring(code.length - 8) }}</span>
        </div>
      </div>
      <div v-if="selectedCodes.length > 20" class="batch-more-hint">
        共 {{ selectedCodes.length }} 个二维码，仅展示前20个
      </div>
      <template #footer>
        <el-button @click="batchPreviewVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchDownload">确认下载全部</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { Search, Refresh, PictureFilled, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import type { EpTagType } from '@/types/common'
import { salesApi } from '@/api/modules/sales'
import { distributionApi } from '@/api/modules/distribution'

const searchForm = reactive({ batchNo: '', qrCode: '', status: null as number | null })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const selectedIds = ref<string[]>([])
const selectedCodes = ref<string[]>([])

const qrStatusType = (s: number): EpTagType =>
  (({ 0: 'info', 1: 'success', 2: 'info', 3: 'danger', 4: 'warning' } as Record<number, EpTagType>)[s] || 'info')
const qrStatusLabel = (s: number) =>
  ({ 0: '未激活', 1: '在售', 2: '已售', 3: '已过期', 4: '已召回' } as Record<number, string>)[s] || ''

// 生成表单
const genVisible = ref(false)
const genSubmitting = ref(false)
const genFormRef = ref()
const genForm = reactive({ receiptId: null as number | null, count: 10, expireDate: '' })
const genRules = {
  receiptId: [{ required: true, message: '请选择签收单', trigger: 'change' }],
  count: [{ required: true, message: '请输入数量', trigger: 'blur' }],
}
const receiptOptions = ref<any[]>([])
const receiptLoading = ref(false)

const selectedReceipt = computed(() =>
  receiptOptions.value.find((r) => r.id === genForm.receiptId) || null
)

function receiptOptionLabel(r: any) {
  return `${r.storeName || '未知门店'} - ${r.transportNo || ('签收单#' + r.id)}`
}

// 预览
const previewVisible = ref(false)
const previewQrCode = ref<any>(null)
const qrCanvasRef = ref<HTMLCanvasElement>()

// 批量下载
const batchPreviewVisible = ref(false)

function handleSelectionChange(items: any[]) {
  selectedIds.value = items.map((i) => i.id)
  selectedCodes.value = items.map((i) => i.qrCode)
}

function handleGenerate() {
  genVisible.value = true
  loadReceiptOptions()
}

async function loadReceiptOptions() {
  if (receiptOptions.value.length > 0) return
  receiptLoading.value = true
  try {
    const res: any = await distributionApi.getReceipts({ current: 1, size: 100 })
    const list = res?.records || res?.list || []
    receiptOptions.value = list
  } catch {
    receiptOptions.value = []
  } finally {
    receiptLoading.value = false
  }
}

function handleReceiptDropdownVisible(visible: boolean) {
  if (visible && receiptOptions.value.length === 0) {
    loadReceiptOptions()
  }
}

async function handleGenSubmit() {
  await genFormRef.value?.validate()
  genSubmitting.value = true
  try {
    await salesApi.generateQrcodes({
      receiptId: genForm.receiptId!,
      count: genForm.count,
      expireDate: genForm.expireDate || undefined,
    })
    ElMessage.success(`已成功生成 ${genForm.count} 个二维码`)
    genVisible.value = false
    fetchList()
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    genSubmitting.value = false
  }
}

async function drawQrOnCanvas(canvas: HTMLCanvasElement | null, code: string) {
  if (!canvas) return
  try {
    await QRCode.toCanvas(canvas, code, { width: 120, margin: 1, color: { dark: '#000', light: '#fff' } })
  } catch {
    /* ignore */
  }
}

async function handlePreview(row: any) {
  previewQrCode.value = row
  previewVisible.value = true
  await nextTick()
  if (qrCanvasRef.value) {
    try {
      await QRCode.toCanvas(qrCanvasRef.value, row.qrCode, { width: 220, margin: 2, color: { dark: '#000', light: '#fff' } })
    } catch {
      /* ignore */
    }
  }
}

function handleDownload(row: any) {
  downloadQrCode(row.qrCode)
}

function downloadQrCode(code: string) {
  const canvas = document.createElement('canvas')
  QRCode.toCanvas(canvas, code, { width: 220, margin: 2, color: { dark: '#000', light: '#fff' } })
    .then(() => {
      const link = document.createElement('a')
      link.download = `${code}.png`
      link.href = canvas.toDataURL('image/png')
      link.click()
    })
    .catch(() => {
      ElMessage.error('二维码生成失败')
    })
}

function handleBatchDownload() {
  if (selectedCodes.value.length === 0) return
  batchPreviewVisible.value = true
}

function confirmBatchDownload() {
  if (selectedCodes.value.length === 0) return
  ElMessage.success(`正在下载 ${selectedCodes.value.length} 个二维码...`)
  selectedCodes.value.forEach((code, index) => {
    setTimeout(() => downloadQrCode(code), index * 200)
  })
  batchPreviewVisible.value = false
}

async function handleActivate(row: any) {
  try {
    await ElMessageBox.confirm(`确定激活二维码 ${row.qrCode}？激活后将进入在售状态。`, '确认激活', {
      type: 'warning',
    })
    await salesApi.activateQrcode(row.id)
    ElMessage.success('激活成功')
    fetchList()
  } catch {
    /* 取消或错误 */
  }
}

function handleDelete(row: any) {
  ElMessageBox.confirm(`确定删除二维码 ${row.qrCode}？`, '确认', { type: 'warning' })
    .then(() => {
      ElMessage.info('后端暂未提供二维码删除接口')
    })
    .catch(() => {})
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}
function handleReset() {
  Object.assign(searchForm, { batchNo: '', qrCode: '', status: null })
  handleSearch()
}
function handleSizeChange() {
  pagination.pageNum = 1
  fetchList()
}
function handlePageChange() {
  fetchList()
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await salesApi.getQrcodes({
      current: pagination.pageNum,
      size: pagination.pageSize,
      batchNo: searchForm.batchNo || undefined,
      qrCode: searchForm.qrCode || undefined,
      status: searchForm.status ?? undefined,
    })
    let list = res?.records || res?.list || []
    list = list.map((r: any) => ({
      ...r,
      batchNo: r.batchNo || (r.splitBatchId ? `SP-${r.splitBatchId}` : '-'),
      transportNo: r.transportNo || (r.transportId ? `T-${r.transportId}` : '-'),
      storeName: r.storeName || (r.storeId ? `门店#${r.storeId}` : '-'),
    }))
    tableData.value = list
    pagination.total = res?.total || list.length || 0
  } catch {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
  background: #fff;
  border-radius: 4px;
}
.search-panel {
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 16px;
  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}
.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid #ebeef5;
}
.qr-preview-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.qr-preview-img {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}
.qr-preview-code {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
}
.qr-preview-actions {
  display: flex;
  gap: 8px;
}
.batch-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  justify-items: center;
}
.batch-qr-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.batch-qr-label {
  font-size: 11px;
  color: #909399;
  font-family: 'Courier New', monospace;
}
.batch-more-hint {
  text-align: center;
  color: #909399;
  font-size: 12px;
  margin-top: 12px;
}
.receipt-detail-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>
