<template>
  <el-dialog
    :model-value="visible"
    :title="editData ? '编辑盖章记录' : '新增盖章记录'"
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
      <el-form-item label="盖章编号" prop="stampNo">
        <el-input v-model="formData.stampNo" placeholder="请输入盖章编号" />
      </el-form-item>
      <el-form-item label="批次号" prop="batchNo">
        <el-input v-model="formData.batchNo" placeholder="请输入批次号" />
      </el-form-item>
      <el-form-item label="胴体编号" prop="carcassNo">
        <el-input v-model="formData.carcassNo" placeholder="请输入胴体编号" />
      </el-form-item>
      <el-form-item label="印章类型" prop="stampType">
        <el-select v-model="formData.stampType" placeholder="请选择印章类型" style="width: 100%">
          <el-option label="检疫合格章" value="检疫合格章" />
          <el-option label="检验合格章" value="检验合格章" />
          <el-option label="无害化处理章" value="无害化处理章" />
          <el-option label="高温处理章" value="高温处理章" />
        </el-select>
      </el-form-item>
      <el-form-item label="盖章时间" prop="stampTime">
        <el-date-picker
          v-model="formData.stampTime"
          type="datetime"
          placeholder="选择盖章时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="检疫员" prop="inspector">
        <el-input v-model="formData.inspector" placeholder="请输入检疫员姓名" />
      </el-form-item>
      <el-form-item label="区块链上链" prop="isVerified">
        <el-switch v-model="formData.isVerified" />
      </el-form-item>
      <el-form-item label="交易哈希" prop="blockchainTxHash">
        <el-input v-model="formData.blockchainTxHash" placeholder="区块链交易哈希" :disabled="!formData.isVerified" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="待盖章" value="待盖章" />
          <el-option label="已盖章" value="已盖章" />
          <el-option label="已作废" value="已作废" />
        </el-select>
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
  stampNo: '',
  batchNo: '',
  carcassNo: '',
  stampType: '检疫合格章',
  stampTime: '',
  inspector: '',
  isVerified: false,
  blockchainTxHash: '',
  status: '待盖章',
  remark: ''
})

const formRules = {
  stampNo: [{ required: true, message: '请输入盖章编号', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  carcassNo: [{ required: true, message: '请输入胴体编号', trigger: 'blur' }],
  stampType: [{ required: true, message: '请选择印章类型', trigger: 'change' }],
  stampTime: [{ required: true, message: '请选择盖章时间', trigger: 'change' }],
  inspector: [{ required: true, message: '请输入检疫员', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const resetForm = () => {
  formData.stampNo = ''
  formData.batchNo = ''
  formData.carcassNo = ''
  formData.stampType = '检疫合格章'
  formData.stampTime = ''
  formData.inspector = ''
  formData.isVerified = false
  formData.blockchainTxHash = ''
  formData.status = '待盖章'
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
      await request.put(`/slaughter/stamps/${props.editData.id}`, formData)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/slaughter/stamps', formData)
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
