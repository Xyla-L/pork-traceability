<template>
  <div class="page-container">
    <!-- 搜索 -->
    <div class="search-panel">
      <el-form :model="searchForm" inline>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="searchForm.realName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="全部" clearable style="width: 140px">
            <el-option label="养殖场" value="FARMER" />
            <el-option label="屠宰场" value="SLAUGHTER_OP" />
            <el-option label="配送商" value="DISTRIBUTOR" />
            <el-option label="零售商" value="RETAILER" />
            <el-option label="监管" value="SUPERVISOR" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="机构">
          <el-input v-model="searchForm.orgName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 100px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="handleCreate"><el-icon><Plus /></el-icon>新增用户</el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column prop="role" label="角色" width="120" align="center">
        <template #default="{ row }"><el-tag size="small" :type="roleColor(row.role)">{{ roleLabel(row.role) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="orgName" label="所属机构" min-width="160" show-overflow-tooltip />
      <el-table-column prop="phone" label="联系电话" width="140" align="center" />
      <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="warning" link size="small" @click="handleResetPwd(row)">重置密码</el-button>
          <el-button v-if="row.status === 1" type="danger" link size="small" @click="handleToggleStatus(row)">禁用</el-button>
          <el-button v-else type="success" link size="small" @click="handleToggleStatus(row)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" :disabled="isEdit" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="formData.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="formData.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="养殖场操作员" value="FARMER" />
            <el-option label="屠宰场操作员" value="SLAUGHTER_OP" />
            <el-option label="配送商操作员" value="DISTRIBUTOR" />
            <el-option label="零售商操作员" value="RETAILER" />
            <el-option label="监管人员" value="SUPERVISOR" />
            <el-option label="系统管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属机构" prop="orgName">
          <el-input v-model="formData.orgName" placeholder="请输入机构名称" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { EpTagType } from '@/types/common'

const searchForm = reactive({ username: '', realName: '', role: '', orgName: '', status: null as number | null })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const roleColor = (r: string): EpTagType => (({ FARMER: 'success', SLAUGHTER_OP: 'danger', DISTRIBUTOR: 'info', RETAILER: 'warning', SUPERVISOR: 'info', ADMIN: 'danger' } as Record<string, EpTagType>)[r] || 'info')
const roleLabel = (r: string) => ({ FARMER: '养殖场', SLAUGHTER_OP: '屠宰场', DISTRIBUTOR: '配送商', RETAILER: '零售商', SUPERVISOR: '监管', ADMIN: '管理员' } as Record<string, string>)[r] || r

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const formData = reactive({ id: 0, username: '', password: '', realName: '', role: '', orgName: '', phone: '', email: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  orgName: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
}

function handleCreate() {
  isEdit.value = false
  Object.assign(formData, { id: 0, username: '', password: '', realName: '', role: '', orgName: '', phone: '', email: '' })
  dialogVisible.value = true
}
function handleEdit(row: any) {
  isEdit.value = true
  Object.assign(formData, { ...row, password: '' })
  dialogVisible.value = true
}
async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      ElMessage.success('用户信息更新成功')
    } else {
      ElMessage.success('用户创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally { submitting.value = false }
}
function handleResetPwd(row: any) {
  ElMessageBox.confirm(`确认重置用户 ${row.username} 的密码吗？`, '提示', { type: 'warning' }).then(() => {
    ElMessage.success(`用户 ${row.username} 密码已重置为默认密码`)
  }).catch(() => {})
}
function handleToggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确认${action}用户 ${row.username} 吗？`, '提示', { type: 'warning' }).then(() => {
    row.status = newStatus
    ElMessage.success(`用户${action}成功`)
  }).catch(() => {})
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { Object.assign(searchForm, { username: '', realName: '', role: '', orgName: '', status: null }); handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function fetchList() {
  loading.value = true
  const roles = ['FARMER', 'SLAUGHTER_OP', 'DISTRIBUTOR', 'RETAILER', 'SUPERVISOR', 'ADMIN']
  const orgs = ['XX养殖合作社', 'XX市定点屠宰场', 'XX冷链物流公司', 'XX社区超市', '市动物卫生监督所']
  const list = Array.from({ length: 32 }, (_, i) => ({
    id: i + 1, username: `user_${String(i + 1).padStart(3, '0')}`,
    realName: ['张伟', '李娜', '王强', '刘洋', '陈静', '赵立', '孙明', '周芳'][i % 8],
    role: roles[i % roles.length], orgName: orgs[i % orgs.length],
    phone: `138${String(10000000 + i * 12345).substring(0, 8)}`,
    email: `user${i + 1}@porktrace.com`,
    status: i < 28 ? 1 : 0,
    createTime: `2024-0${6 + Math.floor(i / 10)}-${String(1 + i % 28).padStart(2, '0')} 10:00:00`
  }))
  const filtered = list.filter(item => {
    if (searchForm.username && !item.username.includes(searchForm.username)) return false
    if (searchForm.realName && !item.realName.includes(searchForm.realName)) return false
    if (searchForm.role && item.role !== searchForm.role) return false
    if (searchForm.orgName && !item.orgName.includes(searchForm.orgName)) return false
    if (searchForm.status !== null && item.status !== searchForm.status) return false
    return true
  })
  tableData.value = filtered.slice((pagination.pageNum - 1) * pagination.pageSize, pagination.pageNum * pagination.pageSize)
  pagination.total = filtered.length
  loading.value = false
}

onMounted(() => fetchList())
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.action-bar { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
</style>
