<script setup lang="ts">
import { Polygon } from '@/models/editor/geometry'
import type { ImageSize, Point } from '@/models/editor'
import { bufferPolygonInPixels, getBufferDistanceRange, isBufferedPolygonValid } from '@/utils/editor/buffer-polygon'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { isPointInPolygon } from '@/utils/editor/hit-detection'
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'

export interface BufferResult {
  distance: number
}

useBlockEditorCanvasInteractions()

const props = defineProps<{
  polygon: RenderablePolygon
  polygons: RenderablePolygon[]
  imageSize: ImageSize
  constrainToImage: boolean
  constrainToParent: boolean
}>()

const emit = defineEmits<{ close: [BufferResult | null] }>()

const formId = useId()
const distance = ref(0)
const validationError = ref<string | null>(null)

const original = computed(() => new Polygon(props.polygon.points.map(p => [p.x, p.y])))
const parent = computed(() => props.constrainToParent && props.polygon.parentId
  ? props.polygons.find(p => p.id === props.polygon.parentId)
  : undefined)
const children = computed(() => props.polygon.type === 'region'
  ? props.polygons.filter(p => p.parentId === props.polygon.id)
  : [])

const previewPoints = computed(() => {
  if (distance.value === 0) return null
  const buffered = bufferPolygonInPixels(original.value, distance.value, props.imageSize)
  return buffered.points.map(([x, y]) => ({ x, y }))
})

defineExpose({ previewPoints })

function getConstraintError(points: Point[], bufferDistance: number): string | null {
  if (props.constrainToImage && points.some(p => p.x < -1 || p.x > 1 || p.y < -1 || p.y > 1)) {
    return 'Shape exceeds image boundaries'
  }

  if (parent.value && points.some(p => !isPointInPolygon(p, parent.value!.points))) {
    return 'Shape exceeds parent region boundaries'
  }

  if (bufferDistance < 0) {
    for (const child of children.value) {
      if (child.points.some(p => !isPointInPolygon(p, points))) {
        return `Child "${child.label}" would be outside`
      }
    }
  }
  return null
}

function getValidationError(points: Point[], bufferDistance: number): string | null {
  return getConstraintError(points, bufferDistance)
    ?? (isBufferedPolygonValid(props.polygon.points, points) ? null : 'Shape would collapse or intersect itself')
}

const distanceRange = computed(() => getBufferDistanceRange(
  original.value, props.imageSize,
  (points, bufferDistance) => getConstraintError(points, bufferDistance) === null
))

watch(() => getValidationError(previewPoints.value ?? props.polygon.points, distance.value), (error) => {
  validationError.value = distance.value === 0 ? null : error
}, { immediate: true })

const canSave = computed(() => distance.value !== 0 && !validationError.value)
const save = (): void => {
  if (!canSave.value) return
  emit('close', { distance: distance.value })
}
const cancel = () => emit('close', null)
</script>

<template>
  <UiResponsiveSlideover
    :open="true"
    :modal="false"
    :close="{ onClick: cancel }"
  >
    <template #header>
      <UiSlideoverHeader title="Expand / Shrink" icon="i-lucide-move-diagonal" />
    </template>

    <template #body>
      <UForm :id="formId" class="flex flex-col gap-4" @submit="save">
        <UFormField label="Distance (pixels)">
          <UInput
            v-model.number="distance"
            type="number"
            :min="distanceRange.min"
            :max="distanceRange.max"
          />
          <input
            v-model.number="distance"
            type="range"
            :min="distanceRange.min"
            :max="distanceRange.max"
            :step="1"
            :disabled="distanceRange.min === distanceRange.max"
            aria-label="Expand or shrink distance in pixels"
            class="w-full mt-2"
          >
          <div class="flex justify-between text-xs text-neutral-500 mt-1">
            <span>Shrink {{ Math.abs(distanceRange.min) }} px</span>
            <span>Grow {{ distanceRange.max }} px</span>
          </div>
        </UFormField>

        <UAlert
          v-if="validationError"
          color="error"
          variant="solid"
          :title="validationError"
          icon="i-lucide-alert-circle"
        />
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="cancel">
          Cancel
        </UButton>
        <UButton type="submit" :form="formId" :disabled="!canSave">
          Apply
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
