<template>
  <div class="inspect-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="inspectNo" label="检验编号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="批次号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="earTagNo" label="耳标号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="inspectType" label="检验类型" min-width="120" align="center">
        <template #default="{ row }">
          {{ inspectTypeLabel(row.inspectType) }}
        </template>
      </el-table-column>
      <el-table-column prop="inspectTime" label="检验时间" min-width="160" align="center" />
      <el-table-column prop="veterinary" label="检验员" min-width="100" align="center" />
      <el-table-column prop="temperature" label="体温(°C)" min-width="100" align="center" />
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="inspectStatusTag(row.status)" size="small">{{ inspectStatusLabel(row.status) }}</el-tag>
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
          <el-popconfirm title="确定删除该检验记录吗？" @confirm="$emit('delete', row)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewVisible" title="屠宰检验详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="检验编号">{{ currentView.inspectNo }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ currentView.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="耳标号">{{ currentView.earTagNo }}</el-descriptions-item>
        <el-descriptions-item label="检验类型">{{ inspectTypeLabel(currentView.inspectType) }}</el-descriptions-item>
        <el-descriptions-item label="检验时间">{{ currentView.inspectTime }}</el-descriptions-item>
        <el-descriptions-item label="体温(°C)">{{ currentView.temperature ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="官方兽医">{{ currentView.veterinary }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="inspectStatusTag(currentView.status)" size="small">{{ inspectStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="检验结论" :span="2">{{ currentView.conclusion || '--' }}</el-descriptions-item>
        <el-descriptions-item label="内容哈希" :span="2">
          <span v-if="currentView.contentHash" class="hash-text">{{ currentView.contentHash }}</span>
          <span v-else>--</span>
        </el-descriptions-item>
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

// 检验类型：1=宰前 2=宰后
const inspectTypeLabel = (t) => ({ 1: '宰前检验', 2: '宰后检验' }[t] ?? t ?? '--')
// 状态：0=待检验 1=合格 2=不合格
const inspectStatusLabel = (s) => ({ 0: '待检验', 1: '合格', 2: '不合格' }[s] ?? '--')
const inspectStatusTag = (s) => ({ 0: 'info', 1: 'success', 2: 'danger' }[s] ?? 'info')
</script>

<style lang="scss" scoped>
.inspect-table {
  width: 100%;
}
.hash-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}
</style>
