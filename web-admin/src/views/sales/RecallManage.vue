<template>
  <div class="page-container">
    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button type="danger" @click="handleCreate"><el-icon><WarningFilled /></el-icon>发起召回</el-button>
    </div>

    <!-- 召回列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="recallNo" label="召回编号" width="180" />
      <el-table-column prop="reason" label="召回原因" min-width="200" show-overflow-tooltip />
      <el-table-column prop="riskLevel" label="风险等级" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="initiator" label="发起人" width="100" align="center" />
      <el-table-column prop="initiateTime" label="发起时间" width="160" align="center" />
      <el-table-column prop="status" label="执行状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="affectedCount" label="受影响数量" width="110" align="center" />
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
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          <el-button v-if="row.status === 1 || row.status === 2" type="success" link size="small" @click="handleUpdateProgress(row)">
            更新进度
          </el-button>
          <el-button v-if="row.status === 1" type="warning" link size="small" @click="handleRevoke(row)">撤销</el-button>
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
          <el-select v-model="recallForm.batchIds" multiple placeholder="选择需召回的批次" style="width: 100%">
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

    <!-- 更新进度弹窗 -->
    <el-dialog v-model="progressVisible" title="更新召回进度" width="420px" destroy-on-close>
      <el-form :model="progressForm" label-width="90px">
        <el-form-item label="当前进度">
          <el-progress :percentage="progressForm.pct" :stroke-width="16" />
        </el-form-item>
        <el-form-item label="已召回数量" required>
          <el-input-number v-model="progressForm.recalled" :min="0" :max="progressForm.max" style="width: 200px" />
          <span class="progress-hint"> / {{ progressForm.max }}</span>
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
import { ref, reactive, onMounted } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { EpTagType } from '@/types/common'

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
const batchOptions = [
  { label: 'B20240715001 - 猪前腿肉', value: 1 }, { label: 'B20240714002 - 猪五花肉', value: 2 },
  { label: 'B20240713003 - 猪里脊', value: 3 }, { label: 'B20240712004 - 猪排骨', value: 4 },
]
const storeOptions = [
  { label: 'XX社区超市', value: 1 }, { label: 'YY生鲜店', value: 2 },
  { label: 'ZZ便利店', value: 3 }, { label: 'WW农贸市场', value: 4 },
]

// 进度更新
const progressVisible = ref(false)
const progressForm = reactive({ pct: 0, recalled: 0, max: 100, note: '', row: null as any })
const progressPct = (row: any) => row.affectedCount ? Math.round(row.recalledCount / row.affectedCount * 100) : 0

function handleCreate() { dialogVisible.value = true }

async function handleSubmitRecall() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    tableData.value.unshift({
      id: Date.now(), recallNo: `RC${Date.now()}`, reason: recallForm.reason,
      riskLevel: recallForm.riskLevel, initiator: '当前用户',
      initiateTime: new Date().toLocaleString(), status: 1,
      affectedCount: recallForm.batchIds.length * 50 + Math.floor(Math.random() * 100),
      recalledCount: 0, blockHash: `0x${Array.from({ length: 64 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join('')}`
    })
    ElMessage.success('召回指令已发布，数据已上链')
    dialogVisible.value = false
  } finally { submitting.value = false }
}

function handleUpdateProgress(row: any) {
  progressForm.row = row
  progressForm.max = row.affectedCount
  progressForm.recalled = row.recalledCount
  progressForm.pct = progressPct(row)
  progressForm.note = ''
  progressVisible.value = true
}

function handleSubmitProgress() {
  if (progressForm.row) {
    progressForm.row.recalledCount = progressForm.recalled
    if (progressForm.recalled >= progressForm.max) progressForm.row.status = 3
    else if (progressForm.row.status === 1) progressForm.row.status = 2
    ElMessage.success('召回进度已更新')
  }
  progressVisible.value = false
}

function handleRevoke(row: any) {
  ElMessageBox.confirm(`确认撤销召回 "${row.recallNo}" 吗？此操作不可逆`, '警告', { type: 'warning' }).then(() => {
    row.status = 4
    ElMessage.success('召回已撤销')
  }).catch(() => {})
}

function handleView(row: any) { ElMessage.info(`查看召回详情: ${row.recallNo}`) }

function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function fetchList() {
  loading.value = true
  const reasons = ['检测到瘦肉精残留超标', '产品包装破损', '冷链温度异常', '消费者投诉质量问题', '批次标签错误']
  const list = Array.from({ length: 18 }, (_, i) => ({
    id: i + 1, recallNo: `RC202407${String(1500 + i).padStart(4, '0')}`,
    reason: reasons[i % reasons.length], riskLevel: (i % 3 + 1) as 1 | 2 | 3,
    initiator: ['张监管', '李监管'][i % 2],
    initiateTime: `2024-07-${String(12 + i).padStart(2, '0')} ${String(10 + i % 8).padStart(2, '0')}:00:00`,
    status: i < 5 ? 1 : i < 10 ? 2 : i < 15 ? 3 : 4,
    affectedCount: (i + 1) * 50 + Math.floor(Math.random() * 50),
    recalledCount: i < 5 ? 0 : i < 10 ? Math.floor(Math.random() * 30) : i < 15 ? (i + 1) * 50 + Math.floor(Math.random() * 50) : 0,
    blockHash: `0x${Array.from({ length: 64 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join('')}`,
  }))
  tableData.value = list.slice((pagination.pageNum - 1) * pagination.pageSize, pagination.pageNum * pagination.pageSize)
  pagination.total = list.length
  loading.value = false
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
</style>
