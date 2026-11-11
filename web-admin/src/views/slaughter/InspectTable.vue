<template>
  <div class="inspect-table">
    <!--
      主行 = 一个批次的检验进展（一批猪陆续宰，跨天是常态），展开 = 逐头检验明细。
      折叠不改口径：合格/不合格、作废入口都在逐头那一层 —— 一头不合格只扣这一头。
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
              <el-table-column prop="inspectNo" label="检验编号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="earTagNo" label="耳标号" min-width="140" show-overflow-tooltip />
              <el-table-column label="检验类型" min-width="130" align="center">
                <template #default="{ row: m }">{{ inspectTypeLabel(m.inspectType) }}</template>
              </el-table-column>
              <el-table-column prop="inspectTime" label="检验时间" min-width="160" align="center" />
              <el-table-column label="检验员" width="100" align="center">
                <template #default="{ row: m }">{{ m.veterinary || '未记录' }}</template>
              </el-table-column>
              <el-table-column label="体温(°C)" width="95" align="center">
                <template #default="{ row: m }">
                  {{ m.temperature === null || m.temperature === undefined || m.temperature === '' ? '--' : m.temperature }}
                </template>
              </el-table-column>
              <el-table-column label="采集设备" min-width="140" align="center">
                <template #default="{ row: m }">
                  <el-tooltip :content="m.source === 'DEVICE' ? `工位终端录入：${m.sourceRef || '--'}` : sourceLabel(m.source)" placement="top">
                    <el-tag :type="sourceTag(m.source)" size="small" effect="plain">{{ deviceText(m) }}</el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row: m }">
                  <el-tag :type="inspectStatusTag(m.status)" size="small">{{ inspectStatusLabel(m.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right" align="center">
                <template #default="{ row: m }">
                  <el-button type="primary" link size="small" @click="handleView(m)">查看</el-button>
                  <el-button v-if="m.status !== 3" type="danger" link size="small" @click="handleVoid(m)">
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
      <el-table-column label="检验环节" min-width="170" align="center">
        <template #default="{ row }">{{ typeSummary(row) }}</template>
      </el-table-column>
      <el-table-column label="检验时间" min-width="200" align="center">
        <template #default="{ row }">{{ timeRange(row) }}</template>
      </el-table-column>
      <el-table-column label="检验员" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ veterinarySummary(row) }}</template>
      </el-table-column>
      <el-table-column label="检验结论" min-width="150" align="center">
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
    <el-dialog v-model="viewVisible" title="屠宰检验详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="检验编号">{{ currentView.inspectNo }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ currentView.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="耳标号">{{ currentView.earTagNo }}</el-descriptions-item>
        <el-descriptions-item label="检验类型">{{ inspectTypeLabel(currentView.inspectType) }}</el-descriptions-item>
        <el-descriptions-item label="检验时间">{{ currentView.inspectTime }}</el-descriptions-item>
        <el-descriptions-item label="体温(°C)">{{ currentView.temperature ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="官方兽医">{{ currentView.veterinary || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="采集设备">{{ deviceText(currentView) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="inspectStatusTag(currentView.status)" size="small">{{ inspectStatusLabel(currentView.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="检验结论" :span="2">{{ currentView.conclusion || '--' }}</el-descriptions-item>
        <el-descriptions-item label="疫苗记录" :span="2">
          <div v-if="vaccineRecords.length === 0" style="color: #909399">无疫苗记录</div>
          <div v-for="v in vaccineRecords" :key="v.id" style="margin-bottom: 4px">
            {{ v.vaccineName }}（{{ v.batchNo }}）- {{ v.injectTime }} - {{ v.operator }}
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="内容哈希" :span="2">
          <span v-if="currentView.contentHash" class="hash-text">{{ currentView.contentHash }}</span>
          <span v-else>--</span>
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

const viewVisible = ref(false)
const currentView = ref({})
const vaccineRecords = ref([])

const tagRange = (row) => {
  if (!row.tagStart) return '--'
  return row.tagStart === row.tagEnd ? row.tagStart : `${row.tagStart} ~ ${row.tagEnd}`
}

const timeRange = (row) => {
  if (!row.firstTime) return '--'
  return row.firstTime === row.lastTime ? row.firstTime : `${row.firstTime} ~ ${row.lastTime}`
}

/** 检验环节：一批里可能既有宰前也有宰后（同步检疫），只报一个环节会误导 */
const typeSummary = (row) => {
  const types = [...new Set((row.members || []).map((m) => inspectTypeLabel(m.inspectType)).filter((t) => t && t !== '--'))]
  if (types.length === 0) return '--'
  return types.length === 1 ? types[0] : `${types[0]} 等 ${types.length} 个环节`
}

/** 检验员：一个人报名，多个人报「首位 等N人」——不留「系统」这种假名 */
const veterinarySummary = (row) => {
  const names = [...new Set((row.members || []).map((m) => m.veterinary).filter(Boolean))]
  if (names.length === 0) return '未记录'
  return names.length === 1 ? names[0] : `${names[0]} 等${names.length}人`
}

const memberTitle = (row) => (row.batchNo
  ? `批次 ${row.batchNo}：本次共 ${row.headCount} 头的检验明细`
  : `未归批：${row.headCount} 头的检验明细`)

/** 结论配色：全合格绿、不合格红、待检验/部分黄 */
const statusTagType = (s) => {
  if (s === '全部合格') return 'success'
  if (s === '全部不合格' || s === '含不合格') return 'danger'
  if (s === '全部作废') return 'info'
  return 'warning'
}

const handleView = async (row) => {
  currentView.value = row
  vaccineRecords.value = []
  viewVisible.value = true
  if (row.pigId) {
    try {
      const res = await request.get(`/breeding/pigs/${row.pigId}/vaccines`)
      vaccineRecords.value = res?.records || res?.list || []
    } catch (e) {
      console.error('获取疫苗记录失败:', e)
    }
  }
}

// 检验类型：1=宰前 2=宰后 3=宰后（同步）
// 2 和 3 按法规口径是同一个环节——《动物检疫管理办法》第二十条「动物屠宰过程实行全流程同步检疫」，
// 宰后检疫的标准做法就是同步检疫，3 只是记录上标明「这次按同步方式做」
const inspectTypeLabel = (t) => ({ 1: '宰前检验', 2: '宰后检验', 3: '宰后检验（同步）' }[t] ?? t ?? '--')
// 状态：0=待检验 1=合格 2=不合格 3=已作废
const inspectStatusLabel = (s) => ({ 0: '待检验', 1: '合格', 2: '不合格', 3: '已作废' }[s] ?? '--')
const inspectStatusTag = (s) => ({ 0: 'info', 1: 'success', 2: 'danger', 3: 'info' }[s] ?? 'info')

// 作废：检验记录已上链，不支持物理删除，输错信息请作废后重新录入
const emit = defineEmits(['voided'])
const handleVoid = async (row) => {
  const ok = await confirmVoid({
    title: '作废屠宰检验记录',
    message: `确认作废检验记录 ${row.inspectNo || ''} 吗？作废后该结论不再生效，作废凭据上链且不可撤销；录入有误请作废后重新录入。`,
    onConfirm: (reason) => slaughterApi.voidInspection(row.id, reason),
  })
  if (ok) emit('voided')
}
</script>

<style lang="scss" scoped>
.inspect-table {
  width: 100%;
}
.hash-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #909399;
  word-break: break-all;
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
