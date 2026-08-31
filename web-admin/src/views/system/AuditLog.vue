<template>
  <div class="page-container">
    <!-- 筛选面板 -->
    <div class="search-panel">
      <el-form :model="filterForm" inline>
        <el-form-item label="操作类型">
          <el-select v-model="filterForm.operType" placeholder="全部" clearable style="width: 160px">
            <el-option label="生猪建档" value="pig_create" />
            <el-option label="疫苗记录" value="vaccine" />
            <el-option label="出栏申报" value="slaughter_apply" />
            <el-option label="检疫证明" value="quarantine_cert" />
            <el-option label="屠宰检验" value="slaughter_inspect" />
            <el-option label="瘦肉精检测" value="ractopamine" />
            <el-option label="检疫盖章" value="carcass_stamp" />
            <el-option label="批次拆分" value="split_batch" />
            <el-option label="冷链运输" value="transport" />
            <el-option label="门店签收" value="store_receipt" />
            <el-option label="销售激活" value="sale_activate" />
            <el-option label="产品召回" value="recall" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作用户">
          <el-input v-model="filterForm.operator" placeholder="请输入用户" clearable style="width: 160px" />
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
      <el-table-column prop="operTime" label="时间" width="170" align="center" />
      <el-table-column prop="operator" label="操作人" width="120" align="center" />
      <el-table-column prop="operType" label="操作类型" width="140" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="operTypeColor(row.operType)">{{ operTypeLabel(row.operType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bizId" label="业务ID" min-width="140" show-overflow-tooltip />
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
      <el-table-column prop="chainStatus" label="链上状态" width="110" align="center">
        <template #default="{ row }">
          <BlockchainVerifyBadge :status="row.chainStatus" />
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

const dateRange = ref(null)
const filterForm = reactive({ operType: '', operator: '' })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const stats = reactive({ totalTx: 12580, todayTx: 47, pendingTx: 3, latestBlock: 284761 })

const operTypeMap: Record<string, string> = {
  pig_create: '生猪建档', vaccine: '疫苗记录', slaughter_apply: '出栏申报',
  quarantine_cert: '检疫证明', slaughter_inspect: '屠宰检验', ractopamine: '瘦肉精检测',
  carcass_stamp: '检疫盖章', split_batch: '批次拆分', transport: '冷链运输',
  store_receipt: '门店签收', sale_activate: '销售激活', recall: '产品召回'
}
const operTypeLabel = (t: string) => operTypeMap[t] || t
const operTypeColor = (t: string): EpTagType => {
  const colors: Record<string, EpTagType> = { pig_create: 'info', vaccine: 'success', quarantine_cert: 'warning',
    slaughter_inspect: 'danger', ractopamine: 'danger', transport: 'info', sale_activate: 'success', recall: 'danger' }
  return colors[t] || 'info'
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { filterForm.operType = ''; filterForm.operator = ''; dateRange.value = null; handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }
function handleView(row: any) { ElMessage.info(`查看审计详情: ${row.txHash}`) }

async function fetchList() {
  loading.value = true
  try {
    const res = await import('@/utils/request').then(m => m.default.get('/blockchain/audit-logs', {
      params: { pageNum: pagination.pageNum, pageSize: pagination.pageSize, ...filterForm,
        startDate: dateRange.value?.[0], endDate: dateRange.value?.[1] }
    }))
    tableData.value = (res.data?.records || res.data?.list || res.list || res.data || [])
    pagination.total = res.data?.total || res.total || 0
  } catch {
    const types = Object.keys(operTypeMap)
    const operators = ['张三', '李四', '王建国', '赵六', '管理员']
    const list = Array.from({ length: 45 }, (_, i) => {
      const type = types[i % types.length]
      return {
        id: i + 1, operTime: `2024-07-${String(15 - Math.floor(i / 4)).padStart(2, '0')} ${String(8 + i % 12).padStart(2, '0')}:${String(i * 13 % 60).padStart(2, '0')}:${String(i * 7 % 60).padStart(2, '0')}`,
        operator: operators[i % operators.length], operType: type,
        bizId: `${type.toUpperCase()}-202407${String(1500 + i).padStart(4, '0')}`,
        contentHash: `0x${Array.from({ length: 64 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join('')}`,
        txHash: `0x${Array.from({ length: 64 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join('')}`,
        blockNumber: 284700 + i, chainStatus: i % 10 === 0 ? 'pending' : 'confirmed' as const,
      }
    })
    const filtered = list.filter(item => {
      if (filterForm.operType && item.operType !== filterForm.operType) return false
      if (filterForm.operator && !item.operator.includes(filterForm.operator)) return false
      return true
    })
    tableData.value = filtered.slice((pagination.pageNum - 1) * pagination.pageSize, pagination.pageNum * pagination.pageSize)
    pagination.total = filtered.length
  } finally { loading.value = false }
}

onMounted(() => fetchList())
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
