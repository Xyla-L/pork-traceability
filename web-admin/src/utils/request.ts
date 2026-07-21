import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import type { ApiResponse } from '@/types/common'

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json; charset=UTF-8',
  },
})

// ========== 请求拦截器 ==========
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// ========== 响应拦截器 ==========
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { code, message, data } = response.data

    if (code === 200) {
      return data as any
    }

    // 上链中 (10001) — 不是错误
    if (code === 10001) {
      ElMessage.warning(message || '数据上链中，请稍后查看')
      return data as any
    }

    // 上链失败
    if (code === 10002) {
      ElMessage.error(message || '上链失败，请重试')
      return Promise.reject(new Error(message))
    }

    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  (error) => {
    const { response } = error

    if (response?.status === 401) {
      const authStore = useAuthStore()
      if (authStore.refreshToken) {
        return authStore
          .refreshAccessToken()
          .then(() => {
            const config = error.config
            config.headers.Authorization = `Bearer ${authStore.token}`
            return instance(config)
          })
          .catch(() => {
            authStore.logout()
            router.push('/login')
          })
      }
      authStore.logout()
      router.push('/login')
      return Promise.reject(error)
    }

    if (response?.status === 403) {
      ElMessage.error('无权限执行该操作')
      return Promise.reject(error)
    }

    if (response?.status === 500) {
      ElMessage.error('服务器内部错误，请稍后重试')
      return Promise.reject(error)
    }

    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

/**
 * 类型安全的请求封装
 * 覆盖 Axios 实例的返回值类型，直接返回 ApiResponse.data
 */
const request = {
  get<T = any>(url: string, config?: any): Promise<T> {
    return instance.get(url, config) as any
  },
  post<T = any>(url: string, data?: any, config?: any): Promise<T> {
    return instance.post(url, data, config) as any
  },
  put<T = any>(url: string, data?: any, config?: any): Promise<T> {
    return instance.put(url, data, config) as any
  },
  delete<T = any>(url: string, config?: any): Promise<T> {
    return instance.delete(url, config) as any
  },
}

export { instance as axiosInstance }
export default request
