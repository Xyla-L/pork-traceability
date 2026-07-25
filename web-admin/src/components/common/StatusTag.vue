<template>
  <el-tag :type="tagType" :size="size" :effect="effect" disable-transitions>
    {{ displayText }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 状态类型，对应内置映射 */
  type: {
    type: String,
    default: ''
  },
  /** 自定义显示文字，不传则根据 type 自动映射 */
  text: {
    type: String,
    default: ''
  },
  /** 尺寸 */
  size: {
    type: String,
    default: 'default'
  },
  /** 样式风格 */
  effect: {
    type: String,
    default: 'light'
  }
})

/** 状态类型 -> { 颜色类型, 显示文字 } 映射 */
const STATUS_MAP = {
  raising:   { tagType: 'success', label: '在养' },
  slaughtered: { tagType: 'primary', label: '已屠宰' },
  abnormal:  { tagType: 'danger',  label: '异常' },
  pending:   { tagType: 'info',    label: '待审' },
  approved:  { tagType: 'success', label: '已通过' },
  rejected:  { tagType: 'danger',  label: '已驳回' },
  transport: { tagType: 'primary', label: '在途' },
  sold:      { tagType: 'success', label: '已售' },
  expired:   { tagType: 'danger',  label: '已过期' }
}

const tagType = computed(() => {
  return STATUS_MAP[props.type]?.tagType || 'info'
})

const displayText = computed(() => {
  if (props.text) return props.text
  return STATUS_MAP[props.type]?.label || props.type
})
</script>

<style lang="scss" scoped>
// el-tag 样式由 Element Plus 原生支持，此处无需额外样式
</style>
