<template>
  <span class="blockchain-verify-badge" :class="`is-${visualKey}`">
    <template v-if="status === 'confirmed'">
      <span class="badge-icon"></span>
      <span class="badge-text">{{ onChain ? '区块链已验证' : '本地存证已记录' }}</span>
      <el-tooltip v-if="txHash" :content="hashTip" placement="top">
        <el-icon class="badge-copy" @click="handleCopy"><DocumentCopy /></el-icon>
      </el-tooltip>
      <!-- 只有真上链且有浏览器地址时才给外链：本地账本的哈希在链上根本查不到 -->
      <a v-if="txLink" :href="txLink" target="_blank" rel="noopener" class="badge-link" title="在区块链浏览器查看">
        <el-icon><TopRight /></el-icon>
      </a>
    </template>

    <template v-else-if="status === 'pending'">
      <span class="badge-icon"></span>
      <span class="badge-text">存证确认中</span>
    </template>

    <template v-else-if="status === 'failed'">
      <span class="badge-icon"></span>
      <span class="badge-text">存证失败</span>
    </template>

    <template v-else>
      <span class="badge-icon">&#8212;</span>
      <span class="badge-text">未存证</span>
    </template>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy, TopRight } from '@element-plus/icons-vue'
import { chainMode as globalChainMode, explorerTxUrl } from '@/utils/chainMode'

const props = defineProps({
  /** 存证状态: confirmed | pending | failed | none */
  status: {
    type: String,
    default: 'none',
    validator: (val) => ['confirmed', 'pending', 'failed', 'none'].includes(val)
  },
  /** 存证哈希（交易哈希） */
  txHash: {
    type: String,
    default: ''
  },
  /** 存证载体；不传则用全局缓存的模式，再兜底按本地存证展示 */
  mode: {
    type: Object,
    default: null
  }
})

const mode = computed(() => props.mode || globalChainMode.value)
const onChain = computed(() => Boolean(mode.value?.onChain))
// 只有「已存证 + 真上链」才是绿色通过；本地存证单独一种颜色，避免和链上验证混为一谈
const visualKey = computed(() => {
  if (props.status !== 'confirmed') return props.status
  return onChain.value ? 'confirmed' : 'local'
})
const txLink = computed(() => explorerTxUrl(mode.value, props.txHash))
const hashTip = computed(() => onChain.value
  ? `交易哈希: ${props.txHash}`
  : `存证指纹: ${props.txHash}（本地存证，未写入区块链，链上查不到）`)

const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.txHash)
    ElMessage.success('存证哈希已复制到剪贴板')
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
    ElMessage.success('存证哈希已复制到剪贴板')
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

  .badge-copy,
  .badge-link {
    cursor: pointer;
    color: inherit;
    margin-left: 2px;
    transition: color 0.2s;

    &:hover {
      color: var(--el-color-primary);
    }
  }

  // 链上已验证 - 绿色
  &.is-confirmed {
    background-color: #f0f9eb;
    color: #67c23a;
    border: 1px solid #e1f3d8;
  }

  // 本地存证 - 蓝灰：如实说明「记录在平台内」，不用绿色冒充通过
  &.is-local {
    background-color: #f4f4f5;
    color: #606266;
    border: 1px solid #e0e2e6;
  }

  // 确认中 - 黄色
  &.is-pending {
    background-color: #fdf6ec;
    color: #e6a23c;
    border: 1px solid #faecd8;
  }

  // 未存证 - 灰色
  &.is-none {
    background-color: #f4f4f5;
    color: #909399;
    border: 1px solid #e9e9eb;
  }

  // 存证失败 - 红色：放在这里而不是混进「确认中」，否则失败会被当成还在等
  &.is-failed {
    background-color: #fef0f0;
    color: #f56c6c;
    border: 1px solid #fde2e2;
  }
}
</style>
