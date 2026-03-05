import { ZOOM, TIMING } from '@/utils/editor/editor-constants'
import { applyInverseRotation } from '@/utils/editor/coordinates'
import type { AspectRatioScale } from '@/models/editor'

export type MouseAction
  = | 'idle'
    | 'click'
    | 'doubleclick'
    | 'rightclick'
    | 'drag'
    | 'panning'
    | 'hovering'
    | 'scrolling'

export interface MouseActionState {
  action: MouseAction
  position: { x: number, y: number }
  startPosition: { x: number, y: number } | null
  deltaPosition: { x: number, y: number } | null
  button: number | null
  isDragging: boolean
  isPressed: boolean
}

/**
 * Unified composable for managing all mouse interactions.
 * Handles action tracking, panning, zooming, and click detection.
 */
export function useMouseInteraction() {
  const view = reactive({ zoom: 1.0, offsetX: 0, offsetY: 0 })

  const panState = reactive({ isDragging: false, lastX: 0, lastY: 0 })

  const actionState = reactive<MouseActionState>({
    action: 'idle',
    position: { x: 0, y: 0 },
    startPosition: null,
    deltaPosition: null,
    button: null,
    isDragging: false,
    isPressed: false
  })

  let clickTimer: ReturnType<typeof setTimeout> | null = null
  let scrollTimer: ReturnType<typeof setTimeout> | null = null
  let clickCount = 0

  const config = {
    dragThreshold: 5,
    clickTimeout: TIMING.CLICK_TIMEOUT,
    scrollTimeout: 150
  }

  /**
   * Update current mouse position and calculate delta.
   */
  function updatePosition(x: number, y: number) {
    actionState.position = { x, y }

    if (actionState.startPosition) {
      actionState.deltaPosition = {
        x: x - actionState.startPosition.x,
        y: y - actionState.startPosition.y
      }
    }
  }

  /**
   * Check if movement threshold has been exceeded.
   */
  function hasExceededMovementThreshold(e: MouseEvent, threshold = config.dragThreshold): boolean {
    if (!actionState.startPosition) return false
    const deltaX = Math.abs(e.clientX - actionState.startPosition.x)
    const deltaY = Math.abs(e.clientY - actionState.startPosition.y)
    return deltaX > threshold || deltaY > threshold
  }

  /**
   * Handle mouse down - initialize tracking.
   */
  function handleMouseDown(e: MouseEvent) {
    actionState.isPressed = true
    actionState.button = e.button
    actionState.startPosition = { x: e.clientX, y: e.clientY }
    updatePosition(e.clientX, e.clientY)
    actionState.isDragging = false
  }

  /**
   * Handle mouse move - detect dragging/panning and hovering.
   */
  function handleMouseMove(e: MouseEvent) {
    updatePosition(e.clientX, e.clientY)

    if (actionState.isPressed && actionState.startPosition) {
      if (hasExceededMovementThreshold(e)) {
        if (panState.isDragging) {
          actionState.isDragging = true
          actionState.action = 'panning'
        } else if (actionState.action === 'drag') {
          actionState.isDragging = true
        }
      }
    } else if (!actionState.isPressed) {
      actionState.action = 'hovering'
    }
  }

  /**
   * Handle mouse up - finalize action detection.
   */
  function handleMouseUp(e: MouseEvent) {
    actionState.isPressed = false
    updatePosition(e.clientX, e.clientY)

    if (actionState.isDragging && !panState.isDragging) {
      actionState.action = 'idle'
      actionState.isDragging = false
    } else if (!actionState.isDragging) {
      clickCount++

      if (clickTimer) {
        clearTimeout(clickTimer)
      }

      clickTimer = setTimeout(() => {
        if (clickCount === 1) {
          actionState.action = 'click'
        } else if (clickCount >= 2) {
          actionState.action = 'doubleclick'
        }
        clickCount = 0

        setTimeout(() => {
          if (actionState.action === 'click' || actionState.action === 'doubleclick') {
            actionState.action = 'idle'
          }
        }, 100)
      }, config.clickTimeout)
    }

    actionState.startPosition = null
    actionState.deltaPosition = null
    actionState.button = null
  }

  /**
   * Handle right-click context menu.
   */
  function handleContextMenu(e: MouseEvent, options?: { preventDefault?: boolean }) {
    if (options?.preventDefault !== false) {
      e.preventDefault()
    }
    actionState.action = 'rightclick'
    updatePosition(e.clientX, e.clientY)

    setTimeout(() => {
      if (actionState.action === 'rightclick') {
        actionState.action = 'idle'
      }
    }, 100)
  }

  /**
   * Handle mouse leaving the tracked area.
   */
  function handleMouseLeave() {
    if (!actionState.isPressed) {
      actionState.action = 'idle'
    }
  }

  /**
   * Handle wheel events for zooming.
   */
  function handleWheel(
    e: WheelEvent,
    canvas: HTMLElement | null,
    aspectRatioScale: AspectRatioScale
  ) {
    if (!canvas) return
    e.preventDefault()
    actionState.action = 'scrolling'
    updatePosition(e.clientX, e.clientY)

    const rect = canvas.getBoundingClientRect()
    const mouseX = ((e.clientX - rect.left) / canvas.clientWidth) * 2 - 1
    const mouseY = -(((e.clientY - rect.top) / canvas.clientHeight) * 2 - 1)
    const scale = aspectRatioScale

    const zoomFactor = e.deltaY > 0 ? 0.9 : 1.1
    const newZoom = Math.min(Math.max(view.zoom * zoomFactor, ZOOM.MIN), ZOOM.MAX)
    const effectiveZoomFactor = newZoom / view.zoom

    const scaleX = (typeof scale.scaleX === 'number' && isFinite(scale.scaleX) && scale.scaleX !== 0) ? scale.scaleX : 1
    const scaleY = (typeof scale.scaleY === 'number' && isFinite(scale.scaleY) && scale.scaleY !== 0) ? scale.scaleY : 1
    const unrotatedClip = applyInverseRotation({ x: mouseX, y: mouseY }, scale)
    const mouseWorldX = unrotatedClip.x / scaleX
    const mouseWorldY = unrotatedClip.y / scaleY

    view.offsetX = mouseWorldX * (1 - effectiveZoomFactor) + view.offsetX * effectiveZoomFactor
    view.offsetY = mouseWorldY * (1 - effectiveZoomFactor) + view.offsetY * effectiveZoomFactor
    view.zoom = newZoom

    if (scrollTimer) {
      clearTimeout(scrollTimer)
    }

    scrollTimer = setTimeout(() => {
      actionState.action = 'idle'
    }, config.scrollTimeout)
  }

  /**
   * Start panning operation.
   */
  function startPanning(e: MouseEvent) {
    if (!panState.isDragging) {
      actionState.isDragging = true
      panState.isDragging = true
      panState.lastX = e.clientX
      panState.lastY = e.clientY
      actionState.action = 'panning'
    }
  }

  /**
   * Update panning operation.
   */
  function updatePanning(
    e: MouseEvent,
    canvas: HTMLElement | null,
    aspectRatioScale: AspectRatioScale
  ) {
    if (!canvas) return
    if (!panState.isDragging) return

    const scale = aspectRatioScale
    const dx = (e.clientX - panState.lastX) / canvas.clientWidth * 2
    const dy = -(e.clientY - panState.lastY) / canvas.clientHeight * 2

    panState.lastX = e.clientX
    panState.lastY = e.clientY

    const scaleX = (typeof scale.scaleX === 'number' && isFinite(scale.scaleX) && scale.scaleX !== 0) ? scale.scaleX : 1
    const scaleY = (typeof scale.scaleY === 'number' && isFinite(scale.scaleY) && scale.scaleY !== 0) ? scale.scaleY : 1
    const unrotatedDelta = applyInverseRotation({ x: dx, y: dy }, scale)
    view.offsetX += unrotatedDelta.x / scaleX
    view.offsetY += unrotatedDelta.y / scaleY
  }

  /**
   * End panning operation.
   */
  function endPanning() {
    if (panState.isDragging) {
      panState.isDragging = false
      actionState.isDragging = false
      actionState.action = 'idle'
    }
  }

  /**
   * Check if we should start panning.
   */
  function shouldStartPanning(e: MouseEvent, isOtherDragActive = false): boolean {
    return (
      actionState.isPressed
      && !panState.isDragging
      && !actionState.isDragging
      && hasExceededMovementThreshold(e)
      && !isOtherDragActive
    )
  }

  /**
   * Reset view to default state.
   */
  function resetView() {
    view.zoom = 1.0
    view.offsetX = 0
    view.offsetY = 0
  }

  /**
   * Set view parameters.
   */
  function setView(newView: { zoom: number, offsetX: number, offsetY: number }) {
    view.zoom = newView.zoom
    view.offsetX = newView.offsetX
    view.offsetY = newView.offsetY
  }

  /**
   * Reset action state.
   */
  function resetActionState() {
    actionState.isDragging = false
    actionState.isPressed = false
    actionState.action = 'idle'
    actionState.startPosition = null
    actionState.deltaPosition = null
  }

  /**
   * Cleanup function for timers.
   */
  function cleanup() {
    if (clickTimer) clearTimeout(clickTimer)
    if (scrollTimer) clearTimeout(scrollTimer)
  }

  return {
    view,
    panState,
    actionState,

    handleMouseDown,
    handleMouseMove,
    handleMouseUp,
    handleContextMenu,
    handleMouseLeave,
    handleWheel,

    startPanning,
    updatePanning,
    endPanning,
    shouldStartPanning,

    resetView,
    setView,

    resetActionState,
    hasExceededMovementThreshold,

    isPanning: () => panState.isDragging,
    isMouseDown: () => actionState.isPressed,
    hasMoved: () => actionState.isDragging,
    getCurrentAction: () => actionState.action,

    cleanup
  }
}
