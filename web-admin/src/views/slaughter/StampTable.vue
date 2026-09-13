<template>
  <div class="stamp-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="stampNo" label="盖章编号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="批次号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="carcassNo" label="胴体编号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="stampType" label="印章类型" min-width="140" align="center" />
      <el-table-column prop="stampTime" label="盖章时间" min-width="160" align="center" />
      <el-table-column prop="veterinary" label="检疫员" min-width="100" align="center" />
      <el-table-column prop="isVerified" label="区块链核验" min-width="120" align="center">
        <template #default="{ row }">
          <blockchain-verify-badge :verified="!!row.contentHash" :tx-hash="row.contentHash" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="stampStatusTag(row.status)" size="small">{{ stampStatusLabel(row.status) }}</el-tag>
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
          <el-popconfirm title="确定删除该盖章记录吗？" @confirm="$emit('delete', row)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewVisible" title="检疫盖章详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="盖章编号">{{ currentView.stampNo }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ currentView.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="胴体编号">{{ currentView.carcassNo }}</el-descriptions-item>
        <el-descriptions-item label="印章类型">{{ currentView.stampType }}</el-descriptions-item>
        <el-descriptions-item label="盖章时间">{{ currentView.stampTime }}</el-descriptions-item>
        <el-descriptions-item label="官方兽医">{{ currentView.veterinary }}</el-descriptions-item>
        <el-descriptions-item label="盖章部位">{{ currentView.stampPosition || '--' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="stampStatusTag(currentView.status)" size="small">{{ stampStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
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
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'

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

// 状态：0=待盖章 1=已盖章 2=已作废
const stampStatusLabel = (s) => ({ 0: '待盖章', 1: '已盖章', 2: '已作废' }[s] ?? '--')
const stampStatusTag = (s) => ({ 0: 'info', 1: 'success', 2: 'danger' }[s] ?? 'info')
</script>

<style lang="scss" scoped>
.stamp-table {
  width: 100%;
}
.hash-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}
</style>
