<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">出栏申报审批</h2>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="申报单号">
        <el-input v-model="searchForm.applyNo" placeholder="请输入申报单号" clearable />
      </el-form-item>
      <el-form-item label="审批状态">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
          <el-option :label="statusOptions[0]" :value="0" />
          <el-option :label="statusOptions[1]" :value="1" />
          <el-option :label="statusOptions[2]" :value="2" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="applyList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="applyNo" label="申报单号" width="150" />
      <el-table-column prop="pigId" label="生猪ID" width="80" />
      <el-table-column prop="earTagNo" label="耳标号" width="120" />
      <el-table-column prop="weightKg" label="重量(kg)" width="100" />
      <el-table-column prop="targetSlaughterhouse" label="目标屠宰场" width="150" />
      <el-table-column prop="applyTime" label="申报时间" width="160" />
      <el-table-column label="审批状态" width="100">
        <template #default="scope">
          <StatusTag
            :status="scope.row.approvalStatus"
            :config="approvalStatusConfig"
          />
        </template>
      </el-table-column>
      <el-table-column prop="approver" label="审批人" width="100" />
      <el-table-column prop="approvalTime" label="审批时间" width="160" />
      <el-table-column label="操作" width="200">
        <template #default="scope">
          <template v-if="scope.row.approvalStatus === 0">
            <el-button size="small" type="primary" @click="approveApply(scope.row, true)">通过</el-button>
            <el-button size="small" type="danger" @click="openRejectDialog(scope.row)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showRejectDialog" title="驳回申请" width="450px">
      <div class="reject-content">
        <p>申报单号：{{ currentApply?.applyNo }}</p>
        <p>耳标号：{{ currentApply?.earTagNo }}</p>
        <el-form-item label="驳回原因" prop="comment">
          <textarea v-model="rejectForm.comment" rows="4" placeholder="请输入驳回原因" />
        </el-form-item>
      </div>
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" @click="rejectApply">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { usePigStore } from '@/stores/pig'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import type { SlaughterApply } from '@/types/pig'

const pigStore = usePigStore()

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const applyList = ref<SlaughterApply[]>([])

const showRejectDialog = ref(false)
const currentApply = ref<SlaughterApply | null>(null)

const searchForm = reactive({
  applyNo: '',
  status: '',
})

const rejectForm = reactive({
  comment: '',
})

const statusOptions: Record<number, string> = {
  0: '待审',
  1: '通过',
  2: '驳回',
}

const approvalStatusConfig = {
  0: { label: '待审', type: 'warning' as const },
  1: { label: '通过', type: 'success' as const },
  2: { label: '驳回', type: 'danger' as const },
}

async function fetchApplyList() {
  loading.value = true
  try {
    await pigStore.fetchApplies({
      ...searchForm,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    applyList.value = pigStore.applyList
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchApplyList()
}

function handleReset() {
  pageNum.value = 1
  fetchApplyList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchApplyList()
}

async function approveApply(apply: SlaughterApply, approved: boolean) {
  try {
    await pigStore.approveApply(apply.id, approved)
    ElMessage.success(approved ? '审批通过' : '已驳回')
    fetchApplyList()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

function openRejectDialog(apply: SlaughterApply) {
  currentApply.value = apply
  rejectForm.comment = ''
  showRejectDialog.value = true
}

async function rejectApply() {
  if (!currentApply.value) return

  try {
    await pigStore.approveApply(currentApply.value.id, false, rejectForm.comment)
    ElMessage.success('已驳回')
    showRejectDialog.value = false
    fetchApplyList()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

onMounted(() => {
  fetchApplyList()
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

.reject-content {
  p {
    margin-bottom: 12px;
    font-size: 14px;
    color: #606266;
  }
}
</style>