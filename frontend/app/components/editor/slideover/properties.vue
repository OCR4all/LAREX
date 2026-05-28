<script setup lang="ts">
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'

const props = defineProps<{
  target: ContextMenuTarget
  inReadingOrder: boolean
}>()

const emit = defineEmits<{
  close: []
  delete: []
  duplicate: []
  toggleReadingOrder: []
}>()

const polygon = computed(() => props.target.type === 'polygon' ? (props.target.element as RenderablePolygon) : null)
const polyline = computed(() => props.target.type === 'polyline' ? (props.target.element as RenderablePolyline) : null)

const title = computed(() => props.target.type === 'polygon' ? 'Element Properties' : 'Baseline Properties')
const elementId = computed(() => (props.target.element as any)?.id as string)
const parentId = computed(() => (props.target.element as any)?.parentId as string | undefined)
const pointsCount = computed(() => ((props.target.element as any)?.points?.length as number | undefined) ?? 0)

const elementType = computed(() => {
  if (polygon.value) {
    if (polygon.value.type === 'textline' || polygon.value.type === 'TextLine') return 'TextLine'
    return polygon.value.regionKind || 'Region'
  }
  if (polyline.value) {
    return polyline.value.type === 'baseline' ? 'Baseline' : 'Polyline'
  }
  return 'Element'
})

const labelText = computed(() => {
  if (polygon.value) {
    return polygon.value.regionSubtype || polygon.value.label || ''
  }
  if (polyline.value) {
    return polyline.value.label || ''
  }
  return ''
})

const canToggleReadingOrder = computed(() => {
  return polygon.value?.type === 'region'
})
</script>

<template>
  <UiResponsiveSlideover
    side="right"
    :close="{ onClick: () => emit('close') }"
  >
    <template #header>
      <UiSlideoverHeader :title="title" icon="i-lucide-sliders-horizontal" />
    </template>

    <template #body>
      <div class="space-y-6">
        <UPageCard variant="subtle" title="Details">
          <div class="space-y-3">
            <div class="flex items-center justify-between gap-4">
              <span class="text-sm text-muted">Type</span>
              <span class="text-sm font-medium text-highlighted">{{ elementType }}</span>
            </div>
            <div class="flex items-center justify-between gap-4">
              <span class="text-sm text-muted">ID</span>
              <span class="text-sm font-mono text-highlighted truncate max-w-[14rem]">{{ elementId }}</span>
            </div>
            <div v-if="parentId" class="flex items-center justify-between gap-4">
              <span class="text-sm text-muted">Parent</span>
              <span class="text-sm font-mono text-highlighted truncate max-w-[14rem]">{{ parentId }}</span>
            </div>
            <div v-if="labelText" class="flex items-center justify-between gap-4">
              <span class="text-sm text-muted">Label</span>
              <span class="text-sm text-highlighted truncate max-w-[14rem]">{{ labelText }}</span>
            </div>
            <div class="flex items-center justify-between gap-4">
              <span class="text-sm text-muted">Points</span>
              <span class="text-sm text-highlighted">{{ pointsCount }}</span>
            </div>
          </div>
        </UPageCard>

        <UPageCard v-if="canToggleReadingOrder" variant="subtle" title="Reading Order">
          <div class="flex items-center justify-between gap-4">
            <UBadge :color="inReadingOrder ? 'success' : 'neutral'" variant="solid" size="sm">
              {{ inReadingOrder ? 'In reading order' : 'Not in reading order' }}
            </UBadge>
            <UButton
              size="sm"
              :color="inReadingOrder ? 'neutral' : 'primary'"
              :variant="inReadingOrder ? 'outline' : 'solid'"
              :icon="inReadingOrder ? 'i-lucide-book-minus' : 'i-lucide-book-plus'"
              @click="emit('toggleReadingOrder')"
            >
              {{ inReadingOrder ? 'Remove' : 'Add' }}
            </UButton>
          </div>
        </UPageCard>

        <UPageCard variant="subtle" title="Actions" class="bg-linear-to-tl from-error/5 from-5% to-default">
          <div class="flex gap-2">
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-copy"
              @click="emit('duplicate')"
            >
              Duplicate
            </UButton>
            <UButton
              color="error"
              variant="solid"
              icon="i-lucide-trash-2"
              @click="emit('delete')"
            >
              Delete
            </UButton>
          </div>
        </UPageCard>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
