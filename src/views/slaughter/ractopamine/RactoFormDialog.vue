<template>
  <el-dialog
    :model-value="visible"
    :title="editData ? '编辑检测记录' : '新增检测记录'"
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
      <el-form-item label="检测编号" prop="testNo">
        <el-input v-model="formData.testNo" placeholder="请输入检测编号" />
      </el-form-item>
      <el-form-item label="批次号" prop="batchNo">
        <el-input v-model="formData.batchNo" placeholder="请输入批次号" />
      </el-form-item>
      <el-form-item label="样本编号" prop="sampleNo">
        <el-input v-model="formData.sampleNo" placeholder="请输入样本编号" />
      </el-form-item>
      <el-form-item label="检测项目" prop="testType">
        <el-select v-model="formData.testType" placeholder="请选择检测项目" style="width: 100%">
          <el-option label="瘦肉精检测" value="瘦肉精检测" />
          <el-option label="克伦特罗检测" value="克伦特罗检测" />
          <el-option label="莱克多巴胺检测" value="莱克多巴胺检测" />
          <el-option label="沙丁胺醇检测" value="沙丁胺醇检测" />
        </el-select>
      </el-form-item>
      <el-form-item label="检测时间" prop="testTime">
        <el-date-picker
          v-model="formData.testTime"
          type="datetime"
          placeholder="选择检测时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="检测员" prop="tester">
        <el-input v-model="formData.tester" placeholder="请输入检测员姓名" />
      </el-form-item>
      <el-form-item label="检测结果" prop="result">
        <el-radio-group v-model="formData.result">
          <el-radio value="阴性">阴性</el-radio>
          <el-radio value="阳性">阳性</el-radio>
          <el-radio value="待检测">待检测</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="待检测" value="待检测" />
          <el-option label="检测中" value="检测中" />
          <el-option label="已完成" value="已完成" />
        </el-select>
      </el-form-item>
      <el-form-item label="检测报告" prop="reportUrl">
        <el-input v-model="formData.reportUrl" placeholder="请输入报告链接" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
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
  testNo: '',
  batchNo: '',
  sampleNo: '',
  testType: '瘦肉精检测',
  testTime: '',
  tester: '',
  result: '待检测',
  status: '待检测',
  reportUrl: '',
  remark: ''
})

const formRules = {
  testNo: [{ required: true, message: '请输入检测编号', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  sampleNo: [{ required: true, message: '请输入样本编号', trigger: 'blur' }],
  testType: [{ required: true, message: '请选择检测项目', trigger: 'change' }],
  testTime: [{ required: true, message: '请选择检测时间', trigger: 'change' }],
  tester: [{ required: true, message: '请输入检测员', trigger: 'blur' }],
  result: [{ required: true, message: '请选择检测结果', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const resetForm = () => {
  formData.testNo = ''
  formData.batchNo = ''
  formData.sampleNo = ''
  formData.testType = '瘦肉精检测'
  formData.testTime = ''
  formData.tester = ''
  formData.result = '待检测'
  formData.status = '待检测'
  formData.reportUrl = ''
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
      await request.put(`/slaughter/ractopamine/${props.editData.id}`, formData)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/slaughter/ractopamine', formData)
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
