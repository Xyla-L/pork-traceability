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
      <el-button @click="handleBatchPrint" :disabled="selectedIds.length === 0">
        <el-icon><Printer /></el-icon>批量打印 ({{ selectedIds.length }})
      </el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="qrCode" label="二维码编号" min-width="200" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="关联批次" width="160" />
      <el-table-column prop="expireDate" label="过期日期" width="120" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="qrStatusType(row.status)" size="small">{{ qrStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="生成时间" width="160" align="center" />
      <el-table-column label="二维码预览" width="100" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handlePreview(row)">预览</el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleDownload(row)">下载</el-button>
          <el-button type="success" link size="small" @click="handlePrint(row)">打印</el-button>
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
    <el-dialog v-model="genVisible" title="生成二维码" width="520px" destroy-on-close>
      <el-form ref="genFormRef" :model="genForm" :rules="genRules" label-width="100px">
        <el-form-item label="选择分割批次" prop="splitBatchId">
          <el-select v-model="genForm.splitBatchId" placeholder="请选择批次" style="width: 100%">
            <el-option v-for="b in splitBatchOptions" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生成数量" prop="count">
          <el-input-number v-model="genForm.count" :min="1" :max="1000" :step="1" style="width: 200px" />
        </el-form-item>
        <el-form-item label="打印样式">
          <el-radio-group v-model="genForm.style">
            <el-radio value="standard">标准版</el-radio>
            <el-radio value="mini">迷你版</el-radio>
          </el-radio-group>
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
          <el-button size="small" @click="handlePrint(previewQrCode)">打印</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 批量打印预览 -->
    <el-dialog v-model="batchPreviewVisible" title="批量打印预览" width="800px">
      <div class="batch-grid">
        <div v-for="id in selectedIds.slice(0, 20)" :key="id" class="batch-qr-item">
          <canvas :ref="el => drawQrOnCanvas(el as HTMLCanvasElement, id)" width="120" height="120"></canvas>
          <span class="batch-qr-label">{{ id }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="batchPreviewVisible = false">取消</el-button>
        <el-button type="primary" @click="batchPreviewVisible = false; ElMessage.success('已发送到打印机')">确认打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { Search, Refresh, PictureFilled, Printer } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import type { EpTagType } from '@/types/common'

const searchForm = reactive({ batchNo: '', qrCode: '', status: null as number | null })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const selectedIds = ref<string[]>([])

const qrStatusType = (s: number): EpTagType => (({ 0: 'info', 1: 'success', 2: 'info', 3: 'danger' } as Record<number, EpTagType>)[s] || 'info')
const qrStatusLabel = (s: number) => ({ 0: '未激活', 1: '在售', 2: '已售', 3: '已过期' } as Record<number, string>)[s] || ''

// 生成表单
const genVisible = ref(false)
const genSubmitting = ref(false)
const genFormRef = ref()
const genForm = reactive({ splitBatchId: null as number | null, count: 10, style: 'standard' })
const genRules = {
  splitBatchId: [{ required: true, message: '请选择批次', trigger: 'change' }],
  count: [{ required: true, message: '请输入数量', trigger: 'blur' }],
}
const splitBatchOptions = [
  { label: 'B20240715001 - 猪前腿肉', value: 1 }, { label: 'B20240714002 - 猪五花肉', value: 2 },
  { label: 'B20240713003 - 猪里脊', value: 3 }, { label: 'B20240712004 - 猪排骨', value: 4 },
]

// 预览
const previewVisible = ref(false)
const previewQrCode = ref<any>(null)
const qrCanvasRef = ref<HTMLCanvasElement>()

// 批量打印
const batchPreviewVisible = ref(false)

function handleSelectionChange(items: any[]) { selectedIds.value = items.map(i => i.qrCode) }

function handleGenerate() { genVisible.value = true }

async function handleGenSubmit() {
  await genFormRef.value?.validate()
  genSubmitting.value = true
  try {
    const newItems = Array.from({ length: genForm.count }, (_, i) => ({
      id: Date.now() + i,
      qrCode: `QR-PORK-${Date.now().toString(36).toUpperCase()}-${String(i + 1).padStart(3, '0')}`,
      batchNo: splitBatchOptions.find(b => b.value === genForm.splitBatchId)?.label.split(' - ')[0] || '',
      splitBatchId: genForm.splitBatchId,
      status: 0 as const,
      expireDate: `2024-08-${String(1 + i % 30).padStart(2, '0')}`,
      createTime: new Date().toLocaleString(),
    }))
    tableData.value.unshift(...newItems)
    pagination.total += genForm.count
    ElMessage.success(`已成功生成 ${genForm.count} 个二维码`)
    genVisible.value = false
  } finally { genSubmitting.value = false }
}

async function drawQrOnCanvas(canvas: HTMLCanvasElement | null, code: string) {
  if (!canvas) return
  try {
    await QRCode.toCanvas(canvas, code, { width: 120, margin: 1, color: { dark: '#000', light: '#fff' } })
  } catch { /* ignore */ }
}

async function handlePreview(row: any) {
  previewQrCode.value = row
  previewVisible.value = true
  await nextTick()
  if (qrCanvasRef.value) {
    try {
      await QRCode.toCanvas(qrCanvasRef.value, row.qrCode, { width: 220, margin: 2, color: { dark: '#000', light: '#fff' } })
    } catch { /* ignore */ }
  }
}

function handleDownload(row: any) {
  ElMessage.success(`二维码 ${row.qrCode} 下载中...`)
}

function handlePrint(row: any) {
  ElMessage.success(`二维码 ${row.qrCode} 已发送到打印机`)
}

function handleBatchPrint() {
  if (selectedIds.value.length === 0) return
  batchPreviewVisible.value = true
  nextTick(() => {
    // Canvas refs rendered via function already
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm(`确定删除二维码 ${row.qrCode}？`, '确认', { type: 'warning' }).then(() => {
    const idx = tableData.value.findIndex(i => i.id === row.id)
    if (idx > -1) { tableData.value.splice(idx, 1); pagination.total-- }
    ElMessage.success('已删除')
  }).catch(() => {})
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { Object.assign(searchForm, { batchNo: '', qrCode: '', status: null }); handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function fetchList() {
  loading.value = true
  const batches = ['B20240715001', 'B20240714002', 'B20240713003', 'B20240712004']
  const list = Array.from({ length: 42 }, (_, i) => ({
    id: i + 1,
    qrCode: `QR-PORK-2024071${String(5 + Math.floor(i / 10)).padStart(1, '0')}${String(i + 1).padStart(3, '0')}`,
    batchNo: batches[i % batches.length],
    splitBatchId: (i % 4) + 1,
    status: i < 20 ? 2 : i < 30 ? 1 : i < 38 ? 0 : 3,
    expireDate: `2024-08-${String(1 + i % 30).padStart(2, '0')}`,
    createTime: `2024-07-${String(10 + Math.floor(i / 4)).padStart(2, '0')} 10:00:00`,
  })) as any[]
  const filtered = list.filter(item => {
    if (searchForm.batchNo && !item.batchNo.includes(searchForm.batchNo)) return false
    if (searchForm.qrCode && !item.qrCode.includes(searchForm.qrCode)) return false
    if (searchForm.status !== null && item.status !== searchForm.status) return false
    return true
  })
  tableData.value = filtered.slice((pagination.pageNum - 1) * pagination.pageSize, pagination.pageNum * pagination.pageSize)
  pagination.total = filtered.length
  loading.value = false
}

onMounted(() => fetchList())
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.action-bar { display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.qr-preview-wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.qr-preview-img { padding: 16px; background: #f5f7fa; border-radius: 8px; }
.qr-preview-code { font-family: 'Courier New', monospace; font-size: 12px; color: #606266; }
.qr-preview-actions { display: flex; gap: 8px; }
.batch-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; justify-items: center; }
.batch-qr-item { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.batch-qr-label { font-size: 11px; color: #909399; }
</style>
