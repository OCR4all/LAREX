/**
 * Workspace Store
 *
 * Manages workspace state with SSR-safe persistence using cookies.
 * The selectedWorkspaceId is stored in a cookie for SSR compatibility,
 * while workspace data is fetched fresh from the API.
 */
import type { WorkspaceCapabilities } from '@/types/capabilities'
import { DEFAULT_WORKSPACE_CAPABILITIES } from '@/types/capabilities'

export const useWorkspaceStore = defineStore('workspace', () => {
  const selectedWorkspaceIdCookie = useCookie<string | null>('selectedWorkspaceId', {
    default: () => null,
    maxAge: 60 * 60 * 24 * 365, // 1 year
    sameSite: 'lax', // Allow cookie to be sent on navigation
    watch: true // Ensure reactivity across components
  })

  if (import.meta.client && !selectedWorkspaceIdCookie.value) {
    try {
      const legacyCookie = useCookie<{ selectedWorkspaceId?: string } | null>('workspace')
      if (legacyCookie.value?.selectedWorkspaceId) {
        selectedWorkspaceIdCookie.value = legacyCookie.value.selectedWorkspaceId
        legacyCookie.value = null
      }
    } catch {
      // Ignore malformed legacy workspace cookies.
    }
  }

  interface Workspace {
    id: string
    name: string
    description?: string
    avatar?: string
    memberCount?: number
    created?: string
    updated?: string
    ownerUserId: string
    ownerUsername?: string
    isPersonal: boolean
    type?: 'personal' | 'team'
    capabilities?: WorkspaceCapabilities
  }

  const workspaces = ref<Workspace[]>([])
  const isLoading = ref(false)
  const loadError = ref<string | null>(null)
  const hasFetched = ref(false)

  const isAdminMode = ref(false)
  const adminWorkspace = ref<Workspace | null>(null)

  const selectedWorkspaceId = computed({
    get: () => selectedWorkspaceIdCookie.value,
    set: (value) => { selectedWorkspaceIdCookie.value = value }
  })

  function selectWorkspace(id: string) {
    isAdminMode.value = false
    adminWorkspace.value = null
    selectedWorkspaceIdCookie.value = id
  }

  /**
   * Select a workspace as admin (for workspaces the user doesn't belong to)
   */
  function selectWorkspaceAsAdmin(workspace: Workspace) {
    isAdminMode.value = true
    adminWorkspace.value = workspace
    selectedWorkspaceIdCookie.value = workspace.id
  }

  /**
   * Exit admin mode and return to user's own workspaces
   */
  function exitAdminMode() {
    isAdminMode.value = false
    adminWorkspace.value = null
    const first = workspaces.value[0]
    if (first?.id) {
      selectedWorkspaceIdCookie.value = first.id
    }
  }

  const currentWorkspace = computed(() => {
    if (isAdminMode.value && adminWorkspace.value) {
      return adminWorkspace.value
    }
    if (!selectedWorkspaceId.value) return null
    return workspaces.value.find(w => w.id === selectedWorkspaceId.value) || null
  })

  const isCurrentUserOwner = computed(() => {
    const workspace = currentWorkspace.value
    if (!workspace) return false
    if (workspace.isPersonal) return true
    const { user } = useUserSession()
    return workspace.ownerUserId === user.value?.id
  })

  const canManageMembers = computed(() => {
    const workspace = currentWorkspace.value
    if (!workspace) return false
    return workspace.capabilities?.canManageMembers ?? DEFAULT_WORKSPACE_CAPABILITIES.canManageMembers
  })

  const canManageProjects = computed(() => {
    const workspace = currentWorkspace.value
    if (!workspace) return false
    return workspace.capabilities?.canManageProjects ?? DEFAULT_WORKSPACE_CAPABILITIES.canManageProjects
  })

  const canManageTasks = computed(() => {
    const workspace = currentWorkspace.value
    if (!workspace) return false
    return workspace.capabilities?.canManageTasks ?? DEFAULT_WORKSPACE_CAPABILITIES.canManageTasks
  })

  const canManageUtilities = computed(() => {
    const workspace = currentWorkspace.value
    if (!workspace) return false
    return workspace.capabilities?.canManageUtilities ?? DEFAULT_WORKSPACE_CAPABILITIES.canManageUtilities
  })

  /**
   * Fetch workspaces from API
   * This also triggers personal workspace creation on the backend if needed
   */
  const fetchWorkspaces = async (): Promise<Workspace[]> => {
    const requestFetch = import.meta.server ? useRequestFetch() : $fetch
    const previousWorkspaces = [...workspaces.value]
    const hadExistingWorkspaces = previousWorkspaces.length > 0

    isLoading.value = true
    loadError.value = null

    try {
      const res = await requestFetch<Workspace[]>('/api/workspaces')
      workspaces.value = Array.isArray(res) ? res : []
      hasFetched.value = true
      return workspaces.value
    } catch (err: unknown) {
      const error = err as { data?: { message?: unknown }, message?: unknown }
      const message = error.data?.message || error.message || 'Failed to load workspaces'
      loadError.value = String(message)
      if (!hadExistingWorkspaces) {
        workspaces.value = []
      }
      return hadExistingWorkspaces ? previousWorkspaces : []
    } finally {
      isLoading.value = false
    }
  }

  /**
   * Validate and correct the selected workspace
   * - Ensures we have workspace data
   * - Checks if the selected workspace still exists
   * - Falls back to first available workspace if needed
   */
  const validateAndSelectWorkspace = async (): Promise<string | null> => {
    const savedWorkspaceId = selectedWorkspaceIdCookie.value

    if (!hasFetched.value || workspaces.value.length === 0) {
      await fetchWorkspaces()
    }

    if (savedWorkspaceId) {
      const exists = workspaces.value.find(w => w.id === savedWorkspaceId)
      if (exists) {
        if (selectedWorkspaceIdCookie.value !== savedWorkspaceId) {
          selectedWorkspaceIdCookie.value = savedWorkspaceId
        }
        return savedWorkspaceId
      }
    }

    const first = workspaces.value[0]
    if (first?.id) {
      selectedWorkspaceIdCookie.value = first.id
      if (!import.meta.server) {
        isAdminMode.value = false
        adminWorkspace.value = null
      }
      return first.id
    }

    selectedWorkspaceIdCookie.value = null
    return null
  }

  /**
   * Clear all workspace state (call on logout)
   */
  const clearState = () => {
    selectedWorkspaceIdCookie.value = null
    workspaces.value = []
    loadError.value = null
    hasFetched.value = false
    isAdminMode.value = false
    adminWorkspace.value = null
  }

  /**
   * Force refresh workspaces (useful after creating/deleting workspaces)
   */
  const refreshWorkspaces = async (): Promise<Workspace[]> => {
    hasFetched.value = false
    return fetchWorkspaces()
  }

  return {
    selectedWorkspaceId,
    workspaces,
    isLoading,
    loadError,
    hasFetched,
    isAdminMode,
    adminWorkspace,
    currentWorkspace,
    isCurrentUserOwner,
    canManageMembers,
    canManageProjects,
    canManageTasks,
    canManageUtilities,
    selectWorkspace,
    selectWorkspaceAsAdmin,
    exitAdminMode,
    fetchWorkspaces,
    validateAndSelectWorkspace,
    refreshWorkspaces,
    clearState
  }
})
