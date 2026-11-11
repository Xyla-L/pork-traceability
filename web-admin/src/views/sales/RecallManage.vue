<template>
  <div class="page-container">
    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button type="danger" @click="handleCreate"><el-icon><WarningFilled /></el-icon>发起召回</el-button>
    </div>

    <!-- 召回列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="recallNo" label="召回编号" width="180">
        <template #default="{ row }">{{ row.recallNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="召回原因" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '-' }}</template>
      </el-table-column>
      <el-table-column prop="riskLevel" label="风险等级" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="initiator" label="发起人" width="100" align="center">
        <template #default="{ row }">{{ row.initiator || '-' }}</template>
      </el-table-column>
      <el-table-column prop="initiateTime" label="发起时间" width="160" align="center">
        <template #default="{ row }">{{ row.initiateTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="执行状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="affectedCount" label="受影响数量" width="110" align="center">
        <template #default="{ row }">{{ row.affectedCount != null ? row.affectedCount : '-' }}</template>
      </el-table-column>
      <el-table-column label="召回进度" min-width="200">
        <template #default="{ row }">
          <div class="progress-cell">
            <el-progress :percentage="row.recalledCount && row.affectedCount ? Math.round(row.recalledCount / row.affectedCount * 100) : 0"
              :status="row.status === 3 ? 'success' : row.status === 4 ? 'exception' : undefined"
              :stroke-width="14" :text-inside="true" />
            <span class="progress-text">{{ row.recalledCount || 0 }} / {{ row.affectedCount || 0 }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <!-- 发起召回弹窗 -->
    <el-dialog v-model="dialogVisible" title="发起产品召回" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="recallForm" :rules="rules" label-width="90px">
        <el-form-item label="召回原因" prop="reason">
          <el-input v-model="recallForm.reason" type="textarea" :rows="2" placeholder="请输入召回原因" />
        </el-form-item>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-radio-group v-model="recallForm.riskLevel">
            <el-radio :value="1">一般</el-radio>
            <el-radio :value="2">严重</el-radio>
            <el-radio :value="3">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="召回范围" prop="batchIds">
          <el-select v-model="recallForm.batchIds" multiple placeholder="选择被举报的批次" style="width: 100%"
            no-data-text="没有可召回的举报批次" @change="handleBatchChange">
            <el-option v-for="b in batchOptions" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
          <!-- 范围不是自由勾选：能召回的只能是举报台账里出现过的批次 -->
          <div class="scope-hint">
            只列被举报过的批次。
            <template v-if="missingReportedBatches.length">
              另有 {{ missingReportedBatches.length }} 个被举报批次查不到分割记录（{{ missingReportedBatches.slice(0, 3).join('、') }}{{ missingReportedBatches.length > 3 ? ' 等' : '' }}），未列入可选范围，请先核对该批次是否已入库。
            </template>
          </div>
        </el-form-item>
        <el-form-item label="目标门店">
          <div v-loading="previewLoading" class="store-preview">
            <template v-if="storePreview.length">
              <el-tag v-for="s in storePreview" :key="s.storeId" size="small" class="store-tag">
                {{ s.storeName || `门店#${s.storeId}` }} · {{ s.saleCount }} 件
              </el-tag>
              <div class="scope-hint">
                共 {{ storePreview.length }} 家门店、{{ affectedCount }} 件在售或已售出的货，按召回范围自动带出，不需要手选。
              </div>
            </template>
            <span v-else class="scope-hint">
              {{ recallForm.batchIds.length ? '所选批次没有在售/已售记录：没有门店需要召回' : '选择召回范围后自动带出门店' }}
            </span>
          </div>
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="recallForm.note" type="textarea" :rows="2" placeholder="补充说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="danger" @click="handleSubmitRecall" :loading="submitting">确认发起召回</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="召回详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="召回编号" :span="2">{{ detailData.recallNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="召回原因">{{ detailData.reason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskTagType(detailData.riskLevel)" size="small">{{ riskLabel(detailData.riskLevel) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发起人">{{ detailData.initiator || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发起时间">{{ detailData.initiateTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间" v-if="detailData.status === 3">{{ detailData.completedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="statusTagType(detailData.status)" size="small">{{ statusLabel(detailData.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="受影响数量" :span="2">{{ detailData.affectedCount != null ? detailData.affectedCount + ' 件' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="召回进度" :span="2">
          <el-progress :percentage="progressPct(detailData)" :status="detailData.status === 3 ? 'success' : detailData.status === 4 ? 'exception' : undefined" :stroke-width="14" />
          <div class="progress-text">{{ detailData.recalledCount || 0 }} / {{ detailData.affectedCount || 0 }} 件</div>
        </el-descriptions-item>
        <el-descriptions-item label="召回范围" :span="2">
          <div v-if="detailData.batchNames && detailData.batchNames.length" class="scope-tags">
            <el-tag v-for="b in detailData.batchNames" :key="b" size="small" style="margin-right: 4px; margin-bottom: 4px;">{{ b }}</el-tag>
          </div>
          <span v-else>{{ detailData.batchIds?.length ? `共 ${detailData.batchIds.length} 个批次` : '--' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="目标门店" :span="2">
          <div v-if="detailData.storeNames && detailData.storeNames.length" class="scope-tags">
            <el-tag v-for="s in detailData.storeNames" :key="s" size="small" style="margin-right: 4px; margin-bottom: 4px;">{{ s }}</el-tag>
          </div>
          <span v-else>{{ detailData.storeIds?.length ? `共 ${detailData.storeIds.length} 个门店` : '--' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注说明" :span="2">{{ detailData.note || '--' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { EpTagType } from '@/types/common'
import { salesApi } from '@/api/modules/sales'
import { distributionApi } from '@/api/modules/distribution'
import { traceApi } from '@/api/modules/trace'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const riskTagType = (r: number): EpTagType => (({ 1: 'info', 2: 'warning', 3: 'danger' } as Record<number, EpTagType>)[r] || 'info')
const riskLabel = (r: number) => ({ 1: '一般', 2: '严重', 3: '紧急' } as Record<number, string>)[r] || ''
const statusTagType = (s: number): EpTagType => (({ 1: 'info', 2: 'warning', 3: 'success', 4: 'danger' } as Record<number, EpTagType>)[s] || 'info')
const statusLabel = (s: number) => ({ 1: '已发布', 2: '执行中', 3: '已完成', 4: '已撤销' } as Record<number, string>)[s] || ''

// 发起召回表单
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const recallForm = reactive({ reason: '', riskLevel: 2, batchIds: [] as number[], storeIds: [] as number[], note: '' })
const rules = { reason: [{ required: true, message: '请输入召回原因', trigger: 'blur' }],
  batchIds: [{ required: true, message: '请选择被举报的批次', trigger: 'change' }] }
const batchOptions = ref<{ label: string; value: number; batchNo: string }[]>([])
/** 举报过、但在分割记录里查不到的批次号：不兜底成当天任意批次，直接报出来让人核 */
const missingReportedBatches = ref<string[]>([])
/** 按召回范围自动带出的门店（只读）：页面不给手选，货卖到哪几家是既成事实 */
const storePreview = ref<{ storeId: number; storeName: string; saleCount: number }[]>([])
const previewLoading = ref(false)
const affectedCount = ref(0)

/**
 * 召回范围只能来自举报台账。
 * 思路是「先看举报、再对批次」：拿到被举报的批次号，再去分割批次里找对应记录。
 * 反过来（列出全部批次让人挑）等于允许无依据召回 —— 报没报过案都能召回，就不叫召回了。
 *
 * 两个「不许偷懒」的地方：
 * ① 举报台账可能不止一页（列表接口 pageSize 上限 200），按页取完；
 * ② 分割批次不能拉一页再本地过筛 —— 那只会筛到前 200 个批次，
 *    剩下的批次全被判成「查不到分割记录」。按批次号逐个精确查才对得上。
 */
async function fetchBatchOptions() {
  try {
    const counts = new Map<string, number>()
    for (let pageNum = 1; pageNum <= 5; pageNum++) {
      const res: any = await traceApi.getComplaints({ pageNum, pageSize: 200 })
      const rows = res?.records || res?.list || []
      rows.forEach((c: any) => {
        const no = String(c?.targetBatch || '').trim()
        if (no) counts.set(no, (counts.get(no) || 0) + 1)
      })
      if (rows.length < 200 || pageNum * 200 >= Number(res?.total || 0)) break
    }
    const batchNos = [...counts.keys()]
    const matched = await Promise.all(batchNos.map(async (no) => {
      try {
        const res: any = await distributionApi.getSplits({ keyword: no, pageNum: 1, pageSize: 20 })
        const rows = res?.records || res?.list || []
        // keyword 是模糊匹配：必须认批次号完全相同的那一条，近似的不要
        return { no, split: rows.find((b: any) => String(b?.batchNo || '').trim() === no) || null }
      } catch {
        return { no, split: null }
      }
    }))
    batchOptions.value = matched
      .filter((m) => m.split)
      .map(({ no, split }) => ({
        label: `${no} · ${split.productName || '未命名'} · 被举报 ${counts.get(no)} 次`,
        value: split.id as number,
        batchNo: no,
      }))
    missingReportedBatches.value = matched.filter((m) => !m.split).map((m) => m.no)
  } catch {
    batchOptions.value = []
    missingReportedBatches.value = []
  }
}

// 详情弹窗
const detailVisible = ref(false)
const detailData = ref<any>({})
const progressPct = (row: any) => row.affectedCount ? Math.round(row.recalledCount / row.affectedCount * 100) : 0

function handleCreate() {
  recallForm.batchIds = []
  recallForm.storeIds = []
  recallForm.note = ''
  storePreview.value = []
  affectedCount.value = 0
  fetchBatchOptions()
  dialogVisible.value = true
}

/**
 * 选了范围就把门店带出来。
 * 口径走销售服务的「受影响门店」查询（与发起召回时的受影响件数同一份 SQL），
 * 前端不再自己拼运单+签收单 —— 那会拼出「弹窗 3 家店、召回后 4 家」。
 */
async function handleBatchChange() {
  recallForm.storeIds = []
  storePreview.value = []
  affectedCount.value = 0
  if (!recallForm.batchIds.length) return
  previewLoading.value = true
  try {
    const res: any = await salesApi.getRecallAffectedStores(recallForm.batchIds)
    const data = res?.data || res || {}
    storePreview.value = Array.isArray(data.stores) ? data.stores : []
    affectedCount.value = Number(data.affectedCount || 0)
    recallForm.storeIds = storePreview.value.map((s) => s.storeId)
  } catch {
    storePreview.value = []
    recallForm.storeIds = []
  } finally { previewLoading.value = false }
}

async function handleSubmitRecall() {
  await formRef.value?.validate()
  if (!recallForm.storeIds.length) {
    ElMessage.warning('所选批次没有在售或已售出的货，没有门店需要召回')
    return
  }
  submitting.value = true
  try {
    await salesApi.createRecall({
      reason: recallForm.reason, riskLevel: recallForm.riskLevel,
      // 后端 RecallCreate 要求 scope(Map) 与 initiator 必填，batchIds/storeIds 需放入 scope；
      // batchNos 只为详情展示留痕（批次改名也认得出当初召的是哪批）
      scope: {
        batchIds: recallForm.batchIds,
        batchNos: batchOptions.value.filter((b) => recallForm.batchIds.includes(b.value)).map((b) => b.batchNo),
        storeIds: recallForm.storeIds,
        // 门店名一起留痕：召回单是历史凭证，门店后来改名也要看得出当初召的是哪几家
        storeNames: storePreview.value.map((s) => s.storeName || `门店#${s.storeId}`),
        note: recallForm.note,
      },
      initiator: authStore.user?.realName || authStore.user?.username || '管理员',
    })
    ElMessage.success('召回指令已发布，数据已上链')
    dialogVisible.value = false
    fetchList()
  } catch { /* 错误已由拦截器提示 */ } finally { submitting.value = false }
}

async function handleView(row: any) {
  detailData.value = { ...row }
  // 解析 scope 中的召回范围和目标门店
  try {
    const scope = typeof row.scope === 'string' ? JSON.parse(row.scope) : (row.scope || {})
    // 召回范围：优先用发起时留痕的批次号；老单据没有 batchNos 才退回按 id 找
    const batchNames: string[] = []
    if (Array.isArray(scope.batchNos) && scope.batchNos.length) {
      batchNames.push(...scope.batchNos)
    } else if (scope.batchNo) {
      batchNames.push(scope.batchNo)
    } else if (scope.batchIds?.length) {
      if (!batchOptions.value.length) await fetchBatchOptions()
      scope.batchIds.forEach((bid: number) => {
        const match = batchOptions.value.find(b => b.value === bid)
        if (match) batchNames.push(match.batchNo)
      })
      if (!batchNames.length) batchNames.push(`批次#${scope.batchIds.join(', #')}`)
    }
    detailData.value.batchNames = batchNames
    // 目标门店：同样是发起时留痕的门店名
    const storeNames: string[] = Array.isArray(scope.storeNames) ? [...scope.storeNames] : []
    if (!storeNames.length && scope.storeIds?.length) {
      scope.storeIds.forEach((sid: number) => storeNames.push(`门店#${sid}`))
    }
    detailData.value.storeNames = storeNames
  } catch {
    // scope 解析失败时不影响基本展示
  }
  detailVisible.value = true
}

function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

async function fetchList() {
  loading.value = true
  try {
    const res: any = await salesApi.getRecalls({ current: pagination.pageNum, size: pagination.pageSize })
    tableData.value = res?.records || res?.list || []
    pagination.total = res?.total || tableData.value.length || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { loading.value = false }
}

onMounted(() => fetchList())
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.action-bar { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.progress-cell { display: flex; align-items: center; gap: 10px; }
.progress-text { font-size: 12px; color: #909399; white-space: nowrap; flex-shrink: 0; }
.scope-tags { display: flex; flex-wrap: wrap; }
/* 目标门店只读：标签 + 一行说明，不给下拉框（货卖到哪几家不是人选出来的） */
.store-preview { width: 100%; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; min-height: 24px; }
.store-tag { margin: 0; }
.scope-hint { width: 100%; font-size: 12px; color: #909399; line-height: 1.6; }
</style>
