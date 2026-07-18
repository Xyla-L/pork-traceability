<template>
  <div class="file-upload">
    <el-upload
      ref="uploadRef"
      :action="action"
      :accept="accept"
      :headers="uploadHeaders"
      :file-list="innerFileList"
      :limit="maxCount"
      :before-upload="handleBeforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :on-exceed="handleExceed"
      :on-preview="handlePreview"
      multiple
      drag
    >
      <el-icon class="file-upload__icon">
        <UploadFilled />
      </el-icon>
      <div class="el-upload__text">
        将文件拖到此处，或 <em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          <span v-if="accept">支持格式: {{ accept }}</span>
          <span v-if="maxSize" class="file-upload__size-tip">
            ，单文件不超过 {{ formatSize(maxSize) }}
          </span>
          <span v-if="maxCount">，最多 {{ maxCount }} 个文件</span>
        </div>
      </template>
    </el-upload>

    <!-- 上传成功后的文件信息展示 -->
    <div v-if="uploadedFiles.length > 0" class="file-upload__result-list">
      <div
        v-for="file in uploadedFiles"
        :key="file.uid"
        class="file-upload__result-item"
      >
        <div class="file-upload__result-info">
          <el-icon class="file-upload__result-icon">
            <Document />
          </el-icon>
          <span class="file-upload__result-name" :title="file.name">
            {{ file.name }}
          </span>
          <span class="file-upload__result-id">
            ID: {{ file.fileId || '--' }}
          </span>
          <el-tooltip v-if="file.sha256" :content="file.sha256" placement="top">
            <span class="file-upload__result-hash">
              SHA256: {{ truncateHash(file.sha256) }}
            </span>
          </el-tooltip>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Document } from '@element-plus/icons-vue'

const props = defineProps({
  /** 接受的文件类型 */
  accept: {
    type: String,
    default: ''
  },
  /** 最大文件数量 */
  maxCount: {
    type: Number,
    default: 5
  },
  /** 最大文件大小（字节），默认 10MB */
  maxSize: {
    type: Number,
    default: 10 * 1024 * 1024
  },
  /** 文件列表 */
  fileList: {
    type: Array,
    default: () => []
  },
  /** 上传接口地址 */
  action: {
    type: String,
    default: ''
  },
  /** 请求头 */
  headers: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['success', 'remove'])

const uploadRef = ref(null)
const innerFileList = ref([])
const uploadedFiles = ref([])

const uploadHeaders = { ...props.headers }

watch(
  () => props.fileList,
  (val) => {
    innerFileList.value = val.map((f) => ({
      ...f,
      uid: f.uid || Date.now() + Math.random()
    }))
  },
  { immediate: true, deep: true }
)

/** 格式化文件大小 */
const formatSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

/** 截断哈希显示 */
const truncateHash = (hash) => {
  if (!hash || hash.length <= 16) return hash
  return hash.substring(0, 8) + '...' + hash.substring(hash.length - 8)
}

/** 上传前校验 */
const handleBeforeUpload = (file) => {
  // 校验文件大小
  if (props.maxSize && file.size > props.maxSize) {
    ElMessage.error(`文件 ${file.name} 大小超过限制（${formatSize(props.maxSize)}）`)
    return false
  }

  // 校验文件类型
  if (props.accept) {
    const acceptTypes = props.accept.split(',').map((t) => t.trim().toLowerCase())
    const fileName = file.name.toLowerCase()
    const fileExt = '.' + fileName.split('.').pop()
    const fileType = file.type.toLowerCase()

    const isValid = acceptTypes.some((t) => {
      if (t.startsWith('.')) return fileExt === t
      if (t.endsWith('/*')) return fileType.startsWith(t.replace('/*', '/'))
      return fileType === t || fileExt === t
    })

    if (!isValid) {
      ElMessage.error(`文件 ${file.name} 格式不支持，仅支持: ${props.accept}`)
      return false
    }
  }

  return true
}

/** 上传成功 */
const handleSuccess = (response, uploadFile) => {
  const fileItem = {
    uid: uploadFile.uid,
    name: uploadFile.name,
    url: uploadFile.url || response?.url || response?.data?.url,
    fileId: response?.data?.fileId || response?.fileId || '',
    sha256: response?.data?.sha256 || response?.sha256 || ''
  }

  uploadedFiles.value.push(fileItem)
  ElMessage.success(`${uploadFile.name} 上传成功`)
  emit('success', fileItem)
}

/** 上传失败 */
const handleError = (error, uploadFile) => {
  ElMessage.error(`${uploadFile.name} 上传失败`)
}

/** 文件移除 */
const handleRemove = (uploadFile) => {
  const index = uploadedFiles.value.findIndex((f) => f.uid === uploadFile.uid)
  if (index > -1) {
    const removed = uploadedFiles.value.splice(index, 1)[0]
    emit('remove', removed)
  }
}

/** 超出数量限制 */
const handleExceed = () => {
  ElMessage.warning(`最多只能上传 ${props.maxCount} 个文件`)
}

/** 预览文件 */
const handlePreview = (uploadFile) => {
  if (uploadFile.url) {
    window.open(uploadFile.url, '_blank')
  }
}
</script>

<style lang="scss" scoped>
.file-upload {
  width: 100%;

  :deep(.el-upload-dragger) {
    padding: 30px;
  }

  &__icon {
    font-size: 48px;
    color: #c0c4cc;
    margin-bottom: 8px;
  }

  &__size-tip {
    color: #909399;
  }

  &__result-list {
    margin-top: 12px;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    overflow: hidden;
  }

  &__result-item {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    border-bottom: 1px solid #ebeef5;

    &:last-child {
      border-bottom: none;
    }
  }

  &__result-info {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    min-width: 0;
  }

  &__result-icon {
    color: #909399;
    font-size: 18px;
    flex-shrink: 0;
  }

  &__result-name {
    font-size: 13px;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 200px;
  }

  &__result-id {
    font-size: 12px;
    color: #67c23a;
    background: #f0f9eb;
    padding: 1px 6px;
    border-radius: 3px;
    flex-shrink: 0;
  }

  &__result-hash {
    font-size: 11px;
    color: #909399;
    font-family: 'Courier New', Courier, monospace;
    cursor: default;
    flex-shrink: 0;
  }
}
</style>