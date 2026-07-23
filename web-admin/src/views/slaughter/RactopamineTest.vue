<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">瘦肉精检测</h2>
      <el-button type="primary" @click="showAddDialog = true">新增检测</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="耳标号">
        <el-input v-model="searchForm.earTagNo" placeholder="请输入耳标号" clearable />
      </el-form-item>
      <el-form-item label="检测结果">
        <el-select v-model="searchForm.result" placeholder="请选择结果" clearable>
          <el-option label="阴性" :value="1" />
          <el-option label="阳性" :value="0" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="testList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="pigId" label="生猪ID" width="80" />
      <el-table-column prop="earTagNo" label="耳标号" width="120" />
      <el-table-column prop="testTime" label="检测时间" width="160" />
      <el-table-column prop="testMethod" label="检测方法" width="120" />
      <el-table-column prop="samplePart" label="取样部位" width="120" />
      <el-table-column label="检测结果" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.result" :config="resultStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人员" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button size="small" @click="viewDetail(scope.row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showAddDialog" title="新增瘦肉精检测" width="600px">
      <el-form ref="testFormRef" :model="testForm" :rules="testRules" label-width="100px">
        <el-form-item label="耳标号" prop="pigId">
          <el-select v-model="testForm.pigId" placeholder="请选择生猪">
            <el-option v-for="pig in pigOptions" :key="pig.id" :label="pig.earTagNo" :value="pig.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检测时间" prop="testTime">
          <el-date-picker v-model="testForm.testTime" type="datetime" placeholder="请选择检测时间" />
        </el-form-item>
        <el-form-item label="检测方法" prop="testMethod">
          <el-select v-model="testForm.testMethod" placeholder="请选择检测方法">
            <el-option label="ELISA" value="ELISA" />
            <el-option label="LC-MS/MS" value="LC-MS/MS" />
            <el-option label="胶体金试纸" value="胶体金试纸" />
          </el-select>
        </el-form-item>
        <el-form-item label="取样部位" prop="samplePart">
          <el-select v-model="testForm.samplePart" placeholder="请选择取样部位">
            <el-option label="尿液" value="尿液" />
            <el-option label="肌肉" value="肌肉" />
            <el-option label="肝脏" value="肝脏" />
          </el-select>
        </el-form-item>
        <el-form-item label="检测结果" prop="result">
          <el-radio-group v-model="testForm.result">
            <el-radio :value="1">阴性</el-radio>
            <el-radio :value="0">阳性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检测人员" prop="operator">
          <el-input v-model="testForm.operator" placeholder="请输入检测人员" />
        </el-form-item>
        <el-form-item label="检测照片">
          <FileUpload @success="handleFileUpload" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTest">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import FileUpload from '@/components/common/FileUpload.vue'
import type { RactopamineTest } from '@/types/slaughter'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const testList = ref<RactopamineTest[]>([])

const showAddDialog = ref(false)
const testFormRef = ref()

const searchForm = reactive({
  earTagNo: '',
  result: '',
})

const testForm = reactive({
  pigId: '',
  testTime: '',
  testMethod: '',
  samplePart: '',
  result: 1,
  operator: '',
})

const testRules = {
  pigId: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  testTime: [{ required: true, message: '请选择检测时间', trigger: 'change' }],
  testMethod: [{ required: true, message: '请选择检测方法', trigger: 'change' }],
  samplePart: [{ required: true, message: '请选择取样部位', trigger: 'change' }],
  result: [{ required: true, message: '请选择检测结果', trigger: 'change' }],
  operator: [{ required: true, message: '请输入检测人员', trigger: 'blur' }],
}

const resultStatusConfig = {
  1: { label: '阴性', type: 'success' as const },
  0: { label: '阳性', type: 'danger' as const },
}

const pigOptions = ref([
  { id: 1, earTagNo: 'PIG-001' },
  { id: 2, earTagNo: 'PIG-002' },
  { id: 3, earTagNo: 'PIG-003' },
])

async function fetchTestList() {
  loading.value = true
  try {
    testList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchTestList()
}

function handleReset() {
  pageNum.value = 1
  fetchTestList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchTestList()
}

function viewDetail(row: RactopamineTest) {
  ElMessage.info(`查看详情: ${row.id}`)
}

function handleFileUpload(files: any[]) {
  ElMessage.info(`已上传 ${files.length} 个文件`)
}

async function saveTest() {
  if (!testFormRef.value) return

  await testFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('保存成功')
      showAddDialog.value = false
      fetchTestList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchTestList()
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