<script setup lang="ts">
import type { AspectRatioScale, Point, View } from '@/models/editor'
import type { CommentOverlayLabel } from '@/types/editor/rendering'
import { worldToClipCoords } from '@/utils/editor/coordinates'

interface Props {
  labels: CommentOverlayLabel[]
  view: View
  aspectRatioScale: AspectRatioScale
  canvasDimensions: { width: number, height: number }
  visible: boolean
}

const props = defineProps<Props>()

function worldToScreen(point: Point): { x: number, y: number } {
  const clip = worldToClipCoords(point, props.view, props.aspectRatioScale)

  return {
    x: (clip.x + 1) * 0.5 * props.canvasDimensions.width,
    y: (1 - clip.y) * 0.5 * props.canvasDimensions.height
  }
}
</script>

<template>
  <div
    v-if="visible && labels.length > 0"
    class="comments-labels-overlay absolute inset-0 pointer-events-none overflow-hidden"
  >
    <div
      v-for="label in labels"
      :key="label.id"
      class="comment-label absolute -translate-x-1/2 -translate-y-1/2"
      :style="{
        left: `${worldToScreen(label.position).x}px`,
        top: `${worldToScreen(label.position).y}px`
      }"
    >
      <span class="inline-flex max-w-[240px] items-center rounded-sm bg-black/70 px-1 py-0.5 text-[10px] font-medium text-white shadow-sm">
        {{ label.text }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.comments-labels-overlay {
  z-index: 10;
}

.comment-label span {
  overflow-wrap: anywhere;
}
</style>
