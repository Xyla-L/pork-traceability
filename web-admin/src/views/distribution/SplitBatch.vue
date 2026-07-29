<template>
  <div class="page-container">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">📦 分割操作</h2>
        <el-input v-model="batchSearch" placeholder="输入批次号搜索..." size="default" clearable
          style="width: 260px" @keyup.enter="highlightBatch" @clear="clearHighlight">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="handleExpandAll">{{ allExpanded ? '折叠全部' : '展开全部' }}</el-button>
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>导出关系图
        </el-button>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="handleCreateSplit"><el-icon><Plus /></el-icon>新建分割</el-button>
      </div>
    </div>

    <!-- 批次拆分树状图 -->
    <el-card v-if="splitTree" class="tree-card">
      <template #header>
        <div class="tree-card-header">
          <span>🌲 批次拆分树 — {{ splitTree.batchNo }}</span>
          <el-tag type="success" size="small">📦 {{ nodeCount }} 个节点</el-tag>
        </div>
      </template>
      <div class="tree-wrapper">
        <!-- 根节点 -->
        <div class="tree-root" :class="{ highlighted: highlightedBatch === splitTree.batchNo }">
          <TreeNode :node="splitTree" :highlighted="highlightedBatch === splitTree.batchNo"
            @view-detail="showDetail" @split="handleCreateSplitFrom" @chain-info="showChainInfo" />
        </div>

        <!-- 子节点树 -->
        <div v-if="splitTree.children && splitTree.children.length" class="tree-children">
          <div v-for="child in splitTree.children" :key="child.batchNo" class="tree-branch">
            <div class="branch-connector"></div>
            <TreeNode :node="child" :highlighted="highlightedBatch === child.batchNo"
              @view-detail="showDetail" @split="handleCreateSplitFrom" @chain-info="showChainInfo" />

            <!-- 递归子节点 -->
            <template v-if="child.children && child.children.length">
              <div v-for="grandKid in child.children" :key="grandKid.batchNo" class="tree-sub-branch">
                <div class="sub-branch-connector"></div>
                <TreeNode :node="grandKid" :highlighted="highlightedBatch === grandKid.batchNo"
                  @view-detail="showDetail" @split="handleCreateSplitFrom" @chain-info="showChainInfo" />
              </div>
            </template>
          </div>
        </div>
      </div>
    </el-card>

    <el-empty v-else description="暂无批次数据，请先创建胴体批次" :image-size="80">
      <el-button type="primary" @click="$router.push('/admin/distribution/batch')">前往创建批次</el-button>
    </el-empty>

    <!-- 批次详情抽屉 -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="480px">
      <template v-if="detailNode">
        <el-descriptions :column="1" border size="default" style="margin-bottom: 16px">
          <el-descriptions-item label="批次号">{{ detailNode.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ detailNode.productName || '胴体批次' }}</el-descriptions-item>
          <el-descriptions-item label="重量">{{ detailNode.weightKg || detailNode.totalWeightKg || '--' }} kg</el-descriptions-item>
          <el-descriptions-item label="包数">{{ detailNode.packageCount || '--' }}</el-descriptions-item>
          <el-descriptions-item label="层级">{{ detailNode.splitLevel || 0 }}</el-descriptions-item>
          <el-descriptions-item label="父批次">{{ detailNode.parentBatchId ? detailNode.parentBatchId : '无（根批次）' }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detailNode.operator || '--' }}</el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ detailNode.splitTime || detailNode.createTime || '--' }}</el-descriptions-item>
          <el-descriptions-item label="车间">{{ detailNode.workshop || '--' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 哈希信息面板 -->
        <BatchHashPanel :node="detailNode" />

        <!-- 区块链状态 -->
        <div class="drawer-chain-section">
          <div class="section-label">🔗 区块链状态</div>
          <BlockchainVerifyBadge :status="detailNode.txHash ? 'confirmed' : 'pending'" :tx-hash="detailNode.txHash" />
          <div style="margin-top: 12px">
            <el-button type="primary" size="small" @click="showChainInfo(detailNode)">查看交易详情</el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 新建分割弹窗 -->
    <el-dialog v-model="splitDialogVisible" title="新建分割操作" width="560px" destroy-on-close>
      <el-form ref="splitFormRef" :model="splitForm" :rules="splitRules" label-width="100px">
        <el-form-item label="父批次" prop="parentBatchNo">
          <el-input :model-value="splitForm.parentBatchNo" disabled />
        </el-form-item>
        <el-form-item label="分割层级" prop="splitLevel">
          <el-input-number v-model="splitForm.splitLevel" :min="1" :max="5" disabled />
          <span class="form-hint">自动计算</span>
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-select v-model="splitForm.productName" placeholder="选择或输入产品名" filterable allow-create style="width: 100%">
            <el-option label="猪前腿肉" value="猪前腿肉" />
            <el-option label="猪后腿肉" value="猪后腿肉" />
            <el-option label="猪五花肉" value="猪五花肉" />
            <el-option label="猪里脊" value="猪里脊" />
            <el-option label="猪排骨" value="猪排骨" />
            <el-option label="猪蹄" value="猪蹄" />
            <el-option label="猪肝" value="猪肝" />
            <el-option label="猪肚" value="猪肚" />
          </el-select>
        </el-form-item>
        <el-form-item label="重量 (kg)" prop="weightKg">
          <el-input-number v-model="splitForm.weightKg" :min="0.1" :precision="1" :max="splitForm.maxWeight" style="width: 200px" />
          <span class="form-hint">可用: {{ splitForm.maxWeight }} kg</span>
        </el-form-item>
        <el-form-item label="包装数量" prop="packageCount">
          <el-input-number v-model="splitForm.packageCount" :min="1" :max="999" style="width: 200px" />
        </el-form-item>
        <el-form-item label="操作车间">
          <el-input v-model="splitForm.workshop" placeholder="如: 分割车间A组" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="splitForm.note" type="textarea" :rows="2" placeholder="备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="splitDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doSplit" :loading="splitting">确认分割</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, h, defineComponent, onMounted } from 'vue'
import { Search, Plus, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'

// ========== TreeNode 组件 (内联递归) ==========
const TreeNode = defineComponent({
  name: 'TreeNode',
  props: { node: Object, highlighted: Boolean },
  emits: ['view-detail', 'split', 'chain-info'],
  setup(props, { emit }) {
    const isExpanded = ref(true)
    const node = computed(() => props.node as any)
    const hasChildren = computed(() => node.value.children && node.value.children.length > 0)
    const style = computed(() => props.highlighted ? { border: '2px solid #409eff', boxShadow: '0 0 12px rgba(64,158,255,0.3)' } : {})

    return () => h('div', {
      class: ['tree-node', { 'is-expanded': isExpanded.value, 'is-highlighted': props.highlighted }],
      style: style.value,
    }, [
      h('div', { class: 'tree-node-header', onClick: () => isExpanded.value = !isExpanded.value }, [
        h('span', { class: 'node-expand-icon' }, hasChildren.value ? (isExpanded.value ? '▾' : '▸') : '·'),
        h('span', { class: 'node-icon' }, ['', '🥩', '🍖', '🥓'][node.value.splitLevel || 0] || '🐖'),
        h('span', { class: 'node-name' }, node.value.productName || node.value.batchNo || '未知'),
        h('span', { class: 'node-batch' }, node.value.batchNo),
        h('span', { class: 'node-weight' }, `${node.value.weightKg || node.value.totalWeightKg || '--'} kg`),
        node.value.packageCount ? h('span', { class: 'node-pkg' }, `${node.value.packageCount}包`) : null,
        h('span', { class: 'node-actions', onClick: (e: Event) => e.stopPropagation() }, [
          h('el-button', { size: 'small', type: 'primary', link: true, onClick: () => emit('view-detail', node.value) }, { default: () => '详情' }),
          h('el-button', { size: 'small', type: 'success', link: true, onClick: () => emit('split', node.value) }, { default: () => '分割' }),
          h('el-button', { size: 'small', type: 'info', link: true, onClick: () => emit('chain-info', node.value) }, { default: () => '链上' }),
        ]),
      ]),
      h('el-collapse-transition', null, {
        default: () => isExpanded.value && hasChildren.value
          ? h('div', { class: 'tree-node-children' },
              node.value.children.map((child: any) =>
                h(TreeNode, {
                  node: child,
                  highlighted: false,
                  onViewDetail: (n: any) => emit('view-detail', n),
                  onSplit: (n: any) => emit('split', n),
                  onChainInfo: (n: any) => emit('chain-info', n),
                })
              )
            )
          : null,
      }),
    ])
  },
})

// ========== BatchHashPanel 组件 ==========
const BatchHashPanel = defineComponent({
  name: 'BatchHashPanel',
  props: { node: Object },
  setup(props) {
    return () => {
      const n = props.node as any
      return h('div', { class: 'hash-panel' }, [
        h('div', { class: 'section-label' }, '📊 数据哈希'),
        h('el-descriptions', { column: 1, border: true, size: 'small' }, () => [
          h('el-descriptions-item', { label: '内容哈希 (SHA-256)' }, () =>
            h('code', { class: 'hash-code' }, n.contentHash?.substring(0, 24) + '...' || '未生成')),
          h('el-descriptions-item', { label: '链上交易哈希' }, () =>
            h('code', { class: 'hash-code', style: { color: n.txHash ? '#67c23a' : '#e6a23c' } },
              n.txHash?.substring(0, 24) + '...' || '待上链')),
        ]),
      ])
    }
  },
})

// ========== 状态变量 ==========
const batchSearch = ref('')
const highlightedBatch = ref('')
const allExpanded = ref(true)
const drawerTitle = ref('')

// 批次树
interface SplitNode {
  id: number; batchNo: string; parentBatchId: number | null; splitLevel: number
  productName: string; weightKg: number; packageCount: number;
  splitTime: string; workshop: string; operator: string; fileIds: string[]
  contentHash: string; txHash?: string; createTime: string; children?: SplitNode[]
  totalWeightKg?: number // root batch
}
const splitTree = ref<SplitNode | null>(null)
const nodeCount = computed(() => countNodes(splitTree.value))

// 详情抽屉
const drawerVisible = ref(false)
const detailNode = ref<SplitNode | null>(null)

// 分割弹窗
const splitDialogVisible = ref(false)
const splitting = ref(false)
const splitFormRef = ref()
const splitForm = reactive({
  parentBatchNo: '', parentId: 0, splitLevel: 1, productName: '',
  weightKg: 10, packageCount: 5, maxWeight: 500, workshop: '分割车间A组', note: '',
})
const splitRules = {
  productName: [{ required: true, message: '请选择或输入产品名', trigger: 'change' }],
  weightKg: [{ required: true, message: '请输入重量', trigger: 'blur' }],
}

// ========== 操作 ==========
function showDetail(node: SplitNode) {
  detailNode.value = node
  drawerTitle.value = `批次详情 — ${node.batchNo}`
  drawerVisible.value = true
}

function handleCreateSplitFrom(node?: SplitNode) {
  const parent = node || splitTree.value
  if (!parent) return
  splitForm.parentBatchNo = parent.batchNo
  splitForm.parentId = parent.id
  splitForm.splitLevel = (parent.splitLevel || 0) + 1
  splitForm.maxWeight = parent.weightKg || parent.totalWeightKg || 100
  splitForm.productName = ''
  splitForm.weightKg = Math.min(10, splitForm.maxWeight)
  splitForm.packageCount = 5
  splitForm.workshop = '分割车间A组'
  splitForm.note = ''
  splitDialogVisible.value = true
}

function handleCreateSplit() { handleCreateSplitFrom() }

function doSplit() {
  splitting.value = true
  setTimeout(() => {
    ElMessage.success('分割操作完成，批次关系已上链')
    splitDialogVisible.value = false
    splitting.value = false
  }, 600)
}

function showChainInfo(node: SplitNode) {
  if (node.txHash) {
    ElMessage.success(`链上已确认\n交易哈希: ${node.txHash}\n点击可跳转区块链浏览器查看详情`)
  } else {
    ElMessage.warning('该批次尚未上链，请在创建批次时触发上链')
  }
}

function highlightBatch() {
  highlightedBatch.value = batchSearch.value
  if (batchSearch.value) ElMessage.success(`已定位到批次: ${batchSearch.value}`)
}

function clearHighlight() { highlightedBatch.value = '' }

function handleExpandAll() {
  allExpanded.value = !allExpanded.value
  // 刷新展开状态 (实际项目中需要递归设置)
}

function handleExport() {
  ElMessage.success('批次关系图导出中...')
}

function countNodes(tree: SplitNode | null): number {
  if (!tree) return 0
  let count = 1
  if (tree.children) {
    for (const child of tree.children) count += countNodes(child)
  }
  return count
}

// ========== 初始化 ==========
function buildMockTree() {
  splitTree.value = {
    id: 1, batchNo: 'B20240715001', parentBatchId: null, splitLevel: 0,
    productName: '胴体批次', weightKg: 354.0, packageCount: 1,
    splitTime: '2024-07-02 15:00', workshop: '待分割车间', operator: '王建国',
    fileIds: [], contentHash: '0x7a3b8c2d1e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b',
    txHash: '0x7a3b8c2d1e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b',
    totalWeightKg: 354.0, createTime: '2024-07-02 15:00:00',
    children: [
      {
        id: 11, batchNo: 'B20240715001-1', parentBatchId: 1, splitLevel: 1,
        productName: '猪前腿肉', weightKg: 120.5, packageCount: 25,
        splitTime: '2024-07-03 08:30', workshop: '分割车间A组', operator: '赵师傅',
        fileIds: [], contentHash: '0x8c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d',
        txHash: '0x8c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d',
        createTime: '2024-07-03 08:30:00',
        children: [
          { id: 111, batchNo: 'B20240715001-1-A', parentBatchId: 11, splitLevel: 2,
            productName: '猪前腿肉(小包装)', weightKg: 60.0, packageCount: 120,
            splitTime: '2024-07-03 09:30', workshop: '分割车间A组', operator: '赵师傅',
            fileIds: [], contentHash: '0x9d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e',
            txHash: '0x9d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e',
            createTime: '2024-07-03 09:30:00', children: [] },
          { id: 112, batchNo: 'B20240715001-1-B', parentBatchId: 11, splitLevel: 2,
            productName: '猪前腿肉(家庭装)', weightKg: 60.5, packageCount: 30,
            splitTime: '2024-07-03 09:35', workshop: '分割车间A组', operator: '赵师傅',
            fileIds: [], contentHash: '0xae6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f',
            txHash: '0xae6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f',
            createTime: '2024-07-03 09:35:00', children: [] },
        ],
      },
      {
        id: 12, batchNo: 'B20240715001-2', parentBatchId: 1, splitLevel: 1,
        productName: '猪后腿肉', weightKg: 98.0, packageCount: 20,
        splitTime: '2024-07-03 08:45', workshop: '分割车间A组', operator: '赵师傅',
        fileIds: [], contentHash: '0xbf7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a',
        createTime: '2024-07-03 08:45:00',
        children: [
          { id: 121, batchNo: 'B20240715001-2-A', parentBatchId: 12, splitLevel: 2,
            productName: '猪后腿肉(精瘦)', weightKg: 50.0, packageCount: 100,
            splitTime: '2024-07-03 10:00', workshop: '分割车间B组', operator: '钱师傅',
            fileIds: [], contentHash: '0xc1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a',
            createTime: '2024-07-03 10:00:00', children: [] },
        ],
      },
      {
        id: 13, batchNo: 'B20240715001-3', parentBatchId: 1, splitLevel: 1,
        productName: '猪排骨', weightKg: 75.5, packageCount: 15,
        splitTime: '2024-07-03 09:00', workshop: '分割车间B组', operator: '钱师傅',
        fileIds: [], contentHash: '0xd2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b',
        txHash: '0xd2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b',
        createTime: '2024-07-03 09:00:00', children: [],
      },
      {
        id: 14, batchNo: 'B20240715001-4', parentBatchId: 1, splitLevel: 1,
        productName: '猪蹄/杂骨', weightKg: 60.0, packageCount: 30,
        splitTime: '2024-07-03 09:15', workshop: '分割车间B组', operator: '钱师傅',
        fileIds: [], contentHash: '0xe3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c',
        createTime: '2024-07-03 09:15:00', children: [],
      },
    ],
  }
}

onMounted(() => buildMockTree())
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }

// 工具栏
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.toolbar-left { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.toolbar-right { display: flex; gap: 8px; }
.page-title { font-size: 18px; font-weight: 700; margin: 0; }

// 树卡片
.tree-card { margin-bottom: 0; }
.tree-card-header { display: flex; align-items: center; justify-content: space-between; }
.tree-wrapper { padding: 16px 0; }
.tree-root { margin-bottom: 24px; }
.tree-children { padding-left: 40px; }

// 分支连接
.tree-branch { position: relative; margin-bottom: 8px; }
.branch-connector {
  position: absolute; left: -24px; top: 0; width: 20px; height: 50%;
  border-left: 2px solid #c0c4cc; border-bottom: 2px solid #c0c4cc; border-radius: 0 0 0 4px;
}
.tree-sub-branch { position: relative; margin-left: 40px; margin-bottom: 6px; }
.sub-branch-connector {
  position: absolute; left: -24px; top: 0; width: 20px; height: 50%;
  border-left: 2px solid #dcdfe6; border-bottom: 2px solid #dcdfe6; border-radius: 0 0 0 4px;
}

// 树节点
:deep(.tree-node) {
  margin-bottom: 4px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  transition: all 0.2s;
  &:hover { border-color: #409eff; box-shadow: 0 2px 6px rgba(64,158,255,0.1); }
  &.is-highlighted { border-color: #409eff; box-shadow: 0 0 12px rgba(64,158,255,0.3); animation: highlightPulse 2s ease-in-out; }
}
:deep(.tree-node-header) {
  display: flex; align-items: center; gap: 10px; padding: 12px 16px; cursor: pointer; user-select: none;
}
:deep(.node-expand-icon) { width: 16px; font-size: 12px; color: #909399; text-align: center; flex-shrink: 0; }
:deep(.node-icon) { font-size: 18px; flex-shrink: 0; }
:deep(.node-name) { font-size: 14px; font-weight: 600; color: #303133; }
:deep(.node-batch) { font-size: 12px; color: #909399; font-family: 'Courier New', monospace; background: #f5f7fa; padding: 2px 8px; border-radius: 3px; }
:deep(.node-weight) { font-size: 13px; color: #606266; margin-left: auto; }
:deep(.node-pkg) { font-size: 12px; color: #909399; }
:deep(.node-actions) { display: flex; gap: 4px; opacity: 0; transition: opacity 0.2s; }
:deep(.tree-node-header):hover :deep(.node-actions) { opacity: 1; }
:deep(.tree-node-children) { padding: 4px 0 4px 40px; border-top: 1px solid #f5f7fa; }

// 抽屉
.drawer-chain-section { margin-top: 16px; padding-top: 16px; border-top: 1px solid #ebeef5; }
.section-label { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.hash-code { font-family: 'Courier New', monospace; font-size: 12px; background: #f5f7fa; padding: 2px 6px; border-radius: 3px; color: #67c23a; word-break: break-all; }

// 哈希面板
:deep(.hash-panel) { margin-bottom: 16px; padding: 12px; background: #f5f7fa; border-radius: 6px; }

// 表单
.form-hint { font-size: 12px; color: #909399; margin-left: 8px; }

@keyframes highlightPulse {
  0%, 100% { box-shadow: 0 0 12px rgba(64,158,255,0.3); }
  50% { box-shadow: 0 0 24px rgba(64,158,255,0.6); }
}
</style>
