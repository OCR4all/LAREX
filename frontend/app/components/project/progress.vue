<script setup lang="ts">
import type { ProgressProps } from '@nuxt/ui'

const props = withDefaults(defineProps<{
  modelValue: number
  size?: ProgressProps['size']
}>(), {
  size: 'sm'
})

const percentage = computed(() => Math.min(100, Math.max(0, props.modelValue)))
const indicatorColor = computed(() => {
  // Blend through the theme's warm midpoint before reaching the success color.
  const start = percentage.value <= 50 ? 'error' : 'warning'
  const end = percentage.value <= 50 ? 'warning' : 'success'
  const weight = percentage.value <= 50 ? percentage.value * 2 : (percentage.value - 50) * 2

  return `color-mix(in oklab, var(--ui-${start}), var(--ui-${end}) ${weight}%)`
})
</script>

<template>
  <UProgress
    :model-value="percentage"
    :max="100"
    :size="size"
    :style="{ '--project-progress-color': indicatorColor }"
    :ui="{ indicator: 'bg-[var(--project-progress-color)]' }"
  />
</template>
