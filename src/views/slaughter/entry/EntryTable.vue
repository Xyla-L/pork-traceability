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
      <el-table-column prop="arrivalTime" label="入场时间" min-width="160" align="center" />
      <el-table-column prop="weight" label="重量(kg)" min-width="100" align="center" />
      <el-table-column prop="quarantineCert" label="检疫证明" min-width="140" show-overflow-tooltip />
      <el-table-column prop="inspector" label="查验员" min-width="100" align="center" />
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
          <el-button type="success" link size="small" @click="$emit('inspect', row)">
            查验
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

defineEmits(['view', 'edit', 'inspect'])
</script>

<style lang="scss" scoped>
.entry-table {
  width: 100%;
}
</style>
