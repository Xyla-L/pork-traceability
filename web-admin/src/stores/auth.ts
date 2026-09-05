import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, removeToken, getRefreshToken, setRefreshToken, removeRefreshToken } from '@/utils/auth'
import type { UserInfo, RoleType } from '@/types/common'

export const useAuthStore = defineStore(
  'auth',
  () => {
    // ========== State ==========
    const token = ref<string>(getToken() || '')
    const refreshToken = ref<string>(getRefreshToken() || '')
    const user = ref<UserInfo | null>(getStoredUser())
    const permissions = ref<string[]>([])

    // ========== Getters ==========
    const isLoggedIn = computed(() => !!token.value)
    const role = computed<RoleType | null>(() => user.value?.role || null)

    // ========== Actions ==========

    /**
     * 登录
     */
    async function login(username: string, password: string) {
      // 引入 api 模块避免循环依赖
      const { authApi } = await import('@/api/modules/auth')
      const data = await authApi.login({ username, password })
      token.value = data.token
      refreshToken.value = data.refreshToken
      user.value = data.user
      permissions.value = data.user.permissions || []

      setToken(data.token)
      setRefreshToken(data.refreshToken)
      setStoredUser(data.user)
    }

    /**
     * 登出
     */
    async function logout() {
      try {
        const { authApi } = await import('@/api/modules/auth')
        await authApi.logout()
      } catch {
        // 即使后端登出失败，前端也清除状态
      } finally {
        resetState()
      }
    }

    /**
     * 获取当前用户信息
     */
    async function fetchUserInfo() {
      const { authApi } = await import('@/api/modules/auth')
      const data = await authApi.getMe()
      user.value = data
      permissions.value = data.permissions || []
      setStoredUser(data)
    }

    /**
     * 更新当前用户个人信息
     */
    async function updateProfile(data: { realName?: string; phone?: string; email?: string }) {
      const { authApi } = await import('@/api/modules/auth')
      const updated = await authApi.updateProfile(data)
      user.value = { ...user.value, ...updated }
      setStoredUser(user.value)
    }

    /**
     * 修改当前用户密码
     */
    async function updatePassword(data: { oldPassword: string; newPassword: string }) {
      const { authApi } = await import('@/api/modules/auth')
      await authApi.updatePassword(data)
    }

    /**
     * 刷新 Access Token
     */
    async function refreshAccessToken() {
      if (!refreshToken.value) throw new Error('No refresh token')
      const { authApi } = await import('@/api/modules/auth')
      const data = await authApi.refreshToken({ refreshToken: refreshToken.value })
      token.value = data.token
      refreshToken.value = data.refreshToken || refreshToken.value
      setToken(data.token)
      if (data.refreshToken) setRefreshToken(data.refreshToken)
    }

    /**
     * 检查是否拥有指定权限
     */
    function hasPermission(perm: string): boolean {
      if (role.value === 'ADMIN') return true
      return permissions.value.includes(perm)
    }

    /**
     * 检查是否拥有任一角色
     */
    function hasAnyRole(roles: string[]): boolean {
      if (!role.value) return false
      if (roles.includes('*')) return true
      return roles.includes(role.value)
    }

    /**
     * 重置状态
     */
    function resetState() {
      token.value = ''
      refreshToken.value = ''
      user.value = null
      permissions.value = []
      removeToken()
      removeRefreshToken()
      removeStoredUser()
    }

    return {
      token,
      refreshToken,
      user,
      permissions,
      isLoggedIn,
      role,
      login,
      logout,
      fetchUserInfo,
      updateProfile,
      updatePassword,
      refreshAccessToken,
      hasPermission,
      hasAnyRole,
      resetState,
    }
  },
  {
    // 不持久化 token 和 user（由 auth.ts 手动管理），避免双重存储
    persist: false,
  }
)

// ========== localStorage 辅助 (auth.ts 的对等实现) ==========
function getStoredUser(): UserInfo | null {
  const raw = localStorage.getItem('trace_admin_user')
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return null }
}
function setStoredUser(u: UserInfo): void {
  localStorage.setItem('trace_admin_user', JSON.stringify(u))
}
function removeStoredUser(): void {
  localStorage.removeItem('trace_admin_user')
}
