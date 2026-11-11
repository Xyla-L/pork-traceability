<template>
  <div class="page-container org-manage">
    <div class="org-layout">
      <!-- 左侧：按角色域（业务环节）分组的机构分类 -->
      <div class="org-tree-panel">
        <div class="tree-header">
          <span class="tree-title">机构分类</span>
        </div>
        <el-input v-model="treeFilter" placeholder="搜索机构..." clearable size="small" style="margin-bottom: 12px" />
        <el-tree ref="treeRef" :data="groupedTree" :props="{ children: 'children', label: 'label' }"
          node-key="id" :filter-node-method="filterNode" :expand-on-click-node="false"
          highlight-current default-expand-all @node-click="handleNodeClick">
          <template #default="{ data }">
            <!-- 分组行：机构类型 + 对应的角色域 + 家数 -->
            <div v-if="data.isGroup" class="tree-group">
              <span class="tree-group-label">
                <el-icon :color="data.color"><component :is="data.icon" /></el-icon>
                <span class="tree-group-name">{{ data.label }}</span>
                <el-tag size="small" type="info" effect="plain">{{ data.roleLabel }}</el-tag>
              </span>
              <span class="tree-group-meta">
                <span class="tree-group-count">{{ data.orgCount }} 家</span>
                <el-button type="primary" link size="small" @click.stop="handleAdd(data.type)">新增</el-button>
              </span>
            </div>

            <!-- 机构行 -->
            <div v-else class="tree-node-content">
              <span class="tree-node-label">
                <el-icon :color="iconColor(data.type)"><component :is="iconOf(data.type)" /></el-icon>
                {{ data.label }}
              </span>
              <span class="tree-node-actions">
                <el-button type="primary" link size="small" @click.stop="handleAddChild(data)">添加下级</el-button>
                <el-button type="primary" link size="small" @click.stop="handleEdit(data)">编辑</el-button>
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
            <el-descriptions-item label="所属环节">{{ orgRoleOf(selectedOrg.type)?.roleLabel || '--' }}</el-descriptions-item>
            <el-descriptions-item label="机构类型">{{ orgTypeLabel(selectedOrg.type) }}</el-descriptions-item>
            <el-descriptions-item label="上级 / 归属">{{ parentName(selectedOrg) }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ selectedOrg.manager || '--' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ selectedOrg.phone || '--' }}</el-descriptions-item>
            <el-descriptions-item label="地址">{{ selectedOrg.address || '--' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ selectedOrg.remark || '--' }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else description="请选择左侧机构查看详情" :image-size="80" />
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="formTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-form-item label="所属环节" prop="type">
          <el-select v-model="formData.type" placeholder="请选择环节" style="width: 100%" @change="handleTypeChange">
            <el-option v-for="g in ORG_ROLE_GROUPS" :key="g.type" :label="`${g.orgLabel}（${g.roleLabel}）`" :value="g.type" />
          </el-select>
        </el-form-item>
        <el-form-item label="机构名称" prop="label">
          <el-input v-model="formData.label" placeholder="请输入机构名称" />
        </el-form-item>
        <el-form-item label="上级机构">
          <el-select v-model="formData.parentId" placeholder="不选则为该环节顶级机构" filterable clearable style="width: 100%">
            <el-option v-for="o in parentOptions" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="formData.manager" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入联系电话" maxlength="11" />
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { Stamp, KnifeFork, Van, Shop, OfficeBuilding } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { systemOrgApi } from '@/api/modules/system'
import { ORG_ROLE_GROUPS, orgRoleOf, orgTypeLabel } from '@/constants/orgRoles'

interface OrgNode {
  id: number
  parentId: number | null
  type: string
  label: string
  name?: string
  manager?: string
  phone?: string
  address?: string
  remark?: string
  children?: OrgNode[]
  isGroup?: boolean
  roleLabel?: string
  color?: string
  icon?: any
  orgCount?: number
}

const ICONS: Record<string, any> = {
  farm: Stamp, slaughter: KnifeFork, distribution: Van, retail: Shop, supervisor: OfficeBuilding,
}

const treeRef = ref()
const treeFilter = ref('')
const selectedOrg = ref<any>(null)

const rawOrgs = ref<OrgNode[]>([])
/** 虚拟分组节点用负数 id，避开真实机构 id */
const GROUP_BASE_ID = -1

const iconOf = (type: string) => ICONS[type] || OfficeBuilding
const iconColor = (type: string) => orgRoleOf(type)?.color || '#909399'

/** 原始机构（扁平，去掉 children 递归，便于按 id 查） */
const flatOrgs = computed<OrgNode[]>(() => {
  const out: OrgNode[] = []
  const walk = (nodes: OrgNode[]) => {
    for (const n of nodes) {
      out.push(n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(rawOrgs.value)
  return out
})
const orgById = computed(() => new Map(flatOrgs.value.map((o) => [o.id, o])))

/**
 * 按角色域归组：一家机构只属于一个环节。
 * 组内仍保留真实上下级（父级必须在同一环节），跨环节的从属关系不进树 ——
 * 门店挂在配送中心下面属于「供货关系」，不是机构层级，放到详情里说明。
 */
const groupedTree = computed<OrgNode[]>(() => {
  return ORG_ROLE_GROUPS.map((g, idx) => {
    const members = flatOrgs.value
      .filter((o) => o.type === g.type)
      .sort((a, b) => (a.label || '').localeCompare(b.label || '', 'zh-CN'))
    const memberIds = new Set(members.map((o) => o.id))
    const byParent = new Map<number, OrgNode[]>()
    for (const m of members) {
      const pid = m.parentId && memberIds.has(m.parentId) ? m.parentId : 0
      if (!byParent.has(pid)) byParent.set(pid, [])
      byParent.get(pid)!.push({ ...m, children: [] })
    }
    const build = (pid: number): OrgNode[] =>
      (byParent.get(pid) || []).map((n) => ({ ...n, children: build(n.id) }))
    return {
      id: GROUP_BASE_ID - idx,
      isGroup: true,
      type: g.type,
      label: g.orgLabel,
      roleLabel: g.roleLabel,
      color: g.color,
      icon: ICONS[g.type] || OfficeBuilding,
      orgCount: members.length,
      children: build(0),
    } as OrgNode
  })
})

function filterNode(value: string, data: any) {
  if (!value) return true
  if (data.isGroup) return data.label?.includes(value)
  return (data.label || '').includes(value)
}

watch(treeFilter, (val) => { treeRef.value?.filter(val) })

function handleNodeClick(data: any) {
  if (data.isGroup) { selectedOrg.value = null; return }
  selectedOrg.value = data
}

function parentName(org: any): string {
  if (!org?.parentId) return '--'
  const p = orgById.value.get(org.parentId)
  if (!p) return '--'
  if (p.type === org.type) return p.label
  // 跨环节的从属不是机构层级：门店挂在配送中心下是供货关系，养殖场挂在监管局下是监管关系
  const RELATION: Record<string, string> = {
    supervisor: '监管单位', distribution: '供货单位', slaughter: '屠宰单位',
    farm: '来源养殖场', retail: '所属门店',
  }
  return `${p.label}（${RELATION[p.type] || orgTypeLabel(p.type)}）`
}

// 弹窗
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const formTitle = ref('新增机构')
const formData = reactive({
  id: 0, type: '', label: '', manager: '', phone: '', address: '', remark: '', parentId: null as number | null,
})
const rules = {
  type: [{ required: true, message: '请选择所属环节', trigger: 'change' }],
  label: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
}

/** 上级机构只能选同环节的，且不能选自己或自己的下级 */
const parentOptions = computed(() => {
  if (!formData.type) return []
  const descendants = new Set<number>()
  const collect = (id: number) => {
    for (const o of flatOrgs.value) {
      if (o.parentId === id && !descendants.has(o.id)) { descendants.add(o.id); collect(o.id) }
    }
  }
  if (formData.id) collect(formData.id)
  const list = flatOrgs.value
    .filter((o) => o.type === formData.type && o.id !== formData.id && !descendants.has(o.id))
    .map((o) => ({ id: o.id, name: o.label }))
  // 原上级若跨环节（存量数据如此），保留为可选项，避免一编辑就把归属抹掉
  if (formData.parentId && !list.some((o) => o.id === formData.parentId)) {
    const cur = orgById.value.get(formData.parentId)
    if (cur) list.unshift({ id: cur.id, name: `${cur.label}（${orgTypeLabel(cur.type)}）` })
  }
  return list
})

function handleTypeChange() {
  // 环节变了，原上级必然不在同一环节，清掉而不是悄悄留个跨环节父级
  formData.parentId = null
}

function handleAdd(type?: string) {
  isEdit.value = false
  formTitle.value = type ? `新增${orgTypeLabel(type)}` : '新增机构'
  Object.assign(formData, {
    id: 0, type: type || '', label: '', manager: '', phone: '', address: '', remark: '', parentId: null,
  })
  dialogVisible.value = true
}

function handleAddChild(data: any) {
  isEdit.value = false
  formTitle.value = `新增${orgTypeLabel(data.type)}（下级）`
  Object.assign(formData, {
    id: 0, type: data.type || '', label: '', manager: '', phone: '', address: '', remark: '', parentId: data.id,
  })
  dialogVisible.value = true
}

function handleEdit(data: any) {
  isEdit.value = true
  formTitle.value = '编辑机构'
  Object.assign(formData, {
    id: data.id,
    type: data.type || '',
    label: data.label || '',
    manager: data.manager || '',
    phone: data.phone || '',
    address: data.address || '',
    remark: data.remark || '',
    parentId: data.parentId || null,
  })
  dialogVisible.value = true
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
    selectedOrg.value = null
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
    const list: any = await systemOrgApi.tree()
    rawOrgs.value = Array.isArray(list) ? list : []
  } catch (error) {
    console.error('获取机构树失败:', error)
    rawOrgs.value = []
  }
}

onMounted(() => loadOrgTree())
</script>

<style lang="scss" scoped>
.org-manage { padding: 20px; background: #fff; border-radius: 4px; }
.org-layout { display: flex; gap: 24px; min-height: 500px; }
.org-tree-panel { width: 400px; flex-shrink: 0; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; }
.tree-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.tree-title { font-size: 15px; font-weight: 600; color: #303133; }

.tree-group { display: flex; align-items: center; justify-content: space-between; flex: 1; padding-right: 8px; }
.tree-group-label { display: flex; align-items: center; gap: 6px; }
.tree-group-name { font-size: 14px; font-weight: 600; color: #303133; }
.tree-group-meta { display: flex; align-items: center; gap: 8px; }
.tree-group-count { font-size: 12px; color: #909399; }

.tree-node-content { display: flex; align-items: center; justify-content: space-between; flex: 1; padding-right: 8px; }
.tree-node-label { display: flex; align-items: center; gap: 6px; font-size: 14px; color: #606266; }
.tree-node-actions { display: flex; gap: 4px; opacity: 0; transition: opacity 0.2s; }
:deep(.el-tree-node__content):hover .tree-node-actions { opacity: 1; }

.org-detail-panel {
  flex: 1;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 24px;
  /* 树很长（几十家机构），点中下部的机构时详情必须跟着视口走，否则要滚回页顶才能看到 */
  position: sticky;
  top: 20px;
  align-self: flex-start;
  max-height: calc(100vh - 40px);
  overflow-y: auto;
}
.detail-title { font-size: 18px; font-weight: 600; margin: 0 0 20px 0; }
</style>
