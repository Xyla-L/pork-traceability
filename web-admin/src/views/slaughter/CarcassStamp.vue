<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">检疫盖章</h2>
      <el-button type="primary" @click="showAddDialog = true">新增盖章</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="耳标号">
        <el-input v-model="searchForm.earTagNo" placeholder="请输入耳标号" clearable />
      </el-form-item>
      <el-form-item label="盖章编号">
        <el-input v-model="searchForm.stampNo" placeholder="请输入盖章编号" clearable />
      </el-form-item>
    </SearchPanel>

    <el-table :data="stampList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="pigId" label="生猪ID" width="80" />
      <el-table-column prop="earTagNo" label="耳标号" width="120" />
      <el-table-column prop="stampNo" label="盖章编号" width="150" />
      <el-table-column prop="stampTime" label="盖章时间" width="160" />
      <el-table-column prop="stampPosition" label="盖章位置" width="120" />
      <el-table-column prop="veterinary" label="兽医" width="100" />
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

    <el-dialog v-model="showAddDialog" title="新增检疫盖章" width="700px">
      <el-form ref="stampFormRef" :model="stampForm" :rules="stampRules" label-width="100px">
        <el-form-item label="耳标号" prop="pigId">
          <el-select v-model="stampForm.pigId" placeholder="请选择生猪">
            <el-option v-for="pig in pigOptions" :key="pig.id" :label="pig.earTagNo" :value="pig.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="盖章编号" prop="stampNo">
          <el-input v-model="stampForm.stampNo" placeholder="请输入盖章编号" />
        </el-form-item>
        <el-form-item label="盖章时间" prop="stampTime">
          <el-date-picker v-model="stampForm.stampTime" type="datetime" placeholder="请选择盖章时间" />
        </el-form-item>
        <el-form-item label="盖章位置" prop="stampPosition">
          <el-select v-model="stampForm.stampPosition" placeholder="请选择盖章位置">
            <el-option label="肩部" value="肩部" />
            <el-option label="臀部" value="臀部" />
            <el-option label="腹部" value="腹部" />
            <el-option label="腿部" value="腿部" />
          </el-select>
        </el-form-item>
        <el-form-item label="兽医" prop="veterinary">
          <el-input v-model="stampForm.veterinary" placeholder="请输入兽医姓名" />
        </el-form-item>
        <el-form-item label="盖章预览">
          <div class="stamp-preview">
            <div class="stamp-circle">
              <span class="stamp-text">{{ stampForm.stampNo }}</span>
              <span class="stamp-sub">{{ stampForm.stampPosition }}</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="电子签名">
          <SignaturePad @save="handleSignatureSave" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveStamp">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import SignaturePad from '@/components/common/SignaturePad.vue'
import type { CarcassStamp } from '@/types/slaughter'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const stampList = ref<CarcassStamp[]>([])

const showAddDialog = ref(false)
const stampFormRef = ref()

const searchForm = reactive({
  earTagNo: '',
  stampNo: '',
})

const stampForm = reactive({
  pigId: '',
  stampNo: '',
  stampTime: '',
  stampPosition: '',
  veterinary: '',
  eSignature: '',
})

const stampRules = {
  pigId: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  stampNo: [{ required: true, message: '请输入盖章编号', trigger: 'blur' }],
  stampTime: [{ required: true, message: '请选择盖章时间', trigger: 'change' }],
  stampPosition: [{ required: true, message: '请选择盖章位置', trigger: 'change' }],
  veterinary: [{ required: true, message: '请输入兽医姓名', trigger: 'blur' }],
}

const pigOptions = ref([
  { id: 1, earTagNo: 'PIG-001' },
  { id: 2, earTagNo: 'PIG-002' },
  { id: 3, earTagNo: 'PIG-003' },
])

async function fetchStampList() {
  loading.value = true
  try {
    stampList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchStampList()
}

function handleReset() {
  pageNum.value = 1
  fetchStampList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchStampList()
}

function viewDetail(row: CarcassStamp) {
  ElMessage.info(`查看详情: ${row.id}`)
}

function handleSignatureSave(dataUrl: string) {
  stampForm.eSignature = dataUrl
}

async function saveStamp() {
  if (!stampFormRef.value) return

  await stampFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('保存成功')
      showAddDialog.value = false
      fetchStampList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchStampList()
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

.stamp-preview {
  display: flex;
  justify-content: center;
  padding: 20px;

  .stamp-circle {
    width: 120px;
    height: 120px;
    border-radius: 50%;
    border: 4px solid #dc3545;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #dc3545;

    .stamp-text {
      font-size: 16px;
      font-weight: bold;
      letter-spacing: 2px;
    }

    .stamp-sub {
      font-size: 12px;
      margin-top: 4px;
    }
  }
}
</style>