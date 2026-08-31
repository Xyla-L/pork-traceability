import request from '@/utils/request'

// ========== 系统管理接口 ==========
// 说明：后端 9 个服务中无独立 system-service，以下接口建议由 auth-service 承载
// （网关将 /system/** 路由至 auth-service），或新增 system-service。
// 数据表：用户复用 sys_user（后端设计文档已定义）；机构需补充 sys_org 表（见下方字段说明）。
//
// sys_org 字段建议：id / parent_id / type(farm|slaughter|distribution|retail|supervisor)
//                   / name / manager / phone / address / remark / create_time
// 注意：前端机构树组件使用 el-tree，label 字段即后端 name，联调时做 name→label 映射。

export const systemUserApi = {
  // ========== 用户管理 ==========
  list(params?: any) {
    return request.get('/system/users', { params })
  },
  detail(id: number) {
    return request.get(`/system/users/${id}`)
  },
  create(data: any) {
    return request.post('/system/users', data)
  },
  update(id: number, data: any) {
    return request.put(`/system/users/${id}`, data)
  },
  remove(id: number) {
    return request.delete(`/system/users/${id}`)
  },
  // 重置密码（设为默认密码）
  resetPassword(id: number) {
    return request.put(`/system/users/${id}/reset-password`)
  },
  // 启用 / 禁用
  toggleStatus(id: number, status: number) {
    return request.put(`/system/users/${id}/status`, { status })
  },
}

export const systemOrgApi = {
  // ========== 机构管理 ==========
  tree() {
    return request.get('/system/orgs/tree')
  },
  create(data: any) {
    return request.post('/system/orgs', data)
  },
  update(id: number, data: any) {
    return request.put(`/system/orgs/${id}`, data)
  },
  remove(id: number) {
    return request.delete(`/system/orgs/${id}`)
  },
}
