<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'
import { useVirtualizer, type VirtualItem } from '@tanstack/vue-virtual'
import ImageItem from './item.vue'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import type { PageData } from '@/stores/editor/types'

const emit = defineEmits<{
  'select-page': [pageId: string, variantId?: string, projectId?: string]
  'unload-page': [pageId: string, projectId?: string]
}>()

const props = withDefaults(defineProps<{
  pages?: PageData[]
  projectId?: string | null
  filter?: string
  onlyWithOpenSubtasks?: boolean
  openSubtaskCountByPage?: Record<string, number>
  filteredPageIds?: string[] | null
}>(), {
  pages: () => [],
  projectId: null,
  filter: '',
  onlyWithOpenSubtasks: false,
  openSubtaskCountByPage: () => ({}),
  filteredPageIds: null
})

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()
const sessionStore = useEditorSessionStore()
const ESTIMATED_ROW_HEIGHT = 400

const currentPageId = computed(() => editorStore.currentPageId)
const scrollMargin = ref(0)
const measuredHeightByPageId = new Map<string, number>()

const filteredPages = computed<PageData[]>(() => {
  const q = props.filter.trim().toLowerCase()
  let result = props.pages

  if (q) {
    result = result.filter(p => (p.label ?? '').toLowerCase().includes(q))
  }

  if (props.onlyWithOpenSubtasks) {
    result = result.filter(p => (props.openSubtaskCountByPage?.[p.id] ?? 0) > 0)
  }

  if (props.filteredPageIds && props.filteredPageIds.length > 0) {
    const pageIdSet = new Set(props.filteredPageIds)
    result = result.filter(p => pageIdSet.has(p.id))
  }

  return result
})

const listRootRef = ref<HTMLElement | null>(null)
const scrollElement = ref<HTMLElement | null>(null)
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
  const nearest = listRootRef.value?.closest('.editor-sidebar-image-scroll')
  if (nearest instanceof HTMLElement) return nearest
  if (import.meta.client) {
    return document.querySelector<HTMLElement>('.editor-sidebar-image-scroll')
  }
  return null
}

const rowVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: filteredPages.value.length,
  getScrollElement: () => scrollElement.value,
  getItemKey: (index) => filteredPages.value[index]?.id ?? index,
  estimateSize: () => ESTIMATED_ROW_HEIGHT,
  overscan: 6,
  scrollMargin: scrollMargin.value,
  measureElement: (element, entry, instance) => {
    const pageId = element.getAttribute('data-page-id')
    const measuredHeight = entry?.contentRect.height ?? element.getBoundingClientRect().height

    if (!pageId) {
      return measuredHeight
    }

    const cachedHeight = measuredHeightByPageId.get(pageId)
    if (instance.scrollDirection === 'backward' && typeof cachedHeight === 'number') {
      return cachedHeight
    }

    measuredHeightByPageId.set(pageId, measuredHeight)
    return measuredHeight
  }
})))

const totalSize = computed(() => rowVirtualizer.value.getTotalSize())
const virtualPageRows = computed<Array<{ item: VirtualItem, page: PageData }>>(() => {
  const pages = filteredPages.value
  return rowVirtualizer.value
    .getVirtualItems()
    .flatMap((item) => {
      const page = pages[item.index]
      return page ? [{ item, page }] : []
    })
})

function measureVirtualRow(el: Element | ComponentPublicInstance | null) {
  const element = el instanceof HTMLElement
    ? el
    : el && '$el' in el && el.$el instanceof HTMLElement
      ? el.$el
      : null

  if (element) {
    rowVirtualizer.value.measureElement(element)
  }
}

function calculateScrollMargin(): number {
  const root = listRootRef.value
  const scroller = scrollElement.value
  if (!root || !scroller) return 0

  const rootRect = root.getBoundingClientRect()
  const scrollerRect = scroller.getBoundingClientRect()
  return Math.max(0, rootRect.top - scrollerRect.top + scroller.scrollTop)
}

function syncVirtualizerLayout(forceMeasure: boolean = false) {
  if (syncLayoutFrameId !== null) {
    cancelAnimationFrame(syncLayoutFrameId)
  }

  syncLayoutFrameId = requestAnimationFrame(() => {
    syncLayoutFrameId = null
    attachScrollElement(resolveScrollElement())

    const nextScrollMargin = calculateScrollMargin()
    const didScrollMarginChange = scrollMargin.value !== nextScrollMargin
    if (scrollMargin.value !== nextScrollMargin) {
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

watch(() => props.projectId, () => {
  syncVirtualizerLayout(true)
})

watch(() => filteredPages.value.length, () => {
  nextTick(() => {
    syncVirtualizerLayout(true)
  })
})

function getDisplayedVariant(page: PageData) {
  return editorStore.getDisplayedVariantForPage(page)
}

function getPreviewUrl(page: PageData) {
  return editorStore.getPreviewUrlForPage(page)
}

function getVariantItems(page: PageData): Array<{ label: string, value: string }> {
  return (page.imageVariants ?? []).map(variant => ({
    label: variant.label,
    value: variant.id
  }))
}

function handleVariantChange(page: PageData, variantId: string) {
  editorStore.setSelectedVariantOverride(page.id, variantId, props.projectId ?? undefined)
  if (props.projectId) {
    sessionStore.setSelectedVariant(props.projectId, page.id, variantId)
  }

  if (page.id === currentPageId.value) {
    const canvasId = editorStore.activeCanvasId
    if (!canvasId) return
    editorStore.switchImageVariantForCanvas(canvasId, variantId)
  }
}

function handlePageClick(page: PageData) {
  const variant = getDisplayedVariant(page)
  emit('select-page', page.id, variant?.id ?? undefined, props.projectId ?? undefined)
}

function handlePageUnload(page: PageData) {
  emit('unload-page', page.id, props.projectId ?? page.projectId)
}
</script>

<template>
  <div ref="listRootRef" :class="editorUiStore.leftCollapsed ? 'px-0  space-y-0 py-0' : 'px-3  space-y-3 py-2'">
    <div v-if="filteredPages.length === 0" class="text-sm text-muted-foreground px-1 py-2">
      No pages match this filter.
    </div>

    <div v-else class="relative w-full" :style="{ height: `${totalSize}px` }">
      <div
        v-for="row in virtualPageRows"
        :key="String(row.item.key)"
        :ref="measureVirtualRow"
        :data-index="row.item.index"
        :data-page-id="row.page.id"
        class="absolute left-0 top-0 w-full"
        :class="editorUiStore.leftCollapsed ? 'pb-1' : 'pb-3'"
        :style="{ transform: `translateY(${row.item.start - scrollMargin}px)` }"
      >
        <ImageItem
          :page="row.page"
          :current-page-id="currentPageId"
          :preview-url="getPreviewUrl(row.page)"
          :variant-items="getVariantItems(row.page)"
          :selected-variant="getDisplayedVariant(row.page)?.id ?? null"
          :open-subtask-count="openSubtaskCountByPage?.[row.page.id] ?? 0"
          @select-page="handlePageClick"
          @variant-change="(id) => handleVariantChange(row.page, id)"
          @unload-page="handlePageUnload"
        />
      </div>
    </div>
  </div>
</template>
