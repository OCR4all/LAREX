/* eslint-disable import/newline-after-import */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref, watch } from 'vue'

;(globalThis as any).ref = ref
;(globalThis as any).computed = computed
;(globalThis as any).watch = watch

const globalSearchTerm = ref('')
const globalResultGroups = ref<any[]>([])
const globalIsSearching = ref(false)

vi.mock('@/composables/use-global-search', () => ({
  useGlobalSearch: () => ({
    searchTerm: globalSearchTerm,
    isSearching: globalIsSearching,
    resultGroups: globalResultGroups
  })
}))

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

describe('useEditorCommandCenter', () => {
  beforeEach(() => {
    vi.resetModules()

    globalSearchTerm.value = ''
    globalResultGroups.value = []
    globalIsSearching.value = false

    const selectedWorkspaceId = ref('ws-1')
    const workspace = {
      get selectedWorkspaceId() {
        return selectedWorkspaceId.value
      },
      _selectedWorkspaceId: selectedWorkspaceId
    }

    ;(globalThis as any).useWorkspaceStore = vi.fn(() => workspace)
    ;(globalThis as any).navigateTo = vi.fn(async () => {})

    const shortcuts: Record<string, { handler: () => void }> = {}
    ;(globalThis as any).defineShortcuts = vi.fn((definition: Record<string, { handler: () => void }>) => {
      Object.assign(shortcuts, definition)
    })
    ;(globalThis as any).__shortcuts = shortcuts
    ;(globalThis as any).useOnboarding = vi.fn(() => ({
      startCurrentPageTour: vi.fn(async () => true)
    }))
  })

  it('builds workspace index once and refetches when workspace changes', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/workspaces/ws-1/projects') {
        return [{ id: 'p1', name: 'Project One', pageCount: 2 }]
      }
      if (url === '/api/projects/p1/pages') {
        return [{ id: 'pg1', name: 'Alpha Page' }, { id: 'pg2', name: 'Beta Page' }]
      }
      if (url === '/api/workspaces/ws-2/projects') {
        return [{ id: 'p2', name: 'Project Two', pageCount: 1 }]
      }
      if (url === '/api/projects/p2/pages') {
        return [{ id: 'pg3', name: 'Gamma Page' }]
      }
      return []
    })
    ;(globalThis as any).$fetch = fetchMock

    const openProjectModal = vi.fn(async () => {})
    const openProjectSelection = vi.fn(async () => {})

    const { useEditorCommandCenter } = await import('../use-editor-command-center')
    const api = useEditorCommandCenter({ openProjectModal, openProjectSelection })

    api.openCommandCenter()
    api.searchTerm.value = 'project'
    await flush()
    await flush()

    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/ws-1/projects')
    expect(fetchMock).toHaveBeenCalledWith('/api/projects/p1/pages')

    api.searchTerm.value = 'page'
    await flush()
    await flush()

    const ws1ProjectCalls = fetchMock.mock.calls.filter(call => call[0] === '/api/workspaces/ws-1/projects')
    expect(ws1ProjectCalls).toHaveLength(1)

    const workspace = (globalThis as any).useWorkspaceStore.mock.results[0].value
    workspace._selectedWorkspaceId.value = 'ws-2'

    api.searchTerm.value = 'project'
    await flush()
    await flush()

    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/ws-2/projects')
    expect(fetchMock).toHaveBeenCalledWith('/api/projects/p2/pages')
  })

  it('maps project and page selections to expected payloads', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/workspaces/ws-1/projects') {
        return [{ id: 'p1', name: 'Project One', pageCount: 2 }]
      }
      if (url === '/api/projects/p1/pages') {
        return [{ id: 'pg1', name: 'Alpha Page' }, { id: 'pg2', name: 'Beta Page' }]
      }
      return []
    })
    ;(globalThis as any).$fetch = fetchMock

    const openProjectSelection = vi.fn(async () => {})
    const { useEditorCommandCenter } = await import('../use-editor-command-center')
    const api = useEditorCommandCenter({
      openProjectModal: async () => {},
      openProjectSelection
    })

    api.openCommandCenter()
    api.searchTerm.value = 'alpha'
    await flush()
    await flush()

    const pageGroup = api.groups.value.find(group => group.id === 'editor-open-pages')
    expect(pageGroup).toBeTruthy()
    await pageGroup?.items[0]?.onSelect?.()
    expect(openProjectSelection).toHaveBeenCalledWith({
      projectId: 'p1',
      projectName: 'Project One',
      pageIds: ['pg1']
    }, 'page-search')

    api.openCommandCenter()
    api.searchTerm.value = 'project one'
    await flush()
    await flush()

    const projectGroup = api.groups.value.find(group => group.id === 'editor-open-projects')
    expect(projectGroup).toBeTruthy()
    await projectGroup?.items[0]?.onSelect?.()
    expect(openProjectSelection).toHaveBeenCalledWith({
      projectId: 'p1',
      projectName: 'Project One',
      pageIds: null
    }, 'project-search')
  })

  it('registers both meta+k and ctrl+k shortcuts', async () => {
    ;(globalThis as any).$fetch = vi.fn(async () => [])

    const { useEditorCommandCenter } = await import('../use-editor-command-center')
    const api = useEditorCommandCenter({
      openProjectModal: async () => {},
      openProjectSelection: async () => {}
    })

    api.closeCommandCenter()
    const shortcuts = (globalThis as any).__shortcuts as Record<string, { handler: () => void }>
    expect(shortcuts.meta_k).toBeTruthy()
    expect(shortcuts.ctrl_k).toBeTruthy()

    shortcuts.meta_k!.handler()
    expect(api.open.value).toBe(true)

    api.closeCommandCenter()
    shortcuts.ctrl_k!.handler()
    expect(api.open.value).toBe(true)
  })
})
