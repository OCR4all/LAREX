<script setup lang="ts">
import type { Point, View, AspectRatioScale } from '@/models/editor'
import type { RelationOverlayLabel } from '@/types/editor/rendering'
import { worldToClipCoords } from '@/utils/editor/coordinates'

interface Props {
  labels: RelationOverlayLabel[]
  view: View
  aspectRatioScale: AspectRatioScale
  canvasDimensions: { width: number, height: number }
  visible: boolean
  showLabels?: boolean
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
    class="relations-labels-overlay absolute inset-0 pointer-events-none overflow-hidden"
  >
    <template v-for="label in labels" :key="label.id">
      <div
        v-if="label.sourcePosition"
        class="relation-endpoint absolute h-2.5 w-2.5 rounded-full -translate-x-1/2 -translate-y-1/2 border border-white/80"
        :class="label.isDraft ? 'bg-sky-400 shadow-[0_0_0_4px_rgba(56,189,248,0.18)]' : label.isSelected ? 'bg-amber-300 shadow-[0_0_0_4px_rgba(251,191,36,0.18)]' : 'bg-orange-400/90'"
        :style="{
          left: `${worldToScreen(label.sourcePosition).x}px`,
          top: `${worldToScreen(label.sourcePosition).y}px`
        }"
      />
    </template>

    <template v-for="label in labels" :key="`${label.id}-target`">
      <div
        v-if="label.targetPosition"
        class="relation-endpoint absolute h-2.5 w-2.5 rounded-full -translate-x-1/2 -translate-y-1/2 border border-white/80"
        :class="label.isDraft ? 'bg-sky-400 shadow-[0_0_0_4px_rgba(56,189,248,0.18)]' : label.isSelected ? 'bg-amber-300 shadow-[0_0_0_4px_rgba(251,191,36,0.18)]' : 'bg-orange-400/90'"
        :style="{
          left: `${worldToScreen(label.targetPosition).x}px`,
          top: `${worldToScreen(label.targetPosition).y}px`
        }"
      />
    </template>

    <template v-if="showLabels">
      <div
        v-for="label in labels"
        :key="`${label.id}-label`"
        class="relation-label absolute -translate-x-1/2 -translate-y-1/2"
        :style="{
          left: `${worldToScreen(label.position).x}px`,
          top: `${worldToScreen(label.position).y}px`
        }"
      >
        <span
          class="inline-flex items-center rounded-sm px-1.5 py-0.5 text-[10px] font-semibold text-white shadow-md"
          :class="label.isDraft ? 'bg-sky-600' : label.isSelected ? 'bg-amber-500' : 'bg-black/75'"
        >
          {{ label.text }}
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.relations-labels-overlay {
  z-index: 11;
}
</style>
