<template>
  <div class="page-container">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.productName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="searchForm.batchNo" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="门店">
          <el-input v-model="searchForm.storeName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="在售" :value="1" />
            <el-option label="已售" :value="2" />
            <el-option label="已过期" :value="3" />
            <el-option label="已召回" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售日期">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="productQrCode" label="产品二维码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="productName" label="产品名称" min-width="140" />
      <el-table-column prop="storeName" label="销售门店" min-width="140" />
      <el-table-column prop="sellTime" label="销售时间" width="160" align="center" />
      <el-table-column prop="sellPrice" label="售价(元)" width="100" align="center">
        <template #default="{ row }">¥{{ row.sellPrice?.toFixed?.(2) || row.sellPrice }}</template>
      </el-table-column>
      <el-table-column prop="sellWeightKg" label="重量(kg)" width="100" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="saleStatusType(row.status)" size="small">{{ saleStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
          <el-button v-if="row.status === 2" type="success" link size="small" @click="handleVerify(row)">
            验真
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
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
import type { EpTagType } from '@/types/common'

const dateRange = ref(null)
const searchForm = reactive({ productName: '', batchNo: '', storeName: '', status: null as number | null })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const saleStatusType = (s: number): EpTagType => (({ 1: 'success', 2: 'info', 3: 'danger', 4: 'warning' } as Record<number, EpTagType>)[s] || 'info')
const saleStatusLabel = (s: number) => ({ 1: '在售', 2: '已售', 3: '已过期', 4: '已召回' } as Record<number, string>)[s] || '未知'

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() {
  searchForm.productName = ''; searchForm.batchNo = ''; searchForm.storeName = ''
  searchForm.status = null; dateRange.value = null; handleSearch()
}
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function handleView(row: any) {
  ElMessage.info(`查看销售详情: ${row.productQrCode}`)
}
function handleVerify(row: any) {
  ElMessage.success(`区块链验证通过: 批次 ${row.productQrCode}`)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await import('@/utils/request').then(m => m.default.get('/sales/records', {
      params: { pageNum: pagination.pageNum, pageSize: pagination.pageSize, ...searchForm,
        startDate: dateRange.value?.[0], endDate: dateRange.value?.[1] }
    }))
    tableData.value = res.data?.records || res.data?.list || res.list || []
    pagination.total = res.data?.total || res.total || 0
  } catch {
    // 模拟数据
    const mockNames = ['猪前腿肉 500g', '猪五花肉 300g', '猪里脊 400g', '猪排骨 600g', '猪蹄 500g']
    const mockStores = ['XX社区超市', 'YY生鲜店', 'ZZ便利店', 'WW农贸市场']
    const list = Array.from({ length: 25 }, (_, i) => ({
      id: i + 1, productQrCode: `QR-PORK-20240715${String(i + 1).padStart(3, '0')}`,
      productName: mockNames[i % mockNames.length], splitBatchId: 100 + i,
      storeName: mockStores[i % mockStores.length],
      sellTime: `2024-07-${String(15 - Math.floor(i / 3)).padStart(2, '0')} ${String(8 + i % 10).padStart(2, '0')}:${String(i * 7 % 60).padStart(2, '0')}`,
      sellPrice: 15 + (i * 7) % 50, sellWeightKg: 0.3 + (i % 5) * 0.2,
      status: i < 18 ? 2 : i < 22 ? 1 : i < 24 ? 3 : 4,
      expireDate: `2024-07-${String(18 + i % 10).padStart(2, '0')}`,
      createTime: `2024-07-${String(10 + i).padStart(2, '0')} 10:00:00`
    }))
    const filtered = list.filter(item => {
      if (searchForm.productName && !item.productName.includes(searchForm.productName)) return false
      if (searchForm.batchNo && !item.productQrCode.includes(searchForm.batchNo)) return false
      if (searchForm.storeName && !item.storeName.includes(searchForm.storeName)) return false
      if (searchForm.status && item.status !== searchForm.status) return false
      return true
    })
    tableData.value = filtered.slice((pagination.pageNum - 1) * pagination.pageSize, pagination.pageNum * pagination.pageSize)
    pagination.total = filtered.length
  } finally {
    loading.value = false
  }
}

onMounted(() => fetchList())
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
</style>
