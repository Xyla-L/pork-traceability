import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

/** 后端统一响应结构 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/** 自定义请求配置扩展 */
interface RequestConfig extends AxiosRequestConfig {
  /** 是否跳过全局错误提示，默认 false */
  skipErrorHandler?: boolean
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ===================== 请求拦截器 =====================
service.interceptors.request.use(
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

// ===================== 响应拦截器 =====================
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    if (res.code === 200) {
      return res.data as any
    }

    if (res.code === 10001) {
      // 数据上链中，仍返回 data，仅做提示
      ElMessage.warning('数据上链中')
      return res.data as any
    }

    // 其他错误码
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // HTTP 状态码级别的错误处理
    if (error.response) {
      const { status } = error.response

      if (status === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        window.location.href = '/login'
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error('没有操作权限')
      } else if (status === 404) {
        ElMessage.error('请求的资源不存在')
      } else if (status === 500) {
        ElMessage.error('服务器内部错误')
      } else {
        ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }

    return Promise.reject(error)
  }
)

/** GET 请求 */
export function get<T = any>(url: string, params?: any, config?: RequestConfig): Promise<T> {
  return service.get(url, { params, ...config })
}

/** POST 请求 */
export function post<T = any>(url: string, data?: any, config?: RequestConfig): Promise<T> {
  return service.post(url, data, config)
}

/** PUT 请求 */
export function put<T = any>(url: string, data?: any, config?: RequestConfig): Promise<T> {
  return service.put(url, data, config)
}

/** DELETE 请求 */
export function del<T = any>(url: string, config?: RequestConfig): Promise<T> {
  return service.delete(url, config)
}

export default service