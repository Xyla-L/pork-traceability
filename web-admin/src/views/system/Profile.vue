<template>
  <div class="page-container profile-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>👤 个人信息</span>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="profile-form">
        <el-form-item label="用户名">
          <el-input :model-value="user?.username || '--'" disabled />
        </el-form-item>

        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>

        <el-form-item label="所属机构">
          <el-input :model-value="user?.orgName || '--'" disabled />
        </el-form-item>

        <el-form-item label="角色">
          <el-input :model-value="roleLabel" disabled />
        </el-form-item>

        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>

        <el-form-item label="最近登录">
          <el-input :model-value="user?.lastLoginTime || '--'" disabled />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const user = computed(() => authStore.user)

const formRef = ref()
const saving = ref(false)

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    FARMER: '养殖场操作员',
    SLAUGHTER_OP: '屠宰场操作员',
    DISTRIBUTOR: '配送商操作员',
    RETAILER: '零售商操作员',
    SUPERVISOR: '监管人员',
    ADMIN: '系统管理员',
  }
  return map[user.value?.role || ''] || user.value?.role || '--'
})

const form = reactive({
  realName: user.value?.realName || '',
  phone: user.value?.phone || '',
  email: user.value?.email || '',
})

const rules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }],
}

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    await authStore.updateProfile({ ...form })
    ElMessage.success('个人信息已更新')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  .card-header {
    font-size: 16px;
    font-weight: 600;
  }

  .profile-form {
    max-width: 520px;

    :deep(.el-input.is-disabled .el-input__inner) {
      color: #909399;
    }
  }
}
</style>
