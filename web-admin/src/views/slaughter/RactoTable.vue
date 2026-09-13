<template>
  <div class="racto-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="testNo" label="检测编号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="批次号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="sampleNo" label="样本编号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="testType" label="检测项目" min-width="120" align="center" />
      <el-table-column prop="testTime" label="检测时间" min-width="160" align="center" />
      <el-table-column prop="operator" label="检测员" min-width="100" align="center" />
      <el-table-column prop="result" label="检测结果" min-width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="ractoResultTag(row.result)" size="small">{{ ractoResultLabel(row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="ractoStatusTag(row.status)" size="small">{{ ractoStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">
            查看
          </el-button>
          <el-button type="warning" link size="small" @click="$emit('edit', row)">
            编辑
          </el-button>
          <el-popconfirm title="确定删除该检测记录吗？" @confirm="$emit('delete', row)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewVisible" title="瘦肉精检测详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="检测编号">{{ currentView.testNo }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ currentView.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="样本编号">{{ currentView.sampleNo }}</el-descriptions-item>
        <el-descriptions-item label="检测项目">{{ currentView.testType }}</el-descriptions-item>
        <el-descriptions-item label="检测方法">{{ currentView.testMethod || '--' }}</el-descriptions-item>
        <el-descriptions-item label="检测时间">{{ currentView.testTime }}</el-descriptions-item>
        <el-descriptions-item label="检测员">{{ currentView.operator }}</el-descriptions-item>
        <el-descriptions-item label="检测结果">
          <el-tag :type="ractoResultTag(currentView.result)" size="small">{{ ractoResultLabel(currentView.result) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="ractoStatusTag(currentView.status)" size="small">{{ ractoStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="检测报告">{{ currentView.reportUrl || '--' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentView.remark || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['edit', 'delete'])

const viewVisible = ref(false)
const currentView = ref({})

const handleView = (row) => {
  currentView.value = row
  viewVisible.value = true
}

// 检测结果：1=阴性 0=阳性
const ractoResultLabel = (r) => ({ 1: '阴性', 0: '阳性' }[r] ?? '--')
const ractoResultTag = (r) => ({ 1: 'success', 0: 'danger' }[r] ?? 'info')
// 状态：0=待检测 1=检测中 2=已完成
const ractoStatusLabel = (s) => ({ 0: '待检测', 1: '检测中', 2: '已完成' }[s] ?? '--')
const ractoStatusTag = (s) => ({ 0: 'info', 1: 'warning', 2: 'success' }[s] ?? 'info')
</script>

<style lang="scss" scoped>
.racto-table {
  width: 100%;
}
</style>
