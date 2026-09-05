// ========== 工作台 / 数据看板类型定义 ==========
// 与后端聚合接口 GET /dashboard/overview 对齐
// 说明：图标、渐变色等为纯前端展示属性，由 DashboardView 按 key 映射，后端只返回数据。

// 统计卡片（key 用于前端映射图标/渐变色）
export interface StatCardItem {
  key: string
  label: string
  value: number
  trend?: number // 百分比，较昨日/上月，正负代表涨跌
}

// 全链路桑基图
export interface SankeyNode {
  name: string
  value: number
}

export interface SankeyLink {
  source: string
  target: string
  value: number
}

// 通用「名称-数量」分布（临期分布、预警级别分布共用）
export interface NameValueItem {
  name: string
  value: number
}

// 每日上链量
export interface DailyChainTx {
  date: string // 展示用，如 "7/1"
  count: number
}

// 最近预警
export interface RecentWarning {
  productName: string
  batchNo: string
  warningLevel: string // '临期3天' | '临期1天' | '已过期'
  expireDate: string
  storeName: string
}

// 待办事项
export interface TodoItem {
  title: string
  type: string // '出栏审批' | '举报处理' | '检测审核' | '配送签收' | '产品召回'
  time: string
  status: string // '待审批' | '待处理' | '待审核' | '待确认' | '进行中'
}

// 工作台聚合响应
export interface DashboardOverview {
  statCards: StatCardItem[]
  sankey: { nodes: SankeyNode[]; links: SankeyLink[] }
  expireDistribution: NameValueItem[]
  chainTxTrend: DailyChainTx[]
  warningDistribution: NameValueItem[]
  recentWarnings: RecentWarning[]
  todoList: TodoItem[]
}
