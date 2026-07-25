<template>
  <div class="login-container">
    <div class="login-brand">
      <div class="brand-content">
        <div class="brand-logo">
          <svg class="logo-icon" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">
            <circle cx="60" cy="60" r="56" fill="none" stroke="#ffffff" stroke-width="3" opacity="0.3" />
            <circle cx="60" cy="60" r="42" fill="none" stroke="#ffffff" stroke-width="2" opacity="0.2" />
            <path d="M35 70 Q40 45 60 40 Q80 45 85 70 Q85 80 75 85 L45 85 Q35 80 35 70Z" fill="none" stroke="#ffffff" stroke-width="2.5" />
            <path d="M50 50 Q55 42 60 50 Q65 42 70 50" fill="none" stroke="#ffffff" stroke-width="2" />
            <circle cx="50" cy="62" r="2.5" fill="#ffffff" />
            <circle cx="70" cy="62" r="2.5" fill="#ffffff" />
            <path d="M52 72 Q60 78 68 72" fill="none" stroke="#ffffff" stroke-width="1.5" />
          </svg>
        </div>
        <h1 class="brand-title">猪肉产品质量安全溯源系统</h1>
        <p class="brand-desc">
          基于区块链技术的全产业链质量追溯平台，实现从养殖、屠宰、加工、运输到销售的全程可追溯管理，
          保障猪肉产品质量安全，让消费者放心消费。
        </p>
        <div class="brand-features">
          <div class="feature-item"><el-icon :size="20"><Connection /></el-icon><span>全程可追溯</span></div>
          <div class="feature-item"><el-icon :size="20"><Lock /></el-icon><span>数据安全</span></div>
          <div class="feature-item"><el-icon :size="20"><Monitor /></el-icon><span>实时监控</span></div>
        </div>
      </div>
    </div>
    <div class="login-form-area">
      <div class="form-wrapper">
        <LoginForm />
        <div class="copyright"><span>&copy; {{ currentYear }} 猪肉产品质量安全溯源系统 版权所有</span></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Lock, Monitor, Connection } from '@element-plus/icons-vue'
import LoginForm from './LoginForm.vue'

const currentYear = computed(() => new Date().getFullYear())
</script>

<style lang="scss" scoped>
.login-container { display: flex; width: 100vw; height: 100vh; min-height: 600px; overflow: hidden; background: linear-gradient(135deg, #0a1628 0%, #122a50 40%, #0d3b6e 70%, #0a1628 100%); }
.login-brand { flex: 1; display: flex; align-items: center; justify-content: center; padding: 60px; position: relative; overflow: hidden;
  &::before { content: ''; position: absolute; top: -120px; left: -120px; width: 400px; height: 400px; border-radius: 50%; background: radial-gradient(circle, rgba(64,158,255,0.08) 0%, transparent 70%); }
  &::after { content: ''; position: absolute; bottom: -80px; right: -80px; width: 300px; height: 300px; border-radius: 50%; background: radial-gradient(circle, rgba(64,158,255,0.06) 0%, transparent 70%); }
}
.brand-content { position: relative; z-index: 1; max-width: 520px; color: #fff; }
.brand-logo { margin-bottom: 32px; .logo-icon { width: 100px; height: 100px; filter: drop-shadow(0 4px 12px rgba(64,158,255,0.3)); } }
.brand-title { font-size: 32px; font-weight: 700; letter-spacing: 2px; margin: 0 0 20px 0; line-height: 1.4; background: linear-gradient(90deg, #fff, #a0cfff); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.brand-desc { font-size: 15px; line-height: 1.8; color: rgba(255,255,255,0.65); margin: 0 0 40px 0; }
.brand-features { display: flex; gap: 32px;
  .feature-item { display: flex; align-items: center; gap: 8px; font-size: 14px; color: rgba(255,255,255,0.75); padding: 10px 16px; border-radius: 8px; background: rgba(255,255,255,0.06); backdrop-filter: blur(4px); border: 1px solid rgba(255,255,255,0.08); transition: all 0.3s ease;
    &:hover { background: rgba(255,255,255,0.1); border-color: rgba(64,158,255,0.3); }
    .el-icon { color: #409eff; }
  }
}
.login-form-area { flex: 0 0 520px; display: flex; flex-direction: column; align-items: center; justify-content: center; background: rgba(255,255,255,0.95); box-shadow: -8px 0 32px rgba(0,0,0,0.15); position: relative;
  &::before { content: ''; position: absolute; top: 0; left: 0; width: 3px; height: 100%; background: linear-gradient(180deg, #409eff, #337ecc, #409eff); }
}
.form-wrapper { width: 100%; max-width: 380px; padding: 40px 32px; }
.copyright { margin-top: 48px; text-align: center; font-size: 12px; color: #a0a0a0; span { letter-spacing: 0.5px; } }
@media screen and (max-width: 960px) {
  .login-container { flex-direction: column; }
  .login-brand { flex: none; padding: 40px 24px; min-height: auto; .brand-title { font-size: 24px; } .brand-desc { font-size: 13px; margin-bottom: 24px; } .brand-features { gap: 12px; .feature-item { font-size: 12px; padding: 6px 12px; } } }
  .login-form-area { flex: 1; border-top: 3px solid #409eff; &::before { display: none; } }
  .form-wrapper { padding: 32px 24px; }
}
</style>
