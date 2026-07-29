<template>
  <div class="page-container">
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="批次号">
          <el-input v-model="searchForm.batchNo" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="searchForm.operator" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="创建日期">
          <el-date-picker v-model="createDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="handleCreate"><el-icon><Plus /></el-icon>创建批次</el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="batchNo" label="批次号" width="180" />
      <el-table-column prop="pigEars" label="关联生猪" min-width="200">
        <template #default="{ row }">
          <el-tag v-for="ear in (row.pigEarNos || []).slice(0, 5)" :key="ear" size="small" style="margin-right: 4px; margin-bottom: 2px;">{{ ear }}</el-tag>
          <el-tag v-if="(row.pigEarNos || []).length > 5" size="small" type="info">+{{ row.pigEarNos.length - 5 }}头</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="pigCount" label="数量" width="80" align="center" />
      <el-table-column prop="totalWeightKg" label="总重量(kg)" width="120" align="center" />
      <el-table-column prop="operator" label="操作人" width="100" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          <el-button type="success" link size="small" @click="handleSplit(row)">
            <el-icon><Grid /></el-icon>分割
          </el-button>
          <el-button type="info" link size="small" @click="handleChainInfo(row)">链上信息</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <!-- 创建批次弹窗 -->
    <el-dialog v-model="dialogVisible" title="创建胴体批次" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="batchForm" :rules="batchRules" label-width="100px">
        <el-form-item label="选择生猪" prop="pigIds">
          <el-select v-model="batchForm.pigIds" multiple filterable placeholder="搜索并选择生猪耳标号" style="width: 100%">
            <el-option v-for="p in pigOptions" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="batchForm.pigIds.length > 0" label="已选生猪" class="selected-pigs">
          <el-tag v-for="id in batchForm.pigIds" :key="id" size="small" closable
            @close="batchForm.pigIds = batchForm.pigIds.filter(i => i !== id)" style="margin-right: 6px; margin-bottom: 4px;">
            {{ pigOptions.find(p => p.value === id)?.label }}
          </el-tag>
        </el-form-item>
        <el-form-item label="预计总重量">
          <el-input-number v-model="batchForm.totalWeightKg" :min="0" :precision="1" style="width: 200px" />
          <span class="unit-hint">kg</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchForm.note" type="textarea" :rows="2" placeholder="备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Plus, Grid } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const searchForm = reactive({ batchNo: '', operator: '' })
const createDate = ref<string | null>(null)
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const batchForm = reactive({ pigIds: [] as number[], totalWeightKg: 0, note: '' })
const batchRules = {
  pigIds: [{ required: true, message: '请至少选择一头生猪', trigger: 'change', type: 'array', min: 1 }],
}
const pigOptions = Array.from({ length: 30 }, (_, i) => ({
  value: i + 1,
  label: `ET202406${String(100 + i).padStart(3, '0')} — ${['长白猪', '杜洛克', '大约克夏'][i % 3]} (${118 + i * 0.5}kg)`
}))

function handleCreate() { dialogVisible.value = true; batchForm.pigIds = []; batchForm.totalWeightKg = 0; batchForm.note = '' }

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const selectedPigs = batchForm.pigIds.map(id => pigOptions.find(p => p.value === id)!)
    tableData.value.unshift({
      id: Date.now(), batchNo: `B${Date.now().toString(36).toUpperCase()}`,
      pigEarNos: selectedPigs.map(p => p.label.split(' —')[0]),
      pigCount: batchForm.pigIds.length,
      totalWeightKg: batchForm.totalWeightKg || selectedPigs.length * 118,
      operator: '当前用户', createTime: new Date().toLocaleString(),
      contentHash: `0x${Array.from({length:64},()=>'0123456789abcdef'[Math.floor(Math.random()*16)]).join('')}`,
    })
    pagination.total++
    ElMessage.success('胴体批次创建成功，数据已上链')
    dialogVisible.value = false
  } finally { submitting.value = false }
}

function handleView(row: any) { ElMessage.info(`查看批次详情: ${row.batchNo}`) }
function handleSplit(row: any) { router.push('/admin/distribution/split') }
function handleChainInfo(row: any) { ElMessage.success(`链上状态: 已确认, 交易哈希: ${row.contentHash}`) }

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { searchForm.batchNo = ''; searchForm.operator = ''; createDate.value = null; handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function fetchList() {
  loading.value = true
  const list = Array.from({ length: 28 }, (_, i) => {
    const count = 3 + Math.floor(Math.random() * 8)
    return {
      id: i + 1, batchNo: `B202407${String(1500 + i).padStart(4, '0')}`,
      pigEarNos: Array.from({ length: count }, (_, j) => `ET202406${String(100 + i * 5 + j).padStart(3, '0')}`),
      pigCount: count, totalWeightKg: +(count * 118 + Math.random() * 50).toFixed(1),
      operator: ['张三', '李四', '王五'][i % 3],
      createTime: `2024-07-${String(1 + i).padStart(2, '0')} ${String(8 + i % 10).padStart(2, '0')}:00:00`,
      contentHash: `0x${Array.from({length:64},()=>'0123456789abcdef'[Math.floor(Math.random()*16)]).join('')}`,
    }
  })
  const filtered = list.filter(item => {
    if (searchForm.batchNo && !item.batchNo.includes(searchForm.batchNo)) return false
    if (searchForm.operator && !item.operator.includes(searchForm.operator)) return false
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
.action-bar { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.unit-hint { margin-left: 6px; font-size: 13px; color: #909399; }
.selected-pigs :deep(.el-form-item__content) { line-height: 2; }
</style>
