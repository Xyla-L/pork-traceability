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
  ElMessageBox.confirm(`确定删除机构 "${data.label}" 吗？`, '警告', { type: 'warning', confirmButtonText: '确定删除' }).then(() => {
    ElMessage.success(`机构 "${data.label}" 已删除`)
    loadOrgTree()
  }).catch(() => {})
}

function handleSubmit() {
  formRef.value?.validate().then(() => {
    ElMessage.success(isEdit.value ? '机构信息更新成功' : '机构创建成功')
    dialogVisible.value = false
    loadOrgTree()
  }).catch(() => {})
}

function loadOrgTree() {
  // 模拟机构树
  orgTreeData.value = [
    { id: 1, label: 'XX养殖合作社', type: 'farm', manager: '张社长', phone: '13800001001', address: 'XX省XX市XX县XX村', createTime: '2024-01-15', children: [] },
    {
      id: 2, label: 'XX市定点屠宰场', type: 'slaughter', manager: '李厂长', phone: '13800002001', address: 'XX市XX区XX路100号', createTime: '2024-01-20',
      children: [
        { id: 21, label: '分割车间A组', type: 'slaughter', manager: '王主任', phone: '13800002011', address: '同上', createTime: '2024-02-01', children: [] },
        { id: 22, label: '分割车间B组', type: 'slaughter', manager: '刘主任', phone: '13800002012', address: '同上', createTime: '2024-02-01', children: [] },
      ]
    },
    {
      id: 3, label: 'XX冷链物流公司', type: 'distribution', manager: '赵经理', phone: '13800003001', address: 'XX市XX区XX物流园3号', createTime: '2024-02-10',
      children: [
        { id: 31, label: '运输车队一队', type: 'distribution', manager: '孙队长', phone: '13800003011', address: '同上', createTime: '2024-02-15', children: [] },
        { id: 32, label: '运输车队二队', type: 'distribution', manager: '周队长', phone: '13800003012', address: '同上', createTime: '2024-02-15', children: [] },
      ]
    },
    { id: 4, label: '市动物卫生监督所', type: 'supervisor', manager: '陈所长', phone: '13800004001', address: 'XX市XX区XX路200号', createTime: '2024-01-01', children: [] },
    { id: 5, label: 'XX社区超市', type: 'retail', manager: '钱店长', phone: '13800005001', address: 'XX市XX区XX社区', createTime: '2024-03-01', children: [] },
    { id: 6, label: 'YY生鲜店', type: 'retail', manager: '吴店长', phone: '13800005002', address: 'XX市XX区YY路50号', createTime: '2024-03-05', children: [] },
  ]
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
