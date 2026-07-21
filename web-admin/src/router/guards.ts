import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const WHITE_LIST = ['/login', '/403', '/404']

/**
 * 设置路由守卫
 * 1. 登录验证 — Token 过期跳登录页
 * 2. 权限检查 — 无权限跳 403
 */
export function setupRouterGuards(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    const authStore = useAuthStore()

    // 白名单路由直接放行
    if (WHITE_LIST.some((path) => to.path.startsWith(path))) {
      return next()
    }

    // ========== 1. 登录验证 ==========
    if (!authStore.token) {
      ElMessage.warning('请先登录')
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }

    // 如果已有 token 但无用户信息，拉取用户信息
    if (!authStore.user) {
      try {
        await authStore.fetchUserInfo()
      } catch {
        // token 无效，清除并跳转登录
        authStore.logout()
        return next({ path: '/login', query: { redirect: to.fullPath } })
      }
    }

    // ========== 2. 权限检查 ==========
    const requiredRoles = to.meta?.roles as string[] | undefined
    if (requiredRoles && !requiredRoles.includes('*')) {
      const hasPermission = authStore.hasAnyRole(requiredRoles)
      if (!hasPermission) {
        ElMessage.error('无权限访问该页面')
        return next('/403')
      }
    }

    next()
  })

  // 路由后置守卫 — 设置页面标题
  router.afterEach((to) => {
    const title = (to.meta?.title as string) || '猪肉溯源管理平台'
    document.title = `${title} - 猪肉溯源管理平台`
  })
}
