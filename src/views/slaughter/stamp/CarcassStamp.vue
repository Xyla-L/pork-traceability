<template>
  <div class="carcass-stamp">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="盖章编号">
          <el-input
            v-model="searchForm.stampNo"
            placeholder="请输入盖章编号"
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
        <el-form-item label="胴体编号">
          <el-input
            v-model="searchForm.carcassNo"
            placeholder="请输入胴体编号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="印章类型">
          <el-select
            v-model="searchForm.stampType"
            placeholder="请选择"
            clearable
            style="width: 140px"
          >
            <el-option label="检疫合格章" value="检疫合格章" />
            <el-option label="检验合格章" value="检验合格章" />
            <el-option label="无害化处理章" value="无害化处理章" />
            <el-option label="高温处理章" value="高温处理章" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="请选择状态"
            clearable
            style="width: 120px"
          >
            <el-option label="待盖章" value="待盖章" />
            <el-option label="已盖章" value="已盖章" />
            <el-option label="已作废" value="已作废" />
          </el-select>
        </el-form-item>
        <el-form-item label="盖章日期">
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
        新增盖章
      </el-button>
    </div>

    <!-- 数据表格 -->
    <StampTable
      :data="tableData"
      :loading="tableLoading"
      @view="handleView"
      @edit="handleEdit"
      @stamp="handleStamp"
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
    <StampFormDialog
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
import StampTable from './StampTable.vue'
import StampFormDialog from './StampFormDialog.vue'

// ==================== 搜索相关 ====================

const dateRange = ref(null)

const searchForm = reactive({
  stampNo: '',
  batchNo: '',
  carcassNo: '',
  stampType: '',
  status: '',
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
      stampNo: searchForm.stampNo || undefined,
      batchNo: searchForm.batchNo || undefined,
      carcassNo: searchForm.carcassNo || undefined,
      stampType: searchForm.stampType || undefined,
      status: searchForm.status || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    }

    const res = await request.get('/slaughter/stamps', { params })
    tableData.value = res.data?.records || res.data?.list || res.list || []
    pagination.total = res.data?.total || res.total || 0
  } catch (error) {
    console.error('获取检疫盖章列表失败:', error)
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
  searchForm.stampNo = ''
  searchForm.batchNo = ''
  searchForm.carcassNo = ''
  searchForm.stampType = ''
  searchForm.status = ''
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

const handleStamp = (row) => {
  currentEditData.value = { ...row, status: '已盖章', isVerified: true }
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
.carcass-stamp {
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
