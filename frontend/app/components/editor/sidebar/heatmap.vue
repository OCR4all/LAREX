<script setup lang="ts">
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { useEditorStore } from '@/stores/editor/editor.store'
import { getEditorSession } from '@/session/editor/editor-session'
import type { RenderablePolygon } from '@/types/editor/rendering'

const editorUiStore = useEditorUiStore()
const editorStore = useEditorStore()
const toast = useToast()

const heatmapModeOptions = [
  { label: 'Average', value: 'average' },
  { label: 'Indices', value: 'indices' }
]

const heatmapScaleStrengthOptions = [
  { label: 'Medium', value: 4 },
  { label: 'Strong', value: 8 },
  { label: 'Extreme', value: 16 }
]

function showSavedToast() {
  toast.add({ title: 'Setting saved', icon: 'i-lucide-check', color: 'success' })
}

function getRenderablePolygonsForActiveCanvas(): RenderablePolygon[] {
  const canvasId = editorStore.activeCanvasId
  if (!canvasId) return []

  const session = getEditorSession(canvasId)
  const controls = session?.controls.value as { polygons?: RenderablePolygon[] } | null
  return controls?.polygons ?? editorStore.regionsByCanvasId(canvasId)
}

const heatmapEnabledModel = computed({
  get: () => editorUiStore.confidenceHeatmap.enabled,
  set: (next: boolean) => {
    editorUiStore.setConfidenceHeatmapEnabled(Boolean(next))
    showSavedToast()
  }
})

const heatmapModeModel = computed({
  get: () => editorUiStore.confidenceHeatmap.mode,
  set: (next: 'indices' | 'average') => {
    editorUiStore.setConfidenceHeatmapMode(next)
    showSavedToast()
  }
})

const heatmapSelectedIndicesModel = computed({
  get: () => editorUiStore.confidenceHeatmap.selectedIndices,
  set: (next: number[]) => {
    editorUiStore.setConfidenceHeatmapSelectedIndices(next)
    showSavedToast()
  }
})

const heatmapLogScaleModel = computed({
  get: () => editorUiStore.confidenceHeatmap.logScale,
  set: (next: boolean) => {
    editorUiStore.setConfidenceHeatmapLogScale(Boolean(next))
    showSavedToast()
  }
})

const heatmapLogScaleStrengthModel = computed({
  get: () => editorUiStore.confidenceHeatmap.logScaleStrength,
  set: (next: unknown) => {
    editorUiStore.setConfidenceHeatmapLogScaleStrength(Number(next))
    showSavedToast()
  }
})

const heatmapFillOpacityModel = computed({
  get: () => editorUiStore.confidenceHeatmap.fillOpacity,
  set: (next: unknown) => {
    editorUiStore.setConfidenceHeatmapFillOpacity(Number(next))
    scheduleFillOpacityToast()
  }
})

const heatmapFillOpacityPercent = computed(() => `${Math.round(heatmapFillOpacityModel.value * 100)}%`)

let fillOpacityToastTimer: ReturnType<typeof setTimeout> | null = null

function scheduleFillOpacityToast(): void {
  if (fillOpacityToastTimer) clearTimeout(fillOpacityToastTimer)
  fillOpacityToastTimer = setTimeout(() => {
    showSavedToast()
    fillOpacityToastTimer = null
  }, 350)
}

onBeforeUnmount(() => {
  if (fillOpacityToastTimer) clearTimeout(fillOpacityToastTimer)
})

const availableHeatmapIndices = computed(() => {
  const indices = new Set<number>()
  const polygons = getRenderablePolygonsForActiveCanvas()

  for (const polygon of polygons) {
    for (const variant of polygon.textContentVariants ?? []) {
      if (typeof variant.index === 'number' && Number.isFinite(variant.index) && variant.index >= 0) {
        indices.add(Math.trunc(variant.index))
      }
    }
  }

  return [...indices].sort((a, b) => a - b)
})

function isHeatmapIndexSelected(index: number): boolean {
  return heatmapSelectedIndicesModel.value.includes(index)
}

function toggleHeatmapIndexSelection(index: number): void {
  const current = new Set(heatmapSelectedIndicesModel.value)
  if (current.has(index)) current.delete(index)
  else current.add(index)
  heatmapSelectedIndicesModel.value = [...current].sort((a, b) => a - b)
}
</script>

<template>
  <div class="p-3 space-y-2">
    <div class="flex items-center justify-between">
      <span class="text-sm">Confidence Heatmap</span>
      <USwitch v-model="heatmapEnabledModel" />
    </div>

    <div v-if="heatmapEnabledModel" class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm">Fill Opacity</span>
        <span class="text-sm font-semibold text-primary">{{ heatmapFillOpacityPercent }}</span>
      </div>
      <USlider
        v-model="heatmapFillOpacityModel"
        :min="0"
        :max="1"
        :step="0.05"
      />

      <div class="flex items-center justify-between">
        <span class="text-sm">Log scale</span>
        <USwitch v-model="heatmapLogScaleModel" />
      </div>
      <div class="text-xs text-muted">
        Emphasizes lower confidence values (drops from green faster).
      </div>

      <div class="flex items-center justify-between">
        <span class="text-sm">Drop strength</span>
        <USelect
          v-model="heatmapLogScaleStrengthModel"
          :items="heatmapScaleStrengthOptions"
          class="w-32"
          size="xs"
        />
      </div>

      <div class="flex items-center justify-between">
        <span class="text-sm">Mode</span>
        <USelect
          v-model="heatmapModeModel"
          :items="heatmapModeOptions"
          class="w-32"
          size="xs"
        />
      </div>

      <div v-if="heatmapModeModel === 'indices'" class="space-y-1">
        <div class="text-xs text-muted">
          Selected indices are used for scoring. If none are selected, average confidence is used.
        </div>

        <div v-if="availableHeatmapIndices.length === 0" class="text-xs text-muted">
          No indices found.
        </div>

        <div
          v-for="idx in availableHeatmapIndices"
          :key="idx"
          class="flex items-center justify-between"
        >
          <span class="text-sm cursor-pointer" @click="toggleHeatmapIndexSelection(idx)">{{ idx }}</span>
          <UCheckbox
            :model-value="isHeatmapIndexSelected(idx)"
            @update:model-value="toggleHeatmapIndexSelection(idx)"
          />
        </div>
      </div>
    </div>
  </div>
</template>
