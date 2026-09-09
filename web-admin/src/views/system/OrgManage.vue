<template>
  <div class="page-container org-manage">
    <div class="org-layout">
      <!-- 左侧机构树 -->
      <div class="org-tree-panel">
        <div class="tree-header">
          <span class="tree-title">机构层级</span>
          <el-button type="primary" size="small" @click="handleCreate(null)"><el-icon><Plus /></el-icon>新增</el-button>
        </div>
        <el-input v-model="treeFilter" placeholder="搜索机构..." clearable size="small" style="margin-bottom: 12px" />
        <el-tree ref="treeRef" :data="orgTreeData" :props="{ children: 'children', label: 'label' }"
          node-key="id" :filter-node-method="filterNode" :expand-on-click-node="false"
          highlight-current default-expand-all @node-click="handleNodeClick">
          <template #default="{ node, data }">
            <div class="tree-node-content">
              <span class="tree-node-label">
                <el-icon v-if="data.type === 'farm'" color="#67c23a"><Stamp /></el-icon>
                <el-icon v-else-if="data.type === 'slaughter'" color="#f56c6c"><KnifeFork /></el-icon>
                <el-icon v-else-if="data.type === 'distribution'" color="#409eff"><Van /></el-icon>
                <el-icon v-else-if="data.type === 'retail'" color="#e6a23c"><Shop /></el-icon>
                <el-icon v-else color="#909399"><OfficeBuilding /></el-icon>
                {{ node.label }}
              </span>
              <span class="tree-node-actions">
                <el-button type="primary" link size="small" @click.stop="handleCreate(data)">+子级</el-button>
                <el-button type="warning" link size="small" @click.stop="handleEdit(data)">编辑</el-button>
                <el-button v-if="!data.children?.length" type="danger" link size="small" @click.stop="handleDelete(data)">删除</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- 右侧详情 -->
      <div class="org-detail-panel">
        <template v-if="selectedOrg">
          <h3 class="detail-title">{{ selectedOrg.label }}</h3>
          <el-descriptions :column="1" border size="default">
            <el-descriptions-item label="机构名称">{{ selectedOrg.label }}</el-descriptions-item>
            <el-descriptions-item label="机构类型">{{ orgTypeLabel(selectedOrg.type) }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ selectedOrg.manager || '--' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ selectedOrg.phone || '--' }}</el-descriptions-item>
            <el-descriptions-item label="地址">{{ selectedOrg.address || '--' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ selectedOrg.createTime || '--' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ selectedOrg.remark || '--' }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-actions">
            <el-button type="primary" @click="handleEdit(selectedOrg)">编辑</el-button>
            <el-button type="danger" plain @click="handleDelete(selectedOrg)">删除</el-button>
          </div>
        </template>
        <el-empty v-else description="请选择左侧机构查看详情" :image-size="80" />
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="formTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-form-item label="机构类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择" style="width: 100%" :disabled="isChild">
            <el-option label="养殖场" value="farm" />
            <el-option label="屠宰场" value="slaughter" />
            <el-option label="配送中心" value="distribution" />
            <el-option label="零售门店" value="retail" />
            <el-option label="监管机构" value="supervisor" />
          </el-select>
        </el-form-item>
        <el-form-item label="机构名称" prop="label">
          <el-input v-model="formData.label" placeholder="请输入机构名称" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="formData.manager" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="formData.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="formData.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
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
import { ref, reactive, watch, onMounted } from 'vue'
import { Plus, Stamp, KnifeFork, Van, Shop, OfficeBuilding } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemOrgApi } from '@/api/modules/system'

const treeRef = ref()
const treeFilter = ref('')
const selectedOrg = ref<any>(null)

const orgTreeData = ref<any[]>([])
const orgTypeLabel = (t: string) => ({ farm: '养殖场', slaughter: '屠宰场', distribution: '配送中心', retail: '零售门店', supervisor: '监管机构' } as Record<string, string>)[t] || t

// 弹窗
const dialogVisible = ref(false)
const isChild = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const formData = reactive({ id: 0, type: '', label: '', manager: '', phone: '', address: '', remark: '', parentId: null as number | null })
const rules = {
  type: [{ required: true, message: '请选择机构类型', trigger: 'change' }],
  label: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
}
const formTitle = ref('新增机构')

function filterNode(value: string, data: any) {
  if (!value) return true
  return data.label?.includes(value)
}

watch(treeFilter, (val) => { treeRef.value?.filter(val) })

function handleNodeClick(data: any) { selectedOrg.value = data }

function handleCreate(parent: any) {
  isEdit.value = false
  isChild.value = !!parent
  formTitle.value = parent ? `新增子机构（父: ${parent.label}）` : '新增机构'
  Object.assign(formData, { id: 0, type: parent?.type || '', label: '', manager: '', phone: '', address: '', remark: '', parentId: parent?.id || null })
  dialogVisible.value = true
}

function handleEdit(data: any) {
  isEdit.value = true
  isChild.value = false
  formTitle.value = '编辑机构'
  Object.assign(formData, { ...data, parentId: data.parentId || null })
  dialogVisible.value = true
}

function handleDelete(data: any) {
  ElMessageBox.confirm(`确定删除机构 "${data.label}" 吗？`, '警告', { type: 'warning', confirmButtonText: '确定删除' }).then(async () => {
    try {
      await systemOrgApi.remove(data.id)
      ElMessage.success(`机构 "${data.label}" 已删除`)
      selectedOrg.value = null
      loadOrgTree()
    } catch (error) {
      console.error('删除机构失败:', error)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = {
      parentId: formData.parentId || null,
      type: formData.type,
      name: formData.label,
      manager: formData.manager,
      phone: formData.phone,
      address: formData.address,
      remark: formData.remark,
    }
    if (isEdit.value) {
      await systemOrgApi.update(formData.id, payload)
      ElMessage.success('机构信息更新成功')
    } else {
      await systemOrgApi.create(payload)
      ElMessage.success('机构创建成功')
    }
    dialogVisible.value = false
    loadOrgTree()
  } catch (error) {
    console.error('保存机构失败:', error)
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

async function loadOrgTree() {
  try {
    const list = await systemOrgApi.tree()
    orgTreeData.value = Array.isArray(list) ? list : []
  } catch (error) {
    console.error('获取机构树失败:', error)
    orgTreeData.value = []
  }
}

onMounted(() => loadOrgTree())
</script>

<style lang="scss" scoped>
.org-manage { padding: 20px; background: #fff; border-radius: 4px; }
.org-layout { display: flex; gap: 24px; min-height: 500px; }
.org-tree-panel { width: 360px; flex-shrink: 0; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; }
.tree-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.tree-title { font-size: 15px; font-weight: 600; color: #303133; }
.tree-node-content { display: flex; align-items: center; justify-content: space-between; flex: 1; padding-right: 8px; }
.tree-node-label { display: flex; align-items: center; gap: 6px; font-size: 14px; }
.tree-node-actions { display: flex; gap: 4px; opacity: 0; transition: opacity 0.2s; }
:deep(.el-tree-node__content):hover .tree-node-actions { opacity: 1; }
.org-detail-panel { flex: 1; border: 1px solid #ebeef5; border-radius: 8px; padding: 24px; }
.detail-title { font-size: 18px; font-weight: 600; margin: 0 0 20px 0; }
.detail-actions { display: flex; gap: 12px; margin-top: 24px; justify-content: center; }
</style>
