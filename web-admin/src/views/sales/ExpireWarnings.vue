<template>
  <div class="page-container">
    <!-- 搜索与统计 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.productName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="searchForm.batchNo" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="预警级别">
          <el-select v-model="searchForm.warningLevel" placeholder="全部" clearable style="width: 130px">
            <el-option label="临期3天" :value="1" />
            <el-option label="临期1天" :value="2" />
            <el-option label="已过期" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="searchForm.handled" placeholder="全部" clearable style="width: 120px">
            <el-option label="未处理" :value="0" />
            <el-option label="已处理" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计卡片 -->
    <div class="warning-stats">
      <div class="stat-item stat-red">
        <span class="stat-num">{{ stats.expired }}</span>
        <span class="stat-txt">已过期</span>
      </div>
      <div class="stat-item stat-orange">
        <span class="stat-num">{{ stats.urgent }}</span>
        <span class="stat-txt">临期1天</span>
      </div>
      <div class="stat-item stat-yellow">
        <span class="stat-num">{{ stats.warning }}</span>
        <span class="stat-txt">临期3天</span>
      </div>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="productName" label="产品名称" min-width="150" />
      <el-table-column prop="productQrCode" label="产品二维码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="storeName" label="所在门店" min-width="140" />
      <el-table-column prop="expireDate" label="过期日期" width="120" align="center" />
      <el-table-column prop="daysLeft" label="剩余天数" width="100" align="center">
        <template #default="{ row }">
          <span :class="daysLeftClass(row.daysLeft)">{{ row.daysLeft > 0 ? `${row.daysLeft}天` : '已过期' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="warningLevel" label="预警级别" width="120" align="center">
        <template #default="{ row }">
          <WarningLevelBadge :level="row.warningLevel" />
        </template>
      </el-table-column>
      <el-table-column prop="handled" label="处理状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.handled ? 'success' : 'danger'" size="small">{{ row.handled ? '已处理' : '未处理' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="warningTime" label="预警时间" width="160" align="center" />
      <el-table-column label="操作" width="160" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="!row.handled" type="primary" link size="small" @click="handleDeal(row)">处理</el-button>
          <el-button type="success" link size="small" @click="handleView(row)">追溯</el-button>
          <span v-if="row.handled" class="handled-info">{{ row.handler }} / {{ row.handleTime }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <!-- 处理弹窗 -->
    <el-dialog v-model="dialogVisible" title="处理预警" width="520px" destroy-on-close>
      <el-descriptions :column="1" border size="default" style="margin-bottom: 20px">
        <el-descriptions-item label="产品">{{ currentRow?.productName }}</el-descriptions-item>
        <el-descriptions-item label="批次">{{ currentRow?.productQrCode }}</el-descriptions-item>
        <el-descriptions-item label="过期日期">{{ currentRow?.expireDate }}</el-descriptions-item>
        <el-descriptions-item label="预警级别">
          <WarningLevelBadge v-if="currentRow" :level="currentRow.warningLevel" />
        </el-descriptions-item>
      </el-descriptions>
      <el-form :model="handleForm" label-width="90px">
        <el-form-item label="处理方式" required>
          <el-radio-group v-model="handleForm.action">
            <el-radio value="off_shelf">确认下架</el-radio>
            <el-radio value="continue_sale">继续销售</el-radio>
            <el-radio value="dispose">报废处理</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="handleForm.note" type="textarea" :rows="3" placeholder="请输入处理备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()

// 内联预警级别徽章组件
const WarningLevelBadge = {
  props: { level: Number },
  setup(props: { level: number }) {
    const map: Record<number, { label: string; color: string; bg: string }> = {
      1: { label: '🟡 临期3天', color: '#e6a23c', bg: '#fdf6ec' },
      2: { label: '🟠 临期1天', color: '#f56c6c', bg: '#fef0f0' },
      3: { label: '🔴 已过期', color: '#fff', bg: '#f56c6c' },
    }
    const info = map[props.level] || { label: '未知', color: '#909399', bg: '#f5f7fa' }
    return () => h('span', {
      style: { display: 'inline-block', padding: '2px 10px', borderRadius: '12px', fontSize: '12px', fontWeight: '500',
        color: info.color, background: info.bg, border: `1px solid ${info.color}20` }
    }, info.label)
  },
}

const searchForm = reactive({ productName: '', batchNo: '', warningLevel: null as number | null, handled: null as number | null })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const stats = reactive({ expired: 8, urgent: 12, warning: 25 })

const dialogVisible = ref(false)
const currentRow = ref<any>(null)
const submitting = ref(false)
const handleForm = reactive({ action: 'off_shelf', note: '' })

function daysLeftClass(d: number) {
  if (d <= 0) return 'days-expired'
  if (d <= 1) return 'days-urgent'
  return 'days-warning'
}

function handleDeal(row: any) {
  currentRow.value = row
  handleForm.action = 'off_shelf'
  handleForm.note = ''
  dialogVisible.value = true
}

function handleView(row: any) {
  router.push('/admin/trace/search')
}

async function handleSubmit() {
  submitting.value = true
  try {
    if (currentRow.value) {
      currentRow.value.handled = 1
      currentRow.value.handler = '当前用户'
      currentRow.value.handleTime = new Date().toLocaleString()
    }
    ElMessage.success('预警处理成功')
    dialogVisible.value = false
  } finally { submitting.value = false }
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { Object.assign(searchForm, { productName: '', batchNo: '', warningLevel: null, handled: null }); handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function fetchList() {
  loading.value = true
  const products = ['猪前腿肉 500g', '猪五花肉 300g', '猪里脊 400g', '猪排骨 600g', '猪蹄 500g', '猪肝 200g', '猪肚 350g']
  const stores = ['XX社区超市', 'YY生鲜店', 'ZZ便利店', 'WW农贸市场', 'UU超市']
  const list = Array.from({ length: 45 }, (_, i) => ({
    id: i + 1,
    productName: products[i % products.length],
    productQrCode: `QR-PORK-202407${String(1500 + i).padStart(4, '0')}`,
    storeName: stores[i % stores.length],
    expireDate: `2024-07-${String(15 + Math.floor(i / 5)).padStart(2, '0')}`,
    daysLeft: 2 - Math.floor(i / 5),
    warningLevel: i < 25 ? 1 : i < 37 ? 2 : 3,
    handled: i < 38 ? 0 : 1,
    handler: i >= 38 ? '管理员' : '',
    handleTime: i >= 38 ? `2024-07-${String(14 + i % 3).padStart(2, '0')} 14:00:00` : '',
    warningTime: `2024-07-${String(12 + Math.floor(i / 3)).padStart(2, '0')} ${String(8 + i % 12).padStart(2, '0')}:00:00`,
  }))
  const filtered = list.filter(item => {
    if (searchForm.productName && !item.productName.includes(searchForm.productName)) return false
    if (searchForm.batchNo && !item.productQrCode.includes(searchForm.batchNo)) return false
    if (searchForm.warningLevel && item.warningLevel !== searchForm.warningLevel) return false
    if (searchForm.handled !== null && item.handled !== searchForm.handled) return false
    return true
  })
  stats.expired = filtered.filter(i => i.warningLevel === 3).length
  stats.urgent = filtered.filter(i => i.warningLevel === 2).length
  stats.warning = filtered.filter(i => i.warningLevel === 1).length
  tableData.value = filtered.slice((pagination.pageNum - 1) * pagination.pageSize, pagination.pageNum * pagination.pageSize)
  pagination.total = filtered.length
  loading.value = false
}

onMounted(() => fetchList())
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.warning-stats { display: flex; gap: 16px; margin-bottom: 20px; }
.stat-item { flex: 1; display: flex; align-items: center; gap: 12px; padding: 16px 20px; border-radius: 8px;
  &.stat-red { background: #fef0f0; .stat-num { color: #f56c6c; } }
  &.stat-orange { background: #fdf6ec; .stat-num { color: #e6a23c; } }
  &.stat-yellow { background: #fdf6ec; .stat-num { color: #e6a23c; } }
}
.stat-num { font-size: 28px; font-weight: 700; }
.stat-txt { font-size: 14px; color: #606266; }
.days-expired { color: #f56c6c; font-weight: 700; }
.days-urgent { color: #e6a23c; font-weight: 600; }
.days-warning { color: #e6a23c; }
.handled-info { font-size: 12px; color: #909399; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
</style>
