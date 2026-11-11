<template>
  <div class="page-container">
    <!-- ========== 无害化处理中心：处理签收 ==========
         命名口径（2026-11-04）：冷链中心 → 无害化处理中心这一段叫「处理运输」，到站签收叫「处理签收」。
         这里只有「确认签收」一个动作，没有拒收 —— 货在门店就被拒收或被召回，这一段运输的使命
         就是把它送进无害化处理。核对数量重量无误后确认，签收即链条终结。
         **两种处置单都要列**（2026-11-05 修）：拒收回运（DISPOSAL）只有一站，召回收运（RECALL）
         是「门店…→ 无害化处理中心」，终点站仍是这里。老逻辑只查 DISPOSAL，导致召回货送到门口
         却在这张表里找不到对应的处理运单，签收签不掉、召回单永远挂在执行中。 -->
    <el-card>
      <div class="list-header">
        <span class="card-title">处理运单</span>
      </div>
      <el-table :data="legs" v-loading="loading" border stripe size="small" style="margin-top: 8px">
        <el-table-column prop="transportNo" label="运单号" width="150" />
        <el-table-column label="运单类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.transportKind === 'RECALL' ? 'warning' : 'info'" size="small">
              {{ row.transportKind === 'RECALL' ? '召回收运' : '拒收回运' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="vehicleNo" label="车牌" width="110" />
        <el-table-column prop="driverName" label="司机" width="100">
          <template #default="{ row }">{{ row.driverName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="origin" label="起运" min-width="130" show-overflow-tooltip />
        <el-table-column label="载货" width="130" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.cargoDetail" placement="top">
              <template #content><div style="white-space: pre-line">{{ row.cargoDetail }}</div></template>
              <span class="cargo-cell">{{ row.cargoText }}</span>
            </el-tooltip>
            <span v-else class="sub-text">{{ row.cargoText || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="到货进度" width="110" align="center">
          <template #default="{ row }">
            {{ row.arrivedStopCount || 0 }}/{{ row.stopCount || 0 }} 站
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 3" link type="success" @click="confirm(row)">确认签收</el-button>
            <span v-else-if="row.status === 4" class="sub-text">已签收 · 链条终结</span>
            <span v-else class="sub-text">等待到货</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !legs.length" description="当前没有待签收的处置单：拒收回运单与召回收运单到货后才会出现在这里" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { EpTagType } from '@/types/common'
import { distributionApi } from '@/api/modules/distribution'

const legs = ref<any[]>([])
const loading = ref(false)

const statusTag = (s: number): EpTagType =>
  (({ 1: 'info', 2: 'warning', 3: 'primary', 4: 'success' } as Record<number, EpTagType>)[s] || 'info')
const statusLabel = (row: any) =>
  ({ 1: '待发车', 2: '运输中', 3: '待确认', 4: '已签收' } as Record<number, string>)[row.status] || '未知'

async function load() {
  loading.value = true
  try {
    // 两种处置单一起取：缺省 kind 只回 NORMAL，所以必须显式点名，逗号多值后端已支持
    const res: any = await distributionApi.getTransports({ kind: 'DISPOSAL,RECALL', pageNum: 1, pageSize: 50 })
    // 拦截器已解包到 data 本体：res 就是分页对象（再 .data 一次会永远拿到空列表）
    legs.value = res?.records || res?.data?.records || []
    await fillCargo(legs.value)
  } finally { loading.value = false }
}

/**
 * 签收前要「核对数量重量」，所以列表得说清这趟车装了什么。
 * <p>
 * 两类处置单的装载明细在两处：拒收回运是 leg-items（逐条拒收货），召回收运是 recall items
 * （逐条「门店 × 批次」召回货）。取不到就不显示（宁可空着，也不编一个 0 批 0 公斤）。
 */
async function fillCargo(rows: any[]) {
  await Promise.all(rows.map(async (row) => {
    try {
      const raw: any = row.transportKind === 'RECALL'
        ? await distributionApi.getRecallItems(row.id)
        : await distributionApi.getTransportLegItems(row.id)
      const list: any[] = Array.isArray(raw) ? raw : (raw?.data || [])
      if (!list.length) { row.cargoText = '—'; return }
      const weight = list.reduce((s: number, i: any) => s + Number(i.weightKg || 0), 0)
      row.cargoText = `${list.length} 件 · ${weight.toFixed(2)} kg`
      row.cargoDetail = list.map((i: any) =>
        `${i.storeName || '—'} ${i.batchNo || ''} ${Number(i.weightKg || 0).toFixed(2)} kg`).join('\n')
    } catch {
      row.cargoText = ''
    }
  }))
}

// 签收成功不弹提示：签完刷新列表，状态直接变「已签收 · 链条终结」，结果就摆在行上。
async function confirm(row: any) {
  await distributionApi.disposalConfirm(row.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.card-title { font-weight: 600; }
.list-header { display: flex; align-items: center; }
.sub-text { color: #909399; font-size: 12px; }
.cargo-cell { border-bottom: 1px dashed #c0c4cc; cursor: help; }
</style>
