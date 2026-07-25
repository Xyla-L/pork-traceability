<template>
  <el-dialog
    :model-value="visible"
    title="录入疫苗记录"
    width="580px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="110px"
      class="vaccine-form"
    >
      <el-form-item label="生猪耳标号" prop="earTagNo">
        <el-select
          v-model="formData.earTagNo"
          placeholder="请搜索选择生猪"
          filterable
          remote
          :remote-method="searchPigs"
          :loading="pigSearchLoading"
          style="width: 100%"
          @change="handlePigSelect"
        >
          <el-option
            v-for="pig in pigOptions"
            :key="pig.id"
            :label="`${pig.earTagNo} (${pig.breed})`"
            :value="pig.earTagNo"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="疫苗名称" prop="vaccineName">
        <el-input
          v-model="formData.vaccineName"
          placeholder="请输入疫苗名称"
          maxlength="50"
          clearable
        />
      </el-form-item>
      <el-form-item label="疫苗批次号" prop="batchNo">
        <el-input
          v-model="formData.batchNo"
          placeholder="请输入疫苗批次号"
          maxlength="50"
          clearable
        />
      </el-form-item>
      <el-form-item label="注射时间" prop="injectTime">
        <el-date-picker
          v-model="formData.injectTime"
          type="datetime"
          placeholder="请选择注射时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="剂量" prop="dosage">
        <el-input
          v-model="formData.dosage"
          placeholder="请输入剂量（ml）"
          type="number"
          min="0"
          clearable
        >
          <template #append>ml</template>
        </el-input>
      </el-form-item>
      <el-form-item label="操作人" prop="operator">
        <el-input
          v-model="formData.operator"
          placeholder="请输入操作人姓名"
          maxlength="20"
          clearable
        />
      </el-form-item>
      <el-form-item label="凭证照片" prop="certPhoto">
        <el-upload
          v-model:file-list="fileList"
          action="/api/v1/breeding/vaccines/upload"
          :headers="uploadHeaders"
          list-type="picture-card"
          :limit="1"
          :on-success="handleUploadSuccess"
          :on-remove="handleUploadRemove"
          :before-upload="beforeUpload"
          accept="image/*"
        >
          <el-icon><Plus /></el-icon>
        </el-upload>
        <div class="upload-tip">支持 jpg、png 格式，不超过 5MB</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确 定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'submit'])

const formRef = ref(null)
const submitting = ref(false)
const pigSearchLoading = ref(false)
const pigOptions = ref([])
const fileList = ref([])

const formData = reactive({
  earTagNo: '',
  pigId: '',
  vaccineName: '',
  batchNo: '',
  injectTime: '',
  dosage: '',
  operator: '',
  certPhoto: ''
})

const uploadHeaders = {
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}

const rules = {
  earTagNo: [
    { required: true, message: '请选择生猪', trigger: 'change' }
  ],
  vaccineName: [
    { required: true, message: '请输入疫苗名称', trigger: 'blur' }
  ],
  batchNo: [
    { required: true, message: '请输入疫苗批次号', trigger: 'blur' }
  ],
  injectTime: [
    { required: true, message: '请选择注射时间', trigger: 'change' }
  ],
  dosage: [
    { required: true, message: '请输入剂量', trigger: 'blur' }
  ],
  operator: [
    { required: true, message: '请输入操作人', trigger: 'blur' }
  ]
}

// 搜索生猪
const searchPigs = async (query) => {
  if (!query) {
    pigOptions.value = []
    return
  }
  pigSearchLoading.value = true
  try {
    const res = await request.get('/breeding/pigs', {
      params: { earTagNo: query, pageSize: 20 }
    })
    pigOptions.value = res.data?.records || res.data?.list || res.list || []
  } catch (error) {
    console.error('搜索生猪失败:', error)
  } finally {
    pigSearchLoading.value = false
  }
}

const handlePigSelect = (earTagNo) => {
  const selectedPig = pigOptions.value.find((p) => p.earTagNo === earTagNo)
  if (selectedPig) {
    formData.pigId = selectedPig.id
  }
}

// 图片上传相关
const beforeUpload = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB!')
    return false
  }
  return true
}

const handleUploadSuccess = (response) => {
  formData.certPhoto = response.data?.url || response.url || ''
}

const handleUploadRemove = () => {
  formData.certPhoto = ''
}

const resetForm = () => {
  Object.assign(formData, {
    earTagNo: '',
    pigId: '',
    vaccineName: '',
    batchNo: '',
    injectTime: '',
    dosage: '',
    operator: '',
    certPhoto: ''
  })
  fileList.value = []
  formRef.value?.resetFields()
}

const handleClose = () => {
  resetForm()
  emit('update:visible', false)
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    emit('submit', { ...formData })
    handleClose()
    ElMessage.success('录入成功')
  } finally {
    submitting.value = false
  }
}

// 监听弹窗关闭时重置
watch(
  () => props.visible,
  (val) => {
    if (!val) {
      resetForm()
    }
  }
)
</script>

<style lang="scss" scoped>
.vaccine-form {
  padding: 20px 10px 0;

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  .upload-tip {
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
    margin-top: 4px;
  }
}
</style>
