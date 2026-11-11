<template>
  <div class="vaccine-table">
    <!--
      主行 = 一次免疫动作（同一批、同一天、同一种疫苗），展开 = 这一次覆盖的逐头明细。
      聚合只是为了不再把「整批打一次疫苗」刷成 30 行耳标；哪一头打了、哪一头被作废，
      展开后一条不少 —— 追溯查的永远是单头，聚合不能把这层能力弄丢。
    -->
    <el-table
      v-loading="loading"
      :data="data"
      row-key="groupKey"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="member-panel">
            <div class="member-panel__title">本次免疫覆盖 {{ row.headCount }} 头</div>
            <el-table :data="row.members" size="small" border :row-class-name="memberRowClass">
              <el-table-column prop="earTagNo" label="耳标号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="injectTime" label="注射时间" min-width="160" align="center" />
              <el-table-column prop="dosage" label="剂量" width="100" align="center">
                <template #default="{ row: m }">{{ m.dosage || '--' }}</template>
              </el-table-column>
              <el-table-column label="采集设备" min-width="140">
                <template #default="{ row: m }">
                  <el-tooltip
                    :content="m.source === 'DEVICE' ? `设备采集：${m.sourceRef || '--'}` : sourceLabel(m.source)"
                    placement="top"
                  >
                    <el-tag :type="sourceTag(m.source)" size="small" effect="plain">{{ deviceText(m) }}</el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110" align="center">
                <template #default="{ row: m }">
                  <el-tooltip v-if="m.voidFlag === 1" placement="top">
                    <template #content>
                      <div>作废理由：{{ m.voidReason || '未填写' }}</div>
                      <div>作废操作人：{{ m.voidOperator || '未记录' }}</div>
                      <div>作废时间：{{ m.voidTime || '--' }}</div>
                    </template>
                    <el-tag type="info" size="small" effect="plain">已作废</el-tag>
                  </el-tooltip>
                  <span v-else class="status-normal">有效</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right" align="center">
                <template #default="{ row: m }">
                  <el-button type="primary" link size="small" @click="handleViewCert(m)">查看凭证</el-button>
                  <el-button v-if="m.voidFlag !== 1" type="danger" link size="small" @click="handleVoid(m)">
                    作废
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="批次" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.batchNo">
            <span class="batch-no">{{ row.batchNo }}</span>
            <span class="batch-count">{{ row.headCount }} 头</span>
          </template>
          <el-tooltip v-else content="这些猪尚未登记归属批次（如外购零星补栏），逐头独立成行" placement="top">
            <el-tag type="info" size="small" effect="plain">未归批</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="疫苗名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="疫苗批号" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.subTitle">{{ row.subTitle }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="注射日期" width="130" align="center">
        <template #default="{ row }">{{ row.happenedOn || '--' }}</template>
      </el-table-column>
      <el-table-column label="覆盖头数" width="100" align="center">
        <template #default="{ row }">
          <span class="head-count">{{ row.headCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="耳标区间" min-width="230" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.tagStart" class="tag-range">{{ tagRange(row) }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="操作人" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.operator || '未记录' }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="150" align="center">
        <template #default="{ row }">
          <!-- 标签只给结论（全部有效/部分有效/全部作废），计数放 tooltip：
               「有效 0 / 已作废 1」被当成结论读会误以为整批还有救，实际那次动作已全部作废 -->
          <el-tooltip :content="row.statusDetail || row.statusSummary" placement="top">
            <el-tag :type="statusTagType(row)" size="small" effect="plain">
              {{ row.statusSummary }}
            </el-tag>
          </el-tooltip>
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
          <el-descriptions-item label="操作人">{{ currentCert.operator || '未记录' }}</el-descriptions-item>
          <el-descriptions-item label="采集设备">{{ currentCert.deviceText }}</el-descriptions-item>
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
import { deviceText, sourceLabel, sourceTag } from '@/api/modules/ingest'
import { pigApi } from '@/api/modules/breeding'
import { confirmVoid } from '@/composables/useVoidAction'
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

const emit = defineEmits(['voided'])

/** 耳标区间：起止相同时直接显示单个耳标，避免「X ~ X」看着像有范围 */
const tagRange = (row) => {
  if (!row.tagStart) return '--'
  return row.tagStart === row.tagEnd ? row.tagStart : `${row.tagStart} ~ ${row.tagEnd}`
}

/** 明细里已作废的那几条置灰：仍在原位可追溯，但一眼能看出「这条不算了」 */
const memberRowClass = ({ row }) => (row.voidFlag === 1 ? 'row-voided' : '')

/** 状态标签配色：全有效=绿，部分有效=黄，全部作废=灰（记录仍在库，只是不再生效，不是报错） */
const statusTagType = (row) => {
  if (!row.voidCount) return 'success'
  return row.voidCount >= row.headCount ? 'info' : 'warning'
}

// 作废：免疫记录已上链，删掉记录会让链上哈希无法溯源，因此只允许作废（需填理由）
const handleVoid = async (member) => {
  const ok = await confirmVoid({
    title: '作废免疫记录',
    message: `确认作废 ${member.earTagNo || ''} 的 ${member.vaccineName || ''}`
      + `${member.batchNo ? '（批号 ' + member.batchNo + '）' : ''} 免疫记录吗？作废后该记录不再生效，但仍保留在库中并可追溯。`,
    onConfirm: (reason) => pigApi.voidVaccine(member.pigId, member.id, reason),
  })
  if (ok) emit('voided')
}

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
  operator: '',
  deviceText: ''
})

/** 释放上一次生成的 blob 临时地址，避免内存泄漏 */
const revokePhotoUrls = () => {
  photoUrls.value.forEach((url) => URL.revokeObjectURL(url))
  photoUrls.value = []
}

const handleViewCert = async (member) => {
  Object.assign(currentCert, {
    earTagNo: member.earTagNo || '',
    vaccineName: member.vaccineName || '',
    batchNo: member.batchNo || '',
    injectTime: member.injectTime || '',
    dosage: member.dosage || '',
    operator: member.operator || '',
    deviceText: deviceText(member)
  })
  revokePhotoUrls()
  certLoadError.value = false
  certVisible.value = true

  // 后端返回的是文件ID列表，真正的图片需请求 GET /file/{fileId}
  let fileIds = member.fileIds
  if (typeof fileIds === 'string') {
    try { fileIds = JSON.parse(fileIds) } catch { fileIds = [] }
  }
  if (!Array.isArray(fileIds)) fileIds = []
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

    // 已作废行置灰：仍列在原位（可追溯），但一眼能看出「这条不算了」
    .row-voided {
      --el-table-tr-bg-color: var(--el-fill-color-lighter);

      td.el-table__cell {
        color: #a8abb2;
      }
    }
  }

  .batch-no {
    font-weight: 600;
    color: #303133;
  }

  .batch-count {
    margin-left: 6px;
    font-size: 12px;
    color: #909399;
  }

  .head-count {
    font-weight: 600;
    color: #409eff;
  }

  .tag-range {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
    color: #606266;
  }

  .empty-cell {
    color: #c0c4cc;
  }

  .member-panel {
    padding: 8px 16px 12px 48px;
    background-color: #fafafa;

    &__title {
      margin-bottom: 8px;
      font-size: 13px;
      color: #606266;
      font-weight: 600;
    }

    &__hint {
      margin-left: 6px;
      font-weight: 400;
      color: #909399;
    }
  }
}

.status-normal {
  color: #909399;
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
