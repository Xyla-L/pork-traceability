<template>
  <el-dialog
    :model-value="visible"
    title="批量审批"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <!-- 本批概况：审的是哪一批、还有几头待审 -->
    <el-descriptions :column="2" border>
      <el-descriptions-item label="批次">{{ group?.batchNo || '未归批' }}</el-descriptions-item>
      <el-descriptions-item label="申报编号">{{ group?.title || '-' }}</el-descriptions-item>
      <el-descriptions-item label="本批头数">{{ group?.headCount || 0 }} 头</el-descriptions-item>
      <el-descriptions-item label="待审批">{{ pending.length }} 头</el-descriptions-item>
    </el-descriptions>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" class="batch-form">
      <el-form-item label="审批人">
        <span v-if="approverName" class="approver-name">{{ approverName }}</span>
        <span v-else class="approver-missing">当前登录账号未登记真实姓名</span>
        <el-tooltip
          v-if="!approverName"
          content="审批是实名责任行为。请先到「个人中心」补全真实姓名，否则审批会被系统拒绝"
          placement="top"
        >
          <span class="approver-hint">需先补全实名</span>
        </el-tooltip>
      </el-form-item>

      <el-form-item label="审批结果">
        <el-radio-group v-model="form.approvalStatus">
          <el-radio :value="1">
            <span style="color: #67c23a">通过</span>
          </el-radio>
          <el-radio :value="2">
            <span style="color: #f56c6c">驳回</span>
          </el-radio>
        </el-radio-group>
        <div v-if="form.approvalStatus === 1" class="batch-hint">
          本批 {{ pending.length }} 头逐条提交「审批通过」，审批人取当前登录账号，逐条留痕；某条失败不影响其他条。
        </div>
        <div v-else class="batch-hint batch-hint--warn">
          本批 {{ pending.length }} 头将<strong>全部驳回</strong>，驳回后不可撤销。只有个别猪不合格时，请回列表用逐头审批。
        </div>
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :placeholder="form.approvalStatus === 2 ? '驳回时请填写理由（整改依据）' : '可填写统一说明'"
          :rows="3"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取 消</el-button>
      <el-button
        :type="form.approvalStatus === 2 ? 'danger' : 'success'"
        :disabled="!approverName || pending.length === 0"
        @click="handleSubmit"
      >
        {{ form.approvalStatus === 2 ? `驳回 ${pending.length} 头` : `通过 ${pending.length} 头` }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  group: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:visible', 'submit'])

const authStore = useAuthStore()
const formRef = ref(null)

const approverName = computed(() => (authStore.user?.realName || '').trim())

/** 只有待审的才在本次批量范围内；已审过的条目不重复提交 */
const pending = computed(() => (props.group?.members || []).filter((m) => m.approvalStatus === 0))

const form = reactive({
  approvalStatus: 1,
  remark: ''
})

const rules = {
  remark: [
    {
      required: true,
      message: '驳回时必须填写理由',
      trigger: 'blur',
      validator: (rule, value, callback) => {
        if (form.approvalStatus === 2 && !String(value || '').trim()) {
          callback(new Error('驳回时必须填写理由'))
        } else {
          callback()
        }
      }
    }
  ]
}

const resetForm = () => {
  form.approvalStatus = 1
  form.remark = ''
  formRef.value?.clearValidate()
}

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
  emit('submit', {
    approvalStatus: form.approvalStatus,
    remark: form.remark.trim()
  })
}

watch(
  () => props.visible,
  (val) => {
    if (val) resetForm()
  }
)
</script>

<style lang="scss" scoped>
.batch-form {
  margin-top: 16px;

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  .approver-name {
    font-weight: 500;
    color: #303133;
  }

  .approver-missing {
    color: #e6a23c;
  }

  .approver-hint {
    margin-left: 8px;
    font-size: 12px;
    color: #909399;
    cursor: help;
  }

  .batch-hint {
    width: 100%;
    margin-top: 6px;
    padding: 8px 12px;
    font-size: 12px;
    line-height: 1.6;
    color: #606266;
    background-color: #f4f4f5;
    border-radius: 4px;

    &--warn {
      color: #8c5a00;
      background-color: #fdf6ec;
    }
  }
}
</style>
