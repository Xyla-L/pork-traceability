<template>
  <div class="apply-filter">
    <div class="tab-bar">
      <div
        v-for="tab in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ active: modelValue === tab.value }"
        @click="handleTabChange(tab.value)"
      >
        <span class="tab-label">{{ tab.label }}</span>
        <span v-if="tab.count !== undefined" class="tab-count">
          ({{ tab.count }})
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0
  },
  counts: {
    type: Object,
    default: () => ({
      0: 0,
      1: 0,
      2: 0
    })
  }
})

const emit = defineEmits(['update:modelValue'])

const tabs = computed(() => [
  {
    label: '待审批',
    value: 0,
    count: props.counts[0] ?? 0
  },
  {
    label: '已通过',
    value: 1,
    count: props.counts[1] ?? 0
  },
  {
    label: '已驳回',
    value: 2,
    count: props.counts[2] ?? 0
  }
])

const handleTabChange = (value) => {
  emit('update:modelValue', value)
}
</script>

<style lang="scss" scoped>
.apply-filter {
  margin-bottom: 16px;

  .tab-bar {
    display: flex;
    gap: 0;
    border-bottom: 2px solid #ebeef5;
  }

  .tab-item {
    position: relative;
    padding: 12px 24px;
    cursor: pointer;
    font-size: 14px;
    color: #606266;
    transition: all 0.3s;
    user-select: none;

    &:hover {
      color: #409eff;
    }

    &.active {
      color: #409eff;
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        bottom: -2px;
        left: 0;
        right: 0;
        height: 2px;
        background-color: #409eff;
        border-radius: 1px;
      }
    }

    .tab-count {
      margin-left: 4px;
      font-size: 12px;
      color: #909399;
    }

    &.active .tab-count {
      color: #409eff;
    }
  }
}
</style>
