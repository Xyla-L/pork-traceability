<template>
  <span class="expire-countdown" :class="statusClass">
    <el-icon v-if="statusClass === 'is-expired'" class="expire-countdown__icon">
      <WarningFilled />
    </el-icon>
    <el-icon v-else-if="statusClass === 'is-warning'" class="expire-countdown__icon">
      <Clock />
    </el-icon>
    <el-icon v-else class="expire-countdown__icon">
      <CircleCheckFilled />
    </el-icon>
    <span class="expire-countdown__text">{{ displayText }}</span>
  </span>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { WarningFilled, Clock, CircleCheckFilled } from '@element-plus/icons-vue'

const props = defineProps({
  /** 到期日期，支持 String 或 Date 对象 */
  expireDate: {
    type: [String, Date],
    required: true
  }
})

const now = ref(Date.now())
let timer = null

/** 计算剩余天数（含小数，向上取整为天数） */
const remainDays = computed(() => {
  const target = new Date(props.expireDate).getTime()
  if (isNaN(target)) return 0
  const diff = target - now.value
  return Math.ceil(diff / (1000 * 60 * 60 * 24))
})

/** 状态样式类 */
const statusClass = computed(() => {
  if (remainDays.value <= 0) return 'is-expired'
  if (remainDays.value <= 3) return 'is-warning'
  return 'is-safe'
})

/** 显示文字 */
const displayText = computed(() => {
  const days = remainDays.value
  if (days <= 0) {
    return '已过期'
  }
  if (days === 1) {
    return '明天到期'
  }
  return `剩余 ${days} 天`
})

onMounted(() => {
  // 每分钟刷新一次
  timer = setInterval(() => {
    now.value = Date.now()
  }, 60 * 1000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style lang="scss" scoped>
.expire-countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;

  &__icon {
    font-size: 15px;
  }

  // 安全 - 绿色（>3天）
  &.is-safe {
    color: #67c23a;
    background: #f0f9eb;
    padding: 2px 10px;
    border-radius: 10px;
    border: 1px solid #e1f3d8;
  }

  // 临期 - 橙色（1-3天）
  &.is-warning {
    color: #e6a23c;
    background: #fdf6ec;
    padding: 2px 10px;
    border-radius: 10px;
    border: 1px solid #faecd8;
  }

  // 已过期 - 红色
  &.is-expired {
    color: #f56c6c;
    background: #fef0f0;
    padding: 2px 10px;
    border-radius: 10px;
    border: 1px solid #fde2e2;
  }
}
</style>
