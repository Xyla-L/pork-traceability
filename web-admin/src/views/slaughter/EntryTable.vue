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
      <el-table-column prop="inspector" label="查验员" min-width="100" align="center" />
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="entryStatusTag(row.status)" size="small">{{ entryStatusLabel(row.status) }}</el-tag>
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
          <el-popconfirm title="确定删除该入场查验记录吗？" @confirm="$emit('delete', row)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
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
        <el-descriptions-item label="查验员">{{ currentView.inspector }}</el-descriptions-item>
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
</style>
