import request from '@/utils/request'
import type { FileInfo } from '@/types/common'

export const fileApi = {
  /**
   * 上传文件
   * @returns { fileId, sha256, url }
   */
  upload(file: File, bizRef?: string): Promise<FileInfo> {
    const formData = new FormData()
    formData.append('file', file)
    if (bizRef) formData.append('bizRef', bizRef)
    return request.post('/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /**
   * 删除文件
   */
  delete(fileId: string) {
    return request.delete(`/file/${fileId}`)
  },

  /**
   * 获取文件预览URL
   */
  getPreviewUrl(fileId: string): string {
    return `/api/v1/file/${fileId}`
  },

  /**
   * 获取缩略图URL
   */
  getThumbnailUrl(fileId: string): string {
    return `/api/v1/file/${fileId}/thumbnail`
  },
}
