<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">入场查验</h2>
      <el-button type="primary" @click="showAddDialog = true">新增查验</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="耳标号">
        <el-input v-model="searchForm.earTagNo" placeholder="请输入耳标号" clearable />
      </el-form-item>
      <el-form-item label="车牌号">
        <el-input v-model="searchForm.vehicleNo" placeholder="请输入车牌号" clearable />
      </el-form-item>
    </SearchPanel>

    <el-table :data="entryList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="pigId" label="生猪ID" width="80" />
      <el-table-column prop="earTagNo" label="耳标号" width="120" />
      <el-table-column prop="vehicleNo" label="车牌号" width="100" />
      <el-table-column prop="arriveTime" label="到达时间" width="160" />
      <el-table-column label="健康检查" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.healthCheck" :config="healthStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column label="检疫证验证" width="120">
        <template #default="scope">
          <StatusTag :status="scope.row.certVerified" :config="certStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="abnormalNote" label="异常备注" width="150" show-overflow-tooltip />
      <el-table-column prop="inspector" label="查验人员" width="120" />
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

    <el-dialog v-model="showAddDialog" title="新增入场查验" width="600px">
      <el-form ref="entryFormRef" :model="entryForm" :rules="entryRules" label-width="100px">
        <el-form-item label="耳标号" prop="pigId">
          <el-select v-model="entryForm.pigId" placeholder="请选择生猪">
            <el-option v-for="pig in pigOptions" :key="pig.id" :label="pig.earTagNo" :value="pig.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="车牌号" prop="vehicleNo">
          <el-input v-model="entryForm.vehicleNo" placeholder="请输入车牌号" />
        </el-form-item>
        <el-form-item label="到达时间" prop="arriveTime">
          <el-date-picker v-model="entryForm.arriveTime" type="datetime" placeholder="请选择到达时间" />
        </el-form-item>
        <el-form-item label="健康检查" prop="healthCheck">
          <el-radio-group v-model="entryForm.healthCheck">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="0">异常</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检疫证验证" prop="certVerified">
          <el-radio-group v-model="entryForm.certVerified">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="0">异常</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="异常备注">
          <textarea v-model="entryForm.abnormalNote" rows="3" placeholder="如有异常请填写备注" />
        </el-form-item>
        <el-form-item label="查验人员" prop="inspector">
          <el-input v-model="entryForm.inspector" placeholder="请输入查验人员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveEntry">保存</el-button>
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
import type { EntryInspection } from '@/types/slaughter'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const entryList = ref<EntryInspection[]>([])

const showAddDialog = ref(false)
const entryFormRef = ref()

const searchForm = reactive({
  earTagNo: '',
  vehicleNo: '',
})

const entryForm = reactive({
  pigId: '',
  vehicleNo: '',
  arriveTime: '',
  healthCheck: 1,
  certVerified: 1,
  abnormalNote: '',
  inspector: '',
})

const entryRules = {
  pigId: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  vehicleNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  arriveTime: [{ required: true, message: '请选择到达时间', trigger: 'change' }],
  inspector: [{ required: true, message: '请输入查验人员', trigger: 'blur' }],
}

const healthStatusConfig = {
  1: { label: '通过', type: 'success' as const },
  0: { label: '异常', type: 'danger' as const },
}

const certStatusConfig = {
  1: { label: '通过', type: 'success' as const },
  0: { label: '异常', type: 'danger' as const },
}

const pigOptions = ref([
  { id: 1, earTagNo: 'PIG-001' },
  { id: 2, earTagNo: 'PIG-002' },
  { id: 3, earTagNo: 'PIG-003' },
])

async function fetchEntryList() {
  loading.value = true
  try {
    entryList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchEntryList()
}

function handleReset() {
  pageNum.value = 1
  fetchEntryList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchEntryList()
}

function viewDetail(row: EntryInspection) {
  ElMessage.info(`查看详情: ${row.id}`)
}

async function saveEntry() {
  if (!entryFormRef.value) return

  await entryFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('保存成功')
      showAddDialog.value = false
      fetchEntryList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchEntryList()
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