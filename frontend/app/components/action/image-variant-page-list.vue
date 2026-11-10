<script setup lang="ts">
import { defaultRangeExtractor, useVirtualizer } from '@tanstack/vue-virtual'
import type { VNodeRef } from 'vue'

const props = defineProps<{
  rows: Array<{
    id: string
    name: string
    options: Array<{ label: string, value: string }>
    hasCompatibleImages: boolean
  }>
  selectedVariants: Record<string, string[]>
  globalVariants: string[]
  multiple: boolean
  required: boolean
  disabled: boolean
}>()
const emit = defineEmits<{ select: [pageId: string, value: unknown] }>()
const scroller = ref<HTMLElement | null>(null)
const focusedPageId = ref<string | null>(null)
const focusedIndex = computed(() => props.rows.findIndex(page => page.id === focusedPageId.value))
const virtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: props.rows.length,
  getScrollElement: () => scroller.value,
  getItemKey: index => props.rows[index]!.id,
  estimateSize: () => 112,
  overscan: 4,
  // Keep the focused selector mounted, including when its menu is teleported
  // outside the scroller and the user scrolls the list behind it.
  rangeExtractor: (range) => {
    const indices = defaultRangeExtractor(range)
    const focused = focusedIndex.value
    return focused < 0 || indices.includes(focused) ? indices : [...indices, focused].sort((left, right) => left - right)
  }
})))
const totalSize = computed(() => virtualizer.value.getTotalSize())
const visibleRows = computed(() => virtualizer.value.getVirtualItems().flatMap((row) => {
  const page = props.rows[row.index]
  return page ? [{ row, page }] : []
}))
const measureRow: VNodeRef = (element) => {
  virtualizer.value.measureElement(element instanceof HTMLElement ? element : null)
}
watch(() => props.rows, async () => {
  focusedPageId.value = null
  await nextTick()
  virtualizer.value.measure()
  virtualizer.value.scrollToOffset(0)
}, { flush: 'post' })

function differsFromGlobal(pageId: string) {
  const variants = props.selectedVariants[pageId] ?? []
  return variants.length !== props.globalVariants.length
    || variants.some(variant => !props.globalVariants.includes(variant))
}
</script>

<template>
  <div
    ref="scroller"
    role="region"
    aria-label="Image variants per page"
    tabindex="0"
    class="max-h-[50svh] overflow-y-auto overscroll-contain rounded-sm border border-default focus-visible:outline-2 focus-visible:outline-primary"
    :style="{ height: `${Math.min(totalSize, 416)}px` }"
  >
    <div
      role="list"
      aria-label="Page image selections"
      class="relative w-full"
      :style="{ height: `${totalSize}px` }"
    >
      <div
        v-for="{ row, page } in visibleRows"
        :key="String(row.key)"
        :ref="measureRow"
        :data-index="row.index"
        role="listitem"
        :aria-posinset="row.index + 1"
        :aria-setsize="rows.length"
        class="absolute left-0 top-0 grid w-full gap-2 border-b border-default p-3 last:border-b-0 sm:grid-cols-[minmax(0,1fr)_minmax(12rem,18rem)] sm:items-center"
        :style="{ transform: `translateY(${row.start}px)` }"
        @focusin="focusedPageId = page.id"
      >
        <div class="min-w-0">
          <p class="truncate text-sm font-medium">
            {{ page.name }}
          </p>
          <p class="truncate text-xs text-muted">
            {{ page.options.length }} variant{{ page.options.length === 1 ? '' : 's' }}
          </p>
          <UBadge
            v-if="page.options.length > 0 && differsFromGlobal(page.id)"
            color="warning"
            variant="soft"
            size="sm"
          >
            Differs from global selection
          </UBadge>
        </div>
        <UFormField
          v-if="page.options.length > 0"
          :error="required && !selectedVariants[page.id]?.length ? 'Select at least one image variant.' : undefined"
        >
          <USelectMenu
            :model-value="multiple ? selectedVariants[page.id] : selectedVariants[page.id]?.[0]"
            :multiple="multiple"
            :items="page.options"
            :aria-label="`Image variants for ${page.name}`"
            value-key="value"
            class="w-full"
            :disabled="disabled"
            @update:model-value="emit('select', page.id, $event)"
          />
        </UFormField>
        <UBadge v-else color="warning" variant="soft">
          {{ page.hasCompatibleImages ? 'Automatic selection' : 'No matching images' }}
        </UBadge>
      </div>
    </div>
  </div>
</template>
