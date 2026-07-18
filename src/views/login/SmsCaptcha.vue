<template>
  <div class="sms-captcha">
    <el-input
      v-model="code"
      placeholder="请输入短信验证码"
      clearable
      :prefix-icon="ChatDotRound"
      class="sms-input"
    >
      <template #append>
        <el-button
          :disabled="countdown > 0 || !phone"
          :loading="sending"
          class="send-btn"
          @click="handleSend"
        >
          {{ buttonText }}
        </el-button>
      </template>
    </el-input>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'

// ==================== Props & Emits ====================
const props = defineProps({
  /** 手机号码 */
  phone: {
    type: String,
    default: '',
  },
  /** 倒计时时长（秒），默认 60 */
  cooldown: {
    type: Number,
    default: 60,
  },
})

const emit = defineEmits(['send'])

// ==================== 响应式数据 ====================
const code = ref('')
const countdown = ref(0)
const sending = ref(false)
let timer = null

// ==================== 计算属性 ====================
const buttonText = computed(() => {
  if (sending.value) return '发送中...'
  if (countdown.value > 0) return `${countdown.value}s 后重发`
  return '获取验证码'
})

// ==================== 手机号格式校验 ====================
function isValidPhone(phone) {
  return /^1[3-9]\d{9}$/.test(phone)
}

// ==================== 发送验证码 ====================
async function handleSend() {
  if (!props.phone) {
    ElMessage.warning('请先输入手机号码')
    return
  }

  if (!isValidPhone(props.phone)) {
    ElMessage.warning('请输入正确的手机号码')
    return
  }

  sending.value = true

  try {
    // 触发发送事件，由父组件处理实际发送逻辑
    // 父组件应通过 Promise 返回发送结果
    await emit('send', props.phone)

    // 发送成功，开始倒计时
    startCountdown()
    ElMessage.success('验证码已发送，请注意查收')
  } catch (error) {
    const message = error?.response?.data?.message || error?.message || '发送失败，请稍后重试'
    ElMessage.error(message)
  } finally {
    sending.value = false
  }
}

// ==================== 倒计时逻辑 ====================
function startCountdown() {
  countdown.value = props.cooldown
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
      countdown.value = 0
    }
  }, 1000)
}

/**
 * 对外暴露获取验证码值的方法
 */
function getCode() {
  return code.value
}

/**
 * 对外暴露重置方法
 */
function reset() {
  code.value = ''
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  countdown.value = 0
}

// 监听手机号变化时重置倒计时
watch(
  () => props.phone,
  (newVal, oldVal) => {
    if (newVal !== oldVal) {
      if (timer) {
        clearInterval(timer)
        timer = null
      }
      countdown.value = 0
    }
  }
)

// 组件卸载时清除定时器
onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

// 暴露方法给父组件
defineExpose({
  getCode,
  reset,
})
</script>

<style lang="scss" scoped>
.sms-captcha {
  width: 100%;

  .sms-input {
    :deep(.el-input-group__append) {
      padding: 0;
      border: none;
      background: transparent;
    }

    :deep(.el-input__wrapper) {
      border-radius: 8px 0 0 8px;
    }
  }

  .send-btn {
    width: 120px;
    height: 100%;
    border-radius: 0 8px 8px 0;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    color: #409eff;
    background: #ecf5ff;
    border: 1px solid #d9ecff;
    border-left: none;
    transition: all 0.3s ease;

    &:hover:not(:disabled) {
      background: #d9ecff;
      border-color: #b3d8ff;
    }

    &:active:not(:disabled) {
      background: #c6e2ff;
    }

    &:disabled {
      color: #a0cfff;
      background: #f5f7fa;
      border-color: #e4e7ed;
      cursor: not-allowed;
    }
  }
}
</style>
