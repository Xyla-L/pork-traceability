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
              <el-tag size="small" effect="plain">{{ CHANNEL_LABELS[row.channel] || row.channelLabel || row.channel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deviceName" label="来源设备" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <div>{{ row.deviceName || row.deviceNo }}</div>
              <div class="sub-cell">{{ row.deviceNo }}<span v-if="row.sourceRef"> · {{ row.sourceRef }}</span></div>
            </template>
          </el-table-column>
          <el-table-column prop="bizKey" label="上报编号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="reportTime" label="设备数据时间" width="165" align="center" />
          <el-table-column prop="status" label="处理结果" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="数据去向" width="150" align="center">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.targetTable"
                :content="`后台数据表：${row.targetTable}（技术人员排查用）`"
                placement="top"
              >
                <span>{{ targetObjectLabel(row.channel, row.targetTable) }}</span>
              </el-tooltip>
              <span v-else class="sub-cell">--</span>
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
        <el-table v-loading="deviceLoading" :data="devices" border stripe>
          <el-table-column prop="deviceNo" label="设备编号" width="140" align="center" />
          <el-table-column prop="deviceName" label="设备名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="channel" label="接入通道" min-width="190" align="center">
            <template #default="{ row }">
              <!-- 车载终端一台设备承担两个通道（发车确认 + 到站刷卡确认，同组见 IngestChannel.deviceGroup()）：
                   能报的通道都要列出来 —— 只显示台账登记的那一个，就把「一台设备两个角色」藏起来了 -->
              <el-tag
                v-for="c in (row.channels && row.channels.length ? row.channels : [row.channel])"
                :key="c"
                size="small"
                effect="plain"
                style="margin: 1px 2px"
              >
                {{ CHANNEL_LABELS[c] || c }}
              </el-tag>
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
          {{ CHANNEL_LABELS[currentView.channel] || currentView.channelLabel || currentView.channel }}
        </el-descriptions-item>
        <el-descriptions-item label="来源设备">
          {{ currentView.deviceName || currentView.deviceNo }}（{{ currentView.deviceNo }}）
        </el-descriptions-item>
        <el-descriptions-item label="上报编号">{{ currentView.bizKey }}</el-descriptions-item>
        <el-descriptions-item label="来源单据号">{{ currentView.sourceRef || '--' }}</el-descriptions-item>
        <el-descriptions-item label="设备数据时间">{{ currentView.reportTime }}</el-descriptions-item>
        <el-descriptions-item label="接入层接收时间">{{ currentView.receiveTime }}</el-descriptions-item>
        <el-descriptions-item label="处理结果">
          <el-tag :type="statusTag(currentView.status)" size="small">{{ currentView.statusLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据去向">
          {{ targetObjectLabel(currentView.channel, currentView.targetTable) }}
          <span v-if="currentView.targetId" class="sub-cell">（记录编号 {{ currentView.targetId }}）</span>
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
      <el-form :model="demoForm" label-width="112px" class="demo-form">
        <el-form-item label="接入设备">
          <el-select v-model="demoForm.deviceNo" filterable style="width: 100%" @change="onDemoDeviceChange">
            <el-option
              v-for="d in enabledDevices"
              :key="d.deviceNo"
              :label="`${d.deviceNo} · ${d.deviceName}`"
              :value="d.deviceNo"
            >
              <span>{{ d.deviceNo }} · {{ d.deviceName }}</span>
              <!-- 车载终端一台设备两个通道：在下拉里就把「它能报什么」摆出来 -->
              <span v-if="d.channels && d.channels.length > 1" class="sub-cell">
                　{{ d.channelLabels }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="接入通道">
          <!-- 一台车载终端承担两个通道（发车确认 + 到站刷卡确认）：这时必须由人指定这一报算
               哪个动作，系统没法替现场猜（报文里的 channel 就是干这个的）。只有一个通道的
               设备照旧，直接用只读标签显示。 -->
          <el-select
            v-if="demoChannelOptions.length > 1"
            v-model="demoForm.channel"
            style="width: 100%"
            @change="resetDemo"
          >
            <el-option
              v-for="c in demoChannelOptions"
              :key="c"
              :label="CHANNEL_LABELS[c] || c"
              :value="c"
            />
          </el-select>
          <el-tag v-else effect="plain">{{ CHANNEL_LABELS[demoChannel] || demoChannel || '--' }}</el-tag>
          <el-tooltip
            v-if="demoTargetTable"
            :content="`后台数据表：${demoTargetTable}（技术人员排查用）`"
            placement="top"
          >
            <span class="sub-cell">　数据将写入「{{ demoTargetObject }}」</span>
          </el-tooltip>
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
          <el-date-picker
            v-else-if="f.type === 'date'"
            v-model="demoForm.data[f.key]"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="f.placeholder || '请选择日期'"
            style="width: 100%"
          />
          <el-date-picker
            v-else-if="f.type === 'datetime'"
            v-model="demoForm.data[f.key]"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="f.placeholder || '请选择时间'"
            style="width: 100%"
          />
          <el-upload
            v-else-if="f.type === 'image'"
            v-model:file-list="demoPhotoFiles"
            action="/api/v1/file/upload"
            :headers="uploadHeaders"
            list-type="picture-card"
            :limit="f.maxCount || 1"
            accept=".jpg,.jpeg,.png"
            :before-upload="beforePhotoUpload"
            :on-success="(res: any, file: any) => onPhotoSuccess(res, file, f.key)"
            :on-error="onPhotoError"
            :on-remove="() => onPhotoRemove(f.key)"
            :on-exceed="onPhotoExceed"
          >
            <el-icon><Plus /></el-icon>
            <template #tip>
              <div class="el-upload__tip">
                {{ f.placeholder || '支持 jpg、png 格式，单张不超过 5MB' }}
              </div>
            </template>
          </el-upload>
          <!-- 部位清单：一次上报 = 一整头猪的产出清单，部位只能取自标准部位字典 -->
          <div v-else-if="f.type === 'items'" class="items-editor">
            <div v-for="(row, idx) in demoItems" :key="idx" class="item-row">
              <el-select v-model="row.partCode" filterable placeholder="选择部位" style="width: 200px">
                <el-option
                  v-for="p in meatPartOptions"
                  :key="p.partCode"
                  :label="`${p.partName}（${p.partCode}）`"
                  :value="p.partCode"
                />
              </el-select>
              <el-input-number
                v-model="row.weightKg"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 140px"
              />
              <span class="item-unit">kg</span>
              <el-input-number
                v-model="row.packageCount"
                :min="1"
                :precision="0"
                controls-position="right"
                style="width: 120px"
              />
              <span class="item-unit">包</span>
              <el-button link type="danger" :disabled="demoItems.length <= 1" @click="removeItemRow(idx)">
                删除
              </el-button>
            </div>
            <div class="items-actions">
              <el-button link type="primary" @click="addItemRow">增加一个部位</el-button>
              <span class="item-tip">缺必出部位、或各部位合计与白条重量对不上，都会转人工复核</span>
            </div>
          </div>
          <el-input v-else v-model="demoForm.data[f.key]" :placeholder="f.placeholder" clearable />
        </el-form-item>

        <el-form-item label="上报编号">
          <el-input v-model="demoForm.bizKey" placeholder="留空自动生成；填同一个值再报一次，会被判重复上报并拒绝入账" clearable />
        </el-form-item>
        <el-form-item label="采集时间">
          <el-input v-model="demoForm.reportTime" placeholder="留空取当前时间（yyyy-MM-dd HH:mm:ss）" clearable />
        </el-form-item>
      </el-form>

      <div class="demo-actions">
        <el-button :loading="sampling" @click="fillSample">填充示例值</el-button>
        <!-- 取不到示例值时，若差的只是「生成运单」这一步，直接把入口递过去 -->
        <el-button v-if="sampleJump" link type="primary" @click="router.push(sampleJump.to)">
          {{ sampleJump.text }}
        </el-button>
        <!-- 差的只是一张业务单（处理计划里的召回货还没排车）时，就地出单，不用跳页面再回来 -->
        <el-button v-if="sampleAction" link type="primary" :loading="sampling" @click="sampleAction.run()">
          {{ sampleAction.text }}
        </el-button>
        <span v-if="sampleHint" class="sub-cell">{{ sampleHint }}</span>
      </div>

      <div v-if="demoResult" class="demo-result">
        <div class="demo-result-head">
          <el-tag :type="demoResultTag" size="small">{{ demoResult.statusLabel }}</el-tag>
        </div>
        <ul class="demo-result-list">
          <li>
            <span class="k">数据去向</span>
            <span class="v">{{ demoResultDest }}</span>
          </li>
          <li v-if="demoResultAfter">
            <span class="k">扫码之后</span>
            <span class="v">{{ demoResultAfter }}</span>
          </li>
          <li>
            <span class="k">上报编号</span>
            <span class="v">{{ demoResult.bizKey }}</span>
          </li>
        </ul>
        <div v-if="!demoResult.accepted && demoResult.message" class="demo-result-reason">
          未入账原因：{{ demoResult.message }}
        </div>
        <!-- 原始回执只在「没入账」时保留入口：正常入账时界面上不出现任何技术内容 -->
        <el-collapse v-if="!demoResult.accepted" v-model="demoRawOpen" class="demo-raw">
          <el-collapse-item name="raw" title="查看原始回执（技术人员核对用）">
            <pre class="payload-box">{{ JSON.stringify(demoResult, null, 2) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 分割上报入账之后：分割线工位的车间相机自动连拍（不用人来这里手动传照片）。
           照片逐张绑定到这次分割的批次，可在「分割操作」页的监控区按批次看到。 -->
      <div v-if="shotState.visible" class="demo-result camera-shot">
        <div class="demo-result-head">
          <el-tag :type="shotState.running ? 'info' : (shotState.failed ? 'warning' : 'success')" size="small">
            分割车间监控 · {{ shotState.running ? '拍摄中' : '已完成' }} {{ shotState.done }}/{{ shotState.total }} 张
          </el-tag>
        </div>
        <ul class="demo-result-list">
          <li>
            <span class="k">相机</span>
            <span class="v">{{ shotState.deviceNo }}</span>
          </li>
          <li>
            <span class="k">绑定批次</span>
            <span class="v">{{ shotState.batchNo || '--' }}</span>
          </li>
        </ul>
        <div v-if="shotState.text" class="demo-result-reason">{{ shotState.text }}</div>
      </div>

      <template #footer>
        <el-button @click="demoVisible = false">关闭</el-button>
        <el-tooltip :disabled="!demoReportLocked" :content="demoLockHint" placement="top">
          <span class="demo-report-wrap">
            <el-button
              type="primary"
              :loading="demoSubmitting"
              :disabled="demoReportLocked"
              @click="submitDemo"
            >
              上报
            </el-button>
          </span>
        </el-tooltip>
      </template>
    </el-dialog>

    <!-- 人工确认入账：设备拿不到的信息（现场人员姓名、凭证照片）由复核人在这里补齐 -->
    <el-dialog v-model="approveVisible" title="确认入账" width="560px" @closed="resetApprove">
      <el-descriptions :column="1" border size="small" class="approve-summary">
        <el-descriptions-item label="接入通道">
          {{ CHANNEL_LABELS[approveRow.channel] || approveRow.channel }}
        </el-descriptions-item>
        <el-descriptions-item label="来源设备">
          {{ approveRow.deviceName || approveRow.deviceNo }}（{{ approveRow.deviceNo }}）
        </el-descriptions-item>
        <el-descriptions-item label="上报编号">{{ approveRow.bizKey }}</el-descriptions-item>
        <el-descriptions-item label="转人工原因">
          <span class="error-text">{{ approveRow.errorMsg || '--' }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="112px" class="approve-form">
        <el-form-item v-if="approvePersonField" :label="approvePersonField.label" required>
          <el-input
            v-model="approveForm.operator"
            :placeholder="approvePersonField.placeholder"
            maxlength="20"
            clearable
          />
        </el-form-item>
        <el-form-item v-if="approveNeedPhoto" label="凭证照片" required>
          <el-upload
            v-model:file-list="approvePhotoFiles"
            action="/api/v1/file/upload"
            :headers="uploadHeaders"
            list-type="picture-card"
            :limit="1"
            accept=".jpg,.jpeg,.png"
            :before-upload="beforePhotoUpload"
            :on-success="(res: any, file: any) => onPhotoSuccess(res, file, 'fileIds', approveFileIds)"
            :on-error="onPhotoError"
            :on-remove="() => onPhotoRemove('fileIds', approveFileIds)"
            :on-exceed="onPhotoExceed"
          >
            <el-icon><Plus /></el-icon>
            <template #tip>
              <div class="el-upload__tip">
                设备端没有照片时由复核人补拍上传；支持 jpg、png，单张不超过 5MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="确认说明" required>
          <el-input
            v-model="approveForm.remark"
            type="textarea"
            :rows="2"
            placeholder="例如：纸质检疫证明已核对无误（将随记录留档）"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approveSubmitting" @click="submitApprove">确认入账</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Cpu, Plus } from '@element-plus/icons-vue'
import type { EpTagType } from '@/types/common'
import { CHANNEL_LABELS, INGEST_STATUS_TAGS, ingestApi } from '@/api/modules/ingest'
import { fileApi } from '@/api/modules/file'
import { getToken } from '@/utils/auth'
import { distributionApi } from '@/api/modules/distribution'
import { pigApi } from '@/api/modules/breeding'
import { salesApi } from '@/api/modules/sales'
import { slaughterApi } from '@/api/modules/slaughter'
import { SNAPSHOT_CHANNEL, SNAPSHOT_COUNT, autoShootSplitSnapshots } from '@/utils/splitCamera'

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
    const list: any[] = (await ingestApi.listDevices()) || []
    // 按「接入通道的业务发生顺序」排布（养殖建档 → 免疫 → 入场查验 → … → 零售售出），
    // 同一通道内按设备编号。台账本身分两个脚本登记，顺序是阶段性的，这里统一为业务序。
    const order = Object.keys(CHANNEL_LABELS)
    const rank = (ch: string) => {
      const i = order.indexOf(ch)
      return i < 0 ? order.length : i
    }
    devices.value = [...list].sort((a, b) => {
      const d = rank(a.channel) - rank(b.channel)
      if (d !== 0) return d
      return String(a.deviceNo || '').localeCompare(String(b.deviceNo || ''))
    })
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

// ==================== 人工确认入账（补齐设备拿不到的信息）====================

const approveVisible = ref(false)
const approveRow = ref<any>({})
const approveSubmitting = ref(false)
const approveForm = reactive<{ operator: string; remark: string }>({ operator: '', remark: '' })
/** 人工补传的凭证照片（fileId 列表），提交时随 payload 补丁一起发 */
const approveFileIds = ref<string[]>([])
const approvePhotoFiles = ref<any[]>([])

/**
 * 通道 → 现场人员字段。设备只是采集工具，人名必须落到人；
 * 转人工的多数原因就是报文里没有这个名字，确认入账时由复核人补上。
 */
const PERSON_FIELDS: Record<string, { key: string; label: string; placeholder: string }> = {
  VACCINE: { key: 'operator', label: '操作人(兽医)', placeholder: '现场接种兽医姓名，机器不得代签' },
  ENTRY: { key: 'inspector', label: '查验员', placeholder: '现场查验人员姓名，机器不得代签' },
  RACTOPAMINE: { key: 'operator', label: '检测员', placeholder: '现场检测人员姓名，机器不得代签' },
  INSPECTION: { key: 'veterinary', label: '检验人(兽医)', placeholder: '现场检验兽医姓名，机器不得代签' },
  STAMP: { key: 'veterinary', label: '授权兽医', placeholder: '盖章授权必须落具体人' },
  SPLIT: { key: 'operator', label: '操作员', placeholder: '现场分割操作人员姓名，机器不得代签' },
  LOAD: { key: 'driverName', label: '司机姓名(可空)', placeholder: '装车时不采集司机：可留空，等他上车刷卡时会自动写进运单' },
  RECEIPT: { key: 'receiver', label: '签收人', placeholder: '门店收货人姓名，责任转移必须落具体人' },
}

const approvePayload = computed<Record<string, any>>(() => {
  const raw = approveRow.value?.payload
  if (!raw) return {}
  try {
    return JSON.parse(raw) || {}
  } catch {
    return {}
  }
})

/** 报文里已经有现场人员姓名的不再问一遍，没有的必须由复核人补上 */
const approvePersonField = computed(() => {
  const field = PERSON_FIELDS[approveRow.value?.channel]
  if (!field) return null
  return approvePayload.value[field.key] ? null : field
})

/** 凭证照片只有免疫注射环节要求；设备已带照片的不再传 */
const approveNeedPhoto = computed(() => {
  if (approveRow.value?.channel !== 'VACCINE') return false
  const fileIds = approvePayload.value.fileIds
  return !Array.isArray(fileIds) || fileIds.length === 0
})

function handleApprove(row: any) {
  approveRow.value = row
  approveForm.operator = ''
  approveForm.remark = ''
  approveFileIds.value = []
  approvePhotoFiles.value = []
  approveVisible.value = true
}

function resetApprove() {
  approveRow.value = {}
  approveForm.operator = ''
  approveForm.remark = ''
  approveFileIds.value = []
  approvePhotoFiles.value = []
}

async function submitApprove() {
  const row = approveRow.value
  const patch: Record<string, any> = {}
  const person = approvePersonField.value
  if (person) {
    const name = approveForm.operator.trim()
    if (!name) {
      ElMessage.warning(`请填写${person.label}姓名——追溯链上要能查到是谁在操作`)
      return
    }
    patch[person.key] = name
  }
  if (approveNeedPhoto.value) {
    if (photoUploadingCount.value > 0) {
      ElMessage.warning('凭证照片正在上传，请稍候再提交')
      return
    }
    if (!approveFileIds.value.length) {
      ElMessage.warning('请上传接种凭证照片——免疫记录没有佐证材料不能入账')
      return
    }
    patch.fileIds = approveFileIds.value
  }
  const remark = approveForm.remark.trim()
  if (!remark) {
    ElMessage.warning('请填写确认说明（将随记录留档）')
    return
  }

  approveSubmitting.value = true
  try {
    const res: any = await ingestApi.approveStaging(row.id, { remark, payload: patch })
    if (res?.accepted) {
      approveVisible.value = false
      ElMessage.success('已入账')
    } else {
      ElMessage.warning(res?.message || '仍不满足入账条件，请查看原因')
    }
    fetchStaging()
  } catch (e: any) {
    ElMessage.error(e?.message || '确认入账失败')
  } finally {
    approveSubmitting.value = false
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

// ==================== 凭证照片上传（模拟上报 / 人工确认共用）====================

// el-upload 不走 axios 拦截器：登录态要自己放进请求头，响应也是后端原始包装 { code, data }
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${getToken() || ''}` }))

/** 仍在传输中的照片数量：提交前必须为 0，否则记录会缺凭证 */
const photoUploadingCount = ref(0)

function beforePhotoUpload(file: File) {
  // 与文件服务白名单对齐（jpg/jpeg/png），webp/heic 等格式后端会拒绝
  if (!/\.(jpe?g|png)$/i.test(file.name || '')) {
    ElMessage.error('仅支持 jpg、png 格式照片，请转换格式后重新上传')
    return false
  }
  if (file.size / 1024 / 1024 >= 5) {
    ElMessage.error('照片大小不能超过 5MB')
    return false
  }
  photoUploadingCount.value++
  return true
}

/** 上传成功：把文件服务返回的 fileId 收进对应表单字段（demo 写表单数据，人工确认写独立列表） */
function onPhotoSuccess(response: any, uploadFile: any, key: string, target?: { value: string[] }) {
  photoUploadingCount.value = Math.max(0, photoUploadingCount.value - 1)
  // 业务异常（如格式不符）也是 HTTP 200 + code!=200，必须透出后端真实原因，并把失败项从缩略图移除
  const fileId = response?.data?.fileId || ''
  if (response?.code !== 200 || !fileId) {
    if (uploadFile?.uid) removePhotoListItem(uploadFile.uid)
    ElMessage.error(response?.message || '凭证照片上传失败，请移除后重新上传')
    return
  }
  const current: string[] = target
    ? target.value
    : Array.isArray(demoForm.data[key])
      ? demoForm.data[key]
      : []
  if (!current.includes(fileId)) {
    const next = [...current, fileId]
    if (target) target.value = next
    else demoForm.data[key] = next
  }
}

function onPhotoError() {
  photoUploadingCount.value = Math.max(0, photoUploadingCount.value - 1)
  ElMessage.error('凭证照片上传失败，请检查网络后重新上传')
}

/** 移除照片 = 清空对应字段的 fileId（当前只允许 1 张，整体覆盖） */
function onPhotoRemove(key: string, target?: { value: string[] }) {
  if (target) target.value = []
  else demoForm.data[key] = []
}

function onPhotoExceed() {
  ElMessage.warning('最多上传 1 张凭证照片，如需更换请先移除原图')
}

/** HTTP 200 但业务失败时，把假成功的项从缩略图列表里拿掉，避免「看着传上了其实没有」 */
function removePhotoListItem(uid: string | number) {
  demoPhotoFiles.value = demoPhotoFiles.value.filter((x: any) => x.uid !== uid)
  approvePhotoFiles.value = approvePhotoFiles.value.filter((x: any) => x.uid !== uid)
}

/**
 * 演示凭证照片：预置的合成图带醒目「NOT A REAL RECORD」声明——
 * 演示库不该混进任何人的真实照片，而设备又必须带凭证才能演示自动入账，所以内置一张。
 * 文件服务按 sha256 去重，反复点「填充示例值」也不会堆文件。
 */
async function uploadDemoVoucherPhoto(): Promise<string | null> {
  try {
    const res = await fetch('/demo/vaccine-voucher.png')
    if (!res.ok) return null
    const blob = await res.blob()
    const info: any = await fileApi.upload(new File([blob], 'vaccine-voucher.png', { type: 'image/png' }))
    return info?.fileId || null
  } catch {
    return null
  }
}

/**
 * 分割车间监控快照的演示照片：与接种凭证同理，走一次真实上传拿回真 fileId。
 * <p>
 * 为什么不直接塞一个假 fileId：接入层会调 {@code /file/{fileId}/meta} 核对照片真实存在，
 * 编造的 fileId 会被判「已拒绝」。演示要演示的是「两段式上报能通」这条路，就得真上传一次。
 * 项目里现成的演示图只有一张合成图，快照复用它（内容无所谓，链路一致即可）。
 */
async function uploadDemoSnapshotPhoto(): Promise<string | null> {
  try {
    const res = await fetch('/demo/vaccine-voucher.png')
    if (!res.ok) return null
    const blob = await res.blob()
    const info: any = await fileApi.upload(new File([blob], 'split-snapshot.png', { type: 'image/png' }))
    return info?.fileId || null
  } catch {
    return null
  }
}

// ==================== 模拟设备上报（演示/联调）====================

interface DemoField {
  key: string
  label: string
  required?: boolean
  type?: 'text' | 'number' | 'select' | 'date' | 'datetime' | 'image' | 'items'
  options?: { label: string; value: number }[]
  placeholder?: string
  /** image 类型：最多几张照片 */
  maxCount?: number
}

/** 各通道最少必填字段——与后端 ChannelHandler.validate 的要求一一对应 */
const DEMO_FIELDS: Record<string, DemoField[]> = {
  FARM_ERP: [
    // 外部养殖系统同步：这条通道模拟的不是设备，而是「养殖场 ERP 把批次档案推过来」。
    // 字段就是 ERP 侧本来记着的那几项（场、批次号、品种、耳标区间、进场/出生日期）；
    // 现实里没人为了溯源再抄一遍，所以这里也没有「录入人」——数据是系统给的，责任在系统。
    { key: 'action', label: '动作', type: 'select', required: true,
      options: [{ label: '新建/更新批次', value: 'UPSERT' }, { label: '源系统撤销', value: 'REVOKE' }] },
    { key: 'farmName', label: '养殖场名称', required: true, placeholder: '必须与本系统养殖场名称一致（对不上转人工，不猜场）' },
    { key: 'batchNo', label: '批次号', required: true, placeholder: '外部系统自己的批次号，如 ERP-20261026-01；场内唯一' },
    { key: 'breed', label: '品种', placeholder: '如 三元杂' },
    { key: 'entryDate', label: '进场日期', type: 'date', placeholder: '外购为进场日，自繁为转育肥日' },
    { key: 'birthDate', label: '出生日期', type: 'date', placeholder: '批次共用的出生日期，日龄由它现算（人工登记一般填不出）' },
    { key: 'earTagStart', label: '耳标起号', placeholder: '与止号一起圈定成员区间；也可在报文里改用耳标明细清单' },
    { key: 'earTagEnd', label: '耳标止号', placeholder: '与起号同前缀' },
    { key: 'voidReason', label: '撤销理由', placeholder: '仅「源系统撤销」时必填，如 录错批次/退养' },
  ],
  TEMPERATURE: [
    { key: 'transportNo', label: '运输单号', required: true, placeholder: '需是「运输中」的运单' },
    // 允许区间与后端 ingest.temperature.range-min/max 一致（-18~0）：落在区间内走正常路径，
    // 越界只会被标 is_abnormal=1（异常值照常入账、且不参与降采样）。示例值给区间内的读数，异常路径留给手工改。
    { key: 'temperature', label: '温度(℃)', required: true, type: 'number', placeholder: '如 -2；允许区间 -18~0，越界会被标为「温度异常」（仍入账）' },
  ],
  ENTRY: [
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '需在生猪批次档案中存在，且该猪的出栏审批已通过' },
    { key: 'batchNo', label: '入场批次号', required: true, placeholder: '如 B-20260921-01' },
    // 入场查验的两道硬前提：① 该猪的出栏审批已通过（未经审批不得入厂）；② 检疫证要存在、未过期、
    // 且证上耳标与闸口扫描的一致。缺任一，设备上报都不会自动入账。
    { key: 'quarantineCert', label: '产地检疫证号', placeholder: '需与耳标一致且在有效期内；点「填充示例值」会自动取一个有效证号' },
    { key: 'weight', label: '地磅重量(kg)', type: 'number', placeholder: '如 112.5' },
    // 查验责任落在人身上：读卡器只是工具，缺查验员会转「待人工确认」
    { key: 'inspector', label: '查验员', required: true, placeholder: '现场查验人员姓名，机器不得代签' },
  ],
  RACTOPAMINE: [
    { key: 'earTagNo', label: '耳标号', required: true },
    { key: 'batchNo', label: '批次号', required: true },
    { key: 'sampleNo', label: '样本编号', required: true, placeholder: '如 S-20260921-01' },
    { key: 'result', label: '检测结果', required: true, type: 'select', options: [{ label: '1 阴性（合格）', value: 1 }, { label: '0 阳性（不合格）', value: 0 }] },
    { key: 'testTarget', label: '检测项目', placeholder: '留空默认克伦特罗' },
    { key: 'samplePart', label: '样品部位', placeholder: '如 尿液' },
    // 检测仪只是工具，检测员是人；缺检测员会转「待人工确认」
    { key: 'operator', label: '检测员', required: true, placeholder: '现场检测人员姓名，机器不得代签' },
  ],
  SALE: [
    { key: 'qrCode', label: '产品二维码', required: true, placeholder: '需是「在库可售」的商品码' },
    { key: 'sellPrice', label: '售价(元)', type: 'number' },
    { key: 'sellWeightKg', label: '重量(kg)', type: 'number' },
  ],
  RECEIPT: [
    { key: 'transportNo', label: '运输单号', required: true, placeholder: '需是「已到达」的运单' },
    { key: 'storeName', label: '门店名称', required: true, placeholder: '如 示范一店' },
    { key: 'receiver', label: '签收人', required: true, placeholder: '门店收货人姓名，责任转移必须落具体人' },
    { key: 'tempValue', label: '到货温度(℃)', type: 'number' },
    { key: 'qtyCheck', label: '数量核对', type: 'select', options: [{ label: '一致', value: 1 }, { label: '不一致', value: 0 }] },
    { key: 'packageIntact', label: '包装完好', type: 'select', options: [{ label: '完好', value: 1 }, { label: '破损', value: 0 }] },
  ],
  TAG: [
    // 读写器能力边界：耳标芯片里只有一个编号，设备只能提供耳标号和读取时刻。
    // 批次信息（养殖场、批次号、品种、进场日期）是人的知识 —— 由人工在「生猪批次档案」
    // 登记批次（耳标区间圈定成员），报文带了也不认。区间外的耳标上报会转「待人工」，
    // 人工登记批次（区间覆盖该号）后回这里确认入账。
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '已登记批次区间内的耳标会幂等返回既有档案；区间外转待人工' },
    // 读取时刻默认取当前时间：让每次演示都是一份「新的采集报文」——同一头猪
    // 再次经过读写器是常态（不是重复上报），没有时刻字段就会撞兜底判重。
    { key: 'readTime', label: '读取时刻', type: 'datetime', placeholder: '读写器读到耳标的时刻' },
  ],
  VACCINE: [
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '需在生猪批次档案中存在' },
    { key: 'vaccineName', label: '疫苗名称', required: true, placeholder: '如 猪瘟兔化弱毒疫苗' },
    { key: 'vaccineBatchNo', label: '疫苗批号', required: true, placeholder: '缺批号会被拒绝入库' },
    { key: 'dosage', label: '剂量', placeholder: '如 2ml' },
    { key: 'injectSite', label: '注射部位', placeholder: '如 耳后颈部' },
    // 注射器自动记录的实际注射时刻（真实设备上报里本就带这个字段）。
    // 默认取当前时间：让每一次演示上报都是一份「新的采集报文」，
    // 否则同一天反复点「填充示例值」生成的报文完全一样，会被判成重复上报而报不进去。
    { key: 'injectTime', label: '注射时间', type: 'datetime', placeholder: '注射器采集的注射时刻' },
    { key: 'operator', label: '操作人', required: true, placeholder: '现场接种兽医姓名，注射器不得代签' },
    // 免疫记录必须有佐证材料：能拍照的终端随报上传，拿不到照片会转「待人工确认」补传
    { key: 'fileIds', label: '接种凭证', required: true, type: 'image', maxCount: 1, placeholder: '接种现场照片，jpg/png，单张不超过 5MB' },
  ],
  QUARANTINE: [
    // 扫码登记：字段全部来自检疫单证面（二维码），设备不产生任何检疫结论
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '证面登记的耳标，需在生猪批次档案中存在' },
    { key: 'certNo', label: '检疫证编号', required: true, placeholder: '证号全局唯一，重复登记会返回既有记录' },
    { key: 'issueOrg', label: '签发机构', required: true, placeholder: '如 顺义区动物卫生监督所' },
    { key: 'issueTime', label: '签发时间', required: true, type: 'datetime', placeholder: '证面签发时刻' },
    { key: 'validUntil', label: '有效期至', type: 'date', placeholder: '过期证入不了场，留空按长期有效处理' },
    // 签发人只能取自证面：扫码终端是工具，缺官方兽医会转「待人工确认」
    { key: 'inspector', label: '官方兽医', required: true, placeholder: '证面签发的官方兽医姓名，扫码终端不得代签' },
    { key: 'fileIds', label: '检疫证扫描件', type: 'image', maxCount: 1, placeholder: '可选：只有扫码枪的终端没有照片，能拍照的手持机可上传' },
  ],
  EXIT_APPLY: [
    // 出栏口地磅：机器能给的只有「哪头猪、多重、什么时候称的」
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '地磅读到的耳标，需在生猪批次档案中且状态为在养' },
    { key: 'weightKg', label: '出栏体重(kg)', required: true, type: 'number', placeholder: '地磅实测读数，合理范围 20–400' },
    // 采集时刻：与注射时间同理，让每次演示都是一份新的采集报文，避免被判重复上报
    { key: 'weighTime', label: '称重时间', type: 'datetime', placeholder: '地磅采集的称重时刻' },
    // 去向是「声明」不是「测量」：地磅给不出，留空会转「待人工确认」由人工补录
    { key: 'targetSlaughterhouse', label: '目标屠宰场', placeholder: '去向声明，留空会转「待人工确认」由人工补录' },
  ],
  INSPECTION: [
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '需在生猪批次档案中存在' },
    { key: 'batchNo', label: '批次号', required: true },
    { key: 'inspectType', label: '检验类型', type: 'select', options: [{ label: '宰前检验', value: 1 }, { label: '宰后检验', value: 2 }, { label: '宰后检验（同步）', value: 3 }] },
    { key: 'result', label: '检验结论', required: true, type: 'select', options: [{ label: '1 合格', value: 1 }, { label: '0 不合格', value: 0 }], placeholder: '判定权在兽医，终端只录入' },
    { key: 'veterinary', label: '检验人(兽医)', required: true, placeholder: '机器不得代签' },
    { key: 'temperature', label: '体温(℃)', type: 'number', placeholder: '宰前检验用，如 38.6' },
    { key: 'conclusion', label: '结论描述', placeholder: '如 体表/脏器无可见病变' },
  ],
  STAMP: [
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '须有一枚章的余量：同一头白条只有一枚章，已盖过章的不能再盖' },
    { key: 'batchNo', label: '批次号', required: true },
    { key: 'carcassNo', label: '胴体编号', required: true, placeholder: '如 CT-20260922-153012；编号重复会被当作「该胴体已有盖章记录」直接回既有记录' },
    { key: 'veterinary', label: '授权兽医', required: true, placeholder: '盖章授权必须落具体人' },
    { key: 'stampType', label: '印章类型', placeholder: '留空默认检疫合格章' },
  ],
  BATCH: [
    // 注意这里没有「胴体批次号」：批次边界与批次号都由系统按「屠宰场 + 屠宰日」算出来，
    // 设备只报它测得到的单头事实。让设备报文带批次号，等于让一台称重台决定业务分组。
    { key: 'earTagNo', label: '耳标号', required: true, placeholder: '需已完成胴体盖章（即已过宰后同步检疫）' },
    { key: 'slaughterhouse', label: '屠宰场', required: true, placeholder: '如 示范定点屠宰场；归集口径是「屠宰场 + 屠宰日」' },
    { key: 'carcassWeightKg', label: '胴体重量(kg)', required: true, type: 'number', placeholder: '分拣台过磅读数，如 78.5' },
    { key: 'carcassNo', label: '胴体编号', placeholder: '可选，如 CT-20260922-001' },
    { key: 'lineNo', label: '分拣产线', placeholder: '可选，如 白条一线' },
  ],
  SPLIT: [
    { key: 'parentBatchNo', label: '胴体批次号', required: true, placeholder: '需是已存在的白条批次，如 CB20261009-001' },
    { key: 'earTagNo', label: '白条耳标号', placeholder: '选填：填了按这头猪的实际重量对账，不填按批次单头均重' },
    // 一次上报 = 一头白条的整份产出清单：分割是固定部位切割，一头猪本来就会切出多个部位
    { key: 'items', label: '产出部位清单', type: 'items', required: true },
    { key: 'workshop', label: '车间', placeholder: '如 分割一号线' },
    { key: 'lineNo', label: '产线/班次', placeholder: '如 1号线' },
    // 分割线终端只是工具，操作员是人；缺操作员会转「待人工确认」
    { key: 'operator', label: '操作员', required: true, placeholder: '现场分割操作人员姓名，机器不得代签' },
  ],
  LOAD: [
    // 装车口是「一个点位、一个通道，但不是一台设备」：车牌识别相机 + 扫码枪
    // 两件套共同构成一次「装车出场」事件。这里没有「目的地」——那是人在发货计划里定死的，
    // 设备测不出来也不该猜。
    { key: 'vehicleNo', label: '车牌号', required: true, placeholder: '车牌识别相机读到的车牌，如 京A·12345' },
    // 装车口**没有读卡器**（2026-10-10 起）：现场只在上车那一刻刷一次卡（发车确认通道）。
    // 所以这里既没有卡号，司机也可以留空 —— 装车时司机还没上车，等他刷卡时会自动补上。
    { key: 'driverName', label: '司机姓名', placeholder: '可留空：装车时司机还没上车，等他在车上刷卡时会写进运单；填了也只算「人工录入」' },
    { key: 'driverPhone', label: '司机电话', placeholder: '选填' },
    { key: 'batchNos', label: '装车批次号', required: true, placeholder: '扫码枪扫出的分割批次号，多件用英文逗号分隔，如 SP20261009-001-01,SP20261009-001-02' },
    // 装车时刻：与其它通道同理，让每次演示都是一份新的采集报文，避免被判重复上报
    { key: 'loadTime', label: '装车时间', type: 'datetime', placeholder: '装车口采集的装车时刻' },
  ],
  ORDER: [
    // 订单转入：这里模拟的是「外部系统把选购结果推过来」，不是本系统在卖货。
    // 所以没有金额、没有支付、没有买家资料 —— 只收「要什么货、发去哪家店、什么时候要」。
    { key: 'orderNo', label: '订单号', required: true, placeholder: '外部系统的订单号，如 SO88001。同一个订单号重复推送只认第一条（幂等）' },
    // 门店必须是机构树里真实存在的门店；乱填 id 会转「待人工确认」而不是默认放行
    { key: 'storeId', label: '目的地门店', required: true, type: 'number', placeholder: '机构树里的门店 id，如 1001（安心生鲜门店）。查不到会转人工' },
    { key: 'expectArrive', label: '期望到货', type: 'datetime', placeholder: '订单要求的到货时间' },
    // 商家选购的粒度是「部位 + 重量」，不是具体批次：装车时装哪几批由系统按可发运库存配
    { key: 'items', label: '订购部位清单', type: 'items', required: true },
    { key: 'remark', label: '订单备注', placeholder: '选填' },
  ],
  DEPART: [
    // 发车确认：设备只报「我（某车）发车了」，运单号由系统按车牌倒查 ——
    // 刷卡是「确认」不是「选择」，所以这里没有运单号，也没有目的地。
    { key: 'vehicleNo', label: '车牌号', required: true, placeholder: '车载终端识别到的车牌，如 京A·12345；留空则取设备台账绑定的车牌' },
    // 与装车口同一套司机口径：卡号优先（机器读的，本人确认），没卡才用姓名。
    // 两样都没有会按车牌反查备案常用司机，但那只是推定，仍转人工。
    { key: 'cardNo', label: '司机卡号', placeholder: '司机上车刷的卡号（必须是他在司机备案里登记的那张）。有卡号时以卡号换人，比手填姓名可靠' },
    { key: 'driverName', label: '司机姓名', placeholder: '没刷卡时填；刷卡后由卡号自动换成持卡人，此项可留空' },
    { key: 'driverPhone', label: '司机电话', placeholder: '选填' },
    // 发车时刻：与其它通道同理，让每次演示都是一份新的采集报文，避免被判重复上报
    { key: 'departTime', label: '发车时间', type: 'datetime', placeholder: '车载终端采集的发车时刻，留空取服务器当前时间' },
  ],
  ARRIVE: [
    // 到达确认：与发车同一套口径 —— 设备只报现场事实（哪台车、司机刷的是哪张卡、什么时刻），
    // 运单按车牌倒查、站点按顺序推导。这里没有运单号，也没有「第几站」，**更没有卸货门店**：
    // 车载终端装的是 T-Box / 读卡头 / 显示屏，读得到车牌、卡号、时刻、GPS，读不到门店主数据
    //（门店编码是 ERP/OMS 的，不会下发到每台车上）。而「到了第几站」在服务端本来就唯一确定 ——
    // 车牌锁定了唯一一张未结束的运单，站点顺序由装车扫码顺序定，所以设备报不了站，也报不错站。
    { key: 'vehicleNo', label: '车牌号', required: true, placeholder: '车载终端识别到的车牌，如 京A·12345' },
    // 司机到站刷的卡：到达必须由本人刷卡确认（与发车是**同一台车载终端**）。
    // 没刷卡会被判「谁把货送到这一站？」答不出来 → 转人工，不拿车牌或人名顶替。
    { key: 'cardNo', label: '司机卡号', required: true, placeholder: '司机到站后在车载终端刷的卡号，必须与运单安排的司机是同一个人；刷成别人的卡会被判「已拒绝」' },
    // 到达时刻：与其它通道同理，让每次演示都是一份新的采集报文，避免被判重复上报
    { key: 'arriveTime', label: '到达时间', type: 'datetime', placeholder: '车载终端采集的到达时刻，留空取服务器当前时间' },
  ],
  RETURN_LOAD: [
    // 拒收回运装车：冷链中心装车位上的**专用**扫码枪，对齐装车口的「车牌 + 货码 → 倒查」。
    // 2026-11-03 起扫码前移到排车之前：现场装车时还没有处理运单号，扫完才生成处理运单。
    { key: 'vehicleNo', label: '车牌号', required: true, placeholder: '相机识别到的车牌，如 京A·12345：这批货上了哪台车，就装到哪台车上' },
    // 货物码：拒收包装上就这两种标签（原运单号 / 分割批次号），扫到哪个报哪个
    { key: 'scanCodes', label: '货物码', required: true, placeholder: '扫到的拒收货标签，多件用英文逗号分隔，如 TR20261103002,SP20261103-001-01' },
    // 处理运单号可选：单号要等生成计划后才有，补扫时可以带上，不参与判定
    { key: 'legTransportNo', label: '处理运单号', placeholder: '选填：补扫已生成的运单时带上，如 TR20261103001；主流程不需要' },
    // 扫码时刻：与其它通道同理，让每次演示都是一份新的采集报文，避免被判重复上报
    { key: 'scanTime', label: '扫码时间', type: 'datetime', placeholder: '扫码枪采集的时刻，留空取服务器当前时间' },
  ],
  RECALL_COLLECT: [
    // 召回到店收货：随车手持扫码枪，和装车扫码同构 —— 只报「车牌 + 货码」，两步都由系统自己倒查：
    // 车牌锁唯一一张在途收运运单，运单锁当前「已到达、还没收完」的站，货码再锁这一站的召回货。
    { key: 'vehicleNo', label: '车牌号', required: true, placeholder: '收运车的车牌，如 京A·12345：靠它认出「正在收的是哪一趟」' },
    { key: 'scanCodes', label: '货物码', required: true, placeholder: '扫到的召回货批次标签，多件用英文逗号分隔，如 SP20261104-001-01' },
    // 扫码时刻：让每次演示都是一份新的采集报文，避免被判重复上报
    { key: 'scanTime', label: '扫码时间', type: 'datetime', placeholder: '扫码枪采集的时刻，留空取服务器当前时间' },
  ],
  SPLIT_SNAPSHOT: [
    // 分割车间监控：唯一一条「只落影像、不产生溯源事实」的通道。
    // 两段式上报：照片先上传文件服务换回 fileId，再随报文报进来（图片塞不进 JSON）。
    // 没有业务前置判定 —— 不判瘦肉精、不判环节顺序，照片只是「这个时刻车间是什么样」。
    // 正常路径下不需要人来这里报：分割线上报之后相机会自动连拍（见 utils/splitCamera.ts），
    // 这一条留给「相机没配 / 单独补一张现场照片」的兜底。
    { key: 'fileIds', label: '车间照片', required: true, type: 'image', maxCount: 1, placeholder: '现场照片，jpg/png，单张不超过 5MB；接入层会核对照片真实存在，编造的 fileId 会被判「已拒绝」' },
    // 拍摄时刻取设备侧时间：与温度打卡同一条纪律 —— 现场时刻不能拿入库时刻顶替
    { key: 'capturedTime', label: '拍摄时间', type: 'datetime', placeholder: '相机采集的拍摄时刻，留空取服务器当前时间' },
    { key: 'workshop', label: '车间', placeholder: '如 分割一号线' },
    // 绑定：工位终端告知相机「当前正在分割的白条属于哪个批次」——带上就归属到该批次，
    // 分割操作页按批次展示；不带就只是现场影像（不落归属）。查不到的批次号会被判「编造绑定」拒绝。
    { key: 'batchNo', label: '所属胴体批次', placeholder: '选填：如 CB20261009-001；带上则这张照片归属到该批次（页面按批次展示）' },
  ],
}

/** 通道 → 目标业务表（与后端 IngestChannel 一致，仅用于界面提示） */
const CHANNEL_TABLES: Record<string, string> = {
  FARM_ERP: 'pig_batch',
  TEMPERATURE: 'temperature_log',
  ENTRY: 'entry_inspection',
  RACTOPAMINE: 'ractopamine_test',
  SALE: 'retail_sale',
  RECEIPT: 'store_receipt',
  TAG: 'pig_individual',
  VACCINE: 'vaccine_record',
  QUARANTINE: 'quarantine_certificate',
  EXIT_APPLY: 'slaughter_apply',
  INSPECTION: 'slaughter_inspection',
  STAMP: 'carcass_stamp',
  BATCH: 'carcass_batch',
  SPLIT: 'split_batch',
  ORDER: 'delivery_plan',
  LOAD: 'cold_chain_transport',
  DEPART: 'cold_chain_transport',
  // 到达确认是唯一一条写「站点」的通道：一车多站时到达要逐站记，运单级装不下
  ARRIVE: 'transport_stop',
  // 拒收回运装车扫码：不建单、不推状态，只核对已有回运单的货物清单（逐件证据落 leg_item）
  RETURN_LOAD: 'return_load_scan',
  // 召回到店收货：这一枪是「证明货被谁收走了」的现场证据，逐枪落 recall_collect_scan
  RECALL_COLLECT: 'recall_collect_scan',
  // 分割车间监控：唯一一条不产生溯源事实的通道，只落一张带时间戳的现场影像快照
  SPLIT_SNAPSHOT: 'workshop_snapshot',
}

/**
 * 业务数据表 → 中文叫法。
 * 表名是实现细节：界面一律展示中文，表名只放进 tooltip 供技术人员排查。
 * 覆盖 12 个溯源环节，与后端 TraceStage 对齐。
 */
const TABLE_TARGET_OBJECTS: Record<string, string> = {
  // 批次档案是养殖端的登记单位；个体行是它按耳标展开出来的成员
  pig_batch: '生猪批次档案（外部系统同步）',
  pig_individual: '生猪档案（批次成员）',
  vaccine_record: '免疫注射记录',
  quarantine_certificate: '产地检疫证明',
  slaughter_apply: '出栏申报记录',
  entry_inspection: '入场查验记录',
  ractopamine_test: '瘦肉精检测记录',
  slaughter_inspection: '屠宰检验记录',
  carcass_stamp: '胴体盖章记录',
  carcass_batch: '胴体批次记录',
  split_batch: '分割作业与部位清单',
  delivery_plan: '发货计划（订单转入）',
  cold_chain_transport: '冷链运单（装车发运）',
  transport_stop: '车次站点行程（到达确认）',
  temperature_log: '冷链温度记录',
  store_receipt: '门店签收记录',
  retail_sale: '门店销售记录',
  workshop_snapshot: '分割车间监控快照',
}

/** 通道 → 数据写入的业务对象（由通道对应的表名派生，避免两处各维护一份） */
const CHANNEL_TARGET_OBJECTS: Record<string, string> = Object.fromEntries(
  Object.entries(CHANNEL_TABLES).map(([channel, table]) => [channel, TABLE_TARGET_OBJECTS[table] || '业务记录']),
)

/** 数据去向的中文说法：优先按通道取；没有通道（如产地检疫出证）时按表名反查兜底 */
function targetObjectLabel(channel?: string, table?: string) {
  if (channel && CHANNEL_TARGET_OBJECTS[channel]) return CHANNEL_TARGET_OBJECTS[channel]
  if (table && TABLE_TARGET_OBJECTS[table]) return TABLE_TARGET_OBJECTS[table]
  return '业务记录'
}

const demoVisible = ref(false)
const demoSubmitting = ref(false)
const sampling = ref(false)

/**
 * 分割上报之后的「车间相机自动连拍」状态。
 *
 * 分割线扫码称重台一上报，就代表**这头白条已经开切** —— 工位上的车间相机随之自动连拍，
 * 不需要人再跑到这一页手动传照片（现实里相机本来就是自动的）。
 * 连拍的节奏与张数见 utils/splitCamera.ts（当前 2 秒一张、共 3 张，演示口径）。
 */
const shotState = reactive({
  visible: false,
  running: false,
  done: 0,
  total: SNAPSHOT_COUNT,
  deviceNo: '',
  batchNo: '',
  failed: 0,
  text: '',
})

/** 「分割车间监控」相机 —— 整本台账里只有一台，按通道认（不按设备名认，改名也不影响） */
function findSnapshotCamera() {
  return devices.value.find((d: any) => d.channel === SNAPSHOT_CHANNEL)
}

/**
 * 分割上报入账后的自动连拍：照片逐张上传文件服务、再走 SPLIT_SNAPSHOT 通道报进来，
 * 每张都带上这次分割的批次号（绑定依据）。
 *
 * 失败不抛给上层：连拍是**附带动作**，不能因为照片传失败就把「分割已入账」这件事搅浑 ——
 * 页面照常展示上报回执，只在旁边说明照片的成败。
 */
async function autoShootAfterSplit(batchNo: string, workshop?: string) {
  const camera = findSnapshotCamera()
  if (!camera) {
    ElMessage.warning('没找到「分割车间监控」相机（通道 SPLIT_SNAPSHOT），本次未自动拍摄')
    return
  }
  shotState.visible = true
  shotState.running = true
  shotState.done = 0
  shotState.failed = 0
  shotState.deviceNo = camera.deviceNo
  shotState.batchNo = batchNo
  shotState.text = '分割线已开切，车间相机正在连拍现场照片…'
  try {
    const r = await autoShootSplitSnapshots({
      cameraDeviceNo: camera.deviceNo,
      batchNo,
      workshop,
      onProgress: (done, total) => {
        shotState.done = done
        shotState.total = total
      },
    })
    shotState.failed = r.failed
    if (r.failed === 0) {
      shotState.text = `已自动上传 ${r.uploaded} 张现场照片并绑定到批次 ${batchNo}，`
        + '可到「分割操作」页的「分割车间监控」查看。'
      ElMessage.success(`车间相机已连拍 ${r.uploaded} 张，已绑定批次 ${batchNo}`)
    } else {
      shotState.text = `成功 ${r.uploaded} 张、失败 ${r.failed} 张`
        + (r.firstError ? `（${r.firstError}）` : '')
      ElMessage.warning(`车间相机连拍完成：成功 ${r.uploaded} 张、失败 ${r.failed} 张`)
    }
  } catch (e: any) {
    shotState.text = e?.message || '车间相机连拍失败'
    ElMessage.warning(shotState.text)
  } finally {
    shotState.running = false
  }
}
const sampleHint = ref('')
/**
 * 「填充示例值」取不到数据时给出的**下一步入口**。
 * <p>
 * 车载终端的发车 / 到达确认推的是**运单**，而「排车」（在计划里填上车牌）与「生成运单」在界面上
 * 是两步 —— 只填了车牌时运单还不存在，示例值自然取不到。光用文字说「请先生成运单」，
 * 使用者还得自己去找那个页面；这里直接给一个能点的入口，把最后一步接上。
 */
const sampleJump = ref<{ text: string; to: any } | null>(null)
/**
 * 取不到示例值、差的又只是「一张业务单」时的**就地动作**（比 sampleJump 更进一步）。
 * <p>
 * 处理计划里有召回货、但还没排成收运运单时，车载终端没有车可发 —— 用户得先去别的页面点生成再回来。
 * 这里把「按处理计划生成收运运单」直接做成提示里的按钮：点一下就地出单，再自动重填示例值。
 * 与装车口「扫码即出单」同一条道理，只是召回的货不走扫码，走的是排车。
 */
const sampleAction = ref<{ text: string; run: () => Promise<void> } | null>(null)
const router = useRouter()
const demoResult = ref<any>(null)
/** 模拟上报弹窗里照片缩略图列表（el-upload 双向绑定；fileId 存在 demoForm.data.fileIds） */
const demoPhotoFiles = ref<any[]>([])
const demoForm = reactive<{
  deviceNo: string
  channel: string
  bizKey: string
  reportTime: string
  data: Record<string, any>
}>({
  deviceNo: '',
  // 一台车载终端承担两个通道（发车确认 + 到站刷卡确认）时，由人指定这一报算哪个动作；
  // 只有一个通道的设备留空，通道由设备自身决定。
  channel: '',
  bizKey: '',
  reportTime: '',
  data: {},
})

/** 分割产出部位清单：一次上报就是这头白条的整份产出，编辑态单独维护、提交前同步进 demoForm.data.items */
const demoItems = ref<Array<{ partCode: string; weightKg?: number; packageCount?: number }>>([])
/** 标准部位字典：设备只能报这里的编码（与后端 meat_part 字典同源） */
const meatPartOptions = ref<Array<{ partCode: string; partName: string; required: number }>>([])
/** 默认三行 = 行业三段主产（前腿/大排/后腿），也正是字典里的必出项：缺一项就会转人工复核 */
const SPLIT_DEFAULT_PARTS: Array<{ partCode: string; weightKg: number }> = [
  { partCode: 'P02', weightKg: 18.5 },
  { partCode: 'P03', weightKg: 15.5 },
  { partCode: 'P04', weightKg: 19.0 },
]

function addItemRow() {
  demoItems.value.push({ partCode: '', weightKg: undefined, packageCount: 1 })
}

function removeItemRow(idx: number) {
  demoItems.value.splice(idx, 1)
}

function resetDemoItems() {
  demoItems.value = SPLIT_DEFAULT_PARTS.map((p) => ({ ...p, packageCount: 1 }))
}

async function loadMeatParts() {
  try {
    const list: any = await distributionApi.getMeatParts()
    meatPartOptions.value = Array.isArray(list) ? list : []
  } catch {
    meatPartOptions.value = []
  }
}

const enabledDevices = computed(() => devices.value.filter((d) => d.status === 1))
const demoDevice = computed(() => devices.value.find((d) => d.deviceNo === demoForm.deviceNo))
/**
 * 本机（选中的设备）绑定的车牌。**车载终端装在车上**：台账 `vehicle_no` 就是它的车牌，
 * 设备名里也写着车号（「车载终端（京A·12345）」），所以它只能报自己这一辆车。
 * 通用终端（`ARRIVE-001`）不绑车，这里为空 —— 示例才退回「任意一趟符合状态的车」。
 */
const demoDevicePlate = computed(() => demoDevice.value?.vehicleNo || '')
const demoChannel = computed(() => demoForm.channel || demoDevice.value?.channel || '')
/**
 * 选中设备能上报的全部通道（由后端按 IngestChannel.deviceGroup() 算好）。
 * 只有车载终端会多于一个：一台设备既确认发车也确认到站刷卡 —— 这时页面上必须让人
 * 指定这一次是哪个动作，光「选设备」定不了通道。
 */
const demoChannelOptions = computed<string[]>(() => (demoDevice.value?.channels?.length
  ? demoDevice.value.channels
  : [demoDevice.value?.channel].filter(Boolean)) as string[])
const demoTargetTable = computed(() => CHANNEL_TABLES[demoChannel.value] || '')
const demoTargetObject = computed(() => targetObjectLabel(demoChannel.value))
const demoFields = computed(() => DEMO_FIELDS[demoChannel.value] || [])
const demoRawOpen = ref<string[]>([])
const demoResultTag = computed<EpTagType>(() => {
  const r = demoResult.value
  if (!r) return 'info'
  if (r.accepted) return 'success'
  // 重复上报不是故障、也不是脏数据：数据早就在系统里了，只是这次没再入一遍
  if (r.status === 5) return 'warning'
  return r.status === 1 ? 'warning' : 'danger'
})

/** 回执的「数据去向」：成没成、写到哪、有没有生成记录 */
const demoResultDest = computed(() => {
  const r = demoResult.value
  if (!r) return ''
  if (r.accepted) {
    const obj = targetObjectLabel(r.channel, r.targetTable)
    return r.targetId ? `已写入「${obj}」，记录编号 ${r.targetId}` : `已写入「${obj}」`
  }
  // 重复上报：数据早就在系统里了，本次明确拒绝入账。
  // 这里不能显示「已写入 xxx」——那正是「重推一次看着像又写了一条」的误导来源。
  if (r.duplicate) return '该信息已存在，本次拒绝入账（未写入任何业务数据）'
  if (r.status === 3) return '未写入任何业务数据，原始报文已留档备查'
  if (r.status === 4) return '未写入（抽稀丢弃属正常，不算故障）'
  return '尚未写入，原始报文已留在「待确认队列」，可人工确认入账或拒绝'
})

/**
 * 这次上报「之后」业务对象会进入什么状态 —— 只用来把因果说清楚，判定仍在各业务服务里。
 * 口径必须与真实状态机一致（状态词改了这里要跟着改）：
 *   装车口 LOAD：这批货排进运单 → **发货计划转「已排车」**；车还没走，运单仍停在「待发车」。
 *   车载终端 DEPART：司机刷卡确认发车 → 运单**「已发车」**，列表状态从「待发车」转「运输中」。
 *   车载终端 ARRIVE：司机到站刷卡确认到达 → **该站转「已到达」**；站点全到齐运单才转「待签收」。
 */
const CHANNEL_AFTER_EFFECT: Record<string, string> = {
  LOAD: '发货计划转「已排车」（车还没走，运单仍停在「待发车」）',
  DEPART: '运单「已发车」——列表状态从「待发车」转「运输中」',
  ARRIVE: '这一站转「已到达」——站点全部到齐，运单才转「待签收」',
  // 扫码登记装车事实：处理计划里的货标「已装车（车牌）」，生成处理运单后进清单；运单状态仍由刷卡推进
  RETURN_LOAD: '扫码即出单：自动生成（或并入）该车的处理运单并置「待发车」——司机刷卡即可发车',
  // 收货扫码不推运单状态：运单推进仍只由车载终端的发车/到达负责，这一枪只把货标成「已收上车」
  RECALL_COLLECT: '这一站的召回货转「已收货」——本站全收完，司机才能刷卡去下一家',
}

/** 后置状态只在「这次真的入账了」时才成立：转人工 / 被拒都不会有业务状态变化 */
const demoResultAfter = computed(() => {
  const r = demoResult.value
  if (!r?.accepted) return ''
  return CHANNEL_AFTER_EFFECT[r.channel] || ''
})

/**
 * 这次上报已被系统受理后，把「上报」按钮置灰。
 * 受理 = 已入账 / 重复上报 / 待人工确认 —— 这三种情况下再点一次只会拿到同一份回执
 * （重复那次的回执是「该信息已存在，拒绝入账」），不该让用户白点。
 * 已拒绝 / 已降采样不锁：改完数据本来就要能重报。
 */
const demoReportLocked = computed(() => {
  const r = demoResult.value
  if (!r) return false
  if (r.duplicate) return true
  if (r.accepted) return true
  return r.status === 1
})

const demoLockHint = '本次上报已被系统受理'

// 表单内容一变，上一次的回执就不再对应当前填写内容，直接清掉并解锁按钮
watch(
  () => [demoForm.deviceNo, demoForm.bizKey, demoForm.reportTime, JSON.stringify(demoForm.data)],
  () => {
    if (demoResult.value) {
      demoResult.value = null
      demoRawOpen.value = []
    }
    // 连拍结果与那份回执是同一件事的两面（同一次分割），表单改了就不再对应，一起清掉
    shotState.visible = false
  },
)

function openDemo() {
  demoVisible.value = true
  if (!demoForm.deviceNo && enabledDevices.value.length) {
    demoForm.deviceNo = enabledDevices.value[0].deviceNo
    onDemoDeviceChange()
  }
}

function resetDemo() {
  demoResult.value = null
  demoRawOpen.value = []
  shotState.visible = false
  sampleHint.value = ''
  sampleJump.value = null
  sampleAction.value = null
  demoForm.bizKey = ''
  demoForm.reportTime = ''
  applyFieldDefaults()
}

/** 切换设备即切换通道，字段结构随之变化，默认值重新灌一遍 */
function onDemoDeviceChange() {
  demoResult.value = null
  demoRawOpen.value = []
  sampleHint.value = ''
  sampleJump.value = null
  sampleAction.value = null
  // 换设备时**预选**该设备登记的主通道，而不是留空：
  // 车载终端一台设备两个通道（发车确认 / 到达确认），留空会兜底成台账登记的那个通道，
  // 可下拉却是空白的 —— 于是「字段显示的是发车、示例值填的也是发车时间」，
  // 而用户想演示到达时压根看不出要去切这个下拉（发车/到达的字段结构并不相同）。
  // 预选之后下拉有值：当前是哪一次动作一眼可见，要改直接切。单通道设备不显示下拉、行为不变。
  demoForm.channel = demoDevice.value?.channel || ''
  applyFieldDefaults()
}

function applyFieldDefaults() {
  const data: Record<string, any> = {}
  for (const f of demoFields.value) {
    if (f.type === 'select' && f.options?.length) {
      data[f.key] = f.options[0].value
    } else if (f.type === 'image') {
      data[f.key] = []
    } else if (f.type === 'items') {
      data[f.key] = []
    } else if (f.type === 'datetime') {
      // 设备采集时刻默认取「此刻」：既是真实设备的行为，也保证每次演示都是一份新报文
      data[f.key] = nowText()
    } else {
      data[f.key] = ''
    }
  }
  // 每次打开/切通道都换一个唯一号，保证能反复触发
  demoForm.bizKey = ''
  demoForm.data = data
  demoPhotoFiles.value = []
  // 带「部位清单」的通道（分割产出 / 订单订购）都从标准部位默认值起步
  if (demoFields.value.some((f) => f.type === 'items')) resetDemoItems()
}

// 部位清单是编辑态、报文里是 data.items：这里单向同步，保证提交时拿到的永远是最新一份
watch(
  demoItems,
  (rows) => {
    demoForm.data.items = rows
      .filter((r) => r.partCode && r.weightKg != null && Number(r.weightKg) > 0)
      .map((r) => ({ partCode: r.partCode, weightKg: Number(r.weightKg), packageCount: r.packageCount || 1 }))
  },
  { deep: true },
)

/**
 * 演示用「司机姓名 → IC 卡号」对照，与 backend/init-sql/29-driver-vehicle-master.sql 的种子一致。
 * <p>
 * 刷卡只发生在**车载终端**上：一是上车那一刻的发车确认，二是到站那一次（司机刷卡确认到达）。
 * 两处是同一台设备、同一个读卡头，也是「司机本人确认」的全部来源（装车口没有读卡器，
 * 装车时司机还没上车）。到站那一刷还要与运单安排的司机一致 —— 不一致直接判「已拒绝」，
 * 司机重刷即可，不占人工队列。
 * <p>
 * <b>取卡口径（2026-10-11 收紧）：只按运单安排的那位司机取卡，取不到就不填。</b>
 * 原先取不到一律兜底成 8801（张师傅），而演示库里在跑的运单安排的是「吴师傅」「周司机」
 * 这类**没进备案**的名字 —— 于是示例卡必然刷出一个与运单不符的人：发车侧被判
 * 「现场刷卡的人 ≠ 运单安排的人」转人工，到站侧<b>直接判「已拒绝」</b>。
 * 示例值默认演示失败路径是本末倒置，所以查不到就留空，并在提示语里说清为什么、怎么办。
 */
const DEMO_DRIVER_CARDS: Record<string, string> = { '张师傅': '8801', '李四': '8802', '王五': '8803' }

/** 运单还没安排司机时，用这张卡演示「刷卡赋身份」（这一刷会把司机写成张师傅） */
const DEMO_FALLBACK_CARD = '8801'
const DEMO_FALLBACK_DRIVER = '张师傅'

/**
 * 卡能不能刷出「运单安排的这个人」——**查得到才填**。
 * 司机为空是另一种情况（无人可比对，刷卡本身就是赋予身份的动作），由调用方单独处理。
 */
function cardOf(driverName?: string | null): string | undefined {
  return driverName ? DEMO_DRIVER_CARDS[driverName] : undefined
}

/**
 * 挑单顺序：把「刷卡能刷出本人」的运单排到前面，供发车/到达两个车载终端通道的示例值使用。
 * <p>
 * 排序依据是<b>这张单刷下去会不会成功</b>，而不是单号新旧：
 * ① 司机在演示卡表里（刷卡人 = 运单安排的人，走「入账」）→ 最优先；
 * ② 运单上还没有司机（装车口不采集，刷卡即赋身份）→ 次之；
 * ③ 安排了别的人（不在备案卡表里，刷谁都会被判上错车）→ 垫底，仍在提示语里说明。
 * 演示库里就有 ③ 这种单子（司机写着「吴师傅」「周司机」），先挑它必然演示成转人工/已拒绝，
 * 所以只在没有 ① ② 时才退回它 —— 退回时卡号留空，不替现场编一张卡。
 */
function preferCardResolvable<T extends { driverName?: string | null }>(list: T[]): T[] {
  const rank = (t: T) => {
    const name = t.driverName
    if (name && DEMO_DRIVER_CARDS[name]) return 0
    return name ? 2 : 1
  }
  // Array.prototype.sort 是稳定排序：同级保持服务端返回的顺序
  return [...list].sort((a, b) => rank(a) - rank(b))
}

/**
 * 冷链温度的演示读数：<b>必须落在允许区间内</b>（后端 ingest.temperature.range-min/max = -18~0）。
 * <p>
 * 原先给的是 2.5℃ —— 一填就被标 is_abnormal=1，示例值默认演示的却是「温度异常」这条路。
 * 想看异常标记，把这个读数手工改到区间外即可（异常值照常入账，只是不进降采样）。
 */
const DEMO_TEMPERATURE = -2

/**
 * 处理计划卡在哪一步 —— 车载终端填不出示例值时，这句话才是使用者真正需要的。
 * <p>
 * 「没有待发车运单」有四种完全不同的原因，笼统回一句「没有数据」等于让人干瞪眼：
 * ① 召回货还没生成收运运单（召回不走装车口扫码，必须在处理运输页生成）；
 * ② 拒收货还没扫码装车（扫码即出单，扫了才有「待发车」的车）；
 * ③ 扫过码但车被占用，货留在计划里（一车一趟）；
 * ④ 计划是空的（返回 null，由调用方说「确实没有」）。
 */
async function describePoolBlockers(): Promise<{
  text: string
  jump?: { text: string; to: any }
  action?: { text: string; run: () => Promise<void> }
} | null> {
  const pool = (await safeList(() => distributionApi.returnPool('DISPOSAL'))) || []
  const rows: any[] = Array.isArray(pool) ? pool : []
  const brief = (list: any[]) => list.slice(0, 4).map((r) => r.batchNo || r.transportNo).join('、')
    + (list.length > 4 ? ' 等' : '')
  const recallPending = rows.filter((r) => r.source === 'RECALL' && !r.scanTime)
  const rejectPending = rows.filter((r) => r.source !== 'RECALL' && !r.scanTime)
  const rejectScanned = rows.filter((r) => r.source !== 'RECALL' && r.scanTime)
  if (recallPending.length) {
    return {
      text: `处理计划里有 ${recallPending.length} 批召回货（${brief(recallPending)}）还没排车：`
        + '召回的货不走「装车扫码出单」那条路，得先按门店排成一张收运运单（末站无害化处理中心），'
        + '那才是这台终端能发的车。点旁边的按钮就地生成，生成后自动重填示例值。',
      // 就地出单：省掉「跳去处理运输 → 选车 → 生成 → 回来重填」这一圈，避免使用者以为示例值坏了
      action: { text: '按处理计划生成收运运单', run: () => generateCollectFromPlan(recallPending.length) },
    }
  }
  if (rejectPending.length) {
    return {
      text: `处理计划里有 ${rejectPending.length} 站拒收货（${brief(rejectPending)}）还没扫码装车：`
        + '先用「拒收回运装车」通道扫一次「车牌 + 货码」—— 扫码即出单，处理运单当场生成并停在「待发车」，'
        + '那才是这台终端要发的车。',
    }
  }
  if (rejectScanned.length) {
    const busy = await fetchBusyPlates()
    const plates = [...new Set(rejectScanned.map((r) => r.scanPlate).filter(Boolean))] as string[]
    const blocked = plates.filter((p) => busy.has(p))
    return {
      text: `处理计划里有 ${rejectScanned.length} 站拒收货已经扫过码（${brief(rejectScanned)}，`
        + `扫码的车：${plates.join('、') || '未记录'}），但没有生成运单 —— `
        + (blocked.length
          ? `${blocked.join('、')}名下还有没送完的运单（一车一趟），这批货当时没装上那台车、留在了计划里：`
            + '把那趟运单送到签收后重扫一次即可出单。'
          : '扫码出单被拦下了：换一台空闲备案车重扫一次即可（扫码即出单，不用再点别的按钮）。'),
    }
  }
  return null
}

/** 处理计划里「还没排车」的货：召回几批、拒收几站（用于成功提示后面补一句，别让使用者以为示例值没管计划） */
async function poolPendingSummary(): Promise<{ recall: number; reject: number }> {
  const pool = (await safeList(() => distributionApi.returnPool('DISPOSAL'))) || []
  const rows: any[] = Array.isArray(pool) ? pool : []
  return {
    recall: rows.filter((r: any) => r.source === 'RECALL' && !r.scanTime).length,
    reject: rows.filter((r: any) => r.source !== 'RECALL' && !r.scanTime).length,
  }
}

/**
 * 召回那条链现在到哪一步：① 还在处理计划里等车的；② 已经排进收运运单的（待发车 / 在途 / 待签收）。
 * <p>
 * 为什么不能只看 `return-pool`：召回货<strong>一排进收运运单就从「待收运」队列里移出</strong>
 * （`recall_collect.status` 0→1），池子取回来是空的。于是所有提示都会说「处理计划是空的」——
 * 可那批货明明正躺在收运运单上跑。车载终端与装车口的示例值必须认这条链，
 * 否则就是「处理计划里有召回货，填充的示例值却跟召回毫无关系」（2026-11-05 用户报障）。
 */
async function recallChainState(): Promise<{ pending: any[]; legs: any[] }> {
  const pool = (await safeList(() => distributionApi.returnPool('DISPOSAL'))) || []
  const poolRows: any[] = Array.isArray(pool) ? pool : []
  const pending = poolRows.filter((r: any) => r.source === 'RECALL' && !r.scanTime)
  // 一次取全部状态的收运单（kind=RECALL），再在本地留「还没跑完」的（待发车 1 / 运输中 2 / 待签收 3）
  const all = (await safeList(() => distributionApi.getTransports({
    kind: 'RECALL', pageNum: 1, pageSize: 20,
  }))) || []
  const legs = (Array.isArray(all) ? all : [])
    .filter((t: any) => [1, 2, 3].includes(Number(t.status)))
    .sort((a: any, b: any) => Number(a.status) - Number(b.status))
  return { pending, legs }
}

/**
 * 收运运单上承的是什么货（召回批次 / 门店）。仅用于提示语把「这趟车=处理计划里那批召回货」点明，
 * 取不到就返回空字符串 —— 不编批次号。
 */
async function describeRecallLegCargo(transportId: number): Promise<string> {
  const items = (await safeList(() => distributionApi.getRecallItems(transportId))) || []
  const rows: any[] = Array.isArray(items) ? items : []
  if (!rows.length) return ''
  const batches = Array.from(new Set(rows.map((r: any) => r.batchNo).filter(Boolean)))
  const stores = Array.from(new Set(rows.map((r: any) => r.storeName).filter(Boolean)))
  const parts: string[] = []
  if (batches.length) parts.push(`批次 ${batches.join('、')}`)
  if (stores.length) parts.push(`门店 ${stores.join('、')}`)
  return parts.join(' / ')
}

/**
 * 「没有待发车的车可填」时，若召回那批货已经排进收运运单 —— 把「它在哪趟车上、下一步演示哪个通道」说清楚。
 * <p>
 * 这段话专门修一个误会：召回货排进收运单后从处理计划队列里移出，池子取回来是空的，
 * 原来的兜底就会说「处理计划也是空的」，而使用者手上那批货正在路上跑 —— 于是觉得
 * 「填充的示例值跟召回没关系」（2026-11-05 用户报障）。
 */
async function describeRecallLegBlocker(): Promise<{
  text: string
  action: { text: string; run: () => Promise<void> }
} | null> {
  const chain = await recallChainState()
  const t: any = chain.legs[0]
  if (!t) return null
  const cargo = await describeRecallLegCargo(t.id)
  const statusText = TRANSPORT_STATUS_LABELS[Number(t.status)] || '未发车'
  return {
    text: `没有「待发车」的车可填 —— 处理计划里那批召回货已经在收运运单 ${t.transportNo}`
      + `（车牌 ${t.vehicleNo || '—'}，状态「${statusText}」${cargo ? '，承运 ' + cargo : ''}）上，`
      + '发车这一步已经过了。这趟车走「冷链中心 → 门店 → 无害化处理中心」：'
      + '接着演示请切到「到达确认」（门店刷卡到店）或「到店扫码收货」（扫店里的召回货）。',
    action: {
      text: '切到「到达确认」',
      run: async () => {
        demoForm.channel = 'ARRIVE'
        resetDemo()
        await fillSample()
      },
    },
  }
}

/**
 * 「填充示例值」的兜底：没有能发的车、但处理计划里压着召回货时，先把计划排成收运运单。
 * <p>
 * 为什么非做这一步：召回货还没排车时，页面上没有任何「待发车运单」，车牌/司机/卡号全都无处可取，
 * 表单只剩一个时刻 —— 使用者看到的就是「点了填充示例值，什么都没回填」。说明文字再全也没用，
 * 手指按下去要看见字段有值。这里排的是池里已经在等车的货（计划的正向推进，不是凭空造单），
 * 已排过就直接复用，反复点不会重复出单。
 */
async function ensureDepartableTransport(): Promise<boolean> {
  // 已经有一趟「刷得出本人」的召回收运车 → 它承载的就是处理计划里那批货，直接拿来填。
  // 注意这里**只认收运车**：只有普通配送车能演示时不算数 —— 使用者要演示的是处理计划里的召回货，
  // 拿普通车填过去，观感就是「填充的示例值跟召回那部分无关」（2026-11-05 用户报障）。
  const waiting = await listTransportsByStatus(1)
  if (waiting.some((t: any) => isDemoable(t) && String(t.transportKind || '') === 'RECALL')) return false
  const pool = (await safeList(() => distributionApi.returnPool('DISPOSAL'))) || []
  const rows: any[] = Array.isArray(pool) ? pool : []
  const recallPending = rows.filter((r) => r.source === 'RECALL' && !r.scanTime)
  if (!recallPending.length) return false
  const busy = await fetchBusyPlates()
  const plate = DEMO_LOAD_PLATES.find((p) => !busy.has(p))
  if (!plate) return false
  try {
    const res: any = await distributionApi.generateRecallTransport({
      vehicleNo: plate,
      driverName: DEMO_FALLBACK_DRIVER,
    })
    const no = res?.transportNo || res?.data?.transportNo
    ElMessage.success(`处理计划里有 ${recallPending.length} 批召回货还没排车，已按计划排成收运运单`
      + `${no ? ' ' + no : ''}（车牌 ${plate}），示例值取自这趟车`)
    return true
  } catch {
    // 拦截器已说明原因（车牌被占 / 池里已无货）；填不上车就退回「说清卡在哪」，不让整条填充失败
    return false
  }
}

/**
 * 按处理计划排一张收运运单（**只出单，不重填表单**）。
 * <p>
 * 与装车口「扫码即出单」对齐：那边扫一下就有车，这边召回货不走扫码，就只能是「排车出单」。
 * 车牌挑一台名下没有未完成运单的备案演示车（一车一趟，`ActiveTransportGuard` 只放空车）；
 * 司机直接用备案的演示司机，这样发车时刷卡能刷出本人，示例不会落到「刷卡人≠司机」的转人工路径上。
 * <p>
 * 单独抽出来是因为它有两个调用方：提示里的按钮（排完再重填），以及扫码枪填充时
 * 「上游还差一张单」的就地补齐（补齐后还要继续发车/到店，不能在这里就把表单填了）。
 */
async function createRecallTransportFromPlan(pendingCount: number): Promise<{
  ok: boolean
  transportNo?: string
  plate?: string
  message?: string
}> {
  const busy = await fetchBusyPlates()
  const plate = DEMO_LOAD_PLATES.find((p) => !busy.has(p))
  if (!plate) {
    return {
      ok: false,
      message: `三台演示车（${DEMO_LOAD_PLATES.join(' / ')}）名下都还有没送完的运单（一车一趟），排不出新的一趟`,
    }
  }
  try {
    const res: any = await distributionApi.generateRecallTransport({
      vehicleNo: plate,
      driverName: DEMO_FALLBACK_DRIVER,
    })
    return { ok: true, transportNo: res?.transportNo || res?.data?.transportNo, plate }
  } catch (e: any) {
    // 拦截器已弹提示（车牌不在备案 / 车被占用 / 池里已没有货）
    return { ok: false, message: e?.message || '生成收运运单失败' }
  }
}

/** 提示里的按钮动作：就地排一张收运运单，排完自动按新运单重填示例值 */
async function generateCollectFromPlan(pendingCount: number) {
  const made = await createRecallTransportFromPlan(pendingCount)
  if (!made.ok) {
    sampleHint.value = `${made.message || '生成收运运单失败'}：原因见上方提示；`
      + '也可以到「处理运输 → 处理计划」手选车辆生成。'
    return
  }
  ElMessage.success(`已按处理计划把 ${pendingCount} 批召回货排成收运运单${made.transportNo ? ' ' + made.transportNo : ''}`
    + `（车牌 ${made.plate}，司机 ${DEMO_FALLBACK_DRIVER}），示例值已重填`)
  // 新运单就是这台终端要发的车：重填一次，车牌/司机/时刻都从它身上取
  await fillSample()
}

/**
 * 用**设备通道**补一次车载终端的动作（发车 / 到店刷卡）。
 * <p>
 * 为什么不去调后台的「人工到达」接口：到店只由车载终端刷卡推进，"谁把这批货送到这一站"
 * 靠的就是那一刷。示例值要补的是这个现场动作，走设备上报落库的才是设备事实、司机身份也才是刷出来的。
 */
async function reportByTerminal(
  channel: 'DEPART' | 'ARRIVE',
  data: Record<string, any>,
): Promise<{ ok: boolean; message?: string }> {
  const dev = enabledDevices.value.find((d: any) => {
    const chs: string[] = d.channels?.length ? d.channels : [d.channel]
    return chs.includes(channel)
  })
  if (!dev) return { ok: false, message: `台账里没有能上报「${CHANNEL_LABELS[channel] || channel}」的终端` }
  try {
    const res: any = await ingestApi.demoReport({ deviceNo: dev.deviceNo, channel, data })
    if (res?.accepted) return { ok: true, message: res?.message || '' }
    return {
      ok: false,
      message: res?.message || (res?.status === 1 ? '转人工确认' : '未入账'),
    }
  } catch (e: any) {
    return { ok: false, message: e?.message || '上报失败' }
  }
}

/**
 * 这张运单上「该被扫码枪扫的那一站」：已到店（站点状态 1）、且这一站还有没收的召回货。
 * <p>
 * 三步倒查的顺序就是后端收货的判定顺序 —— 车牌锁运单、运单当前站、货码再锁这一站的召回货；
 * 少任何一步都扫不成，所以这里也按同样的顺序挑，不猜。
 */
async function findRecallScanTarget(): Promise<{ leg: any; arrived: any; atStop: any[] } | null> {
  const all = (await safeList(() => distributionApi.getTransports({
    kind: 'RECALL', pageNum: 1, pageSize: 20,
  }))) || []
  const alive = (Array.isArray(all) ? all : [])
    .filter((t: any) => [2, 3].includes(Number(t.status)))
    // 待签收（3）优先：站点都已经到齐，能扫的概率最高
    .sort((a: any, b: any) => Number(b.status) - Number(a.status))
  for (const leg of alive) {
    const stops = (await safeList(() => distributionApi.getTransportStops(leg.id))) || []
    const arrived = (stops as any[]).find((s: any) => Number(s.status) === 1)
    if (!arrived) continue
    // 站点接口给的是「站点视图」而不是实体行：**没有 id**，只有 storeId / stopNo。
    // 拿 arrived.id（undefined）去和货物的 stopId 比，永远比不中 —— 于是「已到店、有货可收」
    // 也会被判成「没货可扫」（2026-11-05 报障「扫码枪填不出示例值」的第二个根因）。
    // 所以按门店对站点：一趟车里一家店只有一站，storeId 足够唯一；拿不到 storeId 时再退回 stopNo。
    const sameStop = (r: any) => (arrived.storeId != null
      ? Number(r.storeId) === Number(arrived.storeId)
      : Number(r.stopId) === Number(arrived.id))
    const items = (await safeList(() => distributionApi.getRecallItems(leg.id))) || []
    const atStop = (items as any[]).filter((r: any) => sameStop(r) && Number(r.status) < 2)
    if (atStop.length) return { leg, arrived, atStop }
  }
  return null
}

async function fillSample() {
  sampling.value = true
  sampleHint.value = ''
  sampleJump.value = null
  sampleAction.value = null
  try {
    const ch = demoChannel.value
    if (ch === 'FARM_ERP') {
      // 外部养殖系统同步：示例造一批「自繁」新批次，批次号与耳标段都带时间戳后缀，
      // 保证每次演示都是一批全新的猪（撞上上一轮的批次号会走「同名批次转人工」那条路，
      // 那条路留给讲解冲突场景时手工触发，不该是示例值的默认结果）。
      const farms = await safeList(() => pigApi.getFarms({ current: 1, size: 5 }))
      const farm = farms[0]
      if (!farm) {
        sampleHint.value = '本系统还没有养殖场：外部系统的场必须先在本系统建档（场名对齐），'
          + '对不上时同步会转「待人工处理」，不会凭空造一个场。请先到「养殖管理 → 养殖场」建一个场'
      } else {
        const stamp = Date.now().toString().slice(-8)
        const dayText = (offsetDays: number) => {
          const d = new Date(Date.now() + offsetDays * 86400000)
          const p = (n: number) => String(n).padStart(2, '0')
          return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
        }
        demoForm.data.action = 'UPSERT'
        demoForm.data.farmName = farm.farmName || farm.name
        demoForm.data.batchNo = `ERP-${stamp}`
        demoForm.data.breed = '三元杂'
        demoForm.data.entryDate = dayText(-20)
        demoForm.data.birthDate = dayText(-90)
        demoForm.data.earTagStart = `ET-ERP${stamp}001`
        demoForm.data.earTagEnd = `ET-ERP${stamp}030`
        demoForm.data.voidReason = ''
        sampleHint.value = `以「${demoForm.data.farmName}」的 ERP 身份推一批 30 头自繁猪（批次 ${demoForm.data.batchNo}）：`
          + `进场 ${demoForm.data.entryDate}、出生 ${demoForm.data.birthDate}`
          + '。上报后批次档案与 30 头成员一次性生成，来源标「养殖系统同步」，日龄按出生日期现算。'
          + '再把同一份报文重推一次会返回「重复上报」——这正是外部系统重推时系统该做的事'
      }
    } else if (ch === 'TEMPERATURE') {
      const t = await findTransport(2)
      demoForm.data.temperature = DEMO_TEMPERATURE
      if (t) {
        demoForm.data.transportNo = t.transportNo
        sampleHint.value = `取到运输中运单 ${t.transportNo}；读数给 ${DEMO_TEMPERATURE}℃ —— 落在允许区间 -18~0 内，`
          + '走的是「正常入账」这条路。想演示温度异常（is_abnormal）把读数改到区间外再报即可，异常值照常入账。'
      } else {
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
    } else if (ch === 'ENTRY') {
      // 入场查验自动入账有三道硬前提：① 该猪的出栏审批已通过；② 状态还是「在养/已出栏」；
      // ③ 检疫证核验通过。所以示例值必须同时取齐——找一头三项都占的猪，用它自己的耳标 + 证号
      const found = await findPigWithCert()
      if (found) {
        demoForm.data.earTagNo = found.earTagNo
        demoForm.data.batchNo = `B-${todayText()}-01`
        demoForm.data.quarantineCert = found.certNo || ''
        demoForm.data.weight = 112.5
        demoForm.data.inspector = '张查验'
        sampleHint.value = found.note
          ? `取到耳标 ${found.earTagNo}（状态可入场），但${found.note}：`
            + '报文先按这头猪填好了，把缺的这一步补上再报就能入账（入场查验会逐项核验，缺一项都会被拦下）。'
          : `取到耳标 ${found.earTagNo}（出栏审批已通过、状态可入场）+ 有效检疫证 ${found.certNo}，前提都齐`
      } else {
        sampleHint.value = '没有同时满足「出栏审批已通过 + 状态为在养/已出栏 + 检疫证未过期」的猪：请先在「养殖管理 → 出栏申报」为该猪申报并审批通过（已屠宰的猪是终态，不能再入场，需另找一头在养的猪；证过期可执行 backend/init-sql/19-refresh-demo-certs.sql 顺延效期）'
      }
    } else if (ch === 'RACTOPAMINE') {
      // 宰前抽检：取一头「已入场查验、且自身与批次都没有阳性记录」的猪，批次号沿用入场查验的批次号。
      // 这样同一头猪能顺着 瘦肉精 → 屠宰检验 → 盖章 → 分割 把整条演示链走完；
      // 若取到阳性批次的猪，后面每个环节都会被「阳性阻断」拦下，演示就断在这里。
      const found = await findPigForRactopamine()
      if (found) {
        demoForm.data.earTagNo = found.earTagNo
        demoForm.data.batchNo = found.batchNo
        demoForm.data.sampleNo = `S-${Date.now()}`
        demoForm.data.result = 1
        demoForm.data.operator = '赵检测'
        sampleHint.value = `取到耳标 ${found.earTagNo}（批次 ${found.batchNo} 尚无阳性样本）；读数仪必须给出结果，否则按结构性错误直接拒绝。这一条检测也是后续屠宰检验放行的前提——查不到记录的猪「未检不得屠宰」`
      } else {
        sampleHint.value = '没有「已入场查验、且自身与批次都没有阳性样本」的猪，无法演示自动入账；请先到「模拟设备上报 · 入场查验」让一头猪入场，或换一个无阳性样本的批次'
      }
    } else if (ch === 'SALE') {
      // 只取在库可售(status=1)的商品码：取到已售/未激活的码会演示不出新销售
      // 字段名以 `/sales/qrcodes` 实际返回的 `qrCode` 为准（曾按 productQrCode 取，取不到就当「没有在库码」，
      // 于是示例值一项都不填 —— 2026-11-05「点了并没有回填信息」的其中一例）。
      const page = await safeList(() => salesApi.getQrcodes({ status: 1, pageNum: 1, pageSize: 1 }))
      const code = page?.[0]?.qrCode || page?.[0]?.productQrCode || page?.[0]?.product_qr_code
      if (code) {
        demoForm.data.qrCode = code
        demoForm.data.sellPrice = 38.5
        demoForm.data.sellWeightKg = 0.75
        sampleHint.value = `取到在库可售商品码 ${code}`
      } else {
        // 销售库空了也别给空表单：拿最近一张门店签收单就地建 3 个码（跟门店「生成二维码」同一个接口），
        // 示例值就能立刻报一笔真实销售。销售码是可查可作废的业务单据，不是凭空造的假码。
        const receipts = await safeList(() => distributionApi.getReceipts({ pageNum: 1, pageSize: 20 }))
        // 逐张试：演示库里排在前面的签收单可能挂着已清理的运输单（接口会回「运输单数据无效」），
        // 只试第一张就等于把「建不出来」当成「建不了码」。
        let fresh = ''
        let usedReceipt: any = null
        for (const receipt of (receipts || []).slice(0, 5)) {
          if (!receipt?.id) continue
          const res: any = await safeList(() => salesApi.generateQrcodes({ receiptId: receipt.id, count: 3 }))
          const code = res?.qrcodes?.[0] || res?.data?.qrcodes?.[0]
          if (code) { fresh = code; usedReceipt = receipt; break }
        }
        if (fresh) {
          demoForm.data.qrCode = fresh
          demoForm.data.sellPrice = 38.5
          demoForm.data.sellWeightKg = 0.75
          sampleHint.value = `销售库原本没有在库可售的码，已用签收单 ${usedReceipt?.receiptNo || usedReceipt?.id} 就地生成 3 个新码，`
            + `示例取 ${fresh}（门店「二维码管理」里能看到这批码）`
        } else if (receipts?.length) {
          sampleHint.value = '销售库没有在库可售的商品码，拿最近几张签收单就地建码也没成功'
            + '（多半是这些签收单挂的运输单已被清理）：请到「销售管理 → 二维码管理」为一张有效的签收单建码'
        } else {
          sampleHint.value = '销售库没有在库可售的商品码，也没有可建码的门店签收单：请先让门店签收一批分割品，再回来演示零售销售'
        }
      }
    } else if (ch === 'TAG') {
      // 读写器只提供耳标号 + 读取时刻（芯片里没有别的）；批次信息（养殖场、批次号、
      // 品种、进场日期）由人工在「生猪批次档案」登记，设备报文带了也不认。
      demoForm.data.earTagNo = `ET-DEMO-${Date.now()}`
      demoForm.data.readTime = nowText()
      sampleHint.value = '耳标不在任何已登记批次区间内会转「待人工」：请先到「生猪批次档案」登记批次（耳标区间含此号），再回来确认入账'
    } else if (ch === 'VACCINE') {
      await fillByPig('在养生猪', () => {
        demoForm.data.vaccineName = '猪瘟兔化弱毒疫苗'
        demoForm.data.vaccineBatchNo = `VAC-${todayText()}-01`
        demoForm.data.dosage = '2ml'
        demoForm.data.injectSite = '耳后颈部'
        demoForm.data.injectTime = nowText()
        demoForm.data.operator = '刘兽医'
      })
      if (demoForm.data.earTagNo && demoForm.data.vaccineBatchNo) {
        const fileId = await uploadDemoVoucherPhoto()
        if (fileId) {
          demoForm.data.fileIds = [fileId]
          demoPhotoFiles.value = [{ name: 'vaccine-voucher.png', url: '/demo/vaccine-voucher.png', status: 'success', uid: fileId }]
          sampleHint.value += `；疫苗批号 ${demoForm.data.vaccineBatchNo}，凭证照片用内置演示合成图（非真实照片）`
        } else {
          sampleHint.value += `；疫苗批号 ${demoForm.data.vaccineBatchNo}（缺批号会被拒），凭证照片请手动上传`
        }
      }
    } else if (ch === 'QUARANTINE') {
      // 扫码登记的字段全部来自检疫单证面，设备不产生任何结论。
      // 必须挑一头「还没有有效证」的猪：一头猪同时只能有一张有效证，否则会转「待人工确认」（换发要先作废原证）。
      // 证号带时间戳，保证反复演示每次都是新证，不会被重复上报拦截挡下。
      const pig = await findPigWithoutCert()
      if (pig) {
        demoForm.data.earTagNo = pig.earTagNo
        demoForm.data.certNo = `QC-${todayText()}-${String(Date.now()).slice(-4)}`
        demoForm.data.issueOrg = '顺义区动物卫生监督所'
        demoForm.data.issueTime = nowText()
        // 负值即未来：有效期给一年，过期证入场查验会直接判无效
        demoForm.data.validUntil = daysAgoText(-365)
        demoForm.data.inspector = '王官方兽医'
        sampleHint.value = `取到尚未出证的在养猪：耳标 ${pig.earTagNo}。扫描件留空即「只有扫码枪」的形态，照样能登记`
      } else {
        sampleHint.value = '没有「尚未出证」的在养猪：一头猪同时只能有一张有效证，换发要先作废原证'
      }
    } else if (ch === 'EXIT_APPLY') {
      // 出栏称重登记：一头猪同时只能有一条有效申报（待审或已通过），
      // 挑到已申报的猪会被业务侧判「已有申报」而拒绝，演示不出来。
      const pig = await findPigWithoutApply()
      if (pig) {
        demoForm.data.earTagNo = pig.earTagNo
        demoForm.data.weightKg = 120
        demoForm.data.weighTime = nowText()
        demoForm.data.targetSlaughterhouse = '示范定点屠宰场'
        sampleHint.value = `取到尚未申报出栏的在养猪：耳标 ${pig.earTagNo}。目标屠宰场是「去向声明」，清空它会转「待人工确认」`
      } else {
        sampleHint.value = '没有「尚未申报出栏」的在养猪：一头猪同时只能有一条有效申报，被驳回或已通过都不会再自动登记'
      }
    } else if (ch === 'INSPECTION') {
      // 屠宰检验的前置闸门按检验类型分档：
      // ① 该猪已入场查验（批次以入场批次为准）——两种类型都要满足；
      // ② 做「宰后」检验（2/3）时还要过两道：瘦肉精「已检且无阳性」（查不到检测记录按「未检不得屠宰」
      //    直接拒绝）+ 该猪已通过宰前检查（未做宰前检查不得屠宰）；
      // ③ 做「宰前检查」(1) 本身不设前置（实验室检测可能还没做），只是已检出阳性的猪不得判「合格」。
      // 演示走的是「通过」路径，所以这里挑一头已入场 + 瘦肉精合格的猪；它还没做宰前检查时，
      // 示例值先把类型给成「宰前检查」，判合格后再点一次填充就能接着演示宰后同步检疫。
      const found = await findPigReadyForInspection()
      if (found) {
        demoForm.data.earTagNo = found.earTagNo
        demoForm.data.batchNo = found.batchNo
        demoForm.data.inspectType = found.prePassed ? 2 : 1
        demoForm.data.result = 1
        demoForm.data.veterinary = '李官方兽医'
        demoForm.data.temperature = found.prePassed ? undefined : 38.6
        demoForm.data.conclusion = found.prePassed
          ? '体表、脏器无可见病变，判合格'
          : '精神、呼吸、体表无异常，准予屠宰'
        sampleHint.value = found.prePassed
          ? `取到耳标 ${found.earTagNo}（批次 ${found.batchNo}）：瘦肉精已检无阳性、且已有合格宰前检查，可直接做宰后同步检疫`
          : `取到耳标 ${found.earTagNo}（批次 ${found.batchNo}）：瘦肉精已检无阳性，${found.preChecked ? '但宰前检查还没判过合格' : '但这头猪还没做宰前检查'}——示例值先给「宰前检查」；判合格后再点一次填充，就能接着演示宰后同步检疫`
      } else {
        sampleHint.value = '没有「已入场查验、且瘦肉精已检且无阳性」的猪：请先到「模拟设备上报 · 瘦肉精检测」给一头已入场的猪做一条检测（未检不得屠宰），再回来做屠宰检验'
      }
    } else if (ch === 'STAMP') {
      // 盖章有三道硬前置：① 该猪必须已有一条「结论合格」的**宰后**检验（只有宰前合格不算，盖章发生在同步检疫之后）；
      // ② 瘦肉精已检且无阳性；③ 宰后检验本身已经以宰前合格为前提。
      // 另有两处只能靠示例值自己躲开的唯一键（踩过）：
      //   · carcass_stamp 的 pig_id 唯一 —— 一头白条只有一枚章，耳标必须挑「还没盖过章」的猪；
      //   · carcass_no 也唯一，而盖章通道**第一步就是按胴体编号查重** —— 撞上既有记录会直接
      //     返回那一条、回执照样是「已入账」。同一天给第二头白条盖章时沿用第一头的
      //     CT-当天-001，上报就被静默吃掉：看起来成功，库里什么都没写。
      const stamps = await safeList(() => slaughterApi.getStamps({ pageNum: 1, pageSize: 200 }))
      const stampedPigs = new Set(
        stamps.map((s: any) => Number(s?.pigId ?? s?.pig_id)).filter((id: number) => Number.isFinite(id) && id > 0)
      )
      const ins = await findPassedInspection((pigId) => !stampedPigs.has(pigId))
      if (ins?.earTagNo) {
        demoForm.data.earTagNo = ins.earTagNo
        demoForm.data.batchNo = ins.batchNo || `B-${todayText()}-01`
        demoForm.data.carcassNo = nextCarcassNo()
        demoForm.data.veterinary = ins.veterinary || '李官方兽医'
        sampleHint.value = `取到结论合格的宰后检验记录（耳标 ${ins.earTagNo}）；盖章机硬校验「合格宰后检验 + 瘦肉精已检无阳性」`
          + '，且一头白条只能盖一枚章，因此耳标挑的是尚未盖章的一头，胴体编号也给一个没用过的号'
      } else {
        sampleHint.value = '没有「宰后检验结论合格、瘦肉精已检无阳性、且还没盖过章」的白条：'
          + '已盖过章的白条不能重复盖章（一头白条一枚章），请换一头尚未盖章的猪，或先做宰前检查 + 宰后同步检疫并判合格'
      }
    } else if (ch === 'BATCH') {
      // 胴体批次归集以「白条已盖章」为前提——章就是白条过了宰后同步检疫的凭证。
      // 这里挑一具「已盖章、且还没归进任何批次」的白条：拿已归批的去演示，
      // 只会得到「已归入 XX 批次，本次未重复入批」的回执，看不出归集过程本身。
      const pig = await findStampedPig()
      if (pig?.earTagNo) {
        demoForm.data.earTagNo = pig.earTagNo
        demoForm.data.slaughterhouse = '示范定点屠宰场'
        demoForm.data.carcassWeightKg = 78.5
        demoForm.data.carcassNo = `CT-${todayText()}-001`
        demoForm.data.lineNo = '白条一线'
        sampleHint.value = `取到已盖章的白条（耳标 ${pig.earTagNo}）；系统会按「屠宰场 + 屠宰日」把它并入当日批次，批次号由系统生成`
      } else {
        sampleHint.value = '没有「已盖章、且尚未归批」的白条：胴体批次以胴体盖章为前提（章是白条过同步检疫的凭证），请先用「胴体自动盖章」上报一条，且该白条还没归入任何批次'
      }
    } else if (ch === 'SPLIT') {
      // 分割作业以「已存在的胴体批次」为前提。示例按 78.5kg 白条给一份完整产出，
      // 合计约 72kg（≈92%），落在出肉率合理区间内，能演示「对账通过」这条路；
      // 想演示「待人工复核」，删掉其中一行（例如后腿肉）使必出部位缺项即可。
      const batch = await findCarcassBatch()
      if (batch?.batchNo) {
        demoForm.data.parentBatchNo = batch.batchNo
        demoItems.value = [
          { partCode: 'P02', weightKg: 18.5, packageCount: 20 },
          { partCode: 'P03', weightKg: 15.5, packageCount: 18 },
          { partCode: 'P04', weightKg: 19.0, packageCount: 22 },
          { partCode: 'P06', weightKg: 11.0, packageCount: 15 },
          { partCode: 'P07', weightKg: 8.0, packageCount: 10 },
        ]
        demoForm.data.workshop = '分割车间一号线'
        demoForm.data.lineNo = '1号线'
        demoForm.data.operator = '陈分割'
        sampleHint.value = `取到白条批次 ${batch.batchNo}：一次上报整份产出清单，系统生成作业号并按出肉率对账；`
          + '上报入账后，工位上的车间相机会自动连拍现场照片并绑定到这次分割（不用手动传，见「分割操作」页的监控区）'
      } else {
        sampleHint.value = '没有可用的胴体批次，请先到「分割配送 → 胴体批次」建一条'
      }
    } else if (ch === 'ORDER') {
      // 订单转入演示：这里模拟的是「外部系统把选购结果推过来」，所以示例值要落在
      // 真实有库存的部位上 —— 订单只报「部位 + 重量」，具体装哪几批由系统按可发运批次配，
      // 配不齐会转「待人工确认」（这正是这条通道最该演示的边界：宁缺勿猜）。
      const batches = await safeList(() => distributionApi.getPendingSplits({ pageNum: 1, pageSize: 50 }))
      const batch = batches.find((b: any) => b.partCode || b.part_code)
      if (batch) {
        const partCode = batch.partCode || batch.part_code
        const partName = batch.productName || batch.product_name || partCode
        const stock = Number(batch.weightKg ?? batch.weight_kg ?? 0)
        // 单号每次演示必须唯一：同一个订单号重推是**幂等**的（只认第一条）。
        // 原先写死成「当天-001」，第二次演示必然命中既有计划 —— 回执照样是「已入账」，
        // 但计划列表里不会多出任何一行，看起来像「上报成功了却没建计划」。
        // 幂等要演示的话，把下面这串号手工改回上一次报过的即可（提示语里也说了这一点）。
        const t = new Date()
        const tp = (n: number) => String(n).padStart(2, '0')
        demoForm.data.orderNo = `SO${todayText()}-${tp(t.getHours())}${tp(t.getMinutes())}${tp(t.getSeconds())}`
        // 门店 id 取机构树里真实存在的门店（种子数据 1001 = 安心生鲜门店）；
        // 乱填会得到「机构树里查不到」的转人工回执，那是设计如此，不适合当默认示例
        demoForm.data.storeId = 1001
        demoForm.data.remark = '演示：外部系统选购转入'
        // 订购量不超过该批次的重量，保证一次就能配上，演示走「已入账」这条路
        demoItems.value = [{ partCode, weightKg: Math.max(1, Math.min(stock || 10, 20)), packageCount: 1 }]
        sampleHint.value = `取到有可发运库存的部位「${partName}」，本次订单号 ${demoForm.data.orderNo}：订单只报部位和重量，系统自动配上实物批次并生成「待发货」计划（车与司机留待排车）。结果在「分割配送 → 冷链运输」页顶部的发货计划里按订单号就能查到`
      } else {
        sampleHint.value = '当前没有可发运的分割批次，订单里的部位配不上货会转人工；请先到「模拟设备上报 · 分割批次」上报一条'
      }
    } else if (ch === 'LOAD') {
      // 装车口不产生业务判定：示例值必须拿一张真实存在的「待发货」计划，
      // 因为目的地只能从计划来。车牌若计划里已填就沿用（现场识别到的必须与之一致，
      // 否则会被判「可能装错车」转人工）；计划里空着的，由这里当作设备读到的现场值补齐。
      // 司机**不填**：装车口没有读卡器，装车时司机还没上车 —— 这正是要演示的常态，
      // 运单会照常生成，司机那一栏等他在车上刷卡（发车确认通道）时自动补上。
      // 示例车牌还要**避开已挂着「待发车」运单的车**：撞上就会被并入那趟车，
      // 演示时看不出「建了一张新运单」（业务上并入是对的，见 findLoadablePlan 注释）。
      const pendingPlates = await fetchPendingPlates()
      const plan = await findLoadablePlan(pendingPlates)
      if (plan) {
        const plate = plan.vehicleNo || pickFreePlate(pendingPlates)
        demoForm.data.vehicleNo = plate
        demoForm.data.driverName = plan.driverName || ''
        demoForm.data.driverPhone = plan.driverPhone || ''
        demoForm.data.batchNos = plan.batchNos.join(',')
        demoForm.data.loadTime = nowText()
        sampleHint.value = `取到待发货计划 ${plan.planNo}（发往 ${plan.storeName}）：本次装车 ${plan.batchNos.length} 批货。`
          + '上报后这批货排进运单：发货计划转「已排车」；车还没走，运单停在「待发车」。'
          + '司机留空即可 —— 装车口没有读卡器，司机身份由他在车上刷卡（发车确认）时确认；'
          + '空着不影响生成运单，但没有司机的运单发不出去。'
          + (plan.vehicleNo
            ? `车牌沿用计划安排的 ${plan.vehicleNo}——现场识别到的车牌必须与它一致，改掉再报会转人工核对是否装错车。`
            : `车牌在计划里是空的，这里由装车口设备补齐（这正是自动化要的效果），示例取 ${plate}。`)
          + (pendingPlates.has(plate)
            ? `${plate} 目前还挂着一张没发车的运单：若本次装车在并入窗口内，系统会把这批货并入那一趟车`
              + '（车没走就还是同一趟），而不是新建一张 —— 回执里的运单号就是那张既有的。'
            : '')
      } else {
        sampleHint.value = '没有「待发货」状态的发货计划：装车口只能把货发到计划里定好的门店，不猜目的地。请先到「分割配送 → 冷链运输」建一张发货计划（车与司机可以留空），再回来演示'
      }
    } else if (ch === 'RETURN_LOAD') {
      // 2026-11-04 起扫码即出单：示例从「处理计划」取还没扫的拒收货（货码 = 批次号或原运单号），
      // 上报后业务侧自动生成（或并入）该车的处理运单，司机直接刷卡发车。
      //
      // 车牌必须挑**名下一张未完成运单都没有**的备案车（2026-11-03 用户指出）：
      // 扫了哪台车，运单就开给哪台车（一车一趟，ActiveTransportGuard 只放空车）；
      // 车被占用时扫了也出不了单，货会留在处理计划。
      const pool = await safeList(() => distributionApi.returnPool('DISPOSAL'))
      // 处理计划里的召回货不归这里管：它要生成收运运单、由随车扫码枪在门店收货，
      // 拿去装车口扫只会得到「货码对不上本车拒收货」转人工 —— 示例值不能默认演示失败路径
      const rejects = (pool || []).filter((r: any) => r.source !== 'RECALL')
      const pending = rejects.filter((r: any) => !r.scanTime)
      const rows = (pending.length ? pending : rejects).slice(0, 3)
      const codes = rows.map((r: any) => r.batchNo || r.transportNo).filter(Boolean)
      demoForm.data.scanCodes = codes.join(',')
      demoForm.data.scanTime = nowText()
      demoForm.data.legTransportNo = ''
      const busy = await fetchBusyPlates()
      const freePlates = DEMO_LOAD_PLATES.filter((p) => !busy.has(p))
      demoForm.data.vehicleNo = freePlates[0] || ''
      if (!rows.length) {
        // 没有货可扫就不填车牌：只填一台车、货码空着，看着就像「填错了 / 跟召回无关」。
        // 红线是「示例值必须落成功路径」，扫不出单的场景留空 + 说清下一步。
        demoForm.data.vehicleNo = ''
        const recallRows = (pool || []).filter((r: any) => r.source === 'RECALL')
        if (recallRows.length) {
          sampleHint.value = `处理计划里只有 ${recallRows.length} 批召回货（${recallRows.map((r: any) => r.batchNo).filter(Boolean).join('、')}），`
            + '没有拒收货 —— 召回货不走装车口这条「扫码即出单」的路：先在「处理运输 → 处理计划」把它排成收运运单，'
            + '再由随车扫码枪到店收货。要演示装车出单，请先在门店签收页拒收一批货'
          // 只有召回货时，别让人卡在「知道要走收运链、还得自己找页面」这一步：就地排一趟收运车
          sampleAction.value = {
            text: '按处理计划生成收运运单',
            run: () => generateCollectFromPlan(recallRows.length),
          }
        } else {
          // 拒收货没有，但召回那批可能已经排进收运运单在跑了 —— 那也算「处理计划里有货」，
          // 不能说「处理计划是空的」，更不该拿一台普通演示车凑示例值。
          const chain = await recallChainState()
          const leg: any = chain.legs[0]
          if (leg) {
            const cargo = await describeRecallLegCargo(leg.id)
            const statusText = TRANSPORT_STATUS_LABELS[Number(leg.status)] || '未发车'
            sampleHint.value = `处理计划里没有等装车的拒收货 —— 召回那批货已经在收运运单 ${leg.transportNo}`
              + `（车牌 ${leg.vehicleNo || '—'}，状态「${statusText}」${cargo ? '，承运 ' + cargo : ''}）上：`
              + '召回货走「上门店收货 → 无害化处理中心」，不经过装车口这条「扫码即出单」的路。'
              + '要接着演示召回链，请换「到店扫码收货」通道（随车扫码枪）；要演示装车出单，先在门店签收页拒收一批货。'
            sampleAction.value = {
              text: '切到「到店扫码收货」',
              run: async () => {
                demoForm.channel = 'RECALL_COLLECT'
                resetDemo()
                await fillSample()
              },
            }
          } else {
            sampleHint.value = '处理计划是空的：没有等待回运的拒收货。先在门店签收页拒收一批货，或换一条通道演示'
          }
        }
      } else {
        sampleHint.value = `取到处理计划里 ${rows.length} 站拒收货（${pending.length ? '未扫码' : '已扫码，重扫是幂等'}）：`
          + `${codes.join('、')}。上报后自动生成（或并入）该车的处理运单（待发车）——`
          + '司机即可在车载终端刷卡发车，不用再回页面点「生成处理运单」。'
          + (demoForm.data.vehicleNo
            ? `车牌取 ${demoForm.data.vehicleNo}：它名下没有未完成的运单，扫完即出单（一车一趟）。`
            : '三台备案演示车（' + DEMO_LOAD_PLATES.join(' / ') + '）名下都还有未完成的运单，'
              + '扫了也出不了新单（货会留在处理计划）：请先把那些运单送到签收，或手动填一台空闲备案车的车牌。')
      }
    } else if (ch === 'RECALL_COLLECT') {
      // 到店扫码收货站在整条链的最末端：上游还有「按计划排车 → 司机刷卡发车 → 到店刷卡」三步，
      // 少任何一步都扫不成（车牌锁运单、运单锁当前站、货码再锁这一站的召回货）。
      // 点一次「填充示例值」就要看见值，所以缺哪步就地补哪步 —— 补的都是**设备本来就会做的那次上报**，
      // 不走后台的人工接口：落库的仍是设备事实，司机身份也仍是刷卡刷出来的（2026-11-05 报障：这一步填不出值）。
      const notes: string[] = []
      let blocked = ''
      let blockedChannel: 'DEPART' | 'ARRIVE' | null = null
      let chain = await recallChainState()
      let leg: any = chain.legs.find((t: any) => [2, 3].includes(Number(t.status)))

      // ① 池里还压着召回货、却一张未完成的收运单都没有 → 先按计划排一趟
      if (!chain.legs.length && chain.pending.length) {
        const made = await createRecallTransportFromPlan(chain.pending.length)
        if (made.ok) {
          notes.push(`处理计划里还有 ${chain.pending.length} 批召回货没排车，已排成收运运单`
            + `${made.transportNo ? ' ' + made.transportNo : ''}（车牌 ${made.plate}）`)
          chain = await recallChainState()
        } else {
          notes.push(`处理计划里还有 ${chain.pending.length} 批召回货没排车，但${made.message || '排车失败'}`)
        }
      }

      // ② 车排好了还停在「待发车」→ 替司机刷卡发车（召回车的司机是备案的张师傅，这一刷就是把身份写成他本人）
      const waiting: any = chain.legs.find((t: any) => Number(t.status) === 1)
      if (!leg && waiting) {
        const card = cardOf(waiting.driverName) || (waiting.driverName ? '' : DEMO_FALLBACK_CARD)
        if (!card) {
          blocked = `收运运单 ${waiting.transportNo} 停在「待发车」，但司机「${waiting.driverName}」不在备案卡表里`
            + '（只有 张师傅 / 李四 / 王五），刷谁的卡都会被判「刷卡人 ≠ 运单安排的司机」，示例没有替他编卡。'
            + '请切到「发车确认」填他本人的卡号发一次车，再回来扫门店的召回货'
          blockedChannel = 'DEPART'
        } else {
          const r = await reportByTerminal('DEPART', {
            vehicleNo: waiting.vehicleNo, cardNo: card, departTime: nowText(),
          })
          if (r.ok) {
            notes.push(`收运运单 ${waiting.transportNo} 本来还停在「待发车」，已替司机刷卡发车`)
            chain = await recallChainState()
            leg = chain.legs.find((t: any) => [2, 3].includes(Number(t.status)))
          } else {
            blocked = `收运运单 ${waiting.transportNo} 停在「待发车」，代刷发车没成功（${r.message || '原因见上方提示'}）。`
              + '请切到「发车确认」手工发一次，再回来扫门店的召回货'
            blockedChannel = 'DEPART'
          }
        }
      }

      // ③ 车在路上、门店却还没刷到店 → 逐站替车载终端补「到店刷卡」，直到某一站真的有货可扫
      let target: { leg: any; arrived: any; atStop: any[] } | null = await findRecallScanTarget()
      while (!target && leg && !blocked) {
        const stops = (await safeList(() => distributionApi.getTransportStops(leg.id))) || []
        const next = (stops as any[]).find((s: any) => Number(s.status || 0) < 1)
        // 站点全到了就再没有可推进的动作：这一趟能扫的货已经扫完了（或这趟车没有待收的货）
        if (!next) break
        const card = cardOf(leg.driverName) || (leg.driverName ? '' : DEMO_FALLBACK_CARD)
        if (!card) {
          blocked = `收运运单 ${leg.transportNo} 还在去第 ${next.stopNo} 站（${next.storeName}）的路上，`
            + `司机「${leg.driverName}」不在备案卡表里，示例不能替他编卡：请切到「到达确认」填他本人的卡号刷一次`
          blockedChannel = 'ARRIVE'
          break
        }
        const r = await reportByTerminal('ARRIVE', {
          vehicleNo: leg.vehicleNo, cardNo: card, arriveTime: nowText(),
        })
        if (!r.ok) {
          blocked = `替车载终端补第 ${next.stopNo} 站（${next.storeName}）的到店刷卡没成功（${r.message || '原因见上方提示'}），`
            + '请切到「到达确认」手工刷一次，再回来扫这站的召回货'
          blockedChannel = 'ARRIVE'
          break
        }
        notes.push(`第 ${next.stopNo} 站（${next.storeName}）本来还没刷到店，已替车载终端补了一条到店刷卡`)
        target = await findRecallScanTarget()
      }

      if (target) {
        demoForm.data.vehicleNo = target.leg.vehicleNo || ''
        demoForm.data.scanCodes = target.atStop.map((r: any) => r.batchNo).filter(Boolean).join(',')
        demoForm.data.scanTime = nowText()
        sampleHint.value = `取到收运运单 ${target.leg.transportNo}（车牌 ${target.leg.vehicleNo || '—'}）`
          + `第 ${target.arrived.stopNo} 站（${target.arrived.storeName}）待收的 ${target.atStop.length} 批召回货：`
          + `${target.atStop.map((r: any) => r.batchNo).filter(Boolean).join('、')}。`
          + '上报后这些货转「已收货」——本站全收完，司机才能刷卡去下一家'
          + (notes.length ? ` 为了能扫这一枪，上游缺的动作已就地补齐：${notes.join('；')}。` : '')
      } else {
        demoForm.data.scanCodes = ''
        demoForm.data.vehicleNo = ''
        demoForm.data.scanTime = nowText()
        if (blocked) {
          sampleHint.value = blocked
          sampleAction.value = {
            text: blockedChannel === 'DEPART' ? '切到「发车确认」' : '切到「到达确认」',
            run: async () => {
              demoForm.channel = blockedChannel === 'DEPART' ? 'DEPART' : 'ARRIVE'
              resetDemo()
              await fillSample()
            },
          }
        } else if (leg) {
          // 有车在跑，但这一趟上已经没有可扫的货了：说清「收完了」以及整条链的最后一步在哪
          const cargo = await describeRecallLegCargo(leg.id)
          sampleHint.value = `收运运单 ${leg.transportNo}${cargo ? `（承运 ${cargo}）` : ''}上的门店货都收完了：`
            + '这一趟没有可扫的召回货了 —— 召回链条的最后一步由无害化处理中心在「处理运输」确认签收。'
            + (notes.length ? ` 本次已就地补齐：${notes.join('；')}。` : '')
        } else if (chain.pending.length) {
          // 池里有召回货却连一张收运单都没排出来（车牌被占 / 生成失败）：把原因和出口一起给出
          sampleHint.value = notes.length
            ? `${notes.join('；')}。请到「处理运输 → 处理计划」手选一台空闲备案车生成收运运单，再由司机刷卡发车。`
            : `处理计划里有 ${chain.pending.length} 批召回货还没排车，`
              + '现在没有可扫的货：先排成收运运单、由司机刷卡发车到店，才轮到扫码枪。'
          sampleAction.value = {
            text: '按处理计划生成收运运单',
            run: () => generateCollectFromPlan(chain.pending.length),
          }
        } else {
          sampleHint.value = '没有可扫的召回货：处理计划是空的，也没有在跑的收运运单。'
            + '召回的货要先由门店拒收、销售端发起召回，才会进处理计划排车'
        }
      }
    } else if (ch === 'DEPART') {
      // 发车确认是**全系统唯一能确认「司机是谁」的地方**（装车口没有读卡器，装车时司机还没上车）：
      // 所以示例值必须取一辆真实「待发车」、且**属于本终端所在那辆车**的运单（见 findDepartTarget），
      // 刷卡的人还要与运单安排的人对得上 —— 对不上会被判「可能上错车」转人工，那正是这条通道要防的事。
      // 挑单口径见 findDepartTarget；这里把卡按「运单上记的是谁」分三档给：
      const plate = demoDevicePlate.value
      let t = await findDepartTarget()
      // 处理计划里还压着没排车的召回货 → 先把这趟收运车排出来再取单：
      // ① 一张待发车都没有时，它是唯一能填的车（否则车牌/司机/卡号全空）；
      // ② 有普通配送车能填时也要排 —— 使用者点这里要演示的是召回那批货，填一辆普通车就是
      //    「示例值跟召回没关系」（2026-11-05 用户报障）。
      // 终端绑了车牌时不代排：那会排出一趟不归这台车管的运单，车牌对不上更误导。
      let autoCreated = false
      if (!plate) {
        autoCreated = await ensureDepartableTransport()
        if (autoCreated) t = await findDepartTarget()
      }
      if (t) {
        demoForm.data.vehicleNo = t.vehicleNo || ''
        demoForm.data.departTime = nowText()
        demoForm.data.driverName = t.driverName || ''
        demoForm.data.driverPhone = t.driverPhone || ''
        const card = cardOf(t.driverName)
        let how: string
        if (card) {
          // ① 运单安排的司机在备案卡表里 → 刷他的卡，与运单对得上，走「已发车」
          demoForm.data.cardNo = card
          how = `司机上车刷卡（卡号 ${card}）即确认发车：这一刷同时把发车时刻、司机姓名与「本人确认」写进运单。`
        } else if (!t.driverName) {
          // ② 运单还没有司机：刷卡本身就是**唯一**赋予身份的动作，用备案里的演示卡并说清会写成谁
          demoForm.data.cardNo = DEMO_FALLBACK_CARD
          how = `运单上还没有司机，示例用备案里的演示卡 ${DEMO_FALLBACK_CARD}（${DEMO_FALLBACK_DRIVER}）：`
            + '这一刷把发车时刻、司机姓名与「本人确认」一并写进运单 —— 没司机的运单发不出去，这一刷就是那颗闸门。'
        } else {
          // ③ 安排的是不在备案卡表里的人：卡号留空，不替现场编一张必然被判上错车的卡
          demoForm.data.cardNo = ''
          how = `「${t.driverName}」不在司机备案的演示卡表里（只有 张师傅 / 李四 / 王五），`
            + '刷任何一张卡都会被判「现场刷卡的人 ≠ 运单安排的人」转人工 —— 示例没有替他编卡，卡号留空：'
            + '请填他本人的卡号；或到「分割配送 → 冷链运输」编辑这趟车、把司机改成备案司机再演示'
            + '（改司机只会落成「人工录入」，不会冒充本人刷卡确认）。'
        }
        // 拒收回运的处理运单也归这台终端管：发车/到站都只认「车牌 + 状态」，不分这趟送门店还是送无害化中心
        const kind = String(t.transportKind || '')
        const isReturn = kind === 'DISPOSAL'
        // 召回货是「收运单」：冷链中心 → 门店（随车扫码枪收货）→ 无害化处理中心。
        // 跟拒收回运的「已在冷链中心、只等送无害化」不是一回事，文案不能混着叫「待发车运单」。
        const isRecallLeg = kind === 'RECALL'
        // 收运单承运的就是处理计划里那批召回货：批次/门店点名，示例值与处理计划对得上号
        const recallCargo = isRecallLeg ? await describeRecallLegCargo(t.id) : ''
        sampleHint.value = `取到${isRecallLeg ? '收运运单' : isReturn ? '拒收回运的处理运单' : '待发车运单'} ${t.transportNo}（车牌 ${t.vehicleNo}）`
          + (t.driverName ? `，计划安排的司机是 ${t.driverName}` : '，运单上还没有司机（装车时不采集）')
          + '。' + how
          + (recallCargo ? `承运的是处理计划里的召回货：${recallCargo}。` : '')
          + (isReturn
            ? '这一趟跑的是「冷链中心 → 无害化处理中心」：到站同样刷这张卡，最后由无害化处理中心确认签收，链条才终结。'
            : isRecallLeg
              ? '这一趟跑的是「冷链中心 → 门店 → 无害化处理中心」：先在各门店停稳刷卡确认到达，'
                + '再由随车扫码枪扫店里的召回货；末站无害化处理中心确认签收，召回才收口。'
              : '')
          + '上报后运单「已发车」：列表状态从「待发车」转「运输中」。'
          + (autoCreated
            ? '（这张收运运单是刚才按处理计划就地排出来的：计划里那批货已排进这趟车，'
              + '可在「分割配送 → 冷链运输」看到这张待发车运单。）'
            : '')
        // 处理计划里还压着召回货没排车时，顺带摆出来：用户点这里往往就是想演示计划里那批货怎么发出去，
        // 只看运单会以为「示例值没管处理计划」。就地给出口，排完自动重填成这趟收运车。
        const planPending = await poolPendingSummary()
        if (planPending.recall > 0) {
          sampleHint.value += ` 另外，处理计划里还有 ${planPending.recall} 批召回货没排车 —— `
            + '点「按处理计划生成收运运单」就地排一趟收运车（车牌取空闲备案车），排完自动重填。'
          sampleAction.value = {
            text: '按处理计划生成收运运单',
            run: () => generateCollectFromPlan(planPending.recall),
          }
        }
      } else if (plate) {
        // 本机绑了车牌却取不到待发车运单：**不许退回别人的车**（那正是「车牌号也不对」的来源），
        // 直接把本车现状摆出来，让使用者知道下一步该做什么。
        const unshipped = await listUnshippedPlansOfPlate(plate)
        // 这辆车的扫码拒收货还没生成处理运单时，也把这一步点出来（回运单同样是待发车运单的来源）
        const pool = await safeList(() => distributionApi.returnPool('DISPOSAL'))
        const ownScanned = (pool || []).filter((r: any) => r.scanTime && r.scanPlate === plate)
        sampleHint.value = `本终端装在「${plate}」上（设备名里就写着车号），它只能报自己这辆车。`
          + `${describePlateTransports(await listTransportsOfPlate(plate))}，其中没有「待发车」的。`
          + (ownScanned.length
            ? `另外，处理计划里有 ${ownScanned.length} 站是这台车扫码的拒收货（${ownScanned.map((r: any) => r.batchNo || r.transportNo).join('、')}），`
              + '但还没生成处理运单 —— 生成后它就是这辆车的待发车运单。'
            : '')
          + (unshipped.length
            // 「已排车、没生成运单」正是使用者以为「排了车就能演示」的那一步 —— 点名摆出来并给入口
            ? describeUnshippedPlans(unshipped) + '点过「生成运单」再回来点「填充示例值」即可。'
            : '这辆车名下的计划里也没有已经排好车、等着生成运单的货。发车确认要报的是一辆还没发走的车：'
              + '请先到「装车发运（装车口）」给这辆车装一车货，或到「分割配送 → 冷链运输」为它建一张计划、'
              + '填上车牌后点「生成运单」，再回来点「填充示例值」。')
        if (unshipped.length) {
          sampleJump.value = { text: `去「冷链运输」给 ${plate} 生成运单`, to: { name: 'TransportMonitor' } }
        }
      } else {
        // 没有「待发车」的运单：先分清「真没有车」和「有车但司机刷不出本人」两件事 ——
        // 后者才是「车牌填了、卡号空着」的来源，说成「没有数据」会让人一直找车。
        const waitingAll = await listTransportsByStatus(1)
        const undemo = waitingAll.filter((t: any) => !isDemoable(t))
        const blocker = undemo.length ? null : await describePoolBlockers()
        const recallLeg = undemo.length || blocker ? null : await describeRecallLegBlocker()
        if (undemo.length) {
          sampleHint.value = `现在待发的 ${undemo.length} 张运单（`
            + undemo.slice(0, 3).map((x: any) => `${x.transportNo}·${x.driverName || '未记录司机'}`).join('、')
            + '）司机都不是备案司机：备案里只有 张师傅（8801）/ 李四（8802）/ 王五（8803），'
            + '刷任何一张卡都会被判「司机不是运单安排的人」，卡号没法替你编。'
            + '把这几趟车的司机改成备案司机，或按处理计划排一趟收运车（司机是张师傅，刷卡即本人）再演示。'
        } else if (blocker) {
          sampleHint.value = blocker.text
          if (blocker.jump) sampleJump.value = blocker.jump
          if (blocker.action) sampleAction.value = blocker.action
        } else if (recallLeg) {
          // 处理计划里那批召回货已经排进收运运单（发车那步过了）：别再声称「处理计划是空的」
          sampleHint.value = recallLeg.text
          sampleAction.value = recallLeg.action
        } else {
          sampleHint.value = '本终端不绑车（通用 / 回归用），示例只能从任意一辆待发车的车里挑；'
            + '而现在既没有「待发车」的运单，处理计划里也没有等车的货 —— '
            + '先到「装车发运（装车口）」装一车货（扫码即出单），或在「处理运输」生成一张收运运单，'
            + '再回来点「填充示例值」。'
        }
      }
    } else if (ch === 'ARRIVE') {
      // 到达确认与发车同一套口径：设备只报「哪台车、司机刷的哪张卡、什么时刻」，
      // 运单按车牌倒查、站点按顺序推导 —— **报文里没有门店**（车载终端读不到门店主数据）。
      // 示例值取本终端所在那辆车「未结束（运输中 / 待签收）、且还有站点没到」的运单（见 findArriveTarget）；
      // 报到第几站由系统推，设备挑不了。
      const plate = demoDevicePlate.value
      const found = await findArriveTarget()
      if (found) {
        const { transport, stops, next } = found
        demoForm.data.vehicleNo = transport.vehicleNo || ''
        demoForm.data.arriveTime = nowText()
        const card = cardOf(transport.driverName)
        // 三档与发车侧同源，但到达侧这条更硬：刷卡人与运单安排的人不符直接判「已拒绝」（不是转人工）。
        const willTake = !!card || !transport.driverName
        let how: string
        if (card) {
          demoForm.data.cardNo = card
          how = `司机在车载终端刷卡（卡号 ${card}）即确认：这一刷把「谁把这批货送到这一站」落到具体的人。`
        } else if (!transport.driverName) {
          demoForm.data.cardNo = DEMO_FALLBACK_CARD
          how = `运单上没记司机（无从比对，这一条系统不判身份），示例用备案里的演示卡 ${DEMO_FALLBACK_CARD}`
            + `（${DEMO_FALLBACK_DRIVER}）：这一刷会把「${DEMO_FALLBACK_DRIVER}」记成本站的确认人。`
        } else {
          demoForm.data.cardNo = ''
          how = `「${transport.driverName}」不在司机备案的演示卡表里（只有 张师傅 / 李四 / 王五），`
            + '刷任何一张卡都会被判「现场刷卡的人 ≠ 运单安排的人」——到达侧直接判「已拒绝」（司机重刷即可自愈，'
            + '不占人工队列），所以示例没有替他编卡，卡号留空：请填他本人的卡号；'
            + '或到「分割配送 → 冷链运输」编辑这趟车、把司机改成备案司机再演示。'
        }
        // 回运单到的是无害化处理中心那唯一一站：同样由这张卡确认「谁把拒收货送到了」
        const arriveKind = String(transport.transportKind || '')
        const isReturn = arriveKind === 'DISPOSAL'
        const isRecallLeg = arriveKind === 'RECALL'
        // 召回那趟车承运的就是处理计划里的货：把批次/门店点名，示例值与处理计划才对得上号
        const recallCargo = isRecallLeg ? await describeRecallLegCargo(transport.id) : ''
        sampleHint.value = `取到${isRecallLeg ? '收运运单（门店收货）' : isReturn ? '拒收回运的处理运单' : '运输中运单'} ${transport.transportNo}`
          + `（车牌 ${transport.vehicleNo}，司机 ${transport.driverName || '未记录'}），本趟共 ${stops.length} 站；`
          + (recallCargo ? `这趟车承运的就是处理计划里那批召回货：${recallCargo}。` : '')
          + `运单上「当前第一个还没到达的站」是第 ${next.stopNo} 站（${next.storeName}）。`
          + '报文里不需要填门店，系统按站点顺序自己推。'
          + how
          + (willTake
            ? `这一次刷卡将把第 ${next.stopNo} 站记成「已到达」；`
              + (stops.length > 1
                ? '一车多店：每刷一次卡推进一站，全部站点都到了运单才转「待签收」。'
                : '单站直达：刷完这一次运单即转「待签收」。')
            : `补齐卡号后重报，这一次刷卡才会把第 ${next.stopNo} 站记成「已到达」。`)
        const planPending = await poolPendingSummary()
        if (planPending.recall > 0) {
          sampleHint.value += ` 另外，处理计划里还有 ${planPending.recall} 批召回货没排车；`
            + '点「按处理计划生成收运运单」就能排一趟收运车，排完自动重填。'
          sampleAction.value = {
            text: '按处理计划生成收运运单',
            run: () => generateCollectFromPlan(planPending.recall),
          }
        }
      } else if (plate) {
        // 同上：本机绑了车牌就只认本车，取不到就把本车现状说清楚（含「已经到过站」这种已结束的）。
        // 这里还多认一种「差一步」：车下有待发车的运单 —— 那说明还停在发车那一步，先把话说清楚，
        // 免得使用者以为「到站刷卡」坏了（其实同一台车载终端切个通道就能发车）。
        const own = await listTransportsOfPlate(plate)
        const waiting = own.filter((t: any) => Number(t.status) === 1)
        const unshipped = await listUnshippedPlansOfPlate(plate)
        sampleHint.value = `本终端装在「${plate}」上（设备名里就写着车号），它只能报自己这辆车。`
          + `${describePlateTransports(own)}，其中没有「还没结束、且还有站点没到」的。`
          + (waiting.length
            ? `到达确认倒查的是一趟已经在路上的车，而这辆车还有 ${waiting.length} 张运单停在「待发车」`
              + `（${waiting.map((t: any) => t.transportNo).join('、')}）：请把通道切到「发车确认」`
              + '（同一台车载终端，两个动作共用这一个读卡头）先把这趟车发出去，再回来看「到站刷卡」。'
            : unshipped.length
              ? describeUnshippedPlans(unshipped) + '点过「生成运单」、并把这趟车发出去，才到得了「到站刷卡」这一步。'
              : '请先给这辆车装一车货并在车载终端点发车，或到「分割配送 → 冷链运输」为它建一张计划、'
                + '填上车牌后点「生成运单」，再回来演示「到站刷卡」。')
        if (!waiting.length && unshipped.length) {
          sampleJump.value = { text: `去「冷链运输」给 ${plate} 生成运单`, to: { name: 'TransportMonitor' } }
        }
      } else {
        // 先分清两件事：① 车在跑但司机不是备案司机（刷不出本人）；② 根本没有在途的车。
        // 混成一句「没有在途运单」会让人以为车都停了，其实是车上的卡刷不出人 —— 这才是卡号空的原因。
        const aliveAll = plate
          ? (await listTransportsOfPlate(plate))
          : [...(await listTransportsByStatus(2)), ...(await listTransportsByStatus(3))]
        const aliveCars = aliveAll.filter((t: any) => [2, 3].includes(Number(t.status)))
        const undemoable = aliveCars.filter((t: any) => !isDemoable(t))
        const waiting = await listTransportsByStatus(1)
        if (undemoable.length) {
          sampleHint.value = `现在在途的 ${undemoable.length} 趟车（`
            + undemoable.slice(0, 3).map((x: any) => `${x.transportNo}·${x.driverName || '未记录司机'}`).join('、')
            + '）司机都不是备案司机：备案里只有 张师傅（8801）/ 李四（8802）/ 王五（8803），'
            + '这些车刷任何一张卡都会被判「现场刷卡的人 ≠ 运单安排的人」，所以示例值没有替它编卡号。'
            + (waiting.length
              ? '要把到达演示成「成功入账」，先切到「发车确认」发一趟备案司机的车，再回来刷这一趟。'
              : '要把到达演示成「成功入账」，先到「发车确认」发一趟备案司机的车（处理计划里的召回货可按计划排成收运车，司机就是张师傅），再回来刷这一趟。')
          sampleAction.value = {
            text: '切到「发车确认」',
            run: async () => {
              demoForm.channel = 'DEPART'
              resetDemo()
              await fillSample()
            },
          }
        } else if (waiting.length) {
          sampleHint.value = `现在没有在路上跑的车，但有 ${waiting.length} 张停在「待发车」的运单`
            + `（${waiting.slice(0, 3).map((x: any) => `${x.transportNo}·${x.vehicleNo}`).join('、')}）：`
            + '到达确认只能报已经在路上的车。同一台车载终端两个动作共用一个读卡头 —— '
            + '先把它发出去，再回来点「填充示例值」就能填到车了。'
          sampleAction.value = {
            text: '切到「发车确认」',
            run: async () => {
              demoForm.channel = 'DEPART'
              resetDemo()
              await fillSample()
            },
          }
        } else {
          // 同理：没有在途的车时，把「车还在哪一步」说出来，而不是让使用者对着空表单猜
          const blocker = await describePoolBlockers()
          if (blocker) {
            sampleHint.value = '到达确认只能报一列已经在路上的车，现在没有这样的车 —— ' + blocker.text
            if (blocker.jump) sampleJump.value = blocker.jump
            if (blocker.action) sampleAction.value = blocker.action
          } else {
            sampleHint.value = '本终端不绑车（通用 / 回归用），示例只能从任意一辆在途的车里挑；'
              + '而现在既没有在途运单、处理计划也是空的 —— '
              + '先用「发车确认」通道把一趟车发出去（同一台终端两个动作），再回来演示「到站刷卡」。'
          }
        }
      }
    } else if (ch === 'SPLIT_SNAPSHOT') {
      // 分割车间监控：正常路径下**不需要人来这里报** —— 分割线上报之后相机会自动连拍并绑定批次。
      // 留这一条是兜底：相机没配、或要单独补一张现场照片时，仍能手工走通同一条两段式链路。
      // 示例值只要凑齐「一张真照片 + 拍摄时刻」；照片必须真的上传到文件服务 —— 接入层会调
      // /file/{fileId}/meta 核对，编一个 fileId 报上去会被判「已拒绝」。
      demoForm.data.capturedTime = nowText()
      if (!demoForm.data.workshop) demoForm.data.workshop = '分割一号线'
      // 手工补报也建议填批次号：带了就绑定到该批次（页面按批次展示），不带就只是不落归属
      if (!demoForm.data.batchNo) {
        const batch = await findCarcassBatch()
        if (batch?.batchNo) demoForm.data.batchNo = batch.batchNo
      }
      const photoId = await uploadDemoSnapshotPhoto()
      if (photoId) {
        demoForm.data.fileIds = [photoId]
        demoPhotoFiles.value = [{ name: 'split-snapshot.png', url: '/demo/vaccine-voucher.png', status: 'success', uid: photoId }]
        sampleHint.value = '已取一张内置演示合成图并真实上传到文件服务，拿到真 fileId —— '
          + '接入层核对通过后照常入账；照片只是现场影像，不参与任何业务判定。'
          + '提示：正常演示不必走这里 —— 用「分割批次（扫码称重）」上报一次，相机会自动连拍并绑定批次。'
          + '每台相机只留最近 20 张，超出的连文件一起淘汰。'
      } else {
        sampleHint.value = '演示照片上传失败（文件服务不可用）。可手动上传一张现场照片再提交 —— '
          + '接入层会核对照片真实存在，编造的 fileId 会被判「已拒绝」。'
      }
    }
  } catch (e: any) {
    sampleHint.value = `填充失败：${e?.message || '请手工填写'}`;
  } finally {
    sampling.value = false
  }
}

async function submitDemo() {
  // 已受理（已入账 / 待人工确认）就不让再报：按钮已置灰，这里再兜一道，防回车等方式绕过
  if (demoReportLocked.value) return
  // 照片还在上传就先等一等，否则记录会缺凭证
  if (photoUploadingCount.value > 0) {
    ElMessage.warning('凭证照片正在上传，请稍候再提交')
    return
  }
  const missing = demoFields.value
    .filter((f) => f.required && f.type !== 'items' && !hasValue(demoForm.data[f.key]))
    .map((f) => f.label)
  if (missing.length) {
    ElMessage.warning(`请先填写：${missing.join('、')}`)
    return
  }
  // 数值字段按数字提交，否则后端会当成字符串解析失败
  const payload: Record<string, any> = {}
  for (const f of demoFields.value) {
    const raw = demoForm.data[f.key]
    if (f.type === 'items') {
      if (Array.isArray(raw) && raw.length) payload[f.key] = raw
      continue
    }
    if (!hasValue(raw)) continue
    payload[f.key] = f.type === 'number' ? Number(raw) : raw
  }
  // 部位清单为空的通道（分割产出 / 订单订购）都没有意义：空清单要么退化成单条模式拿不到重量，
  // 要么让后端只能回一句「缺少货物明细」，文案不直观
  const itemsField = demoFields.value.find((f) => f.type === 'items')
  if (itemsField && !(payload.items || []).length) {
    ElMessage.warning(`请至少填写一项「${itemsField.label}」（部位取自标准部位字典）`)
    return
  }

  demoSubmitting.value = true
  try {
    const res: any = await ingestApi.demoReport({
      deviceNo: demoForm.deviceNo,
      // 通道显式传：一台车载终端两个通道（发车确认 + 到站刷卡确认），服务端只校验
      // 它是否属于该设备的能力范围 —— 报别组的通道会被当场挡下
      channel: demoChannel.value || undefined,
      bizKey: demoForm.bizKey || undefined,
      reportTime: demoForm.reportTime || undefined,
      data: payload,
    })
    demoResult.value = res
    if (res?.accepted) ElMessage.success('已入账')
    else if (res?.duplicate) ElMessage.warning('该信息已存在，本次拒绝入账')
    else if (res?.status === 1) ElMessage.warning('待人工确认')
    else ElMessage.warning(res?.message || '未入账，具体原因见下方回执')
    fetchStaging()
    fetchDevices()

    // 分割上报入账 = 分割线开切：工位上的车间相机随之自动连拍，照片绑定到这次分割的批次。
    // 等它跑完再放开按钮（连拍期间按钮保持 loading）—— 演示时人能看到「相机真的在拍」，
    // 而不是以为没反应又去点一次。
    if (res?.accepted && demoChannel.value === 'SPLIT') {
      await autoShootAfterSplit(String(payload.parentBatchNo || ''), payload.workshop)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '上报失败')
  } finally {
    demoSubmitting.value = false
  }
}

/** 必填判定：数组（如凭证照片）看是否有内容，其他看是否为空白 */
function hasValue(v: any) {
  if (Array.isArray(v)) return v.length > 0
  return String(v ?? '').trim() !== ''
}

/**
 * 取一辆指定状态的运单，**按「刷卡能不能刷出本人」优先挑**（见 preferCardResolvable）。
 * <p>
 * 现在只服务「只借一个运单号、挑哪张都行」的通道（冷链温度上报）—— 车载终端的发车 / 到达
 * 已经改走 {@link findDepartTarget}/{@link findArriveTarget}：那是**要刷卡**的通道，
 * 挑错必然失败，而且必须先认「本机所在的那辆车」。
 */
async function findTransport(status: number) {
  return preferCardResolvable(await listTransportsByStatus(status))[0] || null
}

/** 运单状态中文名（列表页同一套口径：1 待发车 / 2 运输中 / 3 待签收 / 4 已签收） */
const TRANSPORT_STATUS_LABELS: Record<number, string> = { 1: '待发车', 2: '运输中', 3: '待签收', 4: '已签收' }

/**
 * 按状态取运单，**正常配送 + 拒收回运两种都算**。
 * <p>
 * `GET /distribution/transports` 缺省只返回正常配送单（kind 缺省 NORMAL），回运单要显式带
 * `kind=DISPOSAL`。而车载终端只认「车牌 + 状态」，不关心这趟送的是门店的货还是无害化中心的货 ——
 * 发车（DEPART）与到站（ARRIVE）在后端都是按车牌倒查、不分单种。示例值不带回运单一起查，
 * 演示出来就是「这台终端发不了拒收回运的车」，可实际上只是取单口径漏了它。
 */
/**
 * 车载终端只认「车牌 + 状态」，不关心这趟送门店还是送无害化中心。
 * 三种运单必须一起查：漏掉 RECALL 时，召回收运单在设备侧就等于不存在 ——
 * 处理计划里明明有货、运单也生成了，终端却报「填不出示例值」（2026-11-05 用户报的就是这个）。
 * 后端 `pageTransports` 的 kind 支持逗号多值，一次请求即可。
 */
const ALL_TRANSPORT_KINDS = 'NORMAL,DISPOSAL,RECALL'

async function listTransportsByStatus(status: number, pageSize = 20) {
  const list = await safeList(() => distributionApi.getTransports({
    status, kind: ALL_TRANSPORT_KINDS, pageNum: 1, pageSize,
  }))
  return list || []
}

/** 一辆车牌名下的全部运单（不限状态，新的在前）。车牌用 keyword 反查，再按车牌精确过滤（keyword 是 LIKE）。 */
async function listTransportsOfPlate(plate: string) {
  const list = await safeList(() => distributionApi.getTransports({
    keyword: plate, kind: ALL_TRANSPORT_KINDS, pageNum: 1, pageSize: 50,
  }))
  return (list || []).filter((t: any) => t.vehicleNo === plate)
}

/** 该车名下现有运单的一句话清单 —— 用于说清「为什么这辆车的示例值取不到」 */
function describePlateTransports(list: any[]) {
  if (!list.length) return '这辆车名下还没有任何运单'
  return '这辆车名下现有的运单：' + list
    .map((t: any) => `${t.transportNo}（${TRANSPORT_STATUS_LABELS[Number(t.status)] || '未知'}）`)
    .join('、')
}

/** 计划状态中文名（与「冷链运输」页同一个口径：1 待发货 / 4 部分排车 / 2 已排车 / 3 已取消） */
const PLAN_STATUS_LABELS: Record<number, string> = { 1: '待发货', 2: '已排车', 3: '已取消', 4: '部分排车' }

/**
 * 该车牌名下「还有货没排进运单」的计划（待发货 1 / 部分排车 4）。
 * <p>
 * 这是「排车」与「生成运单」之间那道缝：在计划里填上车牌只把车定下来了，货还挂在计划上、
 * 没挂到运单上。而发车 / 到达确认倒查的是**运单**，此刻必然取不到示例值 ——
 * 使用者看到的就是「我明明排车了，怎么还填不了」。把这几张计划点名摆出来，
 * 差的哪一步（点一下「生成运单」）才说得清。
 */
async function listUnshippedPlansOfPlate(plate: string) {
  const list = await safeList(() => distributionApi.getPlans({ keyword: plate, pageNum: 1, pageSize: 50 }))
  // keyword 走的是 LIKE，必须再按车牌精确比对（同一个关键字可能命中计划号、门店名等别的字段）
  return (list || []).filter((p: any) => p.vehicleNo === plate && [1, 4].includes(Number(p.status)))
}

/** 上面那组计划的一句话说明：点出「车已排、运单还没生成」，这是取不到示例值的直接原因 */
function describeUnshippedPlans(list: any[]) {
  if (!list.length) return ''
  const names = list.map((p: any) => `${p.planNo}（${PLAN_STATUS_LABELS[Number(p.status)] || '未知'}）`).join('、')
  return `这辆车名下还有 ${list.length} 张计划已经排了车、但还没生成运单：${names}。`
    + '「排车」只是把车定在计划上；货要在「冷链运输」页点过「生成运单」，才会以「待发车」的状态挂到运单上。'
}

/**
 * 这张运单值不值得当示例：**能不能演示成「成功入账」**。
 * <p>
 * 司机在备案卡表里（刷本人卡 → 入账）或运单还没记司机（刷卡本身就是赋身份的动作）→ 值得；
 * 司机是别人（演示库里有「吴师傅」「周司机」这种没进备案的名字）→ **不值得**：刷谁的卡都会被判
 * 「现场刷卡的人 ≠ 运单安排的人」，发车侧转人工、到达侧直接「已拒绝」。
 * 那是**反面**路径，不该是点「填充示例值」的默认结果 —— 使用者的观感就是
 * 「示例取了一辆陌生车、司机还是别人」，本轮就是这么被报上来的。
 */
function isDemoable(t: any) {
  return !t.driverName || !!DEMO_DRIVER_CARDS[t.driverName]
}

/**
 * 召回优先：同时有普通配送车和召回收运车时，把收运车排前面。
 * <p>
 * 使用者点「填充示例值」多半就是想在设备侧把处理计划里那批召回货走一遍 —— 取到一辆普通配送车，
 * 观感就是「填充的示例值跟召回那部分没关系」（2026-11-05 用户报障）。
 * `Array.prototype.sort` 是稳定排序，配合 `preferCardResolvable` 时「卡刷得出本人」仍然优先，
 * 只在同级里把召回提前 —— 宁可演示召回，也不牺牲「刷了能入账」。
 */
function preferRecallLeg<T extends { transportKind?: string | null }>(list: T[]): T[] {
  const isRecall = (t: T) => (String(t.transportKind || '') === 'RECALL' ? 1 : 0)
  return [...list].sort((a, b) => isRecall(b) - isRecall(a))
}

/**
 * 发车确认要报的那趟车。
 * <p>
 * ① **本机绑了车牌就只认这辆车**：车载终端装在车上（台账 `vehicle_no` 就是它的车牌，设备名里
 * 也写着车号），它只能报自己这一辆 —— 退回别人的车，示例值给的就是一辆陌生的「京B-TEST…」
 * 和别人的司机（吴师傅），车牌与设备名里写的车号对不上。
 * ② **本机不绑车（通用终端）才退回「任意一趟」**，且只认 `isDemoable` 的：没有可演示的车宁可
 * 报「没有」，也不拿一辆陌生车凑数。
 */
async function findDepartTarget() {
  const plate = demoDevicePlate.value
  if (!plate) {
    // 正常配送单、拒收回运单、召回收运单都算：终端只认车牌，送门店还是送无害化中心与它无关。
    // 优先「刷卡能刷出本人」的车；实在没有，也退回一辆真实待发车 —— 表单填得出来、再把
    // 卡号为什么留空说清楚，比给一张空表单让人以为功能坏了强（2026-11-05 用户报的就是空表单）。
    const list = await listTransportsByStatus(1)
    // 只取「刷得出本人」的车：司机不在备案卡表的单，车牌填得出来、卡号却只能空着（刷谁都判身份不符），
    // 发不出去。与其给半张表单，不如当成「没有可发的车」，让上层去排一趟自己的车。
    // 同级里召回优先（见 preferRecallLeg）：使用者要演示的往往就是处理计划里那批召回货。
    return preferCardResolvable(preferRecallLeg(list.filter(isDemoable)))[0] || null
  }
  const own = await listTransportsOfPlate(plate)
  // 本机车牌下不筛司机：这辆车就是本机要报的车，司机不是备案里的人也照样是**它**的单子，
  // 由 caller 按「三档」把卡号怎么填说清楚（见发车分支的 how）。
  return preferCardResolvable(preferRecallLeg(own.filter((t: any) => Number(t.status) === 1)))[0] || null
}

/**
 * 到达确认要报的那趟车：**未结束（运输中 2 / 待签收 3）且还有站点没到**的运单。
 * <p>
 * 与发车同一条口径（绑了车牌就只认本机所在的车；不绑车才退回任意一趟，且只认可演示的）。
 * 另外两处对齐后端：① 倒查认的是**未结束的运单**（2、3 都算，后端 `ingestArrive` 就是这么查的），
 * 不是只认 2；② 站点全到的那种车演示不出「确认到达」（只会回「所有站点都已到达」），连站点一起筛。
 */
async function findArriveTarget() {
  const plate = demoDevicePlate.value
  const pool = plate
    ? await listTransportsOfPlate(plate)
    : [
        ...(await listTransportsByStatus(2)),
        ...(await listTransportsByStatus(3)),
      ]
  const alive = pool.filter((t: any) => [2, 3].includes(Number(t.status)))
  // 只取「刷得出本人」的车：司机是备案司机（有卡）或运单还没记司机（刷卡赋身份）。
  // 司机是别人（库里那种「吴师傅」的车）不能拿来当到达示例 —— 刷谁的卡都会被判「身份不符」，
  // 取它只会得到「车牌填了、卡号空」，使用者看到的就是「卡号没有回填」（2026-11-05 报障）。
  const candidates = preferCardResolvable(preferRecallLeg(alive.filter(isDemoable)))
  for (const transport of candidates) {
    const stops = await safeList(() => distributionApi.getTransportStops(transport.id))
    const next = (stops || []).find((s: any) => Number(s.status || 0) < 1)
    if (next) return { transport, stops, next }
  }
  return null
}

/**
 * 找一具「已盖章、且还没归进任何胴体批次」的白条，供胴体批次归集的示例值使用。
 * <p>
 * 归集有三道硬前提：① 已盖胴体章（白条取得放行凭证）；② 瘦肉精已检无阳性；③ 未被其他批次占用。
 * 盖过章的猪必然已过宰后同步检疫合格，② 基本同时成立；真正要挑的是 ③ ——
 * 随手拿一具已归批的白条去演示，只会得到「已归入 XX 批次，本次未重复入批」的回执，
 * 看不出归集过程本身。
 */
async function findStampedPig(): Promise<{ pigId: number; earTagNo: string } | null> {
  const stamps = await safeList(() => slaughterApi.getStamps({ pageNum: 1, pageSize: 50 }))
  const batches = await safeList(() => distributionApi.getBatches({ pageNum: 1, pageSize: 200 }))
  const occupied = new Set<number>()
  batches.forEach((b: any) => (b?.pigIds || []).forEach((id: any) => occupied.add(Number(id))))
  for (const s of stamps) {
    const pigId = Number(s?.pigId ?? s?.pig_id)
    const earTagNo = s?.earTagNo ?? s?.ear_tag_no
    if (!pigId || !earTagNo || occupied.has(pigId)) continue
    return { pigId, earTagNo }
  }
  return null
}

/** 取一个有效的胴体批次（建立分割批次的父级） */
async function findCarcassBatch() {
  const page = await safeList(() => distributionApi.getBatches({ pageNum: 1, pageSize: 1 }))
  return page?.[0] || null
}

/**
 * 取一张「还有货没发」的发货计划，连同它未发运的批次号清单。
 * <p>
 * 装车口的示例值必须落在真实计划上：目的地只能从计划来。随便编一个批次号去演示，
 * 只会得到「不属于任何待发货计划」的转人工回执 —— 那正好是设计如此，但不适合当默认示例。
 */
/**
 * 演示用的车牌候选：装车口上报按**车牌**去找那张还没发车的运单，
 * 撞上就会被并入那一趟车（业务上完全正确 —— 车没走就是同一趟车继续装），
 * 但对着屏幕演示时会让人以为「运单根本没建出来」。
 * 所以取示例值前先看看哪些车牌已经挂着待发车运单，尽量避开。
 * 这三个号与车辆备案的种子数据一致：车型、载重都查得到，运单不会显示「车型未知」。
 */
const DEMO_LOAD_PLATES = ['京A·12345', '京A·99999', '京B·10086']

/** 正在「待发车(1)」的车牌集合 —— 装车口再报这几个车牌会被并入那趟车 */
async function fetchPendingPlates(): Promise<Set<string>> {
  const list = await safeList(() => distributionApi.getTransports({ status: 1, pageNum: 1, pageSize: 200 }))
  return new Set(list.map((t: any) => t.vehicleNo).filter(Boolean))
}

/**
 * 名下还挂着未完成运单（待发车 1 / 运输中 2 / 待签收 3）的车牌集合。
 * <p>
 * 与 {@link fetchPendingPlates} 的区别：那个只关心「会不会被并入同一趟车」（装车口语义），
 * 这里关心「这辆车还能不能接新活」—— 一车一趟（ActiveTransportGuard 拦的是 1/2/3 全部），
 * 正跑着的车给它排新单会被直接拒绝，所以回运扫码的示例车牌必须避开这三种状态。
 */
async function fetchBusyPlates(): Promise<Set<string>> {
  const busy = new Set<string>()
  for (const status of [1, 2, 3]) {
    const list = await listTransportsByStatus(status, 200)
    list.forEach((t: any) => { if (t.vehicleNo) busy.add(t.vehicleNo) })
  }
  return busy
}

/** 从演示车牌里挑一个还没挂待发车运单的；全占着就只能用第一个（提示语里会说明本次会并入） */
function pickFreePlate(pending: Set<string>) {
  return DEMO_LOAD_PLATES.find((p) => !pending.has(p)) || DEMO_LOAD_PLATES[0]
}

async function findLoadablePlan(pending: Set<string>) {
  // 车牌已经挂着「待发车」运单的计划：能避开就避开 —— 再报会被并入那趟车，
  // 演示看不出「新建了一张运单」，也看不出这张计划排进了哪张单。
  // 实在没有别的计划可用时才退回它，并在提示语里说明本次会并入。
  let fallback: {
    planNo: string; storeName: string; vehicleNo: string
    driverName: string; driverPhone: string; batchNos: string[]; plateOccupied: boolean
  } | null = null
  for (const status of [1, 4]) {
    const plans = await safeList(() => distributionApi.getPlans({ status, pageNum: 1, pageSize: 20 }))
    for (const plan of plans) {
      try {
        const detail: any = await distributionApi.getPlanDetail(plan.id)
        const pendingItems = (detail?.items || []).filter((i: any) => !i.shipped)
        if (!pendingItems.length) continue
        const item = {
          planNo: plan.planNo,
          storeName: plan.storeName,
          vehicleNo: plan.vehicleNo,
          driverName: plan.driverName,
          driverPhone: plan.driverPhone,
          batchNos: pendingItems.map((i: any) => i.batchNo).filter(Boolean),
          plateOccupied: !!plan.vehicleNo && pending.has(plan.vehicleNo),
        }
        if (item.plateOccupied) {
          fallback = fallback || item
          continue
        }
        return item
      } catch {
        // 这张计划取不到明细就跳过，继续看下一张
      }
    }
  }
  return fallback
}

/**
 * 取一条「结论合格」的**宰后**检验记录，且这头猪的瘦肉精是「已检且无阳性」。
 * 不依赖后端的结果过滤参数：拉一小页后在本地筛，避免参数名不一致时静默取不到。
 * 为什么必须是宰后（2=宰后检验 / 3=宰后同步检验）：盖章发生在同步检疫之后，
 * 只做过宰前检查的猪业务侧会直接拒绝（只有一条宰前合格不构成盖章依据）；
 * 历史数据里也存在「瘦肉精没检就检验」的记录，直接拿它会演示出一条拒绝回执。
 *
 * @param accept 额外过滤（如盖章要用它排掉「已经盖过章」的白条 —— pig_id 是唯一键）
 */
async function findPassedInspection(accept?: (pigId: number) => boolean) {
  const pre = await fetchPreSlaughterContext()
  const passed = pre.inspections.filter(
    (r: any) => r?.result === 1 && [2, 3].includes(Number(r?.inspectType ?? r?.inspect_type))
  )
  if (!passed.length) return null
  const ctx = await fetchRactopamineContext()
  return passed.find((r: any) => {
    const pigId = Number(r?.pigId ?? r?.pig_id)
    if (accept && !accept(pigId)) return false
    return ractopamineClear(pigId, r?.batchNo ?? r?.batch_no, ctx)
  }) || null
}

/**
 * 拉取「这头猪通过宰前检查没有」所需的数据。
 * 宰前检查按法规是<b>逐头</b>做的（不是按批抽检），所以只看这头猪自己的记录：
 * 有一条结论合格的宰前记录 → 准予屠宰；一条都没有 → 未做宰前检查，不得屠宰。
 * 返回里的 inspections 给别处复用，避免同一份数据重复请求。
 */
async function fetchPreSlaughterContext() {
  const inspections = await safeList(() => slaughterApi.getInspections({ pageNum: 1, pageSize: 300 }))
  const pre = inspections.filter((r: any) => Number(r?.inspectType ?? r?.inspect_type) === 1)
  const pigOf = (r: any) => Number(r?.pigId ?? r?.pig_id)
  return {
    inspections,
    prePigs: new Set(pre.map(pigOf)),
    prePassedPigs: new Set(pre.filter((r: any) => r?.result === 1).map(pigOf)),
  }
}

/**
 * 一站式拉取「这头猪能不能往下走」所需的检测状态：已检/阳性的猪与批次，以及猪→入场批次。
 * 判定粒度与后端一致——瘦肉精按批抽检，「已检」看该猪自身或它所属批次有没有出结果的记录。
 */
async function fetchRactopamineContext() {
  const entries = await safeList(() => slaughterApi.getEntries({ pageNum: 1, pageSize: 200 }))
  const tests = await safeList(() => slaughterApi.getRactopamine({ pageNum: 1, pageSize: 300 }))
  const entryBatch = new Map<number, string>()
  for (const e of entries) {
    const pigId = Number(e?.pigId ?? e?.pig_id)
    const batchNo = e?.batchNo ?? e?.batch_no
    if (pigId && batchNo && !entryBatch.has(pigId)) entryBatch.set(pigId, batchNo)
  }
  // 「出了结果」才算检过：待检测/检测中的记录不能当作已检
  const withResult = tests.filter((t: any) => t?.result !== null && t?.result !== undefined && t?.result !== '')
  const positive = tests.filter((t: any) => t?.result === 0)
  const batchOf = (t: any) => t?.batchNo ?? t?.batch_no
  return {
    entries,
    entryBatch,
    testedPigs: new Set(withResult.map((t: any) => Number(t?.pigId ?? t?.pig_id))),
    testedBatches: new Set(withResult.map(batchOf).filter(Boolean)),
    positivePigs: new Set(positive.map((t: any) => Number(t?.pigId ?? t?.pig_id))),
    positiveBatches: new Set(positive.map(batchOf).filter(Boolean)),
  }
}

/** 这头猪能否进屠宰检验：自身与批次都无阳性，且自身或批次已有出结果的检测记录 */
function ractopamineClear(pigId: number, batchNo: string | undefined, ctx: any) {
  if (!pigId) return false
  if (ctx.positivePigs.has(pigId)) return false
  if (batchNo && ctx.positiveBatches.has(batchNo)) return false
  if (ctx.testedPigs.has(pigId)) return true
  return !!batchNo && ctx.testedBatches.has(batchNo)
}

/**
 * 找一头能顺利做屠宰检验的猪：已入场查验 + 瘦肉精已检且无阳性。
 * 批次号沿用入场查验记录的批次号，与后续盖章、分割保持同一批口径。
 * 同时告诉调用方这头猪「宰前检查过没有」——没通过宰前检查的话，示例值得先把类型给成宰前检查，
 * 直接报宰后会被「未做宰前检查不得屠宰」这道门槛拦下。
 */
async function findPigReadyForInspection() {
  const ctx = await fetchRactopamineContext()
  const pre = await fetchPreSlaughterContext()
  for (const entry of ctx.entries) {
    const pigId = Number(entry?.pigId ?? entry?.pig_id)
    const earTagNo = entry?.earTagNo ?? entry?.ear_tag_no
    const batchNo = entry?.batchNo ?? entry?.batch_no
    if (!pigId || !earTagNo || !batchNo) continue
    if (!ractopamineClear(pigId, batchNo, ctx)) continue
    return {
      pigId,
      earTagNo,
      batchNo,
      prePassed: pre.prePassedPigs.has(pigId),
      preChecked: pre.prePigs.has(pigId),
    }
  }
  return null
}

/**
 * 找一头能顺便把「瘦肉精 → 屠宰检验 → 盖章 → 分割」演示完的猪。
 *
 * 瘦肉精是按批抽检，一批检出阳性则整批受控，所以示例值必须避开两类猪：
 *   1) 自身检出过阳性的猪；
 *   2) 所属批次里有阳性样本的猪（哪怕它自己没检出）。
 * 批次号沿用入场查验记录的批次号，让这头猪在后续环节的批次口径保持一致。
 */
async function findPigForRactopamine() {
  const ctx = await fetchRactopamineContext()
  for (const entry of ctx.entries) {
    const pigId = Number(entry?.pigId ?? entry?.pig_id)
    const earTagNo = entry?.earTagNo ?? entry?.ear_tag_no
    const batchNo = entry?.batchNo ?? entry?.batch_no
    if (!pigId || !earTagNo || !batchNo) continue
    if (ctx.positivePigs.has(pigId) || ctx.positiveBatches.has(batchNo)) continue
    return { pigId, earTagNo, batchNo }
  }
  return null
}

/**
 * 拉全量生猪候选（翻页取尽，可按状态过滤）。
 * <p>
 * 演示示例值必须扫全库：只取第一页时，真正满足条件的那头猪会被后来新增的
 * 演示猪挤出窗口，页面就误报「没有满足要求的猪」——ET-DEMO-0133 正是如此：
 * 它三条件齐（审批通过 + 已出栏 + 证有效），却因按创建时间倒序排在第 44 位、
 * 落在 pageSize=30 之外而没被看到。
 */
async function fetchAllPigs(status?: string): Promise<any[]> {
  const size = 100
  const all: any[] = []
  for (let page = 1; page <= 20; page++) {
    const res: any = await safeGet(() => pigApi.list({ pageNum: page, pageSize: size, status }))
    const list = Array.isArray(res) ? res : (res?.records || res?.list || [])
    if (!Array.isArray(list) || list.length === 0) break
    all.push(...list)
    const total = Number(res?.total ?? 0)
    if (list.length < size || (total > 0 && all.length >= total)) break
  }
  return all
}

/**
 * 找一头「出栏审批已通过 + 状态可入场 + 挂着有效产地检疫证」的猪，供入场查验示例值使用。
 * <p>
 * 入场查验有三道前提，缺一道都演示不出自动入账：
 *   1) 出栏审批已通过——未经审批的猪进不了厂，业务侧会直接拒绝（这是硬前提，人工确认也放不过去）；
 *   2) 生猪状态还是「在养」或「已出栏」——「已屠宰」「异常」是终态，一头猪只能进厂一次，
 *      拿已屠宰的猪演示只会得到一条拒绝回执（历史演示猪多半已被推进到「已屠宰」）；
 *   3) 检疫证核验要过（证存在、未过期、证上耳标与扫描耳标一致）——所以要取「同一头猪自己的耳标和它的证号」，
 *      不能跨猪拼；证还必须没过期，否则填了也白填——核验会直接判 valid=false 转人工；
 *   此外优先挑「还没入场查验过」的猪——已查验过的再报只会命中重复兜底、返回既有记录，看不出新入账。
 */
async function findPigWithCert() {
  // 候选池取全量（服务端按状态过滤），不能只取第一页——满足条件的猪可能排在很后面
  const list = await fetchAllPigs('1,2')
  // 审批状态：只有「1 通过」可以入场；0 待审、2 驳回都不行
  const applies = await safeList(() => pigApi.getApplies({ pageNum: 1, pageSize: 200 }))
  const approved = new Set(applies.filter((a: any) => a?.approvalStatus === 1).map((a: any) => Number(a.pigId)))
  const entries = await safeList(() => slaughterApi.getEntries({ pageNum: 1, pageSize: 200 }))
  const admitted = new Set(entries.map((e: any) => Number(e.pigId)))

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const usable: { id: number; earTagNo: string; certNo: string; admitted: boolean; note?: string }[] = []
  // 完全合格的猪找不到时，退一步用「审批通过、状态可入场，但证过期 / 还没出证」的猪：
  // 表单照样填得出来，提示把缺的那一步说清楚 —— 比给一张空表单强（用户 2026-11-05 报的就是点了没回填）。
  const weak: { id: number; earTagNo: string; certNo: string; admitted: boolean; note: string }[] = []
  // 最后一层：连「审批通过」的猪都没有时（演示库里常见——审批通过的都早已屠宰），
  // 至少拿一头在养的猪把耳标填上，并点明缺的是「申报出栏」。空表单等于告诉使用者功能坏了。
  let anyAlive: { id: number; earTagNo: string } | null = null
  for (const pig of list) {
    const earTagNo = pig?.earTagNo || pig?.ear_tag_no
    if (!earTagNo || !pig?.id) continue
    if (!anyAlive && Number(pig.status) === 1) anyAlive = { id: pig.id, earTagNo }
    if (!approved.has(Number(pig.id))) continue
    // 状态：1 在养 / 2 已出栏 才可能入场；3 已屠宰、4 异常 是终态，业务侧会直接拒绝
    if (![1, 2].includes(Number(pig.status))) continue
    const cert = await safeGet(() => pigApi.getQuarantineCert(pig.id))
    const certNo = cert?.certNo || cert?.cert_no
    if (!certNo) {
      weak.push({
        id: pig.id, earTagNo, certNo, admitted: admitted.has(Number(pig.id)),
        note: '这头猪还没有产地检疫证（入场查验要核验证件）',
      })
      continue
    }
    const validUntil = cert?.validUntil || cert?.valid_until
    if (validUntil && new Date(String(validUntil).slice(0, 10)) < today) {
      weak.push({
        id: pig.id, earTagNo, certNo, admitted: admitted.has(Number(pig.id)),
        note: `这头猪的检疫证 ${certNo} 已经过了有效期`,
      })
      continue
    }
    usable.push({ id: pig.id, earTagNo, certNo, admitted: admitted.has(Number(pig.id)) })
  }
  // 没查验过的排前面：演示出来的才是一条真正的新入场记录
  usable.sort((a, b) => Number(a.admitted) - Number(b.admitted))
  if (usable[0] || weak[0]) return usable[0] || weak[0]
  if (anyAlive) {
    const cert = await safeGet(() => pigApi.getQuarantineCert(anyAlive!.id))
    return {
      id: anyAlive.id,
      earTagNo: anyAlive.earTagNo,
      certNo: cert?.certNo || cert?.cert_no || '',
      admitted: admitted.has(Number(anyAlive.id)),
      note: '这头猪还没有在「出栏申报」里申报审批（入场查验要求审批已通过）',
    }
  }
  return null
}

/** 取单个对象；无数据或接口报错时返回 null（作废或无证会走异常） */
async function safeGet(fn: () => Promise<any>): Promise<any> {
  try {
    return (await fn()) ?? null
  } catch {
    return null
  }
}

/**
 * 找一头「还没有有效产地检疫证」的在养猪。
 * 扫码登记演示必须用它：一头猪同时只能有一张有效证，
 * 挑到已出证的猪会转「待人工确认」，演示不出自动登记。
 * 无证时接口返回业务异常，safeGet 吞掉返回 null —— 正好作为「这头可用」的判据。
 */
async function findPigWithoutCert(): Promise<{ id: number; earTagNo: string } | null> {
  // 同样取全量在养猪：只扫前 10 头时，出证状态参差的老演示猪会把候选挤没
  const list = await fetchAllPigs('1')
  for (const pig of list) {
    const earTagNo = pig?.earTagNo || pig?.ear_tag_no
    if (!earTagNo || !pig?.id) continue
    const cert = await safeGet(() => pigApi.getQuarantineCert(pig.id))
    if (!cert) return { id: pig.id, earTagNo }
  }
  return null
}

/** 取一头在养生猪填入耳标号，再执行通道专属的默认值填充（免疫/检验共用） */
async function fillByPig(desc: string, extra: () => void) {
  const page = await safeList(() => pigApi.list({ pageNum: 1, pageSize: 1, status: 1 }))
  const pig = page?.[0]
  const earTag = pig?.earTagNo || pig?.ear_tag_no
  if (earTag) {
    demoForm.data.earTagNo = earTag
    extra()
    sampleHint.value = `取到${desc}耳标 ${earTag}`
  } else {
    sampleHint.value = `没有${desc}档案，请先到「养殖管理 → 生猪批次档案」登记批次（耳标区间覆盖该猪）`
  }
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

/**
 * 找一头「还没申报出栏」的在养猪。
 * <p>
 * 称重登记演示必须用它：一头猪同时只能有一条有效申报（待审或已通过），
 * 挑到已申报的猪会被业务侧判「已有申报」而拒绝，演示不出自动登记。
 * 已驳回的猪也不选——驳回是审批人的结论，设备不该让它自动复活。
 */
async function findPigWithoutApply(): Promise<{ id: number; earTagNo: string } | null> {
  // 取全量在养猪：只扫前 30 头时，老演示猪一旦都申报过，就会误报「没有尚未申报的猪」
  const pigs = await fetchAllPigs('1')
  const taken = new Set<number>()
  const applies = await safeList(() => pigApi.getApplies({ pageNum: 1, pageSize: 200 }))
  for (const apply of applies) {
    if (apply?.approvalStatus === 0 || apply?.approvalStatus === 1) taken.add(Number(apply.pigId))
  }
  for (const pig of pigs) {
    const earTagNo = pig?.earTagNo || pig?.ear_tag_no
    if (!earTagNo) continue
    if (taken.has(Number(pig.id))) continue
    return { id: pig.id, earTagNo }
  }
  return null
}

function todayText() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}`
}

/** 当前时刻（yyyy-MM-dd HH:mm:ss）：设备采集时刻用 */
function nowText() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/**
 * 演示用的胴体编号 —— 必须每次都是**新的**。
 * <p>
 * 盖章通道的第一步就是按胴体编号查重（`carcass_stamp.carcass_no` 是唯一键），查到既有记录
 * 会直接把那条返回、回执仍是「已入账」。原先写死成 `CT-当天-001`：第一头白条用完这个号之后，
 * 同一天给第二头白条盖章的上报会被**静默吃掉** —— 看着成功，其实一条新记录都没写
 * （接入流水里两条上报的 `target_id` 指向同一条）。
 * 编号里带上时刻（HHmmss）既保证当天内唯一，也让每次演示都是一份新的采集报文，
 * 不会被「同设备 + 同通道 + 同一份报文」的兜底判重拦下。
 */
function nextCarcassNo() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `CT-${todayText()}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
}

/** n 天前的日期（yyyy-MM-dd）：演示出生日期这类「过去某天」的字段，直接给今天的值不合业务常理 */
function daysAgoText(days: number) {
  const d = new Date()
  d.setDate(d.getDate() - days)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

onMounted(() => { fetchStaging(); fetchDevices(); loadMeatParts() })
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.page-title { font-size: 16px; font-weight: 600; color: #303133; }
.demo-form { margin-top: 4px; }
.demo-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 10px 0 4px; border-top: 1px dashed #ebeef5; }
.demo-result { margin-top: 12px; }
.demo-result-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.demo-result-list { margin: 0 0 8px; padding: 0; list-style: none; }
.demo-result-list li { display: flex; gap: 8px; font-size: 13px; line-height: 1.9; }
.demo-result-list .k { flex: 0 0 84px; color: #909399; }
.demo-result-list .v { color: #303133; }
.demo-result-reason { margin-bottom: 8px; font-size: 13px; line-height: 1.7; color: #e6a23c; }
/* 相机连拍区块：与上报回执并排存在，用左边框区分「这不是回执，是相机接着干的事」 */
.camera-shot { border-left: 3px solid #409eff; padding-left: 10px; }
.demo-raw { border-top: 1px dashed #ebeef5; }
.demo-raw :deep(.el-collapse-item__header) { height: 38px; line-height: 38px; font-size: 12px; color: #909399; }
/* 「上报」按钮外面套了一层 tooltip 触发器，会丢掉 .el-button + .el-button 的默认间距 */
.demo-report-wrap { display: inline-block; margin-left: 12px; }
.approve-summary { margin-bottom: 16px; }
.approve-form { padding-top: 4px; }
.approve-form :deep(.el-upload--picture-card) { width: 100px; height: 100px; }
.approve-form :deep(.el-upload-list--picture-card .el-upload-list__item) { width: 100px; height: 100px; }
.demo-form :deep(.el-upload--picture-card) { width: 100px; height: 100px; }
.demo-form :deep(.el-upload-list--picture-card .el-upload-list__item) { width: 100px; height: 100px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.stats-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 12px 16px; background: #f5f7fa; border-radius: 6px; flex-wrap: wrap; }
.stats-hint { margin-left: auto; font-size: 13px; color: #909399; }
.sub-cell { font-size: 12px; color: #909399; }
.error-text { color: #e6a23c; }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.payload-title { margin: 16px 0 8px; font-size: 13px; color: #606266; }
.payload-box { max-height: 260px; overflow: auto; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 4px; padding: 12px; font-size: 12px; line-height: 1.6; color: #303133; white-space: pre-wrap; word-break: break-all; }

// 分割产出部位清单：一行一个部位（部位编码 + 重量 + 包数）
.items-editor { width: 100%; }
.item-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.item-unit { font-size: 12px; color: #909399; }
.items-actions { display: flex; align-items: center; gap: 12px; }
.item-tip { font-size: 12px; color: #909399; }
</style>
