<template>
  <div class="apply-list">
    <!-- 状态筛选标签页 -->
    <ApplyFilter
      v-model="currentStatus"
      :counts="statusCounts"
    />

    <!-- 数据表格 -->
    <ApplyTable
      :data="tableData"
      :loading="tableLoading"
      @approve="handleApprove"
      @view="handleView"
      @batch-approve="handleBatchApprove"
    />

    <!-- 分页：total 是「组数」不是「头数」——列表按批聚合，一头一行会让翻页错位 -->
    <div v-if="pagination.total > 0" class="pagination-wrapper">
      <span class="group-total">共 {{ pagination.total }} 组（按批聚合，点行首箭头看逐头）</span>
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 审批弹窗（逐头） -->
    <ApproveDialog
      v-model:visible="approveDialogVisible"
      :apply-data="currentApplyData"
      @submit="handleApproveSubmit"
    />

    <!-- 批量审批弹窗（整批通过或整批驳回，二选一） -->
    <BatchApproveDialog
      v-model:visible="batchDialogVisible"
      :group="batchGroup"
      @submit="handleBatchSubmit"
    />

    <!-- 查看详情弹窗 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="申报详情"
      width="720px"
      @close="handleViewClose"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申报编号">{{ viewData.applyNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生猪耳标号">{{ viewData.earTagNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申报时间">{{ viewData.applyTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="体重">{{ viewData.weightKg ? `${viewData.weightKg} kg` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标屠宰场" :span="2">{{ viewData.targetSlaughterhouse || '-' }}</el-descriptions-item>
        <el-descriptions-item label="采集设备">{{ deviceText(viewData) }}</el-descriptions-item>
        <el-descriptions-item label="审批人">
          {{ viewData.approvalStatus === 0 ? '-' : viewData.approver || '未记录' }}
        </el-descriptions-item>
        <el-descriptions-item label="审批状态" :span="2">
          <el-tag :type="statusTagType(viewData.approvalStatus)">
            {{ statusLabel(viewData.approvalStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="viewData.rejectReason" label="驳回原因" :span="2">
          {{ viewData.rejectReason }}
        </el-descriptions-item>
        <el-descriptions-item v-if="viewData.approvalStatus === 2" label="下一步" :span="2">
          <div class="reject-next-hint">
            驳回为终态，本单不再变更。请针对驳回原因整改（如补齐免疫档案、核实耳标与数量）；
            整改完成后生猪重新出栏过磅，重新申报将进入正常审批。
          </div>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 疫苗凭证照片 -->
      <el-divider content-position="left">疫苗凭证照片</el-divider>
      <div v-loading="vaccineLoading" class="vaccine-photos">
        <template v-if="vaccineGroups.length > 0">
          <div v-for="group in vaccineGroups" :key="group.id" class="vaccine-group">
            <div class="vaccine-info">
              <span class="vaccine-name">{{ group.vaccineName || '-' }}</span>
              <span class="vaccine-meta">
                {{ group.batchNo ? `批次: ${group.batchNo}` : '' }}
                {{ group.injectTime ? ` | 注射时间: ${group.injectTime}` : '' }}
                {{ group.operator ? ` | 操作人: ${group.operator}` : '' }}
              </span>
            </div>
            <div v-if="group.photos.length > 0" class="photo-list">
              <el-image
                v-for="(photo, idx) in group.photos"
                :key="idx"
                :src="photo.url"
                :preview-src-list="group.allPhotos"
                :initial-index="idx"
                fit="cover"
                class="vaccine-photo"
                preview-teleported
              >
                <template #placeholder>
                  <div class="photo-placeholder">加载中...</div>
                </template>
                <template #error>
                  <div class="photo-placeholder">加载失败</div>
                </template>
              </el-image>
            </div>
            <div v-else class="no-photo">该记录无凭证照片</div>
          </div>
        </template>
        <el-empty v-else description="暂无疫苗凭证照片" :image-size="80" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { recordGroupApi } from '@/api/modules/breeding'
import { deviceText } from '@/api/modules/ingest'
import ApplyFilter from './ApplyFilter.vue'
import ApplyTable from './ApplyTable.vue'
import ApproveDialog from './ApproveDialog.vue'
import BatchApproveDialog from './BatchApproveDialog.vue'

// ==================== 状态筛选 ====================

const currentStatus = ref(0)
const statusCounts = ref({
  0: 0,
  1: 0,
  2: 0
})

// ==================== 表格相关 ====================

const tableData = ref([])
const tableLoading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 获取申报列表（按批次分组：一次出栏申报一行，展开看逐头）
//
// 数据库里申报是逐头一条（一条记录挂一头猪），现实里一批猪一起报检出栏。
// 列表按「批次 + 申报日期」聚合成主行；审批粒度仍是逐条，见 handleApprove。
const fetchList = async () => {
  tableLoading.value = true

  try {
    const res = await recordGroupApi.applies({
      approvalStatus: currentStatus.value,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    })
    const list = res?.records || res?.list || []
    tableData.value = Array.isArray(list) ? list : []
    pagination.total = res?.total || 0
  } catch (error) {
    console.error('获取申报列表失败:', error)
    tableData.value = []
    pagination.total = 0
  } finally {
    tableLoading.value = false
  }
}

// 获取各审批状态数量（tab 角标）
const fetchCounts = async () => {
  try {
    const data = await request.get('/breeding/applies/counts')
    // 后端 Map 经 JSON 序列化后 key 为字符串 "0"/"1"/"2"
    statusCounts.value = {
      0: Number(data?.[0] ?? data?.['0'] ?? 0),
      1: Number(data?.[1] ?? data?.['1'] ?? 0),
      2: Number(data?.[2] ?? data?.['2'] ?? 0)
    }
  } catch (error) {
    console.error('获取审批状态数量失败:', error)
  }
}

// ==================== 状态切换 ====================

watch(currentStatus, () => {
  pagination.pageNum = 1
  fetchList()
})

// ==================== 分页 ====================

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  fetchList()
}

const handlePageChange = (page) => {
  pagination.pageNum = page
  fetchList()
}

// ==================== 审批 ====================

const approveDialogVisible = ref(false)
const currentApplyData = ref({})

const handleApprove = (row) => {
  currentApplyData.value = {
    ...row,
    earTagNo: row.earTagNo || row.pigId
  }
  approveDialogVisible.value = true
}

const handleApproveSubmit = async (approveData) => {
  try {
    // 后端 DTO 契约：approved(Boolean) + comment(String)，不是 approvalStatus/remark
    await request.put(`/breeding/applies/${approveData.id}/approve`, {
      approved: approveData.approvalStatus === 1,
      comment: approveData.remark
    })
    ElMessage.success(approveData.approvalStatus === 1 ? '审批通过' : '已驳回')
    // 接口成功后再关闭弹窗；失败时弹窗保持打开（错误提示由 request 拦截器统一弹出）
    approveDialogVisible.value = false
    // 审批改变了状态分布，列表与角标计数都要刷新
    fetchList()
    fetchCounts()
  } catch (error) {
    console.error('审批失败:', error)
  }
}

// ==================== 批量审批 ====================

/**
 * 批量审批 = 把这一组里待审的申报逐条提交，整批通过或整批驳回。
 *
 * 为什么是「逐条串行」而不是一个新加的批量接口：
 * 后端审批就是单条接口（一条申报一次审批、一条上链），聚合展示不该顺手改审批粒度——
 * 那样「批了哪些头」就说不清了。这里逐条调、按条反馈，失败的单独列出来。
 * 单条失败不影响其他条（每条自己的事务）。
 *
 * 驳回必须写理由：驳回是终态，理由就是养殖端整改的依据；没有理由的驳回等于让人猜。
 */
const batchDialogVisible = ref(false)
const batchGroup = ref({})

const handleBatchApprove = (group) => {
  const pending = (group.members || []).filter((m) => m.approvalStatus === 0)
  if (pending.length === 0) return
  batchGroup.value = group
  batchDialogVisible.value = true
}

const handleBatchSubmit = async ({ approvalStatus, remark }) => {
  const pending = (batchGroup.value.members || []).filter((m) => m.approvalStatus === 0)
  if (pending.length === 0) return

  const approved = approvalStatus === 1
  const comment = remark || (approved ? '按批通过（同一批同日申报）' : '')
  let ok = 0
  const failed = []
  for (const m of pending) {
    try {
      await request.put(`/breeding/applies/${m.id}/approve`, { approved, comment })
      ok++
    } catch {
      failed.push(m.earTagNo || `#${m.id}`)
    }
  }

  if (failed.length === 0) {
    ElMessage.success(`已${approved ? '通过' : '驳回'} ${ok} 头`)
  } else {
    ElMessage.warning(
      `${approved ? '通过' : '驳回'} ${ok} 头，失败 ${failed.length} 头：${failed.slice(0, 3).join('、')}`
        + `${failed.length > 3 ? ' 等' : ''}`,
    )
  }
  batchDialogVisible.value = false
  fetchList()
  fetchCounts()
}

// ==================== 查看详情 ====================

const viewDialogVisible = ref(false)
const viewData = ref({})

// 疫苗凭证照片：按疫苗记录分组展示
const vaccineLoading = ref(false)
const vaccineGroups = ref([])
// blob URL 管理：图片接口需鉴权，需以 blob 方式拉取后生成临时地址，关闭弹窗时统一释放
const vaccineBlobUrls = ref([])
// 加载序号：防止快速切换不同申报时慢请求晚返回导致照片串台
let vaccineLoadSeq = 0

const revokeVaccineBlobUrls = () => {
  vaccineBlobUrls.value.forEach((url) => URL.revokeObjectURL(url))
  vaccineBlobUrls.value = []
}

const loadVaccinePhotos = async (pigId) => {
  if (!pigId) {
    vaccineGroups.value = []
    return
  }
  const seq = ++vaccineLoadSeq
  vaccineLoading.value = true
  revokeVaccineBlobUrls()
  try {
    const list = await request.get(`/breeding/pigs/${pigId}/vaccines/list`)
    if (seq !== vaccineLoadSeq) return
    const records = Array.isArray(list) ? list : []
    // 对每条疫苗记录拉取其凭证照片
    const groups = await Promise.all(records.map(async (r) => {
      const fileIds = Array.isArray(r.fileIds) ? r.fileIds : []
      const photos = await Promise.all(fileIds.map(async (fid) => {
        try {
          const blob = await request.get(`/file/${fid}`, { responseType: 'blob' })
          const url = URL.createObjectURL(blob)
          vaccineBlobUrls.value.push(url)
          return { url }
        } catch (e) {
          return { url: '' }
        }
      }))
      const validPhotos = photos.filter((p) => p.url)
      return {
        id: r.id,
        vaccineName: r.vaccineName,
        batchNo: r.batchNo,
        injectTime: r.injectTime,
        operator: r.operator,
        photos: validPhotos,
        allPhotos: validPhotos.map((p) => p.url)
      }
    }))
    if (seq !== vaccineLoadSeq) return
    vaccineGroups.value = groups
  } catch (error) {
    if (seq !== vaccineLoadSeq) return
    console.error('加载疫苗凭证失败:', error)
    vaccineGroups.value = []
  } finally {
    if (seq === vaccineLoadSeq) vaccineLoading.value = false
  }
}

const handleView = (row) => {
  viewData.value = {
    ...row,
    earTagNo: row.earTagNo || row.pigId
  }
  viewDialogVisible.value = true
  loadVaccinePhotos(row.pigId)
}

const handleViewClose = () => {
  // 让在途的图片请求失效，避免关闭后晚返回的结果写入状态
  vaccineLoadSeq++
  revokeVaccineBlobUrls()
  vaccineGroups.value = []
  vaccineLoading.value = false
}

const statusLabel = (status) => {
  const map = {
    0: '待审批',
    1: '已通过',
    2: '已驳回'
  }
  return map[status] || '未知'
}

const statusTagType = (status) => {
  const map = {
    0: 'warning',
    1: 'success',
    2: 'danger'
  }
  return map[status] || 'info'
}

// ==================== 初始化 ====================

onMounted(() => {
  fetchList()
  fetchCounts()
})
</script>

<style lang="scss" scoped>
.apply-list {
  padding: 20px;
  background-color: #fff;
  border-radius: 4px;

  .pagination-wrapper {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;

    .group-total {
      font-size: 13px;
      color: #909399;
    }
  }

  .reject-next-hint {
    padding: 8px 12px;
    font-size: 12px;
    line-height: 1.6;
    color: #606266;
    background-color: #f4f4f5;
    border-radius: 4px;
  }

  .vaccine-photos {
    min-height: 80px;
    .vaccine-group {
      padding: 10px 0;
      border-bottom: 1px dashed #ebeef5;

      &:last-child {
        border-bottom: none;
      }

      .vaccine-info {
        margin-bottom: 8px;
        font-size: 14px;

        .vaccine-name {
          font-weight: 600;
          color: #303133;
          margin-right: 12px;
        }

        .vaccine-meta {
          color: #909399;
          font-size: 12px;
        }
      }

      .photo-list {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
      }

      .vaccine-photo {
        width: 100px;
        height: 100px;
        border-radius: 4px;
        border: 1px solid #ebeef5;
      }

      .photo-placeholder {
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #909399;
        font-size: 12px;
      }

      .no-photo {
        color: #c0c4cc;
        font-size: 12px;
      }
    }
  }
}
</style>
