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
      <el-table-column prop="inspectType" label="检验类型" min-width="120" align="center" />
      <el-table-column prop="inspectTime" label="检验时间" min-width="160" align="center" />
      <el-table-column prop="inspector" label="检验员" min-width="100" align="center" />
      <el-table-column prop="temperature" label="体温(°C)" min-width="100" align="center" />
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <status-tag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="$emit('view', row)">
            查看
          </el-button>
          <el-button type="warning" link size="small" @click="$emit('edit', row)">
            编辑
          </el-button>
          <el-button type="success" link size="small" @click="$emit('report', row)">
            出具报告
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import StatusTag from '@/components/common/StatusTag.vue'

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

defineEmits(['view', 'edit', 'report'])
</script>

<style lang="scss" scoped>
.inspect-table {
  width: 100%;
}
</style>
