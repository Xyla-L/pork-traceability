// ========== 顶部通知类型定义 ==========
export interface AppNotification {
  id: number
  title: string
  content: string
  type: 'warning' | 'info' | 'success' | 'error'
  read: boolean
  createTime: string
  /** 指向的业务类型（complaint/transport/receipt/recall/sale/quarantine/slaughter_apply/ractopamine/blockchain），点开按它决定去哪个页面 */
  bizType?: string | null
  /** 指向的业务主键，留作后续精确打开详情；列表定位用 content 里的业务编号 */
  bizId?: number | null
}
