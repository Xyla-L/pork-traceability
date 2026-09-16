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
      <el-table-column label="操作" width="80" fixed="right" align="center">
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
          <el-select v-model="recallForm.batchIds" multiple placeholder="选择需召回的批次" style="width: 100%" @change="handleBatchChange">
            <el-option v-for="b in batchOptions" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标门店" prop="storeIds">
          <el-select v-model="recallForm.storeIds" multiple placeholder="选择目标门店" style="width: 100%">
            <el-option v-for="s in storeOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
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
        <el-button v-if="canUpdate" type="primary" @click="openProgressDialog">更新进度</el-button>
        <el-button v-if="canRevoke" type="warning" @click="handleRevoke">撤销召回</el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 更新进度弹窗 -->
    <el-dialog v-model="progressVisible" title="更新召回进度" width="460px" destroy-on-close>
      <el-form :model="progressForm" label-width="100px">
        <el-form-item label="当前进度">
          <el-progress :percentage="progressForm.pct" :stroke-width="16" />
        </el-form-item>
        <el-form-item label="已召回数量" required>
          <el-input-number v-model="progressForm.recalled" :min="0" :max="progressForm.max" style="width: 180px" />
          <span class="progress-hint"> / {{ progressForm.max }} 件</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="progressForm.note" placeholder="进度说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitProgress">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { EpTagType } from '@/types/common'
import { salesApi } from '@/api/modules/sales'
import { distributionApi } from '@/api/modules/distribution'

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
  batchIds: [{ required: true, message: '请选择批次', trigger: 'change' }],
  storeIds: [{ required: true, message: '请选择门店', trigger: 'change' }] }
const batchOptions = ref<{ label: string; value: number }[]>([])
const storeOptions = ref<{ label: string; value: number }[]>([])

async function fetchBatchOptions() {
  try {
    const res: any = await distributionApi.getSplits({ current: 1, size: 1000 })
    const records = res?.records || res?.list || []
    batchOptions.value = records.map((b: any) => ({
      label: `${b.batchNo} - ${b.productName || '未命名'}`,
      value: b.id,
    }))
  } catch { batchOptions.value = [] }
}

async function fetchStoreOptions() {
  try {
    const res: any = await distributionApi.getStores()
    const list = Array.isArray(res) ? res : (res?.records || [])
    storeOptions.value = list.map((s: any) => ({
      label: s.store_name || s.storeName || `门店#${s.store_id ?? s.storeId}`,
      value: s.store_id ?? s.storeId,
    }))
  } catch { storeOptions.value = [] }
}

// 详情弹窗
const detailVisible = ref(false)
const detailData = ref<any>({})
const canUpdate = computed(() => [1, 2].includes(detailData.value.status))
const canRevoke = computed(() => detailData.value.status === 1)

// 进度更新
const progressVisible = ref(false)
const progressForm = reactive({ pct: 0, recalled: 0, max: 100, note: '', row: null as any })
const progressPct = (row: any) => row.affectedCount ? Math.round(row.recalledCount / row.affectedCount * 100) : 0

function handleCreate() {
  fetchBatchOptions()
  fetchStoreOptions()
  dialogVisible.value = true
}

async function handleBatchChange() {
  if (!recallForm.batchIds.length) { recallForm.storeIds = []; return }
  try {
    // 1. 拉所有运单，按所选批次的 splitBatchId 过滤出受影响的 transportId
    const tRes: any = await distributionApi.getTransports({ current: 1, size: 1000 })
    const transports = tRes?.records || []
    const batchSet = new Set(recallForm.batchIds)
    const matchedTransportIds = transports
      .filter((t: any) => batchSet.has(t.splitBatchId))
      .map((t: any) => t.id)
    if (!matchedTransportIds.length) { recallForm.storeIds = []; return }
    // 2. 拉所有签收单，找出这些运单签收过的门店
    const rRes: any = await distributionApi.getReceipts({ current: 1, size: 1000 })
    const receipts = rRes?.records || []
    const transportIdSet = new Set(matchedTransportIds)
    const storeIds = new Set<number>()
    receipts.forEach((r: any) => {
      if (transportIdSet.has(r.transportId) && r.storeId != null) storeIds.add(r.storeId)
    })
    recallForm.storeIds = [...storeIds]
  } catch { /* 拉取失败时保留当前选择 */ }
}

async function handleSubmitRecall() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await salesApi.createRecall({
      reason: recallForm.reason, riskLevel: recallForm.riskLevel,
      batchIds: recallForm.batchIds, storeIds: recallForm.storeIds, note: recallForm.note,
    })
    ElMessage.success('召回指令已发布，数据已上链')
    dialogVisible.value = false
    fetchList()
  } catch { /* 错误已由拦截器提示 */ } finally { submitting.value = false }
}

function openProgressDialog() {
  progressForm.row = detailData.value
  progressForm.max = detailData.value.affectedCount
  progressForm.recalled = detailData.value.recalledCount
  progressForm.pct = progressPct(detailData.value)
  progressForm.note = ''
  progressVisible.value = true
}

async function handleSubmitProgress() {
  if (progressForm.row) {
    try {
      await salesApi.updateRecallStatus(progressForm.row.id, { status: 3 })
      ElMessage.success('召回进度已更新')
      progressVisible.value = false
      detailVisible.value = false
      fetchList()
    } catch { /* 错误已由拦截器提示 */ }
  }
}

async function handleRevoke() {
  ElMessageBox.confirm(`确认撤销召回 "${detailData.value.recallNo}" 吗？此操作不可逆`, '警告', { type: 'warning' }).then(async () => {
    try {
      await salesApi.updateRecallStatus(detailData.value.id, { status: 4 })
      ElMessage.success('召回已撤销')
      detailVisible.value = false
      fetchList()
    } catch { /* 错误已由拦截器提示 */ }
  }).catch(() => {})
}

function handleView(row: any) {
  detailData.value = row
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
.progress-hint { font-size: 14px; color: #909399; margin-left: 4px; }
.scope-tags { display: flex; flex-wrap: wrap; }
</style>
