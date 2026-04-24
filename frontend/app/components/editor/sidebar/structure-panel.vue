<script setup lang="ts">
import { useVirtualizer, type VirtualItem } from '@tanstack/vue-virtual'
import type { FlatStructureRow, TreeItemData } from '@/components/editor/sidebar/structure-tree'
import { buildChildrenByParentId, flattenStructureRows } from '@/components/editor/sidebar/structure-tree'

const props = defineProps<{
  polygons: TreeItemData[]
  polylines: TreeItemData[]
  regions: TreeItemData[]
  selectedPolygonIds: string[]
  selectedPolylineIds: string[]
  hoveredPolygonId?: string | null
  hiddenPolygonIds: string[]
  hiddenPolylineIds: string[]
  expandedRegions: Set<string>
}>()

const emit = defineEmits<{
  'select-polygon': [id: string]
  'select-polyline': [id: string]
  'hover-polygon': [id: string]
  'hover-polyline': [id: string]
  'unhover-polygon': []
  'delete-item': [id: string]
  'toggle-visibility': [id: string]
  'toggle-expanded': [id: string]
  'collapse-all': []
  'expand-all': []
}>()

const ROW_HEIGHT_PX = 36

const selectedPolygonIdSet = computed(() => new Set(props.selectedPolygonIds))
const selectedPolylineIdSet = computed(() => new Set(props.selectedPolylineIds))
const hiddenPolygonIdSet = computed(() => new Set(props.hiddenPolygonIds))
const hiddenPolylineIdSet = computed(() => new Set(props.hiddenPolylineIds))

const childrenByParentId = computed(() => {
  return buildChildrenByParentId(props.polygons, props.polylines)
})

const visibleRows = computed<FlatStructureRow[]>(() => {
  return flattenStructureRows(props.regions, childrenByParentId.value, props.expandedRegions)
})

const hasAnyItems = computed(() => props.polygons.length > 0 || props.polylines.length > 0)
const canExpandAll = computed(() => childrenByParentId.value.size > 0)
const canCollapseAll = computed(() => props.expandedRegions.size > 0)

const listRootRef = ref<HTMLElement | null>(null)
const scrollElement = ref<HTMLElement | null>(null)
const scrollMargin = ref(0)
let resizeObserver: ResizeObserver | null = null
let syncLayoutFrameId: number | null = null

function attachScrollElement(nextScrollElement: HTMLElement | null) {
  const previousScrollElement = scrollElement.value
  if (previousScrollElement === nextScrollElement) return

  if (previousScrollElement) {
    resizeObserver?.unobserve(previousScrollElement)
  }

  scrollElement.value = nextScrollElement

  if (nextScrollElement) {
    resizeObserver?.observe(nextScrollElement)
  }
}

function resolveScrollElement(): HTMLElement | null {
  const nearest = listRootRef.value?.closest('.overflow-auto')
  if (nearest instanceof HTMLElement) return nearest

  if (import.meta.client) {
    return document.querySelector<HTMLElement>('[data-tour="editor-right-sidebar"] .overflow-auto')
  }

  return null
}

function calculateScrollMargin(): number {
  const root = listRootRef.value
  const scroller = scrollElement.value
  if (!root || !scroller) return 0

  const rootRect = root.getBoundingClientRect()
  const scrollerRect = scroller.getBoundingClientRect()
  return Math.max(0, rootRect.top - scrollerRect.top + scroller.scrollTop)
}

const rowVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: visibleRows.value.length,
  getScrollElement: () => scrollElement.value,
  getItemKey: index => visibleRows.value[index]?.item.id ?? index,
  estimateSize: () => ROW_HEIGHT_PX,
  overscan: 12,
  scrollMargin: scrollMargin.value
})))

const totalSize = computed(() => rowVirtualizer.value.getTotalSize())
const virtualRows = computed<Array<{ item: VirtualItem, row: FlatStructureRow }>>(() => {
  const rows = visibleRows.value
  return rowVirtualizer.value
    .getVirtualItems()
    .flatMap((item) => {
      const row = rows[item.index]
      return row ? [{ item, row }] : []
    })
})
const rowOrderSignature = computed(() => visibleRows.value.map(row => row.item.id).join('|'))

function syncVirtualizerLayout(forceMeasure: boolean = false) {
  if (syncLayoutFrameId !== null) {
    cancelAnimationFrame(syncLayoutFrameId)
  }

  syncLayoutFrameId = requestAnimationFrame(() => {
    syncLayoutFrameId = null
    attachScrollElement(resolveScrollElement())

    const nextScrollMargin = calculateScrollMargin()
    const didScrollMarginChange = scrollMargin.value !== nextScrollMargin
    if (didScrollMarginChange) {
      scrollMargin.value = nextScrollMargin
    }

    if (forceMeasure || didScrollMarginChange) {
      rowVirtualizer.value.measure()
    }
  })
}

onMounted(() => {
  if (import.meta.client && typeof ResizeObserver !== 'undefined' && listRootRef.value) {
    resizeObserver = new ResizeObserver(() => {
      syncVirtualizerLayout()
    })
    resizeObserver.observe(listRootRef.value)
  }
  syncVirtualizerLayout(true)
})

onBeforeUnmount(() => {
  attachScrollElement(null)

  if (syncLayoutFrameId !== null) {
    cancelAnimationFrame(syncLayoutFrameId)
    syncLayoutFrameId = null
  }

  resizeObserver?.disconnect()
  resizeObserver = null
})

watch(rowOrderSignature, () => {
  nextTick(() => {
    syncVirtualizerLayout(true)
  })
})
</script>

<template>
  <div class="h-full flex flex-col">
    <div class="px-4 py-2 flex justify-between items-center bg-muted/10">
      <span class="text-xs text-muted font-medium">
        {{ polygons.length }} items
      </span>

      <div class="flex items-center gap-1">
        <UButton
          variant="outline"
          size="sm"
          class="h-7 px-2 text-xs"
          :disabled="!canCollapseAll"
          @click="emit('collapse-all')"
        >
          Collapse All
        </UButton>
        <UButton
          variant="outline"
          size="sm"
          class="h-7 px-2 text-xs"
          :disabled="!canExpandAll"
          @click="emit('expand-all')"
        >
          Expand All
        </UButton>
      </div>
    </div>
    <USeparator />

    <div ref="listRootRef" class="flex-1 min-h-0">
      <div class="p-2">
        <div
          v-if="hasAnyItems"
          role="tree"
          aria-label="Document structure"
          class="relative w-full"
          :style="{ height: `${totalSize}px` }"
        >
          <div
            v-for="row in virtualRows"
            :key="String(row.item.key)"
            class="absolute left-0 top-0 w-full"
            :style="{ transform: `translateY(${row.item.start - scrollMargin}px)` }"
          >
            <EditorSidebarTreeItem
              :item="row.row.item"
              :level="row.row.level"
              :has-children="row.row.hasChildren"
              :is-expanded="row.row.isExpanded"
              :hovered-id="hoveredPolygonId"
              :selected-polygon-id-set="selectedPolygonIdSet"
              :selected-polyline-id-set="selectedPolylineIdSet"
              :hidden-polygon-id-set="hiddenPolygonIdSet"
              :hidden-polyline-id-set="hiddenPolylineIdSet"
              @select-item="(id) => emit('select-polygon', id)"
              @select-polyline="(id) => emit('select-polyline', id)"
              @hover-item="(id) => emit('hover-polygon', id)"
              @hover-polyline="(id) => emit('hover-polyline', id)"
              @unhover-item="emit('unhover-polygon')"
              @delete-item="(id) => emit('delete-item', id)"
              @toggle-visibility="(id) => emit('toggle-visibility', id)"
              @toggle-expanded="(id) => emit('toggle-expanded', id)"
            />
          </div>
        </div>

        <div v-else class="flex flex-col items-center justify-center py-12 text-center px-4">
          <div class="bg-muted rounded-sm p-3 mb-3">
            <Icon name="i-lucide-file-text" class="h-6 w-6 text-muted" />
          </div>
          <h3 class="text-sm font-medium mb-1">
            No document structure
          </h3>
          <p class="text-xs text-muted">
            Start drawing to create regions and textlines
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
