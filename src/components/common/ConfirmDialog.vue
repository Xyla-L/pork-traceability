<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    @close="handleCancel"
  >
    <div class="confirm-dialog__body">
      <el-icon class="confirm-dialog__warning-icon" :class="`is-${confirmType}`">
        <WarningFilled v-if="confirmType === 'danger'" />
        <InfoFilled v-else-if="confirmType === 'warning'" />
        <QuestionFilled v-else />
      </el-icon>
      <span class="confirm-dialog__message">{{ message }}</span>
    </div>

    <template #footer>
      <div class="confirm-dialog__footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button
          :type="confirmType === 'danger' ? 'danger' : confirmType"
          :loading="loading"
          @click="handleConfirm"
        >
          {{ confirmText }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { WarningFilled, InfoFilled, QuestionFilled } from '@element-plus/icons-vue'

const props = defineProps({
  /** 是否显示弹窗 */
  visible: {
    type: Boolean,
    default: false
  },
  /** 弹窗标题 */
  title: {
    type: String,
    default: '提示'
  },
  /** 提示信息 */
  message: {
    type: String,
    default: '确定要执行此操作吗？'
  },
  /** 确认按钮文字 */
  confirmText: {
    type: String,
    default: '确认'
  },
  /** 确认按钮类型: danger / warning / primary */
  confirmType: {
    type: String,
    default: 'danger'
  },
  /** 确认按钮 loading 状态 */
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'confirm', 'cancel'])

const handleConfirm = () => {
  emit('confirm')
}

const handleCancel = () => {
  emit('update:visible', false)
  emit('cancel')
}
</script>

<style lang="scss" scoped>
.confirm-dialog__body {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 0;
}

.confirm-dialog__warning-icon {
  font-size: 22px;
  flex-shrink: 0;
  margin-top: 1px;

  &.is-danger {
    color: #f56c6c;
  }

  &.is-warning {
    color: #e6a23c;
  }

  &.is-primary {
    color: #409eff;
  }
}

.confirm-dialog__message {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  word-break: break-all;
}

.confirm-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>