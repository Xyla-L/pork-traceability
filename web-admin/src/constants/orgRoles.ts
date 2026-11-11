/**
 * 机构分类的唯一口径：机构类型（sys_org.type）↔ 角色域（sys_user.role）。
 *
 * 机构本身没有角色，它承载的是一类角色 —— 所以「机构管理」按角色域分组，
 * 「用户管理」选机构时按同一口径分组。两处共用这一份，避免一边写「养殖场」
 * 一边写「养殖人员」各说一套。
 */

export type OrgType = 'farm' | 'slaughter' | 'distribution' | 'retail' | 'supervisor' | 'disposal'

export interface OrgRoleGroup {
  /** 机构类型取值（sys_org.type） */
  type: OrgType
  /** 该类型对应的角色域（sys_user.role） */
  role: string
  /** 机构类型名 */
  orgLabel: string
  /** 角色名，与 sys_role.role_name 一致 */
  roleLabel: string
  /** 主色，沿用各环节既有配色 */
  color: string
}

/** 顺序即业务链顺序：养殖 → 屠宰 → 配送 → 零售，监管单列 */
export const ORG_ROLE_GROUPS: OrgRoleGroup[] = [
  { type: 'farm', role: 'FARMER', orgLabel: '养殖场', roleLabel: '养殖人员', color: '#67c23a' },
  { type: 'slaughter', role: 'SLAUGHTER_OP', orgLabel: '屠宰场', roleLabel: '屠宰人员', color: '#f56c6c' },
  { type: 'distribution', role: 'DISTRIBUTOR', orgLabel: '配送中心', roleLabel: '配送人员', color: '#409eff' },
  { type: 'retail', role: 'RETAILER', orgLabel: '零售门店', roleLabel: '零售人员', color: '#e6a23c' },
  { type: 'supervisor', role: 'SUPERVISOR', orgLabel: '监管机构', roleLabel: '监管人员', color: '#909399' },
  // 拒收回运链的终点：拒收货经冷链中心回运后集中无害化处理（2026-11-02）
  { type: 'disposal', role: 'DISPOSAL', orgLabel: '无害化处理中心', roleLabel: '无害化处理人员', color: '#8d6e63' },
]

const BY_TYPE: Record<string, OrgRoleGroup> = Object.fromEntries(ORG_ROLE_GROUPS.map((g) => [g.type, g]))

/** 机构类型 → 角色域；未知类型返回 undefined（调用方自行兜底，不编造） */
export function orgRoleOf(type?: string | null): OrgRoleGroup | undefined {
  return type ? BY_TYPE[type] : undefined
}

/** 机构类型的中文名 */
export function orgTypeLabel(type?: string | null): string {
  return orgRoleOf(type)?.orgLabel || (type || '--')
}

/** 角色域的中文名（按角色编码查，不按机构类型查） */
export function orgRoleLabel(role?: string | null): string {
  return ORG_ROLE_GROUPS.find((g) => g.role === role)?.roleLabel || (role || '--')
}

/** 该机构类型下的账号角色编码 */
export function roleOfOrgType(type?: string | null): string {
  return orgRoleOf(type)?.role || ''
}

/** 角色编码对应的机构类型；ADMIN 不绑环节，返回空串表示不限制 */
export function orgTypeOfRole(role?: string | null): string {
  return ORG_ROLE_GROUPS.find((g) => g.role === role)?.type || ''
}

/** 角色下拉选项（含系统管理员，管理员不对应任何机构类型） */
export const ROLE_OPTIONS = [
  ...ORG_ROLE_GROUPS.map((g) => ({ value: g.role, label: g.roleLabel })),
  { value: 'ADMIN', label: '系统管理员' },
]

/** 角色中文名，含 ADMIN */
export function roleLabelOf(role?: string | null): string {
  return ROLE_OPTIONS.find((r) => r.value === role)?.label || (role || '--')
}
