import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, reactive, ref } from 'vue'

const editorStoreMock = vi.hoisted(() => ({
  clearCanvasSelection: vi.fn(),
  updateCanvasHistoryState: vi.fn()
}))

const editorUiStoreMock = vi.hoisted(() => ({
  setLastLayoutViewMode: vi.fn(),
  setTemporaryHoverPolygonId: vi.fn(),
  setTemporaryHoverPolylineId: vi.fn()
}))

vi.mock('@/stores/editor/editor.store', () => ({
  useEditorStore: () => editorStoreMock
}))

vi.mock('@/stores/editor/editor.ui.store', () => ({
  useEditorUiStore: () => ({
    lastLayoutViewMode: 'default',
    ...editorUiStoreMock
  })
}))

async function loadUseCanvasControl() {
  const globalScope = globalThis as typeof globalThis & {
    computed: typeof computed
    reactive: typeof reactive
    ref: typeof ref
  }
  globalScope.computed = computed
  globalScope.reactive = reactive
  globalScope.ref = ref

  const module = await import('../use-canvas-control')
  return module.useCanvasControl
}

describe('useCanvasControl', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('clears selection state before switching view modes', async () => {
    const useCanvasControl = await loadUseCanvasControl()
    const controls = useCanvasControl('canvas-1') as ReturnType<typeof useCanvasControl> & {
      selectedPolylineIndex: ReturnType<typeof ref<number>>
      selectedPolygonIds: ReturnType<typeof ref<string[]>>
      selectedPolylineIds: ReturnType<typeof ref<string[]>>
      selectPolygonById: ReturnType<typeof vi.fn>
      selectPolylineById: ReturnType<typeof vi.fn>
      unhoverPolygon: ReturnType<typeof vi.fn>
      unhoverPolyline: ReturnType<typeof vi.fn>
    }

    controls.selectedPolygonIndex = ref(4)
    controls.selectedPolylineIndex = ref(2)
    controls.selectedPolygonIds = ref(['region-1'])
    controls.selectedPolylineIds = ref(['baseline:line-1'])
    controls.selectPolygonById = vi.fn()
    controls.selectPolylineById = vi.fn()
    controls.unhoverPolygon = vi.fn()
    controls.unhoverPolyline = vi.fn()

    controls.setViewMode('textline')

    expect(controls.selectPolygonById).toHaveBeenCalledWith(null, { zoomToFit: false })
    expect(controls.selectPolylineById).toHaveBeenCalledWith(null, { zoomToFit: false })
    expect(controls.selectedPolygonIndex.value).toBe(-1)
    expect(controls.selectedPolylineIndex.value).toBe(-1)
    expect(controls.selectedPolygonIds.value).toEqual([])
    expect(controls.selectedPolylineIds.value).toEqual([])
    expect(controls.unhoverPolygon).toHaveBeenCalledOnce()
    expect(controls.unhoverPolyline).toHaveBeenCalledOnce()
    expect(editorUiStoreMock.setTemporaryHoverPolygonId).toHaveBeenCalledWith(null)
    expect(editorUiStoreMock.setTemporaryHoverPolylineId).toHaveBeenCalledWith(null)
    expect(editorStoreMock.clearCanvasSelection).toHaveBeenCalledWith('canvas-1')
    expect(editorUiStoreMock.setLastLayoutViewMode).toHaveBeenCalledWith('textline')
    expect(controls.viewMode.value).toBe('textline')
  })
})
