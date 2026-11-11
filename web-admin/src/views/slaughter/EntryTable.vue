<template>
  <div class="entry-table">
    <!--
      主行 = 一个批次（同批到场的猪收成一行），展开 = 逐头查验明细。
      折叠只为浏览：查验结论、作废入口仍在逐头那一层 —— 一头不合格只能扣这一头。
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
              <el-table-column prop="earTagNo" label="耳标号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="arriveTime" label="入场时间" min-width="160" align="center" />
              <el-table-column prop="weight" label="重量(kg)" width="100" align="center" />
              <el-table-column prop="quarantineCert" label="检疫证明" min-width="140" show-overflow-tooltip />
              <el-table-column prop="inspector" label="查验员" width="100" align="center">
                <template #default="{ row: m }">{{ m.inspector || '未记录' }}</template>
              </el-table-column>
              <el-table-column prop="source" label="采集设备" min-width="140" align="center">
                <template #default="{ row: m }">
                  <el-tooltip :content="m.source === 'DEVICE' ? `设备采集：${m.sourceRef || '--'}` : sourceLabel(m.source)" placement="top">
                    <el-tag :type="sourceTag(m.source)" size="small" effect="plain">{{ deviceText(m) }}</el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row: m }">
                  <el-tag :type="entryStatusTag(m.status)" size="small">{{ entryStatusLabel(m.status) }}</el-tag>
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
            <span class="batch-count">{{ row.headCount }} 头</span>
          </template>
          <el-tooltip v-else content="这批猪没有批次号（外购零星补栏等），逐头独立成行" placement="top">
            <el-tag type="info" size="small" effect="plain">未归批</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="来源养殖场" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ farmText(row) }}</template>
      </el-table-column>
      <el-table-column label="耳标区间" min-width="230" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.tagStart" class="tag-range">{{ tagRange(row) }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="到场时间" min-width="200" align="center">
        <template #default="{ row }">{{ timeRange(row) }}</template>
      </el-table-column>
      <el-table-column label="查验状态" min-width="150" align="center">
        <template #default="{ row }">
          <!-- 标签只给结论，计数进 tooltip：「合格 28 / 不合格 1」被当成结论读会漏掉那 1 头 -->
          <el-tooltip :content="row.statusDetail || row.statusSummary" placement="top">
            <el-tag :type="statusTagType(row.statusSummary)" size="small" effect="plain">
              {{ row.statusSummary }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewVisible" title="入场查验详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="批次号">{{ currentView.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="耳标号">{{ currentView.earTagNo }}</el-descriptions-item>
        <el-descriptions-item label="来源养殖场">{{ currentView.sourceFarm }}</el-descriptions-item>
        <el-descriptions-item label="入场时间">{{ currentView.arriveTime }}</el-descriptions-item>
        <el-descriptions-item label="重量(kg)">{{ currentView.weight }}</el-descriptions-item>
        <el-descriptions-item label="检疫证明">{{ currentView.quarantineCert || '--' }}</el-descriptions-item>
        <el-descriptions-item label="车辆牌号">{{ currentView.vehicleNo || '--' }}</el-descriptions-item>
        <el-descriptions-item label="查验员">{{ currentView.inspector || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="采集设备">{{ deviceText(currentView) }}</el-descriptions-item>
        <el-descriptions-item label="健康检查">{{ passFailLabel(currentView.healthCheck) }}</el-descriptions-item>
        <el-descriptions-item label="检疫证核验">{{ passFailLabel(currentView.certVerified) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="entryStatusTag(currentView.status)" size="small">{{ entryStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="查验意见" :span="2">{{ currentView.remark || '--' }}</el-descriptions-item>
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

/** 耳标区间：起止相同就显示单个耳标，不给「X ~ X」这种假范围 */
const tagRange = (row) => {
  if (!row.tagStart) return '--'
  return row.tagStart === row.tagEnd ? row.tagStart : `${row.tagStart} ~ ${row.tagEnd}`
}

/** 到场时间：一个批次可能分几次到场，给区间而不是只报一个时刻 */
const timeRange = (row) => {
  if (!row.firstTime) return '--'
  return row.firstTime === row.lastTime ? row.firstTime : `${row.firstTime} ~ ${row.lastTime}`
}

/** 场名取组内第一头的（同一批次同场；为空说明设备没报场名，不许瞎猜） */
const farmText = (row) => {
  const first = (row.members || []).find((m) => m.sourceFarm)
  return first?.sourceFarm || '--'
}

const memberTitle = (row) => (row.batchNo
  ? `批次 ${row.batchNo}：本次共 ${row.headCount} 头的查验明细`
  : `未归批：${row.headCount} 头的查验明细`)

/** 结论配色：全合格绿、含不合格红、待查验/部分黄 */
const statusTagType = (s) => {
  if (s === '全部合格') return 'success'
  if (s === '全部不合格' || s === '含不合格') return 'danger'
  return 'warning'
}

// 作废：已上链记录不提供删除——作废需填理由，理由与实名操作人随作废事件一并上链
const handleVoid = async (row) => {
  const ok = await confirmVoid({
    title: '作废入场查验记录',
    message: `确认作废 ${row.earTagNo || ''} 的入场查验记录吗？作废后该记录不再生效，但仍保留在库中并可追溯。`,
    onConfirm: (reason) => slaughterApi.voidEntry(row.id, reason),
  })
  if (ok) emit('voided')
}

const handleView = (row) => {
  currentView.value = row
  viewVisible.value = true
}

// 入场查验状态：0=待查验 1=合格 2=不合格
const entryStatusLabel = (s) => ({ 0: '待查验', 1: '合格', 2: '不合格' }[s] ?? '--')
const entryStatusTag = (s) => ({ 0: 'info', 1: 'success', 2: 'danger' }[s] ?? 'info')
// 通过/异常：1=通过 0=异常
const passFailLabel = (v) => ({ 1: '通过', 0: '异常' }[v] ?? '--')
</script>

<style lang="scss" scoped>
.entry-table {
  width: 100%;
}
.source-ref { color: #909399; font-size: 12px; }
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
