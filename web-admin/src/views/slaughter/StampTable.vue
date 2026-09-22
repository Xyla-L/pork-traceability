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
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="stampStatusTag(row.status)" size="small">{{ stampStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="数据来源" min-width="120" align="center">
        <template #default="{ row }">
          <el-tooltip :content="row.source === 'DEVICE' ? `自动盖章机：${row.sourceRef || '--'}` : '人工录入'" placement="top">
            <el-tag :type="sourceTag(row.source)" size="small" effect="plain">{{ sourceLabel(row.source) }}</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">
            查看
          </el-button>
          <el-button v-if="row.status === 0 || row.status === 1" type="danger" link size="small" @click="handleVoid(row)">
            作废
          </el-button>
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
        <el-descriptions-item label="状态">
          <el-tag :type="stampStatusTag(currentView.status)" size="small">{{ stampStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sourceLabel, sourceTag } from '@/api/modules/ingest'
import request from '@/utils/request'
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

const viewVisible = ref(false)
const currentView = ref({})

const handleView = (row) => {
  currentView.value = row
  viewVisible.value = true
}

// 状态：0=待盖章 1=已盖章 2=已作废
const stampStatusLabel = (s) => ({ 0: '待盖章', 1: '已盖章', 2: '已作废' }[s] ?? '--')
const stampStatusTag = (s) => ({ 0: 'info', 1: 'success', 2: 'danger' }[s] ?? 'info')

// 作废：盖章记录已上链，不支持物理删除，输错信息请作废后重新录入
const emit = defineEmits(['voided', 'edit'])
const handleVoid = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认作废盖章记录 ${row.stampNo || ''} 吗？作废动作将上链存证，且不可恢复。如信息录入有误，请作废后重新新增。`,
      '作废确认',
      { confirmButtonText: '确认作废', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await request.put(`/slaughter/stamps/${row.id}/void`)
    ElMessage.success('已作废，作废存证已上链')
    emit('voided')
  } catch (e) {
    console.error('作废失败:', e)
  }
}
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
