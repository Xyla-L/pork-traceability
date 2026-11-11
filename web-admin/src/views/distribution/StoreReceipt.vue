<template>
  <div class="page-container">
    <!-- 门店数据边界：零售商两个页签都只含本门店，页面上要说清楚范围是哪家店 -->
    <StoreScopeTag />
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="待签收" name="pending">
        <div class="search-panel">
          <el-form :model="pendingSearch" inline>
            <el-form-item label="运单号">
              <el-input v-model="pendingSearch.keyword" placeholder="请输入" clearable @keyup.enter="fetchPendingList" style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchPendingList"><el-icon><Search /></el-icon>搜索</el-button>
              <el-button @click="handleResetPending"><el-icon><Refresh /></el-icon>重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="canConfirmReceipt" class="pending-toolbar">
          <el-button type="primary" :disabled="!selectedPending.length" @click="openBatchDialog">
            <el-icon><Finished /></el-icon>批量签收{{ selectedPending.length ? `（已选 ${selectedPending.length} 单）` : '' }}
          </el-button>
          <span class="sub-text">
            一次选中多张运单，统一填写核验结果；每张单按它自己「已到达且未签收」的站点签收，单张失败不影响其余
          </span>
        </div>

        <el-table v-loading="pendingLoading" :data="pendingList" border stripe @selection-change="handlePendingSelectionChange">
          <el-table-column v-if="canConfirmReceipt" type="selection" width="46" align="center" :selectable="canBatchSelect" />
          <el-table-column prop="transportNo" label="运单号" width="180" />
          <el-table-column label="本次签收门店" min-width="150">
            <template #default="{ row }">
              <span v-if="row.signableStoreName">{{ row.signableStoreName }}</span>
              <el-tooltip v-else-if="row.signableStopCount > 1"
                content="本车有多站同时待签收，批量签收不替你挑哪一站，请在该行用「确认签收」逐站处理" placement="top">
                <el-tag type="info" size="small" effect="plain">多站待签</el-tag>
              </el-tooltip>
              <span v-else class="sub-text">--</span>
            </template>
          </el-table-column>
          <el-table-column prop="splitBatchId" label="分割批次" width="120" align="center">
            <template #default="{ row }">{{ splitMap[row.splitBatchId]?.batchNo || '--' }}</template>
          </el-table-column>
          <el-table-column prop="vehicleNo" label="配送车辆" width="120" align="center" />
          <el-table-column prop="origin" label="发货地" min-width="120" />
          <el-table-column prop="destination" label="目的地" min-width="120" />
          <!-- 一车多店：一趟车要停好几站，签完一站运单仍是「待签收」 -->
          <el-table-column label="站点" width="110" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.stopCount > 1" type="warning" size="small" effect="plain">
                途经 {{ row.stopCount }} 站
              </el-tag>
              <span v-else class="sub-text">单站</span>
            </template>
          </el-table-column>
          <el-table-column prop="driverName" label="司机" width="100" align="center" />
          <el-table-column prop="arriveTime" label="到店时间" width="170" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag type="primary" size="small">待签收</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right" align="center">
            <template #default="{ row }">
              <el-button v-if="canConfirmReceipt" type="success" link size="small" @click="openConfirmDialog(row)">确认签收</el-button>
              <el-button v-if="canConfirmReceipt" type="danger" link size="small" @click="openRejectDialog(row)">拒收</el-button>
              <span v-if="!canConfirmReceipt" style="color: #c0c4cc; font-size: 12px">无权限</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="pendingPagination.pageNum" v-model:page-size="pendingPagination.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="pendingPagination.total" layout="total, sizes, prev, pager, next, jumper"
            background @size-change="handlePendingSizeChange" @current-change="handlePendingPageChange" />
        </div>
      </el-tab-pane>

      <!-- 拒收/回运：冷链异常货物的正式出口。拒收是站点终态（理由必填、实名、上链），
           这里的每一单都带着「为什么拒」，回运进度与协商改签收都从这里看。 -->
      <el-tab-pane label="拒收/回运" name="rejected">
        <div class="search-panel">
          <el-form inline>
            <el-form-item label="运单号">
              <el-input v-model="rejectedSearch.keyword" placeholder="请输入" clearable @keyup.enter="fetchRejectedList" style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchRejectedList"><el-icon><Search /></el-icon>搜索</el-button>
            </el-form-item>
          </el-form>
        </div>
        <el-table v-loading="rejectedLoading" :data="rejectedList" border stripe>
          <el-table-column prop="transportNo" label="运单号" width="180" />
          <el-table-column prop="vehicleNo" label="配送车辆" width="120" align="center" />
          <el-table-column prop="driverName" label="司机" width="100" align="center" />
          <el-table-column prop="origin" label="发货地" min-width="120" />
          <el-table-column label="拒收站点" width="110" align="center">
            <template #default="{ row }">
              <el-tag type="danger" size="small">{{ row.rejectedStopCount ?? 0 }} 站</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="arriveTime" label="到店时间" width="170" align="center" />
          <el-table-column label="运单状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 4 ? 'info' : 'warning'" size="small">
                {{ { 1: '待发车', 2: '运输中', 3: '待签收', 4: '已完结' }[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="openRejectDetail(row)">详情 / 改签</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="rejectedPagination.pageNum" v-model:page-size="rejectedPagination.pageSize"
            :page-sizes="[10, 20, 50]" :total="rejectedPagination.total" layout="total, sizes, prev, pager, next"
            background @current-change="fetchRejectedList" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="已签收" name="done">
        <div class="search-panel">
          <el-form :model="searchForm" inline>
            <el-form-item label="运单号">
              <el-input v-model="searchForm.transportNo" placeholder="请输入" clearable @keyup.enter="handleSearch" style="width: 160px" />
            </el-form-item>
            <el-form-item v-if="!isRetailer" label="门店名称">
              <el-input v-model="searchForm.storeName" placeholder="请输入" clearable @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="签收时间">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 260px"
              />
            </el-form-item>
            <el-form-item label="签收状态">
              <el-select v-model="searchForm.statusFilter" placeholder="全部" clearable style="width: 120px">
                <el-option label="全部正常" :value="1" />
                <el-option label="状态异常" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>搜索</el-button>
              <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table v-loading="loading" :data="tableData" border stripe>
          <el-table-column prop="receiptNo" label="签收单号" width="180" />
          <el-table-column prop="transportNo" label="关联运单" width="160" />
          <el-table-column prop="storeName" label="签收门店" min-width="150" />
          <el-table-column prop="receiver" label="签收人" width="100" align="center">
            <template #default="{ row }">{{ row.receiver || '未记录' }}</template>
          </el-table-column>
          <el-table-column prop="source" label="采集设备" width="140" align="center">
            <template #default="{ row }">
              <el-tooltip :content="row.source === 'DEVICE' ? `PDA/探头自动采集：${row.sourceRef || '--'}` : sourceLabel(row.source)" placement="top">
                <el-tag :type="sourceTag(row.source)" size="small" effect="plain">{{ deviceText(row) }}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="receiptTime" label="签收时间" width="170" align="center" />
          <el-table-column prop="qtyCheck" label="数量核验" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.qtyCheck === 1 ? 'success' : 'danger'" size="small">{{ row.qtyCheck === 1 ? '正常' : '异常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="tempCheck" label="温度核验" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.tempCheck === 1 ? 'success' : 'danger'" size="small">{{ row.tempCheck === 1 ? '正常' : '异常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="packageIntact" label="包装状态" width="100" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.packageIntact === 1 ? '#67c23a' : '#f56c6c' }">{{ row.packageIntact === 1 ? '完好' : '破损' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="chainStatus" label="存证状态" width="118" align="center">
            <template #default="{ row }">
              <BlockchainVerifyBadge :status="row.chainStatus" :mode="chainMode" :tx-hash="row.chainTxHash || ''" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
              <el-button type="success" link size="small" @click="handleVerify(row)">验真</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
            background @size-change="handleSizeChange" @current-change="handlePageChange" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 签收弹窗 -->
    <el-dialog v-model="dialogVisible" title="门店签收确认" width="620px" destroy-on-close>
      <el-descriptions :column="2" border size="default" style="margin-bottom: 20px">
        <el-descriptions-item label="运单号">{{ receiptData.transportNo }}</el-descriptions-item>
        <el-descriptions-item label="配送车辆">{{ receiptData.vehicleNo }}</el-descriptions-item>
        <el-descriptions-item label="发货地">{{ receiptData.origin }}</el-descriptions-item>
        <el-descriptions-item label="目的地">{{ receiptData.destination }}</el-descriptions-item>
        <el-descriptions-item label="司机">{{ receiptData.driverName }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ receiptData.driverPhone }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="receiptForm" label-width="90px" size="default" style="margin-bottom: 16px">
        <!-- 站点状态：**单站运单也要说清「这一站车到了没」** ——
             签收的前置是「该站已到达」，而到达是站点级的事实（与车厢里装了几批货无关）。
             多站运单下面还会多一个下拉，让操作员指明这次签的是哪一站。 -->
        <div v-if="receiptStops.length" class="form-tip" style="margin-bottom: 14px">
          {{ receiptStopTip }}
        </div>
        <!-- 一车多店：这一车要停好几站，必须指明「这次签的是哪一站」——
             签收按 (运单, 门店) 记账，签完一站运单仍是「待签收」，全部签完才算已签收。 -->
        <el-form-item v-if="needsStopChoice" label="卸货门店">
          <el-select v-model="selectedStopIndex" style="width: 100%" :loading="stopLoading" @change="applyStopToForm">
            <el-option
              v-for="(s, idx) in receiptStops"
              :key="idx"
              :label="`第 ${s.seq != null ? s.seq : idx + 1} 站 · ${s.storeName || '未记录'}（${s.batchCount} 批）${s.received ? ' · 已签收' : (s.rejected ? ' · 已拒收' : (s.arrived ? ' · 等签收' : ' · 车未到'))}`"
              :value="idx"
              :disabled="s.received || !s.arrived || s.rejected" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="门店" prop="storeName">
              <el-input v-model="receiptForm.storeName" :disabled="needsStopChoice" placeholder="请输入门店名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签收人" prop="receiver">
              <el-input v-model="receiptForm.receiver" placeholder="请输入签收人姓名" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="receipt-checks">
        <!-- 数量核验 -->
        <el-card shadow="hover" class="check-card">
          <template #header><span class="check-title">数量核验</span></template>
          <el-form label-width="80px" size="default">
            <el-form-item label="核验结果">
              <el-radio-group v-model="receiptForm.qtyCheck">
                <el-radio :value="1">数量一致</el-radio>
                <el-radio :value="0">数量不符</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="receiptForm.qtyCheck === 0" label="差异说明">
              <el-input v-model="receiptForm.qtyDiffNote" type="textarea" :rows="2" placeholder="请说明差异情况" />
            </el-form-item>
          </el-form>
        </el-card>
        <!-- 温度核验 -->
        <el-card shadow="hover" class="check-card">
          <template #header><span class="check-title">温度核验</span></template>
          <el-form label-width="80px" size="default">
            <el-form-item label="到货温度">
              <el-input-number v-model="receiptForm.tempValue" :min="-50" :max="50" :precision="1" style="width: 160px" />
              <span class="unit-hint" style="margin-left: 6px">℃</span>
            </el-form-item>
            <el-form-item label="核验结果">
              <el-radio-group v-model="receiptForm.tempCheck">
                <el-radio :value="1">正常</el-radio>
                <el-radio :value="0">异常</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
      <!-- 包装检查 -->
      <el-card shadow="hover" class="check-card" style="margin-bottom: 16px">
        <template #header><span class="check-title">包装检查</span></template>
        <el-form label-width="80px" size="default">
          <el-form-item label="包装状态">
            <el-radio-group v-model="receiptForm.packageIntact">
              <el-radio :value="1">完好</el-radio>
              <el-radio :value="0">破损</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </el-card>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReceipt" :loading="submitting">确认签收</el-button>
      </template>
    </el-dialog>

    <!-- 批量签收弹窗：统一填写核验结果，逐单按各自的待签收站点签收 -->
    <el-dialog v-model="batchVisible" title="批量签收" width="820px" destroy-on-close>
      <template v-if="!batchResult">
        <div class="batch-scope">
          <span class="batch-scope-label">本次签收 {{ selectedPending.length }} 张运单：</span>
          <el-tag v-for="t in selectedPending" :key="t.id" size="small" effect="plain" class="batch-tag">
            {{ t.transportNo }}{{ t.stopCount > 1 ? `（${t.stopCount} 站）` : '' }}
          </el-tag>
        </div>
        <el-form :model="batchForm" label-width="90px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="签收人" required>
                <el-input v-model="batchForm.receiver" placeholder="请输入签收人姓名" maxlength="32" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="到货温度">
                <el-input-number v-model="batchForm.tempValue" :min="-50" :max="50" :precision="1" style="width: 150px" />
                <span class="unit-hint" style="margin-left: 6px">℃</span>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="数量核验">
                <el-radio-group v-model="batchForm.qtyCheck">
                  <el-radio :value="1">数量一致</el-radio>
                  <el-radio :value="0">数量不符</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="温度核验">
                <el-radio-group v-model="batchForm.tempCheck">
                  <el-radio :value="1">正常</el-radio>
                  <el-radio :value="0">异常</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="包装状态">
            <el-radio-group v-model="batchForm.packageIntact">
              <el-radio :value="1">完好</el-radio>
              <el-radio :value="0">破损</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="batchForm.qtyCheck === 0" label="差异说明">
            <el-input v-model="batchForm.qtyDiffNote" type="textarea" :rows="2"
              placeholder="请说明差异情况（同一批卸货会写入每一张签收单）" />
          </el-form-item>
        </el-form>
        <div class="batch-hint">
          「多站待签」的运单不进批量签收（本车有多站同时待签收，不替你挑哪一站），
          请在列表里用该行的「确认签收」逐站处理。
        </div>
      </template>
      <template v-else>
        <div class="batch-summary">
          共 {{ batchResult.total }} 单：成功 <b class="ok">{{ batchResult.successCount }}</b> 单，
          <b :class="{ fail: batchResult.failedCount > 0 }">{{ batchResult.failedCount }}</b> 单未签收
        </div>
        <el-table :data="batchResult.items" border size="small" max-height="360">
          <el-table-column prop="transportNo" label="运单号" width="170" />
          <el-table-column label="签收门店" min-width="150">
            <template #default="{ row }">{{ row.storeName || '--' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'warning'" size="small">
                {{ row.success ? '已签收' : '未签收' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </template>
      <template #footer>
        <template v-if="!batchResult">
          <el-button @click="batchVisible = false">取消</el-button>
          <el-button type="primary" :loading="batchSubmitting" @click="submitBatch">确认签收</el-button>
        </template>
        <el-button v-else type="primary" @click="batchVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 拒收弹窗：理由必填 —— 拒收推翻的是「这批货交给这家店」，理由随事件上链留档 -->
    <el-dialog v-model="rejectVisible" title="门店拒收" width="520px" destroy-on-close>
      <el-descriptions :column="2" border size="default" style="margin-bottom: 16px">
        <el-descriptions-item label="运单号">{{ rejectTarget.transportNo }}</el-descriptions-item>
        <el-descriptions-item label="配送车辆">{{ rejectTarget.vehicleNo }}</el-descriptions-item>
        <el-descriptions-item label="发货地">{{ rejectTarget.origin }}</el-descriptions-item>
        <el-descriptions-item label="目的地">{{ rejectTarget.destination }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="拒收理由" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="如：到货温度 -2℃ 超出冷链要求 / 包装破损化冻 / 数量与运单不符" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="submitReject">确认拒收</el-button>
      </template>
    </el-dialog>

    <!-- 拒收详情弹窗：每一站先看「为什么拒」，看回运状态或协商改签 -->
    <el-dialog v-model="rejectDetailVisible" title="拒收站点与处置" width="760px" destroy-on-close>
      <div v-if="!rejectedStops.length" class="sub-text">本运单没有已拒收的站点。</div>
      <div v-for="(s, idx) in rejectedStops" :key="idx" class="reject-stop-card">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="站点">第 {{ s.stopNo }} 站 · {{ s.storeName }}</el-descriptions-item>
          <el-descriptions-item label="批次">{{ (s.batchNos || []).join('、') || s.batchCount + ' 批' }}</el-descriptions-item>
          <el-descriptions-item label="拒收理由" :span="2">{{ s.rejectReason || '--' }}</el-descriptions-item>
          <el-descriptions-item label="拒收人">{{ s.rejectBy || '--' }}（{{ formatDateTime(s.rejectTime) }}）</el-descriptions-item>
          <el-descriptions-item label="处置状态">
            <el-tag v-if="s.disposalType" type="info" size="small">
              已处置：{{ disposalLabel(s.disposalType) }} · {{ s.disposalBy }}
            </el-tag>
            <el-tag v-else type="warning" size="small">待回运</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <template v-if="!s.disposalType">
          <div class="disposal-block">
            <div class="disposal-title">货物去向（已统一为回运，无需登记）</div>
            <div class="sub-text" style="margin-top: 4px">
              这批拒收货默认已回冷链中心，将由冷链中心统一回运至无害化处理中心 —— 门店无需任何操作，
              可在追溯链上查看回运进度。
            </div>
          </div>
          <div class="disposal-block">
            <div class="disposal-title">协商成功？按正常签收录入（站点将标记「曾拒收后改签」）</div>
            <el-input v-model="convertForm.receiver" placeholder="签收人姓名" maxlength="32" style="width: 200px" />
            <el-button type="success" size="small" style="margin-left: 10px" :loading="convertSubmitting"
              @click="submitConvert(s)">拒收改签收</el-button>
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- 签收详情弹窗 -->
    <el-dialog v-model="detailVisible" title="门店签收详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="门店">{{ detailData.storeName }}</el-descriptions-item>
        <el-descriptions-item label="签收人">{{ detailData.receiver || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="采集设备">{{ deviceText(detailData) }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailData.receiverPhone || '--' }}</el-descriptions-item>
        <el-descriptions-item label="签收时间">{{ detailData.receiptTime }}</el-descriptions-item>
        <el-descriptions-item label="到货温度(℃)">{{ detailData.tempValue ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="温度核验">{{ detailData.tempCheck === 1 ? '正常' : detailData.tempCheck === 0 ? '异常' : '--' }}</el-descriptions-item>
        <el-descriptions-item label="数量核验">{{ detailData.qtyCheck === 1 ? '一致' : detailData.qtyCheck === 0 ? '不符' : '--' }}</el-descriptions-item>
        <el-descriptions-item label="包装完好">{{ detailData.packageIntact === 1 ? '完好' : detailData.packageIntact === 0 ? '破损' : '--' }}</el-descriptions-item>
        <el-descriptions-item label="内容哈希" :span="2">
          <span v-if="detailData.contentHash" class="hash-text">{{ detailData.contentHash }}</span>
          <span v-else>--</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh, Finished } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BlockchainVerifyBadge from '@/components/common/BlockchainVerifyBadge.vue'
import StoreScopeTag from '@/components/common/StoreScopeTag.vue'
import { distributionApi } from '@/api/modules/distribution'
import { blockchainApi } from '@/api/modules/blockchain'
import { chainMode, loadChainMode } from '@/utils/chainMode'
import { deviceText, sourceLabel, sourceTag } from '@/api/modules/ingest'
import { useAuthStore } from '@/stores/auth'
import request from '@/utils/request'
import { useKeywordQuery } from '@/composables/useKeywordQuery'

const authStore = useAuthStore()
const canConfirmReceipt = computed(() => authStore.hasAnyRole(['RETAILER', 'ADMIN']))
const isRetailer = computed(() => authStore.role === 'RETAILER')

const activeTab = ref<'pending' | 'done' | 'rejected'>('pending')

function handleTabChange() {
  if (activeTab.value === 'pending') {
    fetchPendingList()
  } else if (activeTab.value === 'rejected') {
    fetchRejectedList()
  } else {
    fetchList()
  }
}

// ========== 待签收列表 ==========
const pendingSearch = reactive({ keyword: '' })
const pendingList = ref<any[]>([])
const pendingLoading = ref(false)
const pendingPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const splitMap = reactive<Record<number, any>>({})

async function fetchPendingList() {
  pendingLoading.value = true
  try {
    const res: any = await distributionApi.getTransports({
      status: 3,
      keyword: pendingSearch.keyword || undefined,
      pageNum: pendingPagination.pageNum,
      pageSize: pendingPagination.pageSize,
    })
    pendingList.value = res?.records || []
    pendingPagination.total = res?.total || 0
    const missing = [...new Set(pendingList.value.map((t: any) => t.splitBatchId).filter((id: number) => id && !splitMap[id]))]
    await Promise.all(missing.map((id: number) => distributionApi.getSplitDetail(id)
      .then((s: any) => { splitMap[id] = s }).catch(() => {})))
  } catch {
    pendingList.value = []
    pendingPagination.total = 0
  } finally {
    pendingLoading.value = false
  }
}

function handleResetPending() {
  pendingSearch.keyword = ''
  pendingPagination.pageNum = 1
  fetchPendingList()
}
function handlePendingSizeChange() { pendingPagination.pageNum = 1; fetchPendingList() }
function handlePendingPageChange() { fetchPendingList() }

// ========== 批量签收 ==========
const selectedPending = ref<any[]>([])
function handlePendingSelectionChange(rows: any[]) { selectedPending.value = rows }

/** 「多站待签」不参与批量：本车有多站同时待签收，批量不替人挑哪一站 */
function canBatchSelect(row: any) {
  return row?.signableStopCount === 1
}

const batchVisible = ref(false)
const batchSubmitting = ref(false)
const batchResult = ref<any>(null)
const batchForm = reactive({
  receiver: '',
  qtyCheck: 1,
  qtyDiffNote: '',
  tempCheck: 1,
  tempValue: -12,
  packageIntact: 1,
})

function openBatchDialog() {
  if (!selectedPending.value.length) {
    ElMessage.warning('请先勾选要签收的运单')
    return
  }
  batchResult.value = null
  batchForm.receiver = authStore.user?.realName || ''
  batchForm.qtyCheck = 1
  batchForm.qtyDiffNote = ''
  batchForm.tempCheck = 1
  batchForm.tempValue = -12
  batchForm.packageIntact = 1
  batchVisible.value = true
}

async function submitBatch() {
  if (!batchForm.receiver.trim()) {
    ElMessage.warning('请输入签收人姓名')
    return
  }
  batchSubmitting.value = true
  try {
    const res: any = await distributionApi.batchReceipts({
      transportIds: selectedPending.value.map((r: any) => r.id),
      receiver: batchForm.receiver,
      qtyCheck: batchForm.qtyCheck,
      qtyDiffNote: batchForm.qtyDiffNote,
      tempCheck: batchForm.tempCheck,
      tempValue: batchForm.tempValue,
      packageIntact: batchForm.packageIntact,
    })
    batchResult.value = res
    if (res?.successCount) ElMessage.success(`已签收 ${res.successCount} 单`)
    if (res?.failedCount) ElMessage.warning(`${res.failedCount} 单未签收，请查看下方说明`)
    // 签完的运单会从「待签收」里消失（零售商的本店站点已签），两个列表都刷
    fetchList()
    fetchPendingList()
  } catch { /* 错误已由拦截器提示 */ } finally {
    batchSubmitting.value = false
  }
}

// ========== 门店拒收：冷链异常货物的正式出口 ==========
// 拒收是站点终态：理由必填、实名、上链；回运进度与改签都从「拒收/回运」页签看。

const rejectVisible = ref(false)
const rejectSubmitting = ref(false)
const rejectTarget = reactive({ id: null as number | null, transportNo: '', vehicleNo: '', origin: '', destination: '' })
const rejectForm = reactive({ reason: '' })

function openRejectDialog(row: any) {
  Object.assign(rejectTarget, { id: row.id, transportNo: row.transportNo, vehicleNo: row.vehicleNo, origin: row.origin, destination: row.destination })
  rejectForm.reason = ''
  rejectVisible.value = true
}

async function submitReject() {
  if (!rejectForm.reason.trim() || rejectForm.reason.trim().length < 2) {
    ElMessage.warning('请填写拒收理由（不少于 2 个字），理由将随事件上链留档')
    return
  }
  rejectSubmitting.value = true
  try {
    await distributionApi.rejectStop(rejectTarget.id!, { reason: rejectForm.reason.trim() })
    ElMessage.success('已拒收并上链：车照常放行，货物请在「拒收/回运」页签处理')
    rejectVisible.value = false
    fetchPendingList()
    fetchRejectedList()
  } catch { /* 错误已由拦截器提示 */ } finally {
    rejectSubmitting.value = false
  }
}

// 「拒收/处置」页签：列出有本店拒收站点的运单
const rejectedSearch = reactive({ keyword: '' })
const rejectedList = ref<any[]>([])
const rejectedLoading = ref(false)
const rejectedPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function fetchRejectedList() {
  rejectedLoading.value = true
  try {
    const res: any = await distributionApi.getTransports({
      rejected: true,
      keyword: rejectedSearch.keyword || undefined,
      pageNum: rejectedPagination.pageNum,
      pageSize: rejectedPagination.pageSize,
    })
    rejectedList.value = (res?.records || []).filter((t: any) => (t.rejectedStopCount ?? 0) > 0)
    rejectedPagination.total = res?.total || 0
  } catch {
    rejectedList.value = []
    rejectedPagination.total = 0
  } finally {
    rejectedLoading.value = false
  }
}

// 拒收详情弹窗
const rejectDetailVisible = ref(false)
const rejectedStops = ref<any[]>([])
const rejectDetailTransportId = ref<number | null>(null)
const convertForm = reactive({ receiver: '' })
const convertSubmitting = ref(false)

const DISPOSAL_LABELS: Record<string, string> = {
  RETURN_SUPPLIER: '退回供应商', DESTROY: '就地销毁', DISCOUNT: '折价接收',
}
function disposalLabel(type: string) { return DISPOSAL_LABELS[type] || type }
function formatDateTime(v: any) { return v ? String(v).replace('T', ' ').slice(0, 19) : '--' }

async function openRejectDetail(row: any) {
  rejectDetailTransportId.value = row.id
  rejectedStops.value = []
  convertForm.receiver = authStore.user?.realName || ''
  rejectDetailVisible.value = true
  try {
    const list: any = await distributionApi.getTransportStops(row.id)
    rejectedStops.value = (Array.isArray(list) ? list : []).filter((s: any) => s.rejected)
  } catch {
    rejectedStops.value = []
  }
}

async function submitConvert(stop: any) {
  if (!rejectDetailTransportId.value) return
  if (!convertForm.receiver.trim()) {
    ElMessage.warning('请输入签收人姓名')
    return
  }
  convertSubmitting.value = true
  try {
    await distributionApi.convertRejectToReceipt(rejectDetailTransportId.value, {
      transportId: rejectDetailTransportId.value,
      storeId: stop.storeId,
      storeName: stop.storeName,
      receiver: convertForm.receiver.trim(),
      qtyCheck: 1,
      tempCheck: 1,
      packageIntact: 1,
    })
    ElMessage.success('已改签收并上链：本站按正常签收计入')
    const list: any = await distributionApi.getTransportStops(rejectDetailTransportId.value)
    rejectedStops.value = (Array.isArray(list) ? list : []).filter((s: any) => s.rejected)
    fetchRejectedList()
    fetchList()
  } catch { /* 错误已由拦截器提示 */ } finally {
    convertSubmitting.value = false
  }
}

// ========== 已签收列表 ==========
const searchForm = reactive({ transportNo: '', storeName: '', statusFilter: null as number | null })
const dateRange = ref<[string, string] | null>(null)
const tableData = ref<any[]>([])
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const detailVisible = ref(false)
const detailData = ref<any>({})

function handleView(row: any) {
  detailData.value = row
  detailVisible.value = true
}
/**
 * 给当前页的签收单补真实存证状态：状态来自存证表，不是「有没有哈希」的推测。
 * 取不到就显示「未存证」，宁可说得保守，也不能给门店一个假通过。
 */
async function attachChainStatus(list: any[]) {
  const ids = list.map((item) => item?.id).filter((id) => typeof id === 'number')
  let statusMap: Record<string, number> = {}
  if (ids.length) {
    try {
      statusMap = ((await blockchainApi.getStatusMap('STORE_RECEIPT', ids)) || {}) as Record<string, number>
    } catch {
      statusMap = {}
    }
  }
  for (const item of list) {
    const raw = statusMap[String(item.id)]
    item.chainStatus = raw === 1 ? 'confirmed' : raw === 2 ? 'failed' : raw === 0 ? 'pending' : 'none'
  }
}

/** 验真：真去比对存证哈希。本地存证模式只能说「平台存证一致」，不能说「区块链验真通过」 */
async function handleVerify(row: any) {
  if (!row?.id) return
  try {
    const res: any = await blockchainApi.verifyIntegrity('STORE_RECEIPT', row.id)
    const onChain = res?.onChain === true
    const matched = res?.matched === true
    if (!matched) {
      ElMessage.error(onChain
        ? '核对不一致：该签收单的内容哈希与链上存证不符，请联系管理员'
        : '核对不一致：该签收单与平台存证记录不符，请联系管理员')
      return
    }
    ElMessage.success(onChain
      ? `链上核验通过：${row.receiptNo} 与链上存证一致`
      : `平台存证核对一致：${row.receiptNo}（当前为本地存证，未写入区块链）`)
  } catch {
    ElMessage.error('该签收单暂无存证记录，无法核对')
  }
}

function handleSearch() { pagination.pageNum = 1; fetchList() }
function handleReset() { Object.assign(searchForm, { transportNo: '', storeName: '', statusFilter: null }); dateRange.value = null; handleSearch() }
function handleSizeChange() { pagination.pageNum = 1; fetchList() }
function handlePageChange() { fetchList() }

function hasAbnormal(item: any): boolean {
  return item.qtyCheck !== 1 || item.tempCheck !== 1 || item.packageIntact !== 1
    || item.chainStatus === 'none' || item.chainStatus === 'failed'
}
function isAllNormal(item: any): boolean {
  return item.qtyCheck === 1 && item.tempCheck === 1 && item.packageIntact === 1
    && item.chainStatus !== 'none' && item.chainStatus !== 'failed'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await distributionApi.getReceipts({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      storeName: searchForm.storeName || undefined,
      transportNo: searchForm.transportNo || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
    })
    let list = res?.records || res?.list || []
    list = (Array.isArray(list) ? list : []).map((item: any) => ({
      ...item,
      receiptNo: item.receiptNo || `S${item.id || ''}`,
      transportNo: item.transportNo || item.transportId || '',
    }))
    // 存证状态必须问存证服务：contentHash 只说明签收时算过哈希，
    // 哈希写没写进账本、成没成功，用哈希存在与否是看不出来的
    await attachChainStatus(list)
    if (searchForm.statusFilter === 1) {
      list = list.filter(isAllNormal)
    } else if (searchForm.statusFilter === 0) {
      list = list.filter(hasAbnormal)
    }
    if (searchForm.transportNo) {
      list = list.filter((item: any) => item.transportNo?.includes(searchForm.transportNo))
    }
    tableData.value = list
    pagination.total = list.length
  } catch (error) {
    console.error('获取门店签收列表失败:', error)
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

// ========== 签收弹窗 ==========
const dialogVisible = ref(false)
const submitting = ref(false)
const receiptData = reactive({ transportNo: '', vehicleNo: '', origin: '', destination: '', driverName: '', driverPhone: '' })
const receiptForm = reactive({
  transportId: null as number | null,
  storeId: null as number | null,
  storeName: '',
  receiver: '',
  receiverPhone: '',
  qtyCheck: 1,
  qtyDiffNote: '',
  tempCheck: 1,
  tempValue: -12,
  packageIntact: 1,
  receiptPhoto: [] as string[],
})

// ========== 途经站点（一车多店） ==========
/**
 * 签收按 (运单, 门店) 记账：一趟车装多家门店的货，就要一站一签。
 * 站点清单来自运单装载明细（按卸货门店聚合），站点顺序 = 装车扫码顺序。
 */
const receiptStops = ref<any[]>([])
const stopLoading = ref(false)
/** 选中的站点下标（-1 = 未选） */
const selectedStopIndex = ref(-1)
/** 多站时才需要签收人挑一站：单站运单直接按目的地门店签 */
const needsStopChoice = computed(() => receiptStops.value.length > 1)
const receivedStopCount = computed(() => receiptStops.value.filter((s: any) => s.received).length)
/** 已到达站点数：到达与签收分开 —— 车到了不等于货交出去了 */
const arrivedStopCount = computed(() => receiptStops.value.filter((s: any) => s.arrived).length)
const selectedStop = computed(() => (selectedStopIndex.value >= 0 ? receiptStops.value[selectedStopIndex.value] : null))

/**
 * 站点状态提示：**单站运单也显示**。
 * <p>
 * 到达是站点级的事实（车在不在这个卸货点），签收的前置就是「该站已到达」。
 * 单站运单如果什么都不显示，使用者就完全看不出「到达是按门店逐站记的」——
 * 而且单站时下拉被隐藏，这句提示是页面上唯一的落点。
 */
const receiptStopTip = computed(() => {
  const list = receiptStops.value
  if (!list.length) return ''
  const n = list.length
  const arrived = arrivedStopCount.value
  const received = receivedStopCount.value
  const head = n > 1
    ? `本车途经 ${n} 站`
    : `本单 1 站（${list[0].storeName || '未记录门店'}）`
  if (received >= n) {
    return `${head}：${n} 站已全部签收，运单已转为「已签收」`
  }
  if (arrived >= n) {
    return n > 1
      ? `${head}：已到达 ${arrived} 站、已签收 ${received} 站，可签收；全部签完运单才转为「已签收」`
      : `${head}：车已到达，待签收 —— 签收是货权转移的凭证，到达只是车到了`
  }
  return `${head}：已到达 ${arrived}/${n} 站。签收的前置是「该站已到达」：车没到的站点不能签收（避免给还没卸货的门店签了）`
})

/** 把选中站点回填到表单：门店 id 是权威（重名门店靠 id 区分），门店名仅供展示 */
function applyStopToForm() {
  const stop = selectedStop.value
  if (!stop) return
  receiptForm.storeId = stop.storeId ?? null
  receiptForm.storeName = stop.storeName || ''
}

async function loadReceiptStops(transportId: number) {
  stopLoading.value = true
  try {
    const list: any = await distributionApi.getTransportStops(transportId)
    receiptStops.value = Array.isArray(list) ? list : []
  } catch {
    receiptStops.value = []
  } finally {
    stopLoading.value = false
  }
  // 默认落在第一个「已到达且待签收」的站点 —— 这是当下真能签的那一站。
  // 若一站都还没到（车在路上），退回第一个待签收站点：让操作员看得见全貌，
  // 提交时服务端会以「第 N 站的车还没到」拦下，比页面上直接空着更说得清。
  let idx = receiptStops.value.findIndex((s: any) => s.arrived && !s.received && !s.rejected)
  if (idx < 0) idx = receiptStops.value.findIndex((s: any) => !s.received && !s.rejected)
  selectedStopIndex.value = idx >= 0 ? idx : (receiptStops.value.length ? 0 : -1)
  applyStopToForm()
}

async function openConfirmDialog(row: any) {
  receiptData.transportNo = row.transportNo
  receiptData.vehicleNo = row.vehicleNo
  receiptData.origin = row.origin
  receiptData.destination = row.destination
  receiptData.driverName = row.driverName
  receiptData.driverPhone = row.driverPhone
  receiptForm.transportId = row.id
  receiptForm.storeId = null
  receiptForm.storeName = ''
  receiptForm.receiver = ''
  receiptForm.qtyCheck = 1
  receiptForm.qtyDiffNote = ''
  receiptForm.tempCheck = 1
  receiptForm.tempValue = -12
  receiptForm.packageIntact = 1
  receiptStops.value = []
  selectedStopIndex.value = -1
  dialogVisible.value = true
  await loadReceiptStops(row.id)
  // 兜底：老运单没有装载明细、站点取不到时，退回按运单目的地签（多站运单的
  // destination 已被写成「途经 N 站」，不是门店名，不能拿来当门店填进去）
  if (!receiptStops.value.length && !(row.stopCount > 1)) {
    receiptForm.storeName = row.destination || ''
  }
}

async function confirmReceipt() {
  if (!receiptForm.transportId) {
    ElMessage.warning('运输单信息缺失')
    return
  }
  if (needsStopChoice.value && !selectedStop.value) {
    ElMessage.warning('请选择本次签收的卸货门店（本车途经多个站点）')
    return
  }
  if (!receiptForm.storeName.trim()) {
    ElMessage.warning('请输入门店名称')
    return
  }
  if (!receiptForm.receiver.trim()) {
    ElMessage.warning('请输入签收人姓名')
    return
  }
  submitting.value = true
  try {
    await distributionApi.createReceipt({
      transportId: receiptForm.transportId,
      storeId: receiptForm.storeId,
      storeName: receiptForm.storeName,
      receiver: receiptForm.receiver,
      receiverPhone: receiptForm.receiverPhone,
      qtyCheck: receiptForm.qtyCheck,
      qtyDiffNote: receiptForm.qtyDiffNote,
      tempCheck: receiptForm.tempCheck,
      tempValue: receiptForm.tempValue,
      packageIntact: receiptForm.packageIntact,
      receiptPhoto: receiptForm.receiptPhoto,
    })
    ElMessage.success('门店签收确认成功，数据已上链')
    dialogVisible.value = false
    // 签完一站运单可能仍是「待签收」（还有别的站点没签），所以两个列表都刷
    fetchList()
    fetchPendingList()
  } catch (error) {
    console.error('签收失败:', error)
  } finally {
    submitting.value = false
  }
}

// 顶部通知带运单号过来：签收类通知说的是「已经签完的那张单」，
// 所以切到「已签收」页签再按运单号查（留在待签收页会查不到）。
useKeywordQuery((kw) => {
  activeTab.value = 'done'
  searchForm.transportNo = kw
  handleSearch()
})

onMounted(() => {
  // 存证模式决定徽章措辞：本地账本不能显示成「区块链已验证」
  loadChainMode()
  fetchPendingList()
})
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #fff; border-radius: 4px; }
.search-panel { padding-bottom: 16px; border-bottom: 1px solid #ebeef5; margin-bottom: 16px;
  :deep(.el-form-item) { margin-bottom: 12px; } }
.pagination-wrapper { display: flex; justify-content: center; padding-top: 16px; margin-top: 16px; border-top: 1px solid #ebeef5; }
.receipt-checks { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.check-card { border: 1px solid #ebeef5; }
.check-title { font-size: 14px; font-weight: 600; }
.unit-hint { font-size: 13px; color: #909399; }
.sub-text { font-size: 12px; color: #909399; }
.form-tip { font-size: 12px; color: #909399; line-height: 1.5; margin-top: 4px; }
.hash-text { font-family: 'Courier New', monospace; font-size: 12px; color: #909399; word-break: break-all; }
.pending-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.batch-scope { margin-bottom: 16px; padding: 10px 12px; background: #f5f7fa; border-radius: 4px; line-height: 2; }
.batch-scope-label { font-size: 13px; color: #606266; margin-right: 4px; }
.batch-tag { margin-right: 6px; }
.batch-hint { margin-top: 4px; font-size: 12px; line-height: 1.6; color: #909399; }
.batch-summary { margin-bottom: 12px; font-size: 13px; color: #606266;
  b { margin: 0 2px; } b.ok { color: #67c23a; } b.fail { color: #e6a23c; } }
.reject-stop-card { border: 1px solid #ebeef5; border-radius: 4px; padding: 12px; margin-bottom: 14px; }
.disposal-block { margin-top: 12px; padding: 10px 12px; background: #f5f7fa; border-radius: 4px;
  & + .disposal-block { margin-top: 8px; } }
.disposal-title { font-size: 12px; color: #606266; margin-bottom: 8px; }
</style>
