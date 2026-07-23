<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">生猪档案列表</h2>
      <el-button type="primary" @click="showAddDialog = true">新建档案</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="耳标号">
        <el-input v-model="searchForm.earTagNo" placeholder="请输入耳标号" clearable />
      </el-form-item>
      <el-form-item label="养殖场">
        <el-select v-model="searchForm.farmId" placeholder="请选择养殖场" clearable>
          <el-option v-for="farm in farmOptions" :key="farm.id" :label="farm.name" :value="farm.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="品种">
        <el-select v-model="searchForm.breed" placeholder="请选择品种" clearable>
          <el-option label="大白猪" value="大白猪" />
          <el-option label="长白猪" value="长白猪" />
          <el-option label="杜洛克" value="杜洛克" />
          <el-option label="三元杂交" value="三元杂交" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
          <el-option :label="statusOptions[1]" :value="1" />
          <el-option :label="statusOptions[2]" :value="2" />
          <el-option :label="statusOptions[3]" :value="3" />
          <el-option :label="statusOptions[4]" :value="4" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="pigList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="earTagNo" label="耳标号" width="120" />
      <el-table-column prop="farmName" label="养殖场" width="150" />
      <el-table-column prop="breed" label="品种" width="100" />
      <el-table-column prop="birthDate" label="出生日期" width="120" />
      <el-table-column prop="penNo" label="圈舍号" width="100" />
      <el-table-column prop="source" label="来源" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <StatusTag
            :status="scope.row.status"
            :config="statusTagConfig"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="建档时间" width="160" />
      <el-table-column label="操作" width="200">
        <template #default="scope">
          <el-button size="small" @click="viewDetail(scope.row.id)">查看</el-button>
          <el-button size="small" @click="editPig(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deletePig(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showAddDialog" :title="isEdit ? '编辑生猪档案' : '新建生猪档案'" width="600px">
      <el-form ref="pigFormRef" :model="pigForm" :rules="pigRules" label-width="100px">
        <el-form-item label="耳标号" prop="earTagNo">
          <el-input v-model="pigForm.earTagNo" placeholder="请输入耳标号" />
        </el-form-item>
        <el-form-item label="养殖场" prop="farmId">
          <el-select v-model="pigForm.farmId" placeholder="请选择养殖场">
            <el-option v-for="farm in farmOptions" :key="farm.id" :label="farm.name" :value="farm.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品种" prop="breed">
          <el-select v-model="pigForm.breed" placeholder="请选择品种">
            <el-option label="大白猪" value="大白猪" />
            <el-option label="长白猪" value="长白猪" />
            <el-option label="杜洛克" value="杜洛克" />
            <el-option label="三元杂交" value="三元杂交" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期" prop="birthDate">
          <el-date-picker v-model="pigForm.birthDate" type="date" placeholder="请选择出生日期" />
        </el-form-item>
        <el-form-item label="圈舍号" prop="penNo">
          <el-input v-model="pigForm.penNo" placeholder="请输入圈舍号" />
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-input v-model="pigForm.source" placeholder="请输入来源" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="savePig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePigStore } from '@/stores/pig'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import type { PigIndividual } from '@/types/pig'

const router = useRouter()
const pigStore = usePigStore()

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const pigList = ref<PigIndividual[]>([])

const showAddDialog = ref(false)
const isEdit = ref(false)
const pigFormRef = ref()

const searchForm = reactive({
  earTagNo: '',
  farmId: '',
  breed: '',
  status: '',
})

const pigForm = reactive({
  id: undefined,
  earTagNo: '',
  farmId: '',
  breed: '',
  birthDate: '',
  penNo: '',
  source: '',
})

const pigRules = {
  earTagNo: [{ required: true, message: '请输入耳标号', trigger: 'blur' }],
  farmId: [{ required: true, message: '请选择养殖场', trigger: 'change' }],
  breed: [{ required: true, message: '请选择品种', trigger: 'change' }],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }],
}

const statusOptions: Record<number, string> = {
  1: '在养',
  2: '已出栏',
  3: '已屠宰',
  4: '异常',
}

const statusTagConfig = {
  1: { label: '在养', type: 'success' as const },
  2: { label: '已出栏', type: 'info' as const },
  3: { label: '已屠宰', type: 'info' as const },
  4: { label: '异常', type: 'danger' as const },
}

const farmOptions = ref([
  { id: 1, name: '阳光养殖场' },
  { id: 2, name: '青山畜牧场' },
  { id: 3, name: '绿源农场' },
])

async function fetchPigList() {
  loading.value = true
  try {
    await pigStore.fetchPigList({
      ...searchForm,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    pigList.value = pigStore.pigList
    total.value = pigStore.total
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchPigList()
}

function handleReset() {
  pageNum.value = 1
  fetchPigList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchPigList()
}

function viewDetail(id: number) {
  router.push(`/admin/farm/pigs/${id}`)
}

function editPig(row: PigIndividual) {
  isEdit.value = true
  Object.assign(pigForm, {
    id: row.id,
    earTagNo: row.earTagNo,
    farmId: row.farmId,
    breed: row.breed,
    birthDate: row.birthDate,
    penNo: row.penNo,
    source: row.source,
  })
  showAddDialog.value = true
}

async function savePig() {
  if (!pigFormRef.value) return

  await pigFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      if (isEdit.value) {
        await pigStore.updatePig(pigForm.id!, pigForm)
        ElMessage.success('更新成功')
      } else {
        await pigStore.createPig(pigForm)
        ElMessage.success('创建成功')
      }
      showAddDialog.value = false
      fetchPigList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

async function deletePig(id: number) {
  ElMessage.warning('删除功能待后端支持')
}

onMounted(() => {
  fetchPigList()
})
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .card-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}
</style>