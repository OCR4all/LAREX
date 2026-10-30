import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { TextLine, Polygon } from '@/models/editor'
import { Commander } from '@/commands/editor/commander'
import { useEditorCommand } from '../use-editor-command'
import { getEditorSession } from '@/session/editor/editor-session'
import { createMockSession, createTestDocument, createTestTextRegion, createTestContext } from '@/commands/editor/__tests__/test-utils'
import { collectRenderablePolygonsFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

vi.mock('@/session/editor/editor-session', () => ({ getEditorSession: vi.fn() }))
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => ({ labelSet: null, canvases: {} }) }))
vi.mock('@/composables/editor/use-overlay-dialogs', () => ({ useOverlayDialogs: () => ({}) }))
vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({ invalidateMultiplePolygonGeometry: vi.fn() }))
vi.mock('@/stores/editor/editor.ui.store', () => ({ useEditorUiStore: () => ({ bumpReadingOrderVersion: vi.fn() }) }))

function setup(selection: string[]) {
  const parent = createTestTextRegion({
    id: 'parent',
    points: [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }],
    textLines: ['line-1', 'line-2'].map(id => new TextLine({ id, coords: new Polygon([[0, 0], [2, 0], [2, 1], [0, 1]]) }))
  })
  const { session } = createMockSession(createTestDocument({ regions: [parent] }))
  vi.mocked(getEditorSession).mockReturnValue(session)
  const polygons = collectRenderablePolygonsFromPcGts(session.document.value!)
  const commander = new Commander()
  const api = useEditorCommand(commander, 'test', polygons, [], vi.fn(), ref(selection))
  const show = (id: string) => api.showContextMenuForPolygon({ clientX: 10, clientY: 20 } as MouseEvent, polygons.find(p => p.id === id)!)
  const action = () => api.contextMenuItems.value!.find(item => item.id === 'shape-tools')!.submenu!.find(item => item.label === 'Constrain to Parent')!
  return { session, commander, api, show, action }
}

describe('Constrain to Parent menu', () => {
  it.each([['line-1'], ['line-1', 'line-2']])('runs the tool for the captured selection (%j)', async (...selection) => {
    const h = setup(selection)
    h.show('line-1')
    expect(h.action().disabled).toBe(false)
    await h.api.handleContextMenuSelect(h.action())
    const polygons = collectRenderablePolygonsFromPcGts(h.session.document.value!)
    for (const id of selection) expect(polygons.find(p => p.id === id)!.points.every(p => p.x <= 1)).toBe(true)
    expect(h.commander.getState().totalCount).toBe(1)
    h.commander.undo(createTestContext(h.session))
    expect(collectRenderablePolygonsFromPcGts(h.session.document.value!).find(p => p.id === 'line-1')!.points.some(p => p.x === 2)).toBe(true)
  })

  it('disables the tool for a root region', () => {
    const h = setup([])
    h.show('parent')
    expect(h.action().disabled).toBe(true)
  })
})
