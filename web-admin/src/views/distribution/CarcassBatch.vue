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
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="handleCreate"><el-icon><Plus /></el-icon>创建批次</el-button>
      <el-button type="success" :disabled="!currentBatch" @click="handleAppend">
        <el-icon><Connection /></el-icon>添加关联生猪
      </el-button>
      <span v-if="currentBatch" class="current-hint">当前批次：{{ currentBatch.batchNo }}（已关联 {{ currentBatch.pigCount }} 头）</span>
      <span v-else class="current-hint muted">请先点击列表行选中一个批次</span>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe highlight-current-row
      @current-change="handleCurrentChange">
      <el-table-column prop="batchNo" label="批次号" width="180" />
      <el-table-column label="关联生猪" min-width="170">
        <template #default="{ row }">
          <template v-if="(row.pigInfos || []).length">
            <el-tag size="small">{{ row.pigInfos[0].earTagNo }}</el-tag>
            <el-tooltip v-if="row.pigInfos.length > 1" content="查看全部关联生猪" placement="top">
              <el-button link type="primary" size="small" style="margin-left: 2px" @click="showAllPigs(row)">
                <el-icon :size="17"><MoreFilled /></el-icon>
              </el-button>
            </el-tooltip>
          </template>
          <span v-else>--</span>
        </template>
      </el-table-column>
      <el-table-column prop="pigCount" label="数量" width="80" align="center" />
      <el-table-column prop="totalWeightKg" label="总重量(kg)" width="120" align="center" />
      <el-table-column prop="slaughterhouse" label="屠宰场" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.slaughterhouse || '--' }}</template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="100" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column label="操作" width="300" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          <el-button type="warning" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="success" link size="small" @click="handleSplit(row)">
            <el-icon><Grid /></el-icon>分割
          </el-button>
          <el-button type="info" link size="small" @click="handleChainInfo(row)">链上信息</el-button>
          <el-popconfirm title="确定删除该胴体批次吗？有下游分割时将被拦截" width="220" @confirm="handleDelete(row)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <!-- 创建/编辑批次弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑胴体批次' : '创建胴体批次'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="batchForm" :rules="batchRules" label-width="100px">
        <el-form-item label="选择生猪" prop="pigIds">
          <el-select v-model="batchForm.pigIds" multiple filterable remote reserve-keyword
            :remote-method="searchPigsForForm" :loading="pigLoading"
            placeholder="输入耳标号搜索生猪，可多选" style="width: 100%">
            <el-option v-for="p in pigOptions" :key="p.value"
              :label="p.occupiedBatch ? `${p.label}（已属${p.occupiedBatch}）` : p.label"
              :value="p.value" :disabled="!!p.occupiedBatch" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="batchForm.pigIds.length > 0" label="已选生猪" class="selected-pigs">
          <el-tag v-for="id in batchForm.pigIds" :key="id" size="small" closable
            @close="batchForm.pigIds = batchForm.pigIds.filter(i => i !== id)" style="margin-right: 6px; margin-bottom: 4px;">
            {{ pigOptions.find(p => p.value === id)?.label || ('生猪ID ' + id) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="屠宰场" prop="slaughterhouse">
          <el-input v-model="batchForm.slaughterhouse" placeholder="请输入屠宰场名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="操作人" prop="operator">
          <el-input v-model="batchForm.operator" placeholder="请输入操作人姓名" maxlength="32" />
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
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ isEdit ? '保存修改' : '确认创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 批次详情弹窗 -->
    <el-dialog v-model="detailVisible" title="胴体批次详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="批次号">{{ detailData.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="屠宰场">{{ detailData.slaughterhouse || '--' }}</el-descriptions-item>
        <el-descriptions-item label="生猪数量">{{ detailData.pigCount ?? (detailData.pigIds?.length || 0) }}</el-descriptions-item>
        <el-descriptions-item label="总重量(kg)">{{ detailData.totalWeightKg }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detailData.operator || '--' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="关联生猪" :span="2">
          <el-tag v-for="p in (detailData.pigInfos || [])" :key="p.id" size="small" style="margin-right: 4px; margin-bottom: 2px;">
            {{ p.earTagNo }}<span v-if="p.breed" class="pig-breed">（{{ p.breed }}）</span>
          </el-tag>
          <span v-if="!(detailData.pigInfos || []).length">--</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 追加关联生猪弹窗 -->
    <el-dialog v-model="appendVisible" :title="`添加关联生猪${appendTarget ? ' - ' + appendTarget.batchNo : ''}`" width="800px" destroy-on-close>
      <div class="pig-search-bar">
        <el-input v-model="appendKw" placeholder="输入耳标号搜索" clearable style="width: 240px"
          @keyup.enter="searchAppendPigs" />
        <el-button type="primary" @click="searchAppendPigs"><el-icon><Search /></el-icon>搜索</el-button>
        <span class="muted">已在本批次或已归其他批次的猪不可选</span>
      </div>
      <el-table ref="appendTableRef" v-loading="appendLoading" :data="appendPigList" border size="small"
        max-height="320" @selection-change="handleAppendSelectionChange" style="margin-top: 10px">
        <el-table-column type="selection" width="48" :selectable="canSelectPig" />
        <el-table-column prop="earTagNo" label="耳标号" width="190" />
        <el-table-column prop="breed" label="品种" width="110" />
        <el-table-column prop="farmName" label="养殖场" min-width="140">
          <template #default="{ row }">{{ row.farmName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="statusLabel" label="档案状态" width="100">
          <template #default="{ row }">{{ row.statusLabel || '--' }}</template>
        </el-table-column>
        <el-table-column label="归批状态" width="150">
          <template #default="{ row }">
            <span v-if="inCurrentBatch(row.id)" class="muted">已在本批次</span>
            <el-tag v-else-if="occupancyMap[row.id]" size="small" type="danger">已属{{ occupancyMap[row.id] }}</el-tag>
            <el-tag v-else size="small" type="success">可添加</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="append-summary">
        <span>本次新增 <b>{{ appendSelected.length }}</b> 头，追加后共
          <b>{{ (appendTarget?.pigCount || 0) + appendSelected.length }}</b> 头</span>
        <div>
          <span class="weight-label">新总重量(kg)：</span>
          <el-input-number v-model="appendForm.totalWeightKg" :min="0" :precision="1" />
          <span class="unit-hint">生猪重与胴体重不同，请按实际过磅填写</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="appendVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting"
          :disabled="appendSelected.length === 0" @click="submitAppend">确认追加</el-button>
      </template>
    </el-dialog>

    <!-- 全部关联生猪弹窗 -->
    <el-dialog v-model="allPigsVisible" title="全部关联生猪" width="520px" destroy-on-close>
      <el-table :data="allPigsList" border size="small" max-height="440">
        <el-table-column type="index" label="#" width="56" align="center" />
        <el-table-column prop="earTagNo" label="耳标号" min-width="160" />
        <el-table-column prop="breed" label="品种" width="120">
          <template #default="{ row }">{{ row.breed || '--' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { Search, Refresh, Plus, Grid, MoreFilled, Connection } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { distributionApi } from '@/api/modules/distribution'
import { pigApi } from '@/api/modules/breeding'

const router = useRouter()
const searchForm = reactive({ batchNo: '', operator: '' })
const dateRange = ref<[string, string] | null>(null)
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const batchForm = reactive({ pigIds: [] as number[], totalWeightKg: 0, slaughterhouse: '', operator: '', note: '' })
const batchRules = {
  pigIds: [{ required: true, message: '请至少选择一头生猪', trigger: 'change', type: 'array', min: 1 }],
  slaughterhouse: [{ required: true, message: '请输入屠宰场名称', trigger: 'blur' }],
  operator: [{ required: true, message: '请输入操作人姓名', trigger: 'blur' }],
}
// 生猪下拉选项（真实档案远程搜索）；occupiedBatch 标记已归属的批次号（非空则禁选）
const pigOptions = ref<Array<{ value: number; label: string; occupiedBatch?: string }>>([])
const pigLoading = ref(false)
// 全量占用映射：pigId -> 批次号
const occupancyMap = ref<Record<number, string>>({})

async function fetchOccupancy() {
  try {
    const list: any[] = (await distributionApi.getPigOccupancy()) || []
    const map: Record<number, string> = {}
    list.forEach((it) => { map[it.pigId] = it.batchNo })
    occupancyMap.value = map
  } catch { occupancyMap.value = {} }
}

function mergePigOptions(pigs: any[], excludePigIds: number[] = []) {
  pigs.forEach((p) => {
    // 编辑场景下，本批次已有关联猪不视为被占用
    const occupied = excludePigIds.includes(p.id) ? '' : (occupancyMap.value[p.id] || '')
    const existing = pigOptions.value.find(o => o.value === p.id)
    if (existing) {
      existing.occupiedBatch = occupied
    } else {
      pigOptions.value.push({
        value: p.id,
        label: p.breed ? `${p.earTagNo}（${p.breed}）` : p.earTagNo,
        occupiedBatch: occupied,
      })
    }
  })
}

async function searchPigsForForm(keyword: string) {
  pigLoading.value = true
  try {
    const res: any = await pigApi.list({ pageNum: 1, pageSize: 50, earTagNo: keyword || undefined })
    mergePigOptions(res?.records || res?.list || [], isEdit.value ? batchForm.pigIds : [])
  } catch { /* 提示由拦截器处理 */ } finally { pigLoading.value = false }
}

// ========== 列表行选中（顶部「添加关联生猪」作用对象） ==========
const currentBatch = ref<any>(null)
function handleCurrentChange(row: any) {
  currentBatch.value = row
}

// ========== 追加关联生猪弹窗 ==========
const appendVisible = ref(false)
const appendLoading = ref(false)
const appendKw = ref('')
const appendPigList = ref<any[]>([])
const appendSelected = ref<any[]>([])
const appendTarget = ref<any>(null)
const appendTableRef = ref()
const appendForm = reactive<{ totalWeightKg: number }>({ totalWeightKg: 0 })

function inCurrentBatch(pigId: number) {
  return Array.isArray(appendTarget.value?.pigIds) && appendTarget.value.pigIds.includes(pigId)
}
function canSelectPig(row: any) {
  // 本批次已有、或已归其他批次的猪不可选
  return !occupancyMap.value[row.id]
}

async function handleAppend() {
  if (!currentBatch.value) return
  // 已发生分割的批次直接提示并拦截，不打开弹窗（后端更新接口同样会拦截）
  let hasSplits = false
  try {
    const tree: any = await distributionApi.getSplitTree(currentBatch.value.batchNo)
    hasSplits = Array.isArray(tree?.children) && tree.children.length > 0
  } catch { /* 查询失败不阻断，后端提交时会兜底校验 */ }
  if (hasSplits) {
    ElMessage.warning(`批次 ${currentBatch.value.batchNo} 已发生分割，不能再添加关联生猪`)
    return
  }
  appendTarget.value = currentBatch.value
  appendKw.value = ''
  appendSelected.value = []
  appendBlocked.value = false
  appendForm.totalWeightKg = currentBatch.value.totalWeightKg ?? 0
  appendVisible.value = true
  await fetchOccupancy()
  searchAppendPigs()
}

async function searchAppendPigs() {
  appendLoading.value = true
  try {
    const res: any = await pigApi.list({ pageNum: 1, pageSize: 50, earTagNo: appendKw.value || undefined })
    appendPigList.value = res?.records || res?.list || []
  } catch { appendPigList.value = [] } finally { appendLoading.value = false }
}

function handleAppendSelectionChange(rows: any[]) {
  appendSelected.value = rows
}

async function submitAppend() {
  if (!appendTarget.value || appendSelected.value.length === 0) return
  submitting.value = true
  const oldIds: number[] = Array.isArray(appendTarget.value.pigIds) ? appendTarget.value.pigIds : []
  const payload = {
    pigIds: [...oldIds, ...appendSelected.value.map(p => p.id)],
    totalWeightKg: appendForm.totalWeightKg,
    slaughterhouse: appendTarget.value.slaughterhouse,
    operator: appendTarget.value.operator,
    note: appendTarget.value.note,
  }
  try {
    await distributionApi.updateBatch(appendTarget.value.id, payload)
    ElMessage.success(`已追加 ${appendSelected.value.length} 头生猪`)
    appendVisible.value = false
    fetchList()
  } catch { /* 错误已由拦截器提示（如已分割/跨批次占用） */ } finally { submitting.value = false }
}

// ========== 全部关联生猪弹窗 ==========
const allPigsVisible = ref(false)
const allPigsList = ref<Array<{ id: number; earTagNo: string; breed?: string }>>([])
function showAllPigs(row: any) {
  allPigsList.value = row.pigInfos || []
  allPigsVisible.value = true
}

function resetForm() {
  isEdit.value = false
  editingId.value = null
  batchForm.pigIds = []
  batchForm.totalWeightKg = 0
  batchForm.slaughterhouse = ''
  batchForm.operator = ''
  batchForm.note = ''
}

async function handleCreate() {
  resetForm()
  dialogVisible.value = true
  pigOptions.value = []
  await fetchOccupancy()
  searchPigsForForm('')
  nextTick(() => formRef.value?.clearValidate())
}

async function handleEdit(row: any) {
  resetForm()
  isEdit.value = true
  editingId.value = row.id
  batchForm.pigIds = Array.isArray(row.pigIds) ? [...row.pigIds] : []
  batchForm.totalWeightKg = row.totalWeightKg ?? 0
  batchForm.slaughterhouse = row.slaughterhouse || ''
  batchForm.operator = row.operator || ''
  batchForm.note = row.note || ''
  dialogVisible.value = true
  pigOptions.value = []
  await fetchOccupancy()
  // 先灌入该批次已有关联猪，保证已选项有文字标签；编辑自身批次的猪不标占用
  ;(row.pigInfos || []).forEach((p: any) => {
    pigOptions.value.push({ value: p.id, label: p.breed ? `${p.earTagNo}（${p.breed}）` : p.earTagNo })
  })
  searchPigsForForm('')
  nextTick(() => formRef.value?.clearValidate())
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  const payload = {
    pigIds: batchForm.pigIds,
    totalWeightKg: batchForm.totalWeightKg,
    slaughterhouse: batchForm.slaughterhouse,
    operator: batchForm.operator,
    note: batchForm.note
  }
  try {
    if (isEdit.value && editingId.value != null) {
      await distributionApi.updateBatch(editingId.value, payload)
      ElMessage.success('胴体批次修改成功')
    } else {
      await distributionApi.createBatch(payload)
      ElMessage.success('胴体批次创建成功，数据已上链')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 错误已由拦截器提示 */ } finally { submitting.value = false }
}

const detailVisible = ref(false)
const detailData = ref<any>({})

function handleView(row: any) {
  detailData.value = row
  detailVisible.value = true
}
function handleSplit(row: any) { router.push('/admin/distribution/split') }
function handleChainInfo(row: any) { ElMessage.info(`批次 ${row.batchNo} 暂未上链`) }

async function handleDelete(row: any) {
  try {
    await distributionApi.deleteBatch(row.id)
    ElMessage.success('删除成功')
    // 删除的是当前页最后一条时回退一页，避免停留在空页
    if (tableData.value.length === 1 && pagination.pageNum > 1) pagination.pageNum--
    fetchList()
  } catch { /* 错误已由拦截器提示（如存在下游分割） */ }
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { searchForm.batchNo = ''; searchForm.operator = ''; dateRange.value = null; handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

// 根据猪 ID 数组批量反查耳标号、品种
async function resolvePigInfo(pigIds: number[]): Promise<Array<{ id: number; earTagNo: string; breed?: string }>> {
  if (!Array.isArray(pigIds) || pigIds.length === 0) return []
  const infos: Array<{ id: number; earTagNo: string; breed?: string }> = []
  await Promise.all(pigIds.map(async (id) => {
    try {
      const pig = await pigApi.detail(id)
      if (pig?.earTagNo) infos.push({ id, earTagNo: pig.earTagNo, breed: pig.breed })
    } catch { /* 忽略单只猪查询失败 */ }
  }))
  return infos
}

async function fetchList() {
  loading.value = true
  const selectedId = currentBatch.value?.id
  try {
    const res: any = await distributionApi.getBatches({
      current: pagination.pageNum,
      size: pagination.pageSize,
      batchNo: searchForm.batchNo || undefined,
      operator: searchForm.operator || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
    })
    const records = res?.records || res?.list || []
    const list = await Promise.all(records.map(async (r: any) => {
      const pigIds = Array.isArray(r.pigIds) ? r.pigIds : []
      const pigInfos = await resolvePigInfo(pigIds)
      return {
        ...r,
        pigCount: pigIds.length,
        pigInfos,
        pigEarNos: pigInfos.map(p => p.earTagNo),
      }
    }))
    tableData.value = list
    pagination.total = res?.total || list.length || 0
    // 刷新后保持原选中行（数据已更新）
    currentBatch.value = selectedId != null ? list.find((r: any) => r.id === selectedId) || null : null
  } catch { tableData.value = []; pagination.total = 0 } finally { loading.value = false }
}

onMounted(() => { fetchOccupancy(); fetchList() })
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form) { flex-wrap: nowrap; }
  :deep(.el-form-item) { margin-bottom: 12px; } }
.action-bar { display: flex; justify-content: flex-end; align-items: center; gap: 10px; margin-bottom: 16px; }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.unit-hint { margin-left: 6px; font-size: 13px; color: #909399; }
.selected-pigs :deep(.el-form-item__content) { line-height: 2; }
.current-hint { font-size: 13px; color: #67c23a; }
.current-hint.muted { color: #909399; }
.pig-breed { color: #909399; font-size: 12px; }
.pig-search-bar { display: flex; align-items: center; gap: 8px; }
.muted { color: #909399; font-size: 12px; }
.append-summary {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 12px; padding: 10px 12px; background: #f5f7fa; border-radius: 4px;
  b { color: #409eff; margin: 0 2px; }
  .weight-label { font-size: 13px; color: #606266; }
}
:deep(.el-table__row.current-row > td) { background-color: #ecf5ff !important; }
</style>
