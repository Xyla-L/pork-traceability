<template>
  <el-tag :type="tagType" :size="size" effect="plain">
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  status: number | string
  config?: Record<number | string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }>
  size?: 'large' | 'default' | 'small'
}>(), {
  size: 'small',
})

const defaultConfigs: Record<string, Record<number | string, { label: string; type: string }>> = {
  pig: {
    1: { label: '在养', type: 'success' },
    2: { label: '已出栏', type: 'info' },
    3: { label: '已屠宰', type: 'info' },
    4: { label: '异常', type: 'danger' },
  },
  approval: {
    0: { label: '待审', type: 'warning' },
    1: { label: '通过', type: 'success' },
    2: { label: '驳回', type: 'danger' },
  },
  health: {
    1: { label: '正常', type: 'success' },
    0: { label: '异常', type: 'danger' },
  },
  sale: {
    1: { label: '在售', type: 'success' },
    2: { label: '已售', type: 'info' },
    3: { label: '已过期', type: 'danger' },
    4: { label: '已召回', type: 'warning' },
  },
  qrcode: {
    0: { label: '未激活', type: 'info' },
    1: { label: '在售', type: 'success' },
    2: { label: '已售', type: 'info' },
    3: { label: '已过期', type: 'danger' },
  },
  warning: {
    1: { label: '临期3天', type: 'warning' },
    2: { label: '临期1天', type: 'warning' },
    3: { label: '已过期', type: 'danger' },
  },
  recall: {
    1: { label: '已发布', type: 'warning' },
    2: { label: '执行中', type: 'danger' },
    3: { label: '已完成', type: 'success' },
    4: { label: '已撤销', type: 'info' },
  },
  transport: {
    1: { label: '待发', type: 'info' },
    2: { label: '在途', type: 'warning' },
    3: { label: '已送达', type: 'success' },
    4: { label: '异常', type: 'danger' },
  },
}

const tagType = computed(() => {
  if (props.config) {
    return props.config[props.status]?.type || 'info'
  }
  return defaultConfigs['default']?.[props.status]?.type || 'info'
})

const label = computed(() => {
  if (props.config) {
    return props.config[props.status]?.label || '未知'
  }
  return defaultConfigs['default']?.[props.status]?.label || '未知'
})

defineExpose({ defaultConfigs })
</script>