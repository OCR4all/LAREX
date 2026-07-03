import type { ComputedRef, Ref } from 'vue'
import type { Commander } from '@/commands/editor/commander'
import type { CutDrawing } from '@/composables/editor/editor-interactions/types'
import type { DrawingMode, HistoryState, ViewMode } from '@/composables/editor/use-canvas-control'
import type { PolygonType } from '@/models/editor'
import type { SpatialIndexService } from '@/services/editor/spatial-index-service'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'

export type SelectionFocusMode = 'context' | 'fit-width' | 'none'

export interface SelectionFocusOptions {
  /**
   * Backward-compatible flag:
   * - undefined/true => keep default focus behavior
   * - false => no automatic focus adjustment
   */
  zoomToFit?: boolean
  /**
   * Explicit focus strategy.
   * If provided, takes precedence over `zoomToFit`.
   */
  focusMode?: SelectionFocusMode
}

export interface SetViewModeOptions {
  persistAsLayoutPreference?: boolean
}

export interface EditorCanvasViewState {
  zoom: number
  offsetX: number
  offsetY: number
}

export interface EditorCanvasControls {
  commander: Commander
  isCanvasEditable: ComputedRef<boolean>
  pageLockReason: ComputedRef<string | null>

  drawingMode: { value: DrawingMode }
  selectedPolygonIndex: Ref<number>
  constrainToImage: Ref<boolean>
  constrainToParent: Ref<boolean>
  autoSelect: Ref<boolean>
  regionType: Ref<PolygonType>
  viewMode: Ref<ViewMode>
  historyState: HistoryState

  isDrawingMode: ComputedRef<boolean>
  isMoveMode: ComputedRef<boolean>
  isPolygonMode: ComputedRef<boolean>
  isRectangleMode: ComputedRef<boolean>
  isPolylineMode: ComputedRef<boolean>
  isCutLineMode: ComputedRef<boolean>
  isCutPolygonMode: ComputedRef<boolean>
  isCutRectangleMode: ComputedRef<boolean>
  isCutMode: ComputedRef<boolean>

  toggleSelectMode: () => void
  toggleMoveMode: () => void
  togglePolygonMode: () => void
  toggleRectangleMode: () => void
  togglePolylineMode: () => void
  toggleCutLineMode: () => void
  toggleCutPolygonMode: () => void
  toggleCutRectangleMode: () => void
  handleUndo: () => void
  handleRedo: () => void
  jumpToHistory: (targetIndex: number) => boolean
  setConstrainToImage: (value: boolean) => void
  setConstrainToParent: (value: boolean) => void
  setAutoSelect: (value: boolean) => void
  setRegionType: (value: PolygonType) => void
  setViewMode: (value: ViewMode, options?: SetViewModeOptions) => void
  view?: EditorCanvasViewState
  setView?: (value: EditorCanvasViewState) => void
  resetView?: () => void
  addHoveredElementToReadingOrder?: () => boolean

  canUndo: ComputedRef<boolean>
  canRedo: ComputedRef<boolean>
  selectionInfo: ComputedRef<string>

  polygons?: RenderablePolygon[]
  polylines?: RenderablePolyline[]
  spatialIndex?: SpatialIndexService
  selectedPolylineIndex?: Ref<number>
  selectedPolygonIds?: Ref<string[]>
  selectedPolylineIds?: Ref<string[]>
  hiddenPolygonIds?: ComputedRef<string[]>
  hiddenPolylineIds?: ComputedRef<string[]>
  pageId?: ComputedRef<string | null>
  hoveredPolygonId?: Ref<string | null>
  hoveredPolylineId?: Ref<string | null>
  cutDrawing?: CutDrawing
  selectPolygonById?: (id: string | null, options?: SelectionFocusOptions) => void
  selectPolylineById?: (id: string | null, options?: SelectionFocusOptions) => void
  hoverPolygonById?: (id: string | null) => void
  hoverPolylineById?: (id: string | null) => void
  unhoverPolygon?: () => void
  unhoverPolyline?: () => void
}
