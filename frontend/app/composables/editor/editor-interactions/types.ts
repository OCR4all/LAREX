import type { View, AspectRatioScale, Point, GetWorldCoordsFunction } from '@/models/editor'

import type { RenderablePolygon, RenderablePolyline, ViewMode } from '@/types/editor/rendering'

import type {
  HoveredEdgeInfo as PolygonHoveredEdgeInfo,
  PolygonPreviewNodePosition,
  PolygonDraggedNodeInfo
} from '../use-polygon-editing'

import type {
  HoveredSegmentInfo,
  PolylinePreviewNodePosition,
  PolylineDraggedNodeInfo
} from '../use-polyline-editing'

export interface MouseInteraction {
  handleWheel: (e: WheelEvent, canvas: HTMLElement | null, aspectRatioScale: AspectRatioScale) => void
  handleMouseDown: (e: MouseEvent) => void
  handleMouseMove: (e: MouseEvent) => void
  handleMouseUp: (e: MouseEvent) => void
  handleMouseLeave: () => void
  handleContextMenu: (e: MouseEvent, options?: { preventDefault?: boolean }) => void
  setView: (view: { zoom: number, offsetX: number, offsetY: number }) => void
  resetView: () => void
  resetActionState: () => void
  cleanup: () => void
  hasExceededMovementThreshold: (e: MouseEvent) => boolean
  shouldStartPanning: (e: MouseEvent, isDragging: boolean) => boolean
  startPanning: (e: MouseEvent) => void
  isPanning: () => boolean
  updatePanning: (e: MouseEvent, canvas: HTMLElement | null, aspectRatioScale: AspectRatioScale) => void
  endPanning: () => void
  hasMoved: () => boolean
  getCurrentAction: () => string
  view: View
  actionState: {
    action: string
    startPosition: { x: number, y: number } | null
  }
  panState: {
    isDragging: boolean
  }
}

export interface PolygonDrawing {
  handleMouseDown: (
    e: MouseEvent,
    getWorldCoords: GetWorldCoordsFunction,
    canvas: HTMLElement | null,
    aspectRatioScale: AspectRatioScale
  ) => boolean
  handleMouseMove: (
    e: MouseEvent,
    getWorldCoords: GetWorldCoordsFunction,
    canvas: HTMLElement | null,
    aspectRatioScale: AspectRatioScale
  ) => void
  handleDoubleClick: (e: MouseEvent) => void
  undoPolygonCreation: () => void
  redoPolygonCreation: () => void
  cancelPolygonCreation: () => void
  clearDrawing: () => void
  isActive: () => boolean
  currentPolygonPoints: Point[]
  previewPoint: { x: number | null, y: number | null }
  isInvalidPosition: Ref<boolean>
}

export interface PolylineDrawing {
  handleMouseDown: (
    e: MouseEvent,
    getWorldCoords: GetWorldCoordsFunction,
    canvas: HTMLElement | null,
    aspectRatioScale: AspectRatioScale
  ) => boolean
  handleMouseMove: (
    e: MouseEvent,
    getWorldCoords: GetWorldCoordsFunction,
    canvas: HTMLElement | null,
    aspectRatioScale: AspectRatioScale
  ) => void
  handleDoubleClick: (e: MouseEvent) => void
  clearDrawing: () => void
  isActive: () => boolean
  currentPolylinePoints: Point[]
  previewPoint: { x: number | null, y: number | null }
  isInvalidPosition: Ref<boolean>
}

export interface RectangleDrawing {
  handleMouseDown: (
    e: MouseEvent,
    getWorldCoords: GetWorldCoordsFunction,
    canvas: HTMLElement | null,
    view: View,
    aspectRatioScale: AspectRatioScale
  ) => boolean
  handleMouseMove: (
    e: MouseEvent,
    getWorldCoords: GetWorldCoordsFunction,
    canvas: HTMLElement | null,
    view: View,
    aspectRatioScale: AspectRatioScale
  ) => void
  clearDrawing: () => void
  isActive: () => boolean
  previewPoints: Point[]
  isInvalidPosition: Ref<boolean>
}

export interface PolygonEditing {
  handleMouseDown: (point: { x: number, y: number }, selectedPolygonIndex: Ref<number>, canvas: HTMLElement | null) => boolean
  handleMouseMove: (point: { x: number, y: number }) => void
  handleMouseUp: (canvas: HTMLElement | null) => void
  updateHoverStates: (point: { x: number, y: number }, selectedPolygonIndex: Ref<number>) => void
  handleSelection: (point: { x: number, y: number }, selectedPolygonIndex: Ref<number>, isDrawingMode: boolean) => void
  clearEditingState: () => void
  resetDragCompletionFlag: () => void
  isDragging: () => boolean
  hoveredPolygonIndex: Ref<number>
  hoveredNodeIndex: Ref<number>
  hoveredEdgeInfo: PolygonHoveredEdgeInfo
  previewNodePosition: PolygonPreviewNodePosition
  draggedNodeInfo: PolygonDraggedNodeInfo
  isInvalidPosition: Ref<boolean>
  justFinishedDragging: Ref<boolean>
}

export interface PolylineEditing {
  handleMouseDown: (point: { x: number, y: number }, selectedPolygonIndex: Ref<number>, canvas: HTMLElement | null) => boolean
  handleMouseMove: (point: { x: number, y: number }) => void
  handleMouseUp: (canvas: HTMLElement | null) => void
  updateHoverStates: (point: { x: number, y: number }, selectedPolygonIndex: Ref<number>) => void
  handleSelection: (point: { x: number, y: number }, selectedPolygonIndex: Ref<number>, isDrawingMode: boolean) => boolean
  clearEditingState: () => void
  resetDragCompletionFlag: () => void
  isDragging: () => boolean
  selectedPolylineIndex: Ref<number>
  hoveredPolylineIndex: Ref<number>
  hoveredNodeIndex: Ref<number>
  hoveredSegmentInfo: HoveredSegmentInfo
  draggedNodeInfo: PolylineDraggedNodeInfo
  isInvalidPosition: Ref<boolean>
  justFinishedDragging: Ref<boolean>
  previewNodePosition: PolylinePreviewNodePosition
}

export interface CanvasControls {
  viewMode?: Ref<string>
  handleUndo: () => void
  handleRedo: () => void
  isCutLineMode?: Ref<boolean>
  isCutPolygonMode?: Ref<boolean>
  isCutRectangleMode?: Ref<boolean>
  isCutMode?: Ref<boolean>
  cutDrawing?: CutDrawing
}

export interface CutDrawing {
  handleMouseDown: (
    e: MouseEvent,
    getWorldCoords: GetWorldCoordsFunction,
    canvas: HTMLCanvasElement,
    aspectRatioScale: AspectRatioScale,
    mode: 'line' | 'polygon' | 'rectangle'
  ) => boolean
  handleMouseMove: (
    e: MouseEvent,
    getWorldCoords: GetWorldCoordsFunction,
    canvas: HTMLCanvasElement,
    aspectRatioScale: AspectRatioScale,
    mode: 'line' | 'polygon' | 'rectangle'
  ) => void
  handleMouseUp: (
    e: MouseEvent,
    mode: 'line' | 'polygon' | 'rectangle'
  ) => boolean
  handleDoubleClick: (
    e: MouseEvent,
    mode: 'line' | 'polygon' | 'rectangle'
  ) => boolean
  handleEscape: () => void
  clearDrawing: () => void
  isActive: () => boolean
  currentPoints: Point[]
  previewPoint: { x: number | null, y: number | null }
  rectPreviewPoints: Point[]
  isInvalidPosition: Ref<boolean>
  isRectDrawing: Ref<boolean>
}

export interface EditorCommands {
  showContextMenuForPolygon: (event: MouseEvent, polygon: RenderablePolygon) => void
  showContextMenuForPolyline: (event: MouseEvent, polyline: RenderablePolyline) => void
}

export interface MarqueeContext {
  selectedPolygonIndex: number
  selectedPolylineIndex: number
  viewMode?: ViewMode
}
