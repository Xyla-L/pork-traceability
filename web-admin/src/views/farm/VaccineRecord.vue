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
        <el-form-item label="作废记录">
          <el-checkbox v-model="searchForm.includeVoid" @change="handleSearch">
            显示已作废（仍在库中可追溯）
          </el-checkbox>
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
    <VaccineTable
      :data="tableData"
      :loading="tableLoading"
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

  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'
import VaccineTable from './VaccineTable.vue'

// ==================== 搜索相关 ====================

const searchForm = reactive({
  earTagNo: '',
  vaccineName: '',
  // 默认带上已作废记录：作废只是「这条不再生效」，记录仍在库中可追溯，
  // 若默认藏起来，用户会误以为「上报成功却没入账」。
  includeVoid: true
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

  try {
    const pigParams = { size: 100 }
    if (searchForm.earTagNo) {
      pigParams.earTagNo = searchForm.earTagNo
    }

    const pigRes = await request.get('/breeding/pigs', { params: pigParams, timeout: 5000 })
    const pigs = pigRes?.records || pigRes?.list || []

    if (pigs.length === 0) { tableData.value = []; pagination.total = 0; tableLoading.value = false; return }

    const allVaccines = []
    const promises = pigs.map(async (pig) => {
      try {
        // 走 /list 全量取回：分页接口默认每页 10 条，一头猪的免疫记录会被截断；
        // includeVoid 让已作废的记录留在原位（由表格打标），而不是整条消失。
        const vacRes = await request.get(`/breeding/pigs/${pig.id}/vaccines/list`, {
          params: { includeVoid: searchForm.includeVoid },
          timeout: 5000,
        })
        const vaccines = Array.isArray(vacRes) ? vacRes : vacRes?.records || vacRes?.list || []
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

    // 上面的数据是「逐头猪并发拉取再拼接」的，天然没有顺序（谁先返回谁在前）。
    // 不排序时刚上报的记录可能落到第 2、3 页，看起来像「上报成功却没入账」，
    // 因此统一按入账时间倒序，最新的排第一页。
    filtered.sort((a, b) => {
      const ta = String(a.createTime || a.injectTime || '')
      const tb = String(b.createTime || b.injectTime || '')
      if (ta !== tb) return tb.localeCompare(ta)
      return (b.id || 0) - (a.id || 0)
    })

    pagination.total = filtered.length
    const start = (pagination.pageNum - 1) * pagination.pageSize
    tableData.value = filtered.slice(start, start + pagination.pageSize)
  } catch (error) {
    console.error('获取疫苗记录列表失败:', error)
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

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;
  }
}
</style>
