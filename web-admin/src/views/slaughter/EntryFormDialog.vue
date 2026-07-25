<template>
  <el-dialog
    :model-value="visible"
    :title="editData ? '编辑入场记录' : '新增入场记录'"
    width="600px"
    @update:model-value="handleVisibleChange"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="批次号" prop="batchNo">
        <el-input v-model="formData.batchNo" placeholder="请输入批次号" />
      </el-form-item>
      <el-form-item label="耳标号" prop="earTagNo">
        <el-input v-model="formData.earTagNo" placeholder="请输入耳标号" />
      </el-form-item>
      <el-form-item label="来源养殖场" prop="sourceFarm">
        <el-input v-model="formData.sourceFarm" placeholder="请输入来源养殖场" />
      </el-form-item>
      <el-form-item label="入场时间" prop="arrivalTime">
        <el-date-picker
          v-model="formData.arrivalTime"
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
      <el-form-item label="查验员" prop="inspector">
        <el-input v-model="formData.inspector" placeholder="请输入查验员姓名" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="待查验" value="待查验" />
          <el-option label="合格" value="合格" />
          <el-option label="不合格" value="不合格" />
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
import { ref, reactive, watch } from 'vue'
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

const formData = reactive({
  batchNo: '',
  earTagNo: '',
  sourceFarm: '',
  arrivalTime: '',
  weight: 0,
  quarantineCert: '',
  inspector: '',
  status: '待查验',
  remark: ''
})

const formRules = {
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  earTagNo: [{ required: true, message: '请输入耳标号', trigger: 'blur' }],
  sourceFarm: [{ required: true, message: '请输入来源养殖场', trigger: 'blur' }],
  arrivalTime: [{ required: true, message: '请选择入场时间', trigger: 'change' }],
  weight: [{ required: true, message: '请输入重量', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const resetForm = () => {
  formData.batchNo = ''
  formData.earTagNo = ''
  formData.sourceFarm = ''
  formData.arrivalTime = ''
  formData.weight = 0
  formData.quarantineCert = ''
  formData.inspector = ''
  formData.status = '待查验'
  formData.remark = ''
  formRef.value?.clearValidate()
}

watch(() => props.visible, (val) => {
  if (val) {
    if (props.editData) {
      Object.assign(formData, props.editData)
    } else {
      resetForm()
    }
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
    if (props.editData) {
      await request.put(`/slaughter/entries/${props.editData.id}`, formData)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/slaughter/entries', formData)
      ElMessage.success('新增成功')
    }
    emit('submit', formData)
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
