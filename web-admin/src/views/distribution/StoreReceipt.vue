<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">门店签收</h2>
      <el-button type="primary" @click="showAddDialog = true">新增签收</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="运单号">
        <el-input v-model="searchForm.transportNo" placeholder="请输入运单号" clearable />
      </el-form-item>
      <el-form-item label="门店">
        <el-select v-model="searchForm.storeId" placeholder="请选择门店" clearable>
          <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="receiptList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="transportId" label="运单ID" width="80" />
      <el-table-column prop="transportNo" label="运单号" width="150" />
      <el-table-column prop="storeName" label="门店名称" width="150" />
      <el-table-column prop="receiptTime" label="签收时间" width="160" />
      <el-table-column prop="receiver" label="签收人" width="100" />
      <el-table-column label="数量核对" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.qtyCheck" :config="checkStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column label="温度核对" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.tempCheck" :config="checkStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button size="small" @click="viewDetail(scope.row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showAddDialog" title="新增门店签收" width="700px">
      <el-form ref="receiptFormRef" :model="receiptForm" :rules="receiptRules" label-width="100px">
        <el-form-item label="运单号" prop="transportId">
          <el-select v-model="receiptForm.transportId" placeholder="请选择运单">
            <el-option v-for="transport in transportOptions" :key="transport.id" :label="transport.transportNo" :value="transport.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="门店" prop="storeId">
          <el-select v-model="receiptForm.storeId" placeholder="请选择门店">
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="签收时间" prop="receiptTime">
          <el-date-picker v-model="receiptForm.receiptTime" type="datetime" placeholder="请选择签收时间" />
        </el-form-item>
        <el-form-item label="签收人" prop="receiver">
          <el-input v-model="receiptForm.receiver" placeholder="请输入签收人" />
        </el-form-item>
        <el-form-item label="数量核对" prop="qtyCheck">
          <el-radio-group v-model="receiptForm.qtyCheck">
            <el-radio :value="1">一致</el-radio>
            <el-radio :value="0">不一致</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="温度核对" prop="tempCheck">
          <el-radio-group v-model="receiptForm.tempCheck">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">异常</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="签收照片">
          <FileUpload @success="handleFileUpload" />
        </el-form-item>
        <el-form-item label="电子签名">
          <SignaturePad @save="handleSignatureSave" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveReceipt">保存</el-button>
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
import FileUpload from '@/components/common/FileUpload.vue'
import SignaturePad from '@/components/common/SignaturePad.vue'
import type { StoreReceipt } from '@/types/distribution'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const receiptList = ref<StoreReceipt[]>([])

const showAddDialog = ref(false)
const receiptFormRef = ref()

const searchForm = reactive({
  transportNo: '',
  storeId: '',
})

const receiptForm = reactive({
  transportId: '',
  storeId: '',
  receiptTime: '',
  receiver: '',
  qtyCheck: 1,
  tempCheck: 1,
  eSignature: '',
})

const receiptRules = {
  transportId: [{ required: true, message: '请选择运单', trigger: 'change' }],
  storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
  receiptTime: [{ required: true, message: '请选择签收时间', trigger: 'change' }],
  receiver: [{ required: true, message: '请输入签收人', trigger: 'blur' }],
}

const checkStatusConfig = {
  1: { label: '通过', type: 'success' as const },
  0: { label: '异常', type: 'danger' as const },
}

const storeOptions = ref([
  { id: 1, name: '家乐福超市' },
  { id: 2, name: '沃尔玛超市' },
  { id: 3, name: '永辉超市' },
])

const transportOptions = ref([
  { id: 1, transportNo: 'TRANS-001' },
  { id: 2, transportNo: 'TRANS-002' },
])

async function fetchReceiptList() {
  loading.value = true
  try {
    receiptList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchReceiptList()
}

function handleReset() {
  pageNum.value = 1
  fetchReceiptList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchReceiptList()
}

function viewDetail(row: StoreReceipt) {
  ElMessage.info(`查看详情: ${row.id}`)
}

function handleFileUpload(files: any[]) {
  ElMessage.info(`已上传 ${files.length} 个文件`)
}

function handleSignatureSave(dataUrl: string) {
  receiptForm.eSignature = dataUrl
}

async function saveReceipt() {
  if (!receiptFormRef.value) return

  await receiptFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('保存成功')
      showAddDialog.value = false
      fetchReceiptList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchReceiptList()
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
</style>