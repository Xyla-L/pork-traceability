<template>
  <div class="blockchain-badge" :class="{ verified: isVerified }">
    <div class="badge-icon">
      <el-icon v-if="isVerified" color="#67c23a"><CheckCircleFilled /></el-icon>
      <el-icon v-else-if="isLoading" color="#409eff"><Loading /></el-icon>
      <el-icon v-else color="#909399"><CircleFilled /></el-icon>
    </div>
    <div class="badge-info">
      <span class="badge-text">{{ badgeText }}</span>
      <span v-if="txHash" class="badge-hash">{{ formatHash(txHash) }}</span>
    </div>
    <el-button
      v-if="!isVerified && !isLoading"
      size="small"
      type="primary"
      @click="$emit('verify')"
    >
      验真
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircleFilled, CircleFilled, Loading } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  status?: 'confirmed' | 'pending' | 'failed' | 'none'
  txHash?: string
  blockNumber?: number
}>(), {
  status: 'none',
})

defineEmits<{
  (e: 'verify'): void
}>()

const isVerified = computed(() => props.status === 'confirmed')
const isLoading = computed(() => props.status === 'pending')

const badgeText = computed(() => {
  switch (props.status) {
    case 'confirmed':
      return '已上链'
    case 'pending':
      return '上链中...'
    case 'failed':
      return '上链失败'
    default:
      return '未上链'
  }
})

function formatHash(hash: string) {
  if (!hash) return ''
  return `TX: ${hash.slice(0, 8)}...${hash.slice(-4)}`
}
</script>

<style lang="scss" scoped>
.blockchain-badge {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  background: #f5f5f5;
  border-radius: 20px;
  border: 1px solid #ebeef5;

  &.verified {
    background: #f0f9eb;
    border-color: #e1f3d8;
  }

  .badge-icon {
    font-size: 16px;
  }

  .badge-info {
    display: flex;
    flex-direction: column;

    .badge-text {
      font-size: 13px;
      font-weight: 500;
      color: #606266;
    }

    .badge-hash {
      font-size: 11px;
      color: #909399;
      font-family: monospace;
    }
  }
}
</style>