<template>
  <div class="vaccine-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="earTagNo" label="关联生猪" min-width="140" show-overflow-tooltip />
      <el-table-column prop="vaccineName" label="疫苗名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="疫苗批次号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="injectTime" label="注射时间" min-width="160" align="center" />
      <el-table-column prop="dosage" label="剂量" min-width="100" align="center" />
      <el-table-column prop="operator" label="操作人" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="$emit('edit', row)">
            编辑
          </el-button>
          <el-button type="primary" link size="small" @click="handleViewCert(row)">
            查看凭证
          </el-button>
          <el-popconfirm
            title="确定删除该疫苗记录吗？"
            confirm-button-text="确定"
            cancel-button-text="取消"
            @confirm="$emit('delete', row)"
          >
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 凭证预览弹窗 -->
    <el-dialog
      v-model="certVisible"
      title="疫苗注射凭证"
      width="500px"
      @close="handleCertClose"
    >
      <div v-loading="certLoading" class="cert-preview">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="生猪耳标号">{{ currentCert.earTagNo }}</el-descriptions-item>
          <el-descriptions-item label="疫苗名称">{{ currentCert.vaccineName }}</el-descriptions-item>
          <el-descriptions-item label="疫苗批次号">{{ currentCert.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="注射时间">{{ currentCert.injectTime }}</el-descriptions-item>
          <el-descriptions-item label="剂量">{{ currentCert.dosage }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ currentCert.operator }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="photoUrls.length > 0" class="cert-photo">
          <p class="photo-label">凭证照片：</p>
          <el-image
            v-for="(url, index) in photoUrls"
            :key="index"
            :src="url"
            :preview-src-list="photoUrls"
            :initial-index="index"
            fit="contain"
            class="cert-photo__img"
          />
        </div>
        <div v-else-if="!certLoading && certLoadError" class="no-photo">
          凭证照片加载失败
        </div>
        <div v-else-if="!certLoading" class="no-photo">
          暂无凭证照片
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import request from '@/utils/request'

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

defineEmits(['delete', 'edit'])

const certVisible = ref(false)
const certLoading = ref(false)
const certLoadError = ref(false)
// 图片以 blob 方式拉取（文件预览接口需登录鉴权，<img src> 无法带 Authorization 头）
const photoUrls = ref([])
const currentCert = reactive({
  earTagNo: '',
  vaccineName: '',
  batchNo: '',
  injectTime: '',
  dosage: '',
  operator: ''
})

/** 释放上一次生成的 blob 临时地址，避免内存泄漏 */
const revokePhotoUrls = () => {
  photoUrls.value.forEach((url) => URL.revokeObjectURL(url))
  photoUrls.value = []
}

const handleViewCert = async (row) => {
  Object.assign(currentCert, {
    earTagNo: row.earTagNo || '',
    vaccineName: row.vaccineName || '',
    batchNo: row.batchNo || '',
    injectTime: row.injectTime || '',
    dosage: row.dosage || '',
    operator: row.operator || ''
  })
  revokePhotoUrls()
  certLoadError.value = false
  certVisible.value = true

  // 后端返回的是文件ID列表，真正的图片需请求 GET /file/{fileId}
  const fileIds = Array.isArray(row.fileIds) ? row.fileIds : []
  if (fileIds.length === 0) return

  certLoading.value = true
  try {
    const blobs = await Promise.all(
      fileIds.map((fileId) =>
        request.get(`/file/${fileId}`, { responseType: 'blob' })
      )
    )
    photoUrls.value = blobs.map((blob) => URL.createObjectURL(blob))
  } catch (error) {
    console.error('凭证照片加载失败:', error)
    certLoadError.value = true
  } finally {
    certLoading.value = false
  }
}

const handleCertClose = () => {
  revokePhotoUrls()
  certLoadError.value = false
  certLoading.value = false
}
</script>

<style lang="scss" scoped>
.vaccine-table {
  :deep(.el-table) {
    th.el-table__cell {
      background-color: #f5f7fa;
      color: #606266;
      font-weight: 600;
    }
  }
}

.cert-preview {
  .cert-photo {
    margin-top: 20px;

    .photo-label {
      margin-bottom: 10px;
      font-weight: 600;
      color: #606266;
    }

    // 尺寸约束必须加在内部 img 上：el-image 根节点自带 overflow:hidden，
    // 若只限制根节点高度，内部原图不缩放会被裁切，表现为照片只显示上半部分
    :deep(.el-image) {
      max-width: 100%;
      vertical-align: top;
    }

    :deep(.el-image__inner) {
      max-width: 100%;
      max-height: 60vh;
      width: auto;
      height: auto;
      object-fit: contain;
    }

    &__img {
      display: block;
      margin-bottom: 10px;
      border-radius: 4px;
    }
  }

  .no-photo {
    margin-top: 20px;
    text-align: center;
    color: #909399;
    padding: 30px 0;
    background-color: #f5f7fa;
    border-radius: 4px;
  }
}
</style>
