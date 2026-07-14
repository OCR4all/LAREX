import { setCursor } from '@/utils/editor/cursor-manager'
import { computeAutoParentPreview } from '@/utils/editor/auto-parent-utils'
import type { View, AspectRatioScale, Point } from '@/models/editor'
import type {
  MouseInteraction,
  PolygonDrawing,
  PolylineDrawing,
  RectangleDrawing,
  PolygonEditing,
  PolylineEditing,
  CutDrawing
} from './editor-interactions/types'
import type { ActionProcessingRenderTarget, WebGLRenderState, ViewMode, RelationRenderData } from '@/types/editor/rendering'
import type { ReadingOrderRenderData } from '@/webgl/editor/reading-order-renderer'
import type { RenderStats } from './use-render-queue'
import { useEditorCustomCursor } from './use-editor-custom-cursor'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'

export type TriangulateFunction = (points: Point[]) => number[]

export interface WebGLRenderer {
  renderFrame: (renderState: WebGLRenderState, aspectRatioScale: Ref<AspectRatioScale>, view: View, triangulatePolygon: TriangulateFunction) => void
  initGL: (triangulatePolygon: TriangulateFunction) => void
  loadAndRender: (src: string) => Promise<void>
  cleanup: () => void
  gl: () => WebGL2RenderingContext | null
  imageSize: Ref<{ width: number, height: number }>
  invalidateGeometry: (id: string) => void
  invalidateMultipleGeometry: (ids: string[]) => void
  clearGeometryCache: () => void
  pruneGeometryCache: (activePolygonIds: Set<string>) => void
  getGeometryCacheStats: () => unknown
  startReadingOrderAnimation: () => void
  stopReadingOrderAnimation: () => void
}

export interface UseEditorRendererReturn {
  render: () => void
  setupRenderWatches: () => void
  setupCursorWatches: () => void
  setupReadingOrderAnimationWatch: () => void
  startReadingOrderAnimation: () => void
  stopReadingOrderAnimation: () => void
  renderStats: Ref<RenderStats | null>
}

/**
 * Composable for managing editor rendering and cursor state.
 * Handles WebGL rendering, watches for state changes, and cursor updates.
 */
export function useEditorRenderer(
  canvas: Ref<HTMLCanvasElement | null>,
  polygons: WebGLRenderState['polygons'],
  polylines: WebGLRenderState['polylines'],
  selectedPolygonIndex: Ref<number>,
  selectedPolylineIndex: Ref<number>,
  selectedPolygonIds: Ref<string[]>,
  selectedPolylineIds: Ref<string[]>,
  hiddenPolygonIds: Ref<string[]>,
  hiddenPolylineIds: Ref<string[]>,
  hoveredPolygonId: Ref<string | null>,
  isPolygonMode: Ref<boolean>,
  isRectangleMode: Ref<boolean>,
  isPolylineMode: Ref<boolean>,
  isMoveMode: Ref<boolean>,
  canvasDimensions: Ref<{ width: number, height: number }>,
  webglRenderer: WebGLRenderer,
  aspectRatioScale: Ref<AspectRatioScale>,
  view: View,
  mouseInteraction: MouseInteraction,
  polygonDrawing: PolygonDrawing,
  polylineDrawing: PolylineDrawing,
  rectangleDrawing: RectangleDrawing,
  polygonEditing: PolygonEditing,
  polylineEditing: PolylineEditing,
  triangulatePolygon: TriangulateFunction,
  viewMode: Ref<string>,
  regionType: Ref<string>,
  enabled?: Ref<boolean>,
  readingOrderData?: Ref<ReadingOrderRenderData | undefined>,
  showReadingOrderOverlay?: Ref<boolean>,
  relationData?: Ref<RelationRenderData | undefined>,
  showRelationsOverlay?: Ref<boolean>,
  cutDrawing?: CutDrawing,
  isCutMode?: Ref<boolean>,
  isCutLineMode?: Ref<boolean>,
  isCutPolygonMode?: Ref<boolean>,
  isCutRectangleMode?: Ref<boolean>,
  moveInteraction?: { isMoving: () => boolean, state: { isInvalid: boolean, elementId: string | null } },
  bufferPreview?: Ref<{ polygonId: string, points: Point[] } | null>,
  actionProcessingTargets?: Ref<ActionProcessingRenderTarget | null>,
  diffHighlights?: Ref<WebGLRenderState['diffHighlights'] | undefined>,
  labelConflictIds?: Ref<string[]>
): UseEditorRendererReturn {
  const editorUiStore = useEditorUiStore()
  const { activeCursor: activeCustomCursor } = useEditorCustomCursor(computed(() => ({
    actionWandActive: editorUiStore.actionWandActive
  })))
  const renderStats = ref<RenderStats | null>(null)

  const isInvalidPosition = computed(() => (
    polygonEditing.isInvalidPosition.value
    || polylineEditing.isInvalidPosition.value
    || polylineDrawing.isInvalidPosition.value
    || polygonDrawing.isInvalidPosition.value
    || rectangleDrawing.isInvalidPosition.value
    || (cutDrawing?.isInvalidPosition?.value ?? false)
    || (moveInteraction?.state.isInvalid ?? false)
  ))

  function toPointOrNull(preview: { x: number | null, y: number | null }): Point | null {
    if (preview.x === null || preview.y === null) return null
    return { x: preview.x, y: preview.y }
  }

  function normalizeViewMode(raw: string | undefined): ViewMode | undefined {
    if (raw === 'default' || raw === 'textline' || raw === 'baseline') return raw
    return undefined
  }

  /**
   * Render the current editor state to WebGL
   */
  function render(): void {
    if (enabled && !enabled.value) return

    const autoParentPreview = computeAutoParentPreview(
      viewMode.value,
      polygonDrawing.currentPolygonPoints.length > 0,
      polylineDrawing.currentPolylinePoints.length > 0,
      polygonDrawing.currentPolygonPoints,
      polylineDrawing.currentPolylinePoints,
      polygons,
      polylines,
      regionType.value
    )

    const renderState: WebGLRenderState = {
      polygons,
      polylines,
      labelConflictIds: labelConflictIds?.value ?? [],
      hoveredPolygonIndex: polygonEditing.hoveredPolygonIndex,
      selectedPolygonIndex,
      hoveredPolylineIndex: polylineEditing.hoveredPolylineIndex,
      selectedPolylineIndex: polylineEditing.selectedPolylineIndex,
      selectedPolygonIds,
      selectedPolylineIds,
      hiddenPolygonIds,
      hiddenPolylineIds,
      currentPolygonPoints: polygonDrawing.currentPolygonPoints,
      currentPolylinePoints: polylineDrawing.currentPolylinePoints,
      previewPoint: polygonDrawing.previewPoint,
      polylinePreviewPoint: polylineDrawing.previewPoint,
      rectanglePreviewPoints: rectangleDrawing.previewPoints,
      hoveredNodeIndex: polygonEditing.hoveredNodeIndex,
      previewNodePosition: toPointOrNull(polygonEditing.previewNodePosition),
      draggedNodeInfo: polygonEditing.draggedNodeInfo,
      polylineDraggedNodeInfo: polylineEditing.draggedNodeInfo,
      isInvalidPosition,
      hoveredPolylineNodeIndex: polylineEditing.hoveredNodeIndex,
      polylinePreviewNodePosition: toPointOrNull(polylineEditing.previewNodePosition),
      viewMode: normalizeViewMode(viewMode.value),
      autoParentPreview,
      readingOrderData: readingOrderData?.value,
      showReadingOrderOverlay: showReadingOrderOverlay?.value ?? false,
      relationData: relationData?.value,
      showRelationsOverlay: showRelationsOverlay?.value ?? false,
      cutLinePoints: isCutLineMode?.value && cutDrawing?.currentPoints?.length
        ? [...cutDrawing.currentPoints]
        : undefined,
      cutPolygonPoints: isCutPolygonMode?.value && cutDrawing?.currentPoints?.length
        ? [...cutDrawing.currentPoints]
        : undefined,
      cutRectanglePoints: isCutRectangleMode?.value && cutDrawing?.rectPreviewPoints?.length
        ? [...cutDrawing.rectPreviewPoints]
        : undefined,
      cutPreviewPoint: isCutMode?.value ? cutDrawing?.previewPoint : undefined,
      cutMode: isCutLineMode?.value ? 'line' : isCutPolygonMode?.value ? 'polygon' : isCutRectangleMode?.value ? 'rectangle' : undefined,
      isCutDrawingActive: isCutMode?.value && (cutDrawing?.isActive() ?? false),
      moveState: moveInteraction
        ? {
            isMoving: moveInteraction.isMoving(),
            elementId: moveInteraction.state.elementId,
            isInvalid: moveInteraction.state.isInvalid
          }
        : undefined,
      bufferPreview: bufferPreview?.value ?? undefined,
      confidenceHeatmap: editorUiStore.confidenceHeatmap,
      actionProcessingTargets: actionProcessingTargets?.value ?? null,
      diffHighlights: diffHighlights?.value
    }

    webglRenderer.renderFrame(renderState, aspectRatioScale, view, triangulatePolygon)
  }

  /**
   * Setup all watches for automatic re-rendering
   */
  function setupRenderWatches(): void {
    watch([
      polygons,
      polylines,
      () => polygonEditing.hoveredPolygonIndex,
      selectedPolygonIndex,
      selectedPolygonIds,
      selectedPolylineIds,
      hiddenPolygonIds,
      hiddenPolylineIds,
      () => polylineEditing.hoveredPolylineIndex,
      () => polylineEditing.selectedPolylineIndex,
      polygonDrawing.currentPolygonPoints,
      polylineDrawing.currentPolylinePoints,
      polygonDrawing.previewPoint,
      polylineDrawing.previewPoint,
      () => rectangleDrawing.previewPoints,
      () => polygonEditing.hoveredNodeIndex,
      () => polylineEditing.hoveredNodeIndex,
      () => polygonEditing.hoveredEdgeInfo,
      () => polygonEditing.previewNodePosition,
      () => polygonEditing.draggedNodeInfo,
      () => polylineEditing.draggedNodeInfo,
      () => polygonEditing.isInvalidPosition,
      () => polylineEditing.isInvalidPosition,
      isPolygonMode,
      isRectangleMode,
      isPolylineMode,
      isCutMode,
      () => cutDrawing?.currentPoints,
      () => cutDrawing?.previewPoint,
      () => cutDrawing?.rectPreviewPoints,
      () => cutDrawing?.isRectDrawing,
      () => cutDrawing?.isInvalidPosition,
      () => moveInteraction?.isMoving(),
      () => moveInteraction?.state.isInvalid,
      bufferPreview,
      actionProcessingTargets,
      labelConflictIds,
      view,
      aspectRatioScale,
      canvasDimensions,
      () => mouseInteraction.actionState.action,
      viewMode,
      readingOrderData,
      showReadingOrderOverlay,
      relationData,
      showRelationsOverlay,
      () => editorUiStore.readingOrderVersion,
      () => editorUiStore.confidenceHeatmap
    ],
    () => {
      if (enabled && !enabled.value) return
      nextTick(() => render())
    },
    { deep: true })

    watch(() => polygonEditing.hoveredPolygonIndex.value, (newIndex) => {
      if (newIndex >= 0 && polygons[newIndex]) {
        hoveredPolygonId.value = polygons[newIndex]!.id
      } else {
        hoveredPolygonId.value = null
      }
    })

    watch(() => webglRenderer.imageSize, () => {
      if (enabled && !enabled.value) return
      nextTick(() => render())
    }, { deep: true })
  }

  /**
   * Setup cursor state watches
   */
  function setupCursorWatches(): void {
    watch([
      isPolygonMode,
      isRectangleMode,
      isPolylineMode,
      isMoveMode,
      selectedPolygonIndex,
      selectedPolylineIndex,
      () => polygonEditing.hoveredPolygonIndex.value,
      () => polygonEditing.hoveredNodeIndex.value,
      () => polygonEditing.hoveredEdgeInfo.polygonIndex,
      () => polylineEditing.hoveredPolylineIndex.value,
      () => polylineEditing.hoveredNodeIndex.value,
      () => polylineEditing.hoveredSegmentInfo.polylineIndex,
      () => mouseInteraction.panState.isDragging,
      () => polygonEditing.draggedNodeInfo.nodeIndex,
      () => polylineEditing.draggedNodeInfo.nodeIndex,
      () => polygonEditing.isInvalidPosition.value,
      () => polylineEditing.isInvalidPosition.value,
      () => polygonDrawing.isInvalidPosition.value,
      () => polylineDrawing.isInvalidPosition.value,
      () => rectangleDrawing.isInvalidPosition.value,
      () => cutDrawing?.isInvalidPosition?.value,
      isCutMode,
      () => mouseInteraction.actionState.action,
      () => moveInteraction?.isMoving(),
      () => editorUiStore.actionWandActive,
      activeCustomCursor
    ],
    () => {
      if (enabled && !enabled.value) return
      if (activeCustomCursor.value) {
        setCursor(canvas.value, { customCursor: activeCustomCursor.value })
        return
      }
      const isCreateMode = isPolygonMode.value || isRectangleMode.value || isPolylineMode.value || isCutMode?.value

      setCursor(canvas.value, {
        isValidPosition: (
          !polygonEditing.isInvalidPosition.value
          && !polylineEditing.isInvalidPosition.value
          && !polygonDrawing.isInvalidPosition.value
          && !polylineDrawing.isInvalidPosition.value
          && !rectangleDrawing.isInvalidPosition.value
          && !(cutDrawing?.isInvalidPosition?.value)
        ),

        isDraggingNode: polygonEditing.draggedNodeInfo.nodeIndex >= 0,
        isDraggingPolylineNode: polylineEditing.draggedNodeInfo.nodeIndex >= 0,
        isPanning: mouseInteraction.panState.isDragging,
        isMovingElement: moveInteraction?.isMoving() ?? false,

        isHoveringPolygonNode: polygonEditing.hoveredNodeIndex.value >= 0,
        isHoveringPolygonEdge: polygonEditing.hoveredEdgeInfo.polygonIndex >= 0,
        isHoveringPolygon: polygonEditing.hoveredPolygonIndex.value >= 0,

        isHoveringPolylineNode: polylineEditing.hoveredNodeIndex.value >= 0,
        isHoveringPolylineSegment: polylineEditing.hoveredSegmentInfo.polylineIndex >= 0,
        isHoveringPolyline: polylineEditing.hoveredPolylineIndex.value >= 0,

        hasSelectedPolygon: selectedPolygonIndex.value >= 0,
        hasSelectedPolyline: selectedPolylineIndex.value >= 0,

        interactionMode: isMoveMode.value ? 'move' : isCreateMode ? 'create' : 'select'
      })
    },
    { immediate: true, deep: true })
  }

  let animationFrameId: number | null = null

  /**
   * Start the reading order arrow animation loop
   */
  function startReadingOrderAnimation(): void {
    if (animationFrameId !== null) return

    webglRenderer.startReadingOrderAnimation()

    const animate = () => {
      if (enabled && !enabled.value) {
        stopReadingOrderAnimation()
        return
      }
      render()
      animationFrameId = requestAnimationFrame(animate)
    }

    animationFrameId = requestAnimationFrame(animate)
  }

  /**
   * Stop the reading order arrow animation loop
   */
  function stopReadingOrderAnimation(): void {
    if (animationFrameId !== null) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }
    webglRenderer.stopReadingOrderAnimation()
  }

  /**
   * Setup reading order animation watch
   */
  function setupReadingOrderAnimationWatch(): void {
    if (!showReadingOrderOverlay && !showRelationsOverlay) return

    const hasAnimatedOverlay = computed(() => {
      const readingOrderVisible = showReadingOrderOverlay?.value ?? false
      const relationsVisible = showRelationsOverlay?.value ?? false
      return readingOrderVisible || relationsVisible
    })

    watch(hasAnimatedOverlay, (visible) => {
      if (visible) {
        startReadingOrderAnimation()
      } else {
        stopReadingOrderAnimation()
      }
    }, { immediate: true })

    onBeforeUnmount(() => {
      stopReadingOrderAnimation()
    })
  }

  return {
    render,
    setupRenderWatches,
    setupCursorWatches,
    setupReadingOrderAnimationWatch,
    startReadingOrderAnimation,
    stopReadingOrderAnimation,
    renderStats
  }
}
