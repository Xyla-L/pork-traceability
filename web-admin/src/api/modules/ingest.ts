import request from '@/utils/request'

/**
 * 设备接入（B 档自动化录入）管理端接口。
 *
 * 注意：设备上报走 POST /ingest/device/report（用 X-Device-Key 鉴权，不走登录态），
 * 这里是管理端查询/处置待确认队列用的接口，需要登录。
 */
export const ingestApi = {
  /** 待确认队列（status=1 即"待人工处理"，是最需要盯的列表） */
  pageStaging(params: any) {
    return request.get('/ingest/staging', { params })
  },
  /** 单条详情：带原始报文，便于人工比对 */
  getStaging(id: number) {
    return request.get(`/ingest/staging/${id}`)
  },
  /** 人工确认入账（补齐设备拿不到的条件后放行） */
  approveStaging(id: number, data: any) {
    return request.post(`/ingest/staging/${id}/approve`, data)
  },
  /** 人工拒绝（保留原始报文留档，不入业务表） */
  rejectStaging(id: number, data: any) {
    return request.post(`/ingest/staging/${id}/reject`, data)
  },
  /** 设备台账（不含设备密钥，密钥只在服务端保管） */
  listDevices(params?: any) {
    return request.get('/ingest/devices', { params })
  },
  /**
   * 模拟一次设备上报（演示/联调用，需登录）。
   * 服务端按 deviceNo 反查设备并用其自身密钥走完整链路，不绕过鉴权/校验/幂等。
   */
  demoReport(data: {
    deviceNo: string
    /**
     * 这次上报走哪个通道。一台车载终端同时承担「发车确认」与「到站刷卡确认」两个通道
     * （同一台设备、同一个读卡头），所以必须说清这次是哪个动作；不传则由服务端取设备主通道。
     */
    channel?: string
    bizKey?: string
    reportTime?: string
    sourceRef?: string
    data: Record<string, any>
  }) {
    return request.post('/ingest/demo/report', data)
  },
}

/** 接入通道中文名（与后端 IngestChannel 对应） */
/** 设备通道 → 中文名。键顺序即业务发生顺序，用于「接入通道」下拉/筛选的展示次序 */
export const CHANNEL_LABELS: Record<string, string> = {
  TAG: '养殖建档（耳标）',
  VACCINE: '免疫注射',
  QUARANTINE: '产地检疫出证',
  EXIT_APPLY: '出栏称重登记',
  ENTRY: '入场查验',
  RACTOPAMINE: '瘦肉精检测',
  INSPECTION: '屠宰检验（工位终端）',
  STAMP: '胴体自动盖章',
  BATCH: '胴体批次（分拣台）',
  SPLIT: '分割批次（扫码称重）',
  ORDER: '订单转入（商家选购）',
  LOAD: '装车发运（装车口）',
  DEPART: '发车确认（车载终端）',
  ARRIVE: '到达确认（车载终端刷卡）',
  TEMPERATURE: '冷链温度采集',
  RECEIPT: '门店签收',
  SALE: '门店收银',
  SPLIT_SNAPSHOT: '分割车间监控',
}

/** 待确认队列状态：0待处理 1待人工处理 2已入账 3已拒绝 4已降采样丢弃 */

export const INGEST_STATUS_LABELS: Record<number, string> = {
  0: '待处理',
  1: '待人工处理',
  2: '已入账',
  3: '已拒绝',
  4: '已降采样',
}

export const INGEST_STATUS_TAGS: Record<number, string> = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'danger',
  4: 'info',
}

/** 数据来源标签：MANUAL=人工录入 DEVICE=设备自动采集 API=第三方推送 IMPORT=批量导入 */
export const SOURCE_LABELS: Record<string, string> = {
  MANUAL: '人工录入',
  DEVICE: '设备采集',
  API: '第三方推送',
  IMPORT: '批量导入',
}

export const SOURCE_TAGS: Record<string, string> = {
  MANUAL: 'info',
  DEVICE: 'success',
  API: 'warning',
  IMPORT: 'warning',
}

export function sourceLabel(source?: string) {
  if (!source) return SOURCE_LABELS.MANUAL
  return SOURCE_LABELS[source] || source
}

export function sourceTag(source?: string) {
  if (!source) return SOURCE_TAGS.MANUAL
  return SOURCE_TAGS[source] || 'info'
}

/**
 * 「采集设备」列的取值：设备采集时直接给出设备编号（如 INJ-001 / RFID-001），
 * 人工录入等其他来源给出对应中文。
 *
 * 注意：设备只是采集工具，**它不等于操作人**。疫苗由兽医打、查验由查验员做、
 * 签收由收货人签——这些人名字段一律取业务表里的人名，不能拿本函数的值去顶。
 */
export function deviceText(row?: { source?: string; sourceRef?: string }) {
  if (row?.source === 'DEVICE') return row.sourceRef || SOURCE_LABELS.DEVICE
  return sourceLabel(row?.source)
}
