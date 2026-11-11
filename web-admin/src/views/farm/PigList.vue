<template>
  <div class="pig-list">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="批次号">
          <el-input
            v-model="searchForm.batchNo"
            placeholder="请输入批次号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="养殖场">
          <el-select
            v-model="searchForm.farmId"
            placeholder="请选择养殖场"
            filterable
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="farm in farmOptions"
              :key="farm.id"
              :label="farm.farmName || farm.name"
              :value="farm.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="品种">
          <el-select
            v-model="searchForm.breed"
            placeholder="请选择品种"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="item in breedOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
          <el-button type="success" @click="openCreate">
            <el-icon><Plus /></el-icon>
            登记批次
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 登记批次弹窗：批次是养殖端档案的登记单位，耳标区间内的猪自动成为成员 -->
    <el-dialog v-model="createVisible" title="登记生猪批次" width="560px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="批次号" required>
          <el-input v-model="createForm.batchNo" placeholder="同一养殖场内唯一，如 PB-20261025-01" />
        </el-form-item>
        <el-form-item label="养殖场" required>
          <el-select v-model="createForm.farmId" placeholder="请选择养殖场" filterable style="width: 100%">
            <el-option v-for="farm in farmOptions" :key="farm.id" :label="farm.farmName || farm.name" :value="farm.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="耳标起号" required>
          <el-input v-model="createForm.earTagStart" placeholder="本批第一头猪的耳标号" />
        </el-form-item>
        <el-form-item label="耳标止号" required>
          <el-input v-model="createForm.earTagEnd" placeholder="本批最后一头猪的耳标号（与起号同前缀）" />
        </el-form-item>
        <el-form-item label="品种">
          <el-input v-model="createForm.breed" placeholder="如 大白、长白、杜洛克，可留空" />
        </el-form-item>
        <el-form-item label="进场日期">
          <el-date-picker v-model="createForm.entryDate" type="date" value-format="YYYY-MM-DD" placeholder="外购为进场日，自繁为转育肥日" style="width: 100%" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="createForm.source" placeholder="自繁 或 外购-供应商名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">登记</el-button>
      </template>
    </el-dialog>

    <!-- 数据表格 -->
    <div class="pig-table">
      <el-table
        v-loading="tableLoading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="batchNo" label="批次号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="farmId" label="养殖场" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ farmNameMap[row.farmId] || row.farmId }}
          </template>
        </el-table-column>
        <el-table-column prop="breed" label="品种" min-width="100" align="center">
          <template #default="{ row }">
            {{ row.breed || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="耳标区间" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.earTagStart }} ~ {{ row.earTagEnd }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column label="状态" min-width="90" align="center">
          <template #default="{ row }">
            <span :class="batchStatusOf(row).cls">{{ batchStatusOf(row).label }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="entryDate" label="进场日期" min-width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.entryDate">{{ row.entryDate }}</span>
            <span v-else class="empty-cell">--</span>
          </template>
        </el-table-column>
        <!-- 日龄：不存字段，由批次出生日期现算（存下来的日龄第二天就假了）。
             养殖 ERP 同步的批次才有出生日期，人工登记的一般是空的 —— 那不是缺数据，是没人有这个数。 -->
        <el-table-column label="日龄" width="92" align="center">
          <template #default="{ row }">
            <span v-if="ageDays(row.birthDate) !== null">
              {{ ageDays(row.birthDate) }} 天
            </span>
            <el-tooltip v-else content="批次没有出生日期（人工登记不要求）：日龄由出生日期现算，不是单独存的字段" placement="top">
              <span class="empty-cell">--</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <!-- 档案来源：人工登记 vs 外部养殖系统同步。外部系统的批次号是它自己的编号，
             对本系统的人是技术细节，放进 tooltip 供排查，列表上只给中文来源。 -->
        <el-table-column label="档案来源" width="132" align="center">
          <template #default="{ row }">
            <el-tooltip :content="sourceTooltip(row)" placement="top">
              <el-tag :type="row.dataSource === 'API' ? 'success' : 'info'" size="small"
                      :effect="row.dataSource === 'API' ? 'light' : 'plain'">
                {{ row.dataSource === 'API' ? '养殖系统同步' : '人工登记' }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" min-width="110" align="center">
          <template #default="{ row }">
            {{ row.source || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="登记时间" min-width="170" align="center">
          <template #default="{ row }">
            <span v-if="row.createTime">{{ formatTime(row.createTime) }}</span>
            <span v-else class="empty-cell">--</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleVoid(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { pigBatchApi } from '@/api/modules/breeding'
import { confirmVoid } from '@/composables/useVoidAction'

// ==================== 搜索相关 ====================

const searchForm = reactive({
  batchNo: '',
  farmId: '',
  breed: ''
})

// 品种下拉：优先取后端字典（预置常见猪种 + 库内实际出现过的品种），
// 不再前端硬编码——设备通道上报的品种不受前端枚举约束，硬编码会让
// 「列表里看得到、下拉里选不到」（如设备上报的「三元杂」）。接口异常时回退内置常用值。
const breedOptions = ref([
  { label: '长白猪', value: '长白猪' },
  { label: '大白猪', value: '大白猪' },
  { label: '杜洛克', value: '杜洛克' },
  { label: '皮特兰', value: '皮特兰' },
  { label: '三元杂', value: '三元杂' }
])

const fetchBreeds = async () => {
  try {
    const res = await request.get('/breeding/pigs/breeds')
    const list = Array.isArray(res) ? res : (res?.data || [])
    if (Array.isArray(list) && list.length) {
      breedOptions.value = list.map((b) => ({ label: b, value: b }))
    }
  } catch (error) {
    console.error('获取品种字典失败，使用内置备选:', error)
  }
}

const farmOptions = ref([])
const farmNameMap = ref({})

// 获取养殖场列表
const fetchFarmOptions = async () => {
  try {
    const res = await request.get('/breeding/farms', { params: { current: 1, size: 500 } })
    const list = res?.records || res?.list || []
    farmOptions.value = list
    const map = {}
    list.forEach((farm) => {
      map[farm.id] = farm.farmName || farm.name
    })
    farmNameMap.value = map
  } catch (error) {
    console.error('获取养殖场列表失败:', error)
  }
}

// ==================== 表格相关 ====================

const tableData = ref([])
const tableLoading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 获取批次列表数据
const fetchList = async () => {
  tableLoading.value = true

  try {
    const params = {
      current: pagination.pageNum,
      size: pagination.pageSize,
      batchNo: searchForm.batchNo || undefined,
      farmId: searchForm.farmId || undefined,
      breed: searchForm.breed || undefined
    }

    const res = await request.get('/breeding/pigs/batches', { params, timeout: 5000 })
    const list = res?.records || res?.list || []
    const total = res?.total
    tableData.value = Array.isArray(list) ? list : []
    pagination.total = total || list.length || 0
  } catch (error) {
    console.error('获取生猪批次列表失败:', error)
    tableData.value = []
    pagination.total = 0
  } finally {
    tableLoading.value = false
  }
}

// ==================== 登记批次 ====================
// 批次是养殖端档案的登记单位：同场、同批进、同批出的一群猪登记为一批，
// 耳标起止号圈定成员区间，区间内的猪登记时自动成为成员行。

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  batchNo: '',
  farmId: null,
  earTagStart: '',
  earTagEnd: '',
  breed: '',
  entryDate: '',
  source: ''
})

const openCreate = () => {
  createForm.batchNo = ''
  createForm.farmId = null
  createForm.earTagStart = ''
  createForm.earTagEnd = ''
  createForm.breed = ''
  createForm.entryDate = ''
  createForm.source = ''
  createVisible.value = true
}

const submitCreate = async () => {
  if (!createForm.batchNo.trim()) return ElMessage.warning('请填写批次号')
  if (!createForm.farmId) return ElMessage.warning('请选择养殖场')
  if (!createForm.earTagStart.trim() || !createForm.earTagEnd.trim())
    return ElMessage.warning('请填写耳标起号和止号')
  creating.value = true
  try {
    await pigBatchApi.create({
      batchNo: createForm.batchNo.trim(),
      farmId: createForm.farmId,
      earTagStart: createForm.earTagStart.trim(),
      earTagEnd: createForm.earTagEnd.trim(),
      breed: createForm.breed || undefined,
      entryDate: createForm.entryDate || undefined,
      source: createForm.source || undefined
    })
    ElMessage.success('批次登记成功，区间内的猪已自动成为批次成员')
    createVisible.value = false
    fetchList()
  } catch (error) {
    console.error('登记批次失败:', error)
  } finally {
    creating.value = false
  }
}

// 作废：批次与成员行都保留在库中并可追溯，只翻「不再生效」标记
const handleVoid = async (row) => {
  const ok = await confirmVoid({
    title: '作废生猪批次',
    message: `确认作废批次 ${row.batchNo || ''}（${row.quantity || 0} 头）吗？批次与全部成员档案会一并作废，保留在库中并可追溯；批次内已有猪出场时不能整批作废。`,
    onConfirm: (reason) => pigBatchApi.voidBatch(row.id, reason),
  })
  if (ok) fetchList()
}

// ==================== 搜索/重置 ====================

const handleSearch = () => {
  pagination.pageNum = 1
  fetchList()
}

const handleReset = () => {
  searchForm.batchNo = ''
  searchForm.farmId = ''
  searchForm.breed = ''
  handleSearch()
}

/**
 * 登记时间：后端返回的是 ISO 格式（2026-10-25T15:20:00），界面展示到秒。
 */
const formatTime = (value) => String(value).replace('T', ' ').slice(0, 19)

/**
 * 批次状态：作废 > 已出栏（场上无存栏）> 在养。
 */
const batchStatusOf = (row) => {
  if (row.voidReason) return { label: '已作废', cls: 'status-void' }
  if ((row.onFarmCount ?? 0) > 0) return { label: '在养', cls: 'status-on-farm' }
  return { label: '已出栏', cls: 'status-shipped' }
}

/**
 * 日龄 = 今天 − 批次出生日期，现算。
 * 只有养殖系统同步的批次才有出生日期；人工登记拿不到这个数（产房记录不在登记人手上），
 * 返回 null 让界面显示「--」——不猜、不用进场日期顶替。
 */
const ageDays = (birthDate) => {
  if (!birthDate) return null
  const born = new Date(`${String(birthDate).slice(0, 10)}T00:00:00`)
  if (Number.isNaN(born.getTime())) return null
  const days = Math.floor((Date.now() - born.getTime()) / 86400000)
  return days < 0 ? null : days
}

/**
 * 档案来源的悬停说明：外部系统代号与它自己的批次号是技术细节，列表只给中文，
 * 排查时把鼠标放上去就能看到是哪套系统推的、原始编号是什么。
 */
const sourceTooltip = (row) => {
  if (row.dataSource === 'API') {
    const system = row.externalSystem || '外部养殖系统'
    return `${system} 推送${row.externalId ? `（外部编号 ${row.externalId}）` : ''}：批次档案由养殖场系统同步，本系统不再重复录入`
  }
  return '本系统人工登记：批次号、耳标区间由登记人填写'
}

// ==================== 分页 ====================

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  fetchList()
}

const handlePageChange = (page) => {
  pagination.pageNum = page
  fetchList()
}

// ==================== 初始化 ====================

onMounted(() => {
  fetchFarmOptions()
  fetchBreeds()
  fetchList()
})
</script>

<style lang="scss" scoped>
.pig-list {
  padding: 20px;
  background-color: #fff;
  border-radius: 4px;

  .search-panel {
    padding-bottom: 16px;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 16px;

    :deep(.el-form-item) {
      margin-bottom: 12px;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;
  }
}

.pig-table {
  .empty-cell {
    color: #c0c4cc;
  }

  .status-on-farm {
    color: #67c23a;
    font-weight: 600;
  }

  .status-shipped {
    color: #909399;
  }

  .status-void {
    color: #f56c6c;
  }

  :deep(.el-table) {
    th.el-table__cell {
      background-color: #f5f7fa;
      color: #606266;
      font-weight: 600;
    }
  }
}
</style>
