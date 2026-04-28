import { beforeEach, describe, expect, it } from 'vitest'

const STORAGE_KEY = 'larex-editor-session'

type StorageLike = {
  getItem: (key: string) => string | null
  setItem: (key: string, value: string) => void
  removeItem: (key: string) => void
  clear: () => void
}

function createStorageMock(): StorageLike {
  const map = new Map<string, string>()
  return {
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => { map.set(key, value) },
    removeItem: (key: string) => { map.delete(key) },
    clear: () => { map.clear() }
  }
}

function ensureStorage(target: Record<string, unknown>, key: 'localStorage' | 'sessionStorage') {
  const current = target[key] as Partial<StorageLike> | undefined
  if (
    !current
    || typeof current.getItem !== 'function'
    || typeof current.setItem !== 'function'
    || typeof current.removeItem !== 'function'
    || typeof current.clear !== 'function'
  ) {
    target[key] = createStorageMock()
  }
}

let piniaModulePromise: Promise<typeof import('pinia')> | null = null

async function getPiniaModule() {
  piniaModulePromise ??= import('pinia')
  return piniaModulePromise
}

async function initializeStoreGlobals() {
  const [pinia, vue] = await Promise.all([import('pinia'), import('vue')])
  ;(globalThis as any).defineStore = pinia.defineStore
  ;(globalThis as any).ref = vue.ref
  ;(globalThis as any).computed = vue.computed
}

async function createStore() {
  await initializeStoreGlobals()
  const { useEditorSessionStore } = await import('../editor.session.store')
  return useEditorSessionStore()
}

describe('editor.session.store', () => {
  beforeEach(async () => {
    const globalTarget = globalThis as unknown as Record<string, unknown>
    if (!(globalTarget.window && typeof globalTarget.window === 'object')) {
      globalTarget.window = globalTarget
    }

    const windowTarget = globalTarget.window as Record<string, unknown>
    ensureStorage(globalTarget, 'localStorage')
    ensureStorage(globalTarget, 'sessionStorage')
    ensureStorage(windowTarget, 'localStorage')
    ensureStorage(windowTarget, 'sessionStorage')

    const pinia = await getPiniaModule()
    pinia.setActivePinia(pinia.createPinia())
    ;(window as Window & { sessionStorage: StorageLike }).sessionStorage.clear()
  })

  it('tracks opened projects/pages and active context per project', async () => {
    const store = await createStore()

    store.initWorkspaceSession('workspace-1')
    store.addOpenedProject('project-a')
    store.addOpenedPage('project-a', 'page-a1')
    store.addOpenedPage('project-a', 'page-a2')
    store.setActivePage('project-a', 'page-a2')
    store.setSelectedVariant('project-a', 'page-a2', 'variant-a2')

    store.addOpenedProject('project-b')
    store.addOpenedPage('project-b', 'page-b1')
    store.setActiveProject('project-b')
    store.setActivePage('project-b', 'page-b1')

    expect(store.workspaceId).toBe('workspace-1')
    expect(store.openedProjectIds).toEqual(['project-a', 'project-b'])
    expect(store.activeProjectId).toBe('project-b')
    expect(store.getOpenedPageIds('project-a')).toEqual(['page-a1', 'page-a2'])
    expect(store.getActivePageId('project-a')).toBe('page-a2')
    expect(store.getSelectedVariantIdByPageId('project-a')).toEqual({ 'page-a2': 'variant-a2' })
    expect(store.getOpenedPageIds('project-b')).toEqual(['page-b1'])

    store.removeOpenedPage('project-b', 'page-b1')
    expect(store.getOpenedPageIds('project-b')).toEqual([])
    expect(store.getActivePageId('project-b')).toBeNull()

    store.removeOpenedProject('project-b')
    expect(store.openedProjectIds).toEqual(['project-a'])
    expect(store.activeProjectId).toBe('project-a')
  })

  it('migrates legacy single-project session payload on load', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
      projectId: 'legacy-project',
      openedPageIds: ['page-1', 'page-2'],
      activePageId: 'page-2',
      selectedVariantIdByPageId: {
        'page-2': 'variant-2'
      }
    }))

    const store = await createStore()
    const loaded = store.loadPersistedSession()

    expect(loaded).toBe(true)
    expect(store.workspaceId).toBeNull()
    expect(store.openedProjectIds).toEqual(['legacy-project'])
    expect(store.activeProjectId).toBe('legacy-project')
    expect(store.getOpenedPageIds('legacy-project')).toEqual(['page-1', 'page-2'])
    expect(store.getActivePageId('legacy-project')).toBe('page-2')
    expect(store.getSelectedVariantIdByPageId('legacy-project')).toEqual({ 'page-2': 'variant-2' })
    expect(store.textViewSettings).toEqual({
      mode: 'textline',
      gtIndex: 0,
      searchQuery: '',
      showDiff: false,
      showComments: false,
      focusMode: false,
      confidenceRange: [0, 1],
      selectedIndices: [],
      filterUnindexed: false,
      showNonAssignedIndices: false,
      onlyMissingGt: false
    })
  })

  it('persists and restores global text view settings across store instances', async () => {
    const store = await createStore()

    store.initWorkspaceSession('workspace-1')
    store.addOpenedProject('project-a')
    store.addOpenedPage('project-a', 'page-a1')
    store.updateTextViewSettings((current) => ({
      ...current,
      gtIndex: 3,
      showDiff: true,
      focusMode: true,
      confidenceRange: [0.2, 0.9],
      selectedIndices: [2, 5],
      filterUnindexed: true
    }))

    const pinia = await getPiniaModule()
    pinia.setActivePinia(pinia.createPinia())
    const reloadedStore = await createStore()
    const loaded = reloadedStore.loadPersistedSession()

    expect(loaded).toBe(true)
    expect(reloadedStore.textViewSettings).toEqual({
      mode: 'textline',
      gtIndex: 3,
      searchQuery: '',
      showDiff: true,
      showComments: false,
      focusMode: true,
      confidenceRange: [0.2, 0.9],
      selectedIndices: [2, 5],
      filterUnindexed: true,
      showNonAssignedIndices: false,
      onlyMissingGt: false
    })
  })

  it('can clear session state while preserving global text view settings', async () => {
    const store = await createStore()
    store.initWorkspaceSession('workspace-1')
    store.addOpenedProject('project-a')
    store.addOpenedPage('project-a', 'page-a1')
    store.updateTextViewSettings((current) => ({
      ...current,
      showDiff: true,
      confidenceRange: [0.25, 0.75],
      selectedIndices: [7],
      filterUnindexed: true
    }))

    store.clearSession({ preserveTextViewSettings: true })

    expect(store.openedProjectIds).toEqual([])
    expect(store.activeProjectId).toBeNull()
    expect(store.textViewSettings).toEqual({
      mode: 'textline',
      gtIndex: 0,
      searchQuery: '',
      showDiff: true,
      showComments: false,
      focusMode: false,
      confidenceRange: [0.25, 0.75],
      selectedIndices: [7],
      filterUnindexed: true,
      showNonAssignedIndices: false,
      onlyMissingGt: false
    })
  })

  it('migrates legacy onlyMissingGtLines and defaults to textline mode', async () => {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
      workspaceId: 'workspace-1',
      openedProjectIds: ['project-a'],
      activeProjectId: 'project-a',
      projectsById: {
        'project-a': {
          openedPageIds: ['page-a1'],
          activePageId: 'page-a1',
          selectedVariantIdByPageId: {}
        }
      },
      textViewSettings: {
        gtIndex: 4,
        onlyMissingGtLines: true
      }
    }))

    const store = await createStore()
    const loaded = store.loadPersistedSession()

    expect(loaded).toBe(true)
    expect(store.textViewSettings).toEqual({
      mode: 'textline',
      gtIndex: 4,
      searchQuery: '',
      showDiff: false,
      showComments: false,
      focusMode: false,
      confidenceRange: [0, 1],
      selectedIndices: [],
      filterUnindexed: false,
      showNonAssignedIndices: false,
      onlyMissingGt: true
    })
  })

  it('migrates legacy region text mode to textline mode', async () => {
    const store = await createStore()

    store.initWorkspaceSession('workspace-1')
    store.updateTextViewSettings((current) => ({
      ...current,
      mode: 'textline',
      onlyMissingGt: true
    }))

    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
      workspaceId: 'workspace-1',
      openedProjectIds: ['project-a'],
      activeProjectId: 'project-a',
      projectsById: {
        'project-a': {
          openedPageIds: ['page-a1'],
          activePageId: 'page-a1',
          selectedVariantIdByPageId: {}
        }
      },
      textViewSettings: {
        mode: 'region',
        onlyMissingGt: true
      }
    }))

    const pinia = await getPiniaModule()
    pinia.setActivePinia(pinia.createPinia())
    const reloadedStore = await createStore()
    const loaded = reloadedStore.loadPersistedSession()

    expect(loaded).toBe(true)
    expect(reloadedStore.textViewSettings).toEqual({
      mode: 'textline',
      gtIndex: undefined,
      searchQuery: '',
      showDiff: false,
      showComments: false,
      focusMode: false,
      confidenceRange: [0, 1],
      selectedIndices: [],
      filterUnindexed: false,
      showNonAssignedIndices: false,
      onlyMissingGt: true
    })
  })
})
