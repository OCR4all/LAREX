<script setup lang="ts">
const selectedIndices = defineModel<number[]>('selectedIndices', { default: () => [] })
const filterUnindexed = defineModel<boolean>('filterUnindexed', { default: false })
const confidenceRange = defineModel<number[]>('confidenceRange', { default: () => [0, 1] })
const showNonAssignedIndices = defineModel<boolean>('showNonAssignedIndices', { default: false })

defineProps<{
  availableIndices: number[]
  hasUnindexed: boolean
}>()

function isIndexSelected(index: number): boolean {
  return selectedIndices.value.includes(index)
}

function toggleIndexSelection(index: number, event?: Event): void {
  if (event) event.preventDefault()
  const current = new Set(selectedIndices.value)
  if (current.has(index)) {
    current.delete(index)
  } else {
    current.add(index)
  }
  selectedIndices.value = [...current].sort((a, b) => a - b)
}

const normalizedConfidenceRange = computed<[number, number]>(() => {
  const min = Math.max(0, Math.min(1, Number(confidenceRange.value?.[0] ?? 0)))
  const max = Math.max(0, Math.min(1, Number(confidenceRange.value?.[1] ?? 1)))
  return min <= max ? [min, max] : [max, min]
})

const confidenceRangeModel = computed<[number, number]>({
  get: () => normalizedConfidenceRange.value,
  set: (value) => {
    const min = Math.max(0, Math.min(1, Number(value?.[0] ?? 0)))
    const max = Math.max(0, Math.min(1, Number(value?.[1] ?? 1)))
    confidenceRange.value = min <= max ? [min, max] : [max, min]
  }
})
</script>

<template>
  <div class="p-4 space-y-4">
    <div>
      <div class="flex justify-between items-center mb-2">
        <span class="text-sm font-medium">Filter Indices</span>
      </div>
      <div class="flex flex-col gap-2">
        <div v-if="hasUnindexed" class="flex items-center justify-between">
          <span class="text-sm cursor-pointer" @click="filterUnindexed = !filterUnindexed">Unindexed</span>
          <UCheckbox :model-value="filterUnindexed" @update:model-value="(value: string | boolean) => { filterUnindexed = value === true }" />
        </div>
        <template v-if="availableIndices.length > 0">
          <div v-for="idx in availableIndices" :key="idx" class="flex items-center justify-between">
            <span class="text-sm cursor-pointer" @click="toggleIndexSelection(idx)">{{ idx }}</span>
            <UCheckbox :model-value="isIndexSelected(idx)" @update:model-value="() => toggleIndexSelection(idx)" />
          </div>
        </template>
        <div v-if="!hasUnindexed && availableIndices.length === 0" class="text-xs text-muted">
          No variants found.
        </div>
      </div>
    </div>

    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Show Non-Assigned Indices</span>
        <span class="text-xs text-muted">
          Show indices outside configured GT/recognition assignments as disabled fields.
        </span>
      </div>
      <USwitch v-model="showNonAssignedIndices" />
    </div>

    <div>
      <div class="flex justify-between items-center mb-2">
        <span class="text-sm font-medium">Confidence Range</span>
        <span class="text-sm font-semibold text-primary">
          {{ normalizedConfidenceRange[0].toFixed(2) }}–{{ normalizedConfidenceRange[1].toFixed(2) }}
        </span>
      </div>
      <div class="flex flex-col gap-2">
        <USlider
          v-model="confidenceRangeModel"
          :min="0"
          :max="1"
          :step="0.01"
        />
      </div>
      <div class="text-xs text-muted mt-2">
        Index and confidence filters are combined.
      </div>
    </div>
  </div>
</template>
