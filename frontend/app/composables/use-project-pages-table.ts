import { moveArrayElement, useSortable } from '@vueuse/integrations/useSortable'
import { computed, nextTick, onBeforeUnmount, ref, watch, type ComputedRef, type Ref } from 'vue'
import { createPageSortOrderRequest } from '@/utils/editor/page-sort'
import type { Page, ProjectActionScope, ProjectData } from '@/types/project-page'

const AUTO_CREATED_DESCRIPTION = 'Auto-created from bulk upload'
export const PROJECT_PAGES_TABLE_ID = 'project-pages-v2'
export const PROJECT_PAGE_ORDER_COLUMN_ID = 'projectOrderPosition'
export const DEFAULT_PROJECT_PAGE_VISIBLE_COLUMN_IDS = ['name', 'description', 'tags', 'imageCount', 'updated']
export const PROJECT_PAGE_HIDEABLE_COLUMN_IDS = [
  PROJECT_PAGE_ORDER_COLUMN_ID,
  'name',
  'description',
  'tags',
  'imageCount',
  'xmlFileCount',
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
    filteredAndSortedData: filteredAndSortedPages,
    setColumnFilter,
    clearColumnFilter,
    resetAllFilters
  } = useTableFilters(pagesSafe, { column: 'projectOrderPosition', direction: 'asc' })

  const xmlStatusFilter = ref<'all' | 'has_xml' | 'no_xml'>('all')
  const xmlStatusOptions = [
    { value: 'all', label: 'All Pages' },
    { value: 'has_xml', label: 'With XML' },
    { value: 'no_xml', label: 'Without XML' }
  ]

  const activeProjectPageFilters = computed(() => {
    const filters: Array<{ key: string, label: string, clear: () => void }> = []
    if (globalFilter.value) {
      filters.push({
        key: 'search',
        label: `Search: ${globalFilter.value}`,
        clear: () => { globalFilter.value = '' }
      })
    }
    for (const tag of selectedTags.value) {
      filters.push({
        key: `tag-${tag}`,
        label: `Tag: ${getTagLabel(tag)}`,
        clear: () => { selectedTags.value = selectedTags.value.filter(value => value !== tag) }
      })
    }
    if (xmlStatusFilter.value !== 'all') {
      filters.push({
        key: 'xml',
        label: `XML: ${xmlStatusOptions.find(option => option.value === xmlStatusFilter.value)?.label}`,
        clear: () => { xmlStatusFilter.value = 'all' }
      })
    }
    return filters
  })

  const filteredPages = computed(() => {
    let result = filteredAndSortedPages.value
    if (xmlStatusFilter.value !== 'all') {
      result = result.filter((page) => {
        const hasXml = page.xmlFileCount > 0
        return xmlStatusFilter.value === 'has_xml' ? hasXml : !hasXml
      })
    }
    return result
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
  const itemsPerPageRef = ref(10)
  const totalItems = computed(() => filteredPages.value.length)
  const totalPagesCount = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPageRef.value)))
  const itemsPerPage = useItemsPerPageModel(page, itemsPerPageRef, totalItems)
  const paginatedPages = computed(() => {
    const start = (page.value - 1) * itemsPerPageRef.value
    return filteredPages.value.slice(start, start + itemsPerPageRef.value)
  })

  watch([globalFilter, columnFilters, xmlStatusFilter], () => {
    page.value = 1
  }, { deep: true })

  watch(totalPagesCount, (value) => {
    if (page.value > value) {
      page.value = value
    }
  })

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
  const hasPageOrderFilters = computed(() => {
    const tags = columnFilters.value['tags']
    return globalFilter.value.trim().length > 0
      || (Array.isArray(tags) && tags.length > 0)
      || xmlStatusFilter.value !== 'all'
  })
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
    watchElement: true,
    disabled: true,
    onUpdate: (event) => {
      if (typeof event.oldIndex !== 'number' || typeof event.newIndex !== 'number') return
      moveArrayElement(visibleProjectPageRows, event.oldIndex, event.newIndex, event)
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

    const start = (page.value - 1) * itemsPerPageRef.value
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
      toast.add({ title: 'Page order saved', color: 'success', icon: 'i-lucide-check' })
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

  const uniqueTags = computed(() => {
    if (!options.pages.value) return []
    const tagCounts = new Map<string, number>()
    const tagLabels = new Map<string, string>()
    options.pages.value.forEach((page) => {
      page.resolvedTags?.forEach((tag) => {
        tagLabels.set(tag.id, tag.label || tag.id)
      })
      page.tags.forEach((tag) => {
        tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1)
      })
    })
    return Array.from(tagCounts.entries())
      .sort((a, b) => getTagLabel(a[0], tagLabels).localeCompare(getTagLabel(b[0], tagLabels)))
      .map(([tag, count]) => ({ label: getTagLabel(tag, tagLabels), value: tag, count }))
  })

  const selectedTags = computed({
    get: () => {
      const tags = columnFilters.value['tags']
      return Array.isArray(tags) ? tags : []
    },
    set: (value: string[]) => {
      if (value.length === 0) {
        clearColumnFilter('tags')
      } else {
        setColumnFilter('tags', value)
      }
    }
  })

  const tagOperatorOptions = [
    { label: 'Match any (OR)', value: 'or' },
    { label: 'Match all (AND)', value: 'and' }
  ]

  function resetFilters() {
    resetAllFilters()
    xmlStatusFilter.value = 'all'
  }

  function getTagLabel(tagId: string, labels = currentTagLabels.value) {
    return labels.get(tagId) || tagId
  }

  const currentTagLabels = computed(() => {
    const labels = new Map<string, string>()
    options.pages.value?.forEach((page) => {
      page.resolvedTags?.forEach((tag) => {
        labels.set(tag.id, tag.label || tag.id)
      })
    })
    return labels
  })

  function getPageDescription(page: Page) {
    const description = page.description?.trim()
    if (!description || description === AUTO_CREATED_DESCRIPTION) return ''
    return page.description
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
    xmlStatusFilter,
    xmlStatusOptions,
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
