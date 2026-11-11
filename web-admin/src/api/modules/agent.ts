import request from '@/utils/request'

/**
 * 智能助手接口。
 *
 * 只读：这里没有、也不应该有「让助手去审批/作废/签收」的方法。
 * 助手能给的是「查到了什么」，不是「替你决定」。
 */
export const agentApi = {
  /** 提问；不传 threadId 即新开会话 */
  chat(data: { threadId?: number; message: string }) {
    return request.post('/agent/chat', data)
  },
  /** 我的会话（按最近更新排序，最多 20 条） */
  threads() {
    return request.get('/agent/threads')
  },
  /** 某个会话的完整消息（含依据快照） */
  messages(threadId: number) {
    return request.get(`/agent/threads/${threadId}/messages`)
  },
  /** 能力范围与运行状态：用于告诉用户「这条是模型写的还是直接取数」 */
  meta() {
    return request.get('/agent/meta')
  },
}
