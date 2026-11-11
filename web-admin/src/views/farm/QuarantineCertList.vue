<template>
  <div class="cert-list">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="耳标号">
          <el-input
            v-model="searchForm.earTagNo"
            placeholder="请输入耳标号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="证状态">
          <el-select v-model="searchForm.certState" placeholder="全部" clearable style="width: 160px">
            <el-option label="已出证（有效）" value="valid" />
            <el-option label="已出证（已过期）" value="expired" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!--
      主行 = 一次出证（同一批、同一天、同一出证机构），展开 = 这一批逐头的证。
      本系统存量是「一证一头」（设备逐头出证），所以主行的证号可能是「QC-xxx 等 N 张」；
      现实里一张证覆盖一批，将来补了主单这里就直接是一张证号。
    -->
    <el-table v-loading="loading" :data="rows" row-key="groupKey" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="member-panel">
            <div class="member-panel__title">本批出证 {{ row.headCount }} 头</div>
            <el-table :data="row.members" size="small" border :row-class-name="memberRowClass">
              <el-table-column prop="earTagNo" label="耳标号" min-width="150" show-overflow-tooltip />
              <el-table-column label="检疫证编号" min-width="180" show-overflow-tooltip>
                <template #default="{ row: m }">{{ m.certNo || '--' }}</template>
              </el-table-column>
              <el-table-column label="出证日期" min-width="120" align="center">
                <template #default="{ row: m }">{{ dayOf(m.issueTime) }}</template>
              </el-table-column>
              <el-table-column label="有效期至" width="130" align="center">
                <template #default="{ row: m }">
                  <span :class="{ 'cert-expired': isExpired(m) }">{{ dayOf(m.validUntil) || '--' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="查验员" width="110" align="center">
                <template #default="{ row: m }">{{ m.inspector || '未记录' }}</template>
              </el-table-column>
              <el-table-column label="采集设备" width="140" align="center">
                <template #default="{ row: m }">
                  <el-tag v-if="m.source === 'DEVICE'" size="small" effect="plain">
                    {{ m.sourceRef || '设备采集' }}
                  </el-tag>
                  <span v-else>{{ sourceLabel(m.source) }}</span>
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
                  <el-tag v-else-if="isExpired(m)" type="danger" size="small" effect="plain">已过期</el-tag>
                  <el-tag v-else type="success" size="small" effect="plain">有效</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130" fixed="right" align="center">
                <template #default="{ row: m }">
                  <el-button type="primary" link size="small" @click="openCert(m)">
                    {{ m.voidFlag === 1 ? '查看' : '查看 / 作废' }}
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
      <el-table-column prop="breed" label="品种" width="110">
        <template #default="{ row }">{{ row.breed || '--' }}</template>
      </el-table-column>
      <el-table-column label="养殖场" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ row.farmName || '--' }}</template>
      </el-table-column>
      <el-table-column label="检疫证编号" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.title || '--' }}</template>
      </el-table-column>
      <el-table-column label="出证日期" width="130" align="center">
        <template #default="{ row }">{{ row.happenedOn || '--' }}</template>
      </el-table-column>
      <el-table-column label="最早有效期" width="140" align="center">
        <template #default="{ row }">
          <span :class="{ 'cert-expired': rowExpired(row) }">{{ earliestValidUntil(row) || '--' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="覆盖头数" width="100" align="center">
        <template #default="{ row }">
          <span class="head-count">{{ row.headCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="耳标区间" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.tagStart" class="tag-range">{{ tagRange(row) }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="证状态" min-width="160" align="center">
        <template #default="{ row }">
          <!-- 标签给结论（全部有效/部分有效/全部失效/全部作废），有效期与作废计数进 tooltip -->
          <el-tooltip :content="row.statusDetail || row.statusSummary" placement="top">
            <el-tag :type="statusTagType(row)" size="small" effect="plain">
              {{ row.statusSummary }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页：total 是「组数」不是「头数」——列表按批聚合，一头一行会让翻页错位 -->
    <div class="pagination-wrapper">
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

    <QuarantineCertDialog v-model="certDialogVisible" :pig="certPig" @changed="fetchList" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { recordGroupApi } from '@/api/modules/breeding'
import { sourceLabel } from '@/api/modules/ingest'
import QuarantineCertDialog from './QuarantineCertDialog.vue'

const searchForm = reactive({
  earTagNo: '',
  certState: '',
})

const rows = ref([])
const loading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

const todayStart = () => {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d
}

const dayOf = (v) => (v ? String(v).slice(0, 10) : '')

/** 单条明细是否已过期（已作废的不算过期，作废是更强的结论） */
const isExpired = (m) => {
  if (m.voidFlag === 1) return false
  const v = dayOf(m.validUntil)
  if (!v) return false
  return new Date(v) < todayStart()
}

/** 组内最早的有效期：审这张「批」的时候，要盯最先失效的那一头 */
const earliestValidUntil = (row) => {
  const days = (row.members || [])
    .filter((m) => m.voidFlag !== 1 && dayOf(m.validUntil))
    .map((m) => dayOf(m.validUntil))
    .sort()
  return days[0] || ''
}

/** 主行是否含已过期证：列表里一眼看出「这批有证过期了」 */
const rowExpired = (row) => (row.members || []).some((m) => isExpired(m))

/** 证状态标签配色：全有效=绿，部分有效=黄，全部失效（过期）=红，全部作废=灰（作废是人的决定，不是异常） */
const statusTagType = (row) => {
  const s = row.statusSummary || ''
  if (s === '全部有效') return 'success'
  if (s === '部分有效') return 'warning'
  if (s === '全部作废') return 'info'
  return 'danger'
}

const tagRange = (row) => {
  if (!row.tagStart) return '--'
  return row.tagStart === row.tagEnd ? row.tagStart : `${row.tagStart} ~ ${row.tagEnd}`
}

const memberRowClass = ({ row }) => (row.voidFlag === 1 ? 'row-voided' : '')

const fetchList = async () => {
  loading.value = true
  try {
    const res = await recordGroupApi.certs({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      earTagNo: searchForm.earTagNo || undefined,
      certState: searchForm.certState || undefined,
    })
    const list = res?.records || res?.list || []
    rows.value = Array.isArray(list) ? list : []
    pagination.total = res?.total || 0
  } catch {
    rows.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  fetchList()
}

const handleReset = () => {
  searchForm.earTagNo = ''
  searchForm.certState = ''
  handleSearch()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  fetchList()
}

const handlePageChange = (page) => {
  pagination.pageNum = page
  fetchList()
}

// ==================== 出证弹窗 ====================

const certDialogVisible = ref(false)
const certPig = ref(null)

/**
 * 出证/作废的粒度仍是「一头」——分组只是读法变了，写操作没有跟着变成整批。
 * 出证弹窗要的是生猪对象（id + earTagNo），这里把明细行映射回去。
 */
const openCert = (member) => {
  certPig.value = {
    id: member.pigId,
    earTagNo: member.earTagNo,
    breed: member.pigBreed,
    farmId: member.farmId,
  }
  certDialogVisible.value = true
}

onMounted(() => {
  fetchList()
})
</script>

<style lang="scss" scoped>
.cert-list {
  padding: 20px;
  background-color: #fff;
  border-radius: 4px;

  .search-panel {
    padding-bottom: 16px;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 16px;

    :deep(.el-form-item) {
      margin-bottom: 12px;
    }
  }

  .cert-expired {
    color: #f56c6c;
    font-weight: 600;
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

  :deep(.row-voided) {
    --el-table-tr-bg-color: var(--el-fill-color-lighter);

    td.el-table__cell {
      color: #a8abb2;
    }
  }

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
}
</style>
