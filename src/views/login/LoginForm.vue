<template>
  <div class="login-form">
    <!-- 登录表单头部 -->
    <div class="form-header">
      <h2 class="form-title">用户登录</h2>
      <p class="form-subtitle">请输入您的账号信息</p>
    </div>

    <!-- 登录表单 -->
    <el-form
      ref="loginFormRef"
      :model="loginForm"
      :rules="loginRules"
      class="login-form-content"
      size="large"
      @keyup.enter="handleLogin"
    >
      <!-- 用户名 -->
      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          placeholder="请输入用户名"
          clearable
          :prefix-icon="User"
        />
      </el-form-item>

      <!-- 密码 -->
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          placeholder="请输入密码"
          show-password
          clearable
          :prefix-icon="Lock"
        />
      </el-form-item>

      <!-- 验证码 -->
      <el-form-item prop="captcha">
        <div class="captcha-row">
          <el-input
            v-model="loginForm.captcha"
            placeholder="请输入验证码"
            clearable
            :prefix-icon="Key"
            class="captcha-input"
          />
          <div class="captcha-image" title="点击刷新验证码" @click="refreshCaptcha">
            <canvas ref="captchaCanvas" width="120" height="40" />
            <div v-if="captchaLoading" class="captcha-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
            </div>
          </div>
        </div>
      </el-form-item>

      <!-- 记住密码 -->
      <el-form-item>
        <div class="form-options">
          <el-checkbox v-model="loginForm.remember">记住密码</el-checkbox>
        </div>
      </el-form-item>

      <!-- 登录按钮 -->
      <el-form-item>
        <el-button
          type="primary"
          class="login-btn"
          :loading="loginLoading"
          @click="handleLogin"
        >
          {{ loginLoading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, Loading } from '@element-plus/icons-vue'
// 假设项目中有 authApi 模块，实际路径根据项目结构调整
// import { authApi } from '@/api/auth'

const router = useRouter()

// ==================== 表单引用 ====================
const loginFormRef = ref(null)
const captchaCanvas = ref(null)

// ==================== 表单数据 ====================
const loginForm = reactive({
  username: '',
  password: '',
  captcha: '',
  remember: false,
})

// ==================== 表单校验规则 ====================
const loginRules = reactive({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度为 2-20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 30, message: '密码长度为 6-30 个字符', trigger: 'blur' },
  ],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 4, message: '验证码为 4 位字符', trigger: 'blur' },
  ],
})

// ==================== 状态变量 ====================
const loginLoading = ref(false)
const captchaLoading = ref(false)
const captchaKey = ref('') // 验证码唯一标识，用于后端校验

// ==================== 验证码相关 ====================

/**
 * 生成随机验证码字符
 */
function generateCaptchaText(length = 4) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  let result = ''
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

/**
 * 在 Canvas 上绘制验证码图片
 */
function drawCaptcha() {
  const canvas = captchaCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  const width = canvas.width
  const height = canvas.height
  const text = generateCaptchaText(4)

  // 保存当前验证码文本，用于前端模拟校验
  // 实际项目中应使用后端返回的 key 进行校验
  captchaKey.value = text

  // 背景
  ctx.fillStyle = '#e8edf3'
  ctx.fillRect(0, 0, width, height)

  // 干扰线
  for (let i = 0; i < 4; i++) {
    ctx.strokeStyle = randomColor(150, 200)
    ctx.beginPath()
    ctx.moveTo(Math.random() * width, Math.random() * height)
    ctx.lineTo(Math.random() * width, Math.random() * height)
    ctx.stroke()
  }

  // 干扰点
  for (let i = 0; i < 30; i++) {
    ctx.fillStyle = randomColor(100, 200)
    ctx.beginPath()
    ctx.arc(Math.random() * width, Math.random() * height, 1, 0, 2 * Math.PI)
    ctx.fill()
  }

  // 绘制验证码文字
  const fontSize = 22
  ctx.font = `bold ${fontSize}px "Courier New", monospace`
  ctx.textBaseline = 'middle'

  for (let i = 0; i < text.length; i++) {
    ctx.fillStyle = randomColor(50, 120)
    ctx.save()
    const x = 12 + i * 26
    const y = height / 2 + (Math.random() * 8 - 4)
    const angle = (Math.random() - 0.5) * 0.4
    ctx.translate(x, y)
    ctx.rotate(angle)
    ctx.fillText(text[i], 0, 0)
    ctx.restore()
  }
}

/**
 * 生成随机颜色
 */
function randomColor(min, max) {
  const r = Math.floor(Math.random() * (max - min) + min)
  const g = Math.floor(Math.random() * (max - min) + min)
  const b = Math.floor(Math.random() * (max - min) + min)
  return `rgb(${r}, ${g}, ${b})`
}

/**
 * 刷新验证码
 * 实际项目中应调用后端接口获取新的验证码图片和 key
 */
function refreshCaptcha() {
  captchaLoading.value = true
  setTimeout(() => {
    drawCaptcha()
    captchaLoading.value = false
  }, 300)
}

// ==================== 登录逻辑 ====================

/**
 * 从本地存储恢复记住的用户名和密码
 */
function loadSavedCredentials() {
  const saved = localStorage.getItem('login_credentials')
  if (saved) {
    try {
      const { username, password } = JSON.parse(saved)
      loginForm.username = username || ''
      loginForm.password = password || ''
      loginForm.remember = true
    } catch {
      // 忽略解析错误
    }
  }
}

/**
 * 保存或清除记住的凭据
 */
function handleRememberPassword() {
  if (loginForm.remember) {
    localStorage.setItem(
      'login_credentials',
      JSON.stringify({
        username: loginForm.username,
        password: loginForm.password,
      })
    )
  } else {
    localStorage.removeItem('login_credentials')
  }
}

/**
 * 处理登录
 */
async function handleLogin() {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
  } catch {
    return
  }

  // 前端验证码校验（仅演示用，实际项目应交由后端校验）
  if (loginForm.captcha.toLowerCase() !== captchaKey.value.toLowerCase()) {
    ElMessage.error('验证码错误，请重新输入')
    refreshCaptcha()
    loginForm.captcha = ''
    return
  }

  loginLoading.value = true

  try {
    // ====== 调用后端登录接口 ======
    // 实际项目中取消注释并使用真实接口
    /*
    const res = await authApi.login({
      username: loginForm.username,
      password: loginForm.password,
      captcha: loginForm.captcha,
      captchaKey: captchaKey.value,
    })
    */

    // 模拟登录请求延迟
    await new Promise((resolve) => setTimeout(resolve, 1500))

    // 登录成功 - 处理记住密码
    handleRememberPassword()

    ElMessage.success('登录成功，正在跳转...')

    // 延迟跳转，让用户看到成功提示
    setTimeout(() => {
      router.push('/admin')
    }, 800)
  } catch (error) {
    const message = error?.response?.data?.message || error?.message || '登录失败，请稍后重试'
    ElMessage.error(message)
    // 登录失败时刷新验证码
    refreshCaptcha()
    loginForm.captcha = ''
  } finally {
    loginLoading.value = false
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadSavedCredentials()
  nextTick(() => {
    refreshCaptcha()
  })
})
</script>

<style lang="scss" scoped>
.login-form {
  width: 100%;
}

// ========== 表单头部 ==========
.form-header {
  margin-bottom: 36px;
}

.form-title {
  font-size: 26px;
  font-weight: 700;
  color: #1d2129;
  margin: 0 0 8px 0;
  letter-spacing: 1px;
}

.form-subtitle {
  font-size: 14px;
  color: #86909c;
  margin: 0;
}

// ========== 表单内容 ==========
.login-form-content {
  :deep(.el-input__wrapper) {
    border-radius: 8px;
    padding: 4px 12px;
    box-shadow: 0 0 0 1px #d9d9d9 inset;
    transition: all 0.3s ease;

    &:hover {
      box-shadow: 0 0 0 1px #409eff inset;
    }

    &.is-focus {
      box-shadow: 0 0 0 1px #409eff inset, 0 0 0 3px rgba(64, 158, 255, 0.1);
    }
  }

  :deep(.el-input__prefix .el-icon) {
    color: #b0b5bd;
    font-size: 16px;
  }
}

// ========== 验证码行 ==========
.captcha-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.captcha-input {
  flex: 1;
}

.captcha-image {
  flex-shrink: 0;
  width: 120px;
  height: 40px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  border: 1px solid #e5e6eb;
  transition: all 0.3s ease;

  &:hover {
    border-color: #409eff;
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);

    canvas {
      opacity: 0.7;
    }
  }

  &:active {
    transform: scale(0.97);
  }

  canvas {
    display: block;
    width: 100%;
    height: 100%;
    transition: opacity 0.3s ease;
  }
}

.captcha-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(232, 237, 243, 0.85);

  .el-icon {
    font-size: 20px;
    color: #409eff;
  }
}

// ========== 表单选项 ==========
.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;

  :deep(.el-checkbox__label) {
    font-size: 13px;
    color: #646a73;
  }

  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    background-color: #409eff;
    border-color: #409eff;
  }
}

// ========== 登录按钮 ==========
.login-btn {
  width: 100%;
  height: 44px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border: none;
  transition: all 0.3s ease;

  &:hover {
    background: linear-gradient(135deg, #66b1ff 0%, #409eff 100%);
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.35);
  }

  &:active {
    transform: translateY(0);
    box-shadow: 0 2px 6px rgba(64, 158, 255, 0.25);
  }
}
</style>
