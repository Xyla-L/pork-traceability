<template>
  <div class="empty-state">
    <img
      v-if="imageSrc"
      :src="imageSrc"
      alt="暂无数据"
      class="empty-state__image"
    />
    <div v-else class="empty-state__image-empty">
      <el-icon class="empty-state__icon">
        <Box />
      </el-icon>
    </div>
    <p class="empty-state__description">{{ description }}</p>
    <slot name="action" />
  </div>
</template>

<script setup>
import { Box } from '@element-plus/icons-vue'

defineProps({
  /** 描述文字 */
  description: {
    type: String,
    default: '暂无数据'
  },
  /** 自定义图片地址 */
  imageSrc: {
    type: String,
    default: ''
  }
})
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  box-sizing: border-box;

  &__image {
    width: 160px;
    height: 120px;
    object-fit: contain;
    margin-bottom: 16px;
    user-select: none;
  }

  &__image-empty {
    width: 120px;
    height: 120px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 16px;
  }

  &__icon {
    font-size: 80px;
    color: #dcdfe6;
  }

  &__description {
    margin: 0;
    font-size: 14px;
    color: #909399;
    line-height: 1.6;
  }
}
</style>