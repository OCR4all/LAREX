import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'

class TestMouseEvent {
  button: number
  clientX: number
  clientY: number
  shiftKey: boolean
  type: string

  constructor(type: string, init: MouseEventInit = {}) {
    this.type = type
    this.button = init.button ?? 0
    this.clientX = init.clientX ?? 0
    this.clientY = init.clientY ?? 0
    this.shiftKey = init.shiftKey ?? false
  }
}

Object.assign(globalThis, { MouseEvent: TestMouseEvent })

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

type Mode = 'select' | 'polygon' | 'rectangle' | 'polyline' | 'cut'

function eventStub(overrides: Partial<MouseEvent> = {}): MouseEvent {
  return {
    button: 0,
    clientX: 100,
    clientY: 100,
    shiftKey: false,
    ...overrides
  } as MouseEvent
}

function escapeEvent(timeStamp: number): KeyboardEvent {
  return {
    key: 'Escape',
    timeStamp,
    preventDefault: vi.fn()
  } as unknown as KeyboardEvent
}

async function loadUseEditorInteractions() {
  const module = await import('../use-editor-interactions')
  return module.useEditorInteractions
}

async function createHarness(
  mode: Mode,
  options: {
    polygons?: Array<{ id: string, points: Array<{ x: number, y: number }>, parentId?: string, type?: string }>
    polylines?: Array<{ id: string, points: Array<{ x: number, y: number }>, parentId?: string, type?: string }>
    readOnly?: boolean
  } = {}
) {
  const useEditorInteractions = await loadUseEditorInteractions()

  const canvas = ref<HTMLCanvasElement | null>({
    clientWidth: 200,
    clientHeight: 200,
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 200, bottom: 200, width: 200, height: 200 })
  } as HTMLCanvasElement)
  const view = reactive({ zoom: 1, offsetX: 0, offsetY: 0 })
  const aspectRatioScale = ref({ scaleX: 1, scaleY: 1 })

  const selectedPolygonIndex = ref(-1)
  const selectedPolylineIndex = ref(-1)
  const selectedPolygonIds = ref<string[]>([])
  const selectedPolylineIds = ref<string[]>([])
  const hiddenPolygonIds = ref<string[]>([])
  const hiddenPolylineIds = ref<string[]>([])
  const polygons = reactive(options.polygons ?? [])
  const polylines = reactive(options.polylines ?? [])

  const isPolygonMode = ref(mode === 'polygon')
  const isRectangleMode = ref(mode === 'rectangle')
  const isPolylineMode = ref(mode === 'polyline')
  const isMoveMode = ref(false)
  const isDrawingMode = ref(mode !== 'select')

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
    hoveredEdgeInfo: reactive({ polygonIndex: -1, edgeStartIndex: -1, t: 0 }),
    previewNodePosition: reactive({ x: null, y: null }),
    draggedNodeInfo: reactive({ polygonIndex: -1, nodeIndex: -1, isDragging: false }),
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
    hoveredSegmentInfo: reactive({ polylineIndex: -1, segmentIndex: -1, distance: Number.POSITIVE_INFINITY, closestPoint: null }),
    draggedNodeInfo: reactive({ polylineIndex: -1, nodeIndex: -1, isDragging: false, originalPoint: null }),
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
    isCanvasEditable: ref(options.readOnly !== true),
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
    showContextMenuForPolyline: vi.fn(),
    showContextMenuForCanvas: vi.fn()
  }

  const stateActions = {
    clearSelectionSet: vi.fn(() => {
      selectedPolygonIds.value = []
      selectedPolylineIds.value = []
    }),
    replacePolygonSelection: vi.fn((ids: string[]) => {
      selectedPolylineIds.value = []
      selectedPolygonIds.value = ids
    }),
    replacePolylineSelection: vi.fn((ids: string[]) => {
      selectedPolygonIds.value = []
      selectedPolylineIds.value = ids
    }),
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
    polygons,
    polylines,
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
    cutDrawing,
    canvasControls,
    polygonEditing,
    polylineEditing,
    editorCommands,
    stateActions,
    selectedPolygonIndex,
    selectedPolylineIndex,
    selectedPolygonIds,
    selectedPolylineIds
  }
}

describe('useEditorInteractions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each([
    ['polygon'],
    ['rectangle'],
    ['polyline'],
    ['cut']
  ] as const)('routes Shift to pan and preserves normal drawing in %s mode', async (mode) => {
    const shifted = await createHarness(mode)
    shifted.interactions.onMouseDown(eventStub({ shiftKey: true, clientX: 10, clientY: 10 }))
    shifted.interactions.onMouseMove(eventStub({ shiftKey: true, clientX: 30, clientY: 30 }))
    shifted.interactions.onMouseMove(eventStub({ shiftKey: false, clientX: 50, clientY: 50 }))

    expect(shifted.mouseInteraction.handleMouseDown).toHaveBeenCalledOnce()
    expect(shifted.mouseInteraction.startPanning).toHaveBeenCalled()
    expect(shifted.mouseInteraction.updatePanning).toHaveBeenCalledTimes(2)
    for (const drawing of [shifted.polygonDrawing, shifted.rectangleDrawing, shifted.polylineDrawing, shifted.cutDrawing]) {
      expect(drawing.handleMouseDown).not.toHaveBeenCalled()
      expect(drawing.handleMouseMove).not.toHaveBeenCalled()
    }

    const normal = await createHarness(mode)
    normal.interactions.onMouseDown(eventStub())
    normal.interactions.onMouseMove(eventStub())
    const activeDrawing = {
      polygon: normal.polygonDrawing,
      rectangle: normal.rectangleDrawing,
      polyline: normal.polylineDrawing,
      cut: normal.cutDrawing
    }[mode]
    expect(activeDrawing.handleMouseDown).toHaveBeenCalledOnce()
    expect(activeDrawing.handleMouseMove).toHaveBeenCalledOnce()
  })

  it('does not run command undo from the canvas key handler when no drawing is active', async () => {
    const harness = await createHarness('polygon')
    const event = {
      key: 'z',
      ctrlKey: true,
      metaKey: false,
      preventDefault: vi.fn(),
      stopImmediatePropagation: vi.fn(),
      stopPropagation: vi.fn()
    } as unknown as KeyboardEvent

    harness.interactions.onKeyDown(event)

    expect(harness.canvasControls.handleUndo).not.toHaveBeenCalled()
    expect(event.preventDefault).not.toHaveBeenCalled()
    expect(event.stopImmediatePropagation).not.toHaveBeenCalled()
  })

  it('keeps in-progress polygon drawing undo local to the canvas key handler', async () => {
    const harness = await createHarness('polygon')
    harness.polygonDrawing.isActive.mockReturnValue(true)
    const event = {
      key: 'z',
      ctrlKey: true,
      metaKey: false,
      preventDefault: vi.fn(),
      stopImmediatePropagation: vi.fn(),
      stopPropagation: vi.fn()
    } as unknown as KeyboardEvent

    harness.interactions.onKeyDown(event)

    expect(harness.polygonDrawing.undoPolygonCreation).toHaveBeenCalledOnce()
    expect(harness.canvasControls.handleUndo).not.toHaveBeenCalled()
    expect(event.preventDefault).toHaveBeenCalledOnce()
    expect(event.stopImmediatePropagation).toHaveBeenCalledOnce()
  })

  it('allows read-only canvases to pan without invoking edit or drawing handlers', async () => {
    const harness = await createHarness('select', { readOnly: true })

    harness.interactions.onMouseDown(eventStub({ clientX: 10, clientY: 10 }))
    harness.interactions.onMouseMove(eventStub({ clientX: 40, clientY: 40 }))

    expect(harness.mouseInteraction.startPanning).toHaveBeenCalledOnce()
    expect(harness.mouseInteraction.updatePanning).toHaveBeenCalledOnce()
    expect(harness.polygonDrawing.handleMouseDown).not.toHaveBeenCalled()
    expect(harness.rectangleDrawing.handleMouseDown).not.toHaveBeenCalled()
    expect(harness.polylineDrawing.handleMouseDown).not.toHaveBeenCalled()
    expect(harness.polygonEditing.handleMouseDown).not.toHaveBeenCalled()
    expect(harness.polylineEditing.handleMouseDown).not.toHaveBeenCalled()
  })

  it('allows read-only canvases to zoom with the wheel', async () => {
    const harness = await createHarness('select', { readOnly: true })
    const event = eventStub() as WheelEvent

    harness.interactions.onWheel(event)

    expect(harness.mouseInteraction.handleWheel).toHaveBeenCalledOnce()
    expect(harness.mouseInteraction.handleWheel).toHaveBeenCalledWith(
      event,
      expect.anything(),
      { scaleX: 1, scaleY: 1 }
    )
  })

  it('shows polygon hover feedback on read-only canvases without invoking edit hover handlers', async () => {
    const harness = await createHarness('select', {
      readOnly: true,
      polygons: [
        {
          id: 'region-a',
          type: 'region',
          points: [
            { x: -0.5, y: -0.5 },
            { x: 0.5, y: -0.5 },
            { x: 0.5, y: 0.5 },
            { x: -0.5, y: 0.5 }
          ]
        }
      ]
    })

    harness.interactions.onMouseMove(eventStub({ clientX: 100, clientY: 100 }))

    expect(harness.stateActions.setHoveredPolygonId).toHaveBeenLastCalledWith('region-a')
    expect(harness.stateActions.setHoveredPolylineId).toHaveBeenLastCalledWith(null)
    expect(harness.polygonEditing.hoveredPolygonIndex.value).toBe(0)
    expect(harness.polylineEditing.hoveredPolylineIndex.value).toBe(-1)
    expect(harness.polygonEditing.updateHoverStates).not.toHaveBeenCalled()
    expect(harness.polylineEditing.updateHoverStates).not.toHaveBeenCalled()
  })

  it('shows baseline hover feedback on read-only canvases', async () => {
    const harness = await createHarness('select', {
      readOnly: true,
      polylines: [
        {
          id: 'baseline:line-a',
          type: 'baseline',
          points: [
            { x: -0.5, y: 0 },
            { x: 0.5, y: 0 }
          ]
        }
      ]
    })
    harness.canvasControls.viewMode.value = 'baseline'

    harness.interactions.onMouseMove(eventStub({ clientX: 100, clientY: 100 }))

    expect(harness.stateActions.setHoveredPolylineId).toHaveBeenLastCalledWith('baseline:line-a')
    expect(harness.stateActions.setHoveredPolygonId).toHaveBeenLastCalledWith(null)
    expect(harness.polylineEditing.hoveredPolylineIndex.value).toBe(0)
    expect(harness.polygonEditing.hoveredPolygonIndex.value).toBe(-1)
  })

  it('clears read-only hover feedback when the pointer leaves the canvas', async () => {
    const harness = await createHarness('select', {
      readOnly: true,
      polygons: [
        {
          id: 'region-a',
          type: 'region',
          points: [
            { x: -0.5, y: -0.5 },
            { x: 0.5, y: -0.5 },
            { x: 0.5, y: 0.5 },
            { x: -0.5, y: 0.5 }
          ]
        }
      ]
    })

    harness.interactions.onMouseMove(eventStub({ clientX: 100, clientY: 100 }))
    harness.interactions.onMouseLeave()

    expect(harness.polygonEditing.hoveredPolygonIndex.value).toBe(-1)
    expect(harness.polylineEditing.hoveredPolylineIndex.value).toBe(-1)
    expect(harness.stateActions.setHoveredPolygonId).toHaveBeenLastCalledWith(null)
    expect(harness.stateActions.setHoveredPolylineId).toHaveBeenLastCalledWith(null)
  })

  it('allows read-only canvases to select and drill down through existing polygons', async () => {
    const harness = await createHarness('select', {
      readOnly: true,
      polygons: [
        {
          id: 'region-a',
          type: 'region',
          points: [
            { x: -0.5, y: -0.5 },
            { x: 0.5, y: -0.5 },
            { x: 0.5, y: 0.5 },
            { x: -0.5, y: 0.5 }
          ]
        }
      ]
    })
    harness.polygonEditing.handleSelection.mockImplementation(() => {
      harness.selectedPolygonIndex.value = 0
      return true
    })

    harness.interactions.onMouseDown(eventStub({ clientX: 100, clientY: 100 }))
    harness.interactions.onMouseUp(eventStub({ clientX: 100, clientY: 100 }))

    expect(harness.polygonEditing.handleSelection).toHaveBeenCalledOnce()
    expect(harness.stateActions.replacePolygonSelection).toHaveBeenLastCalledWith(['region-a'])
  })

  it('allows read-only canvases to select existing baselines without exposing edit menus', async () => {
    const harness = await createHarness('select', {
      readOnly: true,
      polylines: [
        {
          id: 'baseline:line-a',
          type: 'baseline',
          points: [
            { x: -0.5, y: 0 },
            { x: 0.5, y: 0 }
          ]
        }
      ]
    })
    harness.canvasControls.viewMode.value = 'baseline'
    harness.polylineEditing.handleSelection.mockImplementation(() => {
      harness.selectedPolylineIndex.value = 0
      return true
    })

    harness.interactions.onMouseDown(eventStub({ clientX: 100, clientY: 100 }))
    harness.interactions.onMouseUp(eventStub({ clientX: 100, clientY: 100 }))
    harness.interactions.handleCanvasContextMenu(eventStub({ clientX: 100, clientY: 100 }))

    expect(harness.polylineEditing.handleSelection).toHaveBeenCalledOnce()
    expect(harness.stateActions.replacePolylineSelection).toHaveBeenLastCalledWith(['baseline:line-a'])
    expect(harness.editorCommands.showContextMenuForPolyline).not.toHaveBeenCalled()
    expect(harness.editorCommands.showContextMenuForCanvas).not.toHaveBeenCalled()
  })

  it('moves one polygon level up on a normal Escape press', async () => {
    const harness = await createHarness('polygon', {
      polygons: [
        { id: 'region-root', type: 'region', points: [] },
        { id: 'line-a', type: 'textline', parentId: 'region-root', points: [] }
      ]
    })
    harness.selectedPolygonIndex.value = 1
    harness.selectedPolygonIds.value = ['line-a']
    const event = escapeEvent(1000)

    harness.interactions.onKeyDown(event)

    expect(event.preventDefault).toHaveBeenCalledOnce()
    expect(harness.selectedPolygonIndex.value).toBe(0)
    expect(harness.selectedPolylineIndex.value).toBe(-1)
    expect(harness.stateActions.replacePolygonSelection).toHaveBeenLastCalledWith(['region-root'])
  })

  it('moves one polygon level up when only the selected polygon ID is set', async () => {
    const harness = await createHarness('polygon', {
      polygons: [
        { id: 'region-root', type: 'region', points: [] },
        { id: 'region-child', type: 'region', parentId: 'region-root', points: [] },
        { id: 'line-a', type: 'textline', parentId: 'region-child', points: [] }
      ]
    })
    harness.selectedPolygonIndex.value = -1
    harness.selectedPolygonIds.value = ['line-a']
    const event = escapeEvent(1000)

    harness.interactions.onKeyDown(event)

    expect(harness.selectedPolygonIndex.value).toBe(1)
    expect(harness.selectedPolylineIndex.value).toBe(-1)
    expect(harness.stateActions.replacePolygonSelection).toHaveBeenLastCalledWith(['region-child'])
  })

  it('moves from a selected baseline to its textline on a normal Escape press', async () => {
    const harness = await createHarness('polyline', {
      polygons: [
        { id: 'line-a', type: 'textline', points: [] }
      ],
      polylines: [
        { id: 'baseline:line-a', type: 'baseline', parentId: 'line-a', points: [] }
      ]
    })
    harness.canvasControls.viewMode.value = 'baseline'
    harness.selectedPolylineIndex.value = 0
    harness.selectedPolylineIds.value = ['baseline:line-a']
    const event = escapeEvent(1000)

    harness.interactions.onKeyDown(event)

    expect(harness.selectedPolygonIndex.value).toBe(0)
    expect(harness.selectedPolylineIndex.value).toBe(-1)
    expect(harness.stateActions.replacePolygonSelection).toHaveBeenLastCalledWith(['line-a'])
    expect(harness.stateActions.replacePolylineSelection).not.toHaveBeenCalled()
  })

  it('clears directly to root level on fast double Escape', async () => {
    const harness = await createHarness('polygon', {
      polygons: [
        { id: 'region-root', type: 'region', points: [] },
        { id: 'line-a', type: 'textline', parentId: 'region-root', points: [] }
      ]
    })
    harness.selectedPolygonIndex.value = 1
    harness.selectedPolygonIds.value = ['line-a']

    harness.interactions.onKeyDown(escapeEvent(1000))
    harness.interactions.onKeyDown(escapeEvent(1100))

    expect(harness.selectedPolygonIndex.value).toBe(-1)
    expect(harness.selectedPolylineIndex.value).toBe(-1)
    expect(harness.stateActions.clearSelectionSet).toHaveBeenCalled()
  })
})
