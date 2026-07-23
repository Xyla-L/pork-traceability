<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">举报管理</h2>
    </div>

    <div class="complaint-stats">
      <div class="stat-card">
        <span class="stat-value">{{ stats.total }}</span>
        <span class="stat-label">总举报数</span>
      </div>
      <div class="stat-card status-pending">
        <span class="stat-value">{{ stats.pending }}</span>
        <span class="stat-label">待处理</span>
      </div>
      <div class="stat-card status-processing">
        <span class="stat-value">{{ stats.processing }}</span>
        <span class="stat-label">处理中</span>
      </div>
      <div class="stat-card status-completed">
        <span class="stat-value">{{ stats.completed }}</span>
        <span class="stat-label">已完成</span>
      </div>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="举报类型">
        <el-select v-model="searchForm.type" placeholder="请选择类型" clearable>
          <el-option label="质量问题" :value="1" />
          <el-option label="价格问题" :value="2" />
          <el-option label="服务问题" :value="3" />
          <el-option label="其他" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
          <el-option label="待处理" :value="1" />
          <el-option label="处理中" :value="2" />
          <el-option label="已完成" :value="3" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="complaintList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="complaintNo" label="举报编号" width="150" />
      <el-table-column label="举报类型" width="100">
        <template #default="scope">
          <el-tag size="small">{{ getTypeLabel(scope.row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="举报标题" width="200" show-overflow-tooltip />
      <el-table-column prop="productQrCode" label="产品二维码" width="200" show-overflow-tooltip />
      <el-table-column prop="reporterName" label="举报人" width="100" />
      <el-table-column prop="reporterPhone" label="联系电话" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.status" :config="complaintStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="举报时间" width="160" />
      <el-table-column label="操作" width="150">
        <template #default="scope">
          <el-button size="small" @click="viewDetail(scope.row)">查看</el-button>
          <el-button
            size="small"
            type="primary"
            v-if="scope.row.status === 1"
            @click="handleComplaint(scope.row)"
          >
            处理
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

    <el-dialog v-model="showDetailDialog" title="举报详情" width="600px">
      <div v-if="currentComplaint" class="detail-content">
        <div class="detail-row">
          <span class="detail-label">举报编号：</span>
          <span class="detail-value">{{ currentComplaint.complaintNo }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">举报类型：</span>
          <span class="detail-value">{{ getTypeLabel(currentComplaint.type) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">举报标题：</span>
          <span class="detail-value">{{ currentComplaint.title }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">产品二维码：</span>
          <span class="detail-value">{{ currentComplaint.productQrCode }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">举报人：</span>
          <span class="detail-value">{{ currentComplaint.reporterName }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">联系电话：</span>
          <span class="detail-value">{{ currentComplaint.reporterPhone }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">举报内容：</span>
          <div class="detail-content-text">{{ currentComplaint.content }}</div>
        </div>
        <div v-if="currentComplaint.images?.length" class="detail-row">
          <span class="detail-label">上传图片：</span>
          <div class="image-list">
            <img v-for="(img, idx) in currentComplaint.images" :key="idx" :src="img" class="detail-image" />
          </div>
        </div>
        <div class="detail-row">
          <span class="detail-label">举报时间：</span>
          <span class="detail-value">{{ currentComplaint.createTime }}</span>
        </div>
        <div v-if="currentComplaint.status === 3 && currentComplaint.handleResult" class="detail-row">
          <span class="detail-label">处理结果：</span>
          <div class="detail-content-text">{{ currentComplaint.handleResult }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
        <el-button
          type="primary"
          v-if="currentComplaint?.status === 1"
          @click="handleComplaint(currentComplaint)"
        >
          处理举报
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showHandleDialog" title="处理举报" width="500px">
      <el-form ref="handleFormRef" :model="handleForm" :rules="handleRules" label-width="80px">
        <el-form-item label="处理状态" prop="status">
          <el-radio-group v-model="handleForm.status">
            <el-radio :value="2">处理中</el-radio>
            <el-radio :value="3">已完成</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理结果" prop="handleResult">
          <textarea v-model="handleForm.handleResult" rows="4" placeholder="请输入处理结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showHandleDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmHandle">确认处理</el-button>
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
import type { Complaint } from '@/types/trace'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const complaintList = ref<Complaint[]>([])

const showDetailDialog = ref(false)
const showHandleDialog = ref(false)
const currentComplaint = ref<Complaint | null>(null)
const handleFormRef = ref()

const searchForm = reactive({
  type: '',
  status: '',
})

const handleForm = reactive({
  status: 2,
  handleResult: '',
})

const handleRules = {
  status: [{ required: true, message: '请选择处理状态', trigger: 'change' }],
  handleResult: [{ required: true, message: '请输入处理结果', trigger: 'blur' }],
}

const stats = ref({
  total: 25,
  pending: 8,
  processing: 5,
  completed: 12,
})

const complaintStatusConfig = {
  1: { label: '待处理', type: 'warning' as const },
  2: { label: '处理中', type: 'danger' as const },
  3: { label: '已完成', type: 'success' as const },
}

function getTypeLabel(type: number) {
  const types: Record<number, string> = {
    1: '质量问题',
    2: '价格问题',
    3: '服务问题',
    4: '其他',
  }
  return types[type] || '未知'
}

async function fetchComplaintList() {
  loading.value = true
  try {
    complaintList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchComplaintList()
}

function handleReset() {
  pageNum.value = 1
  fetchComplaintList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchComplaintList()
}

function viewDetail(complaint: Complaint) {
  currentComplaint.value = complaint
  showDetailDialog.value = true
}

function handleComplaint(complaint: Complaint) {
  currentComplaint.value = complaint
  showDetailDialog.value = false
  handleForm.status = complaint.status === 1 ? 2 : complaint.status
  handleForm.handleResult = complaint.handleResult || ''
  showHandleDialog.value = true
}

async function confirmHandle() {
  if (!handleFormRef.value || !currentComplaint.value) return

  await handleFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('处理成功')
      showHandleDialog.value = false
      fetchComplaintList()
    } catch (error: any) {
      ElMessage.error(error.message || '处理失败')
    }
  })
}

onMounted(() => {
  fetchComplaintList()
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

.complaint-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;

  .stat-card {
    flex: 1;
    text-align: center;
    padding: 20px;
    background: #f5f7fa;
    border-radius: 8px;

    &.status-pending {
      background: #fdf6ec;
    }

    &.status-processing {
      background: #e8f4fd;
    }

    &.status-completed {
      background: #f0f9eb;
    }

    .stat-value {
      display: block;
      font-size: 24px;
      font-weight: bold;
      color: #303133;
    }

    .stat-label {
      font-size: 13px;
      color: #606266;
    }
  }
}

.detail-content {
  .detail-row {
    margin-bottom: 16px;
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;

    .detail-label {
      font-weight: 500;
      color: #606266;
      min-width: 100px;
      flex-shrink: 0;
    }

    .detail-value {
      flex: 1;
      color: #303133;
    }

    .detail-content-text {
      flex: 1;
      color: #303133;
      background: #f5f7fa;
      padding: 10px;
      border-radius: 4px;
      white-space: pre-wrap;
    }

    .image-list {
      flex: 1;
      display: flex;
      gap: 8px;
      flex-wrap: wrap;

      .detail-image {
        width: 100px;
        height: 100px;
        object-fit: cover;
        border-radius: 4px;
      }
    }
  }
}
</style>