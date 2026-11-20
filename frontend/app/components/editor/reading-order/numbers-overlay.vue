<script setup lang="ts">
import type { Point, View, AspectRatioScale } from '@/models/editor'
import type { OrderNumber, GroupBounds } from '@/webgl/editor/reading-order-renderer'
import { worldToClipCoords } from '@/utils/editor/coordinates'

interface Props {
  orderNumbers: OrderNumber[]
  groupBounds?: GroupBounds[]
  view: View
  aspectRatioScale: AspectRatioScale
  canvasDimensions: { width: number, height: number }
  visible: boolean
  showLabels?: boolean
}

const props = defineProps<Props>()

const BADGE_HEIGHT = 20
const BADGE_MIN_WIDTH = 20
const BADGE_PADDING_X = 4
const BADGE_RADIUS = 4
const LABEL_GAP = 4
const LABEL_PADDING_X = 4
const LABEL_HEIGHT = 16
const GROUP_LABEL_PADDING_X = 6
const GROUP_LABEL_HEIGHT = 18
const GROUP_LABEL_OFFSET_Y = 20
const DEPTH_COLORS = ['#3b82f6', '#2563eb', '#4f46e5', '#7c3aed', '#7e22ce']

const canvas = ref<HTMLCanvasElement | null>(null)
let frame: number | null = null

function worldToScreen(point: Point): { x: number, y: number } {
  const clip = worldToClipCoords(point, props.view, props.aspectRatioScale)
  return {
    x: (clip.x + 1) * 0.5 * props.canvasDimensions.width,
    y: (1 - clip.y) * 0.5 * props.canvasDimensions.height
  }
}

function pill(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, fill: string): void {
  ctx.beginPath()
  ctx.roundRect(x, y, width, height, BADGE_RADIUS)
  ctx.fillStyle = fill
  ctx.fill()
}

function drawGroupLabel(ctx: CanvasRenderingContext2D, bounds: GroupBounds, font: string): void {
  if (!bounds.label || bounds.points.length < 4) return
  const { x, y } = worldToScreen(bounds.points[0]!)
  ctx.font = `600 10px ${font}`
  const width = ctx.measureText(bounds.label).width + GROUP_LABEL_PADDING_X * 2
  const top = y - GROUP_LABEL_OFFSET_Y
  pill(ctx, x, top, width, GROUP_LABEL_HEIGHT, bounds.isOrdered ? '#10b981' : '#ec4899')
  ctx.fillStyle = '#ffffff'
  ctx.fillText(bounds.label, x + GROUP_LABEL_PADDING_X, top + GROUP_LABEL_HEIGHT / 2)
}

function drawOrderNumber(ctx: CanvasRenderingContext2D, item: OrderNumber, font: string, width: number, height: number): void {
  const { x, y } = worldToScreen(item.position)
  const text = String(item.number)
  ctx.font = `700 12px ${font}`
  const badgeWidth = Math.max(BADGE_MIN_WIDTH, ctx.measureText(text).width + BADGE_PADDING_X * 2)
  const label = props.showLabels ? item.label : undefined
  ctx.font = `500 10px ${font}`
  const labelWidth = label ? ctx.measureText(label).width + LABEL_PADDING_X * 2 : 0
  const rowWidth = badgeWidth + (label ? LABEL_GAP + labelWidth : 0)
  const left = x - rowWidth / 2
  const top = y - BADGE_HEIGHT / 2
  if (left > width || left + rowWidth < 0 || top > height || top + BADGE_HEIGHT < 0) return

  ctx.save()
  ctx.shadowColor = 'rgba(0, 0, 0, 0.18)'
  ctx.shadowBlur = 6
  ctx.shadowOffsetY = 2
  pill(ctx, left, top, badgeWidth, BADGE_HEIGHT, item.isHidden ? 'rgba(163, 163, 163, 0.6)' : DEPTH_COLORS[Math.min(item.depth, DEPTH_COLORS.length - 1)]!)
  ctx.restore()

  ctx.lineWidth = item.isHidden ? 2 : 1
  ctx.strokeStyle = item.isHidden ? '#737373' : 'rgba(255, 255, 255, 0.3)'
  ctx.setLineDash(item.isHidden ? [3, 2] : [])
  ctx.stroke()
  ctx.setLineDash([])

  ctx.font = `700 12px ${font}`
  ctx.textAlign = 'center'
  ctx.fillStyle = '#ffffff'
  ctx.fillText(text, left + badgeWidth / 2, y)
  ctx.textAlign = 'left'

  if (label) {
    const labelLeft = left + badgeWidth + LABEL_GAP
    pill(ctx, labelLeft, y - LABEL_HEIGHT / 2, labelWidth, LABEL_HEIGHT, 'rgba(0, 0, 0, 0.7)')
    ctx.font = `500 10px ${font}`
    ctx.fillStyle = '#ffffff'
    ctx.fillText(label, labelLeft + LABEL_PADDING_X, y)
  }
}

function draw(): void {
  frame = null
  const element = canvas.value
  const ctx = element?.getContext('2d')
  if (!element || !ctx) return

  const { width, height } = props.canvasDimensions
  const dpr = window.devicePixelRatio || 1
  const pixelWidth = Math.round(width * dpr)
  const pixelHeight = Math.round(height * dpr)
  if (element.width !== pixelWidth || element.height !== pixelHeight) {
    element.width = pixelWidth
    element.height = pixelHeight
  }

  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)
  if (!props.visible) return

  const font = getComputedStyle(element).fontFamily
  ctx.textBaseline = 'middle'
  if (props.showLabels) {
    for (const bounds of props.groupBounds ?? []) drawGroupLabel(ctx, bounds, font)
  }
  for (const item of props.orderNumbers) drawOrderNumber(ctx, item, font, width, height)
}

function scheduleDraw(): void {
  if (frame === null) frame = requestAnimationFrame(draw)
}

watch(
  () => [props.orderNumbers, props.groupBounds, props.view, props.aspectRatioScale, props.canvasDimensions, props.visible, props.showLabels],
  scheduleDraw,
  { deep: 2 }
)

onMounted(() => {
  document.fonts?.ready.then(scheduleDraw)
  draw()
})

onBeforeUnmount(() => {
  if (frame !== null) cancelAnimationFrame(frame)
})
</script>

<template>
  <canvas
    ref="canvas"
    class="reading-order-numbers-overlay absolute inset-0 size-full pointer-events-none"
  />
</template>

<style scoped>
.reading-order-numbers-overlay {
  z-index: 10;
  font-variant-numeric: tabular-nums;
}
</style>
