<template>
  <div class="pagination-wrapper">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="currentPageSize"
      :page-sizes="pageSizes"
      :total="total"
      :background="true"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 数据总条数（必填） */
  total: {
    type: Number,
    required: true,
    default: 0
  },
  /** 当前页码 */
  page: {
    type: Number,
    default: 1
  },
  /** 每页条数 */
  pageSize: {
    type: Number,
    default: 10
  },
  /** 每页条数选项 */
  pageSizes: {
    type: Array,
    default: () => [10, 20, 50, 100]
  }
})

const emit = defineEmits(['update:page', 'update:pageSize', 'change'])

const currentPage = computed({
  get: () => props.page,
  set: (val) => emit('update:page', val)
})

const currentPageSize = computed({
  get: () => props.pageSize,
  set: (val) => emit('update:pageSize', val)
})

const handleSizeChange = (size) => {
  emit('update:pageSize', size)
  emit('update:page', 1)
  emit('change', { page: 1, pageSize: size })
}

const handleCurrentChange = (page) => {
  emit('update:page', page)
  emit('change', { page, pageSize: props.pageSize })
}
</script>

<style lang="scss" scoped>
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 16px 0 8px;

  :deep(.el-pagination) {
    .el-pagination__total {
      margin-right: 16px;
    }

    .el-pagination__sizes {
      margin-right: 16px;
    }

    .el-pagination__jump {
      margin-left: 16px;
    }
  }
}
</style>