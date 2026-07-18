import { post, get } from '../request'

/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
  captcha?: string
}

/** 登录响应数据 */
export interface LoginResult {
  token: string
  expiresIn: number
}

/** 当前用户信息 */
export interface UserInfo {
  id: number
  username: string
  realName: string
  role: string
  avatar?: string
  phone?: string
  farmId?: number
  farmName?: string
}

/** 用户认证相关 API */
const authApi = {
  /** 登录 */
  login(data: LoginParams) {
    return post<LoginResult>('/auth/login', data)
  },

  /** 登出 */
  logout() {
    return post<null>('/auth/logout')
  },

  /** 获取当前登录用户信息 */
  getMe() {
    return get<UserInfo>('/auth/me')
  },
}

export default authApi