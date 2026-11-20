import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { Commander } from '@/commands/editor/commander'
import { CreateTableGridCommand } from '@/commands/editor/create-table-grid-command'
import { useEditorCommand } from '../use-editor-command'
import { getEditorSession } from '@/session/editor/editor-session'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import { collectRenderablePolygonsFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

vi.mock('@/session/editor/editor-session', () => ({ getEditorSession: vi.fn() }))
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => ({ labelSet: null, canvases: {} }) }))
vi.mock('@/composables/editor/use-overlay-dialogs', () => ({ useOverlayDialogs: () => ({}) }))
vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({ invalidateMultiplePolygonGeometry: vi.fn() }))
vi.mock('@/stores/editor/editor.ui.store', () => ({ useEditorUiStore: () => ({ bumpReadingOrderVersion: vi.fn() }) }))

function setup() {
  const region = createTestTextRegion({ id: 't', points: [{ x: 0, y: 0 }, { x: 20, y: 0 }, { x: 20, y: 20 }, { x: 0, y: 20 }] })
  const { session } = createMockSession(createTestDocument({ regions: [region] }))
  vi.mocked(getEditorSession).mockReturnValue(session)
  const { cellIds } = new CreateTableGridCommand({ regionId: 't', rows: 2, columns: 2 }).execute(createTestContext(session))!
  const polygons = collectRenderablePolygonsFromPcGts(session.document.value!)
  const api = useEditorCommand(new Commander(), 'test', polygons, [], vi.fn(), ref([]))
  return { session, api, polygons, cellIds }
}

describe('table cell context menu', () => {
  it('offers the table actions in the Table submenu and runs them on the table', async () => {
    const { session, api, polygons, cellIds } = setup()
    api.showContextMenuForPolygon({ clientX: 10, clientY: 20 } as MouseEvent, polygons.find(p => p.id === cellIds[0])!)

    const submenu = api.contextMenuItems.value!.find(item => item.id === 'table-cell')!.submenu!
    expect(submenu.map(item => item.id)).toEqual(expect.arrayContaining(['assign-lines-to-cells', 'table-reading-order', 'table-export']))

    const rows = submenu.find(item => item.id === 'table-reading-order')!.submenu!.find(item => item.id === 'table-reading-order-rows')!
    await api.handleContextMenuSelect(rows)

    expect(JSON.stringify(session.document.value!.page.readingOrder)).toContain(cellIds[3]!)
  })
})
