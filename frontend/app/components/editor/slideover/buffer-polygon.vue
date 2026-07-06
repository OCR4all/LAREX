<script setup lang="ts">
import { Polygon } from '@/models/editor/geometry'
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
  constrainToImage: boolean
  constrainToParent: boolean
}>()

const emit = defineEmits<{ close: [BufferResult | null] }>()

const formId = useId()
const distance = ref(0)
const validationError = ref<string | null>(null)

const previewPoints = computed(() => {
  if (distance.value === 0) return null
  const original = new Polygon(props.polygon.points.map(p => [p.x, p.y] as [number, number]))
  const buffered = original.buffer(distance.value)
  return buffered.points.map(([x, y]) => ({ x, y }))
})

defineExpose({ previewPoints })

watch([distance, previewPoints], () => {
  validationError.value = null
  if (distance.value === 0 || !previewPoints.value) return

  const points = previewPoints.value

  if (props.constrainToImage) {
    if (points.some(p => p.x < -1 || p.x > 1 || p.y < -1 || p.y > 1)) {
      validationError.value = 'Shape exceeds image boundaries'
      return
    }
  }

  if (props.constrainToParent && props.polygon.parentId) {
    const parent = props.polygons.find(p => p.id === props.polygon.parentId)
    if (parent && points.some(p => !isPointInPolygon(p, parent.points))) {
      validationError.value = 'Shape exceeds parent region boundaries'
      return
    }
  }

  if (distance.value < 0 && props.polygon.type === 'region') {
    const children = props.polygons.filter(p => p.parentId === props.polygon.id)
    for (const child of children) {
      if (child.points.some(p => !isPointInPolygon(p, points))) {
        validationError.value = `Child "${child.label}" would be outside`
        return
      }
    }
  }
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
        <UFormField label="Distance">
          <UInput
            v-model.number="distance"
            type="number"
            :min="-100"
            :max="100"
          />
          <input
            v-model.number="distance"
            type="range"
            :min="-50"
            :max="50"
            class="w-full mt-2"
          >
          <div class="flex justify-between text-xs text-neutral-500 mt-1">
            <span>Shrink</span>
            <span>Expand</span>
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
