import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { Commander } from '@/commands/editor/commander'
import { useEditorCommand, type ContextMenuItem } from '../use-editor-command'
import { getEditorSession } from '@/session/editor/editor-session'
import { createMockSession, createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import { collectRenderablePolygonsFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

vi.mock('@/session/editor/editor-session', () => ({ getEditorSession: vi.fn() }))
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => ({ labelSet: null, canvases: {} }) }))
vi.mock('@/composables/editor/use-overlay-dialogs', () => ({ useOverlayDialogs: () => ({}) }))
vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({ invalidateMultiplePolygonGeometry: vi.fn() }))
vi.mock('@/stores/editor/editor.ui.store', () => ({ useEditorUiStore: () => ({ bumpReadingOrderVersion: vi.fn() }) }))

function rect(minX: number, minY: number, maxX: number, maxY: number) {
  return [{ x: minX, y: minY }, { x: maxX, y: minY }, { x: maxX, y: maxY }, { x: minX, y: maxY }]
}

function setup(selectedIds: string[] = []) {
  const { session } = createMockSession(createTestDocument({
    regions: [
      createTestTextRegion({ id: 'a', points: rect(10, 10, 20, 20) }),
      createTestTextRegion({ id: 'b', points: rect(30, 30, 40, 40) }),
      createTestTextRegion({ id: 'page-image', points: rect(0, 0, 100, 100) })
    ]
  }))
  vi.mocked(getEditorSession).mockReturnValue(session)
  const polygons = collectRenderablePolygonsFromPcGts(session.document.value!)
  const commander = new Commander()
  const api = useEditorCommand(commander, 'test', polygons, [], vi.fn(), ref(selectedIds))

  function openHierarchy(id: string): ContextMenuItem[] {
    polygons.splice(0, polygons.length, ...collectRenderablePolygonsFromPcGts(session.document.value!))
    api.showContextMenuForPolygon({ clientX: 0, clientY: 0 } as MouseEvent, polygons.find(p => p.id === id)!)
    return api.contextMenuItems.value!.find(item => item.id === 'hierarchy')?.submenu ?? []
  }

  return {
    commander,
    openHierarchy,
    async run(id: string, itemId: string) {
      const items = openHierarchy(id)
      const item = [...items, ...items.flatMap(entry => entry.submenu ?? [])].find(entry => entry.id === itemId)!
      await api.handleContextMenuSelect(item)
    },
    topLevelIds: () => session.document.value!.page.regions.map(region => region.id),
    childIds: (id: string) => session.document.value!.page.regions.find(region => region.id === id)?.regions?.map(region => region.id) ?? []
  }
}

describe('hierarchy context menu', () => {
  it('moves the regions below a covering region inside it and back out', async () => {
    const { openHierarchy, run, topLevelIds, childIds, commander } = setup()
    expect(openHierarchy('page-image').map(item => item.label)).toEqual(['Move 2 Contained Regions Inside', 'Move to Region'])

    await run('page-image', 'move-contained-inside')
    expect(topLevelIds()).toEqual(['page-image'])
    expect(childIds('page-image')).toEqual(['a', 'b'])
    expect(commander.getState().totalCount).toBe(1)

    await run('page-image', 'move-children-out')
    expect(topLevelIds()).toEqual(['page-image', 'a', 'b'])
  })

  it('moves a single region in and out of a parent', async () => {
    const { openHierarchy, run, topLevelIds, childIds } = setup()
    await run('a', 'reparent-to-page-image')
    expect(childIds('page-image')).toEqual(['a'])
    expect(openHierarchy('a').map(item => item.id)).toEqual(['move-out-of-parent', 'reparent'])

    await run('a', 'move-out-of-parent')
    expect(topLevelIds()).toEqual(['b', 'page-image', 'a'])
  })

  it('moves a multi-selection as one step', async () => {
    const { run, childIds, commander } = setup(['a', 'b'])
    await run('a', 'reparent-to-page-image')
    expect(childIds('page-image')).toEqual(['a', 'b'])
    expect(commander.getState().totalCount).toBe(1)
  })
})
