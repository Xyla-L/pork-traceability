<template>
  <div class="entry-inspect">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="批次号">
          <el-input
            v-model="searchForm.batchNo"
            placeholder="请输入批次号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="耳标号">
          <el-input
            v-model="searchForm.earTagNo"
            placeholder="请输入耳标号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="来源养殖场">
          <el-input
            v-model="searchForm.sourceFarm"
            placeholder="请输入养殖场"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="请选择状态"
            clearable
            style="width: 120px"
          >
            <el-option label="待查验" value="待查验" />
            <el-option label="合格" value="合格" />
            <el-option label="不合格" value="不合格" />
          </el-select>
        </el-form-item>
        <el-form-item label="入场日期">
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
        新增入场
      </el-button>
    </div>

    <!-- 数据表格 -->
    <EntryTable
      :data="tableData"
      :loading="tableLoading"
      @view="handleView"
      @edit="handleEdit"
      @inspect="handleInspect"
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
    <EntryFormDialog
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
import EntryTable from './EntryTable.vue'
import EntryFormDialog from './EntryFormDialog.vue'

// ==================== 搜索相关 ====================

const dateRange = ref(null)

const searchForm = reactive({
  batchNo: '',
  earTagNo: '',
  sourceFarm: '',
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
      batchNo: searchForm.batchNo || undefined,
      earTagNo: searchForm.earTagNo || undefined,
      sourceFarm: searchForm.sourceFarm || undefined,
      status: searchForm.status || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    }

    const res = await request.get('/slaughter/entries', { params })
    tableData.value = res.data?.records || res.data?.list || res.list || []
    pagination.total = res.data?.total || res.total || 0
  } catch (error) {
    console.error('获取入场查验列表失败:', error)
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
  searchForm.batchNo = ''
  searchForm.earTagNo = ''
  searchForm.sourceFarm = ''
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

const handleInspect = (row) => {
  currentEditData.value = { ...row, status: '合格' }
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
.entry-inspect {
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
