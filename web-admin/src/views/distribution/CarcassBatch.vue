<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">胴体批次</h2>
      <el-button type="primary" @click="showAddDialog = true">新增批次</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="批次号">
        <el-input v-model="searchForm.batchNo" placeholder="请输入批次号" clearable />
      </el-form-item>
    </SearchPanel>

    <el-table :data="batchList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="batchNo" label="批次号" width="150" />
      <el-table-column label="生猪数量" width="100">
        <template #default="scope">{{ (scope.row.pigIds as number[])?.length || 0 }}</template>
      </el-table-column>
      <el-table-column prop="totalWeightKg" label="总重量(kg)" width="120" />
      <el-table-column prop="operator" label="操作人员" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button size="small" @click="viewDetail(scope.row)">查看</el-button>
          <el-button size="small" @click="splitBatch(scope.row)">分割</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showAddDialog" title="新增胴体批次" width="600px">
      <el-form ref="batchFormRef" :model="batchForm" :rules="batchRules" label-width="100px">
        <el-form-item label="批次号" prop="batchNo">
          <el-input v-model="batchForm.batchNo" placeholder="请输入批次号" />
        </el-form-item>
        <el-form-item label="选择生猪" prop="pigIds">
          <el-select v-model="batchForm.pigIds" multiple placeholder="请选择生猪">
            <el-option v-for="pig in pigOptions" :key="pig.id" :label="pig.earTagNo" :value="pig.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="总重量(kg)" prop="totalWeightKg">
          <el-input-number v-model="batchForm.totalWeightKg" :min="0" :step="0.1" />
        </el-form-item>
        <el-form-item label="操作人员" prop="operator">
          <el-input v-model="batchForm.operator" placeholder="请输入操作人员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBatch">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import type { CarcassBatch } from '@/types/distribution'

const router = useRouter()

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const batchList = ref<CarcassBatch[]>([])

const showAddDialog = ref(false)
const batchFormRef = ref()

const searchForm = reactive({
  batchNo: '',
})

const batchForm = reactive({
  batchNo: '',
  pigIds: [] as number[],
  totalWeightKg: 0,
  operator: '',
})

const batchRules = {
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  pigIds: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  totalWeightKg: [{ required: true, message: '请输入总重量', trigger: 'blur' }],
  operator: [{ required: true, message: '请输入操作人员', trigger: 'blur' }],
}

const pigOptions = ref([
  { id: 1, earTagNo: 'PIG-001' },
  { id: 2, earTagNo: 'PIG-002' },
  { id: 3, earTagNo: 'PIG-003' },
])

async function fetchBatchList() {
  loading.value = true
  try {
    batchList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchBatchList()
}

function handleReset() {
  pageNum.value = 1
  fetchBatchList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchBatchList()
}

function viewDetail(row: CarcassBatch) {
  ElMessage.info(`查看详情: ${row.batchNo}`)
}

function splitBatch(row: CarcassBatch) {
  router.push(`/admin/distribution/split?batchId=${row.id}`)
}

async function saveBatch() {
  if (!batchFormRef.value) return

  await batchFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('保存成功')
      showAddDialog.value = false
      fetchBatchList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchBatchList()
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