import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { Commander } from '@/commands'
import { usePolygonEditing } from '../use-polygon-editing'
import { usePolylineEditing } from '../use-polyline-editing'

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
    buttons: 1,
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
    realEditing?: boolean
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

  if (options.realEditing) {
    const commander = new Commander()
    Object.assign(polygonEditing, usePolygonEditing(
      polygons, view, aspectRatioScale, ref(false), undefined, ref(false), undefined,
      selectedPolylineIndex, canvasControls.viewMode, commander, 'hit-priority-test', hiddenPolygonIds, hiddenPolylineIds
    ))
    Object.assign(polylineEditing, usePolylineEditing(
      polylines, view, aspectRatioScale, ref(false), undefined, polygons, ref(false), undefined,
      selectedPolygonIndex, selectedPolylineIndex, canvasControls.viewMode, commander, 'hit-priority-test', hiddenPolygonIds, hiddenPolylineIds
    ))
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
    view,
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

  const overlappingShapes = () => ({
    realEditing: true,
    polygons: [{ id: 'line', type: 'textline', points: [
      { x: -0.5, y: 0.5 }, { x: 0.5, y: 0.5 }, { x: 0.5, y: -0.5 },
      { x: 0, y: -0.5 }, { x: -0.5, y: -0.5 }
    ] }],
    polylines: [{ id: 'baseline:line', parentId: 'line', points: [
      { x: -0.5, y: -0.499 }, { x: 0.5, y: -0.499 }
    ] }]
  })

  it.each([['node', 100], ['edge', 125]] as const)('edits a selected polygon %s inside the baseline hit area', async (_target, clientX) => {
    const shapes = overlappingShapes()
    // Place the baseline inside the widened stroke hit area, beyond its
    // narrower node tolerance, while the pointer targets the polygon bottom.
    shapes.polylines[0]!.points.forEach((point) => {
      point.y = -0.485
    })
    const harness = await createHarness('select', shapes)
    harness.selectedPolygonIndex.value = 0
    const pointer = eventStub({ clientX, clientY: 150, buttons: 0 })
    harness.interactions.onMouseMove(pointer)
    harness.interactions.onMouseDown(pointer)

    expect(harness.polygonEditing.isDragging()).toBe(true)
    expect(harness.polylineEditing.isDragging()).toBe(false)
    expect(harness.selectedPolylineIndex.value).toBe(-1)
    expect(harness.polylineEditing.hoveredPolylineIndex.value).toBe(-1)
    harness.polygonEditing.cancelCurrentOperation()
  })

  it('keeps an already-selected baseline editable beside a polygon node', async () => {
    const harness = await createHarness('select', overlappingShapes())
    harness.selectedPolygonIndex.value = 0
    harness.selectedPolylineIndex.value = 0
    const pointer = eventStub({ clientX: 50, clientY: 149.9, buttons: 0 })
    harness.interactions.onMouseMove(pointer)
    harness.interactions.onMouseDown(pointer)

    expect(harness.polylineEditing.isDragging()).toBe(true)
    expect(harness.polygonEditing.isDragging()).toBe(false)
    harness.polylineEditing.cancelCurrentOperation()
  })

  it('keeps baselines selectable away from polygon handles', async () => {
    const shapes = overlappingShapes()
    shapes.polylines[0]!.points = [{ x: -0.5, y: -0.3 }, { x: 0.5, y: -0.3 }]
    const harness = await createHarness('select', shapes)
    harness.selectedPolygonIndex.value = 0
    const pointer = eventStub({ clientX: 100, clientY: 130, buttons: 0 })
    harness.interactions.onMouseMove(pointer)
    harness.interactions.onMouseDown(pointer)

    expect(harness.selectedPolylineIndex.value).toBe(0)
    expect(harness.polygonEditing.isDragging()).toBe(false)
  })

  it.each([
    [1, false], [10, false], [1, true], [10, true]
  ] as const)('keeps baseline screen hit tolerance constant at zoom %i (selected: %s)', async (zoom, selected) => {
    const shapes = overlappingShapes()
    shapes.polylines[0]!.points = [{ x: -0.5, y: 0 }, { x: 0.5, y: 0 }]
    const harness = await createHarness('select', shapes)
    harness.selectedPolygonIndex.value = 0
    harness.selectedPolylineIndex.value = selected ? 0 : -1
    harness.view.zoom = zoom

    // On this 200px canvas the baseline is at y=100. Hover and selection
    // share a 2px tolerance that stays constant while zooming.
    harness.interactions.onMouseMove(eventStub({ clientX: 100, clientY: 97, buttons: 0 }))
    expect(harness.polylineEditing.hoveredPolylineIndex.value).toBe(-1)

    const hit = eventStub({ clientX: 100, clientY: 98.5, buttons: 0 })
    harness.interactions.onMouseMove(hit)
    expect(harness.polylineEditing.hoveredPolylineIndex.value).toBe(0)
    harness.interactions.onMouseDown(hit)
    harness.interactions.onMouseUp(hit)
    expect(harness.selectedPolylineIndex.value).toBe(0)
    expect(harness.polylineEditing.isDragging()).toBe(false)
    expect(shapes.polylines[0]!.points).toHaveLength(2)
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

  it('cancels a marquee when a move reveals that the primary button was released', async () => {
    const harness = await createHarness('select')

    harness.interactions.onMouseDown(eventStub({ shiftKey: true, clientX: 10, clientY: 10 }))
    harness.interactions.onMouseMove(eventStub({ shiftKey: true, clientX: 40, clientY: 40 }))

    expect(harness.interactions.isMarqueeSelecting.value).toBe(true)
    expect(harness.interactions.marqueeRectPx.value).not.toBeNull()

    harness.interactions.onMouseMove(eventStub({ buttons: 0, clientX: 60, clientY: 60 }))

    expect(harness.interactions.isMarqueeSelecting.value).toBe(false)
    expect(harness.interactions.marqueeRectPx.value).toBeNull()
    expect(harness.mouseInteraction.resetActionState).toHaveBeenCalledOnce()
  })

  it('allows an interrupted marquee to be cancelled explicitly', async () => {
    const harness = await createHarness('select')

    harness.interactions.onMouseDown(eventStub({ shiftKey: true, clientX: 10, clientY: 10 }))
    harness.interactions.onMouseMove(eventStub({ shiftKey: true, clientX: 40, clientY: 40 }))

    expect(harness.interactions.cancelMarquee()).toBe(true)
    expect(harness.interactions.isMarqueeSelecting.value).toBe(false)
    expect(harness.interactions.marqueeRectPx.value).toBeNull()
    expect(harness.interactions.cancelMarquee()).toBe(false)
  })

  it('cancels rather than completing a marquee when the pointer leaves the window', async () => {
    const harness = await createHarness('select')

    harness.interactions.onMouseDown(eventStub({ shiftKey: true, clientX: 10, clientY: 10 }))
    harness.interactions.onMouseMove(eventStub({ shiftKey: true, clientX: 40, clientY: 40 }))
    harness.interactions.onMouseLeave()

    expect(harness.interactions.isMarqueeSelecting.value).toBe(false)
    expect(harness.interactions.marqueeRectPx.value).toBeNull()
    expect(harness.stateActions.addPolygonSelection).not.toHaveBeenCalled()
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
