import type { UnwrapNestedRefs } from 'vue'
import type { Commander } from '@/commands'
import { UpdatePolygonCommand } from '@/commands'
import type { Point, View, AspectRatioScale, ImageSize } from '@/models/editor'
import type { RenderablePolygon, ViewMode } from '@/types/editor/rendering'
import { getEditorSession } from '@/session/editor/editor-session'
import type { SpatialIndexService } from '@/services/editor/spatial-index-service'
import {
  getNodeAtPoint,
  getVisiblePolygonAtPoint,
  getHoverablePolygonAtPoint,
  getClosestEdge,
  wouldSelfIntersect,
  isPointInPolygon
} from '@/utils/editor/hit-detection'
import { clampToWorldBounds, getImageBounds } from '@/utils/editor/coordinates'
import { areAllPointsWithinParentBounds } from '@/utils/editor/parent-constraint-utils'

export interface HoveredEdgeInfo {
  polygonIndex: number
  edgeStartIndex: number
  t: number
}

export interface PolygonPreviewNodePosition {
  x: number | null
  y: number | null
}

export interface PolygonDraggedNodeInfo {
  polygonIndex: number
  nodeIndex: number
  isDragging: boolean
}

export interface DragOriginalPosition {
  polygonId: string
  originalPoints: Point[]
}

/**
 * Composable for managing polygon editing functionality.
 * Handles node selection, dragging, edge insertion, and polygon selection.
 */
export function usePolygonEditing(
  polygons: RenderablePolygon[],
  view: View,
  _aspectRatioScale: AspectRatioScale | Ref<AspectRatioScale>,
  constrainToImage: Ref<boolean> | undefined,
  imageSize: Ref<ImageSize> | undefined,
  constrainToParent: Ref<boolean> | undefined,
  spatialIndex: SpatialIndexService | undefined,
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

  const hoveredPolygonIndex = ref<number>(-1)
  const hoveredNodeIndex = ref<number>(-1)
  const hoveredEdgeInfo: UnwrapNestedRefs<HoveredEdgeInfo> = reactive({
    polygonIndex: -1,
    edgeStartIndex: -1,
    t: 0
  })

  const previewNodePosition: UnwrapNestedRefs<PolygonPreviewNodePosition> = reactive({ x: null, y: null })

  const draggedNodeInfo: UnwrapNestedRefs<PolygonDraggedNodeInfo> = reactive({
    polygonIndex: -1,
    nodeIndex: -1,
    isDragging: false
  })
  const dragOriginalPosition = ref<DragOriginalPosition | null>(null)
  const justFinishedDragging = ref<boolean>(false)

  const isInvalidPosition = ref<boolean>(false)

  /**
   * Check if a polygon is currently being dragged.
   *
   * @returns True if dragging is active
   */
  function isDragging(): boolean {
    return draggedNodeInfo.isDragging
  }

  /**
   * Update hover states for polygons, nodes, and edges.
   *
   * @param point - World coordinates to check
   * @param selectedPolygonIndex - Currently selected polygon index
   */
  function updateHoverStates(point: Point, selectedPolygonIndex: Ref<number>): void {
    const hiddenPolygonIdSet = hiddenPolygonIds ? new Set(hiddenPolygonIds.value) : undefined
    const hiddenPolylineIdSet = hiddenPolylineIds ? new Set(hiddenPolylineIds.value) : undefined

    const rawViewMode = viewMode?.value
    const normalizedViewMode: ViewMode | undefined
      = rawViewMode === 'default' || rawViewMode === 'textline' || rawViewMode === 'baseline'
        ? rawViewMode
        : undefined

    const hoverIndex = getHoverablePolygonAtPoint(
      polygons,
      point,
      selectedPolygonIndex.value,
      externalSelectedPolylineIndex?.value ?? -1,
      [],
      spatialIndex,
      normalizedViewMode,
      hiddenPolygonIdSet,
      hiddenPolylineIdSet
    )

    hoveredPolygonIndex.value = hoverIndex

    const isBaselineSelected = externalSelectedPolylineIndex && externalSelectedPolylineIndex.value >= 0

    let currentNodeIndex = -1
    if (selectedPolygonIndex.value >= 0 && !isBaselineSelected) {
      currentNodeIndex = getNodeAtPoint(polygons, point, selectedPolygonIndex.value, view)
    }

    if (selectedPolygonIndex.value >= 0 && currentNodeIndex === -1 && !isBaselineSelected) {
      const edgeInfo = getClosestEdge(polygons, point, selectedPolygonIndex.value, view)
      if (edgeInfo) {
        hoveredEdgeInfo.polygonIndex = edgeInfo.polygonIndex
        hoveredEdgeInfo.edgeStartIndex = edgeInfo.edgeStartIndex
        hoveredEdgeInfo.t = edgeInfo.closestPoint.t
        let constrainedPreviewPoint: Point = { x: edgeInfo.closestPoint.x, y: edgeInfo.closestPoint.y }
        if (constrainToImage?.value && imageSize?.value) {
          constrainedPreviewPoint = clampToWorldBounds(constrainedPreviewPoint, getImageBounds(imageSize.value))
        }

        previewNodePosition.x = constrainedPreviewPoint.x
        previewNodePosition.y = constrainedPreviewPoint.y
      } else {
        resetEdgeHover()
      }
    } else {
      resetEdgeHover()
    }

    hoveredNodeIndex.value = currentNodeIndex
  }

  /**
   * Reset edge hover state.
   */
  function resetEdgeHover(): void {
    hoveredEdgeInfo.polygonIndex = -1
    hoveredEdgeInfo.edgeStartIndex = -1
    hoveredEdgeInfo.t = 0
    previewNodePosition.x = null
    previewNodePosition.y = null
  }

  /**
   * Handle mouse down for polygon editing operations.
   * Starts node dragging or inserts new nodes on edges.
   *
   * @param point - World coordinates where mouse was pressed
   * @param selectedPolygonIndex - Currently selected polygon index
   * @param canvas - Canvas element
   * @returns True if an editing operation was initiated
   */
  function handleMouseDown(point: Point, selectedPolygonIndex: Ref<number>, _canvas: HTMLCanvasElement | null): boolean {
    if (externalSelectedPolylineIndex && externalSelectedPolylineIndex.value >= 0) {
      return false
    }

    if (selectedPolygonIndex.value >= 0) {
      const nodeIndex = getNodeAtPoint(polygons, point, selectedPolygonIndex.value, view)
      if (nodeIndex >= 0) {
        const polygon = polygons[selectedPolygonIndex.value]
        if (polygon) {
          dragOriginalPosition.value = {
            polygonId: polygon.id,
            originalPoints: [...polygon.points]
          }

          draggedNodeInfo.polygonIndex = selectedPolygonIndex.value
          draggedNodeInfo.nodeIndex = nodeIndex
          draggedNodeInfo.isDragging = true
          return true
        }
      }
    }

    if (selectedPolygonIndex.value >= 0 && hoveredEdgeInfo.polygonIndex >= 0) {
      const polyIndex = hoveredEdgeInfo.polygonIndex
      const edgeStartIndex = hoveredEdgeInfo.edgeStartIndex

      if (polyIndex >= 0 && polyIndex < polygons.length) {
        const polygon = polygons[polyIndex]
        if (polygon) {
          const newPoints = [...polygon.points]
          const insertIndex = edgeStartIndex + 1
          const insertPoint: Point = {
            x: previewNodePosition.x ?? 0,
            y: previewNodePosition.y ?? 0
          }
          newPoints.splice(insertIndex, 0, insertPoint)

          const updateCommand = new UpdatePolygonCommand({
            polygonId: polygon.id,
            newPoints: newPoints
          })

          commander.execute(updateCommand, getCommandContext())

          draggedNodeInfo.polygonIndex = polyIndex
          draggedNodeInfo.nodeIndex = insertIndex
          draggedNodeInfo.isDragging = true

          dragOriginalPosition.value = {
            polygonId: polygon.id,
            originalPoints: newPoints
          }
          return true
        }
      }
    }

    return false
  }

  /**
   * Handle mouse move for polygon editing operations.
   * Updates node dragging position and validation.
   *
   * @param point - Current world coordinates
   */
  function handleMouseMove(point: Point): void {
    if (!draggedNodeInfo.isDragging) return

    const polyIndex = draggedNodeInfo.polygonIndex
    const nodeIndex = draggedNodeInfo.nodeIndex

    if (polyIndex >= 0 && polyIndex < polygons.length && nodeIndex >= 0) {
      const polygon = polygons[polyIndex]
      if (!polygon) return

      let constrainedPoint = point
      if (constrainToImage?.value && imageSize?.value) {
        constrainedPoint = clampToWorldBounds(point, getImageBounds(imageSize.value))
      }

      const wouldIntersect = wouldSelfIntersect(polygon.points, nodeIndex, constrainedPoint)
      isInvalidPosition.value = wouldIntersect

      let violatesParentConstraints = false
      if (constrainToParent?.value && !wouldIntersect) {
        if (polygon.parentId) {
          const parentPolygon = polygons.find(p => p.id === polygon.parentId)

          if (parentPolygon) {
            const tempPoints = [...polygon.points]
            tempPoints[nodeIndex] = constrainedPoint

            if (!areAllPointsWithinParentBounds(tempPoints, parentPolygon)) {
              violatesParentConstraints = true
              isInvalidPosition.value = true
            }
          }
        }
      }

      if (!wouldIntersect && !violatesParentConstraints) {
        polygon.points[nodeIndex] = { ...constrainedPoint }
      }
    }

    hoveredNodeIndex.value = -1
    resetEdgeHover()
  }

  /**
   * Handle mouse up to complete polygon editing operations.
   * Creates update commands for successful node dragging.
   *
   * @param canvas - Canvas element
   */
  function handleMouseUp(_canvas: HTMLElement | null): void {
    if (draggedNodeInfo.isDragging) {
      if (dragOriginalPosition.value && !isInvalidPosition.value) {
        const polygon = polygons[draggedNodeInfo.polygonIndex]
        if (polygon) {
          const updateCommand = new UpdatePolygonCommand({
            polygonId: dragOriginalPosition.value.polygonId,
            newPoints: [...polygon.points]
          })

          polygon.points = [...dragOriginalPosition.value.originalPoints]
          commander.execute(updateCommand, getCommandContext())
        }
      }

      draggedNodeInfo.isDragging = false
      draggedNodeInfo.polygonIndex = -1
      draggedNodeInfo.nodeIndex = -1

      hoveredNodeIndex.value = -1
      resetEdgeHover()

      isInvalidPosition.value = false
      dragOriginalPosition.value = null

      justFinishedDragging.value = true
    }
  }

  function cancelCurrentOperation(): void {
    if (draggedNodeInfo.isDragging && dragOriginalPosition.value) {
      const polygon = polygons[draggedNodeInfo.polygonIndex]
      if (polygon) {
        polygon.points = dragOriginalPosition.value.originalPoints.map(point => ({ ...point }))
      }
    }

    hoveredNodeIndex.value = -1
    resetEdgeHover()
    draggedNodeInfo.polygonIndex = -1
    draggedNodeInfo.nodeIndex = -1
    draggedNodeInfo.isDragging = false
    isInvalidPosition.value = false
    dragOriginalPosition.value = null
    justFinishedDragging.value = false
  }

  /**
   * Reset the drag completion flag.
   */
  function resetDragCompletionFlag(): void {
    justFinishedDragging.value = false
  }

  /**
   * Find the parent polygon index for a given polygon.
   *
   * @param polygonId - ID of the polygon to find parent for
   * @returns Index of parent polygon, or -1 if no parent found
   */
  function findParentPolygonIndex(polygonId: string): number {
    const polygon = polygons.find(p => p.id === polygonId)
    if (!polygon || !polygon.parentId) {
      return -1 // No parent or polygon not found
    }

    const parentIndex = polygons.findIndex(p => p.id === polygon.parentId)
    return parentIndex
  }

  /**
   * Handle polygon selection logic.
   * When clicking outside selected polygon, goes up one level instead of full deselection.
   *
   * @param point - World coordinates where click occurred
   * @param selectedPolygonIndex - Currently selected polygon index
   * @param isDrawingMode - Whether currently in drawing mode
   */
  function handleSelection(point: Point, selectedPolygonIndex: Ref<number>, isDrawingMode = false): void {
    if (justFinishedDragging.value) return

    const hiddenPolygonIdSet = hiddenPolygonIds ? new Set(hiddenPolygonIds.value) : undefined

    const rawViewMode = viewMode?.value
    const normalizedViewMode: ViewMode | undefined
      = rawViewMode === 'default' || rawViewMode === 'textline' || rawViewMode === 'baseline'
        ? rawViewMode
        : undefined

    const polygonIndex = getVisiblePolygonAtPoint(polygons, point, selectedPolygonIndex.value, spatialIndex, normalizedViewMode, hiddenPolygonIdSet)
    const clickedInsideSelectedPolygon = !isPointOutsideSelectedPolygon(point, selectedPolygonIndex.value)

    if (polygonIndex >= 0 && polygonIndex !== selectedPolygonIndex.value) {
      if (externalSelectedPolylineIndex && externalSelectedPolylineIndex.value >= 0) {
        externalSelectedPolylineIndex.value = -1
      }
      selectedPolygonIndex.value = polygonIndex
      hoveredPolygonIndex.value = -1
      return
    }

    if (clickedInsideSelectedPolygon && selectedPolygonIndex.value >= 0) {
      if (externalSelectedPolylineIndex && externalSelectedPolylineIndex.value >= 0) {
        externalSelectedPolylineIndex.value = -1
      }
      return
    }

    if (!clickedInsideSelectedPolygon && selectedPolygonIndex.value >= 0 && !isDrawingMode) {
      const selectedPolygon = polygons[selectedPolygonIndex.value]
      if (selectedPolygon) {
        if (externalSelectedPolylineIndex && externalSelectedPolylineIndex.value >= 0) {
          externalSelectedPolylineIndex.value = -1
          return
        }

        const isInViewMode = viewMode?.value === 'textline' || viewMode?.value === 'baseline'

        if (isInViewMode) {
          selectedPolygonIndex.value = -1
          hoveredPolygonIndex.value = -1
        } else {
          const parentIndex = findParentPolygonIndex(selectedPolygon.id)

          if (parentIndex >= 0) {
            selectedPolygonIndex.value = parentIndex
            hoveredPolygonIndex.value = -1
          } else {
            selectedPolygonIndex.value = -1
            hoveredPolygonIndex.value = -1
          }
        }
      }
      return
    }
  }

  /**
   * Check if a point is outside the currently selected polygon.
   *
   * @param point - Point to check
   * @param selectedPolygonIndex - Index of the currently selected polygon
   * @returns True if point is outside the selected polygon or no polygon is selected
   */
  function isPointOutsideSelectedPolygon(point: Point, selectedPolygonIndex: number): boolean {
    if (selectedPolygonIndex < 0 || selectedPolygonIndex >= polygons.length) {
      return true // No polygon selected, consider as "outside"
    }

    const selectedPolygon = polygons[selectedPolygonIndex]
    if (!selectedPolygon) return true

    return !isPointInPolygon(point, selectedPolygon.points)
  }

  /**
   * Clear all editing state.
   */
  function clearEditingState(): void {
    hoveredPolygonIndex.value = -1
    hoveredNodeIndex.value = -1
    resetEdgeHover()
    draggedNodeInfo.polygonIndex = -1
    draggedNodeInfo.nodeIndex = -1
    draggedNodeInfo.isDragging = false
    isInvalidPosition.value = false
    dragOriginalPosition.value = null
    justFinishedDragging.value = false
  }

  return {
    hoveredPolygonIndex,
    hoveredNodeIndex,
    hoveredEdgeInfo,
    previewNodePosition,
    draggedNodeInfo,
    isInvalidPosition,
    justFinishedDragging,

    isDragging,

    updateHoverStates,
    handleMouseDown,
    handleMouseMove,
    handleMouseUp,
    cancelCurrentOperation,
    resetDragCompletionFlag,
    handleSelection,
    clearEditingState
  }
}
