<script setup lang="ts">
const props = defineProps<{
  target: HTMLElement | null
  icon: string
  title: string
  description?: string
}>()

// The drop zone lives inside a scrolling panel body; pin the overlay to the
// part of the drop zone that is currently visible in that scroll container.
const scrollContainer = computed(() => props.target?.parentElement ?? null)
const zone = useElementBounding(() => props.target)
const viewport = useElementBounding(scrollContainer)

const overlayStyle = computed(() => {
  const top = Math.max(zone.top.value, viewport.top.value)
  const bottom = Math.min(zone.bottom.value, viewport.bottom.value)
  return {
    top: `${top}px`,
    left: `${zone.left.value}px`,
    width: `${zone.width.value}px`,
    height: `${Math.max(0, bottom - top)}px`
  }
})
</script>

<template>
  <div
    role="status"
    aria-live="polite"
    class="pointer-events-none fixed z-20 flex items-center justify-center rounded-lg border-2 border-dashed border-primary bg-primary/10 p-6 backdrop-blur-sm"
    :style="overlayStyle"
  >
    <div class="flex flex-col items-center gap-2 rounded-lg border border-primary/20 bg-default/90 px-8 py-6 text-center shadow-lg">
      <UIcon :name="icon" class="size-8 text-primary" />
      <p class="font-medium text-highlighted">
        {{ title }}
      </p>
      <p v-if="description" class="text-sm text-muted">
        {{ description }}
      </p>
    </div>
  </div>
</template>
