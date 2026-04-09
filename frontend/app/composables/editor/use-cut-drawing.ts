import type { Commander, CutMode } from '@/commands'
import { CutElementsCommand } from '@/commands'
import type { Point, ImageSize, View, AspectRatioScale } from '@/models/editor'
import type { RenderablePolygon, PreviewPoint } from '@/types/editor/rendering'
import { getEditorSession } from '@/session/editor/editor-session'
import { isPointWithinImageBounds, clampToWorldBounds, getImageBounds } from '@/utils/editor/coordinates'
import { wouldNewVertexSelfIntersect } from '@/utils/editor/hit-detection'
import { useEditorStore } from '@/stores/editor/editor.store'
import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('CutDrawing')

/**
 * Rectangle point with nullable coordinates
 */
interface RectanglePoint {
  x: number | null
  y: number | null
}

/**
 * Composable for managing cut shape drawing functionality.
 * Supports three modes:
 * - Cut Line: Draw a polyline to split polygons
 * - Cut Polygon: Draw a freeform polygon to subtract from shapes
 * - Cut Rectangle: Draw a rectangle to subtract from shapes
 */
export function useCutDrawing(
  polygons: RenderablePolygon[],
  view: View,
  pixelsToWorld: (pixels: number, view: View) => number,
  constrainToImage: Ref<boolean> | undefined,
  imageSize: Ref<ImageSize> | undefined,
  commander: Commander,
  canvasId: string
) {
  const currentPoints = reactive<Point[]>([])
  const previewPoint = reactive<PreviewPoint>({ x: null, y: null })
  const isInvalidPosition = ref<boolean>(false)

  const rectStartPoint = reactive<RectanglePoint>({ x: null, y: null })
  const rectEndPoint = reactive<RectanglePoint>({ x: null, y: null })
  const rectPreviewPoints = reactive<Point[]>([])
  const isRectDrawing = ref<boolean>(false)
  const rectFirstClickMade = ref<boolean>(false)

  const cutMode = ref<CutMode>('line')

  /**
   * Set the current cut mode
   */
  function setCutMode(mode: CutMode): void {
    cutMode.value = mode
    clearDrawing()
  }

  /**
   * Add a point to the cut line
   */
  function addLinePoint(point: Point): boolean {
    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        return false
      }
    }

    currentPoints.push({ x: point.x, y: point.y })
    return true
  }

  /**
   * Update the preview point for cut line
   */
  function updateLinePreview(point: Point): void {
    let constrainedPoint = point
    let isValid = true

    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        constrainedPoint = clampToWorldBounds(point, getImageBounds(imageSize.value))
        isValid = false
      }
    }

    previewPoint.x = constrainedPoint.x
    previewPoint.y = constrainedPoint.y
    isInvalidPosition.value = !isValid
  }

  /**
   * Finish the cut line and execute the cut command
   */
  function finishCutLine(): boolean {
    if (currentPoints.length < 2) {
      log.debug('Cut line requires at least 2 points')
      return false
    }

    const editorStore = useEditorStore()
    const minAreaThreshold = editorStore.globalSettings.cutMinAreaThreshold

    const cutCommand = new CutElementsCommand({
      mode: 'line',
      cutPoints: [...currentPoints],
      minAreaThreshold
    })

    const session = getEditorSession(canvasId)
    const commandCtx = session ? { canvasId, session } : undefined
    const result = commander.execute(cutCommand, commandCtx)

    log.debug('Cut line result:', result)

    clearDrawing()
    return true
  }

  /**
   * Add a point to the cut polygon
   */
  function addPolygonPoint(point: Point): boolean {
    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        return false
      }
    }

    if (currentPoints.length >= 2) {
      const closingThreshold = pixelsToWorld(15, view)
      const wouldIntersect = wouldNewVertexSelfIntersect(currentPoints, point, closingThreshold)
      if (wouldIntersect) {
        return false
      }
    }

    currentPoints.push({ x: point.x, y: point.y })
    return true
  }

  /**
   * Update the preview point for cut polygon
   */
  function updatePolygonPreview(point: Point): void {
    let constrainedPoint = point
    let isValid = true

    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        constrainedPoint = clampToWorldBounds(point, getImageBounds(imageSize.value))
        isValid = false
      }
    }

    if (currentPoints.length >= 2) {
      const closingThreshold = pixelsToWorld(15, view)
      const wouldIntersect = wouldNewVertexSelfIntersect(currentPoints, constrainedPoint, closingThreshold)
      if (wouldIntersect) {
        isValid = false
      }
    }

    previewPoint.x = constrainedPoint.x
    previewPoint.y = constrainedPoint.y
    isInvalidPosition.value = !isValid
  }

  /**
   * Finish the cut polygon and execute the cut command
   */
  function finishCutPolygon(): boolean {
    if (currentPoints.length < 3) {
      log.debug('Cut polygon requires at least 3 points')
      return false
    }

    const editorStore = useEditorStore()
    const minAreaThreshold = editorStore.globalSettings.cutMinAreaThreshold

    const cutCommand = new CutElementsCommand({
      mode: 'polygon',
      cutPoints: [...currentPoints],
      minAreaThreshold
    })

    const session = getEditorSession(canvasId)
    const commandCtx = session ? { canvasId, session } : undefined
    const result = commander.execute(cutCommand, commandCtx)

    log.debug('Cut polygon result:', result)

    clearDrawing()
    return true
  }

  /**
   * Start drawing a cut rectangle (first click sets the start point)
   */
  function startCutRectangle(point: Point): void {
    let constrainedPoint = point
    if (constrainToImage?.value && imageSize?.value) {
      constrainedPoint = clampToWorldBounds(point, getImageBounds(imageSize.value))
    }

    rectStartPoint.x = constrainedPoint.x
    rectStartPoint.y = constrainedPoint.y
    rectEndPoint.x = constrainedPoint.x
    rectEndPoint.y = constrainedPoint.y
    isRectDrawing.value = true
    rectFirstClickMade.value = true
    updateRectPreviewPoints()
  }

  /**
   * Update the cut rectangle preview (live preview as mouse moves after first click)
   */
  function updateCutRectangle(point: Point): void {
    if (!rectFirstClickMade.value) return

    let constrainedPoint = point
    let isValid = true

    if (constrainToImage?.value && imageSize?.value) {
      if (!isPointWithinImageBounds(point, imageSize.value)) {
        isValid = false
      }
      constrainedPoint = clampToWorldBounds(point, getImageBounds(imageSize.value))
    }

    rectEndPoint.x = constrainedPoint.x
    rectEndPoint.y = constrainedPoint.y
    isInvalidPosition.value = !isValid
    updateRectPreviewPoints()
  }

  /**
   * Update rectangle preview points
   */
  function updateRectPreviewPoints(): void {
    if (!isRectDrawing.value || rectStartPoint.x === null || rectStartPoint.y === null) {
      rectPreviewPoints.length = 0
      return
    }

    const minX = Math.min(rectStartPoint.x, rectEndPoint.x!)
    const maxX = Math.max(rectStartPoint.x, rectEndPoint.x!)
    const minY = Math.min(rectStartPoint.y, rectEndPoint.y!)
    const maxY = Math.max(rectStartPoint.y, rectEndPoint.y!)

    rectPreviewPoints.length = 0
    rectPreviewPoints.push({ x: minX, y: minY })
    rectPreviewPoints.push({ x: maxX, y: minY })
    rectPreviewPoints.push({ x: maxX, y: maxY })
    rectPreviewPoints.push({ x: minX, y: maxY })
  }

  /**
   * Finish the cut rectangle and execute the cut command (second click confirms)
   */
  function finishCutRectangle(): boolean {
    if (!rectFirstClickMade.value || rectPreviewPoints.length < 4) {
      log.debug('Cut rectangle not properly drawn')
      return false
    }

    const width = Math.abs((rectEndPoint.x ?? 0) - (rectStartPoint.x ?? 0))
    const height = Math.abs((rectEndPoint.y ?? 0) - (rectStartPoint.y ?? 0))

    if (width < 0.001 || height < 0.001) {
      log.debug('Cut rectangle too small')
      clearDrawing()
      return false
    }

    const editorStore = useEditorStore()
    const minAreaThreshold = editorStore.globalSettings.cutMinAreaThreshold

    const cutCommand = new CutElementsCommand({
      mode: 'rectangle',
      cutPoints: [...rectPreviewPoints],
      minAreaThreshold
    })

    const session = getEditorSession(canvasId)
    const commandCtx = session ? { canvasId, session } : undefined
    const result = commander.execute(cutCommand, commandCtx)

    log.debug('Cut rectangle result:', result)

    clearDrawing()
    return true
  }

  /**
   * Clear all drawing state
   */
  function clearDrawing(): void {
    currentPoints.length = 0
    previewPoint.x = null
    previewPoint.y = null
    rectStartPoint.x = null
    rectStartPoint.y = null
    rectEndPoint.x = null
    rectEndPoint.y = null
    rectPreviewPoints.length = 0
    isRectDrawing.value = false
    rectFirstClickMade.value = false
    isInvalidPosition.value = false
  }

  /**
   * Check if drawing is currently active
   */
  function isActive(): boolean {
    return currentPoints.length > 0 || rectFirstClickMade.value
  }

  /**
   * Handle mouse down for the current cut mode
   */
  function handleMouseDown(
    e: MouseEvent,
    getWorldCoordsFromEvent: (e: MouseEvent, canvas: HTMLCanvasElement, view: View, scale: AspectRatioScale) => Point,
    canvas: HTMLCanvasElement,
    aspectRatioScale: AspectRatioScale,
    mode: CutMode
  ): boolean {
    const point = getWorldCoordsFromEvent(e, canvas, view, aspectRatioScale)

    if (mode === 'line') {
      return addLinePoint(point)
    } else if (mode === 'polygon') {
      return addPolygonPoint(point)
    } else {
      if (!rectFirstClickMade.value) {
        startCutRectangle(point)
        return true
      } else {
        return finishCutRectangle()
      }
    }
  }

  /**
   * Handle mouse move for the current cut mode
   */
  function handleMouseMove(
    e: MouseEvent,
    getWorldCoordsFromEvent: (e: MouseEvent, canvas: HTMLCanvasElement, view: View, scale: AspectRatioScale) => Point,
    canvas: HTMLCanvasElement,
    aspectRatioScale: AspectRatioScale,
    mode: CutMode
  ): void {
    const point = getWorldCoordsFromEvent(e, canvas, view, aspectRatioScale)

    if (mode === 'line') {
      if (currentPoints.length > 0) {
        updateLinePreview(point)
      }
    } else if (mode === 'polygon') {
      if (currentPoints.length > 0) {
        updatePolygonPreview(point)
      }
    } else {
      if (rectFirstClickMade.value) {
        updateCutRectangle(point)
      }
    }
  }

  /**
   * Handle mouse up for the current cut mode
   * Note: Rectangle now uses click-based drawing (not drag), so mouse up doesn't finish it
   */
  function handleMouseUp(
    _e: MouseEvent,
    _mode: CutMode
  ): boolean {
    return false
  }

  /**
   * Handle double click to finish cut line/polygon
   */
  function handleDoubleClick(
    e: MouseEvent,
    mode: CutMode
  ): boolean {
    e.preventDefault()

    if (mode === 'line') {
      return finishCutLine()
    } else if (mode === 'polygon') {
      return finishCutPolygon()
    }
    return false
  }

  /**
   * Handle escape key to cancel drawing
   */
  function handleEscape(): void {
    clearDrawing()
  }

  return {
    currentPoints,
    previewPoint,
    rectPreviewPoints,
    isInvalidPosition,
    isRectDrawing,
    rectFirstClickMade,
    cutMode,

    setCutMode,
    clearDrawing,
    isActive,

    addLinePoint,
    updateLinePreview,
    finishCutLine,

    addPolygonPoint,
    updatePolygonPreview,
    finishCutPolygon,

    startCutRectangle,
    updateCutRectangle,
    finishCutRectangle,

    handleMouseDown,
    handleMouseMove,
    handleMouseUp,
    handleDoubleClick,
    handleEscape
  }
}
