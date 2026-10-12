import request from '@/utils/request'

// ========== 屠宰管理接口 ==========
// 路径以 Web 管理端页面实际调用为准（entries / inspections / ractopamine / stamps）

export const slaughterApi = {
  // ========== 入场查验 ==========
  // 人工新增接口已删除：入场查验为设备独占环节，数据只接受闸口设备通道上报
  getEntries(params?: any) {
    return request.get('/slaughter/entries', { params })
  },
  updateEntry(id: number, data: any) {
    return request.put(`/slaughter/entries/${id}`, data)
  },

  // ========== 屠宰检验 ==========
  // 人工新增接口已删除：屠宰检验为设备独占环节，数据只接受工位终端通道上报
  getInspections(params?: any) {
    return request.get('/slaughter/inspections', { params })
  },
  updateInspection(id: number, data: any) {
    return request.put(`/slaughter/inspections/${id}`, data)
  },

  // ========== 瘦肉精检测 ==========
  // 人工新增接口已删除：瘦肉精检测为设备独占环节，数据只接受读数仪通道上报
  getRactopamine(params?: any) {
    return request.get('/slaughter/ractopamine', { params })
  },
  updateRactopamine(id: number, data: any) {
    return request.put(`/slaughter/ractopamine/${id}`, data)
  },

  // ========== 检疫盖章 ==========
  // 人工新增接口已删除：胴体盖章为设备独占环节，数据只接受自动盖章机通道上报
  getStamps(params?: any) {
    return request.get('/slaughter/stamps', { params })
  },
  updateStamp(id: number, data: any) {
    return request.put(`/slaughter/stamps/${id}`, data)
  },

  // ========== 屠宰场 ==========
  getSlaughterhouses(params?: any) {
    return request.get('/slaughter/slaughterhouses', { params })
  },

  // ========== 作废（原「删除」入口已移除）==========
  // 已上链记录不提供物理删除：删掉记录会让链上哈希成为孤儿，追溯链直接断在这里。
  // 作废必须填理由，理由与实名操作人随作废事件一并上链，事后可查「这条为什么不算数了」。
  voidEntry(id: number, reason: string) {
    return request.post(`/slaughter/entries/${id}/void`, { reason })
  },
  voidInspection(id: number, reason: string) {
    return request.post(`/slaughter/inspections/${id}/void`, { reason })
  },
  voidRactopamine(id: number, reason: string) {
    return request.post(`/slaughter/ractopamine/${id}/void`, { reason })
  },
  voidStamp(id: number, reason: string) {
    return request.post(`/slaughter/stamps/${id}/void`, { reason })
  },
}
