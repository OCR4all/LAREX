import type { PageData } from './types'
import { naturalSortBy } from '@/utils/natural-sort'

interface ProjectSessionState {
  openedPageIds: string[]
  activePageId: string | null
  selectedVariantIdByPageId: Record<string, string | null>
}

export interface EditorTextViewSettings {
  mode: 'textline'
  gtIndex: number | undefined
  searchQuery: string
  showDiff: boolean
  showComments: boolean
  focusMode: boolean
  confidenceRange: [number, number]
  selectedIndices: number[]
  filterUnindexed: boolean
  showNonAssignedIndices: boolean
  onlyMissingGt: boolean
}

interface MultiProjectEditorSessionState {
  workspaceId: string | null
  openedProjectIds: string[]
  activeProjectId: string | null
  projectsById: Record<string, ProjectSessionState>
  textViewSettings: EditorTextViewSettings
}

interface LegacyEditorSessionState {
  projectId: string | null
  openedPageIds: string[]
  activePageId: string | null
  selectedVariantIdByPageId: Record<string, string | null>
}

const STORAGE_KEY = 'larex-editor-session'

function createEmptyProjectState(): ProjectSessionState {
  return {
    openedPageIds: [],
    activePageId: null,
    selectedVariantIdByPageId: {}
  }
}

function createDefaultTextViewSettings(): EditorTextViewSettings {
  return {
    mode: 'textline',
    gtIndex: 0,
    searchQuery: '',
    showDiff: false,
    showComments: false,
    focusMode: false,
    confidenceRange: [0, 1],
    selectedIndices: [],
    filterUnindexed: false,
    showNonAssignedIndices: false,
    onlyMissingGt: false
  }
}

function createEmptyState(): MultiProjectEditorSessionState {
  return {
    workspaceId: null,
    openedProjectIds: [],
    activeProjectId: null,
    projectsById: {},
    textViewSettings: createDefaultTextViewSettings()
  }
}

function loadSessionState(): MultiProjectEditorSessionState | LegacyEditorSessionState | null {
  if (typeof window === 'undefined') return null
  try {
    const stored = window.sessionStorage.getItem(STORAGE_KEY)
    return stored ? JSON.parse(stored) : null
  } catch {
    return null
  }
}

function saveSessionState(state: MultiProjectEditorSessionState): void {
  if (typeof window === 'undefined') return
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
  }
}

function clearSessionState(): void {
  if (typeof window === 'undefined') return
  window.sessionStorage.removeItem(STORAGE_KEY)
}

function isLegacyState(value: unknown): value is LegacyEditorSessionState {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return 'projectId' in candidate && 'openedPageIds' in candidate && !('projectsById' in candidate)
}

function normalizeProjectState(value: unknown): ProjectSessionState {
  const candidate = (value && typeof value === 'object') ? value as Record<string, unknown> : {}
  const selectedVariantEntries: Array<[string, string | null]> = candidate.selectedVariantIdByPageId && typeof candidate.selectedVariantIdByPageId === 'object'
    ? Object.entries(candidate.selectedVariantIdByPageId as Record<string, unknown>)
      .flatMap(([key, variantId]) => {
        if (typeof key !== 'string') return []
        if (typeof variantId !== 'string' && variantId !== null) return []
        return [[key, variantId]]
      })
    : []

  return {
    openedPageIds: Array.isArray(candidate.openedPageIds)
      ? candidate.openedPageIds.filter((id): id is string => typeof id === 'string')
      : [],
    activePageId: typeof candidate.activePageId === 'string' ? candidate.activePageId : null,
    selectedVariantIdByPageId: Object.fromEntries(selectedVariantEntries)
  }
}

function normalizeTextViewSettings(value: unknown): EditorTextViewSettings {
  const defaults = createDefaultTextViewSettings()
  if (!value || typeof value !== 'object') return defaults
  const candidate = value as Record<string, unknown>

  const rawGtIndex = candidate.gtIndex
  const gtIndex = typeof rawGtIndex === 'number' && Number.isFinite(rawGtIndex) && rawGtIndex >= 0
    ? Math.trunc(rawGtIndex)
    : rawGtIndex == null
      ? undefined
      : defaults.gtIndex

  const rawRange = Array.isArray(candidate.confidenceRange) ? candidate.confidenceRange : defaults.confidenceRange
  const min = Math.max(0, Math.min(1, Number(rawRange[0] ?? defaults.confidenceRange[0])))
  const max = Math.max(0, Math.min(1, Number(rawRange[1] ?? defaults.confidenceRange[1])))
  const confidenceRange: [number, number] = min <= max ? [min, max] : [max, min]

  const selectedIndices = Array.isArray(candidate.selectedIndices)
    ? [...new Set(
      candidate.selectedIndices
        .map(v => Number(v))
        .filter((v): v is number => Number.isFinite(v) && v >= 0)
        .map(v => Math.trunc(v))
    )].sort((a, b) => a - b)
    : defaults.selectedIndices

  return {
    mode: 'textline',
    gtIndex,
    searchQuery: typeof candidate.searchQuery === 'string' ? candidate.searchQuery : defaults.searchQuery,
    showDiff: Boolean(candidate.showDiff ?? defaults.showDiff),
    showComments: Boolean(candidate.showComments ?? defaults.showComments),
    focusMode: Boolean(candidate.focusMode ?? defaults.focusMode),
    confidenceRange,
    selectedIndices,
    filterUnindexed: Boolean(candidate.filterUnindexed ?? defaults.filterUnindexed),
    showNonAssignedIndices: Boolean(candidate.showNonAssignedIndices ?? defaults.showNonAssignedIndices),
    onlyMissingGt: Boolean(candidate.onlyMissingGt ?? candidate.onlyMissingGtLines ?? defaults.onlyMissingGt)
  }
}

function normalizeState(value: unknown): MultiProjectEditorSessionState {
  if (isLegacyState(value)) {
    const projectId = value.projectId
    const projectsById: Record<string, ProjectSessionState> = {}
    const openedProjectIds = typeof projectId === 'string' && projectId.length > 0 ? [projectId] : []
    if (openedProjectIds.length > 0 && projectId) {
      projectsById[projectId] = {
        openedPageIds: Array.isArray(value.openedPageIds)
          ? value.openedPageIds.filter((id): id is string => typeof id === 'string')
          : [],
        activePageId: typeof value.activePageId === 'string' ? value.activePageId : null,
        selectedVariantIdByPageId: value.selectedVariantIdByPageId && typeof value.selectedVariantIdByPageId === 'object'
          ? value.selectedVariantIdByPageId
          : {}
      }
    }
    return {
      workspaceId: null,
      openedProjectIds,
      activeProjectId: openedProjectIds[0] ?? null,
      projectsById,
      textViewSettings: createDefaultTextViewSettings()
    }
  }

  if (!value || typeof value !== 'object') return createEmptyState()

  const candidate = value as Record<string, unknown>
  const openedProjectIds = Array.isArray(candidate.openedProjectIds)
    ? candidate.openedProjectIds.filter((id): id is string => typeof id === 'string')
    : []
  const activeProjectId = typeof candidate.activeProjectId === 'string' ? candidate.activeProjectId : null
  const workspaceId = typeof candidate.workspaceId === 'string' ? candidate.workspaceId : null
  const projectsByIdRaw = candidate.projectsById && typeof candidate.projectsById === 'object'
    ? candidate.projectsById as Record<string, unknown>
    : {}

  const projectsById = Object.fromEntries(
    Object.entries(projectsByIdRaw)
      .filter(([id]) => typeof id === 'string')
      .map(([id, state]) => [id, normalizeProjectState(state)])
  )

  return {
    workspaceId,
    openedProjectIds,
    activeProjectId,
    projectsById,
    textViewSettings: normalizeTextViewSettings(candidate.textViewSettings)
  }
}

export const useEditorSessionStore = defineStore('editor-session', () => {
  const workspaceId = ref<string | null>(null)
  const openedProjectIds = ref<string[]>([])
  const activeProjectId = ref<string | null>(null)
  const projectsById = ref<Record<string, ProjectSessionState>>({})
  const textViewSettings = ref<EditorTextViewSettings>(createDefaultTextViewSettings())

  function persistState() {
    saveSessionState({
      workspaceId: workspaceId.value,
      openedProjectIds: openedProjectIds.value,
      activeProjectId: activeProjectId.value,
      projectsById: projectsById.value,
      textViewSettings: textViewSettings.value
    })
  }

  function ensureProject(projectId: string): ProjectSessionState {
    const existing = projectsById.value[projectId]
    if (existing) return existing
    const created = createEmptyProjectState()
    projectsById.value = { ...projectsById.value, [projectId]: created }
    return created
  }

  function addOpenedProject(projectId: string) {
    if (!openedProjectIds.value.includes(projectId)) {
      openedProjectIds.value = [...openedProjectIds.value, projectId]
    }
    ensureProject(projectId)
    if (!activeProjectId.value) {
      activeProjectId.value = projectId
    }
    persistState()
  }

  function removeOpenedProject(projectId: string) {
    openedProjectIds.value = openedProjectIds.value.filter(id => id !== projectId)
    const { [projectId]: _removed, ...rest } = projectsById.value
    projectsById.value = rest
    if (activeProjectId.value === projectId) {
      activeProjectId.value = openedProjectIds.value[0] ?? null
    }
    persistState()
  }

  function addOpenedPageForProject(projectId: string, pageId: string) {
    addOpenedProject(projectId)
    const state = ensureProject(projectId)
    if (!state.openedPageIds.includes(pageId)) {
      state.openedPageIds = [...state.openedPageIds, pageId]
      projectsById.value = { ...projectsById.value, [projectId]: { ...state } }
    }
  }

  function removeOpenedPageForProject(projectId: string, pageId: string) {
    const state = ensureProject(projectId)
    state.openedPageIds = state.openedPageIds.filter(id => id !== pageId)
    if (state.activePageId === pageId) {
      state.activePageId = state.openedPageIds[0] ?? null
    }
    projectsById.value = { ...projectsById.value, [projectId]: { ...state } }
  }

  function setActiveProject(projectId: string) {
    if (!openedProjectIds.value.includes(projectId)) {
      addOpenedProject(projectId)
    }
    activeProjectId.value = projectId
    persistState()
  }

  function setActivePageForProject(projectId: string, pageId: string) {
    addOpenedPageForProject(projectId, pageId)
    const state = ensureProject(projectId)
    state.activePageId = pageId
    projectsById.value = { ...projectsById.value, [projectId]: { ...state } }
    activeProjectId.value = projectId
  }

  function setSelectedVariantForProject(projectId: string, pageId: string, variantId: string | null) {
    const state = ensureProject(projectId)
    state.selectedVariantIdByPageId = {
      ...state.selectedVariantIdByPageId,
      [pageId]: variantId
    }
    projectsById.value = { ...projectsById.value, [projectId]: { ...state } }
  }

  function initWorkspaceSession(newWorkspaceId: string | null) {
    workspaceId.value = newWorkspaceId
    persistState()
  }

  function initProjectSession(projectId: string, pages: PageData[]) {
    addOpenedProject(projectId)
    const state = ensureProject(projectId)
    const firstPageId = naturalSortBy(pages, 'label')[0]?.id ?? null
    state.openedPageIds = firstPageId ? [firstPageId] : []
    state.activePageId = firstPageId
    state.selectedVariantIdByPageId = {}
    projectsById.value = { ...projectsById.value, [projectId]: { ...state } }
    setActiveProject(projectId)
  }

  function clearSession(options?: { preserveTextViewSettings?: boolean }) {
    workspaceId.value = null
    openedProjectIds.value = []
    activeProjectId.value = null
    projectsById.value = {}
    if (!options?.preserveTextViewSettings) {
      textViewSettings.value = createDefaultTextViewSettings()
    }
    clearSessionState()
    if (options?.preserveTextViewSettings) {
      persistState()
    }
  }

  function loadPersistedSession(): boolean {
    const stored = loadSessionState()
    if (!stored) return false

    const normalized = normalizeState(stored)
    textViewSettings.value = normalized.textViewSettings
    if (normalized.openedProjectIds.length === 0) return false

    workspaceId.value = normalized.workspaceId
    openedProjectIds.value = normalized.openedProjectIds
    activeProjectId.value = normalized.activeProjectId && normalized.openedProjectIds.includes(normalized.activeProjectId)
      ? normalized.activeProjectId
      : normalized.openedProjectIds[0] ?? null
    projectsById.value = normalized.projectsById
    persistState()
    return true
  }

  function getProjectState(projectId: string): ProjectSessionState {
    return projectsById.value[projectId] ?? createEmptyProjectState()
  }

  function getOpenedPageIds(projectId: string): string[] {
    return getProjectState(projectId).openedPageIds
  }

  function getActivePageId(projectId: string): string | null {
    return getProjectState(projectId).activePageId
  }

  function getSelectedVariantIdByPageId(projectId: string): Record<string, string | null> {
    return getProjectState(projectId).selectedVariantIdByPageId
  }

  function setTextViewSettings(next: EditorTextViewSettings) {
    textViewSettings.value = normalizeTextViewSettings(next)
    persistState()
  }

  function updateTextViewSettings(updater: (current: EditorTextViewSettings) => EditorTextViewSettings) {
    textViewSettings.value = normalizeTextViewSettings(updater(textViewSettings.value))
    persistState()
  }

  function addOpenedPage(projectIdOrPageId: string, maybePageId?: string) {
    if (maybePageId) {
      addOpenedPageForProject(projectIdOrPageId, maybePageId)
      persistState()
      return
    }
    if (!activeProjectId.value) return
    addOpenedPageForProject(activeProjectId.value, projectIdOrPageId)
    persistState()
  }

  function removeOpenedPage(projectIdOrPageId: string, maybePageId?: string) {
    if (maybePageId) {
      removeOpenedPageForProject(projectIdOrPageId, maybePageId)
      persistState()
      return
    }
    if (!activeProjectId.value) return
    removeOpenedPageForProject(activeProjectId.value, projectIdOrPageId)
    persistState()
  }

  function setActivePage(projectIdOrPageId: string, maybePageId?: string) {
    if (maybePageId) {
      setActivePageForProject(projectIdOrPageId, maybePageId)
      persistState()
      return
    }
    if (!activeProjectId.value) return
    setActivePageForProject(activeProjectId.value, projectIdOrPageId)
    persistState()
  }

  function setSelectedVariant(projectIdOrPageId: string, pageIdOrVariantId: string | null, maybeVariantId?: string | null) {
    if (maybeVariantId !== undefined) {
      setSelectedVariantForProject(projectIdOrPageId, pageIdOrVariantId as string, maybeVariantId)
      persistState()
      return
    }
    if (!activeProjectId.value) return
    setSelectedVariantForProject(activeProjectId.value, projectIdOrPageId, pageIdOrVariantId)
    persistState()
  }

  const projectId = computed(() => activeProjectId.value)
  const openedPageIds = computed(() => {
    const id = activeProjectId.value
    return id ? getOpenedPageIds(id) : []
  })
  const activePageId = computed(() => {
    const id = activeProjectId.value
    return id ? getActivePageId(id) : null
  })
  const selectedVariantIdByPageId = computed(() => {
    const id = activeProjectId.value
    return id ? getSelectedVariantIdByPageId(id) : {}
  })

  function initSession(newProjectId: string, pages: PageData[]) {
    clearSession({ preserveTextViewSettings: true })
    initProjectSession(newProjectId, pages)
    persistState()
  }

  function hasSession(): boolean {
    return openedProjectIds.value.length > 0
  }

  return {
    workspaceId,
    openedProjectIds,
    activeProjectId,
    projectsById,
    textViewSettings,
    projectId,
    openedPageIds,
    activePageId,
    selectedVariantIdByPageId,
    initWorkspaceSession,
    initProjectSession,
    addOpenedProject,
    removeOpenedProject,
    addOpenedPage,
    removeOpenedPage,
    setActiveProject,
    setActivePage,
    setSelectedVariant,
    getProjectState,
    getOpenedPageIds,
    getActivePageId,
    getSelectedVariantIdByPageId,
    setTextViewSettings,
    updateTextViewSettings,
    initSession,
    clearSession,
    loadPersistedSession,
    hasSession
  }
})
