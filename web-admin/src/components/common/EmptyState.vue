<template>
  <div class="empty-state">
    <div class="empty-icon">
      <el-icon :size="size" color="#c0c4cc">
        <component :is="icon" />
      </el-icon>
    </div>
    <p class="empty-text">{{ text }}</p>
    <p v-if="tip" class="empty-tip">{{ tip }}</p>
    <el-button v-if="showButton" type="primary" @click="$emit('action')">
      {{ buttonText }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { PictureFilled, Document, Search, ShoppingCart, Bell } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  type?: 'data' | 'search' | 'cart' | 'notification' | 'custom'
  text?: string
  tip?: string
  showButton?: boolean
  buttonText?: string
  size?: number
}>(), {
  type: 'data',
  text: '暂无数据',
  tip: '',
  showButton: false,
  buttonText: '点击添加',
  size: 64,
})

defineEmits<{
  (e: 'action'): void
}>()

const iconMap = {
  data: PictureFilled,
  search: Search,
  cart: ShoppingCart,
  notification: Bell,
  custom: Document,
}

const icon = iconMap[props.type] || iconMap.data
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;

  .empty-icon {
    margin-bottom: 16px;
  }

  .empty-text {
    font-size: 14px;
    color: #909399;
    margin-bottom: 8px;
  }

  .empty-tip {
    font-size: 12px;
    color: #c0c4cc;
    margin-bottom: 24px;
  }
}
</style>