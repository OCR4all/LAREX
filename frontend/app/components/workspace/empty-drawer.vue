<script setup lang="ts">
import { drawer, type Figure } from '@lucasmarkes/hairline'

const container = useTemplateRef<HTMLDivElement>('container')
let figure: Figure | undefined

onMounted(() => {
  if (container.value) {
    figure = drawer(container.value, { label: 'An empty filing cabinet' })
  }
})

onBeforeUnmount(() => figure?.destroy())
</script>

<template>
  <div ref="container" class="empty-drawer aspect-5/4 w-52 max-w-full" />
</template>

<style scoped>
.empty-drawer {
  --hairline-plate: var(--ui-bg);
  --hairline-hi: var(--ui-text-highlighted);
  --hairline-edge: var(--ui-text-muted);
  --hairline-mid: var(--ui-text-dimmed);
  --hairline-lo: var(--ui-border);
}
</style>
