<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">过期预警</h2>
    </div>

    <div class="warning-stats">
      <div class="stat-card level-safe">
        <div class="stat-icon"><Clock /></div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.safe }}</span>
          <span class="stat-label">临期3天</span>
        </div>
      </div>
      <div class="stat-card level-warning">
        <div class="stat-icon"><Timer /></div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.warning }}</span>
          <span class="stat-label">临期1天</span>
        </div>
      </div>
      <div class="stat-card level-danger">
        <div class="stat-icon"><WarningFilled /></div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.danger }}</span>
          <span class="stat-label">已过期</span>
        </div>
      </div>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="预警级别">
        <el-select v-model="searchForm.warningLevel" placeholder="请选择级别" clearable>
          <el-option label="临期3天" :value="1" />
          <el-option label="临期1天" :value="2" />
          <el-option label="已过期" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="处理状态">
        <el-select v-model="searchForm.handled" placeholder="请选择状态" clearable>
          <el-option label="未处理" :value="0" />
          <el-option label="已处理" :value="1" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="warningList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="productQrCode" label="二维码" width="200" show-overflow-tooltip />
      <el-table-column prop="productName" label="产品名称" width="150" />
      <el-table-column prop="storeName" label="门店" width="120" />
      <el-table-column label="预警级别" width="100">
        <template #default="scope">
          <div class="warning-badge" :class="getLevelClass(scope.row.warningLevel)">
            <span class="warning-icon">{{ getLevelIcon(scope.row.warningLevel) }}</span>
            {{ getLevelLabel(scope.row.warningLevel) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="warningTime" label="预警时间" width="160" />
      <el-table-column label="处理状态" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.handled" :config="handleStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="handler" label="处理人" width="100" />
      <el-table-column prop="handleTime" label="处理时间" width="160" />
      <el-table-column label="操作" width="150">
        <template #default="scope">
          <el-button
            size="small"
            type="primary"
            v-if="scope.row.handled === 0"
            @click="handleWarning(scope.row)"
          >
            处理
          </el-button>
          <span v-else class="handled-text">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showHandleDialog" title="处理预警" width="450px">
      <div class="handle-content">
        <p>预警编号：{{ currentWarning?.id }}</p>
        <p>产品名称：{{ currentWarning?.productName }}</p>
        <p>预警级别：{{ getLevelLabel(currentWarning?.warningLevel) }}</p>
        <el-form-item label="处理备注">
          <textarea v-model="handleForm.comment" rows="3" placeholder="请输入处理备注" />
        </el-form-item>
      </div>
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
import { Clock, Timer, WarningFilled } from '@element-plus/icons-vue'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import type { ExpireWarning } from '@/types/sales'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const warningList = ref<ExpireWarning[]>([])

const showHandleDialog = ref(false)
const currentWarning = ref<ExpireWarning | null>(null)

const searchForm = reactive({
  warningLevel: '',
  handled: '',
})

const handleForm = reactive({
  comment: '',
})

const stats = ref({
  safe: 15,
  warning: 8,
  danger: 3,
})

const handleStatusConfig = {
  0: { label: '未处理', type: 'warning' as const },
  1: { label: '已处理', type: 'success' as const },
}

function getLevelClass(level: number) {
  if (level === 1) return 'level-safe'
  if (level === 2) return 'level-warning'
  return 'level-danger'
}

function getLevelIcon(level: number) {
  if (level === 1) return '🟢'
  if (level === 2) return '🟡'
  return '🔴'
}

function getLevelLabel(level: number) {
  if (level === 1) return '临期3天'
  if (level === 2) return '临期1天'
  return '已过期'
}

async function fetchWarningList() {
  loading.value = true
  try {
    warningList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchWarningList()
}

function handleReset() {
  pageNum.value = 1
  fetchWarningList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchWarningList()
}

function handleWarning(warning: ExpireWarning) {
  currentWarning.value = warning
  handleForm.comment = ''
  showHandleDialog.value = true
}

async function confirmHandle() {
  if (!currentWarning.value) return

  try {
    ElMessage.success('处理成功')
    showHandleDialog.value = false
    fetchWarningList()
  } catch (error: any) {
    ElMessage.error(error.message || '处理失败')
  }
}

onMounted(() => {
  fetchWarningList()
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

.warning-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;

  .stat-card {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 20px;
    border-radius: 8px;

    &.level-safe {
      background: #f0f9eb;
      border: 1px solid #e1f3d8;
    }

    &.level-warning {
      background: #fdf6ec;
      border: 1px solid #faecd8;
    }

    &.level-danger {
      background: #fef0f0;
      border: 1px solid #fbc4c4;
    }

    .stat-icon {
      font-size: 28px;
    }

    .stat-info {
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
}

.warning-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;

  &.level-safe {
    background: #f0f9eb;
    color: #67c23a;
  }

  &.level-warning {
    background: #fdf6ec;
    color: #e6a23c;
  }

  &.level-danger {
    background: #fef0f0;
    color: #f56c6c;
  }

  .warning-icon {
    font-size: 14px;
  }
}

.handled-text {
  color: #67c23a;
  font-size: 13px;
}

.handle-content {
  p {
    margin-bottom: 12px;
    font-size: 14px;
    color: #606266;
  }
}
</style>