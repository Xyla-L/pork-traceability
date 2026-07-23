<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="400px"
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <div class="confirm-content">
      <div class="confirm-icon">
        <el-icon v-if="type === 'warning'" :size="48" color="#e6a23c"><WarningFilled /></el-icon>
        <el-icon v-else-if="type === 'success'" :size="48" color="#67c23a"><CheckCircleFilled /></el-icon>
        <el-icon v-else :size="48" color="#409eff"><InfoFilled /></el-icon>
      </div>
      <p class="confirm-message">{{ message }}</p>
      <textarea
        v-if="showInput"
        v-model="inputValue"
        class="confirm-input"
        :placeholder="inputPlaceholder"
        rows="3"
      />
    </div>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" @click="handleConfirm" :loading="loading">
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { WarningFilled, CheckCircleFilled, InfoFilled } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  visible: boolean
  title?: string
  message?: string
  type?: 'warning' | 'success' | 'info'
  confirmText?: string
  showInput?: boolean
  inputPlaceholder?: string
}>(), {
  title: '确认操作',
  message: '确定要执行此操作吗？',
  type: 'warning',
  confirmText: '确定',
  showInput: false,
  inputPlaceholder: '请输入备注信息',
})

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'confirm', value?: string): void
  (e: 'cancel'): void
}>()

const inputValue = ref('')
const loading = ref(false)

function handleConfirm() {
  loading.value = true
  setTimeout(() => {
    emit('confirm', props.showInput ? inputValue.value : undefined)
    inputValue.value = ''
    loading.value = false
    emit('update:visible', false)
  }, 300)
}

function handleCancel() {
  inputValue.value = ''
  emit('cancel')
  emit('update:visible', false)
}
</script>

<style lang="scss" scoped>
.confirm-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;

  .confirm-icon {
    margin-bottom: 16px;
  }

  .confirm-message {
    font-size: 14px;
    color: #606266;
    margin-bottom: 16px;
    line-height: 1.6;
  }

  .confirm-input {
    width: 100%;
    padding: 12px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    font-size: 14px;
    resize: vertical;
  }
}
</style>