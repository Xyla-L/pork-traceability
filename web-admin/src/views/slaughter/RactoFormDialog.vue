<template>
  <el-dialog
    :model-value="visible"
    :title="editData ? '编辑瘦肉精检测' : '新增瘦肉精检测'"
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
          :disabled="!!editData"
          style="width: 100%"
          @change="handlePigSelect"
        >
          <el-option
            v-for="pig in pigOptions"
            :key="pig.id"
            :label="pig.earTagNo"
            :value="pig.earTagNo"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="检测编号" prop="testNo">
        <el-input v-model="formData.testNo" placeholder="请输入检测编号" />
      </el-form-item>
      <el-form-item label="批次号" prop="batchNo">
        <el-input v-model="formData.batchNo" placeholder="请输入屠宰批次号" />
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
      <el-form-item label="检测方法" prop="testMethod">
        <el-input v-model="formData.testMethod" placeholder="如：胶体金法" />
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
      <el-form-item label="检测员" prop="operator">
        <el-input v-model="formData.operator" placeholder="请输入检测员姓名" />
      </el-form-item>
      <el-form-item label="检测结果" prop="result">
        <el-select v-model="formData.result" placeholder="请选择检测结果" style="width: 100%">
          <el-option label="阴性" :value="1" />
          <el-option label="阳性" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="待检测" :value="0" />
          <el-option label="检测中" :value="1" />
          <el-option label="已完成" :value="2" />
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
import { ref, reactive, watch, nextTick } from 'vue'
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
const pigSearchLoading = ref(false)
const pigOptions = ref([])

const formData = reactive({
  pigId: null,
  earTagNo: '',
  testNo: '',
  batchNo: '',
  sampleNo: '',
  testType: '瘦肉精检测',
  testMethod: '',
  testTime: '',
  operator: '',
  result: 1,
  status: 0,
  reportUrl: '',
  remark: ''
})

const formRules = {
  earTagNo: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  testNo: [
    { required: true, message: '请输入检测编号', trigger: 'blur' },
    {
      validator: async (rule, value, callback) => {
        if (!value || props.editData) return callback()
        try {
          const res = await request.get('/slaughter/ractopamine', { params: { testNo: value, pageSize: 1 } })
          const list = res?.records || res?.list || []
          if (list.length > 0) return callback(new Error('检测编号已存在'))
          callback()
        } catch (e) { callback() }
      },
      trigger: 'blur'
    }
  ],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  sampleNo: [
    { required: true, message: '请输入样本编号', trigger: 'blur' },
    {
      validator: async (rule, value, callback) => {
        if (!value || props.editData) return callback()
        try {
          const res = await request.get('/slaughter/ractopamine', { params: { sampleNo: value, pageSize: 1 } })
          const list = res?.records || res?.list || []
          if (list.length > 0) return callback(new Error('样本编号已存在'))
          callback()
        } catch (e) { callback() }
      },
      trigger: 'blur'
    }
  ],
  testType: [{ required: true, message: '请选择检测项目', trigger: 'change' }],
  testMethod: [{ required: true, message: '请输入检测方法', trigger: 'blur' }],
  testTime: [{ required: true, message: '请选择检测时间', trigger: 'change' }],
  operator: [{ required: true, message: '请输入检测员', trigger: 'blur' }],
  result: [{ required: true, message: '请选择检测结果', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const searchPigs = async (query) => {
  if (!query) {
    pigOptions.value = []
    return
  }
  pigSearchLoading.value = true
  try {
    // 只搜已出栏(status=2)的生猪（出栏审批通过、尚未进入屠宰环节）；
    // 瘦肉精检测无作废/重录场景，已屠宰(3)的猪不再可选，
    // 同一头猪不同检测项目可多次检测，防重由检测编号/样本编号唯一性校验兜底
    const res = await request.get('/breeding/pigs', {
      params: { earTagNo: query, status: 2, size: 20 }
    })
    pigOptions.value = res?.records || res?.list || []
  } catch (error) {
    console.error('搜索生猪失败:', error)
  } finally {
    pigSearchLoading.value = false
  }
}

const handlePigSelect = async (earTagNo) => {
  const pig = pigOptions.value.find((p) => p.earTagNo === earTagNo)
  if (!pig) return
  formData.pigId = pig.id
  // 自动带入批次号：查询该生猪的入场查验记录
  try {
    const res = await request.get('/slaughter/entries', {
      params: { pigId: pig.id, pageNum: 1, pageSize: 1 }
    })
    const records = res?.records || res?.list || []
    if (records.length > 0 && records[0].batchNo) {
      formData.batchNo = records[0].batchNo
    }
  } catch (e) {
    console.error('获取批次号失败:', e)
  }
}

// 编辑时按 pigId 反查生猪耳标号，用于回填下拉框显示
const fetchPigByEdit = async (pigId) => {
  try {
    const pig = await request.get(`/breeding/pigs/${pigId}`)
    if (pig) {
      formData.earTagNo = pig.earTagNo || ''
      pigOptions.value = [{ id: pig.id, earTagNo: pig.earTagNo, breed: pig.breed }]
    }
  } catch (error) {
    console.error('反查生猪耳标号失败:', error)
  }
}

const resetForm = () => {
  formData.pigId = null
  formData.earTagNo = ''
  formData.testNo = ''
  formData.batchNo = ''
  formData.sampleNo = ''
  formData.testType = '瘦肉精检测'
  formData.testMethod = ''
  formData.testTime = ''
  formData.operator = ''
  formData.result = 1
  formData.status = 0
  formData.reportUrl = ''
  formData.remark = ''
  pigOptions.value = []
}

watch(() => props.visible, (val) => {
  if (val) {
    if (props.editData) {
      formData.pigId = props.editData.pigId ?? null
      formData.earTagNo = props.editData.earTagNo || ''
      formData.testNo = props.editData.testNo || ''
      formData.batchNo = props.editData.batchNo || ''
      formData.sampleNo = props.editData.sampleNo || ''
      formData.testType = props.editData.testType || '瘦肉精检测'
      formData.testMethod = props.editData.testMethod || ''
      formData.testTime = props.editData.testTime || ''
      formData.operator = props.editData.operator || ''
      formData.result = props.editData.result ?? 1
      formData.status = props.editData.status ?? 0
      formData.reportUrl = props.editData.reportUrl || ''
      formData.remark = props.editData.remark || ''
      // 后端列表不返回耳标号，编辑时按 pigId 反查补全耳标号
      if (formData.pigId) {
        fetchPigByEdit(formData.pigId)
      } else if (formData.earTagNo) {
        pigOptions.value = [{ id: formData.pigId, earTagNo: formData.earTagNo, breed: '' }]
      }
    } else {
      resetForm()
    }
    // 等待表单挂载及字段赋值引发的异步校验落定后，清除残留红字
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
      await request.put(`/slaughter/ractopamine/${props.editData.id}`, payload)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/slaughter/ractopamine', payload)
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
