import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, reactive, ref } from 'vue'

;(globalThis as { ref?: typeof ref }).ref = ref
;(globalThis as { reactive?: typeof reactive }).reactive = reactive
;(globalThis as { computed?: typeof computed }).computed = computed

const editorUiStoreMock = vi.hoisted(() => ({
  relationsEditor: {
    pickerMode: 'idle'
  },
  cancelRelationPicking: vi.fn(),
  updateRelationDraft: vi.fn(),
  setRelationPickerMode: vi.fn(),
  setRelationPickerRegionId: vi.fn()
}))

vi.mock('@/stores/editor/editor.ui.store', () => ({
  useEditorUiStore: () => editorUiStoreMock
}))

type Mode = 'polygon' | 'rectangle' | 'polyline' | 'cut'

function eventStub(overrides: Partial<MouseEvent> = {}): MouseEvent {
  return {
    button: 0,
    clientX: 100,
    clientY: 100,
    shiftKey: false,
    ...overrides
  } as MouseEvent
}

async function loadUseEditorInteractions() {
  const module = await import('../use-editor-interactions')
  return module.useEditorInteractions
}

async function createHarness(mode: Mode) {
  const useEditorInteractions = await loadUseEditorInteractions()

  const canvas = ref<HTMLCanvasElement | null>({} as HTMLCanvasElement)
  const view = reactive({ zoom: 1, offsetX: 0, offsetY: 0 })
  const aspectRatioScale = ref({ scaleX: 1, scaleY: 1 })

  const selectedPolygonIndex = ref(-1)
  const selectedPolylineIndex = ref(-1)
  const selectedPolygonIds = ref<string[]>([])
  const selectedPolylineIds = ref<string[]>([])
  const hiddenPolygonIds = ref<string[]>([])
  const hiddenPolylineIds = ref<string[]>([])

  const isPolygonMode = ref(mode === 'polygon')
  const isRectangleMode = ref(mode === 'rectangle')
  const isPolylineMode = ref(mode === 'polyline')
  const isMoveMode = ref(false)
  const isDrawingMode = ref(true)

  const actionState = reactive<{ action: string, startPosition: { x: number, y: number } | null }>({
    action: 'idle',
    startPosition: null
  })
  const panState = reactive({ isDragging: false })

  const mouseInteraction = {
    handleWheel: vi.fn(),
    handleMouseDown: vi.fn((e: MouseEvent) => {
      actionState.startPosition = { x: e.clientX, y: e.clientY }
    }),
    handleMouseMove: vi.fn(),
    handleMouseUp: vi.fn(() => {
      actionState.startPosition = null
    }),
    handleMouseLeave: vi.fn(),
    handleContextMenu: vi.fn(),
    setView: vi.fn(),
    resetView: vi.fn(),
    resetActionState: vi.fn(),
    cleanup: vi.fn(),
    hasExceededMovementThreshold: vi.fn(() => true),
    shouldStartPanning: vi.fn(() => actionState.startPosition !== null && !panState.isDragging),
    startPanning: vi.fn(() => {
      panState.isDragging = true
      actionState.action = 'panning'
    }),
    isPanning: vi.fn(() => panState.isDragging),
    updatePanning: vi.fn(),
    endPanning: vi.fn(() => {
      panState.isDragging = false
      actionState.action = 'idle'
    }),
    hasMoved: vi.fn(() => panState.isDragging),
    getCurrentAction: vi.fn(() => actionState.action),
    view,
    actionState,
    panState
  }

  const polygonDrawing = {
    handleMouseDown: vi.fn(() => true),
    handleMouseMove: vi.fn(),
    handleDoubleClick: vi.fn(),
    undoPolygonCreation: vi.fn(),
    redoPolygonCreation: vi.fn(),
    cancelPolygonCreation: vi.fn(),
    clearDrawing: vi.fn(),
    isActive: vi.fn(() => false),
    currentPolygonPoints: [],
    previewPoint: { x: null, y: null },
    isInvalidPosition: ref(false)
  }

  const polylineDrawing = {
    handleMouseDown: vi.fn(() => true),
    handleMouseMove: vi.fn(),
    handleDoubleClick: vi.fn(),
    clearDrawing: vi.fn(),
    isActive: vi.fn(() => false),
    currentPolylinePoints: [],
    previewPoint: { x: null, y: null },
    isInvalidPosition: ref(false)
  }

  const rectangleDrawing = {
    handleMouseDown: vi.fn(() => true),
    handleMouseMove: vi.fn(),
    clearDrawing: vi.fn(),
    isActive: vi.fn(() => false),
    previewPoints: [],
    isInvalidPosition: ref(false)
  }

  const polygonEditing = {
    handleMouseDown: vi.fn(() => false),
    handleMouseMove: vi.fn(),
    handleMouseUp: vi.fn(),
    updateHoverStates: vi.fn(),
    handleSelection: vi.fn(),
    cancelCurrentOperation: vi.fn(),
    clearEditingState: vi.fn(),
    resetDragCompletionFlag: vi.fn(),
    isDragging: vi.fn(() => false),
    hoveredPolygonIndex: ref(-1),
    hoveredNodeIndex: ref(-1),
    hoveredEdgeInfo: reactive({ edgeIndex: -1, projectedPoint: null, distanceSq: Number.POSITIVE_INFINITY }),
    previewNodePosition: reactive({ x: null, y: null }),
    draggedNodeInfo: reactive({ nodeIndex: -1, parentPolygonIndex: -1, isDragging: false }),
    isInvalidPosition: ref(false),
    justFinishedDragging: ref(false)
  }

  const polylineEditing = {
    handleMouseDown: vi.fn(() => false),
    handleMouseMove: vi.fn(),
    handleMouseUp: vi.fn(),
    updateHoverStates: vi.fn(),
    handleSelection: vi.fn(() => false),
    cancelCurrentOperation: vi.fn(),
    clearEditingState: vi.fn(),
    resetDragCompletionFlag: vi.fn(),
    isDragging: vi.fn(() => false),
    selectedPolylineIndex,
    hoveredPolylineIndex: ref(-1),
    hoveredNodeIndex: ref(-1),
    hoveredSegmentInfo: reactive({ segmentIndex: -1, projectedPoint: null, distanceSq: Number.POSITIVE_INFINITY }),
    draggedNodeInfo: reactive({ nodeIndex: -1, parentPolylineIndex: -1, isDragging: false }),
    isInvalidPosition: ref(false),
    justFinishedDragging: ref(false),
    previewNodePosition: reactive({ x: null, y: null })
  }

  const cutDrawing = {
    handleMouseDown: vi.fn(() => true),
    handleMouseMove: vi.fn(),
    handleMouseUp: vi.fn(() => false),
    handleDoubleClick: vi.fn(() => false),
    handleEscape: vi.fn(),
    clearDrawing: vi.fn(),
    isActive: vi.fn(() => false),
    currentPoints: [],
    previewPoint: { x: null, y: null },
    rectPreviewPoints: [],
    isInvalidPosition: ref(false),
    isRectDrawing: ref(false)
  }

  const canvasControls = {
    viewMode: ref('default'),
    handleUndo: vi.fn(),
    handleRedo: vi.fn(),
    isCutLineMode: ref(mode === 'cut'),
    isCutPolygonMode: ref(false),
    isCutRectangleMode: ref(false),
    isCutMode: ref(mode === 'cut'),
    cutDrawing
  }

  const editorCommands = {
    showContextMenuForPolygon: vi.fn(),
    showContextMenuForPolyline: vi.fn()
  }

  const stateActions = {
    clearSelectionSet: vi.fn(),
    replacePolygonSelection: vi.fn(),
    replacePolylineSelection: vi.fn(),
    addPolygonSelection: vi.fn(),
    addPolylineSelection: vi.fn(),
    togglePolygonSelection: vi.fn(),
    togglePolylineSelection: vi.fn(),
    setHoveredPolygonId: vi.fn(),
    setHoveredPolylineId: vi.fn()
  }

  const interactions = useEditorInteractions(
    canvas,
    view,
    aspectRatioScale,
    [],
    [],
    selectedPolygonIndex,
    selectedPolylineIndex,
    selectedPolygonIds,
    selectedPolylineIds,
    hiddenPolygonIds,
    hiddenPolylineIds,
    isPolygonMode,
    isRectangleMode,
    isPolylineMode,
    isDrawingMode,
    isMoveMode,
    ref('region'),
    mouseInteraction,
    polygonDrawing,
    polylineDrawing,
    rectangleDrawing,
    polygonEditing,
    polylineEditing,
    editorCommands,
    canvasControls,
    ref({ width: 1000, height: 1000 }),
    undefined,
    stateActions
  )

  return {
    interactions,
    mouseInteraction,
    polygonDrawing,
    rectangleDrawing,
    polylineDrawing,
    cutDrawing
  }
}

describe('useEditorInteractions shift-pan routing', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each([
    ['polygon'],
    ['rectangle'],
    ['polyline'],
    ['cut']
  ] as const)('does not run drawing mouse down handlers when Shift is held in %s mode', async (mode) => {
    const harness = await createHarness(mode)

    harness.interactions.onMouseDown(eventStub({ shiftKey: true }))

    expect(harness.mouseInteraction.handleMouseDown).toHaveBeenCalledOnce()
    expect(harness.polygonDrawing.handleMouseDown).not.toHaveBeenCalled()
    expect(harness.rectangleDrawing.handleMouseDown).not.toHaveBeenCalled()
    expect(harness.polylineDrawing.handleMouseDown).not.toHaveBeenCalled()
    expect(harness.cutDrawing.handleMouseDown).not.toHaveBeenCalled()
  })

  it.each([
    ['polygon'],
    ['rectangle'],
    ['polyline'],
    ['cut']
  ] as const)('routes Shift+drag to pan and suppresses drawing move handlers in %s mode', async (mode) => {
    const harness = await createHarness(mode)

    harness.interactions.onMouseDown(eventStub({ shiftKey: true, clientX: 10, clientY: 10 }))
    harness.interactions.onMouseMove(eventStub({ shiftKey: true, clientX: 30, clientY: 30 }))
    harness.interactions.onMouseMove(eventStub({ shiftKey: false, clientX: 50, clientY: 50 }))

    expect(harness.mouseInteraction.startPanning).toHaveBeenCalled()
    expect(harness.mouseInteraction.updatePanning).toHaveBeenCalledTimes(2)
    expect(harness.polygonDrawing.handleMouseMove).not.toHaveBeenCalled()
    expect(harness.rectangleDrawing.handleMouseMove).not.toHaveBeenCalled()
    expect(harness.polylineDrawing.handleMouseMove).not.toHaveBeenCalled()
    expect(harness.cutDrawing.handleMouseMove).not.toHaveBeenCalled()
  })

  it.each([
    ['polygon'],
    ['rectangle'],
    ['polyline'],
    ['cut']
  ] as const)('keeps non-Shift behavior unchanged in %s mode', async (mode) => {
    const harness = await createHarness(mode)

    harness.interactions.onMouseDown(eventStub({ shiftKey: false }))
    harness.interactions.onMouseMove(eventStub({ shiftKey: false }))

    if (mode === 'polygon') {
      expect(harness.polygonDrawing.handleMouseDown).toHaveBeenCalledOnce()
      expect(harness.polygonDrawing.handleMouseMove).toHaveBeenCalledOnce()
    } else if (mode === 'rectangle') {
      expect(harness.rectangleDrawing.handleMouseDown).toHaveBeenCalledOnce()
      expect(harness.rectangleDrawing.handleMouseMove).toHaveBeenCalledOnce()
    } else if (mode === 'polyline') {
      expect(harness.polylineDrawing.handleMouseDown).toHaveBeenCalledOnce()
      expect(harness.polylineDrawing.handleMouseMove).toHaveBeenCalledOnce()
    } else {
      expect(harness.cutDrawing.handleMouseDown).toHaveBeenCalledOnce()
      expect(harness.cutDrawing.handleMouseMove).toHaveBeenCalledOnce()
    }
  })
})
