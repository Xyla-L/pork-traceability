<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">用户管理</h2>
      <el-button type="primary" @click="showAddDialog = true">新增用户</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form-item label="用户名">
        <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="searchForm.role" placeholder="请选择角色" clearable>
          <el-option label="管理员" :value="1" />
          <el-option label="饲养员" :value="2" />
          <el-option label="屠宰员" :value="3" />
          <el-option label="质检员" :value="4" />
          <el-option label="经销商" :value="5" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
    </SearchPanel>

    <el-table :data="userList" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="realName" label="真实姓名" width="120" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="email" label="邮箱" width="180" />
      <el-table-column label="角色" width="100">
        <template #default="scope">
          <el-tag size="small">{{ getRoleLabel(scope.row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orgName" label="所属组织" width="150" />
      <el-table-column label="状态" width="80">
        <template #default="scope">
          <el-switch
            :value="scope.row.status === 1"
            @change="toggleStatus(scope.row)"
            active-text="启用"
            inactive-text="禁用"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="200">
        <template #default="scope">
          <el-button size="small" @click="editUser(scope.row)">编辑</el-button>
          <el-button size="small" @click="resetPassword(scope.row)">重置密码</el-button>
          <el-button
            size="small"
            type="danger"
            @click="deleteUser(scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page-num="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      @change="handlePageChange"
    />

    <el-dialog v-model="showAddDialog" :title="isEdit ? '编辑用户' : '新增用户'" width="500px">
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="userForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" placeholder="请选择角色">
            <el-option label="管理员" :value="1" />
            <el-option label="饲养员" :value="2" />
            <el-option label="屠宰员" :value="3" />
            <el-option label="质检员" :value="4" />
            <el-option label="经销商" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属组织">
          <el-select v-model="userForm.orgId" placeholder="请选择组织">
            <el-option v-for="org in orgOptions" :key="org.id" :label="org.name" :value="org.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input type="password" v-model="userForm.password" placeholder="请输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import SearchPanel from '@/components/common/SearchPanel.vue'
import type { User } from '@/types/system'

const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const userList = ref<User[]>([])

const showAddDialog = ref(false)
const isEdit = ref(false)
const userFormRef = ref()
const currentUserId = ref<number | null>(null)

const searchForm = reactive({
  username: '',
  role: '',
  status: '',
})

const userForm = reactive({
  username: '',
  realName: '',
  phone: '',
  email: '',
  role: 1,
  orgId: '',
  password: '',
})

const userRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const orgOptions = ref([
  { id: 1, name: '养殖基地A' },
  { id: 2, name: '屠宰场B' },
  { id: 3, name: '配送中心C' },
])

function getRoleLabel(role: number) {
  const roles: Record<number, string> = {
    1: '管理员',
    2: '饲养员',
    3: '屠宰员',
    4: '质检员',
    5: '经销商',
  }
  return roles[role] || '未知'
}

async function fetchUserList() {
  loading.value = true
  try {
    userList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch(form: Record<string, any>) {
  Object.assign(searchForm, form)
  pageNum.value = 1
  fetchUserList()
}

function handleReset() {
  pageNum.value = 1
  fetchUserList()
}

function handlePageChange(query: { pageNum: number; pageSize: number }) {
  pageNum.value = query.pageNum
  pageSize.value = query.pageSize
  fetchUserList()
}

function editUser(user: User) {
  isEdit.value = true
  currentUserId.value = user.id
  userForm.username = user.username
  userForm.realName = user.realName
  userForm.phone = user.phone
  userForm.email = user.email
  userForm.role = user.role
  userForm.orgId = user.orgId || ''
  showAddDialog.value = true
}

function resetPassword(user: User) {
  ElMessageBox.confirm(`确定要重置用户 ${user.username} 的密码吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).then(async () => {
    try {
      ElMessage.success('密码已重置为123456')
      fetchUserList()
    } catch (error: any) {
      ElMessage.error(error.message || '重置失败')
    }
  })
}

function deleteUser(user: User) {
  ElMessageBox.confirm(`确定要删除用户 ${user.username} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      ElMessage.success('删除成功')
      fetchUserList()
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
    }
  })
}

function toggleStatus(user: User) {
  const newStatus = user.status === 1 ? 0 : 1
  try {
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    fetchUserList()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function saveUser() {
  if (!userFormRef.value) return

  await userFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      showAddDialog.value = false
      fetchUserList()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchUserList()
})
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .card-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}
</style>