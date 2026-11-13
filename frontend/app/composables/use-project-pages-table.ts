import { moveArrayElement, useSortable } from '@vueuse/integrations/useSortable'
import type { ComputedRef, Ref } from 'vue'
import { createPageSortOrderRequest } from '@/utils/editor/page-sort'
import type { Page, ProjectActionScope, ProjectData } from '@/types/project-page'

export const PROJECT_PAGES_TABLE_ID = 'project-pages-v2'
export const PROJECT_PAGE_ORDER_COLUMN_ID = 'projectOrderPosition'
export const DEFAULT_PROJECT_PAGE_VISIBLE_COLUMN_IDS = ['name', 'description', 'tags', 'workflowState', 'annotationState', 'imageCount', 'updated']
export const PROJECT_PAGE_HIDEABLE_COLUMN_IDS = [
  PROJECT_PAGE_ORDER_COLUMN_ID,
  'name',
  'description',
  'tags',
  'imageCount',
  'workflowState',
  'annotationState',
  'mySubtasks',
  'updated'
]
export const PROJECT_PAGE_TABLE_BODY_CLASS = 'project-pages-sortable-tbody [&>tr]:last:[&>td]:border-b-0'

type ProjectPagesTableOptions = {
  projectId: string
  pages: Ref<Page[] | null | undefined>
  project: Ref<ProjectData | null | undefined>
  canManageProjects: ComputedRef<boolean>
  canDeletePages: ComputedRef<boolean>
  getErrorMessage: (error: unknown, fallback: string) => string
}

export function useProjectPagesTable(options: ProjectPagesTableOptions) {
  const toast = useToast()

  const pagesSafe = computed(() => (options.pages.value ?? []).map((page, index) => {
    const description = getPageDescription(page)
    return {
      ...page,
      description,
      projectOrderPosition: index + 1
    }
  }))
  const {
    sort,
    globalFilter,
    columnFilters,
    tagFilterOperator,
    annotationStatusFilter,
    annotationStatusOptions,
    workflowStateFilter,
    workflowStateOptions,
    activeFilters: activeProjectPageFilters,
    hasContentFilters: hasPageOrderFilters,
    filteredPages,
    uniqueTags,
    selectedTags,
    tagOperatorOptions,
    resetFilters
  } = usePageTableFilters(pagesSafe, {
    tableId: PROJECT_PAGES_TABLE_ID,
    defaultSort: { column: 'projectOrderPosition', direction: 'asc' }
  })

  const selectedPageIds = ref<Set<string>>(new Set())
  const hasSelection = computed(() => selectedPageIds.value.size > 0)
  const canBulkDeletePages = computed(() =>
    options.canDeletePages.value && !options.project.value?.locked
  )

  function togglePageSelection(pageId: string) {
    const newSet = new Set(selectedPageIds.value)
    if (newSet.has(pageId)) {
      newSet.delete(pageId)
    } else {
      newSet.add(pageId)
    }
    selectedPageIds.value = newSet
  }

  function toggleAllPages() {
    if (selectedPageIds.value.size === filteredPages.value.length) {
      selectedPageIds.value = new Set()
    } else {
      selectedPageIds.value = new Set(filteredPages.value.map(page => page.id))
    }
  }

  function clearSelection() {
    selectedPageIds.value = new Set()
  }

  const page = ref(1)
  const itemsPerPageRef = usePersistentTablePageSize(PROJECT_PAGES_TABLE_ID)
  const totalItems = computed(() => filteredPages.value.length)
  const totalPagesCount = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPageRef.value)))
  const itemsPerPage = useItemsPerPageModel(page, itemsPerPageRef, totalItems)
  const { rows: paginatedPages, infiniteScroll } = useTablePagination(filteredPages, page, itemsPerPageRef)

  watch([globalFilter, columnFilters, annotationStatusFilter, workflowStateFilter], () => {
    page.value = 1
  }, { deep: true })

  useClampedPage(page, totalPagesCount)

  const visibleProjectPageRows = ref<Page[]>([])
  const projectPagesTableRef = ref<{ $el?: HTMLElement | null } | null>(null)
  const isPageOrderingMode = ref(false)
  const isSavingPageOrder = ref(false)
  const projectOrderColumnWasHiddenBeforeOrdering = ref<boolean | null>(null)
  const { columnVisibility: projectPageColumnVisibility } = usePersistentTableColumnVisibility(
    PROJECT_PAGES_TABLE_ID,
    PROJECT_PAGE_HIDEABLE_COLUMN_IDS,
    DEFAULT_PROJECT_PAGE_VISIBLE_COLUMN_IDS
  )
  const canEditProjectPageOrder = computed(() =>
    options.canManageProjects.value && !options.project.value?.locked
  )
  const isProjectOrderTableView = computed(() =>
    sort.value.column === 'projectOrderPosition'
    && sort.value.direction === 'asc'
    && !hasPageOrderFilters.value
  )
  const canDragProjectPageOrder = computed(() =>
    isPageOrderingMode.value
    && canEditProjectPageOrder.value
    && isProjectOrderTableView.value
    && !isSavingPageOrder.value
  )
  const projectPageTableUi = computed(() => ({
    tbody: PROJECT_PAGE_TABLE_BODY_CLASS
  }))
  const projectPageTableBody = computed(() =>
    projectPagesTableRef.value?.$el?.querySelector('tbody') ?? null
  )

  watch(paginatedPages, (nextPages) => {
    visibleProjectPageRows.value = [...nextPages]
  }, { immediate: true })

  const pageTableSortable = useSortable(projectPageTableBody, visibleProjectPageRows, {
    animation: 150,
    handle: '.project-page-order-handle',
    draggable: 'tr[data-slot=tr]',
    watchElement: true,
    disabled: true,
    onUpdate: (event) => {
      const oldIndex = event.oldDraggableIndex
      const newIndex = event.newDraggableIndex
      if (typeof oldIndex !== 'number' || typeof newIndex !== 'number') return
      moveArrayElement(visibleProjectPageRows, oldIndex, newIndex, event)
      void nextTick(() => saveProjectPageOrderFromVisibleRows())
    }
  })

  watch(canDragProjectPageOrder, (enabled) => {
    pageTableSortable.option('disabled', !enabled)
  }, { immediate: true })

  function setProjectOrderColumnVisibility(visible: boolean) {
    projectPageColumnVisibility.value = {
      ...projectPageColumnVisibility.value,
      [PROJECT_PAGE_ORDER_COLUMN_ID]: visible
    }
  }

  function showProjectOrderColumnForOrdering() {
    if (projectOrderColumnWasHiddenBeforeOrdering.value === null) {
      projectOrderColumnWasHiddenBeforeOrdering.value = projectPageColumnVisibility.value[PROJECT_PAGE_ORDER_COLUMN_ID] === false
    }
    setProjectOrderColumnVisibility(true)
  }

  function restoreProjectOrderColumnAfterOrdering() {
    const wasHidden = projectOrderColumnWasHiddenBeforeOrdering.value
    projectOrderColumnWasHiddenBeforeOrdering.value = null
    if (wasHidden) {
      setProjectOrderColumnVisibility(false)
    }
  }

  watch(isPageOrderingMode, (enabled) => {
    if (enabled) {
      showProjectOrderColumnForOrdering()
    } else {
      restoreProjectOrderColumnAfterOrdering()
    }
  }, { flush: 'sync' })

  watch(projectPageColumnVisibility, (visibility) => {
    if (!isPageOrderingMode.value || visibility[PROJECT_PAGE_ORDER_COLUMN_ID] !== false) return
    setProjectOrderColumnVisibility(true)
  }, { deep: true })

  onBeforeUnmount(() => {
    if (isPageOrderingMode.value) {
      isPageOrderingMode.value = false
    }
  })

  function togglePageOrderingMode() {
    if (isPageOrderingMode.value) {
      isPageOrderingMode.value = false
      return
    }
    if (!canEditProjectPageOrder.value) return

    resetFilters()
    sort.value = { column: 'projectOrderPosition', direction: 'asc' }
    isPageOrderingMode.value = true
  }

  async function saveProjectPageOrderFromVisibleRows() {
    if (!canDragProjectPageOrder.value || !options.pages.value) {
      visibleProjectPageRows.value = [...paginatedPages.value]
      return
    }

    const currentPages = [...options.pages.value]
    const visibleIds = visibleProjectPageRows.value.map(page => page.id)
    const expectedVisibleIds = paginatedPages.value.map(page => page.id)
    if (visibleIds.length !== expectedVisibleIds.length || visibleIds.every((id, index) => id === expectedVisibleIds[index])) {
      return
    }

    const start = infiniteScroll.enabled ? 0 : (page.value - 1) * itemsPerPageRef.value
    const orderedPages = [
      ...currentPages.slice(0, start),
      ...visibleIds.map(pageId => currentPages.find(page => page.id === pageId)).filter((page): page is Page => Boolean(page)),
      ...currentPages.slice(start + visibleIds.length)
    ]

    if (orderedPages.length !== currentPages.length) {
      visibleProjectPageRows.value = [...paginatedPages.value]
      return
    }

    isSavingPageOrder.value = true
    try {
      const updatedPages = await $fetch<Page[]>(`/api/projects/${options.projectId}/pages/sort-order`, {
        method: 'PUT',
        body: createPageSortOrderRequest(orderedPages)
      })
      options.pages.value = updatedPages
    } catch (error) {
      visibleProjectPageRows.value = [...paginatedPages.value]
      toast.add({
        title: 'Failed to save page order',
        description: options.getErrorMessage(error, 'Could not save the page order.'),
        color: 'error',
        icon: 'i-lucide-triangle-alert'
      })
    } finally {
      isSavingPageOrder.value = false
    }
  }

  function getScopedPageIds(scope: ProjectActionScope): string[] {
    return scope === 'selection' ? Array.from(selectedPageIds.value) : []
  }

  function getPageDescription(page: Page) {
    return getPageDisplayDescription(page)
  }

  return {
    PROJECT_PAGES_TABLE_ID,
    DEFAULT_PROJECT_PAGE_VISIBLE_COLUMN_IDS,
    PROJECT_PAGE_ORDER_COLUMN_ID,
    pagesSafe,
    sort,
    globalFilter,
    columnFilters,
    tagFilterOperator,
    annotationStatusFilter,
    annotationStatusOptions,
    workflowStateFilter,
    workflowStateOptions,
    activeProjectPageFilters,
    filteredPages,
    selectedPageIds,
    hasSelection,
    canBulkDeletePages,
    togglePageSelection,
    toggleAllPages,
    clearSelection,
    page,
    itemsPerPage,
    totalItems,
    totalPagesCount,
    paginatedPages,
    infiniteScroll,
    visibleProjectPageRows,
    projectPagesTableRef,
    isPageOrderingMode,
    isSavingPageOrder,
    canEditProjectPageOrder,
    canDragProjectPageOrder,
    projectPageTableUi,
    togglePageOrderingMode,
    getScopedPageIds,
    uniqueTags,
    selectedTags,
    tagOperatorOptions,
    resetFilters,
    getPageDescription
  }
}
