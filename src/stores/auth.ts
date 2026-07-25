import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { post as httpPost } from '@/utils/request'

export const useAuthStore = defineStore('auth', () => {
  const token = ref('')
  const refreshToken = ref('')
  const user = ref<any>(null)
  const permissions = ref<string[]>([])

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')

  interface LoginResult {
    token: string
    refreshToken: string
    user: any
    permissions: string[]
  }

  const login = async (data: { username: string; password: string; captcha?: string }) => {
    const res = await httpPost<LoginResult>('/auth/login', data)
    token.value = res.token
    refreshToken.value = res.refreshToken
    user.value = res.user
    permissions.value = res.permissions || []
    localStorage.setItem('token', token.value)
    localStorage.setItem('refreshToken', refreshToken.value)
    localStorage.setItem('user', JSON.stringify(user.value))
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  const logout = () => {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
    localStorage.removeItem('permissions')
  }

  const hasPermission = (perm: string) => {
    if (!perm) return true
    return permissions.value.includes(perm) || user.value?.role === 'ADMIN'
  }

  const loadFromStorage = () => {
    const savedToken = localStorage.getItem('token')
    if (savedToken) {
      token.value = savedToken
      refreshToken.value = localStorage.getItem('refreshToken') || ''
      const savedUser = localStorage.getItem('user')
      if (savedUser) user.value = JSON.parse(savedUser)
      const savedPermissions = localStorage.getItem('permissions')
      if (savedPermissions) permissions.value = JSON.parse(savedPermissions)
    }
  }

  return { token, refreshToken, user, permissions, isLoggedIn, role, login, logout, hasPermission, loadFromStorage }
})