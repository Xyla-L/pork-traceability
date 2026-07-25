<template>
  <div class="pig-list">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="耳标号">
          <el-input
            v-model="searchForm.earTagNo"
            placeholder="请输入耳标号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="养殖场">
          <el-select
            v-model="searchForm.farmId"
            placeholder="请选择养殖场"
            filterable
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="farm in farmOptions"
              :key="farm.id"
              :label="farm.name"
              :value="farm.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="品种">
          <el-select
            v-model="searchForm.breed"
            placeholder="请选择品种"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="item in breedOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="请选择状态"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker
            v-model="birthDateRange"
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
        新建生猪
      </el-button>
    </div>

    <!-- 数据表格 -->
    <PigTable
      :data="tableData"
      :loading="tableLoading"
      :farm-name-map="farmNameMap"
      @view="handleView"
      @edit="handleEdit"
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

    <!-- 新建/编辑弹窗 -->
    <PigFormDialog
      v-model:visible="formDialogVisible"
      :edit-data="currentEditData"
      @submit="handleFormSubmit"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import PigTable from './PigTable.vue'
import PigFormDialog from './PigFormDialog.vue'

// ==================== 搜索相关 ====================

const birthDateRange = ref(null)

const searchForm = reactive({
  earTagNo: '',
  farmId: '',
  breed: '',
  status: '',
  birthDateStart: '',
  birthDateEnd: ''
})

const breedOptions = [
  { label: '长白猪', value: '长白猪' },
  { label: '大白猪', value: '大白猪' },
  { label: '杜洛克', value: '杜洛克' },
  { label: '皮特兰', value: '皮特兰' }
]

const statusOptions = [
  { label: '在养', value: '在养' },
  { label: '已出栏', value: '已出栏' },
  { label: '已屠宰', value: '已屠宰' },
  { label: '异常', value: '异常' }
]

const farmOptions = ref([])
const farmNameMap = ref({})

// 获取养殖场列表
const fetchFarmOptions = async () => {
  try {
    const res = await request.get('/breeding/farms')
    const list = res.data || res.list || []
    farmOptions.value = list
    const map = {}
    list.forEach((farm) => {
      map[farm.id] = farm.name
    })
    farmNameMap.value = map
  } catch (error) {
    console.error('获取养殖场列表失败:', error)
  }
}

// ==================== 表格相关 ====================

const tableData = ref([])
const tableLoading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 获取列表数据
const fetchList = async () => {
  tableLoading.value = true

  // 先加载模拟数据兜底
  loadMockData()

  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      earTagNo: searchForm.earTagNo || undefined,
      farmId: searchForm.farmId || undefined,
      breed: searchForm.breed || undefined,
      status: searchForm.status || undefined,
      birthDateStart: birthDateRange.value?.[0] || undefined,
      birthDateEnd: birthDateRange.value?.[1] || undefined
    }

    const res = await request.get('/breeding/pigs', { params, timeout: 5000 })
    const list = res.data?.records || res.data?.list || res.list || []
    const total = res.data?.total || res.total
    if (Array.isArray(list) && list.length > 0) {
      tableData.value = list
      pagination.total = total || list.length || 0
    }
  } catch {
    // 后端不可用，使用已加载的模拟数据
  } finally {
    tableLoading.value = false
  }
}

// 模拟数据
const loadMockData = () => {
  const breeds = ['长白猪', '大白猪', '杜洛克', '皮特兰', '二元杂', '三元杂']
  const sources = ['自繁', '外购-XX种猪场', '外购-YY养殖场']
  const statuses = [1, 1, 1, 1, 1, 2, 2, 3, 4]
  const farms = [
    { id: 1, name: 'XX市XX养殖专业合作社' },
    { id: 2, name: 'YY县YY养殖场' },
    { id: 3, name: 'ZZ生态养殖有限公司' },
  ]

  const allMock = []
  for (let i = 1; i <= 32; i++) {
    const farm = farms[i % 3]
    const status = statuses[i % statuses.length]
    const birthDate = new Date(2024, 2, 1)
    birthDate.setDate(birthDate.getDate() + i * 7)
    allMock.push({
      id: 1000 + i,
      earTagNo: `ET${String(2024060000 + i).padStart(11, '0')}`,
      farmId: farm.id,
      farmName: farm.name,
      breed: breeds[i % breeds.length],
      birthDate: birthDate.toISOString().split('T')[0],
      penNo: `A-${String(Math.floor((i - 1) / 4) + 1).padStart(2, '0')}`,
      source: sources[i % 3],
      status,
      createTime: '2024-03-15 10:00:00',
      updateTime: '2024-07-15 08:00:00',
    })
  }

  // 搜索过滤
  let filtered = [...allMock]
  if (searchForm.earTagNo) {
    filtered = filtered.filter((p) => p.earTagNo.includes(searchForm.earTagNo))
  }
  if (searchForm.breed) {
    filtered = filtered.filter((p) => p.breed === searchForm.breed)
  }
  if (searchForm.status) {
    const statusMap = { '在养': 1, '已出栏': 2, '已屠宰': 3, '异常': 4 }
    const statusVal = statusMap[searchForm.status]
    if (statusVal) filtered = filtered.filter((p) => p.status === statusVal)
  }
  if (searchForm.farmId) {
    filtered = filtered.filter((p) => p.farmId === Number(searchForm.farmId))
  }

  pagination.total = filtered.length
  const start = (pagination.pageNum - 1) * pagination.pageSize
  tableData.value = filtered.slice(start, start + pagination.pageSize)

  // 同时填充 farmOptions（如果后端没返回）
  if (farmOptions.value.length === 0) {
    farmOptions.value = [
      { id: 1, name: 'XX市XX养殖专业合作社' },
      { id: 2, name: 'YY县YY养殖场' },
      { id: 3, name: 'ZZ生态养殖有限公司' },
    ]
    farmNameMap.value = { 1: 'XX市XX养殖专业合作社', 2: 'YY县YY养殖场', 3: 'ZZ生态养殖有限公司' }
  }
}

// ==================== 搜索/重置 ====================

const handleSearch = () => {
  pagination.pageNum = 1
  fetchList()
}

const handleReset = () => {
  searchForm.earTagNo = ''
  searchForm.farmId = ''
  searchForm.breed = ''
  searchForm.status = ''
  birthDateRange.value = null
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
  // 跳转详情页（可根据路由配置实现）
  console.log('查看详情:', row)
}

const handleFormSubmit = async (formData) => {
  try {
    if (currentEditData.value) {
      // 编辑模式
      await request.put(`/breeding/pigs/${currentEditData.value.id}`, formData)
      ElMessage.success('编辑成功')
    } else {
      // 新建模式
      await request.post('/breeding/pigs', formData)
      ElMessage.success('新建成功')
    }
    fetchList()
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('操作失败')
  }
}

// ==================== 删除 ====================

const handleDelete = async (row) => {
  try {
    await request.delete(`/breeding/pigs/${row.id}`)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败')
  }
}

// ==================== 初始化 ====================

onMounted(() => {
  fetchFarmOptions()
  fetchList()
})
</script>

<style lang="scss" scoped>
.pig-list {
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
