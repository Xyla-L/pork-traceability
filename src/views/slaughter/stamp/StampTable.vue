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
      <el-table-column prop="inspector" label="检疫员" min-width="100" align="center" />
      <el-table-column prop="isVerified" label="区块链核验" min-width="120" align="center">
        <template #default="{ row }">
          <blockchain-verify-badge :verified="row.isVerified" :tx-hash="row.blockchainTxHash" />
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
          <el-button type="success" link size="small" @click="$emit('stamp', row)">
            盖章
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import StatusTag from '@/components/common/StatusTag.vue'
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

defineEmits(['view', 'edit', 'stamp'])
</script>

<style lang="scss" scoped>
.stamp-table {
  width: 100%;
}
</style>
