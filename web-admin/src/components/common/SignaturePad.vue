<template>
  <div class="signature-pad">
    <div
      class="signature-pad__canvas-wrap"
      :style="{ width: `${canvasWidth}px`, height: `${canvasHeight}px` }"
    >
      <canvas
        ref="canvasRef"
        :width="canvasWidth * dpr"
        :height="canvasHeight * dpr"
        class="signature-pad__canvas"
        @mousedown="startDraw"
        @mousemove="drawing"
        @mouseup="endDraw"
        @mouseleave="endDraw"
        @touchstart.prevent="startDraw"
        @touchmove.prevent="drawing"
        @touchend.prevent="endDraw"
      />
      <div v-if="isEmpty" class="signature-pad__placeholder">
        请在此处签名
      </div>
    </div>
    <div class="signature-pad__actions">
      <el-button @click="handleClear">清除</el-button>
      <el-button type="primary" :disabled="isEmpty" @click="handleConfirm">
        确认签名
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  /** 画布宽度 */
  width: {
    type: Number,
    default: 400
  },
  /** 画布高度 */
  height: {
    type: Number,
    default: 200
  },
  /** 画笔颜色 */
  penColor: {
    type: String,
    default: '#000'
  }
})

const emit = defineEmits(['confirm'])

const canvasRef = ref(null)
const ctx = ref(null)
const isDrawing = ref(false)
const isEmpty = ref(true)
const dpr = window.devicePixelRatio || 1

const canvasWidth = props.width
const canvasHeight = props.height

let lastPoint = null

/** 获取触摸/鼠标坐标 */
const getPoint = (e) => {
  const canvas = canvasRef.value
  const rect = canvas.getBoundingClientRect()
  const scaleX = canvasWidth / rect.width
  const scaleY = canvasHeight / rect.height

  if (e.touches && e.touches.length > 0) {
    return {
      x: (e.touches[0].clientX - rect.left) * scaleX,
      y: (e.touches[0].clientY - rect.top) * scaleY
    }
  }
  return {
    x: (e.clientX - rect.left) * scaleX,
    y: (e.clientY - rect.top) * scaleY
  }
}

const startDraw = (e) => {
  isDrawing.value = true
  lastPoint = getPoint(e)
  ctx.value.beginPath()
  ctx.value.moveTo(lastPoint.x, lastPoint.y)
}

const drawing = (e) => {
  if (!isDrawing.value) return
  const point = getPoint(e)
  ctx.value.strokeStyle = props.penColor
  ctx.value.lineWidth = 2 * dpr
  ctx.value.lineCap = 'round'
  ctx.value.lineJoin = 'round'
  ctx.value.lineTo(point.x * dpr, point.y * dpr)
  ctx.value.stroke()
  ctx.value.beginPath()
  ctx.value.moveTo(point.x * dpr, point.y * dpr)
  isEmpty.value = false
  lastPoint = point
}

const endDraw = () => {
  if (isDrawing.value) {
    isDrawing.value = false
    ctx.value.beginPath()
    lastPoint = null
  }
}

const handleClear = () => {
  const canvas = canvasRef.value
  ctx.value.clearRect(0, 0, canvas.width, canvas.height)
  isEmpty.value = true
}

const handleConfirm = () => {
  if (isEmpty.value) {
    ElMessage.warning('请先签名')
    return
  }
  const base64 = canvasRef.value.toDataURL('image/png')
  emit('confirm', base64)
}

onMounted(() => {
  nextTick(() => {
    const canvas = canvasRef.value
    if (canvas) {
      ctx.value = canvas.getContext('2d')
      ctx.value.scale(dpr, dpr)
    }
  })
})

onBeforeUnmount(() => {
  handleClear()
})
</script>

<style lang="scss" scoped>
.signature-pad {
  display: inline-block;

  &__canvas-wrap {
    position: relative;
    border: 1px dashed #dcdfe6;
    border-radius: 4px;
    overflow: hidden;
    background: #fff;
    cursor: crosshair;
  }

  &__canvas {
    display: block;
    width: 100%;
    height: 100%;
  }

  &__placeholder {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    color: #c0c4cc;
    font-size: 14px;
    pointer-events: none;
    user-select: none;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 12px;
  }
}
</style>
