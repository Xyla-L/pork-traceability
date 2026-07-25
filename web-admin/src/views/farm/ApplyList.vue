<template>
  <div class="apply-list">
    <!-- 状态筛选标签页 -->
    <ApplyFilter
      v-model="currentStatus"
      :counts="statusCounts"
    />

    <!-- 数据表格 -->
    <ApplyTable
      :data="tableData"
      :loading="tableLoading"
      :ear-tag-no-map="earTagNoMap"
      @approve="handleApprove"
      @view="handleView"
    />

    <!-- 分页 -->
    <div v-if="pagination.total > 0" class="pagination-wrapper">
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

    <!-- 审批弹窗 -->
    <ApproveDialog
      v-model:visible="approveDialogVisible"
      :apply-data="currentApplyData"
      @submit="handleApproveSubmit"
    />

    <!-- 查看详情弹窗 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="申报详情"
      width="560px"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申报编号">{{ viewData.applyNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生猪耳标号">{{ viewData.earTagNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申报时间">{{ viewData.applyTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="体重">{{ viewData.weightKg ? `${viewData.weightKg} kg` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标屠宰场" :span="2">{{ viewData.targetSlaughterhouse || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批状态" :span="2">
          <el-tag :type="statusTagType(viewData.approvalStatus)">
            {{ statusLabel(viewData.approvalStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="viewData.approveRemark" label="审批备注" :span="2">
          {{ viewData.approveRemark }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import ApplyFilter from './ApplyFilter.vue'
import ApplyTable from './ApplyTable.vue'
import ApproveDialog from './ApproveDialog.vue'

// ==================== 状态筛选 ====================

const currentStatus = ref(0)
const statusCounts = ref({
  0: 0,
  1: 0,
  2: 0
})

// ==================== 表格相关 ====================

const tableData = ref([])
const tableLoading = ref(false)
const earTagNoMap = ref({})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// Mock 数据
const MOCK_APPLIES = (() => {
  const earTags = ['ET20240601001', 'ET20240601002', 'ET20240601003', 'ET20240602005', 'ET20240602008']
  const slaughterhouses = ['XX市定点屠宰场', 'YY市肉联厂', 'ZZ市屠宰加工中心']
  const approvers = ['监管员-李建国', '管理员-王伟', null]
  const data = []
  for (let i = 1; i <= 24; i++) {
    const pigId = 1000 + (i % 20) + 1
    const status = i <= 10 ? 0 : i <= 18 ? 1 : 2
    data.push({
      id: 3000 + i,
      pigId,
      applyNo: `SA${String(2024070000 + i).padStart(11, '0')}`,
      applyTime: `2024-07-0${(i % 7) + 1} ${String((i * 3) % 24).padStart(2, '0')}:30`,
      weightKg: 100 + Math.floor(Math.random() * 40),
      targetSlaughterhouse: slaughterhouses[i % 3],
      approvalStatus: status,
      approvalTime: status > 0 ? `2024-07-${String((i + 2) % 28 + 1).padStart(2, '0')} 10:00` : null,
      approver: approvers[i % 3],
      createTime: `2024-07-${String(i % 28 + 1).padStart(2, '0')} 08:00:00`,
    })
  }
  return data
})()

const MOCK_EARTAG_MAP = {
  1001: 'ET20240601001', 1002: 'ET20240601002', 1003: 'ET20240601003',
  1004: 'ET20240601004', 1005: 'ET20240602005', 1006: 'ET20240602006',
  1007: 'ET20240602007', 1008: 'ET20240602008', 1009: 'ET20240603001',
  1010: 'ET20240603002', 1011: 'ET20240603003', 1012: 'ET20240604001',
  1013: 'ET20240604002', 1014: 'ET20240604003', 1015: 'ET20240605001',
  1016: 'ET20240605002', 1017: 'ET20240605003', 1018: 'ET20240606001',
  1019: 'ET20240606002', 1020: 'ET20240606003',
}

// 获取申报列表
const fetchList = async () => {
  tableLoading.value = true

  // 先加载模拟数据
  earTagNoMap.value = MOCK_EARTAG_MAP
  loadMockData()

  try {
    const params = {
      approvalStatus: currentStatus.value,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    }
    const res = await request.get('/breeding/applies', { params, timeout: 5000 })
    const list = res.data?.records || res.data?.list || res.list || []
    if (list.length > 0) {
      tableData.value = list
      pagination.total = res.data?.total || res.total || list.length
    }
  } catch {
    // 后端不可用，使用模拟数据
  } finally {
    tableLoading.value = false
  }
}

const loadMockData = () => {
  let filtered = [...MOCK_APPLIES]
  if (currentStatus.value > 0) {
    filtered = filtered.filter((a) => a.approvalStatus === currentStatus.value)
  }
  statusCounts.value = {
    0: MOCK_APPLIES.filter((a) => a.approvalStatus === 0).length,
    1: MOCK_APPLIES.filter((a) => a.approvalStatus === 1).length,
    2: MOCK_APPLIES.filter((a) => a.approvalStatus === 2).length,
  }
  pagination.total = filtered.length
  const start = (pagination.pageNum - 1) * pagination.pageSize
  tableData.value = filtered.slice(start, start + pagination.pageSize)
}

const fetchCounts = () => {
  // Mock 数据已在 loadMockData 中处理
}

// ==================== 状态切换 ====================

watch(currentStatus, () => {
  pagination.pageNum = 1
  fetchList()
})

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

// ==================== 审批 ====================

const approveDialogVisible = ref(false)
const currentApplyData = ref({})

const handleApprove = (row) => {
  currentApplyData.value = {
    ...row,
    earTagNo: earTagNoMap.value[row.pigId] || row.pigId
  }
  approveDialogVisible.value = true
}

const handleApproveSubmit = async (approveData) => {
  try {
    await request.put(`/breeding/applies/${approveData.id}/approve`, {
      approvalStatus: approveData.approvalStatus,
      remark: approveData.remark
    })
    ElMessage.success(
      approveData.approvalStatus === 1 ? '审批通过' : '已驳回'
    )
    fetchList()
    fetchCounts()
  } catch (error) {
    console.error('审批失败:', error)
    ElMessage.error('审批操作失败')
  }
}

// ==================== 查看详情 ====================

const viewDialogVisible = ref(false)
const viewData = ref({})

const handleView = (row) => {
  viewData.value = {
    ...row,
    earTagNo: earTagNoMap.value[row.pigId] || row.pigId
  }
  viewDialogVisible.value = true
}

const statusLabel = (status) => {
  const map = {
    0: '待审批',
    1: '已通过',
    2: '已驳回'
  }
  return map[status] || '未知'
}

const statusTagType = (status) => {
  const map = {
    0: 'warning',
    1: 'success',
    2: 'danger'
  }
  return map[status] || 'info'
}

// ==================== 初始化 ====================

onMounted(() => {
  fetchList()
  fetchCounts()
})
</script>

<style lang="scss" scoped>
.apply-list {
  padding: 20px;
  background-color: #fff;
  border-radius: 4px;

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;
  }
}
</style>
