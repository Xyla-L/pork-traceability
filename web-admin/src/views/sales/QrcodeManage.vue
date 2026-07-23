<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">二维码管理</h2>
      <el-button type="primary" @click="showGenerateDialog = true">批量生成</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="批次号">
        <el-input v-model="searchForm.batchNo" placeholder="请输入批次号" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
          <el-option :label="statusOptions[0]" :value="0" />
          <el-option :label="statusOptions[1]" :value="1" />
          <el-option :label="statusOptions[2]" :value="2" />
          <el-option :label="statusOptions[3]" :value="3" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="qrcodeList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="qrCode" label="二维码" width="200">
        <template #default="scope">
          <div class="qrcode-cell">
            <el-image :src="scope.row.qrCode" class="qrcode-img" />
            <span class="qrcode-text">{{ scope.row.qrCode?.slice(0, 20) }}...</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="batchNo" label="批次号" width="150" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.status" :config="qrcodeStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="expireDate" label="过期日期" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="150">
        <template #default="scope">
          <el-button size="small" v-if="scope.row.status === 0" @click="activateQrcode(scope.row)">激活</el-button>
          <el-button size="small" @click="previewQrcode(scope.row)">预览</el-button>
          <el-button size="small" type="danger" @click="deleteQrcode(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showGenerateDialog" title="批量生成二维码" width="500px">
      <el-form ref="generateFormRef" :model="generateForm" :rules="generateRules" label-width="100px">
        <el-form-item label="分割批次" prop="splitBatchId">
          <el-select v-model="generateForm.splitBatchId" placeholder="请选择分割批次">
            <el-option v-for="batch in batchOptions" :key="batch.id" :label="batch.batchNo" :value="batch.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="生成数量" prop="count">
          <el-input-number v-model="generateForm.count" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="过期日期" prop="expireDate">
          <el-date-picker v-model="generateForm.expireDate" type="date" placeholder="请选择过期日期" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" @click="generateQrcodes">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPreviewDialog" title="二维码预览" width="400px">
      <div class="qrcode-preview">
        <el-image v-if="previewQrcodeUrl" :src="previewQrcodeUrl" fit="contain" />
        <div class="preview-info">
          <p>二维码编号: {{ currentQrcode?.id }}</p>
          <p>状态: {{ statusOptions[currentQrcode?.status] }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="showPreviewDialog = false">关闭</el-button>
        <el-button type="primary">下载</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import type { QrcodeItem } from '@/types/sales'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const qrcodeList = ref<QrcodeItem[]>([])

const showGenerateDialog = ref(false)
const showPreviewDialog = ref(false)
const generateFormRef = ref()

const previewQrcodeUrl = ref('')
const currentQrcode = ref<QrcodeItem | null>(null)

const searchForm = reactive({
  batchNo: '',
  status: '',
})

const generateForm = reactive({
  splitBatchId: '',
  count: 10,
  expireDate: '',
})

const generateRules = {
  splitBatchId: [{ required: true, message: '请选择分割批次', trigger: 'change' }],
  count: [{ required: true, message: '请输入生成数量', trigger: 'blur' }],
  expireDate: [{ required: true, message: '请选择过期日期', trigger: 'change' }],
}

const statusOptions: Record<number, string> = {
  0: '未激活',
  1: '在售',
  2: '已售',
  3: '已过期',
}

const qrcodeStatusConfig = {
  0: { label: '未激活', type: 'info' as const },
  1: { label: '在售', type: 'success' as const },
  2: { label: '已售', type: 'info' as const },
  3: { label: '已过期', type: 'danger' as const },
}

const batchOptions = ref([
  { id: 1, batchNo: 'SPLIT-001' },
  { id: 2, batchNo: 'SPLIT-002' },
])

async function fetchQrcodeList() {
  loading.value = true
  try {
    qrcodeList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchQrcodeList()
}

function handleReset() {
  pageNum.value = 1
  fetchQrcodeList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchQrcodeList()
}

function activateQrcode(row: QrcodeItem) {
  ElMessage.success(`已激活二维码: ${row.id}`)
  fetchQrcodeList()
}

function previewQrcode(row: QrcodeItem) {
  currentQrcode.value = row
  previewQrcodeUrl.value = row.qrCode || ''
  showPreviewDialog.value = true
}

async function deleteQrcode(id: number) {
  ElMessage.warning('删除功能待后端支持')
}

async function generateQrcodes() {
  if (!generateFormRef.value) return

  await generateFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success(`成功生成 ${generateForm.count} 个二维码`)
      showGenerateDialog.value = false
      fetchQrcodeList()
    } catch (error: any) {
      ElMessage.error(error.message || '生成失败')
    }
  })
}

onMounted(() => {
  fetchQrcodeList()
})
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .card-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}

.qrcode-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .qrcode-img {
    width: 40px;
    height: 40px;
  }

  .qrcode-text {
    font-size: 12px;
    color: #606266;
  }
}

.qrcode-preview {
  text-align: center;

  :deep(.el-image) {
    width: 200px;
    height: 200px;
    margin: 0 auto;
    border: 1px solid #ebeef5;
    border-radius: 8px;
  }

  .preview-info {
    margin-top: 16px;
    text-align: left;
    font-size: 14px;
    color: #606266;
  }
}
</style>