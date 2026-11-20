import type { UnwrapNestedRefs } from 'vue'
import type { Commander } from '@/commands'
import { ConstrainToParentCommand, TableGridPointCommand, UpdatePolygonCommand } from '@/commands'
import type { Point, View, AspectRatioScale, ImageSize } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { normalizeLayoutViewMode } from '@/services/editor/visibility-service'
import { getEditorSession } from '@/session/editor/editor-session'
import type { SpatialIndexService } from '@/services/editor/spatial-index-service'
import {
  getNodeAtPoint,
  getVisiblePolygonAtPoint,
  getHoverablePolygonAtPoint,
  getClosestEdge,
  wouldSelfIntersect,
  isPointInPolygon,
  isClosedPolygonSelfIntersecting
} from '@/utils/editor/hit-detection'
import { clampToWorldBounds, getImageBounds } from '@/utils/editor/coordinates'
import { areAllPointsWithinParentBounds } from '@/utils/editor/parent-constraint-utils'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { buildTableModel } from '@/utils/editor/table/table-model'
import { gridHasPoint, gridLineAt, gridLinePoints, insertGridAddPoint, moveGridLine, type GridLineRef } from '@/utils/editor/table/table-structure'
import type { TableModel } from '@/utils/editor/table/table-model'

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

interface LinkedTableDrag {
  tableId: string
  from: Point
  inserted: boolean
  nodes: { points: Point[], index: number }[]
  originals: { points: Point[], original: Point[] }[]
}

interface TableLineDrag {
  tableId: string
  model: TableModel
  line: GridLineRef
  start: Point
  delta: number
  nodes: { points: Point[], index: number, original: Point }[]
}

export interface PolygonEditingModifiers {
  altKey?: boolean
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
  const selectedNodes = ref<{ polygonId: string, nodeIndices: number[] } | null>(null)

  function clearNodeSelection(): void {
    selectedNodes.value = null
  }

  function selectNodesInRect(
    polygonIndex: number,
    rect: { minX: number, maxX: number, minY: number, maxY: number },
    additive = false
  ): void {
    const polygon = polygons[polygonIndex]
    if (!polygon) return
    const indices = polygon.points.flatMap((point, index) =>
      point.x >= rect.minX && point.x <= rect.maxX && point.y >= rect.minY && point.y <= rect.maxY
        ? [index]
        : []
    )
    const previous = additive && selectedNodes.value?.polygonId === polygon.id
      ? selectedNodes.value.nodeIndices
      : []
    const nodeIndices = Array.from(new Set([...previous, ...indices])).sort((a, b) => a - b)
    clearNodeSelection()
    if (nodeIndices.length > 0) selectedNodes.value = { polygonId: polygon.id, nodeIndices }
    hoveredNodeIndex.value = -1
    resetEdgeHover()
  }

  function deleteTableGridNodes(polygon: RenderablePolygon, nodeIndices: number[]): boolean {
    const tableId = tableIdOf(polygon)
    const model = tableId ? gridTableModel(tableId) : undefined
    if (!tableId || !model) return false
    const points = nodeIndices.map(index => polygon.points[index]).filter((point): point is Point => !!point)
    if (points.length === 0 || !points.every(point => gridHasPoint(model, [point.x, point.y]))) return false
    try {
      commander.execute(new TableGridPointCommand({ tableId, edit: { kind: 'remove', points } }), getCommandContext())
      clearNodeSelection()
      hoveredNodeIndex.value = -1
      resetEdgeHover()
    } catch (error) {
      useToast().add({
        title: 'Cannot delete selected nodes',
        description: error instanceof Error ? error.message : undefined,
        color: 'warning'
      })
    }
    return true
  }

  /** Returns true even when deletion is blocked, so Delete never falls through to the polygon. */
  function deleteSelectedNodes(selectedPolygonIndex: number): boolean {
    const selection = selectedNodes.value
    const polygon = polygons[selectedPolygonIndex]
    if (!selection || !polygon || polygon.id !== selection.polygonId
      || (externalSelectedPolylineIndex?.value ?? -1) >= 0) return false
    if (draggedNodeInfo.isDragging) return true

    const selectedIndices = new Set(selection.nodeIndices)
    if (deleteTableGridNodes(polygon, selection.nodeIndices)) return true
    const points = polygon.points.filter((_, index) => !selectedIndices.has(index))
    let deletionError: string | null = null
    if (points.length < 3) {
      deletionError = 'A polygon needs at least 3 nodes. Select fewer nodes and try again.'
    } else if (isClosedPolygonSelfIntersecting(points)) {
      deletionError = 'Deleting these nodes would make polygon edges cross. Change the node selection and try again.'
    }
    if (deletionError) {
      useToast().add({
        title: 'Cannot delete selected nodes',
        description: deletionError,
        color: 'warning'
      })
      return true
    }

    commander.execute(new UpdatePolygonCommand({ polygonId: polygon.id, newPoints: points }), getCommandContext())
    clearNodeSelection()
    hoveredNodeIndex.value = -1
    resetEdgeHover()
    return true
  }
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
  let blockedByParentBounds = false
  let linkedTableDrag: LinkedTableDrag | null = null
  let tableLineDrag: TableLineDrag | null = null

  function tableIdOf(polygon: RenderablePolygon): string | undefined {
    return polygon.tableCell?.tableId ?? (polygon.regionKind === 'TableRegion' ? polygon.id : undefined)
  }

  function gridTableModel(tableId: string) {
    const regions = getEditorSession(canvasId)?.document.value?.page?.regions
    const table = regions ? findRegionRecursive(regions, tableId)?.region : undefined
    return table?.kind === 'TableRegion' ? buildTableModel(table) : undefined
  }

  function linkedTablePointLists(tableId: string): Point[][] {
    return polygons
      .filter(candidate => candidate.id === tableId || candidate.tableCell?.tableId === tableId)
      .flatMap(candidate => [candidate.points, ...(candidate.tableGridLines ?? [])])
  }

  function isOnSegment(point: Point, from: Point, to: Point): boolean {
    const dx = to.x - from.x
    const dy = to.y - from.y
    const lengthSquared = dx * dx + dy * dy
    if (lengthSquared === 0) return false
    const t = ((point.x - from.x) * dx + (point.y - from.y) * dy) / lengthSquared
    if (t <= 0 || t >= 1) return false
    return Math.hypot(from.x + dx * t - point.x, from.y + dy * t - point.y) <= 1e-6
  }

  function findLinkedTableDrag(polygon: RenderablePolygon, nodeIndex: number): LinkedTableDrag | null {
    const tableId = tableIdOf(polygon)
    const dragged = polygon.points[nodeIndex]
    if (!tableId || !dragged) return null
    const model = gridTableModel(tableId)
    if (!model || !gridHasPoint(model, [dragged.x, dragged.y])) return null

    const from = { ...dragged }
    const same = (point: Point) => Math.abs(point.x - from.x) <= 1e-9 && Math.abs(point.y - from.y) <= 1e-9
    const drag: LinkedTableDrag = { tableId, from, inserted: false, nodes: [], originals: [] }
    for (const points of linkedTablePointLists(tableId)) {
      points.forEach((point, index) => {
        if (same(point) && !(points === polygon.points && index === nodeIndex)) drag.nodes.push({ points, index })
      })
    }
    return drag
  }

  function startTableInsertion(polygon: RenderablePolygon, insertIndex: number, at: Point): LinkedTableDrag | null {
    const tableId = tableIdOf(polygon)
    if (!tableId) return null
    const model = gridTableModel(tableId)
    if (!model || !insertGridAddPoint(model, [at.x, at.y], [at.x, at.y])) return null

    const drag: LinkedTableDrag = { tableId, from: { ...at }, inserted: true, nodes: [], originals: [] }
    for (const points of linkedTablePointLists(tableId)) {
      if (points === polygon.points) continue
      const closed = polygons.some(candidate => candidate.points === points)
      const edgeCount = closed ? points.length : points.length - 1
      for (let index = 0; index < edgeCount; index++) {
        if (!isOnSegment(at, points[index]!, points[(index + 1) % points.length]!)) continue
        drag.originals.push({ points, original: [...points] })
        points.splice(index + 1, 0, { ...at })
        drag.nodes.push({ points, index: index + 1 })
        break
      }
    }
    polygon.points.splice(insertIndex, 0, { ...at })
    return drag
  }

  function restoreLinkedTableNodes(drag: LinkedTableDrag): void {
    for (const node of drag.nodes) node.points[node.index] = { ...drag.from }
    for (const { points, original } of drag.originals) points.splice(0, points.length, ...original)
  }

  function moveLinkedTableNodes(point: Point): void {
    for (const node of linkedTableDrag?.nodes ?? []) node.points[node.index] = { ...point }
  }

  function startTableLineDrag(point: Point, selectedPolygonIndex: number): TableLineDrag | null {
    const polygon = polygons[selectedPolygonIndex]
    const tableId = polygon ? tableIdOf(polygon) : undefined
    const edge = getClosestEdge(polygons, point, selectedPolygonIndex, view)
    if (!polygon || !tableId || !edge) return null
    const model = gridTableModel(tableId)
    const a = polygon.points[edge.edgeStartIndex]
    const b = polygon.points[(edge.edgeStartIndex + 1) % polygon.points.length]
    if (!model?.grid || !a || !b) return null
    const line = gridLineAt(model, [(a.x + b.x) / 2, (a.y + b.y) / 2])
    if (!line) return null

    const linePoints = gridLinePoints(model, line)
    const onLine = (candidate: Point) => linePoints.some(([x, y]) => Math.abs(candidate.x - x) <= 1e-9 && Math.abs(candidate.y - y) <= 1e-9)
    const drag: TableLineDrag = { tableId, model, line, start: { ...point }, delta: 0, nodes: [] }
    for (const points of linkedTablePointLists(tableId)) {
      points.forEach((candidate, index) => {
        if (onLine(candidate)) drag.nodes.push({ points, index, original: { ...candidate } })
      })
    }
    return drag
  }

  function placeTableLineNodes(drag: TableLineDrag, delta: number): void {
    for (const node of drag.nodes) {
      node.points[node.index] = drag.line.axis === 'row'
        ? { x: node.original.x, y: node.original.y + delta }
        : { x: node.original.x + delta, y: node.original.y }
    }
  }

  // Geometry replacements (including undo/redo) invalidate node indices.
  watch(() => {
    const polygon = polygons.find(p => p.id === selectedNodes.value?.polygonId)
    return polygon?.points
  }, (_points, previousPoints) => {
    if (previousPoints && !draggedNodeInfo.isDragging) clearNodeSelection()
  }, { flush: 'sync' })

  /**
   * Check if a polygon is currently being dragged.
   *
   * @returns True if dragging is active
   */
  function isDragging(): boolean {
    return draggedNodeInfo.isDragging || tableLineDrag !== null
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
    const normalizedViewMode = normalizeLayoutViewMode(rawViewMode)

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
  function handleMouseDown(point: Point, selectedPolygonIndex: Ref<number>, _canvas: HTMLCanvasElement | null, modifiers: PolygonEditingModifiers = {}): boolean {
    if (externalSelectedPolylineIndex && externalSelectedPolylineIndex.value >= 0) {
      return false
    }

    clearNodeSelection()

    if (modifiers.altKey && selectedPolygonIndex.value >= 0) {
      tableLineDrag = startTableLineDrag(point, selectedPolygonIndex.value)
      if (tableLineDrag) {
        hoveredNodeIndex.value = -1
        resetEdgeHover()
        return true
      }
    }

    if (selectedPolygonIndex.value >= 0) {
      const nodeIndex = getNodeAtPoint(polygons, point, selectedPolygonIndex.value, view)
      if (nodeIndex >= 0) {
        const polygon = polygons[selectedPolygonIndex.value]
        if (polygon) {
          selectedNodes.value = { polygonId: polygon.id, nodeIndices: [nodeIndex] }
          dragOriginalPosition.value = {
            polygonId: polygon.id,
            originalPoints: [...polygon.points]
          }

          draggedNodeInfo.polygonIndex = selectedPolygonIndex.value
          draggedNodeInfo.nodeIndex = nodeIndex
          draggedNodeInfo.isDragging = true
          linkedTableDrag = findLinkedTableDrag(polygon, nodeIndex)
          return true
        }
      }
    }

    if (selectedPolygonIndex.value >= 0 && hoveredEdgeInfo.polygonIndex >= 0) {
      const polyIndex = hoveredEdgeInfo.polygonIndex
      const edgeStartIndex = hoveredEdgeInfo.edgeStartIndex

      if (polyIndex >= 0 && polyIndex < polygons.length) {
        const polygon = polygons[polyIndex]
        const tablePoint: Point = { x: previewNodePosition.x ?? 0, y: previewNodePosition.y ?? 0 }
        const originalPoints = polygon ? [...polygon.points] : []
        const tableInsertion = polygon ? startTableInsertion(polygon, edgeStartIndex + 1, tablePoint) : null
        if (polygon && tableInsertion) {
          linkedTableDrag = tableInsertion
          selectedNodes.value = { polygonId: polygon.id, nodeIndices: [edgeStartIndex + 1] }
          draggedNodeInfo.polygonIndex = polyIndex
          draggedNodeInfo.nodeIndex = edgeStartIndex + 1
          draggedNodeInfo.isDragging = true
          dragOriginalPosition.value = { polygonId: polygon.id, originalPoints }
          return true
        }
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

          selectedNodes.value = { polygonId: polygon.id, nodeIndices: [insertIndex] }
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
    if (tableLineDrag) {
      const offset = tableLineDrag.line.axis === 'row' ? point.y - tableLineDrag.start.y : point.x - tableLineDrag.start.x
      const valid = moveGridLine(tableLineDrag.model, tableLineDrag.line, offset) !== undefined
      if (valid) {
        tableLineDrag.delta = offset
        placeTableLineNodes(tableLineDrag, offset)
      }
      return
    }
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
      if (constrainToParent?.value && !wouldIntersect && polygon.parentId !== linkedTableDrag?.tableId) {
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

      blockedByParentBounds = violatesParentConstraints

      if (!wouldIntersect && !violatesParentConstraints) {
        polygon.points[nodeIndex] = { ...constrainedPoint }
        moveLinkedTableNodes(constrainedPoint)
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
    if (tableLineDrag) {
      const drag = tableLineDrag
      tableLineDrag = null
      placeTableLineNodes(drag, 0)
      if (drag.delta !== 0) {
        commander.execute(new TableGridPointCommand({ tableId: drag.tableId, edit: { kind: 'move-line', line: drag.line, delta: drag.delta } }), getCommandContext())
      }
      justFinishedDragging.value = true
      return
    }
    if (draggedNodeInfo.isDragging) {
      const polygon = polygons[draggedNodeInfo.polygonIndex]
      const parent = polygons.find(p => p.id === polygon?.parentId)
      const needsRepair = blockedByParentBounds && polygon && parent && dragOriginalPosition.value
        && !areAllPointsWithinParentBounds(dragOriginalPosition.value.originalPoints, parent)
      if (isInvalidPosition.value && polygon && dragOriginalPosition.value) {
        polygon.points = dragOriginalPosition.value.originalPoints.map(point => ({ ...point }))
      }
      if (needsRepair) {
        const polygonId = polygon.id
        const session = getEditorSession(canvasId)
        useToast().add({
          id: `parent-bounds-${canvasId}-${polygonId}`,
          title: 'Shape exceeds parent bounds',
          description: 'This shape already extends outside its parent, so Parent Bounds blocks this edit. Constrain it to remove the parts outside. You can undo this action.',
          color: 'error',
          duration: 10000,
          actions: [{
            label: 'Constrain to Parent',
            onClick: () => {
              // A toast can outlive the page that produced it.
              if (!session || getEditorSession(canvasId) !== session) return
              try {
                commander.execute(new ConstrainToParentCommand([polygonId]), { session, canvasId })
              } catch (error) {
                useToast().add({
                  title: 'Could not constrain shape to parent',
                  description: error instanceof Error ? error.message : 'Check the polygon geometry and try again.',
                  color: 'error'
                })
              }
            }
          }]
        })
      }
      blockedByParentBounds = false
      const tableDrag = linkedTableDrag
      linkedTableDrag = null
      if (tableDrag && dragOriginalPosition.value) {
        const polygon = polygons[draggedNodeInfo.polygonIndex]
        const dropped = polygon?.points[draggedNodeInfo.nodeIndex]
        const to = dropped && !isInvalidPosition.value ? { ...dropped } : { ...tableDrag.from }
        restoreLinkedTableNodes(tableDrag)
        if (polygon) polygon.points = [...dragOriginalPosition.value.originalPoints]
        const moved = to.x !== tableDrag.from.x || to.y !== tableDrag.from.y
        if (tableDrag.inserted || moved) {
          const edit = tableDrag.inserted
            ? { kind: 'insert' as const, at: tableDrag.from, to }
            : { kind: 'move' as const, from: tableDrag.from, to }
          commander.execute(new TableGridPointCommand({ tableId: tableDrag.tableId, edit }), getCommandContext())
        }
      } else if (dragOriginalPosition.value && !isInvalidPosition.value) {
        const polygon = polygons[draggedNodeInfo.polygonIndex]
        if (polygon && polygon.points.some((point, index) => {
          const original = dragOriginalPosition.value?.originalPoints[index]
          return !original || point.x !== original.x || point.y !== original.y
        })) {
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
    clearNodeSelection()
    if (draggedNodeInfo.isDragging && dragOriginalPosition.value) {
      const polygon = polygons[draggedNodeInfo.polygonIndex]
      if (polygon) {
        polygon.points = dragOriginalPosition.value.originalPoints.map(point => ({ ...point }))
      }
    }
    if (linkedTableDrag) {
      restoreLinkedTableNodes(linkedTableDrag)
      linkedTableDrag = null
    }
    if (tableLineDrag) {
      placeTableLineNodes(tableLineDrag, 0)
      tableLineDrag = null
    }

    hoveredNodeIndex.value = -1
    resetEdgeHover()
    draggedNodeInfo.polygonIndex = -1
    draggedNodeInfo.nodeIndex = -1
    draggedNodeInfo.isDragging = false
    isInvalidPosition.value = false
    blockedByParentBounds = false
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
    const normalizedViewMode = normalizeLayoutViewMode(rawViewMode)

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

        const isInViewMode = !!normalizeLayoutViewMode(viewMode?.value) && viewMode?.value !== 'default'

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
    clearNodeSelection()
    hoveredPolygonIndex.value = -1
    hoveredNodeIndex.value = -1
    resetEdgeHover()
    draggedNodeInfo.polygonIndex = -1
    draggedNodeInfo.nodeIndex = -1
    draggedNodeInfo.isDragging = false
    isInvalidPosition.value = false
    blockedByParentBounds = false
    dragOriginalPosition.value = null
    justFinishedDragging.value = false
  }

  return {
    hoveredPolygonIndex,
    hoveredNodeIndex,
    selectedNodes,
    selectNodesInRect,
    clearNodeSelection,
    deleteSelectedNodes,
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
