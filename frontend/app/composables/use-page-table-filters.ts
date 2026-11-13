import type { ComputedRef, Ref } from 'vue'
import type { TableSort } from '@/composables/use-table-filters'
import type { Page } from '@/types/project-page'

export type PageAnnotationStatusFilter = 'all' | 'has_xml' | 'no_xml'
export type PageWorkflowStateFilter = 'all' | 'CHANGES_REQUESTED' | Page['workflowState']

export type PageTableTagOption = { label: string, value: string, count: number }

type FilterablePage = Pick<Page, 'tags' | 'resolvedTags' | 'xmlFileCount' | 'workflowState' | 'changesRequested'>

export const PAGE_ANNOTATION_STATUS_OPTIONS: Array<{ value: PageAnnotationStatusFilter, label: string }> = [
  { value: 'all', label: 'All Pages' },
  { value: 'has_xml', label: 'With Annotation' },
  { value: 'no_xml', label: 'Without Annotation' }
]

const AUTO_CREATED_DESCRIPTION = 'Auto-created from bulk upload'

export function getPageDisplayDescription(page: Pick<Page, 'description'>) {
  const description = page.description?.trim()
  if (!description || description === AUTO_CREATED_DESCRIPTION) return ''
  return page.description
}

export const PAGE_TAG_OPERATOR_OPTIONS = [
  { label: 'Match any (OR)', value: 'or' },
  { label: 'Match all (AND)', value: 'and' }
]

export function usePageTableFilters<T extends FilterablePage>(
  rows: Ref<T[]> | ComputedRef<T[]>,
  options: { tableId: string, defaultSort: TableSort }
) {
  const {
    sort,
    globalFilter,
    columnFilters,
    tagFilterOperator,
    filteredAndSortedData,
    setColumnFilter,
    clearColumnFilter,
    resetAllFilters
  } = useTableFilters(rows, options.defaultSort, options.tableId)

  const annotationStatusFilter = ref<PageAnnotationStatusFilter>('all')
  const workflowStateFilter = ref<PageWorkflowStateFilter>('all')
  const workflowStateOptions: Array<{ value: PageWorkflowStateFilter, label: string }> = [
    { value: 'all', label: 'All states' },
    ...PAGE_WORKFLOW_STATES.map(value => ({ value, label: PAGE_WORKFLOW_STATE_META[value].label })),
    { value: 'CHANGES_REQUESTED', label: 'Changes requested' }
  ]

  const tagLabels = computed(() => {
    const labels = new Map<string, string>()
    for (const row of rows.value) {
      row.resolvedTags?.forEach(tag => labels.set(tag.id, tag.label || tag.id))
    }
    return labels
  })

  function getTagLabel(tagId: string) {
    return tagLabels.value.get(tagId) || tagId
  }

  const uniqueTags = computed<PageTableTagOption[]>(() => {
    const tagCounts = new Map<string, number>()
    for (const row of rows.value) {
      row.tags?.forEach(tag => tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1))
    }
    return Array.from(tagCounts.entries())
      .map(([tag, count]) => ({ label: getTagLabel(tag), value: tag, count }))
      .sort((a, b) => a.label.localeCompare(b.label))
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

  const filteredPages = computed(() => {
    let result = filteredAndSortedData.value
    if (annotationStatusFilter.value !== 'all') {
      const wantsAnnotation = annotationStatusFilter.value === 'has_xml'
      result = result.filter(page => (page.xmlFileCount > 0) === wantsAnnotation)
    }
    if (workflowStateFilter.value !== 'all') {
      const state = workflowStateFilter.value
      result = result.filter(page => state === 'CHANGES_REQUESTED'
        ? page.changesRequested
        : page.workflowState === state)
    }
    return result
  })

  const activeFilters = computed(() => {
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
    if (annotationStatusFilter.value !== 'all') {
      filters.push({
        key: 'annotation',
        label: `Annotation: ${PAGE_ANNOTATION_STATUS_OPTIONS.find(option => option.value === annotationStatusFilter.value)?.label}`,
        clear: () => { annotationStatusFilter.value = 'all' }
      })
    }
    if (workflowStateFilter.value !== 'all') {
      filters.push({
        key: 'workflow-state',
        label: `State: ${workflowStateOptions.find(option => option.value === workflowStateFilter.value)?.label}`,
        clear: () => { workflowStateFilter.value = 'all' }
      })
    }
    return filters
  })

  const hasContentFilters = computed(() => globalFilter.value.trim().length > 0
    || selectedTags.value.length > 0
    || annotationStatusFilter.value !== 'all'
    || workflowStateFilter.value !== 'all')

  function resetFilters() {
    resetAllFilters()
    annotationStatusFilter.value = 'all'
    workflowStateFilter.value = 'all'
  }

  return {
    sort,
    globalFilter,
    columnFilters,
    tagFilterOperator,
    annotationStatusFilter,
    annotationStatusOptions: PAGE_ANNOTATION_STATUS_OPTIONS,
    workflowStateFilter,
    workflowStateOptions,
    activeFilters,
    hasContentFilters,
    filteredPages,
    uniqueTags,
    selectedTags,
    tagOperatorOptions: PAGE_TAG_OPERATOR_OPTIONS,
    resetFilters
  }
}
