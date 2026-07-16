import type {
  AnnotationApiContext,
  CanvasState,
  ImageVariant,
  UiMode,
  UiModeScope,
  ToolbarLayout
} from './types'
import { useEditorUiStore } from './editor.ui.store'
import { useEditorDocumentStore } from './editor.document.store'
import { useEditorSessionStore } from './editor.session.store'
import { visibilityService } from '@/services/editor/visibility-service'
import { Metadata, PcGts, Page, Polygon, Polyline, TextLine, isTextRegion, type Region, type TextRegion } from '@/models/editor'
import type { Polygon as VisibilityPolygon, Polyline as VisibilityPolyline } from '@/services/editor/visibility-service'
import { clearAllEditorSessions, ensureEditorSession, destroyEditorSession, getEditorSession } from '@/session/editor/editor-session'
import { collectPolylinesFromPcGts, collectPolygonsFromPcGts } from './editor.collectors.store'
import { createScopedLogger } from '@/services/editor/logger-service'
import { convertPageDtoToPcGts, convertPcGtsToPageDto, type PageDto } from '@/services/editor/page-conversion.service'
import { LRUCache } from '@/utils/lru-cache'
import { loadSinglePageData } from '@/services/editor/project-loader'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'

const log = createScopedLogger('EditorStore')

function nowIso(): string {
  return new Date().toISOString()
}

function pointsToTuples(points: { x: number, y: number }[]): [number, number][] {
  return points.map(p => [p.x, p.y])
}

function resolveMetadataImageFilename(variant: ImageVariant): string {
  const fileName = variant.fileName?.trim()
  if (fileName) return fileName
  const label = variant.label?.trim()
  if (label) return label
  return variant.url
}

function defaultProjectAnnotationContext(projectId: string, pageId: string): AnnotationApiContext {
  return {
    mode: 'PROJECT',
    basePath: `/api/projects/${projectId}/pages/${pageId}/annotations`,
    createAllowed: true
  }
}

function normalizeAnnotationContext(
  context: AnnotationApiContext | null | undefined,
  projectId: string,
  pageId: string
): AnnotationApiContext {
  if (!context?.basePath) {
    return defaultProjectAnnotationContext(projectId, pageId)
  }
  return context
}

function annotationResourcePath(context: AnnotationApiContext, xmlId: string): string {
  return `${context.basePath}/${xmlId}`
}

function xmlBasePathFromAnnotationContext(context: AnnotationApiContext): string {
  if (context.basePath.endsWith('/annotations')) {
    return `${context.basePath.slice(0, -'/annotations'.length)}/xml`
  }
  return context.basePath.replace(/\/annotations$/, '/xml')
}

function createEmptyPcGts(params: {
  imageFilename: string
  imageWidth: number
  imageHeight: number
  creator?: string
  pcGtsId?: string
}): PcGts {
  const now = nowIso()
  const metadata = new Metadata({ creator: params.creator, created: now, lastChange: now })
  const page = new Page({
    imageFilename: params.imageFilename,
    imageWidth: params.imageWidth,
    imageHeight: params.imageHeight,
    regions: []
  })
  return new PcGts(metadata, page, params.pcGtsId)
}

function findRegionRecursive(regions: Region[], id: string): { region: Region, parent: Region | null } | null {
  for (const region of regions) {
    if (region.id === id) return { region, parent: null }
    if (region.regions && region.regions.length > 0) {
      const stack: Array<{ region: Region, parent: Region | null }> = []
      for (const child of region.regions) stack.push({ region: child, parent: region })
      while (stack.length > 0) {
        const current = stack.pop()
        if (!current) continue
        if (current.region.id === id) return current
        if (current.region.regions) {
          for (const child of current.region.regions) stack.push({ region: child, parent: current.region })
        }
      }
    }
  }
  return null
}

function findTextLineInRegions(regions: Region[], textLineId: string): { textRegion: TextRegion, textLine: TextLine } | null {
  const stack: Region[] = [...regions]
  while (stack.length > 0) {
    const region = stack.pop()
    if (!region) continue
    if (isTextRegion(region) && region.textLines) {
      const hit = region.textLines.find(tl => tl.id === textLineId)
      if (hit) return { textRegion: region, textLine: hit }
    }
    if (region.regions) stack.push(...region.regions)
  }
  return null
}

export const useEditorStore = defineStore('editor', () => {
  const { user: sessionUser } = useUserSession()
  const uiStore = useEditorUiStore()
  const documentStore = useEditorDocumentStore()
  const sessionStore = useEditorSessionStore()

  const {
    uiMode,
    uiModeScope,
    uiModeByCanvasId,
    toolbarLayout,
    globalSettings
  } = storeToRefs(uiStore)

  const {
    pages,
    allPages, // computed in doc store
    preferredImageVariantKey,
    selectedVariantIdByPageId,
    labelSet,
    projectCodecId,
    projectCodecCharacters,
    projectDictionaryId,
    projectDictionaryForms,
    projectDictionaryCaseSensitive,
    projectDictionaryUnicodeNormalization,
    projectDictionaryCanEdit,
    projectDictionaryLocked,
    projectVirtualKeyboardId,
    projectToolkitSettings,
    projectTextDefaultGtIndex,
    projectTextDefaultRecognitionIndices
  } = storeToRefs(documentStore)

  const { effectiveUiMode } = uiStore
  const {
    getDisplayedVariantForPage,
    getPreviewUrlForPage,
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
  } = documentStore

  const canvases = ref<Record<string, CanvasState>>({})
  const activeCanvasId = ref<string | null>(null)

  function authenticatedUsername(): string | undefined {
    const username = sessionUser.value?.login?.trim()
    return username || undefined
  }

  const currentPageId = ref<string | null>(null)
  const currentImageVariantId = ref<string | null>(null)
  const currentProjectId = computed(() => {
    const canvasId = activeCanvasId.value
    if (!canvasId) return null
    return canvases.value[canvasId]?.projectId ?? null
  })

  const unsavedCanvasEntries = computed(() => {
    return Object.entries(canvases.value)
      .filter(([, canvas]) => !!canvas.pageId && canvas.hasUnsavedChanges)
      .map(([id, canvas]) => ({ id, canvas }))
  })

  const annotationCache = new LRUCache<string, PageDto>(50)

  const pendingPrefetches = ref<Set<string>>(new Set())
  const prefetchTimeoutByProjectId = new Map<string, ReturnType<typeof setTimeout>>()
  const prefetchGenerationByProjectId = new Map<string, number>()
  const prefetchBatchByProjectId = new Map<string, { generation: number, controller: AbortController }>()
  const pageXmlPrefetchCache = new Map<string, string | null>()
  const pageXmlLookupByKey = new Map<string, Promise<string | null>>()
  const adjacentPrefetchPageScopeByProjectId = new Map<string, string[]>()

  const ADJACENT_PREFETCH_DELAY_MS = 300
  const ADJACENT_PREFETCH_CONCURRENCY = 2

  /**
   * Get the currently active canvas state
   */
  const activeCanvas = computed<CanvasState | undefined>(() => {
    if (!activeCanvasId.value) return undefined
    return canvases.value[activeCanvasId.value]
  })

  /**
   * Get all canvases as an array
   */
  const allCanvases = computed<CanvasState[]>(() => {
    return Object.values(canvases.value)
  })

  /**
   * Derived polygons (regions + textlines) from active canvas PAGE model.
   */
  const activeRegions = computed<VisibilityPolygon[]>(() => {
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!pcGts) return []
    return collectPolygonsFromPcGts(pcGts)
  })

  /**
   * Get regions from a specific canvas document
   */
  const regionsByCanvasId = (canvasId: string): VisibilityPolygon[] => {
    const session = canvasId ? getEditorSession(canvasId) : undefined
    const pcGts = session?.document.value
    if (!pcGts) return []
    return collectPolygonsFromPcGts(pcGts)
  }

  /**
   * Derived polylines (baselines) from active canvas PAGE model.
   */
  const activeBaselines = computed<VisibilityPolyline[]>(() => {
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!pcGts) return []
    return collectPolylinesFromPcGts(pcGts)
  })

  /**
   * Get baselines from a specific canvas document
   */
  const baselinesByCanvasId = (canvasId: string): VisibilityPolyline[] => {
    const session = canvasId ? getEditorSession(canvasId) : undefined
    const pcGts = session?.document.value
    if (!pcGts) return []
    return collectPolylinesFromPcGts(pcGts)
  }

  /**
   * Get selected region ID from active canvas
   */
  const activeSelectedRegionId = computed<string | null>(() => {
    const canvas = activeCanvasId.value ? canvases.value[activeCanvasId.value] : undefined
    return canvas?.selectedRegionId ?? null
  })

  /**
   * Get selected baseline ID from active canvas
   */
  const activeSelectedBaselineId = computed<string | null>(() => {
    const canvas = activeCanvasId.value ? canvases.value[activeCanvasId.value] : undefined
    return canvas?.selectedBaselineId ?? null
  })

  /**
   * Selected polygon (region or textline) from derived view.
   */
  const selectedRegion = computed<VisibilityPolygon | null>(() => {
    const canvas = activeCanvasId.value ? canvases.value[activeCanvasId.value] : undefined
    if (!canvas?.selectedRegionId) return null
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!pcGts) return null
    const polygons = collectPolygonsFromPcGts(pcGts)
    return polygons.find(p => p.id === canvas.selectedRegionId) ?? null
  })

  /**
   * Selected baseline polyline from derived view.
   */
  const selectedBaseline = computed<VisibilityPolyline | null>(() => {
    const canvas = activeCanvasId.value ? canvases.value[activeCanvasId.value] : undefined
    if (!canvas?.selectedBaselineId) return null
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!pcGts) return null
    const polylines = collectPolylinesFromPcGts(pcGts)
    return polylines.find(pl => pl.id === canvas.selectedBaselineId) ?? null
  })

  /**
   * Get drawing mode from active canvas
   */
  const activeDrawingMode = computed<string>(() => {
    const canvas = activeCanvasId.value ? canvases.value[activeCanvasId.value] : undefined
    return canvas?.drawingMode ?? 'select'
  })

  /**
   * Check if a canvas is active
   */
  const isCanvasActive = (id: string): boolean => {
    return activeCanvasId.value === id
  }

  /**
   * Get the currently loaded page
   */
  const currentPage = computed(() => {
    if (!currentPageId.value) return null
    return documentStore.getPage(currentPageId.value, currentProjectId.value ?? undefined) ?? null
  })

  /**
   * Get the currently selected image variant
   */
  const currentImageVariant = computed<ImageVariant | null>(() => {
    const page = currentPage.value
    if (!page || !currentImageVariantId.value) return null
    return page.imageVariants.find(v => v.id === currentImageVariantId.value) ?? null
  })

  function setUiMode(mode: UiMode) {
    uiStore.setUiMode(mode, activeCanvasId.value)
  }

  function setUiModeScope(scope: UiModeScope) {
    uiStore.setUiModeScope(scope, activeCanvasId.value, Object.keys(canvases.value))
  }

  function updateGlobalSettings(settings: any) {
    uiStore.updateGlobalSettings(settings)
  }
  function toggleConstrainToImage() {
    uiStore.toggleConstrainToImage()
  }
  function toggleConstrainToParent() {
    uiStore.toggleConstrainToParent()
  }
  function toggleAutoSelect() {
    uiStore.toggleAutoSelect()
  }
  function setToolbarLayout(layout: ToolbarLayout) {
    uiStore.setToolbarLayout(layout)
  }

  function setSelectedVariantOverride(pageId: string, variantId: string | null, projectId?: string) {
    documentStore.setSelectedVariantOverride(pageId, variantId, projectId)
  }

  /**
   * Set the globally preferred image variant (by type/label).
   * Applies to the active canvas immediately.
   */
  function setPreferredImageVariantKey(key: string | null) {
    documentStore.updatePreferredImageVariantKey(key)

    const canvasId = activeCanvasId.value
    if (!canvasId) return
    const canvas = canvases.value[canvasId]
    if (!canvas) return
    const pageId = canvas.pageId ?? null
    if (!pageId) return
    const page = documentStore.getPage(pageId, canvas?.projectId ?? undefined)
    if (!page) return

    const nextVariant = documentStore.resolveVariantForPage(page)
    if (!nextVariant) return

    canvas.imageVariantId = nextVariant.id
    canvas.imageSrc = nextVariant.url
    const session = getEditorSession(canvasId)
    if (session?.document.value?.page) {
      session.document.value.page.imageFilename = resolveMetadataImageFilename(nextVariant)
      triggerRef(session.document)
    }

    if (activeCanvasId.value === canvasId) {
      currentImageVariantId.value = nextVariant.id
    }
  }

  /**
   * Register a new canvas instance
   */
  function registerCanvas(id: string, initialState?: Partial<CanvasState>) {
    if (canvases.value[id]) {
      return
    }

    uiStore.initializeCanvasUiMode(id)

    const pcGts = createEmptyPcGts({
      imageFilename: `canvas-${id}`,
      imageWidth: 1000,
      imageHeight: 1000,
      creator: authenticatedUsername(),
      pcGtsId: `pcgts-${id}`
    })
    const session = ensureEditorSession(id, { document: pcGts })

    canvases.value[id] = {
      id,
      projectId: null,
      pageId: null,
      imageVariantId: null,
      imageSrc: null,
      selectedRegionId: null,
      selectedBaselineId: null,
      drawingMode: 'select',
      regionType: 'text-region',
      hoveredRegionId: null,
      hoveredBaselineId: null,
      hoveredNodeIndex: -1,
      hasUnsavedChanges: false,
      historyBaselineIndex: -1,
      historyCurrentIndex: -1,
      ...initialState
    }

    const regions = regionsByCanvasId(id)
    const baselines = baselinesByCanvasId(id)
    if (regions.length > 0) session.spatialIndex.rebuildPolygonIndex(regions)
    if (baselines.length > 0) session.spatialIndex.rebuildPolylineIndex(baselines)

    if (!activeCanvasId.value) {
      activeCanvasId.value = id
      currentPageId.value = canvases.value[id]?.pageId ?? null
      currentImageVariantId.value = canvases.value[id]?.imageVariantId ?? null
    }
  }

  /**
   * Unregister a canvas instance
   */
  function unregisterCanvas(id: string) {
    delete canvases.value[id]
    uiStore.removeCanvasUiMode(id)
    destroyEditorSession(id)

    if (activeCanvasId.value === id) {
      const keys = Object.keys(canvases.value)
      if (keys.length > 0) {
        activeCanvasId.value = keys[0] ?? null
      } else {
        activeCanvasId.value = null
      }
    }
  }

  function resetEditorState(): void {
    clearAnnotationCache()
    adjacentPrefetchPageScopeByProjectId.clear()

    for (const canvasId of Object.keys(canvases.value)) {
      uiStore.removeCanvasUiMode(canvasId)
    }
    clearAllEditorSessions()
    canvases.value = {}
    activeCanvasId.value = null
    currentPageId.value = null
    currentImageVariantId.value = null

    for (const projectId of Object.keys(documentStore.pagesByProjectId)) {
      removeProject(projectId)
      clearLabelSet(projectId)
      clearProjectCodec(projectId)
    }
    pages.value = []
    selectedVariantIdByPageId.value = {}
    clearLabelSet()
    clearProjectCodec()
  }

  function updateCanvasHistoryState(canvasId: string, currentIndex: number): void {
    const canvas = canvases.value[canvasId]
    if (!canvas) return

    canvas.historyCurrentIndex = currentIndex
    if (typeof canvas.historyBaselineIndex !== 'number') {
      canvas.historyBaselineIndex = currentIndex
    }
    canvas.hasUnsavedChanges = canvas.historyBaselineIndex !== canvas.historyCurrentIndex
  }

  function resetCanvasHistoryBaseline(canvasId: string, baselineIndex?: number): void {
    const canvas = canvases.value[canvasId]
    if (!canvas) return

    const nextBaseline = typeof baselineIndex === 'number'
      ? baselineIndex
      : (typeof canvas.historyCurrentIndex === 'number' ? canvas.historyCurrentIndex : -1)
    canvas.historyBaselineIndex = nextBaseline
    canvas.hasUnsavedChanges = false
  }

  function hasUnsavedChangesForPage(pageId: string, projectId?: string): boolean {
    return Object.values(canvases.value).some((canvas) => {
      if (canvas.pageId !== pageId || canvas.hasUnsavedChanges !== true) return false
      if (!projectId) return true
      return canvas.projectId === projectId
    })
  }

  /**
   * Set the active canvas
   */
  function setActiveCanvas(id: string) {
    if (canvases.value[id]) {
      activeCanvasId.value = id

      uiStore.initializeCanvasUiMode(id)

      const canvas = canvases.value[id]
      currentPageId.value = canvas?.pageId ?? null
      currentImageVariantId.value = canvas?.imageVariantId ?? null
    }
  }

  /**
   * Replace entire document in a specific canvas
   */
  function setCanvasDocument(canvasId: string, document: PcGts | null) {
    const session = canvasId ? ensureEditorSession(canvasId) : undefined
    if (!session) return

    session.document.value = document
    resetCanvasHistoryBaseline(canvasId)

    const regions = regionsByCanvasId(canvasId)
    const baselines = baselinesByCanvasId(canvasId)
    session.spatialIndex.rebuildPolygonIndex(regions)
    session.spatialIndex.rebuildPolylineIndex(baselines)
    visibilityService.clearCache()
  }

  /**
   * Get a specific canvas by ID
   */
  function getCanvas(id: string): CanvasState | undefined {
    return canvases.value[id]
  }

  /**
   * Add a region to the active canvas document
   */
  function addRegion(polygon: VisibilityPolygon & { regionKind?: string, regionSubtype?: string, label?: string }, _pageId?: string) {
    const canvas = activeCanvas.value
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!canvas || !session || !pcGts) return

    const kind = polygon.regionKind || 'TextRegion'

    if (polygon.type === 'textline') {
      const parentId = polygon.parentId
      if (!parentId) return
      const hit = findRegionRecursive(pcGts.page.regions, parentId)
      if (!hit || !isTextRegion(hit.region)) return
      const textRegion = hit.region
      textRegion.textLines = textRegion.textLines ?? []
      textRegion.textLines.push(new TextLine({
        id: polygon.id,
        coords: new Polygon(pointsToTuples(polygon.points))
      }))
    } else {
      const region: any = {
        id: polygon.id,
        kind: kind,
        coords: new Polygon(pointsToTuples(polygon.points)),
        regions: [],
        textLines: [],
        textContentVariants: []
      }
      if (kind === 'TextRegion') {
        region.type = polygon.regionSubtype || polygon.label || 'paragraph'
      }
      if (polygon.parentId) {
        const parentHit = findRegionRecursive(pcGts.page.regions, polygon.parentId)
        if (parentHit) {
          parentHit.region.regions = parentHit.region.regions ?? []
          parentHit.region.regions.push(region)
        } else {
          pcGts.page.regions.push(region)
        }
      } else {
        pcGts.page.regions.push(region)
      }
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)

    const regions = activeRegions.value
    const baselines = activeBaselines.value
    session.spatialIndex.rebuildPolygonIndex(regions as any)
    session.spatialIndex.rebuildPolylineIndex(baselines as any)
    visibilityService.clearCache()
  }

  /**
   * Update a region in the active canvas document
   */
  function updateRegion(regionId: string, updates: Partial<VisibilityPolygon>) {
    const canvas = activeCanvas.value
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!canvas || !session || !pcGts) return

    const regionHit = findRegionRecursive(pcGts.page.regions, regionId)
    if (regionHit) {
      if (updates.points) regionHit.region.coords.points = pointsToTuples(updates.points)
    } else {
      const textLineHit = findTextLineInRegions(pcGts.page.regions, regionId)
      if (textLineHit && updates.points) {
        textLineHit.textLine.coords.points = pointsToTuples(updates.points)
      }
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)

    const regions = activeRegions.value
    const baselines = activeBaselines.value
    session.spatialIndex.rebuildPolygonIndex(regions as any)
    session.spatialIndex.rebuildPolylineIndex(baselines as any)
    visibilityService.clearCache()
  }

  /**
   * Remove a region from the active canvas document
   */
  function removeRegion(regionId: string) {
    const canvas = activeCanvas.value
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!canvas || !session || !pcGts) return

    const removeFrom = (list: Region[]): boolean => {
      const idx = list.findIndex(r => r.id === regionId)
      if (idx >= 0) {
        list.splice(idx, 1)
        return true
      }
      for (const r of list) {
        if (r.regions && removeFrom(r.regions)) return true
        if (isTextRegion(r) && r.textLines) {
          const tlIdx = r.textLines.findIndex(tl => tl.id === regionId)
          if (tlIdx >= 0) {
            r.textLines.splice(tlIdx, 1)
            return true
          }
        }
      }
      return false
    }

    removeFrom(pcGts.page.regions)

    if (canvas.selectedRegionId === regionId) canvas.selectedRegionId = null
    if (canvas.selectedBaselineId === `baseline:${regionId}`) canvas.selectedBaselineId = null

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)

    const regions = activeRegions.value
    const baselines = activeBaselines.value
    session.spatialIndex.rebuildPolygonIndex(regions as any)
    session.spatialIndex.rebuildPolylineIndex(baselines as any)
    visibilityService.clearCache()
  }

  /**
   * Select a region by ID
   */
  function selectRegionById(regionId: string) {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.selectedRegionId = regionId
    }
  }

  /**
   * Clear region selection
   */
  function clearRegionSelection() {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.selectedRegionId = null
    }
  }

  /**
   * Add a baseline to a text line in the active canvas document
   */
  function addBaseline(baseline: VisibilityPolyline) {
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const parentTextLineId = baseline.parentId
    if (!parentTextLineId) return
    const hit = findTextLineInRegions(pcGts.page.regions, parentTextLineId)
    if (!hit) return

    hit.textLine.baseline = {
      points: new Polyline(pointsToTuples(baseline.points))
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    session.spatialIndex.rebuildPolylineIndex(activeBaselines.value as any)
    visibilityService.clearCache()
  }

  function updateBaseline(baselineId: string, updates: Partial<VisibilityPolyline>) {
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const textLineId = baselineId.startsWith('baseline:') ? baselineId.slice('baseline:'.length) : baselineId
    const hit = findTextLineInRegions(pcGts.page.regions, textLineId)
    if (!hit) return

    if (updates.points) {
      hit.textLine.baseline = hit.textLine.baseline ?? { points: new Polyline([]) }
      hit.textLine.baseline.points.points = pointsToTuples(updates.points)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    session.spatialIndex.rebuildPolylineIndex(activeBaselines.value as any)
    visibilityService.clearCache()
  }

  function removeBaseline(baselineId: string) {
    const canvas = activeCanvas.value
    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (!canvas || !session || !pcGts) return

    const textLineId = baselineId.startsWith('baseline:') ? baselineId.slice('baseline:'.length) : baselineId
    const hit = findTextLineInRegions(pcGts.page.regions, textLineId)
    if (hit) {
      hit.textLine.baseline = undefined
    }

    if (canvas.selectedBaselineId === baselineId) canvas.selectedBaselineId = null
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    session.spatialIndex.rebuildPolylineIndex(activeBaselines.value as any)
    visibilityService.clearCache()
  }

  function selectBaselineById(baselineId: string) {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.selectedBaselineId = baselineId
    }
  }

  function clearBaselineSelection() {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.selectedBaselineId = null
    }
  }

  function clearCanvasSelection(canvasId?: string | null) {
    const targetCanvasId = canvasId ?? activeCanvasId.value
    if (!targetCanvasId) return

    const canvas = canvases.value[targetCanvasId]
    if (!canvas) return

    canvas.selectedRegionId = null
    canvas.selectedBaselineId = null
  }

  function setDrawingMode(mode: string) {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.drawingMode = mode
    }
  }

  function setRegionType(type: string) {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.regionType = type
    }
  }

  function setHoveredRegionId(regionId: string | null) {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.hoveredRegionId = regionId
    }
  }

  function setHoveredBaselineId(baselineId: string | null) {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.hoveredBaselineId = baselineId
    }
  }

  function setHoveredNodeIndex(index: number) {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.hoveredNodeIndex = index
    }
  }

  function setImageSize(width: number, height: number) {
    const canvas = activeCanvas.value
    if (canvas) {
      canvas.imageSize = { width, height }
    }

    const session = activeCanvasId.value ? getEditorSession(activeCanvasId.value) : undefined
    const pcGts = session?.document.value
    if (session && pcGts) {
      pcGts.page.imageWidth = width
      pcGts.page.imageHeight = height
      session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    }
  }

  function setDocument(document: PcGts) {
    const session = activeCanvasId.value ? ensureEditorSession(activeCanvasId.value) : undefined
    if (!session) return

    session.document.value = document

    const regions = activeRegions.value
    const baselines = activeBaselines.value
    session.spatialIndex.rebuildPolygonIndex(regions as any)
    session.spatialIndex.rebuildPolylineIndex(baselines as any)
    visibilityService.clearCache()
  }

  function clearAllAnnotations() {
    const canvas = activeCanvas.value
    const session = activeCanvasId.value ? ensureEditorSession(activeCanvasId.value) : undefined
    if (!canvas || !session) return

    const pcGts = createEmptyPcGts({
      imageFilename: session.document.value?.page.imageFilename ?? `canvas-${canvas.id}`,
      imageWidth: session.document.value?.page.imageWidth ?? 1000,
      imageHeight: session.document.value?.page.imageHeight ?? 1000,
      creator: session.document.value?.metadata.creator ?? authenticatedUsername(),
      pcGtsId: session.document.value?.pcGtsId
    })
    session.document.value = pcGts

    canvas.selectedRegionId = null
    canvas.selectedBaselineId = null
    session.spatialIndex.clear()
    visibilityService.clearCache()
  }

  function getAnnotationContextForCanvas(canvas: CanvasState): AnnotationApiContext | null {
    const projectId = canvas.projectId ?? null
    const pageId = canvas.pageId ?? null
    if (!projectId || !pageId) return null

    if (canvas.annotationContext?.basePath) {
      return canvas.annotationContext
    }

    const page = documentStore.getPage(pageId, projectId)
    return normalizeAnnotationContext(page?.annotationContext, projectId, pageId)
  }

  function buildAnnotationCacheKey(projectId: string, pageId: string, context: AnnotationApiContext, xmlId: string): string {
    return `${projectId}:${pageId}:${context.basePath}:${xmlId}`
  }

  /**
   * Load a page into a canvas, including fetching annotations from the backend.
   * If the page hasn't been enriched yet (skeleton data only), enriches it first via API calls.
   */
  async function loadPageIntoCanvas(canvasId: string, projectId: string, pageId: string, variantId?: string): Promise<string | null> {
    let page = documentStore.getPage(pageId, projectId)
    if (!page) {
      log.error(`Page ${pageId} not found in project ${projectId}`)
      return null
    }

    if (!canvases.value[canvasId]) {
      registerCanvas(canvasId)
    }

    const canvas = canvases.value[canvasId]
    if (!canvas) {
      log.error(`Canvas ${canvasId} not found`)
      return null
    }

    if (!documentStore.isPageLoaded(pageId, projectId)) {
      canvas.isLoadingAnnotations = true
      try {
        const enrichedData = await loadSinglePageData(projectId, page)
        documentStore.enrichPage(pageId, { ...enrichedData, projectId, projectName: page.projectName }, projectId)
        page = documentStore.getPage(pageId, projectId)!
      } catch (err) {
        log.error(`Failed to enrich page ${pageId} on demand:`, err)
      } finally {
        canvas.isLoadingAnnotations = false
      }
    }

    const variant = documentStore.resolveVariantForPage(page, variantId)
    if (!variant) {
      log.error(`No image variant found for page ${pageId}`)
      return null
    }
    const metadataImageFilename = resolveMetadataImageFilename(variant)

    if (!documentStore.preferredImageVariantKey) {
      documentStore.updatePreferredImageVariantKey(documentStore.getVariantPreferenceKey(variant))
    }

    canvas.projectId = projectId
    canvas.pageId = pageId
    canvas.imageVariantId = variant.id
    canvas.imageSrc = variant.url
    canvas.annotationContext = normalizeAnnotationContext(page.annotationContext, projectId, pageId)

    if (activeCanvasId.value === canvasId) {
      currentPageId.value = pageId
      currentImageVariantId.value = variant.id
      sessionStore.setActiveProject(projectId)
    }

    const emptyPcGts = createEmptyPcGts({
      imageFilename: metadataImageFilename,
      imageWidth: canvas.imageSize?.width ?? 1000,
      imageHeight: canvas.imageSize?.height ?? 1000,
      creator: authenticatedUsername(),
      pcGtsId: `pcgts-${pageId}`
    })
    setCanvasDocument(canvasId, emptyPcGts)

    canvas.selectedRegionId = null
    canvas.selectedBaselineId = null

    if (page.xmlFiles && page.xmlFiles.length > 0) {
      loadAnnotationsForCanvas(canvasId, projectId, pageId, page.xmlFiles, metadataImageFilename, canvas.annotationContext ?? undefined)
    } else {
      log.info(`No XML files available for page ${pageId}, using empty document`)
    }

    return variant.url
  }

  /**
   * Load annotations from backend for a specific canvas/page.
   * This function runs asynchronously and updates the canvas document when complete.
   * Uses caching to avoid re-fetching previously loaded annotations.
   */
  async function loadAnnotationsForCanvas(
    canvasId: string,
    projectId: string,
    pageId: string,
    xmlFiles: { id: string, fileName: string, schema: string }[],
    imageFilename?: string,
    annotationContext?: AnnotationApiContext
  ): Promise<void> {
    const pageXmlFile = xmlFiles.find(xml => xml.schema === 'PAGE_XML')
    if (!pageXmlFile) {
      log.info(`No PAGE XML file found for page ${pageId}, using empty document`)
      return
    }

    const context = normalizeAnnotationContext(annotationContext, projectId, pageId)
    const cacheKey = buildAnnotationCacheKey(projectId, pageId, context, pageXmlFile.id)
    pageXmlPrefetchCache.set(`${projectId}:${pageId}`, pageXmlFile.id)
    const canvas = canvases.value[canvasId]

    if (canvas) {
      canvas.isLoadingAnnotations = true
      canvas.annotationContext = context
    }

    try {
      let pageDto: PageDto

      if (annotationCache.has(cacheKey)) {
        log.info(`Using cached annotations for page ${pageId}`)
        pageDto = annotationCache.get(cacheKey)!
      } else {
        log.info(`Fetching annotations from ${pageXmlFile.fileName} (id: ${pageXmlFile.id}) for page ${pageId}`)

        pageDto = await $fetch<PageDto>(
          annotationResourcePath(context, pageXmlFile.id)
        )

        annotationCache.set(cacheKey, pageDto)

        log.info(`Cached annotations for page ${pageId}`)
      }

      const pcGts = convertPageDtoToPcGts(pageDto)

      if (pcGts.page && imageFilename) {
        pcGts.page.imageFilename = imageFilename
      }

      const regionCount = pcGts.page?.regions?.length ?? 0
      log.info(`Successfully loaded annotations for page ${pageId}: ${regionCount} regions`)

      setCanvasDocument(canvasId, pcGts)

      if (canvas) {
        canvas.xmlFileId = pageXmlFile.id
      }

      scheduleAdjacentAnnotationPrefetch(projectId, pageId)
    } catch (error) {
      log.error(`Failed to load annotations for page ${pageId}:`, error)
    } finally {
      if (canvas) {
        canvas.isLoadingAnnotations = false
      }
    }
  }

  function isAbortError(error: unknown): boolean {
    return typeof error === 'object'
      && error !== null
      && 'name' in error
      && (error as { name?: unknown }).name === 'AbortError'
  }

  function getNextPrefetchGeneration(projectId: string): number {
    const nextGeneration = (prefetchGenerationByProjectId.get(projectId) ?? 0) + 1
    prefetchGenerationByProjectId.set(projectId, nextGeneration)
    return nextGeneration
  }

  function isPrefetchGenerationCurrent(projectId: string, generation: number): boolean {
    return prefetchGenerationByProjectId.get(projectId) === generation
  }

  function clearPrefetchTimeout(projectId: string): void {
    const timeout = prefetchTimeoutByProjectId.get(projectId)
    if (timeout) {
      clearTimeout(timeout)
      prefetchTimeoutByProjectId.delete(projectId)
    }
  }

  function cancelPrefetchBatch(projectId: string): void {
    const batch = prefetchBatchByProjectId.get(projectId)
    if (!batch) return
    batch.controller.abort()
    prefetchBatchByProjectId.delete(projectId)
  }

  function setAdjacentPrefetchPageScope(projectId: string, pageIds: string[] | null): void {
    if (pageIds === null) {
      if (!adjacentPrefetchPageScopeByProjectId.has(projectId)) return
      adjacentPrefetchPageScopeByProjectId.delete(projectId)
      clearPrefetchTimeout(projectId)
      cancelPrefetchBatch(projectId)
      getNextPrefetchGeneration(projectId)
      return
    }

    const nextScope = [...pageIds]
    const currentScope = adjacentPrefetchPageScopeByProjectId.get(projectId)
    const isUnchanged = !!currentScope
      && currentScope.length === nextScope.length
      && currentScope.every((pageId, index) => pageId === nextScope[index])
    if (isUnchanged) return

    adjacentPrefetchPageScopeByProjectId.set(projectId, nextScope)
    clearPrefetchTimeout(projectId)
    cancelPrefetchBatch(projectId)
    getNextPrefetchGeneration(projectId)
  }

  function scheduleAdjacentAnnotationPrefetch(projectId: string, currentPageId: string): void {
    clearPrefetchTimeout(projectId)
    cancelPrefetchBatch(projectId)

    const generation = getNextPrefetchGeneration(projectId)
    const timeout = setTimeout(() => {
      prefetchTimeoutByProjectId.delete(projectId)
      void prefetchAdjacentAnnotations(projectId, currentPageId, generation).catch((error) => {
        if (!isAbortError(error)) {
          log.warn(`Adjacent annotation prefetch failed for project ${projectId}:`, error)
        }
      })
    }, ADJACENT_PREFETCH_DELAY_MS)

    prefetchTimeoutByProjectId.set(projectId, timeout)
  }

  async function resolvePageXmlIdForPrefetch(
    projectId: string,
    page: { id: string, xmlFiles?: { id: string, schema: string }[], xmlFileCount?: number, annotationContext?: AnnotationApiContext },
    signal: AbortSignal
  ): Promise<string | null> {
    const pageKey = `${projectId}:${page.id}`

    const pageXmlInPageData = page.xmlFiles?.find(xml => xml.schema === 'PAGE_XML')?.id ?? null
    if (pageXmlInPageData) {
      pageXmlPrefetchCache.set(pageKey, pageXmlInPageData)
      return pageXmlInPageData
    }

    if (pageXmlPrefetchCache.has(pageKey)) {
      return pageXmlPrefetchCache.get(pageKey) ?? null
    }

    if (page.xmlFileCount === 0) {
      pageXmlPrefetchCache.set(pageKey, null)
      return null
    }

    const pendingLookup = pageXmlLookupByKey.get(pageKey)
    if (pendingLookup) return pendingLookup

    const lookupPromise = (async () => {
      try {
        const context = normalizeAnnotationContext(page.annotationContext, projectId, page.id)
        const xmlFiles = await $fetch<Array<{ id: string, schema: string }>>(
          xmlBasePathFromAnnotationContext(context),
          { signal }
        )
        const pageXmlId = xmlFiles.find(xml => xml.schema === 'PAGE_XML')?.id ?? null
        pageXmlPrefetchCache.set(pageKey, pageXmlId)
        return pageXmlId
      } catch (error) {
        if (!isAbortError(error)) {
          log.warn(`Failed to resolve PAGE XML file for prefetch on page ${page.id}:`, error)
        }
        return null
      } finally {
        pageXmlLookupByKey.delete(pageKey)
      }
    })()

    pageXmlLookupByKey.set(pageKey, lookupPromise)
    return lookupPromise
  }

  /**
   * Prefetch annotations for adjacent pages with bidirectional priority.
   * Prefetches next 5 pages and previous 5 pages around the current page.
   * Runs as a delayed idle task and is canceled when navigation changes.
   * Uses low concurrency and lightweight XML-id resolution for skeleton pages.
   */
  async function prefetchAdjacentAnnotations(projectId: string, currentPageId: string, generation: number): Promise<void> {
    if (!isPrefetchGenerationCurrent(projectId, generation)) return

    const allPagesList = getProjectPages(projectId)
    const scopedPageIds = adjacentPrefetchPageScopeByProjectId.get(projectId)
    const pagesById = scopedPageIds ? new Map(allPagesList.map(page => [page.id, page])) : null
    const scopedPageList = scopedPageIds
      ? scopedPageIds.map(pageId => pagesById?.get(pageId)).filter((page): page is NonNullable<typeof page> => !!page)
      : null
    const pagesList = scopedPageList ?? allPagesList

    if (pagesList.length === 0) return

    const currentIndex = pagesList.findIndex(p => p.id === currentPageId)
    if (currentIndex === -1) return

    const indicesToPrefetch = [
      currentIndex + 1,
      currentIndex + 2,
      currentIndex + 3,
      currentIndex + 4,
      currentIndex + 5,
      currentIndex - 1,
      currentIndex - 2,
      currentIndex - 3,
      currentIndex - 4,
      currentIndex - 5
    ].filter(i => i >= 0 && i < pagesList.length)

    const pagesToPrefetch = indicesToPrefetch
      .map(index => pagesList[index])
      .filter((page): page is NonNullable<typeof page> => !!page)

    if (pagesToPrefetch.length === 0) return

    const controller = new AbortController()
    prefetchBatchByProjectId.set(projectId, { generation, controller })

    try {
      let cursor = 0
      const workerCount = Math.min(ADJACENT_PREFETCH_CONCURRENCY, pagesToPrefetch.length)

      const prefetchNext = async (): Promise<void> => {
        while (cursor < pagesToPrefetch.length) {
          if (!isPrefetchGenerationCurrent(projectId, generation) || controller.signal.aborted) return

          const page = pagesToPrefetch[cursor]
          cursor += 1
          if (!page) continue

          const pageXmlId = await resolvePageXmlIdForPrefetch(projectId, page, controller.signal)
          if (!pageXmlId) continue

          if (!isPrefetchGenerationCurrent(projectId, generation) || controller.signal.aborted) return

          const context = normalizeAnnotationContext(page.annotationContext, projectId, page.id)
          const cacheKey = buildAnnotationCacheKey(projectId, page.id, context, pageXmlId)
          if (annotationCache.has(cacheKey) || pendingPrefetches.value.has(cacheKey)) {
            continue
          }

          pendingPrefetches.value.add(cacheKey)
          try {
            const pageDto = await $fetch<PageDto>(
              annotationResourcePath(context, pageXmlId),
              { signal: controller.signal }
            )
            annotationCache.set(cacheKey, pageDto)
            log.info(`Prefetched annotations for page ${page.id}`)
          } catch (error) {
            if (!isAbortError(error)) {
              log.warn(`Failed to prefetch annotations for page ${page.id}:`, error)
            }
          } finally {
            pendingPrefetches.value.delete(cacheKey)
          }
        }
      }

      await Promise.all(Array.from({ length: workerCount }, () => prefetchNext()))
    } finally {
      const batch = prefetchBatchByProjectId.get(projectId)
      if (batch?.generation === generation) {
        prefetchBatchByProjectId.delete(projectId)
      }
    }
  }

  /**
   * Clear the annotation cache (e.g., when project changes or on explicit refresh)
   */
  function clearAnnotationCache(): void {
    for (const timeout of prefetchTimeoutByProjectId.values()) {
      clearTimeout(timeout)
    }
    prefetchTimeoutByProjectId.clear()

    for (const batch of prefetchBatchByProjectId.values()) {
      batch.controller.abort()
    }
    prefetchBatchByProjectId.clear()
    prefetchGenerationByProjectId.clear()

    annotationCache.clear()
    pendingPrefetches.value.clear()
    pageXmlPrefetchCache.clear()
    pageXmlLookupByKey.clear()
    log.info('Annotation cache cleared')
  }

  /**
   * Invalidate a specific page's annotations from the cache
   */
  function invalidateAnnotationCache(pageId: string, projectId?: string): void {
    const prefix = projectId ? `${projectId}:${pageId}:` : `:${pageId}:`
    const deleted = annotationCache.deleteWhere((key) => {
      if (projectId) return key.startsWith(prefix)
      return key.includes(prefix)
    })
    if (deleted > 0) {
      log.info(`Invalidated ${deleted} annotation cache entries for page ${pageId}`)
    }
  }

  /**
   * Save annotations for a specific canvas back to the backend PAGE XML file.
   * Returns true if save was successful, false otherwise.
   */
  async function saveAnnotations(canvasId?: string): Promise<boolean> {
    const targetCanvasId = canvasId ?? activeCanvasId.value
    if (!targetCanvasId) {
      log.warn('No canvas ID provided for saving annotations')
      return false
    }

    const canvas = canvases.value[targetCanvasId]
    if (!canvas) {
      log.warn(`Canvas not found: ${targetCanvasId}`)
      return false
    }

    if (canvas.comparison?.readOnly) {
      log.warn(`Canvas ${targetCanvasId} is a read-only comparison canvas and cannot be saved`)
      return false
    }

    const { pageId } = canvas
    if (!pageId) {
      log.warn('No page ID available for saving')
      return false
    }

    const projectId = canvas.projectId ?? null
    if (!projectId) {
      log.warn('No project ID available for saving annotations')
      return false
    }
    const context = getAnnotationContextForCanvas(canvas)
    if (!context) {
      log.warn('No annotation context available for saving annotations')
      return false
    }

    const session = getEditorSession(targetCanvasId)
    const pcGts = session?.document.value
    if (!pcGts) {
      log.warn('No document found in session for saving')
      return false
    }

    canvas.isSavingAnnotations = true

    try {
      const pageDto = convertPcGtsToPageDto(pcGts)
      let xmlFileId = canvas.xmlFileId

      if (!xmlFileId) {
        if (!context.createAllowed) {
          log.warn(`Initial XML creation is disabled for page ${pageId} in context ${context.mode}`)
          return false
        }
        log.info(`Creating initial PAGE XML for page ${pageId}`)
        const created = await $fetch<{ xmlId: string, fileName?: string, schema?: string, schemaVersion?: string }>(
          context.basePath,
          {
            method: 'POST',
            body: pageDto
          }
        )
        xmlFileId = created.xmlId
        canvas.xmlFileId = xmlFileId
        pageXmlPrefetchCache.set(`${projectId}:${pageId}`, xmlFileId)

        const page = documentStore.getPage(pageId, projectId)
        if (page) {
          const hasPageXml = page.xmlFiles.some(xml => xml.id === xmlFileId)
          if (!hasPageXml) {
            page.xmlFiles = [
              ...page.xmlFiles,
              {
                id: xmlFileId,
                fileName: created.fileName || `${page.label}.xml`,
                schema: 'PAGE_XML',
                schemaVersion: created.schemaVersion || undefined,
                variant: 'original'
              }
            ]
          }
          page.xmlFileCount = page.xmlFiles.length
        }
      } else {
        log.info(`Saving annotations for page ${pageId} to XML file ${xmlFileId}`)
        await $fetch(annotationResourcePath(context, xmlFileId), {
          method: 'PUT',
          body: pageDto
        })
      }

      log.info(`Successfully saved annotations for page ${pageId}`)

      invalidateAnnotationCache(pageId, projectId)

      const cacheKey = buildAnnotationCacheKey(projectId, pageId, context, xmlFileId)
      annotationCache.set(cacheKey, pageDto)
      resetCanvasHistoryBaseline(targetCanvasId)

      if (import.meta.client) {
        const collaboration = useEditorCollaboration()
        await collaboration.acceptCurrentRevisionForCanvas(targetCanvasId)
      }

      return true
    } catch (error) {
      log.error(`Failed to save annotations for page ${pageId}:`, error)
      return false
    } finally {
      canvas.isSavingAnnotations = false
    }
  }

  async function loadPage(pageId: string, variantId?: string, projectId?: string): Promise<string | null> {
    const canvasId = activeCanvasId.value
    if (!canvasId) return null
    const targetProjectId = projectId ?? canvases.value[canvasId]?.projectId ?? sessionStore.activeProjectId
    if (!targetProjectId) return null
    return loadPageIntoCanvas(canvasId, targetProjectId, pageId, variantId)
  }

  function switchImageVariantForCanvas(canvasId: string, variantId: string): string | null {
    const canvas = canvases.value[canvasId]
    if (!canvas) return null

    const pageId = canvas.pageId ?? null
    if (!pageId) return null

    const projectId = canvas.projectId ?? undefined
    const page = documentStore.getPage(pageId, projectId)
    if (!page) return null

    const variant = page.imageVariants.find(v => v.id === variantId)
    if (!variant) {
      log.error(`Image variant ${variantId} not found`)
      return null
    }

    canvas.imageVariantId = variantId
    canvas.imageSrc = variant.url
    const session = getEditorSession(canvasId)
    if (session?.document.value?.page) {
      session.document.value.page.imageFilename = resolveMetadataImageFilename(variant)
      triggerRef(session.document)
    }

    if (activeCanvasId.value === canvasId) {
      currentImageVariantId.value = variantId
    }

    return variant.url
  }

  function switchImageVariant(variantId: string): string | null {
    const canvasId = activeCanvasId.value
    if (!canvasId) return null
    return switchImageVariantForCanvas(canvasId, variantId)
  }

  return {
    canvases,
    activeCanvasId,
    currentPageId,
    currentProjectId,
    currentImageVariantId,

    uiMode,
    uiModeScope,
    uiModeByCanvasId,
    toolbarLayout,
    globalSettings,
    pages,
    allPages,
    preferredImageVariantKey,
    selectedVariantIdByPageId,
    labelSet,
    projectCodecId,
    projectCodecCharacters,
    projectDictionaryId,
    projectDictionaryForms,
    projectDictionaryCaseSensitive,
    projectDictionaryUnicodeNormalization,
    projectDictionaryCanEdit,
    projectDictionaryLocked,
    projectVirtualKeyboardId,
    projectToolkitSettings,
    projectTextDefaultGtIndex,
    projectTextDefaultRecognitionIndices,

    activeCanvas,
    allCanvases,
    activeRegions,
    regionsByCanvasId,
    activeBaselines,
    baselinesByCanvasId,
    activeSelectedRegionId,
    activeSelectedBaselineId,
    selectedRegion,
    selectedBaseline,
    activeDrawingMode,
    isCanvasActive,
    currentPage,
    currentImageVariant,
    unsavedCanvasEntries,

    effectiveUiMode,
    getDisplayedVariantForPage,
    getPreviewUrlForPage,
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
    clearProjectTextIndexDefaults,

    setUiMode,
    setUiModeScope,
    setPreferredImageVariantKey,
    setSelectedVariantOverride,
    registerCanvas,
    unregisterCanvas,
    resetEditorState,
    updateCanvasHistoryState,
    resetCanvasHistoryBaseline,
    hasUnsavedChangesForPage,
    setActiveCanvas,
    setCanvasDocument,
    getCanvas,
    addRegion,
    updateRegion,
    removeRegion,
    selectRegionById,
    clearRegionSelection,
    clearCanvasSelection,
    addBaseline,
    updateBaseline,
    removeBaseline,
    selectBaselineById,
    clearBaselineSelection,
    setDrawingMode,
    setRegionType,
    setHoveredRegionId,
    setHoveredBaselineId,
    setHoveredNodeIndex,
    setImageSize,
    setDocument,
    clearAllAnnotations,
    loadPage,
    loadPageIntoCanvas,
    switchImageVariant,
    switchImageVariantForCanvas,
    updateGlobalSettings,
    toggleConstrainToImage,
    toggleConstrainToParent,
    toggleAutoSelect,
    setToolbarLayout,
    setAdjacentPrefetchPageScope,

    clearAnnotationCache,
    invalidateAnnotationCache,

    saveAnnotations
  }
})
