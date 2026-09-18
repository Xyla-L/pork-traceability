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
      <el-table-column prop="temperature" label="体温(°C)" min-width="100" align="center">
        <template #default="{ row }">
          {{ row.temperature === null || row.temperature === undefined || row.temperature === '' ? '--' : row.temperature }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="inspectStatusTag(row.status)" size="small">{{ inspectStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">
            查看
          </el-button>
          <el-button v-if="row.status !== 3" type="danger" link size="small" @click="handleVoid(row)">
            作废
          </el-button>
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
        <el-descriptions-item label="疫苗记录" :span="2">
          <div v-if="vaccineRecords.length === 0" style="color: #909399">无疫苗记录</div>
          <div v-for="v in vaccineRecords" :key="v.id" style="margin-bottom: 4px">
            {{ v.vaccineName }}（{{ v.batchNo }}）- {{ v.injectTime }} - {{ v.operator }}
          </div>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

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
const vaccineRecords = ref([])

const handleView = async (row) => {
  currentView.value = row
  vaccineRecords.value = []
  viewVisible.value = true
  if (row.pigId) {
    try {
      const res = await request.get(`/breeding/pigs/${row.pigId}/vaccines`)
      vaccineRecords.value = res?.records || res?.list || []
    } catch (e) {
      console.error('获取疫苗记录失败:', e)
    }
  }
}

// 检验类型：1=宰前 2=宰后
const inspectTypeLabel = (t) => ({ 1: '宰前检验', 2: '宰后检验' }[t] ?? t ?? '--')
// 状态：0=待检验 1=合格 2=不合格 3=已作废
const inspectStatusLabel = (s) => ({ 0: '待检验', 1: '合格', 2: '不合格', 3: '已作废' }[s] ?? '--')
const inspectStatusTag = (s) => ({ 0: 'info', 1: 'success', 2: 'danger', 3: 'info' }[s] ?? 'info')

// 作废：检验记录已上链，不支持物理删除，输错信息请作废后重新录入
const emit = defineEmits(['voided', 'edit'])
const handleVoid = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认作废检验记录 ${row.inspectNo || ''} 吗？作废动作将上链存证，且不可恢复。如信息录入有误，请作废后重新新增。`,
      '作废确认',
      { confirmButtonText: '确认作废', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await request.put(`/slaughter/inspections/${row.id}/void`)
    ElMessage.success('已作废，作废存证已上链')
    emit('voided')
  } catch (e) {
    console.error('作废失败:', e)
  }
}
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
