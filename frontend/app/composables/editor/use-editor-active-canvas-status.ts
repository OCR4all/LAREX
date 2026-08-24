import { PolygonType } from '@/models/editor'
import type { Region, RegionKind } from '@/models/editor/region'
import { getEditorSession } from '@/session/editor/editor-session'
import { parseCanvasId } from '@/stores/editor/editor.keys'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import type { TreeItemData } from '@/components/editor/sidebar/structure-tree'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import type { EditorCanvasControls } from '@/types/editor/canvas-controls'
import type { LabelSet } from '@/models/editor/labels'
import { resolveRegionLabelDisplayName } from '@/utils/editor/page-label-mapping'
import { resolvePageLockReason } from '@/utils/page-lock'

type StatusEntityInfo = {
  regionType: RegionKind | 'TextLine' | 'Baseline' | 'Polyline'
  subtype?: string
  id: string
  label?: string
  width?: number
  height?: number
  childCount?: number
}

type StatusPageSummary = {
  pageId: string
  variantLabel?: string
  totalRegions: number
  textRegions: number
  imageRegions: number
  lineDrawings: number
  tableRegions: number
  otherRegions: number
}

type EditorActiveCanvasStatusOptions = {
  resolveCanvasAnnotationContext: (canvasId: string) => { mode?: 'PROJECT' | 'DATASET_LINK' | 'DATASET_COPY' | null } | null | undefined
}

export function useEditorActiveCanvasStatus(options: EditorActiveCanvasStatusOptions) {
  const editorStore = useEditorStore()
  const editorUiStore = useEditorUiStore()
  const sessionStore = useEditorSessionStore()
  const actionRunsStore = useActionRunsStore()
  const collaboration = useEditorCollaboration()

  const currentProjectId = computed(() => editorStore.currentProjectId ?? sessionStore.activeProjectId)
  const activeCanvasId = computed(() => editorStore.activeCanvasId)
  const isSavingActiveCanvas = computed(() => {
    const id = activeCanvasId.value
    if (!id) return false
    return editorStore.canvases[id]?.isSavingAnnotations === true
  })
  const activeUiMode = computed(() => editorStore.effectiveUiMode(activeCanvasId.value))
  const useFloatingCollapsedSidebars = computed(() =>
    activeUiMode.value !== 'text'
    || editorUiStore.textModeSubmode === 'visual'
    || editorUiStore.textModeSubmode === 'full'
  )
  const activeControls = computed<EditorCanvasControls | null>(() => {
    const id = activeCanvasId.value
    if (!id) return null
    return getEditorSession(id)?.controls.value ?? null
  })

  const activeSelectedPolygonId = computed(() => {
    const controls = activeControls.value
    const index = controls?.selectedPolygonIndex?.value ?? -1
    const polygons = controls?.polygons ?? []
    return index >= 0 ? polygons[index]?.id ?? null : null
  })

  const activeSelectedPolylineId = computed(() => {
    const controls = activeControls.value
    const index = controls?.selectedPolylineIndex?.value ?? -1
    const polylines = controls?.polylines ?? []
    return index >= 0 ? polylines[index]?.id ?? null : null
  })

  const activeHoveredPolygonId = computed(() => {
    const controls = activeControls.value
    return controls?.hoveredPolygonId?.value ?? null
  })

  const activeHoveredPolylineId = computed(() => {
    const controls = activeControls.value
    return controls?.hoveredPolylineId?.value ?? null
  })

  const activePolygons = computed<RenderablePolygon[]>(() => (activeControls.value?.polygons ?? []) as RenderablePolygon[])
  const activePolylines = computed<RenderablePolyline[]>(() => (activeControls.value?.polylines ?? []) as RenderablePolyline[])
  const activePolygonsForSidebar = computed<TreeItemData[]>(() => activePolygons.value as unknown as TreeItemData[])
  const activePolylinesForSidebar = computed<TreeItemData[]>(() => activePolylines.value as unknown as TreeItemData[])

  const activeHoveredEntity = computed<StatusEntityInfo | null>(() => {
    const polygons = activePolygons.value
    const polylines = activePolylines.value

    if (activeHoveredPolylineId.value) {
      const polyline = polylines.find(item => item.id === activeHoveredPolylineId.value)
      return polyline ? buildEntityFromPolyline(polyline, polygons, polylines, editorStore.labelSet) : null
    }

    if (activeHoveredPolygonId.value) {
      const polygon = polygons.find(item => item.id === activeHoveredPolygonId.value)
      return polygon ? buildEntityFromPolygon(polygon, polygons, polylines, editorStore.labelSet) : null
    }

    return null
  })

  const activeSelectedEntity = computed<StatusEntityInfo | null>(() => {
    const polygons = activePolygons.value
    const polylines = activePolylines.value

    if (activeSelectedPolylineId.value) {
      const polyline = polylines.find(item => item.id === activeSelectedPolylineId.value)
      return polyline ? buildEntityFromPolyline(polyline, polygons, polylines, editorStore.labelSet) : null
    }

    if (activeSelectedPolygonId.value) {
      const polygon = polygons.find(item => item.id === activeSelectedPolygonId.value)
      return polygon ? buildEntityFromPolygon(polygon, polygons, polylines, editorStore.labelSet) : null
    }

    return null
  })

  const activePageId = computed(() => {
    const canvasId = activeCanvasId.value
    const canvasPageId = canvasId ? (editorStore.canvases[canvasId]?.pageId ?? null) : null
    if (canvasPageId) return canvasPageId

    const controlsPageId = activeControls.value?.pageId?.value ?? null
    if (!controlsPageId) return null
    const parsedCanvas = parseCanvasId(controlsPageId)
    return parsedCanvas?.pageId ?? controlsPageId
  })

  const activeDocument = computed(() => {
    const canvasId = activeCanvasId.value
    if (!canvasId) return null
    return getEditorSession(canvasId)?.document.value ?? null
  })

  const activePage = computed(() => {
    return activeDocument.value?.page ?? null
  })

  const activePageSummary = computed<StatusPageSummary | null>(() => {
    const page = activePage.value
    if (!page) return null

    const hasSyncedPolygons = Array.isArray(activeControls.value?.polygons)
    const counts = hasSyncedPolygons
      ? collectRegionCountsFromPolygons(activePolygons.value)
      : collectRegionCounts(page.regions)
    const pageId = activePageId.value ?? null
    const activeProjectId = currentProjectId.value ?? undefined
    const pageData = pageId ? editorStore.getPage(pageId, activeProjectId) : undefined
    const pageLabel = pageData?.label || pageId || page.imageFilename || '-'
    const variantLabel = pageData ? editorStore.getDisplayedVariantForPage(pageData)?.label : undefined

    return {
      pageId: pageLabel,
      variantLabel,
      totalRegions: counts.totalRegions,
      textRegions: counts.textRegions,
      imageRegions: counts.imageRegions,
      lineDrawings: counts.lineDrawings,
      tableRegions: counts.tableRegions,
      otherRegions: counts.otherRegions
    }
  })

  const activeCollaborators = computed(() => {
    const canvasId = activeCanvasId.value
    if (!canvasId) return []
    return collaboration.getCanvasCollaborators(canvasId)
  })

  const activeCanvasEditor = computed(() => {
    const canvasId = activeCanvasId.value
    if (!canvasId) return null
    return collaboration.getCanvasEditor(canvasId)
  })

  const activePendingTakeover = computed(() => {
    const canvasId = activeCanvasId.value
    if (!canvasId) return null
    return collaboration.getCanvasPendingTakeover(canvasId)
  })

  const activeCanvasCanEdit = computed(() => {
    const canvasId = activeCanvasId.value
    if (!canvasId) return true
    return collaboration.canEditCanvas(canvasId)
  })

  const activePageLockReason = computed(() => {
    return activeControls.value?.pageLockReason?.value ?? null
  })

  const activeAnnotationMode = computed<'PROJECT' | 'DATASET_LINK' | 'DATASET_COPY' | null>(() => {
    const canvasId = activeCanvasId.value
    if (!canvasId) return null
    const annotationContext = options.resolveCanvasAnnotationContext(canvasId)
    return annotationContext?.mode ?? null
  })

  const canOpenActiveCanvasXmlEditor = computed(() => {
    const canvasId = activeCanvasId.value
    if (!canvasId) return false
    const canvas = editorStore.canvases[canvasId]
    return Boolean(canvas?.projectId && canvas.pageId && canvas.xmlFileId)
  })

  const activeSelectedPolygonIds = computed(() => activeControls.value?.selectedPolygonIds?.value ?? [])
  const activeSelectedPolylineIds = computed(() => activeControls.value?.selectedPolylineIds?.value ?? [])
  const activeHiddenPolygonIds = computed(() => activeControls.value?.hiddenPolygonIds?.value ?? [])
  const activeHiddenPolylineIds = computed(() => activeControls.value?.hiddenPolylineIds?.value ?? [])

  const isActivePageLocked = computed(() => {
    const pageId = activePageId.value
    const projectId = currentProjectId.value
    if (!pageId) return false
    const actionLockReason = actionRunsStore.getPageActionLockReason(projectId, pageId)
    const page = editorStore.getPage(pageId, projectId ?? undefined)
    return resolvePageLockReason(page, actionLockReason) !== null
  })

  return {
    activeCanvasId,
    isSavingActiveCanvas,
    activeUiMode,
    useFloatingCollapsedSidebars,
    activeControls,
    activeSelectedPolygonId,
    activeSelectedPolylineId,
    activeHoveredPolygonId,
    activeHoveredPolylineId,
    activePolygons,
    activePolylines,
    activePolygonsForSidebar,
    activePolylinesForSidebar,
    activeHoveredEntity,
    activeSelectedEntity,
    activePageSummary,
    activeCollaborators,
    activeCanvasEditor,
    activePendingTakeover,
    activeCanvasCanEdit,
    activePageLockReason,
    activeAnnotationMode,
    canOpenActiveCanvasXmlEditor,
    activeSelectedPolygonIds,
    activeSelectedPolylineIds,
    activeHiddenPolygonIds,
    activeHiddenPolylineIds,
    activePageId,
    activeDocument,
    activePage,
    isActivePageLocked
  }
}

function getBounds(points?: { x: number, y: number }[]): { width: number, height: number } | null {
  if (!points || points.length === 0) return null

  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity

  for (const point of points) {
    if (point.x < minX) minX = point.x
    if (point.y < minY) minY = point.y
    if (point.x > maxX) maxX = point.x
    if (point.y > maxY) maxY = point.y
  }

  return {
    width: Math.max(0, Math.round(maxX - minX)),
    height: Math.max(0, Math.round(maxY - minY))
  }
}

function getChildCount(id: string, polygons: RenderablePolygon[], polylines: RenderablePolyline[]): number {
  let count = 0
  for (const polygon of polygons) {
    if (polygon.parentId === id) count += 1
  }
  for (const polyline of polylines) {
    if (polyline.parentId === id) count += 1
  }
  return count
}

function buildEntityFromPolygon(
  polygon: RenderablePolygon,
  polygons: RenderablePolygon[],
  polylines: RenderablePolyline[],
  labelSet?: LabelSet | null
): StatusEntityInfo {
  const isTextLine = polygon.type === PolygonType.TEXTLINE || polygon.type === 'textline'
  const regionType = isTextLine ? 'TextLine' : (polygon.regionKind ?? 'CustomRegion')
  const subtype = !isTextLine ? polygon.regionSubtype : undefined

  const label = isTextLine
    ? polygon.label
    : resolveRegionLabelDisplayName(labelSet?.labels, polygon, polygon.label || polygon.regionSubtype)

  const bounds = getBounds(polygon.points)
  const childCount = getChildCount(polygon.id, polygons, polylines)

  return {
    regionType,
    subtype,
    id: polygon.id,
    label: label || undefined,
    width: bounds?.width,
    height: bounds?.height,
    childCount
  }
}

function buildEntityFromPolyline(
  polyline: RenderablePolyline,
  polygons: RenderablePolygon[],
  polylines: RenderablePolyline[],
  labelSet?: LabelSet | null
): StatusEntityInfo {
  const regionType = polyline.type === PolygonType.BASELINE || polyline.type === 'baseline' ? 'Baseline' : 'Polyline'
  let label = polyline.label

  if (!label || label === 'baseline') {
    if (polyline.parentId) {
      const parentPolygon = polygons.find(polygon => polygon.id === polyline.parentId)
      label = parentPolygon
        ? resolveRegionLabelDisplayName(labelSet?.labels, parentPolygon, parentPolygon.label || parentPolygon.regionSubtype || parentPolygon.id)
        : undefined
    }
  }

  const bounds = getBounds(polyline.points)
  const childCount = getChildCount(polyline.id, polygons, polylines)

  return {
    regionType,
    id: polyline.id,
    label: label || undefined,
    width: bounds?.width,
    height: bounds?.height,
    childCount
  }
}

function collectRegionCounts(regions: Region[] | undefined) {
  const counts = emptyRegionCounts()

  if (!regions) return counts

  const stack = [...regions]
  while (stack.length) {
    const region = stack.pop()
    if (!region) continue
    counts.totalRegions += 1

    incrementRegionKindCount(counts, region.kind)

    if (region.regions && region.regions.length) {
      stack.push(...region.regions)
    }
  }

  return counts
}

function collectRegionCountsFromPolygons(polygons: RenderablePolygon[] | undefined) {
  const counts = emptyRegionCounts()

  if (!polygons) return counts

  for (const polygon of polygons) {
    if (polygon.type !== PolygonType.REGION && polygon.type !== 'region') continue
    counts.totalRegions += 1
    incrementRegionKindCount(counts, polygon.regionKind)
  }

  return counts
}

function emptyRegionCounts() {
  return {
    totalRegions: 0,
    textRegions: 0,
    imageRegions: 0,
    lineDrawings: 0,
    tableRegions: 0,
    otherRegions: 0
  }
}

function incrementRegionKindCount(counts: ReturnType<typeof emptyRegionCounts>, kind: string | undefined) {
  switch (kind) {
    case 'TextRegion':
      counts.textRegions += 1
      break
    case 'ImageRegion':
      counts.imageRegions += 1
      break
    case 'LineDrawingRegion':
      counts.lineDrawings += 1
      break
    case 'TableRegion':
      counts.tableRegions += 1
      break
    default:
      counts.otherRegions += 1
      break
  }
}
