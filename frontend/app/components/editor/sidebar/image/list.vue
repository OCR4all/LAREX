<script setup lang="ts">
import { useVirtualizer, type VirtualItem } from '@tanstack/vue-virtual'
import ImageItem from './item.vue'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import type { PageData } from '@/stores/editor/types'
import { getVerticalScrollDirection, getVerticalVisibilityDirection } from '@/utils/editor/vertical-visibility'

const IMAGE_CARD_ASPECT_RATIO = 4 / 3
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
  visible?: boolean
}>(), {
  pages: () => [],
  projectId: null,
  filter: '',
  onlyWithOpenSubtasks: false,
  openSubtaskCountByPage: () => ({}),
  filteredPageIds: null,
  visible: true
})

const editorStore = useEditorStore()
const sessionStore = useEditorSessionStore()
const ESTIMATED_ROW_HEIGHT = 400
const BACK_TO_SELECTION_OVERLAY_TOP_OFFSET = 12
const BACK_TO_SELECTION_VISIBILITY_PADDING = 8
const ACTIVE_PAGE_CENTER_MAX_RETRIES = 30

const currentPageId = computed(() => editorStore.currentPageId)
const scrollMargin = ref(0)
const estimatedRowHeight = ref(ESTIMATED_ROW_HEIGHT)
const scrollViewport = ref({ top: 0, height: 0 })
const backToSelectionOverlayAnchor = ref<{ top: number, left: number } | null>(null)
const canRenderBackToSelectionOverlay = ref(false)

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
const filteredPageIdsSignature = computed(() => filteredPages.value.map(page => page.id).join('|'))

const listRootRef = ref<HTMLElement | null>(null)
const scrollElement = ref<HTMLElement | null>(null)
let resizeObserver: ResizeObserver | null = null
let syncLayoutFrameId: number | null = null
let shouldCenterActivePageAfterLayout = false
let activePageCenterBehavior: 'auto' | 'smooth' = 'auto'
let activePageCenterRetryCount = 0

function updateScrollViewport() {
  const scroller = scrollElement.value
  scrollViewport.value = scroller
    ? { top: scroller.scrollTop, height: scroller.clientHeight }
    : { top: 0, height: 0 }
}

function createsFixedContainingBlock(element: HTMLElement): boolean {
  const styles = window.getComputedStyle(element)
  return styles.transform !== 'none'
    || styles.perspective !== 'none'
    || styles.filter !== 'none'
    || styles.backdropFilter !== 'none'
    || styles.contain.includes('paint')
    || styles.contain.includes('layout')
    || styles.contain.includes('strict')
    || styles.contain.includes('content')
    || styles.willChange.split(',').some(value => ['transform', 'perspective', 'filter'].includes(value.trim()))
}

function getFixedContainingBlock(element: HTMLElement): HTMLElement | null {
  let parent = element.parentElement
  while (parent) {
    if (createsFixedContainingBlock(parent)) return parent
    parent = parent.parentElement
  }
  return null
}

function updateBackToSelectionOverlayAnchor() {
  const scroller = scrollElement.value
  const listRoot = listRootRef.value
  if (!scroller || !listRoot || !import.meta.client) {
    backToSelectionOverlayAnchor.value = null
    return
  }

  const scrollerRect = scroller.getBoundingClientRect()
  const listRootRect = listRoot.getBoundingClientRect()
  const containingBlockRect = getFixedContainingBlock(scroller)?.getBoundingClientRect()

  backToSelectionOverlayAnchor.value = {
    top: scrollerRect.top - (containingBlockRect?.top ?? 0) + BACK_TO_SELECTION_OVERLAY_TOP_OFFSET,
    left: listRootRect.left - (containingBlockRect?.left ?? 0) + listRootRect.width / 2
  }
}

function attachScrollElement(nextScrollElement: HTMLElement | null) {
  const previousScrollElement = scrollElement.value
  if (previousScrollElement === nextScrollElement) return

  if (previousScrollElement) {
    previousScrollElement.removeEventListener('scroll', updateScrollViewport)
    resizeObserver?.unobserve(previousScrollElement)
  }

  scrollElement.value = nextScrollElement

  if (nextScrollElement) {
    nextScrollElement.addEventListener('scroll', updateScrollViewport, { passive: true })
    updateScrollViewport()
    updateBackToSelectionOverlayAnchor()
    resizeObserver?.observe(nextScrollElement)
  } else {
    updateScrollViewport()
    updateBackToSelectionOverlayAnchor()
  }
}

function resolveScrollElement(): HTMLElement | null {
  const nearest = listRootRef.value?.closest('.editor-sidebar-image-scroll')
  if (nearest instanceof HTMLElement) return nearest
  return null
}

const rowVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: filteredPages.value.length,
  getScrollElement: () => scrollElement.value,
  getItemKey: index => filteredPages.value[index]?.id ?? index,
  estimateSize: () => estimatedRowHeight.value,
  overscan: 6,
  scrollMargin: scrollMargin.value
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

const activePageIndex = computed(() => {
  const pageId = currentPageId.value
  if (!pageId) return -1
  return filteredPages.value.findIndex(page => page.id === pageId)
})

const activePageScrollPosition = computed(() => {
  const index = activePageIndex.value
  if (index < 0) return null
  return rowVirtualizer.value.getOffsetForIndex(index, 'start')?.[0] ?? null
})

const activePageBounds = computed(() => {
  const index = activePageIndex.value
  const start = activePageScrollPosition.value
  if (index < 0 || start === null) return null

  const measuredItem = rowVirtualizer.value.getVirtualItems().find(item => item.index === index)
  return {
    start,
    end: start + (measuredItem?.size ?? estimatedRowHeight.value)
  }
})

const backToSelectionDirection = computed<'up' | 'down' | null>(() => {
  const { top, height } = scrollViewport.value
  if (height <= 0) return null

  const viewportBottom = top + height
  const activeIndex = activePageIndex.value
  const scroller = scrollElement.value
  const renderedActiveRow = activeIndex >= 0
    ? listRootRef.value?.querySelector<HTMLElement>(`[data-index="${activeIndex}"]`)
    : null

  let visibilityDirection: 'up' | 'down' | null

  if (scroller && renderedActiveRow) {
    const scrollerRect = scroller.getBoundingClientRect()
    const activeRowRect = renderedActiveRow.getBoundingClientRect()
    visibilityDirection = getVerticalVisibilityDirection(
      { top: activeRowRect.top, bottom: activeRowRect.bottom },
      { top: scrollerRect.top, bottom: scrollerRect.bottom },
      BACK_TO_SELECTION_VISIBILITY_PADDING
    )
  } else {
    const activeBounds = activePageBounds.value
    if (!activeBounds) return null

    visibilityDirection = getVerticalVisibilityDirection(
      { top: activeBounds.start, bottom: activeBounds.end },
      { top, bottom: viewportBottom },
      BACK_TO_SELECTION_VISIBILITY_PADDING
    )
  }

  if (visibilityDirection === null || activeIndex < 0) return null

  const centerScrollPosition = rowVirtualizer.value.getOffsetForIndex(activeIndex, 'center')?.[0]
  if (centerScrollPosition === undefined) return visibilityDirection

  return getVerticalScrollDirection(top, centerScrollPosition)
})

const showBackToSelection = computed(() => backToSelectionDirection.value !== null)
const showBackToSelectionOverlay = computed(() =>
  canRenderBackToSelectionOverlay.value
  && backToSelectionOverlayAnchor.value !== null
  && scrollElement.value !== null
)
const backToSelectionIcon = computed(() =>
  backToSelectionDirection.value === 'up'
    ? 'i-lucide-arrow-up'
    : 'i-lucide-arrow-down'
)
const backToSelectionTooltip = computed(() =>
  backToSelectionDirection.value === 'up'
    ? 'Back to selection above'
    : 'Back to selection below'
)
const backToSelectionOverlayStyle = computed(() => {
  const anchor = backToSelectionOverlayAnchor.value
  return {
    top: `${anchor?.top ?? 0}px`,
    left: `${anchor?.left ?? 0}px`
  }
})

function scrollToActivePage(behavior: 'auto' | 'smooth' = 'smooth') {
  const index = activePageIndex.value
  if (index < 0) return

  rowVirtualizer.value.scrollToIndex(index, {
    align: 'center',
    behavior
  })
}

function scheduleActivePageCenter(behavior: 'auto' | 'smooth') {
  if (!props.visible || activePageIndex.value < 0) return

  shouldCenterActivePageAfterLayout = true
  activePageCenterBehavior = behavior
  activePageCenterRetryCount = 0
  syncVirtualizerLayout(true)
}

function calculateEstimatedRowHeight(): number {
  const root = listRootRef.value
  if (!root) return ESTIMATED_ROW_HEIGHT

  const styles = window.getComputedStyle(root)
  const paddingLeft = Number.parseFloat(styles.paddingLeft) || 0
  const paddingRight = Number.parseFloat(styles.paddingRight) || 0
  const usableWidth = Math.max(0, root.clientWidth - paddingLeft - paddingRight)
  if (usableWidth <= 0) return ESTIMATED_ROW_HEIGHT

  return Math.round(usableWidth * IMAGE_CARD_ASPECT_RATIO + 12)
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

    const nextEstimatedRowHeight = calculateEstimatedRowHeight()
    const didEstimatedHeightChange = estimatedRowHeight.value !== nextEstimatedRowHeight
    if (didEstimatedHeightChange) {
      estimatedRowHeight.value = nextEstimatedRowHeight
    }

    const nextScrollMargin = calculateScrollMargin()
    const didScrollMarginChange = scrollMargin.value !== nextScrollMargin
    if (scrollMargin.value !== nextScrollMargin) {
      scrollMargin.value = nextScrollMargin
    }

    updateBackToSelectionOverlayAnchor()

    if (forceMeasure || didScrollMarginChange || didEstimatedHeightChange) {
      rowVirtualizer.value.measure()
    }

    if (shouldCenterActivePageAfterLayout) {
      const scroller = scrollElement.value
      if (!scroller || scroller.clientHeight <= 0) {
        if (activePageCenterRetryCount < ACTIVE_PAGE_CENTER_MAX_RETRIES) {
          activePageCenterRetryCount++
          syncVirtualizerLayout(true)
        } else {
          shouldCenterActivePageAfterLayout = false
          activePageCenterRetryCount = 0
        }
        return
      }

      shouldCenterActivePageAfterLayout = false
      activePageCenterRetryCount = 0
      scrollToActivePage(activePageCenterBehavior)
    }
  })
}

onMounted(() => {
  canRenderBackToSelectionOverlay.value = true
  if (import.meta.client && typeof ResizeObserver !== 'undefined' && listRootRef.value) {
    resizeObserver = new ResizeObserver(() => {
      const wasHidden = scrollViewport.value.height <= 0
      updateScrollViewport()
      updateBackToSelectionOverlayAnchor()
      if (props.visible && wasHidden && scrollViewport.value.height > 0) {
        scheduleActivePageCenter('auto')
      } else {
        syncVirtualizerLayout()
      }
    })
    resizeObserver.observe(listRootRef.value)
  }
  if (import.meta.client) {
    window.addEventListener('resize', updateBackToSelectionOverlayAnchor, { passive: true })
  }
  syncVirtualizerLayout(true)
})

onBeforeUnmount(() => {
  canRenderBackToSelectionOverlay.value = false
  if (import.meta.client) {
    window.removeEventListener('resize', updateBackToSelectionOverlayAnchor)
  }
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

watch(filteredPageIdsSignature, () => {
  nextTick(() => {
    updateScrollViewport()
    if (activePageIndex.value >= 0) {
      scheduleActivePageCenter('auto')
    } else {
      syncVirtualizerLayout(true)
    }
  })
})

watch(() => props.visible, (visible) => {
  if (!visible) {
    shouldCenterActivePageAfterLayout = false
    activePageCenterRetryCount = 0
    return
  }

  nextTick(() => {
    scheduleActivePageCenter('auto')
  })
}, { immediate: true, flush: 'post' })

watch(currentPageId, () => {
  nextTick(() => {
    updateScrollViewport()
    scheduleActivePageCenter('smooth')
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
  <div ref="listRootRef" class="relative px-3 py-2">
    <Teleport v-if="showBackToSelectionOverlay && scrollElement" :to="scrollElement">
      <div
        class="fixed z-50 pointer-events-none -translate-x-1/2 transition-opacity duration-150 ease-out"
        :class="showBackToSelection ? 'opacity-100' : 'opacity-0'"
        :style="backToSelectionOverlayStyle"
      >
        <UTooltip :text="backToSelectionTooltip" :content="{ side: 'right' }">
          <UButton
            color="primary"
            variant="solid"
            size="sm"
            :icon="backToSelectionIcon"
            square
            :class="[
              'rounded-full shadow-xl ring-1 ring-default',
              showBackToSelection ? 'pointer-events-auto' : 'pointer-events-none'
            ]"
            :aria-label="backToSelectionTooltip"
            @click.stop="scrollToActivePage()"
          />
        </UTooltip>
      </div>
    </Teleport>

    <div v-if="filteredPages.length === 0" class="text-sm text-muted px-1 py-2">
      No pages match this filter.
    </div>

    <div v-else class="relative w-full" :style="{ height: `${totalSize}px` }">
      <div
        v-for="row in virtualPageRows"
        :key="String(row.item.key)"
        :data-index="row.item.index"
        class="absolute left-0 top-0 w-full pb-3"
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
