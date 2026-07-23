<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">屠宰检验</h2>
      <el-button type="primary" @click="showAddDialog = true">新增检验</el-button>
    </div>

    <el-tabs v-model="inspectType" class="inspect-tabs">
      <el-tab-pane label="宰前检验" name="1" />
      <el-tab-pane label="宰后检验" name="2" />
    </el-tabs>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="耳标号">
        <el-input v-model="searchForm.earTagNo" placeholder="请输入耳标号" clearable />
      </el-form-item>
      <el-form-item label="检验结果">
        <el-select v-model="searchForm.result" placeholder="请选择结果" clearable>
          <el-option label="合格" :value="1" />
          <el-option label="不合格" :value="0" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="inspectList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="pigId" label="生猪ID" width="80" />
      <el-table-column prop="earTagNo" label="耳标号" width="120" />
      <el-table-column label="检验类型" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.inspectType === 1 ? 'info' : 'warning'">
            {{ scope.row.inspectType === 1 ? '宰前' : '宰后' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="inspectTime" label="检验时间" width="160" />
      <el-table-column label="检验结果" width="100">
        <template #default="scope">
          <StatusTag :status="scope.row.result" :config="resultStatusConfig" />
        </template>
      </el-table-column>
      <el-table-column prop="issueDesc" label="问题描述" width="150" show-overflow-tooltip />
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

    <el-dialog v-model="showAddDialog" :title="`${inspectType === '1' ? '宰前' : '宰后'}检验`" width="700px">
      <el-form ref="inspectFormRef" :model="inspectForm" :rules="inspectRules" label-width="100px">
        <el-form-item label="耳标号" prop="pigId">
          <el-select v-model="inspectForm.pigId" placeholder="请选择生猪">
            <el-option v-for="pig in pigOptions" :key="pig.id" :label="pig.earTagNo" :value="pig.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验时间" prop="inspectTime">
          <el-date-picker v-model="inspectForm.inspectTime" type="datetime" placeholder="请选择检验时间" />
        </el-form-item>

        <el-form-item label="脏器检查" v-if="inspectType === '2'">
          <div class="organ-grid">
            <div
              v-for="(label, key) in organOptions"
              :key="key"
              class="organ-item"
              :class="{ selected: inspectForm.organCheck[key] === '正常' }"
              @click="toggleOrgan(key)"
            >
              {{ label }}
              <span class="organ-status">{{ inspectForm.organCheck[key] || '未检查' }}</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="检验结果" prop="result">
          <el-radio-group v-model="inspectForm.result">
            <el-radio :value="1">合格</el-radio>
            <el-radio :value="0">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="问题描述">
          <textarea v-model="inspectForm.issueDesc" rows="3" placeholder="如有问题请填写描述" />
        </el-form-item>
        <el-form-item label="兽医" prop="veterinary">
          <el-input v-model="inspectForm.veterinary" placeholder="请输入兽医姓名" />
        </el-form-item>
        <el-form-item label="电子签名">
          <SignaturePad @save="handleSignatureSave" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveInspect">保存</el-button>
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
import SignaturePad from '@/components/common/SignaturePad.vue'
import type { SlaughterInspection } from '@/types/slaughter'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const inspectList = ref<SlaughterInspection[]>([])
const inspectType = ref('1')

const showAddDialog = ref(false)
const inspectFormRef = ref()

const searchForm = reactive({
  earTagNo: '',
  result: '',
})

const inspectForm = reactive({
  pigId: '',
  inspectType: 1,
  inspectTime: '',
  organCheck: {
    heart: '',
    liver: '',
    lung: '',
    kidney: '',
    lymphNode: '',
  },
  result: 1,
  issueDesc: '',
  veterinary: '',
  eSignature: '',
})

const inspectRules = {
  pigId: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  inspectTime: [{ required: true, message: '请选择检验时间', trigger: 'change' }],
  result: [{ required: true, message: '请选择检验结果', trigger: 'change' }],
  veterinary: [{ required: true, message: '请输入兽医姓名', trigger: 'blur' }],
}

const resultStatusConfig = {
  1: { label: '合格', type: 'success' as const },
  0: { label: '不合格', type: 'danger' as const },
}

const organOptions: Record<string, string> = {
  heart: '心脏',
  liver: '肝脏',
  lung: '肺脏',
  kidney: '肾脏',
  lymphNode: '淋巴结',
}

const pigOptions = ref([
  { id: 1, earTagNo: 'PIG-001' },
  { id: 2, earTagNo: 'PIG-002' },
  { id: 3, earTagNo: 'PIG-003' },
])

async function fetchInspectList() {
  loading.value = true
  try {
    inspectList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchInspectList()
}

function handleReset() {
  pageNum.value = 1
  fetchInspectList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchInspectList()
}

function viewDetail(row: SlaughterInspection) {
  ElMessage.info(`查看详情: ${row.id}`)
}

function toggleOrgan(key: string) {
  inspectForm.organCheck[key] = inspectForm.organCheck[key] === '正常' ? '' : '正常'
}

function handleSignatureSave(dataUrl: string) {
  inspectForm.eSignature = dataUrl
}

async function saveInspect() {
  if (!inspectFormRef.value) return

  await inspectFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success('保存成功')
      showAddDialog.value = false
      fetchInspectList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchInspectList()
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

.inspect-tabs {
  margin-bottom: 16px;
}

.organ-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;

  .organ-item {
    padding: 12px;
    border: 2px solid #dcdfe6;
    border-radius: 8px;
    text-align: center;
    cursor: pointer;
    transition: all 0.3s;

    &.selected {
      border-color: #67c23a;
      background: #f0f9eb;
    }

    .organ-status {
      display: block;
      font-size: 12px;
      color: #909399;
      margin-top: 4px;

      .selected & {
        color: #67c23a;
      }
    }
  }
}
</style>