import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, ref } from 'vue'
import { useEditorRenderer } from '../use-editor-renderer'
import type { RenderablePolygon } from '@/types/editor/rendering'

const scheduleRender = vi.hoisted(() => vi.fn())
vi.mock('../use-render-queue', () => ({
  RenderPriority: { NORMAL: 2 },
  useRenderQueue: () => ({ scheduleRender, stats: ref(null) })
}))
vi.mock('../use-editor-custom-cursor', () => ({ useEditorCustomCursor: () => ({ activeCursor: ref(null) }) }))
vi.mock('@/stores/editor/editor.ui.store', () => ({ useEditorUiStore: () => ({}) }))

function createHarness() {
  let coordinateReads = 0
  const polygons = reactive<RenderablePolygon[]>(Array.from({ length: 8 }, (_, index) => ({
    id: `region-${index}`,
    points: Array.from({ length: 2000 }, (_, pointIndex) => ({
      get x() {
        coordinateReads++
        return pointIndex / 2000
      },
      y: index / 8
    }))
  })))
  const view = reactive({ zoom: 1, offsetX: 0, offsetY: 0 })
  const hoveredNodeIndex = ref(-1)
  const polygonEditing = {
    hoveredPolygonIndex: ref(-1), hoveredNodeIndex,
    hoveredEdgeInfo: reactive({ polygonIndex: -1, edgeStartIndex: -1, t: 0 }),
    previewNodePosition: reactive({ x: null, y: null }),
    draggedNodeInfo: reactive({ polygonIndex: -1, nodeIndex: -1, isDragging: false }),
    isInvalidPosition: ref(false)
  }
  const drawing = () => ({
    currentPolygonPoints: reactive([]), currentPolylinePoints: reactive([]),
    previewPoint: reactive({ x: null, y: null }), isInvalidPosition: ref(false)
  })
  const scope = effectScope()
  scope.run(() => {
    // Only rendering watches are exercised; interaction handlers are not called.
    const renderer = useEditorRenderer(...[
      ref(null), polygons, reactive([]), ref(-1), ref(-1), ref([]), ref([]), ref([]), ref([]), ref(null),
      ref(false), ref(false), ref(false), ref(false), ref({ width: 1000, height: 800 }),
      { renderFrame: vi.fn(), imageSize: ref({ width: 1, height: 1 }) },
      ref({ scaleX: 1, scaleY: 1 }), view, { actionState: reactive({ action: 'idle' }) },
      drawing(), drawing(), { previewPoints: reactive([]), isInvalidPosition: ref(false) },
      polygonEditing, { ...polygonEditing, selectedPolylineIndex: ref(-1) },
      vi.fn(() => []), ref('default'), ref('TextRegion'), ref(true),
      ref(undefined), ref(false), ref(undefined), ref(false), undefined,
      ref(false), ref(false), ref(false), ref(false), undefined, ref(null), ref(null), ref(undefined), ref([])
    ] as unknown as Parameters<typeof useEditorRenderer>)
    renderer.setupRenderWatches()
  })
  coordinateReads = 0
  scheduleRender.mockClear()
  return { scope, view, polygons, hoveredNodeIndex, coordinateReads: () => coordinateReads }
}

afterEach(() => vi.clearAllMocks())

describe('editor render invalidation', () => {
  it.each(['zoom', 'pan', 'hover'] as const)('%s does not traverse unchanged polygon coordinates', async (interaction) => {
    const harness = createHarness()
    try {
      for (let i = 0; i < 10; i++) {
        if (interaction === 'zoom') harness.view.zoom += 0.01
        if (interaction === 'pan') harness.view.offsetX += 0.01
        if (interaction === 'hover') harness.hoveredNodeIndex.value = i
        await nextTick()
        await nextTick()
      }
      expect(scheduleRender).toHaveBeenCalled()
      expect(harness.coordinateReads()).toBe(0)
    } finally {
      harness.scope.stop()
    }
  })

  it('still schedules rendering for point edits and added polygons', async () => {
    const harness = createHarness()
    try {
      harness.polygons[0]!.points[0] = { x: 0.5, y: 0.5 }
      await nextTick()
      await nextTick()
      expect(scheduleRender).toHaveBeenCalled()
      // Editing one shape must not walk the other seven shapes.
      expect(harness.coordinateReads()).toBeLessThan(2000)
      scheduleRender.mockClear()
      harness.polygons.push({ id: 'new-region', points: [{ x: 0, y: 0 }] })
      await nextTick()
      await nextTick()
      expect(scheduleRender).toHaveBeenCalled()
    } finally {
      harness.scope.stop()
    }
  })
})
