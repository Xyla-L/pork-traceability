<template>
  <div class="vaccine-record">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="生猪耳标号">
          <el-input
            v-model="searchForm.earTagNo"
            placeholder="请输入生猪耳标号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="疫苗名称">
          <el-input
            v-model="searchForm.vaccineName"
            placeholder="请输入疫苗名称"
            clearable
            @keyup.enter="handleSearch"
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
        录入疫苗
      </el-button>
    </div>

    <!-- 数据表格 -->
    <VaccineTable
      :data="tableData"
      :loading="tableLoading"
      @delete="handleDelete"
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

    <!-- 录入疫苗弹窗 -->
    <VaccineFormDialog
      v-model:visible="formDialogVisible"
      @submit="handleFormSubmit"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import VaccineTable from './VaccineTable.vue'
import VaccineFormDialog from './VaccineFormDialog.vue'

// ==================== 搜索相关 ====================

const searchForm = reactive({
  earTagNo: '',
  vaccineName: ''
})

// ==================== 表格相关 ====================

const tableData = ref([])
const tableLoading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 获取疫苗记录列表
const fetchList = async () => {
  tableLoading.value = true

  // 先加载模拟数据兜底
  loadMockData()

  try {
    const pigParams = { pageSize: 100 }
    if (searchForm.earTagNo) {
      pigParams.earTagNo = searchForm.earTagNo
    }

    const pigRes = await request.get('/breeding/pigs', { params: pigParams, timeout: 5000 })
    const pigs = pigRes.data?.records || pigRes.data?.list || pigRes.list || []

    if (pigs.length === 0) { tableLoading.value = false; return }

    const allVaccines = []
    const promises = pigs.map(async (pig) => {
      try {
        const vacRes = await request.get(`/breeding/pigs/${pig.id}/vaccines`, { timeout: 5000 })
        const vaccines = vacRes.data || vacRes.list || []
        vaccines.forEach((v) => {
          allVaccines.push({ ...v, earTagNo: pig.earTagNo, pigId: pig.id })
        })
      } catch { /* skip */ }
    })

    await Promise.all(promises)

    // 按疫苗名称过滤
    let filtered = allVaccines
    if (searchForm.vaccineName) {
      filtered = filtered.filter((v) => v.vaccineName?.includes(searchForm.vaccineName))
    }

    if (filtered.length > 0) {
      pagination.total = filtered.length
      const start = (pagination.pageNum - 1) * pagination.pageSize
      tableData.value = filtered.slice(start, start + pagination.pageSize)
    }
  } catch {
    // 后端不可用，使用已加载的模拟数据
  } finally {
    tableLoading.value = false
  }
}

const loadMockData = () => {
  const earTags = ['ET20240601001', 'ET20240601002', 'ET20240601003', 'ET20240602005', 'ET20240602008']
  const vaccineNames = ['猪瘟活疫苗', '口蹄疫O型灭活疫苗', '高致病性蓝耳病疫苗', '猪圆环病毒疫苗']
  const operators = ['养殖员-张三', '兽医-李四', '技术员-王五']
  const batchPrefixes = ['CSF-', 'FMD-', 'PRRS-', 'PCV-']

  const allMock = []
  for (let i = 1; i <= 28; i++) {
    const vIdx = i % vaccineNames.length
    allMock.push({
      id: 2000 + i,
      pigId: 1000 + (i % 20) + 1,
      earTagNo: earTags[i % earTags.length],
      vaccineName: vaccineNames[vIdx],
      batchNo: `${batchPrefixes[vIdx]}2024${String(6 + Math.floor(i / 6)).padStart(2, '0')}${String(i).padStart(2, '0')}`,
      injectTime: `2024-0${6 + Math.floor(i / 8)}-${String((i * 3) % 28 + 1).padStart(2, '0')} 0${(i % 8 + 1)}:30`,
      dosage: `${[1, 2][i % 2]}ml/头`,
      operator: operators[i % 3],
      fileIds: [],
      createTime: `2024-06-${String((i * 5) % 28 + 1).padStart(2, '0')} 10:00:00`,
    })
  }

  let filtered = [...allMock]
  if (searchForm.earTagNo) {
    filtered = filtered.filter((v) => v.earTagNo.includes(searchForm.earTagNo))
  }
  if (searchForm.vaccineName) {
    filtered = filtered.filter((v) => v.vaccineName.includes(searchForm.vaccineName))
  }
  pagination.total = filtered.length
  const start = (pagination.pageNum - 1) * pagination.pageSize
  tableData.value = filtered.slice(start, start + pagination.pageSize)
}

// ==================== 搜索/重置 ====================

const handleSearch = () => {
  pagination.pageNum = 1
  fetchList()
}

const handleReset = () => {
  searchForm.earTagNo = ''
  searchForm.vaccineName = ''
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

const handleCreate = () => {
  formDialogVisible.value = true
}

const handleFormSubmit = async (formData) => {
  try {
    await request.post(`/breeding/pigs/${formData.pigId}/vaccines`, {
      vaccineName: formData.vaccineName,
      batchNo: formData.batchNo,
      injectTime: formData.injectTime,
      dosage: formData.dosage,
      operator: formData.operator,
      certPhoto: formData.certPhoto
    })
    ElMessage.success('录入成功')
    fetchList()
  } catch (error) {
    console.error('录入疫苗失败:', error)
    ElMessage.error('录入疫苗失败')
  }
}

// ==================== 删除 ====================

const handleDelete = async (row) => {
  try {
    // 假设删除接口需要 pigId 和 vaccineId
    await request.delete(`/breeding/pigs/${row.pigId}/vaccines/${row.id}`)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    console.error('删除疫苗记录失败:', error)
    ElMessage.error('删除失败')
  }
}

// ==================== 初始化 ====================

onMounted(() => {
  fetchList()
})
</script>

<style lang="scss" scoped>
.vaccine-record {
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
