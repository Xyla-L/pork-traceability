import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { setupRouterGuards } from './guards'

// ========== 路由配置 ==========
// 全部18个路由 — 先全部用占位组件，后续开发逐步替换

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/login',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/components/layout/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true },
    children: [
      // ========== 工作台 ==========
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '数据看板', icon: 'Monitor', roles: ['*'] },
      },

      // ========== 养殖管理 ==========
      {
        path: 'farm/pigs',
        name: 'PigList',
        component: () => import('@/views/farm/PigList.vue'),
        meta: { title: '生猪档案', icon: 'Stamp', roles: ['*'] },
      },
      {
        path: 'farm/pigs/:id',
        name: 'PigDetail',
        component: () => import('@/views/farm/PigDetail.vue'),
        meta: {
          title: '生猪详情',
          hidden: true,
          roles: ['FARMER', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'farm/vaccines',
        name: 'VaccineRecord',
        component: () => import('@/views/farm/VaccineRecord.vue'),
        meta: {
          title: '疫苗记录',
          icon: 'FirstAidKit',
          roles: ['FARMER', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'farm/apply',
        name: 'ApplyList',
        component: () => import('@/views/farm/ApplyList.vue'),
        meta: {
          title: '出栏审批',
          icon: 'DocumentChecked',
          roles: ['SUPERVISOR', 'ADMIN'],
        },
      },

      // ========== 屠宰管理 ==========
      {
        path: 'slaughter/entry',
        name: 'EntryInspect',
        component: () => import('@/views/slaughter/EntryInspect.vue'),
        meta: {
          title: '入场查验',
          icon: 'Checked',
          roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'slaughter/inspect',
        name: 'SlaughterInspect',
        component: () => import('@/views/slaughter/SlaughterInspect.vue'),
        meta: {
          title: '屠宰检验',
          icon: 'Search',
          roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'slaughter/ractopamine',
        name: 'RactopamineTest',
        component: () => import('@/views/slaughter/RactopamineTest.vue'),
        meta: {
          title: '瘦肉精检测',
          icon: 'Warning',
          roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'slaughter/stamp',
        name: 'CarcassStamp',
        component: () => import('@/views/slaughter/CarcassStamp.vue'),
        meta: {
          title: '检疫盖章',
          icon: 'Stamp',
          roles: ['SLAUGHTER_OP', 'SUPERVISOR', 'ADMIN'],
        },
      },

      // ========== 分割配送 ==========
      {
        path: 'distribution/batch',
        name: 'CarcassBatch',
        component: () => import('@/views/distribution/CarcassBatch.vue'),
        meta: {
          title: '胴体批次',
          icon: 'Box',
          roles: ['DISTRIBUTOR', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'distribution/split',
        name: 'SplitBatch',
        component: () => import('@/views/distribution/SplitBatch.vue'),
        meta: {
          title: '分割操作',
          icon: 'Grid',
          roles: ['DISTRIBUTOR', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'distribution/transport',
        name: 'TransportMonitor',
        component: () => import('@/views/distribution/TransportMonitor.vue'),
        meta: {
          title: '冷链运输',
          icon: 'Van',
          roles: ['DISTRIBUTOR', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'distribution/receipt',
        name: 'StoreReceipt',
        component: () => import('@/views/distribution/StoreReceipt.vue'),
        meta: {
          title: '门店签收',
          icon: 'DocumentAdd',
          roles: ['DISTRIBUTOR', 'RETAILER', 'SUPERVISOR', 'ADMIN'],
        },
      },

      // ========== 销售管理 ==========
      {
        path: 'sales/qrcode',
        name: 'QrcodeManage',
        component: () => import('@/views/sales/QrcodeManage.vue'),
        meta: {
          title: '二维码管理',
          icon: 'PictureFilled',
          roles: ['RETAILER', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'sales/records',
        name: 'SaleRecords',
        component: () => import('@/views/sales/SaleRecords.vue'),
        meta: {
          title: '销售记录',
          icon: 'Sell',
          roles: ['RETAILER', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'sales/warnings',
        name: 'ExpireWarnings',
        component: () => import('@/views/sales/ExpireWarnings.vue'),
        meta: {
          title: '过期预警',
          icon: 'AlarmClock',
          roles: ['RETAILER', 'SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'sales/recall',
        name: 'RecallManage',
        component: () => import('@/views/sales/RecallManage.vue'),
        meta: {
          title: '产品召回',
          icon: 'WarningFilled',
          roles: ['SUPERVISOR', 'ADMIN'],
        },
      },

      // ========== 应急追溯 ==========
      {
        path: 'trace/search',
        name: 'TraceSearch',
        component: () => import('@/views/trace/TraceSearch.vue'),
        meta: {
          title: '追溯查询',
          icon: 'Link',
          roles: ['SUPERVISOR', 'ADMIN'],
        },
      },
      {
        path: 'trace/complaints',
        name: 'ComplaintManage',
        component: () => import('@/views/trace/ComplaintManage.vue'),
        meta: {
          title: '举报管理',
          icon: 'ChatLineRound',
          roles: ['SUPERVISOR', 'ADMIN'],
        },
      },

      // ========== 系统管理 ==========
      {
        path: 'system/users',
        name: 'UserManage',
        component: () => import('@/views/system/UserManage.vue'),
        meta: { title: '用户管理', icon: 'User', roles: ['ADMIN'] },
      },
      {
        path: 'system/orgs',
        name: 'OrgManage',
        component: () => import('@/views/system/OrgManage.vue'),
        meta: { title: '机构管理', icon: 'OfficeBuilding', roles: ['ADMIN'] },
      },
      {
        path: 'system/audit',
        name: 'AuditLog',
        component: () => import('@/views/system/AuditLog.vue'),
        meta: { title: '审计日志', icon: 'List', roles: ['ADMIN'] },
      },
    ],
  },

  // ========== 403 / 404 ==========
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/Forbidden.vue'),
    meta: { title: '无权限' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 注册路由守卫
setupRouterGuards(router)

export default router
