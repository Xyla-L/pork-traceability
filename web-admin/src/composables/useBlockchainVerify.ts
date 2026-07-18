import { ref, onUnmounted } from 'vue'
import { blockchainApi } from '@/api/modules/blockchain'
import type { BlockchainStatus } from '@/types/common'

/**
 * 区块链上链状态轮询 Hook
 *
 * 当 API 返回 code=10001（上链中）时，启动轮询
 * 每 2 秒查询一次，最多 30 次（1 分钟）
 */
export function useBlockchainVerify(bizType: string, bizId: number) {
  const status = ref<BlockchainStatus>('pending')
  const txHash = ref('')
  const blockNumber = ref<number>(0)
  let timer: ReturnType<typeof setInterval> | null = null

  function startPolling() {
    status.value = 'pending'
    let retryCount = 0

    timer = setInterval(async () => {
      try {
        const records = await blockchainApi.getRecords({ bizType, bizId })
        const record = records.records?.[0]
        // status: 0=待确认, 1=已上链, 2=上链失败
        if (record.status === 1) {
          status.value = 'confirmed'
          txHash.value = record.txHash
          blockNumber.value = record.blockNumber
          clearInterval(timer!)
          timer = null
        } else if (record.status === 2 || ++retryCount > 30) {
          status.value = record.status === 2 ? 'failed' : 'pending'
          clearInterval(timer!)
          timer = null
        }
      } catch {
        if (++retryCount > 30) {
          status.value = 'failed'
          clearInterval(timer!)
          timer = null
        }
      }
    }, 2000)
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  onUnmounted(stopPolling)

  return {
    status,
    txHash,
    blockNumber,
    startPolling,
    stopPolling,
  }
}
