<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑生猪档案' : '新建生猪档案'"
    width="560px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
      class="pig-form"
    >
      <el-form-item label="耳标号" prop="earTagNo">
        <el-input
          v-model="formData.earTagNo"
          placeholder="请输入耳标号"
          :disabled="isEdit"
          maxlength="30"
          clearable
        />
      </el-form-item>
      <el-form-item label="养殖场" prop="farmId">
        <el-select
          v-model="formData.farmId"
          placeholder="请选择养殖场"
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="farm in farmList"
            :key="farm.id"
            :label="farm.name"
            :value="farm.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="品种" prop="breed">
        <el-select
          v-model="formData.breed"
          placeholder="请选择品种"
          style="width: 100%"
        >
          <el-option
            v-for="item in breedOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="出生日期" prop="birthDate">
        <el-date-picker
          v-model="formData.birthDate"
          type="date"
          placeholder="请选择出生日期"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="圈舍号" prop="penNo">
        <el-input
          v-model="formData.penNo"
          placeholder="请输入圈舍号"
          maxlength="20"
          clearable
        />
      </el-form-item>
      <el-form-item label="来源" prop="source">
        <el-radio-group v-model="formData.source">
          <el-radio value="自繁">自繁</el-radio>
          <el-radio value="外购">外购</el-radio>
        </el-radio-group>
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  editData: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'submit'])

const formRef = ref(null)
const submitting = ref(false)
const farmList = ref([])

const isEdit = computed(() => !!props.editData)

const formData = reactive({
  earTagNo: '',
  farmId: '',
  breed: '',
  birthDate: '',
  penNo: '',
  source: '自繁'
})

const breedOptions = [
  { label: '长白猪', value: '长白猪' },
  { label: '大白猪', value: '大白猪' },
  { label: '杜洛克', value: '杜洛克' },
  { label: '皮特兰', value: '皮特兰' }
]

const rules = {
  earTagNo: [
    { required: true, message: '请输入耳标号', trigger: 'blur' }
  ],
  farmId: [
    { required: true, message: '请选择养殖场', trigger: 'change' }
  ],
  breed: [
    { required: true, message: '请选择品种', trigger: 'change' }
  ],
  birthDate: [
    { required: true, message: '请选择出生日期', trigger: 'change' }
  ],
  source: [
    { required: true, message: '请选择来源', trigger: 'change' }
  ]
}

// 获取养殖场列表
const fetchFarmList = async () => {
  try {
    const res = await request.get('/breeding/farms')
    farmList.value = res.data || res.list || []
  } catch (error) {
    console.error('获取养殖场列表失败:', error)
  }
}

const resetForm = () => {
  Object.assign(formData, {
    earTagNo: '',
    farmId: '',
    breed: '',
    birthDate: '',
    penNo: '',
    source: '自繁'
  })
  formRef.value?.resetFields()
}

// 监听 editData 变化，回填表单
watch(
  () => props.editData,
  (val) => {
    if (val) {
      Object.assign(formData, {
        earTagNo: val.earTagNo || '',
        farmId: val.farmId || '',
        breed: val.breed || '',
        birthDate: val.birthDate || '',
        penNo: val.penNo || '',
        source: val.source || '自繁'
      })
    } else {
      resetForm()
    }
  },
  { immediate: true }
)

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
    ElMessage.success(isEdit.value ? '编辑成功' : '新建成功')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchFarmList()
})
</script>

<style lang="scss" scoped>
.pig-form {
  padding: 20px 10px 0;

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }
}
</style>
