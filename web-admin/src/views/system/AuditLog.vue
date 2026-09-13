<template>
  <div class="page-container">
    <!-- 筛选面板 -->
    <div class="search-panel">
      <el-form :model="filterForm" inline>
        <el-form-item label="业务类型">
          <el-select v-model="filterForm.operType" placeholder="全部" clearable style="width: 160px">
            <el-option label="批次拆分" value="SPLIT_BATCH" />
            <el-option label="销售激活" value="RETAIL_SALE" />
            <el-option label="门店签收" value="STORE_RECEIPT" />
            <el-option label="产品召回" value="RECALL_ORDER" />
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
      <el-tag type="success" size="large">总上链数: {{ stats.totalTx }}</el-tag>
      <el-tag type="info" size="large">今日新增: {{ stats.todayTx }}</el-tag>
      <el-tag type="warning" size="large">今日待确认: {{ stats.pendingTx }}</el-tag>
      <span class="stats-hint">区块高度: #{{ stats.latestBlock }}</span>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="chainTime" label="时间" width="170" align="center" />
      <el-table-column prop="bizType" label="业务类型" width="140" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="operTypeColor(row.bizType)">{{ operTypeLabel(row.bizType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bizKey" label="业务键" min-width="150" show-overflow-tooltip />
      <el-table-column prop="contentHash" label="内容哈希" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <code class="hash-cell">{{ row.contentHash?.substring(0, 24) }}...</code>
        </template>
      </el-table-column>
      <el-table-column prop="txHash" label="交易哈希" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip :content="row.txHash" placement="top">
            <code class="hash-cell">{{ row.txHash?.substring(0, 24) }}...</code>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="blockNumber" label="区块高度" width="110" align="center">
        <template #default="{ row }">
          <el-tag type="info" size="small">#{{ row.blockNumber }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="链上状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'" size="small">
            {{ row.status === 1 ? '已上链' : row.status === 2 ? '失败' : '待上链' }}
          </el-tag>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'
import type { EpTagType } from '@/types/common'
import { blockchainApi } from '@/api/modules/blockchain'

const dateRange = ref(null)
const filterForm = reactive({ operType: '' })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const stats = reactive({ totalTx: 0, todayTx: 0, pendingTx: 0, latestBlock: 0 })

const operTypeMap: Record<string, string> = {
  SPLIT_BATCH: '批次拆分', RETAIL_SALE: '销售激活', STORE_RECEIPT: '门店签收', RECALL_ORDER: '产品召回',
}
const operTypeLabel = (t: string) => operTypeMap[t] || t
const operTypeColor = (t: string): EpTagType => {
  const colors: Record<string, EpTagType> = { SPLIT_BATCH: 'info', RETAIL_SALE: 'success', STORE_RECEIPT: 'warning', RECALL_ORDER: 'danger' }
  return colors[t] || 'info'
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { filterForm.operType = ''; dateRange.value = null; handleSearch() }
function handleView(row: any) { ElMessage.info(`存证详情: ${row.txHash}`) }
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

onMounted(() => { fetchList(); fetchStats() })
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.stats-bar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; padding: 12px 16px; background: #f5f7fa; border-radius: 6px; }
.stats-hint { margin-left: auto; font-size: 13px; color: #909399; font-family: 'Courier New', monospace; }
.hash-cell { font-family: 'Courier New', monospace; font-size: 12px; color: #909399; background: #f5f7fa; padding: 1px 6px; border-radius: 3px; }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
</style>
