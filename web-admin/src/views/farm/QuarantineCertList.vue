<template>
  <div class="cert-list">
    <!-- 搜索面板 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="耳标号">
          <el-input
            v-model="searchForm.earTagNo"
            placeholder="请输入耳标号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="证状态">
          <el-select v-model="searchForm.certState" placeholder="全部" clearable style="width: 160px">
            <el-option label="已出证（有效）" value="valid" />
            <el-option label="已出证（已过期）" value="expired" />
            <el-option label="未出证" value="none" />
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
        </el-form-item>
      </el-form>
    </div>


    <el-table v-loading="loading" :data="filteredRows" border stripe>
      <el-table-column prop="earTagNo" label="耳标号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="breed" label="品种" width="110" />
      <el-table-column label="养殖场" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ farmNameMap[row.farmId] || '--' }}</template>
      </el-table-column>
      <el-table-column label="检疫证编号" min-width="180">
        <template #default="{ row }">{{ row.cert?.certNo || '--' }}</template>
      </el-table-column>
      <el-table-column label="有效期至" width="140" align="center">
        <template #default="{ row }">
          <span v-if="row.cert" :class="{ 'cert-expired': row.certExpired }">
            {{ row.cert.validUntil || '--' }}
          </span>
          <span v-else>--</span>
        </template>
      </el-table-column>
      <el-table-column label="证状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag
            size="small"
            effect="plain"
            :type="row.cert ? (row.certExpired ? 'danger' : 'success') : 'info'"
          >
            {{ row.cert ? (row.certExpired ? '已过期' : '有效') : '未出证' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="采集设备" width="130" align="center">
        <template #default="{ row }">
          <span v-if="row.cert">{{ deviceText(row.cert) }}</span>
          <span v-else>--</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openCert(row)">
            {{ row.cert ? '查看 / 作废' : '出证' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

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

    <QuarantineCertDialog v-model="certDialogVisible" :pig="certRow" @changed="fetchList" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { pigApi } from '@/api/modules/breeding'
import { deviceText } from '@/api/modules/ingest'
import QuarantineCertDialog from './QuarantineCertDialog.vue'

const searchForm = reactive({
  earTagNo: '',
  certState: '',
})

const rows = ref([])
const loading = ref(false)
const farmNameMap = ref({})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

const todayStart = () => {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d
}

const isExpired = (cert) => {
  const v = cert?.validUntil
  if (!v) return false
  return new Date(String(v).slice(0, 10)) < todayStart()
}

/** 证状态筛选作用于当前页（数据已随行带上证信息，无需再请求） */
const filteredRows = computed(() => {
  const s = searchForm.certState
  if (!s) return rows.value
  return rows.value.filter((r) => {
    if (s === 'valid') return r.cert && !r.certExpired
    if (s === 'expired') return r.cert && r.certExpired
    if (s === 'none') return !r.cert
    return true
  })
})

const fetchFarms = async () => {
  try {
    const res = await pigApi.getFarms({ pageNum: 1, pageSize: 500 })
    const list = res?.records || res?.list || []
    const map = {}
    list.forEach((f) => {
      map[f.id] = f.farmName || f.name
    })
    farmNameMap.value = map
  } catch {
    farmNameMap.value = {}
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await pigApi.list({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      earTagNo: searchForm.earTagNo || undefined,
    })
    const list = res?.records || res?.list || []
    pagination.total = res?.total || list.length || 0
    // 逐头探检疫证：无证是正常态，接口静默失败，按 null 渲染
    rows.value = await Promise.all(
      (Array.isArray(list) ? list : []).map(async (pig) => {
        let cert = null
        try {
          cert = await pigApi.getQuarantineCert(pig.id)
        } catch {
          cert = null
        }
        return { ...pig, cert, certExpired: isExpired(cert) }
      }),
    )
  } catch {
    rows.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  fetchList()
}

const handleReset = () => {
  searchForm.earTagNo = ''
  searchForm.certState = ''
  handleSearch()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  fetchList()
}

const handlePageChange = (page) => {
  pagination.pageNum = page
  fetchList()
}

// ==================== 出证弹窗 ====================

const certDialogVisible = ref(false)
const certRow = ref(null)

const openCert = (row) => {
  certRow.value = { ...row }
  certDialogVisible.value = true
}

onMounted(() => {
  fetchFarms()
  fetchList()
})
</script>

<style lang="scss" scoped>
.cert-list {
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

  .cert-expired {
    color: #f56c6c;
    font-weight: 600;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    padding-top: 16px;
    margin-top: 16px;
    border-top: 1px solid #ebeef5;
  }
}
</style>
