<template>
  <div class="page-container">
    <!-- 搜索 & 状态筛选 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="举报编号">
          <el-input v-model="searchForm.reportNo" placeholder="请输入" clearable @keyup.enter="handleSearch" style="width: 160px" />
        </el-form-item>
        <el-form-item label="举报人">
          <el-input v-model="searchForm.reporterName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="关联批次">
          <el-input v-model="searchForm.targetBatch" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待受理" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已办结" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计标签 -->
    <div class="status-tabs">
      <el-radio-group v-model="statusTab" size="default" @change="handleStatusTabChange">
        <el-radio-button :value="-1">全部 ({{ totalCount }})</el-radio-button>
        <el-radio-button :value="0">待受理 ({{ counts[0] }})</el-radio-button>
        <el-radio-button :value="1">处理中 ({{ counts[1] }})</el-radio-button>
        <el-radio-button :value="2">已办结 ({{ counts[2] }})</el-radio-button>
        <el-radio-button :value="3">已驳回 ({{ counts[3] }})</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="reportNo" label="举报编号" width="180" />
      <el-table-column prop="reporterName" label="举报人" width="120" align="center">
        <template #default="{ row }">
          {{ row.reporterName || '匿名用户' }}
        </template>
      </el-table-column>
      <el-table-column prop="targetBatch" label="关联批次" width="180" />
      <el-table-column prop="complaintText" label="内容摘要" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="complaintStatusType(row.status)" size="small">{{ complaintStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="举报时间" width="170" align="center" />
      <el-table-column prop="handler" label="处理人" width="100" align="center" />
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          <el-button v-if="row.status === 0 || row.status === 1" type="success" link size="small" @click="handleDeal(row)">
            {{ row.status === 0 ? '受理' : '办结' }}
          </el-button>
          <el-button v-if="row.status === 0" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
          <el-button type="info" link size="small" @click="goToTrace(row)">追溯</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="举报详情" size="520px">
      <template v-if="currentRow">
        <el-descriptions :column="1" border size="default" style="margin-bottom: 20px">
          <el-descriptions-item label="举报编号">{{ currentRow.reportNo }}</el-descriptions-item>
          <el-descriptions-item label="举报人">{{ currentRow.reporterName || '匿名用户' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ currentRow.reporterPhone || '未提供' }}</el-descriptions-item>
          <el-descriptions-item label="关联二维码">{{ currentRow.targetQrCode }}</el-descriptions-item>
          <el-descriptions-item label="关联批次">{{ currentRow.targetBatch }}</el-descriptions-item>
          <el-descriptions-item label="举报时间">{{ currentRow.createTime }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="complaintStatusType(currentRow.status)">{{ complaintStatusLabel(currentRow.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div class="complaint-text-section">
          <h4>📝 举报内容</h4>
          <p class="complaint-text">{{ currentRow.complaintText }}</p>
        </div>
        <div v-if="currentRow.photos?.length" class="photo-section">
          <h4>📷 举报照片 ({{ currentRow.photos.length }})</h4>
          <div class="photo-grid">
            <div v-for="(photo, idx) in currentRow.photos" :key="idx" class="photo-item"
              :style="{ background: '#f5f7fa', height: '120px', display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: '6px', cursor: 'pointer' }"
              @click="previewPhoto(photo)">
              <el-icon :size="32" color="#909399"><PictureFilled /></el-icon>
            </div>
          </div>
        </div>
        <div v-if="currentRow.handleNote" class="handle-note-section">
          <h4>📋 处理记录</h4>
          <el-alert :title="`处理人: ${currentRow.handler}`" :description="`${currentRow.handleNote} (${currentRow.handleTime})`"
            :type="currentRow.status === 2 ? 'success' : 'warning'" :closable="false" show-icon />
        </div>
        <div class="drawer-actions">
          <el-button type="primary" @click="goToTrace(currentRow)">跳转追溯查询</el-button>
          <el-button v-if="currentRow.status === 0" type="success" @click="drawerVisible = false; handleDeal(currentRow)">受理</el-button>
          <el-button v-if="currentRow.status === 1" type="success" @click="drawerVisible = false; handleDeal(currentRow)">办结</el-button>
          <el-button v-if="currentRow.status === 0" type="danger" @click="drawerVisible = false; handleReject(currentRow)">驳回</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 处理弹窗 (受理/办结) -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close>
      <el-form :model="handleForm" label-width="80px">
        <el-form-item label="处理方式" required>
          <el-radio-group v-model="handleForm.action">
            <el-radio value="accept" v-if="currentRow?.status === 0">受理</el-radio>
            <el-radio value="complete" v-if="currentRow?.status === 1">办结</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理回复" required>
          <el-input v-model="handleForm.note" type="textarea" :rows="4" placeholder="请输入处理回复内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDeal" :loading="submitting">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh, PictureFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import type { EpTagType } from '@/types/common'

const router = useRouter()
const searchForm = reactive({ reportNo: '', reporterName: '', targetBatch: '', status: null as number | null })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const statusTab = ref(-1)

const counts = computed(() => {
  const c: Record<number, number> = { 0: 0, 1: 0, 2: 0, 3: 0 }
  tableData.value.forEach(item => { if (c[item.status] !== undefined) c[item.status]++ })
  return c
})
const totalCount = computed(() => tableData.value.length)

const complaintStatusType = (s: number): EpTagType => (({ 0: 'danger', 1: 'warning', 2: 'success', 3: 'info' } as Record<number, EpTagType>)[s] || 'info')
const complaintStatusLabel = (s: number) => ({ 0: '待受理', 1: '处理中', 2: '已办结', 3: '已驳回' } as Record<number, string>)[s] || ''

// 详情抽屉
const drawerVisible = ref(false)
const currentRow = ref<any>(null)

// 处理弹窗
const dialogVisible = ref(false)
const submitting = ref(false)
const handleForm = reactive({ action: '', note: '' })
const dialogTitle = computed(() => {
  if (!currentRow.value) return '处理举报'
  return currentRow.value.status === 0 ? '受理举报' : '办结举报'
})

function handleView(row: any) { currentRow.value = row; drawerVisible.value = true }
function handleDeal(row: any) { currentRow.value = row; handleForm.action = row.status === 0 ? 'accept' : 'complete'; handleForm.note = ''; dialogVisible.value = true }
function handleReject(row: any) { currentRow.value = row; handleForm.action = 'reject'; handleForm.note = ''; dialogVisible.value = true }
function previewPhoto(_photo: any) { ElMessage.info('查看照片大图') }
function goToTrace(row: any) { router.push(`/admin/trace/search`) }

function submitDeal() {
  submitting.value = true
  setTimeout(() => {
    if (currentRow.value) {
      if (handleForm.action === 'reject') {
        currentRow.value.status = 3
        ElMessage.warning('举报已驳回')
      } else if (handleForm.action === 'accept') {
        currentRow.value.status = 1
        ElMessage.success('举报已受理')
      } else if (handleForm.action === 'complete') {
        currentRow.value.status = 2
        ElMessage.success('举报已办结')
      }
      currentRow.value.handler = '当前用户'
      currentRow.value.handleTime = new Date().toLocaleString()
      currentRow.value.handleNote = handleForm.note
    }
    dialogVisible.value = false
    submitting.value = false
  }, 600)
}

function handleStatusTabChange(val: string | number | boolean | undefined) {
  const v = Number(val) as number
  searchForm.status = v === -1 ? null : v
  pagination.pageNum = 1
  fetchList()
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { Object.assign(searchForm, { reportNo: '', reporterName: '', targetBatch: '', status: null }); handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function fetchList() {
  loading.value = true
  const texts = [
    '买到的猪肉颜色异常，有异味', '产品二维码无法扫描识别', '包装上标注的产地信息与实际不符',
    '猪肉表面有不明斑点', '产品已过保质期仍在销售', '怀疑产品未经检疫私自销售'
  ]
  const list = Array.from({ length: 36 }, (_, i) => ({
    id: i + 1,
    reportNo: `CP202407${String(1500 + i).padStart(4, '0')}`,
    reporterName: i % 7 === 0 ? '' : ['张先生', '李女士', '王先生', '赵女士', '刘先生'][i % 5],
    reporterPhone: i % 7 === 0 ? '' : `138${String(10000000 + i * 12345).substring(0, 8)}`,
    targetQrCode: `QR-PORK-2024071${String(5 + Math.floor(i / 6)).padStart(1, '0')}${String(i + 1).padStart(3, '0')}`,
    targetBatch: `B202407${String(1500 + i).padStart(4, '0')}`,
    complaintText: texts[i % texts.length],
    photos: i % 3 === 0 ? [] : Array.from({ length: i % 5 + 1 }, (_, j) => `photo_${i}_${j}.jpg`),
    status: i < 8 ? 0 : i < 16 ? 1 : i < 30 ? 2 : 3,
    handler: i >= 8 ? ['王监管', '李监管', '张监管'][i % 3] : '',
    handleNote: i >= 8 ? '经核实，已处理' : '',
    handleTime: i >= 8 ? `2024-07-${String(16 + Math.floor(i / 8)).padStart(2, '0')} 10:00:00` : '',
    createTime: `2024-07-${String(14 + i).padStart(2, '0')} ${String(8 + i % 12).padStart(2, '0')}:${String(i * 7 % 60).padStart(2, '0')}:00`,
  }))
  const filtered = list.filter(item => {
    if (searchForm.reportNo && !item.reportNo.includes(searchForm.reportNo)) return false
    if (searchForm.reporterName && !(item.reporterName || '').includes(searchForm.reporterName)) return false
    if (searchForm.targetBatch && !item.targetBatch.includes(searchForm.targetBatch)) return false
    if (searchForm.status !== null && item.status !== searchForm.status) return false
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
.status-tabs { margin-bottom: 20px; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.complaint-text-section { margin-bottom: 20px; h4 { font-size: 14px; font-weight: 600; margin-bottom: 8px; } }
.complaint-text { font-size: 14px; line-height: 1.8; color: #606266; background: #f5f7fa; padding: 12px 16px; border-radius: 6px; border-left: 3px solid #e6a23c; }
.photo-section { margin-bottom: 20px; h4 { font-size: 14px; font-weight: 600; margin-bottom: 8px; } }
.photo-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.handle-note-section { margin-bottom: 20px; h4 { font-size: 14px; font-weight: 600; margin-bottom: 8px; } }
.drawer-actions { display: flex; gap: 8px; margin-top: 24px; padding-top: 20px; border-top: 1px solid #ebeef5; }
</style>
