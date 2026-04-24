import { getWorldCoordsFromEvent, imageToWorld, worldToClipCoords } from '@/utils/editor/coordinates'
import { getVisiblePolygonAtPoint, getHoverablePolylineAtPoint, isPointInPolygon } from '@/utils/editor/hit-detection'
import { visibilityService } from '@/services/editor/visibility-service'
import { PolygonType, type View, type AspectRatioScale, type Point } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline, ViewMode } from '@/types/editor/rendering'
import { TIMING, VIEW_PADDING, ZOOM } from '@/utils/editor/editor-constants'
import type { EditorStateActions } from './use-editor-state'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import type {
  MouseInteraction,
  PolygonDrawing,
  PolylineDrawing,
  RectangleDrawing,
  PolygonEditing,
  PolylineEditing,
  CanvasControls,
  EditorCommands,
  MarqueeContext
} from './editor-interactions/types'

/**
 * Composable for managing all editor interactions (mouse, keyboard, view controls).
 * Orchestrates interactions between different drawing and editing modes.
 */
export function useEditorInteractions(
  canvas: Ref<HTMLCanvasElement | null>,
  view: View,
  aspectRatioScale: Ref<AspectRatioScale>,
  polygons: RenderablePolygon[],
  polylines: RenderablePolyline[],
  selectedPolygonIndex: Ref<number>,
  selectedPolylineIndex: Ref<number>,
  selectedPolygonIds: Ref<string[]>,
  selectedPolylineIds: Ref<string[]>,
  hiddenPolygonIds: Ref<string[]>,
  hiddenPolylineIds: Ref<string[]>,
  isPolygonMode: Ref<boolean>,
  isRectangleMode: Ref<boolean>,
  isPolylineMode: Ref<boolean>,
  isDrawingMode: Ref<boolean>,
  isMoveMode: Ref<boolean>,
  regionType: Ref<string>,
  mouseInteraction: MouseInteraction,
  polygonDrawing: PolygonDrawing,
  polylineDrawing: PolylineDrawing,
  rectangleDrawing: RectangleDrawing,
  polygonEditing: PolygonEditing,
  polylineEditing: PolylineEditing,
  editorCommands: EditorCommands,
  canvasControls: CanvasControls,
  imageSize: Ref<{ width: number, height: number }>,
  moveInteraction?: {
    isMoving: () => boolean
    handleMouseDown: (point: { x: number, y: number }, selectedPolygonIndex: Ref<number>, selectedPolylineIndex: Ref<number>) => boolean
    handleMouseMove: (point: { x: number, y: number }) => void
    handleMouseUp: () => void
    cancelCurrentOperation?: () => void
  },
  stateActions?: Pick<
    EditorStateActions,
    | 'clearSelectionSet'
    | 'replacePolygonSelection'
    | 'replacePolylineSelection'
    | 'addPolygonSelection'
    | 'addPolylineSelection'
    | 'togglePolygonSelection'
    | 'togglePolylineSelection'
    | 'setHoveredPolygonId'
    | 'setHoveredPolylineId'
  >
) {
  const WORLD_COORD_THRESHOLD = 2.5
  const editorUiStore = useEditorUiStore()
  const hiddenPolygonIdSet = computed(() => new Set(hiddenPolygonIds.value))
  const hiddenPolylineIdSet = computed(() => new Set(hiddenPolylineIds.value))

  const isMarqueeSelecting = ref(false)
  const marqueeRectPx = ref<{ x: number, y: number, width: number, height: number } | null>(null)
  let marqueeStartClient: { x: number, y: number } | null = null
  let marqueeContext: MarqueeContext | null = null

  let pendingShiftMarquee = false
  let pendingShiftStartClient: { x: number, y: number } | null = null

  function isBaselineMode(viewMode: ViewMode | undefined): boolean {
    return viewMode === 'baseline'
  }

  function normalizeViewMode(raw: string | undefined): ViewMode | undefined {
    if (raw === 'default' || raw === 'textline' || raw === 'baseline') return raw
    return undefined
  }

  function pointInRect(p: Point, rect: { minX: number, maxX: number, minY: number, maxY: number }): boolean {
    return p.x >= rect.minX && p.x <= rect.maxX && p.y >= rect.minY && p.y <= rect.maxY
  }

  function allPointsInRect(points: Point[], rect: { minX: number, maxX: number, minY: number, maxY: number }): boolean {
    if (!points || points.length === 0) return false
    for (const pt of points) {
      if (!pointInRect(pt, rect)) return false
    }
    return true
  }

  function startMarqueeFromStartPoint(startClient: { x: number, y: number }): void {
    if (!canvas.value) return
    const rect = canvas.value.getBoundingClientRect()
    marqueeStartClient = { x: startClient.x, y: startClient.y }
    isMarqueeSelecting.value = true
    marqueeRectPx.value = { x: startClient.x - rect.left, y: startClient.y - rect.top, width: 0, height: 0 }

    const rawViewMode = canvasControls.viewMode?.value
    const viewMode = normalizeViewMode(rawViewMode)

    const effectiveSelectedPolygonIndex = isBaselineMode(viewMode) ? -1 : selectedPolygonIndex.value

    marqueeContext = {
      selectedPolygonIndex: effectiveSelectedPolygonIndex,
      selectedPolylineIndex: -1,
      viewMode
    }

    mouseInteraction.actionState.action = 'drag'
  }

  function updateMarquee(e: MouseEvent): void {
    if (!isMarqueeSelecting.value || !canvas.value || !marqueeStartClient) return
    const rect = canvas.value.getBoundingClientRect()
    const x1 = marqueeStartClient.x - rect.left
    const y1 = marqueeStartClient.y - rect.top
    const x2 = e.clientX - rect.left
    const y2 = e.clientY - rect.top
    const left = Math.min(x1, x2)
    const top = Math.min(y1, y2)
    const width = Math.abs(x2 - x1)
    const height = Math.abs(y2 - y1)
    marqueeRectPx.value = { x: left, y: top, width, height }
  }

  function finishMarquee(e: MouseEvent): void {
    if (!isMarqueeSelecting.value || !canvas.value || !marqueeStartClient || !marqueeContext) {
      isMarqueeSelecting.value = false
      marqueeRectPx.value = null
      marqueeStartClient = null
      marqueeContext = null
      return
    }

    const canvasEl = canvas.value as HTMLCanvasElement
    const startEvent = new MouseEvent('mousemove', { clientX: marqueeStartClient.x, clientY: marqueeStartClient.y })
    const startWorld = getWorldCoordsFromEvent(startEvent, canvasEl, view, aspectRatioScale.value)
    const endWorld = getWorldCoordsFromEvent(e, canvasEl, view, aspectRatioScale.value)
    const worldRect = {
      minX: Math.min(startWorld.x, endWorld.x),
      maxX: Math.max(startWorld.x, endWorld.x),
      minY: Math.min(startWorld.y, endWorld.y),
      maxY: Math.max(startWorld.y, endWorld.y)
    }

    const viewMode = marqueeContext.viewMode

    if (isBaselineMode(viewMode)) {
      const polylineIds: string[] = []
      for (const line of polylines) {
        if (!line?.id || !line.points) continue

        if (hiddenPolylineIdSet.value.has(line.id)) continue

        const selectable = visibilityService.shouldShowPolylineHover(line, {
          selectedPolygonIndex: marqueeContext.selectedPolygonIndex,
          selectedPolylineIndex: marqueeContext.selectedPolylineIndex,
          allPolygons: polygons,
          allPolylines: polylines,
          viewMode,
          hiddenPolygonIds: hiddenPolygonIdSet.value,
          hiddenPolylineIds: hiddenPolylineIdSet.value
        })
        if (!selectable) continue
        if (allPointsInRect(line.points, worldRect)) {
          polylineIds.push(line.id)
        }
      }

      if (stateActions?.addPolylineSelection) {
        stateActions.addPolylineSelection(polylineIds)
      } else {
        selectedPolygonIds.value = []
        selectedPolylineIds.value = Array.from(new Set([...selectedPolylineIds.value, ...polylineIds]))
      }
    } else {
      const polygonIds: string[] = []
      for (const poly of polygons) {
        if (!poly?.id || !poly.points) continue

        if (hiddenPolygonIdSet.value.has(poly.id)) continue

        const selectable = visibilityService.shouldBeSelectablePolygon(poly, {
          selectedPolygonIndex: marqueeContext.selectedPolygonIndex,
          allPolygons: polygons,
          viewMode,
          hiddenPolygonIds: hiddenPolygonIdSet.value,
          hiddenPolylineIds: hiddenPolylineIdSet.value
        })
        if (!selectable) continue
        if (allPointsInRect(poly.points, worldRect)) {
          polygonIds.push(poly.id)
        }
      }

      if (stateActions?.addPolygonSelection) {
        stateActions.addPolygonSelection(polygonIds)
      } else {
        selectedPolylineIds.value = []
        selectedPolygonIds.value = Array.from(new Set([...selectedPolygonIds.value, ...polygonIds]))
      }
    }

    isMarqueeSelecting.value = false
    marqueeRectPx.value = null
    marqueeStartClient = null
    marqueeContext = null
  }

  function clearSelectionSetOnly(): void {
    if (stateActions?.clearSelectionSet) {
      stateActions.clearSelectionSet()
    } else {
      selectedPolygonIds.value = []
      selectedPolylineIds.value = []
    }
  }

  function togglePolygonIdInSelection(polygonId: string): void {
    if (stateActions?.togglePolygonSelection) {
      stateActions.togglePolygonSelection(polygonId)
    } else {
      const set = new Set(selectedPolygonIds.value)
      if (set.has(polygonId)) set.delete(polygonId)
      else set.add(polygonId)
      selectedPolylineIds.value = []
      selectedPolygonIds.value = Array.from(set)
    }
  }

  function togglePolylineIdInSelection(polylineId: string): void {
    if (stateActions?.togglePolylineSelection) {
      stateActions.togglePolylineSelection(polylineId)
    } else {
      const set = new Set(selectedPolylineIds.value)
      if (set.has(polylineId)) set.delete(polylineId)
      else set.add(polylineId)
      selectedPolygonIds.value = []
      selectedPolylineIds.value = Array.from(set)
    }
  }

  function replaceSelectionFromCurrentIndices(viewMode: ViewMode | undefined): void {
    if (isBaselineMode(viewMode)) {
      if (selectedPolylineIndex.value >= 0) {
        const id = polylines[selectedPolylineIndex.value]?.id
        if (!id) {
          clearSelectionSetOnly()
          return
        }
        if (stateActions?.replacePolylineSelection) stateActions.replacePolylineSelection([id])
        else {
          selectedPolygonIds.value = []
          selectedPolylineIds.value = [id]
        }
      } else {
        clearSelectionSetOnly()
      }
      return
    }

    if (selectedPolygonIndex.value >= 0) {
      const id = polygons[selectedPolygonIndex.value]?.id
      if (!id) {
        clearSelectionSetOnly()
        return
      }
      if (stateActions?.replacePolygonSelection) stateActions.replacePolygonSelection([id])
      else {
        selectedPolylineIds.value = []
        selectedPolygonIds.value = [id]
      }
    } else {
      clearSelectionSetOnly()
    }
  }

  function drillUpOrDeselect(viewMode: ViewMode | undefined): void {
    clearSelectionSetOnly()

    if (isBaselineMode(viewMode) && selectedPolylineIndex.value < 0) {
      selectedPolygonIndex.value = -1
      replaceSelectionFromCurrentIndices(viewMode)
      return
    }

    if (selectedPolylineIndex.value >= 0) {
      const selected = polylines[selectedPolylineIndex.value]
      if (isBaselineMode(viewMode)) {
        selectedPolylineIndex.value = -1
        selectedPolygonIndex.value = -1
        replaceSelectionFromCurrentIndices(viewMode)
        return
      }

      selectedPolylineIndex.value = -1
      if (selected?.parentId) {
        const parentIndex = polygons.findIndex(p => p.id === selected.parentId)
        selectedPolygonIndex.value = parentIndex >= 0 ? parentIndex : -1
      } else {
        selectedPolygonIndex.value = -1
      }
      replaceSelectionFromCurrentIndices(viewMode)
      return
    }

    if (selectedPolygonIndex.value >= 0) {
      const selected = polygons[selectedPolygonIndex.value]
      if (selected?.parentId) {
        const parentIndex = polygons.findIndex(p => p.id === selected.parentId)
        selectedPolygonIndex.value = parentIndex >= 0 ? parentIndex : -1
      } else {
        selectedPolygonIndex.value = -1
      }
      selectedPolylineIndex.value = -1
      replaceSelectionFromCurrentIndices(viewMode)
      return
    }
  }

  function onWheel(e: WheelEvent): void {
    mouseInteraction.handleWheel(e, canvas.value, aspectRatioScale.value)
  }

  function isShiftPanDrawingModeActive(): boolean {
    return isPolygonMode.value
      || isRectangleMode.value
      || isPolylineMode.value
      || Boolean(canvasControls.isCutMode?.value)
  }

  function onMouseDown(e: MouseEvent): void {
    if (e.button !== 0) return // Only main left-click

    mouseInteraction.handleMouseDown(e)

    stateActions?.setHoveredPolygonId(null)
    stateActions?.setHoveredPolylineId(null)

    if (e.shiftKey && isShiftPanDrawingModeActive()) {
      return
    }

    if (canvasControls.isCutMode?.value && canvasControls.cutDrawing) {
      if (!canvas.value) return
      const cutMode = canvasControls.isCutLineMode?.value
        ? 'line'
        : canvasControls.isCutPolygonMode?.value
          ? 'polygon'
          : 'rectangle'
      canvasControls.cutDrawing.handleMouseDown(
        e,
        getWorldCoordsFromEvent,
        canvas.value,
        aspectRatioScale.value,
        cutMode
      )
      return
    }

    if (isRectangleMode.value) {
      const handled = rectangleDrawing.handleMouseDown(e, getWorldCoordsFromEvent, canvas.value, view, aspectRatioScale.value)
      if (handled) return // Prevent further processing if rectangle was handled
    } else if (isPolylineMode.value) {
      const pointAdded = polylineDrawing.handleMouseDown(e, getWorldCoordsFromEvent, canvas.value, aspectRatioScale.value)
      if (!pointAdded) return // Prevent further processing if point was invalid
    } else if (isPolygonMode.value) {
      const pointAdded = polygonDrawing.handleMouseDown(e, getWorldCoordsFromEvent, canvas.value, aspectRatioScale.value)
      if (!pointAdded) return // Prevent further processing if point was invalid
    } else if (isMoveMode.value && moveInteraction) {
      if (!canvas.value) return
      const point = getWorldCoordsFromEvent(e, canvas.value, view, aspectRatioScale.value)
      const moveStarted = moveInteraction.handleMouseDown(point, selectedPolygonIndex, selectedPolylineIndex)
      if (moveStarted) return
    } else {
      if (!canvas.value) return

      if (e.shiftKey) {
        pendingShiftMarquee = true
        pendingShiftStartClient = { x: e.clientX, y: e.clientY }
        return
      }

      if (editorUiStore.relationsEditor.pickerMode !== 'idle') {
        return
      }

      clearSelectionSetOnly()

      const point = getWorldCoordsFromEvent(e, canvas.value, view, aspectRatioScale.value)

      const polylineEditingStarted = polylineEditing.handleMouseDown(point, selectedPolygonIndex, canvas.value)
      if (polylineEditingStarted) return

      const polygonEditingStarted = polygonEditing.handleMouseDown(point, selectedPolygonIndex, canvas.value)
      if (polygonEditingStarted) return
    }
  }

  function onMouseMove(e: MouseEvent): void {
    mouseInteraction.handleMouseMove(e)

    const shiftPanIntentInDrawingMode = e.shiftKey
      && isShiftPanDrawingModeActive()
      && mouseInteraction.actionState.startPosition !== null
    const keepShiftPanDragging = isShiftPanDrawingModeActive() && mouseInteraction.isPanning()

    if (shiftPanIntentInDrawingMode || keepShiftPanDragging) {
      const isDragging = polylineEditing.isDragging() || polygonEditing.isDragging()
      const shouldPan = e.shiftKey && mouseInteraction.shouldStartPanning(e, isDragging)

      if (shouldPan) {
        mouseInteraction.startPanning(e)
      }

      if (mouseInteraction.isPanning()) {
        mouseInteraction.updatePanning(e, canvas.value, aspectRatioScale.value)
      }

      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
      return
    }

    if (pendingShiftMarquee && pendingShiftStartClient) {
      if (mouseInteraction.hasExceededMovementThreshold(e)) {
        startMarqueeFromStartPoint(pendingShiftStartClient)
        pendingShiftMarquee = false
        pendingShiftStartClient = marqueeStartClient
      } else {
        return
      }
    }

    if (isMarqueeSelecting.value) {
      updateMarquee(e)
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
      return
    }

    if (canvasControls.isCutMode?.value && canvasControls.cutDrawing) {
      if (!canvas.value) return
      const cutMode = canvasControls.isCutLineMode?.value
        ? 'line'
        : canvasControls.isCutPolygonMode?.value
          ? 'polygon'
          : 'rectangle'
      canvasControls.cutDrawing.handleMouseMove(
        e,
        getWorldCoordsFromEvent,
        canvas.value,
        aspectRatioScale.value,
        cutMode
      )
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
      return
    }

    if (isRectangleMode.value) {
      rectangleDrawing.handleMouseMove(e, getWorldCoordsFromEvent, canvas.value, view, aspectRatioScale.value)
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
    } else if (isPolylineMode.value) {
      polylineDrawing.handleMouseMove(e, getWorldCoordsFromEvent, canvas.value, aspectRatioScale.value)
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
    } else if (isPolygonMode.value) {
      polygonDrawing.handleMouseMove(e, getWorldCoordsFromEvent, canvas.value, aspectRatioScale.value)
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
    } else if (isMoveMode.value && moveInteraction?.isMoving()) {
      if (!canvas.value) return
      const point = getWorldCoordsFromEvent(e, canvas.value, view, aspectRatioScale.value)
      moveInteraction.handleMouseMove(point)
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
    } else {
      if (!canvas.value) return
      const point = getWorldCoordsFromEvent(e, canvas.value as HTMLCanvasElement, view, aspectRatioScale.value)

      if (polylineEditing.isDragging()) {
        polylineEditing.handleMouseMove(point)
        stateActions?.setHoveredPolygonId(null)
        stateActions?.setHoveredPolylineId(null)
      } else if (polygonEditing.isDragging()) {
        polygonEditing.handleMouseMove(point)
        stateActions?.setHoveredPolygonId(null)
        stateActions?.setHoveredPolylineId(null)
      } else {
        polylineEditing.updateHoverStates(point, selectedPolygonIndex)
        polygonEditing.updateHoverStates(point, selectedPolygonIndex)

        const isDragging = polylineEditing.isDragging() || polygonEditing.isDragging()
        const shouldPan = mouseInteraction.shouldStartPanning(e, isDragging)

        if (shouldPan) {
          mouseInteraction.startPanning(e)
          stateActions?.setHoveredPolygonId(null)
          stateActions?.setHoveredPolylineId(null)
        }

        if (mouseInteraction.isPanning()) {
          mouseInteraction.updatePanning(e, canvas.value, aspectRatioScale.value)
          stateActions?.setHoveredPolygonId(null)
          stateActions?.setHoveredPolylineId(null)
        } else {
          const hoveredPolylineIdx = polylineEditing.hoveredPolylineIndex.value
          const hoveredPolygonIdx = polygonEditing.hoveredPolygonIndex.value

          if (hoveredPolylineIdx >= 0 && polylines[hoveredPolylineIdx]) {
            stateActions?.setHoveredPolylineId(polylines[hoveredPolylineIdx].id)
            stateActions?.setHoveredPolygonId(null)
          } else if (hoveredPolygonIdx >= 0 && polygons[hoveredPolygonIdx]) {
            stateActions?.setHoveredPolygonId(polygons[hoveredPolygonIdx].id)
            stateActions?.setHoveredPolylineId(null)
          } else {
            stateActions?.setHoveredPolygonId(null)
            stateActions?.setHoveredPolylineId(null)
          }
        }
      }
    }
  }

  function onMouseUp(e: MouseEvent): void {
    const rawViewMode = canvasControls.viewMode?.value
    const normalizedViewMode: ViewMode | undefined = normalizeViewMode(rawViewMode)

    if (isMarqueeSelecting.value) {
      finishMarquee(e)
      mouseInteraction.handleMouseUp(e)
      pendingShiftMarquee = false
      pendingShiftStartClient = null
      return
    }

    if (pendingShiftMarquee && pendingShiftStartClient && !mouseInteraction.hasMoved()) {
      if (!canvas.value) {
        pendingShiftMarquee = false
        pendingShiftStartClient = null
        return
      }

      const point = getWorldCoordsFromEvent(
        e,
        canvas.value,
        view,
        aspectRatioScale.value
      )

      if (isBaselineMode(normalizedViewMode)) {
        const clickedPolylineIndex = getHoverablePolylineAtPoint(
          polylines,
          polygons,
          point,
          -1,
          -1,
          0.02,
          undefined,
          normalizedViewMode
        )

        if (clickedPolylineIndex >= 0) {
          const clicked = polylines[clickedPolylineIndex]
          if (clicked?.id) togglePolylineIdInSelection(clicked.id)
        } else {
          clearSelectionSetOnly()
        }
      } else {
        const clickedPolygonIndex = getVisiblePolygonAtPoint(
          polygons,
          point,
          selectedPolygonIndex.value,
          undefined,
          normalizedViewMode
        )

        if (clickedPolygonIndex >= 0) {
          const clicked = polygons[clickedPolygonIndex]
          if (clicked?.id) togglePolygonIdInSelection(clicked.id)
        } else {
          clearSelectionSetOnly()
        }
      }

      mouseInteraction.handleMouseUp(e)
      pendingShiftMarquee = false
      pendingShiftStartClient = null
      return
    }

    pendingShiftMarquee = false
    pendingShiftStartClient = null

    const startPosition = mouseInteraction.actionState.startPosition
      ? { ...mouseInteraction.actionState.startPosition }
      : null
    const hasMoved = mouseInteraction.hasMoved()

    mouseInteraction.endPanning()

    if (isMoveMode.value && moveInteraction?.isMoving()) {
      moveInteraction.handleMouseUp()
      mouseInteraction.handleMouseUp(e)
      return
    }

    polylineEditing.handleMouseUp(canvas.value)

    polygonEditing.handleMouseUp(canvas.value)

    mouseInteraction.handleMouseUp(e)

    if (!hasMoved && !isDrawingMode.value
      && !polygonEditing.justFinishedDragging.value && !polylineEditing.justFinishedDragging.value
      && startPosition) {
      const fakeEvent = {
        clientX: startPosition.x,
        clientY: startPosition.y,
        button: 0
      }

      if (!canvas.value) return

      const mouseEvent = new MouseEvent('click', {
        clientX: fakeEvent.clientX,
        clientY: fakeEvent.clientY,
        button: fakeEvent.button
      })

      const point = getWorldCoordsFromEvent(
        mouseEvent,
        canvas.value,
        view,
        aspectRatioScale.value
      )

      const isDrawing = isPolygonMode.value || isRectangleMode.value || isPolylineMode.value
        || Boolean(canvasControls.isCutMode?.value)

      if (editorUiStore.relationsEditor.pickerMode !== 'idle') {
        const clickedPolygonIndex = getVisiblePolygonAtPoint(
          polygons,
          point,
          -1,
          undefined,
          undefined,
          hiddenPolygonIdSet.value
        )

        if (clickedPolygonIndex >= 0) {
          const clickedPolygon = polygons[clickedPolygonIndex]
          if (clickedPolygon?.type === PolygonType.REGION) {
            if (editorUiStore.relationsEditor.pickerMode === 'pick-source') {
              editorUiStore.updateRelationDraft({
                sourceRegionRef: clickedPolygon.id,
                targetRegionRef: ''
              })
              editorUiStore.setRelationPickerMode('pick-target')
            } else if (editorUiStore.relationsEditor.pickerMode === 'pick-target') {
              editorUiStore.updateRelationDraft({
                targetRegionRef: clickedPolygon.id
              })
            } else {
              editorUiStore.setRelationPickerRegionId(clickedPolygon.id)
            }
          }
        }

        setTimeout(() => {
          polygonEditing.resetDragCompletionFlag()
          polylineEditing.resetDragCompletionFlag()
        }, TIMING.DRAG_COMPLETION_DELAY)
        return
      }

      const clickedPolylineIndex = getHoverablePolylineAtPoint(
        polylines,
        polygons,
        point,
        selectedPolygonIndex.value,
        selectedPolylineIndex.value,
        0.02,
        undefined,
        normalizedViewMode,
        hiddenPolygonIdSet.value,
        hiddenPolylineIdSet.value
      )

      if (clickedPolylineIndex >= 0) {
        polylineEditing.handleSelection(point, selectedPolygonIndex, isDrawing)
      } else if (selectedPolylineIndex.value >= 0) {
        polylineEditing.handleSelection(point, selectedPolygonIndex, isDrawing)
      } else {
        polygonEditing.handleSelection(point, selectedPolygonIndex, isDrawing)
      }

      replaceSelectionFromCurrentIndices(normalizedViewMode)
    }

    setTimeout(() => {
      polygonEditing.resetDragCompletionFlag()
      polylineEditing.resetDragCompletionFlag()
    }, TIMING.DRAG_COMPLETION_DELAY)
  }

  function onMouseLeave(): void {
    mouseInteraction.handleMouseLeave()
    stateActions?.setHoveredPolygonId(null)
    stateActions?.setHoveredPolylineId(null)
    onMouseUp(new MouseEvent('mouseup'))
  }

  function onDoubleClick(e: MouseEvent): void {
    if (canvasControls.isCutLineMode?.value && canvasControls.cutDrawing && canvasControls.cutDrawing.isActive()) {
      e.preventDefault()
      canvasControls.cutDrawing.handleDoubleClick(e, 'line')
      return
    }
    if (canvasControls.isCutPolygonMode?.value && canvasControls.cutDrawing && canvasControls.cutDrawing.isActive()) {
      e.preventDefault()
      canvasControls.cutDrawing.handleDoubleClick(e, 'polygon')
      return
    }

    if (isPolylineMode.value && polylineDrawing.isActive()) {
      e.preventDefault()
      polylineDrawing.handleDoubleClick(e)
    } else if (isPolygonMode.value && polygonDrawing.isActive()) {
      e.preventDefault()
      polygonDrawing.handleDoubleClick(e)
    }
  }

  function handleCanvasContextMenu(event: MouseEvent): void {
    if (isDrawingMode.value) {
      event.preventDefault()
      return
    }

    mouseInteraction.handleContextMenu(event, { preventDefault: false })

    if (!canvas.value) return
    const point = getWorldCoordsFromEvent(event, canvas.value, view, aspectRatioScale.value)

    const rawViewMode = canvasControls.viewMode?.value
    const normalizedViewMode: ViewMode | undefined = normalizeViewMode(rawViewMode)

    const clickedPolylineIndex = getHoverablePolylineAtPoint(
      polylines,
      polygons,
      point,
      selectedPolygonIndex.value,
      selectedPolylineIndex.value,
      0.02,
      undefined,
      normalizedViewMode,
      hiddenPolygonIdSet.value,
      hiddenPolylineIdSet.value
    )

    if (clickedPolylineIndex >= 0) {
      const clickedPolyline = polylines[clickedPolylineIndex]
      if (clickedPolyline) {
        editorCommands.showContextMenuForPolyline(event, clickedPolyline)
        return
      }
    }

    const clickedPolygonIndex = getVisiblePolygonAtPoint(
      polygons,
      point,
      selectedPolygonIndex.value,
      undefined,
      normalizedViewMode,
      hiddenPolygonIdSet.value
    )

    if (clickedPolygonIndex >= 0) {
      const clickedPolygon = polygons[clickedPolygonIndex]
      if (clickedPolygon) {
        const isSelectable = visibilityService.shouldBeSelectablePolygon(clickedPolygon, {
          selectedPolygonIndex: selectedPolygonIndex.value,
          allPolygons: polygons,
          viewMode: normalizedViewMode,
          hiddenPolygonIds: hiddenPolygonIdSet.value,
          hiddenPolylineIds: hiddenPolylineIdSet.value
        })

        const isSelectedPolygon = clickedPolygonIndex === selectedPolygonIndex.value

        if (isSelectable || isSelectedPolygon) {
          editorCommands.showContextMenuForPolygon(event, clickedPolygon)
          return
        }
      }
    }

    if (selectedPolylineIndex.value >= 0) {
      const selectedPolyline = polylines[selectedPolylineIndex.value]
      if (selectedPolyline && selectedPolyline.parentId) {
        const parentPolygon = polygons.find(p => p.id === selectedPolyline.parentId)
        if (parentPolygon && isPointInPolygon(point, parentPolygon.points)) {
          editorCommands.showContextMenuForPolyline(event, selectedPolyline)
          return
        }
      }
    }

    if (selectedPolygonIndex.value >= 0) {
      const selectedPolygon = polygons[selectedPolygonIndex.value]
      if (selectedPolygon && isPointInPolygon(point, selectedPolygon.points)) {
        editorCommands.showContextMenuForPolygon(event, selectedPolygon)
        return
      }
    }
  }

  function resetMarqueeState(): void {
    isMarqueeSelecting.value = false
    marqueeRectPx.value = null
    marqueeStartClient = null
    marqueeContext = null
    pendingShiftMarquee = false
    pendingShiftStartClient = null
  }

  function cancelActiveOperation(): boolean {
    if (editorUiStore.relationsEditor.pickerMode !== 'idle') {
      editorUiStore.cancelRelationPicking()
      return true
    }

    if (isMarqueeSelecting.value || pendingShiftMarquee) {
      resetMarqueeState()
      mouseInteraction.endPanning()
      mouseInteraction.resetActionState()
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
      return true
    }

    if (moveInteraction?.isMoving()) {
      moveInteraction.cancelCurrentOperation?.()
      mouseInteraction.endPanning()
      mouseInteraction.resetActionState()
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
      return true
    }

    if (polygonEditing.isDragging()) {
      polygonEditing.cancelCurrentOperation()
      mouseInteraction.endPanning()
      mouseInteraction.resetActionState()
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
      return true
    }

    if (polylineEditing.isDragging()) {
      polylineEditing.cancelCurrentOperation()
      mouseInteraction.endPanning()
      mouseInteraction.resetActionState()
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
      return true
    }

    let cancelledDrawing = false

    if (polygonDrawing.isActive()) {
      polygonDrawing.cancelPolygonCreation()
      cancelledDrawing = true
    } else if (rectangleDrawing.isActive()) {
      rectangleDrawing.clearDrawing()
      cancelledDrawing = true
    } else if (polylineDrawing.isActive()) {
      polylineDrawing.clearDrawing()
      cancelledDrawing = true
    } else if (canvasControls.cutDrawing?.isActive()) {
      canvasControls.cutDrawing.clearDrawing()
      cancelledDrawing = true
    }

    if (cancelledDrawing) {
      mouseInteraction.endPanning()
      mouseInteraction.resetActionState()
      stateActions?.setHoveredPolygonId(null)
      stateActions?.setHoveredPolylineId(null)
      return true
    }

    if (mouseInteraction.isPanning()) {
      mouseInteraction.endPanning()
      mouseInteraction.resetActionState()
      return true
    }

    return false
  }

  function onKeyDown(e: KeyboardEvent): void {
    if (e.key === 'Escape') {
      e.preventDefault()

      if (cancelActiveOperation()) {
        return
      }

      const rawViewMode = canvasControls.viewMode?.value
      const normalizedViewMode: ViewMode | undefined = normalizeViewMode(rawViewMode)

      drillUpOrDeselect(normalizedViewMode)
      return
    }

    if (e.ctrlKey || e.metaKey) {
      if (e.key === 'z' && polygonDrawing.isActive()) {
        e.preventDefault()
        e.stopImmediatePropagation()
        e.stopPropagation()
        polygonDrawing.undoPolygonCreation()
      } else if (e.key === 'y' && polygonDrawing.isActive()) {
        e.preventDefault()
        e.stopImmediatePropagation()
        e.stopPropagation()
        polygonDrawing.redoPolygonCreation()
      }
      return
    }
  }

  /**
   * Calculate the bounding box of a polygon in world coordinates.
   */
  function calculatePolygonBounds(polygon: RenderablePolygon) {
    if (!polygon.points || polygon.points.length === 0) {
      return { minX: 0, maxX: 0, minY: 0, maxY: 0 }
    }

    let minX = polygon.points[0]!.x
    let maxX = polygon.points[0]!.x
    let minY = polygon.points[0]!.y
    let maxY = polygon.points[0]!.y

    for (const point of polygon.points) {
      minX = Math.min(minX, point.x)
      maxX = Math.max(maxX, point.x)
      minY = Math.min(minY, point.y)
      maxY = Math.max(maxY, point.y)
    }

    return { minX, maxX, minY, maxY }
  }

  /**
   * Center and zoom the view to fit a polygon comfortably.
   * Note: Polygon points are stored in pixel coordinates. View offset/zoom operate in normalized clip space.
   * The shader transformation is: screenClipPos = (pixelPos * zoom + offset) * aspectScale
   * Where offset is in normalized clip space (roughly -1 to 1 range).
   */
  function centerViewOnPolygon(polygon: RenderablePolygon): void {
    if (!polygon || !polygon.points || polygon.points.length === 0) return
    if (!canvas.value || !imageSize.value || imageSize.value.width === 0 || imageSize.value.height === 0) return

    const bounds = calculatePolygonBounds(polygon)

    const centerXPixels = (bounds.minX + bounds.maxX) / 2
    const centerYPixels = (bounds.minY + bounds.maxY) / 2

    const widthPixels = bounds.maxX - bounds.minX
    const heightPixels = bounds.maxY - bounds.minY

    const targetWidth = Math.max(widthPixels * (1 + VIEW_PADDING.POLYGON), VIEW_PADDING.MIN_SIZE_PIXELS)
    const targetHeight = Math.max(heightPixels * (1 + VIEW_PADDING.POLYGON), VIEW_PADDING.MIN_SIZE_PIXELS)

    const canvasElement = canvas.value as HTMLCanvasElement
    const viewportWidth = canvasElement.clientWidth
    const viewportHeight = canvasElement.clientHeight

    const zoomX = (viewportWidth / targetWidth) / aspectRatioScale.value.scaleX
    const zoomY = (viewportHeight / targetHeight) / aspectRatioScale.value.scaleY
    const newZoom = Math.min(zoomX, zoomY, 2.0) // Cap zoom at 2.0 to always show context

    const offsetX = -(centerXPixels * newZoom)
    const offsetY = -(centerYPixels * newZoom)

    mouseInteraction.setView({
      zoom: newZoom,
      offsetX: offsetX,
      offsetY: offsetY
    })
  }

  /**
   * Center and zoom the view so the polygon width fits the viewport width.
   * Used by canvas text correction to focus a textline without continuously forcing zoom.
   */
  function centerViewOnPolygonFitWidth(polygon: RenderablePolygon): void {
    if (!polygon || !polygon.points || polygon.points.length === 0) return
    if (!canvas.value || !imageSize.value || imageSize.value.width === 0 || imageSize.value.height === 0) return

    const toWorldPoint = (point: { x: number, y: number }): { x: number, y: number } => {
      const looksLikeWorldCoords = Math.abs(point.x) <= WORLD_COORD_THRESHOLD
        && Math.abs(point.y) <= WORLD_COORD_THRESHOLD
      return looksLikeWorldCoords ? point : imageToWorld(point, imageSize.value)
    }

    const worldPoints = polygon.points.map(toWorldPoint)
    if (worldPoints.length === 0) return

    let minX = Infinity
    let maxX = -Infinity
    let minY = Infinity
    let maxY = -Infinity
    for (const point of worldPoints) {
      minX = Math.min(minX, point.x)
      maxX = Math.max(maxX, point.x)
      minY = Math.min(minY, point.y)
      maxY = Math.max(maxY, point.y)
    }

    const canvasElement = canvas.value as HTMLCanvasElement
    const canvasRect = canvasElement.getBoundingClientRect()
    const canvasWidthPx = canvasRect.width
    if (!Number.isFinite(canvasWidthPx) || canvasWidthPx <= 0) return

    const sidebars = [
      document.querySelector<HTMLElement>('[data-tour="editor-left-sidebar"]'),
      document.querySelector<HTMLElement>('[data-tour="editor-right-sidebar"]')
    ].filter((sidebar): sidebar is HTMLElement => Boolean(sidebar))

    const occludedWidthPx = sidebars.reduce((sum, sidebar) => {
      const rect = sidebar.getBoundingClientRect()
      const intersectsVertically = rect.bottom > canvasRect.top && rect.top < canvasRect.bottom
      if (!intersectsVertically) return sum
      const overlapLeft = Math.max(canvasRect.left, rect.left)
      const overlapRight = Math.min(canvasRect.right, rect.right)
      const overlapWidth = Math.max(0, overlapRight - overlapLeft)
      return sum + overlapWidth
    }, 0)

    const horizontalPaddingPx = 24
    const availableWidthPx = Math.max(1, canvasWidthPx - occludedWidthPx - horizontalPaddingPx)

    let minScreenX = Infinity
    let maxScreenX = -Infinity
    for (const point of worldPoints) {
      const clipPoint = worldToClipCoords(point, view, aspectRatioScale.value)
      if (!Number.isFinite(clipPoint.x)) continue
      const screenX = ((clipPoint.x + 1) / 2) * canvasWidthPx
      minScreenX = Math.min(minScreenX, screenX)
      maxScreenX = Math.max(maxScreenX, screenX)
    }

    const currentWidthPx = Math.max(1, maxScreenX - minScreenX)
    const targetWidthPx = Math.max(1, availableWidthPx * 0.9)
    const fitWidthZoom = view.zoom * (targetWidthPx / currentWidthPx)
    const newZoom = Math.min(Math.max(fitWidthZoom, ZOOM.MIN), ZOOM.MAX)

    mouseInteraction.setView({
      zoom: newZoom,
      offsetX: -(((minX + maxX) / 2) * newZoom),
      offsetY: -(((minY + maxY) / 2) * newZoom)
    })
  }

  /**
   * Center and zoom the view to fit a polyline (baseline) comfortably.
   * Note: Polyline points are stored in pixel coordinates. View offset/zoom operate in normalized clip space.
   */
  function centerViewOnPolyline(polyline: RenderablePolyline): void {
    if (!polyline || !polyline.points || polyline.points.length === 0) return
    if (!canvas.value || !imageSize.value || imageSize.value.width === 0 || imageSize.value.height === 0) return

    let minX = polyline.points[0]!.x
    let maxX = polyline.points[0]!.x
    let minY = polyline.points[0]!.y
    let maxY = polyline.points[0]!.y

    for (const point of polyline.points) {
      minX = Math.min(minX, point.x)
      maxX = Math.max(maxX, point.x)
      minY = Math.min(minY, point.y)
      maxY = Math.max(maxY, point.y)
    }

    const centerXPixels = (minX + maxX) / 2
    const centerYPixels = (minY + maxY) / 2

    const widthPixels = maxX - minX
    const heightPixels = maxY - minY

    const targetWidth = Math.max(widthPixels * (1 + VIEW_PADDING.BASELINE), VIEW_PADDING.MIN_SIZE_PIXELS)
    const targetHeight = Math.max(heightPixels * (1 + VIEW_PADDING.BASELINE), VIEW_PADDING.MIN_SIZE_PIXELS)

    const canvasElement = canvas.value as HTMLCanvasElement
    const viewportWidth = canvasElement.clientWidth
    const viewportHeight = canvasElement.clientHeight

    const zoomX = (viewportWidth / targetWidth) / aspectRatioScale.value.scaleX
    const zoomY = (viewportHeight / targetHeight) / aspectRatioScale.value.scaleY
    const newZoom = Math.min(zoomX, zoomY, 5.0) // Cap zoom at 5.0

    const offsetX = -(centerXPixels * newZoom)
    const offsetY = -(centerYPixels * newZoom)

    mouseInteraction.setView({
      zoom: newZoom,
      offsetX: offsetX,
      offsetY: offsetY
    })
  }

  return {
    onWheel,
    onMouseDown,
    onMouseMove,
    onMouseUp,
    onMouseLeave,
    onDoubleClick,
    handleCanvasContextMenu,

    onKeyDown,

    centerViewOnPolygon,
    centerViewOnPolygonFitWidth,
    centerViewOnPolyline,

    isMarqueeSelecting,
    marqueeRectPx
  }
}
