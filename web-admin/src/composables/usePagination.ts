import { reactive, ref } from 'vue'
import type { PageQuery } from '@/types/common'

/**
 * 通用分页 Hook
 * 封装 pageNum/pageSize 状态和切换逻辑
 */
export function usePagination(defaultPageSize = 20) {
  const query = reactive<PageQuery>({
    pageNum: 1,
    pageSize: defaultPageSize,
    sortField: 'create_time',
    sortOrder: 'desc',
  })

  const total = ref(0)

  function onPageChange(page: number) {
    query.pageNum = page
  }

  function onSizeChange(size: number) {
    query.pageSize = size
    query.pageNum = 1 // 重置页码
  }

  function resetPage() {
    query.pageNum = 1
    query.pageSize = defaultPageSize
    total.value = 0
  }

  return {
    query,
    total,
    onPageChange,
    onSizeChange,
    resetPage,
  }
}
