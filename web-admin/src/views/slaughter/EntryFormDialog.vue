<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="600px"
    destroy-on-close
    @update:model-value="handleVisibleChange"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item label="生猪耳标号" prop="earTagNo">
        <el-select
          v-model="formData.earTagNo"
          placeholder="输入耳标号搜索生猪"
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
            :label="`${pig.earTagNo}${pig.breed ? ' (' + pig.breed + ')' : ''}`"
            :value="pig.earTagNo"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="来源养殖场">
        <el-input v-model="formData.sourceFarm" placeholder="选择生猪后自动带出" readonly />
      </el-form-item>
      <el-form-item label="批次号" prop="batchNo">
        <el-input v-model="formData.batchNo" placeholder="请输入屠宰批次号" />
      </el-form-item>
      <el-form-item label="入场时间" prop="arriveTime">
        <el-date-picker
          v-model="formData.arriveTime"
          type="datetime"
          placeholder="选择入场时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="重量(kg)" prop="weight">
        <el-input-number v-model="formData.weight" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="检疫证明" prop="quarantineCert">
        <el-input v-model="formData.quarantineCert" placeholder="请输入检疫证明编号" />
      </el-form-item>
      <el-form-item label="车辆牌号" prop="vehicleNo">
        <el-input v-model="formData.vehicleNo" placeholder="请输入运输车辆牌号" />
      </el-form-item>
      <el-form-item label="查验员" prop="inspector">
        <el-input v-model="formData.inspector" placeholder="请输入查验员姓名" />
      </el-form-item>
      <el-form-item label="健康检查" prop="healthCheck">
        <el-select v-model="formData.healthCheck" style="width: 100%">
          <el-option label="通过" :value="1" />
          <el-option label="异常" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="检疫证核验" prop="certVerified">
        <el-select v-model="formData.certVerified" style="width: 100%">
          <el-option label="通过" :value="1" />
          <el-option label="异常" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="待查验" :value="0" />
          <el-option label="合格" :value="1" />
          <el-option label="不合格" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="查验意见" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入查验意见" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
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
  },
  // create = 新增，edit = 编辑
  mode: {
    type: String,
    default: 'edit'
  }
})

const emit = defineEmits(['update:visible', 'submit'])

const formRef = ref(null)
const submitting = ref(false)
const pigSearchLoading = ref(false)
const pigOptions = ref([])

const isEdit = computed(() => props.mode !== 'create')
const dialogTitle = computed(() => {
  return props.mode === 'create' ? '新增入场记录' : '编辑入场记录'
})

const formData = reactive({
  pigId: null,
  earTagNo: '',
  sourceFarm: '',
  batchNo: '',
  arriveTime: '',
  weight: 0,
  quarantineCert: '',
  vehicleNo: '',
  inspector: '',
  healthCheck: 1,
  certVerified: 1,
  status: 0,
  remark: ''
})

const formRules = {
  earTagNo: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  // 来源养殖场随生猪自动带出，不单独给出校验提示（生猪未选时仅提示“请选择生猪”）
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  arriveTime: [{ required: true, message: '请选择入场时间', trigger: 'change' }],
  weight: [{ required: true, message: '请输入重量', trigger: 'blur' }],
  healthCheck: [{ required: true, message: '请选择健康检查结果', trigger: 'change' }],
  certVerified: [{ required: true, message: '请选择检疫证核验结果', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

// 远程搜索生猪
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

// 选择生猪后自动带出 pigId 和来源养殖场
const handlePigSelect = async (earTagNo) => {
  const pig = pigOptions.value.find((p) => p.earTagNo === earTagNo)
  if (!pig) return
  formData.pigId = pig.id
  // 优先用猪档案自带的 farmName；为空则按 farmId 再查一次养殖场
  if (pig.farmName) {
    formData.sourceFarm = pig.farmName
  } else if (pig.farmId) {
    try {
      const farm = await request.get(`/breeding/farms/${pig.farmId}`)
      formData.sourceFarm = farm?.farmName || farm?.name || ''
    } catch (e) {
      console.error('获取养殖场名称失败:', e)
      formData.sourceFarm = ''
    }
  } else {
    formData.sourceFarm = ''
  }
}

const resetForm = () => {
  formData.pigId = null
  formData.earTagNo = ''
  formData.sourceFarm = ''
  formData.batchNo = ''
  formData.arriveTime = ''
  formData.weight = 0
  formData.quarantineCert = ''
  formData.vehicleNo = ''
  formData.inspector = ''
  formData.healthCheck = 1
  formData.certVerified = 1
  formData.status = 0
  formData.remark = ''
  pigOptions.value = []
}

watch(() => props.visible, (val) => {
  if (val) {
    if (props.editData) {
      // 编辑模式回填
      formData.pigId = props.editData.pigId ?? null
      formData.earTagNo = props.editData.earTagNo || ''
      formData.sourceFarm = props.editData.sourceFarm || ''
      formData.batchNo = props.editData.batchNo || ''
      formData.arriveTime = props.editData.arriveTime || ''
      formData.weight = props.editData.weight ?? 0
      formData.quarantineCert = props.editData.quarantineCert || ''
      formData.vehicleNo = props.editData.vehicleNo || ''
      formData.inspector = props.editData.inspector || ''
      formData.healthCheck = props.editData.healthCheck ?? 1
      formData.certVerified = props.editData.certVerified ?? 1
      formData.status = props.editData.status ?? 0
      formData.remark = props.editData.remark || ''
      // 编辑时把当前耳标号放入选项，保证下拉能显示
      if (formData.earTagNo) {
        pigOptions.value = [{ id: formData.pigId, earTagNo: formData.earTagNo, breed: '', farmName: formData.sourceFarm }]
      }
    } else {
      resetForm()
    }
    // 等待字段变更触发的异步 change 校验全部执行完，再清除校验态，
    // 否则重新打开新增弹窗时残留的红字会在 clearValidate 之后再次出现
    nextTick(() => {
      formRef.value?.clearValidate()
    })
  }
})

const handleVisibleChange = (val) => {
  emit('update:visible', val)
}

const handleClose = () => {
  emit('update:visible', false)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    const payload = { ...formData }
    if (props.editData) {
      await request.put(`/slaughter/entries/${props.editData.id}`, payload)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/slaughter/entries', payload)
      ElMessage.success('新增成功')
    }
    emit('submit', payload)
    emit('update:visible', false)
  } catch (error) {
    if (error !== false) {
      console.error('提交失败:', error)
      ElMessage.error('操作失败')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
</style>
