<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="card-title">组织管理</h2>
      <el-button type="primary" @click="showAddDialog = true">新增组织</el-button>
    </div>

    <div class="org-tree-container">
      <el-tree
        :data="orgTree"
        :props="treeProps"
        node-key="id"
        default-expand-all
        highlight-current
        @node-click="handleNodeClick"
      >
        <template #default="{ node, data }">
          <span class="tree-node">
            <span>{{ data.name }}</span>
            <span class="tree-actions">
              <el-button size="small" @click.stop="editOrg(data)">编辑</el-button>
              <el-button
                size="small"
                type="danger"
                @click.stop="deleteOrg(data)"
              >
                删除
              </el-button>
            </span>
          </span>
        </template>
      </el-tree>
    </div>

    <div class="org-detail" v-if="currentOrg">
      <h3 class="detail-title">组织详情</h3>
      <el-form :model="currentOrg" label-width="100px">
        <el-form-item label="组织名称">
          {{ currentOrg.name }}
        </el-form-item>
        <el-form-item label="组织类型">
          {{ getOrgTypeLabel(currentOrg.type) }}
        </el-form-item>
        <el-form-item label="负责人">
          {{ currentOrg.contactName }}
        </el-form-item>
        <el-form-item label="联系电话">
          {{ currentOrg.contactPhone }}
        </el-form-item>
        <el-form-item label="地址">
          {{ currentOrg.address }}
        </el-form-item>
        <el-form-item label="描述">
          {{ currentOrg.description }}
        </el-form-item>
        <el-form-item label="创建时间">
          {{ currentOrg.createTime }}
        </el-form-item>
      </el-form>
    </div>

    <el-dialog v-model="showAddDialog" :title="isEdit ? '编辑组织' : '新增组织'" width="500px">
      <el-form ref="orgFormRef" :model="orgForm" :rules="orgRules" label-width="80px">
        <el-form-item label="上级组织">
          <el-select v-model="orgForm.parentId" placeholder="请选择上级组织">
            <el-option :label="'无（顶级）'" :value="0" />
            <el-option
              v-for="org in orgOptions"
              :key="org.id"
              :label="org.name"
              :value="org.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="组织名称" prop="name">
          <el-input v-model="orgForm.name" placeholder="请输入组织名称" />
        </el-form-item>
        <el-form-item label="组织类型" prop="type">
          <el-select v-model="orgForm.type" placeholder="请选择组织类型">
            <el-option label="养殖基地" :value="1" />
            <el-option label="屠宰场" :value="2" />
            <el-option label="配送中心" :value="3" />
            <el-option label="销售门店" :value="4" />
            <el-option label="管理机构" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="contactName">
          <el-input v-model="orgForm.contactName" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="orgForm.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="orgForm.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="描述">
          <textarea v-model="orgForm.description" rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveOrg">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Organization } from '@/types/system'

const orgTree = ref<Organization[]>([])
const currentOrg = ref<Organization | null>(null)

const showAddDialog = ref(false)
const isEdit = ref(false)
const orgFormRef = ref()
const currentOrgId = ref<number | null>(null)

const orgOptions = ref<Organization[]>([])

const orgForm = reactive({
  parentId: 0,
  name: '',
  type: 1,
  contactName: '',
  contactPhone: '',
  address: '',
  description: '',
})

const orgRules = {
  name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择组织类型', trigger: 'change' }],
  contactName: [{ required: true, message: '请输入负责人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
}

const treeProps = {
  children: 'children',
  label: 'name',
}

function getOrgTypeLabel(type: number) {
  const types: Record<number, string> = {
    1: '养殖基地',
    2: '屠宰场',
    3: '配送中心',
    4: '销售门店',
    5: '管理机构',
  }
  return types[type] || '未知'
}

async function fetchOrgTree() {
  try {
    orgTree.value = []
    orgOptions.value = []
  } catch (error) {
    console.error(error)
  }
}

function handleNodeClick(data: Organization) {
  currentOrg.value = data
}

function editOrg(org: Organization) {
  isEdit.value = true
  currentOrgId.value = org.id
  orgForm.parentId = org.parentId || 0
  orgForm.name = org.name
  orgForm.type = org.type
  orgForm.contactName = org.contactName
  orgForm.contactPhone = org.contactPhone
  orgForm.address = org.address
  orgForm.description = org.description
  showAddDialog.value = true
}

function deleteOrg(org: Organization) {
  ElMessageBox.confirm(`确定要删除组织 ${org.name} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      ElMessage.success('删除成功')
      fetchOrgTree()
      currentOrg.value = null
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
    }
  })
}

async function saveOrg() {
  if (!orgFormRef.value) return

  await orgFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    try {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      showAddDialog.value = false
      fetchOrgTree()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    }
  })
}

onMounted(() => {
  fetchOrgTree()
})
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
  display: flex;
  gap: 20px;
}

.page-header {
  position: absolute;
  top: 20px;
  right: 20px;
  display: flex;
  justify-content: flex-end;

  .card-title {
    display: none;
  }
}

.org-tree-container {
  width: 350px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #fff;

  .tree-node {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;

    .tree-actions {
      visibility: hidden;
    }
  }

  .el-tree-node:hover .tree-actions {
    visibility: visible;
  }
}

.org-detail {
  flex: 1;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 20px;
  background: #fff;

  .detail-title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 20px;
    color: #303133;
  }
}
</style>