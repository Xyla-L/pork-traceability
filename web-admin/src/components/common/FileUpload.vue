<template>
  <div class="file-upload">
    <el-upload
      :action="uploadUrl"
      :headers="headers"
      :file-list="fileList"
      :accept="accept"
      :multiple="multiple"
      :limit="limit"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :before-upload="beforeUpload"
      list-type="picture-card"
    >
      <el-icon :size="28"><Plus /></el-icon>
      <template #tip>
        <div class="upload-tip">
          <span>支持格式：{{ accept }}</span>
          <span v-if="limit">最多上传 {{ limit }} 个文件</span>
        </div>
      </template>
    </el-upload>

    <div v-if="fileList.length > 0" class="upload-info">
      <span class="info-label">SHA-256 校验：</span>
      <div class="hash-list">
        <span v-for="(file, index) in fileList" :key="index" class="hash-item">
          {{ file.name }}: {{ file.sha256 || '计算中...' }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const props = withDefaults(defineProps<{
  accept?: string
  multiple?: boolean
  limit?: number
}>(), {
  accept: 'image/jpeg,image/png,image/gif',
  multiple: false,
  limit: 9,
})

const emit = defineEmits<{
  (e: 'success', files: any[]): void
  (e: 'error', error: any): void
}>()

const authStore = useAuthStore()
const fileList = ref<any[]>([])

const uploadUrl = computed(() => `${import.meta.env.VITE_API_BASE || '/api/v1'}/file/upload`)

const headers = computed(() => ({
  Authorization: `Bearer ${authStore.token}`,
}))

async function calculateSHA256(file: File): Promise<string> {
  const buffer = await file.arrayBuffer()
  const hash = await crypto.subtle.digest('SHA-256', buffer)
  const hashArray = Array.from(new Uint8Array(hash))
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
}

async function beforeUpload(file: File) {
  const sha256 = await calculateSHA256(file)
  file.sha256 = sha256
}

function handleSuccess(response: any, file: any) {
  file.url = response.url
  file.sha256 = response.sha256 || file.sha256
  emit('success', fileList.value)
}

function handleError(error: any) {
  emit('error', error)
}

function handleRemove(file: any) {
  const index = fileList.value.indexOf(file)
  if (index > -1) {
    fileList.value.splice(index, 1)
  }
}

defineExpose({ fileList })
</script>

<style lang="scss" scoped>
.file-upload {
  .upload-tip {
    margin-top: 8px;
    font-size: 12px;
    color: #909399;

    span {
      margin-right: 16px;
    }
  }

  .upload-info {
    margin-top: 12px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;

    .info-label {
      font-size: 13px;
      font-weight: 500;
      color: #606266;
    }

    .hash-list {
      margin-top: 8px;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      .hash-item {
        font-size: 11px;
        color: #909399;
        font-family: monospace;
        background: #fff;
        padding: 4px 8px;
        border-radius: 4px;
        border: 1px solid #ebeef5;
      }
    }
  }
}
</style>