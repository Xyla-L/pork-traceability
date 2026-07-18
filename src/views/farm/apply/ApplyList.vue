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

// 获取申报列表
const fetchList = async () => {
  tableLoading.value = true
  try {
    const params = {
      approvalStatus: currentStatus.value,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }

    const res = await request.get('/breeding/applies', { params })
    tableData.value = res.data?.records || res.data?.list || res.list || []
    pagination.total = res.data?.total || res.total || 0

    // 构建 pigId -> earTagNo 的映射
    const pigIds = [...new Set(tableData.value.map((item) => item.pigId).filter(Boolean))]
    if (pigIds.length > 0) {
      await buildEarTagNoMap(pigIds)
    }
  } catch (error) {
    console.error('获取申报列表失败:', error)
    ElMessage.error('获取申报列表失败')
  } finally {
    tableLoading.value = false
  }
}

// 获取各状态的数量
const fetchCounts = async () => {
  try {
    const statuses = [0, 1, 2]
    const promises = statuses.map(async (status) => {
      const res = await request.get('/breeding/applies', {
        params: { approvalStatus: status, pageSize: 1 }
      })
      return {
        status,
        count: res.data?.total || res.total || 0
      }
    })
    const results = await Promise.all(promises)
    const counts = { 0: 0, 1: 0, 2: 0 }
    results.forEach((r) => {
      counts[r.status] = r.count
    })
    statusCounts.value = counts
  } catch (error) {
    console.error('获取审批数量失败:', error)
  }
}

// 构建 pigId -> earTagNo 映射
const buildEarTagNoMap = async (pigIds) => {
  try {
    const res = await request.get('/breeding/pigs', {
      params: { pageSize: 200 }
    })
    const pigs = res.data?.records || res.data?.list || res.list || []
    const map = {}
    pigs.forEach((pig) => {
      map[pig.id] = pig.earTagNo
    })
    earTagNoMap.value = map
  } catch (error) {
    console.error('获取生猪信息失败:', error)
  }
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
