<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>猪肉产品质量安全溯源系统</h1>
        <p>Traceability Management Platform</p>
      </div>
      <!-- Person B 将实现 LoginForm 组件 -->
      <div class="login-placeholder">
        <el-icon :size="48"><UserFilled /></el-icon>
        <p>登录页面 — 待 Person B 实现</p>
        <p class="hint">包含：LoginForm（账号密码+验证码+记住密码）</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  padding: 40px;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;

  h1 {
    font-size: 22px;
    color: #303133;
    margin-bottom: 8px;
  }

  p {
    font-size: 13px;
    color: #909399;
  }
}

.login-placeholder {
  text-align: center;
  padding: 40px 0;
  color: #c0c4cc;

  p {
    margin-top: 12px;
  }

  .hint {
    font-size: 12px;
    color: #e6a23c;
  }
}
</style>
