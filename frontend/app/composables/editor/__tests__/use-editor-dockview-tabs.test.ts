import type { DockviewReadyEvent } from 'dockview-vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useEditorDockviewTabs } from '../use-editor-dockview-tabs'

const mocks = vi.hoisted(() => ({
  editorStore: {
    canvases: {},
    getPage: vi.fn(),
    getProjectPages: vi.fn(() => [{ projectName: 'Project One' }])
  },
  sessionStore: {
    openedProjectIds: ['project-1'],
    setActiveProject: vi.fn()
  }
}))

vi.mock('@/stores/editor/editor.store', () => ({
  useEditorStore: () => mocks.editorStore
}))

vi.mock('@/stores/editor/editor.session.store', () => ({
  useEditorSessionStore: () => mocks.sessionStore
}))

describe('useEditorDockviewTabs', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reads the active panel from the Dockview v8 event payload', () => {
    type ActivePanelEvent = {
      panel: { id: string } | undefined
      origin: 'api'
    }
    let activePanelListener: ((event: ActivePanelEvent) => void) | undefined

    const api = {
      addPanel: vi.fn((options: { id: string }) => {
        activePanelListener?.({ panel: { id: options.id }, origin: 'api' })
        return { id: options.id }
      }),
      getPanel: vi.fn(),
      onDidActivePanelChange: vi.fn((listener: (event: ActivePanelEvent) => void) => {
        activePanelListener = listener
        return { dispose: vi.fn() }
      }),
      onDidRemovePanel: vi.fn(() => ({ dispose: vi.fn() }))
    }

    const tabs = useEditorDockviewTabs({
      getDockviewApi: () => api as never,
      setDockviewApi: vi.fn(),
      projectDockviewRegistry: {
        get: vi.fn(() => null)
      } as never,
      projectTabCloseState: {} as never,
      clearProjectTabState: vi.fn(),
      tryCreateInitialPanels: vi.fn()
    })

    tabs.onReady({ api } as unknown as DockviewReadyEvent)

    expect(mocks.sessionStore.setActiveProject).toHaveBeenCalledWith('project-1')
  })
})
