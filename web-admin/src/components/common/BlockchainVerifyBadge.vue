<template>
  <span class="blockchain-verify-badge" :class="`is-${status}`">
    <template v-if="status === 'confirmed'">
      <span class="badge-icon">&#x2705;</span>
      <span class="badge-text">区块链已验证</span>
      <el-tooltip
        v-if="txHash"
        :content="`交易哈希: ${txHash}`"
        placement="top"
      >
        <el-icon class="badge-copy" @click="handleCopy">
          <DocumentCopy />
        </el-icon>
      </el-tooltip>
    </template>

    <template v-else-if="status === 'pending'">
      <span class="badge-icon">&#x23F3;</span>
      <span class="badge-text">上链确认中</span>
    </template>

    <template v-else>
      <span class="badge-icon">&#8212;</span>
      <span class="badge-text">未上链</span>
    </template>
  </span>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'

const props = defineProps({
  /** 上链状态: confirmed | pending | none */
  status: {
    type: String,
    default: 'none',
    validator: (val) => ['confirmed', 'pending', 'none'].includes(val)
  },
  /** 交易哈希 */
  txHash: {
    type: String,
    default: ''
  }
})

const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.txHash)
    ElMessage.success('交易哈希已复制到剪贴板')
  } catch {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = props.txHash
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('交易哈希已复制到剪贴板')
  }
}
</script>

<style lang="scss" scoped>
.blockchain-verify-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 22px;

  .badge-icon {
    font-size: 14px;
  }

  .badge-copy {
    cursor: pointer;
    color: inherit;
    margin-left: 2px;
    transition: color 0.2s;

    &:hover {
      color: var(--el-color-primary);
    }
  }

  // 已验证 - 绿色
  &.is-confirmed {
    background-color: #f0f9eb;
    color: #67c23a;
    border: 1px solid #e1f3d8;
  }

  // 确认中 - 黄色
  &.is-pending {
    background-color: #fdf6ec;
    color: #e6a23c;
    border: 1px solid #faecd8;
  }

  // 未上链 - 灰色
  &.is-none {
    background-color: #f4f4f5;
    color: #909399;
    border: 1px solid #e9e9eb;
  }
}
</style>
