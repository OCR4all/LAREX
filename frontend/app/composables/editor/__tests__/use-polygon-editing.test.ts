// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { Commander } from '@/commands/editor/commander'
import { getEditorSession } from '@/session/editor/editor-session'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import { usePolygonEditing } from '../use-polygon-editing'

vi.mock('@/session/editor/editor-session', () => ({ getEditorSession: vi.fn() }))
vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({ invalidatePolygonGeometry: vi.fn() }))

function setup(points = [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }]) {
  const { session } = createMockSession(createTestDocument({ regions: [createTestTextRegion({ id: 'region', points })] }))
  vi.mocked(getEditorSession).mockReturnValue(session)
  const polygons = reactive([{ id: 'region', points }])
  const commander = new Commander()
  const editing = usePolygonEditing(polygons, { zoom: 1, offsetX: 0, offsetY: 0 }, ref({ scaleX: 1, scaleY: 1 }),
    ref(false), undefined, ref(false), undefined, ref(-1), ref('default'), commander, 'test', ref([]), ref([]))
  const clickNode = (index: number) => {
    editing.handleMouseDown(polygons[0]!.points[index]!, ref(0), null)
    editing.handleMouseUp(null)
  }
  return { editing, polygons, commander, session, clickNode }
}

describe('polygon node deletion', () => {
  const notify = vi.fn()
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('useToast', () => ({ add: notify }))
  })
  afterEach(() => vi.unstubAllGlobals())

  it('retains a clicked corner without recording a drag, deletes it, and supports undo/redo', () => {
    const h = setup()
    h.clickNode(1)
    expect(h.editing.selectedNodes.value).toEqual({ polygonId: 'region', nodeIndices: [1] })
    expect(h.commander.canUndo()).toBe(false)
    expect(h.editing.deleteSelectedNodes(0)).toBe(true)
    const coords = () => h.session.document.value!.page.regions[0]!.coords.points
    expect(coords()).toEqual([[0, 0], [1, 1], [0, 1]])
    expect(h.editing.selectedNodes.value).toBeNull()
    expect(notify).not.toHaveBeenCalled()
    h.commander.undo(createTestContext(h.session))
    expect(coords()).toHaveLength(4)
    h.commander.redo(createTestContext(h.session))
    expect(coords()).toHaveLength(3)
  })

  it('deletes every marquee-selected corner in one undoable edit', () => {
    const h = setup([
      { x: -0.6, y: -0.4 }, { x: 0, y: -0.4 }, { x: 0.6, y: -0.4 },
      { x: 0.6, y: 0.4 }, { x: 0, y: 0.4 }, { x: -0.6, y: 0.4 }
    ])
    h.editing.selectNodesInRect(0, { minX: -0.7, maxX: 0.1, minY: -0.5, maxY: -0.3 })
    expect(h.editing.selectedNodes.value?.nodeIndices).toEqual([0, 1])
    expect(h.editing.deleteSelectedNodes(0)).toBe(true)
    const coords = () => h.session.document.value!.page.regions[0]!.coords.points
    expect(coords()).toEqual([[0.6, -0.4], [0.6, 0.4], [0, 0.4], [-0.6, 0.4]])
    h.commander.undo(createTestContext(h.session))
    expect(coords()).toHaveLength(6)
    expect(h.commander.canUndo()).toBe(false)
    h.commander.redo(createTestContext(h.session))
    expect(coords()).toHaveLength(4)
  })

  it('adds rectangle selections with Shift and replaces them without Shift', () => {
    const h = setup()
    h.editing.selectNodesInRect(0, { minX: -0.1, maxX: 0.1, minY: -0.1, maxY: 1.1 })
    expect(h.editing.selectedNodes.value?.nodeIndices).toEqual([0, 3])
    h.editing.selectNodesInRect(0, { minX: 0.9, maxX: 1.1, minY: -0.1, maxY: 0.1 }, true)
    expect(h.editing.selectedNodes.value?.nodeIndices).toEqual([0, 1, 3])
    h.editing.selectNodesInRect(0, { minX: 0.9, maxX: 1.1, minY: 0.9, maxY: 1.1 })
    expect(h.editing.selectedNodes.value?.nodeIndices).toEqual([2])
  })

  it('selects all enclosed corners but blocks deletion leaving fewer than three points', () => {
    const h = setup()
    h.editing.selectNodesInRect(0, { minX: -1, maxX: 2, minY: -1, maxY: 2 })
    expect(h.editing.selectedNodes.value?.nodeIndices).toEqual([0, 1, 2, 3])
    expect(h.editing.deleteSelectedNodes(0)).toBe(true)
    expect(h.commander.canUndo()).toBe(false)
    expect(h.session.document.value!.page.regions).toHaveLength(1)
    expect(h.editing.selectedNodes.value?.nodeIndices).toEqual([0, 1, 2, 3])
    expect(notify).toHaveBeenCalledWith({
      title: 'Cannot delete selected nodes',
      description: 'A polygon needs at least 3 nodes. Select fewer nodes and try again.',
      color: 'warning'
    })
  })

  it('consumes Delete on a triangle corner without deleting the polygon', () => {
    const h = setup([{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 0, y: 1 }])
    h.clickNode(0)
    expect(h.editing.deleteSelectedNodes(0)).toBe(true)
    expect(h.commander.canUndo()).toBe(false)
  })

  it('rejects removal that introduces a crossing', () => {
    const h = setup([{ x: 0, y: 0 }, { x: 4, y: 0 }, { x: 4, y: 4 }, { x: 2, y: 1 }, { x: 0, y: 4 }])
    h.clickNode(0)
    expect(h.editing.deleteSelectedNodes(0)).toBe(true)
    expect(h.commander.canUndo()).toBe(false)
    expect(h.editing.selectedNodes.value?.nodeIndices).toEqual([0])
    expect(notify).toHaveBeenCalledWith({
      title: 'Cannot delete selected nodes',
      description: 'Deleting these nodes would make polygon edges cross. Change the node selection and try again.',
      color: 'warning'
    })
  })

  it('clears the corner selection when clicking elsewhere or replacing geometry', () => {
    const h = setup()
    h.clickNode(0)
    h.editing.handleMouseDown({ x: 0.5, y: 0.5 }, ref(0), null)
    expect(h.editing.selectedNodes.value).toBeNull()
    h.clickNode(0)
    h.polygons[0]!.points = [...h.polygons[0]!.points]
    expect(h.editing.selectedNodes.value).toBeNull()
    expect(h.editing.deleteSelectedNodes(0)).toBe(false)
  })

  it('does not delete a corner of a different active polygon or during a drag', () => {
    const h = setup()
    h.clickNode(0)
    expect(h.editing.deleteSelectedNodes(-1)).toBe(false)
    h.editing.handleMouseDown({ x: 0, y: 0 }, ref(0), null)
    expect(h.editing.deleteSelectedNodes(0)).toBe(true)
    expect(h.commander.canUndo()).toBe(false)
  })
})
