import type { Commander } from '@/commands'
import { CreatePolygonCommand, CreateTextlineAutoParentCommand } from '@/commands'
import { PolygonType } from '@/models/editor'
import type { PolygonType as PolygonTypeType, Point, ImageSize, View, AspectRatioScale } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { getEditorSession } from '@/session/editor/editor-session'
import { clampToWorldBounds, getImageBounds, isPointWithinImageBounds } from '@/utils/editor/coordinates'
import { findParentPolygon, areAllPointsWithinParentBounds } from '@/utils/editor/parent-constraint-utils'
import { validatePolygonParent } from '@/utils/editor/hierarchy-validation'
import { createScopedLogger } from '@/services/editor/logger-service'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'

const log = createScopedLogger('RectangleDrawing')

/**
 * Coordinates for rectangle corners.
 */
interface RectanglePoint {
  x: number | null
  y: number | null
}

/**
 * Composable for managing rectangle drawing functionality.
 * Handles rectangle creation with preview on hover and creates rectangles as 4-point polygons.
 */
export function useRectangleDrawing(
  polygons: RenderablePolygon[],
  constrainToImage: Ref<boolean> | undefined,
  imageSize: Ref<ImageSize> | undefined,
  regionType: Ref<PolygonTypeType> | undefined,
  selectedPolygonIndex: Ref<number>,
  constrainToParent: Ref<boolean> | undefined,
  allPolygons: RenderablePolygon[] | undefined,
  autoSelect: Ref<boolean> | undefined,
  commander: Commander,
  canvasId: string,
  viewMode?: Ref<string>
) {
  const dialogs = useOverlayDialogs()
  const startPoint = reactive<RectanglePoint>({ x: null, y: null })
  const endPoint = reactive<RectanglePoint>({ x: null, y: null })
  const previewPoints = reactive<Point[]>([])
  const isDrawing = ref<boolean>(false)
  const isInvalidPosition = ref<boolean>(false)

  /**
   * Start drawing a rectangle from the given point.
   *
   * @param point - World coordinates where rectangle starts
   */
  function startRectangle(point: Point): void {
    let constrainedPoint = point
    if (constrainToImage?.value && imageSize?.value) {
      constrainedPoint = clampToWorldBounds(point, getImageBounds(imageSize.value))
    }

    startPoint.x = constrainedPoint.x
    startPoint.y = constrainedPoint.y
    endPoint.x = constrainedPoint.x
    endPoint.y = constrainedPoint.y
    isDrawing.value = true
    updatePreviewPoints()
  }

  /**
   * Update the rectangle preview based on current mouse position.
   *
   * @param point - Current world coordinates
   */
  function updateRectangle(point: Point): void {
    if (!isDrawing.value) return

    let constrainedPoint = point
    let isValid = true

    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        isValid = false
      }
      constrainedPoint = clampToWorldBounds(point, getImageBounds(imageSize.value))
    }

    if (constrainToParent?.value && allPolygons && isValid) {
      const parentPolygon = findParentPolygon(allPolygons, selectedPolygonIndex.value, regionType?.value || PolygonType.REGION)
      if (parentPolygon) {
        const tempStartX = startPoint.x!
        const tempStartY = startPoint.y!
        const tempEndX = constrainedPoint.x
        const tempEndY = constrainedPoint.y

        const minX = Math.min(tempStartX, tempEndX)
        const maxX = Math.max(tempStartX, tempEndX)
        const minY = Math.min(tempStartY, tempEndY)
        const maxY = Math.max(tempStartY, tempEndY)

        const tempPoints: Point[] = [
          { x: minX, y: minY },
          { x: maxX, y: minY },
          { x: maxX, y: maxY },
          { x: minX, y: maxY }
        ]

        if (!areAllPointsWithinParentBounds(tempPoints, parentPolygon)) {
          isValid = false
        }
      }
    }

    endPoint.x = constrainedPoint.x
    endPoint.y = constrainedPoint.y
    isInvalidPosition.value = !isValid
    updatePreviewPoints()
  }

  /**
   * Calculate the 4 corner points of the rectangle.
   * Returns them in a consistent order (clockwise starting from top-left).
   */
  function updatePreviewPoints(): void {
    if (!isDrawing.value || startPoint.x === null || startPoint.y === null) {
      previewPoints.length = 0
      return
    }

    const minX = Math.min(startPoint.x, endPoint.x!)
    const maxX = Math.max(startPoint.x, endPoint.x!)
    const minY = Math.min(startPoint.y, endPoint.y!)
    const maxY = Math.max(startPoint.y, endPoint.y!)

    previewPoints.length = 0 // Clear array
    previewPoints.push({ x: minX, y: minY }) // Top-left
    previewPoints.push({ x: maxX, y: minY }) // Top-right
    previewPoints.push({ x: maxX, y: maxY }) // Bottom-right
    previewPoints.push({ x: minX, y: maxY }) // Bottom-left
  }

  /**
   * Finish drawing the rectangle and create it as a polygon.
   *
   * @returns True if rectangle was created, false if not drawing
   */
  function finishRectangle(): boolean {
    if (!isDrawing.value) return false

    const currentType = regionType?.value || PolygonType.REGION
    log.debug('Creating rectangle with type:', currentType, 'regionType?.value:', regionType?.value, 'viewMode:', viewMode?.value)

    const isTextlineViewMode = viewMode?.value === 'textline'
    const isCreatingTextline = currentType === PolygonType.TEXTLINE

    if (isTextlineViewMode && isCreatingTextline) {
      log.debug('Using auto-parent command for textline rectangle creation in textline view mode')

      const autoParentCommand = new CreateTextlineAutoParentCommand({
        points: [...previewPoints]
      })

      const session = getEditorSession(canvasId)
      const commandCtx = session ? { canvasId, session } : undefined
      const result = commander.execute(autoParentCommand, commandCtx)

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

      if (!selectedPolygon) return false

      const validation = validatePolygonParent(currentType, selectedPolygon.id, allPolygons || [])

      if (validation.valid) {
        parentId = selectedPolygon.id
      } else {
        log.warn(`Cannot create ${currentType} as child of ${selectedPolygon.type}: ${validation.error}`)
        dialogs.alert({
          title: 'Invalid Operation',
          message: validation.error || 'Invalid parent-child relationship'
        })
        clearDrawing()
        return false
      }
    } else {
      const validation = validatePolygonParent(currentType, undefined, allPolygons || [])

      if (!validation.valid) {
        log.warn(`Cannot create ${currentType} without parent: ${validation.error}`)
        dialogs.alert({
          title: 'Invalid Operation',
          message: validation.error || 'Invalid operation'
        })
        clearDrawing()
        return false
      }
    }

    if (constrainToParent?.value && allPolygons) {
      const parentPolygon = findParentPolygon(allPolygons, selectedPolygonIndex.value, currentType)
      if (parentPolygon && !areAllPointsWithinParentBounds(previewPoints, parentPolygon)) {
        clearDrawing() // Clear the drawing state
        return false // Don't create rectangle if it violates parent constraints
      }
    }

    const createCommand = new CreatePolygonCommand({
      points: [...previewPoints], // Copy the points
      type: currentType,
      parentId: parentId
    })

    const session = getEditorSession(canvasId)
    const commandCtx = session ? { canvasId, session } : undefined
    const result = commander.execute(createCommand, commandCtx)

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
   * Cancels the current rectangle without saving it.
   */
  function clearDrawing(): void {
    startPoint.x = null
    startPoint.y = null
    endPoint.x = null
    endPoint.y = null
    previewPoints.length = 0
    isDrawing.value = false
  }

  /**
   * Check if rectangle drawing is currently active.
   *
   * @returns True if currently drawing a rectangle
   */
  function isActive(): boolean {
    return isDrawing.value
  }

  /**
   * Handle mouse down event for rectangle drawing.
   *
   * @param _e - Mouse event
   * @param getWorldCoordsFromEvent - Function to convert event to world coordinates
   * @param canvas - Canvas element
   * @param view - Current view state
   * @param aspectRatioScale - Aspect ratio scaling
   * @returns True if rectangle drawing was started
   */
  function handleMouseDown(
    _e: MouseEvent,
    getWorldCoordsFromEvent: (e: MouseEvent, canvas: HTMLCanvasElement, view: View, aspectRatioScale: AspectRatioScale) => Point,
    canvas: HTMLCanvasElement,
    view: View,
    aspectRatioScale: AspectRatioScale
  ): boolean {
    if (isDrawing.value) {
      return finishRectangle()
    } else {
      const point = getWorldCoordsFromEvent(_e, canvas, view, aspectRatioScale)
      startRectangle(point)
      return true
    }
  }

  /**
   * Handle mouse move event for rectangle drawing.
   *
   * @param e - Mouse event
   * @param getWorldCoordsFromEvent - Function to convert event to world coordinates
   * @param canvas - Canvas element
   * @param view - Current view state
   * @param aspectRatioScale - Aspect ratio scaling
   */
  function handleMouseMove(
    e: MouseEvent,
    getWorldCoordsFromEvent: (e: MouseEvent, canvas: HTMLCanvasElement, view: View, aspectRatioScale: AspectRatioScale) => Point,
    canvas: HTMLCanvasElement,
    view: View,
    aspectRatioScale: AspectRatioScale
  ): void {
    if (!isDrawing.value) return

    const point = getWorldCoordsFromEvent(e, canvas, view, aspectRatioScale)
    updateRectangle(point)
  }

  /**
   * Get drawing statistics for debugging or UI display.
   *
   * @returns Object with drawing state information
   */
  return {
    startPoint,
    endPoint,
    previewPoints,
    isDrawing,
    isInvalidPosition,

    clearDrawing,

    isActive,

    handleMouseDown,
    handleMouseMove
  }
}
