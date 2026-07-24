import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'Login', component: () => import('@/views/login/LoginView.vue'), meta: { requiresAuth: false } },
  {
    path: '/admin', name: 'Admin', component: () => import('@/components/layout/AdminLayout.vue'), meta: { requiresAuth: true },
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '工作台', icon: 'Monitor', roles: ['*'] } },
      { path: 'farm/pigs', name: 'PigList', component: () => import('@/views/farm/pigs/PigList.vue'), meta: { title: '生猪档案', icon: 'User', roles: ['*'] } },
      { path: 'farm/vaccines', name: 'VaccineRecord', component: () => import('@/views/farm/vaccines/VaccineRecord.vue'), meta: { title: '疫苗记录', icon: 'Syringe', roles: ['FARMER', 'SUPERVISOR', 'ADMIN'] } },
      { path: 'farm/apply', name: 'ApplyList', component: () => import('@/views/farm/apply/ApplyList.vue'), meta: { title: '出栏审批', icon: 'ClipboardCheck', roles: ['SUPERVISOR', 'ADMIN'] } },
      { path: 'slaughter/entry', name: 'EntryInspect', component: () => import('@/views/slaughter/entry/EntryInspectList.vue'), meta: { title: '入场查验', icon: 'DoorOpen', roles: ['*'] } },
      { path: 'slaughter/inspect', name: 'SlaughterInspect', component: () => import('@/views/slaughter/inspect/SlaughterInspectList.vue'), meta: { title: '屠宰检验', icon: 'Knife', roles: ['*'] } },
      { path: 'slaughter/ractopamine', name: 'RactopamineTest', component: () => import('@/views/slaughter/ractopamine/RactopamineTestList.vue'), meta: { title: '瘦肉精检测', icon: 'FlaskConical', roles: ['*'] } },
      { path: 'slaughter/stamp', name: 'CarcassStamp', component: () => import('@/views/slaughter/stamp/CarcassStampList.vue'), meta: { title: '检疫盖章', icon: 'Stamp', roles: ['*'] } },
      { path: 'distribution/batch', name: 'CarcassBatch', component: () => import('@/views/distribution/batch/CarcassBatchList.vue'), meta: { title: '胴体批次', icon: 'Package', roles: ['*'] } },
      { path: 'distribution/receipt', name: 'StoreReceipt', component: () => import('@/views/distribution/receipt/StoreReceiptList.vue'), meta: { title: '门店签收', icon: 'Receiving', roles: ['*'] } },
      { path: 'sales/qrcode', name: 'QrcodeManage', component: () => import('@/views/sales/qrcode/QrcodeManageList.vue'), meta: { title: '二维码管理', icon: 'QrCode', roles: ['*'] } },
      { path: 'sales/records', name: 'SaleRecords', component: () => import('@/views/sales/record/SaleRecordsList.vue'), meta: { title: '销售记录', icon: 'ShoppingCart', roles: ['*'] } },
      { path: 'sales/warnings', name: 'ExpireWarnings', component: () => import('@/views/sales/warning/ExpireWarningsList.vue'), meta: { title: '过期预警', icon: 'AlertTriangle', roles: ['*'] } },
      { path: 'sales/recall', name: 'RecallManage', component: () => import('@/views/sales/recall/RecallManageList.vue'), meta: { title: '产品召回', icon: 'RotateCcw', roles: ['*'] } },
      { path: 'trace/complaints', name: 'ComplaintManage', component: () => import('@/views/trace/complaint/ComplaintManageList.vue'), meta: { title: '举报管理', icon: 'MessageWarning', roles: ['*'] } },
      { path: 'system/users', name: 'UserManage', component: () => import('@/views/system/user/UserManageList.vue'), meta: { title: '用户管理', icon: 'UserFilled', roles: ['ADMIN'] } },
      { path: 'system/orgs', name: 'OrgManage', component: () => import('@/views/system/org/OrgManageList.vue'), meta: { title: '机构管理', icon: 'Building', roles: ['ADMIN'] } },
      { path: 'system/audit', name: 'AuditLog', component: () => import('@/views/system/audit/AuditLogList.vue'), meta: { title: '审计日志', icon: 'FileSearch', roles: ['ADMIN'] } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isLoggedIn) { next('/login') }
  else if (!requiresAuth && authStore.isLoggedIn && to.path === '/login') { next('/admin/dashboard') }
  else { next() }
})

export default router