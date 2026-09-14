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
        <div style="display: flex; gap: 8px; width: 100%">
          <el-select
            v-model="formData.farmId"
            placeholder="请选择养殖场"
            filterable
            style="flex: 1"
          >
            <el-option
              v-for="farm in farmList"
              :key="farm.id"
              :label="farm.farmName || farm.name"
              :value="farm.id"
            />
          </el-select>
          <el-button type="primary" plain @click="farmDialogVisible = true">
            <el-icon><Plus /></el-icon>新建
          </el-button>
        </div>
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
      <el-form-item label="性别" prop="gender">
        <el-radio-group v-model="formData.gender">
          <el-radio :value="1">公</el-radio>
          <el-radio :value="2">母</el-radio>
        </el-radio-group>
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

  <!-- 新建养殖场弹窗 -->
  <el-dialog
    v-model="farmDialogVisible"
    title="新建养殖场"
    width="480px"
    :close-on-click-modal="false"
    append-to-body
    @close="resetFarmForm"
  >
    <el-form
      ref="farmFormRef"
      :model="farmForm"
      :rules="farmRules"
      label-width="100px"
      class="pig-form"
    >
      <el-form-item label="养殖场名称" prop="farmName">
        <el-input v-model="farmForm.farmName" placeholder="请输入养殖场名称" maxlength="128" clearable />
      </el-form-item>
      <el-form-item label="许可证号" prop="licenseNo">
        <el-input v-model="farmForm.licenseNo" placeholder="请输入许可证编号" maxlength="32" clearable />
      </el-form-item>
      <el-form-item label="地址" prop="address">
        <el-input v-model="farmForm.address" placeholder="请输入地址" maxlength="256" clearable />
      </el-form-item>
      <el-form-item label="联系人" prop="contactPerson">
        <el-input v-model="farmForm.contactPerson" placeholder="请输入联系人" maxlength="32" clearable />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="farmForm.contactPhone" placeholder="请输入联系电话" maxlength="20" clearable />
      </el-form-item>
      <el-form-item label="养殖规模" prop="scale">
        <el-input-number v-model="farmForm.scale" :min="1" :max="99999" style="width: 200px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="farmDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="farmSubmitting" @click="handleFarmSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
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

const emit = defineEmits(['update:visible', 'saved', 'farmCreated'])

const formRef = ref(null)
const submitting = ref(false)
const farmList = ref([])

const isEdit = computed(() => !!props.editData)

const formData = reactive({
  earTagNo: '',
  farmId: '',
  breed: '',
  birthDate: '',
  gender: 1,
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
    farmList.value = res?.records || res?.list || []
  } catch (error) {
    console.error('获取养殖场列表失败:', error)
  }
}

// ========== 新建养殖场 ==========
const farmDialogVisible = ref(false)
const farmSubmitting = ref(false)
const farmFormRef = ref(null)
const farmForm = reactive({
  farmName: '',
  licenseNo: '',
  address: '',
  contactPerson: '',
  contactPhone: '',
  scale: 100
})
const farmRules = {
  farmName: [{ required: true, message: '请输入养殖场名称', trigger: 'blur' }],
  licenseNo: [{ required: true, message: '请输入许可证编号', trigger: 'blur' }],
  scale: [{ required: true, message: '请输入养殖规模', trigger: 'blur' }]
}

const resetFarmForm = () => {
  Object.assign(farmForm, {
    farmName: '', licenseNo: '', address: '', contactPerson: '', contactPhone: '', scale: 100
  })
  farmFormRef.value?.resetFields()
}

const handleFarmSubmit = async () => {
  try {
    await farmFormRef.value.validate()
  } catch {
    return
  }
  farmSubmitting.value = true
  try {
    await request.post('/breeding/farms', { ...farmForm })
    ElMessage.success('养殖场创建成功')
    farmDialogVisible.value = false
    // 通知父组件刷新养殖场映射表
    emit('farmCreated')
    // 刷新下拉列表并自动选中新创建的养殖场
    await fetchFarmList()
    const newFarm = farmList.value[farmList.value.length - 1]
    if (newFarm) {
      formData.farmId = newFarm.id
    }
  } finally {
    farmSubmitting.value = false
  }
}

const resetForm = () => {
  Object.assign(formData, {
    earTagNo: '',
    farmId: '',
    breed: '',
    birthDate: '',
    gender: 1,
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
          gender: val.gender || 1,
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
    // 由本组件直接调用后端：成功后才关弹窗，失败时保持弹窗打开（错误由 request 拦截器提示）
    if (isEdit.value) {
      await request.put(`/breeding/pigs/${props.editData.id}`, { ...formData })
    } else {
      await request.post('/breeding/pigs', { ...formData })
    }
    ElMessage.success(isEdit.value ? '编辑成功' : '新建成功')
    emit('saved')
    handleClose()
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
