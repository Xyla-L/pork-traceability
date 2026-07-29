<template>
  <div class="page-container">
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="运单号">
          <el-input v-model="searchForm.transportNo" placeholder="请输入" clearable @keyup.enter="handleSearch" style="width: 160px" />
        </el-form-item>
        <el-form-item label="门店名称">
          <el-input v-model="searchForm.storeName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="签收时间">
          <el-date-picker v-model="receiptDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="签收状态">
          <el-select v-model="searchForm.qtyCheck" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部正常" :value="1" />
            <el-option label="数量异常" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="receiptNo" label="签收单号" width="180" />
      <el-table-column prop="transportNo" label="关联运单" width="160" />
      <el-table-column prop="storeName" label="签收门店" min-width="150" />
      <el-table-column prop="receiver" label="签收人" width="100" align="center" />
      <el-table-column prop="receiptTime" label="签收时间" width="170" align="center" />
      <el-table-column prop="qtyCheck" label="数量核验" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.qtyCheck === 1 ? 'success' : 'danger'" size="small">{{ row.qtyCheck === 1 ? '✅ 正常' : '⚠️ 异常' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tempCheck" label="温度核验" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.tempCheck === 1 ? 'success' : 'danger'" size="small">{{ row.tempCheck === 1 ? '✅ 正常' : '⚠️ 异常' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="packageIntact" label="包装状态" width="100" align="center">
        <template #default="{ row }">
          <span :style="{ color: row.packageIntact === 1 ? '#67c23a' : '#f56c6c' }">{{ row.packageIntact === 1 ? '✅ 完好' : '⚠️ 破损' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="chainStatus" label="链上状态" width="100" align="center">
        <template #default="{ row }">
          <BlockchainVerifyBadge :status="row.chainStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          <el-button type="success" link size="small" @click="handleVerify(row)">验真</el-button>
          <el-button type="warning" link size="small" @click="handlePrint(row)">打印回单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <!-- 签收弹窗（模拟门店端操作） -->
    <el-dialog v-model="dialogVisible" title="门店签收确认" width="620px" destroy-on-close>
      <el-descriptions :column="2" border size="default" style="margin-bottom: 20px">
        <el-descriptions-item label="运单号">{{ receiptData.transportNo }}</el-descriptions-item>
        <el-descriptions-item label="配送车辆">{{ receiptData.vehicleNo }}</el-descriptions-item>
        <el-descriptions-item label="发货地">{{ receiptData.origin }}</el-descriptions-item>
        <el-descriptions-item label="目的地">{{ receiptData.destination }}</el-descriptions-item>
        <el-descriptions-item label="司机">{{ receiptData.driverName }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ receiptData.driverPhone }}</el-descriptions-item>
      </el-descriptions>
      <div class="receipt-checks">
        <!-- 数量核验 -->
        <el-card shadow="hover" class="check-card">
          <template #header><span class="check-title">📦 数量核验</span></template>
          <el-form label-width="80px" size="default">
            <el-form-item label="实收数量">
              <el-input-number v-model="receiptData.qtyReceived" :min="0" style="width: 160px" />
            </el-form-item>
            <el-form-item label="应到数量">{{ receiptData.qtyExpected }}</el-form-item>
            <el-form-item label="核验结果">
              <el-tag :type="receiptData.qtyReceived === receiptData.qtyExpected ? 'success' : 'danger'">
                {{ receiptData.qtyReceived === receiptData.qtyExpected ? '✅ 数量一致' : '⚠️ 数量不符' }}
              </el-tag>
            </el-form-item>
          </el-form>
        </el-card>
        <!-- 温度核验 -->
        <el-card shadow="hover" class="check-card">
          <template #header><span class="check-title">🌡️ 温度核验</span></template>
          <el-form label-width="80px" size="default">
            <el-form-item label="到货温度">
              <el-input-number v-model="receiptData.arrivalTemp" :min="-50" :max="50" :precision="1" style="width: 160px" />
              <span class="unit-hint" style="margin-left: 6px">℃</span>
            </el-form-item>
            <el-form-item label="温度状态">
              <el-tag :type="receiptData.arrivalTemp >= -18 && receiptData.arrivalTemp <= 0 ? 'success' : 'danger'">
                {{ receiptData.arrivalTemp >= -18 && receiptData.arrivalTemp <= 0 ? '✅ 在正常范围' : '⚠️ 温度异常' }}
              </el-tag>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
      <!-- 签名 -->
      <el-card shadow="hover" class="check-card signature-card">
        <template #header><span class="check-title">✍️ 签收人签名</span></template>
        <div class="signature-area">
          <canvas ref="signCanvasRef" width="400" height="120" class="sign-canvas"
            @mousedown="startSign" @mousemove="drawSign" @mouseup="endSign" @mouseleave="endSign"></canvas>
          <div class="sign-actions">
            <el-button size="small" @click="clearSign">清除</el-button>
            <span class="sign-hint">请在框内签名</span>
          </div>
        </div>
      </el-card>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReceipt" :loading="submitting">确认签收</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'

const searchForm = reactive({ transportNo: '', storeName: '', qtyCheck: null as number | null })
const receiptDate = ref<string | null>(null)
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

// 签收弹窗
const dialogVisible = ref(false)
const submitting = ref(false)
const receiptData = reactive({ transportNo: '', vehicleNo: '', origin: '', destination: '', driverName: '', driverPhone: '',
  qtyExpected: 120, qtyReceived: 120, arrivalTemp: -12.5 })

// 签名
const signCanvasRef = ref<HTMLCanvasElement>()
let isDrawing = false
let ctx: CanvasRenderingContext2D | null = null

function startSign(e: MouseEvent) {
  if (!signCanvasRef.value) return
  ctx = signCanvasRef.value.getContext('2d')
  if (!ctx) return
  isDrawing = true
  const rect = signCanvasRef.value.getBoundingClientRect()
  ctx.beginPath()
  ctx.moveTo(e.clientX - rect.left, e.clientY - rect.top)
  ctx.strokeStyle = '#333'
  ctx.lineWidth = 2
}
function drawSign(e: MouseEvent) {
  if (!isDrawing || !ctx || !signCanvasRef.value) return
  const rect = signCanvasRef.value.getBoundingClientRect()
  ctx.lineTo(e.clientX - rect.left, e.clientY - rect.top)
  ctx.stroke()
}
function endSign() { isDrawing = false; ctx = null }
function clearSign() {
  if (!signCanvasRef.value) return
  const c = signCanvasRef.value.getContext('2d')
  if (c) c.clearRect(0, 0, 400, 120)
}

function confirmReceipt() {
  submitting.value = true
  setTimeout(() => {
    ElMessage.success('门店签收确认成功，数据已上链')
    dialogVisible.value = false
    submitting.value = false
  }, 800)
}

function handleView(row: any) { ElMessage.info(`查看签收详情: ${row.receiptNo}`) }
function handleVerify(row: any) { ElMessage.success(`区块链验真通过: ${row.receiptNo}`) }
function handlePrint(row: any) { ElMessage.success(`回单 ${row.receiptNo} 已发送打印`) }

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { Object.assign(searchForm, { transportNo: '', storeName: '', qtyCheck: null }); receiptDate.value = null; handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function fetchList() {
  loading.value = true
  const stores = ['XX社区超市', 'YY生鲜店', 'ZZ便利店', 'WW农贸市场', 'UU超市']
  const list = Array.from({ length: 30 }, (_, i) => ({
    id: i + 1, receiptNo: `S202407${String(1500 + i).padStart(4, '0')}`,
    transportNo: `T202407${String(1400 + i).padStart(4, '0')}`,
    storeName: stores[i % stores.length], receiver: ['钱店长', '吴店长', '郑店长', '孙店长'][i % 4],
    receiptTime: `2024-07-${String(3 + i).padStart(2, '0')} ${String(14 + i % 8).padStart(2, '0')}:30:00`,
    qtyCheck: i < 27 ? 1 : 0, tempCheck: i < 28 ? 1 : 0, packageIntact: i < 25 ? 1 : 0,
    chainStatus: i % 5 === 0 ? 'pending' : 'confirmed' as const,
  }))
  const filtered = list.filter(item => {
    if (searchForm.transportNo && !item.transportNo.includes(searchForm.transportNo)) return false
    if (searchForm.storeName && !item.storeName.includes(searchForm.storeName)) return false
    if (searchForm.qtyCheck !== null && item.qtyCheck !== searchForm.qtyCheck) return false
    return true
  })
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
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.receipt-checks { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.check-card { border: 1px solid #ebeef5; }
.check-title { font-size: 14px; font-weight: 600; }
.signature-card { margin-bottom: 0; }
.signature-area { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.sign-canvas { border: 1px dashed #dcdfe6; border-radius: 6px; cursor: crosshair; background: #fafafa; }
.sign-actions { display: flex; align-items: center; gap: 12px; }
.sign-hint { font-size: 12px; color: #c0c4cc; }
.unit-hint { font-size: 13px; color: #909399; }
</style>
