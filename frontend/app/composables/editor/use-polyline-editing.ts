import type { Commander } from '@/commands'
import { UpdatePolylineCommand } from '@/commands'
import type { Point, ImageSize, View, AspectRatioScale } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import { getEditorSession } from '@/session/editor/editor-session'
import type { SpatialIndexService } from '@/services/editor/spatial-index-service'
import { getDistanceToLineSegment, getHoverablePolylineAtPoint } from '@/utils/editor/hit-detection'
import { isPointWithinImageBounds } from '@/utils/editor/coordinates'
import { isPointWithinParentBounds } from '@/utils/editor/parent-constraint-utils'
import { HIT_DETECTION, TIMING } from '@/utils/editor/editor-constants'

export interface PolylineDraggedNodeInfo {
  polylineIndex: number
  nodeIndex: number
  isDragging: boolean
  originalPoint: Point | null
  originalPoints?: Point[]
  isNewlyInsertedNode?: boolean
}

export interface HoveredSegmentInfo {
  polylineIndex: number
  segmentIndex: number
  distance: number
  closestPoint: Point | null
}

export interface PolylinePreviewNodePosition {
  x: number | null
  y: number | null
}

/**
 * Composable for managing polyline editing functionality.
 * Handles node selection, dragging, and modification of polylines.
 */
export function usePolylineEditing(
  polylines: RenderablePolyline[],
  view: View,
  _aspectRatioScale: AspectRatioScale | Ref<AspectRatioScale>,
  constrainToImage: Ref<boolean> | undefined,
  imageSize: Ref<ImageSize> | undefined,
  polygons: RenderablePolygon[] | undefined,
  constrainToParent: Ref<boolean> | undefined,
  spatialIndex: SpatialIndexService | undefined,
  externalSelectedPolygonIndex: Ref<number> | undefined,
  externalSelectedPolylineIndex: Ref<number> | undefined,
  viewMode: Ref<string> | undefined,
  commander: Commander,
  canvasId: string,
  hiddenPolygonIds: Ref<string[]> | undefined,
  hiddenPolylineIds: Ref<string[]> | undefined
) {
  const getCommandContext = () => {
    const session = getEditorSession(canvasId)
    return session ? { session, canvasId } : undefined
  }

  const selectedPolylineIndex = externalSelectedPolylineIndex || ref(-1)
  const hoveredPolylineIndex = ref(-1)
  const hoveredNodeIndex = ref(-1)
  const draggedNodeInfo = reactive<PolylineDraggedNodeInfo>({
    polylineIndex: -1,
    nodeIndex: -1,
    isDragging: false,
    originalPoint: null
  })

  const hoveredSegmentInfo = reactive<HoveredSegmentInfo>({
    polylineIndex: -1,
    segmentIndex: -1,
    distance: Infinity,
    closestPoint: null
  })

  const previewNodePosition = reactive<PolylinePreviewNodePosition>({ x: null, y: null })

  const isInvalidPosition = ref(false)
  const justFinishedDragging = ref(false)

  /**
   * Update hover states based on mouse position.
   */
  function updateHoverStates(point: Point, selectedPolygonIndex: Ref<number>): void {
    const hiddenPolygonIdSet = hiddenPolygonIds ? new Set(hiddenPolygonIds.value) : undefined
    const hiddenPolylineIdSet = hiddenPolylineIds ? new Set(hiddenPolylineIds.value) : undefined

    hoveredPolylineIndex.value = -1
    hoveredNodeIndex.value = -1
    hoveredSegmentInfo.polylineIndex = -1
    hoveredSegmentInfo.segmentIndex = -1
    previewNodePosition.x = null
    previewNodePosition.y = null

    if (!polygons) return // Skip if polygons not provided

    let closestNodeDistance = Infinity
    let closestNodeInfo = { polylineIndex: -1, nodeIndex: -1 }

    let closestSegmentDistance = Infinity
    let closestSegmentInfo: { polylineIndex: number, segmentIndex: number, closestPoint: Point | null } = {
      polylineIndex: -1,
      segmentIndex: -1,
      closestPoint: null
    }

    let closestPolylineDistance = Infinity
    let closestPolylineInfo = { polylineIndex: -1 }

    const hoverablePolylineIndex = getHoverablePolylineAtPoint(
      polylines,
      polygons,
      point,
      selectedPolygonIndex.value,
      selectedPolylineIndex.value,
      HIT_DETECTION.BASELINE_SEGMENT_THRESHOLD,
      spatialIndex,
      viewMode?.value,
      hiddenPolygonIdSet,
      hiddenPolylineIdSet
    )

    polylines.forEach((polyline, polylineIndex) => {
      if (polylineIndex !== hoverablePolylineIndex) {
        return
      }
      const points = polyline.points

      points.forEach((node, nodeIndex) => {
        const distance = Math.sqrt((point.x - node.x) ** 2 + (point.y - node.y) ** 2)
        if (distance < closestNodeDistance && distance < HIT_DETECTION.NODE_THRESHOLD) {
          closestNodeDistance = distance
          closestNodeInfo = { polylineIndex, nodeIndex }
        }
      })

      if (polylineIndex === selectedPolylineIndex.value && polylineIndex >= 0) {
        for (let i = 0; i < points.length - 1; i++) {
          const p1 = points[i]
          const p2 = points[i + 1]
          if (!p1 || !p2) continue

          const segmentResult = getDistanceToLineSegment(point, p1, p2)
          if (segmentResult.distance < closestSegmentDistance && segmentResult.distance < HIT_DETECTION.EDGE_THRESHOLD) {
            closestSegmentDistance = segmentResult.distance
            closestSegmentInfo = {
              polylineIndex,
              segmentIndex: i,
              closestPoint: segmentResult.closestPoint
            }
          }
        }
      }

      for (let i = 0; i < points.length - 1; i++) {
        const p1 = points[i]
        const p2 = points[i + 1]
        if (!p1 || !p2) continue

        const segmentResult = getDistanceToLineSegment(point, p1, p2)
        if (segmentResult.distance < closestPolylineDistance && segmentResult.distance < HIT_DETECTION.NODE_THRESHOLD) {
          closestPolylineDistance = segmentResult.distance
          closestPolylineInfo = { polylineIndex }
        }
      }
    })

    if (closestNodeInfo.polylineIndex >= 0) {
      hoveredPolylineIndex.value = closestNodeInfo.polylineIndex
      hoveredNodeIndex.value = closestNodeInfo.nodeIndex
    } else if (closestSegmentInfo.polylineIndex >= 0 && closestSegmentInfo.closestPoint) {
      hoveredPolylineIndex.value = closestSegmentInfo.polylineIndex
      hoveredSegmentInfo.polylineIndex = closestSegmentInfo.polylineIndex
      hoveredSegmentInfo.segmentIndex = closestSegmentInfo.segmentIndex
      hoveredSegmentInfo.closestPoint = closestSegmentInfo.closestPoint
      previewNodePosition.x = closestSegmentInfo.closestPoint.x
      previewNodePosition.y = closestSegmentInfo.closestPoint.y
    } else if (closestPolylineInfo.polylineIndex >= 0) {
      hoveredPolylineIndex.value = closestPolylineInfo.polylineIndex
    }
  }

  /**
   * Handle mouse down for polyline editing.
   */
  function handleMouseDown(point: Point, selectedPolygonIndex: Ref<number>, _canvas: HTMLCanvasElement | null): boolean {
    if (!polygons) return false // Skip if polygons not provided

    if (hoveredNodeIndex.value >= 0 && hoveredPolylineIndex.value >= 0) {
      const polyline = polylines[hoveredPolylineIndex.value]
      if (polyline && hoveredNodeIndex.value < polyline.points.length) {
        const nodePoint = polyline.points[hoveredNodeIndex.value]
        if (nodePoint) {
          startDragging(hoveredPolylineIndex.value, hoveredNodeIndex.value, nodePoint)
          selectedPolylineIndex.value = hoveredPolylineIndex.value
          if (externalSelectedPolygonIndex) {
            const isInBaselineViewMode = viewMode?.value === 'baseline'
            if (isInBaselineViewMode) {
              externalSelectedPolygonIndex.value = -1
            } else if (polyline.parentId && polygons) {
              const parentIndex = polygons.findIndex(p => p.id === polyline.parentId)
              externalSelectedPolygonIndex.value = parentIndex >= 0 ? parentIndex : externalSelectedPolygonIndex.value
            }
          }
          return true
        }
      }
    }

    if (hoveredSegmentInfo.polylineIndex >= 0 && hoveredSegmentInfo.segmentIndex >= 0) {
      const polylineIndex = hoveredSegmentInfo.polylineIndex
      const segmentIndex = hoveredSegmentInfo.segmentIndex
      const polyline = polylines[polylineIndex]

      if (polyline && segmentIndex < polyline.points.length - 1 && hoveredSegmentInfo.closestPoint) {
        const originalPoints = [...polyline.points]
        const newPoint = { ...hoveredSegmentInfo.closestPoint }
        const newNodeIndex = segmentIndex + 1

        polyline.points.splice(newNodeIndex, 0, newPoint)

        draggedNodeInfo.originalPoints = originalPoints
        draggedNodeInfo.isNewlyInsertedNode = true

        updateHoverStates(point, selectedPolygonIndex)

        startDragging(polylineIndex, newNodeIndex, newPoint)

        selectedPolylineIndex.value = polylineIndex
        if (externalSelectedPolygonIndex) {
          const isInBaselineViewMode = viewMode?.value === 'baseline'
          if (isInBaselineViewMode) {
            externalSelectedPolygonIndex.value = -1
          } else if (polyline.parentId && polygons) {
            const parentIndex = polygons.findIndex(p => p.id === polyline.parentId)
            externalSelectedPolygonIndex.value = parentIndex >= 0 ? parentIndex : externalSelectedPolygonIndex.value
          }
        }
        return true
      }
    }

    if (hoveredPolylineIndex.value >= 0 && hoveredNodeIndex.value < 0 && hoveredSegmentInfo.polylineIndex < 0) {
      selectedPolylineIndex.value = hoveredPolylineIndex.value
      const polyline = polylines[hoveredPolylineIndex.value]
      if (externalSelectedPolygonIndex) {
        const isInBaselineViewMode = viewMode?.value === 'baseline'
        if (isInBaselineViewMode) {
          externalSelectedPolygonIndex.value = -1
        } else if (polyline?.parentId && polygons) {
          const parentIndex = polygons.findIndex(p => p.id === polyline.parentId)
          externalSelectedPolygonIndex.value = parentIndex >= 0 ? parentIndex : externalSelectedPolygonIndex.value
        }
      }
      return true
    }

    return false
  }

  /**
   * Handle mouse move for dragging nodes.
   */
  function handleMouseMove(point: Point): void {
    if (!draggedNodeInfo.isDragging) return

    const polyline = polylines[draggedNodeInfo.polylineIndex]
    if (!polyline) return

    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        isInvalidPosition.value = true
        return
      }
    }

    if (constrainToParent?.value && polygons) {
      if (polyline.parentId) {
        const parentPolygon = polygons.find(p => p.id === polyline.parentId)
        if (parentPolygon && !isPointWithinParentBounds(point, parentPolygon)) {
          isInvalidPosition.value = true
          return
        }
      }
    }

    isInvalidPosition.value = false

    polyline.points[draggedNodeInfo.nodeIndex] = { ...point }
  }

  /**
   * Handle mouse up to finish dragging.
   */
  function handleMouseUp(_canvas: HTMLElement | null): void {
    if (!draggedNodeInfo.isDragging) return

    const polyline = polylines[draggedNodeInfo.polylineIndex]
    if (!polyline) return

    if (draggedNodeInfo.isNewlyInsertedNode) {
      if (draggedNodeInfo.originalPoints) {
        const finalPoints = [...polyline.points]

        polyline.points = [...draggedNodeInfo.originalPoints]

        const updateCommand = new UpdatePolylineCommand({
          polylineId: polyline.id,
          newPoints: finalPoints // Use the stored final state
        })

        commander.execute(updateCommand, getCommandContext())
      }
    } else {
      if (draggedNodeInfo.originalPoint) {
        const currentPoint = polyline.points[draggedNodeInfo.nodeIndex]
        if (currentPoint
          && (currentPoint.x !== draggedNodeInfo.originalPoint.x
            || currentPoint.y !== draggedNodeInfo.originalPoint.y)) {
          const updateCommand = new UpdatePolylineCommand({
            polylineId: polyline.id,
            newPoints: [...polyline.points]
          })

          commander.execute(updateCommand, getCommandContext())
        }
      }
    }

    resetDraggingState()
    justFinishedDragging.value = true
    setTimeout(() => { justFinishedDragging.value = false }, TIMING.DRAG_COMPLETION_DELAY)
  }

  /**
   * Start dragging a node.
   */
  function startDragging(polylineIndex: number, nodeIndex: number, originalPoint: Point): void {
    draggedNodeInfo.polylineIndex = polylineIndex
    draggedNodeInfo.nodeIndex = nodeIndex
    draggedNodeInfo.isDragging = true
    draggedNodeInfo.originalPoint = { ...originalPoint }
  }

  /**
   * Reset dragging state.
   */
  function resetDraggingState(): void {
    draggedNodeInfo.polylineIndex = -1
    draggedNodeInfo.nodeIndex = -1
    draggedNodeInfo.isDragging = false
    draggedNodeInfo.originalPoint = null
    draggedNodeInfo.originalPoints = undefined
    draggedNodeInfo.isNewlyInsertedNode = false
    isInvalidPosition.value = false
  }

  /**
   * Check if currently dragging.
   */
  function isDragging(): boolean {
    return draggedNodeInfo.isDragging
  }

  /**
   * Handle selection of polylines.
   * @returns true if a polyline was selected, false otherwise
   */
  function handleSelection(_point: Point, _selectedPolygonIndex: Ref<number>, _isDrawingMode = false): boolean {
    if (!polygons) return false // Skip if polygons not provided

    if (hoveredPolylineIndex.value >= 0) {
      selectedPolylineIndex.value = hoveredPolylineIndex.value
      const polyline = polylines[hoveredPolylineIndex.value]
      if (externalSelectedPolygonIndex) {
        const isInBaselineViewMode = viewMode?.value === 'baseline'
        if (isInBaselineViewMode) {
          externalSelectedPolygonIndex.value = -1
        } else if (polyline?.parentId) {
          const parentIndex = polygons.findIndex(p => p.id === polyline.parentId)
          if (parentIndex >= 0) externalSelectedPolygonIndex.value = parentIndex
        }
      }
      return true
    } else {
      if (selectedPolylineIndex.value >= 0) {
        const previouslySelectedPolyline = polylines[selectedPolylineIndex.value]

        const isInBaselineViewMode = viewMode?.value === 'baseline'

        if (isInBaselineViewMode) {
          selectedPolylineIndex.value = -1
          if (externalSelectedPolygonIndex) {
            externalSelectedPolygonIndex.value = -1
          }
          return true // We handled the selection (moved to root)
        } else if (previouslySelectedPolyline && previouslySelectedPolyline.parentId && externalSelectedPolygonIndex) {
          const parentIndex = polygons.findIndex(p => p.id === previouslySelectedPolyline.parentId)
          if (parentIndex >= 0) {
            externalSelectedPolygonIndex.value = parentIndex
            selectedPolylineIndex.value = -1
            return true // We handled the selection (moved up to parent)
          }
        }
      }
      selectedPolylineIndex.value = -1
      return false
    }
  }

  /**
   * Clear all editing state.
   */
  function clearEditingState(): void {
    selectedPolylineIndex.value = -1
    hoveredPolylineIndex.value = -1
    hoveredNodeIndex.value = -1
    resetDraggingState()
    previewNodePosition.x = null
    previewNodePosition.y = null
    hoveredSegmentInfo.polylineIndex = -1
    hoveredSegmentInfo.segmentIndex = -1
    hoveredSegmentInfo.closestPoint = null
  }

  /**
   * Reset drag completion flag.
   */
  function resetDragCompletionFlag(): void {
    justFinishedDragging.value = false
  }

  return {
    selectedPolylineIndex,
    hoveredPolylineIndex,
    hoveredNodeIndex,
    draggedNodeInfo,
    hoveredSegmentInfo,
    previewNodePosition,
    isInvalidPosition,
    justFinishedDragging,

    updateHoverStates,
    handleMouseDown,
    handleMouseMove,
    handleMouseUp,
    handleSelection,
    isDragging,
    clearEditingState,
    resetDragCompletionFlag
  }
}
