<template>
  <div class="signature-pad">
    <canvas
      ref="canvasRef"
      :width="width"
      :height="height"
      @mousedown="startDrawing"
      @mousemove="draw"
      @mouseup="stopDrawing"
      @mouseleave="stopDrawing"
      @touchstart.prevent="handleTouchStart"
      @touchmove.prevent="handleTouchMove"
      @touchend.prevent="stopDrawing"
    />
    <div class="signature-actions">
      <el-button size="small" @click="clear">清除</el-button>
      <el-button size="small" type="primary" @click="save">保存签名</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const props = withDefaults(defineProps<{
  width?: number
  height?: number
}>(), {
  width: 400,
  height: 150,
})

const emit = defineEmits<{
  (e: 'save', dataUrl: string): void
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const isDrawing = ref(false)
const ctx = ref<CanvasRenderingContext2D | null>(null)
const lastPos = ref({ x: 0, y: 0 })

onMounted(() => {
  initCanvas()
})

function initCanvas() {
  if (!canvasRef.value) return
  ctx.value = canvasRef.value.getContext('2d')
  if (!ctx.value) return

  ctx.value.strokeStyle = '#303133'
  ctx.value.lineWidth = 2
  ctx.value.lineCap = 'round'
  ctx.value.lineJoin = 'round'

  ctx.value.fillStyle = '#fff'
  ctx.value.fillRect(0, 0, props.width, props.height)

  ctx.value.strokeStyle = '#e4e7ed'
  ctx.value.setLineDash([5, 5])
  ctx.value.strokeRect(1, 1, props.width - 2, props.height - 2)
  ctx.value.setLineDash([])
}

function getPos(e: MouseEvent | Touch) {
  if (!canvasRef.value) return { x: 0, y: 0 }
  const rect = canvasRef.value.getBoundingClientRect()
  return {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top,
  }
}

function startDrawing(e: MouseEvent) {
  isDrawing.value = true
  const pos = getPos(e)
  lastPos.value = pos
}

function draw(e: MouseEvent) {
  if (!isDrawing.value || !ctx.value || !canvasRef.value) return

  const pos = getPos(e)

  ctx.value.beginPath()
  ctx.value.moveTo(lastPos.value.x, lastPos.value.y)
  ctx.value.lineTo(pos.x, pos.y)
  ctx.value.stroke()

  lastPos.value = pos
}

function stopDrawing() {
  isDrawing.value = false
}

function handleTouchStart(e: TouchEvent) {
  if (!e.touches[0]) return
  isDrawing.value = true
  const pos = getPos(e.touches[0])
  lastPos.value = pos
}

function handleTouchMove(e: TouchEvent) {
  if (!isDrawing.value || !e.touches[0] || !ctx.value || !canvasRef.value) return

  const pos = getPos(e.touches[0])

  ctx.value.beginPath()
  ctx.value.moveTo(lastPos.value.x, lastPos.value.y)
  ctx.value.lineTo(pos.x, pos.y)
  ctx.value.stroke()

  lastPos.value = pos
}

function clear() {
  initCanvas()
}

function save() {
  if (!canvasRef.value) return
  const dataUrl = canvasRef.value.toDataURL('image/png')
  emit('save', dataUrl)
}
</script>

<style lang="scss" scoped>
.signature-pad {
  display: flex;
  flex-direction: column;
  gap: 12px;

  canvas {
    border: 1px dashed #dcdfe6;
    border-radius: 4px;
    cursor: crosshair;
    background: #fff;
  }

  .signature-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>