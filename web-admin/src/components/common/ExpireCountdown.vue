<template>
  <div class="expire-countdown" :class="levelClass">
    <el-icon :size="16" :color="levelColor">
      <Clock v-if="days > 3" />
      <Timer v-else-if="days > 0" />
      <WarningFilled v-else />
    </el-icon>
    <span class="countdown-text">{{ countdownText }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Clock, Timer, WarningFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  expireDate: string
}>()

const days = computed(() => {
  const expire = new Date(props.expireDate).getTime()
  const now = Date.now()
  const diff = expire - now
  return Math.floor(diff / (1000 * 60 * 60 * 24))
})

const levelClass = computed(() => {
  if (days.value > 3) return 'level-safe'
  if (days.value > 1) return 'level-warning'
  if (days.value > 0) return 'level-danger'
  return 'level-expired'
})

const levelColor = computed(() => {
  if (days.value > 3) return '#67c23a'
  if (days.value > 1) return '#e6a23c'
  if (days.value > 0) return '#f56c6c'
  return '#909399'
})

const countdownText = computed(() => {
  if (days.value > 3) return `剩余 ${days.value} 天`
  if (days.value > 1) return `临期 ${days.value} 天`
  if (days.value === 1) return '临期 1 天'
  if (days.value === 0) return '今日到期'
  return `已过期 ${Math.abs(days.value)} 天`
})
</script>

<style lang="scss" scoped>
.expire-countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;

  &.level-safe {
    background: #f0f9eb;
    color: #67c23a;
  }

  &.level-warning {
    background: #fdf6ec;
    color: #e6a23c;
  }

  &.level-danger {
    background: #fef0f0;
    color: #f56c6c;
  }

  &.level-expired {
    background: #f5f5f5;
    color: #909399;
  }

  .countdown-text {
    font-weight: 500;
  }
}
</style>