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
      <el-table-column prop="tester" label="检测员" min-width="100" align="center" />
      <el-table-column prop="result" label="检测结果" min-width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.result === '阴性' ? 'success' : row.result === '阳性' ? 'danger' : 'info'" size="small">
            {{ row.result || '待检测' }}
          </el-tag>
        </template>
      </el-table-column>
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
          <el-button type="success" link size="small" @click="$emit('test', row)">
            录入结果
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

defineEmits(['view', 'edit', 'test'])
</script>

<style lang="scss" scoped>
.racto-table {
  width: 100%;
}
</style>
