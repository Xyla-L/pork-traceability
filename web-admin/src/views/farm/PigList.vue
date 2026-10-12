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
              :label="farm.farmName || farm.name"
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


    <!-- 数据表格 -->
    <PigTable
      :data="tableData"
      :loading="tableLoading"
      :farm-name-map="farmNameMap"
      @apply="handleApply"
      @voided="fetchList"
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


    <!-- 出栏申报弹窗 -->
    <el-dialog v-model="applyDialogVisible" title="出栏申报" width="460px" destroy-on-close>
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-width="100px">
        <el-form-item label="耳标号">
          <span>{{ applyRow?.earTagNo || '--' }}</span>
        </el-form-item>
        <el-form-item label="目标屠宰场" prop="targetSlaughterhouse">
          <el-select
            v-model="applyForm.targetSlaughterhouse"
            placeholder="请选择或输入目标屠宰场"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option
              v-for="item in slaughterhouseOptions"
              :key="item.id"
              :label="item.name"
              :value="item.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="出栏体重(kg)">
          <el-input-number v-model="applyForm.weightKg" :min="0" :precision="1" :step="0.5" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="applySubmitting" @click="submitApply">确认申报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { pigApi } from '@/api/modules/breeding'
import PigTable from './PigTable.vue'

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

// 品种下拉：优先取后端字典（预置常见猪种 + 库内实际出现过的品种），
// 不再前端硬编码——设备通道上报的品种不受前端枚举约束，硬编码会让
// 「列表里看得到、下拉里选不到」（如设备上报的「三元杂」）。接口异常时回退内置常用值。
const breedOptions = ref([
  { label: '长白猪', value: '长白猪' },
  { label: '大白猪', value: '大白猪' },
  { label: '杜洛克', value: '杜洛克' },
  { label: '皮特兰', value: '皮特兰' },
  { label: '三元杂', value: '三元杂' }
])

const fetchBreeds = async () => {
  try {
    const res = await request.get('/breeding/pigs/breeds')
    const list = Array.isArray(res) ? res : (res?.data || [])
    if (Array.isArray(list) && list.length) {
      breedOptions.value = list.map((b) => ({ label: b, value: b }))
    }
  } catch (error) {
    console.error('获取品种字典失败，使用内置备选:', error)
  }
}

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
    const res = await request.get('/breeding/farms', { params: { current: 1, size: 500 } })
    const list = res?.records || res?.list || []
    farmOptions.value = list
    const map = {}
    list.forEach((farm) => {
      map[farm.id] = farm.farmName || farm.name
    })
    farmNameMap.value = map
  } catch (error) {
    console.error('获取养殖场列表失败:', error)
  }
}

// ==================== 屠宰场下拉（出栏申报使用） ====================

const slaughterhouseOptions = ref([])

const fetchSlaughterhouses = async () => {
  try {
    const tree = await request.get('/system/orgs/tree')
    // 递归扁平化机构树，提取 type=slaughter 的屠宰场，保证与机构管理数据一致
    const list = []
    const walk = (nodes) => {
      if (!Array.isArray(nodes)) return
      nodes.forEach((n) => {
        if (n.type === 'slaughter') {
          list.push({ id: n.id, name: n.name })
        }
        if (n.children) walk(n.children)
      })
    }
    walk(tree)
    slaughterhouseOptions.value = list
  } catch (error) {
    console.error('获取屠宰场列表失败:', error)
    slaughterhouseOptions.value = []
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

  try {
    const params = {
      current: pagination.pageNum,
      size: pagination.pageSize,
      earTagNo: searchForm.earTagNo || undefined,
      farmId: searchForm.farmId || undefined,
      breed: searchForm.breed || undefined,
      status: searchForm.status || undefined,
      birthDateStart: birthDateRange.value?.[0] || undefined,
      birthDateEnd: birthDateRange.value?.[1] || undefined
    }

    const res = await request.get('/breeding/pigs', { params, timeout: 5000 })
    const list = res?.records || res?.list || []
    const total = res?.total
    tableData.value = Array.isArray(list) ? list : []
    pagination.total = total || list.length || 0
  } catch (error) {
    console.error('获取生猪列表失败:', error)
    tableData.value = []
    pagination.total = 0
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

// ==================== 初始化 ====================

onMounted(() => {
  fetchFarmOptions()
  fetchBreeds()
  fetchList()
  fetchSlaughterhouses()
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

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;
  }
}
</style>
