import type { Commander } from '@/commands'
import { MoveElementCommand } from '@/commands'
import type { Point, ImageSize } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline, ViewMode } from '@/types/editor/rendering'
import { getEditorSession } from '@/session/editor/editor-session'
import { getVisiblePolygonAtPoint, getHoverablePolylineAtPoint, isPointInPolygon } from '@/utils/editor/hit-detection'
import { getImageBounds } from '@/utils/editor/coordinates'

export interface MoveInteractionState {
  isMoving: boolean
  elementId: string | null
  elementType: 'polygon' | 'polyline' | null
  startPoint: Point | null
  currentDelta: Point
  isInvalid: boolean
}

export function useMoveInteraction(
  polygons: RenderablePolygon[],
  polylines: RenderablePolyline[],
  constrainToImage: Ref<boolean> | undefined,
  constrainToParent: Ref<boolean> | undefined,
  imageSize: Ref<ImageSize> | undefined,
  moveWithChildren: Ref<boolean> | undefined,
  commander: Commander,
  canvasId: string,
  hiddenPolygonIds?: Ref<string[]>,
  hiddenPolylineIds?: Ref<string[]>,
  viewMode?: Ref<ViewMode | string>
) {
  const getCommandContext = () => {
    const session = getEditorSession(canvasId)
    return session ? { session, canvasId } : undefined
  }

  const state = reactive<MoveInteractionState>({
    isMoving: false,
    elementId: null,
    elementType: null,
    startPoint: null,
    currentDelta: { x: 0, y: 0 },
    isInvalid: false
  })

  let originalPoints: Point[] | null = null
  const childOriginalPoints: Map<string, Point[]> = new Map()

  function isMoving(): boolean {
    return state.isMoving
  }

  function handleMouseDown(point: Point, selectedPolygonIndex: Ref<number>, selectedPolylineIndex: Ref<number>): boolean {
    const hiddenPolygonIdSet = hiddenPolygonIds ? new Set(hiddenPolygonIds.value) : undefined
    const hiddenPolylineIdSet = hiddenPolylineIds ? new Set(hiddenPolylineIds.value) : undefined
    const normalizedViewMode = normalizeViewMode(viewMode?.value)

    const polylineIndex = getHoverablePolylineAtPoint(
      polylines, polygons, point,
      selectedPolygonIndex.value, selectedPolylineIndex.value,
      0.02, undefined, normalizedViewMode,
      hiddenPolygonIdSet, hiddenPolylineIdSet
    )
    if (polylineIndex >= 0) {
      const polyline = polylines[polylineIndex]
      if (polyline) {
        state.isMoving = true
        state.elementId = polyline.id
        state.elementType = 'polyline'
        state.startPoint = { ...point }
        state.currentDelta = { x: 0, y: 0 }
        originalPoints = polyline.points.map(p => ({ ...p }))
        childOriginalPoints.clear()
        return true
      }
    }

    const polygonIndex = getVisiblePolygonAtPoint(
      polygons, point, selectedPolygonIndex.value,
      undefined, normalizedViewMode, hiddenPolygonIdSet
    )
    if (polygonIndex >= 0) {
      const polygon = polygons[polygonIndex]
      if (polygon) {
        state.isMoving = true
        state.elementId = polygon.id
        state.elementType = 'polygon'
        state.startPoint = { ...point }
        state.currentDelta = { x: 0, y: 0 }
        originalPoints = polygon.points.map(p => ({ ...p }))

        childOriginalPoints.clear()
        if (moveWithChildren?.value) {
          storeChildrenPoints(polygon.id)
        }
        return true
      }
    }

    return false
  }

  function storeChildrenPoints(parentId: string): void {
    for (const p of polygons) {
      if (p.parentId === parentId) {
        childOriginalPoints.set(p.id, p.points.map(pt => ({ ...pt })))
        storeChildrenPoints(p.id)
      }
    }
    for (const pl of polylines) {
      if (pl.parentId && childOriginalPoints.has(pl.parentId)) {
        childOriginalPoints.set(pl.id, pl.points.map(pt => ({ ...pt })))
      }
    }
  }

  function handleMouseMove(point: Point): void {
    if (!state.isMoving || !state.startPoint || !originalPoints) return

    const delta = {
      x: point.x - state.startPoint.x,
      y: point.y - state.startPoint.y
    }

    const allPoints: Point[] = [...originalPoints]
    if (moveWithChildren?.value) {
      for (const pts of childOriginalPoints.values()) {
        allPoints.push(...pts)
      }
    }

    let isInvalid = false

    if (constrainToImage?.value && imageSize?.value) {
      const bounds = getImageBounds(imageSize.value)
      if (!isWithinBounds(allPoints, delta, bounds)) {
        isInvalid = true
      }
    }

    if (constrainToParent?.value && state.elementType === 'polygon') {
      const polygon = polygons.find(p => p.id === state.elementId)
      if (polygon?.parentId) {
        const parent = polygons.find(p => p.id === polygon.parentId)
        if (parent && !isWithinParent(originalPoints, delta, parent.points)) {
          isInvalid = true
        }
      }
    }

    state.currentDelta = delta
    state.isInvalid = isInvalid

    if (state.elementType === 'polygon') {
      const polygon = polygons.find(p => p.id === state.elementId)
      if (polygon) {
        polygon.points = originalPoints.map(p => ({
          x: p.x + delta.x,
          y: p.y + delta.y
        }))
      }
      if (moveWithChildren?.value) {
        for (const [childId, childPts] of childOriginalPoints) {
          const childPolygon = polygons.find(p => p.id === childId)
          if (childPolygon) {
            childPolygon.points = childPts.map(p => ({ x: p.x + delta.x, y: p.y + delta.y }))
          }
          const childPolyline = polylines.find(p => p.id === childId)
          if (childPolyline) {
            childPolyline.points = childPts.map(p => ({ x: p.x + delta.x, y: p.y + delta.y }))
          }
        }
      }
    } else if (state.elementType === 'polyline') {
      const polyline = polylines.find(p => p.id === state.elementId)
      if (polyline) {
        polyline.points = originalPoints.map(p => ({
          x: p.x + delta.x,
          y: p.y + delta.y
        }))
      }
    }
  }

  function handleMouseUp(): void {
    if (!state.isMoving || !state.elementId || !state.elementType || !originalPoints) {
      resetState()
      return
    }

    if (state.elementType === 'polygon') {
      const polygon = polygons.find(p => p.id === state.elementId)
      if (polygon) {
        polygon.points = originalPoints.map(p => ({ ...p }))
      }
      if (moveWithChildren?.value) {
        for (const [childId, childPts] of childOriginalPoints) {
          const childPolygon = polygons.find(p => p.id === childId)
          if (childPolygon) {
            childPolygon.points = childPts.map(p => ({ ...p }))
          }
          const childPolyline = polylines.find(p => p.id === childId)
          if (childPolyline) {
            childPolyline.points = childPts.map(p => ({ ...p }))
          }
        }
      }
    } else {
      const polyline = polylines.find(p => p.id === state.elementId)
      if (polyline) {
        polyline.points = originalPoints.map(p => ({ ...p }))
      }
    }

    if (!state.isInvalid && (state.currentDelta.x !== 0 || state.currentDelta.y !== 0)) {
      const cmd = new MoveElementCommand({
        elementId: state.elementId,
        elementType: state.elementType,
        delta: state.currentDelta,
        moveWithChildren: moveWithChildren?.value ?? false
      })
      commander.execute(cmd, getCommandContext())
    }

    resetState()
  }

  function cancelCurrentOperation(): void {
    if (!state.isMoving || !state.elementId || !state.elementType || !originalPoints) {
      resetState()
      return
    }

    if (state.elementType === 'polygon') {
      const polygon = polygons.find(p => p.id === state.elementId)
      if (polygon) {
        polygon.points = originalPoints.map(point => ({ ...point }))
      }
      if (moveWithChildren?.value) {
        for (const [childId, childPts] of childOriginalPoints) {
          const childPolygon = polygons.find(p => p.id === childId)
          if (childPolygon) {
            childPolygon.points = childPts.map(point => ({ ...point }))
          }
          const childPolyline = polylines.find(p => p.id === childId)
          if (childPolyline) {
            childPolyline.points = childPts.map(point => ({ ...point }))
          }
        }
      }
    } else {
      const polyline = polylines.find(p => p.id === state.elementId)
      if (polyline) {
        polyline.points = originalPoints.map(point => ({ ...point }))
      }
    }

    resetState()
  }

  function resetState(): void {
    state.isMoving = false
    state.elementId = null
    state.elementType = null
    state.startPoint = null
    state.currentDelta = { x: 0, y: 0 }
    state.isInvalid = false
    originalPoints = null
    childOriginalPoints.clear()
  }

  function isWithinBounds(points: Point[], delta: Point, bounds: { minX: number, maxX: number, minY: number, maxY: number }): boolean {
    for (const p of points) {
      const newX = p.x + delta.x
      const newY = p.y + delta.y
      if (newX < bounds.minX || newX > bounds.maxX || newY < bounds.minY || newY > bounds.maxY) {
        return false
      }
    }
    return true
  }

  function isWithinParent(points: Point[], delta: Point, parentPoints: Point[]): boolean {
    for (const p of points) {
      const newPoint = { x: p.x + delta.x, y: p.y + delta.y }
      if (!isPointInPolygon(newPoint, parentPoints)) {
        return false
      }
    }
    return true
  }

  function normalizeViewMode(raw: string | undefined): ViewMode | undefined {
    if (raw === 'default' || raw === 'textline' || raw === 'baseline') return raw
    return undefined
  }

  return {
    state,
    isMoving,
    handleMouseDown,
    handleMouseMove,
    handleMouseUp,
    cancelCurrentOperation
  }
}
