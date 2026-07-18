import request from '@/utils/request'

export const authApi = {
  /**
   * 登录
   */
  login(data: { username: string; password: string }) {
    return request.post('/auth/login', data)
  },

  /**
   * 登出
   */
  logout() {
    return request.post('/auth/logout')
  },

  /**
   * 刷新 Token
   */
  refreshToken(data: { refreshToken: string }) {
    return request.post('/auth/refresh', data)
  },

  /**
   * 获取当前用户信息
   */
  getMe() {
    return request.get('/auth/me')
  },
}
