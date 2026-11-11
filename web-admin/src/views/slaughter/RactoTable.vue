<template>
  <div class="racto-table">
    <!--
      主行 = 一个批次的抽检（现实就是按批抽样，不是逐头全检），展开 = 逐份样本明细。
      阳性是要拦人的结论，所以它必须写在主行上，不能藏进展开面板。
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
            <div class="member-panel__title">{{ memberTitle(row) }}</div>
            <el-table :data="row.members" size="small" border>
              <el-table-column prop="testNo" label="检测编号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="sampleNo" label="样本编号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="earTagNo" label="耳标号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="testType" label="检测项目" min-width="120" align="center" />
              <el-table-column prop="testTime" label="检测时间" min-width="160" align="center" />
              <el-table-column label="检测员" width="100" align="center">
                <template #default="{ row: m }">{{ m.operator || '未记录' }}</template>
              </el-table-column>
              <el-table-column label="采集设备" min-width="140" align="center">
                <template #default="{ row: m }">
                  <el-tooltip :content="m.source === 'DEVICE' ? `设备采集：${m.sourceRef || '--'}` : sourceLabel(m.source)" placement="top">
                    <el-tag :type="sourceTag(m.source)" size="small" effect="plain">{{ deviceText(m) }}</el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="检测结果" width="110" align="center">
                <template #default="{ row: m }">
                  <el-tag :type="ractoResultTag(m.result)" size="small">{{ ractoResultLabel(m.result) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row: m }">
                  <el-tag :type="ractoStatusTag(m.status)" size="small">{{ ractoStatusLabel(m.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right" align="center">
                <template #default="{ row: m }">
                  <el-button type="primary" link size="small" @click="handleView(m)">查看</el-button>
                  <el-button type="danger" link size="small" @click="handleVoid(m)">作废</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="批次号" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.batchNo">
            <span class="batch-no">{{ row.batchNo }}</span>
            <span class="batch-count">{{ row.headCount }} 份样本</span>
          </template>
          <el-tooltip v-else content="这头猪没有批次号（外购零星补栏等），按头独立成行" placement="top">
            <el-tag type="info" size="small" effect="plain">未归批</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="耳标区间" min-width="230" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.tagStart" class="tag-range">{{ tagRange(row) }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="检测项目" min-width="170" align="center">
        <template #default="{ row }">{{ typeSummary(row) }}</template>
      </el-table-column>
      <el-table-column label="检测时间" min-width="200" align="center">
        <template #default="{ row }">{{ timeRange(row) }}</template>
      </el-table-column>
      <el-table-column label="检测员" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ operatorSummary(row) }}</template>
      </el-table-column>
      <el-table-column label="检测结论" min-width="150" align="center">
        <template #default="{ row }">
          <el-tooltip :content="row.statusDetail || row.statusSummary" placement="top">
            <el-tag :type="statusTagType(row.statusSummary)" size="small" effect="plain">
              {{ row.statusSummary }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewVisible" title="瘦肉精检测详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="检测编号">{{ currentView.testNo }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ currentView.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="样本编号">{{ currentView.sampleNo }}</el-descriptions-item>
        <el-descriptions-item label="检测项目">{{ currentView.testType }}</el-descriptions-item>
        <el-descriptions-item label="检测方法">{{ currentView.testMethod || '--' }}</el-descriptions-item>
        <el-descriptions-item label="检测时间">{{ currentView.testTime }}</el-descriptions-item>
        <el-descriptions-item label="检测员">{{ currentView.operator || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="采集设备">
          <el-tag :type="sourceTag(currentView.source)" size="small" effect="plain">{{ deviceText(currentView) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="检测结果">
          <el-tag :type="ractoResultTag(currentView.result)" size="small">{{ ractoResultLabel(currentView.result) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="ractoStatusTag(currentView.status)" size="small">{{ ractoStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentView.remark || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { deviceText, sourceLabel, sourceTag } from '@/api/modules/ingest'
import { slaughterApi } from '@/api/modules/slaughter'
import { confirmVoid } from '@/composables/useVoidAction'

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

const viewVisible = ref(false)
const currentView = ref({})

const tagRange = (row) => {
  if (!row.tagStart) return '--'
  return row.tagStart === row.tagEnd ? row.tagStart : `${row.tagStart} ~ ${row.tagEnd}`
}

const timeRange = (row) => {
  if (!row.firstTime) return '--'
  return row.firstTime === row.lastTime ? row.firstTime : `${row.firstTime} ~ ${row.lastTime}`
}

const typeSummary = (row) => {
  const types = [...new Set((row.members || []).map((m) => m.testType).filter(Boolean))]
  if (types.length === 0) return '--'
  return types.length === 1 ? types[0] : `${types[0]} 等 ${types.length} 项`
}

const operatorSummary = (row) => {
  const names = [...new Set((row.members || []).map((m) => m.operator).filter(Boolean))]
  if (names.length === 0) return '未记录'
  return names.length === 1 ? names[0] : `${names[0]} 等${names.length}人`
}

const memberTitle = (row) => (row.batchNo
  ? `批次 ${row.batchNo}：本次共 ${row.headCount} 份样本的检测明细`
  : `未归批：${row.headCount} 份样本的检测明细`)

/** 结论配色：阴性绿、阳性红、未完成黄 */
const statusTagType = (s) => {
  if (s === '全部阴性') return 'success'
  if (s === '含阳性' || s === '全部阳性') return 'danger'
  return 'warning'
}

// 作废：已上链记录不提供删除——作废需填理由，理由与实名操作人随作废事件一并上链
const handleVoid = async (row) => {
  const ok = await confirmVoid({
    title: '作废瘦肉精检测记录',
    message: `确认作废检测单 ${row.testNo || ''} 吗？作废后该检测结果不再生效，但仍保留在库中并可追溯。`,
    onConfirm: (reason) => slaughterApi.voidRactopamine(row.id, reason),
  })
  if (ok) emit('voided')
}

const handleView = (row) => {
  currentView.value = row
  viewVisible.value = true
}

// 检测结果：1=阴性 0=阳性
const ractoResultLabel = (r) => ({ 1: '阴性', 0: '阳性' }[r] ?? '--')
const ractoResultTag = (r) => ({ 1: 'success', 0: 'danger' }[r] ?? 'info')
// 状态：0=待检测 1=检测中 2=已完成
const ractoStatusLabel = (s) => ({ 0: '待检测', 1: '检测中', 2: '已完成' }[s] ?? '--')
const ractoStatusTag = (s) => ({ 0: 'info', 1: 'warning', 2: 'success' }[s] ?? 'info')
</script>

<style lang="scss" scoped>
.racto-table {
  width: 100%;
}
.batch-no { font-weight: 600; color: #303133; }
.batch-count {
  margin-left: 8px;
  padding: 0 6px;
  font-size: 12px;
  color: #606266;
  background: #f4f4f5;
  border-radius: 9px;
}
.tag-range { font-family: 'Courier New', monospace; font-size: 12px; color: #606266; }
.empty-cell { color: #c0c4cc; }
.member-panel {
  padding: 8px 12px 12px 48px;
  background: #fafafa;

  &__title {
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: #606266;
  }
}
</style>
