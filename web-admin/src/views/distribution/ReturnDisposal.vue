<template>
  <div class="page-container">
    <!-- ========== 处理运输 / 召回收运（冷链中心视角） ==========
         2026-11-04 命名：这一页叫「处理运输」（原拒收回运），只做一趟——冷链中心 → 无害化处理中心。
         2026-11-04 追加召回收运：召回的货还在门店，要走「冷链中心 → 门店… → 无害化处理中心」，
         两种货都进「处理计划」池，排队方式不同（拒收货等装车扫码，召回货等排车上门店收），
         下面的「处理运单」把回运（DISPOSAL）与收运（RECALL）一起列出来。 -->
    <el-card class="pool-card">
      <div class="list-header">
        <span class="card-title">处理计划</span>
        <div class="toolbar">
          <el-input v-model="poolKeyword" placeholder="门店/单号/批次" clearable style="width: 190px"
            :prefix-icon="Search" />
          <el-select v-model="poolStatus" placeholder="全部来源" clearable style="width: 120px">
            <el-option label="拒收货" value="REJECT" />
            <el-option label="召回货" value="RECALL" />
          </el-select>
          <el-button v-if="recallPool.length" type="primary" size="default" :icon="Van"
            @click="openRecallDialog">生成收运运单</el-button>
          <el-button link :icon="poolPanelVisible ? ArrowUp : ArrowDown" @click="poolPanelVisible = !poolPanelVisible">
            {{ poolPanelVisible ? '收起' : '展开' }}
          </el-button>
        </div>
      </div>

      <!-- 池子的合计就是排车的依据：这里给出总量才知道要几车、要不要分趟 -->
      <div v-show="poolPanelVisible && pool.length" class="pool-summary">
        共 <b>{{ pool.length }}</b> 批货 · 合计 <b>{{ poolWeight.toFixed(1) }}</b> kg
        <template v-if="recallPool.length">
          · 召回 <b>{{ recallPool.length }}</b> 批 <b>{{ recallWeight.toFixed(1) }}</b> kg（涉及
          <b>{{ recallStoreCount }}</b> 家门店，等冷链中心上门收）
        </template>
      </div>
      <el-table v-show="poolPanelVisible" :data="poolFiltered" v-loading="poolLoading" border stripe size="small"
        style="margin-top: 12px" empty-text="处理计划是空的：没有等待回运的拒收货，也没有等待上门收取的召回货">
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.source === 'RECALL' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.source === 'RECALL' ? '召回' : '拒收' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单号" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.transportNo || row.recallNo || '--' }}</template>
        </el-table-column>
        <el-table-column label="门店" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.storeName || '--' }}</template>
        </el-table-column>
        <!-- 双行列，对齐发货计划的「货物」列：批次为主、货重为辅 -->
        <el-table-column label="货物" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.batchNo || '--' }}</div>
            <div class="sub-text">{{ row.weightKg != null ? row.weightKg + ' kg' : '重量未知' }}</div>
          </template>
        </el-table-column>
        <!-- 拒收货已有车（装车扫码）与召回货还没车（等排车）在这里分开看 -->
        <el-table-column label="装车 / 排车" width="170">
          <template #default="{ row }">
            <template v-if="row.source === 'RECALL'">
              <span class="sub-text">等排车</span>
            </template>
            <template v-else-if="row.scanTime">
              <div>{{ row.scanPlate || '未识别车牌' }}</div>
              <div class="sub-text">{{ row.scanTime }}</div>
            </template>
            <span v-else class="sub-text">待扫码</span>
          </template>
        </el-table-column>
        <el-table-column label="理由" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.rejectReason || row.reason || '--' }}</template>
        </el-table-column>
        <el-table-column label="留痕" width="180">
          <template #default="{ row }">
            <div>{{ row.rejectBy || '召回发起' }}</div>
            <div v-if="row.rejectTime || row.createTime" class="sub-text">
              {{ row.rejectTime || row.createTime }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.source === 'RECALL'" type="warning" size="small">待上门收</el-tag>
            <el-tag v-else :type="row.scanTime ? 'success' : 'info'" size="small">
              {{ row.scanTime ? '已扫码' : '待扫码' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 生成收运运单：召回货排在一条多站运单上，冷链中心选车后由司机逐店上门收 -->
    <el-dialog v-model="recallDialogVisible" title="生成收运运单" width="480px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
        :title="`本次要收 ${recallPool.length} 批召回货、共 ${recallStoreCount} 家门店，合计 ${recallWeight.toFixed(1)} kg`" />
      <el-form label-width="88px">
        <el-form-item label="收运车辆" required>
          <el-select v-model="recallForm.vehicleNo" placeholder="选择备案车辆" style="width: 100%" filterable>
            <el-option v-for="v in vehicles" :key="v.vehicleNo" :label="`${v.vehicleNo}（${v.vehicleType || '车型未登记'}）`"
              :value="v.vehicleNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="司机">
          <el-input v-model="recallForm.driverName" placeholder="可留空，由司机在车载终端刷卡发车" />
        </el-form-item>
        <el-form-item v-if="recallForm.driverName" label="司机电话">
          <el-input v-model="recallForm.driverPhone" placeholder="选填" />
        </el-form-item>
      </el-form>
      <div class="dialog-note">
        生成后路线为「冷链中心 → 各门店 → 无害化处理中心」，一车一趟；司机到店刷卡确认到达后，
        用随车扫码枪扫该店的召回货批次标签，收完这一站才能往下一家走。
      </div>
      <template #footer>
        <el-button @click="recallDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" :disabled="!recallForm.vehicleNo"
          @click="submitRecallTransport">生成</el-button>
      </template>
    </el-dialog>

    <!-- ========== 处理运单列表 ========== -->
    <el-card class="leg-list-card">
      <div class="list-header">
        <span class="card-title">处理运单（回运 / 收运 → 无害化处理中心）</span>
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="处理运单号/车牌/司机" clearable style="width: 200px"
            :prefix-icon="Search" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="handleSearch">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table :data="legs" v-loading="legsLoading" border stripe size="small"
        highlight-current-row @row-click="selectLeg" style="margin-top: 12px">
        <el-table-column prop="transportNo" label="处理运单号" width="170" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.transportKind === 'RECALL' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.transportKind === 'RECALL' ? '召回货' : '拒收货' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="vehicleNo" label="车牌" width="100" />
        <el-table-column label="司机" width="130">
          <template #default="{ row }">
            <div>
              {{ row.driverName || '未记录' }}
              <el-tag v-if="row.driverConfirmed === 1" type="success" size="small" effect="plain" style="margin-left: 4px">已确认</el-tag>
              <el-tag v-else-if="!row.driverName && row.status === 1" type="info" size="small" effect="plain" style="margin-left: 4px">等发车刷卡</el-tag>
            </div>
            <div v-if="row.driverPhone" class="sub-text">{{ row.driverPhone }}</div>
            <div v-if="driverSourceLabel(row.driverSource)" class="sub-text">{{ driverSourceLabel(row.driverSource) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="运输路线" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.origin || '--' }}</span>
            <el-icon class="route-arrow"><Right /></el-icon>
            <span>{{ row.destination || '--' }}</span>
          </template>
        </el-table-column>
        <!-- 处理运单固定一站（无害化处理中心），「站点进度」在这里的意思是「到没到、签没签」 -->
        <el-table-column label="到达 / 签收" width="130" align="center">
          <template #default="{ row }">
            <!-- 两种处置单同一套文案（已到达 · 已签收）；召回单多站，站数放 tooltip，不混进文案 -->
            <el-tooltip v-if="stopProgressDetail(row)" :content="stopProgressDetail(row)" placement="top">
              <span class="sub-text progress-cell">{{ stopProgressLabel(row) }}</span>
            </el-tooltip>
            <span v-else class="sub-text">{{ stopProgressLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="legStatusTag(row)" size="small">{{ legStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="selectLeg(row)">运输详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next, jumper"
          :total="total" :current-page="query.pageNum" :page-size="query.pageSize"
          @current-change="onPageChange" />
      </div>
    </el-card>

    <!-- ========== 回运详情（对齐冷链运输的监控详情区） ========== -->
    <template v-if="current">
      <div ref="detailRef" class="detail-anchor"></div>

      <div class="transport-info-bar">
        <div class="info-item"><span class="info-label">处理运单号</span><span class="info-value">{{ current.transportNo }}</span></div>
        <div class="info-item"><span class="info-label">车牌号</span><span class="info-value">{{ current.vehicleNo || '--' }}</span></div>
        <div class="info-item">
          <span class="info-label">司机</span>
          <span class="info-value">
            {{ current.driverName || '未记录' }}
            <span v-if="driverSourceLabel(current.driverSource)" class="info-sub">（{{ driverSourceLabel(current.driverSource) }}）</span>
          </span>
        </div>
        <div class="info-item"><span class="info-label">电话</span><span class="info-value">{{ current.driverPhone || '--' }}</span></div>
        <div class="info-item">
          <span class="info-label">装载</span>
          <span class="info-value">
            {{ legItems.length }} {{ current.transportKind === 'RECALL' ? '批' : '站' }} · {{ loadedWeight.toFixed(1) }} kg
          </span>
        </div>
        <div class="info-item">
          <span class="info-label">{{ current.transportKind === 'RECALL' ? '到店收货' : '装车扫码' }}</span>
          <span class="info-value">
            <template v-if="current.transportKind === 'RECALL'">
              {{ collectedCount }} / {{ legItems.length }} 批
              <el-tag v-if="legItems.length && collectedCount < legItems.length" type="warning" size="small">未收齐</el-tag>
            </template>
            <template v-else-if="current.loadScanTime">
              {{ current.loadScanTime }}
            </template>
            <el-tag v-else type="info" size="small">未扫码确认</el-tag>
            <!-- 扫码核对摘要：扫到不属于本车的货码会写在这里（上错车的信号），不藏进日志 -->
            <div v-if="current.loadScanNote" class="sub-text">{{ current.loadScanNote }}</div>
          </span>
        </div>
        <div class="info-item">
          <span class="info-label">{{ current.departTime ? '实际发车' : '计划发车' }}</span>
          <span class="info-value">{{ current.departTime || current.plannedDepart || '--' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">{{ current.arriveTime ? '实际到达' : '预计到达' }}</span>
          <span class="info-value">{{ current.arriveTime || current.plannedArrive || '--' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">状态</span>
          <el-tag :type="legStatusTag(current)" size="small">{{ legStatusLabel(current) }}</el-tag>
        </div>
        <div class="info-actions">
          <el-popconfirm
            v-if="current.status === 1"
            :title="current.driverName
              ? '确认该处理运单立即发车？'
              : '该处理运单还没有司机：货要走了，责任必须落到具体的人。请先补录司机，或由司机在车载终端刷卡发车'"
            @confirm="depart(current)">
            <template #reference>
              <el-button type="warning" size="small" plain>立即发车</el-button>
            </template>
          </el-popconfirm>
          <el-button v-else-if="current.status === 2 && current.transportKind !== 'RECALL'" type="primary" size="small" plain @click="arrive(current)">
            确认到达
          </el-button>
          <span v-else-if="current.status === 2" class="action-hint">
            运输中 · 到店由车载终端刷卡确认到达，随后随车扫码枪扫召回货
          </span>
          <span v-else-if="current.status === 4" class="action-hint">
            已签收 · 这条处理链到此终结
          </span>
        </div>
      </div>

      <!-- 运输状态时间轴 -->
      <el-card class="transport-timeline-card">
        <template #header><span>运输状态时间轴</span></template>
        <div class="transport-timeline">
          <div v-for="(node, idx) in timeline" :key="idx" class="tt-node"
            :class="{ 'tt-done': node.done, 'tt-active': node.active }">
            <div class="tt-line">
              <div class="tt-dot">
                <el-icon v-if="node.done" :size="16"><CircleCheckFilled /></el-icon>
                <el-icon v-else-if="node.active" :size="16" class="is-pulsing"><Clock /></el-icon>
                <div v-else class="tt-dot-empty"></div>
              </div>
              <div v-if="idx < timeline.length - 1" class="tt-connector" :class="{ done: node.done }"></div>
            </div>
            <div class="tt-content">
              <div class="tt-title">{{ node.title }}</div>
              <div class="tt-time">{{ node.time }}</div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 装载明细：回运看「装的是哪几家门店的拒收货、为什么拒」，收运看「哪家店的哪批召回货收没收到」 -->
      <el-card class="leg-items-card">
        <template #header>
          <div class="card-header">
            <span>装载明细（{{ current.transportKind === 'RECALL' ? '召回货' : '拒收货' }}）</span>
            <span class="record-count">
              {{ legItems.length }} 条 · 合计 {{ loadedWeight.toFixed(1) }} kg ·
              {{ current.transportKind === 'RECALL'
                ? `已收货 ${collectedCount}/${legItems.length} 批`
                : `已扫码 ${scannedCount}/${legItems.length}` }}
            </span>
          </div>
        </template>
        <el-table v-if="current.transportKind === 'RECALL'" :data="legItems" border stripe size="small"
          empty-text="暂无装载明细">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="storeName" label="门店" min-width="130" show-overflow-tooltip />
          <el-table-column prop="recallNo" label="召回编号" width="150" />
          <el-table-column prop="batchNo" label="分割批次" width="150" show-overflow-tooltip />
          <el-table-column label="货重" width="90" align="center">
            <template #default="{ row }">{{ row.weightKg != null ? Number(row.weightKg).toFixed(1) + ' kg' : '未知' }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="召回原因" min-width="160" show-overflow-tooltip />
          <!-- 「待收运」是召回链最该被看见的状态：货还在店里，车已经走了，清单就对不上了 -->
          <el-table-column label="到店收货" width="150">
            <template #default="{ row }">
              <template v-if="Number(row.status) >= 2">
                <el-tag type="success" size="small">已收货</el-tag>
                <div v-if="row.collectTime" class="sub-text">{{ row.collectTime }}</div>
              </template>
              <el-tag v-else type="warning" size="small">待扫码收货</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-table v-else :data="legItems" border stripe size="small" empty-text="暂无装载明细">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="storeName" label="拒收门店" min-width="130" show-overflow-tooltip />
          <el-table-column prop="sourceTransportNo" label="原运单号" width="150" />
          <el-table-column prop="batchNo" label="分割批次" width="150" show-overflow-tooltip />
          <el-table-column label="货重" width="90" align="center">
            <template #default="{ row }">{{ row.weightKg != null ? Number(row.weightKg).toFixed(1) + ' kg' : '未知' }}</template>
          </el-table-column>
          <el-table-column label="装车扫码" width="150">
            <template #default="{ row }">
              <!-- 装车口专用扫码枪扫到这件货才有记录；没扫到不等于没装，只是缺现场证据 -->
              <template v-if="row.scanStatus === 1">
                <el-tag type="success" size="small">已核对</el-tag>
                <div v-if="row.scanTime" class="sub-text">{{ row.scanTime }}</div>
              </template>
              <el-tag v-else type="info" size="small">未扫码</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rejectReason" label="拒收理由" min-width="170" show-overflow-tooltip />
          <el-table-column label="拒收留痕" width="200">
            <template #default="{ row }">
              <div>{{ row.rejectBy || '--' }}</div>
              <div v-if="row.rejectTime" class="sub-text">{{ row.rejectTime }}</div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

    </template>

    <el-empty v-else description="请选择上方处理运单查看运输详情" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { RefreshLeft, Search, Right, CircleCheckFilled, Clock, InfoFilled, WarningFilled, ArrowUp, ArrowDown, Van } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { EpTagType } from '@/types/common'
import { distributionApi } from '@/api/modules/distribution'

// ==================== 处理计划（货等车） ====================
const pool = ref<any[]>([])
const poolLoading = ref(false)
const poolKeyword = ref('')
const poolStatus = ref<'' | 'REJECT' | 'RECALL'>('')
const poolPanelVisible = ref(true)
/** 召回池单独留一份：生成收运运单只针对召回货，且要算门店数与总重给调度看 */
const recallPool = ref<any[]>([])

const poolFiltered = computed(() => {
  const k = poolKeyword.value.trim().toLowerCase()
  return pool.value.filter((r) => {
    if (poolStatus.value && r.source !== poolStatus.value) return false
    if (!k) return true
    return [r.storeName, r.transportNo, r.recallNo, r.batchNo].some((v) => String(v || '').toLowerCase().includes(k))
  })
})
const poolWeight = computed(() => pool.value.reduce((sum, r) => sum + Number(r.weightKg || 0), 0))
const recallWeight = computed(() => recallPool.value.reduce((sum, r) => sum + Number(r.weightKg || 0), 0))
const recallStoreCount = computed(() => new Set(recallPool.value.map((r) => r.storeId)).size)

// ==================== 处理运单列表 ====================
const legs = ref<any[]>([])
const legsLoading = ref(false)
const total = ref(0)
const query = reactive({ keyword: '', status: '' as '' | number, pageNum: 1, pageSize: 10 })
const statusOptions = [
  { value: 1, label: '待发车' },
  { value: 2, label: '运输中' },
  { value: 3, label: '待签收' },
  { value: 4, label: '已签收' },
]

// ==================== 详情 ====================
const current = ref<any>(null)
const detailRef = ref<HTMLElement>()
const legItems = ref<any[]>([])

const loadedWeight = computed(() =>
  legItems.value.reduce((sum, r) => sum + Number(r.weightKg || 0), 0))
/** 已扫码核对的件数：装车口专用扫码枪扫到的，才计入（没扫到只是缺证据，不代表没装） */
const scannedCount = computed(() => legItems.value.filter((r) => Number(r.scanStatus) === 1).length)
/** 召回收运：已到店扫码收货的批次数（status≥2 才算收上车） */
const collectedCount = computed(() => legItems.value.filter((r) => Number(r.status) >= 2).length)

const legStatusTag = (row: any): EpTagType =>
  (({ 1: 'info', 2: 'warning', 3: 'primary', 4: 'success' } as Record<number, EpTagType>)[row.status] || 'info')
const legStatusLabel = (row: any) =>
  ({ 1: '待发车', 2: '运输中', 3: '待无害化中心签收', 4: '已签收' } as Record<number, string>)[row.status] || '未知'
const driverSourceLabel = (source: string | null) =>
  ({ CARD: '刷卡确认', FACE: '人脸确认', MANUAL: '人工录入' } as Record<string, string>)[source || ''] || ''

/**
 * 到达 / 签收：两种处置单同一套两点式文案（已到达 · 已签收）。
 * <p>
 * 拒收回运只有一站，到达与签收就是同一站上的两件事；召回收运是多站，但列里只讲「整单到哪一步」，
 * 站数放到 tooltip（见 {@link stopProgressDetail}）—— 同一列出现「2 站已签收」和「已到达 · 已签收」
 * 两种写法，读的人要先猜哪个更靠后。
 */
function stopProgressLabel(row: any) {
  const status = Number(row?.status || 0)
  if (status >= 4) return '已到达 · 已签收'
  if (status === 3) return '已到达 · 待签收'
  if (status === 2) {
    // 召回收运是多站：车在路上时可能已经有店收完了，说「未到达」与事实相反
    return row?.transportKind === 'RECALL' && Number(row?.arrivedStopCount || 0) > 0
      ? '在途 · 部分到站' : '在途 · 未到达'
  }
  return '未发车'
}

/** 召回单的站数明细（拒收回运只有一站，没什么可补充的） */
function stopProgressDetail(row: any) {
  if (row?.transportKind !== 'RECALL') return ''
  const stopCount = Number(row?.stopCount || 0)
  if (!stopCount) return ''
  return `共 ${stopCount} 站：已到 ${Number(row?.arrivedStopCount || 0)} 站，`
    + `已签 ${Number(row?.receivedStopCount || 0)} 站`
}

// ==================== 数据加载 ====================
async function loadPool() {
  poolLoading.value = true
  try {
    // 拦截器已解包到 data 本体：这里直接就是数组，别再 .data 一次（会永远拿到空池）
    const [poolRes, recallRes]: any[] = await Promise.all([
      distributionApi.returnPool('DISPOSAL'),
      distributionApi.getRecallPool(),
    ])
    // 拒收回运池已经把召回货一并返回（同一张处理计划、同一段链，后端按 source 区分）：
    // 前端不再自行拼接，避免同一批召回货出现两行
    const poolRows = Array.isArray(poolRes) ? poolRes : (poolRes?.data || [])
    const recallRows = Array.isArray(recallRes) ? recallRes : (recallRes?.data || [])
    recallPool.value = Array.isArray(recallRows) ? recallRows : []
    pool.value = (Array.isArray(poolRows) ? poolRows : []).map((r: any) => ({ ...r, source: r.source || 'REJECT' }))
  } finally { poolLoading.value = false }
}

// ==================== 生成收运运单（召回货） ====================
const recallDialogVisible = ref(false)
const generating = ref(false)
const vehicles = ref<any[]>([])
const recallForm = reactive({ vehicleNo: '', driverName: '', driverPhone: '' })

async function openRecallDialog() {
  recallForm.vehicleNo = ''
  recallForm.driverName = ''
  recallForm.driverPhone = ''
  recallDialogVisible.value = true
  try {
    const res: any = await distributionApi.getVehicles()
    vehicles.value = Array.isArray(res) ? res : (res?.data || [])
  } catch { vehicles.value = [] }
}

async function submitRecallTransport() {
  if (!recallForm.vehicleNo) { ElMessage.warning('请选择收运车辆'); return }
  generating.value = true
  try {
    const res: any = await distributionApi.generateRecallTransport({
      vehicleNo: recallForm.vehicleNo,
      driverName: recallForm.driverName || undefined,
      driverPhone: recallForm.driverPhone || undefined,
    })
    const no = res?.transportNo || res?.data?.transportNo
    ElMessage.success(`已生成收运运单${no ? ' ' + no : ''}：司机到店刷卡到达后扫码收货`)
    recallDialogVisible.value = false
    await Promise.all([loadPool(), loadLegs()])
  } catch { /* 拦截器已提示 */ } finally { generating.value = false }
}

async function loadLegs() {
  legsLoading.value = true
  try {
    const res: any = await distributionApi.getTransports({
      // 回运（拒收）+ 收运（召回）都属于这条处置链，一页两看
      kind: 'DISPOSAL,RECALL', pageNum: query.pageNum, pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status === '' ? undefined : query.status,
    })
    legs.value = res?.records || res?.data?.records || []
    total.value = Number(res?.total ?? res?.data?.total ?? 0)
  } finally { legsLoading.value = false }
}

function handleSearch() { query.pageNum = 1; loadLegs() }
function handleReset() {
  query.keyword = ''
  query.status = ''
  query.pageNum = 1
  loadLegs()
}
function onPageChange(page: number) { query.pageNum = page; loadLegs() }

async function loadLegItems(id: number, kind?: string) {
  try {
    // 两种运单的「装载」来源不同：回运读装车扫码的拒收货，收运读逐店扫码收的召回货
    const res: any = kind === 'RECALL'
      ? await distributionApi.getRecallItems(id)
      : await distributionApi.getTransportLegItems(id)
    const list = Array.isArray(res) ? res : (res?.data || [])
    legItems.value = Array.isArray(list) ? list : []
  } catch { legItems.value = [] }
}

async function selectLeg(row: any) {
  if (!row || current.value?.id === row.id) return
  current.value = row
  legItems.value = []
  await loadLegItems(row.id, row.transportKind)
  nextTick(() => detailRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

/** 状态推进后把选中行换成服务端最新值：列表里那一行是快照，别拿它当事实 */
async function afterStatusChange(id: number) {
  await loadLegs()
  try {
    const res: any = await distributionApi.getTransportDetail(id)
    if (res) current.value = res?.data || res
  } catch { /* 列表刷新已够用 */ }
  await loadLegItems(id, current.value?.transportKind)
}

// ==================== 推进 ====================
async function depart(row: any) {
  if (!row?.driverName) {
    ElMessage.warning('该处理运单还没有司机：货要走了，责任必须落到具体的人。请先补录司机，或由司机在车载终端刷卡发车')
    return
  }
  try {
    await distributionApi.departTransport(row.id)
    ElMessage.success(`处理运单 ${row.transportNo} 已发车`)
    await afterStatusChange(row.id)
  } catch { /* 拦截器已提示 */ }
}

async function arrive(row: any) {
  try {
    await distributionApi.arriveTransport(row.id)
    ElMessage.success('已确认到达（等待无害化处理中心签收）')
    await afterStatusChange(row.id)
  } catch { /* 拦截器已提示 */ }
}

// ==================== 时间轴 ====================
const timeline = computed(() => {
  const t = current.value
  if (!t) return []
  const status = Number(t.status || 0)
  const departTime = t.departTime || (t.plannedDepart ? `预计 ${t.plannedDepart}` : '')
  const arriveTime = t.arriveTime || (t.plannedArrive ? `预计 ${t.plannedArrive}` : '')
  const isRecall = t.transportKind === 'RECALL'
  return [
    // 拒收货在冷链中心装车，装的是扫码登记的那几件；召回货在门店装车，装的是扫码收货的那一批
    isRecall
      ? {
          title: '生成收运运单',
          time: t.createTime || '--',
          done: true, active: false,
        }
      : {
          title: '装车扫码登记',
          time: t.loadScanTime || '--',
          done: !!t.loadScanTime, active: !t.loadScanTime,
        },
    ...(isRecall ? [] : [{
      title: '生成处理运单',
      time: t.createTime || '--',
      done: true, active: false,
    }]),
    {
      title: '冷链中心发车',
      time: departTime || '--',
      done: status >= 2, active: status === 1,
    },
    {
      title: isRecall ? '逐店到店扫码收货' : '运输途中',
      time: isRecall
        ? (collectedCount.value ? `已收 ${collectedCount.value}/${legItems.value.length} 批` : departTime || '--')
        : departTime || '--',
      // 召回单能走到「全部站点到达」，前提就是每一站的货都已扫完（后端 recallArriveBlocker 拦着不许跳站），
      // 所以这里用运单状态判定即可，不再依赖装载明细是否已加载 —— 明细回来晚一步，节点不会一直灰着。
      done: status >= 3,
      active: status === 2,
    },
    {
      title: '到达无害化处理中心',
      time: arriveTime || '--',
      done: status >= 4, active: status === 3,
    },
    {
      title: '确认签收 · 链条终结',
      time: status >= 4 ? (t.arriveTime || arriveTime || '--') : '--',
      done: status >= 4, active: false,
    },
  ]
})

onMounted(() => {
  loadPool()
  loadLegs()
})
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #f5f7fa; border-radius: 4px; }
.card-title { font-size: 16px; font-weight: 700; color: #303133; }
.sub-text { font-size: 11px; color: #909399; }
.detail-anchor { scroll-margin-top: 12px; }

// 处理计划（处理运单的上游：货等车 + 装车扫码）
.pool-card { margin-bottom: 20px;
  .list-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
  .toolbar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
}
.pool-summary { margin-top: 12px; font-size: 13px; color: #606266;
  b { color: #303133; font-size: 15px; }
}
// 生成收运运单弹窗：路线与现场动作写在按钮下方，避免用户在弹窗里猜
.dialog-note { font-size: 12px; line-height: 1.7; color: #909399; padding: 0 4px; }

// 处理运单列表
.leg-list-card { margin-bottom: 20px;
  .list-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
  .toolbar { display: flex; gap: 8px; flex-wrap: wrap; }
  .route-arrow { display: inline-block; vertical-align: middle; margin: 0 4px; color: #c0c4cc; }
  .progress-cell { border-bottom: 1px dashed #c0c4cc; cursor: help; }
}
:deep(.el-table .current-row) { background-color: #ecf5ff !important; }
.pager { display: flex; justify-content: center; margin-top: 12px; }

// 详情信息条
.transport-info-bar { display: flex; flex-wrap: wrap; gap: 20px; align-items: center; padding: 16px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 8px; margin-bottom: 20px; }
.info-item { display: flex; flex-direction: column; gap: 2px; }
.info-label { font-size: 11px; color: rgba(255, 255, 255, 0.7); }
.info-value { font-size: 14px; font-weight: 600; color: #fff; }
.info-sub { font-size: 11px; font-weight: 400; color: rgba(255, 255, 255, 0.75); }
.info-actions { margin-left: auto; }
.action-hint { font-size: 12px; color: #fff; opacity: 0.85; }

// 装载明细
.leg-items-card { margin-bottom: 20px;
  .card-header { display: flex; justify-content: space-between; align-items: center; }
}
.record-count { font-size: 12px; color: #909399; }

// 时间轴
.transport-timeline-card { margin-bottom: 0; }
.transport-timeline { padding: 16px 0; }
.tt-node { display: flex; gap: 16px; min-height: 56px;
  &.tt-done { .tt-dot { color: #67c23a; } .tt-title { color: #303133; } }
  &.tt-active { .tt-title { color: #409eff; font-weight: 700; } }
  &:not(.tt-done):not(.tt-active) { opacity: 0.5; }
}
.tt-line { display: flex; flex-direction: column; align-items: center; width: 28px; flex-shrink: 0; }
.tt-dot { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; color: #c0c4cc; }
.tt-dot-empty { width: 10px; height: 10px; border-radius: 50%; background: #dcdfe6; }
.tt-connector { width: 2px; flex: 1; min-height: 20px; background: #e4e7ed; &.done { background: #67c23a; } }
.tt-content { flex: 1; padding-bottom: 20px; }
.tt-title { font-size: 14px; font-weight: 600; margin-bottom: 2px; }
.tt-time { font-size: 12px; color: #909399; }
.is-pulsing { animation: pulse 1.5s ease-in-out infinite; color: #409eff; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>
