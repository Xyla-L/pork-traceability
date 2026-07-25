<template>
  <div class="pig-detail">
    <!-- ========== 加载骨架屏 ========== -->
    <template v-if="loading">
      <el-skeleton :rows="8" animated />
    </template>

    <!-- ========== 详情内容 ========== -->
    <template v-else-if="pig">
      <!-- 头部：耳标号 + 状态 + 区块链徽章 -->
      <div class="detail-header">
        <div class="header-left">
          <el-button text :icon="ArrowLeft" @click="goBack">返回列表</el-button>
          <h2 class="pig-title">
            {{ pig.earTagNo }}
            <el-tag :type="pigStatusType" size="large" class="status-tag">{{ pigStatusLabel }}</el-tag>
          </h2>
        </div>
        <BlockchainVerifyBadge :status="blockchainStatus" :tx-hash="latestTxHash" />
      </div>

      <!-- ========== 基本信息卡片 ========== -->
      <el-card class="section-card">
        <template #header><span class="section-title">📋 基本信息</span></template>
        <el-descriptions :column="3" border size="default">
          <el-descriptions-item label="耳标号">{{ pig.earTagNo }}</el-descriptions-item>
          <el-descriptions-item label="品种">{{ pig.breed || '--' }}</el-descriptions-item>
          <el-descriptions-item label="性别">--</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ pig.birthDate || '--' }}</el-descriptions-item>
          <el-descriptions-item label="圈舍编号">{{ pig.penNo || '--' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ pig.source || '--' }}</el-descriptions-item>
          <el-descriptions-item label="养殖场">{{ pig.farmName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="入栏时间">--</el-descriptions-item>
          <el-descriptions-item label="当前体重(kg)">{{ mockWeight }} kg</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- ========== 生命周期时间轴 ========== -->
      <el-card class="section-card">
        <template #header><span class="section-title">🕐 生命周期</span></template>
        <div class="timeline-wrap">
          <div
            v-for="(node, idx) in timelineNodes"
            :key="idx"
            class="timeline-node"
            :class="{ 'is-active': node.active, 'is-done': node.done, 'is-pending': !node.done && !node.active }"
          >
            <!-- 时间轴连线 -->
            <div class="timeline-line">
              <div class="timeline-dot">
                <el-icon v-if="node.done" :size="18"><CircleCheckFilled /></el-icon>
                <el-icon v-else-if="node.active" :size="18" class="is-pulsing"><Clock /></el-icon>
                <div v-else class="dot-empty"></div>
              </div>
              <div v-if="idx < timelineNodes.length - 1" class="timeline-connector" :class="{ done: node.done }"></div>
            </div>
            <!-- 节点内容 -->
            <div class="timeline-content" @click="node.expandable && toggleNode(node)">
              <div class="timeline-card" :class="{ clickable: node.expandable }">
                <div class="node-header">
                  <span class="node-title">{{ node.title }}</span>
                  <span class="node-time">{{ node.time || '--' }}</span>
                </div>
                <div class="node-desc">{{ node.desc }}</div>
                <!-- 展开详情 -->
                <el-collapse-transition>
                  <div v-if="node.expanded && node.detail" class="node-detail">
                    <el-divider style="margin: 12px 0" />
                    <el-descriptions :column="2" size="small" border>
                      <el-descriptions-item
                        v-for="item in node.detail"
                        :key="item.label"
                        :label="item.label"
                      >{{ item.value }}</el-descriptions-item>
                    </el-descriptions>
                  </div>
                </el-collapse-transition>
                <!-- 区块链存证标记 -->
                <div v-if="node.txHash" class="node-blockchain">
                  <BlockchainVerifyBadge status="confirmed" :tx-hash="node.txHash" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- ========== 疫苗记录 ========== -->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span class="section-title">💉 疫苗注射记录</span>
            <el-tag v-if="vaccines.length === 0" type="info" size="small">暂无记录</el-tag>
          </div>
        </template>
        <template v-if="vaccines.length > 0">
          <div class="vaccine-list">
            <el-card
              v-for="v in vaccines"
              :key="v.id"
              class="vaccine-card-item"
              shadow="hover"
            >
              <div class="vaccine-info">
                <div class="vaccine-header">
                  <span class="vaccine-name">{{ v.vaccineName }}</span>
                  <span class="vaccine-batch">批次: {{ v.batchNo }}</span>
                </div>
                <div class="vaccine-meta">
                  <span>注射时间: {{ v.injectTime }}</span>
                  <span>剂量: {{ v.dosage || '--' }}</span>
                  <span>操作人: {{ v.operator }}</span>
                </div>
              </div>
              <BlockchainVerifyBadge :status="vaccineChainStatus(v)" />
            </el-card>
          </div>
        </template>
        <el-empty v-else description="暂无疫苗注射记录" :image-size="80" />
      </el-card>

      <!-- ========== 产地检疫证明 ========== -->
      <el-card class="section-card">
        <template #header>
          <div class="section-header">
            <span class="section-title">📜 产地检疫证明</span>
            <CaSignatureBadge v-if="quarantineCert" :verified="caVerified" :inspector="quarantineCert.inspector" />
          </div>
        </template>
        <template v-if="quarantineCert">
          <el-descriptions :column="3" border size="default">
            <el-descriptions-item label="检疫证编号">{{ quarantineCert.certNo }}</el-descriptions-item>
            <el-descriptions-item label="签发机构">{{ quarantineCert.issueOrg }}</el-descriptions-item>
            <el-descriptions-item label="签发日期">{{ quarantineCert.issueTime }}</el-descriptions-item>
            <el-descriptions-item label="有效期至">{{ quarantineCert.validUntil || '--' }}</el-descriptions-item>
            <el-descriptions-item label="官方兽医">{{ quarantineCert.inspector }}</el-descriptions-item>
            <el-descriptions-item label="内容哈希">
              <code class="hash-code">{{ quarantineCert.contentHash?.substring(0, 16) }}...</code>
            </el-descriptions-item>
          </el-descriptions>
          <div class="section-footer">
            <el-space>
              <el-button type="primary" link :icon="Link" @click="verifyOnChain('quarantine')">链上验真</el-button>
              <BlockchainVerifyBadge :status="caChainStatus" :tx-hash="quarantineTxHash" />
            </el-space>
          </div>
        </template>
        <el-empty v-else description="暂无检疫证明" :image-size="80" />
      </el-card>

      <!-- ========== 屠宰信息摘要 ========== -->
      <el-card class="section-card">
        <template #header><span class="section-title">🔪 屠宰信息</span></template>
        <template v-if="slaughterInfo">
          <el-descriptions :column="3" border size="default">
            <el-descriptions-item label="屠宰场">{{ slaughterInfo.slaughterhouse }}</el-descriptions-item>
            <el-descriptions-item label="入场时间">{{ slaughterInfo.entryTime }}</el-descriptions-item>
            <el-descriptions-item label="入场健康检查">{{ slaughterInfo.healthOk ? '✅ 通过' : '⚠️ 异常' }}</el-descriptions-item>
            <el-descriptions-item label="宰前检验">{{ slaughterInfo.preInspectOk ? '✅ 合格' : '❌ 不合格' }}</el-descriptions-item>
            <el-descriptions-item label="宰后检验">{{ slaughterInfo.postInspectOk ? '✅ 合格' : '❌ 不合格' }}</el-descriptions-item>
            <el-descriptions-item label="瘦肉精检测">{{ slaughterInfo.ractopamineOk ? '✅ 阴性' : '🚨 阳性' }}</el-descriptions-item>
            <el-descriptions-item label="检疫盖章编号">{{ slaughterInfo.stampNo || '--' }}</el-descriptions-item>
            <el-descriptions-item label="官方兽医">{{ slaughterInfo.veterinary }}</el-descriptions-item>
            <el-descriptions-item label="盖章时间">{{ slaughterInfo.stampTime || '--' }}</el-descriptions-item>
          </el-descriptions>
          <div class="section-footer">
            <BlockchainVerifyBadge :status="slaughterChainStatus" :tx-hash="slaughterTxHash" />
          </div>
        </template>
        <el-empty v-else description="暂无屠宰记录" :image-size="80" />
      </el-card>
    </template>

    <!-- ========== 未找到 ========== -->
    <el-empty v-else description="未找到该生猪信息" :image-size="120">
      <el-button type="primary" @click="goBack">返回列表</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CircleCheckFilled, Clock, Link } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'
import { usePigStore } from '@/stores/pig'
import type { PigIndividual, VaccineRecord, QuarantineCert } from '@/types/pig'

// ========== 屠宰摘要类型 ==========
interface SlaughterSummary {
  slaughterhouse: string
  entryTime: string
  healthOk: boolean
  preInspectOk: boolean
  postInspectOk: boolean
  ractopamineOk: boolean
  stampNo: string
  veterinary: string
  stampTime: string
}

const route = useRoute()
const router = useRouter()
const pigStore = usePigStore()

// ==================== CA 签名验证徽章 (内联组件) ====================
import { h, defineComponent } from 'vue'
const CaSignatureBadge = defineComponent({
  name: 'CaSignatureBadge',
  props: { verified: Boolean, inspector: String },
  setup(props) {
    return () => h('span', {
      class: ['ca-badge', props.verified ? 'ca-verified' : 'ca-invalid'],
    }, [
      h('span', { class: 'ca-icon' }, props.verified ? '🛡️' : '⚠️'),
      h('span', { class: 'ca-text' }, props.verified ? 'CA签名已验证' : 'CA签名验证失败'),
      props.inspector ? h('span', { class: 'ca-inspector' }, `兽医: ${props.inspector}`) : null,
    ])
  },
})

// ==================== 状态变量 ====================
const loading = ref(true)
const pig = ref<PigIndividual | null>(null)
const vaccines = ref<VaccineRecord[]>([])
const quarantineCert = ref<QuarantineCert | null>(null)
const blockchainStatus = ref<'confirmed' | 'pending' | 'none'>('confirmed')
const latestTxHash = ref('')

// 屠宰信息（模拟）
const slaughterInfo = ref<SlaughterSummary | null>({
  slaughterhouse: 'XX市定点屠宰场',
  entryTime: '2024-07-02 06:30',
  healthOk: true,
  preInspectOk: true,
  postInspectOk: true,
  ractopamineOk: true,
  stampNo: 'ST2024070200123',
  veterinary: '官方兽医-王建国',
  stampTime: '2024-07-02 14:30',
})

const mockWeight = ref(118.5)

// ==================== 状态映射 ====================
const pigStatusType = computed(() => {
  const map: Record<number, 'success' | 'primary' | 'danger' | 'info' | undefined> = { 1: 'success', 2: 'primary', 3: undefined, 4: 'danger' }
  return pig.value ? (map[pig.value.status] || 'info') : 'info'
})

const pigStatusLabel = computed(() => {
  const map: Record<number, string> = { 1: '在养', 2: '已出栏', 3: '已屠宰', 4: '异常' }
  return pig.value ? map[pig.value.status] || '未知' : '未知'
})

// ==================== CA 签名验证状态 ====================
const caVerified = computed(() => !!quarantineCert.value?.caSignature)
const caChainStatus = ref<'confirmed' | 'pending' | 'none'>('confirmed')
const quarantineTxHash = ref('0x7a3b8c2d1e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b')
const slaughterChainStatus = ref<'confirmed' | 'pending' | 'none'>('confirmed')
const slaughterTxHash = ref('0x8c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d')

// ==================== 生命周期时间轴 ====================
interface TimelineNode {
  title: string
  time: string
  desc: string
  active: boolean
  done: boolean
  expandable: boolean
  expanded: boolean
  txHash?: string
  detail?: { label: string; value: string }[]
}

const timelineNodes = ref<TimelineNode[]>([
  {
    title: '🐷 生猪入栏', time: '2024-03-15', desc: '耳标绑定、入栏XX养殖合作社A-03圈舍',
    active: false, done: true, expandable: true, expanded: false,
    txHash: '0x1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b',
    detail: [
      { label: '耳标号', value: 'ET20240601001' },
      { label: '入栏体重', value: '25.3 kg' },
      { label: '品种', value: '长白猪' },
      { label: '来源', value: '自繁' },
    ],
  },
  {
    title: '💉 疫苗注射', time: '2024-06-05', desc: '猪瘟活疫苗 — 批次 CSF-20240601',
    active: false, done: true, expandable: true, expanded: false,
    detail: [
      { label: '疫苗名称', value: '猪瘟活疫苗' },
      { label: '批次号', value: 'CSF-20240601' },
      { label: '剂量', value: '2ml/头' },
      { label: '操作人', value: '养殖员-张三' },
    ],
  },
  {
    title: '💉 二次免疫', time: '2024-06-20', desc: '口蹄疫O型灭活疫苗 — 批次 FMD-20240615',
    active: false, done: true, expandable: true, expanded: false,
    detail: [
      { label: '疫苗名称', value: '口蹄疫O型灭活疫苗' },
      { label: '批次号', value: 'FMD-20240615' },
      { label: '剂量', value: '2ml/头' },
      { label: '操作人', value: '养殖员-张三' },
    ],
  },
  {
    title: '📋 出栏申报', time: '2024-07-01 08:30', desc: '申报出栏 — 目标屠宰场: XX市定点屠宰场',
    active: false, done: true, expandable: true, expanded: false,
    detail: [
      { label: '申报编号', value: 'SA20240701001' },
      { label: '出栏体重', value: '118.5 kg' },
      { label: '目标屠宰场', value: 'XX市定点屠宰场' },
      { label: '审批状态', value: '✅ 已通过' },
    ],
  },
  {
    title: '📜 产地检疫', time: '2024-07-01 10:30', desc: 'XX县动物卫生监督所签发产地检疫证明',
    active: false, done: true, expandable: true, expanded: false,
    txHash: '0x7a3b8c2d1e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b',
    detail: [
      { label: '检疫证编号', value: 'QC2024070100123' },
      { label: '签发机构', value: 'XX县动物卫生监督所' },
      { label: '官方兽医', value: '李建国' },
      { label: 'CA签名', value: '✅ 已验证' },
    ],
  },
  {
    title: '🚛 入场查验', time: '2024-07-02 06:30', desc: '到达XX市定点屠宰场，临床健康检查通过',
    active: false, done: true, expandable: true, expanded: false,
    detail: [
      { label: '到厂时间', value: '2024-07-02 06:30' },
      { label: '运输车牌', value: '京A·12345' },
      { label: '健康检查', value: '✅ 通过' },
      { label: '检疫证核验', value: '✅ 通过' },
    ],
  },
  {
    title: '🔪 屠宰检验', time: '2024-07-02 10:00', desc: '宰前检验合格 → 宰后脏器检查合格 → 瘦肉精阴性',
    active: false, done: true, expandable: true, expanded: false,
    txHash: '0x8c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d',
    detail: [
      { label: '宰前检验', value: '✅ 合格' },
      { label: '宰后检验', value: '✅ 合格（心/肝/肺/肾/淋巴结均正常）' },
      { label: '瘦肉精检测', value: '✅ 阴性（胶体金法）' },
      { label: '官方兽医', value: '王建国' },
    ],
  },
  {
    title: '✒️ 检疫盖章', time: '2024-07-02 14:30', desc: '加盖检疫合格印章 ST2024070200123，胴体流入分割环节',
    active: false, done: true, expandable: false, expanded: false,
    txHash: '0x8c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d',
  },
  {
    title: '🔪 分割加工', time: '2024-07-03 08:00', desc: '进入分割车间，胴体 → 前腿肉/后腿肉/五花肉/排骨',
    active: true, done: false, expandable: false, expanded: false,
  },
  {
    title: '🚛 冷链配送', time: '--', desc: '待开始 —— 冷链运输至销售门店',
    active: false, done: false, expandable: false, expanded: false,
  },
  {
    title: '🛒 零售上架', time: '--', desc: '待开始 —— 到达门店，扫码激活销售',
    active: false, done: false, expandable: false, expanded: false,
  },
])

function toggleNode(node: TimelineNode) {
  if (node.expandable) {
    node.expanded = !node.expanded
  }
}

// ==================== 区块链状态 ====================
function vaccineChainStatus(v: VaccineRecord): 'confirmed' | 'pending' | 'none' {
  return v.id % 3 === 0 ? 'pending' : v.id % 2 === 0 ? 'confirmed' : 'none'
}

function verifyOnChain(source: string) {
  ElMessage.success(`${source === 'quarantine' ? '检疫证' : '记录'}链上验真通过 ✅`)
}

// ==================== 返回列表 ====================
function goBack() {
  router.push('/admin/farm/pigs')
}

// ==================== 数据加载 ====================
onMounted(async () => {
  const id = Number(route.params.id)
  if (!id) {
    loading.value = false
    return
  }

  try {
    // 尝试从后端加载
    await pigStore.fetchPigDetail(id)
    await pigStore.fetchVaccines(id)
    await pigStore.fetchQuarantineCert(id)

    pig.value = pigStore.currentPig
    vaccines.value = pigStore.vaccineList
    quarantineCert.value = pigStore.quarantineCert
  } catch {
    // 后端不可用时使用模拟数据
    pig.value = {
      id,
      earTagNo: `ET2024060100${id}`,
      farmId: 1,
      farmName: 'XX市XX养殖专业合作社',
      breed: '长白猪',
      birthDate: '2024-03-15',
      penNo: 'A-03',
      source: '自繁',
      status: 1,
      createTime: '2024-03-15 10:00:00',
      updateTime: '2024-07-15 08:00:00',
    }

    vaccines.value = [
      { id: 1, pigId: id, vaccineName: '猪瘟活疫苗', batchNo: 'CSF-20240601', injectTime: '2024-06-05 09:30', dosage: '2ml/头', operator: '养殖员-张三', fileIds: [], createTime: '2024-06-05' },
      { id: 2, pigId: id, vaccineName: '口蹄疫O型灭活疫苗', batchNo: 'FMD-20240615', injectTime: '2024-06-20 10:00', dosage: '2ml/头', operator: '养殖员-张三', fileIds: [], createTime: '2024-06-20' },
    ]
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.pig-detail {
  padding: 20px;
  max-width: 1200px;
}

// ========== 头部 ==========
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pig-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-tag {
  font-size: 13px;
}

// ========== 通用卡片 ==========
.section-card {
  margin-bottom: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-footer {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.hash-code {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  color: #909399;
}

// ========== 生命周期时间轴 ==========
.timeline-wrap {
  padding: 10px 0;
}

.timeline-node {
  display: flex;
  gap: 16px;
  min-height: 80px;

  &.is-pending {
    opacity: 0.5;
  }
}

.timeline-line {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 32px;
  flex-shrink: 0;
}

.timeline-dot {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #67c23a;

  .is-pulsing {
    animation: pulse 1.5s ease-in-out infinite;
    color: #409eff;
  }

  .dot-empty {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: #dcdfe6;
  }
}

.timeline-connector {
  width: 2px;
  flex: 1;
  min-height: 40px;
  background: #e4e7ed;

  &.done {
    background: #67c23a;
  }
}

.timeline-content {
  flex: 1;
  padding-bottom: 24px;
}

.timeline-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 16px;
  transition: all 0.3s;

  &.clickable {
    cursor: pointer;

    &:hover {
      border-color: #409eff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
    }
  }
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.node-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.node-time {
  font-size: 12px;
  color: #909399;
}

.node-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.node-blockchain {
  margin-top: 8px;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

// ========== 疫苗列表 ==========
.vaccine-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.vaccine-card-item {
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 14px 18px;
  }
}

.vaccine-info {
  flex: 1;
}

.vaccine-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.vaccine-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.vaccine-batch {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 1px 8px;
  border-radius: 3px;
}

.vaccine-meta {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: #909399;
}

// ========== CA 签名徽章 ==========
:deep(.ca-badge) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;

  &.ca-verified {
    background: #f0f9eb;
    color: #67c23a;
    border: 1px solid #e1f3d8;
  }

  &.ca-invalid {
    background: #fef0f0;
    color: #f56c6c;
    border: 1px solid #fde2e2;
  }

  .ca-icon { font-size: 14px; }
  .ca-inspector {
    color: #909399;
    font-weight: 400;
    margin-left: 4px;
  }
}
</style>
