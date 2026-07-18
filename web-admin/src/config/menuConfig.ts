import type { MenuItem, RoleType } from '@/types/common'

/**
 * 角色可访问菜单映射表
 * 角色: FARMER | SLAUGHTER_OP | DISTRIBUTOR | RETAILER | SUPERVISOR | ADMIN
 *
 * roles: ['*'] 表示所有角色可见
 * 每个菜单项的 roles 定义了哪些角色可以看到该菜单
 */

export const menuConfig: MenuItem[] = [
  // ========== 工作台 ==========
  {
    path: '/admin/dashboard',
    title: '工作台',
    icon: 'Monitor',
    roles: ['*'],
  },

  // ========== 养殖管理 ==========
  {
    path: '/admin/farm',
    title: '养殖管理',
    icon: 'Stamp',
    roles: ['FARMER', 'SUPERVISOR', 'ADMIN'],
    children: [
      {
        path: '/admin/farm/pigs',
        title: '生猪档案',
        roles: ['*'],
      },
      {
        path: '/admin/farm/vaccines',
        title: '疫苗记录',
        roles: ['FARMER', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/farm/apply',
        title: '出栏审批',
        roles: ['SUPERVISOR', 'ADMIN'],
      },
    ],
  },

  // ========== 屠宰管理 ==========
  {
    path: '/admin/slaughter',
    title: '屠宰管理',
    icon: 'KnifeFork',
    roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
    children: [
      {
        path: '/admin/slaughter/entry',
        title: '入场查验',
        roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/slaughter/inspect',
        title: '屠宰检验',
        roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/slaughter/ractopamine',
        title: '瘦肉精检测',
        roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/slaughter/stamp',
        title: '检疫盖章',
        roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
      },
    ],
  },

  // ========== 分割配送 ==========
  {
    path: '/admin/distribution',
    title: '分割配送',
    icon: 'Box',
    roles: ['DISTRIBUTOR', 'SUPERVISOR', 'ADMIN'],
    children: [
      {
        path: '/admin/distribution/batch',
        title: '胴体批次',
        roles: ['DISTRIBUTOR', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/distribution/split',
        title: '分割操作',
        roles: ['DISTRIBUTOR', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/distribution/transport',
        title: '冷链运输',
        roles: ['DISTRIBUTOR', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/distribution/receipt',
        title: '门店签收',
        roles: ['DISTRIBUTOR', 'RETAILER', 'SUPERVISOR', 'ADMIN'],
      },
    ],
  },

  // ========== 销售管理 ==========
  {
    path: '/admin/sales',
    title: '销售管理',
    icon: 'Sell',
    roles: ['RETAILER', 'SUPERVISOR', 'ADMIN'],
    children: [
      {
        path: '/admin/sales/qrcode',
        title: '二维码管理',
        roles: ['RETAILER', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/sales/records',
        title: '销售记录',
        roles: ['RETAILER', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/sales/warnings',
        title: '过期预警',
        roles: ['RETAILER', 'SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/sales/recall',
        title: '产品召回',
        roles: ['SUPERVISOR', 'ADMIN'],
      },
    ],
  },

  // ========== 应急追溯 ==========
  {
    path: '/admin/trace',
    title: '应急追溯',
    icon: 'Link',
    roles: ['SUPERVISOR', 'ADMIN'],
    children: [
      {
        path: '/admin/trace/search',
        title: '追溯查询',
        roles: ['SUPERVISOR', 'ADMIN'],
      },
      {
        path: '/admin/trace/complaints',
        title: '举报管理',
        roles: ['SUPERVISOR', 'ADMIN'],
      },
    ],
  },

  // ========== 系统管理 ==========
  {
    path: '/admin/system',
    title: '系统管理',
    icon: 'Setting',
    roles: ['ADMIN'],
    children: [
      {
        path: '/admin/system/users',
        title: '用户管理',
        roles: ['ADMIN'],
      },
      {
        path: '/admin/system/orgs',
        title: '机构管理',
        roles: ['ADMIN'],
      },
      {
        path: '/admin/system/audit',
        title: '审计日志',
        roles: ['ADMIN'],
      },
    ],
  },
]

/**
 * 根据角色过滤可见菜单
 */
export function filterMenuByRole(menu: MenuItem[], roles: string[]): MenuItem[] {
  return menu
    .filter((item) => {
      // '*' 表示所有角色可见
      if ((item.roles as string[]).includes('*')) return true
      return (item.roles as string[]).some((r) => roles.includes(r))
    })
    .map((item) => {
      if (item.children) {
        const filteredChildren = filterMenuByRole(item.children, roles)
        // 如果没有可见子菜单，隐藏父菜单
        if (filteredChildren.length === 0) return null
        return { ...item, children: filteredChildren }
      }
      return item
    })
    .filter(Boolean) as MenuItem[]
}

/**
 * 获取所有可见路由路径（用于权限判断）
 */
export function getAllVisiblePaths(menu: MenuItem[]): string[] {
  const paths: string[] = []
  for (const item of menu) {
    if (item.children) {
      paths.push(...getAllVisiblePaths(item.children))
    } else {
      paths.push(item.path)
    }
  }
  return paths
}
