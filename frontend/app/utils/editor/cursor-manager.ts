/**
 * Cursor types for different editor states
 */
export type CursorType
  = | 'not-allowed' // Invalid position
    | 'grabbing' // Dragging/panning
    | 'crosshair' // Creating new shapes
    | 'pointer' // Hovering over interactive elements
    | 'grab' // Default select mode
    | 'move' // Moving/dragging nodes
    | 'default' // Fallback

export interface CursorState {
  customCursor?: 'action-wand'

  isValidPosition: boolean

  isDraggingNode: boolean
  isDraggingPolylineNode: boolean
  isPanning: boolean
  isMovingElement: boolean

  isHoveringPolygonNode: boolean
  isHoveringPolygonEdge: boolean
  isHoveringPolygon: boolean

  isHoveringPolylineNode: boolean
  isHoveringPolylineSegment: boolean
  isHoveringPolyline: boolean

  hasSelectedPolygon: boolean
  hasSelectedPolyline: boolean

  interactionMode: 'select' | 'create' | 'move'
}

/**
 * Determine the appropriate cursor based on editor state
 */
export function getCursorType(state: CursorState): CursorType {
  if (!state.isValidPosition) {
    return 'not-allowed'
  }

  if (state.isDraggingNode || state.isDraggingPolylineNode || state.isMovingElement) {
    return 'move'
  }

  if (state.isPanning) {
    return 'grabbing'
  }

  if (state.interactionMode === 'create') {
    return 'crosshair'
  }

  if (state.interactionMode === 'move') {
    if (state.isHoveringPolygon || state.isHoveringPolyline) {
      return 'move'
    }
    return 'default'
  }

  if (state.hasSelectedPolygon && (state.isHoveringPolygonNode || state.isHoveringPolygonEdge)) {
    return 'pointer'
  }

  if (state.hasSelectedPolyline && (state.isHoveringPolylineNode || state.isHoveringPolylineSegment)) {
    return 'pointer'
  }

  if (state.isHoveringPolyline) {
    return 'pointer'
  }

  if (state.isHoveringPolygon) {
    return 'pointer'
  }

  if (state.interactionMode === 'select') {
    return 'grab'
  }

  return 'default'
}

/**
 * Apply cursor to element based on state
 */
export function setCursor(element: HTMLElement | null, state: CursorState | { customCursor: 'action-wand' }): void {
  if (!element) return

  if (state.customCursor === 'action-wand') {
    element.style.cursor = 'var(--editor-action-wand-cursor), crosshair'
    return
  }

  element.style.cursor = getCursorType(state)
}

/**
 * Create a default cursor state (all false/negative values)
 * Useful for testing or when you want to set specific properties
 */
export function createDefaultCursorState(): CursorState {
  return {
    isValidPosition: true,
    isDraggingNode: false,
    isDraggingPolylineNode: false,
    isPanning: false,
    isMovingElement: false,
    isHoveringPolygonNode: false,
    isHoveringPolygonEdge: false,
    isHoveringPolygon: false,
    isHoveringPolylineNode: false,
    isHoveringPolylineSegment: false,
    isHoveringPolyline: false,
    hasSelectedPolygon: false,
    hasSelectedPolyline: false,
    interactionMode: 'select'
  }
}
