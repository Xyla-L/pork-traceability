<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">审计日志</h2>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="操作类型">
        <el-select v-model="searchForm.actionType" placeholder="请选择类型" clearable>
          <el-option label="登录" :value="1" />
          <el-option label="退出" :value="2" />
          <el-option label="新增" :value="3" />
          <el-option label="编辑" :value="4" />
          <el-option label="删除" :value="5" />
          <el-option label="审批" :value="6" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作人">
        <el-input v-model="searchForm.operator" placeholder="请输入操作人" clearable />
      </el-form-item>
      <el-form-item label="操作时间">
        <el-date-picker
          v-model="searchForm.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </el-form-item>
    </SearchPanel>

    <el-table :data="logList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column label="操作类型" width="100">
        <template #default="scope">
          <el-tag size="small">{{ getActionTypeLabel(scope.row.actionType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="module" label="操作模块" width="120" />
      <el-table-column prop="description" label="操作描述" width="250" show-overflow-tooltip />
      <el-table-column prop="targetId" label="目标ID" width="100" />
      <el-table-column prop="ipAddress" label="IP地址" width="150" />
      <el-table-column prop="userAgent" label="浏览器" width="200" show-overflow-tooltip />
      <el-table-column prop="createTime" label="操作时间" width="160" />
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import type { AuditLog } from '@/types/system'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const logList = ref<AuditLog[]>([])

const searchForm = reactive({
  actionType: '',
  operator: '',
  dateRange: [] as Date[],
})

function getActionTypeLabel(type: number) {
  const types: Record<number, string> = {
    1: '登录',
    2: '退出',
    3: '新增',
    4: '编辑',
    5: '删除',
    6: '审批',
  }
  return types[type] || '未知'
}

async function fetchLogList() {
  loading.value = true
  try {
    logList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchLogList()
}

function handleReset() {
  pageNum.value = 1
  fetchLogList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchLogList()
}

onMounted(() => {
  fetchLogList()
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