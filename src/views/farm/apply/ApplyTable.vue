<template>
  <div class="apply-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="applyNo" label="申报编号" min-width="160" show-overflow-tooltip />
      <el-table-column label="生猪耳标号" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ earTagNoMap[row.pigId] || row.pigId || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申报时间" min-width="160" align="center" />
      <el-table-column prop="weightKg" label="体重(kg)" min-width="100" align="center">
        <template #default="{ row }">
          {{ row.weightKg ? `${row.weightKg} kg` : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="targetSlaughterhouse" label="目标屠宰场" min-width="160" show-overflow-tooltip />
      <el-table-column prop="approvalStatus" label="审批状态" min-width="100" align="center">
        <template #default="{ row }">
          <status-tag :status="row.approvalStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-if="row.approvalStatus === 0"
            type="primary"
            link
            size="small"
            @click="$emit('approve', row)"
          >
            审批
          </el-button>
          <el-button type="info" link size="small" @click="$emit('view', row)">
            查看
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import StatusTag from '@/components/StatusTag.vue'

defineProps({
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  earTagNoMap: {
    type: Object,
    default: () => ({})
  }
})

defineEmits(['approve', 'view'])
</script>

<style lang="scss" scoped>
.apply-table {
  :deep(.el-table) {
    th.el-table__cell {
      background-color: #f5f7fa;
      color: #606266;
      font-weight: 600;
    }
  }
}
</style>
