<template>
  <div class="search-panel">
    <el-form
      :inline="true"
      :label-width="labelWidth"
      class="search-panel__form"
      @submit.prevent
    >
      <div
        class="search-panel__fields"
        :class="{ 'is-collapsed': isCollapsed }"
      >
        <slot />
      </div>
      <div class="search-panel__actions">
        <el-button type="primary" :icon="Search" @click="handleSearch">
          搜索
        </el-button>
        <el-button :icon="RefreshRight" @click="handleReset">
          重置
        </el-button>
        <el-button
          v-if="hasExpandSlot"
          type="primary"
          link
          @click="toggleCollapse"
        >
          {{ isCollapsed ? '展开' : '收起' }}
          <el-icon :class="{ 'is-reverse': !isCollapsed }">
            <ArrowDown />
          </el-icon>
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, useSlots } from 'vue'
import { Search, RefreshRight, ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  /** 是否折叠（默认折叠） */
  collapsed: {
    type: Boolean,
    default: true
  },
  /** 表单 label 宽度 */
  labelWidth: {
    type: String,
    default: '80px'
  }
})

const emit = defineEmits(['search', 'reset', 'update:collapsed'])

const slots = useSlots()

const isCollapsed = ref(props.collapsed)

const hasExpandSlot = computed(() => {
  return !!slots.expand || !!slots.default
})

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  emit('update:collapsed', isCollapsed.value)
}

const handleSearch = () => {
  emit('search')
}

const handleReset = () => {
  emit('reset')
  isCollapsed.value = true
  emit('update:collapsed', true)
}
</script>

<style lang="scss" scoped>
.search-panel {
  background: #fff;
  border-radius: 4px;
  padding: 18px 20px 0;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);

  &__form {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
  }

  &__fields {
    flex: 1;
    display: flex;
    flex-wrap: wrap;
    gap: 0;
    overflow: hidden;
    transition: max-height 0.3s ease;
    max-height: 80px;

    &.is-collapsed {
      max-height: 80px;

      :deep(.el-form-item) {
        &:nth-child(n + 4) {
          display: none;
        }
      }
    }

    &:not(.is-collapsed) {
      max-height: 500px;
    }

    :deep(.el-form-item) {
      margin-bottom: 18px;
      margin-right: 16px;
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    padding-bottom: 18px;
    white-space: nowrap;

    .el-icon {
      transition: transform 0.3s;

      &.is-reverse {
        transform: rotate(180deg);
      }
    }
  }
}
</style>
