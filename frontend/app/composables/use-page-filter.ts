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
export type AnnotationPresence = 'with_xml' | 'without_xml' | null
export type XmlAttributeOperator = 'exists' | 'not_exists' | 'equals' | 'not_equals' | 'contains' | 'not_contains'
export type SingletonPageFilterType = 'workflowStates' | 'annotationPresence' | 'labels' | 'textContent' | 'tags' | 'confidence' | 'comments' | 'openSubtasks'
export type PageFilterType = SingletonPageFilterType | 'xmlAttribute'

export interface XmlAttributeFilterRow {
  id: string
  elementName: string
  attributeName: string
  operator: XmlAttributeOperator
  value: string
}

export interface PageFilterState {
  labelIds: string[]
  textContent: string
  tags: string[]
  filterOperator: 'and' | 'or'
  confidenceRange: [number, number]
  confidenceElementTypes: PageConfidenceElementType[]
  hasComments: boolean
  commentText: string
  onlyWithOpenSubtasks: boolean
  workflowStates: PageWorkflowState[]
  annotationPresence: AnnotationPresence
  xmlAttributeFilters: XmlAttributeFilterRow[]
  visibleFilters: SingletonPageFilterType[]
}

export interface PageFilterResult {
  pageIds: Set<string>
  count: number
}

export interface IndexStats {
  totalPages: number
  indexedTextContentPages: number
  indexedLabelPages: number
  indexedXmlAttributePages: number
  pagesNeedingIndex: number
}

export interface LabelWithCount {
  labelId: string
  pageCount: number
}

export interface XmlAttributeWithCount {
  elementName: string
  attributeName: string
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
  commentText: '',
  onlyWithOpenSubtasks: false,
  workflowStates: [],
  annotationPresence: null,
  xmlAttributeFilters: [],
  visibleFilters: []
}

const VALID_CONFIDENCE_ELEMENT_TYPES = new Set<PageConfidenceElementType>(PAGE_CONFIDENCE_ELEMENT_TYPE_OPTIONS.map(option => option.value))
const VALID_WORKFLOW_STATES = new Set<PageWorkflowState>(['OPEN', 'IN_PROGRESS', 'DONE'])
const VALID_SINGLETON_FILTERS = new Set<SingletonPageFilterType>([
  'workflowStates', 'annotationPresence', 'labels', 'textContent', 'tags', 'confidence', 'comments', 'openSubtasks'
])
const VALID_XML_OPERATORS = new Set<XmlAttributeOperator>([
  'exists', 'not_exists', 'equals', 'not_equals', 'contains', 'not_contains'
])
const VALUE_XML_OPERATORS = new Set<XmlAttributeOperator>(['equals', 'not_equals', 'contains', 'not_contains'])

const globalFilterState = ref<PageFilterState>(cloneDefaultState())
const globalFilteredPageIdsByProjectId = ref<Record<string, string[]>>({})
const globalIsFiltering = ref(false)
const globalFilterError = ref<string | null>(null)
const globalCurrentProjectId = ref<string | null>(null)
const globalIndexStatsCache = ref<Record<string, IndexStats>>({})
const globalAvailableLabelsCache = ref<Record<string, LabelWithCount[]>>({})
const globalAvailableXmlAttributesCache = ref<Record<string, XmlAttributeWithCount[]>>({})
let persistenceWatcherInitialized = false
let requestGeneration = 0
let rowSequence = 0

function cloneDefaultState(): PageFilterState {
  return {
    ...DEFAULT_FILTER_STATE,
    labelIds: [],
    tags: [],
    confidenceRange: [0, 1],
    confidenceElementTypes: [],
    workflowStates: [],
    xmlAttributeFilters: [],
    visibleFilters: []
  }
}

function createRowId(): string {
  rowSequence += 1
  return `xml-attribute-${Date.now()}-${rowSequence}`
}

function normalizeConfidenceRange(input: unknown): [number, number] {
  if (!Array.isArray(input) || input.length < 2) return [0, 1]
  const min = Math.max(0, Math.min(1, Number(input[0] ?? 0)))
  const max = Math.max(0, Math.min(1, Number(input[1] ?? 1)))
  return min <= max ? [min, max] : [max, min]
}

function normalizeConfidenceElementTypes(input: unknown): PageConfidenceElementType[] {
  if (!Array.isArray(input)) return []
  return [...new Set(input.flatMap((value) => {
    if (typeof value !== 'string') return []
    const normalized = value.trim().toUpperCase() as PageConfidenceElementType
    return VALID_CONFIDENCE_ELEMENT_TYPES.has(normalized) ? [normalized] : []
  }))]
}

function normalizeWorkflowStates(input: unknown): PageWorkflowState[] {
  if (!Array.isArray(input)) return []
  return [...new Set(input.filter((value): value is PageWorkflowState =>
    typeof value === 'string' && VALID_WORKFLOW_STATES.has(value as PageWorkflowState)
  ))]
}

function normalizeXmlAttributeFilters(input: unknown): XmlAttributeFilterRow[] {
  if (!Array.isArray(input)) return []
  return input.flatMap((raw) => {
    if (!raw || typeof raw !== 'object') return []
    const value = raw as Record<string, unknown>
    const operator = typeof value.operator === 'string' && VALID_XML_OPERATORS.has(value.operator as XmlAttributeOperator)
      ? value.operator as XmlAttributeOperator
      : 'exists'
    return [{
      id: typeof value.id === 'string' && value.id ? value.id : createRowId(),
      elementName: typeof value.elementName === 'string' ? value.elementName : '',
      attributeName: typeof value.attributeName === 'string' ? value.attributeName : '',
      operator,
      value: typeof value.value === 'string' ? value.value : ''
    }]
  })
}

function deriveVisibleFilters(parsed: Record<string, unknown>, state: Omit<PageFilterState, 'visibleFilters'>): SingletonPageFilterType[] {
  if (Array.isArray(parsed.visibleFilters)) {
    return [...new Set(parsed.visibleFilters.filter((value): value is SingletonPageFilterType =>
      typeof value === 'string' && VALID_SINGLETON_FILTERS.has(value as SingletonPageFilterType)
    ))]
  }
  const visible: SingletonPageFilterType[] = []
  if (state.workflowStates.length) visible.push('workflowStates')
  if (state.annotationPresence) visible.push('annotationPresence')
  if (state.labelIds.length) visible.push('labels')
  if (state.textContent.trim()) visible.push('textContent')
  if (state.tags.length) visible.push('tags')
  if (isConfidenceFilterActive(state)) visible.push('confidence')
  if (state.hasComments) visible.push('comments')
  if (state.onlyWithOpenSubtasks) visible.push('openSubtasks')
  return visible
}

export function normalizeStoredPageFilterState(parsed: Record<string, unknown>): PageFilterState {
  const normalizedAnnotationPresence: AnnotationPresence = parsed.annotationPresence === 'with_xml' || parsed.annotationPresence === 'without_xml'
    ? parsed.annotationPresence
    : null
  const normalizedCommentText = typeof parsed.commentText === 'string' ? parsed.commentText : ''
  const state = {
    labelIds: normalizeLegacyLabelFilterValues(parsed.labelIds),
    textContent: typeof parsed.textContent === 'string' ? parsed.textContent : '',
    tags: Array.isArray(parsed.tags) ? parsed.tags.filter((tag): tag is string => typeof tag === 'string' && tag.trim().length > 0) : [],
    filterOperator: parsed.filterOperator === 'or' ? 'or' as const : 'and' as const,
    confidenceRange: normalizeConfidenceRange(parsed.confidenceRange),
    confidenceElementTypes: normalizeConfidenceElementTypes(parsed.confidenceElementTypes),
    hasComments: parsed.hasComments === true || normalizedCommentText.trim().length > 0,
    commentText: normalizedCommentText,
    onlyWithOpenSubtasks: parsed.onlyWithOpenSubtasks === true,
    workflowStates: normalizeWorkflowStates(parsed.workflowStates),
    annotationPresence: normalizedAnnotationPresence,
    xmlAttributeFilters: normalizeXmlAttributeFilters(parsed.xmlAttributeFilters)
  }
  return { ...state, visibleFilters: deriveVisibleFilters(parsed, state) }
}

export function isConfidenceFilterActive(state: {
  readonly confidenceRange: readonly [number, number]
  readonly confidenceElementTypes: readonly PageConfidenceElementType[]
}): boolean {
  const [min, max] = state.confidenceRange
  return min > 0 || max < 1 || state.confidenceElementTypes.length > 0
}

export function isXmlAttributeFilterComplete(row: XmlAttributeFilterRow): boolean {
  if (!row.attributeName.trim()) return false
  return !VALUE_XML_OPERATORS.has(row.operator) || row.value.length > 0
}

export function activePageFilterCount(state: PageFilterState): number {
  return Number(state.labelIds.length > 0)
    + Number(state.textContent.trim().length > 0)
    + Number(state.tags.length > 0)
    + Number(isConfidenceFilterActive(state))
    + Number(state.hasComments)
    + Number(state.onlyWithOpenSubtasks)
    + Number(state.workflowStates.length > 0)
    + Number(state.annotationPresence !== null)
    + state.xmlAttributeFilters.filter(isXmlAttributeFilterComplete).length
}

export function buildPageFilterRequestBody(state: {
  readonly labelIds: readonly string[]
  readonly textContent: string
  readonly tags: readonly string[]
  readonly filterOperator: 'and' | 'or'
  readonly confidenceRange: readonly [number, number]
  readonly confidenceElementTypes: readonly PageConfidenceElementType[]
  readonly hasComments: boolean
  readonly commentText?: string
  readonly onlyWithOpenSubtasks: boolean
  readonly workflowStates?: readonly PageWorkflowState[]
  readonly annotationPresence?: AnnotationPresence
  readonly xmlAttributeFilters?: readonly XmlAttributeFilterRow[]
}): Record<string, unknown> {
  const confidenceActive = isConfidenceFilterActive(state)
  const xmlAttributeFilters = (state.xmlAttributeFilters ?? [])
    .filter(isXmlAttributeFilterComplete)
    .map(({ elementName, attributeName, operator, value }) => ({
      elementName: elementName.trim() || null,
      attributeName: attributeName.trim(),
      operator,
      value: VALUE_XML_OPERATORS.has(operator) ? value : null
    }))

  return {
    textContent: state.textContent.trim() || null,
    labelIds: state.labelIds.length ? state.labelIds : null,
    tags: state.tags.length ? state.tags : null,
    confidenceMin: confidenceActive ? state.confidenceRange[0] : null,
    confidenceMax: confidenceActive ? state.confidenceRange[1] : null,
    confidenceElementTypes: confidenceActive && state.confidenceElementTypes.length ? state.confidenceElementTypes : null,
    hasComments: state.hasComments || null,
    commentText: state.hasComments && state.commentText?.trim() ? state.commentText.trim() : null,
    workflowStates: state.workflowStates?.length ? state.workflowStates : null,
    annotationPresence: state.annotationPresence ?? null,
    onlyWithOpenSubtasks: state.onlyWithOpenSubtasks || null,
    xmlAttributeFilters: xmlAttributeFilters.length ? xmlAttributeFilters : null,
    filterOperator: state.filterOperator
  }
}

export function addPageFilterRow(state: PageFilterState, type: PageFilterType): PageFilterState {
  if (type === 'xmlAttribute') {
    return {
      ...state,
      xmlAttributeFilters: [...state.xmlAttributeFilters, {
        id: createRowId(),
        elementName: '',
        attributeName: '',
        operator: 'exists',
        value: ''
      }]
    }
  }
  if (state.visibleFilters.includes(type)) return state
  const next = { ...state, visibleFilters: [...state.visibleFilters, type] }
  if (type === 'comments') next.hasComments = true
  if (type === 'openSubtasks') next.onlyWithOpenSubtasks = true
  return next
}

export function removePageFilterRow(state: PageFilterState, type: PageFilterType, rowId?: string): PageFilterState {
  if (type === 'xmlAttribute') {
    return { ...state, xmlAttributeFilters: state.xmlAttributeFilters.filter(row => row.id !== rowId) }
  }
  const next = { ...state, visibleFilters: state.visibleFilters.filter(value => value !== type) }
  if (type === 'workflowStates') next.workflowStates = []
  if (type === 'annotationPresence') next.annotationPresence = null
  if (type === 'labels') next.labelIds = []
  if (type === 'textContent') next.textContent = ''
  if (type === 'tags') next.tags = []
  if (type === 'confidence') {
    next.confidenceRange = [0, 1]
    next.confidenceElementTypes = []
  }
  if (type === 'comments') {
    next.hasComments = false
    next.commentText = ''
  }
  if (type === 'openSubtasks') next.onlyWithOpenSubtasks = false
  return next
}

export function usePageFilter(projectId: Ref<string | undefined>) {
  const { reportIssue, resolveIssue } = useStatusIssues()
  const pageFilterIssueId = (suffix: string) => `page-filter:${projectId.value || 'unknown'}:${suffix}`
  const reportPageFilterIssue = (suffix: string, title: string, fallback: string, error: unknown, retry?: () => Promise<unknown>) => {
    reportIssue({ id: pageFilterIssueId(suffix), source: 'page-filter', severity: 'warning', title, message: extractApiErrorMessage(error, fallback), retryLabel: retry ? 'Retry' : undefined, retry })
  }

  if (!persistenceWatcherInitialized) {
    persistenceWatcherInitialized = true
    watch(globalFilterState, (state) => {
      if (import.meta.client && globalCurrentProjectId.value) {
        sessionStorage.setItem(`page-filter-${globalCurrentProjectId.value}`, JSON.stringify(state))
      }
    }, { deep: true })
  }

  watch(projectId, (newProjectId) => {
    if (newProjectId === globalCurrentProjectId.value) return
    globalCurrentProjectId.value = newProjectId ?? null
    if (!newProjectId || !import.meta.client) {
      globalFilterState.value = cloneDefaultState()
      return
    }
    const stored = sessionStorage.getItem(`page-filter-${newProjectId}`)
    if (!stored) {
      globalFilterState.value = cloneDefaultState()
      return
    }
    try {
      globalFilterState.value = normalizeStoredPageFilterState(JSON.parse(stored) as Record<string, unknown>)
    } catch {
      globalFilterState.value = cloneDefaultState()
    }
  }, { immediate: true })

  const hasActiveFilters = computed(() => activePageFilterCount(globalFilterState.value) > 0)
  const hasBackendFilters = hasActiveFilters
  const filteredPageIds = computed(() => new Set(
    projectId.value ? (globalFilteredPageIdsByProjectId.value[projectId.value] ?? []) : []
  ))
  const filteredCount = computed(() => filteredPageIds.value.size)
  const filtersApplied = computed(() => Boolean(projectId.value && globalFilteredPageIdsByProjectId.value[projectId.value]))

  const model = <K extends keyof PageFilterState>(key: K) => computed({
    get: () => globalFilterState.value[key],
    set: (value: PageFilterState[K]) => { globalFilterState.value = { ...globalFilterState.value, [key]: value } }
  })
  const labelIds = computed({
    get: () => globalFilterState.value.labelIds,
    set: (value) => {
      globalFilterState.value = { ...globalFilterState.value, labelIds: normalizeLegacyLabelFilterValues(value) }
    }
  })
  const confidenceRange = computed({
    get: () => globalFilterState.value.confidenceRange,
    set: (value) => {
      globalFilterState.value = { ...globalFilterState.value, confidenceRange: normalizeConfidenceRange(value) }
    }
  })
  const confidenceElementTypes = computed({
    get: () => globalFilterState.value.confidenceElementTypes,
    set: (value) => {
      globalFilterState.value = { ...globalFilterState.value, confidenceElementTypes: normalizeConfidenceElementTypes(value) }
    }
  })
  const workflowStates = computed({
    get: () => globalFilterState.value.workflowStates,
    set: (value) => {
      globalFilterState.value = { ...globalFilterState.value, workflowStates: normalizeWorkflowStates(value) }
    }
  })

  async function applyFiltersForProjects(projectIds: string[]): Promise<void> {
    const generation = ++requestGeneration
    const uniqueProjectIds = [...new Set(projectIds.filter(Boolean))]
    if (!hasActiveFilters.value || !uniqueProjectIds.length) {
      globalFilteredPageIdsByProjectId.value = {}
      globalFilterError.value = null
      globalIsFiltering.value = false
      return
    }

    globalIsFiltering.value = true
    globalFilterError.value = null
    const body = buildPageFilterRequestBody(globalFilterState.value)
    const results = await Promise.allSettled(uniqueProjectIds.map(async id => ({
      id,
      response: await $fetch<{ pageIds: string[], count: number }>(`/api/projects/${id}/pages/filter`, { method: 'POST', body })
    })))
    if (generation !== requestGeneration) return

    const next = { ...globalFilteredPageIdsByProjectId.value }
    let failed = 0
    for (const result of results) {
      if (result.status === 'fulfilled') next[result.value.id] = result.value.response.pageIds ?? []
      else failed += 1
    }
    for (const existingProjectId of Object.keys(next)) {
      if (!uniqueProjectIds.includes(existingProjectId)) delete next[existingProjectId]
    }
    globalFilteredPageIdsByProjectId.value = next
    globalFilterError.value = failed ? `Could not refresh filters for ${failed} project${failed === 1 ? '' : 's'}. Last successful results are still shown.` : null
    globalIsFiltering.value = false
  }

  async function applyFilters(): Promise<PageFilterResult> {
    if (!projectId.value) return { pageIds: new Set(), count: 0 }
    await applyFiltersForProjects([projectId.value])
    return { pageIds: filteredPageIds.value, count: filteredCount.value }
  }

  function addFilter(type: PageFilterType) {
    globalFilterState.value = addPageFilterRow(globalFilterState.value, type)
  }

  function removeFilter(type: PageFilterType, rowId?: string) {
    globalFilterState.value = removePageFilterRow(globalFilterState.value, type, rowId)
  }

  function clearFilters() {
    requestGeneration += 1
    globalFilterState.value = cloneDefaultState()
    globalFilteredPageIdsByProjectId.value = {}
    globalFilterError.value = null
    globalIsFiltering.value = false
  }

  const clearLabelFilter = () => {
    globalFilterState.value = { ...globalFilterState.value, labelIds: [] }
  }
  const clearTextContentFilter = () => {
    globalFilterState.value = { ...globalFilterState.value, textContent: '' }
  }
  const clearTagFilter = () => {
    globalFilterState.value = { ...globalFilterState.value, tags: [] }
  }
  const clearConfidenceFilter = () => {
    globalFilterState.value = { ...globalFilterState.value, confidenceRange: [0, 1], confidenceElementTypes: [] }
  }
  const clearWorkflowStateFilter = () => {
    globalFilterState.value = { ...globalFilterState.value, workflowStates: [] }
  }

  async function fetchIndexStats(): Promise<IndexStats | null> {
    if (!projectId.value) return null
    try {
      const stats = await $fetch<IndexStats>(`/api/projects/${projectId.value}/pages/index-stats`)
      globalIndexStatsCache.value = { ...globalIndexStatsCache.value, [projectId.value]: stats }
      resolveIssue(pageFilterIssueId('index-stats'))
      return stats
    } catch (error) {
      reportPageFilterIssue('index-stats', 'Page filter stats unavailable', 'Could not refresh page filter index statistics. Last known values are still shown when available.', error, fetchIndexStats)
      return globalIndexStatsCache.value[projectId.value] ?? null
    }
  }

  async function fetchAvailableLabels(): Promise<LabelWithCount[]> {
    if (!projectId.value) return []
    try {
      const labels = await $fetch<LabelWithCount[]>(`/api/projects/${projectId.value}/pages/available-labels`)
      globalAvailableLabelsCache.value = { ...globalAvailableLabelsCache.value, [projectId.value]: labels }
      resolveIssue(pageFilterIssueId('available-labels'))
      return labels
    } catch (error) {
      reportPageFilterIssue('available-labels', 'Available labels unavailable', 'Could not refresh available page filter labels.', error, fetchAvailableLabels)
      return globalAvailableLabelsCache.value[projectId.value] ?? []
    }
  }

  async function fetchAvailableXmlAttributes(): Promise<XmlAttributeWithCount[]> {
    if (!projectId.value) return []
    try {
      const attributes = await $fetch<XmlAttributeWithCount[]>(`/api/projects/${projectId.value}/pages/available-xml-attributes`)
      globalAvailableXmlAttributesCache.value = { ...globalAvailableXmlAttributesCache.value, [projectId.value]: attributes }
      resolveIssue(pageFilterIssueId('available-xml-attributes'))
      return attributes
    } catch (error) {
      reportPageFilterIssue('available-xml-attributes', 'XML attribute suggestions unavailable', 'Could not refresh PAGE XML attribute suggestions.', error, fetchAvailableXmlAttributes)
      return globalAvailableXmlAttributesCache.value[projectId.value] ?? []
    }
  }

  async function rebuildIndex(): Promise<boolean> {
    if (!projectId.value) return false
    try {
      await $fetch(`/api/projects/${projectId.value}/pages/rebuild-index`, { method: 'POST' })
      resolveIssue(pageFilterIssueId('rebuild-index'))
      return true
    } catch (error) {
      reportPageFilterIssue('rebuild-index', 'Page filter rebuild failed', 'Could not rebuild the page filter index.', error, rebuildIndex)
      return false
    }
  }

  async function getMatchingIds<T extends { textLineIds?: string[], regionIds?: string[] }>(pageId: string, endpoint: string): Promise<T> {
    return await $fetch<T>(`/api/projects/${projectId.value}/pages/${pageId}/${endpoint}`, { params: { textContent: globalFilterState.value.textContent } })
  }
  async function getMatchingTextLineIds(pageId: string): Promise<string[]> {
    if (!projectId.value || !globalFilterState.value.textContent.trim()) return []
    try {
      return (await getMatchingIds<{ textLineIds: string[] }>(pageId, 'matching-textlines')).textLineIds
    } catch {
      return []
    }
  }
  async function getMatchingTextRegionIds(pageId: string): Promise<string[]> {
    if (!projectId.value || !globalFilterState.value.textContent.trim()) return []
    try {
      return (await getMatchingIds<{ regionIds: string[] }>(pageId, 'matching-textregions')).regionIds
    } catch {
      return []
    }
  }

  return {
    filterState: readonly(globalFilterState),
    filteredPageIdsByProjectId: readonly(globalFilteredPageIdsByProjectId),
    isFiltering: readonly(globalIsFiltering),
    filterError: readonly(globalFilterError),
    filteredPageIds: readonly(filteredPageIds),
    filteredCount: readonly(filteredCount),
    filtersApplied: readonly(filtersApplied),
    hasActiveFilters,
    hasBackendFilters,
    activeFilterCount: computed(() => activePageFilterCount(globalFilterState.value)),
    labelIds,
    textContent: model('textContent'),
    tags: model('tags'),
    filterOperator: model('filterOperator'),
    confidenceRange,
    confidenceElementTypes,
    hasComments: model('hasComments'),
    commentText: model('commentText'),
    onlyWithOpenSubtasks: model('onlyWithOpenSubtasks'),
    workflowStates,
    annotationPresence: model('annotationPresence'),
    xmlAttributeFilters: model('xmlAttributeFilters'),
    visibleFilters: model('visibleFilters'),
    addFilter,
    removeFilter,
    applyFilters,
    applyFiltersForProjects,
    clearFilters,
    clearLabelFilter,
    clearTextContentFilter,
    clearTagFilter,
    clearConfidenceFilter,
    clearWorkflowStateFilter,
    fetchIndexStats,
    fetchAvailableLabels,
    fetchAvailableXmlAttributes,
    rebuildIndex,
    getMatchingTextLineIds,
    getMatchingTextRegionIds
  }
}
