<script setup lang="ts">
import { HL } from '@/utils/hairline/kernel.js'
import { mountRingBinder } from '@/utils/hairline/ring-binder.js'

const container = useTemplateRef<HTMLDivElement>('container')
let dispose: (() => void) | undefined

onMounted(() => {
  const stage = container.value
  if (!stage) return

  HL.inject(stage.getRootNode())
  const svg = HL.mk('svg', { 'viewBox': '0 0 400 320', 'aria-hidden': 'true' }, stage)
  const figure = mountRingBinder({ stage, svg, read: { textContent: '' } }, 16)
  dispose = () => {
    figure.destroy()
    svg.remove()
  }
})

onBeforeUnmount(() => dispose?.())
</script>

<template>
  <div
    ref="container"
    data-hairline="ring-binder"
    role="img"
    aria-label="An open ring binder with four empty rings"
    class="empty-binder aspect-5/4 w-52 max-w-full"
  />
</template>

<style scoped>
.empty-binder {
  --hairline-plate: var(--ui-bg);
  --hairline-hi: var(--ui-text-highlighted);
  --hairline-edge: var(--ui-text-muted);
  --hairline-mid: var(--ui-text-dimmed);
  --hairline-lo: var(--ui-border);
}
</style>
