import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

type CookieValue = {
  value: any
}

type WorkspaceLike = {
  id: string
  name: string
  description?: string
  avatar?: string
  ownerUserId: string
  ownerUsername?: string
  isPersonal: boolean
  type?: 'personal' | 'team'
  capabilities?: {
    canAdminWorkspace: boolean
    canManageMembers: boolean
    canEditWorkspace: boolean
    canEditWorkspaceTextIndexDefaults: boolean
    canManageProjects: boolean
    canManageTasks: boolean
    canManageToolkit: boolean
    canSetPresets: boolean
  }
}

const cookieJar = new Map<string, CookieValue>()

const fullWorkspaceCapabilities = {
  canAdminWorkspace: true,
  canManageMembers: true,
  canEditWorkspace: true,
  canEditWorkspaceTextIndexDefaults: true,
  canManageProjects: true,
  canManageTasks: true,
  canManageToolkit: true,
  canSetPresets: true
}

function resetCookies(initialValues: Record<string, any> = {}) {
  cookieJar.clear()
  for (const [name, value] of Object.entries(initialValues)) {
    cookieJar.set(name, ref(value))
  }
}

function useCookieMock<T>(name: string, options?: { default?: () => T }): CookieValue {
  const existing = cookieJar.get(name)
  if (existing) {
    return existing
  }

  const next = ref(options?.default ? options.default() : null)
  cookieJar.set(name, next)
  return next
}

function getCookieValue(name: string) {
  return cookieJar.get(name)?.value ?? null
}

function buildWorkspace(overrides: Partial<WorkspaceLike> = {}): WorkspaceLike {
  return {
    id: 'workspace-1',
    name: 'Workspace One',
    description: 'Workspace description',
    ownerUserId: 'owner-1',
    isPersonal: false,
    type: 'team',
    capabilities: { ...fullWorkspaceCapabilities },
    ...overrides
  }
}

function buildPersonalWorkspace(overrides: Partial<WorkspaceLike> = {}): WorkspaceLike {
  return buildWorkspace({
    id: 'personal-1',
    name: 'Personal Workspace',
    ownerUserId: 'user-1',
    isPersonal: true,
    type: 'personal',
    ...overrides
  })
}

function createFetchMock(options: {
  regularWorkspaces: WorkspaceLike[]
  workspaceById?: Record<string, WorkspaceLike>
}) {
  const workspaceById = options.workspaceById ?? {}

  return vi.fn(async (url: string) => {
    if (url === '/api/workspaces') {
      return options.regularWorkspaces
    }

    const workspaceByIdMatch = url.match(/^\/api\/workspaces\/([^/]+)$/)
    if (workspaceByIdMatch) {
      const workspaceId = workspaceByIdMatch[1]
      if (!workspaceId) {
        throw new Error(`Invalid workspace URL: ${url}`)
      }
      const workspace = workspaceById[workspaceId]
      if (!workspace) {
        throw new Error(`Workspace not found: ${workspaceId}`)
      }
      return workspace
    }

    throw new Error(`Unexpected fetch URL: ${url}`)
  })
}

async function setActiveTestingPinia() {
  const pinia = await import('pinia')
  pinia.setActivePinia(pinia.createPinia())
  ;(globalThis as any).defineStore = pinia.defineStore
}

async function initializeStoreGlobals(fetchMock: ReturnType<typeof vi.fn>) {
  const vue = await import('vue')

  ;(globalThis as any).ref = vue.ref
  ;(globalThis as any).computed = vue.computed
  ;(globalThis as any).useCookie = vi.fn(useCookieMock)
  ;(globalThis as any).$fetch = fetchMock
  ;(globalThis as any).useRequestFetch = vi.fn(() => fetchMock)
  ;(globalThis as any).useUserSession = vi.fn(() => ({
    user: ref({ id: 'user-1' })
  }))

  await setActiveTestingPinia()
}

async function createStore(fetchMock: ReturnType<typeof vi.fn>) {
  await initializeStoreGlobals(fetchMock)
  const { useWorkspaceStore } = await import('../workspace.store')
  return useWorkspaceStore()
}

describe('workspace.store', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    resetCookies()
  })

  it('keeps an admin workspace selected even when it is not in the regular workspace list', async () => {
    resetCookies({
      selectedWorkspaceId: 'admin-team',
      adminWorkspaceId: 'admin-team'
    })

    const adminWorkspace = buildWorkspace({
      id: 'admin-team',
      name: 'Admin Team Workspace',
      ownerUserId: 'owner-2'
    })
    const fetchMock = createFetchMock({
      regularWorkspaces: [
        buildPersonalWorkspace(),
        buildWorkspace({ id: 'member-team', name: 'Member Team Workspace' })
      ],
      workspaceById: {
        'admin-team': adminWorkspace
      }
    })

    const store = await createStore(fetchMock)
    const selectedWorkspaceId = await store.validateAndSelectWorkspace()

    expect(selectedWorkspaceId).toBe('admin-team')
    expect(store.selectedWorkspaceId).toBe('admin-team')
    expect(store.isAdminMode).toBe(true)
    expect(store.adminWorkspace?.id).toBe('admin-team')
    expect(store.currentWorkspace?.id).toBe('admin-team')
    expect(store.canManageProjects).toBe(true)
    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces')
    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/admin-team')
  })

  it('rehydrates a persisted admin workspace after a fresh store instance', async () => {
    const adminWorkspace = buildWorkspace({
      id: 'admin-team',
      name: 'Admin Team Workspace',
      description: 'Freshly fetched details',
      ownerUserId: 'owner-2'
    })
    const fetchMock = createFetchMock({
      regularWorkspaces: [buildPersonalWorkspace()],
      workspaceById: {
        'admin-team': adminWorkspace
      }
    })

    const store = await createStore(fetchMock)
    store.selectWorkspaceAsAdmin({
      id: 'admin-team',
      name: 'Admin Team Workspace',
      description: 'Partial admin row',
      ownerUserId: 'owner-2',
      isPersonal: false
    })

    await setActiveTestingPinia()
    const { useWorkspaceStore } = await import('../workspace.store')
    const reloadedStore = useWorkspaceStore()

    const selectedWorkspaceId = await reloadedStore.validateAndSelectWorkspace()

    expect(selectedWorkspaceId).toBe('admin-team')
    expect(reloadedStore.isAdminMode).toBe(true)
    expect(reloadedStore.adminWorkspace?.description).toBe('Freshly fetched details')
    expect(reloadedStore.adminWorkspace?.capabilities).toEqual(fullWorkspaceCapabilities)
    expect(reloadedStore.canManageToolkit).toBe(true)
    expect(getCookieValue('adminWorkspaceId')).toBe('admin-team')
  })

  it('clears a stale admin selection and falls back to the first regular workspace', async () => {
    resetCookies({
      selectedWorkspaceId: 'missing-admin',
      adminWorkspaceId: 'missing-admin'
    })

    const fallbackWorkspace = buildPersonalWorkspace({ id: 'personal-home' })
    const fetchMock = createFetchMock({
      regularWorkspaces: [fallbackWorkspace, buildWorkspace({ id: 'member-team' })]
    })

    const store = await createStore(fetchMock)
    const selectedWorkspaceId = await store.validateAndSelectWorkspace()

    expect(selectedWorkspaceId).toBe('personal-home')
    expect(store.selectedWorkspaceId).toBe('personal-home')
    expect(store.isAdminMode).toBe(false)
    expect(store.adminWorkspace).toBeNull()
    expect(getCookieValue('adminWorkspaceId')).toBeNull()
    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/missing-admin')
  })

  it('keeps normal workspace validation unchanged when no admin selection is persisted', async () => {
    resetCookies({
      selectedWorkspaceId: 'member-team'
    })

    const fetchMock = createFetchMock({
      regularWorkspaces: [
        buildPersonalWorkspace(),
        buildWorkspace({ id: 'member-team', name: 'Member Team Workspace' })
      ]
    })

    const store = await createStore(fetchMock)
    const selectedWorkspaceId = await store.validateAndSelectWorkspace()

    expect(selectedWorkspaceId).toBe('member-team')
    expect(store.selectedWorkspaceId).toBe('member-team')
    expect(store.isAdminMode).toBe(false)
    expect(store.adminWorkspace).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces')
  })
})
