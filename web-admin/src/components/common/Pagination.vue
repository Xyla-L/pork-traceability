<template>
  <div class="pagination-wrapper">
    <el-pagination
      :current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
      background
    />
  </div>
</template>

<script setup lang="ts">
import type { PageQuery } from '@/types/common'

const props = defineProps<{
  pageNum: number
  pageSize: number
  total: number
}>()

const emit = defineEmits<{
  (e: 'update:pageNum', val: number): void
  (e: 'update:pageSize', val: number): void
  (e: 'change', query: PageQuery): void
}>()

function handleSizeChange(val: number) {
  emit('update:pageSize', val)
  emit('change', { pageNum: 1, pageSize: val })
}

function handleCurrentChange(val: number) {
  emit('update:pageNum', val)
  emit('change', { pageNum: val, pageSize: props.pageSize })
}
</script>

<style lang="scss" scoped>
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}
</style>