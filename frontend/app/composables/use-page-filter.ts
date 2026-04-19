/**
 * Page filtering state and backend integration.
 * Supports label, text, tag, and confidence filters with AND/OR logic.
 * Persists filter state in session storage per project.
 */

import { normalizeLegacyLabelFilterValues } from '@/utils/editor/page-filter-tokens'

export const PAGE_CONFIDENCE_ELEMENT_TYPE_OPTIONS = [
  { label: 'Page', value: 'PAGE' },
  { label: 'Coords', value: 'COORDS' },
  { label: 'TextEquiv', value: 'TEXTEQUIV' },
  { label: 'ReadingOrder', value: 'READING_ORDER' },
  { label: 'Baseline', value: 'BASELINE' },
  { label: 'AlternativeImage', value: 'ALTERNATIVE_IMAGE' }
] as const

export type PageConfidenceElementType = typeof PAGE_CONFIDENCE_ELEMENT_TYPE_OPTIONS[number]['value']

export interface PageFilterState {
  /** Canonical label filter tokens */
  labelIds: string[]
  /** Text content substring to search for */
  textContent: string
  /** Tag IDs to filter by */
  tags: string[]
  /** Global operator for combining all filters: "and" or "or" */
  filterOperator: 'and' | 'or'
  /** Confidence range [min, max], both inclusive */
  confidenceRange: [number, number]
  /** Selected PAGE XML @conf element types */
  confidenceElementTypes: PageConfidenceElementType[]
  /** Only show pages with open subtasks */
  onlyWithOpenSubtasks: boolean
}

export interface PageFilterResult {
  /** Set of page IDs matching the filter */
  pageIds: Set<string>
  /** Total count of matching pages */
  count: number
}

export interface IndexStats {
  totalPages: number
  indexedTextContentPages: number
  indexedLabelPages: number
  pagesNeedingIndex: number
}

export interface LabelWithCount {
  labelId: string
  pageCount: number
}

const DEFAULT_FILTER_STATE: PageFilterState = {
  labelIds: [],
  textContent: '',
  tags: [],
  filterOperator: 'and',
  confidenceRange: [0, 1],
  confidenceElementTypes: [],
  onlyWithOpenSubtasks: false
}

const VALID_CONFIDENCE_ELEMENT_TYPES = new Set<PageConfidenceElementType>(
  PAGE_CONFIDENCE_ELEMENT_TYPE_OPTIONS.map(option => option.value)
)

const globalFilterState = ref<PageFilterState>({ ...DEFAULT_FILTER_STATE })
const globalFilteredPageIdsArray = ref<string[]>([])
const globalFilteredCount = ref(0)
const globalIsFiltering = ref(false)
const globalFilterError = ref<string | null>(null)
const globalCurrentProjectId = ref<string | null>(null)
const globalFiltersApplied = ref(false)

let watchersInitialized = false

function normalizeConfidenceRange(input: unknown): [number, number] {
  if (!Array.isArray(input) || input.length < 2) {
    return [...DEFAULT_FILTER_STATE.confidenceRange]
  }

  const min = Math.max(0, Math.min(1, Number(input[0] ?? 0)))
  const max = Math.max(0, Math.min(1, Number(input[1] ?? 1)))
  return min <= max ? [min, max] : [max, min]
}

function normalizeConfidenceElementTypes(input: unknown): PageConfidenceElementType[] {
  if (!Array.isArray(input)) return []

  const result: PageConfidenceElementType[] = []
  for (const value of input) {
    if (typeof value !== 'string') continue
    const normalized = value.trim().toUpperCase() as PageConfidenceElementType
    if (VALID_CONFIDENCE_ELEMENT_TYPES.has(normalized)) {
      result.push(normalized)
    }
  }

  return [...new Set(result)]
}

export function isConfidenceFilterActive(state: Pick<PageFilterState, 'confidenceRange' | 'confidenceElementTypes'>): boolean {
  const [min, max] = state.confidenceRange
  return min > 0 || max < 1 || state.confidenceElementTypes.length > 0
}

export function buildPageFilterRequestBody(state: PageFilterState): Record<string, unknown> {
  const confidenceActive = isConfidenceFilterActive(state)

  return {
    textContent: state.textContent.trim().length > 0 ? state.textContent.trim() : null,
    labelIds: state.labelIds.length > 0 ? state.labelIds : null,
    tags: state.tags.length > 0 ? state.tags : null,
    confidenceMin: confidenceActive ? state.confidenceRange[0] : null,
    confidenceMax: confidenceActive ? state.confidenceRange[1] : null,
    confidenceElementTypes: confidenceActive && state.confidenceElementTypes.length > 0
      ? state.confidenceElementTypes
      : null,
    filterOperator: state.filterOperator
  }
}

export function usePageFilter(projectId: Ref<string | undefined>) {
  if (!watchersInitialized) {
    watchersInitialized = true

    watch(globalFilterState, (state) => {
      const currentProject = globalCurrentProjectId.value
      if (currentProject) {
        sessionStorage.setItem(`page-filter-${currentProject}`, JSON.stringify(state))
      }
    }, { deep: true })
  }

  watch(projectId, (newProjectId) => {
    if (newProjectId && newProjectId !== globalCurrentProjectId.value) {
      globalCurrentProjectId.value = newProjectId
      const stored = sessionStorage.getItem(`page-filter-${newProjectId}`)
      if (stored) {
        try {
          const parsed = JSON.parse(stored) as Record<string, unknown>
          globalFilterState.value = {
            labelIds: normalizeLegacyLabelFilterValues(parsed.labelIds),
            textContent: typeof parsed.textContent === 'string' ? parsed.textContent : '',
            tags: Array.isArray(parsed.tags) ? parsed.tags.filter((tag: unknown): tag is string => typeof tag === 'string' && tag.trim().length > 0) : [],
            filterOperator: parsed.filterOperator === 'or' ? 'or' : 'and',
            confidenceRange: normalizeConfidenceRange(parsed.confidenceRange),
            confidenceElementTypes: normalizeConfidenceElementTypes(parsed.confidenceElementTypes),
            onlyWithOpenSubtasks: parsed.onlyWithOpenSubtasks === true
          }
        } catch {
          globalFilterState.value = { ...DEFAULT_FILTER_STATE }
        }
      } else {
        globalFilterState.value = { ...DEFAULT_FILTER_STATE }
      }
      globalFilteredPageIdsArray.value = []
      globalFilteredCount.value = 0
    } else if (!newProjectId && globalCurrentProjectId.value) {
      globalCurrentProjectId.value = null
      globalFilterState.value = { ...DEFAULT_FILTER_STATE }
      globalFilteredPageIdsArray.value = []
      globalFilteredCount.value = 0
    }
  }, { immediate: true })

  const filteredPageIds = computed(() => new Set(globalFilteredPageIdsArray.value))

  const hasActiveFilters = computed(() => {
    return (
      globalFilterState.value.labelIds.length > 0
      || globalFilterState.value.textContent.trim().length > 0
      || globalFilterState.value.tags.length > 0
      || isConfidenceFilterActive(globalFilterState.value)
      || globalFilterState.value.onlyWithOpenSubtasks
    )
  })

  const hasBackendFilters = computed(() => {
    return (
      globalFilterState.value.labelIds.length > 0
      || globalFilterState.value.textContent.trim().length > 0
      || globalFilterState.value.tags.length > 0
      || isConfidenceFilterActive(globalFilterState.value)
    )
  })

  const labelIds = computed({
    get: () => globalFilterState.value.labelIds,
    set: (value) => {
      globalFilterState.value = {
        ...globalFilterState.value,
        labelIds: normalizeLegacyLabelFilterValues(value)
      }
    }
  })

  const textContent = computed({
    get: () => globalFilterState.value.textContent,
    set: (value) => { globalFilterState.value = { ...globalFilterState.value, textContent: value } }
  })

  const tags = computed({
    get: () => globalFilterState.value.tags,
    set: (value) => { globalFilterState.value = { ...globalFilterState.value, tags: value } }
  })

  const filterOperator = computed({
    get: () => globalFilterState.value.filterOperator,
    set: (value) => { globalFilterState.value = { ...globalFilterState.value, filterOperator: value } }
  })

  const confidenceRange = computed({
    get: () => globalFilterState.value.confidenceRange,
    set: (value: [number, number]) => {
      globalFilterState.value = {
        ...globalFilterState.value,
        confidenceRange: normalizeConfidenceRange(value)
      }
    }
  })

  const confidenceElementTypes = computed({
    get: () => globalFilterState.value.confidenceElementTypes,
    set: (value: PageConfidenceElementType[]) => {
      globalFilterState.value = {
        ...globalFilterState.value,
        confidenceElementTypes: normalizeConfidenceElementTypes(value)
      }
    }
  })

  const onlyWithOpenSubtasks = computed({
    get: () => globalFilterState.value.onlyWithOpenSubtasks,
    set: (value) => { globalFilterState.value = { ...globalFilterState.value, onlyWithOpenSubtasks: value } }
  })

  async function applyFilters(): Promise<PageFilterResult> {
    if (!projectId.value) {
      return { pageIds: new Set(), count: 0 }
    }

    if (!hasBackendFilters.value) {
      globalFilteredPageIdsArray.value = []
      globalFilteredCount.value = 0
      globalFiltersApplied.value = false
      return { pageIds: new Set(), count: 0 }
    }

    globalIsFiltering.value = true
    globalFilterError.value = null

    try {
      const response = await $fetch<{ pageIds: string[], count: number }>(
        `/api/projects/${projectId.value}/pages/filter`,
        {
          method: 'POST',
          body: buildPageFilterRequestBody(globalFilterState.value)
        }
      )

      globalFilteredPageIdsArray.value = response.pageIds
      globalFilteredCount.value = response.count
      globalFiltersApplied.value = true

      return { pageIds: new Set(response.pageIds), count: response.count }
    } catch (error: unknown) {
      const message = typeof error === 'object'
        && error !== null
        && 'message' in error
        && typeof (error as { message?: unknown }).message === 'string'
        ? (error as { message: string }).message
        : 'Failed to apply filters'

      globalFilterError.value = message
      console.error('Filter error:', error)
      return { pageIds: new Set(), count: 0 }
    } finally {
      globalIsFiltering.value = false
    }
  }

  function clearFilters() {
    globalFilterState.value = { ...DEFAULT_FILTER_STATE }
    globalFilteredPageIdsArray.value = []
    globalFilteredCount.value = 0
    globalFilterError.value = null
    globalFiltersApplied.value = false
  }

  function clearLabelFilter() {
    globalFilterState.value = { ...globalFilterState.value, labelIds: [] }
  }

  function clearTextContentFilter() {
    globalFilterState.value = { ...globalFilterState.value, textContent: '' }
  }

  function clearTagFilter() {
    globalFilterState.value = { ...globalFilterState.value, tags: [] }
  }

  function clearConfidenceFilter() {
    globalFilterState.value = {
      ...globalFilterState.value,
      confidenceRange: [...DEFAULT_FILTER_STATE.confidenceRange],
      confidenceElementTypes: []
    }
  }

  async function fetchIndexStats(): Promise<IndexStats | null> {
    if (!projectId.value) return null

    try {
      return await $fetch<IndexStats>(`/api/projects/${projectId.value}/pages/index-stats`)
    } catch (error) {
      console.error('Failed to fetch index stats:', error)
      return null
    }
  }

  async function fetchAvailableLabels(): Promise<LabelWithCount[]> {
    if (!projectId.value) return []

    try {
      return await $fetch<LabelWithCount[]>(`/api/projects/${projectId.value}/pages/available-labels`)
    } catch (error) {
      console.error('Failed to fetch available labels:', error)
      return []
    }
  }

  async function rebuildIndex(): Promise<boolean> {
    if (!projectId.value) return false

    try {
      await $fetch(`/api/projects/${projectId.value}/pages/rebuild-index`, {
        method: 'POST'
      })
      return true
    } catch (error) {
      console.error('Failed to rebuild index:', error)
      return false
    }
  }

  async function getMatchingTextLineIds(pageId: string): Promise<string[]> {
    if (!projectId.value || !globalFilterState.value.textContent.trim()) {
      return []
    }

    try {
      const response = await $fetch<{ pageId: string, textLineIds: string[] }>(
        `/api/projects/${projectId.value}/pages/${pageId}/matching-textlines`,
        {
          params: { textContent: globalFilterState.value.textContent }
        }
      )
      return response.textLineIds
    } catch (error) {
      console.error('Failed to get matching text lines:', error)
      return []
    }
  }

  async function getMatchingTextRegionIds(pageId: string): Promise<string[]> {
    if (!projectId.value || !globalFilterState.value.textContent.trim()) {
      return []
    }

    try {
      const response = await $fetch<{ pageId: string, regionIds: string[] }>(
        `/api/projects/${projectId.value}/pages/${pageId}/matching-textregions`,
        {
          params: { textContent: globalFilterState.value.textContent }
        }
      )
      return response.regionIds
    } catch (error) {
      console.error('Failed to get matching text regions:', error)
      return []
    }
  }

  const debouncedApplyFilters = useDebounceFn(applyFilters, 300)

  watch(
    globalFilterState,
    () => {
      if (hasBackendFilters.value && projectId.value) {
        debouncedApplyFilters()
      } else {
        globalFilteredPageIdsArray.value = []
        globalFilteredCount.value = 0
        globalFiltersApplied.value = false
      }
    },
    { deep: true }
  )

  return {
    filterState: readonly(globalFilterState),
    isFiltering: readonly(globalIsFiltering),
    filterError: readonly(globalFilterError),
    filteredPageIds: readonly(filteredPageIds),
    filteredPageIdsArray: readonly(globalFilteredPageIdsArray),
    filteredCount: readonly(globalFilteredCount),
    hasActiveFilters,
    filtersApplied: readonly(globalFiltersApplied),

    labelIds,
    textContent,
    tags,
    filterOperator,
    confidenceRange,
    confidenceElementTypes,
    onlyWithOpenSubtasks,
    hasBackendFilters,

    applyFilters,
    clearFilters,
    clearLabelFilter,
    clearTextContentFilter,
    clearTagFilter,
    clearConfidenceFilter,
    fetchIndexStats,
    fetchAvailableLabels,
    rebuildIndex,
    getMatchingTextLineIds,
    getMatchingTextRegionIds
  }
}
