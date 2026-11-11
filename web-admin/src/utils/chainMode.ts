import { ref } from 'vue'
import { blockchainApi } from '@/api/modules/blockchain'

export interface ChainMode {
  mode: 'local' | 'evm'
  /** 是否真的写入了区块链：只有它为 true，页面才能说「区块链已验证」 */
  onChain: boolean
  label: string
  summary: string
  explorerUrl?: string | null
  contractAddress?: string | null
}

/**
 * 兜底按「本地存证」算：
 * 接口挂了、还没回来时，页面顶多说得保守一点，绝不能凭空显示「区块链已验证」。
 */
const FALLBACK: ChainMode = {
  mode: 'local',
  onChain: false,
  label: '本地存证账本',
  summary: '存证记录保存在平台数据库内，未写入区块链',
}

export const chainMode = ref<ChainMode>(FALLBACK)
let loaded = false
let pending: Promise<ChainMode> | null = null

/** 取一次存证模式并缓存；多个页面同时进入时共用同一个请求 */
export function loadChainMode(force = false): Promise<ChainMode> {
  if (!force && loaded) return Promise.resolve(chainMode.value)
  if (!force && pending) return pending
  pending = blockchainApi.getMode()
    .then((res: any) => {
      const data = (res?.data ?? res) as ChainMode
      if (!data?.mode) return FALLBACK
      chainMode.value = {
        mode: data.mode === 'evm' ? 'evm' : 'local',
        onChain: data.mode === 'evm',
        label: data.label || FALLBACK.label,
        summary: data.summary || FALLBACK.summary,
        explorerUrl: data.explorerUrl || null,
        contractAddress: data.contractAddress || null,
      }
      loaded = true
      return chainMode.value
    })
    .catch(() => FALLBACK)
    .finally(() => { pending = null })
  return pending
}

/** 有区块浏览器时给出这笔交易的可点链接；本地账本模式永远返回 null */
export function explorerTxUrl(mode: ChainMode | null | undefined, txHash?: string | null): string | null {
  if (!mode?.onChain || !mode.explorerUrl || !txHash) return null
  const base = mode.explorerUrl.replace(/\/+$/, '')
  return `${base}/tx/${txHash}`
}
