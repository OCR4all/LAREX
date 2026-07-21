<script setup lang="ts">
import type { AspectRatioScale, Point, View } from '@/models/editor'
import type { ElementOverlayLabel } from '@/types/editor/rendering'
import { worldToClipCoords } from '@/utils/editor/coordinates'

interface Props {
  labels: ElementOverlayLabel[]
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

const visibleLabels = computed(() => {
  const margin = 12
  return props.labels
    .map(label => ({ ...label, screenPosition: worldToScreen(label.position) }))
    .filter(({ screenPosition }) =>
      screenPosition.x >= -margin
      && screenPosition.y >= -margin
      && screenPosition.x <= props.canvasDimensions.width + margin
      && screenPosition.y <= props.canvasDimensions.height + margin
    )
})
</script>

<template>
  <div
    v-if="visible && visibleLabels.length > 0"
    class="element-labels-overlay absolute inset-0 pointer-events-none overflow-hidden"
    aria-hidden="true"
  >
    <div
      v-for="item in visibleLabels"
      :key="item.id"
      class="absolute flex -translate-x-1/2 -translate-y-1/2 flex-col items-stretch overflow-hidden rounded-sm whitespace-nowrap text-[10px] leading-4 shadow-md"
      :style="{
        left: `${item.screenPosition.x}px`,
        top: `${item.screenPosition.y}px`
      }"
    >
      <span
        v-if="item.elementType"
        class="max-w-40 truncate bg-black px-1.5 font-medium text-white"
      >
        {{ item.elementType }}
      </span>
      <span
        class="max-w-40 truncate px-1.5 font-semibold text-white"
        :class="item.elementType && 'border-t border-white/20'"
        :style="{ backgroundColor: item.backgroundColor }"
      >
        {{ item.label }}
      </span>
      <span
        class="max-w-40 truncate border-t border-white/20 bg-black px-1.5 font-mono text-white"
      >
        {{ item.id }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.element-labels-overlay {
  z-index: 12;
}
</style>
