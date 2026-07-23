<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">产品召回</h2>
      <el-button type="primary" @click="showAddDialog = true">发起召回</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="召回单号">
        <el-input v-model="searchForm.recallNo" placeholder="请输入召回单号" clearable />
      </el-form-item>
      <el-form-item label="风险级别">
        <el-select v-model="searchForm.riskLevel" placeholder="请选择级别" clearable>
          <el-option label="一般" :value="1" />
          <el-option label="严重" :value="2" />
          <el-option label="紧急" :value="3" />
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

    <el-table :data="recallList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="recallNo" label="召回单号" width="150" />
      <el-table-column label="风险级别" width="100">
        <template #default="scope">
          <el-tag :type="getRiskTagType(scope.row.riskLevel)">
            {{ getRiskLabel(scope.row.riskLevel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="召回原因" width="200" show-overflow-tooltip />
      <el-table-column prop="affectedCount" label="涉及数量" width="100" />
      <el-table-column prop="recalledCount" label="已召回" width="100" />
      <el-table-column label="召回进度" width="150">
        <template #default="scope">
          <el-progress
            :percentage="getProgress(scope.row)"
            :color="getProgressColor(scope.row)"
            :stroke-width="12"
          />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.status" :config="recallStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="initiator" label="发起人" width="100" />
      <el-table-column prop="initiateTime" label="发起时间" width="160" />
      <el-table-column label="操作" width="150">
        <template #default="scope">
          <el-button size="small" @click="viewDetail(scope.row)">查看</el-button>
          <el-button
            size="small"
            type="warning"
            v-if="scope.row.status === 2"
            @click="completeRecall(scope.row)"
          >
            完成
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showAddDialog" title="发起召回" width="600px">
      <el-form ref="recallFormRef" :model="recallForm" :rules="recallRules" label-width="100px">
        <el-form-item label="风险级别" prop="riskLevel">
          <el-radio-group v-model="recallForm.riskLevel">
            <el-radio :value="1">一般</el-radio>
            <el-radio :value="2">严重</el-radio>
            <el-radio :value="3">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="召回原因" prop="reason">
          <textarea v-model="recallForm.reason" rows="3" placeholder="请输入召回原因" />
        </el-form-item>
        <el-form-item label="涉及批次">
          <el-select v-model="recallForm.batchIds" multiple placeholder="请选择批次">
            <el-option v-for="batch in batchOptions" :key="batch.id" :label="batch.batchNo" :value="batch.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="涉及门店">
          <el-select v-model="recallForm.storeIds" multiple placeholder="请选择门店">
            <el-option v-for="store in storeOptions" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发起人" prop="initiator">
          <el-input v-model="recallForm.initiator" placeholder="请输入发起人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRecall">发起召回</el-button>
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
import type { RecallOrder } from '@/types/sales'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const recallList = ref<RecallOrder[]>([])

const showAddDialog = ref(false)
const recallFormRef = ref()

const searchForm = reactive({
  recallNo: '',
  riskLevel: '',
  status: '',
})

const recallForm = reactive({
  riskLevel: 1,
  reason: '',
  batchIds: [] as number[],
  storeIds: [] as number[],
  initiator: '',
})

const recallRules = {
  riskLevel: [{ required: true, message: '请选择风险级别', trigger: 'change' }],
  reason: [{ required: true, message: '请输入召回原因', trigger: 'blur' }],
  initiator: [{ required: true, message: '请输入发起人', trigger: 'blur' }],
}

const statusOptions: Record<number, string> = {
  1: '已发布',
  2: '执行中',
  3: '已完成',
  4: '已撤销',
}

const recallStatusConfig = {
  1: { label: '已发布', type: 'warning' as const },
  2: { label: '执行中', type: 'danger' as const },
  3: { label: '已完成', type: 'success' as const },
  4: { label: '已撤销', type: 'info' as const },
}

const batchOptions = ref([
  { id: 1, batchNo: 'SPLIT-001' },
  { id: 2, batchNo: 'SPLIT-002' },
])

const storeOptions = ref([
  { id: 1, name: '家乐福超市' },
  { id: 2, name: '沃尔玛超市' },
  { id: 3, name: '永辉超市' },
])

function getRiskTagType(level: number) {
  if (level === 1) return 'info'
  if (level === 2) return 'warning'
  return 'danger'
}

function getRiskLabel(level: number) {
  if (level === 1) return '一般'
  if (level === 2) return '严重'
  return '紧急'
}

function getProgress(row: RecallOrder) {
  if (row.affectedCount === 0) return 0
  return Math.round((row.recalledCount / row.affectedCount) * 100)
}

function getProgressColor(row: RecallOrder) {
  const progress = getProgress(row)
  if (progress >= 100) return '#67c23a'
  if (progress >= 50) return '#e6a23c'
  return '#f56c6c'
}

async function fetchRecallList() {
  loading.value = true
  try {
    recallList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchRecallList()
}

function handleReset() {
  pageNum.value = 1
  fetchRecallList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchRecallList()
}

function viewDetail(row: RecallOrder) {
  ElMessage.info(`查看详情: ${row.recallNo}`)
}

function completeRecall(row: RecallOrder) {
  ElMessage.success(`召回 ${row.recallNo} 已完成`)
  fetchRecallList()
}

async function saveRecall() {
  if (!recallFormRef.value) return

  await recallFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('召回已发起')
      showAddDialog.value = false
      fetchRecallList()
    } catch (error: any) {
      ElMessage.error(error.message || '发起失败')
    }
  })
}

onMounted(() => {
  fetchRecallList()
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