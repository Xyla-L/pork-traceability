<template>
  <div class="slaughter-inspect">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="检验编号">
          <el-input
            v-model="searchForm.inspectNo"
            placeholder="请输入检验编号"
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
        <el-form-item label="耳标号">
          <el-input
            v-model="searchForm.earTagNo"
            placeholder="请输入耳标号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="检验类型">
          <el-select
            v-model="searchForm.inspectType"
            placeholder="请选择"
            clearable
            style="width: 140px"
          >
            <el-option label="宰前检验" value="宰前检验" />
            <el-option label="宰后检验" value="宰后检验" />
            <el-option label="同步检验" value="同步检验" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="请选择状态"
            clearable
            style="width: 120px"
          >
            <el-option label="待检验" value="待检验" />
            <el-option label="合格" value="合格" />
            <el-option label="不合格" value="不合格" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验日期">
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
        新增检验
      </el-button>
    </div>

    <!-- 数据表格 -->
    <InspectTable
      :data="tableData"
      :loading="tableLoading"
      @view="handleView"
      @edit="handleEdit"
      @report="handleReport"
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
    <InspectFormDialog
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
import InspectTable from './InspectTable.vue'
import InspectFormDialog from './InspectFormDialog.vue'

// ==================== 搜索相关 ====================

const dateRange = ref(null)

const searchForm = reactive({
  inspectNo: '',
  batchNo: '',
  earTagNo: '',
  inspectType: '',
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
      inspectNo: searchForm.inspectNo || undefined,
      batchNo: searchForm.batchNo || undefined,
      earTagNo: searchForm.earTagNo || undefined,
      inspectType: searchForm.inspectType || undefined,
      status: searchForm.status || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    }

    const res = await request.get('/slaughter/inspections', { params })
    tableData.value = res.data?.records || res.data?.list || res.list || []
    pagination.total = res.data?.total || res.total || 0
  } catch (error) {
    console.error('获取屠宰检验列表失败:', error)
    // 后端不可用时使用模拟数据
    const mockData = Array.from({ length: 28 }, (_, i) => ({
      id: i + 1,
      inspectNo: `IN${Date.now().toString(36).toUpperCase()}${String(i + 1).padStart(3, '0')}`,
      batchNo: `B202407${String(1500 + i).padStart(4, '0')}`,
      earTagNo: `ET202406${String(100 + i * 2).padStart(3, '0')}`,
      inspectType: ['宰前检验', '宰后检验', '同步检验'][i % 3],
      inspector: ['王兽医', '李兽医', '张兽医', '赵兽医'][i % 4],
      inspectTime: `2024-07-${String(2 + Math.floor(i / 4)).padStart(2, '0')} ${String(8 + i % 10).padStart(2, '0')}:00`,
      result: i < 22 ? '合格' : i < 26 ? '不合格' : '待检验',
      status: i < 22 ? '合格' : i < 26 ? '不合格' : '待检验',
      organCheck: i % 5 === 0 ? '心脏异常' : '全部正常',
    }))
    const start = (pagination.pageNum - 1) * pagination.pageSize
    const end = start + pagination.pageSize
    tableData.value = mockData.slice(start, end)
    pagination.total = mockData.length
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
  searchForm.inspectNo = ''
  searchForm.batchNo = ''
  searchForm.earTagNo = ''
  searchForm.inspectType = ''
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

const handleReport = (row) => {
  console.log('出具报告:', row)
  ElMessage.success('报告出具功能待实现')
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
.slaughter-inspect {
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
    justify-content: center;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;
  }
}
</style>
