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
      { path: 'farm/vaccines', name: 'VaccineRecord', component: () => import('@/views/farm/vaccines/VaccineRecord.vue'), meta: { title: '疫苗记录', roles: ['FARMER', 'SUPERVISOR', 'ADMIN'] } },
      { path: 'farm/apply', name: 'ApplyList', component: () => import('@/views/farm/apply/ApplyList.vue'), meta: { title: '出栏审批', roles: ['SUPERVISOR', 'ADMIN'] } },
      { path: 'slaughter/entry', name: 'EntryInspect', component: () => import('@/views/slaughter/entry/EntryInspect.vue'), meta: { title: '入场查验', roles: ['SLAUGHTERER', 'SUPERVISOR', 'ADMIN'] } },
      { path: 'slaughter/inspect', name: 'SlaughterInspect', component: () => import('@/views/slaughter/inspect/SlaughterInspect.vue'), meta: { title: '屠宰检验', roles: ['SLAUGHTERER', 'SUPERVISOR', 'ADMIN'] } },
      { path: 'slaughter/ractopamine', name: 'RactopamineTest', component: () => import('@/views/slaughter/ractopamine/RactopamineTest.vue'), meta: { title: '瘦肉精检测', roles: ['SLAUGHTERER', 'SUPERVISOR', 'ADMIN'] } },
      { path: 'slaughter/stamp', name: 'CarcassStamp', component: () => import('@/views/slaughter/stamp/CarcassStamp.vue'), meta: { title: '检疫盖章', roles: ['SUPERVISOR', 'ADMIN'] } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isLoggedIn) { next('/login') }
  else if (!requiresAuth && authStore.isLoggedIn && to.path === '/login') { next('/admin/dashboard') }
  else { next() }
})

export default router