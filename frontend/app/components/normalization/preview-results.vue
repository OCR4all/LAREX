<script setup lang="ts">
import { useVirtualizer } from '@tanstack/vue-virtual'
import type { VNodeRef } from 'vue'
import type { NormalizationPreviewSummary, NormalizePreview } from '@/types/normalization-profile'

const ESTIMATED_ROW_HEIGHT = 260
const OVERSCAN = 4

const props = defineProps<{
  rows: NormalizePreview[]
  summary: NormalizationPreviewSummary
  isLoading: boolean
  isComplete: boolean
  projectCount?: number
  showRowApply?: boolean
  applyingRowKey?: string | null
  rowApplyDisabled?: boolean
}>()

const emit = defineEmits<{ applyRow: [row: NormalizePreview] }>()

const scrollerRef = ref<HTMLElement | null>(null)

const rowVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: props.rows.length,
  getScrollElement: () => scrollerRef.value,
  estimateSize: () => ESTIMATED_ROW_HEIGHT,
  overscan: OVERSCAN,
  getItemKey: index => getNormalizationPreviewKey(props.rows[index]!)
})))

const virtualRows = computed(() =>
  rowVirtualizer.value.getVirtualItems().flatMap((item) => {
    const row = props.rows[item.index]
    return row ? [{ item, row, key: getNormalizationPreviewKey(row) }] : []
  })
)
const totalSize = computed(() => rowVirtualizer.value.getTotalSize())

const measureRow: VNodeRef = (element) => {
  if (element instanceof HTMLElement) {
    rowVirtualizer.value.measureElement(element)
  }
}

const statistics = computed(() => [
  ...(props.projectCount === undefined ? [] : [{ title: 'Projects', value: props.projectCount }]),
  { title: 'Pages', value: props.summary.analyzedPageCount },
  { title: 'Rows', value: props.summary.analyzedRowCount },
  { title: 'Changed Rows', value: props.summary.changedRowCount },
  { title: 'Changed Pages', value: props.summary.changedPageCount }
])
</script>

<template>
  <div class="space-y-4">
    <div class="grid gap-3 sm:grid-cols-2" :class="statistics.length === 5 ? 'lg:grid-cols-5' : 'lg:grid-cols-4'">
      <UPageCard
        v-for="statistic in statistics"
        :key="statistic.title"
        :title="statistic.title"
        :description="statistic.value.toLocaleString()"
        variant="subtle"
      />
    </div>

    <div v-if="isLoading" class="space-y-2">
      <p class="text-sm text-muted">
        Scanning page {{ summary.scannedPageCount.toLocaleString() }} of {{ summary.totalPageCount.toLocaleString() }}…
      </p>
      <UProgress :model-value="summary.scannedPageCount" :max="summary.totalPageCount" size="sm" />
    </div>

    <UAlert
      v-else-if="!isComplete"
      color="warning"
      variant="subtle"
      icon="i-lucide-triangle-alert"
      title="Preview incomplete"
      :description="`Scanned ${summary.scannedPageCount.toLocaleString()} of ${summary.totalPageCount.toLocaleString()} pages. Run the preview again to see every change.`"
    />

    <UAlert
      v-else
      :color="summary.changedRowCount > 0 ? 'warning' : 'success'"
      variant="subtle"
      :icon="summary.changedRowCount > 0 ? 'i-lucide-triangle-alert' : 'i-lucide-circle-check'"
      :title="summary.changedRowCount > 0 ? 'Review changes before applying' : 'No changes needed'"
      :description="summary.changedRowCount > 0 ? 'This profile changes text in the selected sources.' : 'This profile does not change any text in the selected sources.'"
    />

    <div v-if="rows.length > 0" class="space-y-3">
      <h3 class="text-sm font-semibold">
        Affected Rows
      </h3>
      <div ref="scrollerRef" class="max-h-[70dvh] overflow-y-auto">
        <div class="relative w-full" :style="{ height: `${totalSize}px` }">
          <div
            v-for="{ item, row, key } in virtualRows"
            :key="key"
            :ref="measureRow"
            :data-index="item.index"
            class="absolute left-0 top-0 w-full pb-3"
            :style="{ transform: `translateY(${item.start}px)` }"
          >
            <NormalizationPreviewRow
              :preview="row"
              :show-apply="showRowApply"
              :applying="applyingRowKey === key"
              :apply-disabled="rowApplyDisabled || (Boolean(applyingRowKey) && applyingRowKey !== key)"
              @apply="emit('applyRow', row)"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
