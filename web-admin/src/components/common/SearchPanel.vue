<template>
  <div class="search-panel">
    <el-form :model="searchForm" inline>
      <slot />
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'

const props = defineProps<{
  defaultForm?: Record<string, any>
}>()

const emit = defineEmits<{
  (e: 'search', form: Record<string, any>): void
  (e: 'reset'): void
}>()

const searchForm = reactive<Record<string, any>>({ ...props.defaultForm })

function handleSearch() {
  emit('search', { ...searchForm })
}

function handleReset() {
  Object.keys(searchForm).forEach((key) => {
    searchForm[key] = ''
  })
  emit('reset')
}

defineExpose({ searchForm })
</script>

<style lang="scss" scoped>
.search-panel {
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 16px;

  :deep(.el-form-item) {
    margin-bottom: 0;
    margin-right: 16px;
  }
}
</style>