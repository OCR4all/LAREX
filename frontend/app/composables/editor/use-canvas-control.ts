import { Commander } from '@/commands'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import type { Command, CommandContext } from '@/commands/editor/types'
import { PolygonType } from '@/models/editor'
import { getEditorSession } from '@/session/editor/editor-session'

export const DRAWING_MODES = {
  SELECT: 'select',
  MOVE: 'move',
  POLYGON: 'polygon',
  RECTANGLE: 'rectangle',
  POLYLINE: 'polyline',
  CUT_LINE: 'cut-line',
  CUT_POLYGON: 'cut-polygon',
  CUT_RECTANGLE: 'cut-rectangle'
} as const

export type DrawingMode = typeof DRAWING_MODES[keyof typeof DRAWING_MODES]

export const VIEW_MODES = {
  DEFAULT: 'default',
  TEXTLINE: 'textline',
  BASELINE: 'baseline'
} as const

export type ViewMode = typeof VIEW_MODES[keyof typeof VIEW_MODES]

export interface HistoryState {
  canUndo: boolean
  canRedo: boolean
  currentIndex: number
  totalCount: number
}

export interface DrawingModeState {
  value: DrawingMode
}

type ViewModeSelectionRuntime = {
  selectedPolygonIndex?: Ref<number>
  selectedPolylineIndex?: Ref<number>
  selectedPolygonIds?: Ref<string[]>
  selectedPolylineIds?: Ref<string[]>
  selectPolygonById?: (id: string | null, options?: { zoomToFit?: boolean }) => void
  selectPolylineById?: (id: string | null, options?: { zoomToFit?: boolean }) => void
  unhoverPolygon?: () => void
  unhoverPolyline?: () => void
}

export function useCanvasControl(canvasId: string) {
  const commander = new Commander()
  const editorStore = useEditorStore()
  const editorUiStore = useEditorUiStore()
  const collaboration = useEditorCollaboration()
  const isCanvasEditable = computed(() => collaboration.canEditCanvas(canvasId))

  function getCommandContext(): CommandContext | undefined {
    const session = getEditorSession(canvasId)
    return session ? { canvasId, session } : undefined
  }

  const drawingMode: DrawingModeState = reactive({ value: DRAWING_MODES.SELECT })

  const selectedPolygonIndex = ref<number>(-1) // -1 = no selection

  const constrainToImage = ref<boolean>(true) // Default to true
  const constrainToParent = ref<boolean>(true) // Default to true

  const autoSelect = ref<boolean>(false) // Default to false

  const regionType = ref<PolygonType>(PolygonType.REGION) // Default to region

  const viewMode = ref<ViewMode>(editorUiStore.lastLayoutViewMode) // Default to last used layout view

  const historyState = reactive<HistoryState>({
    canUndo: false,
    canRedo: false,
    currentIndex: -1,
    totalCount: 0
  })

  const updateHistoryState = (): void => {
    historyState.canUndo = commander.canUndo()
    historyState.canRedo = commander.canRedo()
    const state = commander.getState()
    historyState.currentIndex = state.currentIndex
    historyState.totalCount = state.totalCount
    editorStore.updateCanvasHistoryState(canvasId, historyState.currentIndex)
  }

  updateHistoryState()

  const rawExecute = commander.execute.bind(commander)
  const rawUndo = commander.undo.bind(commander)
  const rawRedo = commander.redo.bind(commander)
  const rawJumpToHistory = commander.jumpToHistory.bind(commander)

  commander.execute = (command: Command, ctx?: CommandContext) => {
    if (!isCanvasEditable.value) return false
    const result = rawExecute(command, ctx)
    updateHistoryState()
    return result
  }

  commander.undo = (ctx?: CommandContext) => {
    if (!isCanvasEditable.value) return false
    const result = rawUndo(ctx)
    updateHistoryState()
    return result
  }

  commander.redo = (ctx?: CommandContext) => {
    if (!isCanvasEditable.value) return false
    const result = rawRedo(ctx)
    updateHistoryState()
    return result
  }

  commander.jumpToHistory = (targetIndex: number, ctx?: CommandContext) => {
    if (!isCanvasEditable.value) return false
    const result = rawJumpToHistory(targetIndex, ctx)
    updateHistoryState()
    return result
  }

  const toggleSelectMode = (): void => {
    drawingMode.value = DRAWING_MODES.SELECT
  }

  const toggleMoveMode = (): void => {
    if (!isCanvasEditable.value) return
    drawingMode.value = DRAWING_MODES.MOVE
  }

  const togglePolygonMode = (): void => {
    if (!isCanvasEditable.value) return
    drawingMode.value = DRAWING_MODES.POLYGON
  }

  const toggleRectangleMode = (): void => {
    if (!isCanvasEditable.value) return
    drawingMode.value = DRAWING_MODES.RECTANGLE
  }

  const togglePolylineMode = (): void => {
    if (!isCanvasEditable.value) return
    drawingMode.value = DRAWING_MODES.POLYLINE
  }

  const toggleCutLineMode = (): void => {
    if (!isCanvasEditable.value) return
    drawingMode.value = DRAWING_MODES.CUT_LINE
  }

  const toggleCutPolygonMode = (): void => {
    if (!isCanvasEditable.value) return
    drawingMode.value = DRAWING_MODES.CUT_POLYGON
  }

  const toggleCutRectangleMode = (): void => {
    if (!isCanvasEditable.value) return
    drawingMode.value = DRAWING_MODES.CUT_RECTANGLE
  }

  const handleUndo = (): void => {
    commander.undo(getCommandContext())
  }

  const handleRedo = (): void => {
    commander.redo(getCommandContext())
  }

  const jumpToHistory = (targetIndex: number): boolean => {
    return commander.jumpToHistory(targetIndex, getCommandContext())
  }

  const canUndo: ComputedRef<boolean> = computed(() => {
    return isCanvasEditable.value && historyState.canUndo
  })
  const canRedo: ComputedRef<boolean> = computed(() => {
    return isCanvasEditable.value && historyState.canRedo
  })

  const setConstrainToImage = (value: boolean): void => {
    constrainToImage.value = value
  }

  const setConstrainToParent = (value: boolean): void => {
    constrainToParent.value = value
  }

  const setAutoSelect = (value: boolean): void => {
    autoSelect.value = value
  }

  const setRegionType = (value: PolygonType): void => {
    regionType.value = value
  }

  const clearSelectionForViewModeChange = (): void => {
    const runtime = controls as typeof controls & ViewModeSelectionRuntime

    runtime.selectPolygonById?.(null, { zoomToFit: false })
    runtime.selectPolylineById?.(null, { zoomToFit: false })

    if (runtime.selectedPolygonIndex) runtime.selectedPolygonIndex.value = -1
    if (runtime.selectedPolylineIndex) runtime.selectedPolylineIndex.value = -1
    if (runtime.selectedPolygonIds) runtime.selectedPolygonIds.value = []
    if (runtime.selectedPolylineIds) runtime.selectedPolylineIds.value = []

    runtime.unhoverPolygon?.()
    runtime.unhoverPolyline?.()

    editorUiStore.setTemporaryHoverPolygonId(null)
    editorUiStore.setTemporaryHoverPolylineId(null)
    editorStore.clearCanvasSelection(canvasId)
  }

  const setViewMode = (value: ViewMode): void => {
    if (viewMode.value === value) return

    clearSelectionForViewModeChange()
    viewMode.value = value
    editorUiStore.setLastLayoutViewMode(value)
  }

  const isDrawingMode: ComputedRef<boolean> = computed(() => drawingMode.value !== DRAWING_MODES.SELECT && drawingMode.value !== DRAWING_MODES.MOVE)
  const isMoveMode: ComputedRef<boolean> = computed(() => drawingMode.value === DRAWING_MODES.MOVE)
  const isPolygonMode: ComputedRef<boolean> = computed(() => drawingMode.value === DRAWING_MODES.POLYGON)
  const isRectangleMode: ComputedRef<boolean> = computed(() => drawingMode.value === DRAWING_MODES.RECTANGLE)
  const isPolylineMode: ComputedRef<boolean> = computed(() => drawingMode.value === DRAWING_MODES.POLYLINE)
  const isCutLineMode: ComputedRef<boolean> = computed(() => drawingMode.value === DRAWING_MODES.CUT_LINE)
  const isCutPolygonMode: ComputedRef<boolean> = computed(() => drawingMode.value === DRAWING_MODES.CUT_POLYGON)
  const isCutRectangleMode: ComputedRef<boolean> = computed(() => drawingMode.value === DRAWING_MODES.CUT_RECTANGLE)
  const isCutMode: ComputedRef<boolean> = computed(() =>
    isCutLineMode.value || isCutPolygonMode.value || isCutRectangleMode.value
  )

  const selectionInfo: ComputedRef<string> = computed(() => {
    if (selectedPolygonIndex.value >= 0) {
      return `Polygon ${selectedPolygonIndex.value + 1} selected`
    }
    return `Mode: ${drawingMode.value.charAt(0).toUpperCase() + drawingMode.value.slice(1)}`
  })

  const controls = {
    commander,
    isCanvasEditable,

    drawingMode,
    selectedPolygonIndex,
    constrainToImage,
    constrainToParent,
    autoSelect,
    regionType,
    viewMode,
    historyState,

    isDrawingMode,
    isMoveMode,
    isPolygonMode,
    isRectangleMode,
    isPolylineMode,
    isCutLineMode,
    isCutPolygonMode,
    isCutRectangleMode,
    isCutMode,

    toggleSelectMode,
    toggleMoveMode,
    togglePolygonMode,
    toggleRectangleMode,
    togglePolylineMode,
    toggleCutLineMode,
    toggleCutPolygonMode,
    toggleCutRectangleMode,
    handleUndo,
    handleRedo,
    jumpToHistory,
    setConstrainToImage,
    setConstrainToParent,
    setAutoSelect,
    setRegionType,
    setViewMode,

    canUndo,
    canRedo,
    selectionInfo
  }

  return controls
}
