<template>
  <div class="page-container">
    <div class="page-head">
      <span class="page-title">设备接入监控</span>
      <el-button type="primary" @click="openDemo">
        <el-icon><Cpu /></el-icon>模拟设备上报
      </el-button>
    </div>

    <el-tabs v-model="activeTab">
      <!-- ==================== 待确认队列 ==================== -->
      <el-tab-pane label="待确认队列" name="staging">
        <el-alert
          class="rule-tip"
          type="info"
          :closable="false"
          show-icon
          title="自动采集 ≠ 自动生效"
          description="设备上报先落这里留档，只有校验通过才写业务表。校验不通过的数据不会丢，也不会被自动上链——由人工确认后入账，或拒绝并保留原始报文备查。"
        />

        <div class="search-panel">
          <el-form :model="filterForm" inline>
            <el-form-item label="接入通道">
              <el-select v-model="filterForm.channel" placeholder="全部" clearable style="width: 170px">
                <el-option v-for="(label, key) in CHANNEL_LABELS" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item label="处理状态">
              <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 150px">
                <el-option label="待人工处理" :value="1" />
                <el-option label="已入账" :value="2" />
                <el-option label="已拒绝" :value="3" />
                <el-option label="已降采样" :value="4" />
              </el-select>
            </el-form-item>
            <el-form-item label="设备编号">
              <el-input v-model="filterForm.deviceNo" placeholder="如 TEMP-001" clearable style="width: 170px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
              <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="stats-bar">
          <el-tag type="warning" size="large">待人工处理: {{ stats.manual }}</el-tag>
          <el-tag type="success" size="large">已入账: {{ stats.accepted }}</el-tag>
          <el-tag type="danger" size="large">已拒绝: {{ stats.rejected }}</el-tag>
          <el-tag type="info" size="large">降采样丢弃: {{ stats.dropped }}</el-tag>
          <span class="stats-hint">登记设备: {{ devices.length }} 台</span>
        </div>

        <el-table v-loading="loading" :data="tableData" border stripe>
          <el-table-column prop="receiveTime" label="接收时间" width="165" align="center" />
          <el-table-column prop="channelLabel" label="接入通道" width="130" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.channelLabel || CHANNEL_LABELS[row.channel] || row.channel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deviceName" label="来源设备" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <div>{{ row.deviceName || row.deviceNo }}</div>
              <div class="sub-cell">{{ row.deviceNo }}<span v-if="row.sourceRef"> · {{ row.sourceRef }}</span></div>
            </template>
          </el-table-column>
          <el-table-column prop="bizKey" label="设备唯一号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="reportTime" label="设备数据时间" width="165" align="center" />
          <el-table-column prop="status" label="处理结果" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetTable" label="入账目标" width="140" align="center">
            <template #default="{ row }">
              <span class="sub-cell">{{ row.targetTable || '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="errorMsg" label="原因" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="{ 'error-text': row.status === 1 || row.status === 3 }">{{ row.errorMsg || '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="handler" label="处理人" width="110" align="center">
            <template #default="{ row }">{{ row.handler || '--' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
              <el-button v-if="row.status === 1" type="success" link size="small" @click="handleApprove(row)">
                确认入账
              </el-button>
              <el-button v-if="row.status === 1" type="danger" link size="small" @click="handleReject(row)">
                拒绝
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
            background @size-change="handleSizeChange" @current-change="handlePageChange" />
        </div>
      </el-tab-pane>

      <!-- ==================== 设备台账 ==================== -->
      <el-tab-pane label="设备台账" name="devices">
        <el-alert
          class="rule-tip"
          type="info"
          :closable="false"
          show-icon
          title="设备用密钥接入，不走登录态"
          description="设备上报携带请求头 X-Device-Key，接入层按密钥反查登记信息。某台设备长时间无数据时，先在这里核对状态与通道是否匹配。"
        />
        <el-table v-loading="deviceLoading" :data="devices" border stripe>
          <el-table-column prop="deviceNo" label="设备编号" width="140" align="center" />
          <el-table-column prop="deviceName" label="设备名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="channel" label="接入通道" width="140" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ CHANNEL_LABELS[row.channel] || row.channel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="location" label="安装位置" min-width="180" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastSeenTime" label="最近上报" width="165" align="center">
            <template #default="{ row }">{{ row.lastSeenTime || '尚未上报' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情弹窗 -->
    <el-dialog v-model="viewVisible" title="上报详情" width="720px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="接入通道">
          {{ currentView.channelLabel || CHANNEL_LABELS[currentView.channel] || currentView.channel }}
        </el-descriptions-item>
        <el-descriptions-item label="来源设备">
          {{ currentView.deviceName || currentView.deviceNo }}（{{ currentView.deviceNo }}）
        </el-descriptions-item>
        <el-descriptions-item label="设备唯一号">{{ currentView.bizKey }}</el-descriptions-item>
        <el-descriptions-item label="来源单据号">{{ currentView.sourceRef || '--' }}</el-descriptions-item>
        <el-descriptions-item label="设备数据时间">{{ currentView.reportTime }}</el-descriptions-item>
        <el-descriptions-item label="接入层接收时间">{{ currentView.receiveTime }}</el-descriptions-item>
        <el-descriptions-item label="处理结果">
          <el-tag :type="statusTag(currentView.status)" size="small">{{ currentView.statusLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="入账目标">
          {{ currentView.targetTable || '--' }}<span v-if="currentView.targetId"> #{{ currentView.targetId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="处理人">{{ currentView.handler || '--' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ currentView.handleTime || '--' }}</el-descriptions-item>
        <el-descriptions-item label="原因/备注" :span="2">{{ currentView.errorMsg || '--' }}</el-descriptions-item>
      </el-descriptions>

      <div class="payload-title">设备原始报文（不可变留档，用于逐字段核对）</div>
      <pre class="payload-box">{{ prettyPayload }}</pre>

      <template #footer>
        <el-button @click="viewVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 模拟设备上报（演示用） -->
    <el-dialog v-model="demoVisible" title="模拟设备上报" width="660px" @closed="resetDemo">
      <el-alert
        class="demo-tip"
        type="warning"
        :closable="false"
        show-icon
        title="这是在替设备发一次上报，不是后台补录"
        description="服务端按设备编号反查密钥，走的是与真机完全一致的「鉴权 → 校验 → 幂等 → 派发」链路，不绕过任何一步。没有真机时，用它就能看到自动录入的完整过程。"
      />

      <el-form :model="demoForm" label-width="112px" class="demo-form">
        <el-form-item label="接入设备">
          <el-select v-model="demoForm.deviceNo" filterable style="width: 100%" @change="onDemoDeviceChange">
            <el-option
              v-for="d in enabledDevices"
              :key="d.deviceNo"
              :label="`${d.deviceNo} · ${d.deviceName}`"
              :value="d.deviceNo"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="接入通道">
          <el-tag effect="plain">{{ CHANNEL_LABELS[demoChannel] || demoChannel || '--' }}</el-tag>
          <span class="sub-cell">　写入目标：{{ demoTargetTable || '--' }}</span>
        </el-form-item>

        <el-form-item
          v-for="f in demoFields"
          :key="f.key"
          :label="f.label"
          :required="f.required"
        >
          <el-select
            v-if="f.type === 'select'"
            v-model="demoForm.data[f.key]"
            style="width: 100%"
          >
            <el-option v-for="o in f.options" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input v-else v-model="demoForm.data[f.key]" :placeholder="f.placeholder" clearable />
        </el-form-item>

        <el-form-item label="业务唯一号">
          <el-input v-model="demoForm.bizKey" placeholder="留空自动生成；填同一个值再报一次可验证幂等" clearable />
        </el-form-item>
        <el-form-item label="采集时间">
          <el-input v-model="demoForm.reportTime" placeholder="留空取当前时间（yyyy-MM-dd HH:mm:ss）" clearable />
        </el-form-item>
      </el-form>

      <div class="demo-actions">
        <el-button :loading="sampling" @click="fillSample">填充示例值</el-button>
        <span class="sub-cell">{{ sampleHint }}</span>
      </div>

      <div v-if="demoResult" class="demo-result">
        <div class="demo-result-head">
          <el-tag :type="demoResultTag" size="small">{{ demoResult.statusLabel }}</el-tag>
          <span class="demo-msg">{{ demoResult.message }}</span>
        </div>
        <pre class="payload-box">{{ JSON.stringify(demoResult, null, 2) }}</pre>
      </div>

      <template #footer>
        <el-button @click="demoVisible = false">关闭</el-button>
        <el-button type="primary" :loading="demoSubmitting" @click="submitDemo">上报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Cpu } from '@element-plus/icons-vue'
import type { EpTagType } from '@/types/common'
import { CHANNEL_LABELS, INGEST_STATUS_TAGS, ingestApi } from '@/api/modules/ingest'
import { distributionApi } from '@/api/modules/distribution'
import { pigApi } from '@/api/modules/breeding'
import { salesApi } from '@/api/modules/sales'

const activeTab = ref('staging')
const filterForm = reactive<{ channel: string; status: number | undefined; deviceNo: string }>({
  channel: '',
  status: undefined,
  deviceNo: '',
})
const tableData = ref<any[]>([])
const devices = ref<any[]>([])
const loading = ref(false)
const deviceLoading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const stats = reactive({ manual: 0, accepted: 0, rejected: 0, dropped: 0 })

const viewVisible = ref(false)
const currentView = ref<any>({})

const statusTag = (s: number): EpTagType => (INGEST_STATUS_TAGS[s] || 'info') as EpTagType

const prettyPayload = computed(() => {
  const raw = currentView.value?.payload
  if (!raw) return '（无报文）'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
})

function handleSearch() { pagination.pageNum = 1; fetchStaging() }
function handleReset() {
  filterForm.channel = ''
  filterForm.status = undefined
  filterForm.deviceNo = ''
  handleSearch()
}
function handleSizeChange() { pagination.pageNum = 1; fetchStaging() }
function handlePageChange() { fetchStaging() }

async function fetchStaging() {
  loading.value = true
  try {
    const res: any = await ingestApi.pageStaging({
      channel: filterForm.channel || undefined,
      status: filterForm.status,
      deviceNo: filterForm.deviceNo || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
    refreshStats()
  } catch {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

/** 统计按当前筛选条件逐状态各查一次第一页，只为拿到 total（数据量小，够用且不引入新接口） */
async function refreshStats() {
  const base = {
    channel: filterForm.channel || undefined,
    deviceNo: filterForm.deviceNo || undefined,
    pageNum: 1,
    pageSize: 1,
  }
  const [manual, accepted, rejected, dropped] = await Promise.all([
    count(base, 1), count(base, 2), count(base, 3), count(base, 4),
  ])
  stats.manual = manual
  stats.accepted = accepted
  stats.rejected = rejected
  stats.dropped = dropped
}

async function count(base: any, status: number) {
  try {
    const res: any = await ingestApi.pageStaging({ ...base, status })
    return res?.total || 0
  } catch {
    return 0
  }
}

async function fetchDevices() {
  deviceLoading.value = true
  try {
    devices.value = (await ingestApi.listDevices()) || []
  } catch {
    devices.value = []
  } finally {
    deviceLoading.value = false
  }
}

function handleView(row: any) {
  currentView.value = row
  viewVisible.value = true
}

async function handleApprove(row: any) {
  const { value } = await ElMessageBox.prompt(
    `确认将设备上报（${row.deviceNo} / ${row.bizKey}）强制入账？\n请填写人工确认说明，将一并留档。`,
    '确认入账',
    { inputPlaceholder: '例如：纸质检疫证明已核对无误', inputValidator: (v: string) => !!v || '必须填写确认说明' },
  )
  try {
    const res: any = await ingestApi.approveStaging(row.id, { remark: value })
    if (res?.accepted) ElMessage.success('已入账')
    else ElMessage.warning(res?.message || '仍不满足入账条件，请查看原因')
    fetchStaging()
  } catch (e: any) {
    ElMessage.error(e?.message || '确认入账失败')
  }
}

async function handleReject(row: any) {
  const { value } = await ElMessageBox.prompt(
    `确定拒绝该条设备上报（${row.deviceNo} / ${row.bizKey}）？原始报文会保留备查。`,
    '拒绝数据',
    { inputPlaceholder: '例如：耳标与实物不符', inputValidator: (v: string) => !!v || '必须填写拒绝原因' },
  )
  try {
    await ingestApi.rejectStaging(row.id, { remark: value })
    ElMessage.success('已拒绝')
    fetchStaging()
  } catch (e: any) {
    ElMessage.error(e?.message || '拒绝失败')
  }
}

// ==================== 模拟设备上报（演示/联调）====================

interface DemoField {
  key: string
  label: string
  required?: boolean
  type?: 'text' | 'number' | 'select'
  options?: { label: string; value: number }[]
  placeholder?: string
}

/** 各通道最少必填字段——与后端 ChannelHandler.validate 的要求一一对应 */
const DEMO_FIELDS: Record<string, DemoField[]> = {
  TEMPERATURE: [
    { key: 'transportNo', label: '运输单号', required: true, placeholder: '需是「运输中」的运单' },
    { key: 'temperature', label: '温度(℃)', required: true, type: 'number', placeholder: '如 2.5；超 -18~0 会标记异常但照常入账' },
  ],
  ENTRY: [
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '需在生猪档案中存在' },
    { key: 'batchNo', label: '入场批次号', required: true, placeholder: '如 B-20260921-01' },
    { key: 'weight', label: '地磅重量(kg)', type: 'number', placeholder: '如 112.5' },
    { key: 'vehicleNo', label: '车牌号', placeholder: '如 京A·12345' },
    { key: 'healthCheck', label: '临床健康检查', type: 'select', options: [{ label: '通过', value: 1 }, { label: '异常', value: 0 }] },
  ],
  RACTOPAMINE: [
    { key: 'earTagNo', label: '耳标号', required: true },
    { key: 'batchNo', label: '批次号', required: true },
    { key: 'sampleNo', label: '样本编号', required: true, placeholder: '如 S-20260921-01' },
    { key: 'result', label: '检测结果', required: true, type: 'select', options: [{ label: '1 阴性（合格）', value: 1 }, { label: '0 阳性（不合格）', value: 0 }] },
    { key: 'testTarget', label: '检测项目', placeholder: '留空默认克伦特罗' },
    { key: 'samplePart', label: '样品部位', placeholder: '如 尿液' },
  ],
  SALE: [
    { key: 'qrCode', label: '产品二维码', required: true, placeholder: '需是「在库可售」的商品码' },
    { key: 'sellPrice', label: '售价(元)', type: 'number' },
    { key: 'sellWeightKg', label: '重量(kg)', type: 'number' },
  ],
  RECEIPT: [
    { key: 'transportNo', label: '运输单号', required: true, placeholder: '需是「待发车」的运单' },
    { key: 'storeName', label: '门店名称', required: true, placeholder: '如 示范一店' },
    { key: 'receiver', label: '签收人', placeholder: '留空会记为设备，签收需实名时请填写' },
    { key: 'tempValue', label: '到货温度(℃)', type: 'number' },
    { key: 'qtyCheck', label: '数量核对', type: 'select', options: [{ label: '一致', value: 1 }, { label: '不一致', value: 0 }] },
    { key: 'packageIntact', label: '包装完好', type: 'select', options: [{ label: '完好', value: 1 }, { label: '破损', value: 0 }] },
  ],
}

/** 通道 → 目标业务表（与后端 IngestChannel 一致，仅用于界面提示） */
const CHANNEL_TABLES: Record<string, string> = {
  TEMPERATURE: 'temperature_log',
  ENTRY: 'entry_inspection',
  RACTOPAMINE: 'ractopamine_test',
  SALE: 'retail_sale',
  RECEIPT: 'store_receipt',
}

const demoVisible = ref(false)
const demoSubmitting = ref(false)
const sampling = ref(false)
const sampleHint = ref('')
const demoResult = ref<any>(null)
const demoForm = reactive<{ deviceNo: string; bizKey: string; reportTime: string; data: Record<string, any> }>({
  deviceNo: '',
  bizKey: '',
  reportTime: '',
  data: {},
})

const enabledDevices = computed(() => devices.value.filter((d) => d.status === 1))
const demoDevice = computed(() => devices.value.find((d) => d.deviceNo === demoForm.deviceNo))
const demoChannel = computed(() => demoDevice.value?.channel || '')
const demoTargetTable = computed(() => CHANNEL_TABLES[demoChannel.value] || '')
const demoFields = computed(() => DEMO_FIELDS[demoChannel.value] || [])
const demoResultTag = computed<EpTagType>(() => {
  const r = demoResult.value
  if (!r) return 'info'
  if (r.accepted) return 'success'
  return r.status === 1 ? 'warning' : 'danger'
})

function openDemo() {
  demoVisible.value = true
  if (!demoForm.deviceNo && enabledDevices.value.length) {
    demoForm.deviceNo = enabledDevices.value[0].deviceNo
    onDemoDeviceChange()
  }
}

function resetDemo() {
  demoResult.value = null
  sampleHint.value = ''
  demoForm.bizKey = ''
  demoForm.reportTime = ''
  applyFieldDefaults()
}

/** 切换设备即切换通道，字段结构随之变化，默认值重新灌一遍 */
function onDemoDeviceChange() {
  demoResult.value = null
  sampleHint.value = ''
  applyFieldDefaults()
}

function applyFieldDefaults() {
  const data: Record<string, any> = {}
  for (const f of demoFields.value) {
    if (f.type === 'select' && f.options?.length) {
      data[f.key] = f.options[0].value
    } else {
      data[f.key] = ''
    }
  }
  // 每次打开/切通道都换一个唯一号，保证能反复触发
  demoForm.bizKey = ''
  demoForm.data = data
}

async function fillSample() {
  sampling.value = true
  sampleHint.value = ''
  try {
    const ch = demoChannel.value
    if (ch === 'TEMPERATURE') {
      const t = await findTransport(2)
      if (t) {
        demoForm.data.transportNo = t.transportNo
        demoForm.data.temperature = 2.5
        sampleHint.value = `取到运输中运单 ${t.transportNo}`
      } else {
        demoForm.data.temperature = 2.5
        sampleHint.value = '当前没有「运输中」的运单，请先到「分割配送 → 冷链运输」给一条待发车任务点发车'
      }
    } else if (ch === 'RECEIPT') {
      // 签收的业务前置是「已到达(status=3)且尚未签收」——按签收单反查排除掉已签的
      const candidates = await safeList(() => distributionApi.getTransports({ status: 3, pageNum: 1, pageSize: 10 }))
      const receipts = await safeList(() => distributionApi.getReceipts({ pageNum: 1, pageSize: 200 }))
      const signed = new Set(receipts.map((r: any) => r.transportId))
      const t = candidates.find((c: any) => !signed.has(c.id))
      if (t) {
        demoForm.data.transportNo = t.transportNo
        sampleHint.value = `取到已到达未签收运单 ${t.transportNo}`
      } else {
        sampleHint.value = '没有「已到达且未签收」的运单，请先到「分割配送 → 冷链运输」给一条运输中任务点到达'
      }
      const stores = await safeList(() => distributionApi.getStores())
      const name = stores?.[0]?.storeName || stores?.[0]?.store_name
      if (name) demoForm.data.storeName = name
      demoForm.data.receiver = '张三'
      demoForm.data.tempValue = -2
    } else if (ch === 'ENTRY' || ch === 'RACTOPAMINE') {
      const page = await safeList(() => pigApi.list({ pageNum: 1, pageSize: 1 }))
      const pig = page?.[0]
      const earTag = pig?.earTagNo || pig?.ear_tag_no
      if (earTag) {
        demoForm.data.earTagNo = earTag
        demoForm.data.batchNo = `B-${todayText()}-01`
        demoForm.data.sampleNo = `S-${Date.now()}`
        demoForm.data.weight = 112.5
        if (ch === 'RACTOPAMINE') demoForm.data.result = 1
        sampleHint.value = `取到耳标 ${earTag}；检疫证是否有效由服务端核验，核验不过会转「待人工处理」`
      } else {
        sampleHint.value = '生猪档案里没有可用的耳标号，请先到「养殖管理 → 生猪档案」建档'
      }
    } else if (ch === 'SALE') {
      // 只取在库可售(status=1)的商品码：取到已售/未激活的码会演示不出新销售
      const page = await safeList(() => salesApi.getQrcodes({ status: 1, pageNum: 1, pageSize: 1 }))
      const code = page?.[0]?.productQrCode || page?.[0]?.product_qr_code
      if (code) {
        demoForm.data.qrCode = code
        demoForm.data.sellPrice = 38.5
        demoForm.data.sellWeightKg = 0.75
        sampleHint.value = `取到在库可售商品码 ${code}`
      } else {
        sampleHint.value = '销售库没有在库可售的商品码，请先到「销售管理 → 二维码管理」生成一批'
      }
    }
  } catch (e: any) {
    sampleHint.value = `填充失败：${e?.message || '请手工填写'}`;
  } finally {
    sampling.value = false
  }
}

async function submitDemo() {
  const missing = demoFields.value
    .filter((f) => f.required && !String(demoForm.data[f.key] ?? '').trim())
    .map((f) => f.label)
  if (missing.length) {
    ElMessage.warning(`请先填写：${missing.join('、')}`)
    return
  }
  // 数值字段按数字提交，否则后端会当成字符串解析失败
  const payload: Record<string, any> = {}
  for (const f of demoFields.value) {
    const raw = demoForm.data[f.key]
    if (raw === '' || raw === undefined || raw === null) continue
    payload[f.key] = f.type === 'number' ? Number(raw) : raw
  }

  demoSubmitting.value = true
  try {
    const res: any = await ingestApi.demoReport({
      deviceNo: demoForm.deviceNo,
      bizKey: demoForm.bizKey || undefined,
      reportTime: demoForm.reportTime || undefined,
      data: payload,
    })
    demoResult.value = res
    if (res?.accepted) ElMessage.success('已入账，业务表已写入并标记 source=DEVICE')
    else if (res?.status === 1) ElMessage.warning('已收到，但需人工确认后才会入账')
    else ElMessage.warning(res?.message || '未入账，原因见回执')
    fetchStaging()
    fetchDevices()
  } catch (e: any) {
    ElMessage.error(e?.message || '上报失败')
  } finally {
    demoSubmitting.value = false
  }
}

async function findTransport(status: number) {
  const page = await safeList(() => distributionApi.getTransports({ status, pageNum: 1, pageSize: 1 }))
  return page?.[0] || null
}

/** 统一把分页/列表响应的形状差异吃掉，取不到就返回空数组 */
async function safeList(fn: () => Promise<any>): Promise<any[]> {
  try {
    const res: any = await fn()
    if (Array.isArray(res)) return res
    return res?.records || res?.list || []
  } catch {
    return []
  }
}

function todayText() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}`
}

onMounted(() => { fetchStaging(); fetchDevices() })
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.page-title { font-size: 16px; font-weight: 600; color: #303133; }
.rule-tip { margin-bottom: 16px; }
.demo-tip { margin-bottom: 18px; }
.demo-form { margin-top: 4px; }
.demo-actions { display: flex; align-items: center; gap: 12px; padding: 10px 0 4px; border-top: 1px dashed #ebeef5; }
.demo-result { margin-top: 12px; }
.demo-result-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.demo-msg { font-size: 13px; color: #606266; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.stats-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 12px 16px; background: #f5f7fa; border-radius: 6px; flex-wrap: wrap; }
.stats-hint { margin-left: auto; font-size: 13px; color: #909399; }
.sub-cell { font-size: 12px; color: #909399; }
.error-text { color: #e6a23c; }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.payload-title { margin: 16px 0 8px; font-size: 13px; color: #606266; }
.payload-box { max-height: 260px; overflow: auto; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 4px; padding: 12px; font-size: 12px; line-height: 1.6; color: #303133; white-space: pre-wrap; word-break: break-all; }
</style>
