<template>
  <teleport to="body">
    <transition name="viewer-fade">
      <div
        v-if="innerVisible"
        class="image-preview-overlay"
        @click.self="handleClose"
      >
        <!-- 工具栏 -->
        <div class="image-preview__toolbar">
          <el-icon class="toolbar-btn" @click="handleZoomIn">
            <ZoomIn />
          </el-icon>
          <el-icon class="toolbar-btn" @click="handleZoomOut">
            <ZoomOut />
          </el-icon>
          <el-icon class="toolbar-btn" @click="handleRotate">
            <RefreshRight />
          </el-icon>
          <span class="toolbar-divider" />
          <span class="toolbar-index">{{ currentIndex + 1 }} / {{ images.length }}</span>
        </div>

        <!-- 上一张 -->
        <el-icon
          v-if="images.length > 1"
          class="image-preview__nav image-preview__nav--prev"
          @click="handlePrev"
        >
          <ArrowLeft />
        </el-icon>

        <!-- 图片容器 -->
        <div class="image-preview__canvas">
          <img
            :src="images[currentIndex]"
            :style="imgStyle"
            class="image-preview__img"
            draggable="false"
          />
        </div>

        <!-- 下一张 -->
        <el-icon
          v-if="images.length > 1"
          class="image-preview__nav image-preview__nav--next"
          @click="handleNext"
        >
          <ArrowRight />
        </el-icon>

        <!-- 关闭按钮 -->
        <el-icon class="image-preview__close" @click="handleClose">
          <Close />
        </el-icon>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ZoomIn, ZoomOut, RefreshRight, ArrowLeft, ArrowRight, Close } from '@element-plus/icons-vue'

const props = defineProps({
  /** 图片列表 */
  images: {
    type: Array,
    default: () => []
  },
  /** 是否显示 */
  visible: {
    type: Boolean,
    default: false
  },
  /** 初始显示索引 */
  initialIndex: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:visible', 'close'])

const innerVisible = ref(false)
const currentIndex = ref(0)
const scale = ref(1)
const rotate = ref(0)

watch(
  () => props.visible,
  (val) => {
    innerVisible.value = val
    if (val) {
      currentIndex.value = props.initialIndex
      scale.value = 1
      rotate.value = 0
    }
  }
)

watch(innerVisible, (val) => {
  if (!val) {
    emit('update:visible', false)
    emit('close')
  }
})

const imgStyle = computed(() => ({
  transform: `scale(${scale.value}) rotate(${rotate.value}deg)`,
  maxWidth: '100%',
  maxHeight: '100%',
  transition: 'transform 0.3s ease'
}))

const handleZoomIn = () => {
  scale.value = Math.min(scale.value + 0.25, 5)
}

const handleZoomOut = () => {
  scale.value = Math.max(scale.value - 0.25, 0.1)
}

const handleRotate = () => {
  rotate.value = (rotate.value + 90) % 360
}

const handlePrev = () => {
  scale.value = 1
  rotate.value = 0
  currentIndex.value = currentIndex.value <= 0
    ? props.images.length - 1
    : currentIndex.value - 1
}

const handleNext = () => {
  scale.value = 1
  rotate.value = 0
  currentIndex.value = currentIndex.value >= props.images.length - 1
    ? 0
    : currentIndex.value + 1
}

const handleClose = () => {
  innerVisible.value = false
}
</script>

<style lang="scss" scoped>
.image-preview-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-preview__toolbar {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 24px;
  padding: 8px 20px;
  z-index: 1;

  .toolbar-btn {
    color: #fff;
    font-size: 20px;
    cursor: pointer;
    opacity: 0.8;
    transition: opacity 0.2s;

    &:hover {
      opacity: 1;
    }
  }

  .toolbar-divider {
    width: 1px;
    height: 16px;
    background: rgba(255, 255, 255, 0.3);
  }

  .toolbar-index {
    color: #fff;
    font-size: 13px;
    user-select: none;
  }
}

.image-preview__canvas {
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 85vw;
  max-height: 85vh;
  overflow: hidden;
}

.image-preview__img {
  object-fit: contain;
  user-select: none;
  pointer-events: none;
}

.image-preview__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  color: #fff;
  font-size: 32px;
  cursor: pointer;
  opacity: 0.6;
  transition: opacity 0.2s;
  z-index: 1;

  &:hover {
    opacity: 1;
  }

  &--prev {
    left: 20px;
  }

  &--next {
    right: 20px;
  }
}

.image-preview__close {
  position: absolute;
  top: 20px;
  right: 20px;
  color: #fff;
  font-size: 28px;
  cursor: pointer;
  opacity: 0.6;
  transition: opacity 0.2s;
  z-index: 1;

  &:hover {
    opacity: 1;
  }
}

// 进入/离开过渡
.viewer-fade-enter-active,
.viewer-fade-leave-active {
  transition: opacity 0.3s ease;
}

.viewer-fade-enter-from,
.viewer-fade-leave-to {
  opacity: 0;
}
</style>