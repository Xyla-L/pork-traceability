<template>
  <div class="entry-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="batchNo" label="批次号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="earTagNo" label="耳标号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="sourceFarm" label="来源养殖场" min-width="160" show-overflow-tooltip />
      <el-table-column prop="arriveTime" label="入场时间" min-width="160" align="center" />
      <el-table-column prop="weight" label="重量(kg)" min-width="100" align="center" />
      <el-table-column prop="quarantineCert" label="检疫证明" min-width="140" show-overflow-tooltip />
      <el-table-column prop="inspector" label="查验员" min-width="100" align="center">
        <template #default="{ row }">{{ row.inspector || '未记录' }}</template>
      </el-table-column>
      <el-table-column prop="source" label="采集设备" min-width="140" align="center">
        <template #default="{ row }">
          <el-tooltip :content="row.source === 'DEVICE' ? `设备采集：${row.sourceRef || '--'}` : sourceLabel(row.source)" placement="top">
            <el-tag :type="sourceTag(row.source)" size="small" effect="plain">{{ deviceText(row) }}</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="entryStatusTag(row.status)" size="small">{{ entryStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">
            查看
          </el-button>
          <el-button type="danger" link size="small" @click="handleVoid(row)">作废</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewVisible" title="入场查验详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="批次号">{{ currentView.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="耳标号">{{ currentView.earTagNo }}</el-descriptions-item>
        <el-descriptions-item label="来源养殖场">{{ currentView.sourceFarm }}</el-descriptions-item>
        <el-descriptions-item label="入场时间">{{ currentView.arriveTime }}</el-descriptions-item>
        <el-descriptions-item label="重量(kg)">{{ currentView.weight }}</el-descriptions-item>
        <el-descriptions-item label="检疫证明">{{ currentView.quarantineCert || '--' }}</el-descriptions-item>
        <el-descriptions-item label="车辆牌号">{{ currentView.vehicleNo || '--' }}</el-descriptions-item>
        <el-descriptions-item label="查验员">{{ currentView.inspector || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="采集设备">{{ deviceText(currentView) }}</el-descriptions-item>
        <el-descriptions-item label="健康检查">{{ passFailLabel(currentView.healthCheck) }}</el-descriptions-item>
        <el-descriptions-item label="检疫证核验">{{ passFailLabel(currentView.certVerified) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="entryStatusTag(currentView.status)" size="small">{{ entryStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="查验意见" :span="2">{{ currentView.remark || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { deviceText, sourceLabel, sourceTag } from '@/api/modules/ingest'
import { slaughterApi } from '@/api/modules/slaughter'
import { confirmVoid } from '@/composables/useVoidAction'

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

const emit = defineEmits(['voided'])

const viewVisible = ref(false)
const currentView = ref({})

// 作废：已上链记录不提供删除——作废需填理由，理由与实名操作人随作废事件一并上链
const handleVoid = async (row) => {
  const ok = await confirmVoid({
    title: '作废入场查验记录',
    message: `确认作废 ${row.batchNo || row.earTagNo || ''} 的入场查验记录吗？作废后该记录不再生效，但仍保留在库中并可追溯。`,
    onConfirm: (reason) => slaughterApi.voidEntry(row.id, reason),
  })
  if (ok) emit('voided')
}

const handleView = (row) => {
  currentView.value = row
  viewVisible.value = true
}

// 入场查验状态：0=待查验 1=合格 2=不合格
const entryStatusLabel = (s) => ({ 0: '待查验', 1: '合格', 2: '不合格' }[s] ?? '--')
const entryStatusTag = (s) => ({ 0: 'info', 1: 'success', 2: 'danger' }[s] ?? 'info')
// 通过/异常：1=通过 0=异常
const passFailLabel = (v) => ({ 1: '通过', 0: '异常' }[v] ?? '--')
</script>

<style lang="scss" scoped>
.entry-table {
  width: 100%;
}
.source-ref { color: #909399; font-size: 12px; }
</style>
