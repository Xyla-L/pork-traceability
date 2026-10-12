<template>
  <div class="page-container">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">分割操作</h2>
        <el-select
          v-model="currentBatchNo"
          placeholder="选择批次"
          filterable
          style="width: 220px"
          @change="handleBatchChange"
        >
          <el-option
            v-for="b in batchOptions"
            :key="b.batchNo"
            :label="b.splitCount ? `${b.batchNo}（已分割 ${b.splitCount} 项）` : `${b.batchNo}（未分割）`"
            :value="b.batchNo"
          />
        </el-select>
        <el-autocomplete
          v-model="batchSearch"
          :fetch-suggestions="queryBatchSuggestions"
          placeholder="输入批次号搜索..."
          size="default"
          clearable
          style="width: 260px"
          value-key="batchNo"
          @select="handleSelectBatch"
          @keyup.enter="highlightBatch"
          @clear="clearHighlight"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
          <template #default="{ item }">
            <span>{{ item.batchNo }}</span>
            <span v-if="item.slaughterhouse" style="float: right; color: #909399; font-size: 12px; margin-left: 12px">{{ item.slaughterhouse }}</span>
            <span style="float: right; color: #909399; font-size: 12px; margin-left: 12px">
              {{ item.splitCount ? `已分割 ${item.splitCount} 项` : '未分割' }}
            </span>
          </template>
        </el-autocomplete>
        <el-button type="primary" plain @click="highlightBatch">
          <el-icon><Search /></el-icon>搜索
        </el-button>
        <el-button @click="handleExpandAll">{{ allExpanded ? '折叠全部' : '展开全部' }}</el-button>
      </div>
      <div class="toolbar-right">
        <el-tooltip
          content="分割批次由分割线扫码称重台自动创建并上链。人工新建入口已删除——这是「数据只认设备」的硬约束，设备上报异常的数据请到「设备接入 → 待人工处理」实名复核"
          placement="top"
        >
          <el-tag type="warning" effect="plain" size="small">设备独占</el-tag>
        </el-tooltip>
      </div>
    </div>

    <!-- 批次拆分树状图 -->
    <el-card v-if="splitTree" class="tree-card">
      <template #header>
        <div class="tree-card-header">
          <span>批次拆分树 — {{ splitTree.batchNo }}</span>
          <el-tag type="success" size="small">{{ nodeCount }} 个节点</el-tag>
        </div>
      </template>
      <div class="tree-wrapper">
        <!-- 根节点（TreeNode 内部递归渲染全部子节点） -->
        <div class="tree-root" :class="{ highlighted: highlightedBatch === splitTree.batchNo }">
          <TreeNode :node="splitTree" :highlighted="highlightedBatch === splitTree.batchNo"
            :selected-key="selectedKey"
            @view-detail="showDetail" @chain-info="showChainInfo"
            @select="handleSelectNode" />
        </div>
        <!-- 空态引导：批次还没上分割线时，只给一个光秃秃的根节点等于没内容 -->
        <div v-if="!hasSplitData" class="tree-empty-hint">
          <div class="hint-title">该批次还没有分割记录</div>
          <div class="hint-text">
            分割批次来自分割线扫码称重台：一头白条切成哪些部位由设备整份上报，系统生成作业号后自动落库，
            人工不能补录。选一个已分割的批次可以看到完整拆分树。
          </div>
          <el-button link type="primary" @click="goLatestSplitBatch">跳到最近有分割数据的批次</el-button>
        </div>
      </div>
    </el-card>

    <el-empty v-else description="暂无批次数据，请先创建胴体批次" :image-size="80">
      <el-button type="primary" @click="$router.push('/admin/distribution/batch')">前往创建批次</el-button>
    </el-empty>

    <!-- 分割车间监控：展示**当前这批货**在分割线上的现场照片。
         照片由工位相机在分割开始后自动连拍，每张都绑定到这次分割（批次 + 作业），
         所以「换一个批次 = 换一组现场」。照片不参与业务判定，也不上链。 -->
    <el-card class="workshop-monitor-card">
      <template #header>
        <div class="monitor-card-header">
          <span>分割车间监控</span>
          <el-tag v-if="currentBatchNo" type="info" effect="plain" size="small">{{ currentBatchNo }}</el-tag>
          <div class="monitor-actions">
            <el-tooltip
              content="分割线扫码称重台上报之后，工位相机自动连拍现场照片并逐张绑定到这次分割；照片只作现场留痕，不参与任何业务判定，也不上链"
              placement="top"
            >
              <el-tag type="info" effect="plain" size="small">设备自动采集</el-tag>
            </el-tooltip>
            <span class="monitor-refresh-hint">每 10 秒自动刷新</span>
            <el-button link type="primary" size="small" :loading="snapshotLoading" @click="loadSnapshots(true)">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </div>
      </template>
      <div v-if="snapshotLoading && !snapshots.length" class="monitor-empty">加载中…</div>
      <div v-else-if="!snapshots.length" class="monitor-empty">
        <div class="hint-title">该批次暂无现场照片</div>
        <div class="hint-text">
          照片在<b>分割开始之后</b>由工位相机自动拍下：分割线扫码称重台把一头白条上报进来，
          相机随即连拍几张并逐张绑定到这次分割。接入层会核对每张照片真实存在。
          想看别的批次，换个批次即可。
        </div>
      </div>
      <div v-else class="monitor-grid">
        <div v-for="(s, idx) in snapshots" :key="s.id" class="monitor-item">
          <el-image
            class="monitor-photo"
            :src="snapshotUrl(s.fileId)"
            :preview-src-list="snapshotPreviewList"
            :initial-index="idx"
            fit="cover"
            preview-teleported
            hide-on-click-modal
          >
            <template #error>
              <div class="monitor-photo-error">照片不可用</div>
            </template>
          </el-image>
          <div class="monitor-caption">
            <span class="monitor-time">{{ s.capturedTime || s.createTime || '--' }}</span>
            <span v-if="s.workshop" class="monitor-workshop">{{ s.workshop }}</span>
            <span v-if="s.jobNo" class="monitor-job" :title="`这次分割的作业号（一头白条的整份产出）`">
              {{ s.jobNo }}
            </span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 批次详情抽屉 -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="480px">
      <template v-if="detailNode">
        <!-- 白条组节点：一头猪的整份产出清单。出肉率与对账结论只在后端算，页面不展示 -->
        <el-descriptions v-if="detailNode.type === 'SPLIT_JOB'" :column="1" border size="default" style="margin-bottom: 16px">
          <el-descriptions-item label="作业号">{{ detailNode.jobNo || '--' }}</el-descriptions-item>
          <el-descriptions-item label="耳标号">{{ detailNode.earTagNo || '未记录' }}</el-descriptions-item>
          <el-descriptions-item label="所属胴体批次">{{ detailNode.parentBatchNo || '--' }}</el-descriptions-item>
          <el-descriptions-item label="切出部位数">{{ detailNode.itemCount || 0 }} 个</el-descriptions-item>
          <el-descriptions-item label="各部位合计">{{ detailNode.totalWeightKg ?? '--' }} kg</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detailNode.operator || '未记录' }}</el-descriptions-item>
          <el-descriptions-item label="作业时间">{{ detailNode.splitTime || detailNode.createTime || '--' }}</el-descriptions-item>
          <el-descriptions-item label="车间">{{ detailNode.workshop || '--' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-else :column="1" border size="default" style="margin-bottom: 16px">
          <el-descriptions-item label="批次号">{{ detailNode.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ detailNode.productName || '胴体批次' }}</el-descriptions-item>
          <el-descriptions-item label="部位编码">{{ detailNode.partCode || '--' }}</el-descriptions-item>
          <el-descriptions-item label="重量">{{ detailNode.weightKg || detailNode.totalWeightKg || '--' }} kg</el-descriptions-item>
          <el-descriptions-item label="包数">{{ detailNode.packageCount || '--' }}</el-descriptions-item>
          <el-descriptions-item label="层级">{{ detailNode.splitLevel || 0 }}</el-descriptions-item>
          <el-descriptions-item label="父批次">{{ detailNode.parentBatchId ? detailNode.parentBatchId : '无（根批次）' }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detailNode.operator || '未记录' }}</el-descriptions-item>
          <el-descriptions-item label="采集设备">
            <el-tag :type="sourceTag(detailNode.source)" size="small" effect="plain">
              {{ deviceText(detailNode) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ detailNode.splitTime || detailNode.createTime || '--' }}</el-descriptions-item>
          <el-descriptions-item label="车间">{{ detailNode.workshop || '--' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 哈希信息面板 -->
        <BatchHashPanel :node="detailNode" />

        <!-- 区块链状态 -->
        <div class="drawer-chain-section">
          <div class="section-label">区块链状态</div>
          <BlockchainVerifyBadge :status="detailNode.txHash ? 'confirmed' : 'pending'" :tx-hash="detailNode.txHash" />
          <div style="margin-top: 12px">
            <el-button type="primary" size="small" @click="showChainInfo(detailNode)">查看交易详情</el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 新建/编辑分割弹窗已删除：分割批次为设备独占环节，人工创建入口不复存在；
         说明性字段的修改与作废入口保留在详情与列表操作中 -->
  </div>
</template>

<script setup lang="ts">
import { ref, computed, h, defineComponent, onMounted, onBeforeUnmount, provide, inject, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'
import { deviceText, sourceLabel, sourceTag } from '@/api/modules/ingest'
import { distributionApi } from '@/api/modules/distribution'
import { normalizeSplitTree, isPseudoNode, splitNodeTitle, splitNodeNo, splitNodeWeight, type SplitNode } from '@/utils/splitTree'
import request from '@/utils/request'

// 分割批次为「设备独占」环节：分割线扫码称重台自动创建，人工新建入口已删除（前端+后端）

// ========== TreeNode 组件 (内联递归) ==========
const TreeNode = defineComponent({
  name: 'TreeNode',
  props: { node: Object, highlighted: Boolean, selectedKey: { type: String, default: '' } },
  emits: ['view-detail', 'chain-info', 'select'],
  setup(props, { emit }) {
    const isExpanded = ref(true)
    const globalExpand = inject<any>('splitTreeExpand', null)
    watch(globalExpand, (val: boolean) => {
      if (val != null) isExpanded.value = val
    })
    const node = computed(() => props.node as any)
    const hasChildren = computed(() => node.value.children && node.value.children.length > 0)
    /** 节点类型：CARCASS 根 / SPLIT_JOB 白条组 / SPLIT 实产部位 / MISSING_* 未产出对照 */
    const nodeType = computed<string>(() => node.value.type || (node.value.parentBatchId == null ? 'CARCASS' : 'SPLIT'))
    // 对照节点没有库表 id，用后端给的 key 做稳定标识
    const nodeKey = computed(() => node.value.key || `${nodeType.value}:${node.value.id}`)
    const isSelected = computed(() => !!props.selectedKey && props.selectedKey === nodeKey.value)
    /** 对照节点只是字典提示、不是发生过的业务事实：虚线灰底，且不能进详情、不能看链上 */
    const pseudo = computed(() => isPseudoNode(node.value))
    const style = computed(() => {
      if (props.highlighted) return { border: '2px solid #409eff', boxShadow: '0 0 12px rgba(64,158,255,0.3)' }
      if (isSelected.value) return { border: '2px solid #e6a23c', boxShadow: '0 0 10px rgba(230,162,60,0.35)' }
      return {}
    })
    // 标题 / 编号 / 重量统一走 utils/splitTree.ts 的页面口径（不出对账信息）
    const nodeTitle = computed(() => splitNodeTitle(node.value))
    const nodeNo = computed(() => splitNodeNo(node.value))
    const nodeWeight = computed(() => splitNodeWeight(node.value))

    return () => h('div', {
      class: ['tree-node', {
        'is-expanded': isExpanded.value, 'is-highlighted': props.highlighted,
        'is-selected': isSelected.value, 'is-pseudo': pseudo.value,
      }],
      style: style.value,
    }, [
      h('div', {
        class: 'tree-node-header',
        // 点击节点头部：展开/折叠；对照节点没有对应的业务记录，不参与选中
        onClick: () => { isExpanded.value = !isExpanded.value; if (!pseudo.value) emit('select', node.value) },
      }, [
        h('span', { class: 'node-expand-icon' }, hasChildren.value ? (isExpanded.value ? '▾' : '▸') : '·'),
        h('span', { class: 'node-icon' }, ''),
        h('span', { class: 'node-name' }, nodeTitle.value),
        nodeNo.value ? h('span', { class: 'node-batch' }, nodeNo.value) : null,
        nodeWeight.value != null ? h('span', { class: 'node-weight' }, `${nodeWeight.value} kg`) : null,
        node.value.packageCount ? h('span', { class: 'node-pkg' }, `${node.value.packageCount}包`) : null,
        pseudo.value ? null : h('span', { class: 'node-actions', onClick: (e: Event) => e.stopPropagation() }, [
          h('el-button', { size: 'small', type: 'primary', link: true, onClick: () => emit('view-detail', node.value) }, { default: () => '详情' }),
          h('el-button', { size: 'small', type: 'info', link: true, onClick: () => emit('chain-info', node.value) }, { default: () => '链上' }),
        ]),
      ]),
      isExpanded.value && hasChildren.value
        ? h('div', { class: 'tree-node-children' },
            node.value.children.map((child: any) =>
              h(TreeNode, {
                node: child,
                highlighted: false,
                selectedKey: props.selectedKey,
                onViewDetail: (n: any) => emit('view-detail', n),
                onChainInfo: (n: any) => emit('chain-info', n),
                onSelect: (n: any) => emit('select', n),
              })
            )
          )
        : null,
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
        h('div', { class: 'section-label' }, '数据哈希'),
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
const batchOptions = ref<Array<{ batchNo: string; slaughterhouse?: string; splitCount?: number }>>([])
const route = useRoute()
const currentBatchNo = ref('')

// 通过 provide/inject 让所有 TreeNode 共享展开/折叠状态
provide('splitTreeExpand', allExpanded)

// 树节点：胴体批次 → 每头白条一组（组内分已出部位与未产出标准部位）。
// 形状与口径由 utils/splitTree.ts 决定，此处只渲染。
const splitTree = ref<SplitNode | null>(null)
const nodeCount = computed(() => countNodes(splitTree.value))
/** 当前批次有没有分割记录（没有就说明还没上分割线，页面给引导而不是干瞪着一个根节点） */
const hasSplitData = computed(() => (splitTree.value?.children?.length || 0) > 0)

// 当前选中的树节点（用于树节点高亮）
const selectedNode = ref<SplitNode | null>(null)
const selectedKey = computed(() => selectedNode.value ? nodeOptionValue(selectedNode.value) : '')
function handleSelectNode(node: SplitNode) {
  selectedNode.value = node
}

// 详情抽屉
const drawerVisible = ref(false)
const detailNode = ref<SplitNode | null>(null)

/* 新建/编辑分割弹窗及其全部状态已删除：
   分割批次为设备独占环节，人工创建入口（按钮 + 树节点「分割」）一并移除，
   结论字段的更正走「作废 + 重录」，设备写入异常走「设备接入 → 待人工处理」实名复核。 */

// 节点类型：后端树中根节点为 CARCASS，其余为 SPLIT
function nodeType(n: SplitNode): string {
  return n.type || (n.parentBatchId == null ? 'CARCASS' : 'SPLIT')
}
function nodeOptionValue(n: SplitNode): string {
  return `${nodeType(n)}:${n.id}`
}

// ========== 操作 ==========
function showDetail(node: SplitNode) {
  detailNode.value = node
  // 各类节点的「身份」不同：白条组是作业号，部位节点是部位号，根节点才是批次号
  if (node.type === 'SPLIT_JOB') drawerTitle.value = `白条 · ${node.earTagNo || node.jobNo || ''}`
  else if (node.parentBatchId == null) drawerTitle.value = `胴体批次 — ${node.batchNo || ''}`
  else drawerTitle.value = `分割部位 — ${node.batchNo || ''}`
  drawerVisible.value = true
}

function showChainInfo(node: SplitNode) {
  if (node.txHash) {
    ElMessage.success(`链上已确认\n交易哈希: ${node.txHash}\n点击可跳转区块链浏览器查看详情`)
  } else {
    ElMessage.warning('该批次尚未上链，请在创建批次时触发上链')
  }
}

// 搜索框输入时：下拉列出匹配的胴体批次（不输入则列出全部）
function queryBatchSuggestions(query: string, cb: (items: any[]) => void) {
  const q = (query || '').trim().toUpperCase()
  const list = q
    ? batchOptions.value.filter((b) => b.batchNo.toUpperCase().includes(q))
    : batchOptions.value
  cb(list)
}

// 点击下拉建议：切换并加载对应批次的拆分树
async function handleSelectBatch(item: { batchNo: string }) {
  currentBatchNo.value = item.batchNo
  await loadSingleTree(item.batchNo)
  highlightedBatch.value = item.batchNo
}

// 搜索按钮/回车：精确优先、包含兜底，找不到时提示
async function highlightBatch() {
  const keyword = batchSearch.value.trim()
  if (!keyword) return
  const q = keyword.toUpperCase()
  const match = batchOptions.value.find((b) => b.batchNo.toUpperCase() === q)
    || batchOptions.value.find((b) => b.batchNo.toUpperCase().includes(q))
  if (!match) {
    ElMessage.warning(`未找到批次: ${keyword}`)
    return
  }
  currentBatchNo.value = match.batchNo
  await loadSingleTree(match.batchNo)
  highlightedBatch.value = match.batchNo
  ElMessage.success(`已定位到批次: ${match.batchNo}`)
}

function clearHighlight() { highlightedBatch.value = '' }

function handleExpandAll() {
  allExpanded.value = !allExpanded.value
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
async function loadTree() {
  selectedNode.value = null // 树重新加载后原选中对象已过期
  try {
    // 拉取所有胴体批次作为可选项（splitCount = 该批次已分割条数，由后端列表接口顺带返回）
    const batches: any = await distributionApi.getBatches({ current: 1, size: 100 })
    const records = batches?.records || batches?.list || []
    batchOptions.value = records.map((r: any) => ({
      batchNo: r.batchNo,
      slaughterhouse: r.slaughterhouse,
      splitCount: Number(r.splitCount ?? 0),
    }))

    if (records.length === 0) {
      splitTree.value = null
      return
    }

    // 默认落点：路由指定的批次 > 最近一条「真的有分割数据」的批次 > 最新批次。
    // 直接落在最新批次往往会选中刚归集、还没上分割线的批次，拆分树只剩一个根节点，页面等于空的。
    const queryBatchNo = route.query.batchNo as string
    const latestWithSplit = records.find((r: any) => Number(r.splitCount ?? 0) > 0)
    if (!currentBatchNo.value || !records.some((r: any) => r.batchNo === currentBatchNo.value)) {
      currentBatchNo.value = (queryBatchNo && records.some((r: any) => r.batchNo === queryBatchNo))
        ? queryBatchNo
        : (latestWithSplit?.batchNo || records[0].batchNo)
    }

    const tree: any = await distributionApi.getSplitTree(currentBatchNo.value)
    splitTree.value = normalizeSplitTree(tree)
  } catch {
    splitTree.value = null
  }
  // 默认落点确定之后才取监控照片：照片按批次过滤，先有 currentBatchNo 才有得查
  loadSnapshots()
}

/** 空态引导：直接跳到最近一条有分割数据的批次 */
async function goLatestSplitBatch() {
  const target = batchOptions.value.find((b) => (b.splitCount || 0) > 0)
  if (!target) {
    ElMessage.warning('当前还没有任何分割记录：分割批次由分割线扫码称重台自动产生，设备上报后才会出现')
    return
  }
  currentBatchNo.value = target.batchNo
  await loadSingleTree(target.batchNo)
}

function handleBatchChange(batchNo: string) {
  currentBatchNo.value = batchNo
  loadSingleTree(batchNo)
}

async function loadSingleTree(batchNo: string) {
  selectedNode.value = null
  try {
    const tree: any = await distributionApi.getSplitTree(batchNo)
    splitTree.value = normalizeSplitTree(tree)
    // 切换批次后重置展开状态为全部展开
    allExpanded.value = true
    highlightedBatch.value = ''
  } catch {
    splitTree.value = null
  }
  // 监控照片跟批次绑定：换了批次就要换成那一批次的现场照片（不然看到的还是上一个批次的现场）
  loadSnapshots()
}

/**
 * 批次树的口径（分层与「不展示出肉率」）统一在 utils/splitTree.ts 里，
 * 这里只负责把接口结果交进去（回归脚本用同一份实现断言，避免两处各写一套）。
 */
onMounted(() => { loadTree(); startSnapshotPolling() })
onBeforeUnmount(stopSnapshotPolling)

// ========== 分割车间监控快照 ==========
// 相机在**分割线一开切**的时候开拍（分割线扫码称重台上报之后自动触发），
// 每张照片都绑定到这次分割的批次 —— 所以这里按**当前选中的批次**取，
// 而不是取「这台相机最近若干张」：后者会把上一次分割的照片当成这一次的现场。
// 照片本身不参与业务判定，页面读的是只读接口。
/** 页面最多展示的张数。相机保留 20 张，一个批次一次分割连拍 3 张，10 张够看最近几次。 */
const SNAPSHOT_DISPLAY_LIMIT = 10
const snapshots = ref<any[]>([])
const snapshotLoading = ref(false)
let snapshotTimer: ReturnType<typeof setInterval> | null = null

/** 文件服务预览地址（网关对 /api/v1/file/ 公开，图片可直接作为 src 用，无需带 token） */
function snapshotUrl(fileId: string) {
  return `/api/v1/file/${fileId}`
}
const snapshotPreviewList = computed(() => snapshots.value.map((s) => snapshotUrl(s.fileId)))

async function loadSnapshots(manual = false) {
  // 没有选中批次就不查：接口不带 batchNo 会退化成「相机最近 N 张」，与页面口径矛盾
  if (!currentBatchNo.value) {
    snapshots.value = []
    return
  }
  snapshotLoading.value = true
  try {
    const list: any = await distributionApi.getSplitSnapshots({
      batchNo: currentBatchNo.value,
      limit: SNAPSHOT_DISPLAY_LIMIT,
    })
    snapshots.value = Array.isArray(list) ? list : []
    if (manual && !snapshots.value.length) {
      ElMessage.info('该批次还没有现场照片：分割线扫码称重台上报之后，工位相机会自动连拍并绑定到这次分割')
    }
  } catch {
    if (manual) ElMessage.error('车间监控照片加载失败')
  } finally {
    snapshotLoading.value = false
  }
}

/**
 * 轮询刷新：全系统没有 WebSocket / SSE，「实时」只能靠定时查询。
 * 10 秒一次、标签页隐藏时跳过，避免后台空跑 —— 分割是分钟级的动作，
 * 演示时人从设备接入页切回来，基本能立刻看到刚拍的那几张。
 */
function startSnapshotPolling() {
  stopSnapshotPolling()
  snapshotTimer = setInterval(() => {
    if (document.visibilityState === 'visible') loadSnapshots()
  }, 10000)
}

function stopSnapshotPolling() {
  if (snapshotTimer) {
    clearInterval(snapshotTimer)
    snapshotTimer = null
  }
}
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }

// 工具栏
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.toolbar-left { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.toolbar-right { display: flex; gap: 8px; align-items: center; }
.select-hint { font-size: 12px; color: #909399; white-space: nowrap; }
.select-hint.selected { color: #e6a23c; }
.page-title { font-size: 18px; font-weight: 700; margin: 0; }

// 树卡片
.tree-card { margin-bottom: 0; }
.tree-card-header { display: flex; align-items: center; justify-content: space-between; }
.tree-wrapper { padding: 16px 0; }
.tree-root { margin-bottom: 24px; }
.tree-children { padding-left: 40px; }

// 空态引导（批次还没上分割线时）
.tree-empty-hint {
  padding: 12px 14px; border-radius: 8px;
  background: #f5f7fa; border: 1px dashed #dcdfe6;
}
.hint-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 4px; }
.hint-text { font-size: 12px; color: #909399; line-height: 1.6; margin-bottom: 6px; }

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

// 车间监控（批次树下方）
.workshop-monitor-card { margin-top: 16px; }
.monitor-card-header { display: flex; align-items: center; gap: 10px; }
/* 标题 + 批次标签在左，工具区推到最右（标题旁边多了一个批次标签，不能再用 space-between 均分） */
.monitor-card-header > .monitor-actions { margin-left: auto; }
.monitor-actions { display: flex; align-items: center; gap: 12px; }
.monitor-refresh-hint { font-size: 12px; color: #909399; }
.monitor-empty {
  padding: 16px 14px; border-radius: 8px;
  background: #f5f7fa; border: 1px dashed #dcdfe6;
  font-size: 12px; color: #909399;
}
.monitor-grid {
  display: grid; gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
}
.monitor-item {
  border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden; background: #fff;
  transition: box-shadow 0.2s;
  &:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08); }
}
.monitor-photo { display: block; width: 100%; height: 110px; }
.monitor-photo-error {
  display: flex; align-items: center; justify-content: center;
  width: 100%; height: 110px; background: #f5f7fa; color: #c0c4cc; font-size: 12px;
}
.monitor-caption {
  display: flex; flex-direction: column; gap: 2px; padding: 6px 8px;
  font-size: 12px; line-height: 1.4;
}
.monitor-time { color: #303133; font-family: 'Courier New', monospace; }
.monitor-workshop { color: #909399; }
/* 作业号：一眼看出这几张属于哪一次分割（一头白条） */
.monitor-job { color: #409eff; font-family: 'Courier New', monospace; }

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
// 对照节点（未产出标准部位）：虚线 + 灰底，一眼区分「提示」与「事实」
:deep(.tree-node.is-pseudo) { border-style: dashed; background: #fafafa; }
:deep(.tree-node.is-pseudo .node-name) { color: #909399; font-weight: 400; }
:deep(.tree-node.is-pseudo .node-batch) { background: #f0f2f5; }
:deep(.node-actions) { display: flex; gap: 4px; opacity: 0; transition: opacity 0.2s; }
:deep(.tree-node-header):hover :deep(.node-actions) { opacity: 1; }
:deep(.tree-node-children) { padding: 4px 0 4px 40px; border-top: 1px solid #f5f7fa; }

// 抽屉
.drawer-chain-section { margin-top: 16px; padding-top: 16px; border-top: 1px solid #ebeef5; }
.section-label { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.hash-code { font-family: 'Courier New', monospace; font-size: 12px; background: #f5f7fa; padding: 2px 6px; border-radius: 3px; color: #67c23a; word-break: break-all; }

// 哈希面板
:deep(.hash-panel) { margin-bottom: 16px; padding: 12px; background: #f5f7fa; border-radius: 6px; }

@keyframes highlightPulse {
  0%, 100% { box-shadow: 0 0 12px rgba(64,158,255,0.3); }
  50% { box-shadow: 0 0 24px rgba(64,158,255,0.6); }
}
</style>
