import type {
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
import { loadSinglePageData, type PageResponse } from '@/services/editor/project-loader'

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

function createEmptyPcGts(params: { imageFilename: string, imageWidth: number, imageHeight: number, pcGtsId?: string }): PcGts {
  const now = nowIso()
  const metadata = new Metadata({ creator: 'umbra', created: now, lastChange: now })
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
    getPage,
    isPageLoaded,
    setLabelSet,
    setLabelSetFromApi,
    clearLabelSet,
    setProjectCodec,
    clearProjectCodec,
    setProjectTextIndexDefaults,
    clearProjectTextIndexDefaults
  } = documentStore

  const canvases = ref<Record<string, CanvasState>>({})
  const activeCanvasId = ref<string | null>(null)

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
    return Object.values(canvases.value).some(canvas => {
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
      pcGtsId: session.document.value?.pcGtsId
    })
    session.document.value = pcGts

    canvas.selectedRegionId = null
    canvas.selectedBaselineId = null
    session.spatialIndex.clear()
    visibilityService.clearCache()
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
        const enrichedData = await loadSinglePageData(projectId, { id: page.id, name: page.label } as PageResponse)
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

    if (activeCanvasId.value === canvasId) {
      currentPageId.value = pageId
      currentImageVariantId.value = variant.id
      sessionStore.setActiveProject(projectId)
    }

    const emptyPcGts = createEmptyPcGts({
      imageFilename: metadataImageFilename,
      imageWidth: canvas.imageSize?.width ?? 1000,
      imageHeight: canvas.imageSize?.height ?? 1000,
      pcGtsId: `pcgts-${pageId}`
    })
    setCanvasDocument(canvasId, emptyPcGts)

    canvas.selectedRegionId = null
    canvas.selectedBaselineId = null

    if (page.xmlFiles && page.xmlFiles.length > 0) {
      loadAnnotationsForCanvas(canvasId, projectId, pageId, page.xmlFiles, metadataImageFilename)
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
    imageFilename?: string
  ): Promise<void> {
    const pageXmlFile = xmlFiles.find(xml => xml.schema === 'PAGE_XML')
    if (!pageXmlFile) {
      log.info(`No PAGE XML file found for page ${pageId}, using empty document`)
      return
    }

    const cacheKey = `${projectId}:${pageId}:${pageXmlFile.id}`
    const canvas = canvases.value[canvasId]

    if (canvas) {
      canvas.isLoadingAnnotations = true
    }

    try {
      let pageDto: PageDto
      
      if (annotationCache.has(cacheKey)) {
        log.info(`Using cached annotations for page ${pageId}`)
        pageDto = annotationCache.get(cacheKey)!
      } else {
        log.info(`Fetching annotations from ${pageXmlFile.fileName} (id: ${pageXmlFile.id}) for page ${pageId}`)
        
        pageDto = await $fetch<PageDto>(
          `/api/projects/${projectId}/pages/${pageId}/annotations/${pageXmlFile.id}`
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
      
      prefetchAdjacentAnnotations(projectId, pageId)
    } catch (error) {
      log.error(`Failed to load annotations for page ${pageId}:`, error)
    } finally {
      if (canvas) {
        canvas.isLoadingAnnotations = false
      }
    }
  }

  /**
   * Prefetch annotations for adjacent pages with bidirectional priority.
   * Prefetches next 5 pages and previous 5 pages around the current page.
   * Enriches skeleton pages on-the-fly if they haven't been loaded yet.
   * This improves perceived performance when navigating through pages.
   */
  async function prefetchAdjacentAnnotations(projectId: string, currentPageId: string): Promise<void> {
    const allPagesList = getProjectPages(projectId)
    const currentIndex = allPagesList.findIndex(p => p.id === currentPageId)
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
    ].filter(i => i >= 0 && i < allPagesList.length)

    for (const idx of indicesToPrefetch) {
      let page = allPagesList[idx]
      if (!page) continue

      if (!documentStore.isPageLoaded(page.id, projectId)) {
        try {
          const enrichedData = await loadSinglePageData(projectId, { id: page.id, name: page.label } as PageResponse)
          documentStore.enrichPage(page.id, { ...enrichedData, projectId, projectName: page.projectName }, projectId)
          page = documentStore.getPage(page.id, projectId)!
        } catch (err) {
          log.warn(`Failed to enrich page ${page.id} during prefetch:`, err)
          continue
        }
      }

      if (!page.xmlFiles || page.xmlFiles.length === 0) continue

      const pageXmlFile = page.xmlFiles.find(xml => xml.schema === 'PAGE_XML')
      if (!pageXmlFile) continue

      const cacheKey = `${projectId}:${page.id}:${pageXmlFile.id}`
      
      if (annotationCache.has(cacheKey) || pendingPrefetches.value.has(cacheKey)) {
        continue
      }

      pendingPrefetches.value.add(cacheKey)

      $fetch<PageDto>(
        `/api/projects/${projectId}/pages/${page.id}/annotations/${pageXmlFile.id}`
      ).then(pageDto => {
        annotationCache.set(cacheKey, pageDto)
        log.info(`Prefetched annotations for page ${page.id}`)
      }).catch(error => {
        log.warn(`Failed to prefetch annotations for page ${page.id}:`, error)
      }).finally(() => {
        pendingPrefetches.value.delete(cacheKey)
      })
    }
  }

  /**
   * Clear the annotation cache (e.g., when project changes or on explicit refresh)
   */
  function clearAnnotationCache(): void {
    annotationCache.clear()
    pendingPrefetches.value.clear()
    log.info('Annotation cache cleared')
  }

  /**
   * Invalidate a specific page's annotations from the cache
   */
  function invalidateAnnotationCache(pageId: string, projectId?: string): void {
    const prefix = projectId ? `${projectId}:${pageId}:` : `:${pageId}:`
    const deleted = annotationCache.deleteWhere(key => {
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

    const { pageId, xmlFileId } = canvas
    if (!pageId || !xmlFileId) {
      log.warn('No page ID or XML file ID available for saving')
      return false
    }

    const projectId = canvas.projectId ?? null
    if (!projectId) {
      log.warn('No project ID available for saving annotations')
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
      
      log.info(`Saving annotations for page ${pageId} to XML file ${xmlFileId}`)

      await $fetch(`/api/projects/${projectId}/pages/${pageId}/annotations/${xmlFileId}`, {
        method: 'PUT',
        body: pageDto
      })

      log.info(`Successfully saved annotations for page ${pageId}`)

      invalidateAnnotationCache(pageId, projectId)

      const cacheKey = `${projectId}:${pageId}:${xmlFileId}`
      annotationCache.set(cacheKey, pageDto)
      resetCanvasHistoryBaseline(targetCanvasId)

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
    getPage,
    isPageLoaded,
    setLabelSet,
    setLabelSetFromApi,
    clearLabelSet,
    setProjectCodec,
    clearProjectCodec,
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
    
    clearAnnotationCache,
    invalidateAnnotationCache,
    
    saveAnnotations
  }
})
