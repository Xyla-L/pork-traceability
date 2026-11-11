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

    <!-- 批次只由分拣台归集：白条下线时识读挂钩标签并过磅，系统按「屠宰场 + 屠宰日」自动成批。
         人工建批与追加生猪的入口已删除 —— 批次边界由设备事实决定，人手补一条会让「这一批到底是哪几头」失去依据。 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
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
      <el-table-column prop="operator" label="操作人" width="100" align="center">
        <template #default="{ row }">
          <span v-if="isDeviceBatch(row)" class="muted">--</span>
          <span v-else>{{ row.operator || '未记录' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="数据来源" width="130" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="isDeviceBatch(row)" :content="deviceSourceHint(row)" placement="top">
            <el-tag size="small" type="success" effect="plain">分拣台自动归集</el-tag>
          </el-tooltip>
          <el-tag v-else size="small" type="info" effect="plain">人工录入</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="归集状态" width="110" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="isDeviceBatch(row)" :content="sealHint(row)" placement="top">
            <el-tag size="small" :type="row.status === 1 ? 'warning' : 'info'" effect="plain">
              {{ row.status === 1 ? '归集中' : '已封批' }}
            </el-tag>
          </el-tooltip>
          <span v-else class="muted">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column label="操作" width="230" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          <el-button v-if="canSeal(row)" type="danger" link size="small" @click="handleSeal(row)">封批</el-button>
          <el-button type="success" link size="small" @click="handleSplit(row)">
            <el-icon><Grid /></el-icon>分割
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

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
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Grid, MoreFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { distributionApi } from '@/api/modules/distribution'
import { pigApi } from '@/api/modules/breeding'

const router = useRouter()

/** 该批次是不是分拣台按「屠宰场 + 屠宰日」自动归集出来的 */
const isDeviceBatch = (row: any) => row?.source === 'DEVICE'

/** 设备来源的 tooltip：说清它怎么来的、为什么操作人是空的 */
function deviceSourceHint(row: any) {
  const ref = row?.sourceRef ? `分拣台 ${row.sourceRef}` : '分拣台'
  return `${ref}在白条下线时识读挂钩标签并过磅，系统按「屠宰场 + 屠宰日」自动归集；`
    + '归集是机器行为、没有操作人，关联生猪与重量只能作废后由设备重推。'
}

/** 归集状态 tooltip：批次必须有边界，否则一天一场会攒出一个几千头的巨批，追溯就没有区分度了 */
function sealHint(row: any) {
  return row?.status === 1
    ? '归集中：当日同一屠宰场、分拣台推来的白条都会并入本批次。达到头数上限会自动封批；'
      + '换班或换圈时可以手动提前封批，让后一班的白条另开新批。'
    : '已封批：不再并入当日白条，后续白条会另开一个新批次。'
}

/** 只有「仍归集中的设备批次」才需要封批 —— 人工批次本就不参与自动归集，已封的再封也没意义 */
const canSeal = (row: any) => isDeviceBatch(row) && row?.status === 1

async function handleSeal(row: any) {
  try {
    await ElMessageBox.confirm(
      `封批后，分拣台不再把当日白条并入 ${row.batchNo}，后续白条会另开一个新批次。确定封批？`,
      '封批确认',
      { type: 'warning', confirmButtonText: '确认封批', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await distributionApi.sealBatch(row.id)
    ElMessage.success(`批次 ${row.batchNo} 已封批，后续白条将另开新批`)
    fetchList()
  } catch { /* 错误已由拦截器提示（如已封批） */ }
}

const searchForm = reactive({ batchNo: '', operator: '' })
const dateRange = ref<[string, string] | null>(null)
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

// ========== 全部关联生猪弹窗 ==========
const allPigsVisible = ref(false)
const allPigsList = ref<Array<{ id: number; earTagNo: string; breed?: string }>>([])
function showAllPigs(row: any) {
  allPigsList.value = row.pigInfos || []
  allPigsVisible.value = true
}

const detailVisible = ref(false)
const detailData = ref<any>({})

function handleView(row: any) {
  detailData.value = row
  detailVisible.value = true
}
function handleSplit(row: any) { router.push({ path: '/admin/distribution/split', query: { batchNo: row.batchNo } }) }

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
  } catch { tableData.value = []; pagination.total = 0 } finally { loading.value = false }
}

onMounted(() => { fetchList() })
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form) { flex-wrap: nowrap; }
  :deep(.el-form-item) { margin-bottom: 12px; } }

.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.pig-breed { color: #909399; font-size: 12px; }
.muted { color: #909399; font-size: 12px; }
</style>
