// ========== 统一响应体 ==========
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp: number
}

// ========== 分页请求 ==========
export interface PageQuery {
  pageNum: number
  pageSize: number
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

// ========== 分页响应 ==========
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

// ========== 用户信息 ==========
export interface UserInfo {
  id: number
  username: string
  realName: string
  phone: string
  orgId: number
  orgName: string
  role: RoleType
  status: number
  lastLoginTime: string
  permissions: string[]
}

// ========== 角色类型 ==========
export type RoleType =
  | 'FARMER'
  | 'SLAUGHTER_OP'
  | 'DISTRIBUTOR'
  | 'RETAILER'
  | 'SUPERVISOR'
  | 'ADMIN'

// ========== 菜单配置 ==========
export interface MenuItem {
  path: string
  title: string
  icon?: string
  roles: RoleType[] | ['*']
  children?: MenuItem[]
}

// ========== 区块链状态 ==========
export type BlockchainStatus = 'confirmed' | 'pending' | 'failed' | 'none'

export interface BlockchainRecord {
  id: number
  bizType: string
  bizId: number
  contentHash: string
  txHash: string
  blockNumber: number
  chainTime: string
  status: number
}

// ========== 文件信息 ==========
export interface FileInfo {
  fileId: string
  sha256: string
  url: string
  originalName: string
}
