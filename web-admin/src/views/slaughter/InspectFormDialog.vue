<template>
  <el-dialog
    :model-value="visible"
    :title="editData ? '编辑检验记录' : '新增检验记录'"
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
      <el-form-item label="检验编号" prop="inspectNo">
        <el-input v-model="formData.inspectNo" placeholder="请输入检验编号" />
      </el-form-item>
      <el-form-item label="批次号" prop="batchNo">
        <el-input v-model="formData.batchNo" placeholder="请输入批次号" />
      </el-form-item>
      <el-form-item label="耳标号" prop="earTagNo">
        <el-input v-model="formData.earTagNo" placeholder="请输入耳标号" />
      </el-form-item>
      <el-form-item label="检验类型" prop="inspectType">
        <el-select v-model="formData.inspectType" placeholder="请选择检验类型" style="width: 100%">
          <el-option label="宰前检验" value="宰前检验" />
          <el-option label="宰后检验" value="宰后检验" />
          <el-option label="同步检验" value="同步检验" />
        </el-select>
      </el-form-item>
      <el-form-item label="检验时间" prop="inspectTime">
        <el-date-picker
          v-model="formData.inspectTime"
          type="datetime"
          placeholder="选择检验时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="检验员" prop="inspector">
        <el-input v-model="formData.inspector" placeholder="请输入检验员姓名" />
      </el-form-item>
      <el-form-item label="体温(°C)" prop="temperature">
        <el-input-number v-model="formData.temperature" :min="0" :max="50" :precision="1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="待检验" value="待检验" />
          <el-option label="合格" value="合格" />
          <el-option label="不合格" value="不合格" />
        </el-select>
      </el-form-item>
      <el-form-item label="检验结论" prop="conclusion">
        <el-input v-model="formData.conclusion" type="textarea" :rows="3" placeholder="请输入检验结论" />
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
  inspectNo: '',
  batchNo: '',
  earTagNo: '',
  inspectType: '宰前检验',
  inspectTime: '',
  inspector: '',
  temperature: 0,
  status: '待检验',
  conclusion: ''
})

const formRules = {
  inspectNo: [{ required: true, message: '请输入检验编号', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  earTagNo: [{ required: true, message: '请输入耳标号', trigger: 'blur' }],
  inspectType: [{ required: true, message: '请选择检验类型', trigger: 'change' }],
  inspectTime: [{ required: true, message: '请选择检验时间', trigger: 'change' }],
  inspector: [{ required: true, message: '请输入检验员', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const resetForm = () => {
  formData.inspectNo = ''
  formData.batchNo = ''
  formData.earTagNo = ''
  formData.inspectType = '宰前检验'
  formData.inspectTime = ''
  formData.inspector = ''
  formData.temperature = 0
  formData.status = '待检验'
  formData.conclusion = ''
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
      await request.put(`/slaughter/inspections/${props.editData.id}`, formData)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/slaughter/inspections', formData)
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
