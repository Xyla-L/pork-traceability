<template>
  <div class="page-container">
    <!-- ========== 发货计划：运单的上游决策单 ==========
         先把「发哪批货、去哪家店、什么时候走」定下来，运单从这里一键生成，
         目的地与计划时间自动带出，不用在运单弹窗里反复手填。 -->
    <el-card class="plan-card">
      <div class="list-header">
        <span class="card-title">发货计划</span>
        <span class="card-sub">定好去向与车次，运单一键生成</span>
        <div class="toolbar">
          <el-input v-model="planQuery.keyword" placeholder="计划号/订单号/门店/车牌" clearable style="width: 210px"
            :prefix-icon="Search" @keyup.enter="handlePlanSearch" @clear="handlePlanSearch" />
          <el-select v-model="planQuery.status" placeholder="全部状态" clearable style="width: 120px" @change="handlePlanSearch">
            <el-option v-for="s in planStatusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button link :icon="planPanelVisible ? ArrowUp : ArrowDown" @click="planPanelVisible = !planPanelVisible">
            {{ planPanelVisible ? '收起' : '展开' }}
          </el-button>
        </div>
      </div>

      <el-table v-show="planPanelVisible" :data="planList" v-loading="planLoading" border stripe size="small"
        style="margin-top: 12px">
        <el-table-column prop="planNo" label="计划号" width="150" />
        <!-- 来源：内部调度（补货/调拨/铺货，无订单）vs 订单转入（外部系统选购推来的）。
             订单转入的要能一眼看到来源订单号——「这张计划是哪张订单来的」是可追溯的关键一问。 -->
        <el-table-column label="来源" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag :type="row.planSource === 'ORDER' ? 'warning' : 'info'" size="small"
              :effect="row.planSource === 'ORDER' ? 'light' : 'plain'">
              {{ planSourceLabel(row.planSource) }}
            </el-tag>
            <div v-if="row.orderNo" class="sub-text">订单 {{ row.orderNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="planDate" label="计划发货日" width="110" align="center" />
        <el-table-column prop="storeName" label="目的地" min-width="110" show-overflow-tooltip />
        <el-table-column label="货物" width="130">
          <template #default="{ row }">
            <div>{{ row.itemCount }} 个批次</div>
            <div class="sub-text">{{ row.totalWeightKg != null ? row.totalWeightKg + ' kg' : '--' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="车 / 司机" width="140">
          <template #default="{ row }">
            <div>{{ row.vehicleNo || '待安排' }}</div>
            <div class="sub-text">{{ row.driverName || '待安排' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="计划发车" width="155" align="center">
          <template #default="{ row }">{{ row.plannedDepart || '--' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="planStatusTagType(row.status)" size="small">{{ planStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="运单" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.transportNo" link type="primary" size="small" @click="focusTransport(row)">
              {{ row.transportNo }}
            </el-button>
            <span v-else class="sub-text">--</span>
            <!-- 分车后一张计划对应多张运单，主表只留「最近一张」，这里补一句一共发了几车 -->
            <div v-if="row.transportCount > 1" class="sub-text">共 {{ row.transportCount }} 车</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 1 || row.status === 4">
              <el-button link type="success" size="small" @click="openGenerate(row)">生成运单</el-button>
              <el-button link type="primary" size="small" @click="openPlanEdit(row)">编辑</el-button>
              <el-popconfirm title="确认取消该计划？取消后未发运的批次会释放" width="250" @confirm="handleCancelPlan(row)">
                <template #reference>
                  <el-button link type="danger" size="small">取消</el-button>
                </template>
              </el-popconfirm>
            </template>
            <el-button v-else link type="info" size="small" @click="openPlanDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-show="planPanelVisible" class="pager">
        <el-pagination background layout="total, prev, pager, next"
          :total="planTotal" :current-page="planQuery.pageNum" :page-size="planQuery.pageSize"
          @current-change="onPlanPageChange" />
      </div>
    </el-card>

    <!-- ========== 运单列表 ========== -->
    <el-card class="transport-list-card">
      <div class="list-header">
        <span class="card-title">冷链运单</span>
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="运单号/车牌/司机" clearable style="width: 200px"
            :prefix-icon="Search" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="handleSearch">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table :data="transportList" v-loading="listLoading" border stripe size="small"
        highlight-current-row @row-click="selectTransport"
        :row-class-name="rowClassName" style="margin-top: 12px">
        <el-table-column prop="transportNo" label="运单号" width="170" />
        <el-table-column label="分割批次" min-width="180">
          <template #default="{ row }">
            <template v-if="splitMap[row.splitBatchId]">
              <div>{{ splitMap[row.splitBatchId].productName || '--' }}</div>
              <div class="sub-text">{{ splitMap[row.splitBatchId].batchNo }}</div>
            </template>
            <span v-else class="sub-text">#{{ row.splitBatchId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="vehicleNo" label="车牌" width="100" />
        <el-table-column label="司机" width="130">
          <template #default="{ row }">
            <div>
              {{ row.driverName || '未记录' }}
              <!-- 「人名非空」不等于「本人确认过」：刷卡/人脸才有这个标记，人工录入与车牌推定都没有 -->
              <el-tag v-if="row.driverConfirmed === 1" type="success" size="small" effect="plain" style="margin-left: 4px">已确认</el-tag>
              <!-- 装车口没有读卡器：司机常常要等上车刷卡那一刻才有值，这里如实说明，别让人以为数据丢了 -->
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
        <!-- 站点进度：到达是**按卸货门店**逐站记的，单站运单也必须看得见进度 ——
             否则名单上每张单都写「单站」，使用者完全看不出「到达是逐站记的」。 -->
        <el-table-column label="站点进度" width="116" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.stopCount > 1" type="warning" size="small" effect="plain">
              {{ stopProgressLabel(row) }}
            </el-tag>
            <span v-else class="sub-text">{{ stopProgressLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="plannedDepart" label="计划发车" width="160" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="selectTransport(row)">监控详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next, jumper"
          :total="total" :current-page="query.pageNum" :page-size="query.pageSize"
          @current-change="onPageChange" />
      </div>
    </el-card>

    <!-- ========== 运输监控 ========== -->
    <template v-if="currentTransport">
      <div ref="monitorRef" class="monitor-anchor"></div>
      <!-- 运输基本信息条 -->
      <div class="transport-info-bar">
        <div class="info-item"><span class="info-label">运单号</span><span class="info-value">{{ currentTransport.transportNo }}</span></div>
        <div class="info-item"><span class="info-label">车牌号</span><span class="info-value">{{ currentTransport.vehicleNo || '--' }}</span></div>
        <div class="info-item">
          <span class="info-label">司机</span>
          <span class="info-value">
            {{ driverText(currentTransport) }}
            <span v-if="driverSourceLabel(currentTransport.driverSource)" class="sub-text">
              （{{ driverSourceLabel(currentTransport.driverSource) }}）
            </span>
          </span>
        </div>
        <div class="info-item"><span class="info-label">电话</span><span class="info-value">{{ currentTransport.driverPhone || '--' }}</span></div>
        <!-- 站点摘要：单站运单也在这里说清「哪一站、到了没」——这是「到达按卸货门店逐站记」
             在页面上唯一恒定的落点（多站时还会多出一张「途经站点」卡片）。 -->
        <div class="info-item">
          <span class="info-label">途经站点</span>
          <span class="info-value">{{ stopSummaryLabel }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">{{ currentTransport.departTime ? '实际发车' : '计划发车' }}</span>
          <span class="info-value">{{ currentTransport.departTime || currentTransport.plannedDepart || '--' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">{{ currentTransport.arriveTime ? '实际到达' : '预计到达' }}</span>
          <span class="info-value">{{ currentTransport.arriveTime || currentTransport.plannedArrive || '--' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">状态</span>
          <el-tag :type="statusTagType(currentTransport.status)" size="small">{{ statusLabel(currentTransport.status) }}</el-tag>
        </div>
        <div class="info-actions">
          <el-popconfirm
            v-if="currentTransport.status === 1"
            :title="currentTransport.driverName
              ? '确认该运单立即发车？'
              : '该运单还没有司机：货要走了，责任必须落到具体的人。请先编辑运单补录司机，或由司机在车载终端刷卡发车'"
            @confirm="handleDepart(currentTransport)">
            <template #reference>
              <el-button type="warning" size="small" plain>立即发车</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm
            v-else-if="currentTransport.status === 2"
            :title="nextStop
              ? (transportStops.length > 1
                ? `确认已到达第 ${nextStop.stopNo} 站（${nextStop.storeName || '未记录门店'}）？本趟共 ${transportStops.length} 站，逐站确认`
                : '确认该运单已送达？')
              : '本趟车的所有站点都已到达'"
            @confirm="handleArrive(currentTransport)"
          >
            <template #reference>
              <el-button type="success" size="small" plain>
                {{ nextStop && transportStops.length > 1 ? `确认到达第 ${nextStop.stopNo} 站` : '确认到达' }}
              </el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>

      <!-- 温控面板 -->
      <div class="monitor-grid">
        <!-- 温度曲线图 -->
        <el-card class="monitor-card temp-chart-card">
          <template #header>
            <div class="card-header">
              <span>温度变化曲线</span>
              <el-space>
                <el-tag size="small" type="danger">上限 0°C</el-tag>
                <el-tag size="small" type="info">下限 -18°C</el-tag>
              </el-space>
            </div>
          </template>
          <div ref="tempChartRef" class="chart-container"></div>
        </el-card>

        <!-- 当前温度 & 快速打卡 -->
        <div class="monitor-sidebar">
          <!-- 当前温度 -->
          <el-card class="current-temp-card" :class="{ 'temp-abnormal': currentTemp !== null && (currentTemp > 0 || currentTemp < -18) }">
            <div class="current-temp">
              <template v-if="currentTemp !== null">
                <span class="temp-value">{{ currentTemp.toFixed(1) }}</span>
                <span class="temp-unit">°C</span>
              </template>
              <span v-else class="temp-value temp-empty">--</span>
            </div>
            <div class="temp-status">
              <template v-if="currentTemp === null">
                <el-icon color="#909399" :size="24"><InfoFilled /></el-icon>
                <span style="color:#909399">暂无打卡</span>
              </template>
              <template v-else>
                <el-icon v-if="isTempNormal" color="#67c23a" :size="24"><CircleCheckFilled /></el-icon>
                <el-icon v-else color="#f56c6c" :size="24"><WarningFilled /></el-icon>
                <span :style="{ color: isTempNormal ? '#67c23a' : '#f56c6c' }">{{ isTempNormal ? '温度正常' : '温度异常' }}</span>
              </template>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 温度打卡记录列表 -->
      <el-card class="checkin-list-card">
        <template #header>
          <div class="card-header">
            <span>温度打卡记录</span>
            <el-space>
              <!-- 断档数要显眼：有超标至少看得见，有空白却显示「正常」才是最危险的 -->
              <el-tag v-if="gapCount" type="danger" size="small" effect="dark">
                断档 {{ gapCount }} 次
              </el-tag>
              <span class="record-count">共 {{ checkInList.length }} 条</span>
            </el-space>
          </div>
        </template>
        <el-table :data="checkInList" border stripe size="small">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="recordTime" label="记录时间" width="170" align="center" />
          <el-table-column prop="temperature" label="温度 (℃)" width="120" align="center">
            <template #default="{ row }">
              <span :class="{ 'temp-abnormal-value': row.isAbnormal }">{{ Number(row.temperature).toFixed(1) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="isAbnormal" label="是否异常" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isAbnormal ? 'danger' : 'success'" size="small">{{ row.isAbnormal ? '异常' : '正常' }}</el-tag>
            </template>
          </el-table-column>
          <!-- 采集间隔：探头按额定间隔定频打卡，本条距上一条远超额定即标「断档」。
               断档标在断档之后的第一条上 —— 用户看到的就是「从这一刻起中间是空白」。 -->
          <el-table-column label="距上一条" width="150" align="center">
            <template #default="{ row }">
              <el-tooltip
                :content="`探头额定间隔 ${row.sampleIntervalSeconds != null ? row.sampleIntervalSeconds : '--'} 秒；超过额定间隔的若干倍即判为断档（容差倍数默认 3）`"
                placement="top">
                <span>
                  <el-tag v-if="Number(row.isGap) === 1" type="danger" size="small" effect="plain">断档</el-tag>
                  <span v-else-if="row.gapSeconds != null" class="sub-text">{{ row.gapSeconds }} 秒</span>
                  <span v-else class="sub-text">首条</span>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="source" label="采集设备" width="140" align="center">
            <template #default="{ row }">
              <el-tooltip :content="row.source === 'DEVICE' ? `探头自动采集：${row.sourceRef || '--'}` : sourceLabel(row.source)" placement="top">
                <el-tag :type="sourceTag(row.source)" size="small" effect="plain">{{ deviceText(row) }}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 途经站点（一车多店）：一趟车顺路卸多家门店。
           站点清单不是人规划出来的，是按装车扫码顺序「扫」出来的 ——
           扫到的批次属于哪张发货计划，那一站就是那张计划的目的地。 -->
      <el-card v-if="transportStops.length > 1" class="stops-card">
        <template #header>
          <div class="card-header">
            <span>途经站点</span>
            <el-space>
              <el-tag size="small" :type="arrivedStopCount === transportStops.length ? 'primary' : 'info'" effect="plain">
                已到达 {{ arrivedStopCount }} / {{ transportStops.length }} 站
              </el-tag>
              <el-tag size="small" :type="receivedStopCount === transportStops.length ? 'success' : 'warning'" effect="plain">
                已签收 {{ receivedStopCount }} / {{ transportStops.length }} 站
              </el-tag>
              <!--
                到达与签收是两件事：到达是「车到了这个卸货点」（位置事实），签收是「货权转移了」（凭证）。
                一车多店时两者都按站点算：全部站点到达才算「待签收」，全部站点签完才算「已签收」。
              -->
              <span class="record-count">按装车扫码顺序排列</span>
            </el-space>
          </div>
        </template>
        <el-table :data="transportStops" border stripe size="small">
          <el-table-column label="站序" width="70" align="center">
            <template #default="{ row, $index }">{{ row.seq != null ? row.seq : $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="卸货门店" min-width="160">
            <template #default="{ row }">
              {{ row.storeName || '未记录' }}
              <el-tag v-if="row.primary" size="small" effect="plain" style="margin-left: 4px">主站</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="货物" width="120">
            <template #default="{ row }">
              <div>{{ row.batchCount }} 个批次</div>
              <div class="sub-text">{{ row.weightKg != null ? Number(row.weightKg).toFixed(1) + ' kg' : '--' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="批次明细" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.batchNos || []).join('、') || '--' }}</template>
          </el-table-column>
          <!-- 到达是站点级的：一车三站各有各的到达时刻，运单主表那一个 arrive_time 装不下 -->
          <el-table-column label="到达" width="170" align="center">
            <template #default="{ row }">
              <el-tag :type="row.arrived ? 'primary' : 'info'" size="small" effect="plain">
                {{ row.arrived ? '已到达' : '未到达' }}
              </el-tag>
              <div v-if="row.arriveTime" class="sub-text">{{ row.arriveTime }}</div>
              <div v-else-if="row.arrived" class="sub-text">时间未记录</div>
              <!-- 到达由司机本人刷卡确认（与发车同一台车载终端）：谁确认的必须看得见，
                   否则「这一站到了谁的账上」就没了落点。人工确认到达没有确认人，如实写出来。 -->
              <div v-if="row.arrived" class="sub-text">
                {{ row.arriveDriverName
                  ? `${row.arriveDriverName} · ${row.arriveDriverSource === 'CARD' ? '刷卡确认' : '本人确认'}`
                  : '人工确认到达' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="签收" width="190" align="center">
            <template #default="{ row }">
              <el-tag :type="row.received ? 'success' : 'warning'" size="small" effect="plain">
                {{ row.received ? '已签收' : '待签收' }}
              </el-tag>
              <div v-if="row.received" class="sub-text">
                {{ row.receiver || '未记录' }}<template v-if="row.receiptTime"> · {{ row.receiptTime }}</template>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 运输状态时间轴 -->
      <el-card class="transport-timeline-card">
        <template #header><span>运输状态时间轴</span></template>
        <div class="transport-timeline">
          <div v-for="(node, idx) in transportTimeline" :key="idx" class="tt-node" :class="{ 'tt-done': node.done, 'tt-active': node.active }">
            <div class="tt-line">
              <div class="tt-dot">
                <el-icon v-if="node.done" :size="16"><CircleCheckFilled /></el-icon>
                <el-icon v-else-if="node.active" :size="16" class="is-pulsing"><Clock /></el-icon>
                <div v-else class="tt-dot-empty"></div>
              </div>
              <div v-if="idx < transportTimeline.length - 1" class="tt-connector" :class="{ done: node.done }"></div>
            </div>
            <div class="tt-content">
              <div class="tt-title">{{ node.title }}</div>
              <div class="tt-time">{{ node.time }}</div>
              <div v-if="node.desc" class="tt-desc">{{ node.desc }}</div>
            </div>
          </div>
        </div>
      </el-card>
    </template>

    <el-empty v-else description="请选择上方运单查看冷链监控详情" />

    <!-- ========== 新建/编辑运单弹窗 ==========
         2026-10-12 起「新建运单」按钮已删除：运单只由装车口扫码（LOAD）或按计划「生成运单」产生。
         弹窗保留给将来可能恢复的「补录司机」编辑入口，当前页面没有任何打开它的路径。 -->
    <el-dialog v-model="createVisible" :title="editingId ? '编辑冷链运单' : '新建冷链运单'" width="680px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="分割批次" prop="splitBatchId">
          <el-select v-model="createForm.splitBatchId" filterable remote placeholder="输入产品名/批次号搜索"
            :remote-method="searchTransportSplits" :loading="splitLoading" style="width: 100%"
            :disabled="editingId !== null && editingStatus !== 1" @change="handleSplitChange">
            <el-option v-for="s in splitOptions" :key="s.id" :label="`${s.productName || '--'}（${s.batchNo}）`" :value="s.id" />
          </el-select>
          <div v-if="editingId !== null && editingStatus !== 1" class="form-tip">运单已发车，关联分割批次不可变更</div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="车牌号" prop="vehicleNo">
              <el-input v-model="createForm.vehicleNo" placeholder="如 京A·12345" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="车辆类型">
              <el-select v-model="createForm.vehicleType" placeholder="请选择" clearable style="width:100%">
                <el-option label="冷藏车" value="冷藏车" />
                <el-option label="冷冻车" value="冷冻车" />
                <el-option label="保温车" value="保温车" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="制冷方式">
              <el-select v-model="createForm.refrigeration" placeholder="请选择" clearable style="width:100%">
                <el-option label="机械制冷" value="机械制冷" />
                <el-option label="蓄冷板" value="蓄冷板" />
                <el-option label="液氮" value="液氮" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="司机姓名" prop="driverName">
              <el-input v-model="createForm.driverName" placeholder="可留空，发车刷卡时确认" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="司机电话" prop="driverPhone">
              <el-input v-model="createForm.driverPhone" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="起运地" prop="origin">
              <el-input v-model="createForm.origin" placeholder="如 顺鑫屠宰场冷库" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目的地" prop="destination">
              <el-select
                v-model="createForm.destination"
                placeholder="请选择或输入门店"
                filterable
                allow-create
                default-first-option
                style="width: 100%"
              >
                <el-option
                  v-for="item in storeOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.name"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划发车">
              <el-date-picker v-model="createForm.plannedDepart" type="datetime" placeholder="选择日期时间"
                value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划到达">
              <el-date-picker v-model="createForm.plannedArrive" type="datetime" placeholder="选择日期时间"
                value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-popconfirm v-if="editingId && editingStatus === 1" title="确认该运单立即发车？" @confirm="handleDepartFromEdit">
          <template #reference>
            <el-button type="warning">发车</el-button>
          </template>
        </el-popconfirm>
        <el-popconfirm v-else-if="editingId && editingStatus === 2"
          title="确认该运单到达下一站？（一车多店时一次推进一站，按站点序号）" @confirm="handleArriveFromEdit">
          <template #reference>
            <el-button type="success">确认到达</el-button>
          </template>
        </el-popconfirm>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">{{ editingId ? '保存修改' : '确认创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- ========== 新建/编辑发货计划 ========== -->
    <el-dialog v-model="planFormVisible" :title="planEditingId ? '编辑发货计划' : '新建发货计划'" width="760px" destroy-on-close>
      <el-form ref="planFormRef" :model="planForm" :rules="planRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划发货日" prop="planDate">
              <el-date-picker v-model="planForm.planDate" type="date" value-format="YYYY-MM-DD"
                placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目的地门店" prop="storeName">
              <el-select v-model="planForm.storeName" filterable allow-create default-first-option
                placeholder="请选择或输入门店" style="width:100%" @change="handlePlanStoreChange">
                <el-option v-for="item in storeOptions" :key="item.id" :label="item.name" :value="item.name" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="装车货物" prop="splitBatchIds">
          <el-select v-model="planForm.splitBatchIds" multiple filterable remote reserve-keyword
            placeholder="搜索待发运的分割批次" :remote-method="searchPendingSplits" :loading="pendingLoading"
            :disabled="planEditingPartial" style="width:100%">
            <el-option v-for="s in pendingSplits" :key="s.id"
              :label="`${s.productName || '--'}（${s.batchNo}）${s.weightKg ? ' · ' + s.weightKg + 'kg' : ''}`"
              :value="s.id" />
          </el-select>
          <div class="form-tip">
            <template v-if="planEditingPartial">
              该计划已部分排车，货物清单锁定：已排进运单的货不能改，剩下的等下一车。这里只改车牌/司机/时间等信息
            </template>
            <template v-else>
              只列出尚未发运的批次；同一批次被别的待发货计划占用、或已经发运过，服务端都会拦下
            </template>
          </div>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="车牌号">
              <el-input v-model="planForm.vehicleNo" placeholder="可留空，生成运单前必须补全" />
              <!-- 「排车」在系统里指的是**生成运单**那一步：这里填车牌只是把车定在计划上，
                   货还没挂到运单上 —— 设备接入页的发车/到站刷卡倒查的是运单，此时取不到单子。 -->
              <div class="form-tip">填车牌只是把车定下来；点过「生成运单」货才挂到运单上（计划转「已排车」，运单为「待发车」）</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="车辆类型">
              <el-select v-model="planForm.vehicleType" placeholder="请选择" clearable style="width:100%">
                <el-option label="冷藏车" value="冷藏车" />
                <el-option label="冷冻车" value="冷冻车" />
                <el-option label="保温车" value="保温车" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="司机姓名">
              <el-input v-model="planForm.driverName" placeholder="可留空，发车刷卡时确认" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="司机电话">
              <el-input v-model="planForm.driverPhone" maxlength="11" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="起运地">
              <el-input v-model="planForm.origin" placeholder="留空则按货物批次自动带出" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="制冷方式">
              <el-select v-model="planForm.refrigeration" placeholder="请选择" clearable style="width:100%">
                <el-option label="机械制冷" value="机械制冷" />
                <el-option label="蓄冷板" value="蓄冷板" />
                <el-option label="液氮" value="液氮" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划发车">
              <el-date-picker v-model="planForm.plannedDepart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择日期时间" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划到达">
              <el-date-picker v-model="planForm.plannedArrive" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择日期时间" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="planForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="planSubmitting" @click="submitPlan">
          {{ planEditingId ? '保存修改' : '创建计划' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 发货计划详情 ========== -->
    <el-dialog v-model="planDetailVisible" title="发货计划详情" width="680px">
      <el-descriptions v-if="planDetail" :column="2" border size="small">
        <el-descriptions-item label="计划号">{{ planDetail.planNo }}</el-descriptions-item>
        <el-descriptions-item label="计划发货日">{{ planDetail.planDate }}</el-descriptions-item>
        <!-- 计划来源决定「这张单是谁提的」：内部调度是补货/调拨/铺货（没有订单），
             订单转入则要能顺着来源订单号回到外部系统去核对 -->
        <el-descriptions-item label="计划来源">
          <el-tag :type="planDetail.planSource === 'ORDER' ? 'warning' : 'info'" size="small"
            :effect="planDetail.planSource === 'ORDER' ? 'light' : 'plain'">
            {{ planSourceLabel(planDetail.planSource) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源订单号">{{ planDetail.orderNo || '--' }}</el-descriptions-item>
        <el-descriptions-item label="目的地">{{ planDetail.storeName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="planStatusTagType(planDetail.status)" size="small">{{ planStatusLabel(planDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="车牌号">{{ planDetail.vehicleNo || '待安排' }}</el-descriptions-item>
        <el-descriptions-item label="车辆类型">{{ planDetail.vehicleType || '--' }}</el-descriptions-item>
        <el-descriptions-item label="司机">{{ planDetail.driverName || '待安排' }}</el-descriptions-item>
        <el-descriptions-item label="司机电话">{{ planDetail.driverPhone || '--' }}</el-descriptions-item>
        <el-descriptions-item label="起运地">{{ planDetail.origin || '--' }}</el-descriptions-item>
        <el-descriptions-item label="制冷方式">{{ planDetail.refrigeration || '--' }}</el-descriptions-item>
        <el-descriptions-item label="计划发车">{{ planDetail.plannedDepart || '--' }}</el-descriptions-item>
        <el-descriptions-item label="计划到达">{{ planDetail.plannedArrive || '--' }}</el-descriptions-item>
        <el-descriptions-item label="最近运单">{{ planDetail.transportNo || '尚未生成' }}</el-descriptions-item>
        <el-descriptions-item label="已发车次">{{ planDetail.transportCount || 0 }} 车</el-descriptions-item>
        <el-descriptions-item label="制单人">{{ planDetail.operator || '--' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ planDetail.remark || '--' }}</el-descriptions-item>
      </el-descriptions>

      <div class="plan-detail-sub">
        装车货物（共 {{ planDetailItems.length }} 个批次，其中待发运 {{ planPendingItems.length }} 个）
      </div>
      <el-table :data="planDetailItems" border size="small">
        <el-table-column prop="batchNo" label="分割批次" width="170" />
        <el-table-column prop="partCode" label="部位编码" width="90" align="center">
          <template #default="{ row }">{{ row.partCode || '--' }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="部位" min-width="110" />
        <el-table-column label="重量(kg)" width="100" align="right">
          <template #default="{ row }">{{ row.weightKg != null ? row.weightKg : '--' }}</template>
        </el-table-column>
        <!-- 列名与取值都跟着计划侧口径走（「已排车 / 待排车」）：明细这一格说的是
             「这批货排进运单了没」，不是「车走到哪」—— 车走到哪在运单侧看 -->
        <el-table-column label="排车状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.shipped ? 'success' : 'warning'" size="small">
              {{ row.shipped ? '已排车' : '待排车' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- ========== 生成运单（可分批装车） ========== -->
    <el-dialog v-model="generateVisible" title="按计划生成运单" width="640px">
      <div v-if="generatePlan" class="plan-detail-sub">
        计划 {{ generatePlan.planNo }} → {{ generatePlan.storeName }}，
        车牌 {{ generatePlan.vehicleNo || '（未填，需先补全）' }}，
        司机 {{ generatePlan.driverName || '（未填，可留空——发车刷卡时确认）' }}
      </div>
      <el-alert
        v-if="generatePlan && !generatePlan.vehicleNo"
        type="warning"
        :closable="false"
        show-icon
        title="车牌必须补全后才能生成运单"
        description="车牌是后面发车、到达都要按车牌倒查的唯一凭据，缺了整趟车的行程就断了，所以必须补。司机可以留空：现场只在上车那一刻刷一次卡，装车时司机还没上车；但发车之前必须落到具体的人（车载终端刷卡，或人工补录）。"
        style="margin-bottom: 12px"
      />
      <div class="plan-detail-sub">勾选本次装车的货物（默认全选；不勾的留在计划里等下一车）</div>
      <el-table ref="generateTableRef" :data="generatePendingItems" border size="small"
        @selection-change="onGenerateSelect">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="batchNo" label="分割批次" width="170" />
        <el-table-column prop="productName" label="部位" min-width="110" />
        <el-table-column label="重量(kg)" width="100" align="right">
          <template #default="{ row }">{{ row.weightKg != null ? row.weightKg : '--' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="generateVisible = false">取下</el-button>
        <el-button type="primary" :loading="generateSubmitting" :disabled="!generateSelected.length"
          @click="submitGenerate">
          生成运单（{{ generateSelected.length }} 批）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { CircleCheckFilled, Clock, WarningFilled, Search, RefreshLeft, Right, InfoFilled, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import type { FormInstance } from 'element-plus'
import type { EpTagType } from '@/types/common'
import { distributionApi } from '@/api/modules/distribution'
import { deviceText, sourceLabel, sourceTag } from '@/api/modules/ingest'
import request from '@/utils/request'

// ==================== 运单列表 ====================
const query = reactive({ keyword: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })
const transportList = ref<any[]>([])
const total = ref(0)
const listLoading = ref(false)
const currentTransport = ref<any>(null)
// 分割批次 id -> {productName, batchNo} 映射，用于列表展示
const splitMap = reactive<Record<number, any>>({})

const statusOptions = [
  { value: 1, label: '待发车' },
  { value: 2, label: '运输中' },
  { value: 3, label: '待签收' },
  { value: 4, label: '已签收' },
]
const statusTagType = (s: number): EpTagType => (({ 1: 'info', 2: 'warning', 3: 'primary', 4: 'success' } as Record<number, EpTagType>)[s] || 'info')
const statusLabel = (s: number) => ({ 1: '待发车', 2: '运输中', 3: '待签收', 4: '已签收' } as Record<number, string>)[s] || '未知'

/**
 * 司机是怎么确认的（口径与后端 DriverSource 一致）。
 * 只有「刷卡 / 人脸」是本人确认，车牌反查是推定、人工录入是调度代填 ——
 * 追责时看的是这个，而不是司机姓名非空。人工录入不额外打标记（那是常态，标出来反而吵）。
 */
function driverSourceLabel(source?: string) {
  if (source === 'CARD') return '刷卡确认'
  if (source === 'FACE') return '人脸识别'
  if (source === 'PLATE_INFERRED') return '车牌反查（推定）'
  return ''
}

function rowClassName({ row }: { row: any }) {
  return currentTransport.value?.id === row.id ? 'current-row' : ''
}

async function fetchTransports() {
  listLoading.value = true
  try {
    const res: any = await distributionApi.getTransports({
      keyword: query.keyword || undefined,
      status: query.status,
      pageNum: query.pageNum, pageSize: query.pageSize,
    })
    transportList.value = res?.records || []
    total.value = res?.total || 0
    // 补齐列表引用到的分割批次名称
    const missing = [...new Set(transportList.value.map((t: any) => t.splitBatchId).filter((id: number) => id && !splitMap[id]))]
    await Promise.all(missing.map((id: number) => distributionApi.getSplitDetail(id)
      .then((s: any) => { splitMap[id] = s }).catch(() => {})))
  } catch { /* 拦截器已提示 */ } finally { listLoading.value = false }
}

function handleSearch() { query.pageNum = 1; fetchTransports() }
function handleReset() {
  query.keyword = ''; query.status = undefined; query.pageNum = 1
  fetchTransports()
}
function onPageChange(p: number) { query.pageNum = p; fetchTransports() }

// ==================== 运输监控 ====================
const currentTemp = ref<number | null>(null)
const isTempNormal = computed(() => currentTemp.value === null || (currentTemp.value >= -18 && currentTemp.value <= 0))
const checkInList = ref<any[]>([])
const transportTimeline = ref<any[]>([])
/** 途经站点（一车多店）：按卸货门店聚合装载明细而来，站点顺序 = 装车扫码顺序 */
const transportStops = ref<any[]>([])
/** 已签收站点数：一车多店时只有全部站点签完，运单才算「已签收」 */
const receivedStopCount = computed(() => transportStops.value.filter((s) => s.received).length)
/** 已到达站点数：到达与签收分开算 —— 「车到了」不等于「货交出去了」 */
const arrivedStopCount = computed(() => transportStops.value.filter((s) => s.arrived).length)
/**
 * 当前第一个未到达的站。一车多站时「确认到达」推的就是它（顺序由站点序号定，页面不另算一套），
 * 所以按钮上要说清是哪一站 —— 否则演示/操作时根本不知道点下去会推进哪一站。
 */
const nextStop = computed(() => transportStops.value.find((s: any) => !s.arrived) || null)

/**
 * 列表里的站点进度文案。
 * <p>
 * 单站运单也要说清「到了没」：站点级到达如果只在多站时才有落点，使用者就完全看不出
 * 「到达是按卸货门店逐站记的」。措辞按状态分：待发车时站点一律是「未到达」，
 * 若写成「已到 0/1 站」会让人以为车已经在路上。
 */
function stopProgressLabel(row: any) {
  const total = Number(row?.stopCount || 0)
  if (!total) return '--'
  const arrived = Number(row?.arrivedStopCount || 0)
  const received = Number(row?.receivedStopCount || 0)
  const head = total > 1 ? `途经 ${total} 站` : '1 站'
  if (Number(row?.status) === 1) return `${head} · 待发车`
  if (received >= total) return `${head} · 已签收`
  if (arrived >= total) return `${head} · 待签收`
  if (arrived <= 0) return `${head} · 未到达`
  return `${head} · 已到 ${arrived}/${total}`
}

/**
 * 详情信息条的站点摘要：单站时说清「哪一站、到了没」，多站时说清「到几站了」。
 * 站点台账还没加载出来时退回 stopCount，不假装「没有站点」。
 */
const stopSummaryLabel = computed(() => {
  const list = transportStops.value
  if (!list.length) {
    const n = Number(currentTransport.value?.stopCount || 0)
    return n ? `${n} 站 · 进度读取中` : '--'
  }
  const n = list.length
  const arrived = arrivedStopCount.value
  const received = receivedStopCount.value
  const head = n > 1 ? `途经 ${n} 站` : `1 站（${list[0].storeName || '未记录门店'}）`
  if (received >= n) return `${head} · 已签收`
  if (arrived >= n) return `${head} · 待签收`
  if (arrived <= 0) return `${head} · 未到达`
  return `${head} · 已到达 ${arrived}/${n}`
})

/**
 * 司机怎么显示。
 * <p>
 * 装车口没有读卡器（现场只在上车那一刻刷一次卡），所以「还没发车、司机是空的」是**正常中间态**，
 * 不是数据丢了 —— 如实说清「等发车刷卡确认」，别写成 `--` 让人以为漏了。
 */
function driverText(t: any) {
  if (t?.driverName) return t.driverName
  if (Number(t?.status) === 1) return '未记录（等发车刷卡确认）'
  return '未记录'
}
/**
 * 温度断档次数。
 * 「空白 ≠ 正常」：两条记录之间隔了远超额定间隔的时间而没有任何数据，
 * 界面若只显示「温度正常」是在假装有数据。断档数要能一眼看到。
 */
const gapCount = computed(() => checkInList.value.filter((l) => l && Number(l.isGap) === 1).length)
const monitorRef = ref<HTMLElement>()

// 图表
const tempChartRef = ref<HTMLElement>()
let tempChart: echarts.ECharts | null = null

async function selectTransport(row: any) {
  if (!row || currentTransport.value?.id === row.id) return
  currentTransport.value = row
  currentTemp.value = null
  checkInList.value = []
  transportStops.value = []
  await Promise.all([loadTemperatureLogs(), loadTransportStops()])
  // 站点到位之后再建时间轴：到达节点要展示「已到达 N / M 站」，
  // 先建就会拿着空站点列表算出一句错话
  buildTimeline(row)
  nextTick(() => monitorRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

/** 途经站点清单：单站运单也会返回一条，界面只在多站时才展开成卡片 */
async function loadTransportStops() {
  if (!currentTransport.value) return
  try {
    const list: any = await distributionApi.getTransportStops(currentTransport.value.id)
    transportStops.value = Array.isArray(list) ? list : []
  } catch { transportStops.value = [] }
}

/**
 * 人工发车（设备故障 / 现场无网时的兜底）。
 * <p>
 * 司机是硬前提：现场只在上车那一刻刷一次卡，装车建单时司机常是空的 ——
 * 所以这里先拦一道，别让货在「没有责任人」的状态下发出去（服务端也会拦，两条路一致）。
 */
async function handleDepart(row: any) {
  if (!row?.driverName) {
    ElMessage.warning('该运单还没有司机：货要走了，责任必须落到具体的人。'
      + '请先编辑运单补录司机，或由司机在车载终端刷卡发车')
    return
  }
  try {
    await distributionApi.departTransport(row.id)
    ElMessage.success(`运单 ${row.transportNo} 已发车`)
    await afterStatusChange(row.id)
  } catch { /* 拦截器已提示 */ }
}

async function handleArrive(row: any) {
  try {
    const res: any = await distributionApi.arriveTransport(row.id)
    // 多站车次一次只推进一站，所以提示必须说清是**哪一站**到了 ——
    // 笼统一句「已送达」在一车三店时是错觉：车只是到了第一家。
    const stopNo = res?.stopNo
    const store = res?.storeName
    ElMessage.success(stopNo
      ? `运单 ${row.transportNo} 已到达第 ${stopNo} 站${store ? `（${store}）` : ''}`
      : `运单 ${row.transportNo} 已确认送达`)
    await afterStatusChange(row.id)
  } catch { /* 拦截器已提示 */ }
}

async function handleDepartFromEdit() {
  if (!editingId.value) return
  // 与列表里那颗「立即发车」同一条闸门：运单编辑弹窗里也让先补司机（此处司机就在表单里，顺手能填）
  if (!createForm.driverName) {
    ElMessage.warning('还没有司机：货要走了，责任必须落到具体的人。请先填写司机姓名，或由司机在车载终端刷卡发车')
    return
  }
  try {
    await distributionApi.departTransport(editingId.value)
    ElMessage.success('运单已发车')
    createVisible.value = false
    await afterStatusChange(editingId.value)
  } catch { /* 拦截器已提示 */ }
}

async function handleArriveFromEdit() {
  if (!editingId.value) return
  try {
    const res: any = await distributionApi.arriveTransport(editingId.value)
    ElMessage.success(res?.stopNo
      ? `已到达第 ${res.stopNo} 站${res.storeName ? `（${res.storeName}）` : ''}`
      : '运单已确认送达')
    createVisible.value = false
    await afterStatusChange(editingId.value)
  } catch { /* 拦截器已提示 */ }
}

async function afterStatusChange(id: number) {
  await fetchTransports()
  const latest = transportList.value.find((t: any) => t.id === id)
  if (latest) currentTransport.value = latest
  // 发车/到达会改变站点与温度的可用性（未发车不能采温度），一并刷新
  await Promise.all([loadTransportStops(), loadTemperatureLogs()])
  // 时间轴最后建：到达节点要按最新站点完成情况说话
  if (latest) buildTimeline(latest)
}

/* 温度打卡（人工补录）入口已删除：冷链温度为设备独占环节，
   读数只接受车载/冷库探头通过 /distribution/internal/ingest/temperature 上报。 */

async function loadTemperatureLogs() {
  if (!currentTransport.value) return
  try {
    const logs: any = await distributionApi.getTemperatureLog(currentTransport.value.id)
    checkInList.value = logs || []
    // 接口按「记录时间升序」返回（时间轴本来就是这个方向），所以**最后一条才是当前温度**。
    // 之前取 [0] 拿到的是最早那条 —— 在途的运单会一直显示发车时的第一个读数。
    currentTemp.value = checkInList.value.length
      ? Number(checkInList.value[checkInList.value.length - 1].temperature)
      : null
    nextTick(() => renderChart())
  } catch { checkInList.value = [] }
}

function renderChart() {
  if (!tempChartRef.value) return
  if (!tempChart) tempChart = echarts.init(tempChartRef.value)

  // 接口按记录时间升序返回，直接沿时间轴画；之前多了一次 reverse()，把时间轴画反了
  const logs = checkInList.value
  const times = logs.map((l: any) => (l.recordTime || '').slice(11, 16) || '--')
  const temps = logs.map((l: any) => Number(l.temperature))

  tempChart.setOption({
    tooltip: { trigger: 'axis', formatter: (params: any) => `${params[0].axisValue}<br/>温度: <b>${params[0].value}℃</b>` },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: times, boundaryGap: false, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', name: '温度 (℃)', min: -25, max: 10,
      axisLabel: { formatter: '{value}℃' } },
    series: [{
      type: 'line', data: temps, smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { color: '#409eff', width: 2 },
      itemStyle: { color: (params: any) => params.value > 0 || params.value < -18 ? '#f56c6c' : '#409eff' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(64,158,255,0.3)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }
      ])},
      markLine: { silent: true, symbol: 'none', data: [
        { yAxis: 0, lineStyle: { color: '#f56c6c', type: 'dashed', width: 1.5 }, label: { formatter: '上限 0°C', fontSize: 10 } },
        { yAxis: -18, lineStyle: { color: '#409eff', type: 'dashed', width: 1.5 }, label: { formatter: '下限 -18°C', fontSize: 10 } },
      ]},
    }],
  })
}

function handleResize() { tempChart?.resize() }

// 根据运输状态与时间生成时间轴
function buildTimeline(t: any) {
  const status = t.status
  // 实际时间不存在时展示计划时间并加"预计"前缀，避免在途运单看起来已到达
  const departTime = t.departTime || (t.plannedDepart ? `预计 ${t.plannedDepart}` : '')
  const arriveTime = t.arriveTime || (t.plannedArrive ? `预计 ${t.plannedArrive}` : '')
  transportTimeline.value = [
    {
      title: '发车', time: departTime || '--', desc: `车牌 ${t.vehicleNo || '--'} 从 ${t.origin || '--'} 出发`,
      done: status >= 2, active: status === 1,
    },
    {
      title: '运输途中', time: t.departTime || '--', desc: '冷链运输中，全程温控记录',
      done: status >= 3, active: status === 2,
    },
    {
      title: '到达门店', time: arriveTime || '--',
      // 一车多站时「到达」是逐站的：这里说清到几站了，而不是笼统一句「已到达」
      desc: transportStops.value.length > 1
        ? `已到达 ${arrivedStopCount.value} / ${transportStops.value.length} 站，全部到达后转「待签收」`
        : `到达 ${t.destination || '--'}，等待门店签收`,
      done: status >= 3, active: status === 3,
    },
    {
      title: '门店签收', time: status >= 4 ? arriveTime : '--', desc: '门店已确认签收',
      done: status >= 4, active: false,
    },
  ]
}

// ==================== 新建/编辑运单 ====================
const createVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const editingStatus = ref(1)
const createFormRef = ref<FormInstance>()
const splitOptions = ref<any[]>([])
const splitLoading = ref(false)
const createForm = reactive<any>({
  splitBatchId: null, vehicleNo: '', vehicleType: '', refrigeration: '',
  driverName: '', driverPhone: '', origin: '', destination: '',
  plannedDepart: '', plannedArrive: '',
})
const createRules = {
  splitBatchId: [{ required: true, message: '请选择分割批次', trigger: 'change' }],
  vehicleNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  // 司机**不设为必填**：现场只在上车那一刻刷一次卡，装车时司机还没上车，
  // 所以「建单时司机未知」是常态；但发车前必须落到具体的人（发车那一步两条路都会拦）。
  // origin/destination 仍必填：那是这批货去哪、从哪来，人的决策，没有中间态。
  origin: [{ required: true, message: '请输入起运地', trigger: 'blur' }],
  destination: [{ required: true, message: '请输入目的地', trigger: 'blur' }],
  driverPhone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
}

/**
 * 运单弹窗的批次下拉：只列「待发运」的批次。
 * <p>
 * 不能用全量的 getSplits —— 那样能把已经发运过的批次再选一次，做出同一批货两张运单；
 * 发货计划侧有两道拦截，手工建单是唯一的漏洞。编辑老运单时把当前批次补进候选，
 * 否则下拉显示成裸 id，看上去像数据丢了。
 */
async function searchTransportSplits(kw: string) {
  splitLoading.value = true
  try {
    const res: any = await distributionApi.getPendingSplits({ keyword: kw || undefined, pageNum: 1, pageSize: 50 })
    const records = res?.records || []
    records.forEach((s: any) => { splitMap[s.id] = s })
    const current = createForm.splitBatchId
    if (current && !records.some((r: any) => r.id === current)) {
      if (!splitMap[current]) {
        try { splitMap[current] = await distributionApi.getSplitDetail(current) } catch { /* 取不到就只好显示 id */ }
      }
      if (splitMap[current]) records.unshift(splitMap[current])
    }
    splitOptions.value = records
  } catch { splitOptions.value = [] } finally { splitLoading.value = false }
}

// 门店下拉：从机构树提取 type=retail 的节点
const storeOptions = ref<Array<{ id: number; name: string }>>([])
const fetchStoreList = async () => {
  try {
    const tree = await request.get('/system/orgs/tree')
    const list: Array<{ id: number; name: string }> = []
    const flatten = (nodes: any[]) => {
      if (!Array.isArray(nodes)) return
      nodes.forEach((n) => {
        if (n.type === 'retail') list.push({ id: n.id, name: n.name })
        if (n.children?.length) flatten(n.children)
      })
    }
    flatten(tree || [])
    storeOptions.value = list
  } catch (error) {
    console.error('获取门店列表失败:', error)
    storeOptions.value = []
  }
}

// ==================== 发货计划 ====================
// 计划是运单的上游决策单：先定「发哪批货、去哪家店、什么时候走」，运单从它一键生成。
const planPanelVisible = ref(true)
const planQuery = reactive({ keyword: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })
const planList = ref<any[]>([])
const planTotal = ref(0)
const planLoading = ref(false)
// 文案口径（2026-10-10 起）：计划侧只表达「货有没有排进运单」，**不表达车走到哪**。
// 排完车下一步还停在运单的「待发车」，等真的发车了运单才动 —— 两条时间线分开，
// 所以 2 叫「已排车」而不是「已发运」。
const planStatusOptions = [
  { value: 1, label: '待发货' },
  { value: 4, label: '部分排车' },
  { value: 2, label: '已排车' },
  { value: 3, label: '已取消' },
]
const planStatusLabel = (s: number) => ({ 1: '待发货', 2: '已排车', 3: '已取消', 4: '部分排车' } as Record<number, string>)[s] || '未知'
const planStatusTagType = (s: number): EpTagType => (({ 1: 'warning', 2: 'success', 3: 'info', 4: 'primary' } as Record<number, EpTagType>)[s] || 'info')
/** 计划来源的中文说法：内部调度 = 补货/调拨/铺货（没有订单）；订单转入 = 外部系统推来的 */
const planSourceLabel = (s?: string) => (s === 'ORDER' ? '订单转入' : '内部调度')

async function fetchPlans() {
  planLoading.value = true
  try {
    const res: any = await distributionApi.getPlans({
      keyword: planQuery.keyword || undefined,
      status: planQuery.status,
      pageNum: planQuery.pageNum, pageSize: planQuery.pageSize,
    })
    planList.value = res?.records || []
    planTotal.value = res?.total || 0
  } catch { /* 拦截器已提示 */ } finally { planLoading.value = false }
}
function handlePlanSearch() { planQuery.pageNum = 1; fetchPlans() }
function onPlanPageChange(p: number) { planQuery.pageNum = p; fetchPlans() }

// 装车货物下拉：只列尚未发运的批次（已被运单装载过的批次不该再进新计划）
const pendingSplits = ref<any[]>([])
const pendingLoading = ref(false)
/** 编辑中的计划原明细：那些批次已被本计划自己占用，不会出现在「待发运」列表里 */
const editingPlanItems = ref<any[]>([])
/** 正在编辑的是「部分排车」的计划：货物清单锁定，只能改车牌/司机/时间等信息 */
const planEditingPartial = ref(false)

async function searchPendingSplits(kw: string) {
  pendingLoading.value = true
  try {
    const res: any = await distributionApi.getPendingSplits({ keyword: kw || undefined, pageNum: 1, pageSize: 50 })
    const records = res?.records || []
    const chosen = planForm.splitBatchIds || []
    // 把自己已占用的批次补进候选，否则编辑时下拉显示成裸 id（看上去像数据丢了）
    const own = editingPlanItems.value
      .filter((it: any) => chosen.includes(it.splitBatchId) && !records.some((r: any) => r.id === it.splitBatchId))
      .map((it: any) => ({ id: it.splitBatchId, batchNo: it.batchNo, productName: it.productName, partCode: it.partCode, weightKg: it.weightKg }))
    pendingSplits.value = [...records, ...own]
  } catch { pendingSplits.value = [] } finally { pendingLoading.value = false }
}

const planFormVisible = ref(false)
const planFormRef = ref<FormInstance>()
const planSubmitting = ref(false)
const planEditingId = ref<number | null>(null)
const planForm = reactive({
  planDate: '', storeId: null as number | null, storeName: '',
  splitBatchIds: [] as number[],
  vehicleNo: '', vehicleType: '', refrigeration: '',
  driverName: '', driverPhone: '', origin: '',
  plannedDepart: '', plannedArrive: '', remark: '',
})
const planRules = {
  planDate: [{ required: true, message: '请选择计划发货日', trigger: 'change' }],
  storeName: [{ required: true, message: '请选择目的地门店', trigger: 'change' }],
  splitBatchIds: [{ type: 'array', required: true, min: 1, message: '至少要勾选一个待发运的分割批次', trigger: 'change' }],
}

function resetPlanForm() {
  Object.assign(planForm, {
    planDate: '', storeId: null, storeName: '', splitBatchIds: [],
    vehicleNo: '', vehicleType: '', refrigeration: '',
    driverName: '', driverPhone: '', origin: '',
    plannedDepart: '', plannedArrive: '', remark: '',
  })
  editingPlanItems.value = []
  planEditingPartial.value = false
}

// 门店下拉只有名字，id 要从选项里回查（后端建计划需要 storeId + storeName 两个字段）
function handlePlanStoreChange(name: string) {
  const found = storeOptions.value.find((s) => s.name === name)
  planForm.storeId = found ? found.id : null
}

async function openPlanEdit(row: any) {
  planEditingId.value = row.id
  resetPlanForm()
  try {
    const res: any = await distributionApi.getPlanDetail(row.id)
    const plan = res?.plan || {}
    const items = res?.items || []
    editingPlanItems.value = items
    // 「部分排车」的计划只允许改剩下的货：已排进运单的那批是既成台账，服务端也会拦
    planEditingPartial.value = plan.status === 4
    const pendingItems = items.filter((it: any) => !it.shipped)
    Object.assign(planForm, {
      planDate: plan.planDate || '', storeId: plan.storeId ?? null, storeName: plan.storeName || '',
      splitBatchIds: pendingItems.map((it: any) => it.splitBatchId),
      vehicleNo: plan.vehicleNo || '', vehicleType: plan.vehicleType || '', refrigeration: plan.refrigeration || '',
      driverName: plan.driverName || '', driverPhone: plan.driverPhone || '', origin: plan.origin || '',
      plannedDepart: plan.plannedDepart || '', plannedArrive: plan.plannedArrive || '', remark: plan.remark || '',
    })
  } catch { return }
  planFormVisible.value = true
  await searchPendingSplits('')
  nextTick(() => planFormRef.value?.clearValidate())
}

function planPayload() {
  // 空串一律转 null：空串在库里是「有值」，会让「司机是否已安排」这类判断失真
  const orNull = (v: string) => (v ? v : null)
  return {
    planDate: planForm.planDate,
    storeId: planForm.storeId,
    storeName: planForm.storeName,
    splitBatchIds: planForm.splitBatchIds,
    vehicleNo: orNull(planForm.vehicleNo),
    vehicleType: orNull(planForm.vehicleType),
    refrigeration: orNull(planForm.refrigeration),
    driverName: orNull(planForm.driverName),
    driverPhone: orNull(planForm.driverPhone),
    origin: orNull(planForm.origin),
    plannedDepart: orNull(planForm.plannedDepart),
    plannedArrive: orNull(planForm.plannedArrive),
    remark: orNull(planForm.remark),
  }
}

async function submitPlan() {
  if (!planFormRef.value) return
  await planFormRef.value.validate(async (valid) => {
    if (!valid) return
    // 门店允许手输，但手输的名字对不上机构树就取不到 storeId，统计口径会散
    if (!planForm.storeId) {
      ElMessage.warning('目的地门店请从下拉列表中选择')
      return
    }
    planSubmitting.value = true
    try {
      if (planEditingId.value) {
        await distributionApi.updatePlan(planEditingId.value, planPayload())
        ElMessage.success('计划已更新')
      } else {
        const res: any = await distributionApi.createPlan(planPayload())
        ElMessage.success(`计划 ${res.planNo} 已创建`)
      }
      planFormVisible.value = false
      await fetchPlans()
    } catch { /* 拦截器已提示 */ } finally { planSubmitting.value = false }
  })
}

// ==================== 按计划生成运单（支持分批装车） ====================
/**
 * 生成运单前先把「本次装哪几批」摆出来让人勾。
 * 默认全选 = 整单发走；取消勾选 = 分车，没勾的留在计划里等下一车。
 * 不做「一车装不下就整单作废重开」——现场不是那么干活的。
 */
const generateVisible = ref(false)
const generatePlan = ref<any>(null)
const generatePendingItems = ref<any[]>([])
const generateSelected = ref<any[]>([])
const generateSubmitting = ref(false)
const generateTableRef = ref<any>()

async function openGenerate(row: any) {
  try {
    const res: any = await distributionApi.getPlanDetail(row.id)
    const pending = (res?.items || []).filter((it: any) => !it.shipped)
    if (!pending.length) {
      ElMessage.warning('该计划没有待发运的货物')
      return
    }
    generatePlan.value = res?.plan || row
    generatePendingItems.value = pending
    generateSelected.value = []
    generateVisible.value = true
    // 默认全选；勾选状态是手动同步的，否则按钮上的「N 批」会一直是 0
    nextTick(() => {
      generateTableRef.value?.clearSelection()
      generatePendingItems.value.forEach((it: any) => generateTableRef.value?.toggleRowSelection(it, true))
    })
  } catch { /* 拦截器已提示 */ }
}

function onGenerateSelect(rows: any[]) {
  generateSelected.value = rows
}

async function submitGenerate() {
  if (!generatePlan.value || !generateSelected.value.length) return
  if (!generatePlan.value.vehicleNo) {
    ElMessage.warning('该计划还没有车牌号：车牌是发车、到达按车牌倒查的唯一凭据，请先编辑计划补全车牌')
    return
  }
  generateSubmitting.value = true
  try {
    const ids = generateSelected.value.map((it: any) => it.splitBatchId)
    const res: any = await distributionApi.generateTransportFromPlan(generatePlan.value.id, ids)
    const left = generatePendingItems.value.length - ids.length
    ElMessage.success(left > 0
      ? `已生成运单 ${res.transportNo}（本次 ${ids.length} 批），计划转「部分排车」，剩余 ${left} 批等下一车`
      : `已生成运单 ${res.transportNo}，状态：待发车`)
    generateVisible.value = false
    await fetchPlans()
    await fetchTransports()
    const created = transportList.value.find((t: any) => t.id === res.id)
    if (created) await selectTransport(created)
  } catch { /* 拦截器已提示 */ } finally { generateSubmitting.value = false }
}

async function handleCancelPlan(row: any) {
  try {
    await distributionApi.cancelPlan(row.id)
    ElMessage.success('计划已取消，其占用的批次已释放')
    await fetchPlans()
  } catch { /* 拦截器已提示 */ }
}

const planDetailVisible = ref(false)
const planDetail = ref<any>(null)
const planDetailItems = ref<any[]>([])
/** 计划明细里还没发运的部分：生成运单时只能勾这些（发过的货不能再发一次） */
const planPendingItems = computed(() => planDetailItems.value.filter((it: any) => !it.shipped))
async function openPlanDetail(row: any) {
  try {
    const res: any = await distributionApi.getPlanDetail(row.id)
    planDetail.value = res?.plan || row
    planDetailItems.value = res?.items || []
    planDetailVisible.value = true
  } catch { /* 拦截器已提示 */ }
}

/** 从计划跳到它生成的运单：列表里有就直接切监控，没有就重拉一次（可能翻页了） */
function focusTransport(row: any) {
  const found = transportList.value.find((t: any) => t.id === row.transportId)
  if (found) { selectTransport(found); return }
  fetchTransports().then(() => {
    const hit = transportList.value.find((t: any) => t.id === row.transportId)
    if (hit) selectTransport(hit)
    else ElMessage.info('该运单不在当前列表页，可到运单列表按运单号检索')
  })
}

// 选好分割批次后自动带入起运地为该屠宰场
async function handleSplitChange(splitId: number) {
  if (!splitId) return
  const record = splitOptions.value.find((s: any) => s.id === splitId)
  if (!record) return
  // 优先用 record 自带的 slaughterhouse
  if (record.slaughterhouse) {
    createForm.origin = record.slaughterhouse
    return
  }
  // 通过胴体批次查屠宰场：getBatches 返回的胴体批次含 slaughterhouse
  try {
    const res: any = await distributionApi.getBatches({ current: 1, size: 500 })
    const batches = res?.records || []
    const batch = batches.find((b: any) =>
      b.batchNo === record.batchNo ||
      (record.parentBatchId && b.id === record.parentBatchId)
    )
    if (batch?.slaughterhouse) {
      createForm.origin = batch.slaughterhouse
    }
  } catch { /* 忽略，用户手动输入 */ }
}

function resetForm() {
  Object.assign(createForm, {
    splitBatchId: null, vehicleNo: '', vehicleType: '', refrigeration: '',
    driverName: '', driverPhone: '', origin: '', destination: '',
    plannedDepart: '', plannedArrive: '',
  })
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (editingId.value) {
        await distributionApi.updateTransport(editingId.value, { ...createForm })
        ElMessage.success('运单信息已更新')
        createVisible.value = false
        await fetchTransports()
        const updatedId = editingId.value
        const latest = transportList.value.find((t: any) => t.id === updatedId)
        if (latest && currentTransport.value?.id === updatedId) {
          currentTransport.value = latest
          buildTimeline(latest)
        }
      } else {
        const res: any = await distributionApi.createTransport({ ...createForm })
        ElMessage.success(`运单 ${res.transportNo} 创建成功，状态：待发`)
        createVisible.value = false
        query.pageNum = 1
        await fetchTransports()
        const created = transportList.value.find((t: any) => t.id === res.id)
        if (created) await selectTransport(created)
      }
    } catch { /* 拦截器已提示 */ } finally { submitting.value = false }
  })
}

// ==================== 生命周期 ====================
onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await fetchTransports()
  fetchStoreList()
  // 发货计划与运单在同一页：先看计划（上游决策），再看运单（执行结果）
  fetchPlans()
  // 默认加载第一条运单的监控
  if (transportList.value.length) await selectTransport(transportList.value[0])
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  tempChart?.dispose()
})
</script>

<style lang="scss" scoped>
.page-container { padding: 20px; background: #f5f7fa; border-radius: 4px; }
.card-title { font-size: 16px; font-weight: 700; color: #303133; }
.monitor-anchor { scroll-margin-top: 12px; }

// 发货计划（运单的上游决策单）
.plan-card { margin-bottom: 20px;
  .list-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
  .toolbar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
  .card-sub { flex: 1; margin-left: 10px; font-size: 12px; color: #909399; }
  .sub-text { font-size: 11px; color: #909399; }
}
.plan-detail-sub { margin: 16px 0 8px; font-size: 14px; font-weight: 600; color: #303133; }

// 运单列表
.transport-list-card { margin-bottom: 20px;
  .list-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
  .toolbar { display: flex; gap: 8px; flex-wrap: wrap; }
  .sub-text { font-size: 11px; color: #909399; }
  .route-arrow { display: inline-block; vertical-align: middle; margin: 0 4px; color: #c0c4cc; }
}
:deep(.el-table .current-row) { background-color: #ecf5ff !important; }
.pager { display: flex; justify-content: center; margin-top: 12px; }

// 运输信息条
.transport-info-bar { display: flex; flex-wrap: wrap; gap: 20px; align-items: center; padding: 16px 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 8px; margin-bottom: 20px; }
.info-item { display: flex; flex-direction: column; gap: 2px; }
.info-label { font-size: 11px; color: rgba(255,255,255,0.7); }
.info-value { font-size: 14px; font-weight: 600; color: #fff; }
.info-actions { margin-left: auto; }

// 监控网格
.monitor-grid { display: flex; gap: 20px; margin-bottom: 20px; }
.monitor-card { flex: 1; }
.temp-chart-card { .card-header { display: flex; justify-content: space-between; align-items: center; } }
.chart-container { height: 320px; }
.monitor-sidebar { width: 280px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; }
.current-temp-card { text-align: center; padding: 8px 0; border: 2px solid #e1f3d8;
  &.temp-abnormal { border-color: #fde2e2; }
  :deep(.el-card__body) { padding: 20px 16px; }
}
.current-temp { margin-bottom: 12px; }
.temp-value { font-size: 48px; font-weight: 700; color: #303133; line-height: 1; }
.temp-empty { color: #c0c4cc; }
.temp-unit { font-size: 18px; color: #909399; margin-left: 4px; }
.temp-status { display: flex; align-items: center; justify-content: center; gap: 6px; font-size: 14px; font-weight: 500; }
.input-label { font-size: 12px; color: #909399; display: block; margin-bottom: 4px; }
.form-tip { font-size: 12px; color: #909399; line-height: 1.4; margin-top: 2px; }

// 打卡列表
.checkin-list-card { margin-bottom: 20px; .card-header { display: flex; justify-content: space-between; align-items: center; } }
.record-count { font-size: 12px; color: #909399; }
.temp-abnormal-value { color: #f56c6c; font-weight: 700; }

// 运输时间轴
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
.tt-desc { font-size: 12px; color: #606266; margin-top: 2px; }
.is-pulsing { animation: pulse 1.5s ease-in-out infinite; color: #409eff; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>
