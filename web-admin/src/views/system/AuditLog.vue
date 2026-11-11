<template>
  <div class="page-container">
    <!-- 筛选面板 -->
    <div class="search-panel">
      <el-form :model="filterForm" inline>
        <el-form-item label="业务类型">
          <el-select v-model="filterForm.operType" placeholder="全部" clearable filterable style="width: 190px">
            <el-option-group v-for="group in CHAIN_BIZ_TYPE_GROUPS" :key="group.name" :label="group.name">
              <el-option v-for="t in group.types" :key="t" :label="CHAIN_BIZ_TYPE[t].label" :value="t" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计摘要 -->
    <div class="stats-bar">
      <el-tag :type="chainMode.onChain ? 'success' : 'info'" size="large">
        {{ chainMode.onChain ? '存证载体：区块链' : '存证载体：平台本地账本' }}
      </el-tag>
      <el-tag type="info" size="large">存证记录: {{ stats.totalTx }}</el-tag>
      <el-tag type="info" size="large">今日新增: {{ stats.todayTx }}</el-tag>
      <span class="stats-hint">{{ chainMode.onChain ? '区块高度' : '账本序号' }}: #{{ stats.latestBlock }}</span>
    </div>

    <!-- 存证体检：对账结果，异常必须摆在明面上 -->
    <div class="health-card" :class="health.ok ? 'is-ok' : 'is-bad'">
      <div class="health-head">
        <span class="health-title">
          <el-icon><component :is="health.ok ? 'CircleCheckFilled' : 'WarningFilled'" /></el-icon>
          存证体检
        </span>
        <span class="health-state">{{ health.ok ? '链路正常' : '存在异常' }}</span>
        <span class="health-time" v-if="health.checkedAt">体检时间 {{ health.checkedAt }}</span>
        <el-button link size="small" @click="fetchHealth"><el-icon><Refresh /></el-icon>重新体检</el-button>
      </div>
      <div class="health-metrics">
        <span>成功 {{ health.success ?? '-' }}</span>
        <span>待存证 {{ health.pending ?? '-' }}</span>
        <span :class="{ bad: (health.failed ?? 0) > 0 }">失败 {{ health.failed ?? '-' }}</span>
        <span :class="{ bad: (health.danglingRecord ?? 0) > 0 }">悬空存证 {{ health.danglingRecord ?? '-' }}</span>
        <span>账本块 {{ health.ledgerTotal ?? '-' }}</span>
        <span v-if="(health.legacyLocal ?? 0) > 0"
              :title="'写于平台本地账本时期、尚未补写链上的记录；用存证迁移接口补写后自动归零'">
          历史本地存证 {{ health.legacyLocal }}
        </span>
        <span :title="'历史遗留：账本有块但存证表无对应记录，无法验真，也不应删除'">
          历史孤儿块 {{ health.orphanLedger ?? '-' }}（近24h +{{ health.orphanLedgerRecent ?? 0 }}）
        </span>
      </div>
      <div class="health-issues" v-if="health.issues?.length">
        <div v-for="(issue, i) in health.issues" :key="i">· {{ issue }}</div>
      </div>
      <div class="health-issues ok-text" v-else>未发现失败积压、悬空存证或新增孤儿块。</div>
      <div class="health-note">{{ chainMode.summary }}</div>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="chainTime" label="时间" width="170" align="center" />
      <el-table-column prop="bizType" label="业务类型" width="140" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="isLegacyBizType(row.bizType)" placement="top"
                      content="该类型已停止发布，仅历史存证中存在">
            <el-tag size="small" type="info" effect="plain">{{ operTypeLabel(row.bizType) }} · 历史</el-tag>
          </el-tooltip>
          <el-tag v-else size="small" :type="operTypeColor(row.bizType)">{{ operTypeLabel(row.bizType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bizKey" label="业务键" min-width="150" show-overflow-tooltip />
      <el-table-column prop="contentHash" label="内容哈希" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <code class="hash-cell">{{ row.contentHash?.substring(0, 24) }}...</code>
        </template>
      </el-table-column>
      <el-table-column prop="txHash" :label="chainMode.onChain ? '交易哈希' : '存证哈希'" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip :content="chainMode.onChain ? row.txHash : `${row.txHash}（本地账本，链上查不到）`" placement="top">
            <code class="hash-cell">{{ row.txHash?.substring(0, 24) }}...</code>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="blockNumber" :label="chainMode.onChain ? '区块高度' : '账本序号'" width="110" align="center">
        <template #default="{ row }">
          <el-tag type="info" size="small">#{{ row.blockNumber }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="存证状态" width="150" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="row.status === 1 && chainMode.onChain && row.chainMode === 'local'"
                      content="该记录写于平台本地账本时期，尚未补写链上，不能按链上存证核验" placement="top">
            <el-tag type="warning" size="small">本地存证（未上链）</el-tag>
          </el-tooltip>
          <el-tag v-else :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'" size="small">
            {{ row.status === 1 ? '已存证' : row.status === 2 ? '失败' : '待存证' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { blockchainApi } from '@/api/modules/blockchain'
import { chainMode, loadChainMode } from '@/utils/chainMode'
import {
  CHAIN_BIZ_TYPE,
  CHAIN_BIZ_TYPE_GROUPS,
  chainBizTypeLabel,
  chainBizTypeColor,
  isLegacyBizType,
} from '@/utils/chainBizType'

const dateRange = ref(null)
const filterForm = reactive({ operType: '' })
const tableData = ref<any[]>([])
const loading = ref(false)
const health = ref<any>({ ok: false, issues: [] })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const stats = reactive({ totalTx: 0, todayTx: 0, pendingTx: 0, latestBlock: 0 })

// 类型字典统一在 @/utils/chainBizType，页面不再各自维护一份
const operTypeLabel = chainBizTypeLabel
const operTypeColor = chainBizTypeColor

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { filterForm.operType = ''; dateRange.value = null; handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

async function fetchList() {
  loading.value = true
  try {
    const res: any = await blockchainApi.getAuditLogs({
      current: pagination.pageNum,
      size: pagination.pageSize,
      operType: filterForm.operType || undefined,
    })
    tableData.value = res?.records || res?.list || []
    pagination.total = res?.total || tableData.value.length || 0
  } catch { tableData.value = []; pagination.total = 0 } finally { loading.value = false }
}

async function fetchStats() {
  try {
    const s: any = await blockchainApi.getAuditStats()
    stats.totalTx = s?.totalTx ?? 0
    stats.todayTx = s?.todayTx ?? 0
    stats.pendingTx = s?.pendingTx ?? 0
    stats.latestBlock = s?.latestBlock ?? 0
  } catch { /* 忽略统计失败 */ }
}

/**
 * 存证体检：对账结果直接取后端，不在前端算 —— 页面算的和定时任务算的必须是同一份口径，
 * 否则「页面正常」和「告警响了」会互相矛盾。
 */
async function fetchHealth() {
  try {
    const res: any = await blockchainApi.getHealth()
    const data = res?.data ?? res
    health.value = {
      ...data,
      checkedAt: data?.checkedAt ? String(data.checkedAt).replace('T', ' ').slice(0, 19) : '',
      issues: Array.isArray(data?.issues) ? data.issues : [],
    }
  } catch {
    health.value = { ok: false, issues: ['存证体检接口不可用'], checkedAt: '' }
  }
}

onMounted(() => { loadChainMode(); fetchList(); fetchStats(); fetchHealth() })
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.stats-bar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; padding: 12px 16px; background: #f5f7fa; border-radius: 6px; }
.stats-hint { margin-left: auto; font-size: 13px; color: #909399; font-family: 'Courier New', monospace; }
.health-card { margin-bottom: 16px; padding: 12px 16px; border-radius: 6px; border: 1px solid #e9e9eb; background: #fafafa;
  &.is-ok { border-color: #e1f3d8; background: #f7fbf3; }
  &.is-bad { border-color: #fde2e2; background: #fff8f8; }
  .health-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
  .health-title { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; color: #303133; }
  .health-state { font-size: 13px; color: #606266; }
  .is-bad .health-state { color: #f56c6c; font-weight: 600; }
  .health-time { margin-left: auto; font-size: 12px; color: #909399; }
  .health-metrics { display: flex; flex-wrap: wrap; gap: 16px; font-size: 12px; color: #606266; margin-bottom: 6px;
    .bad { color: #f56c6c; font-weight: 600; }
  }
  .health-issues { font-size: 12px; color: #e6a23c; line-height: 1.7;
    &.ok-text { color: #67c23a; }
  }
  .health-note { margin-top: 6px; font-size: 12px; color: #909399; }
}
.hash-cell { font-family: 'Courier New', monospace; font-size: 12px; color: #909399; background: #f5f7fa; padding: 1px 6px; border-radius: 3px; }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
</style>
