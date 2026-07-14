import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { SelectionFocusOptions } from '@/types/editor/canvas-controls'

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

vi.mock('@/stores/action-runs.store', () => ({
  useActionRunsStore: () => ({
    getPageActionLockReason: () => null
  })
}))

vi.mock('@/composables/editor/use-editor-collaboration', () => ({
  useEditorCollaboration: () => ({
    canEditCanvas: () => true
  })
}))

async function loadUseCanvasControl() {
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
      selectPolygonById?: (id: string | null, options?: SelectionFocusOptions) => void
      selectPolylineById?: (id: string | null, options?: SelectionFocusOptions) => void
      unhoverPolygon?: () => void
      unhoverPolyline?: () => void
    }

    const selectPolygonById = vi.fn<(id: string | null, options?: SelectionFocusOptions) => void>()
    const selectPolylineById = vi.fn<(id: string | null, options?: SelectionFocusOptions) => void>()
    const unhoverPolygon = vi.fn<() => void>()
    const unhoverPolyline = vi.fn<() => void>()

    controls.selectedPolygonIndex = ref(4)
    controls.selectedPolylineIndex = ref(2)
    controls.selectedPolygonIds = ref(['region-1'])
    controls.selectedPolylineIds = ref(['baseline:line-1'])
    controls.selectPolygonById = selectPolygonById
    controls.selectPolylineById = selectPolylineById
    controls.unhoverPolygon = unhoverPolygon
    controls.unhoverPolyline = unhoverPolyline

    controls.setViewMode('textline')

    expect(selectPolygonById).toHaveBeenCalledWith(null, { zoomToFit: false })
    expect(selectPolylineById).toHaveBeenCalledWith(null, { zoomToFit: false })
    expect(controls.selectedPolygonIndex.value).toBe(-1)
    expect(controls.selectedPolylineIndex.value).toBe(-1)
    expect(controls.selectedPolygonIds.value).toEqual([])
    expect(controls.selectedPolylineIds.value).toEqual([])
    expect(unhoverPolygon).toHaveBeenCalledOnce()
    expect(unhoverPolyline).toHaveBeenCalledOnce()
    expect(editorUiStoreMock.setTemporaryHoverPolygonId).toHaveBeenCalledWith(null)
    expect(editorUiStoreMock.setTemporaryHoverPolylineId).toHaveBeenCalledWith(null)
    expect(editorStoreMock.clearCanvasSelection).toHaveBeenCalledWith('canvas-1')
    expect(editorUiStoreMock.setLastLayoutViewMode).toHaveBeenCalledWith('textline')
    expect(controls.viewMode.value).toBe('textline')
  })
})
