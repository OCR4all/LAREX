import type { Commander } from '@/commands'
import { CreatePolygonCommand, CreateTextlineAutoParentCommand } from '@/commands'
import { PolygonType } from '@/models/editor'
import type { Point, View, ImageSize, AspectRatioScale } from '@/models/editor'
import type { RenderablePolygon, PreviewPoint } from '@/types/editor/rendering'
import { getEditorSession } from '@/session/editor/editor-session'
import { wouldNewVertexSelfIntersect, isClosedPolygonSelfIntersecting } from '@/utils/editor/hit-detection'
import { isPointWithinImageBounds, clampToWorldBounds, getImageBounds } from '@/utils/editor/coordinates'
import { findParentPolygon, isPointWithinParentBounds } from '@/utils/editor/parent-constraint-utils'
import { validatePolygonParent } from '@/utils/editor/hierarchy-validation'
import type { MouseInteraction } from '@/composables/editor/editor-interactions/types'
import { createScopedLogger } from '@/services/editor/logger-service'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'
import type { HierarchyItem } from '@/utils/editor/hierarchy-validation'

const log = createScopedLogger('PolygonDraw')

/**
 * Composable for managing polygon drawing functionality.
 * Handles the complete drawing workflow from point addition to polygon creation.
 */
export function usePolygonDraw(
  polygons: RenderablePolygon[],
  view: View,
  pixelsToWorld: (pixels: number, view: View) => number,
  constrainToImage: Ref<boolean> | undefined,
  imageSize: Ref<ImageSize> | undefined,
  regionType: Ref<PolygonType> | undefined,
  mouseInteraction: MouseInteraction,
  selectedPolygonIndex: Ref<number>,
  constrainToParent: Ref<boolean> | undefined,
  allPolygons: RenderablePolygon[],
  autoSelect: Ref<boolean> | undefined,
  commander: Commander,
  canvasId: string,
  viewMode?: Ref<string>,
  preventOverlapOnCreate?: Ref<boolean>,
  overlapMinAreaThreshold?: Ref<number>
) {
  const dialogs = useOverlayDialogs()
  const currentPolygonPoints = reactive<Point[]>([])
  const previewPoint = reactive<PreviewPoint>({ x: null, y: null })
  const isInvalidPosition = ref<boolean>(false)

  const creationHistory = ref<Point[]>([])
  const creationHistoryIndex = ref<number>(-1)

  function getHierarchyPolygons(): HierarchyItem[] {
    return allPolygons.map(polygon => ({
      id: polygon.id,
      type: polygon.type ?? PolygonType.REGION,
      parentId: polygon.parentId
    }))
  }

  /**
   * Add a point to the current polygon being drawn.
   * Validates the point before adding to prevent self-intersections.
   *
   * @param point - World coordinates to add
   * @returns True if point was added, false if invalid
   */
  function addPoint(point: Point): boolean {
    if (mouseInteraction.isPanning() || mouseInteraction.hasMoved()) {
      return false
    }

    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        return false // Prevent adding point if outside image bounds
      }
    }

    if (constrainToParent?.value && allPolygons) {
      const parentPolygon = findParentPolygon(allPolygons, selectedPolygonIndex.value, regionType?.value || PolygonType.REGION)
      if (parentPolygon && !isPointWithinParentBounds(point, parentPolygon)) {
        return false // Prevent adding point if outside parent boundaries
      }
    }

    if (currentPolygonPoints.length >= 2) {
      const closingThreshold = pixelsToWorld(15, view)
      const wouldIntersect = wouldNewVertexSelfIntersect(currentPolygonPoints, point, closingThreshold)
      if (wouldIntersect) {
        return false // Prevent adding point if it would create invalid polygon
      }
    }

    currentPolygonPoints.push(point)

    creationHistory.value = []
    creationHistoryIndex.value = -1

    return true
  }

  /**
   * Update the preview point for the next vertex in the polygon.
   * Also updates validation state based on whether the preview position would be valid.
   *
   * @param point - World coordinates for preview
   */
  function updatePreview(point: Point): void {
    let constrainedPoint = point
    let isValidPosition = true

    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        constrainedPoint = clampToWorldBounds(point, getImageBounds(imageSize.value))
        isInvalidPosition.value = true
        isValidPosition = false
      }
    }

    if (constrainToParent?.value && allPolygons && isValidPosition) {
      const parentPolygon = findParentPolygon(allPolygons, selectedPolygonIndex.value, regionType?.value || PolygonType.REGION)
      if (parentPolygon && !isPointWithinParentBounds(point, parentPolygon)) {
        isInvalidPosition.value = true
        isValidPosition = false
      }
    }

    if (isValidPosition) {
      isInvalidPosition.value = false
    }

    previewPoint.x = constrainedPoint.x
    previewPoint.y = constrainedPoint.y

    if (currentPolygonPoints.length >= 2) {
      const closingThreshold = pixelsToWorld(15, view)
      const wouldIntersect = wouldNewVertexSelfIntersect(currentPolygonPoints, constrainedPoint, closingThreshold)
      isInvalidPosition.value = isInvalidPosition.value || wouldIntersect
    }
  }

  /**
   * Complete the current polygon and add it to the polygons array.
   * Uses the Command pattern for undo/redo support.
   * In textline view mode, uses auto-parent detection to create/find parent regions.
   *
   * @returns True if polygon was created, false if insufficient points
   */
  function finishPolygon(): boolean {
    if (currentPolygonPoints.length < 2) {
      return false
    }

    if (isClosedPolygonSelfIntersecting(currentPolygonPoints)) {
      const toast = useToast()
      toast.add({ title: 'Cannot create self-intersecting polygon', color: 'error' })
      return false
    }

    const currentType = regionType?.value || PolygonType.REGION
    log.debug('Creating polygon', {
      currentType,
      regionType: regionType?.value,
      viewMode: viewMode?.value
    })

    const isTextlineViewMode = viewMode?.value === 'textline'
    const isCreatingTextline = currentType === PolygonType.TEXTLINE

    if (isTextlineViewMode && isCreatingTextline) {
      log.debug('Using auto-parent command for textline creation in textline view mode')

      const autoParentCommand = new CreateTextlineAutoParentCommand({
        points: [...currentPolygonPoints],
        preventOverlapOnCreate: preventOverlapOnCreate?.value,
        overlapMinAreaThreshold: overlapMinAreaThreshold?.value
      })

      const session = getEditorSession(canvasId)
      const commandCtx = session ? { canvasId, session } : undefined
      let result: { textlineId: string } | undefined
      try {
        result = commander.execute(autoParentCommand, commandCtx)
      } catch (error) {
        const message = error instanceof Error && error.message
          ? error.message
          : 'Could not create textline with overlap prevention enabled.'
        dialogs.alert({
          title: 'Textline creation blocked',
          message
        })
        clearDrawing()
        return false
      }

      if (autoSelect?.value && result?.textlineId) {
        setTimeout(() => {
          const idx = polygons.findIndex(p => p.id === result.textlineId)
          if (idx >= 0) selectedPolygonIndex.value = idx
        }, 0)
      }

      clearDrawing()
      return true
    }

    let parentId: string | undefined = undefined
    if (selectedPolygonIndex.value >= 0 && selectedPolygonIndex.value < polygons.length) {
      const selectedPolygon = polygons[selectedPolygonIndex.value]

      if (selectedPolygon) {
        const validation = validatePolygonParent(currentType, selectedPolygon.id, getHierarchyPolygons())

        if (validation.valid) {
          parentId = selectedPolygon.id
        } else {
          log.warn(`Cannot create ${currentType} as child of ${selectedPolygon.type}: ${validation.error}`)
          dialogs.alert({
            title: 'Invalid Operation',
            message: validation.error || 'Invalid parent-child relationship'
          })
          return false
        }
      }
    } else {
      const validation = validatePolygonParent(currentType, undefined, getHierarchyPolygons())

      if (!validation.valid) {
        log.warn(`Cannot create ${currentType} without parent: ${validation.error}`)
        dialogs.alert({
          title: 'Invalid Operation',
          message: validation.error || 'Invalid operation'
        })
        return false
      }
    }

    const createCommand = new CreatePolygonCommand({
      points: [...currentPolygonPoints],
      type: currentType,
      parentId: parentId,
      preventOverlapOnCreate: preventOverlapOnCreate?.value,
      overlapMinAreaThreshold: overlapMinAreaThreshold?.value
    })

    const session = getEditorSession(canvasId)
    const commandCtx = session ? { canvasId, session } : undefined
    let result: { id: string } | undefined
    try {
      result = commander.execute(createCommand, commandCtx)
    } catch (error) {
      const message = error instanceof Error && error.message
        ? error.message
        : 'Could not create polygon with overlap prevention enabled.'
      dialogs.alert({
        title: 'Polygon creation blocked',
        message
      })
      clearDrawing()
      return false
    }

    if (autoSelect?.value && result?.id) {
      setTimeout(() => {
        const idx = polygons.findIndex(p => p.id === result.id)
        if (idx >= 0) selectedPolygonIndex.value = idx
      }, 0)
    }

    clearDrawing()
    return true
  }

  /**
   * Clear the current drawing state.
   * Cancels the current polygon without saving it.
   */
  function clearDrawing(): void {
    currentPolygonPoints.length = 0 // Clear the array
    previewPoint.x = null
    previewPoint.y = null
    isInvalidPosition.value = false

    creationHistory.value = []
    creationHistoryIndex.value = -1
  }

  /**
   * Undo the last point added during polygon creation.
   * If at the first point, cancels polygon creation entirely.
   */
  function undoPolygonCreation(): void {
    if (currentPolygonPoints.length === 0) {
      return // Nothing to undo
    }

    const removedPoint = currentPolygonPoints.pop()

    if (creationHistoryIndex.value < creationHistory.value.length - 1) {
      creationHistory.value = creationHistory.value.slice(0, creationHistoryIndex.value + 1)
    }

    if (removedPoint) {
      creationHistory.value.push(removedPoint)
      creationHistoryIndex.value = creationHistory.value.length - 1
    }

    if (currentPolygonPoints.length === 0) {
      previewPoint.x = null
      previewPoint.y = null
      isInvalidPosition.value = false
    }
  }

  /**
   * Redo a previously undone point during polygon creation.
   */
  function redoPolygonCreation(): void {
    if (creationHistoryIndex.value < 0 || creationHistoryIndex.value >= creationHistory.value.length) {
      return // Nothing to redo
    }

    const pointToRestore = creationHistory.value[creationHistoryIndex.value]
    if (pointToRestore) {
      currentPolygonPoints.push(pointToRestore)
      creationHistoryIndex.value++
    }
  }

  /**
   * Cancel polygon creation immediately (Escape key functionality).
   * Clears all points and history.
   */
  function cancelPolygonCreation(): void {
    clearDrawing()
  }

  /**
   * Check if drawing mode is currently active (has points).
   *
   * @returns True if currently drawing a polygon
   */
  function isActive(): boolean {
    return currentPolygonPoints.length > 0
  }

  /**
   * Handle mouse down event for drawing mode.
   * Only adds point if it was a clean click (not a drag or pan).
   *
   * @param e - Mouse event
   * @param getWorldCoordsFromEvent - Function to convert event to world coordinates
   * @param canvas - Canvas element
   * @param aspectRatioScale - Aspect ratio scaling
   * @returns True if point was added
   */
  function handleMouseDown(
    e: MouseEvent,
    getWorldCoordsFromEvent: (e: MouseEvent, canvas: HTMLCanvasElement, view: View, aspectRatioScale: AspectRatioScale) => Point,
    canvas: HTMLCanvasElement | null,
    aspectRatioScale: AspectRatioScale
  ): boolean {
    if (!canvas) return false

    const currentAction = mouseInteraction.getCurrentAction()
    if (currentAction === 'panning' || currentAction === 'drag') {
      return false
    }

    const point = getWorldCoordsFromEvent(e, canvas, view, aspectRatioScale)
    return addPoint(point)
  }

  /**
   * Handle mouse move event for drawing mode.
   *
   * @param e - Mouse event
   * @param getWorldCoordsFromEvent - Function to convert event to world coordinates
   * @param canvas - Canvas element
   * @param aspectRatioScale - Aspect ratio scaling
   */
  function handleMouseMove(
    e: MouseEvent,
    getWorldCoordsFromEvent: (e: MouseEvent, canvas: HTMLCanvasElement, view: View, aspectRatioScale: AspectRatioScale) => Point,
    canvas: HTMLCanvasElement | null,
    aspectRatioScale: AspectRatioScale
  ): void {
    if (!canvas) return

    if (currentPolygonPoints.length > 0) {
      const { x, y } = getWorldCoordsFromEvent(e, canvas, view, aspectRatioScale)
      updatePreview({ x, y })
    }
  }

  /**
   * Handle double click event to finish polygon.
   *
   * @param e - Mouse event
   * @returns True if polygon was created
   */
  function handleDoubleClick(e: MouseEvent): boolean {
    if (currentPolygonPoints.length < 2) return false
    e.preventDefault()
    return finishPolygon()
  }

  return {
    currentPolygonPoints,
    previewPoint,
    isInvalidPosition,
    creationHistory,
    creationHistoryIndex,

    addPoint,
    updatePreview,
    clearDrawing,

    undoPolygonCreation,
    redoPolygonCreation,
    cancelPolygonCreation,

    isActive,

    handleMouseDown,
    handleMouseMove,
    handleDoubleClick
  }
}
