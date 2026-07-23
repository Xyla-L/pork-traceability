<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">销售记录</h2>
      <el-button type="primary" @click="showAddDialog = true">新增销售</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="二维码">
        <el-input v-model="searchForm.productQrCode" placeholder="请输入二维码" clearable />
      </el-form-item>
      <el-form-item label="门店">
        <el-select v-model="searchForm.storeId" placeholder="请选择门店" clearable>
          <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
          <el-option :label="statusOptions[1]" :value="1" />
          <el-option :label="statusOptions[2]" :value="2" />
          <el-option :label="statusOptions[3]" :value="3" />
          <el-option :label="statusOptions[4]" :value="4" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="saleList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="productQrCode" label="二维码" width="200" show-overflow-tooltip />
      <el-table-column prop="storeName" label="门店名称" width="150" />
      <el-table-column prop="sellTime" label="销售时间" width="160" />
      <el-table-column prop="sellPrice" label="销售价格(元)" width="120" />
      <el-table-column prop="sellWeightKg" label="销售重量(kg)" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.status" :config="saleStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="expireDate" label="过期日期" width="120" />
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

    <el-dialog v-model="showAddDialog" title="新增销售记录" width="600px">
      <el-form ref="saleFormRef" :model="saleForm" :rules="saleRules" label-width="100px">
        <el-form-item label="二维码" prop="productQrCode">
          <el-input v-model="saleForm.productQrCode" placeholder="请输入二维码" />
        </el-form-item>
        <el-form-item label="门店" prop="storeId">
          <el-select v-model="saleForm.storeId" placeholder="请选择门店">
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售时间" prop="sellTime">
          <el-date-picker v-model="saleForm.sellTime" type="datetime" placeholder="请选择销售时间" />
        </el-form-item>
        <el-form-item label="销售价格(元)" prop="sellPrice">
          <el-input-number v-model="saleForm.sellPrice" :min="0" :step="0.01" />
        </el-form-item>
        <el-form-item label="销售重量(kg)" prop="sellWeightKg">
          <el-input-number v-model="saleForm.sellWeightKg" :min="0" :step="0.01" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSale">保存</el-button>
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
import type { RetailSale } from '@/types/sales'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const saleList = ref<RetailSale[]>([])

const showAddDialog = ref(false)
const saleFormRef = ref()

const searchForm = reactive({
  productQrCode: '',
  storeId: '',
  status: '',
})

const saleForm = reactive({
  productQrCode: '',
  storeId: '',
  sellTime: '',
  sellPrice: 0,
  sellWeightKg: 0,
})

const saleRules = {
  productQrCode: [{ required: true, message: '请输入二维码', trigger: 'blur' }],
  storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
  sellTime: [{ required: true, message: '请选择销售时间', trigger: 'change' }],
  sellPrice: [{ required: true, message: '请输入销售价格', trigger: 'blur' }],
  sellWeightKg: [{ required: true, message: '请输入销售重量', trigger: 'blur' }],
}

const statusOptions: Record<number, string> = {
  1: '在售',
  2: '已售',
  3: '已过期',
  4: '已召回',
}

const saleStatusConfig = {
  1: { label: '在售', type: 'success' as const },
  2: { label: '已售', type: 'info' as const },
  3: { label: '已过期', type: 'danger' as const },
  4: { label: '已召回', type: 'warning' as const },
}

const storeOptions = ref([
  { id: 1, name: '家乐福超市' },
  { id: 2, name: '沃尔玛超市' },
  { id: 3, name: '永辉超市' },
])

async function fetchSaleList() {
  loading.value = true
  try {
    saleList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchSaleList()
}

function handleReset() {
  pageNum.value = 1
  fetchSaleList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchSaleList()
}

function viewDetail(row: RetailSale) {
  ElMessage.info(`查看详情: ${row.id}`)
}

async function saveSale() {
  if (!saleFormRef.value) return

  await saleFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('保存成功')
      showAddDialog.value = false
      fetchSaleList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchSaleList()
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