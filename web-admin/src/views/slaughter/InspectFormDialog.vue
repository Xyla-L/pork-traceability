<template>
  <el-dialog
    :model-value="visible"
    :title="editData ? '编辑屠宰检验' : '新增屠宰检验'"
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
            :label="`${pig.earTagNo}${pig.breed ? ' (' + pig.breed + ')' : ''}`"
            :value="pig.earTagNo"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="检验编号" prop="inspectNo">
        <el-input v-model="formData.inspectNo" placeholder="请输入检验编号" />
      </el-form-item>
      <el-form-item label="批次号" prop="batchNo">
        <el-input v-model="formData.batchNo" placeholder="请输入屠宰批次号" />
      </el-form-item>
      <el-form-item label="检验类型" prop="inspectType">
        <el-select v-model="formData.inspectType" placeholder="请选择检验类型" style="width: 100%">
          <el-option label="宰前检验" :value="1" />
          <el-option label="宰后检验" :value="2" />
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
      <el-form-item label="官方兽医" prop="veterinary">
        <el-input v-model="formData.veterinary" placeholder="请输入官方兽医姓名" />
      </el-form-item>
      <el-form-item label="体温(°C)" prop="temperature">
        <el-input-number
          v-model="formData.temperature"
          :min="0"
          :max="50"
          :precision="1"
          placeholder="宰后检验可不填"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="待检验" :value="0" />
          <el-option label="合格" :value="1" />
          <el-option label="不合格" :value="2" />
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
  inspectNo: '',
  batchNo: '',
  inspectType: 1,
  inspectTime: '',
  veterinary: '',
  // 宰后检验不量体温，允许为空；不要用 0 兜底（会存成无效的 0.0）
  temperature: null,
  status: 0,
  conclusion: ''
})

const formRules = {
  earTagNo: [{ required: true, message: '请选择生猪', trigger: 'change' }],
  inspectNo: [{ required: true, message: '请输入检验编号', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  inspectType: [{ required: true, message: '请选择检验类型', trigger: 'change' }],
  inspectTime: [{ required: true, message: '请选择检验时间', trigger: 'change' }],
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
    // 条件2：排除已有屠宰检验记录的生猪，避免重复检验
    let excludeIds = []
    try {
      const inspRes = await request.get('/slaughter/inspections', {
        params: { pageNum: 1, pageSize: 500 }
      })
      const inspList = inspRes?.records || inspRes?.list || []
      excludeIds = inspList.map((e) => e.pigId).filter(Boolean)
    } catch (e) {
      console.error('获取已有屠宰检验记录失败:', e)
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
  if (pig) {
    formData.pigId = pig.id
    // 根据耳标号带出入场查验时的批次号
    try {
      const res = await request.get('/slaughter/entries', {
        params: { pigId: pig.id, pageNum: 1, pageSize: 1 }
      })
      const list = res?.records || res?.list || []
      if (list.length > 0 && list[0].batchNo) {
        formData.batchNo = list[0].batchNo
      } else {
        formData.batchNo = ''
        ElMessage.info('该生猪暂无入场查验记录，请手动填写批次号')
      }
    } catch (error) {
      console.error('获取入场批次号失败:', error)
    }
  }
}

const resetForm = () => {
  formData.pigId = null
  formData.earTagNo = ''
  formData.inspectNo = ''
  formData.batchNo = ''
  formData.inspectType = 1
  formData.inspectTime = ''
  formData.veterinary = ''
  formData.temperature = null
  formData.status = 0
  formData.conclusion = ''
  pigOptions.value = []
}

watch(() => props.visible, (val) => {
  if (val) {
    if (props.editData) {
      formData.pigId = props.editData.pigId ?? null
      formData.earTagNo = props.editData.earTagNo || ''
      formData.inspectNo = props.editData.inspectNo || ''
      formData.batchNo = props.editData.batchNo || ''
      formData.inspectType = props.editData.inspectType ?? 1
      formData.inspectTime = props.editData.inspectTime || ''
      formData.veterinary = props.editData.veterinary || ''
      formData.temperature = props.editData.temperature ?? null
      formData.status = props.editData.status ?? 0
      formData.conclusion = props.editData.conclusion || ''
      if (formData.earTagNo) {
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
      await request.put(`/slaughter/inspections/${props.editData.id}`, payload)
      ElMessage.success('编辑成功')
    } else {
      await request.post('/slaughter/inspections', payload)
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
