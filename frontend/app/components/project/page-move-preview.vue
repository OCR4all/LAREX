<script setup lang="ts">
import { useVirtualizer } from '@tanstack/vue-virtual'
import { computed, nextTick, ref, watch, type VNodeRef } from 'vue'
import type { PageMovePreview } from '@/types/page-move'

type PreviewItem = PageMovePreview['items'][number]
type PreviewFilter = 'all' | 'blocked' | PreviewItem['outcome']

const props = defineProps<{ items: PageMovePreview['items'] }>()
const search = ref('')
const filter = ref<PreviewFilter>('all')
const scroller = ref<HTMLElement | null>(null)
const counts = computed(() => {
  const result = { MOVE: 0, SKIP: 0, OVERWRITE: 0, RENAME: 0, blocked: 0 }
  for (const item of props.items) {
    result[item.outcome]++
    if (item.blockers.length) result.blocked++
  }
  return result
})
const filterOptions = computed(() => [
  { label: `All pages (${props.items.length.toLocaleString()})`, value: 'all' },
  { label: `Replacements (${counts.value.OVERWRITE.toLocaleString()})`, value: 'OVERWRITE' },
  { label: `Renamed (${counts.value.RENAME.toLocaleString()})`, value: 'RENAME' },
  { label: `Skipped (${counts.value.SKIP.toLocaleString()})`, value: 'SKIP' },
  { label: `Unchanged names (${counts.value.MOVE.toLocaleString()})`, value: 'MOVE' },
  { label: `Needs attention (${counts.value.blocked.toLocaleString()})`, value: 'blocked' }
])
const filteredItems = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return props.items.filter(item =>
    (filter.value === 'all'
      || (filter.value === 'blocked' ? item.blockers.length > 0 : item.outcome === filter.value))
    && (!query || item.sourceName.toLocaleLowerCase().includes(query) || item.resultingName.toLocaleLowerCase().includes(query))
  )
})
const outcomes = {
  MOVE: { label: 'Move', color: 'success', icon: 'i-lucide-folder-input' },
  SKIP: { label: 'Skip', color: 'neutral', icon: 'i-lucide-skip-forward' },
  OVERWRITE: { label: 'Replace', color: 'warning', icon: 'i-lucide-replace' },
  RENAME: { label: 'Rename', color: 'info', icon: 'i-lucide-pencil-line' }
} as const
const virtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: filteredItems.value.length,
  getScrollElement: () => scroller.value,
  getItemKey: index => filteredItems.value[index]!.pageId,
  estimateSize: index => 80 + (filteredItems.value[index]!.blockers.length * 24),
  overscan: 5
})))
const totalSize = computed(() => virtualizer.value.getTotalSize())
const visibleRows = computed(() => virtualizer.value.getVirtualItems().flatMap((row) => {
  const item = filteredItems.value[row.index]
  return item ? [{ row, item }] : []
}))
const measureRow: VNodeRef = (element) => {
  virtualizer.value.measureElement(element instanceof HTMLElement ? element : null)
}

watch(filteredItems, async () => {
  await nextTick()
  virtualizer.value.measure()
  virtualizer.value.scrollToOffset(0)
}, { flush: 'post' })

function clearFilters() {
  search.value = ''
  filter.value = 'all'
}
</script>

<template>
  <section class="space-y-3" aria-label="Page move preview">
    <div class="flex items-center justify-between gap-3">
      <h3 class="text-sm font-semibold text-highlighted">
        Page preview
      </h3>
      <UButton
        v-if="counts.blocked > 0"
        color="error"
        variant="soft"
        size="xs"
        :aria-pressed="filter === 'blocked'"
        @click="filter = filter === 'blocked' ? 'all' : 'blocked'"
      >
        {{ counts.blocked.toLocaleString() }} need attention
      </UButton>
    </div>
    <div class="flex flex-col gap-2 sm:flex-row">
      <UInput
        v-model="search"
        icon="i-lucide-search"
        placeholder="Find a page…"
        aria-label="Search page move preview"
        class="min-w-0 flex-1"
      >
        <template v-if="search" #trailing>
          <UButton
            icon="i-lucide-x"
            color="neutral"
            variant="link"
            size="xs"
            aria-label="Clear preview search"
            @click="search = ''"
          />
        </template>
      </UInput>
      <USelect
        v-model="filter"
        :items="filterOptions"
        aria-label="Filter preview by outcome"
        class="w-full sm:w-48"
      />
    </div>
    <p class="text-xs text-muted" aria-live="polite">
      {{ filteredItems.length.toLocaleString() }} of {{ items.length.toLocaleString() }} pages shown. Filters only affect this preview.
    </p>
    <div
      v-if="filteredItems.length"
      ref="scroller"
      role="region"
      aria-label="Scrollable page move results"
      tabindex="0"
      class="max-h-[50svh] overflow-y-auto overscroll-contain rounded-sm border border-default focus-visible:outline-2 focus-visible:outline-primary"
      :style="{ height: `${Math.min(totalSize, 416)}px` }"
    >
      <div
        role="list"
        aria-label="Selected page outcomes"
        class="relative w-full"
        :style="{ height: `${totalSize}px` }"
      >
        <div
          v-for="{ row, item } in visibleRows"
          :key="String(row.key)"
          :ref="measureRow"
          :data-index="row.index"
          role="listitem"
          :aria-posinset="row.index + 1"
          :aria-setsize="filteredItems.length"
          class="absolute left-0 top-0 w-full border-b border-default px-3 py-3 last:border-b-0"
          :style="{ transform: `translateY(${row.start}px)` }"
        >
          <div class="flex items-start gap-3">
            <UIcon
              :name="item.blockers.length ? 'i-lucide-triangle-alert' : outcomes[item.outcome].icon"
              :class="['mt-0.5 size-4 shrink-0', item.blockers.length ? 'text-error' : 'text-muted']"
            />
            <div class="min-w-0 flex-1 space-y-1">
              <p class="break-all text-sm font-medium text-highlighted">
                <span class="sr-only">Source page: </span>{{ item.sourceName }}
              </p>
              <p v-if="item.resultingName !== item.sourceName" class="flex items-start gap-1.5 text-sm text-muted">
                <UIcon name="i-lucide-arrow-right" class="mt-0.5 size-3.5 shrink-0" />
                <span class="break-all"><span class="sr-only">Resulting name: </span>{{ item.resultingName }}</span>
              </p>
              <p v-else class="text-xs text-muted">
                {{ item.outcome === 'SKIP' ? 'Stays in the source project' : item.outcome === 'OVERWRITE' ? 'Replaces the destination page with this name' : 'Keeps its name in the destination' }}
              </p>
              <ul v-if="item.blockers.length" class="space-y-1 text-xs text-error" aria-label="Reasons this page blocks the move">
                <li v-for="blocker in item.blockers" :key="blocker" class="break-words">
                  {{ blocker }}
                </li>
              </ul>
            </div>
            <UBadge
              :color="item.blockers.length ? 'error' : outcomes[item.outcome].color"
              variant="subtle"
              size="sm"
              class="shrink-0"
            >
              {{ item.blockers.length ? 'Blocked' : outcomes[item.outcome].label }}
            </UBadge>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="rounded-sm border border-dashed border-default px-4 py-8 text-center">
      <UIcon name="i-lucide-search-x" class="mb-2 size-5 text-muted" />
      <p class="text-sm text-muted">
        No pages match these filters.
      </p>
      <UButton
        label="Clear filters"
        color="neutral"
        variant="link"
        size="sm"
        class="mt-2"
        @click="clearFilters"
      />
    </div>
  </section>
</template>
