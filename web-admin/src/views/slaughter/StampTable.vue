<template>
  <div class="stamp-table">
    <!--
      主行 = 一个批次的盖章进度，展开 = 逐头（逐胴体）盖章明细。
      一枚章对一头白条，任何「整批已盖章」的结论都必须能点开看到每一枚章。
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
              <el-table-column prop="stampNo" label="盖章编号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="carcassNo" label="胴体编号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="earTagNo" label="耳标号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="stampType" label="印章类型" min-width="130" align="center" />
              <el-table-column prop="stampTime" label="盖章时间" min-width="160" align="center" />
              <el-table-column label="检疫员" width="100" align="center">
                <template #default="{ row: m }">{{ m.veterinary || '未记录' }}</template>
              </el-table-column>
              <el-table-column label="采集设备" min-width="140" align="center">
                <template #default="{ row: m }">
                  <el-tooltip :content="m.source === 'DEVICE' ? `自动盖章机：${m.sourceRef || '--'}` : sourceLabel(m.source)" placement="top">
                    <el-tag :type="sourceTag(m.source)" size="small" effect="plain">{{ deviceText(m) }}</el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row: m }">
                  <el-tag :type="stampStatusTag(m.status)" size="small">{{ stampStatusLabel(m.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right" align="center">
                <template #default="{ row: m }">
                  <el-button type="primary" link size="small" @click="handleView(m)">查看</el-button>
                  <el-button v-if="m.status === 0 || m.status === 1" type="danger" link size="small" @click="handleVoid(m)">
                    作废
                  </el-button>
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
      <el-table-column label="印章类型" min-width="170" align="center">
        <template #default="{ row }">{{ typeSummary(row) }}</template>
      </el-table-column>
      <el-table-column label="盖章时间" min-width="200" align="center">
        <template #default="{ row }">{{ timeRange(row) }}</template>
      </el-table-column>
      <el-table-column label="检疫员" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ veterinarySummary(row) }}</template>
      </el-table-column>
      <el-table-column label="盖章状态" min-width="150" align="center">
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
    <el-dialog v-model="viewVisible" title="检疫盖章详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="盖章编号">{{ currentView.stampNo }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ currentView.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="胴体编号">{{ currentView.carcassNo }}</el-descriptions-item>
        <el-descriptions-item label="耳标号">{{ currentView.earTagNo }}</el-descriptions-item>
        <el-descriptions-item label="印章类型">{{ currentView.stampType }}</el-descriptions-item>
        <el-descriptions-item label="盖章时间">{{ currentView.stampTime }}</el-descriptions-item>
        <el-descriptions-item label="官方兽医">{{ currentView.veterinary || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="采集设备">{{ deviceText(currentView) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="stampStatusTag(currentView.status)" size="small">{{ stampStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
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

const viewVisible = ref(false)
const currentView = ref({})

const handleView = (row) => {
  currentView.value = row
  viewVisible.value = true
}

const tagRange = (row) => {
  if (!row.tagStart) return '--'
  return row.tagStart === row.tagEnd ? row.tagStart : `${row.tagStart} ~ ${row.tagEnd}`
}

const timeRange = (row) => {
  if (!row.firstTime) return '--'
  return row.firstTime === row.lastTime ? row.firstTime : `${row.firstTime} ~ ${row.lastTime}`
}

/** 印章类型：一批里可能混用不同印章，报一种会漏 */
const typeSummary = (row) => {
  const types = [...new Set((row.members || []).map((m) => m.stampType).filter(Boolean))]
  if (types.length === 0) return '--'
  return types.length === 1 ? types[0] : `${types[0]} 等 ${types.length} 种`
}

const veterinarySummary = (row) => {
  const names = [...new Set((row.members || []).map((m) => m.veterinary).filter(Boolean))]
  if (names.length === 0) return '未记录'
  return names.length === 1 ? names[0] : `${names[0]} 等${names.length}人`
}

const memberTitle = (row) => (row.batchNo
  ? `批次 ${row.batchNo}：本次共 ${row.headCount} 头的盖章明细`
  : `未归批：${row.headCount} 头的盖章明细`)

/** 结论配色：全已盖章绿、全作废灰、待盖章/部分黄 */
const statusTagType = (s) => {
  if (s === '全部已盖章') return 'success'
  if (s === '全部作废') return 'info'
  return 'warning'
}

// 状态：0=待盖章 1=已盖章 2=已作废
const stampStatusLabel = (s) => ({ 0: '待盖章', 1: '已盖章', 2: '已作废' }[s] ?? '--')
const stampStatusTag = (s) => ({ 0: 'info', 1: 'success', 2: 'danger' }[s] ?? 'info')

// 作废：盖章记录已上链，不支持物理删除，输错信息请作废后重新录入
const emit = defineEmits(['voided'])
const handleVoid = async (row) => {
  const ok = await confirmVoid({
    title: '作废胴体盖章记录',
    message: `确认作废盖章记录 ${row.stampNo || ''} 吗？作废后该盖章凭据不再生效，作废凭据上链且不可撤销；录入有误请作废后重新录入。`,
    onConfirm: (reason) => slaughterApi.voidStamp(row.id, reason),
  })
  if (ok) emit('voided')
}
</script>

<style lang="scss" scoped>
.stamp-table {
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
