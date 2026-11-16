<template>
  <div class="apply-table">
    <!--
      主行 = 一张出栏申报单（单据），展开 = 这张单的「关联生猪」。
      审批是单据级的：一次审批给整张单一个结论（通过 / 驳回），
      通过后单据里的每一头都会被推进为「已出栏」——逐头状态才是入场查验认的结论。
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
            <div class="member-panel__title">
              关联生猪 {{ row.headCount }} 头
            </div>
            <el-table :data="row.members" size="small" border>
              <el-table-column prop="earTagNo" label="耳标号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="applyNo" label="申报编号" min-width="160" show-overflow-tooltip />
              <el-table-column prop="applyTime" label="申报时间" min-width="160" align="center" />
              <el-table-column label="体重" width="100" align="center">
                <template #default="{ row: m }">{{ m.weightKg ? `${m.weightKg} kg` : '--' }}</template>
              </el-table-column>
              <el-table-column label="采集设备" min-width="130" show-overflow-tooltip>
                <template #default="{ row: m }">{{ deviceText(m) }}</template>
              </el-table-column>
              <el-table-column label="审批人" width="110" align="center">
                <template #default="{ row: m }">
                  {{ m.approvalStatus === 0 ? '--' : m.approver || '未记录' }}
                </template>
              </el-table-column>
              <el-table-column label="审批状态" width="110" align="center">
                <template #default="{ row: m }">
                  <StatusTag :type="applyStatusType(m.approvalStatus)" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right" align="center">
                <template #default="{ row: m }">
                  <el-button
                    v-if="m.approvalStatus === 0"
                    type="primary"
                    link
                    size="small"
                    @click="$emit('approve', m)"
                  >
                    审批
                  </el-button>
                  <el-button type="info" link size="small" @click="$emit('view', m)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="申报单号" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="batch-no">{{ row.docNo || '--' }}</span>
          <span class="batch-count">{{ row.headCount }} 头</span>
        </template>
      </el-table-column>
      <el-table-column label="申报日期" width="130" align="center">
        <template #default="{ row }">{{ row.happenedOn || '--' }}</template>
      </el-table-column>
      <el-table-column label="申报头数" width="100" align="center">
        <template #default="{ row }">
          <span class="head-count">{{ row.headCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="均重" width="110" align="center">
        <template #default="{ row }">
          <span v-if="avgWeight(row)">{{ avgWeight(row) }} kg</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="目标屠宰场" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.subTitle || '--' }}</template>
      </el-table-column>
      <el-table-column label="审批状态" min-width="140" align="center">
        <template #default="{ row }">
          <!-- 结论标签 + 计数进 tooltip：不再把「待审 1」这种计数当结论贴在表格里 -->
          <el-tooltip v-if="row.statusDetail && row.statusDetail !== '--'" :content="row.statusDetail" placement="top">
            <el-tag :type="summaryTagType(row.statusSummary)" size="small" effect="plain">
              {{ row.statusSummary }}
            </el-tag>
          </el-tooltip>
          <el-tag v-else :type="summaryTagType(row.statusSummary)" size="small" effect="plain">
            {{ row.statusSummary }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-if="pendingCount(row) > 0"
            type="primary"
            link
            size="small"
            @click="$emit('batch-approve', row)"
          >
            审批
          </el-button>
          <span v-else class="empty-cell">已审完</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import StatusTag from '@/components/common/StatusTag.vue'
import { deviceText } from '@/api/modules/ingest'

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

defineEmits(['approve', 'view', 'batch-approve'])

const applyStatusType = (status) => {
  const map = { 0: 'pending', 1: 'approved', 2: 'rejected' }
  return map[status] || ''
}

/** 结论标签配色：待审=黄、全通过=绿、全驳回=灰（人的决定不是异常）、含驳回=红（要人看） */
const summaryTagType = (summary) => {
  const map = {
    待审批: 'warning',
    已通过: 'success',
    已驳回: 'danger'
  }
  return map[summary] || 'info'
}

/** 组内还等着审的条数：批量审批按钮上的数字，也是「这组还有活」的判据 */
const pendingCount = (row) => (row.members || []).filter((m) => m.approvalStatus === 0).length

const weights = (row) => (row.members || [])
  .map((m) => Number(m.weightKg))
  .filter((n) => Number.isFinite(n) && n > 0)

const avgWeight = (row) => {
  const list = weights(row)
  if (list.length === 0) return ''
  const avg = list.reduce((a, b) => a + b, 0) / list.length
  return avg.toFixed(1)
}
</script>

<style lang="scss" scoped>
.apply-table {
  :deep(.el-table) {
    th.el-table__cell {
      background-color: #f5f7fa;
      color: #606266;
      font-weight: 600;
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
</style>
