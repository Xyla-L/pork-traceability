<template>
  <el-dialog
    :model-value="visible"
    :title="editData ? '编辑检疫盖章' : '新增检疫盖章'"
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
      <el-form-item label="盖章编号" prop="stampNo">
        <el-input v-model="formData.stampNo" placeholder="请输入盖章编号" />
      </el-form-item>
      <el-form-item label="批次号" prop="batchNo">
        <el-input v-model="formData.batchNo" placeholder="请输入屠宰批次号" />
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
      <el-form-item label="官方兽医" prop="veterinary">
        <el-input v-model="formData.veterinary" placeholder="请输入官方兽医姓名" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="待盖章" :value="0" />
          <el-option label="已盖章" :value="1" />
          <el-option label="已作废" :value="2" />
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
  stampNo: '',
  batchNo: '',
  carcassNo: '',
  stampType: '检疫合格章',
  stampTime: '',
  veterinary: '',
  status: 0,
  remark: ''
})

const formRules = {
  earTagNo: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  stampNo: [{ required: true, message: '请输入盖章编号', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  carcassNo: [{ required: true, message: '请输入胴体编号', trigger: 'blur' }],
  stampType: [{ required: true, message: '请选择印章类型', trigger: 'change' }],
  stampTime: [{ required: true, message: '请选择盖章时间', trigger: 'change' }],
  veterinary: [{ required: true, message: '请输入官方兽医', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const searchPigs = async (query) => {
  if (!query) {
    pigOptions.value = []
    return
  }
  pigSearchLoading.value = true
  try {
    // 条件1：只搜已出栏(status=2)的生猪，即出栏申报审批通过的猪
    const res = await request.get('/breeding/pigs', {
      params: { earTagNo: query, status: 2, size: 20 }
    })
    const list = res?.records || res?.list || []
    // 条件2：排除已有胴体盖章记录的生猪，避免重复盖章
    let excludeIds = []
    try {
      const stampRes = await request.get('/slaughter/stamps', {
        params: { pageNum: 1, pageSize: 500 }
      })
      const stampList = stampRes?.records || stampRes?.list || []
      excludeIds = stampList.map((e) => e.pigId).filter(Boolean)
    } catch (e) {
      console.error('获取已有胴体盖章记录失败:', e)
    }
    pigOptions.value = list.filter((p) => !excludeIds.includes(p.id))
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
  formData.stampNo = ''
  formData.batchNo = ''
  formData.carcassNo = ''
  formData.stampType = '检疫合格章'
  formData.stampTime = ''
  formData.veterinary = ''
  formData.status = 0
  formData.remark = ''
  pigOptions.value = []
}

watch(() => props.visible, (val) => {
  if (val) {
    if (props.editData) {
      formData.pigId = props.editData.pigId ?? null
      formData.earTagNo = props.editData.earTagNo || ''
      formData.stampNo = props.editData.stampNo || ''
      formData.batchNo = props.editData.batchNo || ''
      formData.carcassNo = props.editData.carcassNo || ''
      formData.stampType = props.editData.stampType || '检疫合格章'
      formData.stampTime = props.editData.stampTime || ''
      formData.veterinary = props.editData.veterinary || ''
      formData.status = props.editData.status ?? 0
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
      await request.put(`/slaughter/stamps/${props.editData.id}`, payload)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/slaughter/stamps', payload)
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
