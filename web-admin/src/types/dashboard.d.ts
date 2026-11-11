// ========== 工作台 / 数据看板类型定义 ==========
// 与后端聚合接口 GET /dashboard/overview 对齐。
//
// 工作台按角色返回不同内容：后端给出 statCards + blocks，前端用同一套渲染器渲染，
// 不在前端按角色写分支 —— 那样每加一个角色都要改页面，而且容易和后端口径走散。

export interface StatCardItem {
  key: string
  label: string
  value: number | string
  /** 计量单位：头 / 单 / 件 / 条 / 笔 / 元 / kg */
  unit?: string
}

/** 名称-数量，饼图与柱状图共用 */
export interface NameValueItem {
  name: string
  value: number
}

export interface SankeyNode {
  name: string
  value: number
}

export interface SankeyLink {
  source: string
  target: string
  value: number
}

export interface BlockColumn {
  prop: string
  label: string
  width?: number
}

/**
 * 工作台区块。
 * - sankey：全链路流转（data = { nodes, links }）
 * - pie：占比分布（data = NameValueItem[]）
 * - bar：柱状（data = NameValueItem[]）
 * - barH：横向柱，用于排行榜（data = NameValueItem[]）
 * - table：明细表（columns + rows）
 */
export interface DashboardBlock {
  key: string
  type: 'sankey' | 'pie' | 'bar' | 'barH' | 'table'
  title: string
  /** 占几列：2 = 整行 */
  span: 1 | 2
  unit?: string
  data?: NameValueItem[] | { nodes: SankeyNode[]; links: SankeyLink[] }
  columns?: BlockColumn[]
  rows?: Record<string, any>[]
  /** 表格空态文案，由后端按业务场景给（如「今日暂无入场记录」） */
  emptyText?: string
  link?: { text: string; path: string }
}

export interface TodoItem {
  title: string
  type: string
  time: string
  status: string
}

// 工作台聚合响应
export interface DashboardOverview {
  /** ADMIN | FARMER | SLAUGHTER_OP | DISTRIBUTOR | RETAILER | SUPERVISOR */
  role: string
  roleLabel: string
  /** 数据范围说明，如「全平台」「近 30 天 · 全部门店」 */
  scopeLabel: string
  statCards: StatCardItem[]
  blocks: DashboardBlock[]
  todoList: TodoItem[]
  /** 取数失败的下游服务名：非空时页面提示「部分数据暂不可用」，不把取不到当成 0 */
  degraded: string[]
}
