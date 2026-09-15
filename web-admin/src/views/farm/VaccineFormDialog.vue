<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑疫苗记录' : '录入疫苗记录'"
    width="580px"
    :close-on-click-modal="false"
    destroy-on-close
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
          :disabled="isEdit"
          style="width: 100%"
          @change="handlePigSelect"
        >
          <el-option
            v-for="pig in pigOptions"
            :key="pig.id"
            :label="pig.breed ? `${pig.earTagNo} (${pig.breed})` : pig.earTagNo"
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
          placeholder="请输入剂量，如 2ml"
          maxlength="30"
          clearable
        />
      </el-form-item>
      <el-form-item label="操作人" prop="operator">
        <el-input
          v-model="formData.operator"
          placeholder="请输入操作人姓名"
          maxlength="20"
          clearable
        />
      </el-form-item>
      <el-form-item label="凭证照片" prop="fileIds">
        <div v-loading="photoLoading" class="upload-wrapper">
          <el-upload
            v-model:file-list="fileList"
            action="/api/v1/file/upload"
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
        </div>
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
import { ref, reactive, computed, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getToken } from '@/utils/auth'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  // 传入记录对象时为编辑模式，null 时为录入模式
  editData: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'saved'])

const isEdit = computed(() => !!props.editData)

const formRef = ref(null)
const submitting = ref(false)
const pigSearchLoading = ref(false)
const photoLoading = ref(false)
const pigOptions = ref([])
const fileList = ref([])
// 上传成功后由 file-service 返回的文件ID，提交时随疫苗记录一起保存
const fileIds = ref([])
// 编辑回显时生成的 blob 临时地址，关闭弹窗时统一释放
const blobUrls = ref([])
// 照片回显请求序号：防止快速切换记录时慢请求晚返回导致照片串台
let photoLoadSeq = 0

const formData = reactive({
  earTagNo: '',
  pigId: '',
  vaccineName: '',
  batchNo: '',
  injectTime: '',
  dosage: '',
  operator: ''
})

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${getToken() || ''}`
}))

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
      params: { earTagNo: query, size: 20 }
    })
    pigOptions.value = res?.records || res?.list || []
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
  // el-upload 不走 axios 拦截器，响应为后端原始包装：{ code, data: { fileId, url, ... } }
  const fileId = response?.data?.fileId || response?.fileId || ''
  if (fileId && !fileIds.value.includes(fileId)) {
    fileIds.value.push(fileId)
  }
}

const handleUploadRemove = (uploadFile) => {
  // 限制只能传 1 张，移除时清空文件ID（提交后端会整体覆盖，等于删除原照片）
  fileIds.value = []
  if (uploadFile?.url && blobUrls.value.includes(uploadFile.url)) {
    URL.revokeObjectURL(uploadFile.url)
    blobUrls.value = blobUrls.value.filter((u) => u !== uploadFile.url)
  }
}

/**
 * 编辑模式下回显已有凭证：
 * fileIds 原样保留（提交时后端整体覆盖，不清空才不会丢照片），
 * 图片本身需带鉴权以 blob 方式拉取后生成临时地址供缩略图显示
 */
const loadExistingPhotos = async (ids) => {
  const list = Array.isArray(ids) ? ids : []
  const seq = ++photoLoadSeq
  fileIds.value = [...list]
  if (list.length === 0) {
    fileList.value = []
    return
  }
  photoLoading.value = true
  try {
    const blobs = await Promise.all(
      list.map((id) => request.get(`/file/${id}`, { responseType: 'blob' }))
    )
    // 返回期间已切换到其他记录或关闭弹窗，则丢弃本次结果
    if (seq !== photoLoadSeq) return
    fileList.value = blobs.map((blob, index) => {
      const url = URL.createObjectURL(blob)
      blobUrls.value.push(url)
      return {
        name: `凭证照片${index + 1}`,
        url,
        uid: `existing-${list[index]}`,
        fileId: list[index]
      }
    })
  } catch (error) {
    if (seq !== photoLoadSeq) return
    console.error('凭证照片回显失败:', error)
    fileList.value = []
  } finally {
    if (seq === photoLoadSeq) photoLoading.value = false
  }
}

const revokeBlobUrls = () => {
  blobUrls.value.forEach((url) => URL.revokeObjectURL(url))
  blobUrls.value = []
}

const resetForm = () => {
  // 使在途的照片回显请求失效，避免弹窗关闭后晚返回的结果写入并泄漏 blob URL
  photoLoadSeq++
  Object.assign(formData, {
    earTagNo: '',
    pigId: '',
    vaccineName: '',
    batchNo: '',
    injectTime: '',
    dosage: '',
    operator: ''
  })
  fileList.value = []
  fileIds.value = []
  pigOptions.value = []
  photoLoading.value = false
  revokeBlobUrls()
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

  // 提交前兜底校验：pigId 未选则提示
  if (!formData.pigId) {
    ElMessage.warning('请先选择生猪')
    return
  }

  submitting.value = true
  const payload = {
    pigId: formData.pigId,
    vaccineName: formData.vaccineName,
    batchNo: formData.batchNo,
    injectTime: formData.injectTime,
    dosage: formData.dosage,
    operator: formData.operator,
    fileIds: fileIds.value
  }
  try {
    // 由本组件直接调用后端：成功后才关弹窗，失败保持弹窗打开（错误由 request 拦截器提示）
    if (isEdit.value) {
      await request.put(
        `/breeding/pigs/${formData.pigId}/vaccines/${props.editData.id}`,
        payload
      )
    } else {
      await request.post(`/breeding/pigs/${formData.pigId}/vaccines`, payload)
    }
    ElMessage.success(isEdit.value ? '编辑成功' : '录入成功')
    emit('saved')
    handleClose()
  } finally {
    submitting.value = false
  }
}

// 编辑模式：editData 有值时回填表单与已有凭证照片
watch(
  () => props.editData,
  async (val) => {
    if (!val) {
      resetForm()
      return
    }
    Object.assign(formData, {
      earTagNo: val.earTagNo || '',
      pigId: val.pigId ?? '',
      vaccineName: val.vaccineName || '',
      batchNo: val.batchNo || '',
      injectTime: val.injectTime || '',
      dosage: val.dosage ?? '',
      operator: val.operator || ''
    })
    // 远程搜索下拉框中必须存在一个匹配选项，否则已选耳标号无法回显
    pigOptions.value = [{ id: val.pigId, earTagNo: val.earTagNo, breed: '' }]
    await loadExistingPhotos(val.fileIds)
    formRef.value?.clearValidate()
  },
  { immediate: true }
)

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

  .upload-wrapper {
    min-height: 148px;
  }

  .upload-tip {
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
    margin-top: 4px;
  }
}
</style>
