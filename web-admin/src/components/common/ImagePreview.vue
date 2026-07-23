<template>
  <el-dialog v-model="visible" :title="'图片预览'" width="600px" append-to-body>
    <div class="image-preview-container">
      <div class="preview-header">
        <span>{{ currentIndex + 1 }} / {{ images.length }}</span>
        <el-button-group>
          <el-button @click="prev" :disabled="currentIndex === 0">上一张</el-button>
          <el-button @click="next" :disabled="currentIndex === images.length - 1">下一张</el-button>
        </el-button-group>
      </div>
      <div class="preview-image">
        <img :src="currentImage" alt="" />
      </div>
      <div class="preview-thumbnails">
        <div
          v-for="(img, index) in images"
          :key="index"
          class="thumbnail"
          :class="{ active: index === currentIndex }"
          @click="currentIndex = index"
        >
          <img :src="img" alt="" />
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  visible: boolean
  images: string[]
  initialIndex?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const currentIndex = ref(0)

watch(() => props.visible, (val) => {
  if (val) {
    currentIndex.value = props.initialIndex || 0
  }
})

watch(() => props.initialIndex, (val) => {
  if (val !== undefined) {
    currentIndex.value = val
  }
})

const currentImage = ref('')

watch(currentIndex, (val) => {
  currentImage.value = props.images[val] || ''
}, { immediate: true })

function prev() {
  if (currentIndex.value > 0) {
    currentIndex.value--
  }
}

function next() {
  if (currentIndex.value < props.images.length - 1) {
    currentIndex.value++
  }
}
</script>

<style lang="scss" scoped>
.image-preview-container {
  .preview-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    font-size: 14px;
    color: #606266;
  }

  .preview-image {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 400px;
    background: #f5f5f5;
    border-radius: 8px;
    overflow: hidden;

    img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
    }
  }

  .preview-thumbnails {
    display: flex;
    gap: 8px;
    margin-top: 16px;
    overflow-x: auto;
    padding-bottom: 8px;

    .thumbnail {
      width: 64px;
      height: 64px;
      border-radius: 4px;
      overflow: hidden;
      border: 2px solid transparent;
      cursor: pointer;

      &.active {
        border-color: #409eff;
      }

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }
  }
}
</style>