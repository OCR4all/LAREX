import type { Commander } from '@/commands'
import { CreatePolylineCommand, CreateBaselineAutoParentCommand } from '@/commands'
import { isPointWithinImageBounds, clampToWorldBounds, getImageBounds } from '@/utils/editor/coordinates'
import { PolygonType } from '@/models/editor'
import type { Point, ImageSize, View, AspectRatioScale } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline, PreviewPoint } from '@/types/editor/rendering'
import { isPointWithinParentBounds } from '@/utils/editor/parent-constraint-utils'
import { validatePolylineParent, validateBaselineForTextline } from '@/utils/editor/hierarchy-validation'
import { getEditorSession } from '@/session/editor/editor-session'
import { createScopedLogger } from '@/services/editor/logger-service'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'

const log = createScopedLogger('PolylineDrawing')

/**
 * Composable for managing polyline drawing functionality.
 * Handles the complete drawing workflow for open polylines (baselines).
 */
export function usePolylineDrawing(
  polylines: RenderablePolyline[],
  view: View,
  _pixelsToWorld: (pixels: number, viewState: View) => number,
  constrainToImage: Ref<boolean> | undefined,
  imageSize: Ref<ImageSize> | undefined,
  selectedPolygonIndex: Ref<number>,
  polygons: RenderablePolygon[],
  constrainToParent: Ref<boolean> | undefined,
  autoSelect: Ref<boolean> | undefined,
  selectedPolylineIndex: Ref<number> | undefined,
  commander: Commander,
  canvasId: string,
  viewMode?: Ref<string>
) {
  const dialogs = useOverlayDialogs()
  const currentPolylinePoints = reactive<Point[]>([])
  const previewPoint = reactive<PreviewPoint>({ x: null, y: null })
  const isInvalidPosition = ref(false)

  /**
   * Add a point to the current polyline being drawn.
   * Validates the point before adding.
   *
   * @param point - World coordinates to add
   * @returns True if point was added, false if invalid
   */
  function addPoint(point: Point): boolean {
    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        return false // Prevent adding point if outside image bounds
      }
    }

    if (constrainToParent?.value && polygons) {
      if (selectedPolygonIndex.value >= 0 && selectedPolygonIndex.value < polygons.length) {
        const selectedPolygon = polygons[selectedPolygonIndex.value]
        if (selectedPolygon && selectedPolygon.type === PolygonType.TEXTLINE) {
          if (!isPointWithinParentBounds(point, selectedPolygon)) {
            return false // Prevent adding point if outside textline boundaries
          }
        }
      }
    }

    currentPolylinePoints.push(point)
    return true
  }

  /**
   * Update the preview point for the next vertex in the polyline.
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

    if (constrainToParent?.value && polygons && isValidPosition) {
      if (selectedPolygonIndex.value >= 0 && selectedPolygonIndex.value < polygons.length) {
        const selectedPolygon = polygons[selectedPolygonIndex.value]
        if (selectedPolygon && selectedPolygon.type === PolygonType.TEXTLINE) {
          if (!isPointWithinParentBounds(point, selectedPolygon)) {
            isInvalidPosition.value = true
            isValidPosition = false
          }
        }
      }
    }

    if (isValidPosition) {
      isInvalidPosition.value = false
    }

    previewPoint.x = constrainedPoint.x
    previewPoint.y = constrainedPoint.y
  }

  /**
   * Complete the current polyline and add it to the polylines array.
   * Uses the Command pattern for undo/redo support.
   * In baseline view mode, uses auto-parent detection to create/find parent textlines and regions.
   *
   * @returns True if polyline was created, false if insufficient points
   */
  function finishPolyline(): boolean {
    if (currentPolylinePoints.length < 2) {
      return false
    }

    const isBaselineViewMode = viewMode?.value === 'baseline'

    if (isBaselineViewMode) {
      log.debug('Using auto-parent command for baseline creation in baseline view mode')

      const autoParentCommand = new CreateBaselineAutoParentCommand({
        points: [...currentPolylinePoints]
      })

      const session = getEditorSession(canvasId)
      const commandCtx = session ? { canvasId, session } : undefined
      let result: { baselineId?: string } | undefined
      try {
        result = commander.execute(autoParentCommand, commandCtx)
      } catch (error) {
        log.error('Failed to create baseline in auto-parent mode', error)
        dialogs.alert({
          title: 'Baseline creation failed',
          message: 'Could not assign the baseline to a textline. Please retry or draw directly on a selected textline.'
        })
        return false
      }

      if (autoSelect?.value && result?.baselineId && selectedPolylineIndex) {
        setTimeout(() => {
          const idx = polylines.findIndex(p => p.id === result.baselineId)
          if (idx >= 0) selectedPolylineIndex.value = idx
        }, 0)
        selectedPolygonIndex.value = -1
      }

      clearDrawing()
      return true
    }

    let parentId: string | undefined = undefined
    if (selectedPolygonIndex.value >= 0 && selectedPolygonIndex.value < polygons.length) {
      const selectedPolygon = polygons[selectedPolygonIndex.value]

      if (selectedPolygon) {
        const parentValidation = validatePolylineParent(selectedPolygon.id, polygons)

        if (!parentValidation.valid) {
          log.warn(`Cannot create baseline: ${parentValidation.error}`)
          dialogs.alert({
            title: 'Invalid Operation',
            message: parentValidation.error || 'Invalid parent for baseline'
          })
          return false
        }

        const baselineValidation = validateBaselineForTextline(selectedPolygon.id, polylines)

        if (!baselineValidation.valid) {
          log.warn(`Cannot create baseline: ${baselineValidation.error}`)
          dialogs.alert({
            title: 'Invalid Operation',
            message: baselineValidation.error || 'Textline already has a baseline'
          })
          return false
        }

        parentId = selectedPolygon.id
      }
    }

    if (!parentId) {
      const errorMsg = 'A baseline must belong to a textline. Please select a textline first.'
      log.warn(errorMsg)
      dialogs.alert({
        title: 'Invalid Operation',
        message: errorMsg
      })
      return false
    }

    const createCommand = new CreatePolylineCommand({
      points: [...currentPolylinePoints],
      parentId
    })

    const session = getEditorSession(canvasId)
    const commandCtx = session ? { canvasId, session } : undefined
    const result = commander.execute(createCommand, commandCtx)

    if (autoSelect?.value && result?.id && selectedPolylineIndex) {
      setTimeout(() => {
        const idx = polylines.findIndex(p => p.id === result.id)
        if (idx >= 0) selectedPolylineIndex.value = idx
      }, 0)
      selectedPolygonIndex.value = -1
    }

    clearDrawing()
    return true
  }

  /**
   * Clear the current drawing state.
   * Cancels the current polyline without saving it.
   */
  function clearDrawing(): void {
    currentPolylinePoints.length = 0 // Clear the array
    previewPoint.x = null
    previewPoint.y = null
    isInvalidPosition.value = false
  }

  /**
   * Check if drawing mode is currently active (has points).
   *
   * @returns True if currently drawing a polyline
   */
  function isActive(): boolean {
    return currentPolylinePoints.length > 0
  }

  /**
   * Handle mouse down event for drawing mode.
   *
   * @param e - Mouse event
   * @param getWorldCoordsFromEvent - Function to convert event to world coordinates
   * @param canvas - Canvas element
   * @param aspectRatioScale - Aspect ratio scaling
   * @returns True if point was added
   */
  function handleMouseDown(
    e: MouseEvent,
    getWorldCoordsFromEvent: (e: MouseEvent, canvas: HTMLCanvasElement, view: View, scale: AspectRatioScale) => Point,
    canvas: HTMLCanvasElement,
    aspectRatioScale: AspectRatioScale
  ): boolean {
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
    getWorldCoordsFromEvent: (e: MouseEvent, canvas: HTMLCanvasElement, view: View, scale: AspectRatioScale) => Point,
    canvas: HTMLCanvasElement,
    aspectRatioScale: AspectRatioScale
  ): void {
    if (currentPolylinePoints.length > 0) {
      const { x, y } = getWorldCoordsFromEvent(e, canvas, view, aspectRatioScale)
      updatePreview({ x, y })
    }
  }

  /**
   * Handle double click event to finish polyline.
   *
   * @param e - Mouse event
   * @returns True if polyline was created
   */
  function handleDoubleClick(e: MouseEvent): boolean | undefined {
    if (currentPolylinePoints.length < 2) return
    e.preventDefault()
    return finishPolyline()
  }

  /**
   * Get drawing statistics for debugging or UI display.
   *
   * @returns Object with drawing state information
   */
  return {
    currentPolylinePoints,
    previewPoint,
    isInvalidPosition,

    addPoint,
    updatePreview,
    clearDrawing,

    isActive,

    handleMouseDown,
    handleMouseMove,
    handleDoubleClick
  }
}
