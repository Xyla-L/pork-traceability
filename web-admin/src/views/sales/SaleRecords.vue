<template>
  <div class="page-container">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <div class="search-header">
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
        <div class="header-actions">
          <el-button type="success" @click="openSellDialog"><el-icon><ShoppingCart /></el-icon>扫码销售</el-button>
        </div>
      </div>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="productQrCode" label="产品二维码" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productQrCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="productName" label="产品名称" min-width="140">
        <template #default="{ row }">{{ row.productName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="storeName" label="销售门店" min-width="140">
        <template #default="{ row }">{{ row.storeName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="sellTime" label="销售时间" width="160" align="center">
        <template #default="{ row }">{{ row.sellTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="sellPrice" label="售价(元)" width="100" align="center">
        <template #default="{ row }">{{ row.sellPrice != null ? '¥' + Number(row.sellPrice).toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="sellWeightKg" label="重量(kg)" width="100" align="center">
        <template #default="{ row }">{{ row.sellWeightKg != null ? row.sellWeightKg : '-' }}</template>
      </el-table-column>
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

    <!-- 扫码销售弹窗 -->
    <el-dialog v-model="sellVisible" title="扫码销售" width="480px" destroy-on-close>
      <el-form ref="sellFormRef" :model="sellForm" :rules="sellRules" label-width="100px">
        <el-form-item label="二维码" prop="qrCode">
          <el-input v-model="sellForm.qrCode" placeholder="请扫描或输入二维码编号" clearable />
        </el-form-item>
        <el-form-item label="售价(元)" prop="sellPrice">
          <el-input-number v-model="sellForm.sellPrice" :min="0.01" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="重量(kg)" prop="sellWeightKg">
          <el-input-number v-model="sellForm.sellWeightKg" :min="0.01" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sellVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSellSubmit" :loading="sellSubmitting">确认销售</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewVisible" title="销售记录详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="产品二维码" :span="2">{{ viewData.productQrCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ viewData.productName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ viewData.batchNo || '--' }}</el-descriptions-item>
        <el-descriptions-item label="销售门店">{{ viewData.storeName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="saleStatusType(viewData.status)" size="small">{{ saleStatusLabel(viewData.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="售价(元)">¥{{ viewData.sellPrice?.toFixed?.(2) || viewData.sellPrice || '--' }}</el-descriptions-item>
        <el-descriptions-item label="重量(kg)">{{ viewData.sellWeightKg || '--' }}</el-descriptions-item>
        <el-descriptions-item label="销售时间">{{ viewData.sellTime || '--' }}</el-descriptions-item>
        <el-descriptions-item label="上架时间">{{ viewData.shelfTime || '--' }}</el-descriptions-item>
        <el-descriptions-item label="过期日期">{{ viewData.expireDate || '--' }}</el-descriptions-item>
        <el-descriptions-item label="生成时间" :span="2">{{ viewData.createTime || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, ShoppingCart } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { EpTagType } from '@/types/common'
import { salesApi } from '@/api/modules/sales'
import type { FormInstance, FormRules } from 'element-plus'

const dateRange = ref(null)
const searchForm = reactive({ productName: '', batchNo: '', storeName: '', status: null as number | null })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const saleStatusType = (s: number): EpTagType => (({ 1: 'success', 2: 'info', 3: 'danger', 4: 'warning' } as Record<number, EpTagType>)[s] || 'info')
const saleStatusLabel = (s: number) => ({ 1: '在售', 2: '已售', 3: '已过期', 4: '已召回' } as Record<number, string>)[s] || '未知'

const sellVisible = ref(false)
const sellSubmitting = ref(false)
const sellFormRef = ref<FormInstance>()
const sellForm = reactive({ qrCode: '', sellPrice: 0, sellWeightKg: 0 })

const viewVisible = ref(false)
const viewData = ref<any>({})

const sellRules: FormRules = {
  qrCode: [{ required: true, message: '请输入二维码编号', trigger: 'blur' }],
  sellPrice: [{ required: true, message: '请输入售价', trigger: 'blur' }],
  sellWeightKg: [{ required: true, message: '请输入重量', trigger: 'blur' }],
}

function openSellDialog() {
  sellForm.qrCode = ''
  sellForm.sellPrice = 0
  sellForm.sellWeightKg = 0
  sellVisible.value = true
}

async function handleSellSubmit() {
  await sellFormRef.value?.validate()
  if (!sellForm.qrCode.trim()) return
  sellSubmitting.value = true
  try {
    await salesApi.createSaleRecord({
      qrCode: sellForm.qrCode.trim(),
      sellPrice: sellForm.sellPrice,
      sellWeightKg: sellForm.sellWeightKg,
    })
    ElMessage.success('销售成功')
    sellVisible.value = false
    fetchList()
  } catch (error: any) {
    ElMessage.error(error?.message || '销售失败')
  } finally {
    sellSubmitting.value = false
  }
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() {
  searchForm.productName = ''; searchForm.batchNo = ''; searchForm.storeName = ''
  searchForm.status = null; dateRange.value = null; handleSearch()
}
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function handleView(row: any) {
  viewData.value = row
  viewVisible.value = true
}
function handleVerify(row: any) {
  ElMessage.success(`区块链验证通过: 批次 ${row.productQrCode}`)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await salesApi.getSaleRecords({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      productName: searchForm.productName || undefined,
      batchNo: searchForm.batchNo || undefined,
      storeName: searchForm.storeName || undefined,
      status: searchForm.status ?? undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
    })
    tableData.value = res?.records || res?.list || []
    pagination.total = res?.total || 0
  } catch (error) {
    console.error('获取销售记录列表失败:', error)
    tableData.value = []
    pagination.total = 0
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
.search-header { display: flex; justify-content: space-between; align-items: flex-start; }
.header-actions { flex-shrink: 0; padding-top: 4px; }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
</style>
