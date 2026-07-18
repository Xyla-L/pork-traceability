import { ref } from 'vue'

/**
 * 表格多选 Hook
 */
export function useTableSelection<T = any>(rowKey = 'id') {
  const selectedRows = ref<T[]>([])
  const selectedIds = ref<(string | number)[]>([])

  function onSelectionChange(rows: T[]) {
    selectedRows.value = rows
    selectedIds.value = rows.map((row: any) => row[rowKey])
  }

  function clearSelection() {
    selectedRows.value = []
    selectedIds.value = []
  }

  return {
    selectedRows,
    selectedIds,
    onSelectionChange,
    clearSelection,
  }
}
