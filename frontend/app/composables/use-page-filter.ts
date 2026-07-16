/**
 * Page filtering state and backend integration.
 * Supports label, text, tag, and confidence filters with AND/OR logic.
 * Persists filter state in session storage per project.
 */

import { normalizeLegacyLabelFilterValues } from '@/utils/editor/page-filter-tokens'
import { extractApiErrorMessage } from '@/utils/api-error'
import type { PageWorkflowState } from '@/types/project-page'

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
  /** Only show pages with at least one non-empty PAGE XML comment value */
  hasComments: boolean
  /** Only show pages with open subtasks */
  onlyWithOpenSubtasks: boolean
  /** Workflow states shown in the editor image list */
  workflowStates: PageWorkflowState[]
}

type BackendPageFilterState = Omit<PageFilterState, 'workflowStates'>

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
  hasComments: false,
  onlyWithOpenSubtasks: false,
  workflowStates: []
}

const VALID_CONFIDENCE_ELEMENT_TYPES = new Set<PageConfidenceElementType>(
  PAGE_CONFIDENCE_ELEMENT_TYPE_OPTIONS.map(option => option.value)
)
const VALID_WORKFLOW_STATES = new Set<PageWorkflowState>(['OPEN', 'IN_PROGRESS', 'DONE'])

const globalFilterState = ref<PageFilterState>({ ...DEFAULT_FILTER_STATE })
const globalFilteredPageIdsArray = ref<string[]>([])
const globalFilteredCount = ref(0)
const globalIsFiltering = ref(false)
const globalFilterError = ref<string | null>(null)
const globalCurrentProjectId = ref<string | null>(null)
const globalFiltersApplied = ref(false)
const globalIndexStatsCache = ref<Record<string, IndexStats>>({})
const globalAvailableLabelsCache = ref<Record<string, LabelWithCount[]>>({})

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

function normalizeWorkflowStates(input: unknown): PageWorkflowState[] {
  if (!Array.isArray(input)) return []

  return [...new Set(input.filter((value): value is PageWorkflowState =>
    typeof value === 'string' && VALID_WORKFLOW_STATES.has(value as PageWorkflowState)
  ))]
}

export function isConfidenceFilterActive(state: Pick<PageFilterState, 'confidenceRange' | 'confidenceElementTypes'>): boolean {
  const [min, max] = state.confidenceRange
  return min > 0 || max < 1 || state.confidenceElementTypes.length > 0
}

export function buildPageFilterRequestBody(state: BackendPageFilterState): Record<string, unknown> {
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
    hasComments: state.hasComments ? true : null,
    filterOperator: state.filterOperator
  }
}

export function usePageFilter(projectId: Ref<string | undefined>) {
  const { reportIssue, resolveIssue } = useStatusIssues()

  const pageFilterIssueId = (suffix: string) => `page-filter:${projectId.value || 'unknown'}:${suffix}`

  const reportPageFilterIssue = (
    suffix: string,
    title: string,
    fallback: string,
    error: unknown,
    retry?: () => Promise<unknown>
  ) => {
    reportIssue({
      id: pageFilterIssueId(suffix),
      source: 'page-filter',
      severity: 'warning',
      title,
      message: extractApiErrorMessage(error, fallback),
      retryLabel: retry ? 'Retry' : undefined,
      retry
    })
  }

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
            hasComments: parsed.hasComments === true,
            onlyWithOpenSubtasks: parsed.onlyWithOpenSubtasks === true,
            workflowStates: normalizeWorkflowStates(parsed.workflowStates)
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
      || globalFilterState.value.hasComments
      || globalFilterState.value.onlyWithOpenSubtasks
      || globalFilterState.value.workflowStates.length > 0
    )
  })

  const hasBackendFilters = computed(() => {
    return (
      globalFilterState.value.labelIds.length > 0
      || globalFilterState.value.textContent.trim().length > 0
      || globalFilterState.value.tags.length > 0
      || isConfidenceFilterActive(globalFilterState.value)
      || globalFilterState.value.hasComments
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

  const hasComments = computed({
    get: () => globalFilterState.value.hasComments,
    set: (value) => { globalFilterState.value = { ...globalFilterState.value, hasComments: Boolean(value) } }
  })

  const onlyWithOpenSubtasks = computed({
    get: () => globalFilterState.value.onlyWithOpenSubtasks,
    set: (value) => { globalFilterState.value = { ...globalFilterState.value, onlyWithOpenSubtasks: value } }
  })

  const workflowStates = computed({
    get: () => globalFilterState.value.workflowStates,
    set: (value: PageWorkflowState[]) => {
      globalFilterState.value = {
        ...globalFilterState.value,
        workflowStates: normalizeWorkflowStates(value)
      }
    }
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

  function clearWorkflowStateFilter() {
    globalFilterState.value = { ...globalFilterState.value, workflowStates: [] }
  }

  async function fetchIndexStats(): Promise<IndexStats | null> {
    if (!projectId.value) return null

    try {
      const stats = await $fetch<IndexStats>(`/api/projects/${projectId.value}/pages/index-stats`)
      globalIndexStatsCache.value = {
        ...globalIndexStatsCache.value,
        [projectId.value]: stats
      }
      resolveIssue(pageFilterIssueId('index-stats'))
      return stats
    } catch (error) {
      console.error('Failed to fetch index stats:', error)
      reportPageFilterIssue(
        'index-stats',
        'Page filter stats unavailable',
        'Could not refresh page filter index statistics. Last known values are still shown when available.',
        error,
        async () => {
          await fetchIndexStats()
        }
      )
      return globalIndexStatsCache.value[projectId.value] ?? null
    }
  }

  async function fetchAvailableLabels(): Promise<LabelWithCount[]> {
    if (!projectId.value) return []

    try {
      const labels = await $fetch<LabelWithCount[]>(`/api/projects/${projectId.value}/pages/available-labels`)
      globalAvailableLabelsCache.value = {
        ...globalAvailableLabelsCache.value,
        [projectId.value]: labels
      }
      resolveIssue(pageFilterIssueId('available-labels'))
      return labels
    } catch (error) {
      console.error('Failed to fetch available labels:', error)
      reportPageFilterIssue(
        'available-labels',
        'Available labels unavailable',
        'Could not refresh available page filter labels. Last known labels are still shown when available.',
        error,
        async () => {
          await fetchAvailableLabels()
        }
      )
      return globalAvailableLabelsCache.value[projectId.value] ?? []
    }
  }

  async function rebuildIndex(): Promise<boolean> {
    if (!projectId.value) return false

    try {
      await $fetch(`/api/projects/${projectId.value}/pages/rebuild-index`, {
        method: 'POST'
      })
      resolveIssue(pageFilterIssueId('rebuild-index'))
      return true
    } catch (error) {
      console.error('Failed to rebuild index:', error)
      reportPageFilterIssue(
        'rebuild-index',
        'Page filter rebuild failed',
        'Could not rebuild the page filter index.',
        error,
        async () => {
          await rebuildIndex()
        }
      )
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
      resolveIssue(pageFilterIssueId('matching-textlines'))
      return response.textLineIds
    } catch (error) {
      console.error('Failed to get matching text lines:', error)
      reportPageFilterIssue(
        'matching-textlines',
        'Text line highlights unavailable',
        'Could not load matching text line highlights for the current filter.',
        error,
        async () => {
          await getMatchingTextLineIds(pageId)
        }
      )
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
      resolveIssue(pageFilterIssueId('matching-textregions'))
      return response.regionIds
    } catch (error) {
      console.error('Failed to get matching text regions:', error)
      reportPageFilterIssue(
        'matching-textregions',
        'Text region highlights unavailable',
        'Could not load matching text region highlights for the current filter.',
        error,
        async () => {
          await getMatchingTextRegionIds(pageId)
        }
      )
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
    hasComments,
    onlyWithOpenSubtasks,
    workflowStates,
    hasBackendFilters,

    applyFilters,
    clearFilters,
    clearLabelFilter,
    clearTextContentFilter,
    clearTagFilter,
    clearConfidenceFilter,
    clearWorkflowStateFilter,
    fetchIndexStats,
    fetchAvailableLabels,
    rebuildIndex,
    getMatchingTextLineIds,
    getMatchingTextRegionIds
  }
}
