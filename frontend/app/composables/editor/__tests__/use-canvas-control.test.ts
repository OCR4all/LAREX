import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { SelectionFocusOptions } from '@/types/editor/canvas-controls'

const editorStoreMock = vi.hoisted(() => ({
  canvases: {} as Record<string, { pageId: string, projectId: string }>,
  clearCanvasSelection: vi.fn(),
  getPage: vi.fn(),
  updateCanvasHistoryState: vi.fn()
}))

const editorUiStoreMock = vi.hoisted(() => ({
  setActionWandActive: vi.fn(),
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
    editorStoreMock.canvases = {}
    editorStoreMock.getPage.mockReset()
  })

  it('exposes persisted action locks after a faulty run becomes inactive', async () => {
    editorStoreMock.canvases['locked-canvas'] = {
      pageId: 'page-1',
      projectId: 'project-1'
    }
    editorStoreMock.getPage.mockReturnValue({
      locked: true,
      lockedReason: 'LAREX Action running: Faulty OCR'
    })

    const useCanvasControl = await loadUseCanvasControl()
    const controls = useCanvasControl('locked-canvas')

    expect(controls.pageLockReason.value).toBe('LAREX Action running: Faulty OCR')
    expect(controls.isCanvasEditable.value).toBe(false)
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

  it('persists an explicitly selected layout view even when it is already rendered', async () => {
    const useCanvasControl = await loadUseCanvasControl()
    const controls = useCanvasControl('canvas-1')

    controls.setViewMode('default')

    expect(editorUiStoreMock.setLastLayoutViewMode).toHaveBeenCalledWith('default')
    expect(editorStoreMock.clearCanvasSelection).not.toHaveBeenCalled()
  })
})
