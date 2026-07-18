import { reactive } from 'vue'
import { fileApi } from '@/api/modules/file'
import { ElMessage } from 'element-plus'
import type { FileInfo } from '@/types/common'

/**
 * 文件上传 Hook — 上传后获取 fileId + SHA-256
 */
export function useFileUpload() {
  const fileInfo = reactive<{
    fileId: string
    sha256: string
    url: string
    uploading: boolean
  }>({
    fileId: '',
    sha256: '',
    url: '',
    uploading: false,
  })

  async function upload(file: File, bizRef?: string): Promise<FileInfo | null> {
    fileInfo.uploading = true
    try {
      const result = await fileApi.upload(file, bizRef)
      fileInfo.fileId = result.fileId
      fileInfo.sha256 = result.sha256
      fileInfo.url = result.url
      ElMessage.success('文件上传成功')
      return result
    } catch (error) {
      ElMessage.error('文件上传失败')
      return null
    } finally {
      fileInfo.uploading = false
    }
  }

  function reset() {
    fileInfo.fileId = ''
    fileInfo.sha256 = ''
    fileInfo.url = ''
    fileInfo.uploading = false
  }

  return {
    fileInfo,
    upload,
    reset,
  }
}
