import type { PageData, ImageVariant } from './types'
import type { LabelSet as ApiLabelSet, LabelDefinition as ApiLabelDefinition } from '@/types/label-set'
import type { DictionaryFormEntry } from '@/types/dictionary'
import { LabelSet as LabelSetModel, LabelDefinition as LabelDefinitionModel } from '@/models/editor/labels'
import { useEditorSessionStore } from './editor.session.store'
import { parseCanvasId, parsePagePanelId } from './editor.keys'

function getVariantPreferenceKey(variant: ImageVariant): string {
  return variant.type ?? variant.label
}

function cloneSet(set: Set<string>): Set<string> {
  return new Set(Array.from(set.values()))
}

export type ProjectTextIndexDefaults = {
  gtIndex: number
  recognitionIndices: number[]
}

export type ProjectToolkitSettings = {
  codecId: string | null
  labelSetId: string | null
  dictionaryId: string | null
  tagSetId: string | null
  normalizationProfileId: string | null
  validationRulesetId: string | null
  virtualKeyboardId: string | null
  allowCodecOverride: boolean
  allowDictionaryOverride: boolean
  allowVirtualKeyboardOverride: boolean
  allowLabelSetOverride: boolean
  allowTagSetOverride: boolean
  allowNormalizationProfileOverride: boolean
  allowValidationRulesetOverride: boolean
}

const DEFAULT_TOOLKIT_SETTINGS: ProjectToolkitSettings = {
  codecId: null,
  labelSetId: null,
  dictionaryId: null,
  tagSetId: null,
  normalizationProfileId: null,
  validationRulesetId: null,
  virtualKeyboardId: null,
  allowCodecOverride: true,
  allowDictionaryOverride: true,
  allowVirtualKeyboardOverride: true,
  allowLabelSetOverride: true,
  allowTagSetOverride: true,
  allowNormalizationProfileOverride: true,
  allowValidationRulesetOverride: true
}

const UNINDEXED_RECOGNITION_SENTINEL = -1

function normalizeProjectTextIndexDefaults(input?: Partial<ProjectTextIndexDefaults> | null): ProjectTextIndexDefaults {
  const rawGt = Number(input?.gtIndex ?? 0)
  const gtIndex = Number.isFinite(rawGt) && rawGt >= 0 ? Math.trunc(rawGt) : 0

  const recognitionRaw = Array.isArray(input?.recognitionIndices) ? input.recognitionIndices : [1]
  const recognitionIndices = [...new Set(
    recognitionRaw
      .map(v => Number(v))
      .filter(v => Number.isFinite(v) && (v >= 0 || v === UNINDEXED_RECOGNITION_SENTINEL))
      .map(v => Math.trunc(v))
      .filter(v => v !== gtIndex)
  )].sort((a, b) => a - b)

  const fallbackRecognition = [1, 0, 2, 3].find(v => v !== gtIndex) ?? 1

  return {
    gtIndex,
    recognitionIndices: recognitionIndices.length > 0 ? recognitionIndices : [fallbackRecognition]
  }
}

export const useEditorDocumentStore = defineStore('editor-document', () => {
  const sessionStore = useEditorSessionStore()

  const pagesByProjectId = ref<Record<string, PageData[]>>({})
  const pages = ref<PageData[]>([])
  const loadedPageIdsByProjectId = ref<Record<string, Set<string>>>({})
  const loadedPageIds = ref<Set<string>>(new Set())

  const preferredImageVariantKey = ref<string | null>(null)
  const selectedVariantIdByPageIdByProject = ref<Record<string, Record<string, string | null>>>({})
  const selectedVariantIdByPageId = ref<Record<string, string | null>>({})

  const labelSetByProjectId = ref<Record<string, LabelSetModel | null>>({})
  const labelSet = ref<LabelSetModel | null>(null)

  const projectCodecByProjectId = ref<Record<string, { id: string | null, characters: string[] }>>({})
  const projectCodecId = ref<string | null>(null)
  const projectCodecCharacters = ref<string[]>([])
  const projectDictionaryByProjectId = ref<Record<string, {
    id: string | null
    forms: DictionaryFormEntry[]
    caseSensitive: boolean
    unicodeNormalization: string
    canEdit: boolean
    locked: boolean
  }>>({})
  const projectDictionaryId = ref<string | null>(null)
  const projectDictionaryForms = ref<DictionaryFormEntry[]>([])
  const projectDictionaryCaseSensitive = ref<boolean>(false)
  const projectDictionaryUnicodeNormalization = ref<string>('NFC')
  const projectDictionaryCanEdit = ref<boolean>(false)
  const projectDictionaryLocked = ref<boolean>(false)
  const projectVirtualKeyboardIdByProjectId = ref<Record<string, string | null>>({})
  const projectVirtualKeyboardId = ref<string | null>(null)
  const projectToolkitSettingsByProjectId = ref<Record<string, ProjectToolkitSettings>>({})
  const projectToolkitSettings = ref<ProjectToolkitSettings>({ ...DEFAULT_TOOLKIT_SETTINGS })
  const projectTextIndexDefaultsByProjectId = ref<Record<string, ProjectTextIndexDefaults>>({})
  const projectTextDefaultGtIndex = ref<number>(0)
  const projectTextDefaultRecognitionIndices = ref<number[]>([1])

  const allPages = computed<PageData[]>(() => {
    return Object.values(pagesByProjectId.value).flat()
  })

  const activeProjectId = computed(() => sessionStore.activeProjectId)

  function syncActiveProjectState() {
    const projectId = activeProjectId.value
    pages.value = projectId ? [...(pagesByProjectId.value[projectId] ?? [])] : []
    loadedPageIds.value = projectId ? cloneSet(loadedPageIdsByProjectId.value[projectId] ?? new Set()) : new Set()
    selectedVariantIdByPageId.value = projectId ? { ...(selectedVariantIdByPageIdByProject.value[projectId] ?? {}) } : {}
    labelSet.value = projectId ? (labelSetByProjectId.value[projectId] ?? null) : null

    const codec = projectId ? projectCodecByProjectId.value[projectId] : undefined
    projectCodecId.value = codec?.id ?? null
    projectCodecCharacters.value = codec?.characters ? [...codec.characters] : []

    const dictionary = projectId ? projectDictionaryByProjectId.value[projectId] : undefined
    projectDictionaryId.value = dictionary?.id ?? null
    projectDictionaryForms.value = dictionary?.forms ? [...dictionary.forms] : []
    projectDictionaryCaseSensitive.value = dictionary?.caseSensitive ?? false
    projectDictionaryUnicodeNormalization.value = dictionary?.unicodeNormalization ?? 'NFC'
    projectDictionaryCanEdit.value = dictionary?.canEdit ?? false
    projectDictionaryLocked.value = dictionary?.locked ?? false
    projectVirtualKeyboardId.value = projectId ? (projectVirtualKeyboardIdByProjectId.value[projectId] ?? null) : null
    projectToolkitSettings.value = projectId
      ? { ...DEFAULT_TOOLKIT_SETTINGS, ...(projectToolkitSettingsByProjectId.value[projectId] ?? {}) }
      : { ...DEFAULT_TOOLKIT_SETTINGS }

    const textDefaults = projectId ? projectTextIndexDefaultsByProjectId.value[projectId] : undefined
    const normalizedTextDefaults = normalizeProjectTextIndexDefaults(textDefaults)
    projectTextDefaultGtIndex.value = normalizedTextDefaults.gtIndex
    projectTextDefaultRecognitionIndices.value = [...normalizedTextDefaults.recognitionIndices]
  }

  watch(() => activeProjectId.value, () => {
    syncActiveProjectState()
  }, { immediate: true })

  function ensureProject(projectId: string) {
    if (!pagesByProjectId.value[projectId]) {
      pagesByProjectId.value = {
        ...pagesByProjectId.value,
        [projectId]: []
      }
    }
    if (!loadedPageIdsByProjectId.value[projectId]) {
      loadedPageIdsByProjectId.value = {
        ...loadedPageIdsByProjectId.value,
        [projectId]: new Set()
      }
    }
    if (!selectedVariantIdByPageIdByProject.value[projectId]) {
      selectedVariantIdByPageIdByProject.value = {
        ...selectedVariantIdByPageIdByProject.value,
        [projectId]: {}
      }
    }
  }

  function toLabelSetModel(apiLabelSet: ApiLabelSet): LabelSetModel {
    const labels = (apiLabelSet.labels || []).map((label: ApiLabelDefinition) => new LabelDefinitionModel(
      label.id,
      label.name,
      label.scope,
      label.color,
      label.description || '',
      label.hasText,
      label.isContainer,
      label.group || null,
      label.mapping
    ))

    return new LabelSetModel(
      apiLabelSet.id,
      apiLabelSet.meta?.name ?? apiLabelSet.id,
      labels,
      apiLabelSet.meta?.description ?? undefined,
      apiLabelSet.meta?.defaultLabelId ?? null
    )
  }

  function sortPages(projectPages: PageData[]): PageData[] {
    return [...projectPages]
  }

  function normalizePages(projectId: string, projectPages: PageData[]): PageData[] {
    return projectPages.map(page => ({
      ...page,
      projectId: page.projectId || projectId
    }))
  }

  function setProjectPages(projectId: string, projectPages: PageData[], options?: { replaceProject?: boolean, markLoaded?: boolean, preserveLoaded?: boolean }) {
    ensureProject(projectId)

    const incoming = sortPages(normalizePages(projectId, projectPages))
    const replaceProject = options?.replaceProject !== false
    const markLoaded = options?.markLoaded === true
    const current = pagesByProjectId.value[projectId] ?? []

    if (replaceProject) {
      pagesByProjectId.value = {
        ...pagesByProjectId.value,
        [projectId]: incoming
      }
      loadedPageIdsByProjectId.value = {
        ...loadedPageIdsByProjectId.value,
        [projectId]: markLoaded
          ? new Set(incoming.map(page => page.id))
          : options?.preserveLoaded === true
            ? cloneSet(loadedPageIdsByProjectId.value[projectId] ?? new Set())
            : new Set()
      }
    } else {
      const byId = new Map<string, PageData>()
      for (const page of current) byId.set(page.id, page)
      for (const page of incoming) byId.set(page.id, page)
      pagesByProjectId.value = {
        ...pagesByProjectId.value,
        [projectId]: sortPages(Array.from(byId.values()))
      }
      if (markLoaded) {
        const nextLoaded = cloneSet(loadedPageIdsByProjectId.value[projectId] ?? new Set())
        for (const page of incoming) {
          nextLoaded.add(page.id)
        }
        loadedPageIdsByProjectId.value = {
          ...loadedPageIdsByProjectId.value,
          [projectId]: nextLoaded
        }
      }
    }

    syncActiveProjectState()
  }

  function appendProjectPages(projectId: string, projectPages: PageData[]) {
    setProjectPages(projectId, projectPages, { replaceProject: false })
  }

  function removeProject(projectId: string) {
    const { [projectId]: _pages, ...remainingPagesByProject } = pagesByProjectId.value
    pagesByProjectId.value = remainingPagesByProject

    const { [projectId]: _loaded, ...remainingLoaded } = loadedPageIdsByProjectId.value
    loadedPageIdsByProjectId.value = remainingLoaded

    const { [projectId]: _variants, ...remainingVariants } = selectedVariantIdByPageIdByProject.value
    selectedVariantIdByPageIdByProject.value = remainingVariants

    const { [projectId]: _labelSet, ...remainingLabelSets } = labelSetByProjectId.value
    labelSetByProjectId.value = remainingLabelSets

    const { [projectId]: _codec, ...remainingCodecs } = projectCodecByProjectId.value
    projectCodecByProjectId.value = remainingCodecs

    const { [projectId]: _dictionary, ...remainingDictionaries } = projectDictionaryByProjectId.value
    projectDictionaryByProjectId.value = remainingDictionaries

    const { [projectId]: _keyboard, ...remainingVirtualKeyboards } = projectVirtualKeyboardIdByProjectId.value
    projectVirtualKeyboardIdByProjectId.value = remainingVirtualKeyboards

    const { [projectId]: _toolkitSettings, ...remainingToolkitSettings } = projectToolkitSettingsByProjectId.value
    projectToolkitSettingsByProjectId.value = remainingToolkitSettings

    const { [projectId]: _textDefaults, ...remainingTextDefaults } = projectTextIndexDefaultsByProjectId.value
    projectTextIndexDefaultsByProjectId.value = remainingTextDefaults

    syncActiveProjectState()
  }

  function getProjectPages(projectId: string): PageData[] {
    return pagesByProjectId.value[projectId] ?? []
  }

  function setPages(newPages: PageData[]) {
    const inferredProjectId = newPages[0]?.projectId ?? activeProjectId.value
    if (!inferredProjectId) {
      pagesByProjectId.value = {}
      loadedPageIdsByProjectId.value = {}
      syncActiveProjectState()
      return
    }
    setProjectPages(inferredProjectId, newPages, { replaceProject: true })
  }

  function setPagesWithSession(newPages: PageData[], projectId: string, workspaceId?: string | null) {
    sessionStore.clearSession({ preserveTextViewSettings: true })
    if (workspaceId) {
      sessionStore.initWorkspaceSession(workspaceId)
    }
    sessionStore.initProjectSession(projectId, newPages)
    setProjectPages(projectId, newPages, { replaceProject: true })
  }

  function addPage(page: PageData) {
    const projectId = page.projectId || activeProjectId.value
    if (!projectId) return
    appendProjectPages(projectId, [{ ...page, projectId }])
  }

  function markPageAsLoaded(projectId: string, pageId: string) {
    ensureProject(projectId)
    const next = cloneSet(loadedPageIdsByProjectId.value[projectId] ?? new Set())
    next.add(pageId)
    loadedPageIdsByProjectId.value = {
      ...loadedPageIdsByProjectId.value,
      [projectId]: next
    }
    if (activeProjectId.value === projectId) {
      loadedPageIds.value = cloneSet(next)
    }
  }

  function enrichPage(pageId: string, enrichedData: PageData, projectId?: string) {
    const targetProjectId = projectId ?? enrichedData.projectId ?? activeProjectId.value
    if (!targetProjectId) return

    const projectPages = [...(pagesByProjectId.value[targetProjectId] ?? [])]
    const idx = projectPages.findIndex(p => p.id === pageId)
    if (idx === -1) return

    projectPages[idx] = {
      ...projectPages[idx],
      ...enrichedData,
      projectId: targetProjectId
    }
    pagesByProjectId.value = {
      ...pagesByProjectId.value,
      [targetProjectId]: projectPages
    }
    markPageAsLoaded(targetProjectId, pageId)
    syncActiveProjectState()
  }

  function patchPageIndexingStatuses(projectId: string, statuses: Record<string, PageData['indexingStatus']>) {
    if (!projectId || !statuses || Object.keys(statuses).length === 0) return

    const projectPages = pagesByProjectId.value[projectId] ?? []
    if (projectPages.length === 0) return

    let changed = false
    const nextProjectPages = projectPages.map((page) => {
      const nextStatus = statuses[page.id]
      if (!nextStatus || page.indexingStatus === nextStatus) return page
      changed = true
      return {
        ...page,
        indexingStatus: nextStatus
      }
    })

    if (!changed) return

    pagesByProjectId.value = {
      ...pagesByProjectId.value,
      [projectId]: nextProjectPages
    }
    syncActiveProjectState()
  }

  function patchProjectPageSummaries(
    projectId: string,
    summaries: Array<{
      id: string
      name?: string
      thumbnail?: string
      thumbnailUrl?: string
      tags?: string[]
      resolvedTags?: PageData['resolvedTags']
      locked?: boolean
      lockedReason?: string | null
      workflowState?: PageData['workflowState']
      imageCount?: number
      xmlFileCount?: number
      indexingStatus?: PageData['indexingStatus']
      sortOrder?: number | null
      textConfidence?: PageData['textConfidence']
    }>
  ) {
    if (!projectId || summaries.length === 0) return

    const projectPages = pagesByProjectId.value[projectId] ?? []
    if (projectPages.length === 0) return

    const summariesById = new Map(summaries.map(summary => [summary.id, summary]))
    let changed = false
    const nextProjectPages = projectPages.map((page) => {
      const summary = summariesById.get(page.id)
      if (!summary) return page
      changed = true
      return {
        ...page,
        label: summary.name ?? page.label,
        thumbnail: summary.thumbnailUrl ?? summary.thumbnail ?? page.thumbnail,
        tags: summary.tags ?? page.tags,
        resolvedTags: summary.resolvedTags ?? page.resolvedTags,
        locked: summary.locked ?? false,
        lockedReason: summary.lockedReason ?? null,
        workflowState: summary.workflowState ?? page.workflowState ?? 'OPEN',
        imageCount: summary.imageCount ?? page.imageCount,
        xmlFileCount: summary.xmlFileCount ?? page.xmlFileCount,
        indexingStatus: summary.indexingStatus ?? page.indexingStatus,
        sortOrder: summary.sortOrder ?? page.sortOrder ?? null,
        textConfidence: summary.textConfidence ?? page.textConfidence ?? null
      }
    })

    if (!changed) return

    pagesByProjectId.value = {
      ...pagesByProjectId.value,
      [projectId]: nextProjectPages
    }
    syncActiveProjectState()
  }

  function getPage(pageId: string, projectId?: string): PageData | undefined {
    if (!projectId) {
      const parsedPagePanel = parsePagePanelId(pageId)
      if (parsedPagePanel) {
        return (pagesByProjectId.value[parsedPagePanel.projectId] ?? []).find(p => p.id === parsedPagePanel.pageId)
      }

      const parsedCanvas = parseCanvasId(pageId)
      if (parsedCanvas) {
        return (pagesByProjectId.value[parsedCanvas.projectId] ?? []).find(p => p.id === parsedCanvas.pageId)
      }
    }

    if (projectId) {
      return (pagesByProjectId.value[projectId] ?? []).find(p => p.id === pageId)
    }
    for (const projectPages of Object.values(pagesByProjectId.value)) {
      const found = projectPages.find(p => p.id === pageId)
      if (found) return found
    }
    return undefined
  }

  function isPageLoaded(pageId: string, projectId?: string): boolean {
    if (projectId) {
      return (loadedPageIdsByProjectId.value[projectId] ?? new Set()).has(pageId)
    }
    return Object.values(loadedPageIdsByProjectId.value).some(set => set.has(pageId))
  }

  function setSelectedVariantOverride(pageId: string, variantId: string | null, projectId?: string) {
    const targetProjectId = projectId ?? activeProjectId.value ?? getPage(pageId)?.projectId
    if (!targetProjectId) return

    ensureProject(targetProjectId)
    const nextProjectVariants = {
      ...(selectedVariantIdByPageIdByProject.value[targetProjectId] ?? {}),
      [pageId]: variantId
    }
    selectedVariantIdByPageIdByProject.value = {
      ...selectedVariantIdByPageIdByProject.value,
      [targetProjectId]: nextProjectVariants
    }

    if (activeProjectId.value === targetProjectId) {
      selectedVariantIdByPageId.value = { ...nextProjectVariants }
    }
  }

  function updatePreferredImageVariantKey(key: string | null) {
    preferredImageVariantKey.value = key
  }

  function getDisplayedVariantForPage(page: PageData): ImageVariant | null {
    const projectVariants = selectedVariantIdByPageIdByProject.value[page.projectId] ?? {}
    const overrideId = projectVariants[page.id] ?? null

    if (overrideId) {
      return page.imageVariants.find(v => v.id === overrideId) ?? (page.imageVariants[0] ?? null)
    }

    const preferredKey = preferredImageVariantKey.value
    if (preferredKey) {
      const match = page.imageVariants.find(v => getVariantPreferenceKey(v) === preferredKey)
      if (match) return match
    }

    return page.imageVariants[0] ?? null
  }

  function getPreviewUrlForPage(page: PageData): string | null {
    const variant = getDisplayedVariantForPage(page)
    if (variant?.id && page.projectId) {
      return `/api/projects/${page.projectId}/pages/images/${variant.id}/thumbnail`
    }
    return page.thumbnail ?? variant?.url ?? page.imageVariants?.[0]?.url ?? null
  }

  function resolveVariantForPage(page: PageData, specificVariantId?: string): ImageVariant | null {
    if (specificVariantId) {
      return page.imageVariants.find(v => v.id === specificVariantId) ?? null
    }

    const preferredKey = preferredImageVariantKey.value
    const preferredVariant = preferredKey
      ? page.imageVariants.find(v => getVariantPreferenceKey(v) === preferredKey) ?? null
      : null

    return preferredVariant ?? page.imageVariants[0] ?? null
  }

  function setLabelSet(next: LabelSetModel | null, projectId?: string) {
    const targetProjectId = projectId ?? activeProjectId.value
    if (!targetProjectId) {
      labelSet.value = next
      return
    }
    labelSetByProjectId.value = {
      ...labelSetByProjectId.value,
      [targetProjectId]: next
    }
    if (activeProjectId.value === targetProjectId) {
      labelSet.value = next
    }
  }

  function setLabelSetFromApi(apiLabelSet: ApiLabelSet, projectId?: string) {
    setLabelSet(toLabelSetModel(apiLabelSet), projectId)
  }

  function clearLabelSet(projectId?: string) {
    setLabelSet(null, projectId)
  }

  function setProjectCodec(id: string | null, characters: string[], projectId?: string) {
    const targetProjectId = projectId ?? activeProjectId.value
    if (!targetProjectId) {
      projectCodecId.value = id
      projectCodecCharacters.value = [...new Set(characters ?? [])]
      return
    }
    projectCodecByProjectId.value = {
      ...projectCodecByProjectId.value,
      [targetProjectId]: {
        id,
        characters: [...new Set(characters ?? [])]
      }
    }
    if (activeProjectId.value === targetProjectId) {
      projectCodecId.value = id
      projectCodecCharacters.value = [...new Set(characters ?? [])]
    }
  }

  function clearProjectCodec(projectId?: string) {
    setProjectCodec(null, [], projectId)
  }

  function setProjectDictionary(payload: {
    id: string | null
    forms: DictionaryFormEntry[]
    caseSensitive?: boolean
    unicodeNormalization?: string
    canEdit?: boolean
    locked?: boolean
  }, projectId?: string) {
    const targetProjectId = projectId ?? activeProjectId.value
    const normalizedPayload = {
      id: payload.id,
      forms: [...(payload.forms ?? [])],
      caseSensitive: Boolean(payload.caseSensitive),
      unicodeNormalization: payload.unicodeNormalization || 'NFC',
      canEdit: Boolean(payload.canEdit),
      locked: Boolean(payload.locked)
    }

    if (!targetProjectId) {
      projectDictionaryId.value = normalizedPayload.id
      projectDictionaryForms.value = normalizedPayload.forms
      projectDictionaryCaseSensitive.value = normalizedPayload.caseSensitive
      projectDictionaryUnicodeNormalization.value = normalizedPayload.unicodeNormalization
      projectDictionaryCanEdit.value = normalizedPayload.canEdit
      projectDictionaryLocked.value = normalizedPayload.locked
      return
    }

    projectDictionaryByProjectId.value = {
      ...projectDictionaryByProjectId.value,
      [targetProjectId]: normalizedPayload
    }

    if (activeProjectId.value === targetProjectId) {
      projectDictionaryId.value = normalizedPayload.id
      projectDictionaryForms.value = normalizedPayload.forms
      projectDictionaryCaseSensitive.value = normalizedPayload.caseSensitive
      projectDictionaryUnicodeNormalization.value = normalizedPayload.unicodeNormalization
      projectDictionaryCanEdit.value = normalizedPayload.canEdit
      projectDictionaryLocked.value = normalizedPayload.locked
    }
  }

  function clearProjectDictionary(projectId?: string) {
    setProjectDictionary({ id: null, forms: [], caseSensitive: false, unicodeNormalization: 'NFC', canEdit: false, locked: false }, projectId)
  }

  function setProjectVirtualKeyboard(id: string | null, projectId?: string) {
    const targetProjectId = projectId ?? activeProjectId.value
    if (!targetProjectId) {
      projectVirtualKeyboardId.value = id
      return
    }
    projectVirtualKeyboardIdByProjectId.value = {
      ...projectVirtualKeyboardIdByProjectId.value,
      [targetProjectId]: id
    }
    if (activeProjectId.value === targetProjectId) {
      projectVirtualKeyboardId.value = id
    }
  }

  function clearProjectVirtualKeyboard(projectId?: string) {
    setProjectVirtualKeyboard(null, projectId)
  }

  function setProjectToolkitSettings(settings: Partial<ProjectToolkitSettings>, projectId?: string) {
    const targetProjectId = projectId ?? activeProjectId.value
    const normalized = {
      ...DEFAULT_TOOLKIT_SETTINGS,
      ...settings
    }
    if (!targetProjectId) {
      projectToolkitSettings.value = normalized
      return
    }
    projectToolkitSettingsByProjectId.value = {
      ...projectToolkitSettingsByProjectId.value,
      [targetProjectId]: normalized
    }
    if (activeProjectId.value === targetProjectId) {
      projectToolkitSettings.value = normalized
    }
  }

  function setProjectTextIndexDefaults(defaults: Partial<ProjectTextIndexDefaults>, projectId?: string) {
    const targetProjectId = projectId ?? activeProjectId.value
    const normalized = normalizeProjectTextIndexDefaults(defaults)
    if (!targetProjectId) {
      projectTextDefaultGtIndex.value = normalized.gtIndex
      projectTextDefaultRecognitionIndices.value = [...normalized.recognitionIndices]
      return
    }

    projectTextIndexDefaultsByProjectId.value = {
      ...projectTextIndexDefaultsByProjectId.value,
      [targetProjectId]: normalized
    }

    if (activeProjectId.value === targetProjectId) {
      projectTextDefaultGtIndex.value = normalized.gtIndex
      projectTextDefaultRecognitionIndices.value = [...normalized.recognitionIndices]
    }
  }

  function clearProjectTextIndexDefaults(projectId?: string) {
    setProjectTextIndexDefaults({ gtIndex: 0, recognitionIndices: [1] }, projectId)
  }

  return {
    pages,
    pagesByProjectId,
    allPages,
    loadedPageIds,
    loadedPageIdsByProjectId,
    preferredImageVariantKey,
    selectedVariantIdByPageId,
    selectedVariantIdByPageIdByProject,
    labelSet,
    labelSetByProjectId,
    projectCodecId,
    projectCodecCharacters,
    projectCodecByProjectId,
    projectDictionaryId,
    projectDictionaryForms,
    projectDictionaryCaseSensitive,
    projectDictionaryUnicodeNormalization,
    projectDictionaryCanEdit,
    projectDictionaryLocked,
    projectDictionaryByProjectId,
    projectVirtualKeyboardId,
    projectVirtualKeyboardIdByProjectId,
    projectToolkitSettings,
    projectToolkitSettingsByProjectId,
    projectTextIndexDefaultsByProjectId,
    projectTextDefaultGtIndex,
    projectTextDefaultRecognitionIndices,

    setPages,
    setPagesWithSession,
    setProjectPages,
    appendProjectPages,
    removeProject,
    getProjectPages,
    addPage,
    enrichPage,
    patchPageIndexingStatuses,
    patchProjectPageSummaries,
    getPage,
    isPageLoaded,
    setSelectedVariantOverride,
    updatePreferredImageVariantKey,
    getDisplayedVariantForPage,
    getPreviewUrlForPage,
    resolveVariantForPage,
    getVariantPreferenceKey,
    setLabelSet,
    setLabelSetFromApi,
    clearLabelSet,
    setProjectCodec,
    clearProjectCodec,
    setProjectDictionary,
    clearProjectDictionary,
    setProjectVirtualKeyboard,
    clearProjectVirtualKeyboard,
    setProjectToolkitSettings,
    setProjectTextIndexDefaults,
    clearProjectTextIndexDefaults
  }
})
