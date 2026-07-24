<template>
  <div class="ractopamine-test">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="检测编号">
          <el-input
            v-model="searchForm.testNo"
            placeholder="请输入检测编号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input
            v-model="searchForm.batchNo"
            placeholder="请输入批次号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="样本编号">
          <el-input
            v-model="searchForm.sampleNo"
            placeholder="请输入样本编号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="检测项目">
          <el-select
            v-model="searchForm.testType"
            placeholder="请选择"
            clearable
            style="width: 160px"
          >
            <el-option label="瘦肉精检测" value="瘦肉精检测" />
            <el-option label="克伦特罗检测" value="克伦特罗检测" />
            <el-option label="莱克多巴胺检测" value="莱克多巴胺检测" />
            <el-option label="沙丁胺醇检测" value="沙丁胺醇检测" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select
            v-model="searchForm.result"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="阴性" value="阴性" />
            <el-option label="阳性" value="阳性" />
            <el-option label="待检测" value="待检测" />
          </el-select>
        </el-form-item>
        <el-form-item label="检测日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新增检测
      </el-button>
    </div>

    <!-- 数据表格 -->
    <RactoTable
      :data="tableData"
      :loading="tableLoading"
      @view="handleView"
      @edit="handleEdit"
      @test="handleTestResult"
    />

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 新建/编辑弹窗 -->
    <RactoFormDialog
      v-model:visible="formDialogVisible"
      :edit-data="currentEditData"
      @submit="handleFormSubmit"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RactoTable from './RactoTable.vue'
import RactoFormDialog from './RactoFormDialog.vue'

// ==================== 搜索相关 ====================

const dateRange = ref(null)

const searchForm = reactive({
  testNo: '',
  batchNo: '',
  sampleNo: '',
  testType: '',
  result: '',
  startDate: '',
  endDate: ''
})

// ==================== 表格相关 ====================

const tableData = ref([])
const tableLoading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const fetchList = async () => {
  tableLoading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      testNo: searchForm.testNo || undefined,
      batchNo: searchForm.batchNo || undefined,
      sampleNo: searchForm.sampleNo || undefined,
      testType: searchForm.testType || undefined,
      result: searchForm.result || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    }

    const res = await request.get('/slaughter/ractopamine', { params })
    tableData.value = res.data?.records || res.data?.list || res.list || []
    pagination.total = res.data?.total || res.total || 0
  } catch (error) {
    console.error('获取瘦肉精检测列表失败:', error)
    ElMessage.error('获取列表数据失败')
  } finally {
    tableLoading.value = false
  }
}

// ==================== 搜索/重置 ====================

const handleSearch = () => {
  pagination.pageNum = 1
  fetchList()
}

const handleReset = () => {
  searchForm.testNo = ''
  searchForm.batchNo = ''
  searchForm.sampleNo = ''
  searchForm.testType = ''
  searchForm.result = ''
  dateRange.value = null
  handleSearch()
}

// ==================== 分页 ====================

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  fetchList()
}

const handlePageChange = (page) => {
  pagination.pageNum = page
  fetchList()
}

// ==================== 表单弹窗 ====================

const formDialogVisible = ref(false)
const currentEditData = ref(null)

const handleCreate = () => {
  currentEditData.value = null
  formDialogVisible.value = true
}

const handleEdit = (row) => {
  currentEditData.value = { ...row }
  formDialogVisible.value = true
}

const handleView = (row) => {
  console.log('查看详情:', row)
}

const handleTestResult = (row) => {
  currentEditData.value = { ...row, result: '阴性', status: '已完成' }
  formDialogVisible.value = true
}

const handleFormSubmit = () => {
  fetchList()
}

// ==================== 初始化 ====================

onMounted(() => {
  fetchList()
})
</script>

<style lang="scss" scoped>
.ractopamine-test {
  padding: 20px;
  background-color: #fff;
  border-radius: 4px;

  .search-panel {
    padding-bottom: 16px;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 16px;

    :deep(.el-form-item) {
      margin-bottom: 12px;
    }
  }

  .action-bar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 16px;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;
  }
}
</style>
