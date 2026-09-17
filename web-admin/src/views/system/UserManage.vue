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
        <el-form-item label="所属机构" prop="orgId">
          <el-select v-model="formData.orgId" placeholder="请选择机构" filterable clearable style="width: 100%">
            <el-option v-for="o in orgOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入联系电话" maxlength="11" />
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
import { systemUserApi, systemOrgApi } from '@/api/modules/system'

const searchForm = reactive({ username: '', realName: '', role: '', orgName: '', status: null as number | null })
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const orgOptions = ref<{ label: string; value: number }[]>([])

const roleColor = (r: string): EpTagType => (({ FARMER: 'success', SLAUGHTER_OP: 'danger', DISTRIBUTOR: 'info', RETAILER: 'warning', SUPERVISOR: 'info', ADMIN: 'danger' } as Record<string, EpTagType>)[r] || 'info')
const roleLabel = (r: string) => ({ FARMER: '养殖场', SLAUGHTER_OP: '屠宰场', DISTRIBUTOR: '配送商', RETAILER: '零售商', SUPERVISOR: '监管', ADMIN: '管理员' } as Record<string, string>)[r] || r

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const formData = reactive({ id: 0, username: '', password: '', nickname: '', realName: '', role: '', orgId: null as number | null, phone: '', email: '', status: 1 })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
}

function handleCreate() {
  isEdit.value = false
  Object.assign(formData, { id: 0, username: '', password: '', nickname: '', realName: '', role: '', orgId: null, phone: '', email: '', status: 1 })
  dialogVisible.value = true
}
async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await systemUserApi.update(formData.id, {
        nickname: formData.nickname, realName: formData.realName, phone: formData.phone,
        email: formData.email, orgId: formData.orgId, role: formData.role, status: formData.status,
      })
      ElMessage.success('用户信息更新成功')
    } else {
      await systemUserApi.create({
        username: formData.username, password: formData.password, nickname: formData.nickname,
        realName: formData.realName, phone: formData.phone, email: formData.email,
        orgId: formData.orgId, role: formData.role, status: 1,
      })
      ElMessage.success('用户创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    // 错误已在请求拦截器中提示
  } finally { submitting.value = false }
}
function handleResetPwd(row: any) {
  ElMessageBox.confirm(`确认重置用户 ${row.username} 的密码吗？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await systemUserApi.resetPassword(row.id)
      ElMessage.success(`用户 ${row.username} 密码已重置为 123456`)
    } catch (e) { /* 已提示 */ }
  }).catch(() => {})
}
function handleToggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确认${action}用户 ${row.username} 吗？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await systemUserApi.toggleStatus(row.id, newStatus)
      row.status = newStatus
      ElMessage.success(`用户${action}成功`)
    } catch (e) { /* 已提示 */ }
  }).catch(() => {})
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { Object.assign(searchForm, { username: '', realName: '', role: '', orgName: '', status: null }); handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

async function fetchList() {
  loading.value = true
  try {
    const res: any = await systemUserApi.list({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      username: searchForm.username || undefined,
      realName: searchForm.realName || undefined,
      role: searchForm.role || undefined,
      orgName: searchForm.orgName || undefined,
      status: searchForm.status === null ? undefined : searchForm.status,
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch (e) {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

async function loadOrgOptions() {
  try {
    const tree: any = await systemOrgApi.tree()
    const opts: { label: string; value: number }[] = []
    const walk = (nodes: any[]) => {
      if (!Array.isArray(nodes)) return
      for (const n of nodes) {
        opts.push({ label: n.label || n.name, value: n.id })
        if (n.children?.length) walk(n.children)
      }
    }
    walk(tree || [])
    orgOptions.value = opts
  } catch (e) {
    orgOptions.value = []
  }
}

onMounted(() => {
  loadOrgOptions()
  fetchList()
})
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.action-bar { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
</style>
