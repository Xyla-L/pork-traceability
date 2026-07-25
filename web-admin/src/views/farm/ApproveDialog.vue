<template>
  <el-dialog
    :model-value="visible"
    title="出栏申报审批"
    width="560px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <!-- 申报详情 -->
    <div class="apply-detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申报编号">{{ applyData?.applyNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生猪耳标号">{{ applyData?.earTagNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申报时间">{{ applyData?.applyTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="体重">{{ applyData?.weightKg ? `${applyData.weightKg} kg` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标屠宰场" :span="2">
          {{ applyData?.targetSlaughterhouse || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="当前状态" :span="2">
          <el-tag :type="statusTagType(applyData?.approvalStatus)">
            {{ statusLabel(applyData?.approvalStatus) }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 审批操作区 -->
    <div class="approve-action">
      <el-divider content-position="left">审批操作</el-divider>
      <el-form
        ref="formRef"
        :model="approveForm"
        :rules="rules"
        label-width="80px"
      >
        <el-form-item label="审批结果">
          <el-radio-group v-model="approveForm.approvalStatus">
            <el-radio :value="1">
              <span style="color: #67c23a">通过</span>
            </el-radio>
            <el-radio :value="2">
              <span style="color: #f56c6c">驳回</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="approveForm.remark"
            type="textarea"
            placeholder="驳回时备注为必填"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="danger" :disabled="approveForm.approvalStatus !== 2" @click="handleReject">
        驳 回
      </el-button>
      <el-button type="success" :disabled="approveForm.approvalStatus !== 1" @click="handleApprove">
        通 过
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  applyData: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:visible', 'submit'])

const formRef = ref(null)

const approveForm = reactive({
  approvalStatus: 1,
  remark: ''
})

const rules = {
  remark: [
    {
      required: true,
      message: '驳回时备注为必填',
      trigger: 'blur',
      validator: (rule, value, callback) => {
        if (approveForm.approvalStatus === 2 && !value?.trim()) {
          callback(new Error('驳回时请填写备注'))
        } else {
          callback()
        }
      }
    }
  ]
}

const statusLabel = (status) => {
  const map = {
    0: '待审批',
    1: '已通过',
    2: '已驳回'
  }
  return map[status] || '未知'
}

const statusTagType = (status) => {
  const map = {
    0: 'warning',
    1: 'success',
    2: 'danger'
  }
  return map[status] || 'info'
}

const resetForm = () => {
  approveForm.approvalStatus = 1
  approveForm.remark = ''
  formRef.value?.resetFields()
}

const handleClose = () => {
  resetForm()
  emit('update:visible', false)
}

const handleApprove = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  emit('submit', {
    id: props.applyData.id,
    approvalStatus: 1,
    remark: approveForm.remark
  })
  ElMessage.success('审批通过')
  handleClose()
}

const handleReject = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  emit('submit', {
    id: props.applyData.id,
    approvalStatus: 2,
    remark: approveForm.remark
  })
  ElMessage.success('已驳回')
  handleClose()
}

// 监听弹窗打开时重置
watch(
  () => props.visible,
  (val) => {
    if (val) {
      resetForm()
    }
  }
)
</script>

<style lang="scss" scoped>
.apply-detail {
  margin-bottom: 10px;
}

.approve-action {
  :deep(.el-divider) {
    margin: 16px 0;
  }

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}
</style>
