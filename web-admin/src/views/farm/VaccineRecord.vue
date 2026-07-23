<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">疫苗记录</h2>
      <el-button type="primary" @click="showAddDialog = true">添加疫苗</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="耳标号">
        <el-input v-model="searchForm.earTagNo" placeholder="请输入耳标号" clearable />
      </el-form-item>
      <el-form-item label="疫苗名称">
        <el-input v-model="searchForm.vaccineName" placeholder="请输入疫苗名称" clearable />
      </el-form-item>
    </SearchPanel>

    <el-table :data="vaccineList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="pigId" label="生猪ID" width="80" />
      <el-table-column prop="earTagNo" label="耳标号" width="120" />
      <el-table-column prop="vaccineName" label="疫苗名称" width="150" />
      <el-table-column prop="batchNo" label="批次号" width="120" />
      <el-table-column prop="injectTime" label="注射时间" width="160" />
      <el-table-column prop="dosage" label="剂量" width="100" />
      <el-table-column prop="operator" label="操作人员" width="120" />
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button size="small" @click="deleteVaccine(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showAddDialog" title="添加疫苗记录" width="600px">
      <el-form ref="vaccineFormRef" :model="vaccineForm" :rules="vaccineRules" label-width="100px">
        <el-form-item label="耳标号" prop="pigId">
          <el-select v-model="vaccineForm.pigId" placeholder="请选择生猪">
            <el-option v-for="pig in pigOptions" :key="pig.id" :label="pig.earTagNo" :value="pig.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="疫苗名称" prop="vaccineName">
          <el-input v-model="vaccineForm.vaccineName" placeholder="请输入疫苗名称" />
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <el-input v-model="vaccineForm.batchNo" placeholder="请输入批次号" />
        </el-form-item>
        <el-form-item label="注射时间" prop="injectTime">
          <el-date-picker v-model="vaccineForm.injectTime" type="datetime" placeholder="请选择注射时间" />
        </el-form-item>
        <el-form-item label="剂量" prop="dosage">
          <el-input v-model="vaccineForm.dosage" placeholder="请输入剂量" />
        </el-form-item>
        <el-form-item label="操作人员" prop="operator">
          <el-input v-model="vaccineForm.operator" placeholder="请输入操作人员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveVaccine">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { usePigStore } from '@/stores/pig'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import type { VaccineRecord } from '@/types/pig'

const pigStore = usePigStore()

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const vaccineList = ref<VaccineRecord[]>([])

const showAddDialog = ref(false)
const vaccineFormRef = ref()

const searchForm = reactive({
  earTagNo: '',
  vaccineName: '',
})

const vaccineForm = reactive({
  pigId: '',
  vaccineName: '',
  batchNo: '',
  injectTime: '',
  dosage: '',
  operator: '',
})

const vaccineRules = {
  pigId: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  vaccineName: [{ required: true, message: '请输入疫苗名称', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  injectTime: [{ required: true, message: '请选择注射时间', trigger: 'change' }],
}

const pigOptions = ref([
  { id: 1, earTagNo: 'PIG-001' },
  { id: 2, earTagNo: 'PIG-002' },
  { id: 3, earTagNo: 'PIG-003' },
])

async function fetchVaccineList() {
  loading.value = true
  try {
    await pigStore.fetchVaccines(1)
    vaccineList.value = pigStore.vaccineList
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchVaccineList()
}

function handleReset() {
  pageNum.value = 1
  fetchVaccineList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchVaccineList()
}

async function saveVaccine() {
  if (!vaccineFormRef.value) return

  await vaccineFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      await pigStore.addVaccine(vaccineForm.pigId, vaccineForm)
      ElMessage.success('添加成功')
      showAddDialog.value = false
      fetchVaccineList()
    } catch (error: any) {
      ElMessage.error(error.message || '添加失败')
    }
  })
}

async function deleteVaccine(id: number) {
  ElMessage.warning('删除功能待后端支持')
}

onMounted(() => {
  fetchVaccineList()
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