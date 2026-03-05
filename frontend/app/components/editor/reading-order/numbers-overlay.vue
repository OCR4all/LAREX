<script setup lang="ts">
/**
 * Reading Order Numbers Overlay
 *
 * Renders numbered badges at region centroids showing the reading order position.
 * Uses HTML overlay on top of the WebGL canvas for simpler text rendering.
 */

import type { Point, View, AspectRatioScale } from '@/models/editor'
import type { OrderNumber, GroupBounds } from '@/webgl/editor/reading-order-renderer'
import { worldToClipCoords } from '@/utils/editor/coordinates'

interface Props {
  /** Order numbers to display */
  orderNumbers: OrderNumber[]
  /** Group bounds for label display */
  groupBounds?: GroupBounds[]
  /** Canvas view transformation */
  view: View
  /** Aspect ratio scale for coordinate conversion */
  aspectRatioScale: AspectRatioScale
  /** Canvas dimensions in pixels */
  canvasDimensions: { width: number, height: number }
  /** Whether the overlay is visible */
  visible: boolean
  /** Whether to show labels */
  showLabels?: boolean
}

const props = defineProps<Props>()

/**
 * Convert world coordinates to screen coordinates
 */
function worldToScreen(point: Point): { x: number, y: number } {
  const { view, aspectRatioScale, canvasDimensions } = props

  const clip = worldToClipCoords(point, view, aspectRatioScale)

  const screenX = (clip.x + 1) * 0.5 * canvasDimensions.width
  const screenY = (1 - clip.y) * 0.5 * canvasDimensions.height // Y is inverted

  return { x: screenX, y: screenY }
}

/**
 * Get depth-based color gradient (blue -> darker blue/purple as depth increases)
 */
function getDepthColor(depth: number): string {
  if (depth === 0) return 'bg-blue-500'
  if (depth === 1) return 'bg-blue-600'
  if (depth === 2) return 'bg-indigo-600'
  if (depth === 3) return 'bg-violet-600'
  return 'bg-purple-700' // depth >= 4
}

/**
 * Get badge classes based on region state and depth
 */
function getBadgeClasses(item: OrderNumber): string {
  const base = 'inline-flex items-center justify-center min-w-[20px] h-[20px] px-1 text-xs font-bold rounded-sm shadow-md'
  if (item.isHidden) {
    return `${base} bg-gray-400/60 text-white border-2 border-dashed border-gray-500`
  }
  const depthColor = getDepthColor(item.depth)
  return `${base} ${depthColor} text-white border border-white/30`
}

/**
 * Get top-left corner of group bounds for label positioning
 */
function getGroupLabelPosition(bounds: GroupBounds): { x: number, y: number } {
  if (bounds.points.length < 4) return { x: 0, y: 0 }
  const topLeft = bounds.points[0]!
  return worldToScreen(topLeft)
}
</script>

<template>
  <div
    v-if="visible && (orderNumbers.length > 0 || (showLabels && groupBounds?.length))"
    class="reading-order-numbers-overlay absolute inset-0 pointer-events-none overflow-hidden"
  >
    <div
      v-for="(bounds, index) in (showLabels ? groupBounds : [])"
      :key="`group-${index}`"
      class="group-label absolute"
      :style="{
        left: `${getGroupLabelPosition(bounds).x}px`,
        top: `${getGroupLabelPosition(bounds).y - 20}px`
      }"
    >
      <span
        class="inline-flex items-center px-1.5 py-0.5 text-[10px] font-semibold rounded-sm shadow-sm"
        :class="bounds.isOrdered ? 'bg-emerald-500 text-white' : 'bg-pink-500 text-white'"
      >
        {{ bounds.label }}
      </span>
    </div>

    <div
      v-for="(item, index) in orderNumbers"
      :key="index"
      class="order-number absolute flex items-center gap-1"
      :style="{
        left: `${worldToScreen(item.position).x}px`,
        top: `${worldToScreen(item.position).y}px`,
        transform: 'translate(-50%, -50%)'
      }"
    >
      <span :class="getBadgeClasses(item)">
        {{ item.number }}
      </span>
      <span
        v-if="showLabels && item.label"
        class="text-[10px] font-medium text-white bg-black/70 px-1 py-0.5 rounded-sm whitespace-nowrap"
      >
        {{ item.label }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.reading-order-numbers-overlay {
  z-index: 10;
}

.order-number {
  font-variant-numeric: tabular-nums;
}
</style>
