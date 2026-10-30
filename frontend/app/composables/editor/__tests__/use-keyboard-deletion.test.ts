// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { Commander } from '@/commands/editor/commander'
import { getEditorSession } from '@/session/editor/editor-session'
import { createMockSession, createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import type { KeyboardShortcutsOptions } from '../use-keyboard-shortcuts'
import { useKeyboardShortcuts } from '../use-keyboard-shortcuts'

vi.mock('@/session/editor/editor-session', () => ({ getEditorSession: vi.fn() }))
vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({ invalidatePolygonGeometry: vi.fn(), invalidateMultiplePolygonGeometry: vi.fn() }))

function setup(ids: string[] = []) {
  vi.stubGlobal('useEditorPreferences', () => ({ preferences: ref({ shortcutBindings: {} }) }))
  const { session } = createMockSession(createTestDocument({ regions: ['a', 'b'].map(id => createTestTextRegion({ id })) }))
  vi.mocked(getEditorSession).mockReturnValue(session)
  const commander = new Commander()
  const deleteSelectedNodes = vi.fn(() => false)
  const editable = ref(true)
  // Only these controls participate in keyboard deletion.
  session.controls.value = { commander, deleteSelectedNodes, isCanvasEditable: editable } as unknown as NonNullable<typeof session.controls.value>
  const selectedPolygonIds = ref(ids)
  const clearSelection = vi.fn(() => {
    selectedPolygonIds.value = []
  })
  const options = {
    canvasId: ref('test'), selectedPolygonIds, selectedPolylineIds: ref([]),
    polygons: ref([{ id: 'a' }, { id: 'b' }]), polylines: ref([]),
    selectedPolygonIndex: ref(0), selectedPolylineIndex: ref(-1),
    callbacks: { clearSelection }
  } as unknown as KeyboardShortcutsOptions
  return { shortcuts: useKeyboardShortcuts(options), session, commander, deleteSelectedNodes, editable, clearSelection }
}

afterEach(() => {
  if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  vi.unstubAllGlobals()
})

describe('Delete selection routing', () => {
  it('deletes the clicked polygon when the selection set is empty', () => {
    const h = setup()
    expect(h.shortcuts.deleteSelectedElements()).toBe(true)
    expect(h.session.document.value!.page.regions.map(r => r.id)).toEqual(['b'])
  })

  it('deletes every marquee-selected polygon as one undoable command', () => {
    const h = setup(['a', 'b'])
    expect(h.shortcuts.deleteSelectedElements()).toBe(true)
    expect(h.session.document.value!.page.regions).toHaveLength(0)
    h.commander.undo({ session: h.session, canvasId: 'test' })
    expect(h.session.document.value!.page.regions.map(r => r.id)).toEqual(['a', 'b'])
    expect(h.commander.canUndo()).toBe(false)
  })

  it('gives a selected corner priority over whole-polygon deletion', () => {
    const h = setup(['a'])
    h.deleteSelectedNodes.mockReturnValue(true)
    expect(h.shortcuts.deleteSelectedElements()).toBe(true)
    expect(h.session.document.value!.page.regions).toHaveLength(2)
    expect(h.clearSelection).not.toHaveBeenCalled()
  })

  it('does not mutate a read-only canvas', () => {
    const h = setup(['a'])
    h.editable.value = false
    expect(h.shortcuts.deleteSelectedElements()).toBe(false)
    expect(h.deleteSelectedNodes).not.toHaveBeenCalled()
    expect(h.commander.canUndo()).toBe(false)
  })

  it('leaves Delete to the focused text input', () => {
    const h = setup(['a'])
    const input = document.createElement('input')
    document.body.append(input)
    input.focus()
    expect(h.shortcuts.deleteSelectedElements()).toBe(false)
    expect(h.commander.canUndo()).toBe(false)
    input.remove()
  })
})
