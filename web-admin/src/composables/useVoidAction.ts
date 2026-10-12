import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 作废动作的统一入口。
 *
 * 项目里所有已上链的记录都不再提供「删除」——删掉业务数据，链上的哈希就成了孤儿，
 * 追溯链直接断在那里。正确做法是作废：把记录标记为不再生效，同时留下
 * 「谁、什么时候、为什么」作废的凭据，并单独上链一条 VOID 事件。
 *
 * 因此这里强制收集理由，理由会写进 void_reason 并随作废事件上链。
 */
export interface VoidActionOptions {
  /** 弹窗标题，如「作废检验记录」 */
  title: string
  /** 弹窗正文，说明这次作废的对象与影响 */
  message: string
  /** 确认按钮文案，默认「确认作废」 */
  confirmText?: string
  /** 实际执行作废的请求 */
  onConfirm: (reason: string) => Promise<unknown>
}

/**
 * 弹出理由输入框并执行作废。
 *
 * @returns true=已作废；false=用户取消或请求失败
 */
export async function confirmVoid(options: VoidActionOptions): Promise<boolean> {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(options.message, options.title, {
      confirmButtonText: options.confirmText || '确认作废',
      cancelButtonText: '取消',
      inputPlaceholder: '请填写作废理由（不少于 2 个字），将随记录一并上链',
      inputValidator: (val: string) => (!!val && val.trim().length >= 2) || '请填写不少于 2 个字的作废理由',
      type: 'warning',
    })
    reason = (value || '').trim()
  } catch {
    return false
  }
  try {
    await options.onConfirm(reason)
    ElMessage.success('已作废，作废凭据已上链')
    return true
  } catch (error) {
    console.error('作废失败:', error)
    return false
  }
}
