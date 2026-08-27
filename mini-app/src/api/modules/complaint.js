import request from '../index'
import { USE_MOCK, delay, mockComplaints } from '../mock'

/**
 * 举报接口模块
 */

// 提交举报
export const submitComplaint = async (formData) => {
  if (USE_MOCK) {
    await delay()
    return { reportNo: 'CP' + Date.now().toString().slice(-12) }
  }
  return request('/trace/complaints', {
    method: 'POST',
    data: formData,
  })
}

// 举报列表
export const getComplaintList = async (params) => {
  if (USE_MOCK) {
    await delay()
    const status = params?.status
    const list = status === null || status === undefined
      ? mockComplaints
      : mockComplaints.filter((c) => c.status === status)
    return { list }
  }
  return request('/trace/complaints', { data: params })
}

// 举报详情
export const getComplaintDetail = async (id) => {
  if (USE_MOCK) {
    await delay()
    const found = mockComplaints.find((c) => c.id === Number(id))
    return found || mockComplaints[0]
  }
  return request(`/trace/complaints/${id}`)
}
