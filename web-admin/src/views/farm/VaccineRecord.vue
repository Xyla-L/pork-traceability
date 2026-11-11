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

    <!-- 分页：total 是「组数」不是「头数」——列表按批聚合，一头一行会让翻页错位 -->
    <div class="pagination-wrapper">
      <span class="group-total">共 {{ pagination.total }} 组（按批聚合，点行首箭头看逐头）</span>
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="sizes, prev, pager, next, jumper"
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
import { recordGroupApi } from '@/api/modules/breeding'
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

// 获取疫苗记录列表（按批次分组：一次免疫一行，展开看逐头）
//
// 改造前是「先拉 100 头猪、再逐头并发调 /vaccines/list 在前端拼接」——同一批 30 头
// 打一次疫苗会刷成 30 行，而且请求数是 O(头数)。现在换成后端分组查询，
// 一次请求拿回「主行 + 逐头明细」，分页按组算。
const fetchList = async () => {
  tableLoading.value = true

  try {
    const res = await recordGroupApi.vaccines({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      earTagNo: searchForm.earTagNo || undefined,
      vaccineName: searchForm.vaccineName || undefined,
      includeVoid: searchForm.includeVoid,
    })
    const list = res?.records || res?.list || []
    tableData.value = Array.isArray(list) ? list : []
    pagination.total = res?.total || 0
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
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;

    .group-total {
      font-size: 13px;
      color: #909399;
    }
  }
}
</style>
